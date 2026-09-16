package net.yaycraft.yunit.service

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.yaycraft.yunit.cache.IAccountCache
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.database.IDatabaseProvider
import net.yaycraft.yunit.exception.DatabaseUnavailableException
import net.yaycraft.yunit.model.*
import net.yaycraft.yunit.repository.IAccountRepository
import net.yaycraft.yunit.repository.ITransactionRepository
import net.yaycraft.yunit.util.MAX_AMOUNT
import net.yaycraft.yunit.util.isValidAmount
import net.yaycraft.yunit.util.isValidBalance
import java.math.BigDecimal
import java.sql.SQLIntegrityConstraintViolationException
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

class EconomyServiceImpl(
    private val dbProvider: IDatabaseProvider,
    private val accountRepo: IAccountRepository,
    private val transactionRepo: ITransactionRepository,
    private val cache: IAccountCache,
    private val guard: PlayerGuard,
    private val notifier: BalanceChangeNotifier,
    private val dbDispatcher: CoroutineDispatcher,
    private val config: PluginConfig,
    private val logger: Logger
) : IEconomyService {

    override fun getCachedBalance(uuid: UUID): BigDecimal? = cache.get(uuid)?.balance

    override suspend fun getBalance(uuid: UUID): BigDecimal {
        return findAccount(uuid)?.balance ?: BigDecimal.ZERO
    }

    override suspend fun hasBalance(uuid: UUID, amount: BigDecimal): Boolean {
        return getBalance(uuid) >= amount
    }

    override suspend fun findAccount(uuid: UUID): YunitAccount? {
        // Cache-first okuma
        cache.get(uuid)?.let { return it }

        return withContext(dbDispatcher) {
            guard.lockFor(uuid).withLock {
                cache.get(uuid)?.let { return@withLock it }

                val account = dbProvider.executeTransaction { conn -> accountRepo.findByUuid(conn, uuid) }
                if (account != null) cache.put(uuid, account)
                account
            }
        }
    }

    override suspend fun findAccountByName(username: String): YunitAccount? = withContext(dbDispatcher) {
        val matches = dbProvider.executeTransaction { conn -> accountRepo.findByUsername(conn, username) }
        matches.firstOrNull { it.username == username } ?: matches.singleOrNull()
    }

    override suspend fun deposit(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String?, initiatedBy: String, idempotencyKey: String?
    ): TransactionResult = withContext(dbDispatcher) {

        if (!amount.isValidAmount()) {
            return@withContext invalidAmount()
        }
        validateRequest(idempotencyKey)?.let { return@withContext it }

        if (!dbProvider.isHealthy()) {
            return@withContext TransactionResult.Failure(FailureReason.DATABASE_UNAVAILABLE, "Veritabanı şu an erişilemez")
        }

        guard.lockFor(uuid).withLock {
            val result = runSafely(idempotencyKey) {
                dbProvider.executeTransaction { conn ->
                    if (idempotencyKey != null && transactionRepo.existsByIdempotencyKey(conn, idempotencyKey)) {
                        return@executeTransaction duplicate()
                    }

                    val account = accountRepo.findByUuidForUpdate(conn, uuid)
                        ?: return@executeTransaction accountNotFound()

                    val newBalance = account.balance + amount
                    if (newBalance > MAX_AMOUNT) {
                        return@executeTransaction TransactionResult.Failure(FailureReason.INVALID_AMOUNT, "Bakiye üst sınırı aşılıyor")
                    }
                    accountRepo.updateBalance(conn, uuid, newBalance)

                    val txn = transactionRepo.create(conn, Transaction(
                        playerUuid = uuid, type = type, amount = amount,
                        balanceBefore = account.balance, balanceAfter = newBalance,
                        description = description?.take(255), sourceServer = config.serverName,
                        initiatedBy = initiatedBy.take(64), idempotencyKey = idempotencyKey
                    ))

                    TransactionResult.Success(account.copy(balance = newBalance), txn)
                }
            }
            // Commit'ten sonra: önbelleği temizle + diğer sunuculara bildir
            if (result is TransactionResult.Success) notifier.notifyChanged(uuid)
            result
        }
    }

    override suspend fun withdraw(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String?, initiatedBy: String, idempotencyKey: String?
    ): TransactionResult = withContext(dbDispatcher) {

        if (!amount.isValidAmount()) {
            return@withContext invalidAmount()
        }
        validateRequest(idempotencyKey)?.let { return@withContext it }

        if (!dbProvider.isHealthy()) {
            return@withContext TransactionResult.Failure(FailureReason.DATABASE_UNAVAILABLE, "Sistem şu an bakımda")
        }

        // Admin/sistem işlemleri hız sınırına takılmaz
        if (type !in RATE_LIMIT_EXEMPT && !guard.tryAcquireCooldown(uuid)) {
            return@withContext TransactionResult.Failure(FailureReason.RATE_LIMITED, "Çok hızlı işlem, lütfen bekleyin")
        }

        guard.lockFor(uuid).withLock {
            val result = runSafely(idempotencyKey) {
                dbProvider.executeTransaction { conn ->
                    if (idempotencyKey != null && transactionRepo.existsByIdempotencyKey(conn, idempotencyKey)) {
                        return@executeTransaction duplicate()
                    }

                    val account = accountRepo.findByUuidForUpdate(conn, uuid)
                        ?: return@executeTransaction accountNotFound()

                    if (account.balance < amount) {
                        return@executeTransaction TransactionResult.Failure(FailureReason.INSUFFICIENT_BALANCE, "Yetersiz bakiye")
                    }

                    val newBalance = account.balance - amount
                    accountRepo.updateBalance(conn, uuid, newBalance)

                    val txn = transactionRepo.create(conn, Transaction(
                        playerUuid = uuid, type = type, amount = amount,
                        balanceBefore = account.balance, balanceAfter = newBalance,
                        description = description?.take(255), sourceServer = config.serverName,
                        initiatedBy = initiatedBy.take(64), idempotencyKey = idempotencyKey
                    ))

                    TransactionResult.Success(account.copy(balance = newBalance), txn)
                }
            }
            if (result is TransactionResult.Success) notifier.notifyChanged(uuid)
            result
        }
    }

    override suspend fun setBalance(
        uuid: UUID, amount: BigDecimal, initiatedBy: String
    ): TransactionResult = withContext(dbDispatcher) {
        if (!amount.isValidBalance()) {
            return@withContext TransactionResult.Failure(FailureReason.INVALID_AMOUNT, "Geçersiz bakiye (negatif olamaz, en fazla 2 ondalık)")
        }

        if (!dbProvider.isHealthy()) {
            return@withContext TransactionResult.Failure(FailureReason.DATABASE_UNAVAILABLE, "Veritabanı şu an erişilemez")
        }

        guard.lockFor(uuid).withLock {
            val result = runSafely(null) {
                dbProvider.executeTransaction { conn ->
                    val account = accountRepo.findByUuidForUpdate(conn, uuid)
                        ?: return@executeTransaction accountNotFound()

                    accountRepo.updateBalance(conn, uuid, amount)

                    val txn = transactionRepo.create(conn, Transaction(
                        playerUuid = uuid, type = TransactionType.ADMIN_SET, amount = (amount - account.balance).abs(),
                        balanceBefore = account.balance, balanceAfter = amount,
                        description = "Bakiye ayarlandı", sourceServer = config.serverName,
                        initiatedBy = initiatedBy.take(64)
                    ))

                    TransactionResult.Success(account.copy(balance = amount), txn)
                }
            }
            if (result is TransactionResult.Success) notifier.notifyChanged(uuid)
            result
        }
    }

    override suspend fun getOrCreateAccount(uuid: UUID, username: String): YunitAccount = withContext(dbDispatcher) {
        val safeName = username.take(32)
        cache.get(uuid)?.let { if (it.username == safeName) return@withContext it }

        guard.lockFor(uuid).withLock {
            val account = dbProvider.executeTransaction { conn ->
                val existing = accountRepo.findByUuidForUpdate(conn, uuid)
                when {
                    existing == null -> accountRepo.create(conn, uuid, safeName)
                    existing.username != safeName -> {
                        accountRepo.updateUsername(conn, uuid, safeName)
                        existing.copy(username = safeName)
                    }
                    else -> existing
                }
            }
            cache.put(uuid, account)
            account
        }
    }

    override suspend fun getTransactionHistory(uuid: UUID, page: Int, pageSize: Int): List<Transaction> = withContext(dbDispatcher) {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 100)
        dbProvider.executeTransaction { conn ->
            transactionRepo.findByPlayer(conn, uuid, safeSize, (safePage - 1) * safeSize)
        }
    }

    override suspend fun getTopAccounts(limit: Int): List<YunitAccount> = withContext(dbDispatcher) {
        dbProvider.executeTransaction { conn ->
            accountRepo.findTopAccounts(conn, limit.coerceIn(1, 100))
        }
    }

    /**
     * Veritabanı hatalarını exception yerine Failure sonucuna çevirir.
     * Böylece API kullanan eklentiler her durumda anlamlı bir sonuç alır.
     */
    private inline fun runSafely(idempotencyKey: String?, block: () -> TransactionResult): TransactionResult {
        return try {
            block()
        } catch (e: Exception) {
            when {
                e.hasCause<DatabaseUnavailableException>() ->
                    TransactionResult.Failure(FailureReason.DATABASE_UNAVAILABLE, "Veritabanı şu an erişilemez")
                idempotencyKey != null && e.hasCause<SQLIntegrityConstraintViolationException>() ->
                    duplicate()
                else -> {
                    logger.log(Level.SEVERE, "Ekonomi işlemi sırasında veritabanı hatası", e)
                    TransactionResult.Failure(FailureReason.DATABASE_ERROR, "Veritabanı hatası")
                }
            }
        }
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }

    private fun validateRequest(idempotencyKey: String?): TransactionResult.Failure? {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length > 64)) {
            return TransactionResult.Failure(FailureReason.INVALID_REQUEST, "Idempotency key 1-64 karakter olmalı")
        }
        return null
    }

    private fun invalidAmount() = TransactionResult.Failure(
        FailureReason.INVALID_AMOUNT, "Miktar 0'dan büyük olmalı ve en fazla 2 ondalık basamak içermeli"
    )

    private fun duplicate() = TransactionResult.Failure(FailureReason.DUPLICATE_TRANSACTION, "Bu işlem zaten gerçekleşti")

    private fun accountNotFound() = TransactionResult.Failure(FailureReason.ACCOUNT_NOT_FOUND, "Hesap bulunamadı")

    private companion object {
        val RATE_LIMIT_EXEMPT = setOf(
            TransactionType.ADMIN_GIVE, TransactionType.ADMIN_TAKE, TransactionType.ADMIN_SET,
            TransactionType.REFUND, TransactionType.SYSTEM
        )
    }
}

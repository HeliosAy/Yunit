package net.yaycraft.yunit.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.yaycraft.yunit.cache.IAccountCache
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.database.IDatabaseProvider
import net.yaycraft.yunit.model.*
import net.yaycraft.yunit.repository.IAccountRepository
import net.yaycraft.yunit.repository.ITransactionRepository
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class EconomyServiceImpl(
    private val dbProvider: IDatabaseProvider,
    private val accountRepo: IAccountRepository,
    private val transactionRepo: ITransactionRepository,
    private val cache: IAccountCache,
    private val config: PluginConfig,
    private val logger: Logger
) : IEconomyService {

    private val playerLocks = ConcurrentHashMap<UUID, Mutex>()
    private val lastTransactionTime = ConcurrentHashMap<UUID, Long>()
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(config.database.poolSize)

    override suspend fun getBalance(uuid: UUID): BigDecimal {
        // Cache-first okuma
        val cached = cache.get(uuid)
        if (cached != null) return cached.balance

        return withContext(dbDispatcher) {
            val mutex = playerLocks.computeIfAbsent(uuid) { Mutex() }
            mutex.withLock {
                val doubleCheck = cache.get(uuid)
                if (doubleCheck != null) return@withLock doubleCheck.balance

                dbProvider.executeTransaction { conn ->
                    val account = accountRepo.findByUuid(conn, uuid)
                    if (account != null) {
                        cache.put(uuid, account)
                        account.balance
                    } else {
                        BigDecimal.ZERO
                    }
                }
            }
        }
    }

    override suspend fun hasBalance(uuid: UUID, amount: BigDecimal): Boolean {
        return getBalance(uuid) >= amount
    }

    override suspend fun deposit(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String?, initiatedBy: String, idempotencyKey: String?
    ): TransactionResult = withContext(dbDispatcher) {
        
        if (amount <= BigDecimal.ZERO) {
            return@withContext TransactionResult.Failure(FailureReason.INVALID_AMOUNT, "Miktar 0'dan büyük olmalı")
        }

        if (!dbProvider.isHealthy()) {
            return@withContext TransactionResult.Failure(FailureReason.DATABASE_UNAVAILABLE, "Veritabanı şu an erişilemez")
        }

        val mutex = playerLocks.computeIfAbsent(uuid) { Mutex() }
        mutex.withLock {
            
            dbProvider.executeTransaction { conn ->
                if (idempotencyKey != null && transactionRepo.existsByIdempotencyKey(conn, idempotencyKey)) {
                    return@executeTransaction TransactionResult.Failure(FailureReason.DUPLICATE_TRANSACTION, "Bu işlem zaten gerçekleşti")
                }

                val account = accountRepo.findByUuidForUpdate(conn, uuid)
                    ?: return@executeTransaction TransactionResult.Failure(FailureReason.ACCOUNT_NOT_FOUND, "Hesap bulunamadı")

                val newBalance = account.balance + amount
                accountRepo.updateBalance(conn, uuid, newBalance)

                val txn = transactionRepo.create(conn, Transaction(
                    playerUuid = uuid, type = type, amount = amount,
                    balanceBefore = account.balance, balanceAfter = newBalance,
                    description = description, sourceServer = config.serverName,
                    initiatedBy = initiatedBy, idempotencyKey = idempotencyKey
                ))

                val updatedAccount = account.copy(balance = newBalance)
                cache.invalidate(uuid)
                
                TransactionResult.Success(updatedAccount, txn)
            }
        }
    }

    override suspend fun withdraw(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String?, initiatedBy: String, idempotencyKey: String?
    ): TransactionResult = withContext(dbDispatcher) {
        
        if (amount <= BigDecimal.ZERO) {
            return@withContext TransactionResult.Failure(FailureReason.INVALID_AMOUNT, "Miktar 0'dan büyük olmalı")
        }

        if (!dbProvider.isHealthy()) {
            return@withContext TransactionResult.Failure(FailureReason.DATABASE_UNAVAILABLE, "Sistem şu an bakımda")
        }

        val now = System.currentTimeMillis()
        val lastTime = lastTransactionTime[uuid] ?: 0L
        if (now - lastTime < config.safety.transactionCooldownMs) {
            return@withContext TransactionResult.Failure(FailureReason.RATE_LIMITED, "Çok hızlı işlem, lütfen bekleyin")
        }

        val mutex = playerLocks.computeIfAbsent(uuid) { Mutex() }
        mutex.withLock {
            lastTransactionTime[uuid] = System.currentTimeMillis()
            
            dbProvider.executeTransaction { conn ->
                if (idempotencyKey != null && transactionRepo.existsByIdempotencyKey(conn, idempotencyKey)) {
                    return@executeTransaction TransactionResult.Failure(FailureReason.DUPLICATE_TRANSACTION, "Bu işlem zaten gerçekleşti")
                }

                val account = accountRepo.findByUuidForUpdate(conn, uuid)
                    ?: return@executeTransaction TransactionResult.Failure(FailureReason.ACCOUNT_NOT_FOUND, "Hesap bulunamadı")

                if (account.balance < amount) {
                    return@executeTransaction TransactionResult.Failure(FailureReason.INSUFFICIENT_BALANCE, "Yetersiz bakiye")
                }

                val newBalance = account.balance - amount
                accountRepo.updateBalance(conn, uuid, newBalance)

                val txn = transactionRepo.create(conn, Transaction(
                    playerUuid = uuid, type = type, amount = amount,
                    balanceBefore = account.balance, balanceAfter = newBalance,
                    description = description, sourceServer = config.serverName,
                    initiatedBy = initiatedBy, idempotencyKey = idempotencyKey
                ))

                cache.invalidate(uuid)
                
                TransactionResult.Success(account.copy(balance = newBalance), txn)
            }
        }
    }

    override suspend fun setBalance(
        uuid: UUID, amount: BigDecimal, initiatedBy: String
    ): TransactionResult = withContext(dbDispatcher) {
        if (amount < BigDecimal.ZERO) {
            return@withContext TransactionResult.Failure(FailureReason.INVALID_AMOUNT, "Bakiye negatif olamaz")
        }
        
        if (!dbProvider.isHealthy()) {
            return@withContext TransactionResult.Failure(FailureReason.DATABASE_UNAVAILABLE, "Veritabanı şu an erişilemez")
        }

        val mutex = playerLocks.computeIfAbsent(uuid) { Mutex() }
        mutex.withLock {
            dbProvider.executeTransaction { conn ->
                val account = accountRepo.findByUuidForUpdate(conn, uuid)
                    ?: return@executeTransaction TransactionResult.Failure(FailureReason.ACCOUNT_NOT_FOUND, "Hesap bulunamadı")

                val diff = amount - account.balance
                val type = if (diff >= BigDecimal.ZERO) TransactionType.ADMIN_SET else TransactionType.ADMIN_SET
                val amountAbs = diff.abs()

                accountRepo.updateBalance(conn, uuid, amount)

                val txn = transactionRepo.create(conn, Transaction(
                    playerUuid = uuid, type = type, amount = amountAbs,
                    balanceBefore = account.balance, balanceAfter = amount,
                    description = "Bakiye ayarlandı", sourceServer = config.serverName,
                    initiatedBy = initiatedBy
                ))

                cache.invalidate(uuid)
                TransactionResult.Success(account.copy(balance = amount), txn)
            }
        }
    }

    override suspend fun getOrCreateAccount(uuid: UUID, username: String): YunitAccount = withContext(dbDispatcher) {
        val cached = cache.get(uuid)
        if (cached != null) return@withContext cached
        
        val mutex = playerLocks.computeIfAbsent(uuid) { Mutex() }
        mutex.withLock {
            val doubleCheck = cache.get(uuid)
            if (doubleCheck != null) return@withLock doubleCheck

            dbProvider.executeTransaction { conn ->
                var account = accountRepo.findByUuidForUpdate(conn, uuid)
                if (account == null) {
                    account = accountRepo.create(conn, uuid, username)
                } else if (account.username != username) {
                    // Oyuncu isim değiştirmişse güncelle
                    accountRepo.updateUsername(conn, uuid, username)
                    account = account.copy(username = username)
                }
                cache.put(uuid, account)
                account
            }
        }
    }

    override suspend fun getTransactionHistory(uuid: UUID, page: Int, pageSize: Int): List<Transaction> = withContext(dbDispatcher) {
        dbProvider.executeTransaction { conn ->
            val offset = (page - 1) * pageSize
            transactionRepo.findByPlayer(conn, uuid, pageSize, offset)
        }
    }

    override suspend fun getTopAccounts(limit: Int): List<YunitAccount> = withContext(dbDispatcher) {
        dbProvider.executeTransaction { conn ->
            accountRepo.findTopAccounts(conn, limit)
        }
    }
}

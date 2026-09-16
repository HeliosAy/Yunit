package net.yaycraft.yunit.service

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.database.IDatabaseProvider
import net.yaycraft.yunit.exception.DatabaseUnavailableException
import net.yaycraft.yunit.model.*
import net.yaycraft.yunit.repository.IAccountRepository
import net.yaycraft.yunit.repository.IPendingDeliveryRepository
import net.yaycraft.yunit.repository.ITransactionRepository
import net.yaycraft.yunit.util.isValidAmount
import java.math.BigDecimal
import java.util.UUID
import java.util.function.BooleanSupplier
import java.util.logging.Level
import java.util.logging.Logger

class SafePurchaseServiceImpl(
    private val dbProvider: IDatabaseProvider,
    private val accountRepo: IAccountRepository,
    private val transactionRepo: ITransactionRepository,
    private val pendingRepo: IPendingDeliveryRepository,
    private val guard: PlayerGuard,
    private val notifier: BalanceChangeNotifier,
    private val dbDispatcher: CoroutineDispatcher,
    private val config: PluginConfig,
    private val logger: Logger
) : ISafePurchaseService {

    private sealed interface ReserveOutcome {
        data class Reserved(val account: YunitAccount, val transaction: Transaction, val pendingId: Long) : ReserveOutcome
        data class Rejected(val result: PurchaseResult) : ReserveOutcome
    }

    override suspend fun executePurchase(
        uuid: UUID, amount: BigDecimal, description: String,
        pluginName: String, deliveryData: String,
        deliveryAction: BooleanSupplier
    ): PurchaseResult = withContext(dbDispatcher) {

        if (!amount.isValidAmount())
            return@withContext PurchaseResult.Error("Geçersiz miktar")

        if (!dbProvider.isHealthy())
            return@withContext PurchaseResult.Error("Sistem şu an kullanılamıyor")

        if (!guard.tryAcquireCooldown(uuid))
            return@withContext PurchaseResult.Error("Çok hızlı işlem, lütfen bekleyin")

        val safePlugin = pluginName.take(64)

        // Parayı çek + PENDING kaydı oluştur
        val outcome = guard.lockFor(uuid).withLock {
            reserve(uuid, amount, description.take(255), safePlugin, normalizeJson(deliveryData))
        }
        val reserved = when (outcome) {
            is ReserveOutcome.Rejected -> return@withContext outcome.result
            is ReserveOutcome.Reserved -> outcome
        }
        notifier.notifyChanged(uuid)

        //  Para çekildi. Bundan sonrası (teslimat + işaretleme/iade) yarıda kesilmemeli;
        //  plugin kapanırken coroutine iptal edilse bile tamamlanmaya çalışır.
        withContext(NonCancellable) {
            val delivered = try {
                deliveryAction.asBoolean
            } catch (t: Throwable) {
                logger.log(Level.SEVERE, "Delivery sırasında hata oluştu ($safePlugin): ${t.message}", t)
                false
            }

            if (delivered) {
                if (!markDelivered(reserved.pendingId)) {
                    logger.severe(
                        "DIKKAT: Teslimat yapıldı ama DELIVERED olarak işaretlenemedi (pendingId: ${reserved.pendingId}). " +
                        "Sunucu yeniden başlamadan önce bu kaydı elle DELIVERED yapın, aksi halde recovery iade edebilir."
                    )
                }
                PurchaseResult.Success(reserved.account, reserved.transaction)
            } else {
                val refunded = refundPending(reserved.pendingId, uuid, amount, safePlugin)
                if (refunded) {
                    logger.info("Otomatik iade tamamlandı: $uuid, pendingId: ${reserved.pendingId}, tutar: $amount")
                    PurchaseResult.DeliveryFailed(amount, "Teslim başarısız, para iade edildi", true)
                } else {
                    PurchaseResult.DeliveryFailed(amount, "Teslim başarısız, iade sunucu yeniden başladığında yapılacak", false)
                }
            }
        }
    }

    private fun reserve(
        uuid: UUID, amount: BigDecimal, description: String, pluginName: String, deliveryData: String
    ): ReserveOutcome {
        return try {
            dbProvider.executeTransaction { conn ->
                val account = accountRepo.findByUuidForUpdate(conn, uuid)
                    ?: return@executeTransaction ReserveOutcome.Rejected(PurchaseResult.Error("Hesap bulunamadı"))

                if (account.balance < amount) {
                    return@executeTransaction ReserveOutcome.Rejected(PurchaseResult.InsufficientBalance(amount, account.balance))
                }

                val newBalance = account.balance - amount
                accountRepo.updateBalance(conn, uuid, newBalance)

                val txn = transactionRepo.create(conn, Transaction(
                    playerUuid = uuid,
                    type = TransactionType.PURCHASE,
                    amount = amount,
                    balanceBefore = account.balance,
                    balanceAfter = newBalance,
                    description = description,
                    sourceServer = config.serverName,
                    initiatedBy = pluginName
                ))

                val pending = pendingRepo.create(conn, PendingDelivery(
                    transactionId = txn.id!!,
                    playerUuid = uuid,
                    amount = amount,
                    deliveryPlugin = pluginName,
                    deliveryData = deliveryData,
                    status = DeliveryStatus.PENDING,
                    sourceServer = config.serverName
                ))

                ReserveOutcome.Reserved(account.copy(balance = newBalance), txn, pending.id!!)
            }
        } catch (e: Exception) {
            if (e.cause is DatabaseUnavailableException || e is DatabaseUnavailableException) {
                ReserveOutcome.Rejected(PurchaseResult.Error("Sistem şu an kullanılamıyor"))
            } else {
                logger.log(Level.SEVERE, "Purchase DB hatası: ${e.message}", e)
                ReserveOutcome.Rejected(PurchaseResult.Error("Veritabanı hatası"))
            }
        }
    }

    /** DELIVERED işaretlemesi kritik: başarısız olursa recovery iade edebilir, bu yüzden birkaç kez denenir. */
    private suspend fun markDelivered(pendingId: Long): Boolean {
        repeat(3) { attempt ->
            try {
                dbProvider.executeTransaction { conn ->
                    pendingRepo.updateStatus(conn, pendingId, DeliveryStatus.DELIVERED)
                }
                return true
            } catch (e: Exception) {
                logger.log(Level.WARNING, "markDelivered denemesi ${attempt + 1}/3 başarısız (pendingId: $pendingId): ${e.message}")
                delay(250L * (attempt + 1))
            }
        }
        return false
    }

    private suspend fun refundPending(pendingId: Long, uuid: UUID, amount: BigDecimal, pluginName: String): Boolean {
        val refunded = guard.lockFor(uuid).withLock {
            try {
                dbProvider.executeTransaction { conn ->
                    val account = accountRepo.findByUuidForUpdate(conn, uuid)
                        ?: throw IllegalStateException("Oyuncu hesabı bulunamadı ($uuid)")

                    val newBalance = account.balance + amount
                    accountRepo.updateBalance(conn, uuid, newBalance)

                    transactionRepo.create(conn, Transaction(
                        playerUuid = uuid,
                        type = TransactionType.REFUND,
                        amount = amount,
                        balanceBefore = account.balance,
                        balanceAfter = newBalance,
                        description = "Otomatik iade: teslim başarısız ($pluginName)",
                        sourceServer = config.serverName,
                        initiatedBy = "SYSTEM"
                    ))

                    pendingRepo.updateStatus(conn, pendingId, DeliveryStatus.REFUNDED)
                }
                true
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "İade yapılamadı (pendingId: $pendingId), kayıt PENDING bırakıldı: ${e.message}", e)
                false
            }
        }
        if (refunded) notifier.notifyChanged(uuid)
        return refunded
    }

    /** delivery_data sütunu JSON tipindedir; geçersiz veri INSERT'i patlatmasın diye normalize edilir. */
    private fun normalizeJson(data: String): String {
        if (data.isBlank()) return "{}"
        return try {
            JsonParser.parseString(data).toString()
        } catch (e: JsonParseException) {
            JsonPrimitive(data).toString()
        }
    }
}

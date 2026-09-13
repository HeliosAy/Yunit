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
import net.yaycraft.yunit.repository.IPendingDeliveryRepository
import net.yaycraft.yunit.repository.ITransactionRepository
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BooleanSupplier
import java.util.logging.Logger

class SafePurchaseServiceImpl(
    private val dbProvider: IDatabaseProvider,
    private val accountRepo: IAccountRepository,
    private val transactionRepo: ITransactionRepository,
    private val pendingRepo: IPendingDeliveryRepository,
    private val cache: IAccountCache,
    private val config: PluginConfig,
    private val logger: Logger
) : ISafePurchaseService {

    private val playerLocks = ConcurrentHashMap<UUID, Mutex>()
    private val lastPurchaseTime = ConcurrentHashMap<UUID, Long>()
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(config.database.poolSize)

    override suspend fun executePurchase(
        uuid: UUID, amount: BigDecimal, description: String,
        pluginName: String, deliveryData: String,
        deliveryAction: BooleanSupplier
    ): PurchaseResult = withContext(dbDispatcher) {
        
        if (amount <= BigDecimal.ZERO) 
            return@withContext PurchaseResult.Error("Geçersiz miktar")
        
        if (!dbProvider.isHealthy())
            return@withContext PurchaseResult.Error("Sistem şu an kullanılamıyor")
        
        val now = System.currentTimeMillis()
        val lastTime = lastPurchaseTime[uuid] ?: 0L
        if (now - lastTime < config.safety.transactionCooldownMs)
            return@withContext PurchaseResult.Error("Çok hızlı işlem, lütfen bekleyin")
        
        val mutex = playerLocks.computeIfAbsent(uuid) { Mutex() }
        val purchaseLockResult = mutex.withLock {
            lastPurchaseTime[uuid] = System.currentTimeMillis()
            
            val withdrawResult = try {
                dbProvider.executeTransaction { conn ->
                    val account = accountRepo.findByUuidForUpdate(conn, uuid)
                        ?: return@executeTransaction Triple(null, "Hesap bulunamadı", null)
                    
                    if (account.balance < amount) {
                        return@executeTransaction Triple(account, "INSUFFICIENT", null)
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
                    
                    cache.invalidate(uuid)
                    Triple(account.copy(balance = newBalance), txn, pending.id)
                }
            } catch (e: Exception) {
                logger.severe("Purchase DB hatası: ${e.message}")
                return@withLock PurchaseResult.Error("Veritabanı hatası")
            }
            
            val (updatedAccount, txnOrError, pendingId) = withdrawResult
            if (updatedAccount == null) return@withLock PurchaseResult.Error(txnOrError as String)
            if (txnOrError == "INSUFFICIENT") return@withLock PurchaseResult.InsufficientBalance(amount, updatedAccount.balance)
            
            Triple(updatedAccount, txnOrError as Transaction, pendingId as Long)
        }
        
        if (purchaseLockResult is PurchaseResult) {
            return@withContext purchaseLockResult
        }
        
        @Suppress("UNCHECKED_CAST")
        val (updatedAccount, transaction, pendingId) = purchaseLockResult as Triple<YunitAccount, Transaction, Long>
        
        val delivered = try {
            deliveryAction.asBoolean
        } catch (t: Throwable) {
            logger.log(java.util.logging.Level.SEVERE, "Delivery sırasında hata oluştu: ${t.message}", t)
            false
        }
        
        if (delivered) {
            try {
                markDelivered(pendingId)
            } catch (t: Throwable) {
                logger.log(java.util.logging.Level.SEVERE, "markDelivered hatası (pendingId: $pendingId): ${t.message}", t)
            }
            return@withContext PurchaseResult.Success(updatedAccount, transaction)
        } else {
            try {
                refundPending(pendingId, uuid, amount, pluginName)
                logger.info("Otomatik iade başarıyla tamamlandı: $uuid, pendingId: $pendingId, tutar: $amount")
            } catch (t: Throwable) {
                logger.log(java.util.logging.Level.SEVERE, "refundPending çağrısında hata (pendingId: $pendingId): ${t.message}", t)
            }
            return@withContext PurchaseResult.DeliveryFailed(amount, "Teslim başarısız, para iade edildi")
        }
    }
    
    private fun markDelivered(pendingId: Long) {
        dbProvider.executeTransaction { conn ->
            pendingRepo.updateStatus(conn, pendingId, DeliveryStatus.DELIVERED)
        }
    }
    
    private suspend fun refundPending(pendingId: Long, uuid: UUID, amount: BigDecimal, pluginName: String) {
        val mutex = playerLocks.computeIfAbsent(uuid) { Mutex() }
        mutex.withLock {
            try {
                dbProvider.executeTransaction { conn ->
                    val account = accountRepo.findByUuidForUpdate(conn, uuid)
                    if (account == null) {
                        logger.severe("refundPending: Oyuncu hesabı bulunamadı ($uuid)")
                        return@executeTransaction
                    }
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
                    cache.invalidate(uuid)
                }
            } catch (t: Throwable) {
                logger.log(java.util.logging.Level.SEVERE, "refundPending veritabanı transaction hatası: ${t.message}", t)
                throw t
            }
        }
    }
}

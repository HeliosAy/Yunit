package net.yaycraft.yunit.service

import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.database.IDatabaseProvider
import net.yaycraft.yunit.model.DeliveryStatus
import net.yaycraft.yunit.model.Transaction
import net.yaycraft.yunit.model.TransactionType
import net.yaycraft.yunit.repository.IAccountRepository
import net.yaycraft.yunit.repository.IPendingDeliveryRepository
import net.yaycraft.yunit.repository.ITransactionRepository
import java.util.logging.Logger

class StartupRecoveryServiceImpl(
    private val dbProvider: IDatabaseProvider,
    private val accountRepo: IAccountRepository,
    private val transactionRepo: ITransactionRepository,
    private val pendingRepo: IPendingDeliveryRepository,
    private val notifier: BalanceChangeNotifier,
    private val config: PluginConfig,
    private val logger: Logger
) : IRecoveryService {

    override fun recoverPendingDeliveries() {
        logger.info("Startup recovery: Yarım kalmış işlemler taranıyor (sunucu: ${config.serverName})...")

        try {
            val pendingList = dbProvider.executeTransaction { conn ->
                pendingRepo.findPendingByServer(conn, config.serverName)
            }

            if (pendingList.isEmpty()) {
                logger.info("Startup recovery: Yarım kalmış işlem bulunamadı ✓")
                return
            }

            if (!config.safety.recoveryAutoRefund) {
                logger.warning("Startup recovery: ${pendingList.size} yarım kalmış işlem var, otomatik iade KAPALI. Elle inceleyin:")
                pendingList.forEach {
                    logger.warning(" - pendingId=${it.id} oyuncu=${it.playerUuid} tutar=${it.amount} eklenti=${it.deliveryPlugin} veri=${it.deliveryData}")
                }
                return
            }

            logger.warning("Startup recovery: ${pendingList.size} yarım kalmış işlem bulundu! İade işlemi başlatılıyor...")

            for (pending in pendingList) {
                try {
                    val refunded = dbProvider.executeTransaction { conn ->
                        val account = accountRepo.findByUuidForUpdate(conn, pending.playerUuid)
                            ?: return@executeTransaction false

                        val newBalance = account.balance + pending.amount
                        accountRepo.updateBalance(conn, pending.playerUuid, newBalance)

                        transactionRepo.create(conn, Transaction(
                            playerUuid = pending.playerUuid,
                            type = TransactionType.REFUND,
                            amount = pending.amount,
                            balanceBefore = account.balance,
                            balanceAfter = newBalance,
                            description = "Crash recovery iade (txn:${pending.transactionId})",
                            sourceServer = config.serverName,
                            initiatedBy = "RECOVERY_SYSTEM"
                        ))

                        pendingRepo.updateStatus(conn, pending.id!!, DeliveryStatus.REFUNDED)
                        true
                    }

                    if (refunded) {
                        notifier.notifyChanged(pending.playerUuid)
                        logger.info("Recovery: ${pending.playerUuid} -> ${pending.amount} iade edildi ✓")
                    } else {
                        logger.severe("Recovery BAŞARISIZ (ID: ${pending.id}): oyuncu hesabı bulunamadı, kayıt PENDING bırakıldı.")
                    }
                } catch (e: Exception) {
                    logger.severe("Recovery BAŞARISIZ (ID: ${pending.id}): ${e.message}")
                }
            }
            logger.info("Startup recovery tamamlandı.")
        } catch (e: Exception) {
            logger.severe("Startup recovery listesi alınırken hata oluştu: ${e.message}")
        }
    }
}

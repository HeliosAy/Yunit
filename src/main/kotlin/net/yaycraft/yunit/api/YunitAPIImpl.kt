package net.yaycraft.yunit.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.database.IDatabaseProvider
import net.yaycraft.yunit.model.PurchaseResult
import net.yaycraft.yunit.model.TransactionResult
import net.yaycraft.yunit.model.TransactionType
import net.yaycraft.yunit.model.YunitAccount
import net.yaycraft.yunit.service.IEconomyService
import net.yaycraft.yunit.service.ISafePurchaseService
import net.yaycraft.yunit.util.format
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier

class YunitAPIImpl(
    private val economyService: IEconomyService,
    private val purchaseService: ISafePurchaseService,
    private val dbProvider: IDatabaseProvider,
    private val config: PluginConfig,
    private val scope: CoroutineScope
) : YunitAPI {

    override fun getBalance(uuid: UUID): CompletableFuture<BigDecimal> {
        return scope.future {
            economyService.getBalance(uuid)
        }
    }

    override fun getCachedBalance(uuid: UUID): BigDecimal? {
        return economyService.getCachedBalance(uuid)
    }

    override fun hasBalance(uuid: UUID, amount: BigDecimal): CompletableFuture<Boolean> {
        return scope.future {
            economyService.hasBalance(uuid, amount)
        }
    }

    override fun getAccount(uuid: UUID): CompletableFuture<YunitAccount?> {
        return scope.future {
            try {
                economyService.findAccount(uuid)
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun deposit(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String?, initiatedBy: String, idempotencyKey: String?
    ): CompletableFuture<TransactionResult> {
        return scope.future {
            economyService.deposit(uuid, amount, type, description, initiatedBy, idempotencyKey)
        }
    }

    override fun withdraw(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String?, initiatedBy: String, idempotencyKey: String?
    ): CompletableFuture<TransactionResult> {
        return scope.future {
            economyService.withdraw(uuid, amount, type, description, initiatedBy, idempotencyKey)
        }
    }

    override fun executePurchase(
        uuid: UUID, amount: BigDecimal, description: String,
        pluginName: String, deliveryData: String,
        deliveryAction: BooleanSupplier
    ): CompletableFuture<PurchaseResult> {
        return scope.future {
            purchaseService.executePurchase(
                uuid, amount, description, pluginName, deliveryData, deliveryAction
            )
        }
    }

    override fun isDatabaseHealthy(): Boolean {
        return dbProvider.isHealthy()
    }

    override fun getCurrencySymbol(): String = config.currencySymbol

    override fun getCurrencyName(): String = config.currencyName

    override fun formatAmount(amount: BigDecimal): String = amount.format()
}

package net.yaycraft.yunit.api

import net.yaycraft.yunit.model.PurchaseResult
import net.yaycraft.yunit.model.TransactionResult
import net.yaycraft.yunit.model.TransactionType
import net.yaycraft.yunit.model.YunitAccount
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier

interface YunitAPI {
    
    fun getBalance(uuid: UUID): CompletableFuture<BigDecimal>
    
    fun hasBalance(uuid: UUID, amount: BigDecimal): CompletableFuture<Boolean>
    
    fun getAccount(uuid: UUID): CompletableFuture<YunitAccount?>
    
    fun deposit(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String?, initiatedBy: String,
        idempotencyKey: String? = null
    ): CompletableFuture<TransactionResult>
    
    fun withdraw(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String?, initiatedBy: String,
        idempotencyKey: String? = null
    ): CompletableFuture<TransactionResult>
    
    fun executePurchase(
        uuid: UUID,
        amount: BigDecimal,
        description: String,
        pluginName: String,
        deliveryData: String,
        deliveryAction: BooleanSupplier
    ): CompletableFuture<PurchaseResult>
    
    fun isDatabaseHealthy(): Boolean
}

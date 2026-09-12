package net.yaycraft.yunit.model

import java.math.BigDecimal

sealed class PurchaseResult {
    data class Success(
        val account: YunitAccount,
        val transaction: Transaction
    ) : PurchaseResult()
    
    data class InsufficientBalance(
        val required: BigDecimal,
        val available: BigDecimal
    ) : PurchaseResult()
    
    data class DeliveryFailed(
        val refundedAmount: BigDecimal,
        val reason: String
    ) : PurchaseResult()
    
    data class Error(
        val message: String
    ) : PurchaseResult()
}

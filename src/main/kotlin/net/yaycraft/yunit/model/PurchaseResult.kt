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
    
    /**
     * Teslimat başarısız oldu.
     * [refunded] false ise iade o an yapılamadı; kayıt PENDING kaldı ve
     * sunucu yeniden başladığında recovery tarafından işlenecek.
     */
    data class DeliveryFailed(
        val refundedAmount: BigDecimal,
        val reason: String,
        val refunded: Boolean = true
    ) : PurchaseResult()
    
    data class Error(
        val message: String
    ) : PurchaseResult()
}

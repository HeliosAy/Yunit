package net.yaycraft.yunit.service

import net.yaycraft.yunit.model.PurchaseResult
import java.math.BigDecimal
import java.util.UUID

interface ISafePurchaseService {
    suspend fun executePurchase(
        uuid: UUID,
        amount: BigDecimal,
        description: String,
        pluginName: String,
        deliveryData: String,
        deliveryAction: () -> Boolean
    ): PurchaseResult
}

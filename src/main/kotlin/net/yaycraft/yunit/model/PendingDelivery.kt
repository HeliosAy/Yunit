package net.yaycraft.yunit.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PendingDelivery(
    val id: Long? = null,
    val transactionId: Long,
    val playerUuid: UUID,
    val amount: BigDecimal,
    val deliveryPlugin: String,
    val deliveryData: String,
    val status: DeliveryStatus,
    val sourceServer: String,
    val createdAt: Instant? = null,
    val resolvedAt: Instant? = null
)

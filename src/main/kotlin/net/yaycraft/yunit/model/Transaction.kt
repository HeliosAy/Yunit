package net.yaycraft.yunit.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Transaction(
    val id: Long? = null,
    val idempotencyKey: String? = null,
    val playerUuid: UUID,
    val type: TransactionType,
    val amount: BigDecimal,
    val balanceBefore: BigDecimal,
    val balanceAfter: BigDecimal,
    val description: String? = null,
    val sourceServer: String,
    val initiatedBy: String,
    val metadata: String? = null,
    val createdAt: Instant? = null
)

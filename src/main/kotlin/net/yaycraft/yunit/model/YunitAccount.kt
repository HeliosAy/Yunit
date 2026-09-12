package net.yaycraft.yunit.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class YunitAccount(
    val uuid: UUID,
    val username: String,
    val balance: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun hasBalance(amount: BigDecimal): Boolean = balance >= amount
}

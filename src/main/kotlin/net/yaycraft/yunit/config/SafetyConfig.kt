package net.yaycraft.yunit.config

data class SafetyConfig(
    val transactionCooldownMs: Long,
    val healthCheckIntervalSeconds: Long,
    val recoveryAutoRefund: Boolean
)

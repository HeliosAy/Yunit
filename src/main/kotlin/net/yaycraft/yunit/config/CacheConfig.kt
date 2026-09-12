package net.yaycraft.yunit.config

data class CacheConfig(
    val expireAfterWriteSeconds: Long,
    val maximumSize: Long
)

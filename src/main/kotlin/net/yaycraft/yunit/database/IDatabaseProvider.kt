package net.yaycraft.yunit.database

import net.yaycraft.yunit.config.DatabaseConfig
import java.sql.Connection

interface IDatabaseProvider {
    /** Connection pool'u başlatır */
    fun initialize(config: DatabaseConfig)

    /** Pool'dan bir connection alır (try-with-resources / use ile kullanılmalı) */
    fun getConnection(): Connection

    /** Tüm bağlantıları temiz şekilde kapatır */
    fun shutdown()

    /** Veritabanı çalışıyr mu kontrol eder */
    fun isHealthy(): Boolean

    /** Atomik transaction çalıştırır. Hata olursa otomatik ROLLBACK */
    fun <T> executeTransaction(block: (Connection) -> T): T
}

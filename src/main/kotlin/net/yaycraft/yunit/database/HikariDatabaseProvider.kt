package net.yaycraft.yunit.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.yaycraft.yunit.config.DatabaseConfig
import net.yaycraft.yunit.exception.DatabaseException
import net.yaycraft.yunit.exception.DatabaseUnavailableException
import java.sql.Connection
import java.util.logging.Level
import java.util.logging.Logger

class HikariDatabaseProvider(private val logger: Logger) : IDatabaseProvider {

    @Volatile
    private var dataSource: HikariDataSource? = null

    // Her işlemde bağlantı açıp test etmek yerine periyodik kontrolün sonucu tutulur.
    @Volatile
    private var healthy = false

    override fun initialize(config: DatabaseConfig) {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = buildString {
                append("jdbc:mysql://${config.host}:${config.port}/${config.database}")
                append("?useSSL=${config.useSSL}&characterEncoding=UTF-8")
                if (!config.useSSL) append("&allowPublicKeyRetrieval=true")
            }
            username = config.username
            password = config.password
            poolName = "Yunit-Pool"

            maximumPoolSize = config.poolSize
            minimumIdle = config.minimumIdle
            connectionTimeout = config.connectionTimeout
            idleTimeout = config.idleTimeout
            maxLifetime = config.maxLifetime

            // MySQL specific optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("useLocalSessionState", "true")
            addDataSourceProperty("rewriteBatchedStatements", "true")
            addDataSourceProperty("cacheResultSetMetadata", "true")
            addDataSourceProperty("cacheServerConfiguration", "true")
            addDataSourceProperty("elideSetAutoCommits", "true")
            addDataSourceProperty("maintainTimeStats", "false")
        }

        try {
            val ds = HikariDataSource(hikariConfig)
            dataSource = ds

            // Test connection
            ds.connection.use { conn ->
                healthy = conn.isValid(2)
            }
            logger.info("Veritabanı bağlantısı başarılı: ${config.host}:${config.port}/${config.database}")
        } catch (e: Exception) {
            healthy = false
            dataSource?.close()
            dataSource = null
            logger.severe("=====================================================")
            logger.severe("                YUNIT - KRITIK HATA                  ")
            logger.severe("=====================================================")
            logger.severe(" MySQL veritabanına bağlanılamadı! Eklenti durduruldu.")
            logger.severe(" Lütfen 'plugins/Yunit/config.yml' dosyasını acin")
            logger.severe(" ve veritabani bilgilerinizi (kullanici adi, sifre) ")
            logger.severe(" dogru girdiginizden emin olun.")
            logger.severe("-----------------------------------------------------")
            logger.severe(" Hata Detayi: ${e.message}")
            logger.severe("=====================================================")
            throw DatabaseException("Veritabanina baglanilamadi", e)
        }
    }

    override fun getConnection(): Connection {
        val ds = dataSource ?: throw IllegalStateException("DatabaseProvider henüz başlatılmadı!")
        return try {
            ds.connection
        } catch (e: Exception) {
            markUnhealthy(e)
            throw DatabaseUnavailableException(e)
        }
    }

    override fun shutdown() {
        healthy = false
        val ds = dataSource ?: return
        if (!ds.isClosed) {
            ds.close()
            logger.info("Veritabanı bağlantıları kapatıldı.")
        }
    }

    override fun isHealthy(): Boolean {
        val ds = dataSource ?: return false
        return healthy && !ds.isClosed
    }

    override fun checkHealth(): Boolean {
        val ds = dataSource
        if (ds == null || ds.isClosed) return false

        val valid = try {
            ds.connection.use { it.isValid(2) }
        } catch (e: Exception) {
            false
        }

        if (valid && !healthy) logger.info("Veritabanı bağlantısı tekrar sağlandı.")
        if (!valid && healthy) logger.severe("Veritabanı bağlantısı koptu! Ekonomi işlemleri geçici olarak durduruldu.")
        healthy = valid
        return valid
    }

    private fun markUnhealthy(e: Exception) {
        if (healthy) {
            logger.log(Level.SEVERE, "Veritabanından bağlantı alınamadı, işlemler durduruluyor: ${e.message}")
        }
        healthy = false
    }

    override fun <T> executeTransaction(block: (Connection) -> T): T {
        val connection = getConnection()
        val autoCommitOriginal = try {
            connection.autoCommit
        } catch (e: Exception) {
            connection.close()
            throw DatabaseException("Transaction başlatılamadı", e)
        }

        return try {
            connection.autoCommit = false
            val result = block(connection)
            connection.commit()
            result
        } catch (e: Exception) {
            try {
                connection.rollback()
            } catch (rollbackEx: Exception) {
                logger.log(Level.SEVERE, "Rollback sırasında hata oluştu", rollbackEx)
            }
            throw DatabaseException("Transaction hatası", e)
        } finally {
            try {
                connection.autoCommit = autoCommitOriginal
            } catch (ignored: Exception) {
            }
            try {
                connection.close()
            } catch (closeEx: Exception) {
                logger.log(Level.SEVERE, "Bağlantı kapatılırken hata oluştu", closeEx)
            }
        }
    }
}

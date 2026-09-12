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

    private var dataSource: HikariDataSource? = null
    private var isHealthy = false

    override fun initialize(config: DatabaseConfig) {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://${config.host}:${config.port}/${config.database}?useSSL=${config.useSSL}&characterEncoding=UTF-8"
            username = config.username
            password = config.password
            
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
            dataSource = HikariDataSource(hikariConfig)
            
            // Test connection
            dataSource!!.connection.use { conn ->
                isHealthy = conn.isValid(2)
            }
            logger.info("Veritabanı bağlantısı başarılı: ${config.host}:${config.port}/${config.database}")
        } catch (e: Exception) {
            isHealthy = false
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
            val conn = ds.connection
            isHealthy = true
            conn
        } catch (e: Exception) {
            isHealthy = false
            throw DatabaseUnavailableException()
        }
    }

    override fun shutdown() {
        if (dataSource != null && !dataSource!!.isClosed) {
            dataSource!!.close()
            logger.info("Veritabanı bağlantıları kapatıldı.")
        }
    }

    override fun isHealthy(): Boolean {

        if (dataSource == null || dataSource!!.isClosed) return false
        
        return try {
            dataSource!!.connection.use { conn ->
                val valid = conn.isValid(1)
                isHealthy = valid
                valid
            }
        } catch (e: Exception) {
            isHealthy = false
            false
        }
    }

    override fun <T> executeTransaction(block: (Connection) -> T): T {
        val connection = getConnection()
        return try {
            val autoCommitOriginal = connection.autoCommit
            connection.autoCommit = false
            
            val result = block(connection)
            
            connection.commit()
            connection.autoCommit = autoCommitOriginal
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
                connection.close()
            } catch (closeEx: Exception) {
                logger.log(Level.SEVERE, "Bağlantı kapatılırken hata oluştu", closeEx)
            }
        }
    }
}

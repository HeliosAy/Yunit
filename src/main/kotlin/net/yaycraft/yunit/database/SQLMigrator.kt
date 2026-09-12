package net.yaycraft.yunit.database

import java.util.logging.Level
import java.util.logging.Logger

class SQLMigrator(
    private val dbProvider: IDatabaseProvider,
    private val logger: Logger
) : IMigrationRunner {

    override fun migrate() {
        logger.info("Veritabanı migration kontrol ediliyor...")

        try {
            dbProvider.getConnection().use { connection ->
                // Migration tablosu var mı kontrol et, yoksa oluştur
                connection.createStatement().use { stmt ->
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS yunit_migrations (
                            version     INT             PRIMARY KEY,
                            name        VARCHAR(128)    NOT NULL,
                            checksum    VARCHAR(64)     NULL,
                            applied_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """.trimIndent())
                }

                // Mevcut versiyonu al
                var currentVersion = 0
                connection.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT MAX(version) FROM yunit_migrations")
                    if (rs.next()) {
                        currentVersion = rs.getInt(1)
                    }
                }

                // Bekleyen migrationları çalıştır
                for (migration in MIGRATIONS) {
                    if (migration.version > currentVersion) {
                        logger.info("Migration uygulanıyor: V${migration.version} - ${migration.name}")
                        dbProvider.executeTransaction { txConn ->
                            txConn.createStatement().use { stmt ->
                                // Batched statements
                                val queries = migration.sql.split(";").filter { it.isNotBlank() }
                                for (query in queries) {
                                    stmt.execute(query.trim())
                                }
                            }
                            
                            txConn.prepareStatement("INSERT INTO yunit_migrations (version, name) VALUES (?, ?)").use { stmt ->
                                stmt.setInt(1, migration.version)
                                stmt.setString(2, migration.name)
                                stmt.executeUpdate()
                            }
                        }
                        logger.info("Migration başarılı: V${migration.version}")
                    }
                }
                
                logger.info("Veritabanı güncel.")
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Migration sırasında kritik hata oluştu!", e)
            throw RuntimeException("Database migration failed", e)
        }
    }

    private data class Migration(val version: Int, val name: String, val sql: String)

    private companion object {
        private val MIGRATIONS = listOf(
            Migration(
                version = 1,
                name = "Initial Schema",
                sql = """
                    CREATE TABLE IF NOT EXISTS yunit_accounts (
                        uuid            CHAR(36)        PRIMARY KEY,
                        username        VARCHAR(16)     NOT NULL,
                        balance         DECIMAL(18,2)   NOT NULL DEFAULT 0.00,
                        created_at      TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        updated_at      TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                        
                        INDEX idx_username (username),
                        INDEX idx_balance (balance),
                        CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

                    CREATE TABLE IF NOT EXISTS yunit_transactions (
                        id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
                        idempotency_key     VARCHAR(64)     NULL,
                        player_uuid         CHAR(36)        NOT NULL,
                        type                VARCHAR(32)     NOT NULL,
                        amount              DECIMAL(18,2)   NOT NULL,
                        balance_before      DECIMAL(18,2)   NOT NULL,
                        balance_after       DECIMAL(18,2)   NOT NULL,
                        description         VARCHAR(255)    NULL,
                        source_server       VARCHAR(64)     NOT NULL,
                        initiated_by        VARCHAR(64)     NOT NULL,
                        metadata            JSON            NULL,
                        created_at          TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        
                        UNIQUE INDEX idx_idempotency (idempotency_key),
                        INDEX idx_player_uuid (player_uuid),
                        INDEX idx_player_type (player_uuid, type),
                        INDEX idx_created_at (created_at),
                        FOREIGN KEY (player_uuid) REFERENCES yunit_accounts(uuid)
                            ON DELETE RESTRICT ON UPDATE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

                    CREATE TABLE IF NOT EXISTS yunit_pending_deliveries (
                        id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
                        transaction_id      BIGINT          NOT NULL,
                        player_uuid         CHAR(36)        NOT NULL,
                        amount              DECIMAL(18,2)   NOT NULL,
                        delivery_plugin     VARCHAR(64)     NOT NULL,
                        delivery_data       JSON            NOT NULL,
                        status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
                        source_server       VARCHAR(64)     NOT NULL,
                        created_at          TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        resolved_at         TIMESTAMP(3)    NULL,
                        
                        INDEX idx_status (status),
                        INDEX idx_player (player_uuid),
                        INDEX idx_server_status (source_server, status),
                        FOREIGN KEY (transaction_id) REFERENCES yunit_transactions(id),
                        FOREIGN KEY (player_uuid) REFERENCES yunit_accounts(uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """.trimIndent()
            )
        )
    }
}

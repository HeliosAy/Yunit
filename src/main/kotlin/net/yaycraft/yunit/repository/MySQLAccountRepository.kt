package net.yaycraft.yunit.repository

import net.yaycraft.yunit.model.YunitAccount
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class MySQLAccountRepository : IAccountRepository {

    override fun findByUuid(connection: Connection, uuid: UUID): YunitAccount? {
        val query = "SELECT * FROM yunit_accounts WHERE uuid = ?"
        connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, uuid.toString())
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return mapRowToAccount(rs)
                }
            }
        }
        return null
    }

    override fun findByUuidForUpdate(connection: Connection, uuid: UUID): YunitAccount? {
        val query = "SELECT * FROM yunit_accounts WHERE uuid = ? FOR UPDATE"
        connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, uuid.toString())
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return mapRowToAccount(rs)
                }
            }
        }
        return null
    }

    override fun create(connection: Connection, uuid: UUID, username: String): YunitAccount {
        val query = "INSERT INTO yunit_accounts (uuid, username, balance) VALUES (?, ?, ?)"
        connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, uuid.toString())
            stmt.setString(2, username)
            stmt.setBigDecimal(3, BigDecimal.ZERO)
            stmt.executeUpdate()
        }
        return findByUuid(connection, uuid) ?: throw IllegalStateException("Hesap oluşturuldu ama DB'den okunamadı")
    }

    override fun updateBalance(connection: Connection, uuid: UUID, newBalance: BigDecimal) {
        val query = "UPDATE yunit_accounts SET balance = ? WHERE uuid = ?"
        connection.prepareStatement(query).use { stmt ->
            stmt.setBigDecimal(1, newBalance)
            stmt.setString(2, uuid.toString())
            stmt.executeUpdate()
        }
    }

    override fun updateUsername(connection: Connection, uuid: UUID, username: String) {
        val query = "UPDATE yunit_accounts SET username = ? WHERE uuid = ?"
        connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, username)
            stmt.setString(2, uuid.toString())
            stmt.executeUpdate()
        }
    }

    override fun exists(connection: Connection, uuid: UUID): Boolean {
        val query = "SELECT 1 FROM yunit_accounts WHERE uuid = ?"
        connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, uuid.toString())
            stmt.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    override fun findTopAccounts(connection: Connection, limit: Int): List<YunitAccount> {
        val query = "SELECT * FROM yunit_accounts ORDER BY balance DESC LIMIT ?"
        val list = mutableListOf<YunitAccount>()
        connection.prepareStatement(query).use { stmt ->
            stmt.setInt(1, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(mapRowToAccount(rs))
                }
            }
        }
        return list
    }

    private fun mapRowToAccount(rs: ResultSet): YunitAccount {
        return YunitAccount(
            uuid = UUID.fromString(rs.getString("uuid")),
            username = rs.getString("username"),
            balance = rs.getBigDecimal("balance"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }
}

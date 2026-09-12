package net.yaycraft.yunit.repository

import net.yaycraft.yunit.model.Transaction
import net.yaycraft.yunit.model.TransactionType
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.util.UUID

class MySQLTransactionRepository : ITransactionRepository {

    override fun create(connection: Connection, transaction: Transaction): Transaction {
        val query = """
            INSERT INTO yunit_transactions 
            (idempotency_key, player_uuid, type, amount, balance_before, balance_after, description, source_server, initiated_by, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setString(1, transaction.idempotencyKey)
            stmt.setString(2, transaction.playerUuid.toString())
            stmt.setString(3, transaction.type.name)
            stmt.setBigDecimal(4, transaction.amount)
            stmt.setBigDecimal(5, transaction.balanceBefore)
            stmt.setBigDecimal(6, transaction.balanceAfter)
            stmt.setString(7, transaction.description)
            stmt.setString(8, transaction.sourceServer)
            stmt.setString(9, transaction.initiatedBy)
            stmt.setString(10, transaction.metadata)
            
            stmt.executeUpdate()
            
            stmt.generatedKeys.use { rs ->
                if (rs.next()) {
                    val id = rs.getLong(1)
                    return transaction.copy(id = id)
                }
            }
        }
        throw IllegalStateException("Transaction oluşturuldu ama ID alınamadı")
    }

    override fun existsByIdempotencyKey(connection: Connection, key: String): Boolean {
        val query = "SELECT 1 FROM yunit_transactions WHERE idempotency_key = ?"
        connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    override fun findByPlayer(connection: Connection, uuid: UUID, limit: Int, offset: Int): List<Transaction> {
        val query = "SELECT * FROM yunit_transactions WHERE player_uuid = ? ORDER BY created_at DESC LIMIT ? OFFSET ?"
        val list = mutableListOf<Transaction>()
        connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, uuid.toString())
            stmt.setInt(2, limit)
            stmt.setInt(3, offset)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs))
                }
            }
        }
        return list
    }

    override fun findRecentByPlayer(connection: Connection, uuid: UUID, count: Int): List<Transaction> {
        return findByPlayer(connection, uuid, count, 0)
    }
    
    private fun mapRowToTransaction(rs: ResultSet): Transaction {
        return Transaction(
            id = rs.getLong("id"),
            idempotencyKey = rs.getString("idempotency_key"),
            playerUuid = UUID.fromString(rs.getString("player_uuid")),
            type = TransactionType.valueOf(rs.getString("type")),
            amount = rs.getBigDecimal("amount"),
            balanceBefore = rs.getBigDecimal("balance_before"),
            balanceAfter = rs.getBigDecimal("balance_after"),
            description = rs.getString("description"),
            sourceServer = rs.getString("source_server"),
            initiatedBy = rs.getString("initiated_by"),
            metadata = rs.getString("metadata"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}

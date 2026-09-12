package net.yaycraft.yunit.repository

import net.yaycraft.yunit.model.DeliveryStatus
import net.yaycraft.yunit.model.PendingDelivery
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.util.UUID

class MySQLPendingDeliveryRepository : IPendingDeliveryRepository {

    override fun create(connection: Connection, delivery: PendingDelivery): PendingDelivery {
        val query = """
            INSERT INTO yunit_pending_deliveries 
            (transaction_id, player_uuid, amount, delivery_plugin, delivery_data, status, source_server)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setLong(1, delivery.transactionId)
            stmt.setString(2, delivery.playerUuid.toString())
            stmt.setBigDecimal(3, delivery.amount)
            stmt.setString(4, delivery.deliveryPlugin)
            stmt.setString(5, delivery.deliveryData)
            stmt.setString(6, delivery.status.name)
            stmt.setString(7, delivery.sourceServer)
            
            stmt.executeUpdate()
            
            stmt.generatedKeys.use { rs ->
                if (rs.next()) {
                    val id = rs.getLong(1)
                    return delivery.copy(id = id)
                }
            }
        }
        throw IllegalStateException("Pending Delivery oluşturuldu ama ID alınamadı")
    }

    override fun updateStatus(connection: Connection, id: Long, status: DeliveryStatus) {
        val query = if (status != DeliveryStatus.PENDING) {
            "UPDATE yunit_pending_deliveries SET status = ?, resolved_at = CURRENT_TIMESTAMP(3) WHERE id = ?"
        } else {
            "UPDATE yunit_pending_deliveries SET status = ?, resolved_at = NULL WHERE id = ?"
        }
        
        connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, status.name)
            stmt.setLong(2, id)
            stmt.executeUpdate()
        }
    }

    override fun findPendingByServer(connection: Connection, server: String): List<PendingDelivery> {
        val query = "SELECT * FROM yunit_pending_deliveries WHERE source_server = ? AND status = 'PENDING'"
        val list = mutableListOf<PendingDelivery>()
        connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, server)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(mapRowToPendingDelivery(rs))
                }
            }
        }
        return list
    }

    override fun findPendingByPlayer(connection: Connection, uuid: UUID): List<PendingDelivery> {
        val query = "SELECT * FROM yunit_pending_deliveries WHERE player_uuid = ? AND status = 'PENDING'"
        val list = mutableListOf<PendingDelivery>()
        connection.prepareStatement(query).use { stmt ->
            stmt.setString(1, uuid.toString())
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(mapRowToPendingDelivery(rs))
                }
            }
        }
        return list
    }

    private fun mapRowToPendingDelivery(rs: ResultSet): PendingDelivery {
        return PendingDelivery(
            id = rs.getLong("id"),
            transactionId = rs.getLong("transaction_id"),
            playerUuid = UUID.fromString(rs.getString("player_uuid")),
            amount = rs.getBigDecimal("amount"),
            deliveryPlugin = rs.getString("delivery_plugin"),
            deliveryData = rs.getString("delivery_data"),
            status = DeliveryStatus.valueOf(rs.getString("status")),
            sourceServer = rs.getString("source_server"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            resolvedAt = rs.getTimestamp("resolved_at")?.toInstant()
        )
    }
}

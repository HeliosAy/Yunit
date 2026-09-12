package net.yaycraft.yunit.repository

import net.yaycraft.yunit.model.Transaction
import java.sql.Connection
import java.util.UUID

interface ITransactionRepository {
    fun create(connection: Connection, transaction: Transaction): Transaction
    
    fun existsByIdempotencyKey(connection: Connection, key: String): Boolean
    
    fun findByPlayer(connection: Connection, uuid: UUID, limit: Int, offset: Int): List<Transaction>
    
    fun findRecentByPlayer(connection: Connection, uuid: UUID, count: Int): List<Transaction>
}

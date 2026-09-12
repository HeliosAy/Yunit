package net.yaycraft.yunit.repository

import net.yaycraft.yunit.model.YunitAccount
import java.math.BigDecimal
import java.sql.Connection
import java.util.UUID

interface IAccountRepository {
    fun findByUuid(connection: Connection, uuid: UUID): YunitAccount?
    
    fun findByUuidForUpdate(connection: Connection, uuid: UUID): YunitAccount?
    
    fun create(connection: Connection, uuid: UUID, username: String): YunitAccount
    
    fun updateBalance(connection: Connection, uuid: UUID, newBalance: BigDecimal)
    
    fun updateUsername(connection: Connection, uuid: UUID, username: String)
    
    fun exists(connection: Connection, uuid: UUID): Boolean
    
    fun findTopAccounts(connection: Connection, limit: Int): List<YunitAccount>
}

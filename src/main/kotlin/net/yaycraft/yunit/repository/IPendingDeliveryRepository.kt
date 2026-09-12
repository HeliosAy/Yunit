package net.yaycraft.yunit.repository

import net.yaycraft.yunit.model.DeliveryStatus
import net.yaycraft.yunit.model.PendingDelivery
import java.sql.Connection
import java.util.UUID

interface IPendingDeliveryRepository {
    /** Pending delivery kaydı oluştur */
    fun create(connection: Connection, delivery: PendingDelivery): PendingDelivery
    
    /** Status güncelle */
    fun updateStatus(connection: Connection, id: Long, status: DeliveryStatus)
    
    /** Bu sunucudaki tüm PENDING kayıtları getir (startup recovery) */
    fun findPendingByServer(connection: Connection, server: String): List<PendingDelivery>
    
    /** Oyuncunun pending kayıtlarını getir */
    fun findPendingByPlayer(connection: Connection, uuid: UUID): List<PendingDelivery>
}

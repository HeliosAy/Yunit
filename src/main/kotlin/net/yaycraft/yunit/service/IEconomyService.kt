package net.yaycraft.yunit.service

import net.yaycraft.yunit.model.Transaction
import net.yaycraft.yunit.model.TransactionResult
import net.yaycraft.yunit.model.TransactionType
import net.yaycraft.yunit.model.YunitAccount
import java.math.BigDecimal
import java.util.UUID

interface IEconomyService {
    /** Bakiye sorgula (cache-first, gösterim amaçlı) */
    suspend fun getBalance(uuid: UUID): BigDecimal
    
    /** Yeterli bakiye var mı? (GÖSTERIM AMAÇLI - satın alma kararında SAKIN kullanma!) */
    suspend fun hasBalance(uuid: UUID, amount: BigDecimal): Boolean
    
    /** Bakiye ekle (atomik, thread-safe) */
    suspend fun deposit(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String? = null, initiatedBy: String,
        idempotencyKey: String? = null
    ): TransactionResult
    
    /** Bakiye çek (atomik, thread-safe, bakiye kontrolü) */
    suspend fun withdraw(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String? = null, initiatedBy: String,
        idempotencyKey: String? = null
    ): TransactionResult
    
    /** Bakiye ayarla (Admin) */
    suspend fun setBalance(
        uuid: UUID, amount: BigDecimal, initiatedBy: String
    ): TransactionResult
    
    /** Hesap getir/oluştur */
    suspend fun getOrCreateAccount(uuid: UUID, username: String): YunitAccount
    
    /** İşlem geçmişi */
    suspend fun getTransactionHistory(uuid: UUID, page: Int, pageSize: Int): List<Transaction>
    
    /** Sıralama */
    suspend fun getTopAccounts(limit: Int): List<YunitAccount>
}

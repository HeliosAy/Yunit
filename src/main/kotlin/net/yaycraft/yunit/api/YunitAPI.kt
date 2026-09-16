package net.yaycraft.yunit.api

import net.yaycraft.yunit.model.PurchaseResult
import net.yaycraft.yunit.model.TransactionResult
import net.yaycraft.yunit.model.TransactionType
import net.yaycraft.yunit.model.YunitAccount
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier

/**
 * Yunit ekonomi API'si.
 *
 * Tüm CompletableFuture'lar Yunit'in arka plan thread'lerinde tamamlanır.
 * Sonuçta Bukkit API kullanacaksanız (mesaj dışı: envanter, dünya vb.)
 * `Bukkit.getScheduler().runTask(...)` ile ana thread'e geçin.
 */
interface YunitAPI {

    /** Bakiye (gösterim amaçlı, önbellekten gelebilir). Hesap yoksa 0. */
    fun getBalance(uuid: UUID): CompletableFuture<BigDecimal>

    /** Önbellekteki bakiye; veritabanına gitmez, ana thread'de güvenle çağrılabilir. Önbellekte yoksa null. */
    fun getCachedBalance(uuid: UUID): BigDecimal?

    /** GÖSTERİM AMAÇLI. Satın alma kararında kullanmayın; [executePurchase] veya [withdraw] zaten kontrol eder. */
    fun hasBalance(uuid: UUID, amount: BigDecimal): CompletableFuture<Boolean>

    /** Hesabı getirir, yoksa null. Hesap oluşturmaz, veriyi değiştirmez. */
    fun getAccount(uuid: UUID): CompletableFuture<YunitAccount?>

    fun deposit(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String?, initiatedBy: String,
        idempotencyKey: String? = null
    ): CompletableFuture<TransactionResult>

    fun withdraw(
        uuid: UUID, amount: BigDecimal, type: TransactionType,
        description: String?, initiatedBy: String,
        idempotencyKey: String? = null
    ): CompletableFuture<TransactionResult>

    /**
     * Güvenli satın alma: para çekilir > [deliveryAction] çalışır > false dönerse/hata atarsa para otomatik iade edilir.
     *
     * ÖNEMLİ:
     * - [deliveryAction] Yunit'in **arka plan (IO) thread'inde** çağrılır, ana thread'de DEĞİL.
     *   Envanter vb. Bukkit işlemleri için `Bukkit.getScheduler().callSyncMethod(...)` ile ana thread'e geçip
     *   sonucu (zaman aşımıyla) bekleyin.
     * - [deliveryData] JSON olmalıdır (ör. `{"item":"stone"}`). Geçersizse JSON string'e çevrilerek saklanır.
     * - [description] en fazla 255, [pluginName] en fazla 64 karakter saklanır.
     */
    fun executePurchase(
        uuid: UUID,
        amount: BigDecimal,
        description: String,
        pluginName: String,
        deliveryData: String,
        deliveryAction: BooleanSupplier
    ): CompletableFuture<PurchaseResult>

    /** Son veritabanı sağlık kontrolünün sonucu */
    fun isDatabaseHealthy(): Boolean

    /** config.yml -> currency.symbol */
    fun getCurrencySymbol(): String

    /** config.yml > currency.name */
    fun getCurrencyName(): String

    /** Yunit'in standart para formatı, örn: 1.500,50 */
    fun formatAmount(amount: BigDecimal): String
}

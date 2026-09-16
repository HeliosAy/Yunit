package net.yaycraft.yunit.command

import net.yaycraft.yunit.service.IEconomyService
import org.bukkit.Bukkit
import java.util.UUID

data class ResolvedPlayer(val uuid: UUID, val name: String)

/**
 * Komutlarda yazılan ismi güvenli şekilde UUID'ye çevirir.
 *
 * `Bukkit.getOfflinePlayer(name)` KULLANILMAZ: offline (crack) sunucuda isimden UUID üretir ve
 * büyük/küçük harf farkı ("Ali" / "ali") ya da yazım hatası sessizce yanlış/hayali bir hesap açar.
 *
 * Sıra: çevrimiçi oyuncu -> Yunit veritabanındaki hesap -> sunucunun usercache kaydı.
 * Hiçbirinde yoksa null döner (oyuncu sunucuya hiç girmemiş).
 */
class PlayerResolver(private val economyService: IEconomyService) {

    /** Bukkit tarafındaki bilgiler; ana thread'de toplanır. */
    class Lookup internal constructor(
        val input: String,
        internal val online: ResolvedPlayer?,
        internal val cached: ResolvedPlayer?
    )

    /** ANA THREAD'de çağrılmalı. */
    fun prepare(input: String): Lookup {
        val online = Bukkit.getPlayerExact(input)?.let { ResolvedPlayer(it.uniqueId, it.name) }
        val cached = if (online == null) {
            Bukkit.getOfflinePlayerIfCached(input)?.let { ResolvedPlayer(it.uniqueId, it.name ?: input) }
        } else null
        return Lookup(input, online, cached)
    }

    /** Arka planda çağrılır (veritabanı sorgusu yapabilir). */
    suspend fun resolve(lookup: Lookup): ResolvedPlayer? {
        lookup.online?.let { return it }
        economyService.findAccountByName(lookup.input)?.let { return ResolvedPlayer(it.uuid, it.username) }
        return lookup.cached
    }
}

package net.yaycraft.yunit.cache

import net.yaycraft.yunit.model.YunitAccount
import java.util.UUID

interface IAccountCache {
    fun get(uuid: UUID): YunitAccount?
    fun put(uuid: UUID, account: YunitAccount)
    fun invalidate(uuid: UUID)
    fun invalidateAll()
    fun stats(): String
}

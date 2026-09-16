package net.yaycraft.yunit.api

object YunitProvider {
    @Volatile
    private var api: YunitAPI? = null

    fun register(api: YunitAPI) {
        this.api = api
    }

    fun unregister() {
        this.api = null
    }

    /** API yüklü değilse null döner */
    fun getOrNull(): YunitAPI? = api

    fun get(): YunitAPI {
        return api ?: throw IllegalStateException(
            "Yunit API henüz yüklenmedi! Plugin yükleme sırasını kontrol edin (depend: [Yunit])"
        )
    }
}

package net.yaycraft.yunit.api

object YunitProvider {
    private var api: YunitAPI? = null
    
    fun register(api: YunitAPI) {
        this.api = api
    }
    
    fun unregister() {
        this.api = null
    }
    
    fun get(): YunitAPI {
        return api ?: throw IllegalStateException(
            "Yunit API henüz yüklenmedi! Plugin yükleme sırasını kontrol edin (depend: [Yunit])"
        )
    }
}

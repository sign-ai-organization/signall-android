package com.signall.app.data

import org.json.JSONObject

/**
 * Lo que el backend de SignAll dice en su `/health`.
 *
 * No confirma que el host sea *un* backend cualquiera: confirma que sea **el
 * nuestro**. El descubrimiento automático prueba decenas de direcciones de la
 * red local y un puerto 8080 abierto puede ser un router, una impresora o la
 * máquina de otra persona. Por eso se exigen los campos propios de SignAll
 * (`cache_loaded`, `catalog_loaded`, `catalog_glosses`), no solo un `200`.
 *
 * [isHealthy] es más estricto todavía y es lo que decide si se abre el socket:
 * `status == "ok"` **y** catálogo cargado. Un backend degradado contesta, pero
 * no puede traducir: todas las glosas saldrían como no disponibles, así que
 * aceptarlo como descubrimiento sería encontrar algo que no sirve.
 */
data class SignAllHealth(
    val status: String,
    val cacheLoaded: Boolean,
    val cacheRows: Int,
    val catalogLoaded: Boolean,
    val catalogGlosses: Int,
) {
    val isHealthy: Boolean
        get() = status == "ok" && catalogLoaded && catalogGlosses > 0
}

/**
 * Lee el `/health` del backend sin lanzar: cualquier cuerpo que no sea el JSON
 * de SignAll devuelve `null`.
 *
 * Se exige que existan los campos, no solo que el JSON sea válido. Eso es lo
 * que separa "respondió algo" de "respondió SignAll".
 */
object SignAllHealthParser {

    fun parse(body: String?): SignAllHealth? {
        if (body.isNullOrBlank()) return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val status = json.opt("status") as? String ?: return null
        val cacheLoaded = json.opt("cache_loaded") as? Boolean ?: return null
        val catalogLoaded = json.opt("catalog_loaded") as? Boolean ?: return null
        val catalogGlosses = (json.opt("catalog_glosses") as? Number)?.toInt() ?: return null

        return SignAllHealth(
            status = status,
            cacheLoaded = cacheLoaded,
            cacheRows = (json.opt("cache_rows") as? Number)?.toInt() ?: 0,
            catalogLoaded = catalogLoaded,
            catalogGlosses = catalogGlosses,
        )
    }
}

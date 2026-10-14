package com.despeja.app.scanner

import android.content.Context
import com.despeja.app.model.FileItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Guarda hashes y análisis de fotos entre escaneos. La clave incluye tamaño y fecha,
 * así que si un archivo cambia se vuelve a calcular. Solo se conservan las entradas
 * usadas en el último escaneo, de modo que el archivo no crece sin límite.
 */
class HashCache(context: Context) {
    private val file = File(context.filesDir, "hash_cache.tsv")
    private val stored = ConcurrentHashMap<String, String>()
    private val used = ConcurrentHashMap<String, String>()

    @Synchronized
    fun load() {
        stored.clear()
        used.clear()
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                val i = line.indexOf('\t')
                if (i > 0) stored[line.substring(0, i)] = line.substring(i + 1)
            }
        }
    }

    fun key(prefix: String, item: FileItem) = "$prefix|${item.path}|${item.size}|${item.lastModified}"

    fun get(key: String): String? = stored[key]?.also { used[key] = it }

    fun put(key: String, value: String) {
        stored[key] = value
        used[key] = value
    }

    @Synchronized
    fun save() {
        runCatching {
            file.bufferedWriter().use { w ->
                used.forEach { (k, v) ->
                    w.write(k); w.write("\t"); w.write(v); w.newLine()
                }
            }
        }
    }
}

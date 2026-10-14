package com.despeja.app.scanner

import com.despeja.app.model.FileGroup
import com.despeja.app.model.FileItem
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Encuentra duplicados exactos en tres pasadas, de la más barata a la más cara:
 * 1) mismo tamaño, 2) mismo hash de los primeros 64 KB, 3) mismo MD5 completo.
 * Así casi nunca hay que leer un archivo entero.
 */
class DuplicateFinder(private val cache: HashCache) {

    data class Result(val groups: List<FileGroup>, val keepers: Set<String>)

    suspend fun find(files: List<FileItem>, onProgress: (Float) -> Unit): Result {
        val bySize = files
            .filter { it.size >= ScanConfig.MIN_DUPLICATE_BYTES }
            .groupBy { it.size }
            .values
            .filter { it.size > 1 }

        val total = bySize.sumOf { it.size }.coerceAtLeast(1)
        var done = 0
        val groups = ArrayList<FileGroup>()
        val keepers = HashSet<String>()

        for (sameSize in bySize) {
            coroutineContext.ensureActive()

            val byPartial = sameSize.groupBy { partialHash(it) }.filterKeys { it != null }.values.filter { it.size > 1 }
            for (candidates in byPartial) {
                // Si el archivo cabe entero en el hash parcial, ya es un duplicado exacto.
                val confirmed = if (candidates.first().size <= ScanConfig.PARTIAL_HASH_BYTES) {
                    listOf(candidates)
                } else {
                    candidates.groupBy { fullHash(it) }.filterKeys { it != null }.values.filter { it.size > 1 }
                }
                for (dupes in confirmed) {
                    // Se conserva el más antiguo: suele ser el original.
                    val sorted = dupes.sortedBy { it.lastModified }
                    keepers += sorted.first().path
                    groups += FileGroup(id = "dup-${groups.size}", files = sorted)
                }
            }
            done += sameSize.size
            onProgress(done.toFloat() / total)
        }

        groups.sortByDescending { g -> g.files.drop(1).sumOf { it.size } }
        return Result(groups, keepers)
    }

    private fun partialHash(item: FileItem): String? = runCatching {
        FileInputStream(File(item.path)).use { input ->
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(ScanConfig.PARTIAL_HASH_BYTES)
            var read = 0
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            md.update(buffer, 0, read)
            md.digest().toHex()
        }
    }.getOrNull()

    private fun fullHash(item: FileItem): String? {
        val key = cache.key("md5", item)
        cache.get(key)?.let { return it }
        return runCatching {
            FileInputStream(File(item.path)).use { input ->
                val md = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    md.update(buffer, 0, n)
                }
                md.digest().toHex()
            }
        }.getOrNull()?.also { cache.put(key, it) }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

package com.despeja.app.system

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.despeja.app.model.TrashEntry
import com.despeja.app.scanner.ScanConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Papelera propia: los archivos se mueven a una carpeta oculta (mismo volumen, así que
 * es un simple renombrado instantáneo) y pueden restaurarse durante 30 días.
 * El espacio se libera al vaciarla o cuando caduca cada archivo.
 */
class TrashManager(private val context: Context) {

    private val dir: File get() = File(Environment.getExternalStorageDirectory(), ScanConfig.TRASH_DIR_NAME)
    private val index: File get() = File(dir, "index.tsv")

    @Synchronized
    fun list(): List<TrashEntry> {
        if (!index.exists()) return emptyList()
        return runCatching {
            index.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size != 4) return@mapNotNull null
                TrashEntry(p[0], p[1], p[2].toLongOrNull() ?: 0L, p[3].toLongOrNull() ?: 0L)
            }.filter { File(dir, it.id).exists() }
        }.getOrDefault(emptyList()).sortedByDescending { it.deletedAt }
    }

    @Synchronized
    fun moveToTrash(file: File): Boolean {
        if (!ensureDir()) return false
        val id = UUID.randomUUID().toString()
        val target = File(dir, id)
        val size = file.length()
        val moved = file.renameTo(target) || runCatching {
            file.copyTo(target, overwrite = true)
            file.delete()
        }.getOrDefault(false)
        if (moved) {
            index.appendText("$id\t${file.absolutePath}\t${System.currentTimeMillis()}\t$size\n")
        }
        return moved
    }

    @Synchronized
    fun restore(entry: TrashEntry): Boolean {
        val stored = File(dir, entry.id)
        var dest = File(entry.originalPath)
        if (dest.exists()) {
            val base = dest.nameWithoutExtension
            val ext = dest.extension.let { if (it.isEmpty()) "" else ".$it" }
            dest = File(dest.parentFile, "$base (restaurado)$ext")
        }
        dest.parentFile?.mkdirs()
        val ok = stored.renameTo(dest)
        if (ok) {
            rewriteIndex(list().filterNot { it.id == entry.id })
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
        }
        return ok
    }

    @Synchronized
    fun deleteForever(entries: List<TrashEntry>): Long {
        var freed = 0L
        val ids = entries.map { it.id }.toSet()
        entries.forEach { e ->
            val f = File(dir, e.id)
            val len = f.length()
            if (f.delete()) freed += len
        }
        rewriteIndex(list().filterNot { it.id in ids })
        return freed
    }

    fun empty(): Long = deleteForever(list())

    fun purgeExpired(): Long {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ScanConfig.TRASH_RETENTION_DAYS.toLong())
        return deleteForever(list().filter { it.deletedAt < cutoff })
    }

    private fun ensureDir(): Boolean {
        if (!dir.exists() && !dir.mkdirs()) return false
        // Evita que la galería muestre lo que hay en la papelera.
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) runCatching { noMedia.createNewFile() }
        return true
    }

    private fun rewriteIndex(entries: List<TrashEntry>) {
        if (!dir.exists()) return
        runCatching {
            index.writeText(entries.joinToString("") { "${it.id}\t${it.originalPath}\t${it.deletedAt}\t${it.size}\n" })
        }
    }
}

/** Borra (o manda a la papelera) y avisa a la galería para que no queden miniaturas fantasma. */
class FileOps(private val context: Context, private val trash: TrashManager) {

    data class Outcome(val removed: Set<String>, val bytes: Long, val failed: Int)

    fun remove(paths: Collection<String>, useTrash: Boolean): Outcome {
        val removed = HashSet<String>()
        var bytes = 0L
        var failed = 0
        for (path in paths) {
            val f = File(path)
            if (!f.exists()) { removed += path; continue }
            val len = f.length()
            val ok = if (useTrash) trash.moveToTrash(f) else f.delete()
            if (ok) { removed += path; bytes += len } else failed++
        }
        if (removed.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, removed.toTypedArray(), null, null)
        }
        return Outcome(removed, bytes, failed)
    }
}

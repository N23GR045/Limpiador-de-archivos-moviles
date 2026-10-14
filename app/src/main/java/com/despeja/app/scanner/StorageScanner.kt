package com.despeja.app.scanner

import android.content.Context
import android.os.Environment
import com.despeja.app.model.Category
import com.despeja.app.model.CategoryResult
import com.despeja.app.model.FileGroup
import com.despeja.app.model.FileItem
import com.despeja.app.model.FileType
import com.despeja.app.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Recorre el almacenamiento interno compartido y clasifica lo encontrado. */
class StorageScanner(private val context: Context, private val cache: HashCache) {

    enum class Phase(val label: String) {
        WALKING("Recorriendo carpetas"),
        DUPLICATES("Buscando duplicados"),
        IMAGES("Analizando fotos"),
        DONE("Terminando"),
    }

    data class Progress(
        val phase: Phase,
        val filesFound: Int,
        val bytesFound: Long,
        /** null = progreso indeterminado (mientras se recorren carpetas). */
        val fraction: Float?,
        val currentPath: String = "",
    )

    suspend fun scan(onProgress: (Progress) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        cache.load()

        val root = Environment.getExternalStorageDirectory()
        val all = ArrayList<FileItem>(20_000)
        val temp = ArrayList<FileItem>()
        var bytes = 0L

        // 1) Recorrido iterativo (sin recursión para no desbordar la pila en árboles profundos)
        val skipped = ScanConfig.SKIPPED_RELATIVE_DIRS.map { File(root, it).absolutePath }.toSet()
        val stack = ArrayDeque<Pair<File, Boolean>>() // carpeta, ¿está dentro de una carpeta temporal?
        stack.addLast(root to false)
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (dir, inTemp) = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (f.isDirectory) {
                    if (f.absolutePath in skipped) continue
                    val isTempDir = inTemp || f.name.lowercase() in ScanConfig.TEMP_DIR_NAMES
                    stack.addLast(f to isTempDir)
                } else {
                    val item = FileItem(f.absolutePath, f.name, f.length(), f.lastModified())
                    all += item
                    bytes += item.size
                    if (inTemp || isTempFile(item)) temp += item
                    if (all.size % 250 == 0) {
                        onProgress(Progress(Phase.WALKING, all.size, bytes, null, dir.absolutePath))
                    }
                }
            }
        }

        // La caché propia de la app también cuenta como temporal.
        listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { dir ->
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                temp += FileItem(f.absolutePath, f.name, f.length(), f.lastModified())
            }
        }

        // 2) Categorías que salen directamente de los metadatos
        val large = all
            .filter { it.size >= ScanConfig.LARGE_FILE_BYTES || (it.extension in ScanConfig.INSTALLER_EXTENSIONS && it.size > 0) }
            .sortedByDescending { it.size }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/"
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ScanConfig.OLD_DOWNLOAD_DAYS.toLong())
        val oldDownloads = all
            .filter { it.path.startsWith(downloadsDir) && it.lastModified in 1 until cutoff }
            .sortedByDescending { it.size }

        val breakdown = all.groupBy { it.type }.mapValues { (_, l) -> l.sumOf { it.size } }

        // 3) Duplicados exactos
        onProgress(Progress(Phase.DUPLICATES, all.size, bytes, 0f))
        val dupes = DuplicateFinder(cache).find(all.filterNot { isHidden(it) }) { f ->
            onProgress(Progress(Phase.DUPLICATES, all.size, bytes, f))
        }

        // 4) Fotos borrosas y similares
        onProgress(Progress(Phase.IMAGES, all.size, bytes, 0f))
        val images = all.filter { it.type == FileType.IMAGES && !isHidden(it) && it.extension != "gif" }
        val img = ImageAnalyzer(cache).analyze(images) { f ->
            onProgress(Progress(Phase.IMAGES, all.size, bytes, f))
        }

        onProgress(Progress(Phase.DONE, all.size, bytes, 1f))
        cache.save()

        fun flat(c: Category, files: List<FileItem>) =
            CategoryResult(c, if (files.isEmpty()) emptyList() else listOf(FileGroup(c.name, files)))

        val categories = mapOf(
            Category.DUPLICATES to CategoryResult(Category.DUPLICATES, dupes.groups, dupes.keepers),
            Category.LARGE to flat(Category.LARGE, large),
            Category.TEMP to flat(Category.TEMP, temp.sortedByDescending { it.size }),
            Category.OLD_DOWNLOADS to flat(Category.OLD_DOWNLOADS, oldDownloads),
            Category.BLURRY to flat(Category.BLURRY, img.blurry),
            Category.SIMILAR to CategoryResult(Category.SIMILAR, img.similarGroups, img.similarKeepers),
        )

        ScanResult(
            categories = categories,
            breakdown = breakdown,
            scannedFiles = all.size,
            scannedBytes = bytes,
            durationMs = System.currentTimeMillis() - start,
            finishedAt = System.currentTimeMillis(),
        )
    }

    private fun isTempFile(item: FileItem): Boolean =
        item.extension in ScanConfig.TEMP_EXTENSIONS || item.name.startsWith("~")

    /** Archivos dentro de carpetas ocultas (.thumbnails, .trash...) no cuentan como fotos ni duplicados. */
    private fun isHidden(item: FileItem): Boolean = item.path.contains("/.")
}

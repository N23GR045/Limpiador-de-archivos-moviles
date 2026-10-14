package com.despeja.app.model

/** Un archivo encontrado durante el escaneo. */
data class FileItem(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val folder: String get() = path.substringBeforeLast('/')
    val type: FileType get() = FileType.fromExtension(extension)
}

/** Categorías de limpieza. [autoSelect] decide la selección recomendada al abrirla. */
enum class Category(
    val title: String,
    val description: String,
    val grouped: Boolean,
    val autoSelect: AutoSelect,
) {
    DUPLICATES("Duplicados", "Archivos con contenido idéntico", true, AutoSelect.ALL_BUT_KEEPER),
    LARGE("Archivos grandes", "Vídeos pesados, APKs e instaladores", false, AutoSelect.NONE),
    TEMP("Temporales y caché", "Archivos .tmp, logs, miniaturas y caché", false, AutoSelect.ALL),
    OLD_DOWNLOADS("Descargas antiguas", "Sin tocar desde hace más de 90 días", false, AutoSelect.NONE),
    BLURRY("Fotos borrosas", "Imágenes desenfocadas o movidas", false, AutoSelect.NONE),
    SIMILAR("Fotos similares", "Ráfagas y fotos casi iguales", true, AutoSelect.ALL_BUT_KEEPER),
}

enum class AutoSelect { NONE, ALL, ALL_BUT_KEEPER }

/** Conjunto de archivos relacionados (copias idénticas o fotos parecidas). */
data class FileGroup(val id: String, val files: List<FileItem>)

data class CategoryResult(
    val category: Category,
    val groups: List<FileGroup>,
    /** Archivo que se recomienda conservar en cada grupo (el original o la foto más nítida). */
    val keepers: Set<String> = emptySet(),
) {
    val files: List<FileItem> = groups.flatMap { it.files }
    val count: Int = files.size
    val totalSize: Long = files.sumOf { it.size }

    /** Espacio que se liberaría con la selección recomendada (o todo, si no hay recomendación). */
    val reclaimable: Long = when (category.autoSelect) {
        AutoSelect.ALL_BUT_KEEPER -> files.filter { it.path !in keepers }.sumOf { it.size }
        else -> totalSize
    }

    val defaultSelection: Set<String> = when (category.autoSelect) {
        AutoSelect.NONE -> emptySet()
        AutoSelect.ALL -> files.mapTo(HashSet()) { it.path }
        AutoSelect.ALL_BUT_KEEPER -> files.filter { it.path !in keepers }.mapTo(HashSet()) { it.path }
    }

    /** Quita archivos ya borrados; los grupos que se quedan con menos de 2 desaparecen. */
    fun without(removed: Set<String>): CategoryResult {
        val newGroups = groups.mapNotNull { g ->
            val left = g.files.filter { it.path !in removed }
            when {
                left.isEmpty() -> null
                category.grouped && left.size < 2 -> null
                else -> g.copy(files = left)
            }
        }
        return copy(groups = newGroups, keepers = keepers - removed)
    }
}

data class ScanResult(
    val categories: Map<Category, CategoryResult>,
    val breakdown: Map<FileType, Long>,
    val scannedFiles: Int,
    val scannedBytes: Long,
    val durationMs: Long,
    val finishedAt: Long,
) {
    fun without(removed: Set<String>): ScanResult =
        copy(categories = categories.mapValues { it.value.without(removed) })
}

enum class FileType(val label: String, val color: Long) {
    IMAGES("Imágenes", 0xFF2E9E7A),
    VIDEOS("Vídeos", 0xFFE0703A),
    AUDIO("Audio", 0xFF8B6CD9),
    DOCUMENTS("Documentos", 0xFF3B82C4),
    APKS("APKs", 0xFFD9A62E),
    ARCHIVES("Comprimidos", 0xFFC2507A),
    OTHER("Otros", 0xFF7A8A86);

    companion object {
        private val map: Map<String, FileType> = buildMap {
            listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng").forEach { put(it, IMAGES) }
            listOf("mp4", "mkv", "mov", "avi", "3gp", "webm", "m4v").forEach { put(it, VIDEOS) }
            listOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "amr").forEach { put(it, AUDIO) }
            listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "csv", "epub").forEach { put(it, DOCUMENTS) }
            listOf("apk", "xapk", "apks", "apkm").forEach { put(it, APKS) }
            listOf("zip", "rar", "7z", "tar", "gz").forEach { put(it, ARCHIVES) }
        }

        fun fromExtension(ext: String): FileType = map[ext] ?: OTHER
    }
}

data class StorageSummary(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class AppCacheInfo(val packageName: String, val label: String, val cacheBytes: Long)

data class TrashEntry(val id: String, val originalPath: String, val deletedAt: Long, val size: Long) {
    val name: String get() = originalPath.substringAfterLast('/')
}

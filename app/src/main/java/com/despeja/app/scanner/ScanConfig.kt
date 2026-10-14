package com.despeja.app.scanner

/** Todos los umbrales en un sitio para ajustarlos fácilmente. */
object ScanConfig {
    /** A partir de este tamaño un archivo cuenta como "grande". */
    const val LARGE_FILE_BYTES = 100L * 1024 * 1024

    /** Extensiones de instaladores que se listan siempre como grandes, pesen lo que pesen. */
    val INSTALLER_EXTENSIONS = setOf("apk", "xapk", "apks", "apkm", "exe", "msi", "dmg", "iso")

    /** Días sin modificar para que una descarga se considere antigua. */
    const val OLD_DOWNLOAD_DAYS = 90

    /** Los duplicados más pequeños que esto se ignoran (iconos, .nomedia, etc.). */
    const val MIN_DUPLICATE_BYTES = 4L * 1024

    /** Bytes leídos para el hash parcial previo al hash completo. */
    const val PARTIAL_HASH_BYTES = 64 * 1024

    /** Lado mayor (px) al que se reduce cada foto para analizarla. */
    const val IMAGE_ANALYSIS_SIZE = 400

    /** Fotos por debajo de este lado (px) se ignoran: iconos, stickers... */
    const val MIN_IMAGE_DIMENSION = 320

    /** Varianza del Laplaciano por debajo de la cual una foto se considera borrosa. */
    const val BLUR_THRESHOLD = 80.0

    /** Distancia de Hamming máxima entre dHash para considerar dos fotos similares. */
    const val SIMILAR_MAX_DISTANCE = 8

    /** Tope de fotos analizadas (las más recientes) para que el escaneo no se eternice. */
    const val MAX_IMAGES_ANALYZED = 4000

    /** Días que un archivo permanece en la papelera antes de borrarse solo. */
    const val TRASH_RETENTION_DAYS = 30

    const val TRASH_DIR_NAME = ".PapeleraDespeja"

    /** Carpetas que Android no deja leer o que no conviene tocar. */
    val SKIPPED_RELATIVE_DIRS = setOf("Android/data", "Android/obb", TRASH_DIR_NAME)

    val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "part", "crdownload", "partial")
    val TEMP_DIR_NAMES = setOf(".thumbnails", "cache", ".cache", "temp", ".temp", "tmp", ".tmp")
}

package com.despeja.app.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.despeja.app.model.FileGroup
import com.despeja.app.model.FileItem
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Analiza fotos sin librerías externas:
 * - Nitidez: varianza del Laplaciano sobre la imagen en gris. Poca varianza = pocos bordes = borrosa.
 * - Similitud: dHash de 64 bits (gradientes horizontales en una miniatura de 9x8).
 *   Dos fotos con hashes a poca distancia de Hamming son casi iguales.
 */
class ImageAnalyzer(private val cache: HashCache) {

    data class Analysis(val item: FileItem, val sharpness: Double, val dHash: Long)

    data class Result(
        val blurry: List<FileItem>,
        val similarGroups: List<FileGroup>,
        val similarKeepers: Set<String>,
    )

    suspend fun analyze(images: List<FileItem>, onProgress: (Float) -> Unit): Result {
        val targets = images
            .sortedByDescending { it.lastModified }
            .take(ScanConfig.MAX_IMAGES_ANALYZED)
        if (targets.isEmpty()) return Result(emptyList(), emptyList(), emptySet())

        val threads = max(2, Runtime.getRuntime().availableProcessors() / 2)
        val executor = Executors.newFixedThreadPool(threads)
        val dispatcher = executor.asCoroutineDispatcher()
        val done = AtomicInteger(0)

        val analyses: List<Analysis> = try {
            coroutineScope {
                targets.chunked((targets.size + threads - 1) / threads).map { chunk ->
                    async(dispatcher) {
                        chunk.mapNotNull { item ->
                            ensureActive()
                            val a = analyzeOne(item)
                            val n = done.incrementAndGet()
                            if (n % 10 == 0) onProgress(n.toFloat() / targets.size)
                            a
                        }
                    }
                }.awaitAll().flatten()
            }
        } finally {
            dispatcher.close()
        }

        val blurry = analyses
            .filter { it.sharpness < ScanConfig.BLUR_THRESHOLD }
            .sortedBy { it.sharpness }
            .map { it.item }

        val (groups, keepers) = groupSimilar(analyses)
        return Result(blurry, groups, keepers)
    }

    private fun analyzeOne(item: FileItem): Analysis? {
        val key = cache.key("img", item)
        cache.get(key)?.let { cached ->
            val parts = cached.split(',')
            if (parts.size == 2) {
                val s = parts[0].toDoubleOrNull()
                val h = parts[1].toLongOrNull()
                if (s != null && h != null) return Analysis(item, s, h)
            }
            if (cached == "skip") return null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(item.path, bounds)
        if (bounds.outWidth < ScanConfig.MIN_IMAGE_DIMENSION || bounds.outHeight < ScanConfig.MIN_IMAGE_DIMENSION) {
            cache.put(key, "skip")
            return null
        }

        var sample = 1
        val target = ScanConfig.IMAGE_ANALYSIS_SIZE
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = runCatching { BitmapFactory.decodeFile(item.path, opts) }.getOrNull() ?: return null

        return try {
            val scaled = scaleToMaxSide(bmp, target)
            val sharpness = laplacianVariance(scaled)
            if (scaled !== bmp) scaled.recycle()

            val tiny = Bitmap.createScaledBitmap(bmp, 9, 8, true)
            val hash = dHash(tiny)
            if (tiny !== bmp) tiny.recycle()

            cache.put(key, "$sharpness,$hash")
            Analysis(item, sharpness, hash)
        } catch (e: Exception) {
            null
        } finally {
            bmp.recycle()
        }
    }

    private fun scaleToMaxSide(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val ratio = maxSide.toFloat() / longest
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun gray(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun laplacianVariance(bmp: Bitmap): Double {
        val w = bmp.width
        val h = bmp.height
        if (w < 3 || h < 3) return Double.MAX_VALUE
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val g = gray(px)

        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val lap = (g[i - 1] + g[i + 1] + g[i - w] + g[i + w] - 4 * g[i]).toDouble()
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    private fun dHash(bmp9x8: Bitmap): Long {
        val px = IntArray(9 * 8)
        bmp9x8.getPixels(px, 0, 9, 0, 0, 9, 8)
        val g = gray(px)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (g[y * 9 + x] > g[y * 9 + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** Agrupa con union-find las fotos cuyo dHash difiere en pocos bits. */
    private fun groupSimilar(list: List<Analysis>): Pair<List<FileGroup>, Set<String>> {
        val n = list.size
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }
            return r
        }
        for (i in 0 until n) {
            val hi = list[i].dHash
            for (j in i + 1 until n) {
                if (java.lang.Long.bitCount(hi xor list[j].dHash) <= ScanConfig.SIMILAR_MAX_DISTANCE) {
                    val a = find(i); val b = find(j)
                    if (a != b) parent[a] = b
                }
            }
        }

        val clusters = (0 until n).groupBy { find(it) }.values.filter { it.size > 1 }
        val keepers = HashSet<String>()
        val groups = clusters.mapIndexed { idx, members ->
            // Se conserva la más nítida; el resto se propone para borrar.
            val sorted = members.map { list[it] }.sortedByDescending { it.sharpness }
            keepers += sorted.first().item.path
            FileGroup("sim-$idx", sorted.map { it.item })
        }.sortedByDescending { g -> g.files.drop(1).sumOf { it.size } }

        return groups to keepers
    }
}

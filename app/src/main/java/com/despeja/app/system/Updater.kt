package com.despeja.app.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.despeja.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Actualizaciones conectadas con GitHub Releases:
 * 1) consulta la última versión publicada por el workflow,
 * 2) descarga el APK dentro de la app (con progreso),
 * 3) abre el instalador de Android, que lo instala encima conservando los datos.
 */
object Updater {
    private const val REPO = "N23GR045/Limpiador-de-archivos-moviles"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    val currentBuild: Int get() = BuildConfig.VERSION_CODE
    val currentName: String get() = BuildConfig.VERSION_NAME

    sealed interface Result {
        data class Available(
            val build: Int,
            val apkUrl: String,
            val sizeBytes: Long,
            /** Novedades escritas en la versión de GitHub (puede estar vacío). */
            val notes: String,
        ) : Result
        data object UpToDate : Result
        data class Error(val reason: String) : Result
    }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        try {
            val conn = open(API).apply { setRequestProperty("Accept", "application/vnd.github+json") }
            val code = conn.responseCode
            if (code != 200) return@withContext Result.Error("GitHub respondió $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()

            val body = json.optString("body")
            val remoteBuild = Regex("""build:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""compilaci[oó]n\s+(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@withContext Result.Error("No se encontró el número de versión")

            // Si hay varios APK (p. ej. con el nombre antiguo), se usa el subido más recientemente.
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var size = 0L
            var newest = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val updated = a.optString("updated_at")
                    if (a.optString("name").endsWith(".apk") && updated > newest) {
                        newest = updated
                        apkUrl = a.optString("browser_download_url")
                        size = a.optLong("size")
                    }
                }
            }

            if (remoteBuild > currentBuild && apkUrl != null) {
                Result.Available(remoteBuild, apkUrl, size, extractNotes(body))
            } else {
                Result.UpToDate
            }
        } catch (e: Exception) {
            Result.Error("Sin conexión con GitHub")
        }
    }

    /** Las novedades van entre "Novedades:" y "build:" en la descripción de la versión. */
    private fun extractNotes(body: String): String {
        val start = body.indexOf("Novedades:")
        if (start < 0) return ""
        val end = body.indexOf("build:", start).let { if (it < 0) body.length else it }
        return body.substring(start + "Novedades:".length, end)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Co-Authored-By") && !it.startsWith("Claude-Session") }
            .joinToString("\n")
            .trim()
    }

    /** Descarga el APK a la caché de la app informando del progreso (0..1). */
    suspend fun download(
        ctx: Context,
        info: Result.Available,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // borra descargas anteriores
        val target = File(dir, "EspacioLibre-${info.build}.apk")
        val tmp = File(dir, "${target.name}.part")

        val conn = open(info.apkUrl) // GitHub redirige al almacenamiento de archivos; se sigue solo
        if (conn.responseCode != 200) throw IllegalStateException("La descarga respondió ${conn.responseCode}")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastReported = -1
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != lastReported) { lastReported = pct; onProgress(done.toFloat() / total) }
                    }
                }
            }
        }
        conn.disconnect()
        if (info.sizeBytes > 0 && tmp.length() != info.sizeBytes) {
            tmp.delete()
            throw IllegalStateException("La descarga llegó incompleta")
        }
        tmp.renameTo(target)
        target
    }

    /** ¿Tiene la app permiso de Android para instalar actualizaciones? */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Abre los ajustes "Instalar apps desconocidas" de esta app. */
    fun openInstallPermission(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Lanza el instalador del sistema con el APK descargado. */
    fun install(ctx: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }
        ctx.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "EspacioLibre-Android")
        }
}

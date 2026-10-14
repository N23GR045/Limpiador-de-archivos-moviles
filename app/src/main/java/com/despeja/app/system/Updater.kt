package com.despeja.app.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.despeja.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Busca versiones nuevas en las Releases de GitHub. El workflow escribe "build: N"
 * en la descripción de cada versión; si N es mayor que la versión instalada, hay actualización.
 */
object Updater {
    private const val REPO = "N23GR045/Limpiador-de-archivos-moviles"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    val currentBuild: Int get() = BuildConfig.VERSION_CODE
    val currentName: String get() = BuildConfig.VERSION_NAME

    sealed interface Result {
        data class Available(val build: Int, val apkUrl: String, val sizeBytes: Long) : Result
        data object UpToDate : Result
        data class Error(val reason: String) : Result
    }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "EspacioLibre-Android")
            }
            val code = conn.responseCode
            if (code != 200) return@withContext Result.Error("GitHub respondió $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()

            val body = json.optString("body")
            val remoteBuild = Regex("""build:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""compilaci[oó]n\s+(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@withContext Result.Error("No se encontró el número de versión")

            val assets = json.optJSONArray("assets")
            // Si hay varios APK (p. ej. con el nombre antiguo), se usa el subido más recientemente.
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

            if (remoteBuild > currentBuild && apkUrl != null) Result.Available(remoteBuild, apkUrl, size)
            else Result.UpToDate
        } catch (e: Exception) {
            Result.Error("Sin conexión con GitHub")
        }
    }

    /** Abre la descarga del APK en el navegador; Android ofrece instalarlo al terminar. */
    fun openDownload(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

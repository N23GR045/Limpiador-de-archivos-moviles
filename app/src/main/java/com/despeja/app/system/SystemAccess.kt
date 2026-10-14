package com.despeja.app.system

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.despeja.app.model.AppCacheInfo
import com.despeja.app.model.StorageSummary

object Permissions {

    /** ¿Puede la app leer y borrar en todo el almacenamiento compartido? */
    fun hasStorageAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    val legacyPermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    /** Abre la pantalla "Acceso a todos los archivos" de esta app (Android 11+). */
    fun openAllFilesAccess(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val specific = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${ctx.packageName}"),
        )
        val generic = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        startSafely(ctx, specific) || startSafely(ctx, generic)
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccess(ctx: Context) {
        val specific = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        startSafely(ctx, specific) || startSafely(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openAppDetails(ctx: Context, packageName: String) {
        startSafely(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun startSafely(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}

object StorageInfo {
    fun summary(): StorageSummary {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageSummary(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    }
}

object AppCaches {

    /** Tamaño de la caché de cada app instalada. Requiere "Acceso a datos de uso". */
    fun query(ctx: Context): List<AppCacheInfo> {
        if (!Permissions.hasUsageAccess(ctx)) return emptyList()
        val ssm = ctx.getSystemService(StorageStatsManager::class.java) ?: return emptyList()
        val pm = ctx.packageManager
        val apps: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        return apps.mapNotNull { ai ->
            try {
                val stats = ssm.queryStatsForPackage(ai.storageUuid, ai.packageName, Process.myUserHandle())
                AppCacheInfo(ai.packageName, pm.getApplicationLabel(ai).toString(), stats.cacheBytes)
            } catch (e: Exception) {
                null
            }
        }.filter { it.cacheBytes > 0 }.sortedByDescending { it.cacheBytes }
    }

    /**
     * Intent del sistema que vacía la caché de TODAS las apps tras confirmación del usuario.
     * Solo Android 11+ y solo si la app tiene "Acceso a todos los archivos".
     */
    fun clearAllIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent(StorageManager.ACTION_CLEAR_APP_CACHE) else null
}

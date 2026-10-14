package com.despeja.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.despeja.app.system.Updater
import com.despeja.app.ui.formatSize

@Composable
fun UpdateDialog(
    result: Updater.Result,
    onDownload: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (result) {
        is Updater.Result.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
            title = { Text("Hay una versión nueva") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tienes la ${Updater.currentName}. Ya está disponible la 1.0.${result.build}.")
                    Text(
                        "Se descargará el APK (${formatSize(result.sizeBytes)}). Al terminar, ábrelo y pulsa «Actualizar». " +
                            "Tus datos y la papelera se conservan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { Button(onClick = { onDownload(result.apkUrl) }) { Text("Descargar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Más tarde") } },
        )

        Updater.Result.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Estás al día") },
            text = { Text("Tienes la última versión (${Updater.currentName}).") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Vale") } },
        )

        is Updater.Result.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("No se pudo comprobar") },
            text = { Text("${result.reason}. Revisa tu conexión a internet e inténtalo de nuevo.") },
            confirmButton = { TextButton(onClick = onRetry) { Text("Reintentar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        )
    }
}

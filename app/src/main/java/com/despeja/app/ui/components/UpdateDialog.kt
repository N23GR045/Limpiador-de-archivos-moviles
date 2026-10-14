package com.despeja.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.despeja.app.system.Updater
import com.despeja.app.ui.formatSize

@Composable
fun UpdateDialog(
    result: Updater.Result,
    downloadProgress: Float?,
    downloaded: Boolean,
    needsInstallPermission: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = downloadProgress != null
    when (result) {
        is Updater.Result.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnClickOutside = !downloading, dismissOnBackPress = !downloading),
            icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
            title = { Text(if (downloaded) "Actualización descargada" else "Versión 1.0.${result.build} disponible") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Tienes la ${Updater.currentName}. Se descarga directamente desde GitHub " +
                            "(${formatSize(result.sizeBytes)}) y se instala encima: tus datos y la papelera se conservan.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (result.notes.isNotBlank()) {
                        Text("Novedades", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Column(
                            Modifier
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                result.notes.lines().joinToString("\n") { line ->
                                    val l = line.trimStart('-', '*', ' ')
                                    "• $l"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when {
                        downloading -> {
                            LinearProgressIndicator(
                                progress = { downloadProgress ?: 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Descargando… ${((downloadProgress ?: 0f) * 100).toInt()} %",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        needsInstallPermission -> Text(
                            "Android pide permiso: activa «Permitir de esta fuente» para Espacio Libre y vuelve. " +
                                "La instalación empezará sola.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        downloaded -> Text(
                            "Pulsa «Instalar» y después «Actualizar» en la ventana de Android.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    downloading -> TextButton(onClick = onCancelDownload) { Text("Cancelar descarga") }
                    downloaded -> Button(onClick = onInstall) { Text("Instalar") }
                    else -> Button(onClick = onDownload) { Text("Descargar e instalar") }
                }
            },
            dismissButton = {
                if (!downloading) TextButton(onClick = onDismiss) { Text("Más tarde") }
            },
        )

        Updater.Result.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Estás al día") },
            text = { Text("Tienes la última versión publicada en GitHub (${Updater.currentName}).") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Vale") } },
        )

        is Updater.Result.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("No se pudo actualizar") },
            text = { Text("${result.reason}. Revisa tu conexión a internet e inténtalo de nuevo.") },
            confirmButton = { TextButton(onClick = onRetry) { Text("Reintentar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        )
    }
}

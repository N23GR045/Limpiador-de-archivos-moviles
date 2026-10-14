package com.despeja.app.ui.components

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.despeja.app.model.FileItem
import com.despeja.app.model.FileType
import com.despeja.app.ui.daysAgo
import com.despeja.app.ui.formatSize
import java.io.File

/**
 * Visor a pantalla completa: reproduce vídeos con los controles de Android
 * (play/pausa, barra de progreso) y muestra fotos a tamaño grande.
 * Desde aquí también se puede marcar o desmarcar el archivo.
 */
@Composable
fun MediaPreview(
    file: FileItem,
    checked: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            when (file.type) {
                FileType.VIDEOS -> VideoPlayer(file.path, Modifier.fillMaxSize())
                else -> AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(File(file.path)).build(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Barra superior: nombre, datos y cerrar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .safeDrawingPadding()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(file.name, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatSize(file.size)} · ${daysAgo(file.lastModified)} · ${file.folder.substringAfter("/emulated/0")}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Cerrar", tint = Color.White) }
            }

            // Barra inferior: marcar para limpiar
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .safeDrawingPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(uncheckedColor = Color.White),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (checked) "Marcado para limpiar" else "Marcar para limpiar",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun VideoPlayer(path: String, modifier: Modifier) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoPath(path)
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                        controller.show(3000)
                    }
                    view = this
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    DisposableEffect(Unit) {
        onDispose { view?.stopPlayback() }
    }
}


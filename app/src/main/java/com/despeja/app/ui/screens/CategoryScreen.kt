package com.despeja.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.despeja.app.model.AutoSelect
import com.despeja.app.model.Category
import com.despeja.app.model.CategoryResult
import com.despeja.app.model.FileItem
import com.despeja.app.model.FileType
import com.despeja.app.ui.daysAgo
import com.despeja.app.ui.formatSize
import com.despeja.app.ui.plural
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    category: Category,
    result: CategoryResult?,
    selection: Set<String>,
    working: Boolean,
    snackbarHost: @Composable () -> Unit,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onSelectRecommended: () -> Unit,
    onDelete: (useTrash: Boolean) -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val files = result?.files.orEmpty()
    val selectedFiles = remember(files, selection) { files.filter { it.path in selection } }
    val selectedBytes = selectedFiles.sumOf { it.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(category.title, style = MaterialTheme.typography.titleLarge)
                        if (result != null) {
                            Text(
                                "${plural(result.count, "archivo")} · ${formatSize(result.totalSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Button(
                        onClick = { confirm = true },
                        enabled = selectedFiles.isNotEmpty() && !working,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        if (working) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(
                                if (selectedFiles.isEmpty()) "Marca archivos para limpiar"
                                else "Limpiar ${plural(selectedFiles.size, "archivo")} · ${formatSize(selectedBytes)}",
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        if (result == null || files.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nada que limpiar aquí ✨", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Hint(category)
                SelectionBar(
                    category = category,
                    selected = selectedFiles.size,
                    total = files.size,
                    onSelectAll = onSelectAll,
                    onSelectRecommended = onSelectRecommended,
                )
            }

            if (category.grouped) {
                result.groups.forEachIndexed { idx, group ->
                    item(key = "h-${group.id}") {
                        val extra = group.files.drop(1).sumOf { it.size }
                        Text(
                            "Grupo ${idx + 1} · ${group.files.size} ${if (category == Category.DUPLICATES) "copias" else "fotos"} · " +
                                "${formatSize(extra)} sobrantes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                        )
                    }
                    items(group.files, key = { "${group.id}/${it.path}" }) { f ->
                        FileRow(
                            file = f,
                            checked = f.path in selection,
                            badge = when {
                                f.path !in result.keepers -> null
                                category == Category.DUPLICATES -> "Original"
                                else -> "Más nítida"
                            },
                            onToggle = { onToggle(f.path) },
                        )
                    }
                }
            } else {
                items(files, key = { it.path }) { f ->
                    FileRow(file = f, checked = f.path in selection, badge = null, onToggle = { onToggle(f.path) })
                }
            }
        }
    }

    if (confirm) {
        ConfirmDeleteDialog(
            category = category,
            result = result,
            selectedFiles = selectedFiles,
            selection = selection,
            selectedBytes = selectedBytes,
            onDismiss = { confirm = false },
            onConfirm = { useTrash ->
                confirm = false
                onDelete(useTrash)
            },
        )
    }
}

@Composable
private fun Hint(category: Category) {
    val text = when (category) {
        Category.DUPLICATES -> "Se ha marcado todo salvo el original (el más antiguo) de cada grupo."
        Category.LARGE -> "Archivos de más de 100 MB e instaladores. Nada viene marcado: elige tú."
        Category.TEMP -> "Archivos que las apps regeneran solas. Seguros de borrar."
        Category.OLD_DOWNLOADS -> "Descargas sin modificar en más de 90 días. Revisa antes de borrar."
        Category.BLURRY -> "Detección automática: revisa las miniaturas antes de borrar."
        Category.SIMILAR -> "Se conserva la foto más nítida de cada grupo y se marcan las demás."
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SelectionBar(
    category: Category,
    selected: Int,
    total: Int,
    onSelectAll: (Boolean) -> Unit,
    onSelectRecommended: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$selected de $total",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        )
        if (category.autoSelect != AutoSelect.NONE) {
            TextButton(onClick = onSelectRecommended) { Text("Recomendado") }
        }
        TextButton(onClick = { onSelectAll(selected < total) }) {
            Text(if (selected < total) "Todos" else "Ninguno")
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun FileRow(file: FileItem, checked: Boolean, badge: String?, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Thumbnail(file)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${file.folder.substringAfter("/emulated/0").ifEmpty { "/" }} · ${daysAgo(file.lastModified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatSize(file.size),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun Thumbnail(file: FileItem) {
    val shape = RoundedCornerShape(10.dp)
    val mod = Modifier
        .size(48.dp)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (file.type == FileType.IMAGES) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(file.path))
                .size(144)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = mod,
        )
    } else {
        val icon = when (file.type) {
            FileType.VIDEOS -> Icons.Outlined.Movie
            FileType.AUDIO -> Icons.Outlined.AudioFile
            FileType.DOCUMENTS -> Icons.Outlined.Description
            FileType.APKS -> Icons.Outlined.Android
            FileType.ARCHIVES -> Icons.Outlined.FolderZip
            FileType.IMAGES -> Icons.Outlined.Image
            FileType.OTHER -> Icons.Outlined.Attachment
        }
        Box(mod, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(file.type.color))
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    category: Category,
    result: CategoryResult?,
    selectedFiles: List<FileItem>,
    selection: Set<String>,
    selectedBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: (useTrash: Boolean) -> Unit,
) {
    // Por seguridad, por defecto todo va a la papelera (recuperable 30 días).
    var useTrash by rememberSaveable { mutableStateOf(true) }

    // Seguridad: avisar si en algún grupo se han marcado TODAS las copias.
    val wipedGroups = if (category.grouped && result != null) {
        result.groups.count { g -> g.files.all { it.path in selection } }
    } else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Limpiar ${plural(selectedFiles.size, "archivo")}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Se liberarán ${formatSize(selectedBytes)}.")
                if (wipedGroups > 0) {
                    Text(
                        "Atención: en ${plural(wipedGroups, "grupo")} has marcado todas las copias, así que no quedará ninguna.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { useTrash = !useTrash },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = useTrash, onCheckedChange = { useTrash = it })
                    Column {
                        Text("Mover a la papelera", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Recuperable 30 días. El espacio se libera al vaciarla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(useTrash) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(if (useTrash) "Mover" else "Borrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

package com.despeja.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.despeja.app.model.FileGroup
import com.despeja.app.model.FileItem
import com.despeja.app.model.FileType
import com.despeja.app.ui.components.MediaPreview
import com.despeja.app.ui.daysAgo
import com.despeja.app.ui.formatSize
import com.despeja.app.ui.plural
import java.io.File

/** Filtro por tipo para separar fotos, vídeos y el resto. */
enum class TypeFilter(val label: String) {
    ALL("Todo"), PHOTOS("Fotos"), VIDEOS("Vídeos"), OTHER("Otros");

    fun matches(type: FileType): Boolean = when (this) {
        ALL -> true
        PHOTOS -> type == FileType.IMAGES
        VIDEOS -> type == FileType.VIDEOS
        OTHER -> type != FileType.IMAGES && type != FileType.VIDEOS
    }
}

/** Categorías mezcladas en las que tiene sentido separar por tipo. */
private val FILTERABLE = setOf(Category.DUPLICATES, Category.LARGE, Category.OLD_DOWNLOADS)

private fun FileItem.isMedia() = type == FileType.IMAGES || type == FileType.VIDEOS

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
    onSetSelected: (paths: Collection<String>, selected: Boolean) -> Unit,
    onSelectRecommended: (paths: Collection<String>) -> Unit,
    onDelete: (useTrash: Boolean, onlyPaths: Set<String>) -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    var filter by rememberSaveable(category) { mutableStateOf(TypeFilter.ALL) }
    var preview by remember { mutableStateOf<FileItem?>(null) }

    val allGroups = result?.groups.orEmpty()

    // Grupos visibles con el filtro. En duplicados todas las copias de un grupo son del mismo tipo,
    // así que se filtra el grupo entero; en listas simples se filtran los archivos.
    val visibleGroups: List<FileGroup> = remember(allGroups, filter, category) {
        if (category.grouped) allGroups.filter { g -> filter.matches(g.files.first().type) }
        else allGroups.map { g -> g.copy(files = g.files.filter { filter.matches(it.type) }) }.filter { it.files.isNotEmpty() }
    }
    val visibleFiles = remember(visibleGroups) { visibleGroups.flatMap { it.files } }
    val visiblePaths = remember(visibleFiles) { visibleFiles.mapTo(HashSet()) { it.path } }
    val selectedFiles = remember(visibleFiles, selection) { visibleFiles.filter { it.path in selection } }
    val selectedBytes = selectedFiles.sumOf { it.size }

    // Cuántos archivos hay de cada tipo, para los chips.
    val counts: Map<TypeFilter, Int> = remember(allGroups) {
        val files = allGroups.flatMap { it.files }
        TypeFilter.entries.associateWith { f -> files.count { f.matches(it.type) } }
    }
    val showFilter = category in FILTERABLE && counts.count { (k, v) -> k != TypeFilter.ALL && v > 0 } > 1

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
                            val what = if (filter == TypeFilter.ALL) "archivo" else filter.label.lowercase().removeSuffix("s")
                            Text(
                                if (selectedFiles.isEmpty()) "Marca lo que quieras limpiar"
                                else "Limpiar ${plural(selectedFiles.size, what)} · ${formatSize(selectedBytes)}",
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        if (result == null || allGroups.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aquí ya está todo limpio", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Hint(category)
                if (showFilter) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TypeFilter.entries.filter { it == TypeFilter.ALL || (counts[it] ?: 0) > 0 }.forEach { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text("${f.label} (${counts[f] ?: 0})") },
                            )
                        }
                    }
                }
                SelectionBar(
                    category = category,
                    selected = selectedFiles.size,
                    total = visibleFiles.size,
                    onSelectAll = { all -> onSetSelected(visiblePaths, all) },
                    onSelectRecommended = { onSelectRecommended(visiblePaths) },
                )
            }

            if (visibleFiles.isEmpty()) {
                item {
                    Text(
                        "No hay ${filter.label.lowercase()} en esta categoría.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else if (category.grouped) {
                visibleGroups.forEachIndexed { idx, group ->
                    item(key = "h-${group.id}") {
                        val extra = group.files.drop(1).sumOf { it.size }
                        val noun = when {
                            category == Category.SIMILAR -> "fotos"
                            group.files.first().type == FileType.IMAGES -> "fotos iguales"
                            group.files.first().type == FileType.VIDEOS -> "vídeos iguales"
                            else -> "copias"
                        }
                        Text(
                            "Grupo ${idx + 1} · ${group.files.size} $noun · ${formatSize(extra)} sobrantes",
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
                            onPreview = { preview = f },
                        )
                    }
                }
            } else {
                items(visibleFiles, key = { it.path }) { f ->
                    FileRow(
                        file = f,
                        checked = f.path in selection,
                        badge = null,
                        onToggle = { onToggle(f.path) },
                        onPreview = { preview = f },
                    )
                }
            }
        }
    }

    preview?.let { f ->
        MediaPreview(
            file = f,
            checked = f.path in selection,
            onToggle = { onToggle(f.path) },
            onDismiss = { preview = null },
        )
    }

    if (confirm) {
        val wipedGroups = if (category.grouped) visibleGroups.count { g -> g.files.all { it.path in selection } } else 0
        ConfirmDeleteDialog(
            count = selectedFiles.size,
            selectedBytes = selectedBytes,
            wipedGroups = wipedGroups,
            onDismiss = { confirm = false },
            onConfirm = { useTrash ->
                confirm = false
                onDelete(useTrash, visiblePaths)
            },
        )
    }
}

@Composable
private fun Hint(category: Category) {
    val text = when (category) {
        Category.DUPLICATES -> "Ya están marcadas las copias; el original (el más antiguo) de cada grupo se conserva. Toca una miniatura para verla."
        Category.LARGE -> "Archivos de más de 100 MB e instaladores. No hay nada marcado: elige qué quieres quitar. Toca un vídeo para verlo."
        Category.TEMP -> "Las apps vuelven a crear estos archivos cuando los necesitan. Puedes borrarlos sin problema."
        Category.OLD_DOWNLOADS -> "Descargas de hace más de 3 meses. Revísalas antes de borrar: puede haber documentos que quieras guardar."
        Category.BLURRY -> "La detección es automática y puede fallar: toca cada foto para revisarla antes de borrar."
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
        TextButton(onClick = { onSelectAll(selected < total) }, enabled = total > 0) {
            Text(if (selected < total || total == 0) "Todos" else "Ninguno")
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun FileRow(
    file: FileItem,
    checked: Boolean,
    badge: String?,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Thumbnail(file, onClick = if (file.isMedia()) onPreview else null)
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
private fun Thumbnail(file: FileItem, onClick: (() -> Unit)?) {
    val icon = when (file.type) {
        FileType.VIDEOS -> Icons.Outlined.Movie
        FileType.AUDIO -> Icons.Outlined.AudioFile
        FileType.DOCUMENTS -> Icons.Outlined.Description
        FileType.APKS -> Icons.Outlined.Android
        FileType.ARCHIVES -> Icons.Outlined.FolderZip
        FileType.IMAGES -> Icons.Outlined.Image
        FileType.OTHER -> Icons.Outlined.Attachment
    }
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // El icono queda debajo: si la miniatura no se puede generar, se ve el icono.
        Icon(icon, contentDescription = null, tint = Color(file.type.color))
        if (file.isMedia()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(file.path))
                    .size(156)
                    .build(),
                contentDescription = if (file.type == FileType.VIDEOS) "Ver vídeo" else "Ver foto",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (file.type == FileType.VIDEOS) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    count: Int,
    selectedBytes: Long,
    wipedGroups: Int,
    onDismiss: () -> Unit,
    onConfirm: (useTrash: Boolean) -> Unit,
) {
    // Por seguridad, por defecto todo va a la papelera (recuperable 30 días).
    var useTrash by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Limpiar ${plural(count, "archivo")}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (useTrash) "Irán a la papelera. Para liberar los ${formatSize(selectedBytes)} ya, vacíala después."
                    else "Se borrarán para siempre y liberarás ${formatSize(selectedBytes)}.",
                )
                if (wipedGroups > 0) {
                    Text(
                        "Ojo: en ${plural(wipedGroups, "grupo")} has marcado todas las copias y no quedará ninguna.",
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
                            "Podrás recuperarlos durante 30 días.",
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

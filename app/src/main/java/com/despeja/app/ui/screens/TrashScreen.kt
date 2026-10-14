package com.despeja.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.despeja.app.model.TrashEntry
import com.despeja.app.scanner.ScanConfig
import com.despeja.app.ui.daysAgo
import com.despeja.app.ui.formatSize
import com.despeja.app.ui.plural

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    entries: List<TrashEntry>,
    working: Boolean,
    snackbarHost: @Composable () -> Unit,
    onBack: () -> Unit,
    onRestore: (TrashEntry) -> Unit,
    onEmpty: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val total = entries.sumOf { it.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Papelera") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { confirm = true }, enabled = !working) {
                            Icon(Icons.Outlined.DeleteForever, "Vaciar papelera")
                        }
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("La papelera está vacía", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(
                    "${plural(entries.size, "archivo")} · ${formatSize(total)}. Se borran solos a los ${ScanConfig.TRASH_RETENTION_DAYS} días.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(entries, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatSize(e.size)} · borrado ${daysAgo(e.deletedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onRestore(e) }) {
                        Icon(Icons.Outlined.Restore, contentDescription = null)
                        Text(" Restaurar")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Vaciar la papelera?") },
            text = { Text("Se borrarán definitivamente ${plural(entries.size, "archivo")} y se liberarán ${formatSize(total)}.") },
            confirmButton = {
                Button(
                    onClick = { confirm = false; onEmpty() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Vaciar") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

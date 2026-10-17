package com.despeja.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.TextButton
import com.despeja.app.system.Updater
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.despeja.app.UiState
import com.despeja.app.model.Category
import com.despeja.app.model.CategoryResult
import com.despeja.app.model.FileType
import com.despeja.app.ui.components.ChartSlice
import com.despeja.app.ui.components.DonutChart
import com.despeja.app.ui.components.IconBadge
import com.despeja.app.ui.components.LegendRow
import com.despeja.app.ui.components.SectionTitle
import com.despeja.app.ui.components.icon
import com.despeja.app.ui.daysAgo
import com.despeja.app.ui.formatSize
import com.despeja.app.ui.plural

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    snackbarHost: @Composable () -> Unit,
    onRequestStorage: () -> Unit,
    onScan: () -> Unit,
    onOpenCategory: (Category) -> Unit,
    onOpenAppCache: () -> Unit,
    onOpenTrash: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val updateAvailable = state.update is Updater.Result.Available
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Espacio Libre", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onCheckUpdates) {
                        BadgedBox(badge = { if (updateAvailable) Badge() }) {
                            Icon(Icons.Outlined.SystemUpdate, contentDescription = "Buscar actualizaciones")
                        }
                    }
                    IconButton(onClick = onOpenTrash) {
                        BadgedBox(badge = { if (state.trash.isNotEmpty()) Badge { Text("${state.trash.size}") } }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Papelera")
                        }
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            if (!state.hasStorageAccess) PermissionCard(onRequestStorage)

            StorageCard(state)

            Spacer(Modifier.height(16.dp))
            Button(
                // Siempre pulsable: sin permiso, lleva directamente a concederlo.
                onClick = { if (state.hasStorageAccess) onScan() else onRequestStorage() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.result == null) "Analizar ahora" else "Analizar de nuevo",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            state.result?.let {
                Text(
                    "Último análisis ${daysAgo(it.finishedAt)} · ${plural(it.scannedFiles, "archivo")} en ${it.durationMs / 1000} s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            SectionTitle("Qué puedes limpiar")
            Category.entries.forEach { cat ->
                CategoryCard(cat, state.result?.categories?.get(cat), state.selections[cat]?.size ?: 0) {
                    onOpenCategory(cat)
                }
                Spacer(Modifier.height(8.dp))
            }
            AppCacheCard(state, onOpenAppCache)

            // Versión instalada y comprobación manual de actualizaciones
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Espacio Libre ${Updater.currentName}" + if (updateAvailable) " · hay una versión nueva" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (updateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCheckUpdates, enabled = !state.checkingUpdate) {
                    Text(if (state.checkingUpdate) "Comprobando…" else if (updateAvailable) "Actualizar" else "Buscar actualizaciones")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Para empezar, permite el acceso a tus archivos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                "Espacio Libre revisa tu almacenamiento para encontrar copias, archivos pesados y fotos repetidas. " +
                    "Todo se analiza en el móvil: no se envía nada y no se borra nada sin tu permiso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            FilledTonalButton(onClick = onRequest) { Text("Permitir acceso") }
        }
    }
}

@Composable
private fun StorageCard(state: UiState) {
    val storage = state.storage ?: return
    val result = state.result
    val freeColor = MaterialTheme.colorScheme.surfaceVariant

    // Con un análisis hecho, el espacio usado se desglosa por tipo; lo que no se ve
    // desde el almacenamiento compartido (sistema, apps y sus datos) va aparte.
    val slices: List<ChartSlice> = if (result != null) {
        val typed = FileType.entries.map { t ->
            ChartSlice(t.label, result.breakdown[t] ?: 0L, Color(t.color))
        }.filter { it.value > 0 }
        val system = (storage.usedBytes - result.scannedBytes).coerceAtLeast(0)
        typed + ChartSlice("Sistema y apps", system, MaterialTheme.colorScheme.secondary) +
            ChartSlice("Libre", storage.freeBytes, freeColor)
    } else {
        listOf(
            ChartSlice("Usado", storage.usedBytes, MaterialTheme.colorScheme.primary),
            ChartSlice("Libre", storage.freeBytes, freeColor),
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Almacenamiento interno", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(slices, Modifier.size(150.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(storage.usedFraction * 100).toInt()} %",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("usado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatSize(storage.freeBytes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "libres de ${formatSize(storage.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    val reclaim = result?.categories?.values?.sumOf { it.reclaimable } ?: 0L
                    if (result != null) {
                        Text(
                            "Puedes liberar hasta ${formatSize(reclaim)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        Text(
                            "Analiza el móvil para saber qué ocupa espacio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            slices.forEach { LegendRow(it.color, it.label, formatSize(it.value)) }
        }
    }
}

@Composable
private fun CategoryCard(category: Category, result: CategoryResult?, selected: Int, onClick: () -> Unit) {
    val enabled = result != null && result.count > 0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                category.icon(),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                background = MaterialTheme.colorScheme.primaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(category.title, style = MaterialTheme.typography.titleSmall)
                val sub = when {
                    result == null -> category.description
                    result.count == 0 -> "Todo limpio"
                    category.grouped -> "${plural(result.groups.size, "grupo")} · ${plural(result.count, "archivo")}"
                    else -> plural(result.count, "archivo")
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected > 0) {
                    Text(
                        "$selected marcados",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (result != null && result.count > 0) {
                Text(
                    formatSize(result.reclaimable),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun AppCacheCard(state: UiState, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                Icons.Outlined.Apps,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                background = MaterialTheme.colorScheme.primaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Caché de aplicaciones", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        !state.hasUsageAccess -> "Toca para ver cuánto ocupa cada app"
                        state.loadingAppCaches -> "Calculando…"
                        else -> plural(state.appCaches.size, "app")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.hasUsageAccess && state.appCacheTotal > 0) {
                Text(formatSize(state.appCacheTotal), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

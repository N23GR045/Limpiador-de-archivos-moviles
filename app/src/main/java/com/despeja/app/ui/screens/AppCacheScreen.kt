package com.despeja.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.despeja.app.UiState
import com.despeja.app.system.AppCaches
import com.despeja.app.ui.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCacheScreen(
    state: UiState,
    snackbarHost: @Composable () -> Unit,
    onBack: () -> Unit,
    onGrantUsage: () -> Unit,
    onOpenApp: (String) -> Unit,
    onRefresh: () -> Unit,
    onCleared: () -> Unit,
) {
    val context = LocalContext.current
    val clearLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onCleared()
    }
    val clearIntent = AppCaches.clearAllIntent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Caché de aplicaciones") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    if (state.hasUsageAccess) {
                        IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Actualizar") }
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                if (!state.hasUsageAccess) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Ver la caché de cada app", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Android solo muestra cuánto ocupa la caché de cada app si activas Espacio Libre en " +
                                    "Ajustes › Acceso a datos de uso.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            FilledTonalButton(onClick = onGrantUsage) { Text("Abrir ajustes") }
                        }
                    }
                } else {
                    Column {
                        Text(formatSize(state.appCacheTotal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "en caché repartidos entre ${state.appCaches.size} apps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (clearIntent != null) {
                    Button(
                        onClick = {
                            if (!state.hasStorageAccess) {
                                Toast.makeText(context, "Primero permite el acceso a tus archivos", Toast.LENGTH_LONG).show()
                            } else {
                                runCatching { clearLauncher.launch(clearIntent) }.onFailure {
                                    Toast.makeText(context, "Tu móvil no permite limpiar toda la caché desde aquí", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .height(52.dp),
                    ) { Text("Limpiar la caché de todas las apps") }
                    Text(
                        "Android te pedirá confirmación. No perderás datos ni sesiones: solo se borran archivos temporales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                } else {
                    Text(
                        "En esta versión de Android la caché se limpia app por app: toca una para abrir sus ajustes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            if (state.loadingAppCaches) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(state.appCaches, key = { it.packageName }) { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenApp(app.packageName) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(formatSize(app.cacheBytes), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
                    Icon(Icons.Outlined.Settings, contentDescription = "Abrir ajustes de la app")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (state.hasUsageAccess && !state.loadingAppCaches && state.appCaches.isEmpty()) {
                item {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Ninguna app tiene caché acumulada")
                    }
                }
            }
        }
    }
}

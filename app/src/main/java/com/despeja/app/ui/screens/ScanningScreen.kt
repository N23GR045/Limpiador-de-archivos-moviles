package com.despeja.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.despeja.app.scanner.StorageScanner
import com.despeja.app.ui.formatSize

@Composable
fun ScanningScreen(progress: StorageScanner.Progress?, onCancel: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val phases = StorageScanner.Phase.entries
            val step = progress?.phase?.let { phases.indexOf(it) + 1 } ?: 1

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                val fraction = progress?.fraction
                if (fraction == null) {
                    CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 10.dp)
                } else {
                    CircularProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 10.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${progress?.filesFound ?: 0}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("archivos", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                progress?.phase?.label ?: "Preparando…",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Paso $step de ${phases.size} · ${formatSize(progress?.bytesFound ?: 0)} revisados",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { (step - 1 + (progress?.fraction ?: 0f)) / phases.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                progress?.currentPath?.substringAfter("/emulated/0").orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

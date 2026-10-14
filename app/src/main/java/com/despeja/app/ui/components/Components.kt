package com.despeja.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.despeja.app.model.Category

data class ChartSlice(val label: String, val value: Long, val color: Color)

/** Gráfico de anillo. El contenido central (porcentaje, espacio libre) va en [center]. */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp,
    center: @Composable () -> Unit,
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val gap = if (slices.count { it.value > 0 } > 1) 1.6f else 0f
            var start = -90f
            slices.forEach { s ->
                if (s.value <= 0) return@forEach
                val sweep = 360f * s.value / total
                drawArc(
                    color = s.color,
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun LegendRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Barra horizontal de proporciones (para el resumen compacto). */
@Composable
fun StackedBar(slices: List<ChartSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        slices.filter { it.value > 0 }.forEach { s ->
            Box(
                Modifier
                    .weight((s.value.toFloat() / total).coerceAtLeast(0.01f))
                    .fillMaxSize()
                    .background(s.color),
            )
        }
    }
}

fun Category.icon(): ImageVector = when (this) {
    Category.DUPLICATES -> Icons.Outlined.ContentCopy
    Category.LARGE -> Icons.Outlined.VideoFile
    Category.TEMP -> Icons.Outlined.CleaningServices
    Category.OLD_DOWNLOADS -> Icons.Outlined.Download
    Category.BLURRY -> Icons.Outlined.BlurOn
    Category.SIMILAR -> Icons.Outlined.Collections
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, background: Color, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 20.dp, bottom = 8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

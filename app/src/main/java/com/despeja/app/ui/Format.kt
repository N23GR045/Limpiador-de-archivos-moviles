package com.despeja.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val spanish: Locale = Locale.forLanguageTag("es-ES")

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (value >= 100) String.format(spanish, "%.0f %s", value, units[i])
    else String.format(spanish, "%.1f %s", value, units[i])
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", spanish).format(Date(millis))

fun daysAgo(millis: Long): String {
    val days = ((System.currentTimeMillis() - millis) / 86_400_000L).coerceAtLeast(0)
    return when (days) {
        0L -> "hoy"
        1L -> "ayer"
        else -> "hace $days días"
    }
}

fun plural(n: Int, singular: String, pluralForm: String = singular + "s") =
    "$n ${if (n == 1) singular else pluralForm}"

package com.wtracker.field.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Abstracción del reloj: permite testear y centraliza la zona horaria usada en sellos y reportes. */
interface TimeProvider {
    fun nowMillis(): Long
    fun zone(): ZoneId
}

class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

object TimeUtils {

    private val appLocale: Locale = Locale("es")

    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm", appLocale)
    private val longDateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", appLocale)
    private val stampFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss xxx", appLocale)
    private val exifDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val exifOffsetFormatter = DateTimeFormatter.ofPattern("xxx", Locale.US)
    private val exifGpsDateFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.US)
    private val exifGpsTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L

    fun startOfDayMillis(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** ISO-8601 UTC con milisegundos: formato aceptado por `z.string().datetime()` del Backoffice. */
    fun toIsoUtc(millis: Long): String =
        Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.MILLIS).toString()

    fun parseIsoMillis(isoValue: String?): Long? =
        isoValue?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    fun formatClock(millis: Long, zone: ZoneId): String = clockFormatter.format(zoned(millis, zone))

    fun formatLongDate(millis: Long, zone: ZoneId): String =
        longDateFormatter.format(zoned(millis, zone)).replaceFirstChar { it.titlecase(appLocale) }

    fun formatStamp(millis: Long, zone: ZoneId): String = stampFormatter.format(zoned(millis, zone))

    fun formatExifDateTime(millis: Long, zone: ZoneId): String = exifDateTimeFormatter.format(zoned(millis, zone))

    fun formatExifOffset(millis: Long, zone: ZoneId): String = exifOffsetFormatter.format(zoned(millis, zone))

    fun formatExifGpsDate(millis: Long): String = exifGpsDateFormatter.format(zoned(millis, ZoneId.of("UTC")))

    fun formatExifGpsTime(millis: Long): String = exifGpsTimeFormatter.format(zoned(millis, ZoneId.of("UTC")))

    fun formatDuration(durationMillis: Long): String {
        val totalMinutes = (durationMillis.coerceAtLeast(0L) / MILLIS_PER_MINUTE)
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        if (hours == 0L) return "$minutes min"
        return "$hours h ${minutes.toString().padStart(2, '0')} min"
    }

    private fun zoned(millis: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone)
}

/** Emite la hora actual periódicamente; útil para cronómetros de jornada en la UI. */
fun tickerFlow(periodMillis: Long, timeProvider: TimeProvider): Flow<Long> = flow {
    while (true) {
        emit(timeProvider.nowMillis())
        delay(periodMillis)
    }
}

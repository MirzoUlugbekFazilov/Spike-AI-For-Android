package com.spikeai.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val isoDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** Current UTC timestamp in ISO-8601 (for updated_at columns). */
fun nowIso(): String = Instant.now().toString()

/** A LocalDate formatted as yyyy-MM-dd. */
fun LocalDate.toDbDate(): String = format(isoDate)

/** Parse a yyyy-MM-dd string to LocalDate, or null. */
fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this, isoDate) }.getOrNull()

/** Today in the device's local time zone. */
fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())

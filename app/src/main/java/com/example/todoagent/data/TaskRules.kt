package com.example.todoagent.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Thrown when the agent (or the user) proposes something the app won't accept. */
class RuleViolation(message: String) : Exception(message)

/**
 * "Code works out the facts, AI just interprets."
 *
 * The model never does date math on its own. Every message gets a calendar of the next
 * 14 days (see [contextBlock]) so the model only has to look a date up, and everything it
 * sends back is validated here before it touches the database.
 */
object TaskRules {
    const val MAX_TITLE_LENGTH = 120
    const val MAX_DAYS_AHEAD = 365L
    const val MAX_REMIND_MINUTES = 24 * 60

    private val calendarFormat = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.US)
    private val nowFormat = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.US)
    private val timeFormat = DateTimeFormatter.ofPattern("H:mm", Locale.US)

    /** Prepended to every user message so the agent always sees the real date. */
    fun contextBlock(now: LocalDateTime = LocalDateTime.now()): String = buildString {
        appendLine("[Context]")
        appendLine("Now: ${now.format(nowFormat)}")
        appendLine("Calendar for the next 14 days:")
        val today = now.toLocalDate()
        for (offset in 0L..13L) {
            val day = today.plusDays(offset)
            val note = when (offset) {
                0L -> " (today)"
                1L -> " (tomorrow)"
                else -> ""
            }
            appendLine("- ${day.format(calendarFormat)}$note")
        }
    }

    fun title(raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw RuleViolation("The task needs a title.")
        if (clean.length > MAX_TITLE_LENGTH) {
            throw RuleViolation("Titles are limited to $MAX_TITLE_LENGTH characters. Use a shorter title.")
        }
        return clean
    }

    /** Any valid date, including past ones (used for "move today's/overdue tasks"). */
    fun anyDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
            ?: throw RuleViolation("'$raw' is not a date. Use yyyy-MM-dd from the calendar.")
    }

    /** A due date: must be today or later, and within a year. */
    fun dueDate(raw: String?, today: LocalDate): LocalDate? {
        val date = anyDate(raw) ?: return null
        if (date.isBefore(today)) throw RuleViolation("$date is in the past. Pick today or a later date.")
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw RuleViolation("$date is more than a year away. TodoAgent only plans up to a year ahead.")
        }
        return date
    }

    fun dueTime(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(raw.trim(), timeFormat) }.getOrNull()
            ?: throw RuleViolation("'$raw' is not a time. Use 24-hour HH:mm, like 09:30 or 18:00.")
    }

    fun remindAt(now: LocalDateTime, minutes: Int): LocalDateTime {
        if (minutes !in 1..MAX_REMIND_MINUTES) {
            throw RuleViolation("Relative reminders must be between 1 minute and 24 hours from now.")
        }
        return now.plusMinutes(minutes.toLong()).withSecond(0).withNano(0)
    }

    /** Resolves the final date/time for a new task, all in code. */
    fun resolveSchedule(
        now: LocalDateTime,
        dueDate: String?,
        dueTime: String?,
        remindInMinutes: Int?,
    ): Pair<LocalDate?, LocalTime?> {
        if (remindInMinutes != null) {
            val at = remindAt(now, remindInMinutes)
            return at.toLocalDate() to at.toLocalTime()
        }
        val today = now.toLocalDate()
        var date = dueDate(dueDate, today)
        val time = dueTime(dueTime)

        // A time with no date means the next occurrence of that time.
        if (date == null && time != null) {
            date = if (today.atTime(time).isAfter(now)) today else today.plusDays(1)
        }
        if (date != null && time != null && date.atTime(time).isBefore(now)) {
            throw RuleViolation("${date.atTime(time)} has already passed.")
        }
        return date to time
    }
}

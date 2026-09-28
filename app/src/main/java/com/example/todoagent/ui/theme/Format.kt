package com.example.todoagent.ui

import com.example.todoagent.data.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

fun friendlyDate(date: LocalDate?, today: LocalDate = LocalDate.now()): String = when (date) {
    null -> "no date"
    today -> "today"
    today.plusDays(1) -> "tomorrow"
    today.minusDays(1) -> "yesterday"
    else -> date.format(dayFormat)
}

fun dueLabel(task: Task, today: LocalDate = LocalDate.now()): String? {
    val date = task.dueDate ?: return null
    val day = friendlyDate(date, today).replaceFirstChar { it.uppercase() }
    return task.dueTime?.let { "$day at ${it.format(timeFormat)}" } ?: day
}

fun isOverdue(task: Task, today: LocalDate = LocalDate.now()): Boolean =
    !task.isDone && task.dueDate?.isBefore(today) == true

/** "addTask, addTask, addTask, getTasks" -> ["addTask ×3", "getTasks"] */
fun groupToolNames(names: List<String>): List<String> =
    names.groupingBy { it }.eachCount().map { (name, count) -> if (count > 1) "$name ×$count" else name }

package com.example.todoagent.agent

import com.example.todoagent.data.RuleViolation
import com.example.todoagent.data.TaskRepository
import com.example.todoagent.data.TaskRules
import com.example.todoagent.data.describeForAgent
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import java.time.LocalDateTime

/**
 * Everything the agent is allowed to do, and nothing more.
 *
 * KSP turns these into tool schemas at compile time and generates
 * `TaskTools(...).generatedTools()` in this same package.
 *
 * Destructive tools use requireConfirmation = true: ADK pauses the agent,
 * the app shows a confirm card, and the tool only runs if the user approves.
 */
class TaskTools(
    private val repo: TaskRepository,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
) {

    @Tool(
        name = "addTask",
        description = "Adds ONE task to the user's list and schedules its reminder. " +
                "If the user mentions several tasks, call this once per task.",
    )
    suspend fun addTask(
        @Param("Short task title in the user's own words, max 120 characters") title: String,
        @Param("Due date as yyyy-MM-dd, copied from the calendar in the context. Leave empty if no date was mentioned.")
        dueDate: String? = null,
        @Param("Due time as HH:mm in 24-hour format. Leave empty if no time was mentioned.")
        dueTime: String? = null,
        @Param("Only for phrases like 'in 10 minutes' or 'in 2 hours': minutes from now, 1 to 1440. Leave empty otherwise.")
        remindInMinutes: Int? = null,
    ): String = guarded {
        val (date, time) = TaskRules.resolveSchedule(clock(), dueDate, dueTime, remindInMinutes)
        val task = repo.addTask(TaskRules.title(title), date, time, createdByAgent = true)
        "OK: added ${task.describeForAgent()}"
    }

    @Tool(
        name = "getTasks",
        description = "Returns the user's tasks with their ids. Always call this before answering " +
                "questions about tasks and before changing or deleting a task.",
    )
    suspend fun getTasks(
        @Param("Which tasks: today, tomorrow, week, overdue, open, completed or all. Defaults to open.")
        filter: String? = null,
    ): String {
        val today = clock().toLocalDate()
        val all = repo.getAll()
        val chosen = filter?.trim()?.lowercase()?.ifEmpty { null } ?: "open"
        val selected = when (chosen) {
            "today" -> all.filter { !it.isDone && it.dueDate == today }
            "tomorrow" -> all.filter { !it.isDone && it.dueDate == today.plusDays(1) }
            "week" -> all.filter { task ->
                val due = task.dueDate
                !task.isDone && due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(6))
            }
            "overdue" -> all.filter { task -> !task.isDone && task.dueDate?.isBefore(today) == true }
            "completed" -> all.filter { it.isDone }
            "all" -> all
            else -> all.filter { !it.isDone }
        }
        if (selected.isEmpty()) return "No tasks match '$chosen'."
        return "${selected.size} task(s):\n" + selected.joinToString("\n") { it.describeForAgent() }
    }

    @Tool(
        name = "completeTask",
        description = "Marks a task as done. Get the id from getTasks first; never guess an id.",
    )
    suspend fun completeTask(
        @Param("The task id from getTasks") taskId: Int,
    ): String = guarded {
        val task = repo.setDone(taskId.toLong(), done = true)
            ?: throw RuleViolation("There is no task #$taskId. Call getTasks to see valid ids.")
        "OK: completed ${task.describeForAgent()}"
    }

    @Tool(
        name = "rescheduleTasks",
        description = "Moves ALL open tasks due on fromDate to toDate. The user is asked to confirm first.",
        requireConfirmation = true,
    )
    suspend fun rescheduleTasks(
        @Param("Current due date of the tasks, yyyy-MM-dd") fromDate: String,
        @Param("New due date, yyyy-MM-dd from the calendar") toDate: String,
    ): String = guarded {
        val today = clock().toLocalDate()
        val from = TaskRules.anyDate(fromDate) ?: throw RuleViolation("fromDate is required.")
        val to = TaskRules.dueDate(toDate, today) ?: throw RuleViolation("toDate is required.")
        if (from == to) throw RuleViolation("Both dates are the same, so nothing would move.")
        val moved = repo.moveOpenTasks(from, to)
        if (moved.isEmpty()) "No open tasks were due on $from."
        else "OK: moved ${moved.size} task(s) to $to: " + moved.joinToString { it.title }
    }

    @Tool(
        name = "deleteTask",
        description = "Permanently deletes one task. The user is asked to confirm first.",
        requireConfirmation = true,
    )
    suspend fun deleteTask(
        @Param("The task id from getTasks") taskId: Int,
    ): String = guarded {
        val task = repo.delete(taskId.toLong())
            ?: throw RuleViolation("There is no task #$taskId. Call getTasks to see valid ids.")
        "OK: deleted \"${task.title}\""
    }

    @Tool(
        name = "deleteCompletedTasks",
        description = "Permanently deletes every completed task. The user is asked to confirm first.",
        requireConfirmation = true,
    )
    suspend fun deleteCompletedTasks(): String {
        val count = repo.deleteCompleted()
        return if (count == 0) "There were no completed tasks." else "OK: deleted $count completed task(s)."
    }

    /** Rule violations go back to the agent as text so it can explain them, instead of crashing. */
    private inline fun guarded(block: () -> String): String =
        try {
            block()
        } catch (e: RuleViolation) {
            "ERROR: ${e.message}"
        }
}
package com.example.todoagent.data

import com.example.todoagent.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/**
 * Single source of truth. The typed "classic" UI and the AI agent both go through here,
 * so the agent can never do anything the regular app couldn't.
 */
class TaskRepository(
    private val dao: TaskDao,
    private val reminders: ReminderScheduler,
) {
    fun observeTasks(): Flow<List<Task>> = dao.observeAll()

    suspend fun getAll(): List<Task> = dao.getAll()
    suspend fun getById(id: Long): Task? = dao.getById(id)
    suspend fun openTasksOn(date: LocalDate): List<Task> = dao.getOpenOn(date)
    suspend fun completedTasks(): List<Task> = dao.getCompleted()

    suspend fun addTask(
        title: String,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        createdByAgent: Boolean = false,
    ): Task {
        val draft = Task(title = title, dueDate = dueDate, dueTime = dueTime, createdByAgent = createdByAgent)
        val saved = draft.copy(id = dao.insert(draft))
        reminders.schedule(saved)
        return saved
    }

    suspend fun setDone(id: Long, done: Boolean): Task? {
        val task = dao.getById(id) ?: return null
        val updated = task.copy(isDone = done)
        dao.update(updated)
        if (done) reminders.cancel(id) else reminders.schedule(updated)
        return updated
    }

    suspend fun moveOpenTasks(from: LocalDate, to: LocalDate): List<Task> {
        val moved = dao.getOpenOn(from).map { it.copy(dueDate = to) }
        moved.forEach { task ->
            dao.update(task)
            reminders.schedule(task)
        }
        return moved
    }

    suspend fun delete(id: Long): Task? {
        val task = dao.getById(id) ?: return null
        reminders.cancel(id)
        dao.delete(task)
        return task
    }

    suspend fun deleteCompleted(): Int {
        dao.getCompleted().forEach { reminders.cancel(it.id) }
        return dao.deleteCompleted()
    }
}

package com.example.todoagent.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The classic to-do entity. The only "AI" field is createdByAgent, used for a small badge in the UI. */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val isDone: Boolean = false,
    val createdByAgent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

private val AGENT_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd", Locale.US)

/** One-line description handed back to the agent. Always includes the id so it never has to guess. */
fun Task.describeForAgent(): String = buildString {
    append("#$id \"$title\"")
    if (dueDate != null) {
        append(" due ${dueDate.format(AGENT_DATE_FORMAT)}")
        if (dueTime != null) append(" $dueTime")
    } else {
        append(" (no due date)")
    }
    append(if (isDone) " [done]" else " [open]")
}

class Converters {
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun timeToString(value: LocalTime?): String? = value?.toString()
    @TypeConverter fun stringToTime(value: String?): LocalTime? = value?.let(LocalTime::parse)
}

@Dao
interface TaskDao {
    @Query(
        "SELECT * FROM tasks ORDER BY isDone ASC, dueDate IS NULL ASC, dueDate ASC, " +
            "dueTime IS NULL ASC, dueTime ASC, createdAt ASC"
    )
    fun observeAll(): Flow<List<Task>>

    @Query(
        "SELECT * FROM tasks ORDER BY isDone ASC, dueDate IS NULL ASC, dueDate ASC, " +
            "dueTime IS NULL ASC, dueTime ASC, createdAt ASC"
    )
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND dueDate = :date")
    suspend fun getOpenOn(date: LocalDate): List<Task>

    @Query("SELECT * FROM tasks WHERE isDone = 1")
    suspend fun getCompleted(): List<Task>

    @Insert suspend fun insert(task: Task): Long
    @Update suspend fun update(task: Task)
    @Delete suspend fun delete(task: Task)

    @Query("DELETE FROM tasks WHERE isDone = 1")
    suspend fun deleteCompleted(): Int
}

@Database(entities = [Task::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): TaskDatabase =
            Room.databaseBuilder(context, TaskDatabase::class.java, "todoagent.db").build()
    }
}

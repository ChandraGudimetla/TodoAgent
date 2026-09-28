package com.example.todoagent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.todoagent.TodoAgentApp
import com.example.todoagent.agent.AgentTurn
import com.example.todoagent.agent.PendingConfirmation
import com.example.todoagent.data.RuleViolation
import com.example.todoagent.data.Task
import com.example.todoagent.data.TaskRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The confirm card. Its text is written by app code from real data, not by the model. */
data class ConfirmCard(
    val request: PendingConfirmation,
    val title: String,
    val details: List<String>,
    val approveLabel: String,
)

data class AgentState(
    val userText: String? = null,
    val reply: String? = null,
    val toolsCalled: List<String> = emptyList(),
    val thinking: Boolean = false,
    val confirm: ConfirmCard? = null,
    val error: String? = null,
)

data class VoiceState(
    val listening: Boolean = false,
    val partial: String = "",
)

data class UiState(
    val tasks: List<Task> = emptyList(),
    val agent: AgentState = AgentState(),
    val voice: VoiceState = VoiceState(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TodoAgentApp
    private val repo = app.repository

    private val agentState = MutableStateFlow(AgentState())
    private val voiceState = MutableStateFlow(VoiceState())

    val uiState: StateFlow<UiState> =
        combine(repo.observeTasks(), agentState, voiceState) { tasks, agent, voice ->
            UiState(tasks, agent, voice)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    // ---- The classic app: typed add and checkboxes, no AI involved ----

    fun addTyped(title: String) {
        viewModelScope.launch {
            try {
                repo.addTask(TaskRules.title(title))
            } catch (e: RuleViolation) {
                agentState.update { it.copy(error = e.message) }
            }
        }
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch { repo.setDone(task.id, !task.isDone) }
    }

    // ---- The AI upgrade ----

    fun ask(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || agentState.value.thinking) return
        app.speaker.stop()
        agentState.value = AgentState(userText = trimmed, thinking = true)
        viewModelScope.launch {
            runCatching { app.agent.send(trimmed) }
                .onSuccess { applyTurn(it) }
                .onFailure { showAgentError(it) }
        }
    }

    fun answerConfirm(approved: Boolean) {
        val card = agentState.value.confirm ?: return
        agentState.update { it.copy(thinking = true, confirm = null) }
        viewModelScope.launch {
            runCatching { app.agent.answerConfirmation(card.request.requestId, approved) }
                .onSuccess { applyTurn(it, keepTools = true) }
                .onFailure { showAgentError(it) }
        }
    }

    fun dismissError() = agentState.update { it.copy(error = null) }
    fun stopSpeaking() = app.speaker.stop()

    fun setSpeechEnabled(enabled: Boolean) {
        app.speaker.enabled = enabled
        if (!enabled) app.speaker.stop()
    }

    // ---- Voice callbacks (called from SpeechInput on the main thread) ----

    fun onListeningChanged(listening: Boolean) =
        voiceState.update { it.copy(listening = listening, partial = if (listening) "" else it.partial) }

    fun onPartialTranscript(text: String) = voiceState.update { it.copy(partial = text) }

    fun onFinalTranscript(text: String) {
        voiceState.value = VoiceState()
        ask(text)
    }

    fun onVoiceError(message: String) {
        voiceState.value = VoiceState()
        agentState.update { it.copy(error = message) }
    }

    // ---- Internals ----

    private suspend fun applyTurn(turn: AgentTurn, keepTools: Boolean = false) {
        val card = turn.pendingConfirmation?.let { buildConfirmCard(it) }
        agentState.update { current ->
            current.copy(
                reply = turn.reply.ifBlank { null },
                toolsCalled = if (keepTools) current.toolsCalled + turn.toolsCalled else turn.toolsCalled,
                thinking = false,
                confirm = card,
            )
        }
        app.speaker.speak(card?.title ?: turn.reply)
    }

    private fun showAgentError(error: Throwable) {
        agentState.update {
            it.copy(
                thinking = false,
                error = "The assistant couldn't respond: ${error.message ?: error::class.simpleName}. " +
                    "Check your connection and try again.",
            )
        }
    }

    /** Code counts and names exactly what will change, so the card can't be wrong. */
    private suspend fun buildConfirmCard(request: PendingConfirmation): ConfirmCard =
        when (request.toolName) {
            "rescheduleTasks" -> {
                val from = request.args["fromDate"]?.toString()
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val to = request.args["toDate"]?.toString()
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val affected = from?.let { repo.openTasksOn(it) }.orEmpty()
                ConfirmCard(
                    request = request,
                    title = "Move ${affected.size} ${plural(affected.size)} from " +
                        "${friendlyDate(from)} to ${friendlyDate(to)}?",
                    details = affected.map { it.title },
                    approveLabel = "Move ${plural(affected.size)}",
                )
            }
            "deleteCompletedTasks" -> {
                val done = repo.completedTasks()
                ConfirmCard(
                    request = request,
                    title = "Delete ${done.size} completed ${plural(done.size)}?",
                    details = done.map { it.title },
                    approveLabel = "Delete",
                )
            }
            "deleteTask" -> {
                val id = request.args["taskId"]?.toString()?.toDoubleOrNull()?.toLong()
                val task = id?.let { repo.getById(it) }
                ConfirmCard(
                    request = request,
                    title = "Delete \"${task?.title ?: "task #$id"}\"?",
                    details = emptyList(),
                    approveLabel = "Delete",
                )
            }
            else -> ConfirmCard(
                request = request,
                title = "Allow the assistant to run ${request.toolName}?",
                details = request.args.map { (k, v) -> "$k: $v" },
                approveLabel = "Allow",
            )
        }

    private fun plural(count: Int) = if (count == 1) "task" else "tasks"
}

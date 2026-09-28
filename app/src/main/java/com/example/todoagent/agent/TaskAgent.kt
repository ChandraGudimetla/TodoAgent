package com.example.todoagent.agent

import android.content.Context
import com.example.todoagent.data.TaskRules
import com.google.adk.firebase.models.Firebase
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.events.ToolConfirmation
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.room.RoomSessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import kotlinx.coroutines.flow.toList

/** What happened in one agent turn, in plain app terms. */
data class AgentTurn(
    val reply: String,
    val toolsCalled: List<String>,
    val pendingConfirmation: PendingConfirmation?,
)

/** ADK paused because a requireConfirmation tool wants to run. */
data class PendingConfirmation(
    val requestId: String,
    val toolName: String,
    val args: Map<String, Any?>,
)

/**
 * The only file that talks to ADK directly. If an ADK API name differs in your version,
 * this is the one place to fix it.
 */
class TaskAgent(context: Context, tools: TaskTools) {

    private val agent = LlmAgent(
        name = "todo_agent",
        description = "Manages the user's to-do list and reminders.",
        model = createCloudModel(),
        // To try Gemini Nano instead, see createOnDeviceModel() below.
        instruction = Instruction(INSTRUCTION),
        tools = tools.generatedTools(),
    )

    private val runner = InMemoryRunner(
        agent = agent,
        appName = "TodoAgent",
        // Conversation state survives process death, so "kill the app and reopen" works.
        sessionService = RoomSessionService.fromContext(context.applicationContext),
    )

    /** Sends what the user said (voice or typed) with a code-generated date context. */
    suspend fun send(userText: String): AgentTurn =
        run(Content.fromText(Role.USER, TaskRules.contextBlock() + "\nUser: " + userText))

    /** Resumes a paused turn after the user taps Approve or Cancel on the confirm card. */
    suspend fun answerConfirmation(requestId: String, approved: Boolean): AgentTurn =
        run(
            Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        functionResponse = FunctionResponse(
                            name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                            id = requestId,
                            response = mapOf(ToolConfirmation.CONFIRMED_KEY to approved),
                        )
                    )
                ),
            )
        )

    private suspend fun run(message: Content): AgentTurn {
        val events = runner.runAsync(
            userId = USER_ID,
            sessionId = SESSION_ID,
            newMessage = message,
        ).toList()

        val calls = events.flatMap { it.functionCalls() }
        val confirmRequest = calls.lastOrNull { it.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME }
        val realToolCalls = calls.filter { it.name != FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME }

        val reply = events
            .mapNotNull { event ->
                event.content?.parts
                    ?.mapNotNull { it.text }
                    ?.joinToString("")
                    ?.takeIf { it.isNotBlank() }
            }
            .lastOrNull()
            .orEmpty()
            .trim()

        val pending = confirmRequest?.let { request ->
            // The tool waiting for approval is the last real call in this turn.
            val target = realToolCalls.lastOrNull()
            PendingConfirmation(
                requestId = "${request.id}",
                toolName = "${target?.name}",
                args = target?.args.orEmpty(),
            )
        }

        return AgentTurn(
            reply = reply,
            toolsCalled = realToolCalls.map { "${it.name}" },
            pendingConfirmation = pending,
        )
    }

    private fun createCloudModel() =
        Firebase.create(
            "gemini-flash-lite-latest",
            FirebaseAI.getInstance(FirebaseApp.getInstance()),
        )

    /*
     * On-device option (needs google-adk-kotlin-mlkit-android:1.0.0-beta in build.gradle).
     * Test multi-task sentences before relying on it for the demo; if Nano handles them,
     * you get a fully offline, airplane-mode voice agent.
     *
     * private fun createOnDeviceModel() = GenaiPrompt.create(
     *     generativeModel = com.google.mlkit.genai.prompt.Generation.getClient(),  // VERIFY
     *     name = "gemini-nano",
     * )
     */

    companion object {
        private const val USER_ID = "local-user"
        private const val SESSION_ID = "todoagent-main"

        private val INSTRUCTION = """
            You are TodoAgent, the assistant inside a to-do app on the user's phone.

            Every user message starts with a [Context] block: the current date and time and a
            calendar of the next 14 days. Use it for every date. Never calculate dates yourself.

            Adding tasks:
            - Split what the user said into separate tasks and call addTask once per task.
            - Copy dates from the calendar as yyyy-MM-dd. "Before Friday" or "by Friday" means that Friday.
            - A weekday name means its next occurrence in the calendar.
            - If no date is mentioned, leave dueDate empty. For "in N minutes/hours", use remindInMinutes.

            Reading and changing tasks:
            - To answer questions about tasks, call getTasks. Don't answer from memory; the list changes.
            - Before completing or deleting a task, call getTasks to find its id. Never invent ids.
            - To push tasks from one day to another, use rescheduleTasks.

            If a tool returns ERROR, tell the user briefly what the problem was. Don't retry with a guess.
                        The user's words come from speech recognition and may contain misheard words.
                        Fix obvious mistakes in task titles (for example "Android Def Summit" is "Android Dev Summit").

            Your replies are read aloud: keep them to one or two short sentences, with no markdown,
            no lists and no emojis.
            
        """.trimIndent()
    }
}
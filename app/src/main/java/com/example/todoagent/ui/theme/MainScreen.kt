package com.example.todoagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todoagent.data.Task
import java.time.LocalDate

// Palette: cool paper and ink for the classic app, one violet reserved for anything the agent did.
object AppColors {
    val Paper = Color(0xFFF6F7FB)
    val Ink = Color(0xFF1B2140)
    val Muted = Color(0xFF6B7089)
    val Line = Color(0xFFE2E4EE)
    val Agent = Color(0xFF5A48D6)
    val AgentTint = Color(0xFFECE9FF)
    val Overdue = Color(0xFFB8432F)
}

val AppColorScheme: ColorScheme = lightColorScheme(
    primary = AppColors.Agent,
    onPrimary = Color.White,
    background = AppColors.Paper,
    surface = AppColors.Paper,
    onBackground = AppColors.Ink,
    onSurface = AppColors.Ink,
    outline = AppColors.Line,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoAgentScreen(
    state: UiState,
    speechOn: Boolean,
    onToggleSpeech: () -> Unit,
    onAddTyped: (String) -> Unit,
    onAsk: (String) -> Unit,
    onToggleDone: (Task) -> Unit,
    onMic: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    onDismissError: () -> Unit,
) {
    val today = LocalDate.now()
    val open = state.tasks.count { !it.isDone }
    val dueToday = state.tasks.count { !it.isDone && it.dueDate == today }

    Scaffold(
        containerColor = AppColors.Paper,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Paper),
                title = {
                    Column {
                        Text("TodoAgent", fontWeight = FontWeight.Bold, fontSize = 26.sp, color = AppColors.Ink)
                        Text(
                            "$open open, $dueToday due today",
                            fontSize = 13.sp,
                            color = AppColors.Muted,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleSpeech) {
                        Icon(
                            if (speechOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (speechOn) "Turn off spoken replies" else "Turn on spoken replies",
                            tint = AppColors.Muted,
                        )
                    }
                },
            )
        },
        bottomBar = {
            BottomPanel(
                state = state,
                onAddTyped = onAddTyped,
                onAsk = onAsk,
                onMic = onMic,
                onConfirm = onConfirm,
                onDismissError = onDismissError,
            )
        },
    ) { padding ->
        TaskList(
            tasks = state.tasks,
            today = today,
            onToggleDone = onToggleDone,
            contentPadding = padding,
        )
    }
}

// ------------------------------------------------------------------ task list (the classic app)

@Composable
private fun TaskList(
    tasks: List<Task>,
    today: LocalDate,
    onToggleDone: (Task) -> Unit,
    contentPadding: PaddingValues,
) {
    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding).padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No tasks yet. Type one below, or tap the mic and say everything on your mind.",
                color = AppColors.Muted,
                fontSize = 16.sp,
            )
        }
        return
    }

    val sections = listOf(
        "Overdue" to tasks.filter { isOverdue(it, today) },
        "Today" to tasks.filter { !it.isDone && it.dueDate == today },
        "Upcoming" to tasks.filter { !it.isDone && it.dueDate?.isAfter(today) == true },
        "Anytime" to tasks.filter { !it.isDone && it.dueDate == null },
        "Done" to tasks.filter { it.isDone },
    ).filter { it.second.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        sections.forEach { (title, sectionTasks) ->
            item(key = "header-$title") {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (title == "Overdue") AppColors.Overdue else AppColors.Muted,
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                )
            }
            items(sectionTasks, key = { it.id }) { task ->
                TaskRow(task, today, onToggleDone)
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, today: LocalDate, onToggleDone: (Task) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Checkbox(
            checked = task.isDone,
            onCheckedChange = { onToggleDone(task) },
            colors = CheckboxDefaults.colors(checkedColor = AppColors.Muted),
        )
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                fontSize = 17.sp,
                color = if (task.isDone) AppColors.Muted else AppColors.Ink,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            dueLabel(task, today)?.let {
                Text(
                    it,
                    fontSize = 13.sp,
                    color = if (isOverdue(task, today)) AppColors.Overdue else AppColors.Muted,
                )
            }
        }
        if (task.createdByAgent) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = "Added by the assistant",
                tint = AppColors.Agent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ bottom: agent + input

@Composable
private fun BottomPanel(
    state: UiState,
    onAddTyped: (String) -> Unit,
    onAsk: (String) -> Unit,
    onMic: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    onDismissError: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val agent = state.agent

    Surface(color = Color.White, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            agent.error?.let { ErrorLine(it, onDismissError) }

            AnimatedVisibility(visible = agent.userText != null || agent.thinking) {
                AgentPanel(agent)
            }

            agent.confirm?.let { ConfirmCardView(it, onConfirm) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Add a task, or ask the assistant") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = AppColors.Line),
                    modifier = Modifier.weight(1f),
                )
                // Classic path: plain add, no AI.
                IconButton(
                    onClick = { onAddTyped(text); text = "" },
                    enabled = text.isNotBlank(),
                ) { Icon(Icons.Filled.Add, contentDescription = "Add as a task") }
                // AI path: send the typed text to the agent (handy on emulators without a mic).
                IconButton(
                    onClick = { onAsk(text); text = "" },
                    enabled = text.isNotBlank() && !agent.thinking,
                ) { Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask the assistant", tint = AppColors.Agent) }
            }

            Spacer(Modifier.height(12.dp))
            MicButton(
                listening = state.voice.listening,
                partial = state.voice.partial,
                enabled = !agent.thinking && agent.confirm == null,
                onClick = onMic,
            )
        }
    }
}

@Composable
private fun AgentPanel(agent: AgentState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.AgentTint),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(AppColors.Agent))
        Column(Modifier.padding(12.dp)) {
            agent.userText?.let {
                Text("You said: \"$it\"", fontSize = 13.sp, color = AppColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (agent.thinking) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(color = AppColors.Agent, modifier = Modifier.fillMaxWidth())
            }
            agent.reply?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 16.sp, color = AppColors.Ink)
            }
            if (agent.toolsCalled.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // Shows viewers the agent is calling real app functions, not just chatting.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    groupToolNames(agent.toolsCalled).forEach { name ->
                        Text(
                            "Tool: $name",
                            fontSize = 12.sp,
                            color = AppColors.Agent,
                            modifier = Modifier
                                .border(1.dp, AppColors.Agent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmCardView(card: ConfirmCard, onConfirm: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.5.dp, AppColors.Agent, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(card.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = AppColors.Ink)
        card.details.take(5).forEach {
            Text(it, fontSize = 14.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 2.dp))
        }
        if (card.details.size > 5) {
            Text("and ${card.details.size - 5} more", fontSize = 14.sp, color = AppColors.Muted)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onConfirm(false) }) { Text("Cancel") }
            Button(
                onClick = { onConfirm(true) },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Agent),
            ) { Text(card.approveLabel) }
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, partial: String, enabled: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (listening) AppColors.Overdue else AppColors.Agent,
        label = "micColor",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (listening) {
            Text(
                partial.ifBlank { "Listening…" },
                fontSize = 15.sp,
                color = AppColors.Ink,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (enabled || listening) color else AppColors.Line),
        ) {
            IconButton(onClick = onClick, enabled = enabled || listening, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (listening) "Stop listening" else "Speak to the assistant",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorLine(message: String, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Text(message, color = AppColors.Overdue, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

@Composable
fun TodoAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, content = content)
}

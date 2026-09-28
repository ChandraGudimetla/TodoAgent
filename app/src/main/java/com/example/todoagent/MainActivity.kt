package com.example.todoagent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todoagent.ui.TodoAgentScreen
import com.example.todoagent.ui.TodoAgentTheme
import com.example.todoagent.ui.MainViewModel
import com.example.todoagent.voice.SpeechInput

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TodoAgentTheme {
                val context = LocalContext.current
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var speechOn by rememberSaveable { mutableStateOf(true) }

                val speech = remember {
                    SpeechInput(
                        context = context,
                        onListeningChanged = viewModel::onListeningChanged,
                        onPartial = viewModel::onPartialTranscript,
                        onFinal = viewModel::onFinalTranscript,
                        onError = viewModel::onVoiceError,
                    )
                }
                DisposableEffect(Unit) { onDispose { speech.destroy() } }

                val micPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) speech.start()
                    else viewModel.onVoiceError("TodoAgent needs the microphone to hear you. You can still type.")
                }

                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* Reminders just stay silent if denied. */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                TodoAgentScreen(
                    state = state,
                    speechOn = speechOn,
                    onToggleSpeech = {
                        speechOn = !speechOn
                        viewModel.setSpeechEnabled(speechOn)
                    },
                    onAddTyped = viewModel::addTyped,
                    onAsk = viewModel::ask,
                    onToggleDone = viewModel::toggleDone,
                    onMic = {
                        viewModel.stopSpeaking()
                        when {
                            state.voice.listening -> speech.stop()
                            !speech.isAvailable ->
                                viewModel.onVoiceError("No speech recognizer on this device. Type instead.")
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED -> speech.start()
                            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onConfirm = viewModel::answerConfirm,
                    onDismissError = viewModel::dismissError,
                )
            }
        }
    }
}

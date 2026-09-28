package com.example.todoagent

import android.app.Application
import com.example.todoagent.agent.TaskAgent
import com.example.todoagent.agent.TaskTools
import com.example.todoagent.data.TaskDatabase
import com.example.todoagent.data.TaskRepository
import com.example.todoagent.reminders.ReminderScheduler
import com.example.todoagent.voice.Speaker
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize

/** Manual dependency container. Swap for Hilt later if the app grows. */
class TodoAgentApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // App Check: proves requests to Firebase AI Logic come from this app.
        // The debug provider is for development only; use Play Integrity for a released app.
        Firebase.initialize(this)
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }

    val repository by lazy {
        TaskRepository(TaskDatabase.create(this).taskDao(), ReminderScheduler(this))
    }
    val agent by lazy { TaskAgent(this, TaskTools(repository)) }
    val speaker by lazy { Speaker(this) }
}
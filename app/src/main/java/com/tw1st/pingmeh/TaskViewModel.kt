package com.tw1st.pingmeh

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tw1st.pingmeh.data.AppDatabase
import com.tw1st.pingmeh.data.Task
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val dao = AppDatabase.getDatabase(application).taskDao()

    val allTasks: StateFlow<List<Task>> = dao.getAllTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTask(appPackage: String, hour: Int, minute: Int) {
        viewModelScope.launch {
            val task = Task(
                title = appPackage,
                appPackageName = appPackage,
                resetHour = hour,
                resetMinute = minute
            )
            val id = dao.insertTask(task)
            val insertedTask = task.copy(id = id.toInt())
            NotificationScheduler.scheduleAlarmsForTask(app, insertedTask)
            PingMehWidget.updateAll(app)
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(
                isCompleted = !task.isCompleted,
                lastCompletedTimestamp = System.currentTimeMillis()
            )
            dao.updateTask(updatedTask)
            if (updatedTask.isCompleted) {
                // Cancel pre-reset reminders, but KEEP the reset alarm scheduled!
                NotificationScheduler.cancelAlarmsForTask(app, updatedTask, cancelResetAlarm = false)
            } else {
                NotificationScheduler.scheduleAlarmsForTask(app, updatedTask)
            }
            PingMehWidget.updateAll(app)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.deleteTask(task)
            NotificationScheduler.cancelAlarmsForTask(app, task, cancelResetAlarm = true)
            PingMehWidget.updateAll(app)
        }
    }
}

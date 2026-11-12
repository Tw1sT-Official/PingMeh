package com.tw1st.pingmeh

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.tw1st.pingmeh.data.AppDatabase
import java.util.Calendar

class ResetWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(context).taskDao()
        val tasks = dao.getAllTasksSync()

        var didResetAny = false
        val now = Calendar.getInstance()
        val currentMillis = now.timeInMillis

        tasks.forEach { task ->
            if (task.isCompleted) {
                // Calculate the most recent occurrence of this task's reset time
                val resetCal = Calendar.getInstance()
                resetCal.set(Calendar.HOUR_OF_DAY, task.resetHour)
                resetCal.set(Calendar.MINUTE, task.resetMinute)
                resetCal.set(Calendar.SECOND, 0)
                resetCal.set(Calendar.MILLISECOND, 0)

                // If the reset time for today is in the future, then the *most recent* reset time was yesterday
                if (resetCal.timeInMillis > currentMillis) {
                    resetCal.add(Calendar.DAY_OF_YEAR, -1)
                }

                val mostRecentResetMillis = resetCal.timeInMillis

                // If the task was completed before the most recent reset time, it means it was completed "yesterday"
                // and should now be reset!
                if (task.lastCompletedTimestamp < mostRecentResetMillis) {
                    val updatedTask = task.copy(isCompleted = false)
                    dao.updateTask(updatedTask)
                    NotificationScheduler.scheduleAlarmsForTask(context, updatedTask)
                    didResetAny = true
                }
            }
        }

        if (didResetAny) {
            // Update the widget to reflect the reset tasks
            PingMehWidget.updateAll(context)
        }

        return Result.success()
    }
}

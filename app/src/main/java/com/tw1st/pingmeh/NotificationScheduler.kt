package com.tw1st.pingmeh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.tw1st.pingmeh.data.Task
import java.util.Calendar

object NotificationScheduler {
    // 3h, 2h, 1h, 30m, 10m before reset
    val reminderOffsetsMinutes = listOf(180, 120, 60, 30, 10)
    const val RESET_OFFSET_MINUTES = 0

    fun scheduleAlarmsForTask(context: Context, task: Task) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("NotificationScheduler", "Cannot schedule exact alarms - missing permission")
                return
            }
        }

        // Clean slate for this task before setting fresh alarms
        cancelAlarmsForTask(context, task, cancelResetAlarm = true)

        val now = Calendar.getInstance()
        val resetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, task.resetHour)
            set(Calendar.MINUTE, task.resetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (resetTime.before(now)) {
            resetTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 1. ALWAYS schedule the exact reset alarm (offset = 0)
        // This ensures the task automatically unchecks itself and refreshes the widget when reset time arrives,
        // even if the user completed the task earlier!
        scheduleSingleAlarm(context, alarmManager, task, resetTime, RESET_OFFSET_MINUTES)

        // 2. If the task is NOT completed, schedule pre-reset countdown reminders
        if (!task.isCompleted) {
            reminderOffsetsMinutes.forEach { offsetMinutes ->
                scheduleSingleAlarm(context, alarmManager, task, resetTime, offsetMinutes)
            }
        }
    }

    private fun scheduleSingleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        task: Task,
        resetTime: Calendar,
        offsetMinutes: Int
    ) {
        val now = Calendar.getInstance()
        val alarmTime = resetTime.clone() as Calendar
        alarmTime.add(Calendar.MINUTE, -offsetMinutes)

        if (alarmTime.after(now)) {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("TASK_ID", task.id)
                putExtra("TASK_TITLE", task.title)
                putExtra("APP_NAME", task.appPackageName)
                putExtra("OFFSET_MINUTES", offsetMinutes)
            }

            val requestCode = (task.id * 1000 + offsetMinutes)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime.timeInMillis,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                Log.e("NotificationScheduler", "SecurityException scheduling alarm", e)
            }
        }
    }

    fun cancelAlarmsForTask(context: Context, task: Task, cancelResetAlarm: Boolean = true) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val offsetsToCancel = if (cancelResetAlarm) {
            reminderOffsetsMinutes + RESET_OFFSET_MINUTES
        } else {
            reminderOffsetsMinutes
        }

        offsetsToCancel.forEach { offsetMinutes ->
            val intent = Intent(context, NotificationReceiver::class.java)
            val requestCode = (task.id * 1000 + offsetMinutes)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}

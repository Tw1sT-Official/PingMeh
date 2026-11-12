package com.tw1st.pingmeh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tw1st.pingmeh.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("TASK_ID", -1)
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Task"
        val appName = intent.getStringExtra("APP_NAME") ?: "App"
        val offsetMinutes = intent.getIntExtra("OFFSET_MINUTES", -1)

        if (taskId == -1 || offsetMinutes == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).taskDao()
                val task = dao.getTaskById(taskId)

                if (offsetMinutes == 0) {
                    // EXACT RESET TIME HIT!
                    if (task != null) {
                        val resetTask = task.copy(
                            isCompleted = false,
                            lastCompletedTimestamp = 0L
                        )
                        dao.updateTask(resetTask)
                        // Schedule alarms for the new day
                        NotificationScheduler.scheduleAlarmsForTask(context, resetTask)
                    }

                    showNotification(
                        context = context,
                        notificationId = taskId,
                        title = "Ping Meh - $appName",
                        message = "$appName has reset for today! Ready to do."
                    )
                } else {
                    // Pre-reset reminder
                    if (task != null && !task.isCompleted) {
                        val timeString = when {
                            offsetMinutes >= 60 -> "${offsetMinutes / 60} hour${if (offsetMinutes >= 120) "s" else ""} left"
                            else -> "$offsetMinutes minutes left"
                        }

                        showNotification(
                            context = context,
                            notificationId = taskId,
                            title = "Ping Meh - $appName",
                            message = "$appName: $timeString"
                        )
                    }
                }

                // Update the home screen widget immediately whenever an alarm fires!
                PingMehWidget.updateAll(context)
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Error processing notification alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "pingmeh_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for pending tasks"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}

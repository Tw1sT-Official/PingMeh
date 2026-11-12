package com.tw1st.pingmeh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tw1st.pingmeh.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d("BootReceiver", "Boot completed or app updated. Restoring alarms...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getDatabase(context).taskDao()
                    val tasks = dao.getAllTasksSync()
                    tasks.forEach { task ->
                        NotificationScheduler.scheduleAlarmsForTask(context, task)
                    }
                    PingMehWidget.updateAll(context)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error restoring alarms on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

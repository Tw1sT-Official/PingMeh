package com.tw1st.pingmeh

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tw1st.pingmeh.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

fun getTimeUntilResetMinutes(resetHour: Int, resetMinute: Int): Int {
    val now = Calendar.getInstance()
    val currentHour = now.get(Calendar.HOUR_OF_DAY)
    val currentMinute = now.get(Calendar.MINUTE)

    val currentTotalMinutes = currentHour * 60 + currentMinute
    val resetTotalMinutes = resetHour * 60 + resetMinute

    return if (resetTotalMinutes > currentTotalMinutes) {
        resetTotalMinutes - currentTotalMinutes
    } else {
        (24 * 60 - currentTotalMinutes) + resetTotalMinutes
    }
}

// Memory-cached icon loader so widget never blocks or lags on click
object AppIconCache {
    private val iconCache = LruCache<String, Bitmap>(50)
    private val nameToPackageMap = ConcurrentHashMap<String, String>()
    @Volatile private var allPackagesCached = false

    fun getIcon(context: Context, nameOrPackage: String): Bitmap? {
        synchronized(iconCache) {
            val cached = iconCache.get(nameOrPackage)
            if (cached != null) return cached
        }

        val pm = context.packageManager
        var resolvedPackage = nameToPackageMap[nameOrPackage] ?: nameOrPackage
        var drawable: android.graphics.drawable.Drawable? = null

        try {
            drawable = pm.getApplicationIcon(resolvedPackage)
        } catch (_: Exception) {
            if (!allPackagesCached) {
                try {
                    val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
                    for (info in resolveInfos) {
                        val label = info.loadLabel(pm).toString()
                        val pkg = info.activityInfo.packageName
                        nameToPackageMap[label] = pkg
                        nameToPackageMap[pkg] = pkg
                    }
                    allPackagesCached = true
                } catch (_: Exception) {}
            }
            resolvedPackage = nameToPackageMap[nameOrPackage] ?: return null
            try {
                drawable = pm.getApplicationIcon(resolvedPackage)
            } catch (_: Exception) {
                return null
            }
        }

        val bitmap = try {
            val size = 48
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }

        if (bitmap != null) {
            synchronized(iconCache) {
                iconCache.put(nameOrPackage, bitmap)
                iconCache.put(resolvedPackage, bitmap)
            }
        }
        return bitmap
    }
}

class PingMehWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        val refreshKey = intPreferencesKey("refresh_counter")

        suspend fun updateWidget(context: Context, glanceId: GlanceId) {
            try {
                updateAppWidgetState(context, glanceId) { prefs ->
                    val current = prefs[refreshKey] ?: 0
                    prefs[refreshKey] = current + 1
                }
                PingMehWidget().update(context, glanceId)
            } catch (e: Exception) {
                android.util.Log.e("PingMehWidget", "Error updating widget", e)
            }
        }

        suspend fun updateAll(context: Context) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(PingMehWidget::class.java)
                glanceIds.forEach { glanceId ->
                    updateWidget(context, glanceId)
                }
            } catch (e: Exception) {
                android.util.Log.e("PingMehWidget", "Error updating widgets", e)
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.getDatabase(context).taskDao()

        provideContent {
            // Subscribe to state changes so Compose never skips recomposition on update()
            val prefs = currentState<Preferences>()
            val refreshCounter = prefs[refreshKey] ?: 0

            val tasks = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                dao.getAllTasksSync()
            }

            val sortedTasks = tasks.sortedWith(compareBy(
                { it.isCompleted }, // Uncompleted first
                { getTimeUntilResetMinutes(it.resetHour, it.resetMinute) } // Then soonest reset
            ))

            val pendingCount = sortedTasks.count { !it.isCompleted }

            // Space-optimized outer container with smooth rounded corners
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(ColorProvider(Color(0xFF141416)))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start
            ) {
                // Header Bar: App Name + Status + Refresh Button
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PingMeh",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFF453A)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    val statusText = if (sortedTasks.isEmpty()) {
                        "No tasks"
                    } else if (pendingCount == 0) {
                        "All done! 🎉"
                    } else {
                        "$pendingCount pending"
                    }

                    val statusColor = if (pendingCount == 0 && sortedTasks.isNotEmpty()) {
                        Color(0xFF34C759)
                    } else {
                        Color(0xFF8E8E93)
                    }

                    Text(
                        text = "• $statusText",
                        style = TextStyle(
                            color = ColorProvider(statusColor),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    // Manual refresh button with comfortable tap target
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(8.dp)
                            .background(ColorProvider(Color(0xFF222226)))
                            .clickable(actionRunCallback<RefreshWidgetAction>())
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⟳",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFE4E4E7)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }

                if (sortedTasks.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No apps added yet! Tap + in app.",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF71717A)),
                                fontSize = 12.sp
                            )
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        // Pass stable item ID so ListView never mixes up click handlers when items change order
                        items(sortedTasks, itemId = { it.id.toLong() }) { task ->
                            val toggleAction = actionRunCallback<ToggleTaskAction>(
                                actionParametersOf(
                                    ActionParameters.Key<Int>("taskId") to task.id
                                )
                            )

                            val minutesUntil = getTimeUntilResetMinutes(task.resetHour, task.resetMinute)
                            val hoursLeft = minutesUntil / 60
                            val minsLeft = minutesUntil % 60

                            val timeString = if (task.isCompleted) {
                                "Done ✓"
                            } else if (hoursLeft > 0) {
                                "${hoursLeft}h left"
                            } else {
                                val roundedMins = ((minsLeft + 5) / 10) * 10
                                if (roundedMins == 60) "1h left"
                                else if (roundedMins == 0) "<10m left"
                                else "${roundedMins}m left"
                            }

                            // Dynamic pill color based on urgency
                            val badgeBgColor = when {
                                task.isCompleted -> Color(0xFF222226)
                                minutesUntil <= 60 -> Color(0xFF451A1A) // Alert red container
                                minutesUntil <= 180 -> Color(0xFF3E2D12) // Alert amber container
                                else -> Color(0xFF222226) // Normal dark
                            }

                            val badgeTextColor = when {
                                task.isCompleted -> Color(0xFF71717A)
                                minutesUntil <= 60 -> Color(0xFFFF453A)
                                minutesUntil <= 180 -> Color(0xFFFF9F0A)
                                else -> Color(0xFFE4E4E7)
                            }

                            val textDecoration = if (task.isCompleted) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            }

                            val titleColor = if (task.isCompleted) {
                                Color(0xFF636366)
                            } else {
                                Color(0xFFFFFFFF)
                            }

                            // Instant cache lookup
                            val appIconBitmap = AppIconCache.getIcon(context, task.appPackageName)

                            // Fixed-height card row (guarantees cards NEVER shrink or squish when multiple tasks exist)
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .cornerRadius(12.dp)
                                    .background(ColorProvider(Color(0xFF1C1C1E)))
                                    .clickable(toggleAction)
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // App Icon or Initial Fallback
                                if (appIconBitmap != null) {
                                    Image(
                                        provider = ImageProvider(appIconBitmap),
                                        contentDescription = "App Icon",
                                        modifier = GlanceModifier
                                            .size(28.dp)
                                            .cornerRadius(6.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = GlanceModifier
                                            .size(28.dp)
                                            .cornerRadius(6.dp)
                                            .background(ColorProvider(Color(0xFF2C2C2E))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val initial = task.appPackageName.firstOrNull()?.uppercase() ?: "•"
                                        Text(
                                            text = initial,
                                            style = TextStyle(
                                                color = ColorProvider(Color(0xFFFF453A)),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = GlanceModifier.width(10.dp))

                                // App Name
                                Column(
                                    modifier = GlanceModifier.defaultWeight(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = task.appPackageName,
                                        style = TextStyle(
                                            color = ColorProvider(titleColor),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            textDecoration = textDecoration
                                        ),
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = GlanceModifier.width(8.dp))

                                // Urgency Badge Pill
                                Box(
                                    modifier = GlanceModifier
                                        .cornerRadius(8.dp)
                                        .background(ColorProvider(badgeBgColor))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = timeString,
                                        style = TextStyle(
                                            color = ColorProvider(badgeTextColor),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = GlanceModifier.fillMaxWidth().height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[ActionParameters.Key<Int>("taskId")] ?: return
        val dao = AppDatabase.getDatabase(context).taskDao()

        withContext(Dispatchers.IO) {
            val task = dao.getTaskById(taskId)
            if (task != null) {
                val updatedTask = task.copy(
                    isCompleted = !task.isCompleted,
                    lastCompletedTimestamp = if (!task.isCompleted) System.currentTimeMillis() else 0L
                )
                dao.updateTask(updatedTask)
                if (updatedTask.isCompleted) {
                    NotificationScheduler.cancelAlarmsForTask(context, updatedTask, cancelResetAlarm = false)
                } else {
                    NotificationScheduler.scheduleAlarmsForTask(context, updatedTask)
                }
            }
        }

        // Increment refresh state and update widget immediately
        PingMehWidget.updateWidget(context, glanceId)
        PingMehWidget.updateAll(context)
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        PingMehWidget.updateWidget(context, glanceId)
        PingMehWidget.updateAll(context)
    }
}

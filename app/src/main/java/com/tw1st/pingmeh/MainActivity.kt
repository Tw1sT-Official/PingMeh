package com.tw1st.pingmeh

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tw1st.pingmeh.data.Task
import com.tw1st.pingmeh.ui.theme.PingMehTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class InstalledApp(val name: String, val packageName: String)

class MainActivity : ComponentActivity() {
    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Full screen immersive mode
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        // Periodic reset worker as fallback safety net
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<ResetWorker>(
            1, java.util.concurrent.TimeUnit.HOURS
        ).build()

        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "daily_task_reset",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        enableEdgeToEdge()
        setContent {
            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(1500)
                showSplash = false
            }

            PingMehTheme {
                if (showSplash) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_splash_logo),
                            contentDescription = "Splash Logo",
                            modifier = Modifier.fillMaxWidth(0.6f),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    val tasks by viewModel.allTasks.collectAsState()
                    var showAddDialog by remember { mutableStateOf(false) }

                    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
                    LaunchedEffect(Unit) {
                        installedApps = loadInstalledApps(this@MainActivity)
                    }

                    var taskToDelete by remember { mutableStateOf<Task?>(null) }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp, bottom = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "- Ping Meh -",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF0000)
                                )
                            }
                        },
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = { showAddDialog = true },
                                containerColor = Color(0xFFFF0000),
                                contentColor = Color.Black
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Task")
                            }
                        }
                    ) { innerPadding ->
                        TaskListScreen(
                            tasks = tasks,
                            onToggle = { viewModel.toggleTask(it) },
                            onDelete = { taskToDelete = it },
                            modifier = Modifier.padding(innerPadding)
                        )

                        if (showAddDialog) {
                            AddTaskDialog(
                                installedApps = installedApps,
                                onDismiss = { showAddDialog = false },
                                onConfirm = { app, hr, min ->
                                    viewModel.addTask(app, hr, min)
                                    showAddDialog = false
                                }
                            )
                        }

                        taskToDelete?.let { task ->
                            AlertDialog(
                                onDismissRequest = { taskToDelete = null },
                                title = { Text("Delete App") },
                                text = { Text("Are you sure you want to delete '${task.appPackageName}'?") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.deleteTask(task)
                                            taskToDelete = null
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF0000),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { taskToDelete = null }) {
                                        Text("Cancel", color = Color.White)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadInstalledApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
        resolveInfos
            .mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                InstalledApp(
                    name = resolveInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.name }
    }
}

@Composable
fun TaskListScreen(
    tasks: List<Task>,
    onToggle: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No apps added yet! Click the + button to add one.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks) { task ->
                TaskCard(task, onToggle, onDelete)
            }
        }
    }
}

@Composable
fun TaskCard(task: Task, onToggle: (Task) -> Unit, onDelete: (Task) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(task) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            val textColor = if (task.isCompleted) Color.Gray else Color.White

            Text(
                text = task.appPackageName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textDecoration = textDecoration
            )

            val isPm = task.resetHour >= 12
            val displayHour = if (task.resetHour % 12 == 0) 12 else task.resetHour % 12
            val amPm = if (isPm) "PM" else "AM"
            val timeString = String.format("%02d:%02d %s", displayHour, task.resetMinute, amPm)

            Text(
                text = "Resets daily at $timeString",
                style = MaterialTheme.typography.bodySmall,
                color = if (task.isCompleted) Color.DarkGray else Color.LightGray,
                textDecoration = textDecoration
            )
        }
        IconButton(onClick = { onDelete(task) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF0000))
        }
    }
}

@Composable
fun AddTaskDialog(
    installedApps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int) -> Unit
) {
    var appName by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("12") }
    var minute by remember { mutableStateOf("00") }
    var isPm by remember { mutableStateOf(false) } // true for PM, false for AM

    // Filter apps smoothly without locking keyboard
    val filteredApps = remember(appName, installedApps) {
        if (appName.isBlank()) {
            emptyList()
        } else {
            installedApps.filter {
                it.name.contains(appName, ignoreCase = true)
            }.take(4)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add App Reset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // App Search Input
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Name") },
                    placeholder = { Text("e.g. Genshin, Duolingo") },
                    singleLine = true,
                    trailingIcon = {
                        if (appName.isNotEmpty()) {
                            IconButton(onClick = { appName = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear text")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Inline suggestions that do NOT steal keyboard focus or interrupt backspace
                if (filteredApps.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column {
                            filteredApps.forEachIndexed { index, app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { appName = app.name }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = app.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                }
                                if (index < filteredApps.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(0.5.dp)
                                            .background(Color.Gray.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }
                }

                // Time picker row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { if (it.length <= 2) hour = it },
                        label = { Text("Hour (1-12)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { if (it.length <= 2) minute = it },
                        label = { Text("Min (0-59)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    // AM/PM Toggle Button
                    OutlinedButton(
                        onClick = { isPm = !isPm },
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(if (isPm) "PM" else "AM")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawHour = hour.toIntOrNull() ?: 12
                    val m = minute.toIntOrNull() ?: 0

                    var finalHour = rawHour % 12
                    if (isPm) {
                        finalHour += 12
                    }

                    onConfirm(appName.trim(), finalHour, m)
                },
                enabled = appName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

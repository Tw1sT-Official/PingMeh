package com.tw1st.pingmeh.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "", // Defaulted for Room SQLite compatibility
    val appPackageName: String, // The name of the app (e.g. Genshin Impact)
    val resetHour: Int, // The hour it resets (0-23)
    val resetMinute: Int, // The minute it resets
    val isCompleted: Boolean = false,
    val lastCompletedTimestamp: Long = 0L // To know when to un-check it
)

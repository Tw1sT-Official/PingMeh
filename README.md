# PingMeh ⏰

**PingMeh** is a modern, lightweight Android utility app built with **Kotlin**, **Jetpack Compose**, and **Jetpack Compose Glance**. It tracks daily reset deadlines for your mobile games and daily habit apps (e.g., Genshin Impact, Honkai: Star Rail, Duolingo, etc.), ensuring you never miss a daily check-in or reset.

---

## ✨ Features

- **Interactive Home Screen Widget (Jetpack Compose Glance)**:
  - One-tap check and uncheck directly from your home screen.
  - Urgency countdown badges (`<10m left`, `1h left`, `3h left`, `Done ✓`).
  - Displays real installed application icons dynamically with zero lag.
  - Fully versatile vertical and horizontal resizing (1 row to full screen).
  - Manual `⟳` refresh button with instant feedback.
- **Self-Resetting Alarms (The "Midnight Trigger")**:
  - Automatically un-checks completed apps and rolls over countdowns when daily reset times hit.
  - Works in the background using `AlarmManager.setExactAndAllowWhileIdle`.
- **Pre-Reset Milestone Notifications**:
  - Reminders at **3h**, **2h**, **1h**, **30m**, and **10m** before reset.
  - Reset moment announcement notification when a new daily cycle begins.
- **Reboot Persistence**:
  - Automatically re-registers all exact alarms on device boot (`BOOT_COMPLETED`).
- **Smooth App Search**:
  - Fast search through installed launcher apps with zero keyboard lag.
- **Offline & Private**:
  - 100% local Room SQLite database with no network tracking or analytics.

---

## 🛠️ Tech Stack

- **Language**: Kotlin 2.0
- **UI Framework**: Jetpack Compose (Material 3)
- **Home Widget**: Jetpack Compose Glance (`androidx.glance:glance-appwidget`)
- **Database**: Room Database (SQLite) + Kotlin Coroutines & Flow
- **Background Scheduling**: Android `AlarmManager` + `BroadcastReceiver` + `WorkManager`
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 35)

---

## 🚀 Building & Installing

1. Clone the repository:
   ```bash
   git clone https://github.com/Tw1sT-Official/PingMeh.git
   ```
2. Open the project in **Android Studio Ladybug** (or later).
3. Build the debug APK via terminal:
   ```bash
   ./gradlew assembleDebug
   ```
4. Output APK location:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 License
This project is open-source. Feel free to contribute or adapt it for your own daily routine!

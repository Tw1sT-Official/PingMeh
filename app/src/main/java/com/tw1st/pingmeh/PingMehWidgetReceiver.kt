package com.tw1st.pingmeh

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

// This receiver catches the Android System broadcast to update the Home Screen Widget
class PingMehWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PingMehWidget()
}

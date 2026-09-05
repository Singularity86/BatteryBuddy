package jibaro.etherdrive.reserve.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BatteryWidget()
}

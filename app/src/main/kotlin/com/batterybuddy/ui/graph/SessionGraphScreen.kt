package com.batterybuddy.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batterybuddy.data.model.BatteryReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun SessionGraphScreen(
    viewModel: SessionGraphViewModel,
    sessionId: Long,
    isCharge: Boolean
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId, isCharge) {
        if (isCharge) viewModel.loadCharge(sessionId)
        else viewModel.loadDischarge(sessionId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (val s = state) {
            is GraphUiState.Loading -> {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is GraphUiState.Empty -> {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No readings recorded for this session yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is GraphUiState.Content -> GraphContent(s)
        }
    }
}

@Composable
private fun GraphContent(state: GraphUiState.Content) {
    val readings = state.readings
    val isCharge = state.isCharge
    val first = readings.first()
    val last = readings.last()
    val isLive = (System.currentTimeMillis() - last.timestamp) < 10 * 60_000L

    if (isLive) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = if (isCharge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        ) {
            Text(
                text = if (isCharge) "LIVE · CHARGING" else "LIVE · DISCHARGING",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isCharge) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondary
            )
        }
    }

    val durationMin = max(0L, (last.timestamp - first.timestamp) / 60_000L).toInt()
    val durationStr = when {
        durationMin >= 60 -> "${durationMin / 60}h ${durationMin % 60}m"
        else -> "${durationMin}m"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip("${first.batteryPercent}% → ${last.batteryPercent}%")
        StatChip(durationStr)
        if (isCharge) {
            val peakW = readings.maxOf { it.chargingPowerWatts }
            if (peakW > 0f) StatChip("↑ %.0fW peak".format(peakW))
        } else {
            val dropped = (first.batteryPercent - last.batteryPercent).coerceAtLeast(0)
            if (dropped > 0) StatChip("↓ $dropped% drained")
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendLine(primary, "Battery %")
        if (isCharge) LegendLine(secondary, "Power (W)")
    }

    SessionLineChart(readings, isCharge)

    val peakTempC = readings.maxOf { it.temperatureCelsius }
    if (peakTempC > 0f) {
        val tempColor = when {
            peakTempC > 40f -> MaterialTheme.colorScheme.error
            peakTempC > 35f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            "Peak temp: ${peakTempC.toInt()}°C",
            style = MaterialTheme.typography.bodySmall,
            color = tempColor
        )
    }

    if (isLive) {
        Text(
            "Updates as new readings arrive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatChip(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun LegendLine(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(modifier = Modifier.size(20.dp, 3.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionLineChart(
    readings: List<BatteryReading>,
    isCharge: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    if (readings.size < 2) {
        Box(modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text("Need at least two readings to draw a chart.", color = outlineColor)
        }
        return
    }

    val startTs = readings.first().timestamp
    val endTs = readings.last().timestamp
    val totalMs = (endTs - startTs).coerceAtLeast(60_000L).toFloat()
    val maxWatts = if (isCharge) readings.maxOf { it.chargingPowerWatts }.coerceAtLeast(1f) else 0f

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val padL = 40.dp.toPx()
        val padR = if (isCharge) 40.dp.toPx() else 8.dp.toPx()
        val padT = 8.dp.toPx()
        val padB = 26.dp.toPx()
        val chartW = (size.width - padL - padR).coerceAtLeast(1f)
        val chartH = (size.height - padT - padB).coerceAtLeast(1f)

        fun xOf(ts: Long) = padL + (ts - startTs).toFloat() / totalMs * chartW
        fun yOfPct(p: Int) = padT + (1f - p / 100f) * chartH
        fun yOfWatts(w: Float) = padT + (1f - (w / maxWatts).coerceIn(0f, 1f)) * chartH

        // Background tint
        drawRect(
            color = surfaceVariantColor.copy(alpha = 0.15f),
            topLeft = Offset(padL, padT),
            size = androidx.compose.ui.geometry.Size(chartW, chartH)
        )

        // Horizontal grid at 25, 50, 75%
        for (pct in listOf(25, 50, 75)) {
            val y = yOfPct(pct)
            drawLine(outlineColor.copy(alpha = 0.12f), Offset(padL, y), Offset(padL + chartW, y), 1.dp.toPx())
        }

        // Battery % fill
        val fillPath = Path()
        readings.forEachIndexed { i, r ->
            val x = xOf(r.timestamp); val y = yOfPct(r.batteryPercent)
            if (i == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
        }
        fillPath.lineTo(xOf(endTs), padT + chartH)
        fillPath.lineTo(xOf(startTs), padT + chartH)
        fillPath.close()
        drawPath(fillPath, primaryColor.copy(alpha = 0.08f))

        // Battery % stroke
        val pctPath = Path()
        readings.forEachIndexed { i, r ->
            val x = xOf(r.timestamp); val y = yOfPct(r.batteryPercent)
            if (i == 0) pctPath.moveTo(x, y) else pctPath.lineTo(x, y)
        }
        drawPath(pctPath, primaryColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Power line (charge only)
        if (isCharge && maxWatts > 1f) {
            val wPath = Path()
            readings.forEachIndexed { i, r ->
                val x = xOf(r.timestamp); val y = yOfWatts(r.chargingPowerWatts)
                if (i == 0) wPath.moveTo(x, y) else wPath.lineTo(x, y)
            }
            drawPath(wPath, secondaryColor.copy(alpha = 0.75f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        }

        // Axes
        drawLine(outlineColor.copy(alpha = 0.35f), Offset(padL, padT), Offset(padL, padT + chartH), 1.dp.toPx())
        drawLine(outlineColor.copy(alpha = 0.35f), Offset(padL, padT + chartH), Offset(padL + chartW, padT + chartH), 1.dp.toPx())

        // Y-axis % labels
        val lblPaint = android.graphics.Paint().apply {
            color = outlineColor.copy(alpha = 0.8f).toArgb()
            textSize = 9.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        for (pct in listOf(0, 50, 100)) {
            val y = yOfPct(pct)
            val fm = lblPaint.fontMetrics
            drawContext.canvas.nativeCanvas.drawText("$pct%", padL - 4f, y - (fm.ascent + fm.descent) / 2, lblPaint)
        }

        // Right-axis W labels (charge)
        if (isCharge && maxWatts > 1f) {
            val wPaint = android.graphics.Paint().apply {
                color = secondaryColor.copy(alpha = 0.7f).toArgb()
                textSize = 9.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }
            val fm = wPaint.fontMetrics
            drawContext.canvas.nativeCanvas.drawText("%.0fW".format(maxWatts), padL + chartW + 4f, padT - (fm.ascent + fm.descent) / 2, wPaint)
            drawContext.canvas.nativeCanvas.drawText("0W", padL + chartW + 4f, padT + chartH - (fm.ascent + fm.descent) / 2, wPaint)
        }

        // X-axis time labels
        val tPaint = android.graphics.Paint().apply {
            color = outlineColor.copy(alpha = 0.7f).toArgb()
            textSize = 9.sp.toPx()
            isAntiAlias = true
        }
        val timeY = padT + chartH + 16.dp.toPx()
        tPaint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(formatShortTime(startTs), padL, timeY, tPaint)
        tPaint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(formatShortTime(endTs), padL + chartW, timeY, tPaint)
    }
}

private fun formatShortTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

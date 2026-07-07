package com.lqborges.garminpacecharts.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lqborges.garminpacecharts.domain.ChartDataBuilder
import com.lqborges.garminpacecharts.domain.PaceFormatter
import com.lqborges.garminpacecharts.domain.model.AxisMarkerType
import com.lqborges.garminpacecharts.domain.model.ChartData
import com.lqborges.garminpacecharts.domain.model.Workout
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private class ChartTextPaints {
    val pointLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = android.graphics.Color.DKGRAY
        textAlign = Paint.Align.CENTER
    }
    val averagePace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val weekLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = android.graphics.Color.DKGRAY
        textAlign = Paint.Align.CENTER
    }
    val monthLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = android.graphics.Color.GRAY
        textAlign = Paint.Align.CENTER
    }
    val yearLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
}

@Composable
fun PaceChart(
    chartData: ChartData,
    modifier: Modifier = Modifier,
    onWorkoutSelected: (Workout) -> Unit = {},
) {
    if (chartData.weeks.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(240.dp)) {
            Text("No workouts in this range")
        }
        return
    }

    var selectedWorkout by remember { mutableStateOf<Workout?>(null) }
    var scale by remember(chartData.title) { mutableFloatStateOf(1f) }
    var offsetX by remember(chartData.title) { mutableFloatStateOf(0f) }
    var isTransforming by remember { mutableStateOf(false) }
    val textPaints = remember { ChartTextPaints() }
    val density = LocalDensity.current
    val weekCount = chartData.weeks.size

    val allPaces = remember(chartData) {
        chartData.weeks.flatMap { week ->
            week.workouts.map { it.paceMinPerKm } + listOf(week.averagePace)
        }
    }
    val minPace = allPaces.min() - 0.2
    val maxPace = allPaces.max() + 0.2

    LaunchedEffect(scale, offsetX) {
        isTransforming = true
        delay(80)
        isTransforming = false
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .clipToBounds()
            .background(Color(0xFFFAFAFA)),
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val minWeekWidthPx = with(density) { 56.dp.toPx() }
        val leftPaddingPx = with(density) { 48.dp.toPx() }
        val rightPaddingPx = with(density) { 24.dp.toPx() }

        fun contentWidthPx(scaleValue: Float): Float =
            max(
                viewportWidthPx,
                leftPaddingPx + weekCount * minWeekWidthPx * scaleValue * 0.9f + rightPaddingPx,
            )

        fun minOffsetPx(scaleValue: Float): Float =
            min(0f, viewportWidthPx - contentWidthPx(scaleValue))

        val weekWidthPx = minWeekWidthPx * scale * 0.9f
        val minLabelSpacingPx = with(density) { 52.dp.toPx() }
        val labelStride = ChartDataBuilder.weekLabelStride(weekWidthPx, minLabelSpacingPx)
        val axisMarkers = remember(chartData) { ChartDataBuilder.buildAxisMarkers(chartData.weeks) }
        val monthSpans = remember(chartData) { ChartDataBuilder.buildMonthSpans(chartData.weeks) }
        val canvasWidthPx = contentWidthPx(scale)
        val canvasWidthDp = with(density) { canvasWidthPx.toDp() }
        val clampedOffsetX = offsetX.coerceIn(minOffsetPx(scale), 0f)

        LaunchedEffect(chartData.title, weekCount, viewportWidthPx) {
            scale = 1f
            offsetX = minOffsetPx(1f)
        }

        // Gesture layer spans the viewport; chart canvas is wider and pans underneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(chartData.title, weekCount, viewportWidthPx) {
                    var gestureScale = scale
                    var gestureOffset = offsetX

                    fun minOff(): Float = min(0f, viewportWidthPx - contentWidthPx(gestureScale))

                    fun applyOffset(deltaX: Float) {
                        gestureOffset = (gestureOffset + deltaX).coerceIn(minOff(), 0f)
                        offsetX = gestureOffset
                    }

                    coroutineScope {
                        launch {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                applyOffset(dragAmount)
                            }
                        }
                        launch {
                            detectTransformGestures { _, pan, zoom, _ ->
                                gestureScale = (gestureScale * zoom).coerceIn(0.6f, 4f)
                                applyOffset(pan.x)
                                scale = gestureScale
                            }
                        }
                    }
                },
        ) {
            Canvas(
                modifier = Modifier
                    .width(canvasWidthDp)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = clampedOffsetX },
            ) {
                val top = 28f
                val bottom = size.height - 52f
                val height = bottom - top
                val startX = leftPaddingPx
                val drawTextLabels = !isTransforming
                val showWeekLabels = labelStride <= 3
                val showAveragePaceLabels = weekWidthPx >= minLabelSpacingPx * 0.75f

                fun paceY(pace: Double): Float {
                    val ratio = ((pace - minPace) / (maxPace - minPace)).toFloat()
                    return top + ratio * height
                }

                fun weekStartX(index: Int): Float =
                    startX + index * weekWidthPx

                axisMarkers.forEach { marker ->
                    val x = weekStartX(marker.weekIndex)
                    val lineColor = when (marker.type) {
                        AxisMarkerType.YEAR -> Color(0x66000000)
                        AxisMarkerType.MONTH -> Color(0x33000000)
                    }
                    drawLine(
                        color = lineColor,
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = if (marker.type == AxisMarkerType.YEAR) 2f else 1f,
                    )
                }

                if (drawTextLabels) {
                    axisMarkers.filter { it.type == AxisMarkerType.YEAR }.forEach { marker ->
                        drawContext.canvas.nativeCanvas.drawText(
                            marker.label,
                            weekStartX(marker.weekIndex) + weekWidthPx / 2f,
                            18f,
                            textPaints.yearLabel,
                        )
                    }

                    monthSpans.forEach { span ->
                        val centerX = (weekStartX(span.startWeekIndex) + weekStartX(span.endWeekIndex) + weekWidthPx) / 2f
                        drawContext.canvas.nativeCanvas.drawText(
                            span.label,
                            centerX,
                            size.height - 28f,
                            textPaints.monthLabel,
                        )
                    }
                }

                chartData.weeks.forEachIndexed { index, week ->
                    val xCenter = startX + index * weekWidthPx + weekWidthPx / 2f
                    val color = Color(ChartDataBuilder.MONTH_COLORS[week.month] ?: 0xFF888888)
                    val avgY = paceY(week.averagePace)

                    drawRect(
                        color = color.copy(alpha = 0.3f),
                        topLeft = Offset(xCenter - weekWidthPx * 0.28f, avgY),
                        size = Size(weekWidthPx * 0.56f, bottom - avgY),
                    )

                    val offsets = ChartDataBuilder.calculateOffsets(week.workouts.size)
                    week.workouts.forEachIndexed { pointIndex, workout ->
                        val x = xCenter + offsets[pointIndex] * weekWidthPx
                        val y = paceY(workout.paceMinPerKm)
                        drawCircle(color = color, radius = 6f, center = Offset(x, y))
                        drawCircle(color = Color.White, radius = 4f, center = Offset(x, y))

                        if (chartData.showPointLabels && drawTextLabels) {
                            drawContext.canvas.nativeCanvas.drawText(
                                PaceFormatter.toDisplay(workout.paceMinPerKm),
                                x,
                                y - 10f,
                                textPaints.pointLabel,
                            )
                        }
                    }

                    if (drawTextLabels) {
                        drawContext.canvas.nativeCanvas.apply {
                            if (showAveragePaceLabels) {
                                drawText(
                                    PaceFormatter.toDisplay(week.averagePace),
                                    xCenter,
                                    avgY + 28f,
                                    textPaints.averagePace,
                                )
                            }
                            if (showWeekLabels &&
                                ChartDataBuilder.shouldShowWeekLabel(index, weekCount, labelStride)
                            ) {
                                drawText(
                                    week.label,
                                    xCenter,
                                    size.height - 8f,
                                    textPaints.weekLabel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedWorkout?.let { workout ->
        Text(
            text = "${workout.activityName} • ${PaceFormatter.toDisplay(workout.paceMinPerKm)} • ${workout.startTimeLocal}",
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
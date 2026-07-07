package com.lqborges.garminpacecharts.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lqborges.garminpacecharts.domain.ChartDataBuilder
import com.lqborges.garminpacecharts.domain.PaceFormatter
import com.lqborges.garminpacecharts.domain.model.ChartData
import com.lqborges.garminpacecharts.domain.model.Workout
import kotlin.math.max

@Composable
fun PaceChart(
    chartData: ChartData,
    modifier: Modifier = Modifier,
    onWorkoutSelected: (Workout) -> Unit = {},
) {
    if (chartData.weeks.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(240.dp).padding(16.dp)) {
            Text("No workouts in this range")
        }
        return
    }

    var selectedWorkout by remember { mutableStateOf<Workout?>(null) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val weekCount = chartData.weeks.size

    val allPaces = chartData.weeks.flatMap { week ->
        week.workouts.map { it.paceMinPerKm } + listOf(week.averagePace)
    }
    val minPace = allPaces.min() - 0.2
    val maxPace = allPaces.max() + 0.2

    LaunchedEffect(chartData.title, weekCount, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(Color(0xFFFAFAFA)),
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val minWeekWidthPx = with(density) { 56.dp.toPx() }
        val leftPaddingPx = with(density) { 48.dp.toPx() }
        val rightPaddingPx = with(density) { 24.dp.toPx() }
        val contentWidthPx = max(
            viewportWidthPx,
            leftPaddingPx + weekCount * minWeekWidthPx + rightPaddingPx,
        )
        val contentWidth = with(density) { contentWidthPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .horizontalScroll(scrollState),
        ) {
            Canvas(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                val left = leftPaddingPx
                val top = 24f
                val bottom = size.height - 40f
                val height = bottom - top
                val weekWidth = (size.width - left - rightPaddingPx) / max(weekCount, 1) * 0.9f
                val startX = left

                fun paceY(pace: Double): Float {
                    val ratio = ((pace - minPace) / (maxPace - minPace)).toFloat()
                    return top + ratio * height
                }

                chartData.weeks.forEachIndexed { index, week ->
                    val xCenter = startX + index * weekWidth + weekWidth / 2f
                    val color = Color(ChartDataBuilder.MONTH_COLORS[week.month] ?: 0xFF888888)
                    val avgY = paceY(week.averagePace)

                    drawRect(
                        color = color.copy(alpha = 0.3f),
                        topLeft = Offset(xCenter - weekWidth * 0.28f, avgY),
                        size = Size(weekWidth * 0.56f, bottom - avgY),
                    )

                    val offsets = ChartDataBuilder.calculateOffsets(week.workouts.size)
                    week.workouts.forEachIndexed { pointIndex, workout ->
                        val x = xCenter + offsets[pointIndex] * weekWidth
                        val y = paceY(workout.paceMinPerKm)
                        drawCircle(color = color, radius = 6f, center = Offset(x, y))
                        drawCircle(color = Color.White, radius = 4f, center = Offset(x, y))

                        if (chartData.showPointLabels) {
                            drawContext.canvas.nativeCanvas.apply {
                                drawText(
                                    PaceFormatter.toDisplay(workout.paceMinPerKm),
                                    x,
                                    y - 10f,
                                    android.graphics.Paint().apply {
                                        textSize = 24f
                                        this.color = android.graphics.Color.DKGRAY
                                        textAlign = android.graphics.Paint.Align.CENTER
                                    },
                                )
                            }
                        }
                    }

                    drawContext.canvas.nativeCanvas.apply {
                        drawText(
                            PaceFormatter.toDisplay(week.averagePace),
                            xCenter,
                            avgY + 28f,
                            android.graphics.Paint().apply {
                                textSize = 26f
                                this.color = android.graphics.Color.BLACK
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                            },
                        )
                        drawText(
                            week.label,
                            xCenter,
                            size.height - 8f,
                            android.graphics.Paint().apply {
                                textSize = 22f
                                this.color = android.graphics.Color.DKGRAY
                                textAlign = android.graphics.Paint.Align.CENTER
                            },
                        )
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
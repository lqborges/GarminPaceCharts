package com.lqborges.garminpacecharts.domain

import kotlin.math.abs

enum class MetricSentiment {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
}

object MetricChange {
    fun percentChange(current: Double, prior: Double): Double? {
        if (prior == 0.0) return null
        return ((current - prior) / prior) * 100.0
    }

    fun sentiment(change: Double?, lowerIsBetter: Boolean): MetricSentiment {
        if (change == null || abs(change) < 2.0) return MetricSentiment.NEUTRAL
        val improving = if (lowerIsBetter) change < 0 else change > 0
        return if (improving) MetricSentiment.POSITIVE else MetricSentiment.NEGATIVE
    }

    fun formatPercent(change: Double?): String? {
        if (change == null) return null
        val rounded = change.toInt()
        return when {
            rounded > 0 -> "+$rounded%"
            else -> "$rounded%"
        }
    }

    fun arrow(sentiment: MetricSentiment): String = when (sentiment) {
        MetricSentiment.POSITIVE -> "▲"
        MetricSentiment.NEGATIVE -> "▼"
        MetricSentiment.NEUTRAL -> "■"
    }
}
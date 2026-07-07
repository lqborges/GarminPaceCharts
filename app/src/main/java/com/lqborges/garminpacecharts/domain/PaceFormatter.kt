package com.lqborges.garminpacecharts.domain

import kotlin.math.roundToInt

object PaceFormatter {
    fun toDisplay(paceMinPerKm: Double): String {
        val minutes = paceMinPerKm.toInt()
        val seconds = ((paceMinPerKm - minutes) * 60).roundToInt().coerceIn(0, 59)
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

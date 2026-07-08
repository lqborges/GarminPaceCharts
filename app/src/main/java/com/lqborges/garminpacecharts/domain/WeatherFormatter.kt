package com.lqborges.garminpacecharts.domain

object WeatherFormatter {
    fun formatTemperature(celsius: Double): String =
        "${celsius.toInt()}°C"

    fun formatRainChanceNextHour(percent: Int): String =
        "Rain next hour: $percent%"
}
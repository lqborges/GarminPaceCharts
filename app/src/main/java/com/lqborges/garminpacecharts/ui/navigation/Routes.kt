package com.lqborges.garminpacecharts.ui.navigation

object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
    const val CHARTS = "charts"
    const val WORKOUTS = "workouts"
    const val WORKOUT_DETAIL = "workout/{workoutId}"
    const val HEALTH = "health"
    const val REFRESH = "refresh"
    const val SETTINGS = "settings"

    fun workoutDetail(id: Long) = "workout/$id"
}

package com.lqborges.garminpacecharts

import android.app.Application

class GarminPaceChartsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

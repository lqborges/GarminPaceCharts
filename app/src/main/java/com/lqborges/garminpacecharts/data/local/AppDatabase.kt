package com.lqborges.garminpacecharts.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lqborges.garminpacecharts.data.local.dao.HealthAssessmentDao
import com.lqborges.garminpacecharts.data.local.dao.HealthMetricDao
import com.lqborges.garminpacecharts.data.local.dao.RefreshRunDao
import com.lqborges.garminpacecharts.data.local.dao.WorkoutDao
import com.lqborges.garminpacecharts.data.local.entity.HealthAssessmentEntity
import com.lqborges.garminpacecharts.data.local.entity.HealthMetricSnapshotEntity
import com.lqborges.garminpacecharts.data.local.entity.RefreshRunEntity
import com.lqborges.garminpacecharts.data.local.entity.WorkoutEntity

@Database(
    entities = [
        WorkoutEntity::class,
        RefreshRunEntity::class,
        HealthMetricSnapshotEntity::class,
        HealthAssessmentEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun refreshRunDao(): RefreshRunDao
    abstract fun healthMetricDao(): HealthMetricDao
    abstract fun healthAssessmentDao(): HealthAssessmentDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "garmin_pace_charts.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

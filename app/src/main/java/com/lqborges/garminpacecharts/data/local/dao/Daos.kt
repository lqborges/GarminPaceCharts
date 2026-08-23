package com.lqborges.garminpacecharts.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lqborges.garminpacecharts.data.local.entity.HealthAssessmentEntity
import com.lqborges.garminpacecharts.data.local.entity.HealthMetricSnapshotEntity
import com.lqborges.garminpacecharts.data.local.entity.RefreshRunEntity
import com.lqborges.garminpacecharts.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startTimeMillis ASC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY startTimeMillis ASC")
    suspend fun getAll(): List<WorkoutEntity>

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun count(): Int

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE activityId = :activityId LIMIT 1")
    suspend fun getByActivityId(activityId: Long): WorkoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(workouts: List<WorkoutEntity>)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()
}

@Dao
interface RefreshRunDao {
    @Insert
    suspend fun insert(run: RefreshRunEntity): Long

    @Query("SELECT * FROM refresh_runs ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getLatest(): RefreshRunEntity?

    @Query("SELECT * FROM refresh_runs ORDER BY startedAtMillis DESC LIMIT 1")
    fun observeLatest(): Flow<RefreshRunEntity?>

    @Query("DELETE FROM refresh_runs")
    suspend fun deleteAll()
}

@Dao
interface HealthMetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metrics: List<HealthMetricSnapshotEntity>)

    @Query("SELECT * FROM health_metric_snapshots ORDER BY dateMillis DESC")
    suspend fun getAll(): List<HealthMetricSnapshotEntity>

    @Query(
        "SELECT MIN(dateMillis) FROM health_metric_snapshots WHERE metricType = :metricType",
    )
    suspend fun oldestDateMillis(metricType: String): Long?

    @Query(
        "DELETE FROM health_metric_snapshots WHERE metricType = :metricType " +
            "AND dateMillis >= :fromMillis AND dateMillis <= :toMillis",
    )
    suspend fun deleteTypeInRange(metricType: String, fromMillis: Long, toMillis: Long)

    @Query("DELETE FROM health_metric_snapshots")
    suspend fun deleteAll()
}

@Dao
interface HealthAssessmentDao {
    @Insert
    suspend fun insert(assessment: HealthAssessmentEntity): Long

    @Query("SELECT * FROM health_assessments ORDER BY generatedAtMillis DESC LIMIT 1")
    suspend fun getLatest(): HealthAssessmentEntity?

    @Query("SELECT * FROM health_assessments ORDER BY generatedAtMillis DESC LIMIT 1")
    fun observeLatest(): Flow<HealthAssessmentEntity?>

    @Query("DELETE FROM health_assessments")
    suspend fun deleteAll()
}

package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Entities ---

@Entity(tableName = "weather_uploads")
data class WeatherUpload(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val district: String,
    val description: String,
    val photoType: String, // "sunny", "stormy", "cq_satellite", "cloudy"
    val timestamp: Long = System.currentTimeMillis(),
    val upvotes: Int = 0,
    val reporterName: String = "User"
)

@Entity(tableName = "system_alerts")
data class SystemAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val severity: String, // "RED", "ORANGE", "YELLOW"
    val district: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sharedCount: Int = 0
)

@Entity(tableName = "user_points")
data class UserPoints(
    @PrimaryKey val userId: String = "default_user",
    val points: Int = 0
)

// --- DAOs ---

@Dao
interface WeatherUploadDao {
    @Query("SELECT * FROM weather_uploads ORDER BY timestamp DESC")
    fun getAllUploads(): Flow<List<WeatherUpload>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpload(upload: WeatherUpload)

    @Query("UPDATE weather_uploads SET upvotes = upvotes + 1 WHERE id = :id")
    suspend fun upvoteUpload(id: Int)
}

@Dao
interface SystemAlertDao {
    @Query("SELECT * FROM system_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<SystemAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: SystemAlert)

    @Query("UPDATE system_alerts SET sharedCount = sharedCount + 1 WHERE id = :id")
    suspend fun incrementShareCount(id: Int)

    @Query("DELETE FROM system_alerts")
    suspend fun clearAlerts()
}

@Dao
interface UserPointsDao {
    @Query("SELECT * FROM user_points WHERE userId = :userId LIMIT 1")
    suspend fun getUserPoints(userId: String = "default_user"): UserPoints?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserPoints(points: UserPoints)
}

// --- Database ---

@Database(entities = [WeatherUpload::class, SystemAlert::class, UserPoints::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weatherUploadDao(): WeatherUploadDao
    abstract fun systemAlertDao(): SystemAlertDao
    abstract fun userPointsDao(): UserPointsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "weather_alert_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Repository ---

class WeatherRepository(private val db: AppDatabase) {
    val allUploads: Flow<List<WeatherUpload>> = db.weatherUploadDao().getAllUploads()
    val allAlerts: Flow<List<SystemAlert>> = db.systemAlertDao().getAllAlerts()

    suspend fun insertUpload(upload: WeatherUpload) {
        db.weatherUploadDao().insertUpload(upload)
        // Award points when user uploads weather content
        val currentPoints = db.userPointsDao().getUserPoints()?.points ?: 0
        db.userPointsDao().saveUserPoints(UserPoints(points = currentPoints + 50))
    }

    suspend fun upvoteUpload(id: Int) {
        db.weatherUploadDao().upvoteUpload(id)
        // Also award points when receiving an upvote
        val currentPoints = db.userPointsDao().getUserPoints()?.points ?: 0
        db.userPointsDao().saveUserPoints(UserPoints(points = currentPoints + 15))
    }

    suspend fun insertAlert(alert: SystemAlert) {
        db.systemAlertDao().insertAlert(alert)
    }

    suspend fun incrementShareCount(id: Int) {
        db.systemAlertDao().incrementShareCount(id)
        // Award points for sharing warnings/information to promote viral sharing
        val currentPoints = db.userPointsDao().getUserPoints()?.points ?: 0
        db.userPointsDao().saveUserPoints(UserPoints(points = currentPoints + 25))
    }

    suspend fun getUserPoints(): Int {
        return db.userPointsDao().getUserPoints()?.points ?: 0
    }

    suspend fun resetDatabase() {
        db.systemAlertDao().clearAlerts()
    }
}

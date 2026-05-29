package com.securityshield.utils

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Entity
@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val triggerReason: String,
    val failCount: Int,
    val photoPath: String?,
    val videoPath: String?,
    val aiReport: String?,
    val latitude: Double,
    val longitude: Double
)

// DAO
@Dao
interface IncidentDao {
    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    @Insert
    suspend fun insert(incident: IncidentEntity)

    @Query("DELETE FROM incidents WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM incidents")
    suspend fun getCount(): Int
}

// Database
@Database(entities = [IncidentEntity::class], version = 1)
abstract class SecurityDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao

    companion object {
        @Volatile private var INSTANCE: SecurityDatabase? = null

        fun getInstance(context: android.content.Context): SecurityDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SecurityDatabase::class.java,
                    "security_shield.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

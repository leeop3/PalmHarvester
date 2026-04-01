package com.palm.harvester.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*

@Entity(tableName = "harvest_entries")
data class HarvestEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockId: String,
    val ripeCount: Int,
    val emptyCount: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val reportDate: String, // yyyy-MM-dd
    val photoBase64: String,
    val isSynced: Boolean = false
)

data class DaySummary(
    val totalRipe: Int,
    val totalEmpty: Int,
    val totalBunches: Int,
    val entryCount: Int
)

data class BlockMonthlySummary(
    val blockId: String,
    val totalBunches: Int
)

@Dao
interface HarvestDao {
    @Insert suspend fun insert(entry: HarvestEntry)
    @Update suspend fun update(entry: HarvestEntry)
    @Delete suspend fun delete(entry: HarvestEntry)

    @Query("SELECT * FROM harvest_entries ORDER BY id DESC")
    fun getAllEntries(): LiveData<List<HarvestEntry>>

    @Query("SELECT * FROM harvest_entries")
    suspend fun getAllEntriesOnce(): List<HarvestEntry>

    @Query("SELECT * FROM harvest_entries WHERE id = :entryId LIMIT 1")
    suspend fun getEntryById(entryId: Long): HarvestEntry?

    @Query("SELECT * FROM harvest_entries WHERE isSynced = 0")
    suspend fun getUnsentEntries(): List<HarvestEntry>

    @Query("""
        SELECT SUM(ripeCount) as totalRipe, SUM(emptyCount) as totalEmpty, 
        SUM(ripeCount + emptyCount) as totalBunches, COUNT(*) as entryCount 
        FROM harvest_entries WHERE reportDate = :date
    """)
    fun getSummaryForDate(date: String): LiveData<DaySummary>

    // FIX: Added for Analytics
    @Query("""
        SELECT SUM(ripeCount) as totalRipe, SUM(emptyCount) as totalEmpty, 
        SUM(ripeCount + emptyCount) as totalBunches, COUNT(*) as entryCount 
        FROM harvest_entries WHERE reportDate LIKE :month || '%'
    """)
    fun getSummaryForMonth(month: String): LiveData<DaySummary>

    @Query("""
        SELECT blockId, SUM(ripeCount + emptyCount) as totalBunches 
        FROM harvest_entries WHERE reportDate LIKE :month || '%' 
        GROUP BY blockId
    """)
    fun getBlockSummaryForMonth(month: String): LiveData<List<BlockMonthlySummary>>

    @Query("UPDATE harvest_entries SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
}

@Database(entities = [HarvestEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun harvestDao(): HarvestDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "harvester_db").build()
                INSTANCE = instance
                instance
            }
        }
    }
}
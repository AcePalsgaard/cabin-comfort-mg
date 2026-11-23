package dk.Android.Cpmonster.mgmanager.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY kind, id")
    fun all(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): Profile?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(profile: Profile): Long
}

@Dao
abstract class JobDao {
    @Insert
    abstract suspend fun insert(job: ApiJob): Long

    @Query("DELETE FROM jobs WHERE dedupeKey = :key")
    abstract suspend fun deleteByKey(key: String)

    @Transaction
    open suspend fun replace(job: ApiJob): Long {
        job.dedupeKey?.let { deleteByKey(it) }
        return insert(job)
    }

    @Query("SELECT * FROM jobs WHERE nextRunAt <= :now ORDER BY nextRunAt, id LIMIT 1")
    abstract suspend fun nextDue(now: Long): ApiJob?

    @Query("SELECT MIN(nextRunAt) FROM jobs")
    abstract suspend fun nextRunAt(): Long?

    @Query("SELECT COUNT(*) FROM jobs")
    abstract fun countFlow(): Flow<Int>

    @Query("UPDATE jobs SET attempts = :attempts, nextRunAt = :nextRunAt, lastError = :error WHERE id = :id")
    abstract suspend fun reschedule(id: Long, attempts: Int, nextRunAt: Long, error: String?)

    @Query("DELETE FROM jobs WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM jobs")
    abstract suspend fun clear()
}

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicle_state WHERE id = 0")
    fun observe(): Flow<VehicleState?>

    @Query("SELECT * FROM vehicle_state WHERE id = 0")
    suspend fun get(): VehicleState?

    @Upsert
    suspend fun upsert(state: VehicleState)

    @Query("DELETE FROM vehicle_state")
    suspend fun clear()
}

@Database(entities = [Profile::class, ApiJob::class, VehicleState::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun jobs(): JobDao
    abstract fun vehicle(): VehicleDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN runs INTEGER NOT NULL DEFAULT 2")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicle_state ADD COLUMN seatHeating TEXT")
            }
        }

        // Default profiles renamed: "Cold" -> "Warm up", "Warm" -> "Chill" (only if the user kept the old names).
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE profiles SET name = 'Warm up' WHERE name = 'Cold'")
                db.execSQL("UPDATE profiles SET name = 'Chill' WHERE name = 'Warm'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicle_state ADD COLUMN colorName TEXT")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mgmanager.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

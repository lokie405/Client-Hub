package com.seryoga.myapplication.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter
    fun fromNoteType(value: NoteType): String = value.name

    @TypeConverter
    fun toNoteType(value: String): NoteType = NoteType.valueOf(value)
}

@Dao
interface ClientDao {
    @Transaction
    @Query("SELECT * FROM clients")
    fun getAllClients(): Flow<List<ClientWithDetails>>

    @Transaction
    @Query("SELECT * FROM clients")
    fun searchClientsRaw(): Flow<List<ClientWithDetails>>

    @Transaction
    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClientById(id: Long): ClientWithDetails?

    @Transaction
    @Query("SELECT * FROM clients WHERE label = :label")
    suspend fun getClientByLabel(label: String): ClientWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhone(phone: PhoneEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Unit

    @Delete
    suspend fun deleteClient(client: ClientEntity): Unit

    @Query("DELETE FROM phones WHERE clientId = :clientId")
    suspend fun deletePhonesForClient(clientId: Long): Unit

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCallLog(callLog: CallLogEntity)

    @Query("SELECT * FROM call_logs WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC")
    fun getCallLogsForPhone(phoneNumber: String): Flow<List<CallLogEntity>>

    @Query("SELECT COUNT(*) FROM call_logs WHERE phoneNumber = :phoneNumber AND type = 1")
    suspend fun getIncomingCount(phoneNumber: String): Int

    @Query("SELECT COUNT(*) FROM call_logs WHERE phoneNumber = :phoneNumber AND type = 2")
    suspend fun getOutgoingCount(phoneNumber: String): Int
}

@Database(entities = [ClientEntity::class, PhoneEntity::class, NoteEntity::class, CallLogEntity::class], version = 7)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS clients_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        fullName TEXT NOT NULL, 
                        middleName TEXT NOT NULL, 
                        shopName TEXT NOT NULL, 
                        city TEXT, 
                        addressManual TEXT, 
                        latitude REAL, 
                        longitude REAL, 
                        label TEXT
                    )
                """.trimIndent())

                // 2. Copy data merging lastName and firstName
                db.execSQL("""
                    INSERT INTO clients_new (id, fullName, middleName, shopName, city, addressManual, latitude, longitude, label)
                    SELECT id, (lastName || ' ' || firstName), middleName, shopName, city, addressManual, latitude, longitude, label
                    FROM clients
                """.trimIndent())

                // 3. Remove old table
                db.execSQL("DROP TABLE clients")

                // 4. Rename new table
                db.execSQL("ALTER TABLE clients_new RENAME TO clients")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "client_database"
                )
                    .addMigrations(MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

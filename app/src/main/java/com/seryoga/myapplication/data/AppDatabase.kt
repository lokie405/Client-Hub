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
    @Query("SELECT * FROM clients WHERE firstName LIKE :query OR lastName LIKE :query OR shopName LIKE :query")
    fun searchClients(query: String): Flow<List<ClientWithDetails>>

    @Transaction
    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClientById(id: Long): ClientWithDetails?

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

@Database(entities = [ClientEntity::class, PhoneEntity::class, NoteEntity::class, CallLogEntity::class], version = 5)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "client_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

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
import androidx.room.Update
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
    suspend fun insertName(name: NameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhone(phone: PhoneEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Unit

    @Delete
    suspend fun deleteClient(client: ClientEntity): Unit

    @Query("DELETE FROM client_names WHERE clientId = :clientId")
    suspend fun deleteNamesForClient(clientId: Long): Unit

    @Query("DELETE FROM phones WHERE clientId = :clientId")
    suspend fun deletePhonesForClient(clientId: Long): Unit

    @Query("DELETE FROM clients")
    suspend fun deleteAllClients()

    @Query("DELETE FROM client_names")
    suspend fun deleteAllNames()

    @Query("DELETE FROM phones")
    suspend fun deleteAllPhones()

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCallLog(callLog: CallLogEntity)

    @Insert
    suspend fun insertLogSession(session: UpdateLogSession): Long

    @Insert
    suspend fun insertLogEntry(entry: UpdateLogEntry)

    @Query("SELECT * FROM update_log_sessions ORDER BY timestamp DESC")
    fun getAllLogSessions(): Flow<List<UpdateLogSession>>

    @Query("SELECT * FROM update_log_entries WHERE sessionId = :sessionId")
    suspend fun getLogEntriesForSession(sessionId: Long): List<UpdateLogEntry>

    @Update
    suspend fun updateLogEntry(entry: UpdateLogEntry)

    @Query("SELECT * FROM call_logs WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC")
    fun getCallLogsForPhone(phoneNumber: String): Flow<List<CallLogEntity>>

    @Query("SELECT COUNT(*) FROM call_logs WHERE phoneNumber = :phoneNumber AND type = 1")
    suspend fun getIncomingCount(phoneNumber: String): Int

    @Query("SELECT COUNT(*) FROM call_logs WHERE phoneNumber = :phoneNumber AND type = 2")
    suspend fun getOutgoingCount(phoneNumber: String): Int

    @Transaction
    @Query("SELECT * FROM route_sheets WHERE isArchived = 0 ORDER BY createdTimestamp DESC LIMIT 1")
    fun getActiveRouteSheet(): Flow<RouteSheetWithItems?>

    @Transaction
    @Query("SELECT * FROM route_sheets ORDER BY createdTimestamp DESC")
    fun getAllRouteSheetsWithItems(): Flow<List<RouteSheetWithItems>>

    @Transaction
    @Query("SELECT * FROM route_sheets WHERE id = :id")
    suspend fun getRouteSheetById(id: Long): RouteSheetWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteSheet(routeSheet: RouteSheetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteSheetItems(items: List<RouteSheetItemEntity>)

    @Update
    suspend fun updateRouteSheetItem(item: RouteSheetItemEntity)

    @Update
    suspend fun updateRouteSheetItems(items: List<RouteSheetItemEntity>)

    @Query("UPDATE route_sheets SET isArchived = 1 WHERE isArchived = 0 AND dateString != :todayDate")
    suspend fun archiveOldRouteSheets(todayDate: String)

    @Query("UPDATE route_sheets SET isArchived = 1 WHERE isArchived = 0")
    suspend fun archiveActiveRouteSheet()

    @Query("DELETE FROM route_sheets WHERE id = :routeSheetId")
    suspend fun deleteRouteSheet(routeSheetId: Long)

    @Query("DELETE FROM route_sheet_items WHERE routeSheetId = :routeSheetId")
    suspend fun deleteRouteSheetItems(routeSheetId: Long)
}

@Database(entities = [ClientEntity::class, NameEntity::class, PhoneEntity::class, NoteEntity::class, CallLogEntity::class, UpdateLogSession::class, UpdateLogEntry::class, RouteSheetEntity::class, RouteSheetItemEntity::class], version = 12)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS clients_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fullName TEXT NOT NULL, middleName TEXT NOT NULL, shopName TEXT NOT NULL, city TEXT, addressManual TEXT, latitude REAL, longitude REAL, label TEXT)")
                db.execSQL("INSERT INTO clients_new (id, fullName, middleName, shopName, city, addressManual, latitude, longitude, label) SELECT id, (lastName || ' ' || firstName), middleName, shopName, city, addressManual, latitude, longitude, label FROM clients")
                db.execSQL("DROP TABLE clients")
                db.execSQL("ALTER TABLE clients_new RENAME TO clients")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clients ADD COLUMN fullNameStatus TEXT NOT NULL DEFAULT 'changed'")
                db.execSQL("ALTER TABLE clients ADD COLUMN shopNameStatus TEXT NOT NULL DEFAULT 'changed'")
                db.execSQL("ALTER TABLE clients ADD COLUMN cityStatus TEXT NOT NULL DEFAULT 'changed'")
                db.execSQL("ALTER TABLE clients ADD COLUMN addressStatus TEXT NOT NULL DEFAULT 'changed'")
                db.execSQL("ALTER TABLE clients ADD COLUMN phonesStatus TEXT NOT NULL DEFAULT 'changed'")
                db.execSQL("ALTER TABLE clients ADD COLUMN notesStatus TEXT NOT NULL DEFAULT 'changed'")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `client_names` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clientId` INTEGER NOT NULL, `fullName` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `clients_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `middleName` TEXT NOT NULL, `shopName` TEXT NOT NULL, `city` TEXT, `addressManual` TEXT, `latitude` REAL, `longitude` REAL, `label` TEXT, `namesStatus` TEXT NOT NULL, `shopNameStatus` TEXT NOT NULL, `cityStatus` TEXT NOT NULL, `addressStatus` TEXT NOT NULL, `phonesStatus` TEXT NOT NULL, `notesStatus` TEXT NOT NULL)")
                db.execSQL("INSERT INTO client_names (clientId, fullName) SELECT id, fullName FROM clients")
                db.execSQL("INSERT INTO clients_new (id, middleName, shopName, city, addressManual, latitude, longitude, label, namesStatus, shopNameStatus, cityStatus, addressStatus, phonesStatus, notesStatus) SELECT id, middleName, shopName, city, addressManual, latitude, longitude, label, fullNameStatus, shopNameStatus, cityStatus, addressStatus, phonesStatus, notesStatus FROM clients")
                db.execSQL("DROP TABLE clients")
                db.execSQL("ALTER TABLE clients_new RENAME TO clients")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `update_log_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `update_log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `clientLabel` TEXT NOT NULL, `clientNameSnapshot` TEXT NOT NULL, `fieldName` TEXT NOT NULL, `oldValue` TEXT NOT NULL, `newValue` TEXT NOT NULL, `isReverted` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `route_sheets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateString` TEXT NOT NULL, `createdTimestamp` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `route_sheet_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routeSheetId` INTEGER NOT NULL, `clientId` INTEGER NOT NULL DEFAULT 0, `orderIndex` INTEGER NOT NULL, `clientLabelSnapshot` TEXT NOT NULL DEFAULT '', `clientShopSnapshot` TEXT NOT NULL DEFAULT '', `clientCitySnapshot` TEXT, `clientNameSnapshot` TEXT NOT NULL DEFAULT '', `noteText` TEXT NOT NULL DEFAULT '', `noteAudioUri` TEXT, `notePhotoUri` TEXT, `noteFileUri` TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_sheet_items_routeSheetId` ON `route_sheet_items` (`routeSheetId`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE route_sheet_items ADD COLUMN orderNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE route_sheet_items ADD COLUMN weightKg REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE route_sheet_items ADD COLUMN amountSum REAL NOT NULL DEFAULT 0.0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "client_database"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

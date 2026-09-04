package com.seryoga.myapplication.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val middleName: String,
    val shopName: String,
    val shopPhotoUri: String? = null,
    val addressManual: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val label: String? = null
)

@Entity(tableName = "phones")
data class PhoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val phoneNumber: String
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val content: String,
    val type: NoteType = NoteType.TEXT,
    val reminderTimestamp: Long? = null,
    val photoUri: String? = null,
    val audioUri: String? = null
)

enum class NoteType {
    TEXT, CHECKLIST, AUDIO, PHOTO
}

data class ClientWithDetails(
    @Embedded val client: ClientEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "clientId"
    )
    val phones: List<PhoneEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "clientId"
    )
    val notes: List<NoteEntity>
)

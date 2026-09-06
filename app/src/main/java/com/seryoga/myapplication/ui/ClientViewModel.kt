package com.seryoga.myapplication.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import android.net.Uri
import android.os.Environment
import android.provider.CallLog
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.seryoga.myapplication.data.AppDatabase
import com.seryoga.myapplication.data.CallLogEntity
import com.seryoga.myapplication.data.ClientDao
import com.seryoga.myapplication.data.ClientEntity
import com.seryoga.myapplication.data.ClientWithDetails
import com.seryoga.myapplication.data.NoteEntity
import com.seryoga.myapplication.data.PhoneEntity
import com.seryoga.myapplication.data.PhoneWithStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ClientViewModel(application: Application) : AndroidViewModel(application) {
    private val clientDao: ClientDao = AppDatabase.getDatabase(application).clientDao()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private val gson = Gson()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val clients: StateFlow<List<ClientWithDetails>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                clientDao.getAllClients()
            } else {
                clientDao.searchClients("%$query%")
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getPhonesWithStats(clientId: Long): List<PhoneWithStats> {
        val details = clientDao.getClientById(clientId) ?: return emptyList()
        refreshCallLogs(details.phones.map { it.phoneNumber })
        
        return details.phones.map { phone ->
            PhoneWithStats(
                phone = phone,
                incomingCount = clientDao.getIncomingCount(phone.phoneNumber),
                outgoingCount = clientDao.getOutgoingCount(phone.phoneNumber)
            )
        }
    }

    fun getCallHistory(phoneNumber: String): StateFlow<List<CallLogEntity>> {
        return clientDao.getCallLogsForPhone(phoneNumber)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    @SuppressLint("Range")
    suspend fun refreshCallLogs(phoneNumbers: List<String>) {
        if (phoneNumbers.isEmpty()) return
        
        withContext(Dispatchers.IO) {
            val contentResolver = getApplication<Application>().contentResolver
            phoneNumbers.forEach { phone ->
                // Extract last 9 digits for flexible matching (covers most cases)
                val cleanPhone = phone.replace(Regex("[^0-9]"), "")
                if (cleanPhone.length < 9) return@forEach
                val matchPattern = "%${cleanPhone.takeLast(9)}"

                try {
                    val cursor = contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        null,
                        "${CallLog.Calls.NUMBER} LIKE ?",
                        arrayOf(matchPattern),
                        "${CallLog.Calls.DATE} DESC"
                    )

                    cursor?.use {
                        while (it.moveToNext()) {
                            val type = it.getInt(it.getColumnIndex(CallLog.Calls.TYPE))
                            val date = it.getLong(it.getColumnIndex(CallLog.Calls.DATE))
                            val duration = it.getInt(it.getColumnIndex(CallLog.Calls.DURATION))

                            if (type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.OUTGOING_TYPE) {
                                clientDao.insertCallLog(
                                    CallLogEntity(
                                        phoneNumber = phone,
                                        type = if (type == CallLog.Calls.INCOMING_TYPE) 1 else 2,
                                        timestamp = date,
                                        duration = duration
                                    )
                                )
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    // Log or handle
                }
            }
        }
    }

    fun addClient(
        id: Long? = null,
        firstName: String,
        lastName: String,
        middleName: String,
        shopName: String,
        phones: List<String>,
        address: String? = null,
        lat: Double? = null,
        lon: Double? = null,
        label: String? = null
    ) {
        viewModelScope.launch {
            val clientId = clientDao.insertClient(
                ClientEntity(
                    id = id ?: 0,
                    firstName = firstName,
                    lastName = lastName,
                    middleName = middleName,
                    shopName = shopName,
                    addressManual = address,
                    latitude = lat,
                    longitude = lon,
                    label = label
                )
            )
            
            if (id != null) {
                clientDao.deletePhonesForClient(id)
            }
            
            phones.forEach { phone ->
                if (phone.isNotBlank()) {
                    clientDao.insertPhone(PhoneEntity(clientId = clientId, phoneNumber = phone))
                }
            }
            
            autoExportData()
        }
    }

    private suspend fun autoExportData() {
        try {
            val allClients = clientDao.getAllClients().first()
            val recordCount = allClients.size
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "costumer_${timestamp}_$recordCount.json"
            
            val json = gson.toJson(allClients)
            
            withContext(Dispatchers.IO) {
                val directory = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (directory != null) {
                    if (!directory.exists()) directory.mkdirs()
                    val file = File(directory, fileName)
                    FileOutputStream(file).use {
                        it.write(json.toByteArray())
                    }
                }
            }
        } catch (e: Exception) {}
    }

    fun addNote(clientId: Long, content: String) {
        viewModelScope.launch {
            clientDao.insertNote(NoteEntity(clientId = clientId, content = content))
        }
    }

    suspend fun getClient(id: Long): ClientWithDetails? {
        return clientDao.getClientById(id)
    }

    fun deleteClient(clientWithDetails: ClientWithDetails) {
        viewModelScope.launch {
            clientDao.deleteClient(clientWithDetails.client)
            clientDao.deletePhonesForClient(clientWithDetails.client.id)
        }
    }

    fun exportData(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val allClients = clientDao.getAllClients().first()
                val json = gson.toJson(allClients)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { 
                        it.write(json.toByteArray())
                    }
                }
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun importData(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                    }
                }
                
                if (json != null) {
                    val type = object : TypeToken<List<ClientWithDetails>>() {}.type
                    val importedClients: List<ClientWithDetails> = gson.fromJson(json, type)
                    
                    importedClients.forEach { details ->
                        val newId = clientDao.insertClient(details.client.copy(id = 0))
                        details.phones.forEach { phone ->
                            clientDao.insertPhone(phone.copy(id = 0, clientId = newId))
                        }
                        details.notes.forEach { note ->
                            clientDao.insertNote(note.copy(id = 0, clientId = newId))
                        }
                    }
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocationAddress(onResult: (String?, Double?, Double?) -> Unit) {
        viewModelScope.launch {
            try {
                val location = withContext(Dispatchers.IO) {
                    val result = fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    )
                    com.google.android.gms.tasks.Tasks.await(result)
                }

                location?.let {
                    getAddressFromLocation(it.latitude, it.longitude) { addr ->
                        onResult(addr, it.latitude, it.longitude)
                    }
                } ?: onResult(null, null, null)
            } catch (e: Exception) {
                onResult(null, null, null)
            }
        }
    }

    fun getAddressFromLocation(latitude: Double, longitude: Double, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val addressStr = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(getApplication(), Locale.getDefault())
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val city = addr.locality ?: ""
                        val street = addr.thoroughfare ?: ""
                        val house = addr.subThoroughfare ?: ""
                        listOf(city, street, house).filter { it.isNotBlank() }.joinToString(", ")
                    } else null
                }
                onResult(addressStr)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }
}

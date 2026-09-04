package com.seryoga.myapplication.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.seryoga.myapplication.data.AppDatabase
import com.seryoga.myapplication.data.ClientDao
import com.seryoga.myapplication.data.ClientEntity
import com.seryoga.myapplication.data.ClientWithDetails
import com.seryoga.myapplication.data.NoteEntity
import com.seryoga.myapplication.data.PhoneEntity
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
import java.io.InputStreamReader
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ClientViewModel(application: Application) : AndroidViewModel(application) {
    private val clientDao: ClientDao = AppDatabase.getDatabase(application).clientDao()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private val gson = Gson()

    fun getPhotoUri(): Uri {
        val directory = File(getApplication<Application>().filesDir, "Pictures")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "shop_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            getApplication<Application>(),
            "com.seryoga.myapplication.fileprovider",
            file
        )
    }

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

    fun addClient(
        id: Long? = null,
        firstName: String,
        lastName: String,
        middleName: String,
        shopName: String,
        phones: List<String>,
        shopPhotoUri: String? = null,
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
                    shopPhotoUri = shopPhotoUri,
                    addressManual = address,
                    latitude = lat,
                    longitude = lon,
                    label = label
                )
            )
            
            // If updating, clear old phones first to avoid duplicates
            if (id != null) {
                clientDao.deletePhonesForClient(id)
            }
            
            phones.forEach { phone ->
                if (phone.isNotBlank()) {
                    clientDao.insertPhone(PhoneEntity(clientId = clientId, phoneNumber = phone))
                }
            }
        }
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
            // Note: Room should handle cascaded deletes if configured, but let's be explicit
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
}

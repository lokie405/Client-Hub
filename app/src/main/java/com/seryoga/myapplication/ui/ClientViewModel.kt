package com.seryoga.myapplication.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import android.net.Uri
import android.os.Environment
import android.provider.CallLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.seryoga.myapplication.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.apache.poi.ss.usermodel.*

data class ImportResult(
    val success: Boolean,
    val message: String,
    val added: List<String> = emptyList(),
    val updated: List<String> = emptyList(),
    val logs: List<UpdateLogEntry> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ClientViewModel(application: Application) : AndroidViewModel(application) {
    private val clientDao: ClientDao = AppDatabase.getDatabase(application).clientDao()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private val gson = Gson()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val clients: StateFlow<List<ClientWithDetails>> = _searchQuery
        .flatMapLatest { query ->
            clientDao.getAllClients().map { list ->
                if (query.isBlank()) list else {
                    val words = query.lowercase().split(" ").filter { it.isNotBlank() }
                    list.filter { client ->
                        val allNames = client.names.joinToString(" ") { it.fullName }.lowercase()
                        words.all { word ->
                            allNames.contains(word) || client.client.shopName.lowercase().contains(word) ||
                            (client.client.city?.lowercase()?.contains(word) ?: false) || (client.client.label?.lowercase()?.contains(word) ?: false)
                        }
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val logSessions: Flow<List<UpdateLogSession>> = clientDao.getAllLogSessions()

    suspend fun getLogEntries(sessionId: Long): List<UpdateLogEntry> = clientDao.getLogEntriesForSession(sessionId)

    fun revertChange(entry: UpdateLogEntry, onComplete: () -> Unit) {
        viewModelScope.launch {
            val clientWithDetails = clientDao.getClientByLabel(entry.clientLabel) ?: return@launch
            var client = clientWithDetails.client
            when (entry.fieldName) {
                "shopName" -> client = client.copy(shopName = entry.oldValue, shopNameStatus = "unchanged")
                "city" -> client = client.copy(city = if (entry.oldValue.isBlank()) null else entry.oldValue, cityStatus = "unchanged")
                "address" -> client = client.copy(addressManual = if (entry.oldValue.isBlank()) null else entry.oldValue, addressStatus = "unchanged")
                "phones" -> {
                    clientDao.deletePhonesForClient(client.id)
                    entry.oldValue.split(", ").filter { it.isNotBlank() }.forEach { clientDao.insertPhone(PhoneEntity(clientId = client.id, phoneNumber = it)) }
                    client = client.copy(phonesStatus = "unchanged")
                }
                "notes" -> {
                    // Reverting notes is tricky since they are multiple. 
                    // But usually we just add a new note during import. Reverting means blocking auto-updates.
                    client = client.copy(notesStatus = "unchanged")
                }
                "names" -> {
                    // If multiple names were added, reverting means we keep only the old value or block further changes
                    client = client.copy(namesStatus = "unchanged")
                }
            }
            clientDao.insertClient(client)
            clientDao.updateLogEntry(entry.copy(isReverted = true))
            onComplete()
            autoExportData()
        }
    }

    fun unrevertChange(entry: UpdateLogEntry, onComplete: () -> Unit) {
        viewModelScope.launch {
            val clientWithDetails = clientDao.getClientByLabel(entry.clientLabel) ?: return@launch
            var client = clientWithDetails.client
            when (entry.fieldName) {
                "shopName" -> client = client.copy(shopName = entry.newValue, shopNameStatus = "changed")
                "city" -> client = client.copy(city = if (entry.newValue.isBlank()) null else entry.newValue, cityStatus = "changed")
                "address" -> client = client.copy(addressManual = if (entry.newValue.isBlank()) null else entry.newValue, addressStatus = "changed")
                "phones" -> {
                    clientDao.deletePhonesForClient(client.id)
                    entry.newValue.split(", ").filter { it.isNotBlank() }.forEach { clientDao.insertPhone(PhoneEntity(clientId = client.id, phoneNumber = it)) }
                    client = client.copy(phonesStatus = "changed")
                }
                "notes" -> client = client.copy(notesStatus = "changed")
                "names" -> client = client.copy(namesStatus = "changed")
            }
            clientDao.insertClient(client)
            clientDao.updateLogEntry(entry.copy(isReverted = false))
            onComplete()
            autoExportData()
        }
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    suspend fun getPhonesWithStats(clientId: Long): List<PhoneWithStats> {
        val details = clientDao.getClientById(clientId) ?: return emptyList()
        refreshCallLogs(details.phones.map { it.phoneNumber })
        return details.phones.map { PhoneWithStats(it, clientDao.getIncomingCount(it.phoneNumber), clientDao.getOutgoingCount(it.phoneNumber)) }
    }

    fun getCallHistory(phoneNumber: String): StateFlow<List<CallLogEntity>> = clientDao.getCallLogsForPhone(phoneNumber).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @SuppressLint("Range")
    suspend fun refreshCallLogs(phoneNumbers: List<String>) {
        if (phoneNumbers.isEmpty()) return
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            phoneNumbers.forEach { phone ->
                val clean = phone.replace(Regex("[^0-9]"), ""); if (clean.length < 9) return@forEach
                val match = "%${clean.takeLast(9)}"
                try {
                    resolver.query(CallLog.Calls.CONTENT_URI, null, "${CallLog.Calls.NUMBER} LIKE ?", arrayOf(match), "${CallLog.Calls.DATE} DESC")?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE))
                            val date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE))
                            val dur = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.DURATION))
                            if (type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.OUTGOING_TYPE) {
                                clientDao.insertCallLog(CallLogEntity(phoneNumber = phone, type = if (type == CallLog.Calls.INCOMING_TYPE) 1 else 2, timestamp = date, duration = dur))
                            }
                        }
                    }
                } catch (e: SecurityException) {}
            }
        }
    }

    fun addClient(id: Long? = null, names: List<String>, shopName: String, city: String? = null, phones: List<String>, address: String? = null, lat: Double? = null, lon: Double? = null, label: String? = null, namesStatus: String = "changed", shopNameStatus: String = "changed", cityStatus: String = "changed", addressStatus: String = "changed", phonesStatus: String = "changed", notesStatus: String = "changed") {
        viewModelScope.launch {
            val clientId = clientDao.insertClient(ClientEntity(id = id ?: 0, shopName = shopName, city = city, addressManual = address, latitude = lat, longitude = lon, label = label, namesStatus = namesStatus, shopNameStatus = shopNameStatus, cityStatus = cityStatus, addressStatus = addressStatus, phonesStatus = phonesStatus, notesStatus = notesStatus))
            clientDao.deleteNamesForClient(clientId); names.filter { it.isNotBlank() }.forEach { clientDao.insertName(NameEntity(clientId = clientId, fullName = it)) }
            if (id != null) clientDao.deletePhonesForClient(id)
            phones.filter { it.isNotBlank() }.forEach { clientDao.insertPhone(PhoneEntity(clientId = clientId, phoneNumber = it)) }
            autoExportData()
        }
    }

    private suspend fun autoExportData() {
        try {
            val all = clientDao.getAllClients().first(); val ts = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(Date())
            val json = gson.toJson(all)
            withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val cv = android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "clients_db_${all.size}_$ts.json"); put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json"); put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/Polisan DB") }
                    val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    uri?.let { ctx.contentResolver.openOutputStream(it)?.use { out -> out.write(json.toByteArray()) } }
                } else {
                    val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Polisan DB")
                    if (!dir.exists()) dir.mkdirs()
                    FileOutputStream(File(dir, "clients_db_${all.size}_$ts.json")).use { it.write(json.toByteArray()) }
                }
            }
        } catch (e: Exception) {}
    }

    fun addNote(clientId: Long, content: String) { viewModelScope.launch { clientDao.insertNote(NoteEntity(clientId = clientId, content = content)) } }
    suspend fun getClient(id: Long): ClientWithDetails? = clientDao.getClientById(id)
    fun deleteClient(clientWithDetails: ClientWithDetails) { viewModelScope.launch { clientDao.deleteClient(clientWithDetails.client); clientDao.deleteNamesForClient(clientWithDetails.client.id); clientDao.deletePhonesForClient(clientWithDetails.client.id); autoExportData() } }

    fun cloneClient(clientWithDetails: ClientWithDetails) {
        viewModelScope.launch {
            val oldClient = clientWithDetails.client
            val newClientId = clientDao.insertClient(oldClient.copy(id = 0))
            clientWithDetails.names.forEach { clientDao.insertName(it.copy(id = 0, clientId = newClientId)) }
            clientWithDetails.phones.forEach { clientDao.insertPhone(it.copy(id = 0, clientId = newClientId)) }
            clientWithDetails.notes.forEach { clientDao.insertNote(it.copy(id = 0, clientId = newClientId)) }
            autoExportData()
        }
    }

    fun importRouteSheet(uri: Uri, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>(); val input = ctx.contentResolver.openInputStream(uri) ?: return@launch onResult(ImportResult(false, "Не вдалося відкрити файл"))
                withContext(Dispatchers.IO) {
                    val workbook = WorkbookFactory.create(input); val sheet = workbook.getSheetAt(0); val fmt = DataFormatter()
                    var isRS = false; for (i in 0 until minOf(7, sheet.physicalNumberOfRows)) { val row = sheet.getRow(i) ?: continue; for (j in 0 until row.lastCellNum.toInt()) { if (fmt.formatCellValue(row.getCell(j)).contains("Маршрутний лист", true)) { isRS = true; break } }; if (isRS) break }
                    if (!isRS) return@withContext withContext(Dispatchers.Main) { onResult(ImportResult(false, "Файл не є маршрутним листом")) }
                    var hIdx = -1; val req = listOf("№ з/п", "№ заявки", "Населений пункт", "Найменування ТРТ", "Клієнт", "Адреса", "Примітка", "Телефон")
                    val idxs = mutableMapOf<String, Int>(); for (i in 0 until minOf(20, sheet.physicalNumberOfRows)) { val row = sheet.getRow(i) ?: continue; var fc = 0; for (j in 0 until row.lastCellNum.toInt()) { val cv = fmt.formatCellValue(row.getCell(j)).trim(); if (req.any { it.equals(cv, true) }) { idxs[cv.lowercase()] = j; fc++ } }; if (fc >= 5) { hIdx = i; break } }
                    if (hIdx == -1) return@withContext withContext(Dispatchers.Main) { onResult(ImportResult(false, "Не знайдено стовпці")) }
                    fun getI(n: String) = idxs[n.lowercase()] ?: -1
                    val ciI = getI("Населений пункт"); val shI = getI("Найменування ТРТ"); val clI = getI("Клієнт"); val adI = getI("Адреса"); val noI = getI("Примітка"); val phI = getI("Телефон")
                    val anyIdRegex = Regex("""([A-ZА-ЯІЇЄҐ]\d{1,3})|(\d{1,3}[A-ZА-ЯІЇЄҐ])""")
                    val added = mutableListOf<String>(); val currentLogs = mutableListOf<UpdateLogEntry>()
                    val allClients = clientDao.getAllClients().first()
                    val existingDNumbers = allClients.mapNotNull { Regex("""^D(\d+)D$""", RegexOption.IGNORE_CASE).find(it.client.label ?: "")?.groupValues?.get(1)?.toIntOrNull() }
                    var dCount = maxOf(allClients.count { it.client.label?.uppercase()?.startsWith("D") == true }, existingDNumbers.maxOrNull() ?: 0)

                    for (i in (hIdx + 1) until sheet.physicalNumberOfRows) {
                        val row = sheet.getRow(i) ?: continue; val nc = fmt.formatCellValue(row.getCell(noI)).trim()
                        val m = anyIdRegex.find(nc)
                        val label: String
                        val en: String
                        if (m != null) {
                            label = m.value.trim().uppercase()
                            en = nc.replace(m.value, "").trim().trim(',', '.', ';', ' ', ':', '-', '—')
                        } else {
                            dCount++
                            label = "D${dCount}D"
                            en = nc.trim().trim(',', '.', ';', ' ', ':', '-', '—')
                        }

                        val city = fmt.formatCellValue(row.getCell(ciI)).trim(); val shop = fmt.formatCellValue(row.getCell(shI)).trim()
                        val name = fmt.formatCellValue(row.getCell(clI)).trim(); val addr = fmt.formatCellValue(row.getCell(adI)).trim()
                        val phones = fmt.formatCellValue(row.getCell(phI)).trim().split("\n", "\r", ",", ";").map { it.replace(Regex("[^0-9+]"), "") }.filter { it.isNotBlank() }.distinct().sorted()
                        if (name.isBlank() && shop.isBlank()) continue
                        val existing = clientDao.getClientByLabel(label)
                        if (existing == null) {
                            val nid = clientDao.insertClient(ClientEntity(shopName = shop, city = city.ifBlank { null }, addressManual = addr.ifBlank { null }, addressStatus = if (addr.isNotBlank()) "changed" else "changed", label = label))
                            clientDao.insertName(NameEntity(clientId = nid, fullName = name)); phones.forEach { p -> clientDao.insertPhone(PhoneEntity(clientId = nid, phoneNumber = p)) }
                            if (en.isNotBlank()) clientDao.insertNote(NoteEntity(clientId = nid, content = en))
                            added.add("[$label] $shop")
                        } else {
                            var cur = existing.client; var changed = false; val mainName = existing.names.firstOrNull()?.fullName ?: ""
                            if (cur.namesStatus == "changed" && name != mainName && !name.contains("ФОП", true) && existing.names.none { it.fullName.trim() == name.trim() }) { clientDao.insertName(NameEntity(clientId = cur.id, fullName = name)); currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "names", mainName, "Додано: $name")); changed = true }
                            if (cur.shopNameStatus == "changed" && shop != cur.shopName) { currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "shopName", cur.shopName, shop)); cur = cur.copy(shopName = shop); changed = true }
                            if (cur.cityStatus == "changed" && city != (cur.city ?: "")) { currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "city", cur.city ?: "", city)); cur = cur.copy(city = city.ifBlank { null }); changed = true }
                            if (cur.phonesStatus == "changed") { val cp = existing.phones.map { it.phoneNumber }.distinct().sorted(); if (phones != cp) { clientDao.deletePhonesForClient(cur.id); phones.forEach { p -> clientDao.insertPhone(PhoneEntity(clientId = cur.id, phoneNumber = p)) }; currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "phones", cp.joinToString(", "), phones.joinToString(", "))); changed = true } }
                            if (addr.isNotBlank()) {
                                if (cur.addressManual.isNullOrBlank()) { cur = cur.copy(addressManual = addr, addressStatus = "changed"); currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "address", "", addr)); changed = true }
                                else if (cur.addressStatus == "changed" && addr.trim() != cur.addressManual.trim()) { currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "address", cur.addressManual, addr)); cur = cur.copy(addressManual = addr); changed = true }
                            }
                            if (cur.notesStatus == "changed" && en.isNotBlank() && existing.notes.none { it.content.trim().equals(en, true) }) { clientDao.insertNote(NoteEntity(clientId = cur.id, content = en)); currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "notes", "...", "Додано: $en")); changed = true }
                            if (changed) clientDao.insertClient(cur)
                        }
                    }
                    if (currentLogs.isNotEmpty()) {
                        val sid = clientDao.insertLogSession(UpdateLogSession(timestamp = System.currentTimeMillis()))
                        val entriesWithId = currentLogs.map { it.copy(sessionId = sid) }
                        entriesWithId.forEach { clientDao.insertLogEntry(it) }
                        
                        autoExportData()
                        withContext(Dispatchers.Main) { 
                            onResult(ImportResult(
                                success = true, 
                                message = "Обробка завершена", 
                                added = added, 
                                updated = currentLogs.map { it.clientLabel }.distinct(),
                                logs = entriesWithId 
                            )) 
                        }
                    } else {
                        autoExportData()
                        withContext(Dispatchers.Main) { 
                            onResult(ImportResult(true, "Обробка завершена", added, emptyList(), emptyList())) 
                        }
                    }
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { onResult(ImportResult(false, "Помилка: ${e.localizedMessage}")) } }
        }
    }

    fun exportData(uri: Uri, onComplete: (Boolean) -> Unit) { viewModelScope.launch { try { val all = clientDao.getAllClients().first(); val json = gson.toJson(all); withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }; onComplete(true) } catch (e: Exception) { onComplete(false) } } }
    fun importData(uri: Uri, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonText = withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }
                if (jsonText != null) {
                    val jsonArray = com.google.gson.JsonParser.parseString(jsonText).asJsonArray
                    clientDao.deleteAllClients(); clientDao.deleteAllNames(); clientDao.deleteAllPhones(); clientDao.deleteAllNotes()
                    var count = 0
                    jsonArray.forEach { element ->
                        val obj = element.asJsonObject; val clientObj = obj.getAsJsonObject("client")
                        val nStatus = clientObj.get("namesStatus")?.asString ?: clientObj.get("fullNameStatus")?.asString ?: "changed"
                        val nid = clientDao.insertClient(ClientEntity(id = 0, middleName = clientObj.get("middleName")?.asString ?: "", shopName = clientObj.get("shopName")?.asString ?: "", city = clientObj.get("city")?.asString, addressManual = clientObj.get("addressManual")?.asString, latitude = clientObj.get("latitude")?.asDouble, longitude = clientObj.get("longitude")?.asDouble, label = clientObj.get("label")?.asString, namesStatus = nStatus, shopNameStatus = clientObj.get("shopNameStatus")?.asString ?: "changed", cityStatus = clientObj.get("cityStatus")?.asString ?: "changed", addressStatus = clientObj.get("addressStatus")?.asString ?: "changed", phonesStatus = clientObj.get("phonesStatus")?.asString ?: "changed", notesStatus = clientObj.get("notesStatus")?.asString ?: "changed"))
                        count++
                        if (obj.has("names")) obj.getAsJsonArray("names").forEach { clientDao.insertName(NameEntity(clientId = nid, fullName = it.asJsonObject.get("fullName").asString)) }
                        else if (clientObj.has("fullName")) clientDao.insertName(NameEntity(clientId = nid, fullName = clientObj.get("fullName").asString))
                        else if (clientObj.has("firstName") || clientObj.has("lastName")) { val f = clientObj.get("firstName")?.asString ?: ""; val l = clientObj.get("lastName")?.asString ?: ""; clientDao.insertName(NameEntity(clientId = nid, fullName = "$l $f".trim())) }
                        obj.getAsJsonArray("phones")?.forEach { clientDao.insertPhone(PhoneEntity(clientId = nid, phoneNumber = it.asJsonObject.get("phoneNumber").asString)) }
                        obj.getAsJsonArray("notes")?.forEach { clientDao.insertNote(NoteEntity(clientId = nid, content = it.asJsonObject.get("content").asString)) }
                    }
                    onComplete(count)
                } else onComplete(-1)
            } catch (e: Exception) { onComplete(-1) }
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocationAddress(onResult: (String?, String?, Double?, Double?) -> Unit) { viewModelScope.launch { try { val location = withContext(Dispatchers.IO) { fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).let { com.google.android.gms.tasks.Tasks.await(it) } }; location?.let { l -> getAddressFromLocation(l.latitude, l.longitude) { c, s -> onResult(c, s, l.latitude, l.longitude) } } ?: onResult(null, null, null, null) } catch (e: Exception) { onResult(null, null, null, null) } } }
    fun getAddressFromLocation(latitude: Double, longitude: Double, onResult: (String?, String?) -> Unit) { viewModelScope.launch { try { val data = withContext(Dispatchers.IO) { val addresses = Geocoder(getApplication(), Locale.getDefault()).getFromLocation(latitude, longitude, 1); if (!addresses.isNullOrEmpty()) { val a = addresses[0]; val city = a.locality ?: a.subAdminArea ?: ""; val fullStreet = listOf(a.thoroughfare ?: "", a.subThoroughfare ?: "").filter { it.isNotBlank() }.joinToString(", "); city to fullStreet } else null to null }; onResult(data.first, data.second) } catch (e: Exception) { onResult(null, null) } } }
}

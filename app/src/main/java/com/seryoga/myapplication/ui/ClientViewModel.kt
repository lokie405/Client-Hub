package com.seryoga.myapplication.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Environment
import android.provider.CallLog
import android.util.Log
import androidx.compose.ui.graphics.Color
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

data class ImportedRouteClient(
    val clientId: Long,
    val label: String,
    val shopName: String,
    val city: String?,
    val name: String,
    val orderNumber: String = "",
    val weightKg: Double = 0.0,
    val amountSum: Double = 0.0
)

data class ImportResult(
    val success: Boolean,
    val message: String,
    val added: List<String> = emptyList(),
    val updated: List<String> = emptyList(),
    val logs: List<UpdateLogEntry> = emptyList(),
    val routeClients: List<ImportedRouteClient> = emptyList()
)

data class ClientGeoDetails(
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    val coordinatesManual: String?
)

fun getTaskIconTint(tasks: List<TaskEntity>): androidx.compose.ui.graphics.Color {
    if (tasks.isEmpty()) return androidx.compose.ui.graphics.Color(0xFF9E9E9E)
    val hasActive = tasks.any { !it.isCompleted }
    return if (hasActive) androidx.compose.ui.graphics.Color(0xFFF44336) else androidx.compose.ui.graphics.Color(0xFF4CAF50)
}

fun normalizeCyrillicLookalikes(text: String): String {
    if (text.isBlank()) return ""
    return text
        .replace('i', 'і')
        .replace('I', 'І')
        .replace('e', 'е')
        .replace('E', 'Е')
        .replace('o', 'о')
        .replace('O', 'О')
        .replace('a', 'а')
        .replace('A', 'А')
        .replace('c', 'с')
        .replace('C', 'С')
        .replace('p', 'р')
        .replace('P', 'Р')
        .replace('x', 'х')
        .replace('X', 'Х')
}

fun cleanTextForCopy(text: String): String {
    if (text.isBlank()) return ""
    val cleaned = text
        .replace(Regex("""(?i)\b(ФОП|маг\.|м-н\.|маг|м-н)\b\.?"""), "")
        .replace(Regex("""(?i)^[,\.\s:\-—"“'«»]+|[,\.\s:\-—"“'«»]+$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim('"', '“', '”', '«', '»', ' ')
    return normalizeCyrillicLookalikes(cleaned)
}

fun isHttpsCoordinates(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    return raw.trim().lowercase().contains("https")
}

fun isCoordinatesFieldFilled(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    val clean = raw.trim().lowercase()
    if (clean == "null" || clean == "координати" || clean.startsWith("координати (ручне") || clean == "координати (ручне введення)") {
        return false
    }
    return true
}

fun extractUrlFromText(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val regex = Regex("""https?://[^\s]+""")
    val match = regex.find(text.trim())
    return match?.value
}

fun parseCoordinates(raw: String?): Pair<Double, Double>? {
    if (raw.isNullOrBlank()) return null
    val clean = raw.trim()
    val regex = Regex("""([+-]?\d+(?:[\.,]\d+)?)[\s,;]+([+-]?\d+(?:[\.,]\d+)?)""")
    val match = regex.find(clean)
    if (match != null) {
        val lat = match.groupValues[1].replace(',', '.').toDoubleOrNull()
        val lng = match.groupValues[2].replace(',', '.').toDoubleOrNull()
        if (lat != null && lng != null) {
            return Pair(lat, lng)
        }
    }
    val parts = clean.split(";", " ").mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
    if (parts.size >= 2) {
        return Pair(parts[0], parts[1])
    }
    return null
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClientViewModel(application: Application) : AndroidViewModel(application) {
    private val clientDao: ClientDao = AppDatabase.getDatabase(application).clientDao()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private val gson = Gson()
    private val prefs = application.getSharedPreferences("client_app_prefs", Context.MODE_PRIVATE)

    private val _isRouteSheetLocked = MutableStateFlow(false)
    val isRouteSheetLocked: StateFlow<Boolean> = _isRouteSheetLocked.asStateFlow()

    fun setRouteSheetLocked(locked: Boolean) {
        _isRouteSheetLocked.value = locked
    }

    private val _showCityHeadersInRouteSheet = MutableStateFlow(
        prefs.getBoolean("show_city_headers_route_sheet", true)
    )
    val showCityHeadersInRouteSheet: StateFlow<Boolean> = _showCityHeadersInRouteSheet.asStateFlow()

    fun setShowCityHeadersInRouteSheet(enabled: Boolean) {
        prefs.edit().putBoolean("show_city_headers_route_sheet", enabled).apply()
        _showCityHeadersInRouteSheet.value = enabled
    }

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
        }
        .catch { e -> Log.e("ClientViewModel", "Error in clients flow", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val logSessions: Flow<List<UpdateLogSession>> = clientDao.getAllLogSessions()

    val activeRouteSheet: StateFlow<RouteSheetWithItems?> = clientDao.getActiveRouteSheet()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val allRouteSheets: Flow<List<RouteSheetWithItems>> = clientDao.getAllRouteSheetsWithItems()

    init {
        checkAndArchiveMidnightRoutes()
    }

    fun checkAndArchiveMidnightRoutes() {
        // Auto-archiving at 00:00 disabled per user request.
        // Route sheets are now preserved until a new route file is imported.
    }

    fun archiveActiveRouteSheet() {
        viewModelScope.launch {
            clientDao.archiveActiveRouteSheet()
        }
    }

    fun createRouteSheet(clients: List<ImportedRouteClient>, savePreviousToJournal: Boolean = true) {
        if (clients.isEmpty()) return
        viewModelScope.launch {
            val active = clientDao.getActiveRouteSheet().first()
            if (active != null) {
                if (savePreviousToJournal) {
                    clientDao.archiveActiveRouteSheet()
                } else {
                    clientDao.deleteRouteSheetItems(active.routeSheet.id)
                    clientDao.deleteRouteSheet(active.routeSheet.id)
                }
            }
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val routeSheetId = clientDao.insertRouteSheet(
                RouteSheetEntity(
                    dateString = today,
                    createdTimestamp = System.currentTimeMillis(),
                    isArchived = false
                )
            )
            val items = clients.mapIndexed { index, c ->
                RouteSheetItemEntity(
                    routeSheetId = routeSheetId,
                    clientId = c.clientId,
                    orderIndex = index,
                    clientLabelSnapshot = c.label,
                    clientShopSnapshot = c.shopName,
                    clientCitySnapshot = c.city,
                    clientNameSnapshot = c.name,
                    orderNumber = c.orderNumber,
                    weightKg = c.weightKg,
                    amountSum = c.amountSum
                )
            }
            clientDao.insertRouteSheetItems(items)
        }
    }

    fun reorderRouteItems(items: List<RouteSheetItemEntity>) {
        viewModelScope.launch {
            val updated = items.mapIndexed { index, item -> item.copy(orderIndex = index) }
            clientDao.updateRouteSheetItems(updated)
        }
    }

    fun moveRouteItemUp(items: List<RouteSheetItemEntity>, index: Int) {
        if (index <= 0 || index >= items.size) return
        val list = items.toMutableList()
        val item = list.removeAt(index)
        list.add(index - 1, item)
        reorderRouteItems(list)
    }

    fun moveRouteItemDown(items: List<RouteSheetItemEntity>, index: Int) {
        if (index < 0 || index >= items.size - 1) return
        val list = items.toMutableList()
        val item = list.removeAt(index)
        list.add(index + 1, item)
        reorderRouteItems(list)
    }

    fun updateRouteItemNote(
        item: RouteSheetItemEntity,
        noteText: String,
        audioUri: String? = item.noteAudioUri,
        photoUri: String? = item.notePhotoUri,
        fileUri: String? = item.noteFileUri
    ) {
        viewModelScope.launch {
            val updated = item.copy(
                noteText = noteText,
                noteAudioUri = audioUri,
                notePhotoUri = photoUri,
                noteFileUri = fileUri
            )
            clientDao.updateRouteSheetItem(updated)
        }
    }

    fun toggleRouteItemCompleted(item: RouteSheetItemEntity, isCompleted: Boolean) {
        viewModelScope.launch {
            clientDao.updateRouteSheetItem(item.copy(isCompleted = isCompleted))
        }
    }

    fun deleteRouteSheet(routeSheetId: Long) {
        viewModelScope.launch {
            clientDao.deleteRouteSheetItems(routeSheetId)
            clientDao.deleteRouteSheet(routeSheetId)
        }
    }

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

    suspend fun getClientGeoDetails(clientId: Long): ClientGeoDetails {
        if (clientId == 0L) return ClientGeoDetails(null, null, null, null)
        val details = clientDao.getClientById(clientId) ?: return ClientGeoDetails(null, null, null, null)
        val client = details.client
        val manualCoords = client.coordinatesManual
        val parsed = parseCoordinates(manualCoords)
        val lat = parsed?.first ?: client.latitude
        val lng = parsed?.second ?: client.longitude
        return ClientGeoDetails(client.addressManual, lat, lng, manualCoords)
    }

    suspend fun getClientAddressAndCoords(clientId: Long): Triple<String?, Double?, Double?> {
        val geo = getClientGeoDetails(clientId)
        return Triple(geo.address, geo.lat, geo.lng)
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

    fun addClient(
        id: Long? = null,
        names: List<String>,
        shopName: String,
        city: String? = null,
        phones: List<String>,
        notes: List<String> = emptyList(),
        address: String? = null,
        coordinatesManual: String? = null,
        lat: Double? = null,
        lon: Double? = null,
        label: String? = null,
        namesStatus: String = "changed",
        shopNameStatus: String = "changed",
        cityStatus: String = "changed",
        addressStatus: String = "changed",
        phonesStatus: String = "changed",
        notesStatus: String = "changed"
    ) {
        viewModelScope.launch {
            val clientId = clientDao.insertClient(
                ClientEntity(
                    id = id ?: 0,
                    shopName = shopName,
                    city = city,
                    addressManual = address,
                    coordinatesManual = coordinatesManual,
                    latitude = lat,
                    longitude = lon,
                    label = label,
                    namesStatus = namesStatus,
                    shopNameStatus = shopNameStatus,
                    cityStatus = cityStatus,
                    addressStatus = addressStatus,
                    phonesStatus = phonesStatus,
                    notesStatus = notesStatus
                )
            )
            clientDao.deleteNamesForClient(clientId)
            names.filter { it.isNotBlank() }.forEach { clientDao.insertName(NameEntity(clientId = clientId, fullName = it)) }

            clientDao.deletePhonesForClient(clientId)
            phones.filter { it.isNotBlank() }.forEach { clientDao.insertPhone(PhoneEntity(clientId = clientId, phoneNumber = it)) }

            clientDao.deleteNotesForClient(clientId)
            notes.filter { it.isNotBlank() }.forEach { clientDao.insertNote(NoteEntity(clientId = clientId, content = it)) }

            val targetId = if (id != null && id != 0L) id else clientId
            val activeRoute = activeRouteSheet.value
            if (activeRoute != null && targetId != 0L) {
                val matchingItems = activeRoute.items.filter { it.clientId == targetId }
                if (matchingItems.isNotEmpty()) {
                    val mainName = names.filter { it.isNotBlank() }.firstOrNull() ?: ""
                    val updatedItems = matchingItems.map { item ->
                        item.copy(
                            clientLabelSnapshot = label ?: "",
                            clientShopSnapshot = shopName,
                            clientCitySnapshot = city,
                            clientNameSnapshot = mainName
                        )
                    }
                    clientDao.updateRouteSheetItems(updatedItems)
                }
            }

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

    fun addTask(clientId: Long, title: String, commentText: String = "", photoUri: String? = null, audioUri: String? = null) {
        viewModelScope.launch {
            clientDao.insertTask(
                TaskEntity(
                    clientId = clientId,
                    title = title,
                    commentText = commentText,
                    photoUri = photoUri,
                    audioUri = audioUri,
                    createdTimestamp = System.currentTimeMillis(),
                    isCompleted = false
                )
            )
            autoExportData()
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            clientDao.updateTask(task)
            autoExportData()
        }
    }

    fun toggleTaskCompleted(task: TaskEntity) {
        viewModelScope.launch {
            clientDao.updateTask(task.copy(isCompleted = !task.isCompleted))
            autoExportData()
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            clientDao.deleteTask(task)
            autoExportData()
        }
    }

    fun getTasksForClient(clientId: Long): Flow<List<TaskEntity>> = clientDao.getTasksForClient(clientId)
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
                    var hIdx = -1; val reqKeywords = listOf("з/п", "заявк", "населен", "нанайменуван", "тр т", "клієнт", "адрес", "примітк", "телефон", "брутт", "сума", "вага")
                    for (i in 0 until minOf(25, sheet.physicalNumberOfRows)) {
                        val row = sheet.getRow(i) ?: continue
                        var fc = 0
                        for (j in 0 until row.lastCellNum.toInt()) {
                            val cv = fmt.formatCellValue(row.getCell(j)).lowercase().trim()
                            if (reqKeywords.any { cv.contains(it) }) fc++
                        }
                        if (fc >= 4) { hIdx = i; break }
                    }
                    if (hIdx == -1) return@withContext withContext(Dispatchers.Main) { onResult(ImportResult(false, "Не знайдено стовпці в таблиці")) }

                    val mainHeaderRow = sheet.getRow(hIdx)
                    val nextRow = sheet.getRow(hIdx + 1)
                    val nextRowKeywords = listOf("брутт", "сума", "вага", "тн", "грн")
                    val hasSubHeader = nextRow != null && (0 until nextRow.lastCellNum.toInt()).any { j ->
                        val valSub = fmt.formatCellValue(nextRow.getCell(j)).lowercase().trim()
                        nextRowKeywords.any { valSub.contains(it) }
                    }

                    val dataStartRowIdx = if (hasSubHeader) hIdx + 2 else hIdx + 1
                    val maxCols = maxOf(mainHeaderRow?.lastCellNum?.toInt() ?: 0, nextRow?.lastCellNum?.toInt() ?: 0)
                    val combinedHeaders = (0 until maxCols).map { j ->
                        val topVal = fmt.formatCellValue(mainHeaderRow?.getCell(j)).trim()
                        val subVal = if (hasSubHeader) fmt.formatCellValue(nextRow?.getCell(j)).trim() else ""
                        val full = listOfNotNull(topVal.ifBlank { null }, subVal.ifBlank { null }).joinToString(" ")
                        j to full
                    }

                    Log.d("ExcelHeaders", "=== ЗАГОЛОВКИ ТАБЛИЦІ (Рядок $hIdx, 2-рядкова шапка: $hasSubHeader) ===")
                    Log.d("ExcelHeaders", "Усі об'єднані стовпці: ${combinedHeaders.joinToString { "[${it.first}]: '${it.second}'" }}")

                    fun findColIndex(vararg keywords: String): Int {
                        for ((colIdx, headerText) in combinedHeaders) {
                            val lowerText = headerText.lowercase()
                            for (kw in keywords) {
                                if (lowerText.contains(kw.lowercase())) return colIdx
                            }
                        }
                        return -1
                    }

                    fun parseCleanDouble(raw: String): Double {
                        if (raw.isBlank()) return 0.0
                        val cleaned = raw.replace("\u00A0", "").replace(" ", "").replace(",", ".").replace(Regex("[^0-9.-]"), "")
                        return cleaned.toDoubleOrNull() ?: 0.0
                    }

                    fun parseNumericCellValue(cell: Cell?): Double {
                        if (cell == null) return 0.0
                        return try {
                            when (cell.cellType) {
                                CellType.NUMERIC -> cell.numericCellValue
                                CellType.FORMULA -> {
                                    try { cell.numericCellValue } catch (_: Exception) { parseCleanDouble(fmt.formatCellValue(cell)) }
                                }
                                else -> parseCleanDouble(fmt.formatCellValue(cell))
                            }
                        } catch (_: Exception) { 0.0 }
                    }

                    val ciI = findColIndex("населен", "місто")
                    val shI = findColIndex("найменуван", "трт", "магазин")
                    val clI = findColIndex("клієнт", "піб")
                    val adI = findColIndex("адрес")
                    val noI = findColIndex("примітк")
                    val phI = findColIndex("телефон")
                    val ordI = findColIndex("заявк", "накладн")
                    val wtI = findColIndex("брутт", "вага")
                    val sumI = findColIndex("сума", "вартість", "ціна")

                    Log.d("ExcelHeaders", "Знайдені індекси: Вага (Брутто)=$wtI, Сума=$sumI, №Заявки=$ordI, ТРТ=$shI, Клієнт=$clI, Місто=$ciI")

                    val anyIdRegex = Regex("""([A-ZА-ЯІЇЄҐ]\d{1,3})|(\d{1,3}[A-ZА-ЯІЇЄҐ])""")
                    val added = mutableListOf<String>(); val currentLogs = mutableListOf<UpdateLogEntry>()
                    val importedRouteClients = mutableListOf<ImportedRouteClient>()
                    val allClients = clientDao.getAllClients().first()
                    val currentDbClients = clientDao.getAllClients().first().toMutableList()
                    val existingDNumbers = currentDbClients.mapNotNull { Regex("""^D(\d+)D$""", RegexOption.IGNORE_CASE).find(it.client.label ?: "")?.groupValues?.get(1)?.toIntOrNull() }
                    var dCount = maxOf(currentDbClients.count { it.client.label?.uppercase()?.startsWith("D") == true }, existingDNumbers.maxOrNull() ?: 0)

                    fun findExistingClientByLabel(targetLabel: String): ClientWithDetails? {
                        if (targetLabel.isBlank()) return null
                        val cleanLabel = normalizeCyrillicLookalikes(targetLabel.trim().uppercase())
                        return currentDbClients.firstOrNull { c -> 
                            normalizeCyrillicLookalikes(c.client.label?.trim()?.uppercase() ?: "") == cleanLabel
                        }
                    }

                    fun findExistingClientByShopOrName(targetShop: String, targetCity: String, targetName: String): ClientWithDetails? {
                        val cleanShop = cleanTextForCopy(targetShop).lowercase()
                        val cleanCity = cleanTextForCopy(targetCity).lowercase()
                        val cleanName = cleanTextForCopy(targetName).lowercase()

                        if (cleanShop.isNotBlank()) {
                            val matchShopAndCity = currentDbClients.firstOrNull { c ->
                                val cShop = cleanTextForCopy(c.client.shopName).lowercase()
                                val cCity = cleanTextForCopy(c.client.city ?: "").lowercase()
                                cShop == cleanShop && (cleanCity.isBlank() || cCity == cleanCity)
                            }
                            if (matchShopAndCity != null) return matchShopAndCity

                            val matchShopExact = currentDbClients.firstOrNull { c ->
                                val cShop = cleanTextForCopy(c.client.shopName).lowercase()
                                val cCity = cleanTextForCopy(c.client.city ?: "").lowercase()
                                val isCityConflict = cleanCity.isNotBlank() && cCity.isNotBlank() && cleanCity != cCity
                                cShop == cleanShop && !isCityConflict
                            }
                            if (matchShopExact != null) return matchShopExact
                        }

                        if (cleanName.isNotBlank()) {
                            val matchName = currentDbClients.firstOrNull { c ->
                                val cCity = cleanTextForCopy(c.client.city ?: "").lowercase()
                                val isCityConflict = cleanCity.isNotBlank() && cCity.isNotBlank() && cleanCity != cCity
                                !isCityConflict && c.names.any { n ->
                                    val cName = cleanTextForCopy(n.fullName).lowercase()
                                    cName == cleanName
                                }
                            }
                            if (matchName != null) return matchName
                        }

                        return null
                    }

                    Log.d("way", "=== ПОЧАТОК СКАНУВАННЯ ТАБЛИЦІ МАРШРУТНОГО ЛИСТА (Всього рядків: ${sheet.physicalNumberOfRows}, Початковий рядок даних: $dataStartRowIdx) ===")

                    for (i in dataStartRowIdx until sheet.physicalNumberOfRows) {
                        val row = sheet.getRow(i)
                        if (row == null) {
                            Log.d("way", "[Рядок $i] Рядок порожній (null) -> ПРОПУЩЕНО")
                            continue
                        }
                        val nc = if (noI != -1) fmt.formatCellValue(row.getCell(noI)).trim() else ""
                        val m = anyIdRegex.find(nc)
                        val city = if (ciI != -1) fmt.formatCellValue(row.getCell(ciI)).trim() else ""
                        val shop = if (shI != -1) fmt.formatCellValue(row.getCell(shI)).trim() else ""
                        val name = if (clI != -1) fmt.formatCellValue(row.getCell(clI)).trim() else ""
                        val addr = if (adI != -1) fmt.formatCellValue(row.getCell(adI)).trim() else ""
                        val phones = if (phI != -1) fmt.formatCellValue(row.getCell(phI)).trim().split("\n", "\r", ",", ";").map { it.replace(Regex("[^0-9+]"), "") }.filter { it.isNotBlank() }.distinct().sorted() else emptyList()
                        val orderNumber = if (ordI != -1) fmt.formatCellValue(row.getCell(ordI)).trim() else ""

                        val rawWeight = if (wtI != -1) parseNumericCellValue(row.getCell(wtI)) else 0.0
                        val weightKg = if (rawWeight > 0.0 && rawWeight < 30.0) rawWeight * 1000.0 else rawWeight
                        val amountSum = if (sumI != -1) parseNumericCellValue(row.getCell(sumI)) else 0.0

                        Log.d("way", "--------------------------------------------------")
                        Log.d("way", "[Рядок $i] СКАНУВАННЯ: ТРТ='$shop' | Клієнт='$name' | Місто='$city' | Примітка='$nc' | Адреса='$addr' | Тел=$phones | Заявка='$orderNumber' | Вага=$weightKg кг | Сума=$amountSum грн")

                        if (name.isBlank() && shop.isBlank()) {
                            Log.d("way", "[Рядок $i] РІШЕННЯ: ПРОПУСТИТИ. Причина: Назва магазину (ТРТ) та ПІБ клієнта порожні.")
                            continue
                        }

                        val label: String
                        val en: String
                        val existing: ClientWithDetails?

                        if (m != null) {
                            label = normalizeCyrillicLookalikes(m.value.trim().uppercase())
                            en = nc.replace(m.value, "").trim().trim(',', '.', ';', ' ', ':', '-', '—')
                            // Шукаємо клієнта СУВОРО за міткою з примітки
                            existing = findExistingClientByLabel(label)
                            if (existing != null) {
                                Log.d("way", "[Рядок $i] МІТКА: Знайдено в примітці -> '$label'. Клієнта з міткою '$label' ЗНАЙДЕНО в БД (ID=${existing.client.id}).")
                            } else {
                                Log.d("way", "[Рядок $i] МІТКА: Знайдено в примітці -> '$label'. Мітка НОВА, клієнта з міткою '$label' немає в БД.")
                            }
                        } else {
                            en = nc.trim().trim(',', '.', ';', ' ', ':', '-', '—')
                            Log.d("way", "[Рядок $i] МІТКА: У примітці мітку не знайдено.")
                            val duplicate = findExistingClientByShopOrName(shop, city, name)
                            if (duplicate != null) {
                                existing = duplicate
                                label = duplicate.client.label ?: ""
                                Log.d("way", "[Рядок $i] МІТКА: Визначено за існуючим клієнтом в БД (ID=${duplicate.client.id}) -> '$label'")
                            } else {
                                existing = null
                                dCount++
                                label = "D${dCount}D"
                                Log.d("way", "[Рядок $i] МІТКА: Згенеровано нову тимчасову мітку D-класу -> '$label'")
                            }
                        }

                        if (existing == null) {
                            Log.d("way", "[Рядок $i] РІШЕННЯ: ДОДАТИ НОВОГО КЛІЄНТА (Збігів у БД не знайдено)")
                            val cleanNameForNew = cleanTextForCopy(name).ifBlank { name }
                            val nid = clientDao.insertClient(ClientEntity(shopName = shop, city = city.ifBlank { null }, addressManual = addr.ifBlank { null }, addressStatus = "unchanged", label = label))
                            clientDao.insertName(NameEntity(clientId = nid, fullName = cleanNameForNew)); phones.forEach { p -> clientDao.insertPhone(PhoneEntity(clientId = nid, phoneNumber = p)) }
                            if (en.isNotBlank()) clientDao.insertNote(NoteEntity(clientId = nid, content = en))
                            added.add("[$label] $shop")
                            importedRouteClients.add(ImportedRouteClient(nid, label, shop, city.ifBlank { null }, cleanNameForNew, orderNumber, weightKg, amountSum))
                            clientDao.getClientById(nid)?.let { currentDbClients.add(it) }

                            Log.d("way", "[Рядок $i] ДІЯ ПРИЙНЯТА ТА ЗРОБЛЕНА: Створено нового клієнта ID=$nid з початковими даними в БД [Мітка='$label', ТРТ='$shop', Місто='${city.ifBlank { "null" }}', ПІБ='$cleanNameForNew', Телефони=$phones, Примітка='${en.ifBlank { "немає" }}']")
                        } else {
                            val dbNamesStr = existing.names.map { "'${it.fullName}'" }.joinToString(", ")
                            val dbPhonesStr = existing.phones.map { "'${it.phoneNumber}'" }.joinToString(", ")
                            val dbNotesStr = existing.notes.map { "'${it.content}'" }.joinToString("; ")
                            
                            Log.d("way", "[Рядок $i] РІШЕННЯ: ОНОВИТИ ІСНУЮЧОГО КЛІЄНТА")
                            Log.d("way", "[Рядок $i] [В БД ВЖЕ БУЛО КЛІЄНТА ID=${existing.client.id}] Мітка='${existing.client.label}' | ТРТ='${existing.client.shopName}' (статус: ${existing.client.shopNameStatus}) | Місто='${existing.client.city}' (статус: ${existing.client.cityStatus}) | Імена=[$dbNamesStr] | Телефони=[$dbPhonesStr] | Адреса='${existing.client.addressManual}' | Примітки=[$dbNotesStr]")

                            importedRouteClients.add(ImportedRouteClient(existing.client.id, label, shop, city.ifBlank { null }, name, orderNumber, weightKg, amountSum))
                            var cur = existing.client
                            var changed = false
                            val mainName = existing.names.firstOrNull()?.fullName ?: ""
                            val actionsDone = mutableListOf<String>()

                            if (label.isNotBlank() && normalizeCyrillicLookalikes(cur.label ?: "") != normalizeCyrillicLookalikes(label)) {
                                val oldLabel = cur.label
                                cur = cur.copy(label = label)
                                changed = true
                                actionsDone.add("Змінено мітку з '$oldLabel' на '$label'")
                                Log.d("way", "[Рядок $i] [Заміна мітки] В БД було: '$oldLabel' -> Замінюємо на: '$label'")
                            } else {
                                Log.d("way", "[Рядок $i] [Мітка] Без змін (в БД вже було: '${cur.label}')")
                            }

                            // Clean FOP/mag prefix and check if clean name exists in client's names list -> Add as additional name
                            val cleanNameInput = cleanTextForCopy(name)
                            if (cleanNameInput.isNotBlank()) {
                                val nameAlreadyExists = existing.names.any {
                                    val existingClean = cleanTextForCopy(it.fullName)
                                    existingClean.equals(cleanNameInput, ignoreCase = true) || it.fullName.trim().equals(cleanNameInput, ignoreCase = true)
                                }
                                if (!nameAlreadyExists) {
                                    val newNameEntity = NameEntity(clientId = cur.id, fullName = cleanNameInput)
                                    clientDao.insertName(newNameEntity)
                                    currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "names", mainName, "Додано: $cleanNameInput"))
                                    changed = true
                                    actionsDone.add("Додано нову назву/ПІБ '$cleanNameInput'")
                                    Log.d("way", "[Рядок $i] [Додавання імені] В БД вже були імена: [$dbNamesStr] -> Додаємо нове ім'я: '$cleanNameInput'")

                                    // Update memory cache immediately
                                    clientDao.getClientById(cur.id)?.let { refreshedDetail ->
                                        val idx = currentDbClients.indexOfFirst { it.client.id == cur.id }
                                        if (idx != -1) {
                                            currentDbClients[idx] = refreshedDetail
                                        }
                                    }
                                } else {
                                    Log.d("way", "[Рядок $i] [ПІБ] Назва/ПІБ '$cleanNameInput' вже є в БД в списку імен: [$dbNamesStr]")
                                }
                            }

                            if (cur.shopNameStatus == "changed" && shop.isNotBlank() && cleanTextForCopy(shop) != cleanTextForCopy(cur.shopName)) {
                                currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "shopName", cur.shopName, shop))
                                actionsDone.add("Оновлено ТРТ з '${cur.shopName}' на '$shop'")
                                Log.d("way", "[Рядок $i] [Заміна ТРТ] В БД було: '${cur.shopName}' (статус: ${cur.shopNameStatus}) -> Замінюємо на: '$shop'")
                                cur = cur.copy(shopName = shop)
                                changed = true
                            } else if (shop.isNotBlank() && cleanTextForCopy(shop) != cleanTextForCopy(cur.shopName)) {
                                Log.d("way", "[Рядок $i] [ТРТ НЕ замінено] В БД лишається: '${cur.shopName}' (заблоковано, статус: ${cur.shopNameStatus}). В таблиці було: '$shop'")
                            }

                            if (cur.cityStatus == "changed" && city.isNotBlank() && cleanTextForCopy(city) != cleanTextForCopy(cur.city ?: "")) {
                                currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "city", cur.city ?: "", city))
                                actionsDone.add("Оновлено місто з '${cur.city}' на '$city'")
                                Log.d("way", "[Рядок $i] [Заміна міста] В БД було: '${cur.city}' (статус: ${cur.cityStatus}) -> Замінюємо на: '$city'")
                                cur = cur.copy(city = city)
                                changed = true
                            } else if (city.isNotBlank() && cleanTextForCopy(city) != cleanTextForCopy(cur.city ?: "")) {
                                Log.d("way", "[Рядок $i] [Місто НЕ замінено] В БД лишається: '${cur.city}' (заблоковано, статус: ${cur.cityStatus}). В таблиці було: '$city'")
                            }

                            val validPhonesInput = phones.filter { p -> p.replace(Regex("[^0-9]"), "").length >= 9 }
                            if (cur.phonesStatus == "changed" && validPhonesInput.isNotEmpty()) {
                                val cp = existing.phones.map { it.phoneNumber }.distinct().sorted()
                                if (validPhonesInput != cp) {
                                    clientDao.deletePhonesForClient(cur.id)
                                    validPhonesInput.forEach { p -> clientDao.insertPhone(PhoneEntity(clientId = cur.id, phoneNumber = p)) }
                                    currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "phones", cp.joinToString(", "), validPhonesInput.joinToString(", ")))
                                    actionsDone.add("Оновлено телефони з '${cp.joinToString(", ")}' на '${validPhonesInput.joinToString(", ")}'")
                                    Log.d("way", "[Рядок $i] [Заміна телефонів] В БД були телефони: $cp -> Замінюємо на: $validPhonesInput")
                                    changed = true
                                } else {
                                    Log.d("way", "[Рядок $i] [Телефони] В БД вже були такі ж телефони: $cp")
                                }
                            } else if (phones.isNotEmpty()) {
                                Log.d("way", "[Рядок $i] [Телефони НЕ замінено] Некоректний телефон у файлі ($phones) або заблоковано (phonesStatus='${cur.phonesStatus}'). В БД лишаються телефони: $dbPhonesStr")
                            }

                            if (addr.isNotBlank() && cur.addressManual.isNullOrBlank()) {
                                cur = cur.copy(addressManual = addr, addressStatus = "unchanged")
                                currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "address", "", addr))
                                actionsDone.add("Додано ручну адресу '$addr'")
                                Log.d("way", "[Рядок $i] [Заповнення адреси] В БД адреси не було (null) -> Встановлюємо ручну адресу: '$addr'")
                                changed = true
                            } else if (addr.isNotBlank()) {
                                Log.d("way", "[Рядок $i] [Адреса НЕ замінена] В БД вже є ручна адреса: '${cur.addressManual}'. В таблиці була: '$addr'")
                            }

                            if (cur.notesStatus == "changed" && en.isNotBlank() && existing.notes.none { cleanTextForCopy(it.content).equals(cleanTextForCopy(en), ignoreCase = true) }) {
                                clientDao.insertNote(NoteEntity(clientId = cur.id, content = en))
                                currentLogs.add(UpdateLogEntry(0, 0, label, mainName, "notes", "...", "Додано: $en"))
                                actionsDone.add("Додано примітку '$en'")
                                Log.d("way", "[Рядок $i] [Додавання примітки] В БД вже були примітки: [$dbNotesStr] -> Додаємо нову примітку: '$en'")
                                changed = true
                            } else if (en.isNotBlank()) {
                                Log.d("way", "[Рядок $i] [Примітка НЕ додана] В БД примітка вже існує або notesStatus='${cur.notesStatus}'. В таблиці була: '$en'")
                            }

                            if (changed) {
                                clientDao.insertClient(cur)
                                clientDao.getClientById(cur.id)?.let { updatedDetail ->
                                    val idx = currentDbClients.indexOfFirst { it.client.id == cur.id }
                                    if (idx != -1) {
                                        currentDbClients[idx] = updatedDetail
                                    }
                                }
                                Log.d("way", "[Рядок $i] ДІЯ ПРИЙНЯТА ТА ЗРОБЛЕНА: Клієнта ID=${existing.client.id} успішно оновлено. Виконано оновлення: ${actionsDone.joinToString("; ")}")
                            } else {
                                Log.d("way", "[Рядок $i] ДІЯ ПРИЙНЯТА ТА ЗРОБЛЕНА: Оновлення клієнта ID=${existing.client.id} ПРОПУЩЕНО, змін не виявлено (всі поля відповідають БД або заблоковані від оновлення).")
                            }
                        }
                    }
                    val last3 = importedRouteClients.takeLast(3)
                    Log.d("ExcelRouteImport", "=== УСПІШНО ЗЧИТАНО РЯДКІВ: ${importedRouteClients.size} ===")
                    Log.d("ExcelRouteImport", "Індекси стовпців: wtI=$wtI, sumI=$sumI, ordI=$ordI, ciI=$ciI, shI=$shI, clI=$clI")
                    last3.forEachIndexed { idx, item ->
                        val pos = importedRouteClients.size - last3.size + idx + 1
                        Log.d("ExcelRouteImport", "Останній рядок #$pos: ТРТ='${item.shopName}', Клієнт='${item.name}', Місто='${item.city}', Заявка='${item.orderNumber}', Вага=${item.weightKg} кг, Сума=${item.amountSum} грн")
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
                                logs = entriesWithId,
                                routeClients = importedRouteClients
                            )) 
                        }
                    } else {
                        autoExportData()
                        withContext(Dispatchers.Main) { 
                            onResult(ImportResult(true, "Обробка завершена", added, emptyList(), emptyList(), importedRouteClients)) 
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
                        val coordsManual = clientObj.get("coordinatesManual")?.asString
                        val nid = clientDao.insertClient(ClientEntity(id = 0, middleName = clientObj.get("middleName")?.asString ?: "", shopName = clientObj.get("shopName")?.asString ?: "", city = clientObj.get("city")?.asString, addressManual = clientObj.get("addressManual")?.asString, coordinatesManual = coordsManual, latitude = clientObj.get("latitude")?.asDouble, longitude = clientObj.get("longitude")?.asDouble, label = clientObj.get("label")?.asString, namesStatus = nStatus, shopNameStatus = clientObj.get("shopNameStatus")?.asString ?: "changed", cityStatus = clientObj.get("cityStatus")?.asString ?: "changed", addressStatus = clientObj.get("addressStatus")?.asString ?: "changed", phonesStatus = clientObj.get("phonesStatus")?.asString ?: "changed", notesStatus = clientObj.get("notesStatus")?.asString ?: "changed"))
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

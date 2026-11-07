package com.seryoga.myapplication.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import java.io.File
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.seryoga.myapplication.data.*
import com.seryoga.myapplication.ui.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientListScreen(
    viewModel: ClientViewModel,
    onClientClick: (Long) -> Unit,
    onAddClientClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRouteSheetClick: () -> Unit = {},
    onRouteJournalClick: () -> Unit = {}
) {
    val clients by viewModel.clients.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val focusRequester = remember { FocusRequester() }
    var selectedClientForPhones by remember { mutableStateOf<Long?>(null) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var pendingRouteClients by remember { mutableStateOf<List<ImportedRouteClient>?>(null) }

    val labelCounts = remember(clients) {
        clients.groupingBy { it.client.label }.eachCount()
    }

    val routePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importRouteSheet(it) { result ->
                importResult = result
                if (!result.success) {
                    scope.launch { snackbarHostState.showSnackbar(result.message) }
                }
            }
        }
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let {
                viewModel.updateSearchQuery(it)
            }
        }
    }

    val startVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говоріть...")
        }
        try { voiceSearchLauncher.launch(intent) } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    if (selectedClientForPhones != null) {
        PhoneNumbersDialog(
            clientId = selectedClientForPhones!!,
            onDismiss = { selectedClientForPhones = null },
            viewModel = viewModel
        )
    }

    if (pendingRouteClients != null) {
        val activeRouteSheet by viewModel.activeRouteSheet.collectAsState()
        val hasExistingActiveRoute = activeRouteSheet != null && activeRouteSheet!!.items.isNotEmpty()

        if (hasExistingActiveRoute) {
            AlertDialog(
                onDismissRequest = { pendingRouteClients = null },
                title = { Text("Скласти новий маршрутник?") },
                text = { Text("У вас вже є активний маршрутник. Зберегти його з усіма нотатками в журнал перед створенням нового?") },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = {
                            val list = pendingRouteClients!!
                            pendingRouteClients = null
                            viewModel.createRouteSheet(list, savePreviousToJournal = true)
                            onRouteSheetClick()
                        }) {
                            Text("Зберегти в журнал", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = {
                            val list = pendingRouteClients!!
                            pendingRouteClients = null
                            viewModel.createRouteSheet(list, savePreviousToJournal = false)
                            onRouteSheetClick()
                        }) {
                            Text("Замінити без збереження", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRouteClients = null }) { Text("Скасувати") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { pendingRouteClients = null },
                title = { Text("Скласти маршрутник?") },
                text = { Text("Скласти новий маршрутник з завантажених клієнтів (${pendingRouteClients!!.size})?") },
                confirmButton = {
                    TextButton(onClick = {
                        val list = pendingRouteClients!!
                        pendingRouteClients = null
                        viewModel.createRouteSheet(list, savePreviousToJournal = true)
                        onRouteSheetClick()
                    }) {
                        Text("Створити новий маршрутник", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRouteClients = null }) { Text("Скасувати") }
                }
            )
        }
    }

    if (importResult != null) {
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Результат імпорту") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (!importResult!!.success) {
                        Text(importResult!!.message, color = MaterialTheme.colorScheme.error)
                    } else if (importResult!!.added.isEmpty() && importResult!!.updated.isEmpty() && importResult!!.logs.isEmpty()) {
                        Text("Усі дані актуальні", fontWeight = FontWeight.Bold)
                    } else {
                        if (importResult!!.added.isNotEmpty()) {
                            Text("Додано нових (${importResult!!.added.size}):", fontWeight = FontWeight.Bold)
                            importResult!!.added.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (importResult!!.logs.isNotEmpty()) {
                            Text("Журнал змін:", fontWeight = FontWeight.Bold)
                            importResult!!.logs.forEach { log ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("${log.clientLabel} (${log.clientNameSnapshot})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text("${translateFieldName(log.fieldName)}:", style = MaterialTheme.typography.labelSmall)
                                        Text("Старе: ${log.oldValue}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        Text("Нове: ${log.newValue}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                                    }
                                }
                            }
                        }
                        if (importResult!!.routeClients.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Останні 3 позиції маршрутника:", fontWeight = FontWeight.Bold)
                            val last3 = importResult!!.routeClients.takeLast(3)
                            last3.forEachIndexed { idx, item ->
                                val pos = importResult!!.routeClients.size - last3.size + idx + 1
                                val wText = if (item.weightKg > 0) "${if (item.weightKg % 1.0 == 0.0) item.weightKg.toInt().toString() else String.format(Locale.US, "%.1f", item.weightKg)} кг" else "0 кг"
                                val sText = if (item.amountSum > 0) "${if (item.amountSum % 1.0 == 0.0) item.amountSum.toInt().toString() else String.format(Locale.US, "%.2f", item.amountSum)} грн" else "0 грн"
                                val ordText = if (item.orderNumber.isNotBlank()) "№${item.orderNumber}" else "№—"

                                Text(
                                    text = "• Рядок #$pos: ${item.shopName} | $ordText | $wText | $sText",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val res = importResult
                    importResult = null
                    if (res?.success == true && res.routeClients.isNotEmpty()) {
                        pendingRouteClients = res.routeClients
                    }
                }) { Text("Прийняти та скласти маршрут", fontWeight = FontWeight.Bold) }
            }
        )
    }

    val activeRouteSheet by viewModel.activeRouteSheet.collectAsState()
    var selectedClientIdForTasks by remember { mutableStateOf<Long?>(null) }
    var showOnlyActiveTasksClients by remember { mutableStateOf(false) }

    val totalClientsWithActiveTasks = remember(clients) {
        clients.count { client -> client.tasks.any { !it.isCompleted } }
    }
    val hasAnyActiveTasks = totalClientsWithActiveTasks > 0

    val displayedClients = remember(clients, showOnlyActiveTasksClients) {
        if (showOnlyActiveTasksClients) {
            clients.filter { client -> client.tasks.any { !it.isCompleted } }
        } else {
            clients
        }
    }

    if (selectedClientIdForTasks != null) {
        val clientObj = clients.find { it.client.id == selectedClientIdForTasks }
        val clientName = clientObj?.client?.shopName ?: ""
        ClientTasksDialog(
            clientId = selectedClientIdForTasks!!,
            clientName = clientName,
            onDismiss = { selectedClientIdForTasks = null },
            viewModel = viewModel
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val titleText = if (showOnlyActiveTasksClients) {
                        "Завдання: ${displayedClients.size}"
                    } else {
                        "Клієнти (${displayedClients.size})"
                    }
                    Text(titleText, fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = { showOnlyActiveTasksClients = !showOnlyActiveTasksClients }) {
                        Box(
                            modifier = if (showOnlyActiveTasksClients) {
                                Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
                                    .padding(4.dp)
                            } else Modifier
                        ) {
                            Icon(
                                imageVector = Icons.Default.AssignmentLate,
                                contentDescription = "Фільтр за невиконаними завданнями",
                                tint = if (hasAnyActiveTasks) Color(0xFFF44336) else Color(0xFF4CAF50)
                            )
                        }
                    }
                    IconButton(onClick = onRouteSheetClick) { Icon(Icons.AutoMirrored.Filled.AltRoute, "Маршрутник") }
                    IconButton(onClick = onRouteJournalClick) { Icon(Icons.Default.History, "Журнал") }
                    IconButton(onClick = { routePickerLauncher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) { Icon(Icons.Default.Map, "Завантажити маршрут") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Налаштування") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer, titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()) {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayedClients) { client ->
                    ClientCard(
                        clientWithDetails = client,
                        activeRouteSheet = activeRouteSheet,
                        onClick = { onClientClick(client.client.id) },
                        onCallClick = { selectedClientForPhones = client.client.id },
                        onTasksClick = { selectedClientIdForTasks = client.client.id },
                        labelCounts = labelCounts,
                        searchQuery = searchQuery
                    )
                }
            }

            SearchBar(
                query = searchQuery, onQueryChange = { viewModel.updateSearchQuery(it) }, onSearch = {}, active = false, onActiveChange = {},
                placeholder = { Text("Пошук...") },
                leadingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onAddClientClick) { Icon(Icons.Default.Add, "Додати", tint = MaterialTheme.colorScheme.primary) }
                        Icon(Icons.Default.Search, null)
                    }
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) { IconButton(onClick = { viewModel.updateSearchQuery("") }) { Icon(Icons.Default.Close, null) } }
                        IconButton(onClick = startVoiceSearch) { Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focusRequester)
            ) {}
        }
    }
}

fun cleanTextForCopy(text: String): String {
    if (text.isBlank()) return ""
    return text
        .replace(Regex("""(?i)\b(ФОП|маг\.|м-н\.|маг|м-н)\b\.?"""), "")
        .replace(Regex("""(?i)^[,\.\s:\-—"“'«»]+|[,\.\s:\-—"“'«»]+$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim('"', '“', '”', '«', '»', ' ')
}

fun formatShortName(fullName: String): String {
    val cleaned = cleanTextForCopy(fullName)
    val parts = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }
    if (parts.isEmpty()) return ""
    if (parts.size == 1) return parts[0]
    val lastName = parts[0]
    val initials = parts.drop(1).mapNotNull { part ->
        part.firstOrNull { it.isLetter() }?.uppercaseChar()?.let { "$it." }
    }.joinToString(" ")
    return if (initials.isNotBlank()) "$lastName $initials" else lastName
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClientCard(
    clientWithDetails: ClientWithDetails,
    activeRouteSheet: RouteSheetWithItems? = null,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onTasksClick: () -> Unit = {},
    labelCounts: Map<String?, Int>,
    searchQuery: String
) {
    val context = LocalContext.current
    val client = clientWithDetails.client
    val highlightColor = Color(0xFFFF9800).copy(alpha = 0.4f)
    val words = remember(searchQuery) { searchQuery.split(" ").filter { it.isNotBlank() } }

    @Composable
    fun HighlightedText(text: String, style: TextStyle, modifier: Modifier = Modifier, fontWeight: FontWeight? = null, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip) {
        val annotatedString = buildAnnotatedString {
            if (words.isEmpty()) append(text) else {
                val lower = text.lowercase(); val matches = mutableListOf<IntRange>()
                words.forEach { w ->
                    var s = lower.indexOf(w.lowercase())
                    while (s != -1) { matches.add(s until (s + w.length)); s = lower.indexOf(w.lowercase(), s + 1) }
                }
                val sorted = matches.sortedBy { it.first }; val merged = mutableListOf<IntRange>()
                if (sorted.isNotEmpty()) {
                    var cur = sorted[0]
                    for (i in 1 until sorted.size) { if (sorted[i].first <= cur.last + 1) cur = cur.first..maxOf(cur.last, sorted[i].last) else { merged.add(cur); cur = sorted[i] } }
                    merged.add(cur)
                }
                var last = 0
                merged.forEach { r -> append(text.substring(last, r.first)); withStyle(SpanStyle(background = highlightColor)) { append(text.substring(r.first, r.last + 1)) }; last = r.last + 1 }
                append(text.substring(last))
            }
        }
        Text(text = annotatedString, style = style, modifier = modifier, fontWeight = fontWeight, color = color, maxLines = maxLines, overflow = overflow)
    }
    
    val isPhoneFilled = clientWithDetails.phones.any { it.phoneNumber.isNotBlank() }
    val isAddressFilled = !client.addressManual.isNullOrBlank()
    val isLabelFilled = !client.label.isNullOrBlank()
    val isShopNameFilled = client.shopName.isNotBlank()
    val isNamesFilled = clientWithDetails.names.any { it.fullName.isNotBlank() }
    val hasNotes = clientWithDetails.notes.any { it.content.isNotBlank() }
    val isAllFilled = isPhoneFilled && isAddressFilled && isLabelFilled && isNamesFilled && isShopNameFilled
    val isHighlighted = client.label != null && client.label != "D" && (labelCounts[client.label] ?: 0) > 1
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = when { showMenu -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f); isHighlighted -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f); else -> MaterialTheme.colorScheme.surface })
    ) {
        Box {
            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(50.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 4.dp) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Storefront, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    clientWithDetails.names.filter { it.fullName.isNotBlank() }.forEach { name -> HighlightedText(text = name.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HighlightedText(text = client.shopName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (hasNotes) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.Comment, "Примітка", tint = Color(0xFF2196F3), modifier = Modifier.size(14.dp))
                        }
                    }
                    if (isAddressFilled) Text(text = client.addressManual!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                    if (!client.city.isNullOrBlank()) HighlightedText(text = client.city!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp, end = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (isLabelFilled) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) { HighlightedText(text = client.label!!, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                        IconButton(onClick = {
                            val manualCoords = client.coordinatesManual?.trim()
                            val extractedUrl = extractUrlFromText(manualCoords)
                            if (extractedUrl != null) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(extractedUrl)).apply { setPackage("com.google.android.apps.maps") }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(extractedUrl)))
                                }
                            } else {
                                val parsedCoords = parseCoordinates(manualCoords)
                                val effectiveLat = parsedCoords?.first ?: client.latitude
                                val effectiveLng = parsedCoords?.second ?: client.longitude

                                val pointQuery = when {
                                    effectiveLat != null && effectiveLng != null -> "$effectiveLat,$effectiveLng"
                                    !manualCoords.isNullOrBlank() -> manualCoords
                                    else -> null
                                }

                                if (!pointQuery.isNullOrBlank()) {
                                    val cleanShop = cleanTextForCopy(client.shopName)
                                    val cleanFirstName = clientWithDetails.names.firstOrNull()?.fullName?.let { cleanTextForCopy(it) } ?: ""
                                    val pinLabel = listOfNotNull(cleanShop.ifBlank { null }, cleanFirstName.ifBlank { null }).joinToString(", ") + if (!client.label.isNullOrBlank()) " (${client.label})" else ""

                                    val gmmUri = Uri.parse("geo:0,0?q=${Uri.encode(pointQuery)}(${Uri.encode(pinLabel)})&z=20")
                                    val intent = Intent(Intent.ACTION_VIEW, gmmUri).apply { setPackage("com.google.android.apps.maps") }
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(pointQuery)}")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                    }
                                } else {
                                    Toast.makeText(context, "У клієнта не вказано координати!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            val pinIconTint = if (isCoordinatesFieldFilled(client.coordinatesManual)) Color(0xFF4CAF50) else Color(0xFFF44336)
                            Icon(Icons.Default.Place, "Показати на карті", tint = pinIconTint, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onCallClick) { Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val taskTint = getTaskIconTint(clientWithDetails.tasks)
                    IconButton(
                        onClick = onTasksClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Checklist, "Завдання", tint = taskTint, modifier = Modifier.size(20.dp))
                    }
                }
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clip(RoundedCornerShape(12.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                DropdownMenuItem(
                    text = { Text("№ та Σ накладної") },
                    leadingIcon = { Icon(Icons.Default.Receipt, null) },
                    onClick = {
                        showMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val activeItems = activeRouteSheet?.items?.filter { it.clientId == client.id } ?: emptyList()
                        val textToCopy = if (activeItems.isNotEmpty()) {
                            activeItems.joinToString("; ") { sel ->
                                val ord = formatOrderNumber(sel.orderNumber)
                                val sumStr = formatNumberWithSpaces(sel.amountSum, true)
                                if (ord.isNotBlank()) "$ord ($sumStr)" else "($sumStr)"
                            }
                        } else {
                            val labelText = client.label?.trim() ?: ""
                            if (labelText.isNotBlank()) "[$labelText]" else "Немає накладних"
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("№ та Σ накладної", textToCopy))
                    }
                )
                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("Перероб. на готівкову") },
                    leadingIcon = { Icon(Icons.Default.Autorenew, null) },
                    onClick = {
                        showMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val namesClean = clientWithDetails.names.map { cleanTextForCopy(it.fullName) }.filter { it.isNotBlank() }.joinToString(" ").replace("\"\"", "\"")
                        val textToCopy = "$namesClean потрібно переробити готівкову накладну на перерахункову"
                        clipboard.setPrimaryClip(ClipData.newPlainText("Перероб. на готівкову", textToCopy))
                    }
                )

                DropdownMenuItem(
                    text = { Text("Оплатили перерах.") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                    onClick = {
                        showMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val namesClean = clientWithDetails.names.map { cleanTextForCopy(it.fullName) }.filter { it.isNotBlank() }.joinToString(" ").replace("\"\"", "\"")
                        val textToCopy = "$namesClean оплатили перерахункову накладну"
                        clipboard.setPrimaryClip(ClipData.newPlainText("Оплатили перерах", textToCopy))
                    }
                )
                HorizontalDivider()

                DropdownMenuItem(text = { Text("Поділитись") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                    showMenu = false
                    val shopClean = cleanTextForCopy(client.shopName)
                    val namesClean = clientWithDetails.names.map { cleanTextForCopy(it.fullName) }.filter { it.isNotBlank() }.joinToString(", ")
                    val shareText = "Магазин: $shopClean\nКлієнт: $namesClean\nАдреса: ${client.addressManual ?: ""}".replace("\"\"", "\"")
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, shareText); type = "text/plain" }, null))
                })
                DropdownMenuItem(text = { Text("ПІБ") }, leadingIcon = { Icon(Icons.Default.Person, null) }, onClick = {
                    showMenu = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val namesText = clientWithDetails.names.map { cleanTextForCopy(it.fullName) }.filter { it.isNotBlank() }.joinToString(" ").replace("\"\"", "\"")
                    clipboard.setPrimaryClip(ClipData.newPlainText("ПІБ", namesText))
                })
                DropdownMenuItem(text = { Text("Повні дані") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Assignment, null) }, onClick = {
                    showMenu = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val shopClean = cleanTextForCopy(client.shopName)
                    val shopText = if (shopClean.isNotBlank()) "\"$shopClean\"" else null
                    val namesText = clientWithDetails.names.map { cleanTextForCopy(it.fullName) }.filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
                    val labelText = client.label?.trim()?.ifBlank { null }
                    val fullData = listOfNotNull(shopText, namesText, labelText).joinToString(" ").replace("\"\"", "\"")
                    clipboard.setPrimaryClip(ClipData.newPlainText("Повні дані", fullData))
                })
                DropdownMenuItem(text = { Text("Коротко") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ShortText, null) }, onClick = {
                    showMenu = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val shopClean = cleanTextForCopy(client.shopName)
                    val shopText = if (shopClean.isNotBlank()) "\"$shopClean\"" else null
                    val shortNamesText = clientWithDetails.names.map { formatShortName(it.fullName) }.filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
                    val labelText = client.label?.trim()?.ifBlank { null }
                    val shortData = listOfNotNull(shopText, shortNamesText, labelText).joinToString(" ").replace("\"\"", "\"")
                    clipboard.setPrimaryClip(ClipData.newPlainText("Коротко", shortData))
                })
            }
        }
    }
}

@Composable
fun PhoneNumbersDialog(clientId: Long, onDismiss: () -> Unit, viewModel: ClientViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var phonesWithStats by remember { mutableStateOf<List<PhoneWithStats>>(emptyList()) }
    LaunchedEffect(clientId) { phonesWithStats = viewModel.getPhonesWithStats(clientId) }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Виберіть номер для дзвінка", style = MaterialTheme.typography.titleLarge)
                phonesWithStats.forEach { stat ->
                    Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${stat.phone.phoneNumber}"))) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stat.phone.phoneNumber, modifier = Modifier.weight(1f))
                            Row {
                                Icon(Icons.AutoMirrored.Filled.CallReceived, null, modifier = Modifier.size(16.dp)); Text(stat.incomingCount.toString(), style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.CallMade, null, modifier = Modifier.size(16.dp)); Text(stat.outgoingCount.toString(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Закрити") }
            }
        }
    }
}
@Composable
fun ZoomableImageViewer(
    uri: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            if (newScale > 1f) {
                                val maxX = (size.width * (newScale - 1)) / 2f
                                val maxY = (size.height * (newScale - 1)) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                            scale = newScale
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Зображення",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "Масштаб: ${(scale * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (scale > 1f) {
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.RestartAlt, "Скинути масштаб", tint = Color.White)
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Закрити", tint = Color.White)
                    }
                }
            }
        }
    }
}

enum class MediaTarget { CREATE, EDIT }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClientTasksDialog(
    clientId: Long,
    clientName: String,
    onDismiss: () -> Unit,
    viewModel: ClientViewModel
) {
    val context = LocalContext.current
    val tasksFlow = remember(clientId) { viewModel.getTasksForClient(clientId) }
    val tasks by tasksFlow.collectAsState(initial = emptyList())

    // Creation section state
    var showCreateSection by remember { mutableStateOf(false) }
    var selectedPresetTitle by remember { mutableStateOf("Залишили накладні на пропечатку") }
    var customTitleInput by remember { mutableStateOf("") }
    var customText by remember { mutableStateOf("") }
    var photoUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var audioUris by remember { mutableStateOf<List<String>>(emptyList()) }

    // Editing section state
    var taskToEdit by remember { mutableStateOf<TaskEntity?>(null) }
    var editPresetTitle by remember { mutableStateOf("Залишили накладні на пропечатку") }
    var editCustomTitleInput by remember { mutableStateOf("") }
    var editCustomText by remember { mutableStateOf("") }
    var editPhotoUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var editAudioUris by remember { mutableStateOf<List<String>>(emptyList()) }

    // Media targets & dialogs
    var targetMediaTarget by remember { mutableStateOf(MediaTarget.CREATE) }
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    var fullImageUriToPreview by remember { mutableStateOf<String?>(null) }

    // Audio recording & playback state
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingTarget by remember { mutableStateOf(MediaTarget.CREATE) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingAudioUri by remember { mutableStateOf<String?>(null) }

    val presetTitles = listOf(
        "Залишили накладні на пропечатку",
        "Довезти товар",
        "Пропечатати накладні",
        "Інше"
    )

    // Launchers
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            val uriStr = pendingCameraUri.toString()
            if (targetMediaTarget == MediaTarget.EDIT) {
                editPhotoUris = (editPhotoUris + uriStr).distinct()
            } else {
                photoUris = (photoUris + uriStr).distinct()
            }
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && pendingCameraUri != null) {
            val uriStr = pendingCameraUri.toString()
            if (targetMediaTarget == MediaTarget.EDIT) {
                editPhotoUris = (editPhotoUris + uriStr).distinct()
            } else {
                photoUris = (photoUris + uriStr).distinct()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showPhotoOptionsDialog = true
        } else {
            Toast.makeText(context, "Необхідно надати дозвіл на використання камери", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val uriStrings = uris.map { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                uri.toString()
            }
            if (targetMediaTarget == MediaTarget.EDIT) {
                editPhotoUris = (editPhotoUris + uriStrings).distinct()
            } else {
                photoUris = (photoUris + uriStrings).distinct()
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val audioFile = File(context.cacheDir, "task_audio_${System.currentTimeMillis()}.mp3")
                val recorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(audioFile)
                    prepare()
                    start()
                }
                mediaRecorder = recorder
                isRecordingAudio = true
                val newAudioUri = Uri.fromFile(audioFile).toString()
                if (recordingTarget == MediaTarget.EDIT) {
                    editAudioUris = editAudioUris + newAudioUri
                } else {
                    audioUris = audioUris + newAudioUri
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Не вдалося розпочати запис", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Немає дозволу на мікрофон", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraPhoto(target: MediaTarget) {
        try {
            targetMediaTarget = target
            val photoFile = File(context.cacheDir, "task_photo_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Не вдалося відкрити камеру: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraVideo(target: MediaTarget) {
        try {
            targetMediaTarget = target
            val videoFile = File(context.cacheDir, "task_video_${System.currentTimeMillis()}.mp4")
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
            pendingCameraUri = uri
            captureVideoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Не вдалося відкрити відеокамеру: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun startOrStopRecording(target: MediaTarget) {
        recordingTarget = target
        if (isRecordingAudio) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (_: Exception) {}
            mediaRecorder = null
            isRecordingAudio = false
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (_: Exception) {}
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    if (showPhotoOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            title = { Text("Оберіть джерело медіа") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            launchCameraPhoto(targetMediaTarget)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Зробити фото (Камера)")
                    }
                    OutlinedButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            launchCameraVideo(targetMediaTarget)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Videocam, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Записати відео (Камера)")
                    }
                    OutlinedButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            photoPickerLauncher.launch(arrayOf("image/*", "video/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Обрати з галереї / файлів")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoOptionsDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Список завдань",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (clientName.isNotBlank()) {
                            Text(
                                text = clientName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрити")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!showCreateSection) {
                    Button(
                        onClick = { showCreateSection = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Створити нове завдання")
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Виберіть тип завдання:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            presetTitles.forEach { preset ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPresetTitle = preset }
                                        .padding(vertical = 2.dp)
                                ) {
                                    RadioButton(
                                        selected = (selectedPresetTitle == preset),
                                        onClick = { selectedPresetTitle = preset }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = preset,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selectedPresetTitle == preset) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            if (selectedPresetTitle == "Інше") {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = customTitleInput,
                                    onValueChange = { customTitleInput = it },
                                    label = { Text("Вкажіть назву завдання") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                label = { Text("Коментар / Додатковий текст") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Photo Section (Multiple)
                            Text("Фото / Відео:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            if (photoUris.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    items(photoUris, key = { it }) { uri ->
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                        ) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = "Медіа",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable { fullImageUriToPreview = uri },
                                                contentScale = ContentScale.Crop
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .align(Alignment.TopEnd)
                                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                                    .clickable { photoUris = photoUris.filter { it != uri } },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Close, "Видалити", tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    targetMediaTarget = MediaTarget.CREATE
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (photoUris.isEmpty()) "Додати фото або відео" else "Додати ще фото/відео (${photoUris.size})", style = MaterialTheme.typography.labelMedium)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Audio Section (Multiple)
                            Text("Записи диктофону (Аудіо):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            if (audioUris.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                    audioUris.forEachIndexed { index, uri ->
                                        val isPlaying = playingAudioUri == uri
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (isPlaying) {
                                                        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
                                                        mediaPlayer = null
                                                        playingAudioUri = null
                                                    } else {
                                                        try {
                                                            mediaPlayer?.stop(); mediaPlayer?.release()
                                                            mediaPlayer = MediaPlayer().apply {
                                                                setDataSource(context, Uri.parse(uri))
                                                                prepare()
                                                                setOnCompletionListener { playingAudioUri = null }
                                                                start()
                                                            }
                                                            playingAudioUri = uri
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Не вдалося відтворити запис", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (isPlaying) "Стоп" else "Аудіозапис ${index + 1} ▶", style = MaterialTheme.typography.labelSmall)
                                            }

                                            IconButton(onClick = { audioUris = audioUris.filter { it != uri } }) {
                                                Icon(Icons.Default.Delete, "Видалити", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { startOrStopRecording(MediaTarget.CREATE) },
                                colors = if (isRecordingAudio && recordingTarget == MediaTarget.CREATE) ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFFEBEE)) else ButtonDefaults.outlinedButtonColors(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (isRecordingAudio && recordingTarget == MediaTarget.CREATE) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (isRecordingAudio && recordingTarget == MediaTarget.CREATE) Color.Red else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isRecordingAudio && recordingTarget == MediaTarget.CREATE) "Запис... [Стоп]" else if (audioUris.isEmpty()) "Записати аудіо" else "Записати ще аудіо (${audioUris.size})",
                                    color = if (isRecordingAudio && recordingTarget == MediaTarget.CREATE) Color.Red else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    showCreateSection = false
                                    customTitleInput = ""
                                    customText = ""
                                    photoUris = emptyList()
                                    audioUris = emptyList()
                                }) {
                                    Text("Скасувати")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    val finalTitle = if (selectedPresetTitle == "Інше") customTitleInput.ifBlank { "Інше" } else selectedPresetTitle
                                    viewModel.addTask(
                                        clientId = clientId,
                                        title = finalTitle,
                                        commentText = customText.trim(),
                                        photoUri = encodeUrisList(photoUris),
                                        audioUri = encodeUrisList(audioUris)
                                    )
                                    showCreateSection = false
                                    customTitleInput = ""
                                    customText = ""
                                    photoUris = emptyList()
                                    audioUris = emptyList()
                                }) {
                                    Text("Створити")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Немає створених завдань",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            val creationDateStr = remember(task.createdTimestamp) {
                                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(task.createdTimestamp))
                            }
                            val taskPhotoUris = remember(task.photoUri) { task.getPhotoUris() }
                            val taskAudioUris = remember(task.audioUri) { task.getAudioUris() }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            taskToEdit = task
                                            editPresetTitle = if (presetTitles.contains(task.title)) task.title else "Інше"
                                            editCustomTitleInput = if (!presetTitles.contains(task.title)) task.title else ""
                                            editCustomText = task.commentText
                                            editPhotoUris = task.getPhotoUris()
                                            editAudioUris = task.getAudioUris()
                                        }
                                    ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (task.isCompleted) Color(0xFF4CAF50) else Color(0xFFF44336)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Checkbox(
                                                checked = task.isCompleted,
                                                onCheckedChange = { viewModel.toggleTaskCompleted(task) }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = task.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (task.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Створено: $creationDateStr",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }

                                        Row {
                                            IconButton(onClick = {
                                                taskToEdit = task
                                                editPresetTitle = if (presetTitles.contains(task.title)) task.title else "Інше"
                                                editCustomTitleInput = if (!presetTitles.contains(task.title)) task.title else ""
                                                editCustomText = task.commentText
                                                editPhotoUris = task.getPhotoUris()
                                                editAudioUris = task.getAudioUris()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Редагувати",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            IconButton(onClick = { viewModel.deleteTask(task) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Видалити",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    if (task.commentText.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = task.commentText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (taskPhotoUris.isNotEmpty()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 6.dp)
                                        ) {
                                            items(taskPhotoUris) { uri ->
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = "Фото завдання",
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { fullImageUriToPreview = uri },
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }

                                    if (taskAudioUris.isNotEmpty()) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(top = 6.dp)
                                        ) {
                                            taskAudioUris.forEachIndexed { index, uri ->
                                                val isThisAudioPlaying = playingAudioUri == uri
                                                OutlinedButton(
                                                    onClick = {
                                                        if (isThisAudioPlaying) {
                                                            try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
                                                            mediaPlayer = null
                                                            playingAudioUri = null
                                                        } else {
                                                            try {
                                                                mediaPlayer?.stop(); mediaPlayer?.release()
                                                                mediaPlayer = MediaPlayer().apply {
                                                                    setDataSource(context, Uri.parse(uri))
                                                                    prepare()
                                                                    setOnCompletionListener { playingAudioUri = null }
                                                                    start()
                                                                }
                                                                playingAudioUri = uri
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Не вдалося відтворити запис", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (isThisAudioPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isThisAudioPlaying) "Стоп" else if (taskAudioUris.size > 1) "Аудіозапис ${index + 1} ▶" else "Аудіозапис ▶",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Editing Dialog Modal
    if (taskToEdit != null) {
        Dialog(
            onDismissRequest = { taskToEdit = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Редагувати завдання",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { taskToEdit = null }) {
                            Icon(Icons.Default.Close, "Закрити")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Виберіть тип завдання:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    presetTitles.forEach { preset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editPresetTitle = preset }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = (editPresetTitle == preset),
                                onClick = { editPresetTitle = preset }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (editPresetTitle == preset) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    if (editPresetTitle == "Інше") {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editCustomTitleInput,
                            onValueChange = { editCustomTitleInput = it },
                            label = { Text("Вкажіть назву завдання") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editCustomText,
                        onValueChange = { editCustomText = it },
                        label = { Text("Коментар / Додатковий текст") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Photo section
                    Text("Фото / Відео:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (editPhotoUris.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            items(editPhotoUris, key = { it }) { uri ->
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Медіа",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { fullImageUriToPreview = uri },
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .align(Alignment.TopEnd)
                                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                            .clickable { editPhotoUris = editPhotoUris.filter { it != uri } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Close, "Видалити", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            targetMediaTarget = MediaTarget.EDIT
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (editPhotoUris.isEmpty()) "Додати фото або відео" else "Додати ще фото/відео (${editPhotoUris.size})", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Audio section
                    Text("Записи диктофону (Аудіо):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (editAudioUris.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            editAudioUris.forEachIndexed { index, uri ->
                                val isEditingAudioPlaying = playingAudioUri == uri
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (isEditingAudioPlaying) {
                                                try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
                                                mediaPlayer = null
                                                playingAudioUri = null
                                            } else {
                                                try {
                                                    mediaPlayer?.stop(); mediaPlayer?.release()
                                                    mediaPlayer = MediaPlayer().apply {
                                                        setDataSource(context, Uri.parse(uri))
                                                        prepare()
                                                        setOnCompletionListener { playingAudioUri = null }
                                                        start()
                                                    }
                                                    playingAudioUri = uri
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Не вдалося відтворити запис", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (isEditingAudioPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isEditingAudioPlaying) "Стоп" else "Аудіозапис ${index + 1} ▶", style = MaterialTheme.typography.labelSmall)
                                    }

                                    IconButton(onClick = { editAudioUris = editAudioUris.filter { it != uri } }) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            startOrStopRecording(MediaTarget.EDIT)
                        },
                        colors = if (isRecordingAudio && recordingTarget == MediaTarget.EDIT) ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFFEBEE)) else ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isRecordingAudio && recordingTarget == MediaTarget.EDIT) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isRecordingAudio && recordingTarget == MediaTarget.EDIT) Color.Red else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRecordingAudio && recordingTarget == MediaTarget.EDIT) "Запис... [Стоп]" else if (editAudioUris.isEmpty()) "Записати аудіо" else "Записати ще аудіо (${editAudioUris.size})",
                            color = if (isRecordingAudio && recordingTarget == MediaTarget.EDIT) Color.Red else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { taskToEdit = null }) {
                            Text("Скасувати")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val finalTitle = if (editPresetTitle == "Інше") editCustomTitleInput.ifBlank { "Інше" } else editPresetTitle
                            viewModel.updateTask(
                                taskToEdit!!.copy(
                                    title = finalTitle,
                                    commentText = editCustomText.trim(),
                                    photoUri = encodeUrisList(editPhotoUris),
                                    audioUri = encodeUrisList(editAudioUris)
                                )
                            )
                            taskToEdit = null
                        }) {
                            Text("Зберегти")
                        }
                    }
                }
            }
        }
    }

    if (fullImageUriToPreview != null) {
        ZoomableImageViewer(
            uri = fullImageUriToPreview!!,
            onDismiss = { fullImageUriToPreview = null }
        )
    }
}

@Composable
fun CallHistoryDialog(phoneNumber: String, onDismiss: () -> Unit, viewModel: ClientViewModel) { /* ... Similar to before ... */ }

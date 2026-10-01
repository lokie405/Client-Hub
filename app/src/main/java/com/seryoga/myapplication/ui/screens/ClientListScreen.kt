package com.seryoga.myapplication.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Assignment
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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
                        Text("Так", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRouteClients = null }) { Text("Ні") }
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
                }) { Text("ОК") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Клієнти (${clients.size})", fontWeight = FontWeight.Bold) },
                actions = {
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
                items(clients) { client ->
                    ClientCard(
                        clientWithDetails = client,
                        onClick = { onClientClick(client.client.id) },
                        onCallClick = { selectedClientForPhones = client.client.id },
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
    onClick: () -> Unit,
    onCallClick: () -> Unit,
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (hasNotes) Icon(Icons.AutoMirrored.Filled.Comment, "Примітка", modifier = Modifier.size(14.dp), tint = Color(0xFF2196F3))
                        if (isAllFilled) Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = Color(0xFF4CAF50))
                        else {
                            if (!isPhoneFilled) Icon(Icons.Default.Phone, null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFB300))
                            if (!isAddressFilled || client.addressStatus == "changed") Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = Color(0xFFF44336))
                            if (!isLabelFilled) Icon(Icons.AutoMirrored.Filled.Label, null, modifier = Modifier.size(14.dp), tint = Color(0xFF9C27B0))
                            if (!isShopNameFilled) Icon(Icons.Default.Storefront, null, modifier = Modifier.size(14.dp), tint = Color(0xFF03A9F4))
                        }
                    }
                    Surface(modifier = Modifier.size(50.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 4.dp) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Storefront, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    clientWithDetails.names.forEach { name -> HighlightedText(text = name.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLabelFilled) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) { HighlightedText(text = client.label!!, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                        IconButton(onClick = onCallClick) { Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
fun CallHistoryDialog(phoneNumber: String, onDismiss: () -> Unit, viewModel: ClientViewModel) { /* ... Similar to before ... */ }

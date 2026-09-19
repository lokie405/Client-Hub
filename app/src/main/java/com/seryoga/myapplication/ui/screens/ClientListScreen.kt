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
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.Label
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.seryoga.myapplication.data.CallLogEntity
import com.seryoga.myapplication.data.ClientWithDetails
import com.seryoga.myapplication.data.PhoneWithStats
import com.seryoga.myapplication.ui.ClientViewModel
import com.seryoga.myapplication.ui.ImportResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientListScreen(
    viewModel: ClientViewModel,
    onClientClick: (Long) -> Unit,
    onAddClientClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val clients by viewModel.clients.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedClientForPhones by remember { mutableStateOf<Long?>(null) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }

    val routePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importRouteSheet(it) { result ->
                if (result.success) {
                    importResult = result
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar(result.message)
                    }
                }
            }
        }
    }

    val labelCounts = remember(clients) {
        clients.groupingBy { it.client.label }.eachCount()
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            spokenText?.let { viewModel.updateSearchQuery(it) }
        }
    }

    val startVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говоріть...")
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (_: Exception) {
            // Handle case where voice search is not available
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (selectedClientForPhones != null) {
        PhoneNumbersDialog(
            clientId = selectedClientForPhones!!,
            onDismiss = { selectedClientForPhones = null },
            viewModel = viewModel
        )
    }

    if (importResult != null) {
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Результат імпорту") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (importResult!!.added.isEmpty() && importResult!!.updated.isEmpty()) {
                        Text(
                            "Усі дані з цього маршрутного листа актуальні (вже завантажені та без змін)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (importResult!!.added.isNotEmpty()) {
                        Text("Додано нових (${importResult!!.added.size}):", fontWeight = FontWeight.Bold)
                        importResult!!.added.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        if (importResult!!.updated.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    if (importResult!!.updated.isNotEmpty()) {
                        Text("Оновлено (${importResult!!.updated.size}):", fontWeight = FontWeight.Bold)
                        importResult!!.updated.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importResult = null }) {
                    Text("ОК")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Мої Клієнти (${clients.size})", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { 
                        routePickerLauncher.launch(arrayOf(
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/octet-stream" // Some xls files might have this
                        ))
                    }) {
                        Icon(Icons.Default.Map, contentDescription = "Маршрут")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Налаштування")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(clients) { clientWithDetails ->
                    ClientCard(
                        clientWithDetails = clientWithDetails,
                        onClick = { onClientClick(clientWithDetails.client.id) },
                        onCallClick = { selectedClientForPhones = clientWithDetails.client.id },
                        labelCounts = labelCounts,
                        onClone = { viewModel.cloneClient(clientWithDetails) },
                        searchQuery = searchQuery
                    )
                }
            }

            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Пошук...") },
                leadingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onAddClientClick) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Додати клієнта",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Очистити пошук",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = startVoiceSearch) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Голосовий пошук",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester)
            ) {}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClientCard(
    clientWithDetails: ClientWithDetails,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    labelCounts: Map<String?, Int>,
    onClone: () -> Unit,
    searchQuery: String
) {
    val context = LocalContext.current
    val client = clientWithDetails.client
    val phones = clientWithDetails.phones

    val highlightColor = Color(0xFFFF9800).copy(alpha = 0.4f)
    val words = remember(searchQuery) { 
        searchQuery.split(" ").filter { it.isNotBlank() } 
    }

    @Composable
    fun HighlightedText(
        text: String,
        style: androidx.compose.ui.text.TextStyle,
        modifier: Modifier = Modifier,
        fontWeight: FontWeight? = null,
        color: Color = Color.Unspecified,
        maxLines: Int = Int.MAX_VALUE,
        overflow: TextOverflow = TextOverflow.Clip
    ) {
        val annotatedString = buildAnnotatedString {
            if (words.isEmpty()) {
                append(text)
            } else {
                val lowerText = text.lowercase()
                
                // Collect all matches
                val matches = mutableListOf<IntRange>()
                words.forEach { word ->
                    var start = lowerText.indexOf(word.lowercase())
                    while (start != -1) {
                        matches.add(start until (start + word.length))
                        start = lowerText.indexOf(word.lowercase(), start + 1)
                    }
                }
                
                // Sort and merge overlapping matches
                val sortedMatches = matches.sortedBy { it.first }
                val mergedMatches = mutableListOf<IntRange>()
                if (sortedMatches.isNotEmpty()) {
                    var currentMatch = sortedMatches[0]
                    for (i in 1 until sortedMatches.size) {
                        val nextMatch = sortedMatches[i]
                        if (nextMatch.first <= currentMatch.last + 1) { // Merge adjacent or overlapping
                            currentMatch = currentMatch.first..maxOf(currentMatch.last, nextMatch.last)
                        } else {
                            mergedMatches.add(currentMatch)
                            currentMatch = nextMatch
                        }
                    }
                    mergedMatches.add(currentMatch)
                }

                var lastPos = 0
                mergedMatches.forEach { range ->
                    append(text.substring(lastPos, range.first))
                    withStyle(style = SpanStyle(background = highlightColor)) {
                        append(text.substring(range.first, range.last + 1))
                    }
                    lastPos = range.last + 1
                }
                append(text.substring(lastPos))
            }
        }
        Text(
            text = annotatedString,
            style = style,
            modifier = modifier,
            fontWeight = fontWeight,
            color = color,
            maxLines = maxLines,
            overflow = overflow
        )
    }
    
    val isPhoneFilled = phones.any { it.phoneNumber.isNotBlank() }
    val isAddressFilled = !client.addressManual.isNullOrBlank()
    val isLabelFilled = !client.label.isNullOrBlank()
    val isShopNameFilled = client.shopName.isNotBlank()
    val isNamesFilled = client.fullName.isNotBlank()
    
    val isAllFilled = isPhoneFilled && isAddressFilled && isLabelFilled && isNamesFilled && isShopNameFilled

    val isHighlighted = client.label != null && client.label != "D" && (labelCounts[client.label] ?: 0) > 1
    
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                showMenu -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                isHighlighted -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Box {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Indicators Row
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (isAllFilled) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF4CAF50)
                            )
                        } else {
                            if (!isPhoneFilled) Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFFFB300)
                            )
                            if (!isAddressFilled) Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFF44336)
                            )
                            if (!isLabelFilled) Icon(
                                Icons.AutoMirrored.Filled.Label,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF9C27B0)
                            )
                            if (!isShopNameFilled) Icon(
                                Icons.Default.Storefront,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF03A9F4)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            val address = clientWithDetails.client.addressManual
                            if (!address.isNullOrBlank()) {
                                val gmmIntentUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(address)}")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                context.startActivity(mapIntent)
                            }
                        },
                        modifier = Modifier.size(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 4.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Storefront,
                                contentDescription = "Магазин",
                                modifier = Modifier.size(28.dp),
                                tint = if (isShopNameFilled) MaterialTheme.colorScheme.primary else Color(0xFF03A9F4)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    HighlightedText(
                        text = clientWithDetails.client.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HighlightedText(
                        text = clientWithDetails.client.shopName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!clientWithDetails.client.addressManual.isNullOrBlank()) {
                        Text(
                            text = clientWithDetails.client.addressManual,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            minLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!client.city.isNullOrBlank()) {
                        HighlightedText(
                            text = client.city,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp, end = 8.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!clientWithDetails.client.label.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                HighlightedText(
                                    text = clientWithDetails.client.label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(onClick = onCallClick) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Дзвінок",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Поділитись") },
                    onClick = {
                        showMenu = false
                        val client = clientWithDetails.client
                        val shareText = buildString {
                            appendLine("Магазин: ${client.shopName}")
                            if (!client.city.isNullOrBlank()) {
                                appendLine("Населений пункт: ${client.city}")
                            }
                            appendLine("Клієнт: ${client.fullName}")
                            if (!client.label.isNullOrBlank()) {
                                appendLine("Мітка: ${client.label}")
                            }
                            val fullAddress = listOfNotNull(client.city, client.addressManual).joinToString(", ")
                            if (client.latitude != null && client.longitude != null) {
                                if (fullAddress.isNotBlank()) {
                                    appendLine("Адреса: $fullAddress")
                                }
                                appendLine("Карта: http://maps.google.com/maps?q=loc:${client.latitude},${client.longitude}&z=20")
                            } else if (fullAddress.isNotBlank()) {
                                appendLine("Адреса: $fullAddress")
                                appendLine("Карта: http://maps.google.com/maps?q=${Uri.encode(fullAddress)}&z=20")
                            }
                        }
                        
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Копіювати") },
                    onClick = {
                        showMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Client Name", client.fullName)
                        clipboard.setPrimaryClip(clip)
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Клонувати") },
                    onClick = {
                        showMenu = false
                        onClone()
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = "Клонувати") }
                )
            }
        }
    }
}

@Composable
fun PhoneNumbersDialog(
    clientId: Long,
    onDismiss: () -> Unit,
    viewModel: ClientViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phonesWithStats by remember { mutableStateOf<List<PhoneWithStats>>(emptyList()) }
    var selectedPhoneForHistory by remember { mutableStateOf<String?>(null) }
    
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                phonesWithStats = viewModel.getPhonesWithStats(clientId)
            }
        }
    }

    LaunchedEffect(clientId) {
        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        )
        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            phonesWithStats = viewModel.getPhonesWithStats(clientId)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    if (selectedPhoneForHistory != null) {
        CallHistoryDialog(
            phoneNumber = selectedPhoneForHistory!!,
            onDismiss = { selectedPhoneForHistory = null },
            viewModel = viewModel
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Виберіть номер для дзвінка", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                phonesWithStats.forEach { stat ->
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${stat.phone.phoneNumber}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stat.phone.phoneNumber, modifier = Modifier.weight(1f))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.CallReceived, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(stat.incomingCount.toString(), style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.CallMade, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(stat.outgoingCount.toString(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    
                    Text(
                        "Утримуйте для історії",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { selectedPhoneForHistory = stat.phone.phoneNumber }
                        ).padding(bottom = 8.dp)
                    )
                }
                
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Закрити")
                }
            }
        }
    }
}

@Composable
fun CallHistoryDialog(
    phoneNumber: String,
    onDismiss: () -> Unit,
    viewModel: ClientViewModel
) {
    val history by viewModel.getCallHistory(phoneNumber).collectAsState()
    val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Історія дзвінків: $phoneNumber", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(history) { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (log.type == 1) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                                contentDescription = null,
                                tint = if (log.type == 1) Color(0xFF4CAF50) else Color(0xFF2196F3),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(dateFormat.format(Date(log.timestamp)), style = MaterialTheme.typography.bodyMedium)
                                Text("${log.duration} сек.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
                
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Закрити")
                }
            }
        }
    }
}

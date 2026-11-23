package com.seryoga.myapplication.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Comment
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.seryoga.myapplication.data.ClientWithDetails
import com.seryoga.myapplication.ui.parseCoordinates
import com.seryoga.myapplication.data.RouteSheetItemEntity
import com.seryoga.myapplication.ui.ClientGeoDetails
import com.seryoga.myapplication.ui.ClientViewModel
import com.seryoga.myapplication.ui.extractUrlFromText
import com.seryoga.myapplication.ui.getTaskIconTint
import com.seryoga.myapplication.ui.isCoordinatesFieldFilled
import com.seryoga.myapplication.ui.isHttpsCoordinates
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

fun formatNumberWithSpaces(number: Double, isCurrency: Boolean = false): String {
    if (number <= 0) return ""
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ' '
    }
    val pattern = if (number % 1.0 == 0.0) "#,##0" else "#,##0.00"
    val formatter = DecimalFormat(pattern, symbols)
    val formatted = formatter.format(number)
    return if (isCurrency) "$formatted грн" else "$formatted кг"
}

fun formatOrderNumber(raw: String): String {
    if (raw.isBlank()) return ""
    val clean = raw.trim().replace(Regex("(?i)^ПС"), "").trim()
    return if (clean.isNotBlank()) "№$clean" else ""
}

fun getBadgeColorForCityAndLabel(city: String?, label: String): Color {
    val cleanCity = (city ?: "").lowercase().trim()
    if (cleanCity.isBlank() && label.isBlank()) return Color(0xFF616161)

    val cityHues = floatArrayOf(
        200f, // Blue
        280f, // Purple
        30f,  // Orange
        170f, // Teal
        340f, // Pink/Rose
        240f, // Indigo
        45f,  // Amber
        140f, // Green
        10f,  // Deep Orange
        220f  // Steel Blue
    )

    val cityHash = Math.abs(cleanCity.hashCode())
    val baseHue = cityHues[cityHash % cityHues.size]

    val labelHash = Math.abs(label.lowercase().trim().hashCode())
    val lightnessShades = floatArrayOf(0.35f, 0.45f, 0.55f, 0.65f)
    val lightness = lightnessShades[labelHash % lightnessShades.size]

    return Color.hsl(hue = baseHue, saturation = 0.65f, lightness = lightness)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSheetScreen(
    viewModel: ClientViewModel,
    onBack: () -> Unit,
    onOpenClientDetail: (clientId: Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeRouteSheet by viewModel.activeRouteSheet.collectAsState()
    val allClients by viewModel.allClients.collectAsState()
    var selectedItemForNote by remember { mutableStateOf<RouteSheetItemEntity?>(null) }
    var selectedItemIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var swapItemData by remember { mutableStateOf<Pair<RouteSheetItemEntity, Int>?>(null) }
    var selectedClientIdForCalls by remember { mutableStateOf<Long?>(null) }
    val isLocked by viewModel.isRouteSheetLocked.collectAsState()
    val showCityHeaders by viewModel.showCityHeadersInRouteSheet.collectAsState()

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val minSwipeThresholdPx = screenWidthPx * 0.25f

    var isManualOrderingMode by remember { mutableStateOf(false) }
    val manualAssignments = remember { mutableStateMapOf<Long, Int>() }

    val items = remember(activeRouteSheet) {
        activeRouteSheet?.items?.sortedBy { it.orderIndex } ?: emptyList()
    }

    var localItems by remember(items) { mutableStateOf(items) }
    LaunchedEffect(items) {
        localItems = items
    }

    var showOnlyActiveTasksRouteItems by remember { mutableStateOf(false) }

    val activeTaskClientsInRoute = remember(localItems, allClients) {
        localItems.mapNotNull { item ->
            val matched = allClients.find { it.client.id == item.clientId }
                ?: allClients.find { !it.client.label.isNullOrBlank() && it.client.label.trim().equals(item.clientLabelSnapshot.trim(), ignoreCase = true) }
                ?: allClients.find { it.client.shopName.trim().equals(item.clientShopSnapshot.trim(), ignoreCase = true) }
            matched
        }.filter { clientWithDetails ->
            clientWithDetails.tasks.any { !it.isCompleted }
        }.distinctBy { it.client.id }
    }
    val routeClientsWithActiveTasksCount = activeTaskClientsInRoute.size
    val hasRouteClientsWithActiveTasks = routeClientsWithActiveTasksCount > 0

    val hasMissingCoordinatesInRoute = remember(localItems, allClients) {
        localItems.any { item ->
            val matched = allClients.find { it.client.id == item.clientId }
                ?: allClients.find { !it.client.label.isNullOrBlank() && it.client.label.trim().equals(item.clientLabelSnapshot.trim(), ignoreCase = true) }
                ?: allClients.find { it.client.shopName.trim().equals(item.clientShopSnapshot.trim(), ignoreCase = true) }
            val coords = matched?.client?.coordinatesManual
            !isCoordinatesFieldFilled(coords)
        }
    }

    val displayedLocalItems = remember(localItems, allClients, showOnlyActiveTasksRouteItems) {
        if (showOnlyActiveTasksRouteItems) {
            localItems.filter { item ->
                val matched = allClients.find { it.client.id == item.clientId }
                    ?: allClients.find { !it.client.label.isNullOrBlank() && it.client.label.trim().equals(item.clientLabelSnapshot.trim(), ignoreCase = true) }
                    ?: allClients.find { it.client.shopName.trim().equals(item.clientShopSnapshot.trim(), ignoreCase = true) }
                matched?.tasks?.any { !it.isCompleted } == true
            }
        } else {
            localItems
        }
    }

    var isOptimizingRoute by remember { mutableStateOf(false) }
    var pendingOptimizedList by remember { mutableStateOf<Pair<List<RouteSheetItemEntity>, Int>?>(null) }
    var showNavigationStagesDialog by remember { mutableStateOf<List<RouteItemWithLocation>?>(null) }

    fun runRouteOptimization(byCityOnly: Boolean = false) {
        scope.launch {
            isOptimizingRoute = true
            val (baseLat, baseLng) = getBaseLocation(context)

            if (byCityOnly) {
                val cityMap = mutableMapOf<String, MutableList<RouteSheetItemEntity>>()
                localItems.forEach { item ->
                    val matched = allClients.find { it.client.id == item.clientId }
                        ?: allClients.find { !it.client.label.isNullOrBlank() && it.client.label.trim().equals(item.clientLabelSnapshot.trim(), ignoreCase = true) }
                        ?: allClients.find { it.client.shopName.trim().equals(item.clientShopSnapshot.trim(), ignoreCase = true) }
                    val city = (matched?.client?.city ?: item.clientCitySnapshot ?: "—").trim().ifBlank { "—" }
                    cityMap.getOrPut(city) { mutableListOf() }.add(item)
                }

                val cityLocations = mutableListOf<RouteItemWithLocation>()
                cityMap.keys.forEach { city ->
                    if (city != "—") {
                        val coords = resolveCityCenterLocation(context, city)
                        if (coords != null) {
                            val dummyItem = RouteSheetItemEntity(routeSheetId = 0, orderIndex = 0, clientShopSnapshot = city, clientCitySnapshot = city)
                            cityLocations.add(RouteItemWithLocation(dummyItem, coords.first, coords.second, true))
                        }
                    }
                }

                if (cityLocations.isEmpty()) {
                    isOptimizingRoute = false
                    Toast.makeText(context, "Не вдалося визначити географічне розміщення населених пунктів", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val optimizedCityLocations = optimizeRouteOrderTSPWithBase(baseLat, baseLng, cityLocations)
                val sortedCities = optimizedCityLocations.map { it.item.clientShopSnapshot }
                val remainingCities = cityMap.keys.filter { it !in sortedCities }

                val reorderedItems = mutableListOf<RouteSheetItemEntity>()
                (sortedCities + remainingCities).forEach { city ->
                    cityMap[city]?.let { itemsInCity -> reorderedItems.addAll(itemsInCity) }
                }

                isOptimizingRoute = false
                pendingOptimizedList = Pair(reorderedItems, 0)
            } else {
                val resolvedList = mutableListOf<RouteItemWithLocation>()
                val unresolvable = mutableListOf<RouteSheetItemEntity>()

                localItems.forEach { item ->
                    val matched = allClients.find { it.client.id == item.clientId }
                        ?: allClients.find { !it.client.label.isNullOrBlank() && it.client.label.trim().equals(item.clientLabelSnapshot.trim(), ignoreCase = true) }
                        ?: allClients.find { it.client.shopName.trim().equals(item.clientShopSnapshot.trim(), ignoreCase = true) }

                    val loc = resolveRouteItemLocation(context, item, matched)
                    if (loc != null) {
                        resolvedList.add(loc)
                    } else {
                        unresolvable.add(item)
                    }
                }

                isOptimizingRoute = false

                if (resolvedList.isEmpty()) {
                    Toast.makeText(context, "Не вдалося визначити координати жодного клієнта", Toast.LENGTH_SHORT).show()
                } else {
                    val optimizedLocations = optimizeRouteOrderTSPWithBase(baseLat, baseLng, resolvedList)
                    val reorderedItems = optimizedLocations.map { it.item } + unresolvable
                    val cityFallbackCount = resolvedList.count { it.isCityCenterFallback }
                    pendingOptimizedList = Pair(reorderedItems, cityFallbackCount)
                }
            }
        }
    }

    fun launchGoogleMapsNavigation() {
        scope.launch {
            isOptimizingRoute = true
            val resolvedList = mutableListOf<RouteItemWithLocation>()

            localItems.forEach { item ->
                val matched = allClients.find { it.client.id == item.clientId }
                    ?: allClients.find { !it.client.label.isNullOrBlank() && it.client.label.trim().equals(item.clientLabelSnapshot.trim(), ignoreCase = true) }
                    ?: allClients.find { it.client.shopName.trim().equals(item.clientShopSnapshot.trim(), ignoreCase = true) }

                val loc = resolveRouteItemLocation(context, item, matched)
                if (loc != null) {
                    resolvedList.add(loc)
                }
            }

            isOptimizingRoute = false

            if (resolvedList.isEmpty()) {
                Toast.makeText(context, "Не вдалося визначити координати клієнтів", Toast.LENGTH_SHORT).show()
            } else if (resolvedList.size <= 8) {
                openGoogleMapsRouteWithBase(context, ROUTE_BASE_ADDRESS, resolvedList, isFirstStage = true, isLastStage = true)
            } else {
                showNavigationStagesDialog = resolvedList
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localItems = localItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        viewModel.reorderRouteItems(localItems)
    }

    LaunchedEffect(isLocked, localItems) {
        if (isLocked && localItems.isNotEmpty()) {
            val firstUncompletedIndex = localItems.indexOfFirst { !it.isCompleted }
            if (firstUncompletedIndex >= 0) {
                lazyListState.animateScrollToItem(firstUncompletedIndex)
            }
        }
    }

    fun exitManualOrderingMode() {
        if (manualAssignments.isNotEmpty()) {
            val assignedItems = localItems
                .filter { it.id in manualAssignments }
                .sortedBy { manualAssignments[it.id] ?: Int.MAX_VALUE }
            val unassignedItems = localItems.filter { it.id !in manualAssignments }
            val reorderedList = assignedItems + unassignedItems
            localItems = reorderedList
            viewModel.reorderRouteItems(reorderedList)
        }
        manualAssignments.clear()
        isManualOrderingMode = false
    }

    fun assignNextNumber(item: RouteSheetItemEntity) {
        if (item.id in manualAssignments) return
        val usedNumbers = manualAssignments.values.toSet()
        val nextNum = (1..localItems.size).firstOrNull { it !in usedNumbers } ?: (manualAssignments.size + 1)
        manualAssignments[item.id] = nextNum

        if (manualAssignments.size >= localItems.size) {
            exitManualOrderingMode()
        }
    }

    fun unassignNumber(item: RouteSheetItemEntity) {
        manualAssignments.remove(item.id)
    }

    if (selectedItemForNote != null) {
        RouteItemNoteDialog(
            item = selectedItemForNote!!,
            onDismiss = { selectedItemForNote = null },
            onSave = { text, audio, photo, file ->
                viewModel.updateRouteItemNote(selectedItemForNote!!, text, audio, photo, file)
                selectedItemForNote = null
            }
        )
    }

    var selectedClientIdForTasks by remember { mutableStateOf<Long?>(null) }

    if (selectedClientIdForTasks != null) {
        val clientObj = allClients.find { it.client.id == selectedClientIdForTasks }
        val clientName = clientObj?.client?.shopName ?: ""
        ClientTasksDialog(
            clientId = selectedClientIdForTasks!!,
            clientName = clientName,
            onDismiss = { selectedClientIdForTasks = null },
            viewModel = viewModel
        )
    }

    if (selectedClientIdForCalls != null) {
        PhoneNumbersDialog(
            clientId = selectedClientIdForCalls!!,
            onDismiss = { selectedClientIdForCalls = null },
            viewModel = viewModel
        )
    }

    if (swapItemData != null) {
        SwapNumberDialog(
            item = swapItemData!!.first,
            currentIndex = swapItemData!!.second,
            totalCount = localItems.size,
            onDismiss = { swapItemData = null },
            onConfirmSwap = { targetIndex ->
                val currentIdx = swapItemData!!.second
                swapItemData = null
                if (targetIndex in localItems.indices && currentIdx in localItems.indices && targetIndex != currentIdx) {
                    val list = localItems.toMutableList()
                    val temp = list[currentIdx]
                    list[currentIdx] = list[targetIndex]
                    list[targetIndex] = temp
                    localItems = list
                    viewModel.reorderRouteItems(list)
                }
            }
        )
    }

    if (pendingOptimizedList != null) {
        val newList = pendingOptimizedList!!.first
        val cityFallbackCount = pendingOptimizedList!!.second
        val fallbackInfo = if (cityFallbackCount > 0) "\n\n(примітка: для $cityFallbackCount клієнтів без точних координат використано центр їхнього населеного пункту)" else ""

        AlertDialog(
            onDismissRequest = { pendingOptimizedList = null },
            title = { Text("Оптимізувати порядок маршруту?") },
            text = {
                Text("Обчислено найоптимальніший шлях для ${newList.size} клієнтів.$fallbackInfo\n\nБажаєте оновити порядкові номери №1, №2, №3... у маршрутному листі?")
            },
            confirmButton = {
                TextButton(onClick = {
                    val listToSave = pendingOptimizedList!!.first
                    pendingOptimizedList = null
                    localItems = listToSave
                    viewModel.reorderRouteItems(listToSave)
                    Toast.makeText(context, "Порядок у маршрутнику оновлено!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Так, оновити порядок", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOptimizedList = null }) {
                    Text("Скасувати")
                }
            }
        )
    }

    if (showNavigationStagesDialog != null) {
        val resolved = showNavigationStagesDialog!!
        val totalPoints = resolved.size
        val chunkSize = 8
        val stages = mutableListOf<List<RouteItemWithLocation>>()
        var startIdx = 0
        while (startIdx < totalPoints) {
            val endIdx = minOf(startIdx + chunkSize, totalPoints)
            stages.add(resolved.subList(startIdx, endIdx))
            startIdx = endIdx
        }

        AlertDialog(
            onDismissRequest = { showNavigationStagesDialog = null },
            title = { Text("Навігація у Google Картах ($totalPoints точок)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("База: $ROUTE_BASE_ADDRESS\nМаршрут розбито на етапи (до 10 зупинок у Картах):", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    stages.forEachIndexed { idx, stage ->
                        val isFirst = idx == 0
                        val isLast = idx == stages.size - 1
                        val startName = if (isFirst) "м. Рівне (База)" else stage.first().item.clientShopSnapshot.ifBlank { stage.first().item.clientNameSnapshot }
                        val endName = if (isLast) "м. Рівне (База)" else stage.last().item.clientShopSnapshot.ifBlank { stage.last().item.clientNameSnapshot }

                        OutlinedButton(
                            onClick = {
                                showNavigationStagesDialog = null
                                openGoogleMapsRouteWithBase(context, ROUTE_BASE_ADDRESS, stage, isFirstStage = isFirst, isLastStage = isLast)
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("Етап ${idx + 1}: $startName ➔ $endName (${stage.size} зупинок)")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNavigationStagesDialog = null }) {
                    Text("Закрити")
                }
            }
        )
    }

    if (isOptimizingRoute) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Обробка маршруту...") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Розпізнавання координат та обчислення шляху...")
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        topBar = {
            if (selectedItemIds.isNotEmpty()) {
                val selectedItemsList = localItems.filter { it.id in selectedItemIds }
                val selectedWeightKg = selectedItemsList.sumOf { it.weightKg }
                val selectedAmountSum = selectedItemsList.sumOf { it.amountSum }

                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Виділено",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "${selectedItemIds.size}",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            if (selectedWeightKg > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = formatNumberWithSpaces(selectedWeightKg, isCurrency = false),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (selectedAmountSum > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = formatNumberWithSpaces(selectedAmountSum, isCurrency = true),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedItemIds = emptySet() }) {
                            Icon(Icons.Default.Close, "Очистити виділення")
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedItemIds = localItems.map { it.id }.toSet() }) {
                            Icon(Icons.Default.SelectAll, "Виділити все")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            if (isManualOrderingMode) {
                                Text("Ручний порядок (${manualAssignments.size}/${localItems.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            } else {
                                Text("Маршрутник", fontWeight = FontWeight.Bold)
                            }
                            activeRouteSheet?.routeSheet?.dateString?.let { date ->
                                Text("Дата: $date", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                        }
                    },
                    actions = {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(end = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                IconButton(
                                    modifier = Modifier.size(30.dp),
                                    onClick = {
                                        val missingCount = localItems.count { item ->
                                            val matched = allClients.find { it.client.id == item.clientId }
                                                ?: allClients.find { !it.client.label.isNullOrBlank() && it.client.label.trim().equals(item.clientLabelSnapshot.trim(), ignoreCase = true) }
                                                ?: allClients.find { it.client.shopName.trim().equals(item.clientShopSnapshot.trim(), ignoreCase = true) }
                                            !isCoordinatesFieldFilled(matched?.client?.coordinatesManual)
                                        }
                                        val msg = if (hasMissingCoordinatesInRoute) "У маршруті є клієнти без координат ($missingCount)" else "У всіх клієнтів є координати"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = "Індикатор координат у маршруті",
                                        tint = if (hasMissingCoordinatesInRoute) Color(0xFFF44336) else Color(0xFF4CAF50),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    modifier = Modifier.size(30.dp),
                                    onClick = { showOnlyActiveTasksRouteItems = !showOnlyActiveTasksRouteItems }
                                ) {
                                    Box(
                                        modifier = if (showOnlyActiveTasksRouteItems) {
                                            Modifier
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
                                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                        } else Modifier
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            if (routeClientsWithActiveTasksCount > 0) {
                                                Text(
                                                    text = "$routeClientsWithActiveTasksCount",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (hasRouteClientsWithActiveTasks) Color(0xFFF44336) else Color(0xFF4CAF50)
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.AssignmentLate,
                                                contentDescription = "Фільтр за активними завданнями",
                                                tint = if (hasRouteClientsWithActiveTasks) Color(0xFFF44336) else Color(0xFF4CAF50),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    modifier = Modifier.size(30.dp),
                                    onClick = {
                                        val newLock = !isLocked
                                        viewModel.setRouteSheetLocked(newLock)
                                        if (newLock && isManualOrderingMode) {
                                            exitManualOrderingMode()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (isLocked) "Розблокувати" else "Заблокувати",
                                        tint = if (isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                IconButton(
                                    modifier = Modifier.size(30.dp),
                                    onClick = { runRouteOptimization(byCityOnly = false) },
                                    enabled = !isLocked && localItems.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = "Оптимізувати порядок маршруту повністю",
                                        tint = if (isLocked || localItems.isEmpty()) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    modifier = Modifier.size(30.dp),
                                    onClick = { runRouteOptimization(byCityOnly = true) },
                                    enabled = !isLocked && localItems.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationCity,
                                        contentDescription = "Оптимізувати порядок тільки по населених пунктах",
                                        tint = if (isLocked || localItems.isEmpty()) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f) else Color(0xFFFF9800),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    modifier = Modifier.size(30.dp),
                                    onClick = { launchGoogleMapsNavigation() },
                                    enabled = localItems.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Directions,
                                        contentDescription = "Побудувати маршрут у Google Картах",
                                        tint = if (localItems.isEmpty()) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    modifier = Modifier.size(30.dp),
                                    onClick = {
                                        if (isManualOrderingMode) {
                                            exitManualOrderingMode()
                                        } else if (!isLocked) {
                                            isManualOrderingMode = true
                                            manualAssignments.clear()
                                        }
                                    },
                                    enabled = !isLocked
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FormatListNumbered,
                                        contentDescription = "Упорядкування вручну",
                                        tint = if (isLocked) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
                                        } else if (isManualOrderingMode) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isManualOrderingMode) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (activeRouteSheet == null || localItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Немає активного маршрутника.\nЗавантажте файл таблиці для створення маршруту.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    val totalWeightKg = localItems.sumOf { it.weightKg }
                    val totalAmountSum = localItems.sumOf { it.amountSum }
                    val uniquePointsCount = remember(localItems) {
                        localItems.map { item ->
                            val label = item.clientLabelSnapshot.trim()
                            val shop = item.clientShopSnapshot.trim()
                            val city = (item.clientCitySnapshot ?: "").trim()
                            if (label.isNotBlank()) "$city-$label" else "$city-$shop"
                        }.distinct().size
                    }

                    if (totalWeightKg > 0 || totalAmountSum > 0) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isLocked) "Точок: $uniquePointsCount" else "Позицій: ${localItems.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (totalWeightKg > 0) {
                                    Text(
                                        text = "Вага: ${formatNumberWithSpaces(totalWeightKg, false)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (totalAmountSum > 0) {
                                    Text(
                                        text = "Сума: ${formatNumberWithSpaces(totalAmountSum, true)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedLocalItems, key = { it.id }) { item ->
                            val index = localItems.indexOf(item)

                            fun getEffectiveCityForItem(routeItem: RouteSheetItemEntity): String {
                                val matchingClient = allClients.find { it.client.id == routeItem.clientId }
                                return (matchingClient?.client?.city ?: routeItem.clientCitySnapshot) ?: "—"
                            }

                            val currentCity = getEffectiveCityForItem(item)
                            val prevCity = if (index > 0) getEffectiveCityForItem(localItems[index - 1]) else null
                            val isCityHeader = isLocked && showCityHeaders && (index == 0 || currentCity != prevCity)

                            if (isCityHeader) {
                                val cityItems = localItems.filter { getEffectiveCityForItem(it) == currentCity }
                                val cityPointCount = cityItems.size
                                val cityTotalWeight = cityItems.sumOf { it.weightKg }
                                val cityTotalSum = cityItems.sumOf { it.amountSum }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 2.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (currentCity != "—") currentCity else "Без міста",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Точок: $cityPointCount",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            if (cityTotalWeight > 0) {
                                                Text(
                                                    text = "Вага: ${formatNumberWithSpaces(cityTotalWeight, false)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (cityTotalSum > 0) {
                                                Text(
                                                    text = "Сума: ${formatNumberWithSpaces(cityTotalSum, true)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            ReorderableItem(reorderableState, key = item.id, enabled = !isLocked && !isManualOrderingMode) { isDragging ->
                                val isSelected = selectedItemIds.contains(item.id)
                                val assignedNum = manualAssignments[item.id]
                                val matchedClient = allClients.find { it.client.id == item.clientId }
                                    ?: allClients.find { !it.client.label.isNullOrBlank() && it.client.label.trim().equals(item.clientLabelSnapshot.trim(), ignoreCase = true) }
                                    ?: allClients.find { it.client.shopName.trim().equals(item.clientShopSnapshot.trim(), ignoreCase = true) }

                                RouteItemCard(
                                    reorderableScope = this,
                                    index = if (index >= 0) index else 0,
                                    item = item,
                                    clientWithDetails = matchedClient,
                                    isDragging = isDragging,
                                    isSelected = isSelected,
                                    selectedItems = localItems.filter { it.id in selectedItemIds },
                                    hasSelectionMode = selectedItemIds.isNotEmpty(),
                                    isLocked = isLocked,
                                    isManualOrderingMode = isManualOrderingMode,
                                    assignedNumber = assignedNum,
                                    minSwipeThresholdPx = minSwipeThresholdPx,
                                    onToggleSelect = {
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    },
                                    onLongPressNumber = {
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    },
                                    onDoubleTapNumber = {
                                        if (!isLocked && !isManualOrderingMode) {
                                            swapItemData = item to if (index >= 0) index else 0
                                        }
                                    },
                                    onEditNote = { selectedItemForNote = item },
                                    onCallClient = { clientId -> selectedClientIdForCalls = clientId },
                                    onTasksClick = { clientId -> selectedClientIdForTasks = clientId },
                                    onGetClientAddressAndCoords = { clientId -> viewModel.getClientAddressAndCoords(clientId) },
                                    onGetClientGeoDetails = { clientId -> viewModel.getClientGeoDetails(clientId) },
                                    onOpenClientDetail = onOpenClientDetail,
                                    onAssignNextNumber = { assignNextNumber(item) },
                                    onUnassignNumber = { unassignNumber(item) },
                                    onToggleCompleted = { completed -> viewModel.toggleRouteItemCompleted(item, completed) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RouteItemCard(
    reorderableScope: ReorderableCollectionItemScope,
    index: Int,
    item: RouteSheetItemEntity,
    clientWithDetails: ClientWithDetails? = null,
    isDragging: Boolean,
    isSelected: Boolean,
    selectedItems: List<RouteSheetItemEntity> = emptyList(),
    hasSelectionMode: Boolean,
    isLocked: Boolean,
    isManualOrderingMode: Boolean,
    assignedNumber: Int?,
    minSwipeThresholdPx: Float = 0f,
    onToggleSelect: () -> Unit,
    onLongPressNumber: () -> Unit,
    onDoubleTapNumber: () -> Unit,
    onEditNote: () -> Unit,
    onCallClient: (clientId: Long) -> Unit,
    onTasksClick: (clientId: Long) -> Unit = {},
    onGetClientAddressAndCoords: suspend (clientId: Long) -> Triple<String?, Double?, Double?>,
    onGetClientGeoDetails: suspend (clientId: Long) -> ClientGeoDetails,
    onOpenClientDetail: (clientId: Long) -> Unit,
    onAssignNextNumber: () -> Unit,
    onUnassignNumber: () -> Unit,
    onToggleCompleted: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasNote = item.noteText.isNotBlank() || item.noteAudioUri != null || item.notePhotoUri != null || item.noteFileUri != null
    var showContextMenu by remember { mutableStateOf(false) }

    val liveClient = clientWithDetails?.client
    val liveNames = clientWithDetails?.names

    val effectiveCity = liveClient?.city ?: item.clientCitySnapshot
    val effectiveShop = liveClient?.shopName ?: item.clientShopSnapshot
    val effectiveLabel = liveClient?.label ?: item.clientLabelSnapshot
    val effectiveName = if (!liveNames.isNullOrEmpty()) liveNames.joinToString(" ") { it.fullName } else item.clientNameSnapshot
    val effectiveAddress = if (liveClient != null) (liveClient.addressManual ?: "") else ""

    val hasCoordsRecord = isHttpsCoordinates(liveClient?.coordinatesManual)

    var clientAddressText by remember { mutableStateOf(effectiveAddress) }
    LaunchedEffect(item.clientId, liveClient?.addressManual) {
        if (liveClient != null) {
            clientAddressText = liveClient.addressManual ?: ""
        } else if (item.clientId != 0L) {
            val geo = onGetClientGeoDetails(item.clientId)
            clientAddressText = geo.address ?: ""
        }
    }

    val elevation by animateDpAsState(if (isDragging) 8.dp else 2.dp, label = "elevation")
    val scale by animateFloatAsState(if (isDragging) 1.03f else 1f, label = "scale")

    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var totalHorizontalDragX by remember { mutableFloatStateOf(0f) }

    val maxSlidePx = if (minSwipeThresholdPx > 0f) minSwipeThresholdPx * 1.25f else 400f

    val targetClientId = clientWithDetails?.client?.id ?: (if (item.clientId != 0L) item.clientId else null)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
            .graphicsLayer {
                translationX = if (isManualOrderingMode || isLocked) totalHorizontalDragX.coerceIn(-maxSlidePx, maxSlidePx) else 0f
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                onClick = {
                    if (isManualOrderingMode) {
                        if (assignedNumber == null) onAssignNextNumber() else onUnassignNumber()
                    } else if (hasSelectionMode) {
                        onToggleSelect()
                    } else {
                        // Поодиноке натискання нічого не робить за вимогою користувача
                    }
                },
                onLongClick = {
                    if (!isManualOrderingMode) {
                        showContextMenu = true
                    }
                }
            )
            .pointerInput(item.id, isManualOrderingMode, isLocked, minSwipeThresholdPx) {
                if (isManualOrderingMode || isLocked) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalHorizontalDragX = 0f },
                        onDragEnd = {
                            val threshold = if (minSwipeThresholdPx > 0f) minSwipeThresholdPx else if (cardWidthPx > 0f) cardWidthPx * 0.5f else 180f
                            if (totalHorizontalDragX >= threshold) {
                                if (isManualOrderingMode) onAssignNextNumber()
                                else if (isLocked) onToggleCompleted(true)
                            } else if (totalHorizontalDragX <= -threshold) {
                                if (isManualOrderingMode) onUnassignNumber()
                                else if (isLocked) onToggleCompleted(false)
                            }
                            totalHorizontalDragX = 0f
                        },
                        onDragCancel = { totalHorizontalDragX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalHorizontalDragX += dragAmount
                        }
                    )
                }
            }
            .border(
                width = if (isSelected || (isLocked && item.isCompleted) || (isManualOrderingMode && assignedNumber != null)) 2.5.dp else 1.dp,
                color = when {
                    isLocked && item.isCompleted -> Color(0xFF4CAF50)
                    isManualOrderingMode && assignedNumber != null -> MaterialTheme.colorScheme.primary
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                isSelected || (isManualOrderingMode && assignedNumber != null) -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Box {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                // Top Row: Number Badge + (City & Shop Name) + (Invoice Number & Label & Drag Handle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Number Badge (Top-Left)
                    val badgeBgColor = when {
                        isManualOrderingMode -> if (assignedNumber != null) MaterialTheme.colorScheme.primary else Color(0xFF9E9E9E)
                        isSelected || isDragging -> MaterialTheme.colorScheme.primary
                        else -> getBadgeColorForCityAndLabel(effectiveCity, effectiveLabel)
                    }

                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .pointerInput(item.id, index, hasSelectionMode, isManualOrderingMode, isLocked) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (!isManualOrderingMode) onLongPressNumber()
                                    },
                                    onTap = {
                                        if (isManualOrderingMode) {
                                            if (assignedNumber == null) onAssignNextNumber() else onUnassignNumber()
                                        } else {
                                            onToggleSelect()
                                        }
                                    }
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBgColor,
                        tonalElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isManualOrderingMode) {
                                Text(
                                    text = if (assignedNumber != null) "$assignedNumber" else "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (isSelected && !isDragging) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Виділено",
                                    tint = Color.White
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // City (Blue) & Shop Name Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!effectiveCity.isNullOrBlank()) effectiveCity else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF448AFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = effectiveShop,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right Column: Invoice Number & Label & Drag Handle
                    Column(horizontalAlignment = Alignment.End) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Invoice Number Badge (BEFORE Label chip, on same level)
                            if (item.orderNumber.isNotBlank()) {
                                val cleanOrd = formatOrderNumber(item.orderNumber)
                                if (cleanOrd.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Receipt,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = cleanOrd,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }

                            // Label Chip ("Мітка")
                            if (effectiveLabel.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = effectiveLabel,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        with(reorderableScope) {
                            IconButton(
                                onClick = {},
                                enabled = !isLocked && !isManualOrderingMode,
                                modifier = Modifier
                                    .size(36.dp)
                                    .then(if (!isLocked && !isManualOrderingMode) Modifier.draggableHandle() else Modifier)
                            ) {
                                Icon(
                                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.Menu,
                                    contentDescription = if (isLocked) "Заблоковано" else "Перетягнути",
                                    modifier = Modifier.size(26.dp),
                                    tint = if (isLocked) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Middle Row: Client Names ("Клієнт") - Червоний колір якщо є невиконане (активне) завдання, Білий якщо немає
                val clientTasks = clientWithDetails?.tasks ?: emptyList()
                val hasActiveTasks = clientTasks.any { !it.isCompleted }
                val nameColor = if (hasActiveTasks) Color(0xFFF44336) else Color.White

                if (!liveNames.isNullOrEmpty()) {
                    liveNames.filter { it.fullName.isNotBlank() }.forEach { n ->
                        Text(
                            text = n.fullName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = nameColor
                        )
                    }
                } else if (item.clientNameSnapshot.isNotBlank()) {
                    Text(
                        text = item.clientNameSnapshot,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = nameColor
                    )
                }

                // Address (Place Icon) placed ABOVE Weight and Sum
                if (clientAddressText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = clientAddressText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Bottom Row: Left = (Weight & Sum), Right = (Action Buttons: Directions, Call, Note)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.weightKg > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Scale,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = formatNumberWithSpaces(item.weightKg, isCurrency = false),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (item.amountSum > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(1.dp))
                                Text(
                                    text = formatNumberWithSpaces(item.amountSum, isCurrency = true),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Action Buttons Row (Tasks, Directions, Call, Note)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Tasks button (To the LEFT of Map Pin icon)
                        val clientTasks = clientWithDetails?.tasks ?: emptyList()
                        val taskTint = getTaskIconTint(clientTasks)

                        IconButton(
                            onClick = {
                                if (targetClientId != null) {
                                    onTasksClick(targetClientId)
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Завдання",
                                tint = taskTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Map Pin button («Показати на карті»)
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val geo = if (targetClientId != null) onGetClientGeoDetails(targetClientId) else null
                                    val shop = cleanTextForCopy(item.clientShopSnapshot)
                                    val cleanFirstName = cleanTextForCopy(item.clientNameSnapshot)
                                    val pinLabel = listOfNotNull(shop.ifBlank { null }, cleanFirstName.ifBlank { null }).joinToString(", ") + if (item.clientLabelSnapshot.isNotBlank()) " (${item.clientLabelSnapshot})" else ""
                                    val manualCoords = geo?.coordinatesManual?.trim()

                                    val extractedUrl = extractUrlFromText(manualCoords)
                                    if (extractedUrl != null) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(extractedUrl)).apply { setPackage("com.google.android.apps.maps") }
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(extractedUrl)))
                                        }
                                    } else {
                                        val pointQuery = when {
                                            geo?.lat != null && geo.lng != null -> "${geo.lat},${geo.lng}"
                                            !manualCoords.isNullOrBlank() -> manualCoords
                                            else -> null
                                        }

                                        if (!pointQuery.isNullOrBlank()) {
                                            val gmmUri = Uri.parse("geo:0,0?q=${Uri.encode(pointQuery)}(${Uri.encode(pinLabel)})&z=20")
                                            val intent = Intent(Intent.ACTION_VIEW, gmmUri).apply { setPackage("com.google.android.apps.maps") }
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(pointQuery)}")
                                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, "У клієнта не вказано координати!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            val pinIconTint = if (isCoordinatesFieldFilled(liveClient?.coordinatesManual)) Color(0xFF4CAF50) else Color(0xFFF44336)
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Показати на карті",
                                tint = pinIconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Call button
                        IconButton(
                            onClick = {
                                if (targetClientId != null) {
                                    onCallClient(targetClientId)
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Передзвонити",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Note button
                        IconButton(
                            onClick = onEditNote,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (hasNote) Icons.AutoMirrored.Filled.Comment else Icons.Default.AddComment,
                                contentDescription = "Нотатка",
                                tint = if (hasNote) Color(0xFF2196F3) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (item.noteText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Замітка: ${item.noteText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
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
                    text = { Text("Відкрити картку") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        showContextMenu = false
                        if (targetClientId != null) {
                            onOpenClientDetail(targetClientId)
                        } else {
                            android.widget.Toast.makeText(context, "Клієнта не знайдено в базі", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                if (!isLocked && !isManualOrderingMode) {
                    DropdownMenuItem(
                        text = { Text("Поміняти місцями позиції") },
                        leadingIcon = { Icon(Icons.Default.SwapVert, null) },
                        onClick = {
                            showContextMenu = false
                            onDoubleTapNumber()
                        }
                    )
                }
                HorizontalDivider()

                val targetSelectedItems = if (hasSelectionMode && isSelected) selectedItems else listOf(item)

                DropdownMenuItem(
                    text = { Text("№ та Σ накладної") },
                    leadingIcon = { Icon(Icons.Default.Receipt, null) },
                    onClick = {
                        showContextMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val textToCopy = if (targetSelectedItems.size > 1) {
                            targetSelectedItems.joinToString("; ") { sel ->
                                val ord = formatOrderNumber(sel.orderNumber)
                                val sumStr = formatNumberWithSpaces(sel.amountSum, true)
                                if (ord.isNotBlank()) "$ord ($sumStr)" else "($sumStr)"
                            }
                        } else {
                            val ord = formatOrderNumber(item.orderNumber)
                            val sumStr = formatNumberWithSpaces(item.amountSum, true)
                            if (ord.isNotBlank()) "$ord ($sumStr)" else "($sumStr)"
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("№ та Σ накладної", textToCopy))
                    }
                )

                DropdownMenuItem(
                    text = { Text("Перероб. на готівкову") },
                    leadingIcon = { Icon(Icons.Default.Autorenew, null) },
                    onClick = {
                        showContextMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val namesClean = cleanTextForCopy(effectiveName)
                        val textToCopy = if (targetSelectedItems.size > 1) {
                            val ordersList = targetSelectedItems.mapNotNull { sel -> formatOrderNumber(sel.orderNumber).ifBlank { null } }.joinToString("; ")
                            "$namesClean потрібно переробити готівкову накладну $ordersList на перерахункову"
                        } else {
                            val ordStr = formatOrderNumber(item.orderNumber)
                            val ordSpace = if (ordStr.isNotBlank()) "$ordStr " else ""
                            "$namesClean потрібно переробити готівкову накладну ${ordSpace}на перерахункову"
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("Перероб. на готівкову", textToCopy))
                    }
                )

                DropdownMenuItem(
                    text = { Text("Оплатили перерах.") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                    onClick = {
                        showContextMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val namesClean = cleanTextForCopy(effectiveName)
                        val textToCopy = if (targetSelectedItems.size > 1) {
                            val ordersWithSums = targetSelectedItems.joinToString("; ") { selItem ->
                                val ord = formatOrderNumber(selItem.orderNumber)
                                val ordSpace = if (ord.isNotBlank()) "$ord " else ""
                                val sumStr = formatNumberWithSpaces(selItem.amountSum, true)
                                "$ordSpace($sumStr)"
                            }
                            val totalSelectedSum = targetSelectedItems.sumOf { it.amountSum }
                            val totalSumStr = formatNumberWithSpaces(totalSelectedSum, true)
                            "$namesClean оплатили перерахункову накладну $ordersWithSums на загальну суму $totalSumStr"
                        } else {
                            val amountStr = formatNumberWithSpaces(item.amountSum, true)
                            val ord = formatOrderNumber(item.orderNumber)
                            val ordSpace = if (ord.isNotBlank()) "$ord " else ""
                            "$namesClean оплатили перерахункову накладну $ordSpace($amountStr)"
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("Оплатили перерах", textToCopy))
                    }
                )
                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("Поділитись") },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = {
                        showContextMenu = false
                        val shopClean = cleanTextForCopy(item.clientShopSnapshot)
                        val namesClean = cleanTextForCopy(item.clientNameSnapshot)
                        val shareText = "Магазин: $shopClean\nКлієнт: $namesClean\nМісто: ${item.clientCitySnapshot ?: ""}".replace("\"\"", "\"")
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, shareText); type = "text/plain" }, null))
                    }
                )
                DropdownMenuItem(
                    text = { Text("ПІБ") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    onClick = {
                        showContextMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val namesText = cleanTextForCopy(item.clientNameSnapshot).replace("\"\"", "\"")
                        clipboard.setPrimaryClip(ClipData.newPlainText("ПІБ", namesText))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Повні дані") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Assignment, null) },
                    onClick = {
                        showContextMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val shopClean = cleanTextForCopy(item.clientShopSnapshot)
                        val shopText = if (shopClean.isNotBlank()) "\"$shopClean\"" else null
                        val namesText = cleanTextForCopy(item.clientNameSnapshot).ifBlank { null }
                        val labelText = item.clientLabelSnapshot.ifBlank { null }
                        val fullData = listOfNotNull(shopText, namesText, labelText).joinToString(" ").replace("\"\"", "\"")
                        clipboard.setPrimaryClip(ClipData.newPlainText("Повні дані", fullData))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Коротко") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ShortText, null) },
                    onClick = {
                        showContextMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val shopClean = cleanTextForCopy(item.clientShopSnapshot)
                        val shopText = if (shopClean.isNotBlank()) "\"$shopClean\"" else null
                        val shortNamesText = formatShortName(item.clientNameSnapshot).ifBlank { null }
                        val labelText = item.clientLabelSnapshot.ifBlank { null }
                        val shortData = listOfNotNull(shopText, shortNamesText, labelText).joinToString(" ").replace("\"\"", "\"")
                        clipboard.setPrimaryClip(ClipData.newPlainText("Коротко", shortData))
                    }
                )
            }
        }
    }
}

@Composable
fun SwapNumberDialog(
    item: RouteSheetItemEntity,
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onConfirmSwap: (targetIndex: Int) -> Unit
) {
    val initialText = "${currentIndex + 1}"
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(0, initialText.length)
            )
        )
    }
    var isError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Змінити номер позиції") },
        text = {
            Column {
                Text(
                    text = "Клієнт: ${item.clientShopSnapshot}\nПоточний номер: ${currentIndex + 1}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val digitsOnly = newValue.text.filter { c -> c.isDigit() }
                        textFieldValue = newValue.copy(text = digitsOnly)
                        isError = false
                    },
                    label = { Text("Новий порядковий номер (1..$totalCount)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                if (isError) {
                    Text(
                        text = "Введіть число від 1 до $totalCount",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val num = textFieldValue.text.toIntOrNull()
                if (num != null && num in 1..totalCount) {
                    onConfirmSwap(num - 1)
                } else {
                    isError = true
                }
            }) {
                Text("Змінити")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Скасувати") }
        }
    )
}

@Composable
fun RouteItemNoteDialog(
    item: RouteSheetItemEntity,
    onDismiss: () -> Unit,
    onSave: (text: String, audioUri: String?, photoUri: String?, fileUri: String?) -> Unit
) {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf(item.noteText) }
    var audioUri by remember { mutableStateOf(item.noteAudioUri) }
    var photoUri by remember { mutableStateOf(item.notePhotoUri) }
    var fileUri by remember { mutableStateOf(item.noteFileUri) }

    var isRecordingAudio by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let { voiceText ->
                noteText = if (noteText.isBlank()) voiceText else "$noteText $voiceText"
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            photoUri = it.toString()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            fileUri = it.toString()
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            audioUri = it.toString()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Нотатка: ${item.clientShopSnapshot}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Текст замітки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons: Voice input, Audio, Photo, File
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Speech to text button
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говоріть...")
                        }
                        try { voiceSearchLauncher.launch(intent) } catch (_: Exception) {}
                    }) {
                        Icon(Icons.Default.Mic, "Надиктувати", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Attach photo button
                    IconButton(onClick = {
                        photoPickerLauncher.launch(arrayOf("image/*"))
                    }) {
                        Icon(Icons.Default.PhotoCamera, "Фото", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Attach audio button
                    IconButton(onClick = {
                        audioPickerLauncher.launch(arrayOf("audio/*"))
                    }) {
                        Icon(Icons.Default.Audiotrack, "Аудіо", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Attach file button
                    IconButton(onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Default.AttachFile, "Файл", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Audio Preview / Playback
                if (audioUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Audiotrack, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Аудіо запис", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                try {
                                    if (isPlayingAudio) {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                        mediaPlayer = null
                                        isPlayingAudio = false
                                    } else {
                                        mediaPlayer = MediaPlayer().apply {
                                            setDataSource(context, Uri.parse(audioUri))
                                            prepare()
                                            start()
                                            setOnCompletionListener {
                                                isPlayingAudio = false
                                            }
                                        }
                                        isPlayingAudio = true
                                    }
                                } catch (_: Exception) {
                                    isPlayingAudio = false
                                }
                            }) {
                                Icon(if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow, "Програти")
                            }
                            IconButton(onClick = { audioUri = null }) {
                                Icon(Icons.Default.Close, "Видалити аудіо", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Photo Preview
                if (photoUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Фото",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { photoUri = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Close, "Видалити фото", tint = Color.Red)
                        }
                    }
                }

                // File Attachment Indicator
                if (fileUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Файл прикріплено", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { fileUri = null }) {
                                Icon(Icons.Default.Close, "Видалити файл", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Скасувати") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(noteText, audioUri, photoUri, fileUri)
                    }) {
                        Text("Зберегти")
                    }
                }

                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.ime))
            }
        }
    }
}

data class RouteItemWithLocation(
    val item: RouteSheetItemEntity,
    val lat: Double,
    val lng: Double,
    val isCityCenterFallback: Boolean
)

suspend fun resolveRouteItemLocation(
    context: Context,
    item: RouteSheetItemEntity,
    matchedClient: ClientWithDetails?
): RouteItemWithLocation? = withContext(Dispatchers.IO) {
    val clientLat = matchedClient?.client?.latitude
    val clientLng = matchedClient?.client?.longitude
    if (clientLat != null && clientLng != null) {
        return@withContext RouteItemWithLocation(item, clientLat, clientLng, false)
    }

    val manualCoordsStr = (matchedClient?.client?.coordinatesManual ?: "").trim()
    val parsedCoords = parseCoordinates(manualCoordsStr)
    if (parsedCoords != null) {
        return@withContext RouteItemWithLocation(item, parsedCoords.first, parsedCoords.second, false)
    }

    val city = (matchedClient?.client?.city ?: item.clientCitySnapshot ?: "").trim()
    if (city.isNotBlank()) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName("$city, Україна", 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                return@withContext RouteItemWithLocation(item, addr.latitude, addr.longitude, true)
            }
        } catch (_: Exception) {}
    }
    null
}

const val ROUTE_BASE_ADDRESS = "м. Рівне, вул. Князя Володимира 112А"
const val BASE_DEFAULT_LAT = 50.6385
const val BASE_DEFAULT_LNG = 26.2574

suspend fun resolveCityCenterLocation(context: Context, city: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    if (city.isBlank() || city == "—") return@withContext null
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocationName("$city, Україна", 1)
        if (!addresses.isNullOrEmpty()) {
            val a = addresses[0]
            return@withContext Pair(a.latitude, a.longitude)
        }
    } catch (_: Exception) {}
    null
}

suspend fun getBaseLocation(context: Context): Pair<Double, Double> = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocationName(ROUTE_BASE_ADDRESS, 1)
        if (!addresses.isNullOrEmpty()) {
            val a = addresses[0]
            return@withContext Pair(a.latitude, a.longitude)
        }
    } catch (_: Exception) {}
    Pair(BASE_DEFAULT_LAT, BASE_DEFAULT_LNG)
}

fun calculateHaversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

fun optimizeRouteOrderTSPWithBase(
    baseLat: Double,
    baseLng: Double,
    locations: List<RouteItemWithLocation>
): List<RouteItemWithLocation> {
    if (locations.isEmpty()) return emptyList()
    if (locations.size == 1) return locations

    val remaining = locations.toMutableList()
    val route = mutableListOf<RouteItemWithLocation>()

    var curLat = baseLat
    var curLng = baseLng

    while (remaining.isNotEmpty()) {
        val nearest = remaining.minByOrNull {
            calculateHaversineDistanceKm(curLat, curLng, it.lat, it.lng)
        }!!
        remaining.remove(nearest)
        route.add(nearest)
        curLat = nearest.lat
        curLng = nearest.lng
    }

    var improved = true
    var iterations = 0
    while (improved && iterations < 100) {
        improved = false
        iterations++
        for (i in 0 until route.size - 1) {
            for (j in i + 1 until route.size) {
                val prevLat = if (i == 0) baseLat else route[i - 1].lat
                val prevLng = if (i == 0) baseLng else route[i - 1].lng

                val nextLat = if (j + 1 < route.size) route[j + 1].lat else baseLat
                val nextLng = if (j + 1 < route.size) route[j + 1].lng else baseLng

                val d1 = calculateHaversineDistanceKm(prevLat, prevLng, route[i].lat, route[i].lng) +
                         calculateHaversineDistanceKm(route[j].lat, route[j].lng, nextLat, nextLng)

                val d2 = calculateHaversineDistanceKm(prevLat, prevLng, route[j].lat, route[j].lng) +
                         calculateHaversineDistanceKm(route[i].lat, route[i].lng, nextLat, nextLng)

                if (d2 < d1 - 0.001) {
                    route.subList(i, j + 1).reverse()
                    improved = true
                }
            }
        }
    }

    return route
}

fun openGoogleMapsRouteWithBase(
    context: Context,
    baseAddressOrCoords: String,
    segment: List<RouteItemWithLocation>,
    isFirstStage: Boolean,
    isLastStage: Boolean
) {
    if (segment.isEmpty()) return

    val origin = if (isFirstStage) baseAddressOrCoords else "${segment.first().lat},${segment.first().lng}"
    val destination = if (isLastStage) baseAddressOrCoords else "${segment.last().lat},${segment.last().lng}"

    val waypointsList = when {
        isFirstStage && isLastStage -> segment.map { "${it.lat},${it.lng}" }
        isFirstStage -> segment.dropLast(1).map { "${it.lat},${it.lng}" }
        isLastStage -> segment.drop(1).map { "${it.lat},${it.lng}" }
        else -> if (segment.size > 2) segment.subList(1, segment.size - 1).map { "${it.lat},${it.lng}" } else emptyList()
    }

    val waypointsStr = if (waypointsList.isNotEmpty()) waypointsList.joinToString("|") else null

    val mapsUriStr = buildString {
        append("https://www.google.com/maps/dir/?api=1")
        append("&origin=").append(Uri.encode(origin))
        append("&destination=").append(Uri.encode(destination))
        if (!waypointsStr.isNullOrBlank()) {
            append("&waypoints=").append(Uri.encode(waypointsStr))
        }
        append("&travelmode=driving")
    }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUriStr)).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUriStr)))
    }
}

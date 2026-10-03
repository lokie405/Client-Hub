package com.seryoga.myapplication.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.seryoga.myapplication.data.RouteSheetItemEntity
import com.seryoga.myapplication.ui.ClientViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSheetScreen(
    viewModel: ClientViewModel,
    onBack: () -> Unit
) {
    val activeRouteSheet by viewModel.activeRouteSheet.collectAsState()
    var selectedItemForNote by remember { mutableStateOf<RouteSheetItemEntity?>(null) }
    var selectedItemIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var swapItemData by remember { mutableStateOf<Pair<RouteSheetItemEntity, Int>?>(null) }
    var isLocked by remember { mutableStateOf(false) }

    val items = remember(activeRouteSheet) {
        activeRouteSheet?.items?.sortedBy { it.orderIndex } ?: emptyList()
    }

    var localItems by remember(items) { mutableStateOf(items) }
    LaunchedEffect(items) {
        localItems = items
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localItems = localItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        viewModel.reorderRouteItems(localItems)
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

    Scaffold(
        topBar = {
            if (selectedItemIds.isNotEmpty()) {
                TopAppBar(
                    title = {
                        Text("Виділено: ${selectedItemIds.size}", fontWeight = FontWeight.Bold)
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
                            Text("Маршрутник", fontWeight = FontWeight.Bold)
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
                        IconButton(onClick = { isLocked = !isLocked }) {
                            Icon(
                                if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isLocked) "Розблокувати" else "Заблокувати",
                                tint = if (isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
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
                                    text = "Позицій: ${localItems.size}",
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
                                        color = Color(0xFF2E7D32)
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
                        items(localItems, key = { it.id }) { item ->
                            ReorderableItem(reorderableState, key = item.id, enabled = !isLocked) { isDragging ->
                                val index = localItems.indexOf(item)
                                val isSelected = selectedItemIds.contains(item.id)

                                RouteItemCard(
                                    reorderableScope = this,
                                    index = if (index >= 0) index else 0,
                                    item = item,
                                    isDragging = isDragging,
                                    isSelected = isSelected,
                                    hasSelectionMode = selectedItemIds.isNotEmpty(),
                                    isLocked = isLocked,
                                    onToggleSelect = {
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    },
                                    onLongPressNumber = {
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    },
                                    onDoubleTapNumber = {
                                        if (!isLocked) {
                                            swapItemData = item to if (index >= 0) index else 0
                                        }
                                    },
                                    onEditNote = { selectedItemForNote = item }
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
    isDragging: Boolean,
    isSelected: Boolean,
    hasSelectionMode: Boolean,
    isLocked: Boolean,
    onToggleSelect: () -> Unit,
    onLongPressNumber: () -> Unit,
    onDoubleTapNumber: () -> Unit,
    onEditNote: () -> Unit
) {
    val context = LocalContext.current
    val hasNote = item.noteText.isNotBlank() || item.noteAudioUri != null || item.notePhotoUri != null || item.noteFileUri != null
    var showContextMenu by remember { mutableStateOf(false) }

    val elevation by animateDpAsState(if (isDragging) 8.dp else 2.dp, label = "elevation")
    val scale by animateFloatAsState(if (isDragging) 1.03f else 1f, label = "scale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                onClick = {
                    if (hasSelectionMode) {
                        onToggleSelect()
                    }
                },
                onLongClick = {
                    if (!hasSelectionMode) {
                        showContextMenu = true
                    }
                }
            )
            .then(
                if (isSelected) Modifier.border(
                    width = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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
                // Top Row: Number Badge + (City & Shop Name) + (Label & Icons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Number Badge (Top-Left)
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .pointerInput(item.id, index) {
                                detectTapGestures(
                                    onDoubleTap = { onDoubleTapNumber() },
                                    onLongPress = { onLongPressNumber() },
                                    onTap = {
                                        if (hasSelectionMode) {
                                            onToggleSelect()
                                        }
                                    }
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected || isDragging) MaterialTheme.colorScheme.primary else Color(0xFF616161),
                        tonalElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSelected && !isDragging) {
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
                            text = if (!item.clientCitySnapshot.isNullOrBlank()) item.clientCitySnapshot else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF448AFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = item.clientShopSnapshot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right Column: Label ("Мітка") & Icons
                    Column(horizontalAlignment = Alignment.End) {
                        if (item.clientLabelSnapshot.isNotBlank()) {
                            Text(
                                text = item.clientLabelSnapshot,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onEditNote,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (hasNote) Icons.AutoMirrored.Filled.Comment else Icons.Default.AddComment,
                                    contentDescription = "Нотатка",
                                    tint = if (hasNote) Color(0xFF2196F3) else MaterialTheme.colorScheme.outline
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            with(reorderableScope) {
                                IconButton(
                                    onClick = {},
                                    enabled = !isLocked,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .then(if (!isLocked) Modifier.draggableHandle() else Modifier)
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
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Middle Row: Client Name ("Клієнт")
                if (item.clientNameSnapshot.isNotBlank()) {
                    Text(
                        text = item.clientNameSnapshot,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Row: "Вага, кг" & "Сума, грн"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val weightText = if (item.weightKg > 0) formatNumberWithSpaces(item.weightKg, isCurrency = false) else "— кг"
                    Text(
                        text = weightText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val amountText = if (item.amountSum > 0) formatNumberWithSpaces(item.amountSum, isCurrency = true) else "— грн"
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                onDismissRequest = { showContextMenu = false }
            ) {
                if (item.orderNumber.isNotBlank()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Заявка № ${item.orderNumber}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        enabled = false,
                        onClick = {},
                        leadingIcon = {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                    HorizontalDivider()
                }

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
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
            }
        }
    }
}

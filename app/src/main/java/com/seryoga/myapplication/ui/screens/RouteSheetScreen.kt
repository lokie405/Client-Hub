package com.seryoga.myapplication.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.seryoga.myapplication.data.RouteSheetItemEntity
import com.seryoga.myapplication.ui.ClientViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSheetScreen(
    viewModel: ClientViewModel,
    onBack: () -> Unit
) {
    val activeRouteSheet by viewModel.activeRouteSheet.collectAsState()
    var selectedItemForNote by remember { mutableStateOf<RouteSheetItemEntity?>(null) }
    var swapItemData by remember { mutableStateOf<Pair<RouteSheetItemEntity, Int>?>(null) }

    val items = remember(activeRouteSheet) {
        activeRouteSheet?.items?.sortedBy { it.orderIndex } ?: emptyList()
    }

    var localItems by remember(items) { mutableStateOf(items) }
    val lazyListState = rememberLazyListState()

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
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
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(localItems, key = { _, item -> item.id }) { index, item ->
                        RouteItemCard(
                            index = index,
                            totalCount = localItems.size,
                            item = item,
                            onDoubleTapNumber = {
                                swapItemData = item to index
                            },
                            onMoveUp = { viewModel.moveRouteItemUp(localItems, index) },
                            onMoveDown = { viewModel.moveRouteItemDown(localItems, index) },
                            onEditNote = { selectedItemForNote = item }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RouteItemCard(
    index: Int,
    totalCount: Int,
    item: RouteSheetItemEntity,
    onDoubleTapNumber: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEditNote: () -> Unit
) {
    val hasNote = item.noteText.isNotBlank() || item.noteAudioUri != null || item.notePhotoUri != null || item.noteFileUri != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Number badge with Double Tap Swap Dialog
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .pointerInput(item.id, index) {
                        detectTapGestures(
                            onDoubleTap = { onDoubleTapNumber() }
                        )
                    },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.clientLabelSnapshot.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = item.clientLabelSnapshot,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Text(
                        text = item.clientShopSnapshot,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.clientNameSnapshot.isNotBlank()) {
                    Text(
                        text = item.clientNameSnapshot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!item.clientCitySnapshot.isNullOrBlank()) {
                    Text(
                        text = item.clientCitySnapshot,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Note button & Move Up/Down buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditNote) {
                    Icon(
                        imageVector = if (hasNote) Icons.AutoMirrored.Filled.Comment else Icons.Default.AddComment,
                        contentDescription = "Нотатка",
                        tint = if (hasNote) Color(0xFF2196F3) else MaterialTheme.colorScheme.outline
                    )
                }

                Column {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Вгору",
                            tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Вниз",
                            tint = if (index < totalCount - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                }
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
    var inputText by remember { mutableStateOf("${currentIndex + 1}") }
    var isError by remember { mutableStateOf(false) }

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
                    value = inputText,
                    onValueChange = {
                        inputText = it.filter { c -> c.isDigit() }
                        isError = false
                    },
                    label = { Text("Новий порядковий номер (1..$totalCount)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                val num = inputText.toIntOrNull()
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

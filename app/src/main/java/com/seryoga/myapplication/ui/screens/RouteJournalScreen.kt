package com.seryoga.myapplication.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.seryoga.myapplication.data.RouteSheetWithItems
import com.seryoga.myapplication.ui.ClientViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteJournalScreen(
    viewModel: ClientViewModel,
    onBack: () -> Unit
) {
    val allRouteSheets by viewModel.allRouteSheets.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var routeSheetToDelete by remember { mutableStateOf<Long?>(null) }

    val filteredRouteSheets = remember(allRouteSheets, searchQuery) {
        if (searchQuery.isBlank()) {
            allRouteSheets
        } else {
            val words = searchQuery.lowercase().split(" ").filter { it.isNotBlank() }
            allRouteSheets.mapNotNull { routeWithItems ->
                val matchingItems = routeWithItems.items.filter { item ->
                    words.all { w ->
                        item.clientNameSnapshot.lowercase().contains(w) ||
                        item.clientShopSnapshot.lowercase().contains(w) ||
                        (item.clientCitySnapshot?.lowercase()?.contains(w) ?: false) ||
                        item.clientLabelSnapshot.lowercase().contains(w) ||
                        item.noteText.lowercase().contains(w) ||
                        routeWithItems.routeSheet.dateString.lowercase().contains(w)
                    }
                }
                if (matchingItems.isNotEmpty() || words.all { w -> routeWithItems.routeSheet.dateString.lowercase().contains(w) }) {
                    routeWithItems.copy(items = if (matchingItems.isNotEmpty()) matchingItems else routeWithItems.items)
                } else null
            }
        }
    }

    if (routeSheetToDelete != null) {
        AlertDialog(
            onDismissRequest = { routeSheetToDelete = null },
            title = { Text("Видалити маршрутник?") },
            text = { Text("Ви дійсно бажаєте видалити цей маршрутник з журналу?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRouteSheet(routeSheetToDelete!!)
                    routeSheetToDelete = null
                }) {
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { routeSheetToDelete = null }) { Text("Скасувати") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал маршрутників", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Пошук по клієнтах, містах, магазинах...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, "Очистити")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            if (filteredRouteSheets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isBlank()) "Журнал маршрутників порожній" else "Нічого не знайдено",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredRouteSheets, key = { it.routeSheet.id }) { routeWithItems ->
                        JournalRouteCard(
                            routeWithItems = routeWithItems,
                            onDelete = { routeSheetToDelete = routeWithItems.routeSheet.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JournalRouteCard(
    routeWithItems: RouteSheetWithItems,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var activeAudioUri by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val sortedItems = remember(routeWithItems) {
        routeWithItems.items.sortedBy { it.orderIndex }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Маршрут від ${routeWithItems.routeSheet.dateString}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (routeWithItems.routeSheet.isArchived) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF4CAF50).copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = if (routeWithItems.routeSheet.isArchived) "Архів" else "Активний",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (routeWithItems.routeSheet.isArchived) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Клієнтів у маршруті: ${sortedItems.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Row {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Розгорнути"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Видалити", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                sortedItems.forEachIndexed { idx, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${idx + 1}. ",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (item.clientLabelSnapshot.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text(
                                            text = item.clientLabelSnapshot,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = item.clientShopSnapshot,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            if (item.clientNameSnapshot.isNotBlank() || !item.clientCitySnapshot.isNullOrBlank()) {
                                Text(
                                    text = listOfNotNull(item.clientNameSnapshot.ifBlank { null }, item.clientCitySnapshot?.ifBlank { null }).joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (item.noteText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Замітка: ${item.noteText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (item.noteAudioUri != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Audiotrack, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Аудіо запис", style = MaterialTheme.typography.labelSmall)
                                    IconButton(
                                        onClick = {
                                            try {
                                                if (activeAudioUri == item.noteAudioUri) {
                                                    mediaPlayer?.stop()
                                                    mediaPlayer?.release()
                                                    mediaPlayer = null
                                                    activeAudioUri = null
                                                } else {
                                                    mediaPlayer?.stop()
                                                    mediaPlayer?.release()
                                                    mediaPlayer = MediaPlayer().apply {
                                                        setDataSource(context, Uri.parse(item.noteAudioUri))
                                                        prepare()
                                                        start()
                                                        setOnCompletionListener { activeAudioUri = null }
                                                    }
                                                    activeAudioUri = item.noteAudioUri
                                                }
                                            } catch (_: Exception) {
                                                activeAudioUri = null
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            if (activeAudioUri == item.noteAudioUri) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            "Відтворити",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (item.notePhotoUri != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                AsyncImage(
                                    model = item.notePhotoUri,
                                    contentDescription = "Фото",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            if (item.noteFileUri != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Прикріплений файл", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

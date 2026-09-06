package com.seryoga.myapplication.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.seryoga.myapplication.data.CallLogEntity
import com.seryoga.myapplication.data.ClientWithDetails
import com.seryoga.myapplication.data.PhoneWithStats
import com.seryoga.myapplication.ui.ClientViewModel
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
    var selectedClientForPhones by remember { mutableStateOf<Long?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мої Клієнти", fontWeight = FontWeight.Bold) },
                actions = {
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
                        onCallClick = { selectedClientForPhones = clientWithDetails.client.id }
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
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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
                        IconButton(onClick = onAddClientClick) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Додати клієнта",
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
    onCallClick: () -> Unit
) {
    val context = LocalContext.current
    val client = clientWithDetails.client
    val phones = clientWithDetails.phones
    
    val isPhoneFilled = phones.any { it.phoneNumber.isNotBlank() }
    val isAddressFilled = !client.addressManual.isNullOrBlank()
    val isLabelFilled = !client.label.isNullOrBlank()
    val isShopNameFilled = client.shopName.isNotBlank()
    val isNamesFilled = client.firstName.isNotBlank() && client.lastName.isNotBlank()
    
    val isAllFilled = isPhoneFilled && isAddressFilled && isLabelFilled && isNamesFilled && isShopNameFilled

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
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
                Text(
                    text = "${clientWithDetails.client.lastName} ${clientWithDetails.client.firstName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
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
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!clientWithDetails.client.label.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
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

                IconButton(onClick = {
                    val client = clientWithDetails.client
                    val shareText = buildString {
                        appendLine("Магазин: ${client.shopName}")
                        appendLine("Клієнт: ${client.lastName} ${client.firstName}")
                        if (!client.label.isNullOrBlank()) {
                            appendLine("Мітка: ${client.label}")
                        }
                        if (client.latitude != null && client.longitude != null) {
                            if (!client.addressManual.isNullOrBlank()) {
                                appendLine("Адреса: ${client.addressManual}")
                            }
                            appendLine("Карта: http://maps.google.com/maps?q=loc:${client.latitude},${client.longitude}&z=20")
                        } else if (!client.addressManual.isNullOrBlank()) {
                            appendLine("Адреса: ${client.addressManual}")
                            appendLine("Карта: http://maps.google.com/maps?q=${Uri.encode(client.addressManual)}&z=20")
                        }
                    }
                    
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Поділитись",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
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

package com.seryoga.myapplication.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import coil.compose.AsyncImage
import com.seryoga.myapplication.data.ClientWithDetails
import com.seryoga.myapplication.ui.ClientViewModel

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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
                        onClick = { onClientClick(clientWithDetails.client.id) }
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant                                )
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

@Composable
fun ClientCard(
    clientWithDetails: ClientWithDetails,
    onClick: () -> Unit
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
                    if (clientWithDetails.client.shopPhotoUri != null) {
                        AsyncImage(
                            model = clientWithDetails.client.shopPhotoUri,
                            contentDescription = "Маршрут",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = "Маршрут",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
                            // Using coordinates for precise pin and zoom
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

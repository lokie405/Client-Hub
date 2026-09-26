package com.seryoga.myapplication.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.seryoga.myapplication.data.ClientWithDetails
import com.seryoga.myapplication.ui.ClientViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClientDetailScreen(
    viewModel: ClientViewModel,
    clientId: Long?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var names by remember { mutableStateOf(listOf("")) }
    var shopName by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var phones by remember { mutableStateOf(listOf("")) }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var label by remember { mutableStateOf("") }
    var newNoteText by remember { mutableStateOf("") }

    // Statuses
    var namesStatus by remember { mutableStateOf("changed") }
    var shopNameStatus by remember { mutableStateOf("changed") }
    var cityStatus by remember { mutableStateOf("changed") }
    var addressStatus by remember { mutableStateOf("changed") }
    var phonesStatus by remember { mutableStateOf("changed") }
    var notesStatus by remember { mutableStateOf("changed") }
    
    // Initial values to detect changes
    var initialNames by remember { mutableStateOf(listOf("")) }
    var initialShopName by remember { mutableStateOf("") }
    var initialCity by remember { mutableStateOf("") }
    var initialPhones by remember { mutableStateOf(listOf("")) }
    var initialAddress by remember { mutableStateOf("") }
    var initialLabel by remember { mutableStateOf("") }
    var initialNamesStatus by remember { mutableStateOf("changed") }
    var initialShopNameStatus by remember { mutableStateOf("changed") }
    var initialCityStatus by remember { mutableStateOf("changed") }
    var initialAddressStatus by remember { mutableStateOf("changed") }
    var initialPhonesStatus by remember { mutableStateOf("changed") }
    var initialNotesStatus by remember { mutableStateOf("changed") }
    
    val hasChanges by remember {
        derivedStateOf {
            names != initialNames ||
            shopName != initialShopName ||
            city != initialCity ||
            phones != initialPhones ||
            address != initialAddress ||
            label != initialLabel ||
            namesStatus != initialNamesStatus ||
            shopNameStatus != initialShopNameStatus ||
            cityStatus != initialCityStatus ||
            addressStatus != initialAddressStatus ||
            phonesStatus != initialPhonesStatus ||
            notesStatus != initialNotesStatus
        }
    }
    
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    
    val performSave = {
        viewModel.addClient(
            id = clientId,
            names = names,
            shopName = shopName,
            city = city,
            phones = phones,
            address = address,
            lat = latitude,
            lon = longitude,
            label = label,
            namesStatus = namesStatus,
            shopNameStatus = shopNameStatus,
            cityStatus = cityStatus,
            addressStatus = addressStatus,
            phonesStatus = phonesStatus,
            notesStatus = notesStatus
        )
        onBack()
    }
    
    val requestBack = {
        if (hasChanges) {
            showUnsavedChangesDialog = true
        } else {
            onBack()
        }
    }
    
    BackHandler(enabled = true) { requestBack() }
    
    var showAddressOverwriteDialog by remember { mutableStateOf(false) }
    var pendingCity by remember { mutableStateOf("") }
    var pendingAddress by remember { mutableStateOf("") }
    var pendingLat by remember { mutableStateOf<Double?>(null) }
    var pendingLon by remember { mutableStateOf<Double?>(null) }
    
    var showMapPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var clientToDelete by remember { mutableStateOf<ClientWithDetails?>(null) }
    
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (clientId == null) { nameFocusRequester.requestFocus() }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.getCurrentLocationAddress { cityVal, addr, lat, lon ->
                if (!addr.isNullOrBlank() || !cityVal.isNullOrBlank()) {
                    if (address.isBlank() && city.isBlank()) {
                        city = cityVal ?: ""; address = addr ?: ""; latitude = lat; longitude = lon
                        addressStatus = "unchanged" 
                    } else {
                        pendingCity = cityVal ?: ""; pendingAddress = addr ?: ""; pendingLat = lat; pendingLon = lon
                        showAddressOverwriteDialog = true
                    }
                }
            }
        }
    }

    if (showAddressOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showAddressOverwriteDialog = false },
            title = { Text("Оновити адресу?") },
            text = { Text("Поле адреси вже містить дані. Ви бажаєте перезаписати їх на поточну адресу?") },
            confirmButton = {
                TextButton(onClick = {
                    city = pendingCity; address = pendingAddress; latitude = pendingLat; longitude = pendingLon
                    addressStatus = "unchanged"
                    showAddressOverwriteDialog = false
                }) { Text("Так") }
            },
            dismissButton = {
                TextButton(onClick = { showAddressOverwriteDialog = false }) { Text("Ні") }
            }
        )
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Зберегти зміни?") },
            text = { Text("У вас є незбережені зміни. Ви бажаєте зберегти їх перед виходом?") },
            confirmButton = { TextButton(onClick = performSave) { Text("Зберегти") } },
            dismissButton = { TextButton(onClick = onBack) { Text("Вийти без збереження") } }
        )
    }

    if (showMapPicker) {
        MapPickerDialog(
            initialCity = city, initialAddress = address,
            onDismiss = { showMapPicker = false },
            onAddressSelected = { sc, sa, lat, lon ->
                if (address.isBlank() && city.isBlank()) {
                    city = sc; address = sa; latitude = lat; longitude = lon
                    addressStatus = "unchanged"
                } else {
                    pendingCity = sc; pendingAddress = sa; pendingLat = lat; pendingLon = lon
                    showAddressOverwriteDialog = true
                }
                showMapPicker = false
            },
            viewModel = viewModel
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Видалити клієнта?") },
            text = { Text("Ви впевнені, що хочете видалити цю картку?") },
            confirmButton = {
                TextButton(onClick = {
                        clientToDelete?.let { viewModel.deleteClient(it) }
                        showDeleteConfirmDialog = false
                        onBack()
                    }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Видалити") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Скасувати") } }
        )
    }

    LaunchedEffect(clientId) {
        if (clientId != null) {
            val details = viewModel.getClient(clientId)
            details?.let {
                clientToDelete = it
                names = it.names.map { n -> n.fullName }.ifEmpty { listOf("") }
                shopName = it.client.shopName; city = it.client.city ?: ""
                phones = it.phones.map { p -> p.phoneNumber }.ifEmpty { listOf("") }
                address = it.client.addressManual ?: ""
                latitude = it.client.latitude; longitude = it.client.longitude
                label = it.client.label ?: ""
                
                namesStatus = it.client.namesStatus; shopNameStatus = it.client.shopNameStatus
                cityStatus = it.client.cityStatus; addressStatus = it.client.addressStatus
                phonesStatus = it.client.phonesStatus; notesStatus = it.client.notesStatus

                initialNames = names; initialShopName = shopName; initialCity = city
                initialPhones = phones; initialAddress = address; initialLabel = label
                initialNamesStatus = namesStatus; initialShopNameStatus = shopNameStatus
                initialCityStatus = cityStatus; initialAddressStatus = addressStatus
                initialPhonesStatus = phonesStatus; initialNotesStatus = notesStatus
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (clientId == null) "Новий Клієнт" else "Редагувати") },
                navigationIcon = { IconButton(onClick = requestBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (clientId != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) { Icon(Icons.Default.Delete, "Видалити", tint = MaterialTheme.colorScheme.error) }
                    }
                    IconButton(onClick = performSave) { Icon(Icons.Default.Save, "Зберегти") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            
            // Names section
            Column(modifier = Modifier.border(2.dp, if(namesStatus=="changed") Color(0xFF4CAF50) else Color(0xFFFF9800), RoundedCornerShape(4.dp)).padding(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Імена та прізвища", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StatusIndicator(status = namesStatus, onToggle = { namesStatus = it }, label = "Імена")
                }
                names.forEachIndexed { index, name ->
                    OutlinedTextField(
                        value = name, onValueChange = { nv -> names = names.toMutableList().also { it[index] = nv } },
                        label = { Text("Ім'я та прізвище ${index + 1}") },
                        modifier = Modifier.fillMaxWidth().focusRequester(if(index==0) nameFocusRequester else remember { FocusRequester() })
                    )
                }
                TextButton(onClick = { names = names + "" }) { Text("Додати ще одне ім'я") }
            }

            StatusTextField(value = shopName, onValue_Change = { shopName = it.replaceFirstChar { c -> c.uppercase() } }, label = "Назва магазину", status = shopNameStatus, onStatusChange = { shopNameStatus = it }, icon = Icons.Default.Storefront)
            StatusTextField(value = city, onValue_Change = { city = it.replaceFirstChar { c -> c.uppercase() } }, label = "Населений пункт", status = cityStatus, onStatusChange = { cityStatus = it }, icon = Icons.Default.LocationOn)

            OutlinedTextField(value = label, onValueChange = { label = it.uppercase() }, label = { Text("Мітка") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) }, modifier = Modifier.fillMaxWidth())

            StatusTextField(
                value = address, onValue_Change = { address = it; addressStatus = "unchanged" }, label = "Адреса", status = addressStatus, onStatusChange = { addressStatus = it }, icon = Icons.Default.Map,
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            if (address.isNotBlank()) {
                                val cleanShop = cleanTextForCopy(shopName)
                                val cleanFirstName = cleanTextForCopy(names.firstOrNull() ?: "")
                                val pinLabel = listOfNotNull(cleanShop.ifBlank { null }, cleanFirstName.ifBlank { null }).joinToString(", ") + if (label.isNotBlank()) " ($label)" else ""
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Client Info", pinLabel))
                                scope.launch { snackbarHostState.showSnackbar("Інформацію скопійовано") }
                                val gmmUri = if (latitude != null && longitude != null) Uri.parse("geo:0,0?q=$latitude,$longitude(${Uri.encode(pinLabel)})&z=20") else Uri.parse("geo:0,0?q=${Uri.encode(address)}(${Uri.encode(pinLabel)})&z=20")
                                context.startActivity(Intent(Intent.ACTION_VIEW, gmmUri).apply { setPackage("com.google.android.apps.maps") })
                            }
                        }) { Icon(Icons.Default.PushPin, "Створити мітку", tint = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = { locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Icon(Icons.Default.MyLocation, "Моя локація") }
                        IconButton(onClick = { showMapPicker = true }) { Icon(Icons.Default.AddLocationAlt, "Вибрати на карті") }
                    }
                }
            )

            // Phones section
            Column(modifier = Modifier.border(2.dp, if(phonesStatus=="changed") Color(0xFF4CAF50) else Color(0xFFFF9800), RoundedCornerShape(4.dp)).padding(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Телефони", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StatusIndicator(status = phonesStatus, onToggle = { phonesStatus = it }, label = "Телефони")
                }
                phones.forEachIndexed { index, phone ->
                    OutlinedTextField(value = phone, onValueChange = { nv -> phones = phones.toMutableList().also { it[index] = nv } }, label = { Text("Телефон ${index + 1}") }, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { phones = phones + "" }) { Text("Додати ще один номер") }
            }

            // Notes section
            Column(modifier = Modifier.border(2.dp, if(notesStatus=="changed") Color(0xFF4CAF50) else Color(0xFFFF9800), RoundedCornerShape(4.dp)).padding(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Примітки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StatusIndicator(status = notesStatus, onToggle = { notesStatus = it }, label = "Примітки")
                }
                OutlinedTextField(value = newNoteText, onValueChange = { newNoteText = it }, label = { Text("Нова замітка") }, trailingIcon = {
                    IconButton(onClick = { if (clientId != null && newNoteText.isNotBlank()) { viewModel.addNote(clientId, newNoteText); newNoteText = "" } }) { Icon(Icons.Default.Save, null) }
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun MapPickerDialog(
    initialCity: String,
    initialAddress: String,
    onDismiss: () -> Unit,
    onAddressSelected: (String, String, Double, Double) -> Unit,
    viewModel: ClientViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kyiv = LatLng(50.4501, 30.5234)
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(kyiv, 19f) }
    var currentCity by remember { mutableStateOf("") }
    var currentStreet by remember { mutableStateOf("Завантаження...") }
    LaunchedEffect(initialCity, initialAddress) {
        val fullAddr = listOf(initialCity, initialAddress).filter { it.isNotBlank() }.joinToString(", ")
        if (fullAddr.isNotBlank()) {
            try {
                val addresses = Geocoder(context, Locale.getDefault()).getFromLocationName(fullAddr, 1)
                if (!addresses.isNullOrEmpty()) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(addresses[0].latitude, addresses[0].longitude), 19f)
                }
            } catch (e: Exception) {}
        }
    }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val center = cameraPositionState.position.target
            viewModel.getAddressFromLocation(center.latitude, center.longitude) { cityVal, street ->
                currentCity = cityVal ?: ""; currentStreet = street ?: "Не вдалося визначити адресу"
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState, uiSettings = MapUiSettings(zoomControlsEnabled = false), contentPadding = PaddingValues(bottom = 160.dp))
                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.align(Alignment.Center).padding(bottom = 196.dp).size(48.dp), tint = Color.Red)
                Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FloatingActionButton(onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn(), 400) } }, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp)) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                    FloatingActionButton(onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut(), 400) } }, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp)) { Text("-", style = MaterialTheme.typography.headlineSmall) }
                }
                Card(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = if (currentCity.isNotBlank()) "$currentCity, $currentStreet" else currentStreet, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Скасувати") }
                            Button(onClick = { onAddressSelected(currentCity, currentStreet, cameraPositionState.position.target.latitude, cameraPositionState.position.target.longitude) }, modifier = Modifier.weight(1f), enabled = currentStreet != "Завантаження..." && currentStreet != "Не вдалося визначити адресу") { Text("Вибрати") }
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusTextField(value: String, onValue_Change: (String) -> Unit, label: String, status: String, onStatusChange: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, trailingIcon: @Composable (() -> Unit)? = null) {
    var showDialog by remember { mutableStateOf(false) }
    val borderColor = if (status == "changed") Color(0xFF4CAF50) else Color(0xFFFF9800)
    if (showDialog) {
        val nextStatus = if (status == "changed") "unchanged" else "changed"
        AlertDialog(onDismissRequest = { showDialog = false }, title = { Text("$status змінити на $nextStatus") }, confirmButton = { TextButton(onClick = { onStatusChange(nextStatus); showDialog = false }) { Text(nextStatus) } }, dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Скасувати") } })
    }
    Box(modifier = modifier.fillMaxWidth().border(2.dp, borderColor, RoundedCornerShape(4.dp)).combinedClickable(onClick = {}, onLongClick = { showDialog = true })) {
        OutlinedTextField(value = value, onValueChange = onValue_Change, label = { Text(label) }, leadingIcon = { Icon(icon, null) }, trailingIcon = trailingIcon, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusIndicator(status: String, onToggle: (String) -> Unit, label: String) {
    var showDialog by remember { mutableStateOf(false) }
    val color = if (status == "changed") Color(0xFF4CAF50) else Color(0xFFFF9800)
    if (showDialog) {
        val nextStatus = if (status == "changed") "unchanged" else "changed"
        AlertDialog(onDismissRequest = { showDialog = false }, title = { Text("$status змінити на $nextStatus для поля $label") }, confirmButton = { TextButton(onClick = { onToggle(nextStatus); showDialog = false }) { Text(nextStatus) } }, dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Скасувати") } })
    }
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { showDialog = true })) {
        Text(text = status, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
    }
}

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
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

@OptIn(ExperimentalMaterial3Api::class)
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
    
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var phones by remember { mutableStateOf(listOf("")) }
    var shopPhotoUri by remember { mutableStateOf<String?>(null) }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var label by remember { mutableStateOf("") }
    var newNoteText by remember { mutableStateOf("") }
    
    // Initial values to detect changes
    var initialFirstName by remember { mutableStateOf("") }
    var initialLastName by remember { mutableStateOf("") }
    var initialShopName by remember { mutableStateOf("") }
    var initialPhones by remember { mutableStateOf(listOf("")) }
    var initialShopPhotoUri by remember { mutableStateOf<String?>(null) }
    var initialAddress by remember { mutableStateOf("") }
    var initialLabel by remember { mutableStateOf("") }
    
    val hasChanges by remember {
        derivedStateOf {
            firstName != initialFirstName ||
            lastName != initialLastName ||
            shopName != initialShopName ||
            phones != initialPhones ||
            shopPhotoUri != initialShopPhotoUri ||
            address != initialAddress ||
            label != initialLabel
        }
    }
    
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    
    val performSave = {
        viewModel.addClient(
            id = clientId,
            firstName = firstName,
            lastName = lastName,
            middleName = "",
            shopName = shopName,
            phones = phones,
            shopPhotoUri = shopPhotoUri,
            address = address,
            lat = latitude,
            lon = longitude,
            label = label
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
    
    BackHandler(enabled = true) {
        requestBack()
    }
    
    var showAddressOverwriteDialog by remember { mutableStateOf(false) }
    var pendingAddress by remember { mutableStateOf("") }
    var pendingLat by remember { mutableStateOf<Double?>(null) }
    var pendingLon by remember { mutableStateOf<Double?>(null) }
    
    var showMapPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var clientToDelete by remember { mutableStateOf<ClientWithDetails?>(null) }
    var showPhotoFullScreen by remember { mutableStateOf(false) }
    
    val lastNameFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        if (clientId == null) {
            lastNameFocusRequester.requestFocus()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            shopPhotoUri = uri.toString()
            showPhotoFullScreen = false
        }
    }

    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraTempUri != null) {
            shopPhotoUri = cameraTempUri.toString()
            showPhotoFullScreen = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.getCurrentLocationAddress { addr, lat, lon ->
                addr?.let {
                    if (address.isBlank()) {
                        address = it
                        latitude = lat
                        longitude = lon
                    } else {
                        pendingAddress = it
                        pendingLat = lat
                        pendingLon = lon
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
                    address = pendingAddress
                    latitude = pendingLat
                    longitude = pendingLon
                    showAddressOverwriteDialog = false
                }) {
                    Text("Так")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddressOverwriteDialog = false }) {
                    Text("Ні")
                }
            }
        )
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Зберегти зміни?") },
            text = { Text("У вас є незбережені зміни. Ви бажаєте зберегти їх перед виходом?") },
            confirmButton = {
                TextButton(onClick = performSave) {
                    Text("Зберегти")
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text("Вийти без збереження")
                }
            }
        )
    }

    if (showMapPicker) {
        MapPickerDialog(
            initialAddress = address,
            onDismiss = { showMapPicker = false },
            onAddressSelected = { selectedAddress, lat, lon ->
                if (address.isBlank()) {
                    address = selectedAddress
                    latitude = lat
                    longitude = lon
                } else {
                    pendingAddress = selectedAddress
                    pendingLat = lat
                    pendingLon = lon
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
            text = { Text("Ви впевнені, що хочете видалити цю картку? Цю дію неможливо буде скасувати.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clientToDelete?.let { viewModel.deleteClient(it) }
                        showDeleteConfirmDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Видалити")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }

    if (showPhotoFullScreen && shopPhotoUri != null) {
        FullScreenPhotoDialog(
            photoUri = shopPhotoUri!!,
            onDismiss = { showPhotoFullScreen = false },
            onChange = { photoPickerLauncher.launch("image/*") },
            onCamera = {
                val uri = viewModel.getPhotoUri()
                cameraTempUri = uri
                cameraLauncher.launch(uri)
            }
        )
    }

    LaunchedEffect(clientId) {
        if (clientId != null) {
            val details = viewModel.getClient(clientId)
            details?.let {
                clientToDelete = it
                firstName = it.client.firstName
                lastName = it.client.lastName
                shopName = it.client.shopName
                phones = it.phones.map { p -> p.phoneNumber }.ifEmpty { listOf("") }
                shopPhotoUri = it.client.shopPhotoUri
                address = it.client.addressManual ?: ""
                latitude = it.client.latitude
                longitude = it.client.longitude
                label = it.client.label ?: ""
                
                // Set initial values
                initialFirstName = firstName
                initialLastName = lastName
                initialShopName = shopName
                initialPhones = phones
                initialShopPhotoUri = shopPhotoUri
                initialAddress = address
                initialLabel = label
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (clientId == null) "Новий Клієнт" else "Редагувати") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (clientId != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Видалити",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = performSave) {
                        Icon(Icons.Default.Save, contentDescription = "Зберегти")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { 
                        if (shopPhotoUri != null) {
                            showPhotoFullScreen = true
                        } else {
                            photoPickerLauncher.launch("image/*")
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (shopPhotoUri != null) {
                    AsyncImage(
                        model = shopPhotoUri,
                        contentDescription = "Фото магазину",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("Додати фото магазину")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Прізвище") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(lastNameFocusRequester)
            )
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("Ім'я") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = shopName,
                onValueChange = { shopName = it.replaceFirstChar { char -> char.uppercase() } },
                label = { Text("Назва магазину") },
                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it.uppercase() },
                label = { Text("Мітка") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Адреса") },
                leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            if (address.isNotBlank()) {
                                val pinLabel = "$shopName, $lastName $firstName ($label)"
                                
                                // Copy to clipboard
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Client Info", pinLabel)
                                clipboard.setPrimaryClip(clip)
                                
                                scope.launch {
                                    snackbarHostState.showSnackbar("Інформацію скопійовано")
                                }

                                // Open Google Maps with high zoom
                                val gmmIntentUri = if (latitude != null && longitude != null) {
                                    Uri.parse("geo:0,0?q=${latitude},${longitude}(${Uri.encode(pinLabel)})&z=20")
                                } else {
                                    Uri.parse("geo:0,0?q=${Uri.encode(address)}(${Uri.encode(pinLabel)})&z=20")
                                }
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                context.startActivity(mapIntent)
                            }
                        }) {
                            Icon(Icons.Default.PushPin, contentDescription = "Створити мітку", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Моя локація")
                        }
                        IconButton(onClick = { showMapPicker = true }) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = "Вибрати на карті")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Телефони", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            phones.forEachIndexed { index, phone ->
                OutlinedTextField(
                    value = phone,
                    onValueChange = { newValue ->
                        if (newValue.contains("\n")) {
                            val lines = newValue.split("\n").filter { it.isNotBlank() }
                            val newList = phones.toMutableList()
                            newList.removeAt(index)
                            newList.addAll(index, lines)
                            phones = newList
                        } else {
                            val newList = phones.toMutableList()
                            newList[index] = newValue
                            phones = newList
                        }
                    },
                    label = { Text("Телефон ${index + 1}") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(onClick = { phones = phones + "" }) {
                Text("Додати ще один номер")
            }

            HorizontalDivider()
            Text("Примітки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = newNoteText,
                onValueChange = { newNoteText = it },
                label = { Text("Нова замітка") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        if (clientId != null && newNoteText.isNotBlank()) {
                            viewModel.addNote(clientId, newNoteText)
                            newNoteText = ""
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FullScreenPhotoDialog(
    photoUri: String,
    onDismiss: () -> Unit,
    onChange: () -> Unit,
    onCamera: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                    ) {
                        Text("Назад")
                    }
                    Button(
                        onClick = onChange,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Змінити")
                    }
                    Button(
                        onClick = onCamera,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Камера")
                    }
                }
            }
        }
    }
}

@Composable
fun MapPickerDialog(
    initialAddress: String,
    onDismiss: () -> Unit,
    onAddressSelected: (String, Double, Double) -> Unit,
    viewModel: ClientViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kyiv = LatLng(50.4501, 30.5234)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(kyiv, 19f)
    }
    
    var currentAddress by remember { mutableStateOf("Завантаження...") }

    // Center map on initial address if provided
    LaunchedEffect(initialAddress) {
        if (initialAddress.isNotBlank()) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(initialAddress, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(
                        LatLng(addr.latitude, addr.longitude), 19f
                    )
                }
            } catch (e: Exception) {}
        }
    }

    // Update address when map stops moving
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val center = cameraPositionState.position.target
            viewModel.getAddressFromLocation(center.latitude, center.longitude) { addr ->
                currentAddress = addr ?: "Не вдалося визначити адресу"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false),
                    contentPadding = PaddingValues(bottom = 160.dp)
                )

                // Fixed Center Marker
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 196.dp) // Adjusted for contentPadding
                        .size(48.dp),
                    tint = Color.Red
                )

                // Zoom Buttons
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.zoomIn(),
                                    400
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.zoomOut(),
                                    400
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("-", style = MaterialTheme.typography.headlineSmall)
                    }
                }

                // Bottom Panel
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentAddress,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Скасувати")
                            }
                            Button(
                                onClick = { 
                                    val target = cameraPositionState.position.target
                                    onAddressSelected(currentAddress, target.latitude, target.longitude) 
                                },
                                modifier = Modifier.weight(1f),
                                enabled = currentAddress != "Завантаження..." && currentAddress != "Не вдалося визначити адресу"
                            ) {
                                Text("Вибрати")
                            }
                        }
                    }
                }

                // Top Back Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        }
    }
}

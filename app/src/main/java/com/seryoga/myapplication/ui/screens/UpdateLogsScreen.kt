package com.seryoga.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seryoga.myapplication.data.UpdateLogEntry
import com.seryoga.myapplication.data.UpdateLogSession
import com.seryoga.myapplication.ui.ClientViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateLogsScreen(
    viewModel: ClientViewModel,
    onBack: () -> Unit
) {
    val sessions by viewModel.logSessions.collectAsState(initial = emptyList())
    var selectedSessionId by remember { mutableStateOf<Long?>(null) }
    
    val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (selectedSessionId == null) "Журнали оновлень" else "Деталі оновлення",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedSessionId != null) selectedSessionId = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (sessions.isEmpty()) {
                EmptyLogsPlaceholder()
            } else if (selectedSessionId == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions) { session ->
                        SessionItem(
                            onClick = { selectedSessionId = session.id },
                            formattedDate = dateFormat.format(Date(session.timestamp))
                        )
                    }
                }
            } else {
                LogDetailsView(
                    sessionId = selectedSessionId!!,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun SessionItem(
    onClick: () -> Unit,
    formattedDate: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.History, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Переглянути зміни",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogDetailsView(
    sessionId: Long,
    viewModel: ClientViewModel
) {
    var entries by remember { mutableStateOf<List<UpdateLogEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionId) {
        entries = viewModel.getLogEntries(sessionId)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(entries) { entry ->
            LogEntryCard(
                entry = entry,
                onRevert = {
                    viewModel.revertChange(entry) {
                        scope.launch { entries = viewModel.getLogEntries(sessionId) }
                    }
                },
                onUnrevert = {
                    viewModel.unrevertChange(entry) {
                        scope.launch { entries = viewModel.getLogEntries(sessionId) }
                    }
                }
            )
        }
    }
}

@Composable
fun LogEntryCard(
    entry: UpdateLogEntry,
    onRevert: () -> Unit,
    onUnrevert: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isReverted) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) 
                else MaterialTheme.colorScheme.surface
        ),
        border = if (entry.isReverted) null else CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = CircleShape
                ) {
                    Text(
                        text = entry.clientLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.clientNameSnapshot,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = translateFieldName(entry.fieldName).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Value comparison row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("БУЛО", style = MaterialTheme.typography.labelExtraSmall, color = Color.Gray)
                    Text(
                        text = entry.oldValue.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.isReverted) MaterialTheme.colorScheme.onSurface else Color(0xFFE57373)
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, 
                    null, 
                    modifier = Modifier.padding(horizontal = 8.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("СТАЛО", style = MaterialTheme.typography.labelExtraSmall, color = Color.Gray)
                    Text(
                        text = entry.newValue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.isReverted) Color.Gray else Color(0xFF81C784),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!entry.isReverted) {
                Button(
                    onClick = onRevert,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Скасувати та заблокувати", fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick = onUnrevert,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("Відновити зміни", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyLogsPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.History, 
            null, 
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Журналів поки немає",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

val Typography.labelExtraSmall: androidx.compose.ui.text.TextStyle
    get() = labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium)

fun translateFieldName(name: String): String {
    return when(name) {
        "shopName" -> "Назва магазину"
        "city" -> "Населений пункт"
        "address" -> "Адреса"
        "phones" -> "Телефони"
        "notes" -> "Примітки"
        "names" -> "Імена"
        else -> name
    }
}

package com.seryoga.myapplication.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// 1. Спрощена модель даних Client
data class SimpleClient(
    val id: Long,
    val name: String,
    val shopName: String,
    val city: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderableClientListExample(
    modifier: Modifier = Modifier
) {
    // 1. Джерело даних: Список клієнтів з підтримкою модифікації в реальному часі
    val clientList = remember {
        mutableStateListOf(
            SimpleClient(1, "Петров Степан Назарович", "БудМаркет", "Київ"),
            SimpleClient(2, "Сидоренко Іван Васильович", "Продукти", "Одеса"),
            SimpleClient(3, "Коваленко Марія Іванівна", "Мій Дім", "Львів"),
            SimpleClient(4, "Бондаренко Олексій", "АвтоЗапчастини", "Харків"),
            SimpleClient(5, "Шевченко Ольга", "Квіти та Декор", "Дніпро")
        )
    }

    val lazyListState = rememberLazyListState()

    // 1. Управління станом бібліотеки sh.calvin.reorderable та логіка переміщення onMove
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Миттєве оновлення базового списку під час перетягування
        clientList.add(to.index, clientList.removeAt(from.index))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Список клієнтів (Drag & Drop)", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 2. Унікальні стабільні ключі key = { client.id }
            items(clientList, key = { client -> client.id }) { client ->
                // 2. Модифікатор ReorderableItem для кожного елемента LazyColumn
                ReorderableItem(state = reorderableState, key = client.id) { isDragging ->
                    // 3. Візуальний відгук та анімації під час перетягування
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 12.dp else 2.dp,
                        label = "elevationAnimation"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.05f else 1f,
                        label = "scaleAnimation"
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 100f else 0f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDragging) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = client.shopName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${client.name} • ${client.city}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 2. Точка активації (Drag Handle): Модифікатор .draggableHandle() навішано
                            // виключно на іконку зачепу, а НЕ на всю площу картки
                            IconButton(
                                onClick = { },
                                modifier = Modifier
                                    .size(40.dp)
                                    .draggableHandle() // Модифікатор зачепу бібліотеки
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Перетягнути елемент",
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NotificationItem
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminNotificationsScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val notifications by viewModel.allNotifications.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var showSendDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Broadcast Announcements",
                subtitle = "${notifications.size} Announcements Sent",
                onBack = { viewModel.navigateBack() },
                isOnline = isOnline
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showSendDialog = true },
                icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                text = { Text("Send Broadcast") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OnlineStatusBanner(isOnline = isOnline)

            if (notifications.isEmpty()) {
                EmptyStateView(
                    title = "No Announcements Yet",
                    message = "Tap 'Send Broadcast' to dispatch news, holiday updates, or exam alerts to all students.",
                    icon = Icons.Default.Notifications
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = item.targetAudience,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(item.message, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Posted: ${item.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    IconButton(onClick = { viewModel.deleteNotification(item) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSendDialog) {
        var title by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var targetAudience by remember { mutableStateOf("All Students") }

        AlertDialog(
            onDismissRequest = { showSendDialog = false },
            title = { Text("Compose Announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Message Body *") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = targetAudience == "All Students",
                            onClick = { targetAudience = "All Students" },
                            label = { Text("All Students") }
                        )
                        FilterChip(
                            selected = targetAudience == "Class 10",
                            onClick = { targetAudience = "Class 10" },
                            label = { Text("Class 10") }
                        )
                        FilterChip(
                            selected = targetAudience == "Class 12",
                            onClick = { targetAudience = "Class 12" },
                            label = { Text("Class 12") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isBlank() || message.isBlank()) return@Button
                        viewModel.sendNotification(title, message, targetAudience) {
                            showSendDialog = false
                        }
                    }
                ) {
                    Text("Broadcast Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

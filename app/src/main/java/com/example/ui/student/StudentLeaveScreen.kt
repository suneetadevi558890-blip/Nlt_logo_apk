package com.example.ui.student

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
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentLeaveScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val studentLeaves by viewModel.currentStudentLeaves.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var showApplyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Apply for Leave",
                subtitle = "${studentLeaves.size} Total Applications",
                onBack = { viewModel.navigateBack() },
                isOnline = isOnline
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showApplyDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Leave Request") },
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

            if (studentLeaves.isEmpty()) {
                EmptyStateView(
                    title = "No Leave Requests",
                    message = "Tap 'New Leave Request' to submit an application to the administration.",
                    icon = Icons.Default.EventBusy
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(studentLeaves, key = { it.id }) { leave ->
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
                                    Text(
                                        "${leave.startDate} to ${leave.endDate}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Surface(
                                        color = when (leave.status) {
                                            "Approved" -> Color(0xFFE8F5E9)
                                            "Rejected" -> Color(0xFFFFEBEE)
                                            else -> Color(0xFFFFF3E0)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = leave.status.uppercase(),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = when (leave.status) {
                                                "Approved" -> Color(0xFF2E7D32)
                                                "Rejected" -> Color(0xFFC62828)
                                                else -> Color(0xFFE65100)
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Reason: ${leave.reason}", style = MaterialTheme.typography.bodyMedium)

                                if (leave.adminNote.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Admin Note: ${leave.adminNote}",
                                            modifier = Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApplyDialog) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        var startDate by remember { mutableStateOf(todayStr) }
        var endDate by remember { mutableStateOf(todayStr) }
        var reason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showApplyDialog = false },
            title = { Text("Submit Leave Application") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("End Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason for Leave *") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reason.isBlank()) return@Button
                        viewModel.applyLeave(startDate, endDate, reason) {
                            showApplyDialog = false
                        }
                    }
                ) {
                    Text("Submit to Admin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

package com.example.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.example.data.model.LeaveRequest
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLeaveScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val leaves by viewModel.allLeaveRequests.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Pending, 1 = Reviewed

    val pendingLeaves = leaves.filter { it.status == "Pending" }
    val reviewedLeaves = leaves.filter { it.status != "Pending" }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Leave Management",
                subtitle = "${pendingLeaves.size} Pending Review",
                onBack = { viewModel.navigateBack() },
                isOnline = isOnline
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OnlineStatusBanner(isOnline = isOnline)

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Pending (${pendingLeaves.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Past Applications (${reviewedLeaves.size})", fontWeight = FontWeight.Bold) }
                )
            }

            val listToShow = if (selectedTab == 0) pendingLeaves else reviewedLeaves

            if (listToShow.isEmpty()) {
                EmptyStateView(
                    title = if (selectedTab == 0) "No Pending Requests" else "No Past Applications",
                    message = if (selectedTab == 0) "All student leave applications have been reviewed." else "Reviewed leaves will appear here.",
                    icon = Icons.Default.EventNote
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(listToShow, key = { it.id }) { leave ->
                        LeaveAdminCard(
                            leave = leave,
                            onReview = { approve, note ->
                                viewModel.reviewLeave(leave.id, approve, note, "Admin")
                            },
                            onDelete = {
                                viewModel.adminDeleteLeave(leave.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LeaveAdminCard(
    leave: LeaveRequest,
    onReview: (Boolean, String) -> Unit,
    onDelete: () -> Unit
) {
    var adminNote by remember { mutableStateOf("") }

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
                Column {
                    Text(leave.studentName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("ID: ${leave.studentIdCode} • ${leave.className}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Duration: ${leave.startDate} to ${leave.endDate}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Reason: ${leave.reason}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (leave.status == "Pending") {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = adminNote,
                    onValueChange = { adminNote = it },
                    label = { Text("Admin Remarks (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onReview(false, adminNote) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reject")
                    }
                    Button(
                        onClick = { onReview(true, adminNote) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Approve")
                    }
                }
            } else {
                if (leave.adminNote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Admin Note: ${leave.adminNote}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

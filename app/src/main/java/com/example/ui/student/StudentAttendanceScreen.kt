package com.example.ui.student

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAttendanceScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val studentAttendance by viewModel.currentStudentAttendance.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val totalDays = studentAttendance.size
    val presentDays = studentAttendance.count { it.isPresent }
    val absentDays = totalDays - presentDays
    val pct = if (totalDays > 0) (presentDays * 100 / totalDays) else 100

    Scaffold(
        topBar = {
            AppTopBar(
                title = "My Attendance Record",
                subtitle = "$pct% Cumulative Attendance",
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

            // Attendance KPI Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL CLASSES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalDays Days", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }

                    Column {
                        Text("PRESENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$presentDays", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF2E7D32))
                    }

                    Column {
                        Text("ABSENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$absentDays", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFC62828))
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (pct >= 75) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$pct%",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = if (pct >= 75) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }

            if (studentAttendance.isEmpty()) {
                EmptyStateView(
                    title = "No Attendance Marked Yet",
                    message = "Your attendance marked daily by Admin will appear here.",
                    icon = Icons.Default.EventAvailable
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(studentAttendance, key = { it.id }) { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (record.isPresent) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (record.isPresent) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (record.isPresent) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(record.date, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (record.remarks.isNotBlank()) {
                                        Text(record.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(
                                    color = if (record.isPresent) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (record.isPresent) "PRESENT" else "ABSENT",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (record.isPresent) Color(0xFF2E7D32) else Color(0xFFC62828)
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

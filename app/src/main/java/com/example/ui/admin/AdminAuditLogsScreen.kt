package com.example.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuditLogsScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val auditLogs by viewModel.allAuditLogs.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Security & Audit Logs",
                subtitle = "${auditLogs.size} Real-time Event Trails",
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

            if (auditLogs.isEmpty()) {
                EmptyStateView(
                    title = "No Audit Events Logged",
                    message = "Administrative events, student modifications, and logins are logged here in real time.",
                    icon = Icons.Default.Security
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(auditLogs, key = { it.id }) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(log.action, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(log.dateTimeStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(log.details, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Performed By: ${log.performedBy}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

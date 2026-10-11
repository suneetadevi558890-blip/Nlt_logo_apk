package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.OnlineStatusBanner
import com.example.ui.components.StatCard
import com.example.ui.navigation.Screen

data class AdminMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val screen: Screen,
    val badge: String? = null,
    val badgeColor: Color = Color(0xFFFF5252),
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(viewModel: MainViewModel) {
    val adminProfile by viewModel.adminProfile.collectAsState()
    val students by viewModel.allStudents.collectAsState()
    val fees by viewModel.allFees.collectAsState()
    val attendance by viewModel.allAttendance.collectAsState()
    val leaves by viewModel.allLeaveRequests.collectAsState()
    val videos by viewModel.allVideos.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val pendingFeesCount = fees.count { it.status == "Pending" || it.status == "Pending Verification" }
    val pendingLeavesCount = leaves.count { it.status == "Pending" }
    val freeVideosCount = videos.count { !it.isPaid }
    val paidVideosCount = videos.count { it.isPaid }

    val menuItems = listOf(
        AdminMenuItem(
            title = "Video Lectures",
            subtitle = "$freeVideosCount Free • $paidVideosCount Premium",
            icon = Icons.Default.PlayCircle,
            screen = Screen.AdminVideos,
            color = Color(0xFFE91E63)
        ),
        AdminMenuItem(
            title = "Students Directory",
            subtitle = "${students.size} Active Students",
            icon = Icons.Default.People,
            screen = Screen.AdminStudents,
            color = Color(0xFF1976D2)
        ),
        AdminMenuItem(
            title = "Attendance Register",
            subtitle = "Daily Register & Reports",
            icon = Icons.Default.EventAvailable,
            screen = Screen.AdminAttendance,
            color = Color(0xFF388E3C)
        ),
        AdminMenuItem(
            title = "Fees & Payments",
            subtitle = if (pendingFeesCount > 0) "$pendingFeesCount Pending" else "All Cleared",
            icon = Icons.Default.CurrencyRupee,
            screen = Screen.AdminFees,
            badge = if (pendingFeesCount > 0) "$pendingFeesCount" else null,
            color = Color(0xFFF57C00)
        ),
        AdminMenuItem(
            title = "UPI & Payment QR",
            subtitle = adminProfile?.upiId ?: "Configure UPI ID",
            icon = Icons.Default.QrCode,
            screen = Screen.AdminUpi,
            color = Color(0xFF7B1FA2)
        ),
        AdminMenuItem(
            title = "Leave Requests",
            subtitle = if (pendingLeavesCount > 0) "$pendingLeavesCount Pending Approval" else "No Pending",
            icon = Icons.Default.AssignmentLate,
            screen = Screen.AdminLeave,
            badge = if (pendingLeavesCount > 0) "$pendingLeavesCount" else null,
            color = Color(0xFF0097A7)
        ),
        AdminMenuItem(
            title = "Broadcast Messages",
            subtitle = "Announcements to All",
            icon = Icons.Default.Notifications,
            screen = Screen.AdminNotifications,
            color = Color(0xFF5D4037)
        ),
        AdminMenuItem(
            title = "Audit & Security",
            subtitle = "Real-time Access Logs",
            icon = Icons.Default.Security,
            screen = Screen.AdminAuditLogs,
            color = Color(0xFF455A64)
        )
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = adminProfile?.instituteName ?: "Neol Tube Academy",
                subtitle = "Admin Console • Production Cloud",
                onLogout = { viewModel.logout() },
                isOnline = isOnline,
                actions = {
                    val isSyncing by viewModel.isSyncing.collectAsState()
                    IconButton(
                        onClick = { viewModel.syncAllFromCloud() },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Sync Cloud Data",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = { showProfileDialog = true }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile Settings", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            OnlineStatusBanner(isOnline = isOnline)

            // Welcome banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome, ${adminProfile?.name ?: "Administrator"}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Centralized Firebase Online Management Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Quick Stats Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "STUDENTS",
                    value = "${students.size}",
                    icon = Icons.Default.School,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(Screen.AdminStudents) }
                )
                StatCard(
                    title = "VIDEOS",
                    value = "${videos.size}",
                    subtitle = "$freeVideosCount Free / $paidVideosCount Paid",
                    icon = Icons.Default.VideoLibrary,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(Screen.AdminVideos) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "PENDING FEES",
                    value = "$pendingFeesCount",
                    icon = Icons.Default.ReceiptLong,
                    containerColor = if (pendingFeesCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (pendingFeesCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(Screen.AdminFees) }
                )
                StatCard(
                    title = "LEAVES",
                    value = "$pendingLeavesCount",
                    icon = Icons.Default.EventBusy,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(Screen.AdminLeave) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Modules Header
            Text(
                text = "MANAGEMENT CONSOLE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // Grid of Actions
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                menuItems.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.navigateTo(item.screen) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(item.color.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = null,
                                                tint = item.color,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        if (item.badge != null) {
                                            Badge(
                                                containerColor = item.badgeColor,
                                                contentColor = Color.White
                                            ) {
                                                Text(item.badge, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Admin Profile & Password Dialog
    if (showProfileDialog) {
        val currentProfile = adminProfile
        var name by remember { mutableStateOf(currentProfile?.name ?: "") }
        var instituteName by remember { mutableStateOf(currentProfile?.instituteName ?: "") }
        var mobile by remember { mutableStateOf(currentProfile?.mobile ?: "") }
        var email by remember { mutableStateOf(currentProfile?.email ?: "") }

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Administrator Settings") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Admin Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = instituteName,
                        onValueChange = { instituteName = it },
                        label = { Text("Institute Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Admin Mobile") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showProfileDialog = false
                            showPasswordDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LockReset, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Admin Password")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = (currentProfile ?: com.example.data.model.AdminProfile()).copy(
                            name = name,
                            instituteName = instituteName,
                            mobile = mobile,
                            email = email
                        )
                        viewModel.updateAdminProfile(updated)
                        showProfileDialog = false
                    }
                ) {
                    Text("Save & Sync Online")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Change Password Dialog
    if (showPasswordDialog) {
        var oldPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Change Admin Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Updating password will instantly synchronize across all devices connected to the Firebase backend.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = oldPass,
                        onValueChange = { oldPass = it; errorMsg = null },
                        label = { Text("Current Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it; errorMsg = null },
                        label = { Text("New Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it; errorMsg = null },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMsg != null) {
                        Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPass.length < 4) {
                            errorMsg = "Password must be at least 4 characters."
                            return@Button
                        }
                        if (newPass != confirmPass) {
                            errorMsg = "Passwords do not match."
                            return@Button
                        }
                        viewModel.changeAdminPassword(oldPass, newPass) { success, msg ->
                            if (success) {
                                showPasswordDialog = false
                            } else {
                                errorMsg = msg
                            }
                        }
                    }
                ) {
                    Text("Update Everywhere")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

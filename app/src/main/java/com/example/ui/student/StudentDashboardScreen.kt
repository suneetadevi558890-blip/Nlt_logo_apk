package com.example.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.ui.UserSession
import com.example.ui.components.AppTopBar
import com.example.ui.components.OnlineStatusBanner
import com.example.ui.components.StatCard
import com.example.ui.navigation.Screen

data class StudentMenuAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val screen: Screen,
    val badge: String? = null,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(viewModel: MainViewModel) {
    val session by viewModel.session.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val student = (session as? UserSession.StudentUser)?.student

    val studentFees by viewModel.currentStudentFees.collectAsState()
    val studentAttendance by viewModel.currentStudentAttendance.collectAsState()
    val studentLeaves by viewModel.currentStudentLeaves.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()
    val myPurchases by viewModel.currentStudentPurchases.collectAsState()

    val pendingFeesCount = studentFees.count { it.status == "Pending" || it.status == "Pending Verification" }
    val totalAttendanceDays = studentAttendance.size
    val presentDays = studentAttendance.count { it.isPresent }
    val attendancePct = if (totalAttendanceDays > 0) (presentDays * 100 / totalAttendanceDays) else 100

    val freeVideosCount = allVideos.count { !it.isPaid }
    val paidVideosCount = allVideos.count { it.isPaid }

    val menuActions = listOf(
        StudentMenuAction(
            title = "Video Learning Hub",
            subtitle = "$freeVideosCount Free • $paidVideosCount Premium",
            icon = Icons.Default.PlayCircleFilled,
            screen = Screen.StudentVideos,
            badge = "HOT",
            color = Color(0xFFE91E63)
        ),
        StudentMenuAction(
            title = "Fees & Receipts",
            subtitle = if (pendingFeesCount > 0) "$pendingFeesCount Dues" else "All Paid",
            icon = Icons.Default.ReceiptLong,
            screen = Screen.StudentFees,
            badge = if (pendingFeesCount > 0) "$pendingFeesCount" else null,
            color = Color(0xFFF57C00)
        ),
        StudentMenuAction(
            title = "My Attendance",
            subtitle = "$attendancePct% Overall ($presentDays/$totalAttendanceDays Days)",
            icon = Icons.Default.EventAvailable,
            screen = Screen.StudentAttendance,
            color = Color(0xFF388E3C)
        ),
        StudentMenuAction(
            title = "Apply for Leave",
            subtitle = "${studentLeaves.size} Submitted Requests",
            icon = Icons.Default.EventBusy,
            screen = Screen.StudentLeave,
            color = Color(0xFF0097A7)
        ),
        StudentMenuAction(
            title = "Announcements",
            subtitle = "School Circulars & News",
            icon = Icons.Default.Notifications,
            screen = Screen.StudentNotifications,
            color = Color(0xFF5D4037)
        ),
        StudentMenuAction(
            title = "My Student Profile",
            subtitle = "View Bio & Change Password",
            icon = Icons.Default.AccountCircle,
            screen = Screen.StudentProfile,
            color = Color(0xFF1976D2)
        )
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Neol Tube Academy",
                subtitle = "Student Portal • Production Cloud",
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
                    IconButton(onClick = { viewModel.navigateTo(Screen.StudentProfile) }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onPrimary)
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

            // Greeting Card
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
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = student?.name?.take(1)?.uppercase() ?: "S",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hello, ${student?.name ?: "Student"}!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "ID: ${student?.studentIdCode ?: "NT-001"} • ${student?.className ?: "Class 10"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Roll No: ${student?.rollNumber ?: "01"} • Enrolled Online",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // PROMINENT HERO CARD: Video Learning Hub (Free & Paid)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { viewModel.navigateTo(Screen.StudentVideos) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = Color(0xFFE91E63),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "VIDEO PORTAL",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Watch Lectures & Courses",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$freeVideosCount Free Videos • $paidVideosCount Premium Courses • ${myPurchases.size} Enrolled",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Explore Video Hub",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFFF4081)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFFF4081), modifier = Modifier.size(16.dp))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE91E63)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "ATTENDANCE",
                    value = "$attendancePct%",
                    subtitle = "$presentDays of $totalAttendanceDays Present",
                    icon = Icons.Default.EventAvailable,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(Screen.StudentAttendance) }
                )
                StatCard(
                    title = "FEE DUES",
                    value = if (pendingFeesCount > 0) "$pendingFeesCount Dues" else "₹0",
                    subtitle = if (pendingFeesCount > 0) "Pending Payment" else "All Cleared",
                    icon = Icons.Default.CurrencyRupee,
                    containerColor = if (pendingFeesCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (pendingFeesCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(Screen.StudentFees) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Menu Items
            Text(
                text = "ACADEMIC SERVICES",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                menuActions.chunked(2).forEach { rowItems ->
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
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
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
                                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                                Text(item.badge, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
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
}

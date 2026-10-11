package com.example.ui.student

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.example.data.model.ProfileVisibilityConfig
import com.example.ui.MainViewModel
import com.example.ui.UserSession
import com.example.ui.components.AppTopBar
import com.example.ui.components.OnlineStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val session by viewModel.session.collectAsState()
    val student = (session as? UserSession.StudentUser)?.student
    val visibilityConfig by viewModel.visibilityConfig.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val config = visibilityConfig ?: ProfileVisibilityConfig()
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Student Profile",
                subtitle = "Academic & Personal Credentials",
                onBack = { viewModel.navigateBack() },
                isOnline = isOnline
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

            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar & Name Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (config.showPhoto) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = student?.name?.take(1)?.uppercase() ?: "S",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (config.showStudentName) {
                            Text(
                                text = student?.name ?: "Student",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (config.showStudentId) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Student ID: ${student?.studentIdCode ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Academic & Bio Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "PROFILE DETAILS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (config.showClass) {
                            ProfileDetailRow(Icons.Default.School, "Class / Batch", student?.className ?: "Class 10")
                        }
                        if (config.showRollNumber) {
                            ProfileDetailRow(Icons.Default.Numbers, "Roll Number", student?.rollNumber ?: "01")
                        }
                        if (config.showContact) {
                            ProfileDetailRow(Icons.Default.Phone, "Registered Mobile", student?.mobile ?: "")
                        }
                        if (config.showFatherName && !student?.fatherName.isNullOrBlank()) {
                            ProfileDetailRow(Icons.Default.Person, "Father's Name", student?.fatherName ?: "")
                        }
                        if (config.showMotherName && !student?.motherName.isNullOrBlank()) {
                            ProfileDetailRow(Icons.Default.PersonOutline, "Mother's Name", student?.motherName ?: "")
                        }
                        if (!student?.email.isNullOrBlank()) {
                            ProfileDetailRow(Icons.Default.Email, "Email Address", student?.email ?: "")
                        }
                        if (config.showAddress && !student?.address.isNullOrBlank()) {
                            ProfileDetailRow(Icons.Default.Home, "Residential Address", student?.address ?: "")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Security & Password Button
                Button(
                    onClick = { showChangePasswordDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.LockReset, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change Account Password")
                }
            }
        }
    }

    if (showChangePasswordDialog && student != null) {
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Your new password will synchronize online and work on every phone where you log in.",
                        style = MaterialTheme.typography.bodySmall
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
                        viewModel.changeStudentPassword(student.id, newPass) {
                            showChangePasswordDialog = false
                        }
                    }
                ) {
                    Text("Save Online")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProfileDetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

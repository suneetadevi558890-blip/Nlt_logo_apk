package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Student
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Admin, 1 = Student
    var mobileOrId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Family account selection dialog when multiple students match a phone number
    var familyStudents by remember { mutableStateOf<List<Student>>(emptyList()) }
    var showFamilyPicker by remember { mutableStateOf(false) }

    val isOnline by viewModel.isOnline.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsState()
    val registeredStudents by viewModel.allStudents.collectAsState()
    val adminProfile by viewModel.adminProfile.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
                    .padding(top = 40.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = "Neol Tube Logo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "NEOL TUBE",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "Centralized Academy & Video Learning Hub",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color(0xFF00E676) else Color(0xFFFF5252))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOnline) "Firebase Cloud Connected" else "Offline Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Role Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                errorMessage = null
                                mobileOrId = ""
                                password = ""
                            },
                            text = { Text("Admin Sign In", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                errorMessage = null
                                mobileOrId = ""
                                password = ""
                            },
                            text = { Text("Student Sign In", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.School, contentDescription = null) }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (selectedTab == 0) "Administrator Authentication" else "Student Portal Access",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedTab == 0)
                            "Sign in with Google Email, Registered Mobile, or Username"
                        else
                            "Sign in with Google Email, Student ID Code, or Registered Mobile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Identifier input (Google Email / Mobile / ID)
                    OutlinedTextField(
                        value = mobileOrId,
                        onValueChange = {
                            mobileOrId = it
                            errorMessage = null
                        },
                        label = { Text(if (selectedTab == 0) "Google Email / Mobile / Username" else "Google Email / Student ID / Mobile") },
                        placeholder = {
                            Text(if (selectedTab == 0) "admin@gmail.com or 9876543210" else "student@gmail.com or NT-2026-001")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (mobileOrId.contains("@")) Icons.Default.Email else if (selectedTab == 0) Icons.Default.Phone else Icons.Default.Badge,
                                contentDescription = null
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (mobileOrId.contains("@")) KeyboardType.Email else KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password input
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (mobileOrId.isNotBlank() && password.isNotBlank()) {
                                    performLogin(
                                        selectedTab,
                                        mobileOrId,
                                        password,
                                        viewModel,
                                        onLoading = { isLoading = it },
                                        onError = { errorMessage = it },
                                        onFamilySelect = {
                                            familyStudents = it
                                            showFamilyPicker = true
                                        }
                                    )
                                }
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Error text
                    AnimatedVisibility(visible = errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Login Button
                    Button(
                        onClick = {
                            if (mobileOrId.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter both credentials."
                                return@Button
                            }
                            performLogin(
                                selectedTab,
                                mobileOrId,
                                password,
                                viewModel,
                                onLoading = { isLoading = it },
                                onError = { errorMessage = it },
                                onFamilySelect = {
                                    familyStudents = it
                                    showFamilyPicker = true
                                }
                            )
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (selectedTab == 0) "Login to Admin Console" else "Login to Student Portal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick fill helpers
                    Text(
                        text = if (selectedTab == 0) "Quick Admin Login Credentials:" else "Registered Student Profiles (Tap to auto-fill):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (selectedTab == 0) {
                        val admMob = adminProfile?.mobile ?: "9876543210"
                        val admEmail = adminProfile?.email?.takeIf { it.isNotBlank() } ?: "suneetadevi558890@gmail.com"
                        val admPass = adminProfile?.passwordHash ?: "admin123"
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SuggestionChip(
                                    onClick = {
                                        mobileOrId = admEmail
                                        password = admPass
                                    },
                                    label = { Text("Google Email (${admEmail.substringBefore("@")})") },
                                    modifier = Modifier.weight(1f)
                                )
                                SuggestionChip(
                                    onClick = {
                                        mobileOrId = admMob
                                        password = admPass
                                    },
                                    label = { Text("Mobile ($admMob)") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SuggestionChip(
                                    onClick = {
                                        mobileOrId = "admin"
                                        password = admPass
                                    },
                                    label = { Text("Username (admin)") },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        val displayStudents = if (registeredStudents.isNotEmpty()) registeredStudents.take(3) else emptyList()
                        if (displayStudents.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                displayStudents.chunked(2).forEach { rowStudents ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowStudents.forEach { st ->
                                            val chipLabel = if (st.email.isNotBlank()) {
                                                "${st.name.substringBefore(" ")} (${st.email.substringBefore("@")})"
                                            } else {
                                                "${st.name.substringBefore(" ")} (${st.studentIdCode})"
                                            }
                                            SuggestionChip(
                                                onClick = {
                                                    mobileOrId = if (st.email.isNotBlank()) st.email else st.studentIdCode
                                                    password = st.password
                                                },
                                                label = { Text(chipLabel) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowStudents.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SuggestionChip(
                                    onClick = {
                                        mobileOrId = "NT-2026-001"
                                        password = "student@123"
                                    },
                                    label = { Text("Aarav (NT-001)") },
                                    modifier = Modifier.weight(1f)
                                )
                                SuggestionChip(
                                    onClick = {
                                        mobileOrId = "9876500002"
                                        password = "student@123"
                                    },
                                    label = { Text("Ananya (Mobile)") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-device Cloud Sync Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Cloud Sync",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Multi-Device Cloud Sync",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ) {
                            Text(
                                text = if (isOnline) "Cloud Active" else "Offline",
                                color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Using on another phone? Tap below to download all registered students, fees, admin password, and app data from Google Firebase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (registeredStudents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cloud Status: ${registeredStudents.size} students synchronized with Firebase.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!syncStatusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = syncStatusMessage ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.syncAllFromCloud() },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing from Google Firebase...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync All Data from Cloud")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security note
            Text(
                text = "Firebase Auth • Role Verified (Admin & Student) • Project: gen-lang-client-0076422639",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Family Member Picker Dialog (when 1 phone number has 2+ students)
    if (showFamilyPicker) {
        AlertDialog(
            onDismissRequest = { showFamilyPicker = false },
            title = { Text("Multiple Profiles Found") },
            text = {
                Column {
                    Text(
                        "Multiple students share this mobile number. Select your profile:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    familyStudents.forEach { st ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.selectStudentSession(st)
                                    showFamilyPicker = false
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = st.name.take(1),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(st.name, fontWeight = FontWeight.Bold)
                                    Text("${st.className} • ID: ${st.studentIdCode}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFamilyPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun performLogin(
    tab: Int,
    idOrMobile: String,
    pass: String,
    viewModel: MainViewModel,
    onLoading: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onFamilySelect: (List<Student>) -> Unit
) {
    onLoading(true)
    onError(null)
    if (tab == 0) {
        viewModel.loginAdmin(idOrMobile, pass) { success, msg ->
            onLoading(false)
            if (!success) onError(msg)
        }
    } else {
        viewModel.loginStudent(idOrMobile, pass) { success, students, msg ->
            onLoading(false)
            if (!success) {
                onError(msg)
            } else if (students.size > 1) {
                onFamilySelect(students)
            }
        }
    }
}

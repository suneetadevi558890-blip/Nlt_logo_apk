package com.example.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProfileVisibilityConfig
import com.example.data.model.Student
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStudentsScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val students by viewModel.allStudents.collectAsState()
    val visibilityConfig by viewModel.visibilityConfig.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var studentToEdit by remember { mutableStateOf<Student?>(null) }
    var studentForPasswordChange by remember { mutableStateOf<Student?>(null) }
    var showVisibilityDialog by remember { mutableStateOf(false) }

    val filteredStudents = students.filter { st ->
        st.name.contains(searchQuery, ignoreCase = true) ||
                st.studentIdCode.contains(searchQuery, ignoreCase = true) ||
                st.mobile.contains(searchQuery, ignoreCase = true) ||
                st.className.contains(searchQuery, ignoreCase = true) ||
                st.rollNumber.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Student Directory",
                subtitle = "${students.size} Enrolled • Cloud Synchronized",
                onBack = { viewModel.navigateBack() },
                isOnline = isOnline,
                actions = {
                    IconButton(onClick = { showVisibilityDialog = true }) {
                        Icon(Icons.Default.Visibility, contentDescription = "Profile Visibility Settings", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    studentToEdit = null
                    showAddDialog = true
                },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Add Student") },
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

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name, ID code, mobile, class...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            if (filteredStudents.isEmpty()) {
                EmptyStateView(
                    title = "No Students Found",
                    message = if (searchQuery.isNotEmpty()) "No students matched your search criteria." else "No students in the directory yet. Tap 'Add Student' to register.",
                    icon = Icons.Default.People
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredStudents, key = { it.id }) { student ->
                        StudentAdminCard(
                            student = student,
                            onEdit = {
                                studentToEdit = student
                                showAddDialog = true
                            },
                            onChangePassword = {
                                studentForPasswordChange = student
                            },
                            onDelete = {
                                viewModel.deleteStudent(student)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Student Dialog
    if (showAddDialog) {
        StudentFormDialog(
            existing = studentToEdit,
            onDismiss = { showAddDialog = false },
            onSave = { updatedStudent ->
                viewModel.saveStudent(updatedStudent) {
                    showAddDialog = false
                }
            }
        )
    }

    // Change Password Dialog
    if (studentForPasswordChange != null) {
        val target = studentForPasswordChange!!
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { studentForPasswordChange = null },
            title = { Text("Change Password for ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Updating password will update online immediately. The student will use this new password to sign in on their phone.",
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
                        viewModel.changeStudentPassword(target.id, newPass) {
                            studentForPasswordChange = null
                        }
                    }
                ) {
                    Text("Save Online")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentForPasswordChange = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Profile Visibility Config Dialog
    if (showVisibilityDialog) {
        val currentConfig = visibilityConfig ?: ProfileVisibilityConfig()
        var showPhoto by remember { mutableStateOf(currentConfig.showPhoto) }
        var showName by remember { mutableStateOf(currentConfig.showStudentName) }
        var showFather by remember { mutableStateOf(currentConfig.showFatherName) }
        var showMother by remember { mutableStateOf(currentConfig.showMotherName) }
        var showClass by remember { mutableStateOf(currentConfig.showClass) }
        var showRoll by remember { mutableStateOf(currentConfig.showRollNumber) }
        var showId by remember { mutableStateOf(currentConfig.showStudentId) }
        var showContact by remember { mutableStateOf(currentConfig.showContact) }
        var showAddress by remember { mutableStateOf(currentConfig.showAddress) }

        AlertDialog(
            onDismissRequest = { showVisibilityDialog = false },
            title = { Text("Student Profile Visibility Controls") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Control what fields students can see on their profile:", style = MaterialTheme.typography.bodySmall)
                    VisibilitySwitchRow("Student Photo", showPhoto) { showPhoto = it }
                    VisibilitySwitchRow("Student Name", showName) { showName = it }
                    VisibilitySwitchRow("Father's Name", showFather) { showFather = it }
                    VisibilitySwitchRow("Mother's Name", showMother) { showMother = it }
                    VisibilitySwitchRow("Class / Batch", showClass) { showClass = it }
                    VisibilitySwitchRow("Roll Number", showRoll) { showRoll = it }
                    VisibilitySwitchRow("Student ID Code", showId) { showId = it }
                    VisibilitySwitchRow("Contact Mobile", showContact) { showContact = it }
                    VisibilitySwitchRow("Address", showAddress) { showAddress = it }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = currentConfig.copy(
                            showPhoto = showPhoto,
                            showStudentName = showName,
                            showFatherName = showFather,
                            showMotherName = showMother,
                            showClass = showClass,
                            showRollNumber = showRoll,
                            showStudentId = showId,
                            showContact = showContact,
                            showAddress = showAddress
                        )
                        viewModel.saveVisibilityConfig(updated)
                        showVisibilityDialog = false
                    }
                ) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVisibilityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun VisibilitySwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun StudentAdminCard(
    student: Student,
    onEdit: () -> Unit,
    onChangePassword: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = student.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(student.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("ID: ${student.studentIdCode} • ${student.className}", style = MaterialTheme.typography.bodySmall)
                    Text("Mobile: ${student.mobile} • Roll: ${student.rollNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = student.className,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onChangePassword) {
                    Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Password")
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormDialog(
    existing: Student?,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var studentIdCode by remember { mutableStateOf(existing?.studentIdCode ?: "NT-2026-${(100..999).random()}") }
    var mobile by remember { mutableStateOf(existing?.mobile ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "student@123") }
    var className by remember { mutableStateOf(existing?.className ?: "Class 10") }
    var rollNumber by remember { mutableStateOf(existing?.rollNumber ?: "") }
    var fatherName by remember { mutableStateOf(existing?.fatherName ?: "") }
    var motherName by remember { mutableStateOf(existing?.motherName ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var familyGroupId by remember { mutableStateOf(existing?.familyGroupId ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Register New Student" else "Edit Student Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = studentIdCode,
                    onValueChange = { studentIdCode = it },
                    label = { Text("Student ID Code *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Google Email (Optional Login)") },
                    placeholder = { Text("student@gmail.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number (Login) *") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Login Password *") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = className,
                        onValueChange = { className = it },
                        label = { Text("Class / Batch") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rollNumber,
                        onValueChange = { rollNumber = it },
                        label = { Text("Roll No") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = fatherName,
                    onValueChange = { fatherName = it },
                    label = { Text("Father's Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = motherName,
                    onValueChange = { motherName = it },
                    label = { Text("Mother's Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Residential Address") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = familyGroupId,
                    onValueChange = { familyGroupId = it },
                    label = { Text("Family Group ID (For siblings)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || mobile.isBlank()) return@Button
                    val finalStudent = (existing ?: Student(
                        id = 0L,
                        studentIdCode = studentIdCode,
                        name = name,
                        mobile = mobile,
                        password = password
                    )).copy(
                        name = name,
                        studentIdCode = studentIdCode,
                        mobile = mobile,
                        password = password,
                        className = className,
                        rollNumber = rollNumber,
                        fatherName = fatherName,
                        motherName = motherName,
                        email = email,
                        address = address,
                        familyGroupId = familyGroupId
                    )
                    onSave(finalStudent)
                }
            ) {
                Text("Save Online")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

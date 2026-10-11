package com.example.ui.admin

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AdminProfile
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.OnlineStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUpiScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val adminProfile by viewModel.adminProfile.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Fee QR Code, 1 = App Icon, 2 = UPI Settings

    // UPI Settings state
    var upiId by remember(adminProfile) { mutableStateOf(adminProfile?.upiId ?: "neoltube@upi") }
    var payeeName by remember(adminProfile) { mutableStateOf(adminProfile?.upiPayeeName ?: "Neol Tube Academy") }
    var qrNote by remember(adminProfile) { mutableStateOf(adminProfile?.upiQrNote ?: "Student Fees & Video Courses") }

    // QR Image state
    var qrUrlInput by remember { mutableStateOf("") }
    var selectedQrUri by remember { mutableStateOf<Uri?>(null) }
    var showDeleteQrDialog by remember { mutableStateOf(false) }

    // App Icon state
    var iconUrlInput by remember { mutableStateOf("") }
    var selectedIconUri by remember { mutableStateOf<Uri?>(null) }
    var showDeleteIconDialog by remember { mutableStateOf(false) }

    var isUploading by remember { mutableStateOf(false) }
    var isSyncingRtdb by remember { mutableStateOf(false) }

    // Photo pickers
    val qrPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedQrUri = uri
            isUploading = true
            viewModel.updateAdminQrCode(imageUri = uri) {
                isUploading = false
            }
        }
    }

    val iconPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedIconUri = uri
            isUploading = true
            viewModel.updateAdminAppIcon(imageUri = uri) {
                isUploading = false
            }
        }
    }

    val currentQrPath = adminProfile?.qrImagePath ?: ""
    val currentAppIcon = adminProfile?.appIconPath ?: ""

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Payment QR & App Icon",
                subtitle = "Manage Admin Fee QR Code & Custom Branding",
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

            // Section Tabs
            TabRow(
                selectedTabIndex = selectedSection,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    text = { Text("Fee QR Code", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null) }
                )
                Tab(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    text = { Text("App Icon", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Image, contentDescription = null) }
                )
                Tab(
                    selected = selectedSection == 2,
                    onClick = { selectedSection = 2 },
                    text = { Text("UPI Gateway", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) }
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedSection) {
                    0 -> {
                        // --- FEE QR CODE SECTION ---
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ADMIN FEE QR CODE",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Surface(
                                        color = if (currentQrPath.isNotBlank()) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (currentQrPath.isNotBlank()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (currentQrPath.isNotBlank()) "CUSTOM QR ACTIVE" else "DEFAULT QR",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // QR Code Display Box
                                Box(
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (currentQrPath.isNotBlank()) {
                                        AsyncImage(
                                            model = currentQrPath,
                                            contentDescription = "Fee QR Code",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp)
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.QrCode2,
                                                contentDescription = "Placeholder QR",
                                                modifier = Modifier.size(120.dp),
                                                tint = Color.DarkGray
                                            )
                                            Text(
                                                "Tap below to add custom QR",
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = payeeName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = upiId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(20.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(16.dp))

                                // ADD / CHANGE / UPDATE ACTIONS
                                Text(
                                    text = "Add or Update QR Code Image:",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            qrPhotoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = !isUploading,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (currentQrPath.isBlank()) "Add QR Code" else "Change QR")
                                    }

                                    if (currentQrPath.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { showDeleteQrDialog = true },
                                            enabled = !isUploading,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete QR")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = qrUrlInput,
                                    onValueChange = { qrUrlInput = it },
                                    label = { Text("Or Paste Online Image URL") },
                                    placeholder = { Text("https://example.com/qr-code.png") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (qrUrlInput.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            isUploading = true
                                            viewModel.updateAdminQrCode(imageUrl = qrUrlInput) {
                                                isUploading = false
                                                qrUrlInput = ""
                                            }
                                        },
                                        enabled = !isUploading,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Update QR from URL")
                                    }
                                }

                                if (isUploading) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }

                    1 -> {
                        // --- CUSTOM APP ICON SECTION ---
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CUSTOM APP ICON",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Surface(
                                        color = if (currentAppIcon.isNotBlank()) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (currentAppIcon.isNotBlank()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (currentAppIcon.isNotBlank()) "CUSTOM ICON" else "DEFAULT ICON",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // App Icon Preview Box
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (currentAppIcon.isNotBlank()) {
                                        AsyncImage(
                                            model = currentAppIcon,
                                            contentDescription = "App Icon",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircleFilled,
                                            contentDescription = "Neol Tube Icon",
                                            modifier = Modifier.size(64.dp),
                                            tint = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = adminProfile?.instituteName ?: "Neol Tube",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (currentAppIcon.isNotBlank()) "Custom uploaded launcher icon active" else "Default Neol Tube launcher emblem active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(20.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Add or Update App Icon:",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            iconPhotoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = !isUploading,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (currentAppIcon.isBlank()) "Upload Icon" else "Change Icon")
                                    }

                                    if (currentAppIcon.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { showDeleteIconDialog = true },
                                            enabled = !isUploading,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete Icon")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = iconUrlInput,
                                    onValueChange = { iconUrlInput = it },
                                    label = { Text("Or Paste Online Icon URL") },
                                    placeholder = { Text("https://example.com/logo.png") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (iconUrlInput.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            isUploading = true
                                            viewModel.updateAdminAppIcon(imageUrl = iconUrlInput) {
                                                isUploading = false
                                                iconUrlInput = ""
                                            }
                                        },
                                        enabled = !isUploading,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Update App Icon from URL")
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // --- UPI GATEWAY SETTINGS SECTION ---
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
                                    text = "Institute UPI Credentials",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                OutlinedTextField(
                                    value = upiId,
                                    onValueChange = { upiId = it },
                                    label = { Text("UPI Virtual Payment Address (VPA) *") },
                                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = payeeName,
                                    onValueChange = { payeeName = it },
                                    label = { Text("Account Payee Name *") },
                                    leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = qrNote,
                                    onValueChange = { qrNote = it },
                                    label = { Text("Default Transaction Note") },
                                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        val current = adminProfile ?: AdminProfile()
                                        val updated = current.copy(
                                            upiId = upiId.trim(),
                                            upiPayeeName = payeeName.trim(),
                                            upiQrNote = qrNote.trim()
                                        )
                                        viewModel.updateAdminProfile(updated)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save & Sync to Firebase", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // FIREBASE REALTIME DATABASE SYNC CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFFFFA000))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Firebase RTDB Backend", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Surface(
                                color = Color(0xFFE8F5E9),
                                contentColor = Color(0xFF2E7D32),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("CONNECTED", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "https://neol-tube-default-rtdb.firebaseio.com/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = {
                                isSyncingRtdb = true
                                viewModel.uploadAllDataToFirebaseRtdb {
                                    isSyncingRtdb = false
                                }
                            },
                            enabled = !isSyncingRtdb,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSyncingRtdb) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Uploading App Data...")
                            } else {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload & Sync All App Data to RTDB")
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete QR Dialog
    if (showDeleteQrDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteQrDialog = false },
            title = { Text("Delete Fee QR Code") },
            text = { Text("Are you sure you want to remove the custom QR Code? The app will revert to the standard default placeholder.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAdminQrCode {
                            showDeleteQrDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteQrDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete App Icon Dialog
    if (showDeleteIconDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteIconDialog = false },
            title = { Text("Reset Custom App Icon") },
            text = { Text("Are you sure you want to reset the App Icon? It will revert to the default Neol Tube academy logo.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAdminAppIcon {
                            showDeleteIconDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset to Default")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteIconDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

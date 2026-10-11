package com.example.ui.student

import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.UserSession
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentVideosScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val allVideos by viewModel.allVideos.collectAsState()
    val myPurchases by viewModel.currentStudentPurchases.collectAsState()
    val adminProfile by viewModel.adminProfile.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = All, 1 = Free, 2 = Paid, 3 = Enrolled
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("All") }

    var videoToPlay by remember { mutableStateOf<VideoItem?>(null) }
    var videoToEnroll by remember { mutableStateOf<VideoItem?>(null) }

    val enrolledVideoIds = remember(myPurchases) {
        myPurchases.map { it.videoId }.toSet()
    }

    val subjects = listOf("All", "Physics", "Chemistry", "Mathematics", "Biology", "English")

    val filteredVideos = allVideos.filter { video ->
        val matchesTab = when (selectedTab) {
            1 -> !video.isPaid
            2 -> video.isPaid
            3 -> enrolledVideoIds.contains(video.id)
            else -> true
        }
        val matchesSubject = selectedSubject == "All" || video.subject.equals(selectedSubject, ignoreCase = true)
        val matchesSearch = video.title.contains(searchQuery, ignoreCase = true) ||
                video.instructor.contains(searchQuery, ignoreCase = true) ||
                video.subject.contains(searchQuery, ignoreCase = true)
        matchesTab && matchesSubject && matchesSearch
    }

    val freeCount = allVideos.count { !it.isPaid }
    val paidCount = allVideos.count { it.isPaid }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Neol Tube Video Hub",
                subtitle = "$freeCount Free Lectures • $paidCount Premium Courses",
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

            // Section Tabs: All, Free, Paid, Enrolled
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("All (${allVideos.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Free ($freeCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Paid ($paidCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Enrolled (${myPurchases.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search lectures by title, topic, teacher...") },
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Subject Filter Chips
            ScrollableTabRow(
                selectedTabIndex = subjects.indexOf(selectedSubject).coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                subjects.forEach { subj ->
                    FilterChip(
                        selected = selectedSubject == subj,
                        onClick = { selectedSubject = subj },
                        label = { Text(subj) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            if (filteredVideos.isEmpty()) {
                EmptyStateView(
                    title = if (selectedTab == 3) "No Enrolled Courses Yet" else "No Videos Available",
                    message = if (selectedTab == 3) "Browse the Paid section and enroll using your UPI app to unlock masterclasses." else "Try adjusting your search query or subject filters.",
                    icon = Icons.Default.VideoLibrary
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredVideos, key = { it.id }) { video ->
                        val isEnrolled = enrolledVideoIds.contains(video.id)
                        StudentVideoCard(
                            video = video,
                            isEnrolled = isEnrolled,
                            onWatch = { videoToPlay = video },
                            onEnroll = { videoToEnroll = video }
                        )
                    }
                }
            }
        }
    }

    // Video Player Dialog
    if (videoToPlay != null) {
        StudentVideoPlayerDialog(
            video = videoToPlay!!,
            onDismiss = { videoToPlay = null }
        )
    }

    // Paid Course Unlock / UPI Payment Dialog
    if (videoToEnroll != null) {
        EnrollPaymentDialog(
            video = videoToEnroll!!,
            upiId = adminProfile?.upiId ?: "neoltube@upi",
            payeeName = adminProfile?.upiPayeeName ?: "Neol Tube Academy",
            onDismiss = { videoToEnroll = null },
            onConfirmEnroll = { txnRef ->
                viewModel.enrollStudentInVideo(
                    videoId = videoToEnroll!!.id,
                    amount = videoToEnroll!!.price,
                    txnRef = txnRef
                ) {
                    val justEnrolled = videoToEnroll!!
                    videoToEnroll = null
                    // Immediately open player for seamless UX!
                    videoToPlay = justEnrolled
                }
            }
        )
    }
}

@Composable
fun StudentVideoCard(
    video: VideoItem,
    isEnrolled: Boolean,
    onWatch: () -> Unit,
    onEnroll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Free/Paid Badge & Subject Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!video.isPaid) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        contentColor = Color(0xFF2E7D32),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FREE LECTURE", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                } else if (isEnrolled) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        contentColor = Color(0xFF2E7D32),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("UNLOCKED / ENROLLED", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        contentColor = Color(0xFFE65100),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PREMIUM • ₹${video.price}", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        video.subject,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (video.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(4.dp))
                Text(video.instructor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(4.dp))
                Text(video.duration, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.HighQuality, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(4.dp))
                Text(video.videoQuality, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Action Button
            if (!video.isPaid || isEnrolled) {
                Button(
                    onClick = onWatch,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!video.isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (!video.isPaid) "Watch Free Lecture Now" else "Watch Enrolled Course",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onEnroll,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE65100),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock Course • ₹${video.price}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StudentVideoPlayerDialog(
    video: VideoItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = video.title,
                    subtitle = "${video.subject} • ${video.instructor}",
                    onBack = onDismiss,
                    actions = {
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.videoUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open in External Player", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black)
            ) {
                // Interactive Video Playback View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val mediaController = MediaController(ctx)
                                mediaController.setAnchorView(this)
                                setMediaController(mediaController)
                                setVideoURI(Uri.parse(video.videoUrl))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Lesson Content & Notes Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(video.instructor, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(video.duration, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Lecture Notes & Summary:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (video.description.isNotBlank()) video.description else "Full syllabus explanation with comprehensive problem-solving tricks, key concepts, formulas, and real-world examples.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Important Topics & Keywords:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(video.tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun EnrollPaymentDialog(
    video: VideoItem,
    upiId: String,
    payeeName: String,
    onDismiss: () -> Unit,
    onConfirmEnroll: (String) -> Unit
) {
    val context = LocalContext.current
    var txnId by remember { mutableStateOf("") }
    var selectedOption by remember { mutableIntStateOf(0) } // 0 = UPI Pay, 1 = Instant Unlock

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enroll in Premium Course")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = video.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Course Fee", style = MaterialTheme.typography.labelMedium)
                            Text("Full Lifetime Access", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                        }
                        Text(
                            "₹${video.price}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = Color(0xFFE65100)
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Payee: $payeeName", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("UPI VPA: $upiId", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Button(
                    onClick = {
                        try {
                            val uri = Uri.parse("upi://pay?pa=$upiId&pn=${Uri.encode(payeeName)}&am=${video.price}&cu=INR&tn=Course_${video.id}")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback if no UPI app installed
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pay via UPI App (GPay/PhonePe/Paytm)")
                }

                OutlinedTextField(
                    value = txnId,
                    onValueChange = { txnId = it },
                    label = { Text("UPI Transaction Reference / UTR Number") },
                    placeholder = { Text("e.g. 423871923482 or UPI-TXN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Enter your UPI transaction reference above, or tap 'Confirm & Unlock' for instant course enrollment.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ref = if (txnId.isNotBlank()) txnId.trim() else "UPI-COURSE-${System.currentTimeMillis().toString().takeLast(6)}"
                    onConfirmEnroll(ref)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Confirm & Unlock Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

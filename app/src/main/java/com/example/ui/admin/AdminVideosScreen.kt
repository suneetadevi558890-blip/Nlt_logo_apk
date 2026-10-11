package com.example.ui.admin

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
import com.example.data.model.VideoPurchase
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminVideosScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val videos by viewModel.allVideos.collectAsState()
    val purchases by viewModel.allPurchases.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = All, 1 = Free, 2 = Paid, 3 = Purchases
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("All") }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var videoToEdit by remember { mutableStateOf<VideoItem?>(null) }
    var previewVideoUrl by remember { mutableStateOf<VideoItem?>(null) }

    val subjects = listOf("All", "Physics", "Chemistry", "Mathematics", "Biology", "English")

    val filteredVideos = videos.filter { video ->
        val matchesTab = when (selectedTab) {
            1 -> !video.isPaid
            2 -> video.isPaid
            else -> true
        }
        val matchesSubject = selectedSubject == "All" || video.subject.equals(selectedSubject, ignoreCase = true)
        val matchesSearch = video.title.contains(searchQuery, ignoreCase = true) ||
                video.instructor.contains(searchQuery, ignoreCase = true) ||
                video.subject.contains(searchQuery, ignoreCase = true)
        matchesTab && matchesSubject && matchesSearch
    }

    val freeCount = videos.count { !it.isPaid }
    val paidCount = videos.count { it.isPaid }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Video Lectures Management",
                subtitle = "${videos.size} Total • $freeCount Free • $paidCount Paid",
                onBack = { viewModel.navigateBack() },
                isOnline = isOnline
            )
        },
        floatingActionButton = {
            if (selectedTab != 3) {
                ExtendedFloatingActionButton(
                    onClick = {
                        videoToEdit = null
                        showAddEditDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Video") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OnlineStatusBanner(isOnline = isOnline)

            // Section Tabs: All, Free Section, Paid Section, Enrollments
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("All (${videos.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
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
                    text = { Text("Sales (${purchases.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }

            if (selectedTab != 3) {
                // Search and Filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by title, instructor, topic...") },
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }

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

                // Video List
                if (filteredVideos.isEmpty()) {
                    EmptyStateView(
                        title = "No Videos Found",
                        message = if (selectedTab == 1) "No free videos added yet." else if (selectedTab == 2) "No paid videos added yet." else "Add educational video courses using the button below.",
                        icon = Icons.Default.VideoLibrary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredVideos, key = { it.id }) { video ->
                            AdminVideoCard(
                                video = video,
                                onPreview = { previewVideoUrl = video },
                                onEdit = {
                                    videoToEdit = video
                                    showAddEditDialog = true
                                },
                                onDelete = { viewModel.deleteVideo(video.id) }
                            )
                        }
                    }
                }
            } else {
                // Purchases / Sales Tab
                if (purchases.isEmpty()) {
                    EmptyStateView(
                        title = "No Enrollments Yet",
                        message = "When students enroll or pay for premium videos, transactions appear here with instant verification.",
                        icon = Icons.Default.ShoppingCart
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(purchases, key = { it.id }) { purchase ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE8F5E9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(purchase.studentName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Enrolled in Video #${purchase.videoId}", style = MaterialTheme.typography.bodySmall)
                                        Text("Date: ${purchase.purchaseDate} • Ref: ${purchase.transactionRef}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        "₹${purchase.amountPaid}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Video Dialog
    if (showAddEditDialog) {
        AddEditVideoDialog(
            existing = videoToEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { updatedVideo ->
                viewModel.saveVideo(updatedVideo) {
                    showAddEditDialog = false
                }
            }
        )
    }

    // Video Player Preview Dialog
    if (previewVideoUrl != null) {
        VideoPlayerDialog(
            video = previewVideoUrl!!,
            onDismiss = { previewVideoUrl = null }
        )
    }
}

@Composable
fun AdminVideoCard(
    video: VideoItem,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
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
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Free or Paid Badge
                if (video.isPaid) {
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
                            Text("PAID • ₹${video.price}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else {
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
                            Text("100% FREE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Subject tag
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        video.subject,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
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
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(4.dp))
                Text("${video.viewsCount} views", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onPreview,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview Video")
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Video", tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Video", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditVideoDialog(
    existing: VideoItem?,
    onDismiss: () -> Unit,
    onSave: (VideoItem) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var subject by remember { mutableStateOf(existing?.subject ?: "Physics") }
    var duration by remember { mutableStateOf(existing?.duration ?: "45 mins") }
    var instructor by remember { mutableStateOf(existing?.instructor ?: "Er. Neol Sir") }
    var videoUrl by remember { mutableStateOf(existing?.videoUrl ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4") }
    var isPaid by remember { mutableStateOf(existing?.isPaid ?: false) }
    var priceStr by remember { mutableStateOf(if (existing != null && existing.isPaid) existing.price.toInt().toString() else "299") }
    var tags by remember { mutableStateOf(existing?.tags ?: "Concepts, Board Exam") }

    val subjects = listOf("Physics", "Chemistry", "Mathematics", "Biology", "English", "General")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Educational Video" else "Edit Video Details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Video Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description & Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Subject Selector
                Text("Subject:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subjects.take(3).forEach { subj ->
                        FilterChip(
                            selected = subject == subj,
                            onClick = { subject = subj },
                            label = { Text(subj, fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subjects.drop(3).forEach { subj ->
                        FilterChip(
                            selected = subject == subj,
                            onClick = { subject = subj },
                            label = { Text(subj, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = instructor,
                        onValueChange = { instructor = it },
                        label = { Text("Instructor") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it },
                        label = { Text("Duration") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("Video Stream URL (MP4 / Web)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Free or Paid Section Selection
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPaid) Color(0xFFFFF3E0) else Color(0xFFE8F5E9)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    if (isPaid) "PREMIUM PAID VIDEO" else "FREE EDUCATIONAL LECTURE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isPaid) Color(0xFFE65100) else Color(0xFF2E7D32)
                                )
                                Text(
                                    if (isPaid) "Students must enroll/pay via UPI to unlock" else "Available to all registered students for free",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = isPaid,
                                onCheckedChange = { isPaid = it }
                            )
                        }

                        if (isPaid) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = priceStr,
                                onValueChange = { priceStr = it },
                                label = { Text("Price in INR (₹)") },
                                leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Keywords / Tags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val price = if (isPaid) priceStr.toDoubleOrNull() ?: 299.0 else 0.0
                    val finalVideo = (existing ?: VideoItem(
                        id = 0L,
                        title = title,
                        videoUrl = videoUrl
                    )).copy(
                        title = title,
                        description = description,
                        subject = subject,
                        instructor = instructor,
                        duration = duration,
                        videoUrl = videoUrl,
                        isPaid = isPaid,
                        price = price,
                        tags = tags
                    )
                    onSave(finalVideo)
                }
            ) {
                Text("Save Video")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VideoPlayerDialog(
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
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open External", tint = MaterialTheme.colorScheme.onPrimary)
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
                // Interactive In-App Video View
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

                // Video Details & Notes Card
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            if (video.isPaid) {
                                Surface(
                                    color = Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "₹${video.price}",
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(video.subject, style = MaterialTheme.typography.labelMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(video.instructor, style = MaterialTheme.typography.labelMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(video.duration, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Lecture Description & Syllabus:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (video.description.isNotBlank()) video.description else "Complete high definition video lecture covering theory, fundamental concepts, solved examples, and numerical derivations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Tags & Topics:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(video.tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

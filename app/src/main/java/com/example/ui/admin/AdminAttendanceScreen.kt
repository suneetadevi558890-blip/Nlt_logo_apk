package com.example.ui.admin

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceRecord
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAttendanceScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val students by viewModel.allStudents.collectAsState()
    val allAttendance by viewModel.allAttendance.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var selectedDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    var selectedClass by remember { mutableStateOf("All") }

    val classes = remember(students) {
        listOf("All") + students.map { it.className }.distinct()
    }

    val studentsFiltered = students.filter {
        selectedClass == "All" || it.className.equals(selectedClass, ignoreCase = true)
    }

    val dayRecords = allAttendance.filter { it.date == selectedDate }
    val recordMap = remember(dayRecords) {
        dayRecords.associateBy { it.studentId }
    }

    val presentCount = dayRecords.count { it.isPresent }
    val absentCount = dayRecords.count { !it.isPresent }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Attendance Register",
                subtitle = "Date: $selectedDate • $presentCount Present / $absentCount Absent",
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

            // Date and class selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = selectedDate,
                    onValueChange = { selectedDate = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        // Bulk Mark All Present
                        studentsFiltered.forEach { st ->
                            val record = AttendanceRecord(
                                id = 0L,
                                studentId = st.id,
                                studentName = st.name,
                                className = st.className,
                                date = selectedDate,
                                isPresent = true,
                                status = "Present"
                            )
                            viewModel.markAttendance(record)
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Mark All Present", fontSize = 12.sp)
                }
            }

            // Class filters
            ScrollableTabRow(
                selectedTabIndex = classes.indexOf(selectedClass).coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                classes.forEach { cls ->
                    FilterChip(
                        selected = selectedClass == cls,
                        onClick = { selectedClass = cls },
                        label = { Text(cls) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            if (studentsFiltered.isEmpty()) {
                EmptyStateView(
                    title = "No Students in Class",
                    message = "Add students in Student Directory to start marking attendance.",
                    icon = Icons.Default.EventBusy
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(studentsFiltered, key = { it.id }) { student ->
                        val record = recordMap[student.id]
                        val isPresent = record?.isPresent == true

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isPresent) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPresent) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (isPresent) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(student.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Roll: ${student.rollNumber} • ${student.className}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.markAttendance(
                                                AttendanceRecord(
                                                    id = record?.id ?: 0L,
                                                    studentId = student.id,
                                                    studentName = student.name,
                                                    className = student.className,
                                                    date = selectedDate,
                                                    isPresent = true,
                                                    status = "Present"
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isPresent) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isPresent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("P", fontWeight = FontWeight.Bold)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.markAttendance(
                                                AttendanceRecord(
                                                    id = record?.id ?: 0L,
                                                    studentId = student.id,
                                                    studentName = student.name,
                                                    className = student.className,
                                                    date = selectedDate,
                                                    isPresent = false,
                                                    status = "Absent"
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (!isPresent && record != null) Color(0xFFC62828) else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (!isPresent && record != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("A", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

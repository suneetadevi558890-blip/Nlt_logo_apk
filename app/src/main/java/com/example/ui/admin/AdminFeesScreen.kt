package com.example.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.example.data.model.FeeRecord
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.ui.MainViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFeesScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val fees by viewModel.allFees.collectAsState()
    val payments by viewModel.allPayments.collectAsState()
    val students by viewModel.allStudents.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Fee Records, 1 = Verification Queue, 2 = Paid History
    var showCreateFeeDialog by remember { mutableStateOf(false) }
    var feeForManualPayment by remember { mutableStateOf<FeeRecord?>(null) }

    val pendingVerifications = payments.filter { it.paymentStatus == "Pending Verification" }
    val paidHistory = payments.filter { it.paymentStatus == "Paid" }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Fees & Payment Gateway",
                subtitle = "${fees.size} Fee Records • ${pendingVerifications.size} Pending Approval",
                onBack = { viewModel.navigateBack() },
                isOnline = isOnline
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateFeeDialog = true },
                    icon = { Icon(Icons.Default.AddCard, contentDescription = null) },
                    text = { Text("Assign Fee") },
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

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("All Fees (${fees.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Verification (${pendingVerifications.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Paid History (${paidHistory.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // All Fees
                    if (fees.isEmpty()) {
                        EmptyStateView(
                            title = "No Fee Records",
                            message = "Tap 'Assign Fee' below to create tuition, exam, or course fee invoices.",
                            icon = Icons.Default.ReceiptLong
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(fees, key = { it.id }) { fee ->
                                FeeAdminCard(
                                    fee = fee,
                                    onManualPay = { feeForManualPayment = fee },
                                    onDelete = { viewModel.deleteFee(fee) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Pending Verification Queue
                    if (pendingVerifications.isEmpty()) {
                        EmptyStateView(
                            title = "Verification Queue Clear",
                            message = "When students submit UPI payment receipts/transaction IDs, they will appear here for admin review.",
                            icon = Icons.Default.CheckCircle
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(pendingVerifications, key = { it.id }) { p ->
                                PaymentVerificationCard(
                                    payment = p,
                                    onApprove = { notes ->
                                        val receiptNo = "REC-${System.currentTimeMillis().toString().takeLast(6)}"
                                        viewModel.verifyPayment(p.id, p.feeId, true, notes, receiptNo, "Admin")
                                    },
                                    onReject = { notes ->
                                        viewModel.verifyPayment(p.id, p.feeId, false, notes, "", "Admin")
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Paid History & Digital Receipts
                    if (paidHistory.isEmpty()) {
                        EmptyStateView(
                            title = "No Completed Payments",
                            message = "Approved student payments will appear here with downloadable receipt records.",
                            icon = Icons.Default.History
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(paidHistory, key = { it.id }) { p ->
                                PaidHistoryCard(payment = p)
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Fee Dialog
    if (showCreateFeeDialog) {
        CreateFeeDialog(
            students = students,
            onDismiss = { showCreateFeeDialog = false },
            onSave = { fee ->
                viewModel.createFee(fee) {
                    showCreateFeeDialog = false
                }
            }
        )
    }

    // Manual Admin Payment Dialog
    if (feeForManualPayment != null) {
        val targetFee = feeForManualPayment!!
        var amountStr by remember { mutableStateOf(targetFee.amount.toInt().toString()) }
        var method by remember { mutableStateOf("Cash") }
        var notes by remember { mutableStateOf("Received at front desk") }

        AlertDialog(
            onDismissRequest = { feeForManualPayment = null },
            title = { Text("Record Manual Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Student: ${targetFee.studentName}", fontWeight = FontWeight.Bold)
                    Text("Fee: ${targetFee.feeType} (${targetFee.month})", style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount Paid (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = method == "Cash",
                            onClick = { method = "Cash" },
                            label = { Text("Cash") }
                        )
                        FilterChip(
                            selected = method == "UPI Direct",
                            onClick = { method = "UPI Direct" },
                            label = { Text("UPI Direct") }
                        )
                        FilterChip(
                            selected = method == "Bank Transfer",
                            onClick = { method = "Bank Transfer" },
                            label = { Text("Bank") }
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Remarks / Receipt Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: targetFee.amount
                        viewModel.recordManualAdminPayment(targetFee.id, targetFee.studentId, amount, method, notes) {
                            feeForManualPayment = null
                        }
                    }
                ) {
                    Text("Confirm & Generate Receipt")
                }
            },
            dismissButton = {
                TextButton(onClick = { feeForManualPayment = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FeeAdminCard(
    fee: FeeRecord,
    onManualPay: () -> Unit,
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(fee.studentName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${fee.feeType} • ${fee.month}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = when (fee.status) {
                        "Paid" -> Color(0xFFE8F5E9)
                        "Pending Verification" -> Color(0xFFFFF3E0)
                        else -> Color(0xFFFFEBEE)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = fee.status.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when (fee.status) {
                            "Paid" -> Color(0xFF2E7D32)
                            "Pending Verification" -> Color(0xFFE65100)
                            else -> Color(0xFFC62828)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Due: ${fee.dueDate}", style = MaterialTheme.typography.bodySmall)
                Text("₹${fee.amount}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            }

            if (fee.status != "Paid") {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onManualPay) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Record Cash/UPI")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentVerificationCard(
    payment: PaymentRecord,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    var notes by remember { mutableStateOf("Verified via UPI") }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Column {
                    Text(payment.studentName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Student ID: ${payment.studentIdCode}", style = MaterialTheme.typography.bodySmall)
                    Text("${payment.feeType} (${payment.month})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("₹${payment.amount}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF2E7D32))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Txn Reference: ${payment.transactionId}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Submitted At: ${payment.paymentDateTime}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Verification Notes / Reason") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onReject(notes) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reject")
                }
                Button(
                    onClick = { onApprove(notes) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Approve Payment")
                }
            }
        }
    }
}

@Composable
fun PaidHistoryCard(payment: PaymentRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Icon(Icons.Default.Receipt, contentDescription = null, tint = Color(0xFF2E7D32))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(payment.studentName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${payment.feeType} • Receipt: ${payment.receiptNumber}", style = MaterialTheme.typography.bodySmall)
                Text("Txn: ${payment.transactionId} • ${payment.paymentDateTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("₹${payment.amount}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFeeDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onSave: (FeeRecord) -> Unit
) {
    var selectedStudent by remember { mutableStateOf<Student?>(students.firstOrNull()) }
    var feeType by remember { mutableStateOf("Tuition Fee") }
    var month by remember { mutableStateOf("October 2026") }
    var amountStr by remember { mutableStateOf("1500") }
    var dueDate by remember { mutableStateOf("2026-10-25") }
    var description by remember { mutableStateOf("") }

    val feeTypes = listOf("Tuition Fee", "Exam Fee", "Lab Fee", "Course Material", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Fee Invoice") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select Student:", style = MaterialTheme.typography.labelMedium)
                // Student selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    students.take(4).forEach { st ->
                        FilterChip(
                            selected = selectedStudent?.id == st.id,
                            onClick = { selectedStudent = st },
                            label = { Text(st.name, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Fee Type:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    feeTypes.take(3).forEach { ft ->
                        FilterChip(
                            selected = feeType == ft,
                            onClick = { feeType = ft },
                            label = { Text(ft, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = month,
                    onValueChange = { month = it },
                    label = { Text("Billing Month / Term") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Fee Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val st = selectedStudent ?: return@Button
                    val amount = amountStr.toDoubleOrNull() ?: 1500.0
                    val fee = FeeRecord(
                        id = 0L,
                        studentId = st.id,
                        studentName = st.name,
                        className = st.className,
                        feeType = feeType,
                        month = month,
                        amount = amount,
                        dueDate = dueDate,
                        description = description,
                        status = "Pending"
                    )
                    onSave(fee)
                }
            ) {
                Text("Create Invoice")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

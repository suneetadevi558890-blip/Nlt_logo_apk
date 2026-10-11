package com.example.ui.student

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FeeRecord
import com.example.ui.MainViewModel
import com.example.ui.UserSession
import com.example.ui.components.AppTopBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OnlineStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFeesScreen(viewModel: MainViewModel) {
    BackHandler { viewModel.navigateBack() }

    val session by viewModel.session.collectAsState()
    val student = (session as? UserSession.StudentUser)?.student
    val studentFees by viewModel.currentStudentFees.collectAsState()
    val adminProfile by viewModel.adminProfile.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var feeToPay by remember { mutableStateOf<FeeRecord?>(null) }
    var feeForReceipt by remember { mutableStateOf<FeeRecord?>(null) }

    val totalPending = studentFees.filter { it.status == "Pending" }.sumOf { it.amount }
    val totalPaid = studentFees.filter { it.status == "Paid" }.sumOf { it.amount }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Fees & Online Payments",
                subtitle = "Student ID: ${student?.studentIdCode ?: ""}",
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

            // Balance Summary
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL DUES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "₹${totalPending.toInt()}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = if (totalPending > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(40.dp))

                    Column {
                        Text("TOTAL PAID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "₹${totalPaid.toInt()}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            if (studentFees.isEmpty()) {
                EmptyStateView(
                    title = "No Fee Records Found",
                    message = "No invoices have been assigned to your profile yet.",
                    icon = Icons.Default.ReceiptLong
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(studentFees, key = { it.id }) { fee ->
                        StudentFeeCard(
                            fee = fee,
                            onPay = { feeToPay = fee },
                            onViewReceipt = { feeForReceipt = fee }
                        )
                    }
                }
            }
        }
    }

    // Pay via UPI Dialog
    if (feeToPay != null && student != null) {
        StudentPayUpiDialog(
            fee = feeToPay!!,
            student = student,
            upiId = adminProfile?.upiId ?: "neoltube@upi",
            payeeName = adminProfile?.upiPayeeName ?: "Neol Tube Academy",
            onDismiss = { feeToPay = null },
            onSubmitPayment = { txnId ->
                viewModel.submitStudentPayment(
                    feeId = feeToPay!!.id,
                    studentId = student.id,
                    studentName = student.name,
                    studentCode = student.studentIdCode,
                    feeType = feeToPay!!.feeType,
                    month = feeToPay!!.month,
                    amount = feeToPay!!.amount,
                    txnId = txnId
                ) {
                    feeToPay = null
                }
            }
        )
    }

    // Digital Receipt Dialog
    if (feeForReceipt != null && student != null) {
        DigitalReceiptDialog(
            fee = feeForReceipt!!,
            student = student,
            instituteName = adminProfile?.instituteName ?: "Neol Tube Academy",
            onDismiss = { feeForReceipt = null }
        )
    }
}

@Composable
fun StudentFeeCard(
    fee: FeeRecord,
    onPay: () -> Unit,
    onViewReceipt: () -> Unit
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
                    Text(fee.feeType, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(fee.month, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("₹${fee.amount.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            if (fee.status == "Paid") {
                Button(
                    onClick = onViewReceipt,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Digital Fee Receipt")
                }
            } else if (fee.status == "Pending Verification") {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verification in Progress by Admin")
                }
            } else {
                Button(
                    onClick = onPay,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pay ₹${fee.amount.toInt()} via UPI")
                }
            }
        }
    }
}

@Composable
fun StudentPayUpiDialog(
    fee: FeeRecord,
    student: com.example.data.model.Student,
    upiId: String,
    payeeName: String,
    onDismiss: () -> Unit,
    onSubmitPayment: (String) -> Unit
) {
    val context = LocalContext.current
    var txnId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("UPI Fee Payment") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${fee.feeType} - ${fee.month}", fontWeight = FontWeight.Bold)

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Amount to Pay:", style = MaterialTheme.typography.bodyMedium)
                        Text("₹${fee.amount.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Institute: $payeeName", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("UPI VPA: $upiId", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Button(
                    onClick = {
                        try {
                            val uri = Uri.parse("upi://pay?pa=$upiId&pn=${Uri.encode(payeeName)}&am=${fee.amount}&cu=INR&tn=Fee_${fee.id}")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Launch UPI App (GPay/PhonePe)")
                }

                OutlinedTextField(
                    value = txnId,
                    onValueChange = { txnId = it },
                    label = { Text("UPI Transaction Reference / UTR *") },
                    placeholder = { Text("e.g. 423987162534") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "After transferring via your UPI app, paste the transaction reference above to notify Admin.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ref = if (txnId.isNotBlank()) txnId.trim() else "UPI-${System.currentTimeMillis().toString().takeLast(6)}"
                    onSubmitPayment(ref)
                }
            ) {
                Text("Submit Receipt")
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
fun DigitalReceiptDialog(
    fee: FeeRecord,
    student: com.example.data.model.Student,
    instituteName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Official Fee Receipt")
            }
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(instituteName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Receipt No: ${fee.receiptNumber ?: "REC-2026-001"}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Divider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Student Name:", style = MaterialTheme.typography.bodySmall)
                        Text(student.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Student ID:", style = MaterialTheme.typography.bodySmall)
                        Text(student.studentIdCode, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Class / Batch:", style = MaterialTheme.typography.bodySmall)
                        Text(student.className, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fee Particulars:", style = MaterialTheme.typography.bodySmall)
                        Text("${fee.feeType} (${fee.month})", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Payment Mode:", style = MaterialTheme.typography.bodySmall)
                        Text(fee.paymentMethod ?: "UPI", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Transaction Ref:", style = MaterialTheme.typography.bodySmall)
                        Text(fee.transactionId ?: "ONLINE-TXN", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Paid Date:", style = MaterialTheme.typography.bodySmall)
                        Text(fee.paidDate ?: "2026-10-05", style = MaterialTheme.typography.bodySmall)
                    }

                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AMOUNT PAID:", fontWeight = FontWeight.Bold)
                        Text("₹${fee.amount.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF2E7D32))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin_profile")
data class AdminProfile(
    @PrimaryKey val id: Int = 1,
    val mobile: String = "9876543210",
    val passwordHash: String = "admin123",
    val name: String = "Administrator",
    val instituteName: String = "Neol Tube Academy",
    val email: String = "admin@neoltube.edu",
    val instituteAddress: String = "Neol Tube Campus, Education Hub",
    val securityPin: String = "1234",
    val isTwoFactorEnabled: Boolean = false,
    val requireOtpOnLogin: Boolean = false,
    val upiId: String = "neoltube@upi",
    val upiPayeeName: String = "Neol Tube Academy",
    val upiQrNote: String = "Student Fees Payment",
    val qrImagePath: String = "", // Uploaded QR image
    val appIconPath: String = "" // Uploaded custom App Icon
)

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentIdCode: String, // e.g. NT-2026-001
    val name: String,
    val mobile: String,
    val password: String,
    val familyGroupId: String = "", // Group siblings sharing mobile
    val fatherName: String = "",
    val motherName: String = "",
    val className: String = "Class 10",
    val rollNumber: String = "01",
    val email: String = "",
    val address: String = "",
    val photoUri: String = "", // Photo download URL or file path
    val avatarId: String = "avatar_1",
    val customFields: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "profile_visibility_config")
data class ProfileVisibilityConfig(
    @PrimaryKey val id: Int = 1,
    val showPhoto: Boolean = true,
    val showStudentName: Boolean = true,
    val showFatherName: Boolean = true,
    val showMotherName: Boolean = true,
    val showClass: Boolean = true,
    val showRollNumber: Boolean = true,
    val showStudentId: Boolean = true,
    val showContact: Boolean = true,
    val showAddress: Boolean = true,
    val showCustomFields: Boolean = true
)

@Entity(tableName = "attendance")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val studentName: String,
    val className: String,
    val date: String, // Format: YYYY-MM-DD
    val isPresent: Boolean,
    val remarks: String = "",
    val status: String = if (isPresent) "Present" else "Absent"
)

@Entity(tableName = "fees")
data class FeeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val studentName: String,
    val className: String,
    val feeType: String, // Tuition Fee, Exam Fee, Food Fee, Transport Fee, Other Fee
    val description: String = "",
    val month: String, // January .. December
    val amount: Double,
    val dueDate: String,
    val startDate: String = "",
    val endDate: String = "",
    val calculationMode: String = "Fixed", // Fixed or Prorated
    val applicableDays: Int = 0,
    val perDayRate: Double = 0.0,
    val status: String = "Pending", // Pending, Paid, Pending Verification, Cancelled
    val paidDate: String? = null,
    val transactionId: String? = null,
    val receiptNumber: String? = null,
    val paymentMethod: String? = null
)

@Entity(tableName = "payment_records")
data class PaymentRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feeId: Long,
    val studentId: Long,
    val studentName: String,
    val studentIdCode: String,
    val feeType: String,
    val month: String,
    val amount: Double,
    val paymentDateTime: String,
    val transactionId: String,
    val orderId: String = "",
    val paymentStatus: String = "Pending Verification", // Pending Verification, Paid, Failed, Cancelled
    val paymentMethod: String = "UPI",
    val receiptNumber: String = "",
    val verificationNotes: String = "",
    val isManualAdminPayment: Boolean = false,
    val verifiedBy: String = "",
    val screenshotUri: String? = null
)

@Entity(tableName = "notifications")
data class NotificationItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val targetAudience: String = "All Students",
    val studentId: Long? = null,
    val familyGroupId: String = "",
    val date: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val entityType: String,
    val entityId: String,
    val details: String,
    val performedBy: String = "Admin",
    val timestamp: Long = System.currentTimeMillis(),
    val dateTimeStr: String = ""
)

@Entity(tableName = "leave_requests")
data class LeaveRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val studentName: String,
    val studentIdCode: String,
    val className: String,
    val startDate: String, // Format: YYYY-MM-DD
    val endDate: String,   // Format: YYYY-MM-DD
    val reason: String,
    val status: String = "Pending", // "Pending", "Approved", "Rejected", "Cancelled"
    val adminNote: String = "",
    val appliedDate: String = "",
    val reviewedBy: String = "",
    val reviewedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "videos")
data class VideoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val subject: String = "Physics", // Physics, Chemistry, Mathematics, Biology, English, General
    val duration: String = "45 mins",
    val instructor: String = "Er. Neol Sir",
    val videoUrl: String,
    val thumbnailUrl: String = "",
    val isPaid: Boolean = false, // false = Free, true = Paid
    val price: Double = 0.0,
    val videoQuality: String = "1080p FHD",
    val viewsCount: Int = 150,
    val tags: String = "Class 10/12, Concepts, Full Lecture",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "video_purchases")
data class VideoPurchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: Long,
    val studentId: Long,
    val studentName: String,
    val amountPaid: Double,
    val transactionRef: String = "",
    val purchaseDate: String, // YYYY-MM-DD
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Active" // Active, Expired
)

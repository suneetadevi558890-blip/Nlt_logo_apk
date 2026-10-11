package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.model.AdminProfile
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLogRecord
import com.example.data.model.FeeRecord
import com.example.data.model.LeaveRequest
import com.example.data.model.NotificationItem
import com.example.data.model.PaymentRecord
import com.example.data.model.ProfileVisibilityConfig
import com.example.data.model.Student
import com.example.data.model.VideoItem
import com.example.data.model.VideoPurchase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class FirestoreSyncManager(
    private val context: Context,
    private val db: AppDatabase
) {
    private val TAG = "FirestoreSyncManager"
    private val firestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseFirestore", e)
            null
        }
    }

    private val listeners = mutableListOf<ListenerRegistration>()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Collection names on central server
    private val COL_ADMIN = "admin_profile"
    private val COL_STUDENTS = "students"
    private val COL_ATTENDANCE = "attendance"
    private val COL_FEES = "fees"
    private val COL_PAYMENTS = "payments"
    private val COL_NOTIFICATIONS = "notifications"
    private val COL_AUDIT_LOGS = "audit_logs"
    private val COL_LEAVES = "leaves"
    private val COL_VIDEOS = "videos"
    private val COL_VIDEO_PURCHASES = "video_purchases"

    fun startRealtimeSync() {
        val fs = firestore ?: return

        // 1. Sync Admin Profile in real-time
        try {
            val adminListener = fs.collection(COL_ADMIN).document("main")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    scope.launch {
                        try {
                            val data = snapshot.data ?: return@launch
                            val profile = AdminProfile(
                                id = 1,
                                mobile = data["mobile"] as? String ?: "9876543210",
                                passwordHash = data["passwordHash"] as? String ?: "admin123",
                                name = data["name"] as? String ?: "Administrator",
                                instituteName = data["instituteName"] as? String ?: "Neol Tube Academy",
                                email = data["email"] as? String ?: "admin@neoltube.edu",
                                instituteAddress = data["instituteAddress"] as? String ?: "",
                                securityPin = data["securityPin"] as? String ?: "1234",
                                isTwoFactorEnabled = data["isTwoFactorEnabled"] as? Boolean ?: false,
                                requireOtpOnLogin = data["requireOtpOnLogin"] as? Boolean ?: false,
                                upiId = data["upiId"] as? String ?: "neoltube@upi",
                                upiPayeeName = data["upiPayeeName"] as? String ?: "Neol Tube Academy",
                                upiQrNote = data["upiQrNote"] as? String ?: "Student Fees Payment",
                                qrImagePath = data["qrImagePath"] as? String ?: "",
                                appIconPath = data["appIconPath"] as? String ?: ""
                            )
                            db.adminDao().saveAdminProfile(profile)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing admin profile", e)
                        }
                    }
                }
            listeners.add(adminListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach admin listener", e)
        }

        // 2. Sync Students in real-time across devices
        try {
            val studentListener = fs.collection(COL_STUDENTS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (doc in snapshot.documents) {
                                val d = doc.data ?: continue
                                val student = Student(
                                    id = (d["id"] as? Long) ?: (doc.id.toLongOrNull() ?: System.currentTimeMillis()),
                                    studentIdCode = d["studentIdCode"] as? String ?: "",
                                    name = d["name"] as? String ?: "",
                                    mobile = d["mobile"] as? String ?: "",
                                    password = d["password"] as? String ?: "",
                                    familyGroupId = d["familyGroupId"] as? String ?: "",
                                    fatherName = d["fatherName"] as? String ?: "",
                                    motherName = d["motherName"] as? String ?: "",
                                    className = d["className"] as? String ?: "Class 10",
                                    rollNumber = d["rollNumber"] as? String ?: "",
                                    email = d["email"] as? String ?: "",
                                    address = d["address"] as? String ?: "",
                                    photoUri = d["photoUri"] as? String ?: "",
                                    avatarId = d["avatarId"] as? String ?: "avatar_1",
                                    customFields = d["customFields"] as? String ?: "",
                                    createdAt = (d["createdAt"] as? Long) ?: System.currentTimeMillis()
                                )
                                if (student.studentIdCode.isNotBlank()) {
                                    db.studentDao().insertStudent(student)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing students", e)
                        }
                    }
                }
            listeners.add(studentListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach students listener", e)
        }

        // 3. Sync Fees in real-time
        try {
            val feeListener = fs.collection(COL_FEES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (doc in snapshot.documents) {
                                val d = doc.data ?: continue
                                val fee = FeeRecord(
                                    id = (d["id"] as? Long) ?: (doc.id.toLongOrNull() ?: 0L),
                                    studentId = (d["studentId"] as? Long) ?: 0L,
                                    studentName = d["studentName"] as? String ?: "",
                                    className = d["className"] as? String ?: "",
                                    feeType = d["feeType"] as? String ?: "Tuition Fee",
                                    description = d["description"] as? String ?: "",
                                    month = d["month"] as? String ?: "",
                                    amount = (d["amount"] as? Number)?.toDouble() ?: 0.0,
                                    dueDate = d["dueDate"] as? String ?: "",
                                    startDate = d["startDate"] as? String ?: "",
                                    endDate = d["endDate"] as? String ?: "",
                                    calculationMode = d["calculationMode"] as? String ?: "Fixed",
                                    applicableDays = (d["applicableDays"] as? Long)?.toInt() ?: 0,
                                    perDayRate = (d["perDayRate"] as? Number)?.toDouble() ?: 0.0,
                                    status = d["status"] as? String ?: "Pending",
                                    paidDate = d["paidDate"] as? String,
                                    transactionId = d["transactionId"] as? String,
                                    receiptNumber = d["receiptNumber"] as? String,
                                    paymentMethod = d["paymentMethod"] as? String
                                )
                                if (fee.id > 0) {
                                    db.feeDao().insertFee(fee)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing fees", e)
                        }
                    }
                }
            listeners.add(feeListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach fee listener", e)
        }

        // 4. Sync Attendance in real-time
        try {
            val attListener = fs.collection(COL_ATTENDANCE)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (doc in snapshot.documents) {
                                val d = doc.data ?: continue
                                val att = AttendanceRecord(
                                    id = (d["id"] as? Long) ?: (doc.id.toLongOrNull() ?: 0L),
                                    studentId = (d["studentId"] as? Long) ?: 0L,
                                    studentName = d["studentName"] as? String ?: "",
                                    className = d["className"] as? String ?: "",
                                    date = d["date"] as? String ?: "",
                                    isPresent = d["isPresent"] as? Boolean ?: true,
                                    remarks = d["remarks"] as? String ?: "",
                                    status = d["status"] as? String ?: "Present"
                                )
                                if (att.studentId > 0 && att.date.isNotBlank()) {
                                    db.attendanceDao().recordAttendance(att)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing attendance", e)
                        }
                    }
                }
            listeners.add(attListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach attendance listener", e)
        }

        // 5. Sync Payments in real-time
        try {
            val paymentListener = fs.collection(COL_PAYMENTS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (doc in snapshot.documents) {
                                val d = doc.data ?: continue
                                val payment = PaymentRecord(
                                    id = (d["id"] as? Long) ?: (doc.id.toLongOrNull() ?: 0L),
                                    feeId = (d["feeId"] as? Long) ?: 0L,
                                    studentId = (d["studentId"] as? Long) ?: 0L,
                                    studentName = d["studentName"] as? String ?: "",
                                    studentIdCode = d["studentIdCode"] as? String ?: "",
                                    feeType = d["feeType"] as? String ?: "",
                                    month = d["month"] as? String ?: "",
                                    amount = (d["amount"] as? Number)?.toDouble() ?: 0.0,
                                    paymentDateTime = d["paymentDateTime"] as? String ?: "",
                                    transactionId = d["transactionId"] as? String ?: "",
                                    orderId = d["orderId"] as? String ?: "",
                                    paymentStatus = d["paymentStatus"] as? String ?: "Pending Verification",
                                    paymentMethod = d["paymentMethod"] as? String ?: "UPI",
                                    receiptNumber = d["receiptNumber"] as? String ?: "",
                                    verificationNotes = d["verificationNotes"] as? String ?: "",
                                    isManualAdminPayment = d["isManualAdminPayment"] as? Boolean ?: false,
                                    verifiedBy = d["verifiedBy"] as? String ?: "",
                                    screenshotUri = d["screenshotUri"] as? String
                                )
                                if (payment.id > 0) {
                                    db.paymentDao().insertPayment(payment)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing payments", e)
                        }
                    }
                }
            listeners.add(paymentListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach payment listener", e)
        }

        // 6. Sync Videos in real-time (Free & Paid)
        try {
            val videoListener = fs.collection(COL_VIDEOS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (doc in snapshot.documents) {
                                val d = doc.data ?: continue
                                val video = VideoItem(
                                    id = (d["id"] as? Long) ?: (doc.id.toLongOrNull() ?: 0L),
                                    title = d["title"] as? String ?: "",
                                    description = d["description"] as? String ?: "",
                                    subject = d["subject"] as? String ?: "Physics",
                                    duration = d["duration"] as? String ?: "45 mins",
                                    instructor = d["instructor"] as? String ?: "Er. Neol Sir",
                                    videoUrl = d["videoUrl"] as? String ?: "",
                                    thumbnailUrl = d["thumbnailUrl"] as? String ?: "",
                                    isPaid = d["isPaid"] as? Boolean ?: false,
                                    price = (d["price"] as? Number)?.toDouble() ?: 0.0,
                                    videoQuality = d["videoQuality"] as? String ?: "1080p FHD",
                                    viewsCount = (d["viewsCount"] as? Long)?.toInt() ?: 100,
                                    tags = d["tags"] as? String ?: "",
                                    createdAt = (d["createdAt"] as? Long) ?: System.currentTimeMillis()
                                )
                                if (video.id > 0 && video.title.isNotBlank()) {
                                    db.videoDao().insertVideo(video)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing videos", e)
                        }
                    }
                }
            listeners.add(videoListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach video listener", e)
        }

        // 7. Sync Video Purchases in real-time
        try {
            val purchaseListener = fs.collection(COL_VIDEO_PURCHASES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (doc in snapshot.documents) {
                                val d = doc.data ?: continue
                                val purchase = VideoPurchase(
                                    id = (d["id"] as? Long) ?: (doc.id.toLongOrNull() ?: 0L),
                                    videoId = (d["videoId"] as? Long) ?: 0L,
                                    studentId = (d["studentId"] as? Long) ?: 0L,
                                    studentName = d["studentName"] as? String ?: "",
                                    amountPaid = (d["amountPaid"] as? Number)?.toDouble() ?: 0.0,
                                    transactionRef = d["transactionRef"] as? String ?: "",
                                    purchaseDate = d["purchaseDate"] as? String ?: "",
                                    timestamp = (d["timestamp"] as? Long) ?: System.currentTimeMillis(),
                                    status = d["status"] as? String ?: "Active"
                                )
                                if (purchase.id > 0) {
                                    db.videoDao().insertPurchase(purchase)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing purchases", e)
                        }
                    }
                }
            listeners.add(purchaseListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach video purchase listener", e)
        }

        // 8. Sync Leaves in real-time
        try {
            val leaveListener = fs.collection(COL_LEAVES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        try {
                            for (doc in snapshot.documents) {
                                val d = doc.data ?: continue
                                val req = LeaveRequest(
                                    id = (d["id"] as? Long) ?: (doc.id.toLongOrNull() ?: 0L),
                                    studentId = (d["studentId"] as? Long) ?: 0L,
                                    studentName = d["studentName"] as? String ?: "",
                                    studentIdCode = d["studentIdCode"] as? String ?: "",
                                    className = d["className"] as? String ?: "",
                                    startDate = d["startDate"] as? String ?: "",
                                    endDate = d["endDate"] as? String ?: "",
                                    reason = d["reason"] as? String ?: "",
                                    status = d["status"] as? String ?: "Pending",
                                    adminNote = d["adminNote"] as? String ?: "",
                                    appliedDate = d["appliedDate"] as? String ?: "",
                                    reviewedBy = d["reviewedBy"] as? String ?: "",
                                    reviewedAt = (d["reviewedAt"] as? Long) ?: 0L,
                                    createdAt = (d["createdAt"] as? Long) ?: System.currentTimeMillis()
                                )
                                if (req.id > 0) {
                                    db.leaveDao().insertLeaveRequest(req)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error syncing leaves", e)
                        }
                    }
                }
            listeners.add(leaveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach leave listener", e)
        }
    }

    fun stopRealtimeSync() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    // --- Online Authentication Methods ---

    suspend fun authenticateAdminOnline(mobile: String, pass: String): AdminProfile? = withContext(Dispatchers.IO) {
        val fs = firestore ?: throw Exception("Firebase connection not initialized.")
        try {
            withTimeout(8000) {
                val doc = fs.collection(COL_ADMIN).document("main").get(Source.SERVER).await()
                if (doc.exists()) {
                    val d = doc.data ?: throw Exception("Empty admin profile on server.")
                    val serverMobile = d["mobile"] as? String ?: "9876543210"
                    val serverPass = d["passwordHash"] as? String ?: "admin123"

                    if (serverMobile == mobile.trim() && serverPass == pass.trim()) {
                        val profile = AdminProfile(
                            id = 1,
                            mobile = serverMobile,
                            passwordHash = serverPass,
                            name = d["name"] as? String ?: "Administrator",
                            instituteName = d["instituteName"] as? String ?: "Neol Tube Academy",
                            email = d["email"] as? String ?: "admin@neoltube.edu",
                            securityPin = d["securityPin"] as? String ?: "1234",
                            isTwoFactorEnabled = d["isTwoFactorEnabled"] as? Boolean ?: false,
                            requireOtpOnLogin = d["requireOtpOnLogin"] as? Boolean ?: false,
                            upiId = d["upiId"] as? String ?: "neoltube@upi",
                            upiPayeeName = d["upiPayeeName"] as? String ?: "Neol Tube Academy",
                            upiQrNote = d["upiQrNote"] as? String ?: "Student Fees Payment"
                        )
                        db.adminDao().saveAdminProfile(profile)
                        profile
                    } else {
                        null
                    }
                } else {
                    // Seed initial admin profile to central server on first run
                    val defaultAdmin = AdminProfile()
                    saveAdminProfileOnline(defaultAdmin)
                    if (defaultAdmin.mobile == mobile.trim() && defaultAdmin.passwordHash == pass.trim()) {
                        defaultAdmin
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Admin login error connecting to server", e)
            throw Exception("Could not reach online server. Please check your internet connection: ${e.localizedMessage}")
        }
    }

    suspend fun authenticateStudentOnline(identifier: String, pass: String): Student? = withContext(Dispatchers.IO) {
        val fs = firestore ?: throw Exception("Firebase connection not initialized.")
        try {
            withTimeout(8000) {
                val idClean = identifier.trim()
                // Search by studentIdCode
                var querySnapshot = fs.collection(COL_STUDENTS)
                    .whereEqualTo("studentIdCode", idClean)
                    .get(Source.SERVER)
                    .await()

                // If not found, search by mobile
                if (querySnapshot.isEmpty) {
                    querySnapshot = fs.collection(COL_STUDENTS)
                        .whereEqualTo("mobile", idClean)
                        .get(Source.SERVER)
                        .await()
                }

                if (!querySnapshot.isEmpty) {
                    val matchingDoc = querySnapshot.documents.firstOrNull { doc ->
                        val p = doc.getString("password") ?: ""
                        p == pass.trim()
                    }

                    if (matchingDoc != null) {
                        val d = matchingDoc.data ?: throw Exception("Corrupted student data on server.")
                        val student = Student(
                            id = (d["id"] as? Long) ?: (matchingDoc.id.toLongOrNull() ?: System.currentTimeMillis()),
                            studentIdCode = d["studentIdCode"] as? String ?: "",
                            name = d["name"] as? String ?: "",
                            mobile = d["mobile"] as? String ?: "",
                            password = d["password"] as? String ?: "",
                            familyGroupId = d["familyGroupId"] as? String ?: "",
                            fatherName = d["fatherName"] as? String ?: "",
                            motherName = d["motherName"] as? String ?: "",
                            className = d["className"] as? String ?: "Class 10",
                            rollNumber = d["rollNumber"] as? String ?: "",
                            email = d["email"] as? String ?: "",
                            address = d["address"] as? String ?: "",
                            photoUri = d["photoUri"] as? String ?: "",
                            avatarId = d["avatarId"] as? String ?: "avatar_1",
                            customFields = d["customFields"] as? String ?: "",
                            createdAt = (d["createdAt"] as? Long) ?: System.currentTimeMillis()
                        )
                        db.studentDao().insertStudent(student)
                        student
                    } else {
                        // User exists but password mismatched
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Student online authentication error", e)
            throw Exception("Could not reach online server. Please verify your internet connection: ${e.localizedMessage}")
        }
    }

    // --- Online Write Operations ---

    suspend fun saveAdminProfileOnline(profile: AdminProfile) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "mobile" to profile.mobile,
                "passwordHash" to profile.passwordHash,
                "name" to profile.name,
                "instituteName" to profile.instituteName,
                "email" to profile.email,
                "instituteAddress" to profile.instituteAddress,
                "securityPin" to profile.securityPin,
                "isTwoFactorEnabled" to profile.isTwoFactorEnabled,
                "requireOtpOnLogin" to profile.requireOtpOnLogin,
                "upiId" to profile.upiId,
                "upiPayeeName" to profile.upiPayeeName,
                "upiQrNote" to profile.upiQrNote,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection(COL_ADMIN).document("main").set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload admin profile", e)
        }
    }

    suspend fun saveStudentOnline(student: Student) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        try {
            val docId = if (student.id > 0) student.id.toString() else student.studentIdCode
            val map = hashMapOf(
                "id" to student.id,
                "studentIdCode" to student.studentIdCode,
                "name" to student.name,
                "mobile" to student.mobile,
                "password" to student.password,
                "familyGroupId" to student.familyGroupId,
                "fatherName" to student.fatherName,
                "motherName" to student.motherName,
                "className" to student.className,
                "rollNumber" to student.rollNumber,
                "email" to student.email,
                "address" to student.address,
                "photoUri" to student.photoUri,
                "avatarId" to student.avatarId,
                "customFields" to student.customFields,
                "createdAt" to student.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection(COL_STUDENTS).document(docId).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload student online", e)
        }
    }

    suspend fun deleteStudentOnline(studentId: Long) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        try {
            fs.collection(COL_STUDENTS).document(studentId.toString()).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete student online", e)
        }
    }

    suspend fun saveFeeOnline(fee: FeeRecord) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "id" to fee.id,
                "studentId" to fee.studentId,
                "studentName" to fee.studentName,
                "className" to fee.className,
                "feeType" to fee.feeType,
                "description" to fee.description,
                "month" to fee.month,
                "amount" to fee.amount,
                "dueDate" to fee.dueDate,
                "startDate" to fee.startDate,
                "endDate" to fee.endDate,
                "calculationMode" to fee.calculationMode,
                "applicableDays" to fee.applicableDays,
                "perDayRate" to fee.perDayRate,
                "status" to fee.status,
                "paidDate" to fee.paidDate,
                "transactionId" to fee.transactionId,
                "receiptNumber" to fee.receiptNumber,
                "paymentMethod" to fee.paymentMethod
            )
            fs.collection(COL_FEES).document(fee.id.toString()).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload fee online", e)
        }
    }

    suspend fun saveAttendanceOnline(record: AttendanceRecord) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        try {
            val docId = "${record.studentId}_${record.date}"
            val map = hashMapOf(
                "id" to record.id,
                "studentId" to record.studentId,
                "studentName" to record.studentName,
                "className" to record.className,
                "date" to record.date,
                "isPresent" to record.isPresent,
                "remarks" to record.remarks,
                "status" to record.status
            )
            fs.collection(COL_ATTENDANCE).document(docId).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload attendance online", e)
        }
    }

    suspend fun savePaymentOnline(payment: PaymentRecord) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "id" to payment.id,
                "feeId" to payment.feeId,
                "studentId" to payment.studentId,
                "studentName" to payment.studentName,
                "studentIdCode" to payment.studentIdCode,
                "feeType" to payment.feeType,
                "month" to payment.month,
                "amount" to payment.amount,
                "paymentDateTime" to payment.paymentDateTime,
                "transactionId" to payment.transactionId,
                "orderId" to payment.orderId,
                "paymentStatus" to payment.paymentStatus,
                "paymentMethod" to payment.paymentMethod,
                "receiptNumber" to payment.receiptNumber,
                "verificationNotes" to payment.verificationNotes,
                "isManualAdminPayment" to payment.isManualAdminPayment,
                "verifiedBy" to payment.verifiedBy,
                "screenshotUri" to payment.screenshotUri
            )
            fs.collection(COL_PAYMENTS).document(payment.id.toString()).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload payment online", e)
        }
    }

    suspend fun saveVideoOnline(video: VideoItem) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "id" to video.id,
                "title" to video.title,
                "description" to video.description,
                "subject" to video.subject,
                "duration" to video.duration,
                "instructor" to video.instructor,
                "videoUrl" to video.videoUrl,
                "thumbnailUrl" to video.thumbnailUrl,
                "isPaid" to video.isPaid,
                "price" to video.price,
                "videoQuality" to video.videoQuality,
                "viewsCount" to video.viewsCount,
                "tags" to video.tags,
                "createdAt" to video.createdAt
            )
            fs.collection(COL_VIDEOS).document(video.id.toString()).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload video online", e)
        }
    }

    suspend fun deleteVideoOnline(videoId: Long) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        try {
            fs.collection(COL_VIDEOS).document(videoId.toString()).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete video online", e)
        }
    }

    suspend fun saveVideoPurchaseOnline(purchase: VideoPurchase) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "id" to purchase.id,
                "videoId" to purchase.videoId,
                "studentId" to purchase.studentId,
                "studentName" to purchase.studentName,
                "amountPaid" to purchase.amountPaid,
                "transactionRef" to purchase.transactionRef,
                "purchaseDate" to purchase.purchaseDate,
                "timestamp" to purchase.timestamp,
                "status" to purchase.status
            )
            fs.collection(COL_VIDEO_PURCHASES).document(purchase.id.toString()).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload purchase online", e)
        }
    }

    suspend fun saveLeaveOnline(leave: LeaveRequest) = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "id" to leave.id,
                "studentId" to leave.studentId,
                "studentName" to leave.studentName,
                "studentIdCode" to leave.studentIdCode,
                "className" to leave.className,
                "startDate" to leave.startDate,
                "endDate" to leave.endDate,
                "reason" to leave.reason,
                "status" to leave.status,
                "adminNote" to leave.adminNote,
                "appliedDate" to leave.appliedDate,
                "reviewedBy" to leave.reviewedBy,
                "reviewedAt" to leave.reviewedAt,
                "createdAt" to leave.createdAt
            )
            fs.collection(COL_LEAVES).document(leave.id.toString()).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload leave online", e)
        }
    }
}

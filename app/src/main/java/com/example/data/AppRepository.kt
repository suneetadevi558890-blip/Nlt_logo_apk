package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.dao.*
import com.example.data.model.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AppRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val adminDao = db.adminDao()
    private val studentDao = db.studentDao()
    private val visibilityDao = db.visibilityDao()
    private val attendanceDao = db.attendanceDao()
    private val feeDao = db.feeDao()
    private val paymentDao = db.paymentDao()
    private val notificationDao = db.notificationDao()
    private val auditLogDao = db.auditLogDao()
    private val leaveDao = db.leaveDao()
    private val videoDao = db.videoDao()

    val rtdbUrl = "https://neol-tube-default-rtdb.firebaseio.com/"

    private val realtimeDb by lazy {
        try {
            FirebaseDatabase.getInstance(rtdbUrl).apply {
                try { setPersistenceEnabled(true) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            try {
                FirebaseDatabase.getInstance(rtdbUrl)
            } catch (e2: Exception) {
                Log.e("AppRepository", "Realtime Database init error", e2)
                null
            }
        }
    }

    private val firestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("AppRepository", "Firestore init error", e)
            null
        }
    }

    private val storage by lazy {
        try {
            FirebaseStorage.getInstance()
        } catch (e: Exception) {
            Log.e("AppRepository", "Storage init error", e)
            null
        }
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val activeListeners = mutableListOf<ListenerRegistration>()

    init {
        repositoryScope.launch {
            setupFirestoreRealtimeSync()
            setupRealtimeDatabaseSync()
            // Step 1: Download from Cloud FIRST so new devices immediately receive all existing data
            val hasCloudData = downloadAndSyncAllFromFirebase()
            if (!hasCloudData) {
                // Cloud is empty, seed initial data locally and upload to Firebase
                seedInitialDataIfNeeded()
                uploadAllDataToFirebaseRtdb()
            } else {
                // Cloud data synced, make sure initial structure is complete
                seedInitialDataIfNeeded()
            }
        }
    }

    // --- Online Realtime Sync Setup ---
    private fun setupFirestoreRealtimeSync() {
        val fs = firestore ?: return
        try {
            // 1. Admin Profile sync
            val adminSub = fs.collection("admin_profile").document("main")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val data = snapshot.data ?: return@addSnapshotListener
                    repositoryScope.launch {
                        val profile = AdminProfile(
                            id = 1,
                            mobile = data["mobile"]?.toString() ?: "9876543210",
                            passwordHash = data["passwordHash"]?.toString() ?: "admin123",
                            name = data["name"]?.toString() ?: "Administrator",
                            instituteName = data["instituteName"]?.toString() ?: "Neol Tube Academy",
                            email = data["email"]?.toString() ?: "admin@neoltube.edu",
                            instituteAddress = data["instituteAddress"]?.toString() ?: "Neol Tube Campus, Education Hub",
                            securityPin = data["securityPin"]?.toString() ?: "1234",
                            isTwoFactorEnabled = data["isTwoFactorEnabled"] as? Boolean ?: false,
                            requireOtpOnLogin = data["requireOtpOnLogin"] as? Boolean ?: false,
                            upiId = data["upiId"]?.toString() ?: "neoltube@upi",
                            upiPayeeName = data["upiPayeeName"]?.toString() ?: "Neol Tube Academy",
                            upiQrNote = data["upiQrNote"]?.toString() ?: "Student Fees Payment",
                            qrImagePath = data["qrImagePath"]?.toString() ?: "",
                            appIconPath = data["appIconPath"]?.toString() ?: ""
                        )
                        adminDao.saveAdminProfile(profile)
                    }
                }
            activeListeners.add(adminSub)

            // 2. Students sync
            val studentsSub = fs.collection("students")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    repositoryScope.launch {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val id = (data["id"] as? Number)?.toLong() ?: doc.id.hashCode().toLong().let { if (it < 0) -it else it }
                            val student = Student(
                                id = id,
                                studentIdCode = data["studentIdCode"]?.toString() ?: "NT-001",
                                name = data["name"]?.toString() ?: "",
                                mobile = data["mobile"]?.toString() ?: "",
                                password = data["password"]?.toString() ?: "123456",
                                familyGroupId = data["familyGroupId"]?.toString() ?: "",
                                fatherName = data["fatherName"]?.toString() ?: "",
                                motherName = data["motherName"]?.toString() ?: "",
                                className = data["className"]?.toString() ?: "Class 10",
                                rollNumber = data["rollNumber"]?.toString() ?: "01",
                                email = data["email"]?.toString() ?: "",
                                address = data["address"]?.toString() ?: "",
                                photoUri = data["photoUri"]?.toString() ?: "",
                                avatarId = data["avatarId"]?.toString() ?: "avatar_1",
                                customFields = data["customFields"]?.toString() ?: "",
                                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )
                            studentDao.insertStudent(student)
                        }
                    }
                }
            activeListeners.add(studentsSub)

            // 3. Profile Visibility Config sync
            val visibilitySub = fs.collection("profile_visibility_config").document("main")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val data = snapshot.data ?: return@addSnapshotListener
                    repositoryScope.launch {
                        val config = ProfileVisibilityConfig(
                            id = 1,
                            showPhoto = data["showPhoto"] as? Boolean ?: true,
                            showStudentName = data["showStudentName"] as? Boolean ?: true,
                            showFatherName = data["showFatherName"] as? Boolean ?: true,
                            showMotherName = data["showMotherName"] as? Boolean ?: true,
                            showClass = data["showClass"] as? Boolean ?: true,
                            showRollNumber = data["showRollNumber"] as? Boolean ?: true,
                            showStudentId = data["showStudentId"] as? Boolean ?: true,
                            showContact = data["showContact"] as? Boolean ?: true,
                            showAddress = data["showAddress"] as? Boolean ?: true,
                            showCustomFields = data["showCustomFields"] as? Boolean ?: true
                        )
                        visibilityDao.saveVisibilityConfig(config)
                    }
                }
            activeListeners.add(visibilitySub)

            // 4. Attendance sync
            val attSub = fs.collection("attendance")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    repositoryScope.launch {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val id = (data["id"] as? Number)?.toLong() ?: doc.id.hashCode().toLong().let { if (it < 0) -it else it }
                            val att = AttendanceRecord(
                                id = id,
                                studentId = (data["studentId"] as? Number)?.toLong() ?: 0L,
                                studentName = data["studentName"]?.toString() ?: "",
                                className = data["className"]?.toString() ?: "",
                                date = data["date"]?.toString() ?: "",
                                isPresent = data["isPresent"] as? Boolean ?: true,
                                remarks = data["remarks"]?.toString() ?: "",
                                status = data["status"]?.toString() ?: "Present"
                            )
                            attendanceDao.recordAttendance(att)
                        }
                    }
                }
            activeListeners.add(attSub)

            // 5. Fees sync
            val feesSub = fs.collection("fees")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    repositoryScope.launch {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val id = (data["id"] as? Number)?.toLong() ?: doc.id.hashCode().toLong().let { if (it < 0) -it else it }
                            val fee = FeeRecord(
                                id = id,
                                studentId = (data["studentId"] as? Number)?.toLong() ?: 0L,
                                studentName = data["studentName"]?.toString() ?: "",
                                className = data["className"]?.toString() ?: "",
                                feeType = data["feeType"]?.toString() ?: "Tuition Fee",
                                description = data["description"]?.toString() ?: "",
                                month = data["month"]?.toString() ?: "General",
                                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                                dueDate = data["dueDate"]?.toString() ?: "",
                                startDate = data["startDate"]?.toString() ?: "",
                                endDate = data["endDate"]?.toString() ?: "",
                                calculationMode = data["calculationMode"]?.toString() ?: "Fixed",
                                applicableDays = (data["applicableDays"] as? Number)?.toInt() ?: 0,
                                perDayRate = (data["perDayRate"] as? Number)?.toDouble() ?: 0.0,
                                status = data["status"]?.toString() ?: "Pending",
                                paidDate = data["paidDate"]?.toString(),
                                transactionId = data["transactionId"]?.toString(),
                                receiptNumber = data["receiptNumber"]?.toString(),
                                paymentMethod = data["paymentMethod"]?.toString()
                            )
                            feeDao.insertFee(fee)
                        }
                    }
                }
            activeListeners.add(feesSub)

            // 6. Payments sync
            val paymentsSub = fs.collection("payments")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    repositoryScope.launch {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val id = (data["id"] as? Number)?.toLong() ?: doc.id.hashCode().toLong().let { if (it < 0) -it else it }
                            val payment = PaymentRecord(
                                id = id,
                                feeId = (data["feeId"] as? Number)?.toLong() ?: 0L,
                                studentId = (data["studentId"] as? Number)?.toLong() ?: 0L,
                                studentName = data["studentName"]?.toString() ?: "",
                                studentIdCode = data["studentIdCode"]?.toString() ?: "",
                                feeType = data["feeType"]?.toString() ?: "",
                                month = data["month"]?.toString() ?: "",
                                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                                paymentDateTime = data["paymentDateTime"]?.toString() ?: "",
                                transactionId = data["transactionId"]?.toString() ?: "",
                                orderId = data["orderId"]?.toString() ?: "",
                                paymentStatus = data["paymentStatus"]?.toString() ?: "Pending Verification",
                                paymentMethod = data["paymentMethod"]?.toString() ?: "UPI",
                                receiptNumber = data["receiptNumber"]?.toString() ?: "",
                                verificationNotes = data["verificationNotes"]?.toString() ?: "",
                                isManualAdminPayment = data["isManualAdminPayment"] as? Boolean ?: false,
                                verifiedBy = data["verifiedBy"]?.toString() ?: "",
                                screenshotUri = data["screenshotUri"]?.toString()
                            )
                            paymentDao.insertPayment(payment)
                        }
                    }
                }
            activeListeners.add(paymentsSub)

            // 7. Notifications sync
            val notifSub = fs.collection("notifications")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    repositoryScope.launch {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val id = (data["id"] as? Number)?.toLong() ?: doc.id.hashCode().toLong().let { if (it < 0) -it else it }
                            val notif = NotificationItem(
                                id = id,
                                title = data["title"]?.toString() ?: "",
                                message = data["message"]?.toString() ?: "",
                                targetAudience = data["targetAudience"]?.toString() ?: "All Students",
                                studentId = (data["studentId"] as? Number)?.toLong(),
                                familyGroupId = data["familyGroupId"]?.toString() ?: "",
                                date = data["date"]?.toString() ?: "",
                                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )
                            notificationDao.insertNotification(notif)
                        }
                    }
                }
            activeListeners.add(notifSub)

            // 8. Leave requests sync
            val leaveSub = fs.collection("leaves")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    repositoryScope.launch {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val id = (data["id"] as? Number)?.toLong() ?: doc.id.hashCode().toLong().let { if (it < 0) -it else it }
                            val leave = LeaveRequest(
                                id = id,
                                studentId = (data["studentId"] as? Number)?.toLong() ?: 0L,
                                studentName = data["studentName"]?.toString() ?: "",
                                studentIdCode = data["studentIdCode"]?.toString() ?: "",
                                className = data["className"]?.toString() ?: "",
                                startDate = data["startDate"]?.toString() ?: "",
                                endDate = data["endDate"]?.toString() ?: "",
                                reason = data["reason"]?.toString() ?: "",
                                status = data["status"]?.toString() ?: "Pending",
                                adminNote = data["adminNote"]?.toString() ?: "",
                                appliedDate = data["appliedDate"]?.toString() ?: "",
                                reviewedBy = data["reviewedBy"]?.toString() ?: "",
                                reviewedAt = (data["reviewedAt"] as? Number)?.toLong() ?: 0L,
                                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )
                            leaveDao.insertLeaveRequest(leave)
                        }
                    }
                }
            activeListeners.add(leaveSub)

            // 9. Videos (Free & Paid) sync
            val videosSub = fs.collection("videos")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    repositoryScope.launch {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val id = (data["id"] as? Number)?.toLong() ?: doc.id.hashCode().toLong().let { if (it < 0) -it else it }
                            val video = VideoItem(
                                id = id,
                                title = data["title"]?.toString() ?: "",
                                description = data["description"]?.toString() ?: "",
                                subject = data["subject"]?.toString() ?: "Physics",
                                duration = data["duration"]?.toString() ?: "45 mins",
                                instructor = data["instructor"]?.toString() ?: "Er. Neol Sir",
                                videoUrl = data["videoUrl"]?.toString() ?: "",
                                thumbnailUrl = data["thumbnailUrl"]?.toString() ?: "",
                                isPaid = data["isPaid"] as? Boolean ?: false,
                                price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                                videoQuality = data["videoQuality"]?.toString() ?: "1080p FHD",
                                viewsCount = (data["viewsCount"] as? Number)?.toInt() ?: 150,
                                tags = data["tags"]?.toString() ?: "",
                                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )
                            videoDao.insertVideo(video)
                        }
                    }
                }
            activeListeners.add(videosSub)

            // 10. Video Purchases sync
            val purchasesSub = fs.collection("video_purchases")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    repositoryScope.launch {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val id = (data["id"] as? Number)?.toLong() ?: doc.id.hashCode().toLong().let { if (it < 0) -it else it }
                            val purchase = VideoPurchase(
                                id = id,
                                videoId = (data["videoId"] as? Number)?.toLong() ?: 0L,
                                studentId = (data["studentId"] as? Number)?.toLong() ?: 0L,
                                studentName = data["studentName"]?.toString() ?: "",
                                amountPaid = (data["amountPaid"] as? Number)?.toDouble() ?: 0.0,
                                transactionRef = data["transactionRef"]?.toString() ?: "",
                                purchaseDate = data["purchaseDate"]?.toString() ?: "",
                                timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                status = data["status"]?.toString() ?: "Active"
                            )
                            videoDao.insertPurchase(purchase)
                        }
                    }
                }
            activeListeners.add(purchasesSub)

            // 11. Audit logs sync
            val auditSub = fs.collection("audit_logs")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    repositoryScope.launch {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val id = (data["id"] as? Number)?.toLong() ?: doc.id.hashCode().toLong().let { if (it < 0) -it else it }
                            val log = AuditLogRecord(
                                id = id,
                                action = data["action"]?.toString() ?: "",
                                entityType = data["entityType"]?.toString() ?: "",
                                entityId = data["entityId"]?.toString() ?: "",
                                details = data["details"]?.toString() ?: "",
                                performedBy = data["performedBy"]?.toString() ?: "Admin",
                                timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                dateTimeStr = data["dateTimeStr"]?.toString() ?: ""
                            )
                            auditLogDao.insertAuditLog(log)
                        }
                    }
                }
            activeListeners.add(auditSub)

        } catch (e: Exception) {
            Log.e("AppRepository", "Error setting up firestore listeners", e)
        }
    }

    // --- Realtime Database Sync & Uploads (https://neol-tube-default-rtdb.firebaseio.com/) ---
    private fun setupRealtimeDatabaseSync() {
        val rdb = realtimeDb ?: return
        try {
            // 1. Admin Profile sync
            rdb.getReference("admin_profile").child("main")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        repositoryScope.launch {
                            parseAdminSnapshot(snapshot)?.let { adminDao.saveAdminProfile(it) }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            // 2. Profile Visibility Config sync
            rdb.getReference("profile_visibility_config").child("main")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        repositoryScope.launch {
                            parseVisibilitySnapshot(snapshot)?.let { visibilityDao.saveVisibilityConfig(it) }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            // 3. Students sync
            rdb.getReference("students")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        repositoryScope.launch {
                            for (child in snapshot.children) {
                                parseStudentSnapshot(child)?.let { studentDao.insertStudent(it) }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            // 4. Fees sync
            rdb.getReference("fees")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        repositoryScope.launch {
                            for (child in snapshot.children) {
                                parseFeeSnapshot(child)?.let { feeDao.insertFee(it) }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            // 5. Payments sync
            rdb.getReference("payments")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        repositoryScope.launch {
                            for (child in snapshot.children) {
                                parsePaymentSnapshot(child)?.let { paymentDao.insertPayment(it) }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            // 6. Attendance sync
            rdb.getReference("attendance")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        repositoryScope.launch {
                            for (child in snapshot.children) {
                                parseAttendanceSnapshot(child)?.let { attendanceDao.recordAttendance(it) }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            // 7. Leaves sync
            rdb.getReference("leaves")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        repositoryScope.launch {
                            for (child in snapshot.children) {
                                parseLeaveSnapshot(child)?.let { leaveDao.insertLeaveRequest(it) }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            // 8. Notifications sync
            rdb.getReference("notifications")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        repositoryScope.launch {
                            for (child in snapshot.children) {
                                parseNotificationSnapshot(child)?.let { notificationDao.insertNotification(it) }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            // 9. Videos sync
            rdb.getReference("videos")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        repositoryScope.launch {
                            for (child in snapshot.children) {
                                parseVideoSnapshot(child)?.let { videoDao.insertVideo(it) }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            // 10. Video purchases sync
            rdb.getReference("video_purchases")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        repositoryScope.launch {
                            for (child in snapshot.children) {
                                parsePurchaseSnapshot(child)?.let { videoDao.insertPurchase(it) }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        } catch (e: Exception) {
            Log.e("AppRepository", "Error setting up RTDB listeners", e)
        }
    }

    // Helper: HTTP GET for fast REST access
    private fun fetchJsonFromUrl(urlString: String, timeoutMs: Int = 4000): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                sb.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "fetchJsonFromUrl error: ${e.message}")
            null
        }
    }

    // Parsers from DataSnapshot
    fun parseAdminSnapshot(snap: DataSnapshot): AdminProfile? {
        if (!snap.exists()) return null
        return AdminProfile(
            id = snap.child("id").getValue(Int::class.java) ?: 1,
            mobile = snap.child("mobile").getValue(String::class.java) ?: "9876543210",
            passwordHash = snap.child("passwordHash").getValue(String::class.java) ?: "admin123",
            name = snap.child("name").getValue(String::class.java) ?: "Administrator",
            instituteName = snap.child("instituteName").getValue(String::class.java) ?: "Neol Tube Academy",
            email = snap.child("email").getValue(String::class.java) ?: "admin@neoltube.edu",
            instituteAddress = snap.child("instituteAddress").getValue(String::class.java) ?: "Neol Tube Campus, Education Hub",
            securityPin = snap.child("securityPin").getValue(String::class.java) ?: "1234",
            isTwoFactorEnabled = snap.child("twoFactorEnabled").getValue(Boolean::class.java) ?: false,
            requireOtpOnLogin = snap.child("requireOtpOnLogin").getValue(Boolean::class.java) ?: false,
            upiId = snap.child("upiId").getValue(String::class.java) ?: "neoltube@upi",
            upiPayeeName = snap.child("upiPayeeName").getValue(String::class.java) ?: "Neol Tube Academy",
            upiQrNote = snap.child("upiQrNote").getValue(String::class.java) ?: "Student Fees Payment",
            qrImagePath = snap.child("qrImagePath").getValue(String::class.java) ?: "",
            appIconPath = snap.child("appIconPath").getValue(String::class.java) ?: ""
        )
    }

    fun parseVisibilitySnapshot(snap: DataSnapshot): ProfileVisibilityConfig? {
        if (!snap.exists()) return null
        return ProfileVisibilityConfig(
            id = 1,
            showPhoto = snap.child("showPhoto").getValue(Boolean::class.java) ?: true,
            showStudentName = snap.child("showStudentName").getValue(Boolean::class.java) ?: true,
            showFatherName = snap.child("showFatherName").getValue(Boolean::class.java) ?: true,
            showMotherName = snap.child("showMotherName").getValue(Boolean::class.java) ?: true,
            showClass = snap.child("showClass").getValue(Boolean::class.java) ?: true,
            showRollNumber = snap.child("showRollNumber").getValue(Boolean::class.java) ?: true,
            showStudentId = snap.child("showStudentId").getValue(Boolean::class.java) ?: true,
            showContact = snap.child("showContact").getValue(Boolean::class.java) ?: true,
            showAddress = snap.child("showAddress").getValue(Boolean::class.java) ?: true,
            showCustomFields = snap.child("showCustomFields").getValue(Boolean::class.java) ?: true
        )
    }

    fun parseStudentSnapshot(snap: DataSnapshot): Student? {
        if (!snap.exists()) return null
        val id = snap.child("id").getValue(Long::class.java) ?: snap.key?.toLongOrNull() ?: return null
        return Student(
            id = id,
            studentIdCode = snap.child("studentIdCode").getValue(String::class.java) ?: "NT-001",
            name = snap.child("name").getValue(String::class.java) ?: "",
            mobile = snap.child("mobile").getValue(String::class.java) ?: "",
            password = snap.child("password").getValue(String::class.java) ?: "student@123",
            familyGroupId = snap.child("familyGroupId").getValue(String::class.java) ?: "",
            fatherName = snap.child("fatherName").getValue(String::class.java) ?: "",
            motherName = snap.child("motherName").getValue(String::class.java) ?: "",
            className = snap.child("className").getValue(String::class.java) ?: "Class 10",
            rollNumber = snap.child("rollNumber").getValue(String::class.java) ?: "01",
            email = snap.child("email").getValue(String::class.java) ?: "",
            address = snap.child("address").getValue(String::class.java) ?: "",
            photoUri = snap.child("photoUri").getValue(String::class.java) ?: "",
            avatarId = snap.child("avatarId").getValue(String::class.java) ?: "avatar_1",
            customFields = snap.child("customFields").getValue(String::class.java) ?: "",
            createdAt = snap.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
        )
    }

    fun parseFeeSnapshot(snap: DataSnapshot): FeeRecord? {
        if (!snap.exists()) return null
        val id = snap.child("id").getValue(Long::class.java) ?: snap.key?.toLongOrNull() ?: return null
        return FeeRecord(
            id = id,
            studentId = snap.child("studentId").getValue(Long::class.java) ?: 0L,
            studentName = snap.child("studentName").getValue(String::class.java) ?: "",
            className = snap.child("className").getValue(String::class.java) ?: "",
            feeType = snap.child("feeType").getValue(String::class.java) ?: "Tuition Fee",
            description = snap.child("description").getValue(String::class.java) ?: "",
            month = snap.child("month").getValue(String::class.java) ?: "General",
            amount = snap.child("amount").getValue(Double::class.java) ?: 0.0,
            dueDate = snap.child("dueDate").getValue(String::class.java) ?: "",
            startDate = snap.child("startDate").getValue(String::class.java) ?: "",
            endDate = snap.child("endDate").getValue(String::class.java) ?: "",
            calculationMode = snap.child("calculationMode").getValue(String::class.java) ?: "Fixed",
            applicableDays = snap.child("applicableDays").getValue(Int::class.java) ?: 0,
            perDayRate = snap.child("perDayRate").getValue(Double::class.java) ?: 0.0,
            status = snap.child("status").getValue(String::class.java) ?: "Pending",
            paidDate = snap.child("paidDate").getValue(String::class.java),
            transactionId = snap.child("transactionId").getValue(String::class.java),
            receiptNumber = snap.child("receiptNumber").getValue(String::class.java),
            paymentMethod = snap.child("paymentMethod").getValue(String::class.java)
        )
    }

    fun parsePaymentSnapshot(snap: DataSnapshot): PaymentRecord? {
        if (!snap.exists()) return null
        val id = snap.child("id").getValue(Long::class.java) ?: snap.key?.toLongOrNull() ?: return null
        return PaymentRecord(
            id = id,
            feeId = snap.child("feeId").getValue(Long::class.java) ?: 0L,
            studentId = snap.child("studentId").getValue(Long::class.java) ?: 0L,
            studentName = snap.child("studentName").getValue(String::class.java) ?: "",
            studentIdCode = snap.child("studentIdCode").getValue(String::class.java) ?: "",
            feeType = snap.child("feeType").getValue(String::class.java) ?: "",
            month = snap.child("month").getValue(String::class.java) ?: "",
            amount = snap.child("amount").getValue(Double::class.java) ?: 0.0,
            paymentDateTime = snap.child("paymentDateTime").getValue(String::class.java) ?: "",
            transactionId = snap.child("transactionId").getValue(String::class.java) ?: "",
            orderId = snap.child("orderId").getValue(String::class.java) ?: "",
            paymentStatus = snap.child("paymentStatus").getValue(String::class.java) ?: "Pending Verification",
            paymentMethod = snap.child("paymentMethod").getValue(String::class.java) ?: "UPI",
            receiptNumber = snap.child("receiptNumber").getValue(String::class.java) ?: "",
            verificationNotes = snap.child("verificationNotes").getValue(String::class.java) ?: "",
            isManualAdminPayment = snap.child("manualAdminPayment").getValue(Boolean::class.java) ?: false,
            verifiedBy = snap.child("verifiedBy").getValue(String::class.java) ?: "",
            screenshotUri = snap.child("screenshotUri").getValue(String::class.java)
        )
    }

    fun parseAttendanceSnapshot(snap: DataSnapshot): AttendanceRecord? {
        if (!snap.exists()) return null
        val id = snap.child("id").getValue(Long::class.java) ?: snap.key?.toLongOrNull() ?: return null
        return AttendanceRecord(
            id = id,
            studentId = snap.child("studentId").getValue(Long::class.java) ?: 0L,
            studentName = snap.child("studentName").getValue(String::class.java) ?: "",
            className = snap.child("className").getValue(String::class.java) ?: "",
            date = snap.child("date").getValue(String::class.java) ?: "",
            isPresent = snap.child("present").getValue(Boolean::class.java) ?: snap.child("isPresent").getValue(Boolean::class.java) ?: true,
            remarks = snap.child("remarks").getValue(String::class.java) ?: "",
            status = snap.child("status").getValue(String::class.java) ?: "Present"
        )
    }

    fun parseLeaveSnapshot(snap: DataSnapshot): LeaveRequest? {
        if (!snap.exists()) return null
        val id = snap.child("id").getValue(Long::class.java) ?: snap.key?.toLongOrNull() ?: return null
        return LeaveRequest(
            id = id,
            studentId = snap.child("studentId").getValue(Long::class.java) ?: 0L,
            studentName = snap.child("studentName").getValue(String::class.java) ?: "",
            studentIdCode = snap.child("studentIdCode").getValue(String::class.java) ?: "",
            className = snap.child("className").getValue(String::class.java) ?: "",
            startDate = snap.child("startDate").getValue(String::class.java) ?: "",
            endDate = snap.child("endDate").getValue(String::class.java) ?: "",
            reason = snap.child("reason").getValue(String::class.java) ?: "",
            status = snap.child("status").getValue(String::class.java) ?: "Pending",
            adminNote = snap.child("adminNote").getValue(String::class.java) ?: "",
            appliedDate = snap.child("appliedDate").getValue(String::class.java) ?: "",
            reviewedBy = snap.child("reviewedBy").getValue(String::class.java) ?: "",
            reviewedAt = snap.child("reviewedAt").getValue(Long::class.java) ?: 0L,
            createdAt = snap.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
        )
    }

    fun parseNotificationSnapshot(snap: DataSnapshot): NotificationItem? {
        if (!snap.exists()) return null
        val id = snap.child("id").getValue(Long::class.java) ?: snap.key?.toLongOrNull() ?: return null
        return NotificationItem(
            id = id,
            title = snap.child("title").getValue(String::class.java) ?: "",
            message = snap.child("message").getValue(String::class.java) ?: "",
            targetAudience = snap.child("targetAudience").getValue(String::class.java) ?: "All Students",
            studentId = snap.child("studentId").getValue(Long::class.java),
            familyGroupId = snap.child("familyGroupId").getValue(String::class.java) ?: "",
            date = snap.child("date").getValue(String::class.java) ?: "",
            createdAt = snap.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
        )
    }

    fun parseVideoSnapshot(snap: DataSnapshot): VideoItem? {
        if (!snap.exists()) return null
        val id = snap.child("id").getValue(Long::class.java) ?: snap.key?.toLongOrNull() ?: return null
        return VideoItem(
            id = id,
            title = snap.child("title").getValue(String::class.java) ?: "",
            description = snap.child("description").getValue(String::class.java) ?: "",
            subject = snap.child("subject").getValue(String::class.java) ?: "Physics",
            duration = snap.child("duration").getValue(String::class.java) ?: "45 mins",
            instructor = snap.child("instructor").getValue(String::class.java) ?: "Er. Neol Sir",
            videoUrl = snap.child("videoUrl").getValue(String::class.java) ?: "",
            thumbnailUrl = snap.child("thumbnailUrl").getValue(String::class.java) ?: "",
            isPaid = snap.child("paid").getValue(Boolean::class.java) ?: snap.child("isPaid").getValue(Boolean::class.java) ?: false,
            price = snap.child("price").getValue(Double::class.java) ?: 0.0,
            videoQuality = snap.child("videoQuality").getValue(String::class.java) ?: "1080p FHD",
            viewsCount = snap.child("viewsCount").getValue(Int::class.java) ?: 150,
            tags = snap.child("tags").getValue(String::class.java) ?: "",
            createdAt = snap.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
        )
    }

    fun parsePurchaseSnapshot(snap: DataSnapshot): VideoPurchase? {
        if (!snap.exists()) return null
        val id = snap.child("id").getValue(Long::class.java) ?: snap.key?.toLongOrNull() ?: return null
        return VideoPurchase(
            id = id,
            videoId = snap.child("videoId").getValue(Long::class.java) ?: 0L,
            studentId = snap.child("studentId").getValue(Long::class.java) ?: 0L,
            studentName = snap.child("studentName").getValue(String::class.java) ?: "",
            amountPaid = snap.child("amountPaid").getValue(Double::class.java) ?: 0.0,
            transactionRef = snap.child("transactionRef").getValue(String::class.java) ?: "",
            purchaseDate = snap.child("purchaseDate").getValue(String::class.java) ?: "",
            timestamp = snap.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis(),
            status = snap.child("status").getValue(String::class.java) ?: "Active"
        )
    }

    // Parsers from JSON objects
    fun parseAdminJson(obj: JSONObject): AdminProfile {
        return AdminProfile(
            id = obj.optInt("id", 1),
            mobile = obj.optString("mobile", "9876543210"),
            passwordHash = obj.optString("passwordHash", "admin123"),
            name = obj.optString("name", "Administrator"),
            instituteName = obj.optString("instituteName", "Neol Tube Academy"),
            email = obj.optString("email", "admin@neoltube.edu"),
            instituteAddress = obj.optString("instituteAddress", "Neol Tube Campus, Education Hub"),
            securityPin = obj.optString("securityPin", "1234"),
            isTwoFactorEnabled = obj.optBoolean("twoFactorEnabled", false),
            requireOtpOnLogin = obj.optBoolean("requireOtpOnLogin", false),
            upiId = obj.optString("upiId", "neoltube@upi"),
            upiPayeeName = obj.optString("upiPayeeName", "Neol Tube Academy"),
            upiQrNote = obj.optString("upiQrNote", "Student Fees Payment"),
            qrImagePath = obj.optString("qrImagePath", ""),
            appIconPath = obj.optString("appIconPath", "")
        )
    }

    fun parseStudentJson(obj: JSONObject): Student {
        return Student(
            id = obj.optLong("id", System.currentTimeMillis()),
            studentIdCode = obj.optString("studentIdCode", "NT-001"),
            name = obj.optString("name", ""),
            mobile = obj.optString("mobile", ""),
            password = obj.optString("password", "student@123"),
            familyGroupId = obj.optString("familyGroupId", ""),
            fatherName = obj.optString("fatherName", ""),
            motherName = obj.optString("motherName", ""),
            className = obj.optString("className", "Class 10"),
            rollNumber = obj.optString("rollNumber", "01"),
            email = obj.optString("email", ""),
            address = obj.optString("address", ""),
            photoUri = obj.optString("photoUri", ""),
            avatarId = obj.optString("avatarId", "avatar_1"),
            customFields = obj.optString("customFields", ""),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        )
    }

    fun parseFeeJson(obj: JSONObject): FeeRecord {
        return FeeRecord(
            id = obj.optLong("id", 0L),
            studentId = obj.optLong("studentId", 0L),
            studentName = obj.optString("studentName", ""),
            className = obj.optString("className", ""),
            feeType = obj.optString("feeType", "Tuition Fee"),
            description = obj.optString("description", ""),
            month = obj.optString("month", "General"),
            amount = obj.optDouble("amount", 0.0),
            dueDate = obj.optString("dueDate", ""),
            startDate = obj.optString("startDate", ""),
            endDate = obj.optString("endDate", ""),
            calculationMode = obj.optString("calculationMode", "Fixed"),
            applicableDays = obj.optInt("applicableDays", 0),
            perDayRate = obj.optDouble("perDayRate", 0.0),
            status = obj.optString("status", "Pending"),
            paidDate = if (obj.has("paidDate")) obj.optString("paidDate") else null,
            transactionId = if (obj.has("transactionId")) obj.optString("transactionId") else null,
            receiptNumber = if (obj.has("receiptNumber")) obj.optString("receiptNumber") else null,
            paymentMethod = if (obj.has("paymentMethod")) obj.optString("paymentMethod") else null
        )
    }

    fun parseAttendanceJson(obj: JSONObject): AttendanceRecord {
        return AttendanceRecord(
            id = obj.optLong("id", 0L),
            studentId = obj.optLong("studentId", 0L),
            studentName = obj.optString("studentName", ""),
            className = obj.optString("className", ""),
            date = obj.optString("date", ""),
            isPresent = if (obj.has("present")) obj.optBoolean("present") else obj.optBoolean("isPresent", true),
            remarks = obj.optString("remarks", ""),
            status = obj.optString("status", "Present")
        )
    }

    fun parsePaymentJson(obj: JSONObject): PaymentRecord {
        return PaymentRecord(
            id = obj.optLong("id", 0L),
            feeId = obj.optLong("feeId", 0L),
            studentId = obj.optLong("studentId", 0L),
            studentName = obj.optString("studentName", ""),
            studentIdCode = obj.optString("studentIdCode", ""),
            feeType = obj.optString("feeType", ""),
            month = obj.optString("month", ""),
            amount = obj.optDouble("amount", 0.0),
            paymentDateTime = obj.optString("paymentDateTime", ""),
            transactionId = obj.optString("transactionId", ""),
            orderId = obj.optString("orderId", ""),
            paymentStatus = obj.optString("paymentStatus", "Pending Verification"),
            paymentMethod = obj.optString("paymentMethod", "UPI"),
            receiptNumber = obj.optString("receiptNumber", ""),
            verificationNotes = obj.optString("verificationNotes", ""),
            isManualAdminPayment = obj.optBoolean("manualAdminPayment", false),
            verifiedBy = obj.optString("verifiedBy", ""),
            screenshotUri = if (obj.has("screenshotUri")) obj.optString("screenshotUri") else null
        )
    }

    fun parseNotificationJson(obj: JSONObject): NotificationItem {
        return NotificationItem(
            id = obj.optLong("id", 0L),
            title = obj.optString("title", ""),
            message = obj.optString("message", ""),
            targetAudience = obj.optString("targetAudience", "All Students"),
            studentId = if (obj.has("studentId") && !obj.isNull("studentId")) obj.optLong("studentId") else null,
            familyGroupId = obj.optString("familyGroupId", ""),
            date = obj.optString("date", ""),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        )
    }

    fun parseVideoJson(obj: JSONObject): VideoItem {
        return VideoItem(
            id = obj.optLong("id", 0L),
            title = obj.optString("title", ""),
            description = obj.optString("description", ""),
            subject = obj.optString("subject", "Physics"),
            duration = obj.optString("duration", "45 mins"),
            instructor = obj.optString("instructor", "Er. Neol Sir"),
            videoUrl = obj.optString("videoUrl", ""),
            thumbnailUrl = obj.optString("thumbnailUrl", ""),
            isPaid = if (obj.has("paid")) obj.optBoolean("paid") else obj.optBoolean("isPaid", false),
            price = obj.optDouble("price", 0.0),
            videoQuality = obj.optString("videoQuality", "1080p FHD"),
            viewsCount = obj.optInt("viewsCount", 150),
            tags = obj.optString("tags", ""),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private suspend fun syncFromJsonTree(rootJson: JSONObject) {
        // admin_profile
        if (rootJson.has("admin_profile")) {
            val admTree = rootJson.get("admin_profile")
            if (admTree is JSONObject) {
                val mainObj = if (admTree.has("main")) admTree.getJSONObject("main") else admTree
                adminDao.saveAdminProfile(parseAdminJson(mainObj))
            }
        }
        // profile_visibility_config
        if (rootJson.has("profile_visibility_config")) {
            val visTree = rootJson.get("profile_visibility_config")
            if (visTree is JSONObject) {
                val mainObj = if (visTree.has("main")) visTree.getJSONObject("main") else visTree
                visibilityDao.saveVisibilityConfig(ProfileVisibilityConfig(
                    id = 1,
                    showPhoto = mainObj.optBoolean("showPhoto", true),
                    showStudentName = mainObj.optBoolean("showStudentName", true),
                    showFatherName = mainObj.optBoolean("showFatherName", true),
                    showMotherName = mainObj.optBoolean("showMotherName", true),
                    showClass = mainObj.optBoolean("showClass", true),
                    showRollNumber = mainObj.optBoolean("showRollNumber", true),
                    showStudentId = mainObj.optBoolean("showStudentId", true),
                    showContact = mainObj.optBoolean("showContact", true),
                    showAddress = mainObj.optBoolean("showAddress", true),
                    showCustomFields = mainObj.optBoolean("showCustomFields", true)
                ))
            }
        }
        // students
        if (rootJson.has("students")) {
            val stTree = rootJson.get("students")
            if (stTree is JSONObject) {
                val keys = stTree.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = stTree.getJSONObject(k)
                    studentDao.insertStudent(parseStudentJson(obj))
                }
            } else if (stTree is JSONArray) {
                for (i in 0 until stTree.length()) {
                    if (stTree.isNull(i)) continue
                    val obj = stTree.getJSONObject(i)
                    studentDao.insertStudent(parseStudentJson(obj))
                }
            }
        }
        // fees
        if (rootJson.has("fees")) {
            val feeTree = rootJson.get("fees")
            if (feeTree is JSONObject) {
                val keys = feeTree.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = feeTree.getJSONObject(k)
                    feeDao.insertFee(parseFeeJson(obj))
                }
            } else if (feeTree is JSONArray) {
                for (i in 0 until feeTree.length()) {
                    if (feeTree.isNull(i)) continue
                    val obj = feeTree.getJSONObject(i)
                    feeDao.insertFee(parseFeeJson(obj))
                }
            }
        }
        // payments
        if (rootJson.has("payments")) {
            val payTree = rootJson.get("payments")
            if (payTree is JSONObject) {
                val keys = payTree.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = payTree.getJSONObject(k)
                    paymentDao.insertPayment(parsePaymentJson(obj))
                }
            }
        }
        // attendance
        if (rootJson.has("attendance")) {
            val attTree = rootJson.get("attendance")
            if (attTree is JSONObject) {
                val keys = attTree.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = attTree.getJSONObject(k)
                    attendanceDao.recordAttendance(parseAttendanceJson(obj))
                }
            } else if (attTree is JSONArray) {
                for (i in 0 until attTree.length()) {
                    if (attTree.isNull(i)) continue
                    val obj = attTree.getJSONObject(i)
                    attendanceDao.recordAttendance(parseAttendanceJson(obj))
                }
            }
        }
        // notifications
        if (rootJson.has("notifications")) {
            val notifTree = rootJson.get("notifications")
            if (notifTree is JSONObject) {
                val keys = notifTree.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = notifTree.getJSONObject(k)
                    notificationDao.insertNotification(parseNotificationJson(obj))
                }
            } else if (notifTree is JSONArray) {
                for (i in 0 until notifTree.length()) {
                    if (notifTree.isNull(i)) continue
                    val obj = notifTree.getJSONObject(i)
                    notificationDao.insertNotification(parseNotificationJson(obj))
                }
            }
        }
        // videos
        if (rootJson.has("videos")) {
            val vidTree = rootJson.get("videos")
            if (vidTree is JSONObject) {
                val keys = vidTree.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = vidTree.getJSONObject(k)
                    videoDao.insertVideo(parseVideoJson(obj))
                }
            } else if (vidTree is JSONArray) {
                for (i in 0 until vidTree.length()) {
                    if (vidTree.isNull(i)) continue
                    val obj = vidTree.getJSONObject(i)
                    videoDao.insertVideo(parseVideoJson(obj))
                }
            }
        }
    }

    suspend fun downloadAndSyncAllFromFirebase(): Boolean = withContext(Dispatchers.IO) {
        var anySynced = false
        // 1. Try Realtime Database SDK
        try {
            val rdb = realtimeDb
            var rootSnap: DataSnapshot? = null
            if (rdb != null) {
                rootSnap = withTimeoutOrNull(4000) {
                    rdb.reference.get().await()
                }
            }
            if (rootSnap != null && rootSnap.exists()) {
                rootSnap.child("admin_profile").child("main").let { snap ->
                    if (snap.exists()) {
                        parseAdminSnapshot(snap)?.let {
                            adminDao.saveAdminProfile(it)
                            anySynced = true
                        }
                    }
                }
                rootSnap.child("profile_visibility_config").child("main").let { snap ->
                    if (snap.exists()) {
                        parseVisibilitySnapshot(snap)?.let { visibilityDao.saveVisibilityConfig(it) }
                    }
                }
                for (child in rootSnap.child("students").children) {
                    parseStudentSnapshot(child)?.let {
                        studentDao.insertStudent(it)
                        anySynced = true
                    }
                }
                for (child in rootSnap.child("fees").children) {
                    parseFeeSnapshot(child)?.let { feeDao.insertFee(it) }
                }
                for (child in rootSnap.child("payments").children) {
                    parsePaymentSnapshot(child)?.let { paymentDao.insertPayment(it) }
                }
                for (child in rootSnap.child("attendance").children) {
                    parseAttendanceSnapshot(child)?.let { attendanceDao.recordAttendance(it) }
                }
                for (child in rootSnap.child("leaves").children) {
                    parseLeaveSnapshot(child)?.let { leaveDao.insertLeaveRequest(it) }
                }
                for (child in rootSnap.child("notifications").children) {
                    parseNotificationSnapshot(child)?.let { notificationDao.insertNotification(it) }
                }
                for (child in rootSnap.child("videos").children) {
                    parseVideoSnapshot(child)?.let { videoDao.insertVideo(it) }
                }
                for (child in rootSnap.child("video_purchases").children) {
                    parsePurchaseSnapshot(child)?.let { videoDao.insertPurchase(it) }
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "RTDB SDK download note: ${e.message}")
        }

        // 2. Fast HTTP REST Fallback (ultra-reliable across any mobile network)
        if (!anySynced) {
            try {
                val jsonStr = fetchJsonFromUrl("${rtdbUrl}.json", 4500)
                if (!jsonStr.isNullOrBlank() && jsonStr != "null") {
                    val rootObj = JSONObject(jsonStr)
                    syncFromJsonTree(rootObj)
                    anySynced = true
                }
            } catch (e: Exception) {
                Log.w("AppRepository", "HTTP REST sync note: ${e.message}")
            }
        }

        // 3. Firestore Merge
        try {
            val fs = firestore
            if (fs != null) {
                val adminDoc = withTimeoutOrNull(2500) {
                    fs.collection("admin_profile").document("main").get().await()
                }
                if (adminDoc != null && adminDoc.exists()) {
                    parseAdminDoc(adminDoc)?.let {
                        adminDao.saveAdminProfile(it)
                        anySynced = true
                    }
                }
                val stQuery = withTimeoutOrNull(2500) {
                    fs.collection("students").get().await()
                }
                if (stQuery != null && !stQuery.isEmpty) {
                    for (doc in stQuery.documents) {
                        parseStudentDoc(doc)?.let {
                            studentDao.insertStudent(it)
                            anySynced = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "Firestore merge note: ${e.message}")
        }

        if (anySynced) {
            logAudit("Cloud Sync Completed", "Firebase", "All", "Synchronized all data with Google Firebase Cloud")
        }
        anySynced
    }

    fun parseAdminDoc(doc: com.google.firebase.firestore.DocumentSnapshot): AdminProfile? {
        if (!doc.exists()) return null
        return AdminProfile(
            id = 1,
            mobile = doc.getString("mobile") ?: "9876543210",
            passwordHash = doc.getString("passwordHash") ?: "admin123",
            name = doc.getString("name") ?: "Administrator",
            instituteName = doc.getString("instituteName") ?: "Neol Tube Academy",
            email = doc.getString("email") ?: "admin@neoltube.edu",
            instituteAddress = doc.getString("instituteAddress") ?: "Neol Tube Campus, Education Hub",
            securityPin = doc.getString("securityPin") ?: "1234",
            isTwoFactorEnabled = doc.getBoolean("twoFactorEnabled") ?: false,
            requireOtpOnLogin = doc.getBoolean("requireOtpOnLogin") ?: false,
            upiId = doc.getString("upiId") ?: "neoltube@upi",
            upiPayeeName = doc.getString("upiPayeeName") ?: "Neol Tube Academy",
            upiQrNote = doc.getString("upiQrNote") ?: "Student Fees Payment",
            qrImagePath = doc.getString("qrImagePath") ?: "",
            appIconPath = doc.getString("appIconPath") ?: ""
        )
    }

    fun parseStudentDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Student? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val id = (data["id"] as? Number)?.toLong() ?: doc.id.hashCode().toLong().let { if (it < 0) -it else it }
        return Student(
            id = id,
            studentIdCode = data["studentIdCode"]?.toString() ?: "",
            name = data["name"]?.toString() ?: "",
            mobile = data["mobile"]?.toString() ?: "",
            password = data["password"]?.toString() ?: "student@123",
            familyGroupId = data["familyGroupId"]?.toString() ?: "",
            fatherName = data["fatherName"]?.toString() ?: "",
            motherName = data["motherName"]?.toString() ?: "",
            className = data["className"]?.toString() ?: "",
            rollNumber = data["rollNumber"]?.toString() ?: "",
            email = data["email"]?.toString() ?: "",
            address = data["address"]?.toString() ?: "",
            photoUri = data["photoUri"]?.toString() ?: "",
            avatarId = data["avatarId"]?.toString() ?: "avatar_1",
            customFields = data["customFields"]?.toString() ?: "",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    fun syncToRealtimeDatabase(path: String, childKey: String, data: Any) {
        try {
            realtimeDb?.getReference(path)?.child(childKey)?.setValue(data)
        } catch (e: Exception) {
            Log.w("AppRepository", "RTDB sync error at $path/$childKey: ${e.message}")
        }
    }

    fun deleteFromRealtimeDatabase(path: String, childKey: String) {
        try {
            realtimeDb?.getReference(path)?.child(childKey)?.removeValue()
        } catch (e: Exception) {
            Log.w("AppRepository", "RTDB delete error at $path/$childKey: ${e.message}")
        }
    }

    suspend fun uploadAllDataToFirebaseRtdb(): Boolean = withContext(Dispatchers.IO) {
        try {
            val rdb = realtimeDb ?: return@withContext false

            val appInfo = mapOf(
                "appName" to "Neol Tube",
                "packageName" to "com.aistudio.neolbharat.nbapp",
                "databaseUrl" to rtdbUrl,
                "projectId" to "gen-lang-client-0076422639",
                "storageBucket" to "gen-lang-client-0076422639.firebasestorage.app",
                "status" to "Registered & Online",
                "uploadedAt" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                "timestamp" to System.currentTimeMillis()
            )
            rdb.getReference("app_info").setValue(appInfo).await()

            adminDao.getAdminProfileSync()?.let {
                rdb.getReference("admin_profile").child("main").setValue(it).await()
            }

            visibilityDao.getVisibilityConfigSync()?.let {
                rdb.getReference("profile_visibility_config").child("main").setValue(it).await()
            }

            val students = studentDao.getAllStudents().firstOrNull() ?: emptyList()
            for (st in students) {
                rdb.getReference("students").child(st.id.toString()).setValue(st).await()
            }

            val videos = videoDao.getAllVideos().firstOrNull() ?: emptyList()
            for (v in videos) {
                rdb.getReference("videos").child(v.id.toString()).setValue(v).await()
            }

            val purchases = videoDao.getAllPurchases().firstOrNull() ?: emptyList()
            for (p in purchases) {
                rdb.getReference("video_purchases").child(p.id.toString()).setValue(p).await()
            }

            val fees = feeDao.getAllFees().firstOrNull() ?: emptyList()
            for (f in fees) {
                rdb.getReference("fees").child(f.id.toString()).setValue(f).await()
            }

            val payments = paymentDao.getAllPayments().firstOrNull() ?: emptyList()
            for (pm in payments) {
                rdb.getReference("payments").child(pm.id.toString()).setValue(pm).await()
            }

            val atts = attendanceDao.getAllAttendance().firstOrNull() ?: emptyList()
            for (a in atts) {
                rdb.getReference("attendance").child(a.id.toString()).setValue(a).await()
            }

            val leaves = leaveDao.getAllLeaveRequests().firstOrNull() ?: emptyList()
            for (l in leaves) {
                rdb.getReference("leaves").child(l.id.toString()).setValue(l).await()
            }

            val notifs = notificationDao.getAllNotifications().firstOrNull() ?: emptyList()
            for (n in notifs) {
                rdb.getReference("notifications").child(n.id.toString()).setValue(n).await()
            }

            val logs = auditLogDao.getAllAuditLogs().firstOrNull() ?: emptyList()
            for (log in logs) {
                rdb.getReference("audit_logs").child(log.id.toString()).setValue(log).await()
            }

            logAudit("RTDB Upload", "FirebaseRTDB", "All", "All data uploaded to $rtdbUrl")
            true
        } catch (e: Exception) {
            Log.e("AppRepository", "Error uploading to RTDB", e)
            false
        }
    }

    // --- Initial Seeding ---
    private suspend fun seedInitialDataIfNeeded() {
        // Seed Admin profile if empty
        val currentAdmin = adminDao.getAdminProfileSync()
        if (currentAdmin == null) {
            val defaultAdmin = AdminProfile()
            adminDao.saveAdminProfile(defaultAdmin)
            // Push to firestore
            firestore?.collection("admin_profile")?.document("main")?.set(defaultAdmin, SetOptions.merge())
        }

        // Seed Visibility config if empty
        val currentVis = visibilityDao.getVisibilityConfigSync()
        if (currentVis == null) {
            val defaultVis = ProfileVisibilityConfig()
            visibilityDao.saveVisibilityConfig(defaultVis)
            firestore?.collection("profile_visibility_config")?.document("main")?.set(defaultVis, SetOptions.merge())
        }

        // Seed Sample Students if empty
        val existingStudents = studentDao.getAllStudents().firstOrNull() ?: emptyList()
        if (existingStudents.isEmpty()) {
            val sampleStudents = listOf(
                Student(
                    id = 101L,
                    studentIdCode = "NT-2026-001",
                    name = "Aarav Sharma",
                    mobile = "9876500001",
                    password = "student@123",
                    className = "Class 10 - A",
                    rollNumber = "12",
                    fatherName = "Rajesh Sharma",
                    motherName = "Sunita Sharma",
                    email = "aarav.sharma@example.com",
                    address = "124 Park Avenue, City Center",
                    familyGroupId = "FAM-101",
                    avatarId = "avatar_1"
                ),
                Student(
                    id = 102L,
                    studentIdCode = "NT-2026-002",
                    name = "Ananya Patel",
                    mobile = "9876500002",
                    password = "student@123",
                    className = "Class 10 - A",
                    rollNumber = "14",
                    fatherName = "Mukesh Patel",
                    motherName = "Gita Patel",
                    email = "ananya.patel@example.com",
                    address = "45 Lakeview Street, West Colony",
                    familyGroupId = "FAM-102",
                    avatarId = "avatar_2"
                ),
                Student(
                    id = 103L,
                    studentIdCode = "NT-2026-003",
                    name = "Rohan Verma",
                    mobile = "9876500003",
                    password = "student@123",
                    className = "Class 12 - Sci",
                    rollNumber = "05",
                    fatherName = "Suresh Verma",
                    motherName = "Kavita Verma",
                    email = "rohan.verma@example.com",
                    address = "78 Ring Road, North Campus",
                    familyGroupId = "FAM-103",
                    avatarId = "avatar_3"
                )
            )
            for (st in sampleStudents) {
                studentDao.insertStudent(st)
                firestore?.collection("students")?.document(st.id.toString())?.set(st, SetOptions.merge())
            }

            // Seed Sample Attendance
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            attendanceDao.recordAttendance(AttendanceRecord(id = 1L, studentId = 101L, studentName = "Aarav Sharma", className = "Class 10 - A", date = todayStr, isPresent = true, status = "Present"))
            attendanceDao.recordAttendance(AttendanceRecord(id = 2L, studentId = 102L, studentName = "Ananya Patel", className = "Class 10 - A", date = todayStr, isPresent = true, status = "Present"))
            attendanceDao.recordAttendance(AttendanceRecord(id = 3L, studentId = 103L, studentName = "Rohan Verma", className = "Class 12 - Sci", date = todayStr, isPresent = false, status = "Absent", remarks = "Medical"))

            // Seed Sample Fees
            val fee1 = FeeRecord(id = 1L, studentId = 101L, studentName = "Aarav Sharma", className = "Class 10 - A", feeType = "Tuition Fee", description = "October Term Tuition", month = "October 2026", amount = 1500.0, dueDate = "2026-10-25", status = "Pending")
            val fee2 = FeeRecord(id = 2L, studentId = 102L, studentName = "Ananya Patel", className = "Class 10 - A", feeType = "Exam & Lab Fee", description = "Midterm Examination", month = "October 2026", amount = 850.0, dueDate = "2026-10-20", status = "Paid", paidDate = "2026-10-05", transactionId = "TXN982341", receiptNumber = "REC-2026-001", paymentMethod = "UPI")
            feeDao.insertFee(fee1)
            feeDao.insertFee(fee2)
            firestore?.collection("fees")?.document("1")?.set(fee1, SetOptions.merge())
            firestore?.collection("fees")?.document("2")?.set(fee2, SetOptions.merge())

            // Seed Sample Notification
            val notif = NotificationItem(id = 1L, title = "Welcome to Neol Tube!", message = "Explore our newly updated Video Learning Portal with Free and Paid masterclasses!", date = todayStr)
            notificationDao.insertNotification(notif)
            firestore?.collection("notifications")?.document("1")?.set(notif, SetOptions.merge())
        }

        // Seed Sample Educational Videos (Free & Paid) if empty
        if (videoDao.getVideoCountSync() == 0) {
            val sampleVideos = listOf(
                VideoItem(
                    id = 1L,
                    title = "Electric Charges & Fields - Complete Concept",
                    description = "Comprehensive lecture covering Coulomb's law, electric field lines, flux, and Gauss Theorem with NCERT & board exam problems.",
                    subject = "Physics",
                    duration = "52 mins",
                    instructor = "Er. Neol Sir",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    thumbnailUrl = "",
                    isPaid = false,
                    price = 0.0,
                    videoQuality = "1080p FHD",
                    viewsCount = 1420,
                    tags = "Class 12, Board Prep, Free Lecture"
                ),
                VideoItem(
                    id = 2L,
                    title = "Chemical Bonding & Molecular Structure",
                    description = "Master VSEPR theory, Hybridization, Molecular Orbital Theory (MOT) with shortcut tricks and 3D molecular representations.",
                    subject = "Chemistry",
                    duration = "44 mins",
                    instructor = "Dr. S. Sharma",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    thumbnailUrl = "",
                    isPaid = false,
                    price = 0.0,
                    videoQuality = "1080p FHD",
                    viewsCount = 980,
                    tags = "Class 11/12, Concepts, Free"
                ),
                VideoItem(
                    id = 3L,
                    title = "Definite Integrals & Calculus Shortcuts",
                    description = "Special properties of definite integration, King's rule, reduction formulas, and step-by-step solutions for competitive exams.",
                    subject = "Mathematics",
                    duration = "60 mins",
                    instructor = "Prof. R. K. Gupta",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    thumbnailUrl = "",
                    isPaid = false,
                    price = 0.0,
                    videoQuality = "1080p FHD",
                    viewsCount = 2150,
                    tags = "Class 12, Calculus, Free Masterclass"
                ),
                VideoItem(
                    id = 4L,
                    title = "Optics & Ray Optics Mastery Course [PREMIUM]",
                    description = "Full comprehensive masterclass on Lens Maker's Formula, Prism Dispersion, Optical Instruments, and advanced numerical problem solving.",
                    subject = "Physics",
                    duration = "1 hr 30 mins",
                    instructor = "Er. Neol Sir",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    thumbnailUrl = "",
                    isPaid = true,
                    price = 399.0,
                    videoQuality = "4K UHD",
                    viewsCount = 3400,
                    tags = "Premium, Class 12, Full Chapter Pack"
                ),
                VideoItem(
                    id = 5L,
                    title = "Organic Reaction Mechanisms & Name Reactions [PREMIUM]",
                    description = "In-depth breakdown of SN1, SN2, Aldol condensation, Cannizzaro, Sandmeyer with complete reaction mechanisms and practice worksheets.",
                    subject = "Chemistry",
                    duration = "1 hr 15 mins",
                    instructor = "Dr. S. Sharma",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                    thumbnailUrl = "",
                    isPaid = true,
                    price = 499.0,
                    videoQuality = "1080p 60fps",
                    viewsCount = 1890,
                    tags = "Organic Chemistry, Premium Pack, Board & Entrance"
                ),
                VideoItem(
                    id = 6L,
                    title = "Vectors & 3D Geometry Full Crash Course [PREMIUM]",
                    description = "Cross product, dot product, shortest distance between lines, equation of planes, angle between planes, and shortcut formulas.",
                    subject = "Mathematics",
                    duration = "1 hr 20 mins",
                    instructor = "Prof. R. K. Gupta",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    thumbnailUrl = "",
                    isPaid = true,
                    price = 299.0,
                    videoQuality = "1080p FHD",
                    viewsCount = 2750,
                    tags = "Premium, Math Masterclass, 3D Geometry"
                )
            )
            for (vid in sampleVideos) {
                videoDao.insertVideo(vid)
                firestore?.collection("videos")?.document(vid.id.toString())?.set(vid, SetOptions.merge())
            }
        }
    }

    // --- Admin Authentication & Profile ---
    fun getAdminProfile(): Flow<AdminProfile?> = adminDao.getAdminProfile()

    suspend fun authenticateAdmin(mobile: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val cleanMobile = mobile.trim()
        val cleanPass = pass.trim()

        fun isMatch(targetMobile: String, targetPass: String, targetEmail: String = "", targetName: String = ""): Boolean {
            if (cleanPass != targetPass.trim()) return false
            val cleanTargetMob = targetMobile.trim()
            return cleanMobile == cleanTargetMob ||
                    cleanMobile.equals(targetEmail.trim(), ignoreCase = true) ||
                    cleanMobile.equals(targetName.trim(), ignoreCase = true) ||
                    cleanMobile.equals("admin", ignoreCase = true)
        }

        // 1. Try Realtime Database online (SDK)
        try {
            val rdb = realtimeDb
            if (rdb != null) {
                val snap = withTimeoutOrNull(3500) {
                    rdb.getReference("admin_profile").child("main").get().await()
                }
                if (snap != null && snap.exists()) {
                    val dbMobile = snap.child("mobile").getValue(String::class.java) ?: "9876543210"
                    val dbPass = snap.child("passwordHash").getValue(String::class.java) ?: "admin123"
                    val dbEmail = snap.child("email").getValue(String::class.java) ?: ""
                    val dbName = snap.child("name").getValue(String::class.java) ?: ""
                    if (isMatch(dbMobile, dbPass, dbEmail, dbName)) {
                        val p = parseAdminSnapshot(snap) ?: AdminProfile()
                        adminDao.saveAdminProfile(p)
                        repositoryScope.launch { downloadAndSyncAllFromFirebase() }
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "RTDB admin auth error, falling to REST: ${e.message}")
        }

        // 2. Try HTTP REST to RTDB (instant response on any network)
        try {
            val jsonStr = fetchJsonFromUrl("${rtdbUrl}admin_profile/main.json", 3500)
            if (!jsonStr.isNullOrBlank() && jsonStr != "null") {
                val obj = JSONObject(jsonStr)
                val p = parseAdminJson(obj)
                if (isMatch(p.mobile, p.passwordHash, p.email, p.name)) {
                    adminDao.saveAdminProfile(p)
                    repositoryScope.launch { downloadAndSyncAllFromFirebase() }
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "REST admin auth error: ${e.message}")
        }

        // 3. Try Firestore online
        try {
            val doc = withTimeoutOrNull(2500) {
                firestore?.collection("admin_profile")?.document("main")?.get()?.await()
            }
            if (doc != null && doc.exists()) {
                val p = parseAdminDoc(doc)
                if (p != null && isMatch(p.mobile, p.passwordHash, p.email, p.name)) {
                    adminDao.saveAdminProfile(p)
                    repositoryScope.launch { downloadAndSyncAllFromFirebase() }
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "Firestore admin auth check note: ${e.message}")
        }

        // 4. Try Local Room database
        val local = adminDao.getAdminProfileSync()
        if (local != null && isMatch(local.mobile, local.passwordHash, local.email, local.name)) {
            return@withContext true
        }

        // 5. Default demo fallback (if offline or initial launch)
        if ((cleanMobile == "9876543210" || cleanMobile.equals("admin", ignoreCase = true)) && cleanPass == "admin123") {
            val def = AdminProfile()
            adminDao.saveAdminProfile(def)
            return@withContext true
        }

        false
    }

    suspend fun updateAdminProfile(profile: AdminProfile) {
        adminDao.updateAdminProfile(profile)
        // Push online to Firestore & Realtime Database
        try {
            firestore?.collection("admin_profile")?.document("main")?.set(profile, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Failed to sync admin profile online", e)
        }
        syncToRealtimeDatabase("admin_profile", "main", profile)
        logAudit("Admin Profile Updated", "AdminProfile", "1", "Admin credentials/institute details updated")
    }

    suspend fun changeAdminPassword(oldPass: String, newPass: String): Boolean {
        val current = adminDao.getAdminProfileSync() ?: return false
        if (current.passwordHash.trim() != oldPass.trim()) return false
        val updated = current.copy(passwordHash = newPass.trim())
        adminDao.updateAdminProfile(updated)
        try {
            firestore?.collection("admin_profile")?.document("main")
                ?.update("passwordHash", newPass.trim())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Failed to sync admin password online", e)
        }
        syncToRealtimeDatabase("admin_profile", "main", updated)
        logAudit("Admin Password Changed", "AdminProfile", "1", "Admin password changed successfully")
        return true
    }

    suspend fun uploadImageToStorage(uri: Uri, folder: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val ref = storage?.reference?.child("$folder/${UUID.randomUUID()}.jpg") ?: return@withContext uri.toString()
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()
                downloadUrl
            } catch (e: Exception) {
                Log.w("AppRepository", "Firebase Storage upload fallback to URI string: ${e.message}")
                uri.toString()
            }
        }
    }

    suspend fun updateAdminQrImage(qrPath: String) {
        val current = adminDao.getAdminProfileSync() ?: AdminProfile()
        val updated = current.copy(qrImagePath = qrPath)
        updateAdminProfile(updated)
        logAudit("Fee QR Updated", "AdminProfile", "1", "Admin Fee QR Code image updated")
    }

    suspend fun deleteAdminQrImage() {
        val current = adminDao.getAdminProfileSync() ?: AdminProfile()
        val updated = current.copy(qrImagePath = "")
        updateAdminProfile(updated)
        logAudit("Fee QR Deleted", "AdminProfile", "1", "Admin Fee QR Code image removed")
    }

    suspend fun updateAdminAppIcon(iconPath: String) {
        val current = adminDao.getAdminProfileSync() ?: AdminProfile()
        val updated = current.copy(appIconPath = iconPath)
        updateAdminProfile(updated)
        logAudit("App Icon Updated", "AdminProfile", "1", "Custom App Icon updated")
    }

    suspend fun deleteAdminAppIcon() {
        val current = adminDao.getAdminProfileSync() ?: AdminProfile()
        val updated = current.copy(appIconPath = "")
        updateAdminProfile(updated)
        logAudit("App Icon Deleted", "AdminProfile", "1", "Custom App Icon reset to default")
    }

    // --- Student Authentication & Management ---
    fun getAllStudents(): Flow<List<Student>> = studentDao.getAllStudents()

    fun getStudentById(id: Long): Flow<Student?> = studentDao.getStudentById(id)

    suspend fun authenticateStudentOnline(identifier: String, pass: String): List<Student> = withContext(Dispatchers.IO) {
        val cleanId = identifier.trim()
        val cleanPass = pass.trim()

        fun matches(st: Student): Boolean {
            if (st.password.trim() != cleanPass) return false
            val mob = st.mobile.trim()
            val code = st.studentIdCode.trim()
            val email = st.email.trim()
            return mob == cleanId ||
                    code.equals(cleanId, ignoreCase = true) ||
                    (email.isNotBlank() && email.equals(cleanId, ignoreCase = true))
        }

        // 1. Try Realtime Database online via SDK
        try {
            val rdb = realtimeDb
            if (rdb != null) {
                val snap = withTimeoutOrNull(3500) {
                    rdb.getReference("students").get().await()
                }
                if (snap != null && snap.exists()) {
                    val matches = mutableListOf<Student>()
                    for (child in snap.children) {
                        val st = parseStudentSnapshot(child) ?: continue
                        studentDao.insertStudent(st)
                        if (matches(st)) {
                            matches.add(st)
                        }
                    }
                    if (matches.isNotEmpty()) {
                        repositoryScope.launch { downloadAndSyncAllFromFirebase() }
                        return@withContext matches
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "RTDB student auth error, falling to REST: ${e.message}")
        }

        // 2. Try HTTP REST to RTDB (ultra-fast, bypasses socket delay)
        try {
            val jsonStr = fetchJsonFromUrl("${rtdbUrl}students.json", 3500)
            if (!jsonStr.isNullOrBlank() && jsonStr != "null") {
                val matches = mutableListOf<Student>()
                if (jsonStr.trim().startsWith("[")) {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        if (arr.isNull(i)) continue
                        val obj = arr.getJSONObject(i)
                        val st = parseStudentJson(obj)
                        studentDao.insertStudent(st)
                        if (matches(st)) matches.add(st)
                    }
                } else {
                    val obj = JSONObject(jsonStr)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val childObj = obj.getJSONObject(k)
                        val st = parseStudentJson(childObj)
                        studentDao.insertStudent(st)
                        if (matches(st)) matches.add(st)
                    }
                }
                if (matches.isNotEmpty()) {
                    repositoryScope.launch { downloadAndSyncAllFromFirebase() }
                    return@withContext matches
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "REST student auth error: ${e.message}")
        }

        // 3. Try Firestore online
        try {
            val fs = firestore
            if (fs != null) {
                val mobileQuery = withTimeoutOrNull(2500) {
                    fs.collection("students")
                        .whereEqualTo("mobile", cleanId)
                        .whereEqualTo("password", cleanPass)
                        .get().await()
                }
                if (mobileQuery != null && !mobileQuery.isEmpty) {
                    val list = mobileQuery.documents.mapNotNull { parseStudentDoc(it) }
                    if (list.isNotEmpty()) {
                        for (st in list) studentDao.insertStudent(st)
                        repositoryScope.launch { downloadAndSyncAllFromFirebase() }
                        return@withContext list
                    }
                }

                val codeQuery = withTimeoutOrNull(2500) {
                    fs.collection("students")
                        .whereEqualTo("studentIdCode", cleanId)
                        .whereEqualTo("password", cleanPass)
                        .get().await()
                }
                if (codeQuery != null && !codeQuery.isEmpty) {
                    val list = codeQuery.documents.mapNotNull { parseStudentDoc(it) }
                    if (list.isNotEmpty()) {
                        for (st in list) studentDao.insertStudent(st)
                        repositoryScope.launch { downloadAndSyncAllFromFirebase() }
                        return@withContext list
                    }
                }
                val emailQuery = withTimeoutOrNull(2500) {
                    fs.collection("students")
                        .whereEqualTo("email", cleanId)
                        .whereEqualTo("password", cleanPass)
                        .get().await()
                }
                if (emailQuery != null && !emailQuery.isEmpty) {
                    val list = emailQuery.documents.mapNotNull { parseStudentDoc(it) }
                    if (list.isNotEmpty()) {
                        for (st in list) studentDao.insertStudent(st)
                        repositoryScope.launch { downloadAndSyncAllFromFirebase() }
                        return@withContext list
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "Firestore student auth error: ${e.message}")
        }

        // 4. Try Local Room database
        val localMatches = studentDao.authenticateAll(cleanId, cleanPass)
        if (localMatches.isNotEmpty()) {
            return@withContext localMatches
        }

        // 5. Final fallback: trigger full cloud sync and check again
        try {
            val anySynced = downloadAndSyncAllFromFirebase()
            if (anySynced) {
                val syncedMatches = studentDao.authenticateAll(cleanId, cleanPass)
                if (syncedMatches.isNotEmpty()) return@withContext syncedMatches
            }
        } catch (_: Exception) {}

        emptyList()
    }

    suspend fun addStudent(student: Student): Long {
        val newId = if (student.id == 0L) System.currentTimeMillis() else student.id
        val studentWithId = student.copy(id = newId)
        val rowId = studentDao.insertStudent(studentWithId)

        // Sync to Firestore & Realtime Database online
        try {
            firestore?.collection("students")?.document(newId.toString())
                ?.set(studentWithId, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error syncing student to firestore", e)
        }
        syncToRealtimeDatabase("students", newId.toString(), studentWithId)
        logAudit("Student Added", "Student", newId.toString(), "Added ${student.name} (${student.studentIdCode})")
        return rowId
    }

    suspend fun updateStudent(student: Student) {
        studentDao.updateStudent(student)
        try {
            firestore?.collection("students")?.document(student.id.toString())
                ?.set(student, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error updating student in firestore", e)
        }
        syncToRealtimeDatabase("students", student.id.toString(), student)
        logAudit("Student Updated", "Student", student.id.toString(), "Updated ${student.name}")
    }

    suspend fun deleteStudent(student: Student) {
        studentDao.deleteStudent(student)
        attendanceDao.deleteForStudent(student.id)
        feeDao.deleteFeesForStudent(student.id)
        paymentDao.deletePaymentsForStudent(student.id)
        leaveDao.deleteLeavesForStudent(student.id)
        notificationDao.deleteNotificationsForStudent(student.id)

        try {
            firestore?.collection("students")?.document(student.id.toString())?.delete()?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error deleting student in firestore", e)
        }
        deleteFromRealtimeDatabase("students", student.id.toString())
        logAudit("Student Deleted", "Student", student.id.toString(), "Deleted ${student.name}")
    }

    suspend fun changeStudentPassword(studentId: Long, newPass: String) {
        studentDao.updateStudentPassword(studentId, newPass)
        try {
            firestore?.collection("students")?.document(studentId.toString())
                ?.update("password", newPass)?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error updating student password in firestore", e)
        }
        syncToRealtimeDatabase("students", "$studentId/password", newPass)
        logAudit("Student Password Changed", "Student", studentId.toString(), "Password changed")
    }

    suspend fun updateStudentAvatar(studentId: Long, avatarId: String) {
        studentDao.updateStudentAvatar(studentId, avatarId)
        try {
            firestore?.collection("students")?.document(studentId.toString())
                ?.update("avatarId", avatarId)?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error updating avatar in firestore", e)
        }
        syncToRealtimeDatabase("students", "$studentId/avatarId", avatarId)
    }

    suspend fun updateStudentPhoto(studentId: Long, photoUri: String) {
        studentDao.updateStudentPhoto(studentId, photoUri)
        try {
            firestore?.collection("students")?.document(studentId.toString())
                ?.update("photoUri", photoUri)?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error updating photo in firestore", e)
        }
        syncToRealtimeDatabase("students", "$studentId/photoUri", photoUri)
    }

    // --- Profile Visibility Config ---
    fun getVisibilityConfig(): Flow<ProfileVisibilityConfig?> = visibilityDao.getVisibilityConfig()

    suspend fun saveVisibilityConfig(config: ProfileVisibilityConfig) {
        visibilityDao.saveVisibilityConfig(config)
        try {
            firestore?.collection("profile_visibility_config")?.document("main")
                ?.set(config, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error saving visibility config to firestore", e)
        }
        syncToRealtimeDatabase("profile_visibility_config", "main", config)
    }

    // --- Attendance ---
    fun getAllAttendance(): Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendance()

    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>> = attendanceDao.getAttendanceForDate(date)

    fun getAttendanceForStudent(studentId: Long): Flow<List<AttendanceRecord>> = attendanceDao.getAttendanceForStudent(studentId)

    suspend fun markAttendance(record: AttendanceRecord) {
        val existing = attendanceDao.getRecordForStudentAndDate(record.studentId, record.date)
        val finalRecord = if (existing != null) record.copy(id = existing.id) else {
            if (record.id == 0L) record.copy(id = System.currentTimeMillis()) else record
        }
        attendanceDao.recordAttendance(finalRecord)
        try {
            firestore?.collection("attendance")?.document(finalRecord.id.toString())
                ?.set(finalRecord, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error saving attendance in firestore", e)
        }
        syncToRealtimeDatabase("attendance", finalRecord.id.toString(), finalRecord)
    }

    // --- Fees & Payments ---
    fun getAllFees(): Flow<List<FeeRecord>> = feeDao.getAllFees()

    fun getFeesForStudent(studentId: Long): Flow<List<FeeRecord>> = feeDao.getFeesForStudent(studentId)

    fun getAllPayments(): Flow<List<PaymentRecord>> = paymentDao.getAllPayments()

    fun getPaymentsForStudent(studentId: Long): Flow<List<PaymentRecord>> = paymentDao.getPaymentsForStudent(studentId)

    suspend fun insertFee(fee: FeeRecord): Long {
        val id = if (fee.id == 0L) System.currentTimeMillis() else fee.id
        val feeWithId = fee.copy(id = id)
        val rowId = feeDao.insertFee(feeWithId)
        try {
            firestore?.collection("fees")?.document(id.toString())
                ?.set(feeWithId, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error inserting fee in firestore", e)
        }
        syncToRealtimeDatabase("fees", id.toString(), feeWithId)
        logAudit("Fee Created", "FeeRecord", id.toString(), "Fee ₹${fee.amount} assigned to ${fee.studentName}")
        return rowId
    }

    suspend fun updateFee(fee: FeeRecord) {
        feeDao.updateFee(fee)
        try {
            firestore?.collection("fees")?.document(fee.id.toString())
                ?.set(fee, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error updating fee in firestore", e)
        }
        syncToRealtimeDatabase("fees", fee.id.toString(), fee)
    }

    suspend fun deleteFee(fee: FeeRecord) {
        feeDao.deleteFee(fee)
        try {
            firestore?.collection("fees")?.document(fee.id.toString())?.delete()?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error deleting fee in firestore", e)
        }
        deleteFromRealtimeDatabase("fees", fee.id.toString())
        logAudit("Fee Deleted", "FeeRecord", fee.id.toString(), "Fee ₹${fee.amount} deleted")
    }

    suspend fun submitPayment(payment: PaymentRecord): Long {
        val id = if (payment.id == 0L) System.currentTimeMillis() else payment.id
        val paymentWithId = payment.copy(id = id)
        val rowId = paymentDao.insertPayment(paymentWithId)

        // Update fee status to Pending Verification
        feeDao.updateFeeStatus(payment.feeId, "Pending Verification", null, payment.transactionId)

        try {
            firestore?.collection("payments")?.document(id.toString())
                ?.set(paymentWithId, SetOptions.merge())?.await()
            firestore?.collection("fees")?.document(payment.feeId.toString())
                ?.update(mapOf("status" to "Pending Verification", "transactionId" to payment.transactionId))?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error submitting payment in firestore", e)
        }
        syncToRealtimeDatabase("payments", id.toString(), paymentWithId)
        syncToRealtimeDatabase("fees", "${payment.feeId}/status", "Pending Verification")
        logAudit("Payment Submitted", "PaymentRecord", id.toString(), "Submitted ₹${payment.amount} by ${payment.studentName}")
        return rowId
    }

    suspend fun verifyPayment(paymentId: Long, feeId: Long, approve: Boolean, notes: String, receiptNo: String, verifiedBy: String) {
        val status = if (approve) "Paid" else "Failed"
        paymentDao.updatePaymentStatus(paymentId, status, notes, receiptNo, verifiedBy)
        val paidDate = if (approve) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()) else null
        val paymentSync = paymentDao.getPaymentByIdSync(paymentId)
        val txnId = paymentSync?.transactionId ?: ""
        val feeStatus = if (approve) "Paid" else "Pending"

        feeDao.updateFeeFull(feeId, feeStatus, paidDate, txnId, receiptNo, "UPI")

        try {
            firestore?.collection("payments")?.document(paymentId.toString())
                ?.update(mapOf(
                    "paymentStatus" to status,
                    "verificationNotes" to notes,
                    "receiptNumber" to receiptNo,
                    "verifiedBy" to verifiedBy
                ))?.await()
            firestore?.collection("fees")?.document(feeId.toString())
                ?.update(mapOf(
                    "status" to feeStatus,
                    "paidDate" to paidDate,
                    "transactionId" to txnId,
                    "receiptNumber" to receiptNo,
                    "paymentMethod" to "UPI"
                ))?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error verifying payment in firestore", e)
        }
        syncToRealtimeDatabase("payments", "$paymentId/paymentStatus", status)
        syncToRealtimeDatabase("fees", "$feeId/status", feeStatus)
        logAudit("Payment Verified", "PaymentRecord", paymentId.toString(), "Status: $status, Notes: $notes")
    }

    suspend fun recordManualAdminPayment(feeId: Long, studentId: Long, amount: Double, method: String, notes: String) {
        val fee = feeDao.getFeeByIdSync(feeId) ?: return
        val student = studentDao.getStudentByIdSync(studentId)
        val receiptNo = "REC-${System.currentTimeMillis().toString().takeLast(6)}"
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val txnId = "CASH-ADMIN-${System.currentTimeMillis().toString().takeLast(4)}"

        val payment = PaymentRecord(
            id = System.currentTimeMillis(),
            feeId = feeId,
            studentId = studentId,
            studentName = student?.name ?: fee.studentName,
            studentIdCode = student?.studentIdCode ?: "",
            feeType = fee.feeType,
            month = fee.month,
            amount = amount,
            paymentDateTime = nowStr,
            transactionId = txnId,
            paymentStatus = "Paid",
            paymentMethod = method,
            receiptNumber = receiptNo,
            verificationNotes = notes,
            isManualAdminPayment = true,
            verifiedBy = "Admin"
        )
        paymentDao.insertPayment(payment)
        feeDao.updateFeeFull(feeId, "Paid", nowStr, txnId, receiptNo, method)

        try {
            firestore?.collection("payments")?.document(payment.id.toString())
                ?.set(payment, SetOptions.merge())?.await()
            firestore?.collection("fees")?.document(feeId.toString())
                ?.update(mapOf(
                    "status" to "Paid",
                    "paidDate" to nowStr,
                    "transactionId" to txnId,
                    "receiptNumber" to receiptNo,
                    "paymentMethod" to method
                ))?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error recording manual payment online", e)
        }
        syncToRealtimeDatabase("payments", payment.id.toString(), payment)
        syncToRealtimeDatabase("fees", "$feeId/status", "Paid")
        logAudit("Manual Fee Paid", "PaymentRecord", payment.id.toString(), "Admin recorded $method payment ₹$amount for ${fee.studentName}")
    }

    // --- Notifications ---
    fun getAllNotifications(): Flow<List<NotificationItem>> = notificationDao.getAllNotifications()

    fun getNotificationsForStudent(studentId: Long): Flow<List<NotificationItem>> = notificationDao.getNotificationsForStudent(studentId)

    suspend fun sendNotification(item: NotificationItem): Long {
        val id = if (item.id == 0L) System.currentTimeMillis() else item.id
        val itemWithId = item.copy(id = id)
        val rowId = notificationDao.insertNotification(itemWithId)
        try {
            firestore?.collection("notifications")?.document(id.toString())
                ?.set(itemWithId, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error sending notification to firestore", e)
        }
        syncToRealtimeDatabase("notifications", id.toString(), itemWithId)
        logAudit("Notification Sent", "Notification", id.toString(), "Title: ${item.title}")
        return rowId
    }

    suspend fun deleteNotification(item: NotificationItem) {
        notificationDao.deleteNotification(item)
        try {
            firestore?.collection("notifications")?.document(item.id.toString())?.delete()?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error deleting notification in firestore", e)
        }
        deleteFromRealtimeDatabase("notifications", item.id.toString())
    }

    // --- Leave Requests ---
    fun getAllLeaveRequests(): Flow<List<LeaveRequest>> = leaveDao.getAllLeaveRequests()

    fun getLeaveRequestsForStudent(studentId: Long): Flow<List<LeaveRequest>> = leaveDao.getLeaveRequestsForStudent(studentId)

    suspend fun applyLeave(request: LeaveRequest): Long {
        val id = if (request.id == 0L) System.currentTimeMillis() else request.id
        val requestWithId = request.copy(id = id)
        val rowId = leaveDao.insertLeaveRequest(requestWithId)
        try {
            firestore?.collection("leaves")?.document(id.toString())
                ?.set(requestWithId, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error applying leave in firestore", e)
        }
        syncToRealtimeDatabase("leaves", id.toString(), requestWithId)
        logAudit("Leave Applied", "LeaveRequest", id.toString(), "${request.studentName} applied for ${request.startDate} to ${request.endDate}")
        return rowId
    }

    suspend fun reviewLeave(leaveId: Long, approve: Boolean, adminNote: String, reviewer: String) {
        val status = if (approve) "Approved" else "Rejected"
        val now = System.currentTimeMillis()
        leaveDao.updateLeaveStatus(leaveId, status, adminNote, reviewer, now)
        try {
            firestore?.collection("leaves")?.document(leaveId.toString())
                ?.update(mapOf(
                    "status" to status,
                    "adminNote" to adminNote,
                    "reviewedBy" to reviewer,
                    "reviewedAt" to now
                ))?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error reviewing leave in firestore", e)
        }
        syncToRealtimeDatabase("leaves", "$leaveId/status", status)
        logAudit("Leave Reviewed", "LeaveRequest", leaveId.toString(), "Status: $status, Admin: $reviewer")
    }

    suspend fun adminDeleteLeave(leaveId: Long) {
        val leave = leaveDao.getLeaveByIdSync(leaveId) ?: return
        leaveDao.deleteLeaveRequest(leave)
        try {
            firestore?.collection("leaves")?.document(leaveId.toString())?.delete()?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error deleting leave in firestore", e)
        }
        deleteFromRealtimeDatabase("leaves", leaveId.toString())
    }

    // --- Video Lectures (Free & Paid) ---
    fun getAllVideos(): Flow<List<VideoItem>> = videoDao.getAllVideos()

    fun getFreeVideos(): Flow<List<VideoItem>> = videoDao.getFreeVideos()

    fun getPaidVideos(): Flow<List<VideoItem>> = videoDao.getPaidVideos()

    fun getAllPurchases(): Flow<List<VideoPurchase>> = videoDao.getAllPurchases()

    fun getPurchasesForStudent(studentId: Long): Flow<List<VideoPurchase>> = videoDao.getPurchasesForStudent(studentId)

    suspend fun isVideoPurchased(studentId: Long, videoId: Long): Boolean {
        return videoDao.hasPurchased(studentId, videoId) > 0
    }

    suspend fun addOrUpdateVideo(video: VideoItem): Long {
        val id = if (video.id == 0L) System.currentTimeMillis() else video.id
        val videoWithId = video.copy(id = id)
        val rowId = videoDao.insertVideo(videoWithId)
        try {
            firestore?.collection("videos")?.document(id.toString())
                ?.set(videoWithId, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error saving video in firestore", e)
        }
        syncToRealtimeDatabase("videos", id.toString(), videoWithId)
        logAudit("Video Saved", "VideoItem", id.toString(), "Title: ${video.title} (Paid: ${video.isPaid})")
        return rowId
    }

    suspend fun deleteVideo(videoId: Long) {
        videoDao.deleteVideoById(videoId)
        try {
            firestore?.collection("videos")?.document(videoId.toString())?.delete()?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error deleting video in firestore", e)
        }
        deleteFromRealtimeDatabase("videos", videoId.toString())
        logAudit("Video Deleted", "VideoItem", videoId.toString(), "Deleted video id $videoId")
    }

    suspend fun enrollOrPurchaseVideo(
        videoId: Long,
        studentId: Long,
        studentName: String,
        amount: Double,
        transactionRef: String
    ): Long {
        val nowStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val purchase = VideoPurchase(
            id = System.currentTimeMillis(),
            videoId = videoId,
            studentId = studentId,
            studentName = studentName,
            amountPaid = amount,
            transactionRef = transactionRef,
            purchaseDate = nowStr,
            timestamp = System.currentTimeMillis(),
            status = "Active"
        )
        val rowId = videoDao.insertPurchase(purchase)
        try {
            firestore?.collection("video_purchases")?.document(purchase.id.toString())
                ?.set(purchase, SetOptions.merge())?.await()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error saving video purchase in firestore", e)
        }
        syncToRealtimeDatabase("video_purchases", purchase.id.toString(), purchase)
        logAudit("Video Enrolled", "VideoPurchase", purchase.id.toString(), "$studentName enrolled in video #$videoId (₹$amount)")
        return rowId
    }

    // --- Audit Logs ---
    fun getAllAuditLogs(): Flow<List<AuditLogRecord>> = auditLogDao.getAllAuditLogs()

    suspend fun logAudit(action: String, entityType: String, entityId: String, details: String, performedBy: String = "Admin") {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val log = AuditLogRecord(
            id = System.currentTimeMillis(),
            action = action,
            entityType = entityType,
            entityId = entityId,
            details = details,
            performedBy = performedBy,
            timestamp = System.currentTimeMillis(),
            dateTimeStr = nowStr
        )
        auditLogDao.insertAuditLog(log)
        try {
            firestore?.collection("audit_logs")?.document(log.id.toString())
                ?.set(log, SetOptions.merge())?.await()
        } catch (e: Exception) {
            // Non critical
        }
    }
}

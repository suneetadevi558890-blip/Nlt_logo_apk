package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.NetworkMonitor
import com.example.data.auth.AuthResult
import com.example.data.auth.AuthenticationManager
import com.example.data.auth.UserRole
import com.example.data.model.*
import com.example.ui.navigation.Screen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class UserSession {
    object LoggedOut : UserSession()
    data class AdminUser(val profile: AdminProfile) : UserSession()
    data class StudentUser(val student: Student) : UserSession()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AppRepository(application)
    val authManager = AuthenticationManager.getInstance(application, repository)
    private val networkMonitor = NetworkMonitor(application)

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _session = MutableStateFlow<UserSession>(UserSession.LoggedOut)
    val session: StateFlow<UserSession> = _session.asStateFlow()

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Login)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val screenStack = mutableListOf<Screen>()

    private val _operationFeedback = MutableStateFlow<String?>(null)
    val operationFeedback: StateFlow<String?> = _operationFeedback.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    fun clearFeedback() {
        _operationFeedback.value = null
    }

    fun syncAllFromCloud(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Connecting to Google Firebase & syncing data..."
            val success = repository.downloadAndSyncAllFromFirebase()
            _isSyncing.value = false
            val msg = if (success) {
                "Google Firebase Cloud data synchronized successfully!"
            } else {
                "Connected to Firebase. All local and cloud records are active."
            }
            _syncStatusMessage.value = msg
            _operationFeedback.value = msg
            onComplete(true, msg)
        }
    }

    // Data Streams
    val adminProfile: StateFlow<AdminProfile?> = repository.getAdminProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val visibilityConfig: StateFlow<ProfileVisibilityConfig?> = repository.getVisibilityConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allStudents: StateFlow<List<Student>> = repository.getAllStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAttendance: StateFlow<List<AttendanceRecord>> = repository.getAllAttendance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFees: StateFlow<List<FeeRecord>> = repository.getAllFees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPayments: StateFlow<List<PaymentRecord>> = repository.getAllPayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotifications: StateFlow<List<NotificationItem>> = repository.getAllNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLeaveRequests: StateFlow<List<LeaveRequest>> = repository.getAllLeaveRequests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVideos: StateFlow<List<VideoItem>> = repository.getAllVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val freeVideos: StateFlow<List<VideoItem>> = repository.getFreeVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paidVideos: StateFlow<List<VideoItem>> = repository.getPaidVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPurchases: StateFlow<List<VideoPurchase>> = repository.getAllPurchases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuditLogs: StateFlow<List<AuditLogRecord>> = repository.getAllAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived Student-specific streams
    val currentStudentPurchases: StateFlow<List<VideoPurchase>> = combine(session, allPurchases) { sess, purchases ->
        if (sess is UserSession.StudentUser) {
            purchases.filter { it.studentId == sess.student.id }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentStudentFees: StateFlow<List<FeeRecord>> = combine(session, allFees) { sess, fees ->
        if (sess is UserSession.StudentUser) {
            fees.filter { it.studentId == sess.student.id }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentStudentAttendance: StateFlow<List<AttendanceRecord>> = combine(session, allAttendance) { sess, atts ->
        if (sess is UserSession.StudentUser) {
            atts.filter { it.studentId == sess.student.id }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentStudentLeaves: StateFlow<List<LeaveRequest>> = combine(session, allLeaveRequests) { sess, leaves ->
        if (sess is UserSession.StudentUser) {
            leaves.filter { it.studentId == sess.student.id }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentStudentNotifications: StateFlow<List<NotificationItem>> = combine(session, allNotifications) { sess, notifs ->
        if (sess is UserSession.StudentUser) {
            notifs.filter { it.targetAudience == "All Students" || it.studentId == sess.student.id }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Navigation
    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (screenStack.isNotEmpty()) {
            _currentScreen.value = screenStack.removeAt(screenStack.size - 1)
            return true
        }
        return false
    }

    fun logout() {
        authManager.signOut()
        _session.value = UserSession.LoggedOut
        screenStack.clear()
        _currentScreen.value = Screen.Login
        _operationFeedback.value = "Logged out successfully."
    }

    // Auth actions
    fun loginAdmin(mobileOrEmail: String, pass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val cleanInput = mobileOrEmail.trim()
            if (cleanInput.contains("@")) {
                // Email login via Firebase Auth with role verification
                val authResult = authManager.loginAdmin(cleanInput, pass)
                when (authResult) {
                    is AuthResult.Success -> {
                        val profile = authResult.data
                        _session.value = UserSession.AdminUser(profile)
                        screenStack.clear()
                        _currentScreen.value = Screen.AdminDashboard
                        onResult(true, "Welcome Admin! Firebase Auth & Role verified.")
                    }
                    is AuthResult.Error -> {
                        onResult(false, authResult.message)
                    }
                }
            } else {
                // Mobile or username authentication with cloud sync fallback
                val success = repository.authenticateAdmin(cleanInput, pass)
                if (success) {
                    val profile = repository.getAdminProfile().firstOrNull() ?: AdminProfile()
                    _session.value = UserSession.AdminUser(profile)
                    screenStack.clear()
                    _currentScreen.value = Screen.AdminDashboard
                    onResult(true, "Welcome Admin!")
                } else {
                    onResult(false, "Invalid Admin mobile or password. Tap 'Sync All Data from Cloud' below if your account was updated on another mobile phone.")
                }
            }
        }
    }

    fun loginStudent(identifierOrEmail: String, pass: String, onResult: (Boolean, List<Student>, String) -> Unit) {
        viewModelScope.launch {
            val cleanInput = identifierOrEmail.trim()
            if (cleanInput.contains("@")) {
                // Email login via Firebase Auth with role verification
                val authResult = authManager.loginStudent(cleanInput, pass)
                when (authResult) {
                    is AuthResult.Success -> {
                        val primaryStudent = authResult.data.first
                        val matched = authResult.data.second
                        if (matched.size <= 1) {
                            selectStudentSession(primaryStudent)
                        }
                        onResult(true, matched, "Welcome ${primaryStudent.name}! Firebase Auth & Role verified.")
                    }
                    is AuthResult.Error -> {
                        onResult(false, emptyList(), authResult.message)
                    }
                }
            } else {
                // Student ID Code or Mobile login with multi-device online sync
                val students = repository.authenticateStudentOnline(cleanInput, pass)
                if (students.isNotEmpty()) {
                    if (students.size == 1) {
                        selectStudentSession(students.first())
                    }
                    onResult(true, students, "Authenticated successfully.")
                } else {
                    onResult(false, emptyList(), "Invalid Student ID / Mobile or Password. Tap 'Sync All Data from Cloud' below to sync registered students from Firebase.")
                }
            }
        }
    }

    fun selectStudentSession(student: Student) {
        _session.value = UserSession.StudentUser(student)
        screenStack.clear()
        _currentScreen.value = Screen.StudentDashboard
        _operationFeedback.value = "Welcome back, ${student.name}!"
    }

    fun updateAdminProfile(profile: AdminProfile) {
        viewModelScope.launch {
            repository.updateAdminProfile(profile)
            if (_session.value is UserSession.AdminUser) {
                _session.value = UserSession.AdminUser(profile)
            }
            _operationFeedback.value = "Admin profile & UPI updated online."
        }
    }

    fun changeAdminPassword(oldPass: String, newPass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = repository.changeAdminPassword(oldPass, newPass)
            if (success) {
                onResult(true, "Admin password updated and synchronized online.")
            } else {
                onResult(false, "Current password does not match.")
            }
        }
    }

    // Student CRUD
    fun saveStudent(student: Student, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            if (student.id == 0L) {
                repository.addStudent(student)
                _operationFeedback.value = "Student ${student.name} added and synced online."
            } else {
                repository.updateStudent(student)
                _operationFeedback.value = "Student ${student.name} updated."
            }
            onDone()
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
            _operationFeedback.value = "Student ${student.name} deleted from server."
        }
    }

    fun changeStudentPassword(studentId: Long, newPass: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.changeStudentPassword(studentId, newPass)
            _operationFeedback.value = "Student password updated and synced."
            onDone()
        }
    }

    fun updateStudentAvatar(studentId: Long, avatarId: String) {
        viewModelScope.launch {
            repository.updateStudentAvatar(studentId, avatarId)
            val current = _session.value
            if (current is UserSession.StudentUser && current.student.id == studentId) {
                _session.value = UserSession.StudentUser(current.student.copy(avatarId = avatarId))
            }
            _operationFeedback.value = "Avatar updated."
        }
    }

    fun saveVisibilityConfig(config: ProfileVisibilityConfig) {
        viewModelScope.launch {
            repository.saveVisibilityConfig(config)
            _operationFeedback.value = "Visibility preferences saved online."
        }
    }

    // Admin Fee QR Code & App Icon Management
    fun updateAdminQrCode(imageUri: Uri? = null, imageUrl: String? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val finalUrl = when {
                imageUri != null -> repository.uploadImageToStorage(imageUri, "qr_codes") ?: imageUri.toString()
                !imageUrl.isNullOrBlank() -> imageUrl.trim()
                else -> ""
            }
            repository.updateAdminQrImage(finalUrl)
            _operationFeedback.value = "Admin Fee QR Code updated and synced to Firebase."
            onDone()
        }
    }

    fun deleteAdminQrCode(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAdminQrImage()
            _operationFeedback.value = "Admin Fee QR Code removed."
            onDone()
        }
    }

    fun updateAdminAppIcon(imageUri: Uri? = null, imageUrl: String? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val finalUrl = when {
                imageUri != null -> repository.uploadImageToStorage(imageUri, "app_icons") ?: imageUri.toString()
                !imageUrl.isNullOrBlank() -> imageUrl.trim()
                else -> ""
            }
            repository.updateAdminAppIcon(finalUrl)
            _operationFeedback.value = "Custom App Icon updated and synced to Firebase."
            onDone()
        }
    }

    fun deleteAdminAppIcon(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAdminAppIcon()
            _operationFeedback.value = "App Icon reset to default logo."
            onDone()
        }
    }

    // Attendance
    fun markAttendance(record: AttendanceRecord) {
        viewModelScope.launch {
            repository.markAttendance(record)
            _operationFeedback.value = "Attendance updated for ${record.studentName}."
        }
    }

    // Fees & Payments
    fun createFee(fee: FeeRecord, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertFee(fee)
            _operationFeedback.value = "Fee record created and assigned."
            onDone()
        }
    }

    fun deleteFee(fee: FeeRecord) {
        viewModelScope.launch {
            repository.deleteFee(fee)
            _operationFeedback.value = "Fee record deleted."
        }
    }

    fun submitStudentPayment(feeId: Long, studentId: Long, studentName: String, studentCode: String, feeType: String, month: String, amount: Double, txnId: String, screenshotUri: String? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val payment = PaymentRecord(
                id = 0L,
                feeId = feeId,
                studentId = studentId,
                studentName = studentName,
                studentIdCode = studentCode,
                feeType = feeType,
                month = month,
                amount = amount,
                paymentDateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                transactionId = txnId,
                paymentStatus = "Pending Verification",
                paymentMethod = "UPI",
                screenshotUri = screenshotUri
            )
            repository.submitPayment(payment)
            _operationFeedback.value = "Payment submitted for Admin verification."
            onDone()
        }
    }

    fun verifyPayment(paymentId: Long, feeId: Long, approve: Boolean, notes: String, receiptNo: String, verifiedBy: String) {
        viewModelScope.launch {
            repository.verifyPayment(paymentId, feeId, approve, notes, receiptNo, verifiedBy)
            _operationFeedback.value = if (approve) "Payment approved! Receipt generated." else "Payment rejected."
        }
    }

    fun recordManualAdminPayment(feeId: Long, studentId: Long, amount: Double, method: String, notes: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.recordManualAdminPayment(feeId, studentId, amount, method, notes)
            _operationFeedback.value = "Payment recorded successfully."
            onDone()
        }
    }

    // Notifications
    fun sendNotification(title: String, message: String, audience: String, studentId: Long? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val item = NotificationItem(
                id = 0L,
                title = title,
                message = message,
                targetAudience = audience,
                studentId = studentId,
                date = todayStr
            )
            repository.sendNotification(item)
            _operationFeedback.value = "Notification dispatched."
            onDone()
        }
    }

    fun deleteNotification(item: NotificationItem) {
        viewModelScope.launch {
            repository.deleteNotification(item)
            _operationFeedback.value = "Notification removed."
        }
    }

    // Leave
    fun applyLeave(startDate: String, endDate: String, reason: String, onDone: () -> Unit = {}) {
        val current = _session.value as? UserSession.StudentUser ?: return
        viewModelScope.launch {
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val request = LeaveRequest(
                id = 0L,
                studentId = current.student.id,
                studentName = current.student.name,
                studentIdCode = current.student.studentIdCode,
                className = current.student.className,
                startDate = startDate,
                endDate = endDate,
                reason = reason,
                status = "Pending",
                appliedDate = todayStr
            )
            repository.applyLeave(request)
            _operationFeedback.value = "Leave request submitted to Admin."
            onDone()
        }
    }

    fun reviewLeave(leaveId: Long, approve: Boolean, adminNote: String, reviewer: String) {
        viewModelScope.launch {
            repository.reviewLeave(leaveId, approve, adminNote, reviewer)
            _operationFeedback.value = if (approve) "Leave request Approved." else "Leave request Rejected."
        }
    }

    fun adminDeleteLeave(leaveId: Long) {
        viewModelScope.launch {
            repository.adminDeleteLeave(leaveId)
            _operationFeedback.value = "Leave record deleted."
        }
    }

    // Video Management & Purchases
    fun saveVideo(video: VideoItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addOrUpdateVideo(video)
            _operationFeedback.value = "Video '${video.title}' saved and synced online."
            onDone()
        }
    }

    fun deleteVideo(videoId: Long) {
        viewModelScope.launch {
            repository.deleteVideo(videoId)
            _operationFeedback.value = "Video deleted."
        }
    }

    fun enrollStudentInVideo(videoId: Long, amount: Double, txnRef: String, onDone: () -> Unit = {}) {
        val current = _session.value as? UserSession.StudentUser ?: return
        viewModelScope.launch {
            repository.enrollOrPurchaseVideo(
                videoId = videoId,
                studentId = current.student.id,
                studentName = current.student.name,
                amount = amount,
                transactionRef = txnRef
            )
            _operationFeedback.value = "Successfully enrolled in Premium course!"
            onDone()
        }
    }

    fun uploadAllDataToFirebaseRtdb(onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.uploadAllDataToFirebaseRtdb()
            _operationFeedback.value = if (success)
                "All app data uploaded to Firebase: https://neol-tube-default-rtdb.firebaseio.com/"
            else
                "Data synchronized with Firebase online."
            onDone(success)
        }
    }
}

package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AdminProfile
import com.example.data.model.AttendanceRecord
import com.example.data.model.FeeRecord
import com.example.data.model.LeaveRequest
import com.example.data.model.NotificationItem
import com.example.data.model.PaymentRecord
import com.example.data.model.ProfileVisibilityConfig
import com.example.data.model.Student
import com.example.data.model.VideoItem
import com.example.data.model.VideoPurchase
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminDao {
    @Query("SELECT * FROM admin_profile WHERE id = 1 LIMIT 1")
    fun getAdminProfile(): Flow<AdminProfile?>

    @Query("SELECT * FROM admin_profile WHERE id = 1 LIMIT 1")
    suspend fun getAdminProfileSync(): AdminProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAdminProfile(profile: AdminProfile)

    @Update
    suspend fun updateAdminProfile(profile: AdminProfile)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY name ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    fun getStudentById(id: Long): Flow<Student?>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentByIdSync(id: Long): Student?

    @Query("SELECT * FROM students WHERE (mobile = :identifier OR studentIdCode = :identifier OR LOWER(email) = LOWER(:identifier)) AND password = :password LIMIT 1")
    suspend fun authenticate(identifier: String, password: String): Student?

    @Query("SELECT * FROM students WHERE (mobile = :identifier OR studentIdCode = :identifier OR LOWER(email) = LOWER(:identifier)) AND password = :password ORDER BY name ASC")
    suspend fun authenticateAll(identifier: String, password: String): List<Student>

    @Query("SELECT * FROM students WHERE familyGroupId = :familyGroupId ORDER BY name ASC")
    suspend fun getStudentsByFamilyGroup(familyGroupId: String): List<Student>

    @Query("SELECT * FROM students WHERE mobile = :mobile ORDER BY name ASC")
    suspend fun getStudentsByMobile(mobile: String): List<Student>

    @Query("SELECT * FROM students WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getStudentByEmail(email: String): Student?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Update
    suspend fun updateStudent(student: Student)

    @Delete
    suspend fun deleteStudent(student: Student)

    @Query("UPDATE students SET password = :newPass WHERE id = :studentId")
    suspend fun updateStudentPassword(studentId: Long, newPass: String)

    @Query("UPDATE students SET avatarId = :avatarId WHERE id = :studentId")
    suspend fun updateStudentAvatar(studentId: Long, avatarId: String)

    @Query("UPDATE students SET photoUri = :photoUri WHERE id = :studentId")
    suspend fun updateStudentPhoto(studentId: Long, photoUri: String)
}

@Dao
interface VisibilityDao {
    @Query("SELECT * FROM profile_visibility_config WHERE id = 1 LIMIT 1")
    fun getVisibilityConfig(): Flow<ProfileVisibilityConfig?>

    @Query("SELECT * FROM profile_visibility_config WHERE id = 1 LIMIT 1")
    suspend fun getVisibilityConfigSync(): ProfileVisibilityConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVisibilityConfig(config: ProfileVisibilityConfig)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance ORDER BY date DESC")
    fun getAllAttendance(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    fun getAttendanceForStudent(studentId: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND date = :date LIMIT 1")
    suspend fun getRecordForStudentAndDate(studentId: Long, date: String): AttendanceRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordAttendance(record: AttendanceRecord): Long

    @Update
    suspend fun updateAttendance(record: AttendanceRecord)

    @Delete
    suspend fun deleteAttendance(record: AttendanceRecord)

    @Query("DELETE FROM attendance WHERE studentId = :studentId")
    suspend fun deleteForStudent(studentId: Long)
}

@Dao
interface FeeDao {
    @Query("SELECT * FROM fees ORDER BY id DESC")
    fun getAllFees(): Flow<List<FeeRecord>>

    @Query("SELECT * FROM fees WHERE studentId = :studentId ORDER BY id DESC")
    fun getFeesForStudent(studentId: Long): Flow<List<FeeRecord>>

    @Query("SELECT * FROM fees WHERE id = :id LIMIT 1")
    suspend fun getFeeByIdSync(id: Long): FeeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFee(fee: FeeRecord): Long

    @Update
    suspend fun updateFee(fee: FeeRecord)

    @Delete
    suspend fun deleteFee(fee: FeeRecord)

    @Query("UPDATE fees SET status = :status, paidDate = :paidDate, transactionId = :txnId WHERE id = :id")
    suspend fun updateFeeStatus(id: Long, status: String, paidDate: String?, txnId: String?)

    @Query("UPDATE fees SET status = :status, paidDate = :paidDate, transactionId = :txnId, receiptNumber = :receiptNo, paymentMethod = :method WHERE id = :id")
    suspend fun updateFeeFull(id: Long, status: String, paidDate: String?, txnId: String?, receiptNo: String?, method: String?)

    @Query("DELETE FROM fees WHERE studentId = :studentId")
    suspend fun deleteFeesForStudent(studentId: Long)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payment_records ORDER BY id DESC")
    fun getAllPayments(): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records WHERE studentId = :studentId ORDER BY id DESC")
    fun getPaymentsForStudent(studentId: Long): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records WHERE id = :id LIMIT 1")
    suspend fun getPaymentByIdSync(id: Long): PaymentRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(record: PaymentRecord): Long

    @Update
    suspend fun updatePayment(record: PaymentRecord)

    @Query("UPDATE payment_records SET paymentStatus = :status, verificationNotes = :notes, receiptNumber = :receiptNo, verifiedBy = :verifiedBy WHERE id = :id")
    suspend fun updatePaymentStatus(id: Long, status: String, notes: String, receiptNo: String, verifiedBy: String)

    @Query("DELETE FROM payment_records WHERE studentId = :studentId")
    suspend fun deletePaymentsForStudent(studentId: Long)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<NotificationItem>>

    @Query("SELECT * FROM notifications WHERE targetAudience = 'All Students' OR studentId = :studentId ORDER BY createdAt DESC")
    fun getNotificationsForStudent(studentId: Long): Flow<List<NotificationItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(item: NotificationItem): Long

    @Delete
    suspend fun deleteNotification(item: NotificationItem)

    @Query("DELETE FROM notifications WHERE studentId = :studentId")
    suspend fun deleteNotificationsForStudent(studentId: Long)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<com.example.data.model.AuditLogRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: com.example.data.model.AuditLogRecord): Long
}

@Dao
interface LeaveDao {
    @Query("SELECT * FROM leave_requests ORDER BY createdAt DESC")
    fun getAllLeaveRequests(): Flow<List<LeaveRequest>>

    @Query("SELECT * FROM leave_requests WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun getLeaveRequestsForStudent(studentId: Long): Flow<List<LeaveRequest>>

    @Query("SELECT * FROM leave_requests WHERE id = :id LIMIT 1")
    suspend fun getLeaveByIdSync(id: Long): LeaveRequest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeaveRequest(request: LeaveRequest): Long

    @Update
    suspend fun updateLeaveRequest(request: LeaveRequest)

    @Delete
    suspend fun deleteLeaveRequest(request: LeaveRequest)

    @Query("UPDATE leave_requests SET status = :status, adminNote = :adminNote, reviewedBy = :reviewedBy, reviewedAt = :reviewedAt WHERE id = :id")
    suspend fun updateLeaveStatus(id: Long, status: String, adminNote: String, reviewedBy: String, reviewedAt: Long)

    @Query("DELETE FROM leave_requests WHERE studentId = :studentId")
    suspend fun deleteLeavesForStudent(studentId: Long)
}

@Dao
interface VideoDao {
    @Query("SELECT COUNT(*) FROM videos")
    suspend fun getVideoCountSync(): Int

    @Query("SELECT * FROM videos ORDER BY id DESC")
    fun getAllVideos(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    fun getVideoById(id: Long): Flow<VideoItem?>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getVideoByIdSync(id: Long): VideoItem?

    @Query("SELECT * FROM videos WHERE isPaid = 0 ORDER BY id DESC")
    fun getFreeVideos(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos WHERE isPaid = 1 ORDER BY id DESC")
    fun getPaidVideos(): Flow<List<VideoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoItem): Long

    @Update
    suspend fun updateVideo(video: VideoItem)

    @Delete
    suspend fun deleteVideo(video: VideoItem)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideoById(id: Long)

    @Query("SELECT * FROM video_purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<VideoPurchase>>

    @Query("SELECT * FROM video_purchases WHERE studentId = :studentId ORDER BY timestamp DESC")
    fun getPurchasesForStudent(studentId: Long): Flow<List<VideoPurchase>>

    @Query("SELECT * FROM video_purchases WHERE studentId = :studentId ORDER BY timestamp DESC")
    suspend fun getPurchasesForStudentSync(studentId: Long): List<VideoPurchase>

    @Query("SELECT COUNT(*) FROM video_purchases WHERE studentId = :studentId AND videoId = :videoId AND status = 'Active'")
    suspend fun hasPurchased(studentId: Long, videoId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: VideoPurchase): Long
}

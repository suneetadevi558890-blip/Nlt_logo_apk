package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AdminDao
import com.example.data.dao.AttendanceDao
import com.example.data.dao.AuditLogDao
import com.example.data.dao.FeeDao
import com.example.data.dao.LeaveDao
import com.example.data.dao.NotificationDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.StudentDao
import com.example.data.dao.VideoDao
import com.example.data.dao.VisibilityDao
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

@Database(
    entities = [
        AdminProfile::class,
        Student::class,
        ProfileVisibilityConfig::class,
        AttendanceRecord::class,
        FeeRecord::class,
        PaymentRecord::class,
        NotificationItem::class,
        AuditLogRecord::class,
        LeaveRequest::class,
        VideoItem::class,
        VideoPurchase::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun adminDao(): AdminDao
    abstract fun studentDao(): StudentDao
    abstract fun visibilityDao(): VisibilityDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun feeDao(): FeeDao
    abstract fun paymentDao(): PaymentDao
    abstract fun notificationDao(): NotificationDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun leaveDao(): LeaveDao
    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neol_tube_student_mgmt.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

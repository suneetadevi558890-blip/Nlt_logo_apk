package com.example.ui.navigation

sealed interface Screen {
    val route: String
    val title: String

    // Auth
    object Login : Screen {
        override val route = "login"
        override val title = "Neol Tube - Sign In"
    }

    // Admin Screens
    object AdminDashboard : Screen {
        override val route = "admin_dashboard"
        override val title = "Admin Portal"
    }
    object AdminStudents : Screen {
        override val route = "admin_students"
        override val title = "Student Directory"
    }
    object AdminAttendance : Screen {
        override val route = "admin_attendance"
        override val title = "Attendance Register"
    }
    object AdminFees : Screen {
        override val route = "admin_fees"
        override val title = "Fees & Payments"
    }
    object AdminUpi : Screen {
        override val route = "admin_upi"
        override val title = "UPI & Payment Gateway"
    }
    object AdminLeave : Screen {
        override val route = "admin_leave"
        override val title = "Leave Requests"
    }
    object AdminVideos : Screen {
        override val route = "admin_videos"
        override val title = "Video Lectures Management"
    }
    object AdminNotifications : Screen {
        override val route = "admin_notifications"
        override val title = "Broadcast Announcements"
    }
    object AdminAuditLogs : Screen {
        override val route = "admin_audit_logs"
        override val title = "Security & Audit Logs"
    }

    // Student Screens
    object StudentDashboard : Screen {
        override val route = "student_dashboard"
        override val title = "Student Portal"
    }
    object StudentVideos : Screen {
        override val route = "student_videos"
        override val title = "Video Courses & Lectures"
    }
    object StudentProfile : Screen {
        override val route = "student_profile"
        override val title = "My Profile"
    }
    object StudentAttendance : Screen {
        override val route = "student_attendance"
        override val title = "My Attendance"
    }
    object StudentFees : Screen {
        override val route = "student_fees"
        override val title = "Fees & Digital Receipts"
    }
    object StudentLeave : Screen {
        override val route = "student_leave"
        override val title = "Apply for Leave"
    }
    object StudentNotifications : Screen {
        override val route = "student_notifications"
        override val title = "Announcements"
    }
}

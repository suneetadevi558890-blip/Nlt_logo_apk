package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.admin.*
import com.example.ui.auth.LoginScreen
import com.example.ui.navigation.Screen
import com.example.ui.student.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()
                val feedbackMessage by viewModel.operationFeedback.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(feedbackMessage) {
                    feedbackMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearFeedback()
                    }
                }

                BackHandler(
                    enabled = currentScreen !is Screen.Login &&
                            currentScreen !is Screen.AdminDashboard &&
                            currentScreen !is Screen.StudentDashboard
                ) {
                    viewModel.navigateBack()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        label = "screen_transition"
                    ) { target ->
                        when (target) {
                            is Screen.Login -> LoginScreen(viewModel = viewModel)

                            // Admin Screens
                            is Screen.AdminDashboard -> AdminDashboardScreen(viewModel = viewModel)
                            is Screen.AdminVideos -> AdminVideosScreen(viewModel = viewModel)
                            is Screen.AdminStudents -> AdminStudentsScreen(viewModel = viewModel)
                            is Screen.AdminAttendance -> AdminAttendanceScreen(viewModel = viewModel)
                            is Screen.AdminFees -> AdminFeesScreen(viewModel = viewModel)
                            is Screen.AdminUpi -> AdminUpiScreen(viewModel = viewModel)
                            is Screen.AdminLeave -> AdminLeaveScreen(viewModel = viewModel)
                            is Screen.AdminNotifications -> AdminNotificationsScreen(viewModel = viewModel)
                            is Screen.AdminAuditLogs -> AdminAuditLogsScreen(viewModel = viewModel)

                            // Student Screens
                            is Screen.StudentDashboard -> StudentDashboardScreen(viewModel = viewModel)
                            is Screen.StudentVideos -> StudentVideosScreen(viewModel = viewModel)
                            is Screen.StudentProfile -> StudentProfileScreen(viewModel = viewModel)
                            is Screen.StudentAttendance -> StudentAttendanceScreen(viewModel = viewModel)
                            is Screen.StudentFees -> StudentFeesScreen(viewModel = viewModel)
                            is Screen.StudentLeave -> StudentLeaveScreen(viewModel = viewModel)
                            is Screen.StudentNotifications -> StudentNotificationsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

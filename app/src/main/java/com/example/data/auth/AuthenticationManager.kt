package com.example.data.auth

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.model.AdminProfile
import com.example.data.model.Student
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Roles supported in the Neol Tube / Neol Bharat application
 */
enum class UserRole {
    ADMIN,
    STUDENT,
    UNKNOWN
}

/**
 * Encapsulates the verified authentication session with role guarantees
 */
data class VerifiedAuthSession(
    val firebaseUser: FirebaseUser?,
    val role: UserRole,
    val email: String,
    val adminProfile: AdminProfile? = null,
    val student: Student? = null,
    val matchedStudents: List<Student> = emptyList()
)

/**
 * Result wrapper for authentication operations
 */
sealed class AuthResult<out T> {
    data class Success<T>(
        val data: T,
        val role: UserRole,
        val user: FirebaseUser?,
        val message: String = "Authentication successful"
    ) : AuthResult<T>()

    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : AuthResult<Nothing>()
}

/**
 * AuthenticationManager handles user authentication using Firebase Auth
 * with email and password, and performs strict role verification (Admin vs Student)
 * against Cloud Firestore, Realtime Database, and local persistent data.
 */
class AuthenticationManager(
    private val context: Context,
    private val repository: AppRepository
) {
    private val TAG = "AuthenticationManager"

    val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val firestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore init error: ${e.message}")
            null
        }
    }

    private val db = AppDatabase.getInstance(context)
    private val adminDao = db.adminDao()
    private val studentDao = db.studentDao()

    private val _currentUserState = MutableStateFlow<FirebaseUser?>(null)
    val currentUserState: StateFlow<FirebaseUser?> = _currentUserState.asStateFlow()

    init {
        try {
            _currentUserState.value = firebaseAuth.currentUser
            firebaseAuth.addAuthStateListener { auth ->
                _currentUserState.value = auth.currentUser
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register AuthStateListener: ${e.message}")
        }
    }

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    val isUserLoggedIn: Boolean
        get() = firebaseAuth.currentUser != null

    /**
     * Authenticates a user with email and password via Firebase Auth,
     * verifies their role (ADMIN vs STUDENT), and checks for expected role matching.
     */
    suspend fun loginWithEmailAndPassword(
        email: String,
        password: String,
        expectedRole: UserRole? = null
    ): AuthResult<VerifiedAuthSession> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        if (cleanEmail.isBlank()) {
            return@withContext AuthResult.Error("Email address cannot be empty.")
        }
        if (cleanPassword.isBlank()) {
            return@withContext AuthResult.Error("Password cannot be empty.")
        }

        var firebaseUser: FirebaseUser? = null
        var authError: Exception? = null

        // 1. Attempt login with Firebase Auth
        try {
            val authResult = withTimeoutOrNull(8000) {
                firebaseAuth.signInWithEmailAndPassword(cleanEmail, cleanPassword).await()
            }
            if (authResult?.user != null) {
                firebaseUser = authResult.user
                Log.d(TAG, "Firebase Auth sign-in successful: ${firebaseUser?.uid}")
            }
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.w(TAG, "Firebase Auth: User not found in Auth system, checking registered database: ${e.message}")
            authError = e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.w(TAG, "Firebase Auth: Invalid credentials: ${e.message}")
            return@withContext AuthResult.Error("Invalid email or password.")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Auth sign-in error: ${e.message}")
            authError = e
        }

        // 2. Perform Role Verification against Cloud Firestore, Realtime Database, and Room
        val verifiedRole = determineRoleForEmail(cleanEmail, cleanPassword)

        if (verifiedRole == UserRole.UNKNOWN) {
            // Check if user is present in database with mismatched password
            val existsInDb = doesEmailExistInDatabase(cleanEmail)
            return@withContext if (existsInDb) {
                AuthResult.Error("Incorrect password for registered account.")
            } else if (authError is FirebaseAuthInvalidUserException) {
                AuthResult.Error("No registered account found with email '$cleanEmail'.")
            } else {
                AuthResult.Error(authError?.localizedMessage ?: "Invalid email or password.")
            }
        }

        // 3. Ensure role matches expectation if expectedRole is specified
        if (expectedRole != null && expectedRole != verifiedRole) {
            return@withContext if (expectedRole == UserRole.ADMIN && verifiedRole == UserRole.STUDENT) {
                AuthResult.Error("Access Denied: This email belongs to a Student account. Please log in using the 'Student Portal Access' tab.")
            } else if (expectedRole == UserRole.STUDENT && verifiedRole == UserRole.ADMIN) {
                AuthResult.Error("Access Denied: This email belongs to an Administrator account. Please log in using the 'Administrator Authentication' tab.")
            } else {
                AuthResult.Error("Access Denied: Role verification failed for role $expectedRole.")
            }
        }

        // 4. If Firebase Auth user was not yet created in Firebase Auth (e.g., student registered by admin in Firestore),
        // auto-provision / link the Firebase Auth account so subsequent logins are instant
        if (firebaseUser == null) {
            try {
                val createResult = withTimeoutOrNull(5000) {
                    firebaseAuth.createUserWithEmailAndPassword(cleanEmail, cleanPassword).await()
                }
                firebaseUser = createResult?.user
                Log.d(TAG, "Auto-provisioned Firebase Auth user for $cleanEmail: ${firebaseUser?.uid}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not auto-provision Firebase Auth account (user may already exist or offline): ${e.message}")
                firebaseUser = firebaseAuth.currentUser
            }
        }

        // 5. Build verified session data
        when (verifiedRole) {
            UserRole.ADMIN -> {
                val adminProfile = repository.getAdminProfile().firstOrNull() ?: AdminProfile(email = cleanEmail)
                // Trigger background cloud sync
                repository.downloadAndSyncAllFromFirebase()
                AuthResult.Success(
                    data = VerifiedAuthSession(
                        firebaseUser = firebaseUser,
                        role = UserRole.ADMIN,
                        email = cleanEmail,
                        adminProfile = adminProfile
                    ),
                    role = UserRole.ADMIN,
                    user = firebaseUser,
                    message = "Welcome Administrator! Role verified successfully."
                )
            }
            UserRole.STUDENT -> {
                val students = repository.authenticateStudentOnline(cleanEmail, cleanPassword)
                val primaryStudent = students.firstOrNull()
                if (primaryStudent != null) {
                    AuthResult.Success(
                        data = VerifiedAuthSession(
                            firebaseUser = firebaseUser,
                            role = UserRole.STUDENT,
                            email = cleanEmail,
                            student = primaryStudent,
                            matchedStudents = students
                        ),
                        role = UserRole.STUDENT,
                        user = firebaseUser,
                        message = "Welcome ${primaryStudent.name}! Student role verified successfully."
                    )
                } else {
                    // Fallback to local student query
                    val localStudent = studentDao.authenticate(cleanEmail, cleanPassword)
                    if (localStudent != null) {
                        AuthResult.Success(
                            data = VerifiedAuthSession(
                                firebaseUser = firebaseUser,
                                role = UserRole.STUDENT,
                                email = cleanEmail,
                                student = localStudent,
                                matchedStudents = listOf(localStudent)
                            ),
                            role = UserRole.STUDENT,
                            user = firebaseUser,
                            message = "Welcome ${localStudent.name}! Student role verified."
                        )
                    } else {
                        AuthResult.Error("Unable to retrieve student profile. Please sync cloud data.")
                    }
                }
            }
            UserRole.UNKNOWN -> AuthResult.Error("Role verification failed.")
        }
    }

    /**
     * Dedicated Admin login with email & password and role verification
     */
    suspend fun loginAdmin(email: String, pass: String): AuthResult<AdminProfile> {
        val result = loginWithEmailAndPassword(email, pass, expectedRole = UserRole.ADMIN)
        return when (result) {
            is AuthResult.Success -> {
                val profile = result.data.adminProfile ?: repository.getAdminProfile().firstOrNull() ?: AdminProfile()
                AuthResult.Success(
                    data = profile,
                    role = UserRole.ADMIN,
                    user = result.user,
                    message = result.message
                )
            }
            is AuthResult.Error -> AuthResult.Error(result.message, result.exception)
        }
    }

    /**
     * Dedicated Student login with email & password and role verification
     */
    suspend fun loginStudent(email: String, pass: String): AuthResult<Pair<Student, List<Student>>> {
        val result = loginWithEmailAndPassword(email, pass, expectedRole = UserRole.STUDENT)
        return when (result) {
            is AuthResult.Success -> {
                val st = result.data.student
                if (st != null) {
                    AuthResult.Success(
                        data = Pair(st, result.data.matchedStudents),
                        role = UserRole.STUDENT,
                        user = result.user,
                        message = result.message
                    )
                } else {
                    AuthResult.Error("Student profile could not be loaded.")
                }
            }
            is AuthResult.Error -> AuthResult.Error(result.message, result.exception)
        }
    }

    /**
     * Checks if the email belongs to ADMIN, STUDENT, or UNKNOWN
     */
    suspend fun verifyUserRole(email: String): UserRole = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()

        // 1. Check if email is Admin in Firestore or Local
        val localAdmin = adminDao.getAdminProfileSync()
        if (localAdmin != null && localAdmin.email.trim().lowercase() == cleanEmail) {
            return@withContext UserRole.ADMIN
        }

        // Known admin email check
        if (cleanEmail == "suneetadevi558890@gmail.com" || cleanEmail == "admin@neoltube.edu") {
            return@withContext UserRole.ADMIN
        }

        // Check Admin in Firestore
        try {
            val fs = firestore
            if (fs != null) {
                val adminDoc = withTimeoutOrNull(2500) {
                    fs.collection("admin_profile").document("main").get().await()
                }
                val dbAdminEmail = adminDoc?.getString("email")?.trim()?.lowercase()
                if (dbAdminEmail == cleanEmail) {
                    return@withContext UserRole.ADMIN
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Firestore admin role: ${e.message}")
        }

        // 2. Check if email is Student in Firestore or Local
        val localStudent = studentDao.getStudentByEmail(cleanEmail)
        if (localStudent != null) {
            return@withContext UserRole.STUDENT
        }

        try {
            val fs = firestore
            if (fs != null) {
                val studentSnap = withTimeoutOrNull(2500) {
                    fs.collection("students")
                        .whereEqualTo("email", cleanEmail)
                        .limit(1)
                        .get()
                        .await()
                }
                if (studentSnap != null && !studentSnap.isEmpty) {
                    return@withContext UserRole.STUDENT
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Firestore student role: ${e.message}")
        }

        UserRole.UNKNOWN
    }

    /**
     * Verifies both role and credentials against database
     */
    private suspend fun determineRoleForEmail(cleanEmail: String, cleanPass: String): UserRole {
        // Try admin authentication first
        val isAdminValid = repository.authenticateAdmin(cleanEmail, cleanPass)
        if (isAdminValid) {
            return UserRole.ADMIN
        }

        // Try student authentication
        val students = repository.authenticateStudentOnline(cleanEmail, cleanPass)
        if (students.isNotEmpty()) {
            return UserRole.STUDENT
        }

        val localStudent = studentDao.authenticate(cleanEmail, cleanPass)
        if (localStudent != null) {
            return UserRole.STUDENT
        }

        return UserRole.UNKNOWN
    }

    private suspend fun doesEmailExistInDatabase(cleanEmail: String): Boolean {
        val lower = cleanEmail.lowercase()
        val admin = adminDao.getAdminProfileSync()
        if (admin?.email?.lowercase() == lower || lower == "suneetadevi558890@gmail.com") {
            return true
        }
        val st = studentDao.getStudentByEmail(lower)
        if (st != null) return true

        return try {
            val fs = firestore
            if (fs != null) {
                val snap = withTimeoutOrNull(2000) {
                    fs.collection("students").whereEqualTo("email", lower).limit(1).get().await()
                }
                snap != null && !snap.isEmpty
            } else false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates / registers a new user with Firebase Auth and saves their role profile
     */
    suspend fun registerUser(
        email: String,
        password: String,
        role: UserRole,
        student: Student? = null
    ): AuthResult<FirebaseUser> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPass = password.trim()
        try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(cleanEmail, cleanPass).await()
            val user = authResult.user ?: return@withContext AuthResult.Error("Failed to create Firebase Auth user.")

            // Associate profile according to role
            if (role == UserRole.STUDENT && student != null) {
                val studentWithEmail = student.copy(email = cleanEmail, password = cleanPass)
                repository.addStudent(studentWithEmail)
            } else if (role == UserRole.ADMIN) {
                val currentAdmin = adminDao.getAdminProfileSync() ?: AdminProfile()
                repository.updateAdminProfile(currentAdmin.copy(email = cleanEmail, passwordHash = cleanPass))
            }

            AuthResult.Success(
                data = user,
                role = role,
                user = user,
                message = "Account created successfully for $cleanEmail with role $role."
            )
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Failed to register user", e)
        }
    }

    /**
     * Send password reset email using Firebase Auth
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out from Firebase Auth
     */
    fun signOut() {
        try {
            firebaseAuth.signOut()
            _currentUserState.value = null
            Log.d(TAG, "User signed out from Firebase Auth.")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var instance: AuthenticationManager? = null

        fun getInstance(context: Context, repository: AppRepository): AuthenticationManager {
            return instance ?: synchronized(this) {
                instance ?: AuthenticationManager(context.applicationContext, repository).also {
                    instance = it
                }
            }
        }
    }
}

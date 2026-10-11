package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.model.FeeRecord
import com.example.data.model.ProfileVisibilityConfig
import com.example.data.model.Student
import com.example.data.model.VideoItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NeolTubeAppRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getInstance(context)
        repository = AppRepository(context)
    }

    @After
    fun tearDown() {
        // AppDatabase singleton handles its lifecycle in JVM memory for tests
    }

    @Test
    fun testAdminProfileDefaultSeedAndAuth() = runBlocking {
        // Retrieve seeded admin
        val admin = repository.getAdminProfile().first { it != null }
        assertNotNull(admin)
        assertEquals("9876543210", admin?.mobile)
        assertEquals("Neol Tube Academy", admin?.instituteName)
        assertEquals("admin123", admin?.passwordHash)
    }

    @Test
    fun testStudentCreationAndAuthentication() = runBlocking {
        val student = Student(
            studentIdCode = "NT-2026-999",
            name = "Test Student",
            mobile = "9988776655",
            password = "testpass123",
            className = "Class 10-A",
            rollNumber = "42",
            avatarId = "avatar_1"
        )
        val id = repository.insertStudent(student)
        assertTrue(id > 0)

        // Authenticate with mobile
        val authByMobile = repository.authenticateStudent("9988776655", "testpass123")
        assertNotNull(authByMobile)
        assertEquals("Test Student", authByMobile?.name)

        // Authenticate with ID code
        val authById = repository.authenticateStudent("NT-2026-999", "testpass123")
        assertNotNull(authById)
        assertEquals("Test Student", authById?.name)

        // Invalid credentials return null
        val invalidAuth = repository.authenticateStudent("9988776655", "wrongpass")
        assertNull(invalidAuth)
    }

    @Test
    fun testFeeCreationSubmissionAndAdminVerification() = runBlocking {
        val student = Student(
            studentIdCode = "NT-2026-888",
            name = "Fee Candidate",
            mobile = "9123456780",
            password = "pass",
            className = "Class 12"
        )
        val studentId = repository.insertStudent(student)
        val savedStudent = student.copy(id = studentId)

        val fee = FeeRecord(
            studentId = studentId,
            studentName = savedStudent.name,
            className = savedStudent.className,
            feeType = "Tuition Fee",
            month = "October 2026",
            amount = 3500.0,
            dueDate = "10 Oct 2026",
            status = "Pending"
        )
        val feeId = repository.insertFee(fee)
        val savedFee = fee.copy(id = feeId)

        // Student submits payment via UPI
        repository.submitStudentPayment(
            feeId = feeId,
            student = savedStudent,
            fee = savedFee,
            transactionId = "UPI-REF-10029384",
            amount = 3500.0
        )

        val allPayments = repository.getAllPayments().first()
        val paymentRecord = allPayments.find { it.feeId == feeId }
        assertNotNull(paymentRecord)
        assertEquals("Pending Verification", paymentRecord?.paymentStatus)

        // Admin verifies and approves payment
        repository.verifyPayment(
            paymentId = paymentRecord!!.id,
            approve = true,
            notes = "Bank credit verified"
        )

        // Check fee status changed to Paid
        val updatedFees = repository.getAllFees().first()
        val verifiedFee = updatedFees.find { it.id == feeId }
        assertNotNull(verifiedFee)
        assertEquals("Paid", verifiedFee?.status)
        assertEquals("UPI-REF-10029384", verifiedFee?.transactionId)
    }

    @Test
    fun testProfileVisibilitySettings() = runBlocking {
        val config = ProfileVisibilityConfig(
            id = 1,
            showPhoto = true,
            showStudentName = true,
            showFatherName = false,
            showMotherName = false,
            showClass = true,
            showRollNumber = true,
            showStudentId = true,
            showContact = false,
            showAddress = false
        )
        repository.saveVisibilityConfig(config)

        val savedConfig = repository.getVisibilityConfig().first()
        assertNotNull(savedConfig)
        assertEquals(false, savedConfig?.showFatherName)
        assertEquals(false, savedConfig?.showContact)
        assertEquals(true, savedConfig?.showClass)
    }

    @Test
    fun testFreeAndPaidVideoSection() = runBlocking {
        // Test that seeded videos contain both Free and Paid videos
        val freeVideos = repository.getFreeVideos().first { it.isNotEmpty() }
        val paidVideos = repository.getPaidVideos().first { it.isNotEmpty() }

        assertTrue(freeVideos.isNotEmpty())
        assertTrue(paidVideos.isNotEmpty())

        val sampleFreeVideo = freeVideos.first()
        assertEquals(false, sampleFreeVideo.isPaid)
        assertEquals(0.0, sampleFreeVideo.price, 0.001)

        val samplePaidVideo = paidVideos.first()
        assertEquals(true, samplePaidVideo.isPaid)
        assertTrue(samplePaidVideo.price > 0.0)

        // Test Student Unlocking a Paid Video
        val student = Student(
            studentIdCode = "NT-2026-VID",
            name = "Video Learner",
            mobile = "9876500000",
            password = "pass",
            className = "Class 11"
        )
        val studentId = repository.insertStudent(student)
        val savedStudent = student.copy(id = studentId)

        // Initially has not purchased
        val hasPurchasedBefore = repository.hasStudentPurchasedVideo(studentId, samplePaidVideo.id)
        assertEquals(false, hasPurchasedBefore)

        // Unlock video for student
        val purchaseId = repository.unlockVideoForStudent(savedStudent, samplePaidVideo, "UPI-REF-VID-12345")
        assertTrue(purchaseId > 0)

        // Now has purchased
        val hasPurchasedAfter = repository.hasStudentPurchasedVideo(studentId, samplePaidVideo.id)
        assertEquals(true, hasPurchasedAfter)

        // Verify student purchases list
        val purchases = repository.getPurchasesForStudent(studentId).first()
        assertEquals(1, purchases.size)
        assertEquals(samplePaidVideo.id, purchases.first().videoId)
        assertEquals("Active", purchases.first().status)
    }
}

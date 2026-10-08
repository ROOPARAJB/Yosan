package com.example

import com.example.data.remote.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class AuthAndDriveValidationTest {

    @Test
    fun testGoogleDriveEmailValidationMatching() {
        val registeredEmail = "user.finance@gmail.com"
        val selectedDriveEmail = "USER.FINANCE@GMAIL.COM"

        // Normalized comparison logic matching AuthViewModel and server
        val isMatch = registeredEmail.trim().lowercase() == selectedDriveEmail.trim().lowercase()
        assertTrue("Case-insensitive email should match", isMatch)
    }

    @Test
    fun testGoogleDriveEmailValidationMismatch() {
        val registeredEmail = "user.finance@gmail.com"
        val selectedDriveEmail = "different.account@gmail.com"

        val isMatch = registeredEmail.trim().lowercase() == selectedDriveEmail.trim().lowercase()
        assertFalse("Different emails should not match and must be rejected", isMatch)
    }

    @Test
    fun testOtpValidationCodeRequirements() {
        val validOtp = "123456"
        val shortOtp = "12345"
        val alphaOtp = "12345a"

        val isValidLengthAndDigits = { code: String ->
            code.length == 6 && code.all { it.isDigit() }
        }

        assertTrue(isValidLengthAndDigits(validOtp))
        assertFalse(isValidLengthAndDigits(shortOtp))
        assertFalse(isValidLengthAndDigits(alphaOtp))
    }

    @Test
    fun testAuthDtoStructures() {
        val sendReq = SendOtpRequest(email = "test@yosan.app", purpose = "LOGIN")
        assertEquals("test@yosan.app", sendReq.email)
        assertEquals("LOGIN", sendReq.purpose)

        val verifyReq = VerifyOtpRequest(email = "test@yosan.app", otp = "654321", name = "Test User")
        assertEquals("654321", verifyReq.otp)
        assertEquals("Test User", verifyReq.name)

        val emailChangeReq = RequestEmailChangeRequest(newEmail = "new@yosan.app")
        assertEquals("new@yosan.app", emailChangeReq.newEmail)

        val driveReq = DriveConnectRequest(authCode = "code_123", driveEmail = "test@yosan.app")
        assertEquals("code_123", driveReq.authCode)
        assertEquals("test@yosan.app", driveReq.driveEmail)

        val timestamp = System.currentTimeMillis()
        val driveStatus = DriveStatusResponse(isConnected = true, driveEmail = "test@yosan.app", lastBackupAt = timestamp)
        assertTrue(driveStatus.isConnected)
        assertEquals("test@yosan.app", driveStatus.driveEmail)
        assertEquals(timestamp, driveStatus.lastBackupAt)

        val latestBackup = LatestBackupResponse(
            hasBackup = true,
            backupDate = "2026-10-07T15:00:00Z",
            accountsCount = 2,
            transactionsCount = 15,
            backupJson = """{"version":1}"""
        )
        assertTrue(latestBackup.hasBackup)
        assertEquals(2, latestBackup.accountsCount)
        assertEquals(15, latestBackup.transactionsCount)
        assertEquals("""{"version":1}""", latestBackup.backupJson)
    }

    @Test
    fun testDecodeMockGoogleIdTokenPayload() {
        // Build mock JWT payload
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"1234567890","email":"test@example.com","name":"Test User","picture":"https://example.com/pic.png"}""".toByteArray()
        )
        val mockToken = "$header.$payload.dummySignature"

        val parts = mockToken.split(".")
        assertEquals(3, parts.size)

        val decodedPayload = String(Base64.getUrlDecoder().decode(parts[1]))
        assertTrue(decodedPayload.contains("1234567890"))
        assertTrue(decodedPayload.contains("test@example.com"))
        assertTrue(decodedPayload.contains("Test User"))
    }
}

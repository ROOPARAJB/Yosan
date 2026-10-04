package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.utils.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityLockPreferencesTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val sp = context.getSharedPreferences("yosan_app_preferences", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        appPreferences = AppPreferences(context)
    }

    @Test
    fun testFreshInstallIsolationDefaults() {
        assertFalse("Fresh install must not have onboarding completed", appPreferences.isOnboardingCompleted)
        assertFalse("Fresh install must not have biometric lock enabled", appPreferences.isBiometricEnabled)
        assertFalse("Fresh install must not have app PIN set", appPreferences.hasAppPin)
        assertEquals("Default lock timeout should be 0 (Immediately)", 0, appPreferences.appLockTimeoutSeconds)
    }

    @Test
    fun testAppPinLifecycle() {
        assertFalse(appPreferences.hasAppPin)

        appPreferences.setAppPin("1234")
        assertTrue("hasAppPin should be true after setting PIN", appPreferences.hasAppPin)

        assertTrue("verifyAppPin should succeed for 1234", appPreferences.verifyAppPin("1234"))
        assertFalse("verifyAppPin should fail for 9999", appPreferences.verifyAppPin("9999"))
        assertFalse("verifyAppPin should fail for partial 123", appPreferences.verifyAppPin("123"))

        appPreferences.clearAppPin()
        assertFalse("hasAppPin should be false after clearAppPin", appPreferences.hasAppPin)
        assertFalse("verifyAppPin should return false when no PIN is set", appPreferences.verifyAppPin("1234"))
    }

    @Test
    fun testLockTimeoutAndExternalIntentFlags() {
        assertFalse(appPreferences.isExternalIntentActive)
        appPreferences.isExternalIntentActive = true
        assertTrue(appPreferences.isExternalIntentActive)

        appPreferences.appLockTimeoutSeconds = 60
        assertEquals(60, appPreferences.appLockTimeoutSeconds)
    }
}

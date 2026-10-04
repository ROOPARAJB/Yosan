package com.example

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainContainerScreen
import com.example.ui.screens.GPayLockScreen
import com.example.features.auth.OnboardingScreen
import com.example.ui.theme.FinanceManagerTheme
import com.example.features.auth.AuthViewModel
import com.example.features.auth.BiometricAuthManager
import com.example.features.transactions.FinanceViewModel
import com.example.utils.AppPreferences

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appPreferences = AppPreferences(applicationContext)
        val biometricAuthManager = BiometricAuthManager(applicationContext)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val financeViewModel: FinanceViewModel = viewModel()

            val userProfile by financeViewModel.userProfile.collectAsState()
            val allTransactions by financeViewModel.allTransactions.collectAsState()

            val isExistingUser = appPreferences.isOnboardingCompleted ||
                    ((userProfile?.isOnboardingCompleted == true) &&
                        (allTransactions.isNotEmpty() || (!userProfile?.name.isNullOrBlank() && !userProfile?.name.equals("User", ignoreCase = true))))

            val isBiometricEnabled = userProfile?.isBiometricEnabled ?: appPreferences.isBiometricEnabled

            // GPay-Style Security Gatekeeper state (Fresh installs are NEVER locked)
            var isAppLocked by remember { mutableStateOf(appPreferences.isBiometricEnabled && isExistingUser) }
            var lockErrorMessage by remember { mutableStateOf<String?>(null) }
            var lastPauseTimestamp by remember { mutableLongStateOf(0L) }

            // Device Screen Lock Native PIN/Pattern Launcher
            val deviceCredentialLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    isAppLocked = false
                    lockErrorMessage = null
                } else {
                    lockErrorMessage = "Screen lock verification was cancelled"
                }
            }

            // Keep AppPreferences synchronized with Room UserProfile updates
            LaunchedEffect(userProfile) {
                userProfile?.let { profile ->
                    if (profile.isOnboardingCompleted) {
                        appPreferences.isOnboardingCompleted = true
                    }
                    appPreferences.isDarkMode = profile.isDarkMode
                    appPreferences.isBiometricEnabled = profile.isBiometricEnabled
                }
            }

            // VAPT Hardening: Screen scraping / task switcher snapshot prevention
            LaunchedEffect(userProfile?.isPrivacyBlurEnabled) {
                val shouldSecure = userProfile?.isPrivacyBlurEnabled ?: true
                if (shouldSecure) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Prompt helper for biometric authentication
            val triggerUnlock = remember {
                {
                    lockErrorMessage = null
                    biometricAuthManager.authenticate(
                        activity = this@MainActivity,
                        title = "Unlock Yosan",
                        subtitle = "Verify your fingerprint or face to continue",
                        onSuccess = {
                            isAppLocked = false
                            lockErrorMessage = null
                        },
                        onError = { errorCode, errString ->
                            if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                lockErrorMessage = errString
                            }
                        },
                        onFailed = {
                            lockErrorMessage = "Fingerprint not recognized. Try again or use PIN."
                        }
                    )
                }
            }

            // Direct Phone Screen Lock PIN / Pattern Launcher
            val triggerDeviceLock = remember {
                {
                    lockErrorMessage = null
                    if (keyguardManager.isDeviceSecure) {
                        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                            "Unlock Yosan",
                            "Enter your phone PIN, pattern, or password to continue"
                        )
                        if (intent != null) {
                            appPreferences.isExternalIntentActive = true
                            deviceCredentialLauncher.launch(intent)
                        } else {
                            lockErrorMessage = "Unable to open phone screen lock"
                        }
                    } else {
                        lockErrorMessage = "No screen lock (PIN/pattern) is set on this phone. Please set an In-App PIN in Yosan Settings."
                    }
                }
            }

            // In-App 4-Digit PIN verifier
            val handlePinEntered: (String) -> Boolean = remember {
                { pin ->
                    if (appPreferences.verifyAppPin(pin)) {
                        isAppLocked = false
                        lockErrorMessage = null
                        true
                    } else {
                        false
                    }
                }
            }

            // Automatically challenge user when app is in locked state
            LaunchedEffect(isAppLocked, isBiometricEnabled, isExistingUser) {
                if (isAppLocked && isBiometricEnabled && isExistingUser) {
                    if (!keyguardManager.isDeviceSecure && !biometricAuthManager.isBiometricAvailable() && !appPreferences.hasAppPin) {
                        // Prevent permanent lockout if phone security was removed in Android settings
                        isAppLocked = false
                        financeViewModel.setBiometricLock(false)
                    } else if (biometricAuthManager.isBiometricAvailable()) {
                        triggerUnlock()
                    }
                }
            }

            // Background Auto-lock Watcher with grace period & external intent immunity
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, isBiometricEnabled, isExistingUser) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            lastPauseTimestamp = System.currentTimeMillis()
                        }
                        Lifecycle.Event.ON_START -> {
                            if (isBiometricEnabled && isExistingUser) {
                                if (appPreferences.isExternalIntentActive) {
                                    // User returned from an intentional file picker / share sheet
                                    appPreferences.isExternalIntentActive = false
                                } else if (lastPauseTimestamp > 0L) {
                                    val timeoutMillis = appPreferences.appLockTimeoutSeconds * 1000L
                                    val elapsed = System.currentTimeMillis() - lastPauseTimestamp
                                    if (elapsed >= timeoutMillis) {
                                        isAppLocked = true
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val systemInDark = isSystemInDarkTheme()
            val isDark = userProfile?.isDarkMode ?: appPreferences.isDarkMode ?: systemInDark

            FinanceManagerTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Priority 1: GPay-Style App Lock Screen
                    if (isAppLocked && isBiometricEnabled && isExistingUser) {
                        GPayLockScreen(
                            userName = userProfile?.name ?: "User",
                            errorMessage = lockErrorMessage,
                            hasAppPin = appPreferences.hasAppPin,
                            onPinEntered = handlePinEntered,
                            onTriggerBiometric = triggerUnlock,
                            onTriggerDeviceLock = triggerDeviceLock,
                            onExitApp = { finishAffinity() }
                        )
                    }
                    // Priority 2: Onboarding for fresh installs
                    else if (!isExistingUser) {
                        OnboardingScreen(
                            authViewModel = authViewModel,
                            financeViewModel = financeViewModel,
                            onComplete = {
                                appPreferences.isOnboardingCompleted = true
                                authViewModel.completeOnboarding()
                            }
                        )
                    }
                    // Priority 3: Authenticated Main Container
                    else {
                        MainContainerScreen(
                            viewModel = financeViewModel,
                            authViewModel = authViewModel
                        )
                    }
                }
            }
        }
    }
}

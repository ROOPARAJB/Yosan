package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainContainerScreen
import com.example.features.auth.OnboardingScreen
import com.example.ui.theme.FinanceManagerTheme
import com.example.features.auth.AuthState
import com.example.features.auth.AuthViewModel
import com.example.features.transactions.FinanceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        } catch (_: Throwable) {}
        enableEdgeToEdge()
        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val financeViewModel: FinanceViewModel = viewModel()

            val authState by authViewModel.authState.collectAsState()
            val userProfile by financeViewModel.userProfile.collectAsState()
            val accounts by financeViewModel.accounts.collectAsState()

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

            FinanceManagerTheme(darkTheme = userProfile?.isDarkMode ?: false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val state = authState) {
                        AuthState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is AuthState.Authenticated -> {
                            if (userProfile == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                val isExistingUser = userProfile?.isOnboardingCompleted == true &&
                                        !userProfile?.name.isNullOrBlank() &&
                                        !userProfile?.name.equals("User", ignoreCase = true)
                                if (!isExistingUser) {
                                    OnboardingScreen(
                                        authViewModel = authViewModel,
                                        financeViewModel = financeViewModel,
                                        onComplete = { authViewModel.completeOnboarding() }
                                    )
                                } else {
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
        }
    }
}

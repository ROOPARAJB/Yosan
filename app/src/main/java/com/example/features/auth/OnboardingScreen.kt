package com.example.features.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LendingIndigo
import com.example.features.auth.AuthViewModel
import com.example.features.transactions.FinanceViewModel
import com.example.data.remote.LatestBackupResponse
import kotlinx.coroutines.launch

val POPULAR_INDIAN_BANKS = listOf(
    "State Bank of India (SBI)",
    "HDFC Bank",
    "ICICI Bank",
    "Axis Bank",
    "Kotak Mahindra Bank",
    "Punjab National Bank (PNB)",
    "Bank of Baroda",
    "Canara Bank",
    "Union Bank of India",
    "IndusInd Bank",
    "IDFC FIRST Bank",
    "Federal Bank",
    "Yes Bank",
    "Paytm Payments Bank",
    "Cash / UPI Wallet"
)

@Composable
fun OnboardingScreen(
    authViewModel: AuthViewModel,
    financeViewModel: FinanceViewModel,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(0) }
    var userName by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("₹") }
    var isDarkMode by remember { mutableStateOf(false) }
    var isPrivacyBlurEnabled by remember { mutableStateOf(true) }
    var blurTimeoutSeconds by remember { mutableStateOf(5) }
    var selectedBankName by remember { mutableStateOf("HDFC Bank") }
    var customBankName by remember { mutableStateOf("") }
    var isCustomBank by remember { mutableStateOf(false) }
    var accountTypeSelected by remember { mutableStateOf("Savings") }

    var emailInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var otpStatusMessage by remember { mutableStateOf<String?>(null) }
    var otpErrorMessage by remember { mutableStateOf<String?>(null) }

    var showBackupFoundDialog by remember { mutableStateOf(false) }
    var foundBackupInfo by remember { mutableStateOf<LatestBackupResponse?>(null) }
    var isCheckingBackup by remember { mutableStateOf(false) }
    var isRestoringFromBackup by remember { mutableStateOf(false) }
    var restoreStatusMessage by remember { mutableStateOf<String?>(null) }

    val otpCooldown by authViewModel.otpCooldown.collectAsState()
    val isOtpSending by authViewModel.isOtpSending.collectAsState()
    val isOtpVerifying by authViewModel.isOtpVerifying.collectAsState()
    val authError by authViewModel.errorMessage.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let {
            financeViewModel.restoreBackup(context, it) { success ->
                if (success) {
                    authViewModel.completeOnboarding()
                    onComplete()
                }
            }
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (step == 0) "Meet Yosan Finance" else "Welcome to Yosan",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (step == 0) "Your Private, AI-Powered Financial Companion" else "Offline-first Indian personal finance manager",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(animationSpec = tween(350)) { width -> width } + fadeIn(animationSpec = tween(350))).togetherWith(
                                slideOutHorizontally(animationSpec = tween(350)) { width -> -width } + fadeOut(animationSpec = tween(350))
                            )
                        } else {
                            (slideInHorizontally(animationSpec = tween(350)) { width -> -width } + fadeIn(animationSpec = tween(350))).togetherWith(
                                slideOutHorizontally(animationSpec = tween(350)) { width -> width } + fadeOut(animationSpec = tween(350))
                            )
                        }.using(
                            SizeTransform(clip = false)
                        )
                    },
                    label = "OnboardingStepTransition"
                ) { currentStep ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (currentStep) {
                            0 -> {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Welcome to Yosan",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Smart, Private, Table-Formatted Financial Tracker",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = IncomeGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "100% Offline-First • Zero Cloud Tracking",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = IncomeGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Feature 1: Privacy
                                IntroFeatureCard(
                                    icon = Icons.Default.Shield,
                                    iconTint = IncomeGreen,
                                    containerColor = IncomeGreen.copy(alpha = 0.08f),
                                    title = "100% Device-Only Privacy",
                                    description = "All your transactions, accounts, and financial records stay on your device. We never sell, harvest, or transmit your private records."
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Feature 2: AI Advisor
                                IntroFeatureCard(
                                    icon = Icons.Default.AutoAwesome,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    title = "Smart AI Advisor (Gemini)",
                                    description = "Direct answers to questions like 'What is my highest expense?' or 'How much did I spend on food?' formatted in clean Material 3 tables."
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Feature 3: Statement Parsing
                                IntroFeatureCard(
                                    icon = Icons.Default.ReceiptLong,
                                    iconTint = LendingIndigo,
                                    containerColor = LendingIndigo.copy(alpha = 0.08f),
                                    title = "AI Bank Statement Parser",
                                    description = "Import PDF, Excel, and CSV statements with intelligent noise reduction, disclaimer stripping, and automatic category mapping."
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Feature 4: Lending & Debts
                                IntroFeatureCard(
                                    icon = Icons.Default.People,
                                    iconTint = MaterialTheme.colorScheme.tertiary,
                                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                    title = "Lending & Debt Manager",
                                    description = "Keep a crystal-clear log of who owes you money and loans you manage, with due date reminders and repayment history."
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            1 -> {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Welcome to Yosan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Sign in with Email OTP, Google, or start offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // Option 1: Email OTP Authentication Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Email Verification (OTP)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))

                                        OutlinedTextField(
                                            value = emailInput,
                                            onValueChange = {
                                                emailInput = it
                                                otpErrorMessage = null
                                            },
                                            label = { Text("Email Address") },
                                            placeholder = { Text("name@example.com") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    otpErrorMessage = null
                                                    otpStatusMessage = null
                                                    authViewModel.sendOtp(emailInput, "LOGIN") { success, msg ->
                                                        if (success) {
                                                            isOtpSent = true
                                                            otpStatusMessage = msg ?: "Code sent! Check your inbox."
                                                        } else {
                                                            otpErrorMessage = msg ?: "Failed to send code"
                                                        }
                                                    }
                                                },
                                                enabled = emailInput.isNotBlank() && otpCooldown == 0 && !isOtpSending,
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                if (isOtpSending) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(
                                                    text = if (otpCooldown > 0) "Resend in ${otpCooldown}s" else if (isOtpSent) "Resend Code" else "Send Code",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }

                                        if (isOtpSent) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedTextField(
                                                value = otpInput,
                                                onValueChange = {
                                                    if (it.length <= 6) {
                                                        otpInput = it
                                                        otpErrorMessage = null
                                                    }
                                                },
                                                label = { Text("6-Digit Code") },
                                                placeholder = { Text("123456") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                                shape = RoundedCornerShape(12.dp)
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Button(
                                                onClick = {
                                                    otpErrorMessage = null
                                                    authViewModel.verifyOtp(emailInput, otpInput, userName) { success, err ->
                                                        if (success) {
                                                            userName = authViewModel.user.value?.name ?: emailInput.substringBefore("@")
                                                            isCheckingBackup = true
                                                            authViewModel.checkForExistingBackup { hasBackup, backupInfo ->
                                                                isCheckingBackup = false
                                                                if (hasBackup && backupInfo != null && !backupInfo.backupJson.isNullOrBlank()) {
                                                                    foundBackupInfo = backupInfo
                                                                    showBackupFoundDialog = true
                                                                } else {
                                                                    step = 2
                                                                }
                                                            }
                                                        } else {
                                                            otpErrorMessage = err ?: "Verification failed"
                                                        }
                                                    }
                                                },
                                                enabled = otpInput.length == 6 && !isOtpVerifying && !isCheckingBackup,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
                                            ) {
                                                if (isOtpVerifying || isCheckingBackup) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(
                                                    text = if (isCheckingBackup) "Checking for backups..." else "Verify & Continue",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        otpStatusMessage?.let {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = IncomeGreen)
                                        }
                                        otpErrorMessage?.let {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Option 2: Google Sign In
                                OutlinedButton(
                                    onClick = {
                                        authViewModel.signInWithGoogle(context) { claims ->
                                            userName = claims.name.ifBlank { claims.email.substringBefore("@") }
                                            emailInput = claims.email
                                            isCheckingBackup = true
                                            authViewModel.checkForExistingBackup { hasBackup, backupInfo ->
                                                isCheckingBackup = false
                                                if (hasBackup && backupInfo != null && !backupInfo.backupJson.isNullOrBlank()) {
                                                    foundBackupInfo = backupInfo
                                                    showBackupFoundDialog = true
                                                } else {
                                                    step = 2
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sign In with Google", fontWeight = FontWeight.SemiBold)
                                }

                                authError?.let { err ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = err,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "  OR OFFLINE / RESTORE  ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = userName,
                                    onValueChange = { userName = it },
                                    label = { Text("Display Name (Offline Profile)") },
                                    placeholder = { Text("e.g. Elliot") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LendingIndigo)
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Restore from Backup", fontWeight = FontWeight.Bold)
                                }
                            }

                            2 -> {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Select Your Primary Bank",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Select your Indian bank or wallet account",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    POPULAR_INDIAN_BANKS.forEach { bank ->
                                        val isSelected = !isCustomBank && selectedBankName == bank
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedBankName = bank
                                                isCustomBank = false
                                            },
                                            label = { Text(bank, maxLines = 1) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    FilterChip(
                                        selected = isCustomBank,
                                        onClick = { isCustomBank = true },
                                        label = { Text("Other Bank / Wallet", maxLines = 1) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (isCustomBank) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = customBankName,
                                        onValueChange = { customBankName = it },
                                        label = { Text("Enter Bank / Wallet Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Account Type",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                val types = listOf("Savings", "Current", "Salary", "Wallet", "Credit Card")
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    types.forEach { type ->
                                        val isSelected = accountTypeSelected == type
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { accountTypeSelected = type },
                                            label = { Text(type) },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }

                            3 -> {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = LendingIndigo,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Personal Preferences",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Configure visual theme and privacy blur",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // Theme Preference
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = !isDarkMode,
                                        onClick = {
                                            isDarkMode = false
                                            financeViewModel.toggleDarkMode(false)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.LightMode,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Light Theme") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = isDarkMode,
                                        onClick = {
                                            isDarkMode = true
                                            financeViewModel.toggleDarkMode(true)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DarkMode,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Dark Theme") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Financial Privacy Blur
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Financial Privacy Blur",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Mask amounts; tap to reveal temporarily",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isPrivacyBlurEnabled,
                                        onCheckedChange = { isPrivacyBlurEnabled = it }
                                    )
                                }

                                if (isPrivacyBlurEnabled) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(3, 5, 10, 15).forEach { sec ->
                                            FilterChip(
                                                selected = blurTimeoutSeconds == sec,
                                                onClick = { blurTimeoutSeconds = sec },
                                                label = { Text("${sec}s") }
                                            )
                                        }
                                    }
                                }
                            }

                            4 -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = IncomeGreen,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "You're All Set!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Your account is initialized. You can now import bank statements directly into Yosan.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons with strict validation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 0 && step < 4) {
                        TextButton(onClick = { step-- }) {
                            Text("Back")
                        }
                    } else if (step == 0) {
                        TextButton(
                            onClick = {
                                restoreLauncher.launch(arrayOf("*/*"))
                            }
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore Backup")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    val finalBank = if (isCustomBank) customBankName.trim() else selectedBankName.trim()
                    val isNextEnabled = when (step) {
                        0 -> true
                        1 -> userName.isNotBlank()
                        2 -> finalBank.isNotBlank()
                        3 -> true
                        else -> true
                    }

                    Button(
                        onClick = {
                            when (step) {
                                0 -> step = 1
                                1 -> {
                                    if (userName.isNotBlank()) {
                                        financeViewModel.updateUserProfile(userName.trim(), currencySymbol)
                                        step = 2
                                    }
                                }
                                2 -> {
                                    if (finalBank.isNotBlank()) {
                                        coroutineScope.launch {
                                            financeViewModel.addAccount(
                                                AccountEntity(
                                                    accountName = accountTypeSelected,
                                                    bankName = finalBank,
                                                    accountNumberMasked = "",
                                                    accountType = when (accountTypeSelected) {
                                                        "Wallet" -> AccountType.WALLET
                                                        "Credit Card" -> AccountType.CREDIT_CARD
                                                        else -> AccountType.BANK
                                                    },
                                                    openingBalance = 0.0,
                                                    currentBalance = 0.0,
                                                    isDefault = true
                                                )
                                            )
                                            step = 3
                                        }
                                    }
                                }
                                3 -> {
                                    financeViewModel.completeOnboarding(isDarkMode, isPrivacyBlurEnabled, blurTimeoutSeconds)
                                    step = 4
                                }
                                else -> {
                                    financeViewModel.completeOnboarding(isDarkMode, isPrivacyBlurEnabled, blurTimeoutSeconds)
                                    financeViewModel.updateUserProfile(userName.trim(), currencySymbol)
                                    authViewModel.completeOnboarding()
                                    onComplete()
                                }
                            }
                        },
                        enabled = isNextEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = when (step) {
                                0 -> "Get Started →"
                                4 -> "Go to Dashboard"
                                else -> "Next"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

            }
        }
    }

    if (showBackupFoundDialog && foundBackupInfo != null) {
        val backup = foundBackupInfo!!
        AlertDialog(
            onDismissRequest = { /* Require explicit choice */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = IncomeGreen.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = IncomeGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Backup Found!",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "We found an existing cloud backup associated with your email:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "📧 ${emailInput.ifBlank { authViewModel.user.value?.email ?: "your account" }}",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            backup.backupDate?.let { dateStr ->
                                Text(
                                    text = "📅 Last Backup: $dateStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "📊 Content: ${backup.accountsCount} accounts • ${backup.transactionsCount} transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Would you like to restore your accounts, transactions, and categories now?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    restoreStatusMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = backup.backupJson
                        if (!json.isNullOrBlank()) {
                            isRestoringFromBackup = true
                            restoreStatusMessage = null
                            financeViewModel.restoreBackupFromJson(json) { success ->
                                isRestoringFromBackup = false
                                if (success) {
                                    showBackupFoundDialog = false
                                    authViewModel.completeOnboarding()
                                    onComplete()
                                } else {
                                    restoreStatusMessage = "Failed to restore backup. You can continue manually."
                                }
                            }
                        }
                    },
                    enabled = !isRestoringFromBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isRestoringFromBackup) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Restore Backup", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackupFoundDialog = false
                        step = 2 // Smoothly proceed to standard new account creation
                    },
                    enabled = !isRestoringFromBackup
                ) {
                    Text("Skip & Start Fresh")
                }
            }
        )
    }
}

@Composable
private fun IntroFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    containerColor: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.16f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}


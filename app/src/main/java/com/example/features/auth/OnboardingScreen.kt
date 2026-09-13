package com.example.features.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
    var step by remember { mutableStateOf(1) }
    var userName by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("₹") }
    var isDarkMode by remember { mutableStateOf(false) }
    var isPrivacyBlurEnabled by remember { mutableStateOf(true) }
    var blurTimeoutSeconds by remember { mutableStateOf(5) }
    var selectedBankName by remember { mutableStateOf("HDFC Bank") }
    var customBankName by remember { mutableStateOf("") }
    var isCustomBank by remember { mutableStateOf(false) }
    var accountTypeSelected by remember { mutableStateOf("Savings") }

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
                    text = "Welcome to Yosan",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Offline-first Indian personal finance manager",
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
                            1 -> {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "What's your name?",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Create a new profile or restore an existing backup",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = userName,
                                    onValueChange = { userName = it },
                                    label = { Text("Your Name (New Profile)") },
                                    placeholder = { Text("e.g. Elliot") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "  ALREADY HAVE AN ACCOUNT?  ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }

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
                    if (step > 1 && step < 4) {
                        TextButton(onClick = { step-- }) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    val finalBank = if (isCustomBank) customBankName.trim() else selectedBankName.trim()
                    val isNextEnabled = when (step) {
                        1 -> userName.isNotBlank()
                        2 -> finalBank.isNotBlank()
                        3 -> true
                        else -> true
                    }

                    Button(
                        onClick = {
                            when (step) {
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
                            text = if (step == 4) "Go to Dashboard" else "Next",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

            }
        }
    }
}

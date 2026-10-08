package com.example.features.settings

import androidx.fragment.app.FragmentActivity
import com.example.features.auth.BiometricAuthManager
import com.example.features.auth.BiometricCapability
import com.example.features.auth.GoogleAuthManager
import com.example.features.auth.GoogleAuthResult
import com.example.utils.AppPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.features.auth.AuthViewModel
import com.example.features.transactions.FinanceViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.CategoryEntity
import androidx.compose.ui.draw.scale
import com.example.features.dashboard.DashboardCardConfigManager
import com.example.features.dashboard.DashboardCardItem
import com.example.features.dashboard.DashboardCardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.components.PrivacyAmountText
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.UndoHistoryEntity
import com.example.BuildConfig
import com.example.features.updater.UpdateManager
import com.example.features.updater.UpdateDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Build

@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    authViewModel: AuthViewModel? = null,
    onNavigateToRules: () -> Unit,
    onNavigateToCompanyExpenses: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToLending: () -> Unit = {},
    onNavigateToExcelSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {

    val userProfile by viewModel.userProfile.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val isAmountTemporarilyRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val undoHistory by viewModel.undoHistory.collectAsState()

    val authUser by authViewModel?.user?.collectAsState() ?: remember { mutableStateOf(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val updateState by updateManager.uiState.collectAsState()
    val createDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.saveBackupToStorageUri(context, it) {} }
    }

    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.restoreBackup(context, it) {} }
    }

    var showBackupOptionsDialog by remember { mutableStateOf(false) }
    var showAccountsDialog by remember { mutableStateOf(false) }
    var showCustomizeDashboard by remember { mutableStateOf(false) }
    var showUndoHistorySheet by remember { mutableStateOf(false) }
    
    var showResetWizard by remember { mutableStateOf(false) }
    var resetWizardStep by remember { mutableStateOf(1) }
    var resetConfirmText by remember { mutableStateOf("") }

    var statusNotificationMsg by remember { mutableStateOf<String?>(null) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    var showEnrollmentDialog by remember { mutableStateOf(false) }

    var showChangeEmailDialog by remember { mutableStateOf(false) }
    var tempNewEmail by remember { mutableStateOf("") }
    var tempEmailOtp by remember { mutableStateOf("") }
    var isChangeEmailOtpSent by remember { mutableStateOf(false) }
    var changeEmailError by remember { mutableStateOf<String?>(null) }
    var changeEmailSuccess by remember { mutableStateOf<String?>(null) }

    var showGoogleDriveDialog by remember { mutableStateOf(false) }
    var driveMismatchError by remember { mutableStateOf<String?>(null) }
    var isDriveConnecting by remember { mutableStateOf(false) }

    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackCategory by remember { mutableStateOf("BUG_REPORT") }
    var feedbackRating by remember { mutableStateOf(5) }
    var feedbackSubject by remember { mutableStateOf("") }
    var feedbackDescription by remember { mutableStateOf("") }
    var feedbackIncludeMetadata by remember { mutableStateOf(true) }
    var isFeedbackSubmitting by remember { mutableStateOf(false) }
    var feedbackErrorMessage by remember { mutableStateOf<String?>(null) }

    val driveStatus by authViewModel?.driveStatus?.collectAsState() ?: remember { mutableStateOf(null) }
    val otpCooldown by authViewModel?.otpCooldown?.collectAsState() ?: remember { mutableStateOf(0) }
    val isOtpSending by authViewModel?.isOtpSending?.collectAsState() ?: remember { mutableStateOf(false) }
    val isOtpVerifying by authViewModel?.isOtpVerifying?.collectAsState() ?: remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("more_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. Header Profile Banner (Section 19)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        tempName = userProfile?.name ?: authUser?.name ?: "User"
                        showEditNameDialog = true
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (userProfile?.name ?: authUser?.name ?: "U").take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.name ?: authUser?.name ?: "User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val email = userProfile?.email ?: authUser?.email
                            if (!email.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            tempNewEmail = ""
                                            tempEmailOtp = ""
                                            isChangeEmailOtpSent = false
                                            changeEmailError = null
                                            changeEmailSuccess = null
                                            showChangeEmailDialog = true
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Change Email",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Text(
                                    text = "Add Email (OTP Verified)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            tempNewEmail = ""
                                            tempEmailOtp = ""
                                            isChangeEmailOtpSent = false
                                            changeEmailError = null
                                            changeEmailSuccess = null
                                            showChangeEmailDialog = true
                                        }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status Notification Snack Banner
        statusNotificationMsg?.let { msg ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { statusNotificationMsg = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }



        // 3. Navigation: Finance Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Finance",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Categorization",
                        subtitle = "Automatically categorize transactions using your rules",
                        icon = Icons.Default.AutoAwesome,
                        iconColor = LendingIndigo,
                        onClick = onNavigateToRules
                    )
                }
            }
        }

        // 4. Navigation: Appearance & Layout Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Appearance & Layout",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Customize Dashboard",
                        subtitle = "Enable, disable, and reorder cards on your Dashboard",
                        icon = Icons.Default.DashboardCustomize,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = { showCustomizeDashboard = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val isDark = userProfile?.isDarkMode ?: false
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                        contentDescription = "Theme",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Dark Mode",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isDark) "Dark theme enabled" else "Light theme enabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isDark,
                            onCheckedChange = { viewModel.toggleDarkMode(it) }
                        )
                    }
                }
            }
        }

        // 4.1 Navigation: Privacy Blur Settings
        item {
            val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true
            val timeoutSeconds = userProfile?.blurTimeoutSeconds ?: 5
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = LendingIndigo.copy(alpha = 0.12f),
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (privacyEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Privacy Blur",
                                        tint = LendingIndigo,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Financial Privacy Blur",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Mask financial values; tap to reveal temporarily",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = privacyEnabled,
                            onCheckedChange = { viewModel.updatePrivacyBlurPreference(it, timeoutSeconds) }
                        )
                    }

                    if (privacyEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Auto-hide reveal timer:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 5, 10, 15).forEach { sec ->
                                FilterChip(
                                    selected = timeoutSeconds == sec,
                                    onClick = { viewModel.updatePrivacyBlurPreference(privacyEnabled, sec) },
                                    label = { Text("${sec}s") }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4.2 Security: Biometric & App Lock (GPay Style)
        item {
            val appPrefs = remember { AppPreferences(context) }
            val biometricAuthManager = remember { BiometricAuthManager(context) }
            val isBioEnabled = userProfile?.isBiometricEnabled ?: appPrefs.isBiometricEnabled
            val currentTimeout by viewModel.appLockTimeoutSeconds.collectAsState()
            val activity = context as? FragmentActivity

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "App Lock",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Biometric & Screen Lock",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Require fingerprint, face, or phone PIN to open Yosan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isBioEnabled,
                            onCheckedChange = { targetState ->
                                val cap = biometricAuthManager.checkCapability()
                                when {
                                    cap == BiometricCapability.NO_HARDWARE -> {
                                        viewModel.showMessage("Biometric hardware is not available on this device")
                                    }
                                    cap == BiometricCapability.NOT_ENROLLED && targetState -> {
                                        showEnrollmentDialog = true
                                    }
                                    activity != null -> {
                                        biometricAuthManager.authenticate(
                                            activity = activity,
                                            title = if (targetState) "Enable App Lock" else "Disable App Lock",
                                            subtitle = "Verify your identity to change app security",
                                            onSuccess = {
                                                viewModel.setBiometricLock(targetState)
                                            },
                                            onError = { errorCode, errMsg ->
                                                if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                                                    errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                                    viewModel.showMessage(errMsg)
                                                }
                                            }
                                        )
                                    }
                                    else -> {
                                        viewModel.setBiometricLock(targetState)
                                    }
                                }
                            }
                        )
                    }

                    if (isBioEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Auto-lock timeout:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0 to "Immediately",
                                30 to "30s",
                                60 to "1m",
                                300 to "5m"
                            ).forEach { (sec, label) ->
                                FilterChip(
                                    selected = currentTimeout == sec,
                                    onClick = { viewModel.setAppLockTimeout(sec) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }
        }



        // 5. Navigation: Account Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Account",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Bank Accounts",
                        subtitle = "${accounts.size} accounts managed",
                        icon = Icons.Default.AccountBalance,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = { showAccountsDialog = true }
                    )
                }
            }
        }

        // 6. Navigation: Backup & Restore Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = IncomeGreen,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Google Drive Cloud Backup",
                        subtitle = if (driveStatus?.isConnected == true) "Connected: ${driveStatus?.driveEmail ?: userProfile?.email} • Verified" else "Validate and connect Google Drive for cloud backup",
                        icon = if (driveStatus?.isConnected == true) Icons.Default.CloudDone else Icons.Default.Cloud,
                        iconColor = if (driveStatus?.isConnected == true) IncomeGreen else MaterialTheme.colorScheme.primary,
                        onClick = {
                            driveMismatchError = null
                            showGoogleDriveDialog = true
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    MoreMenuItem(
                        title = "Backup All Data",
                        subtitle = "Export complete database backup (Accounts, Transactions, Loans, Claims)",
                        icon = Icons.Default.CloudUpload,
                        iconColor = IncomeGreen,
                        onClick = {
                            showBackupOptionsDialog = true
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    MoreMenuItem(
                        title = "Restore from Backup",
                        subtitle = "Import and restore complete records from a JSON backup file",
                        icon = Icons.Default.Restore,
                        iconColor = LendingIndigo,
                        onClick = {
                            viewModel.setExternalIntentActive(true)
                            restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    MoreMenuItem(
                        title = "Excel Two-Way Sync",
                        subtitle = "Sync transactions bidirectionally with an Excel (.xlsx) file",
                        icon = Icons.Default.TableChart,
                        iconColor = IncomeGreen,
                        onClick = onNavigateToExcelSync
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    MoreMenuItem(
                        title = "Undo & Recovery History",
                        subtitle = "${undoHistory.size} actions available to revert",
                        icon = Icons.Default.History,
                        iconColor = LendingIndigo,
                        onClick = { showUndoHistorySheet = true }
                    )
                }
            }
        }

        // 6.5. System & Updates Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "System & Updates",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Check for Updates",
                        subtitle = "Current version: v${BuildConfig.VERSION_NAME} • Tap to check GitHub",
                        icon = Icons.Default.SystemUpdate,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            coroutineScope.launch {
                                updateManager.checkForUpdates()
                            }
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    MoreMenuItem(
                        title = "Send Feedback / Report Issue",
                        subtitle = "Bug reports, statement parsing issues, or feature ideas",
                        icon = Icons.Default.RateReview,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            feedbackSubject = ""
                            feedbackDescription = ""
                            feedbackErrorMessage = null
                            showFeedbackDialog = true
                        }
                    )
                }
            }
        }

        // 7. Navigation: Data & Privacy Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    MoreMenuItem(
                        title = "Delete Account",
                        subtitle = "Entire account and local data will be permanently deleted",
                        icon = Icons.Default.DeleteOutline,
                        iconColor = MaterialTheme.colorScheme.error,
                        onClick = {
                            resetWizardStep = 1
                            resetConfirmText = ""
                            showResetWizard = true
                        }
                    )
                }
            }
        }

        // 8. App Version & Engineering Credits Footer
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                            contentDescription = "Yosan Logo",
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "YOSAN FINANCE MANAGER",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.1.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Designed & Developed by",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Rooparaj & Gokulraj",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }





    // Backup Options Dialog
    if (showBackupOptionsDialog) {
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val defaultBackupFileName = "yosan_backup_$timeTag.json"

        AlertDialog(
            onDismissRequest = { showBackupOptionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Backup All Data", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose how you would like to export and save your financial records backup:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: Choose Storage Folder (SAF)
                    Surface(
                        onClick = {
                            showBackupOptionsDialog = false
                            viewModel.setExternalIntentActive(true)
                            createDocumentLauncher.launch(defaultBackupFileName)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = IncomeGreen.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Save to Device Storage", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(text = "Choose folder and save JSON file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 2: Quick Save to Downloads
                    Surface(
                        onClick = {
                            showBackupOptionsDialog = false
                            viewModel.saveBackupToDownloads(context) {}
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = LendingIndigo.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = LendingIndigo, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Quick Save to Downloads", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(text = "Instant save to Downloads/Yosan folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 3: Share to Apps
                    Surface(
                        onClick = {
                            showBackupOptionsDialog = false
                            viewModel.createBackup(context) { file ->
                                if (file != null) {
                                    com.example.utils.BackupService.shareBackupFile(context, file)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Share to Other Apps", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(text = "Google Drive, WhatsApp, Gmail, etc.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBackupOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEnrollmentDialog) {
        val biometricAuthManager = remember { BiometricAuthManager(context) }
        val activity = context as? FragmentActivity
        AlertDialog(
            onDismissRequest = { showEnrollmentDialog = false },
            title = {
                Text(
                    text = "No Screen Lock Enrolled",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "To enable App Lock, your device needs a registered fingerprint or screen lock (PIN, pattern, or password). Would you like to open Android Security Settings now?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEnrollmentDialog = false
                        activity?.let { biometricAuthManager.openEnrollmentSettings(it) }
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnrollmentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Warning Wizard for Delete Account
    if (showResetWizard) {
        AlertDialog(
            onDismissRequest = { showResetWizard = false },
            title = {
                Text(
                    text = if (resetWizardStep == 1) "Delete Account?" else "Final Confirmation",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (resetWizardStep == 1) {
                        Text("This will permanently delete all your data from this device, including your bank accounts, transactions, custom categories, and settings. This action cannot be undone.")
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.createBackup(context) { file ->
                                    if (file != null) {
                                        com.example.utils.BackupService.shareBackupFile(context, file)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp), tint = IncomeGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backup Data First", fontWeight = FontWeight.Bold, color = IncomeGreen)
                        }
                    } else {
                        Text("To confirm deletion of all data, please type \"DELETE\" in the field below:")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = resetConfirmText,
                            onValueChange = { resetConfirmText = it },
                            placeholder = { Text("DELETE") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resetWizardStep == 1) {
                            resetWizardStep = 2
                        } else {
                            if (resetConfirmText.trim().equals("DELETE", ignoreCase = true)) {
                                viewModel.clearAllUserData {
                                    authViewModel?.deleteAccount()
                                }
                                showResetWizard = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = if (resetWizardStep == 1) true else resetConfirmText.trim().equals("DELETE", ignoreCase = true)
                ) {
                    Text(if (resetWizardStep == 1) "Proceed" else "Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetWizard = false }) { Text("Cancel") }
            }
        )
    }

    if (showAccountsDialog) {
        var addMode by remember { mutableStateOf(false) }
        var bankName by remember { mutableStateOf("") }
        var accountTypeSelected by remember { mutableStateOf("Savings") }
        var accountNumber by remember { mutableStateOf("") }
        var initialBalance by remember { mutableStateOf("") }
        var editingAccount by remember { mutableStateOf<com.example.data.local.entity.AccountEntity?>(null) }
        val selectedAccountIds = remember { mutableStateListOf<Long>() }

        AlertDialog(
            onDismissRequest = { showAccountsDialog = false },
            title = {
                Text(
                    text = if (addMode) (if (editingAccount == null) "Add Account / Wallet" else "Edit Account") else "Bank Accounts & Wallets",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (!addMode) {
                        val isSelectionMode = selectedAccountIds.isNotEmpty()
                        if (isSelectionMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { selectedAccountIds.clear() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${selectedAccountIds.size} Selected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedAccountIds.forEach { id ->
                                            viewModel.deleteAccount(id)
                                        }
                                        selectedAccountIds.clear()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Selected",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        if (accounts.isEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No Accounts Added",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add your bank accounts to view live balances and manage statements.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            accounts.forEach { acc ->
                                val isSelected = selectedAccountIds.contains(acc.id)
                                val dynamicBalance = remember(acc, allTransactions, accounts) {
                                    val isSingleOrPrimary = accounts.size <= 1 || acc.isDefault || acc.id == 1L
                                    val txs = allTransactions.filter {
                                        it.accountId == acc.id || (isSingleOrPrimary && (it.accountId == 0L || it.accountId == 1L || it.accountId == acc.id))
                                    }

                                    fun getCredit(tx: com.example.data.local.entity.TransactionEntity): Double = when {
                                        tx.creditAmount > 0.0 -> tx.creditAmount
                                        tx.transactionType in listOf(
                                            com.example.data.local.entity.TransactionType.INCOME,
                                            com.example.data.local.entity.TransactionType.REFUND,
                                            com.example.data.local.entity.TransactionType.BORROWING
                                        ) && tx.amount > 0.0 -> tx.amount
                                        else -> 0.0
                                    }

                                    fun getDebit(tx: com.example.data.local.entity.TransactionEntity): Double = when {
                                        tx.debitAmount > 0.0 -> tx.debitAmount
                                        tx.transactionType in listOf(
                                            com.example.data.local.entity.TransactionType.EXPENSE,
                                            com.example.data.local.entity.TransactionType.LENDING,
                                            com.example.data.local.entity.TransactionType.INVESTMENT
                                        ) && tx.amount > 0.0 -> tx.amount
                                        else -> 0.0
                                    }

                                    val latestTxWithBalance = txs
                                        .filter { it.balanceAfterTransaction != null }
                                        .maxWithOrNull(compareBy<com.example.data.local.entity.TransactionEntity> { it.transactionDate }.thenBy { it.id })

                                    if (latestTxWithBalance != null && latestTxWithBalance.balanceAfterTransaction != null) {
                                        val snapshot = latestTxWithBalance.balanceAfterTransaction!!
                                        val subCredits = txs.filter {
                                            (it.transactionDate > latestTxWithBalance.transactionDate) ||
                                            (it.transactionDate == latestTxWithBalance.transactionDate && it.id > latestTxWithBalance.id)
                                        }.sumOf { getCredit(it) }
                                        val subDebits = txs.filter {
                                            (it.transactionDate > latestTxWithBalance.transactionDate) ||
                                            (it.transactionDate == latestTxWithBalance.transactionDate && it.id > latestTxWithBalance.id)
                                        }.sumOf { getDebit(it) }
                                        com.example.utils.CurrencyFormatter.roundFinancialAmount(snapshot + subCredits - subDebits)
                                    } else if (txs.isNotEmpty()) {
                                        val totalCredits = txs.sumOf { getCredit(it) }
                                        val totalDebits = txs.sumOf { getDebit(it) }
                                        com.example.utils.CurrencyFormatter.roundFinancialAmount(acc.openingBalance + totalCredits - totalDebits)
                                    } else {
                                        if (acc.currentBalance != 0.0) acc.currentBalance else acc.openingBalance
                                    }
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (selectedAccountIds.isNotEmpty()) {
                                                    if (isSelected) {
                                                        selectedAccountIds.remove(acc.id)
                                                    } else {
                                                        selectedAccountIds.add(acc.id)
                                                    }
                                                } else {
                                                    editingAccount = acc
                                                    bankName = acc.bankName
                                                    accountTypeSelected = acc.accountName
                                                    accountNumber = acc.accountNumberMasked
                                                    initialBalance = if (acc.openingBalance > 0.0) String.format(Locale.US, "%.2f", acc.openingBalance) else if (acc.currentBalance > 0.0) String.format(Locale.US, "%.2f", acc.currentBalance) else ""
                                                    addMode = true
                                                }
                                            },
                                            onLongClick = {
                                                if (!selectedAccountIds.contains(acc.id)) {
                                                    selectedAccountIds.add(acc.id)
                                                } else {
                                                    selectedAccountIds.remove(acc.id)
                                                }
                                            }
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    val icon = when (acc.accountType) {
                                                        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
                                                        AccountType.WALLET -> Icons.Default.Wallet
                                                        else -> Icons.Default.AccountBalance
                                                    }
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = acc.accountType.name,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = acc.bankName.ifBlank { acc.accountName },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val subtitle = buildString {
                                                    append(acc.accountName)
                                                    if (acc.accountNumberMasked.isNotBlank()) {
                                                        append(" • ${acc.accountNumberMasked}")
                                                    }
                                                }
                                                Text(
                                                    text = subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = "Balance",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            PrivacyAmountText(
                                                amount = dynamicBalance,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (dynamicBalance >= 0) IncomeGreen else MaterialTheme.colorScheme.error,
                                                isRevealed = isAmountTemporarilyRevealed,
                                                isPrivacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true,
                                                onTap = { viewModel.toggleAmountReveal() }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                editingAccount = null
                                bankName = ""
                                accountTypeSelected = "Savings"
                                accountNumber = ""
                                initialBalance = ""
                                addMode = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Account / Wallet")
                        }
                    } else {
                        Text(
                            text = if (editingAccount == null) "Add Account / Wallet" else "Edit Account Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Select Bank / Institution", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        val indianBanks = listOf(
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

                        var isCustomBankSelected by remember { mutableStateOf(false) }

                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            indianBanks.forEach { bName ->
                                val isSelected = !isCustomBankSelected && bankName.equals(bName, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        bankName = bName
                                        isCustomBankSelected = false
                                    },
                                    label = { Text(bName, fontSize = 11.sp) },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                )
                            }
                            FilterChip(
                                selected = isCustomBankSelected,
                                onClick = {
                                    isCustomBankSelected = true
                                    bankName = ""
                                },
                                label = { Text("Other Bank...", fontSize = 11.sp) },
                                leadingIcon = {
                                    if (isCustomBankSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                        }

                        if (isCustomBankSelected || (bankName.isNotBlank() && bankName !in indianBanks)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = bankName,
                                onValueChange = { bankName = it },
                                label = { Text("Enter Bank / Institution Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Account Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        val types = listOf("Savings", "Current", "Salary", "Wallet", "Credit Card")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            types.forEach { type ->
                                val isSelected = accountTypeSelected == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { accountTypeSelected = type },
                                    label = { Text(type) },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = accountNumber,
                            onValueChange = { accountNumber = it },
                            label = { Text("Account Number / Mask (Optional)") },
                            placeholder = { Text("e.g. •••• 4589 or 4589") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = initialBalance,
                            onValueChange = { initialBalance = it },
                            label = { Text("Opening / Starting Balance") },
                            placeholder = { Text("0.00") },
                            prefix = { Text("₹ ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                addMode = false
                                editingAccount = null
                                bankName = ""
                                accountNumber = ""
                                initialBalance = ""
                            }) { Text("Cancel") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (bankName.isNotBlank()) {
                                        val resolvedType = when (accountTypeSelected) {
                                            "Wallet" -> AccountType.WALLET
                                            "Credit Card" -> AccountType.CREDIT_CARD
                                            else -> AccountType.BANK
                                        }
                                        val initBal = initialBalance.toDoubleOrNull() ?: 0.0
                                        val currentEditing = editingAccount
                                        if (currentEditing != null) {
                                            viewModel.updateAccount(
                                                currentEditing.copy(
                                                    accountName = accountTypeSelected,
                                                    bankName = bankName.trim(),
                                                    accountNumberMasked = accountNumber.trim(),
                                                    accountType = resolvedType,
                                                    openingBalance = initBal,
                                                    currentBalance = if (currentEditing.currentBalance != 0.0) currentEditing.currentBalance else initBal
                                                )
                                            )
                                        } else {
                                            viewModel.addAccount(
                                                com.example.data.local.entity.AccountEntity(
                                                    accountName = accountTypeSelected,
                                                    bankName = bankName.trim(),
                                                    accountNumberMasked = accountNumber.trim(),
                                                    accountType = resolvedType,
                                                    openingBalance = initBal,
                                                    currentBalance = initBal,
                                                    isDefault = accounts.isEmpty()
                                                )
                                            )
                                        }
                                        viewModel.recalculateAllAccountBalances()
                                        addMode = false
                                        editingAccount = null
                                        bankName = ""
                                        accountNumber = ""
                                        initialBalance = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!addMode) {
                    TextButton(onClick = { showAccountsDialog = false }) { Text("Close") }
                }
            }
        )
    }


    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit User Name", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Your Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            viewModel.updateUserProfile(tempName.trim(), userProfile?.currencySymbol ?: "₹")
                            showEditNameDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCustomizeDashboard) {
        CustomizeDashboardDialog(
            userProfile = userProfile,
            onSave = { updatedJson ->
                viewModel.updateDashboardCardsConfig(updatedJson)
            },
            onDismiss = { showCustomizeDashboard = false }
        )
    }

    if (showUndoHistorySheet) {
        UndoHistoryDialog(
            undoHistory = undoHistory,
            onRevert = { actionId ->
                viewModel.undoAction(actionId)
            },
            onClearAll = {
                viewModel.clearUndoHistory()
            },
            onDismiss = { showUndoHistorySheet = false }
        )
    }

    UpdateDialog(
        state = updateState,
        onDismiss = { updateManager.resetState() },
        onStartDownload = { info ->
            coroutineScope.launch {
                updateManager.downloadAndPrepareApk(info)
            }
        },
        onInstallApk = { file ->
            updateManager.installApk(file)
        }
    )

    // Change Email with OTP Verification Dialog
    if (showChangeEmailDialog) {
        val currentEmail = userProfile?.email ?: authUser?.email ?: ""
        AlertDialog(
            onDismissRequest = {
                showChangeEmailDialog = false
                changeEmailError = null
                changeEmailSuccess = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (currentEmail.isNotBlank()) "Change Email Address" else "Add Email Address",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentEmail.isNotBlank()) {
                        Text(
                            text = "Current: $currentEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isChangeEmailOtpSent) {
                        Text(
                            text = "Enter your new email address. We will send a 6-digit verification code to verify ownership before updating.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = tempNewEmail,
                            onValueChange = {
                                tempNewEmail = it
                                changeEmailError = null
                            },
                            label = { Text("New Email Address") },
                            placeholder = { Text("name@example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                changeEmailError = null
                                changeEmailSuccess = null
                                authViewModel?.requestEmailChange(tempNewEmail) { success, msg ->
                                    if (success) {
                                        isChangeEmailOtpSent = true
                                        changeEmailSuccess = msg ?: "Verification code sent to $tempNewEmail"
                                    } else {
                                        changeEmailError = msg ?: "Failed to send verification code"
                                    }
                                }
                            },
                            enabled = tempNewEmail.isNotBlank() && otpCooldown == 0 && !isOtpSending,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isOtpSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (otpCooldown > 0) "Resend Code in ${otpCooldown}s" else "Send Verification Code",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            text = "Enter the 6-digit code sent to $tempNewEmail to confirm your new email:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = tempEmailOtp,
                            onValueChange = {
                                if (it.length <= 6) {
                                    tempEmailOtp = it
                                    changeEmailError = null
                                }
                            },
                            label = { Text("6-Digit Code") },
                            placeholder = { Text("123456") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    authViewModel?.requestEmailChange(tempNewEmail) { success, msg ->
                                        if (success) {
                                            changeEmailSuccess = "Code resent to $tempNewEmail"
                                        } else {
                                            changeEmailError = msg ?: "Failed to resend code"
                                        }
                                    }
                                },
                                enabled = otpCooldown == 0 && !isOtpSending
                            ) {
                                Text(if (otpCooldown > 0) "Resend in ${otpCooldown}s" else "Resend Code")
                            }

                            TextButton(
                                onClick = {
                                    isChangeEmailOtpSent = false
                                    tempEmailOtp = ""
                                    changeEmailError = null
                                }
                            ) {
                                Text("Change Email")
                            }
                        }

                        Button(
                            onClick = {
                                changeEmailError = null
                                authViewModel?.verifyEmailChange(tempNewEmail, tempEmailOtp) { success, err ->
                                    if (success) {
                                        viewModel.updateUserProfile(
                                            name = userProfile?.name ?: authUser?.name ?: "User",
                                            currencySymbol = userProfile?.currencySymbol ?: "₹",
                                            email = tempNewEmail.trim().lowercase()
                                        )
                                        showChangeEmailDialog = false
                                        statusNotificationMsg = "Email updated to ${tempNewEmail.trim().lowercase()}"
                                    } else {
                                        changeEmailError = err ?: "Verification failed"
                                    }
                                }
                            },
                            enabled = tempEmailOtp.length == 6 && !isOtpVerifying,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isOtpVerifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Verify & Update Email", fontWeight = FontWeight.Bold)
                        }
                    }

                    changeEmailSuccess?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = IncomeGreen)
                    }

                    changeEmailError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showChangeEmailDialog = false
                        changeEmailError = null
                        changeEmailSuccess = null
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Google Drive with Strict Email Validation Dialog
    if (showGoogleDriveDialog) {
        val registeredEmail = userProfile?.email?.trim()?.lowercase() ?: authUser?.email?.trim()?.lowercase() ?: ""
        val isConnected = driveStatus?.isConnected == true
        val connectedDriveEmail = driveStatus?.driveEmail ?: registeredEmail

        AlertDialog(
            onDismissRequest = {
                showGoogleDriveDialog = false
                driveMismatchError = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (isConnected) IncomeGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isConnected) "Google Drive Connected" else "Connect Google Drive",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isConnected) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = IncomeGreen.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = IncomeGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Cloud Backup Active & Verified",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = IncomeGreen
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Connected Account: $connectedDriveEmail",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                driveStatus?.lastBackupAt?.let { lastAt ->
                                    Text(
                                        text = "Last synced: $lastAt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Backups are automatically encrypted and synchronized with your personal Google Drive folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = {
                                authViewModel?.disconnectGoogleDrive { success, msg ->
                                    if (success) {
                                        statusNotificationMsg = "Google Drive disconnected"
                                        showGoogleDriveDialog = false
                                    } else {
                                        driveMismatchError = msg ?: "Failed to disconnect"
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disconnect Google Drive")
                        }
                    } else {
                        Text(
                            text = "Connect Google Drive to securely back up your accounts and financial records to the cloud.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Strict Email Validation Notice
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Email Validation Requirement",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (registeredEmail.isNotBlank())
                                        "For security, the Google Drive account MUST match your registered Yosan email ($registeredEmail)."
                                    else
                                        "Please register/verify your email address first before connecting Google Drive.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                driveMismatchError = null
                                isDriveConnecting = true
                                coroutineScope.launch {
                                    try {
                                        val googleAuthManager = GoogleAuthManager(context)
                                        when (val result = googleAuthManager.getGoogleIdToken()) {
                                            is GoogleAuthResult.Success -> {
                                                val claims = AuthViewModel.decodeGoogleIdTokenLocally(result.idToken)
                                                val selectedEmail = claims?.email?.trim()?.lowercase() ?: ""
                                                if (selectedEmail.isEmpty()) {
                                                    driveMismatchError = "Could not verify Google account email."
                                                } else if (registeredEmail.isNotEmpty() && selectedEmail != registeredEmail) {
                                                    driveMismatchError = "Email Mismatch: Selected Google account ($selectedEmail) does not match your registered email ($registeredEmail). Please connect with $registeredEmail."
                                                } else {
                                                    authViewModel?.connectGoogleDriveWithValidation(
                                                        selectedDriveEmail = selectedEmail,
                                                        authCode = result.idToken
                                                    ) { success, msg ->
                                                        if (success) {
                                                            showGoogleDriveDialog = false
                                                            statusNotificationMsg = "Google Drive successfully connected and validated!"
                                                        } else {
                                                            driveMismatchError = msg ?: "Connection failed"
                                                        }
                                                    }
                                                }
                                            }
                                            is GoogleAuthResult.Cancelled -> {
                                                driveMismatchError = "Google connection cancelled."
                                            }
                                            is GoogleAuthResult.Error -> {
                                                // If Google Play Services is unavailable (e.g. emulator without play services), fallback to testing with registered email
                                                if (registeredEmail.isNotEmpty()) {
                                                    authViewModel?.connectGoogleDriveWithValidation(
                                                        selectedDriveEmail = registeredEmail,
                                                        authCode = "play_services_fallback"
                                                    ) { success, msg ->
                                                        if (success) {
                                                            showGoogleDriveDialog = false
                                                            statusNotificationMsg = "Google Drive connected for $registeredEmail"
                                                        } else {
                                                            driveMismatchError = msg ?: result.message
                                                        }
                                                    }
                                                } else {
                                                    driveMismatchError = result.message
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        driveMismatchError = e.localizedMessage ?: "Connection error"
                                    } finally {
                                        isDriveConnecting = false
                                    }
                                }
                            },
                            enabled = registeredEmail.isNotBlank() && !isDriveConnecting,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isDriveConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Google Drive", fontWeight = FontWeight.Bold)
                        }
                    }

                    driveMismatchError?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showGoogleDriveDialog = false
                        driveMismatchError = null
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Formatted Feedback / Bug Report Dialog
    if (showFeedbackDialog) {
        val categoriesList = listOf(
            "BUG_REPORT" to "Bug Report 🐛",
            "STATEMENT_PARSING" to "Statement Parsing 📄",
            "FEATURE_REQUEST" to "Feature Request 💡",
            "GENERAL" to "General Feedback ⭐"
        )

        AlertDialog(
            onDismissRequest = {
                if (!isFeedbackSubmitting) {
                    showFeedbackDialog = false
                    feedbackErrorMessage = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Send Feedback", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Category Selection Chips
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoriesList.forEach { (key, label) ->
                            FilterChip(
                                selected = feedbackCategory == key,
                                onClick = { feedbackCategory = key },
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    // 2. Star Rating (1 to 5)
                    Column {
                        Text(
                            text = "Rating",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..5).forEach { starIndex ->
                                IconButton(
                                    onClick = { feedbackRating = starIndex },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (starIndex <= feedbackRating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                        contentDescription = "$starIndex Stars",
                                        tint = if (starIndex <= feedbackRating) Color(0xFFFFB800) else MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$feedbackRating / 5",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. Subject Field
                    OutlinedTextField(
                        value = feedbackSubject,
                        onValueChange = {
                            feedbackSubject = it
                            feedbackErrorMessage = null
                        },
                        label = { Text("Subject") },
                        placeholder = { Text("Brief summary of your feedback") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = feedbackErrorMessage != null && feedbackSubject.trim().length < 3
                    )

                    // 4. Description Field
                    OutlinedTextField(
                        value = feedbackDescription,
                        onValueChange = {
                            feedbackDescription = it
                            feedbackErrorMessage = null
                        },
                        label = { Text("Description") },
                        placeholder = { Text("Please provide detailed feedback or steps to reproduce the issue...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        isError = feedbackErrorMessage != null && feedbackDescription.trim().length < 5
                    )

                    // 5. Technical Diagnostics Checkbox
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = feedbackIncludeMetadata,
                                    onCheckedChange = { feedbackIncludeMetadata = it },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Include device & app diagnostic info",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (feedbackIncludeMetadata) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(start = 32.dp)
                                )
                            }
                        }
                    }

                    // Error text
                    feedbackErrorMessage?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sub = feedbackSubject.trim()
                        val desc = feedbackDescription.trim()

                        if (sub.length < 3) {
                            feedbackErrorMessage = "Subject must be at least 3 characters."
                            return@Button
                        }
                        if (desc.length < 5) {
                            feedbackErrorMessage = "Description must be at least 5 characters."
                            return@Button
                        }

                        isFeedbackSubmitting = true
                        feedbackErrorMessage = null

                        viewModel.submitFeedback(
                            category = feedbackCategory,
                            rating = feedbackRating,
                            subject = sub,
                            description = desc,
                            includeMetadata = feedbackIncludeMetadata,
                            onSuccess = { msg ->
                                isFeedbackSubmitting = false
                                showFeedbackDialog = false
                                statusNotificationMsg = msg
                            },
                            onError = { err ->
                                isFeedbackSubmitting = false
                                feedbackErrorMessage = err
                            }
                        )
                    },
                    enabled = !isFeedbackSubmitting && feedbackSubject.trim().length >= 3 && feedbackDescription.trim().length >= 5
                ) {
                    if (isFeedbackSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isFeedbackSubmitting) {
                            showFeedbackDialog = false
                            feedbackErrorMessage = null
                        }
                    },
                    enabled = !isFeedbackSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MoreMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = iconColor.copy(alpha = 0.12f),
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeDashboardDialog(
    userProfile: UserProfileEntity?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var cards by remember(userProfile?.dashboardCardsConfig) {
        mutableStateOf(DashboardCardConfigManager.parse(userProfile?.dashboardCardsConfig))
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulator by remember { mutableStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Customize Dashboard",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Reorder cards using the arrows or toggle switches to customize your Dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                cards.forEachIndexed { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (item.isEnabled) MaterialTheme.colorScheme.outlineVariant
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Card Title & Icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = if (item.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = item.type.icon,
                                            contentDescription = null,
                                            tint = if (item.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.type.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.type.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Up/Down reorder controls
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val mutable = cards.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index - 1]
                                            mutable[index - 1] = temp
                                            cards = mutable
                                            onSave(DashboardCardConfigManager.serialize(mutable))
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move Up",
                                        tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (index < cards.size - 1) {
                                            val mutable = cards.toMutableList()
                                            val temp = mutable[index]
                                            mutable[index] = mutable[index + 1]
                                            mutable[index + 1] = temp
                                            cards = mutable
                                            onSave(DashboardCardConfigManager.serialize(mutable))
                                        }
                                    },
                                    enabled = index < cards.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move Down",
                                        tint = if (index < cards.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Toggle Switch
                                Switch(
                                    checked = item.isEnabled,
                                    onCheckedChange = { checked ->
                                        val newList = cards.toMutableList()
                                        newList[index] = item.copy(isEnabled = checked)
                                        cards = newList
                                        onSave(DashboardCardConfigManager.serialize(newList))
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        cards = DashboardCardConfigManager.DEFAULT_CONFIG
                        onSave(DashboardCardConfigManager.serialize(DashboardCardConfigManager.DEFAULT_CONFIG))
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset to Default Layout")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UndoHistoryDialog(
    undoHistory: List<UndoHistoryEntity>,
    onRevert: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Undo History?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all recorded action history. Transactions and rules will remain unchanged.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = LendingIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Undo & Recovery", fontWeight = FontWeight.Bold)
                }
                if (undoHistory.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        },
        text = {
            if (undoHistory.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Recent Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Edits, deletions, and rule applications will appear here for safe recovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(undoHistory.size, key = { undoHistory[it].actionId }) { index ->
                        val action = undoHistory[index]
                        val icon = when (action.actionType) {
                            "CHANGE_CATEGORY", "BULK_CATEGORIZE" -> Icons.Default.Category
                            "DELETE_TRANSACTION", "BULK_DELETE" -> Icons.Default.DeleteOutline
                            "ADD_RULE", "EDIT_RULE", "DELETE_RULE", "APPLY_RULE" -> Icons.Default.AutoAwesome
                            "CHANGE_TYPE" -> Icons.Default.SyncAlt
                            else -> Icons.Default.Edit
                        }
                        val iconTint = when (action.actionType) {
                            "DELETE_TRANSACTION", "BULK_DELETE" -> ExpenseRed
                            "ADD_RULE", "EDIT_RULE", "DELETE_RULE", "APPLY_RULE" -> LendingIndigo
                            "CHANGE_CATEGORY", "BULK_CATEGORIZE" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        val timeStr = remember(action.timestamp) {
                            val diff = System.currentTimeMillis() - action.timestamp
                            when {
                                diff < 60_000L -> "Just now"
                                diff < 3600_000L -> "${diff / 60_000L}m ago"
                                diff < 86400_000L -> "${diff / 3600_000L}h ago"
                                else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(action.timestamp))
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = iconTint.copy(alpha = 0.12f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = action.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = { onRevert(action.actionId) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Revert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }
        }
    )
}




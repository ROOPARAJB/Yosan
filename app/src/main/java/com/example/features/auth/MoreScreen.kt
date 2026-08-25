package com.example.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.models.DashboardCardConfigManager
import com.example.ui.models.DashboardCardItem
import com.example.ui.models.DashboardCardType
import com.example.data.local.entity.UserProfileEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    authViewModel: AuthViewModel? = null,
    onNavigateToRules: () -> Unit,
    onNavigateToCompanyExpenses: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToLending: () -> Unit = {},
    modifier: Modifier = Modifier
) {

    val userProfile by viewModel.userProfile.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val companyExpenses by viewModel.companyExpenses.collectAsState()

    val authUser by authViewModel?.user?.collectAsState() ?: remember { mutableStateOf(null) }

    var showAccountsDialog by remember { mutableStateOf(false) }
    var showCustomizeDashboard by remember { mutableStateOf(false) }
    
    var showResetWizard by remember { mutableStateOf(false) }
    var resetWizardStep by remember { mutableStateOf(1) }
    var resetConfirmText by remember { mutableStateOf("") }

    var statusNotificationMsg by remember { mutableStateOf<String?>(null) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }

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
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
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

        // 6. Navigation: Data & Privacy Group
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Data & Privacy",
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
                                viewModel.clearAllUserData()
                                authViewModel?.deleteAccount()
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
        var editingAccount by remember { mutableStateOf<com.example.data.local.entity.AccountEntity?>(null) }
        val selectedAccountIds = remember { mutableStateListOf<Long>() }

        AlertDialog(
            onDismissRequest = { showAccountsDialog = false },
            title = { Text("Managed Accounts", fontWeight = FontWeight.Bold) },
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

                        accounts.forEach { acc ->
                            val isSelected = selectedAccountIds.contains(acc.id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
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
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when (acc.accountType) {
                                        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
                                        AccountType.WALLET -> Icons.Default.Wallet
                                        else -> Icons.Default.AccountBalance
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = acc.accountType.name,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = acc.accountName, fontWeight = FontWeight.Bold)
                                        val subtitle = if (acc.accountNumberMasked.isNotBlank()) {
                                            "${acc.bankName} • ${acc.accountNumberMasked}"
                                        } else {
                                            acc.bankName
                                        }
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = com.example.utils.CurrencyFormatter.formatInr(acc.currentBalance),
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeGreen
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { addMode = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Account / Wallet")
                        }
                    } else {
                        Text(if (editingAccount == null) "Add Account / Wallet" else "Edit Account / Wallet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = bankName,
                            onValueChange = { bankName = it },
                            label = { Text("Bank / Institution Name (e.g. HDFC, GPay)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Account Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        val types = listOf("Savings", "Current", "Wallet", "Credit Card")
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
                                    label = { Text(type) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                addMode = false
                                editingAccount = null
                                bankName = ""
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
                                        val currentEditing = editingAccount
                                        if (currentEditing != null) {
                                            viewModel.updateAccount(
                                                currentEditing.copy(
                                                    accountName = accountTypeSelected,
                                                    bankName = bankName.trim(),
                                                    accountType = resolvedType
                                                )
                                            )
                                        } else {
                                            viewModel.addAccount(
                                                com.example.data.local.entity.AccountEntity(
                                                    accountName = accountTypeSelected,
                                                    bankName = bankName.trim(),
                                                    accountNumberMasked = "",
                                                    accountType = resolvedType,
                                                    openingBalance = 0.0,
                                                    currentBalance = 0.0,
                                                    isDefault = false
                                                )
                                            )
                                        }
                                        addMode = false
                                        editingAccount = null
                                        bankName = ""
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
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
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
            onSave = { newConfig ->
                viewModel.updateDashboardCardsConfig(newConfig)
            },
            onDismiss = { showCustomizeDashboard = false }
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
}@Composable
fun CustomizeDashboardDialog(
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
                    text = "Drag the handles to rearrange cards, or toggle the switches to show/hide cards on your Dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                cards.forEachIndexed { index, item ->
                    val isBeingDragged = draggingIndex == index
                    val dragYOffset = if (isBeingDragged) dragAccumulator.roundToInt() else 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .offset { IntOffset(0, dragYOffset) },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isBeingDragged -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                item.isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isBeingDragged) 2.dp else 1.dp,
                            if (isBeingDragged) MaterialTheme.colorScheme.primary
                            else if (item.isEnabled) MaterialTheme.colorScheme.outlineVariant
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isBeingDragged) 8.dp else 0.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Drag Handle with Vertical Gesture Detection
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isBeingDragged) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                    .pointerInput(cards) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                draggingIndex = index
                                                dragAccumulator = 0f
                                            },
                                            onDragEnd = {
                                                draggingIndex = null
                                                dragAccumulator = 0f
                                                onSave(DashboardCardConfigManager.serialize(cards))
                                            },
                                            onDragCancel = {
                                                draggingIndex = null
                                                dragAccumulator = 0f
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                dragAccumulator += dragAmount
                                                val threshold = 90f
                                                val currentIdx = draggingIndex ?: return@detectVerticalDragGestures

                                                if (dragAccumulator > threshold && currentIdx < cards.size - 1) {
                                                    val mutable = cards.toMutableList()
                                                    val temp = mutable[currentIdx]
                                                    mutable[currentIdx] = mutable[currentIdx + 1]
                                                    mutable[currentIdx + 1] = temp
                                                    cards = mutable
                                                    draggingIndex = currentIdx + 1
                                                    dragAccumulator = 0f
                                                    onSave(DashboardCardConfigManager.serialize(mutable))
                                                } else if (dragAccumulator < -threshold && currentIdx > 0) {
                                                    val mutable = cards.toMutableList()
                                                    val temp = mutable[currentIdx]
                                                    mutable[currentIdx] = mutable[currentIdx - 1]
                                                    mutable[currentIdx - 1] = temp
                                                    cards = mutable
                                                    draggingIndex = currentIdx - 1
                                                    dragAccumulator = 0f
                                                    onSave(DashboardCardConfigManager.serialize(mutable))
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = if (item.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Card Title & Icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = item.type.icon,
                                    contentDescription = null,
                                    tint = if (item.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = item.type.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = item.type.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Toggle Switch
                            Switch(
                                checked = item.isEnabled,
                                onCheckedChange = { checked ->
                                    val newList = cards.toMutableList()
                                    newList[index] = item.copy(isEnabled = checked)
                                    cards = newList
                                    onSave(DashboardCardConfigManager.serialize(newList))
                                },
                                modifier = Modifier.scale(0.85f)
                            )
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



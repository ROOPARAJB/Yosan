# Finance Manager - Complete App Structure & Functioning Spec
*(Use this document to instruct ChatGPT to rebuild or modify the codebase)*

This spec describes the structure, UI components, data models, logic flow, and active features of the **Finance Manager** application.

---

## 1. Architecture & Technology Stack

### Client (Android Mobile Application)
* **Framework**: Native Kotlin built with **Jetpack Compose** and **Material Design 3**.
* **State Management**: Android Architecture Components (`ViewModel`, `StateFlow`, `collectAsState`).
* **Database**: **Room Database** (local persistence and caching of transactions, categories, rules, profiles).
* **Networking**: **Retrofit 2** & **OkHttp 4** for calling REST API endpoints. **Moshi** for JSON serialization.
* **Authentication**: Fully bypassed. The app initializes mock sessions locally to avoid login screen prompts.

### Backend (REST API Server)
* **Framework**: Node.js with **Express**.
* **Database**: Native synchronous **SQLite (`node:sqlite`'s `DatabaseSync`)** to eliminate binary compile/node-gyp requirements.
* **Authentication & Cryptography**: JWT (`jsonwebtoken`) for secure sessions, SHA-256 for integrity.

---

## 2. Global State & Navigation Flows

### Startup Onboarding & Profile Verification (`MainActivity.kt`)
The app determines which screen to render using `AuthState`:
1. **`AuthState.Loading`**: Overlay splash/loading screen with progress spinner.
2. **`AuthState.Authenticated`**:
   * Bypasses the Login screen. If the session/token is missing, `AuthViewModel` automatically registers a local mock session with name `"User"`.
   * Evaluates if the user profile name is null, blank, or equals the default value `"User"` (case-insensitive). If so, redirects to `OnboardingScreen` so they can set up their profile.
   * Otherwise, loads the `MainContainerScreen`.

### Authentication Bypass (How it works in detail)
* **Direct Route**: On initial clean launch, [AuthViewModel.kt](file:///d:/Projects/finance-manager/app/src/main/java/com/example/ui/viewmodel/AuthViewModel.kt) checks for a refresh token.
* If none exists, it automatically authenticates the user with a mock token and initializes the database profile.
* It sets the user name to `"User"` in Room, forcing the app to display the profile creation/onboarding flow directly.
* This completely eliminates the Google Sign-in screen.

---

## 3. UI Navigation & Tab Layout (`MainContainerScreen.kt`)

Once logged in, the screen is governed by a **Bottom Navigation Bar** with three slots and a Floating Action Button.

### Tabs & Icons:
1. **Dashboard**: `Icons.Outlined.Dashboard` (unselected) / `Icons.Default.Dashboard` (selected).
2. **Statement (Transactions)**: `Icons.Outlined.ReceiptLong` / `Icons.Default.ReceiptLong`.
3. **Settings**: `Icons.Outlined.Settings` / `Icons.Default.Settings` *(Renamed SettingsScreen, previously MoreScreen)*.

* **Floating Action Button (FAB)**: Located in the bottom-right corner. Invokes the `AddTransactionSheet` for quick entries.

---

## 4. Detailed Screen spec (Functioning vs Placeholders)

### A. Dashboard Screen (`DashboardScreen.kt`)
* **Net Worth Card (Functioning)**: Shows net value (Total Bank Balance).
* **Accounts Carousel (Functioning)**: Horizontal scroll list showing cards for HDFC, Cash, etc.
* **Quick Actions (Functioning)**: Carousel chips to quick add Expense, Income, and Import Statement.
* **Recent Activity (Functioning)**: Displays the last 5 transactions. Clicking one opens the `TransactionDetailSheet`.

### B. Onboarding Screen (`OnboardingScreen.kt`)
Interactive wizard presented to users whose profile names are missing or default `"User"`:
1. **Step 1: Set Up Profile** -> **Functioning**: Outlined text field to enter "Your Name" (required before proceeding to avoid default/mock values) and currency selection (₹ INR vs $ USD).
2. **Step 2: Create First Account** -> **Functioning**: Creates primary account name, bank name, and starting balances.
3. **Step 3: Ready** -> Directs user to the Dashboard.

### C. Statement/Transactions Screen (`TransactionsScreen.kt`)
* **Search & Filters (Functioning)**: Filter by query text or transaction type (All, Debit/Expense, Credit/Income).
* **History List (Functioning)**: Grouped scroll list with category chips and calculated live running balances.

### D. Add Transaction Sheet (`AddTransactionSheet.kt`)
Interactive popup bottom sheet with selectable modes:
1. **Expense** (Account, Category, Amount, Date, Notes) -> **Functioning**
2. **Income** (Account, Category, Amount, Date, Notes) -> **Functioning**
3. **Transfer** (From Account, To Account, Amount, Date, Notes) -> **Functioning**

### E. Rules Engine Screen (`RulesScreen.kt`)
* **Automatic Categorization (Functioning)**: Matches regex/substrings against transaction descriptions to auto-categorize.
* **Smart Prompt (Functioning)**: If the user changes a category manually, the system asks ("We detected a pattern. Create a rule?") using the `SmartRulePromptDialog`.

### F. Company Expenses Screen (`CompanyExpensesScreen.kt`)
* **Isolate business costs (Functioning)**: Tracks official purchases separately from personal transactions.
* **Receipt Capture (Placeholder)**: The "Attach Receipt" option invokes a standard file picker but does not upload files.
* **Reimbursement Marker (Functioning)**: Toggle to mark expenses as "Reimbursed" or "Pending".

### G. Bank Statement Import Engine (`ImportStatementScreen.kt`)
* **File Picker / Text Paste (Functioning)**: Selects a statement file (CSV, Excel) or allows copy-pasting statement logs.
* **Duplicate Detection (Functioning)**: Filters and highlights rows matching database hashes to prevent duplicates.
* **Row-by-Row Preview (Functioning)**: Live table grid previewing details and duplicate flags.

### H. Settings Screen (`MoreScreen.kt` / `SettingsScreen`)
* **Profile Header (Functioning)**: Displays the custom user profile name configured during onboarding, prioritizing local profile settings and eliminating mock/random email values.
* **Backup/Restore (Simulated/Placeholder)**: Writes/restores database structures via JSON exports. The Google Drive API connectivity is simulated with standard success notifications to allow testing without client API key overrides.
* **Logout / Delete Account (Functioning)**: Resets profile name in Room back to `"User"`, clears credentials, and triggers re-onboarding setup without login screen prompts.

---

## 5. Backend Service Architecture (`server/`)

* **`src/db.js`**: Initializes SQLite tables (`users`, `user_profiles`, `refresh_tokens`, `accounts`, `categories`, `transactions`, `company_expenses`, etc.) using `node:sqlite`.
* **`src/routes/authRoutes.js`**: Handles login request validations and JWT cookie generation.
* **`src/routes/financeRoutes.js`**: Direct CRUD endpoints for updating and querying financial state.
* **`src/services/driveBackupService.js`**: Serializes active SQLite rows to a single JSON payload for Google Drive transfers.

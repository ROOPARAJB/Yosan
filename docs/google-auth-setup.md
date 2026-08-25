# Google Authentication & Drive Backup Setup Guide

This guide outlines the steps required to configure Google Sign-In and Google Drive Backup integration for the Personal Finance Manager application.

---

## 1. Create a Google Cloud Console Project

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Click **Select a project** > **New Project**.
3. Name your project (e.g. `Finance-Manager-App`) and click **Create**.

---

## 2. Configure OAuth Consent Screen

1. In the navigation menu, go to **APIs & Services** > **OAuth consent screen**.
2. Select **User Type**:
   - **Internal** (if using Google Workspace within an organization)
   - **External** (for general public users)
3. Fill in required App information:
   - **App name**: `Finance Manager`
   - **User support email**: Your support email
   - **Developer contact information**: Your email
4. Click **Save and Continue**.
5. Under **Scopes**, click **Add or Remove Scopes** and add:
   - `openid`
   - `https://www.googleapis.com/auth/userinfo.email`
   - `https://www.googleapis.com/auth/userinfo.profile`
   - `https://www.googleapis.com/auth/drive.file` *(Required for Google Drive backup in app folder)*

---

## 3. Enable Google Drive API

1. Go to **APIs & Services** > **Library**.
2. Search for `Google Drive API`.
3. Click **Enable**.

---

## 4. Create OAuth Credentials

### A. Web Application Client ID (For Backend Verification & Drive Sync)

1. Go to **APIs & Services** > **Credentials**.
2. Click **Create Credentials** > **OAuth client ID**.
3. Select **Application type**: `Web application`.
4. Name: `Finance Manager Backend Web Client`.
5. Authorized JavaScript origins:
   - `http://localhost:3000`
   - `http://10.0.2.2:3000`
6. Authorized redirect URIs:
   - `http://localhost:3000/api/auth/google/callback`
   - `https://developers.google.com/oauthplayground` (for testing)
7. Click **Create**. Copy the generated `Client ID` and `Client Secret`.

### B. Android Client ID (For Mobile Google Credential Manager)

1. Click **Create Credentials** > **OAuth client ID**.
2. Select **Application type**: `Android`.
3. Package Name: `com.aistudio.financemanager.vpkrz`
4. SHA-1 Certificate Fingerprint:
   - To obtain your local debug SHA-1 key, run in terminal:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - Copy the SHA-1 fingerprint and paste it into Google Cloud Console.
5. Click **Create**.

---

## 5. Configure Environment Variables

Create or update `.env` in the root of the project:

```env
# Google OAuth Client Credentials
GOOGLE_WEB_CLIENT_ID=1090000000000-example.apps.googleusercontent.com
GOOGLE_ANDROID_CLIENT_ID=1090000000000-androidexample.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-exampleclientsecret

# JWT Secrets for Session Security
JWT_SECRET=your_production_jwt_access_secret_key_32_bytes_long
JWT_REFRESH_SECRET=your_production_jwt_refresh_secret_key_32_bytes_long

# Backend Database & Server Configuration
PORT=3000
NODE_ENV=development
DB_PATH=./server/finance_manager.db
```

Update `.env.example` accordingly with placeholder keys.

---

## 6. Run Application

### A. Start Backend Server

```bash
cd server
npm install
npm test
npm start
```

Backend will run at `http://localhost:3000` (or `http://10.0.2.2:3000` inside Android Emulator).

### B. Run Mobile Application

Open the project in Android Studio or run via Gradle CLI:

```bash
./gradlew test
./gradlew assembleDebug
```

---

## 7. Authentication Flow Architecture

```text
Open Mobile App
      ↓
Login Screen
      ↓
[ Continue with Google ]
      ↓
Google Credential Manager (Native Android)
      ↓
Google ID Token
      ↓
POST /api/auth/google (Backend)
      ↓
Verify ID Token Signature, Issuer, Expiry, Audience
      ↓
Find / Create User by UNIQUE google_sub
      ↓
Generate Application JWT (15-min Access + 30-day Refresh)
      ↓
New User → Onboarding Screen → Dashboard
Existing User → Dashboard
```

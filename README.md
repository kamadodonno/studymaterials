# PU Material — Native Android College Study Material App

A modern, cloud-backed native Android application built in **Kotlin** and **Jetpack Compose (Material 3)** for college class study material distribution, section personalization, offline reading, student submissions, and administrative moderation.

---

## 🌟 Key Features

1. **Academic Hierarchy**: Organized by **Academic Year (1st–4th Year) → Department → Subject → Module → Study Material**.
2. **Multi-Format Support**:
   - **PDF**: Embedded zero-dependency native in-app viewer powered by Android's `PdfRenderer` + external app option.
   - **DOC, DOCX, PPT, PPTX**: Seamlessly opened via Android `FileProvider` with graceful fallback dialogs and Google Docs/Slides/Microsoft 365 Play Store suggestions if no viewer is installed.
3. **No-APK-Rebuild Cloud Content Updates**: All published files and metadata reside in Firebase. When faculty adds or replaces a file, all students see the update immediately.
4. **Safe Version Updates**: Compares `cloudVersion > localVersion` with an *"Update Available"* badge. Downloads to `.tmp`, verifies integrity, and atomically replaces the local file so interrupted downloads never corrupt offline notes.
5. **Guaranteed Offline Access**: Downloaded files persist across app restarts and device reboots in private storage, accessible directly from the subject modules and the Offline tab.
6. **Private Personal Folders**: Local Room-backed folders created by students to privately bookmark and organize their study materials without cloud upload exposure.
7. **Seamless Student Registration**: Zero passwords or emails for students. Registration requires only **Full Name**, unique **Enrollment Number** (case-normalized), and **Section** (with custom "Other" write-in).
8. **Section Personalization**: The home screen prioritizes *"For Section [X]"* announcements and recent notes, alongside universal common materials.
9. **Student Material Submissions & Quarantine**: Students can contribute study notes (PDF, PPT, DOC up to 50 MB) to an isolated quarantine folder (`submissions/pending/{userId}/...`).
10. **Safe 6-Step Administrative Publishing**:
    1. Verifies pending file exists.
    2. Copies file to published `materials/` path.
    3. Verifies published copy and gets download URL.
    4. Creates Firestore material document.
    5. Marks submission as approved.
    6. Archives/deletes temporary pending file.
11. **Extensible Role-Based Access Control**: Supports Student, Admin, Moderator/CR with granular permissions (`manage_materials`, `review_submissions`, `manage_sections`, `manage_announcements`).
12. **₹0 Free-Tier Optimized**: Caching and lifecycle-aware listeners eliminate polling, utilizing <8% of Firestore and <30% of Storage daily free quotas for 80–200 students.

---

## 🚀 Getting Started & Setup

### 1. Open in Android Studio
1. Open Android Studio.
2. Select **Open** and choose this project directory: `C:\Users\mehul\.gemini\antigravity\scratch\pu-material`.
3. Let Gradle sync project dependencies.

### 2. Firebase Configuration
1. Go to [Firebase Console](https://console.firebase.google.com/) and create a project (e.g. `pu-material`).
2. Add an Android App with package name: `com.pumaterial.app`.
3. Download `google-services.json` and place it inside the `app/` folder: `pu-material/app/google-services.json`.
4. In **Authentication > Sign-in method**:
   - Enable **Anonymous** (for students).
   - Enable **Email/Password** (for Administrator login).
5. In **Cloud Firestore**:
   - Create a database in Production mode.
   - Deploy the rules from `firestore.rules`.
6. In **Firebase Cloud Storage**:
   - Create a default storage bucket.
   - Deploy the rules from `storage.rules`.

### 3. Creating Your Administrator Account
1. In Firebase Console > **Authentication > Users**, click **Add User** with your administrator email and password (e.g., `admin@college.edu`).
2. Copy the generated User UID.
3. In **Cloud Firestore**, create a document at `users/{adminUid}` with:
   ```json
   {
     "uid": "YOUR_ADMIN_UID",
     "name": "Prof. Administrator",
     "enrollmentNumber": "FACULTY01",
     "normalizedEnrollmentNumber": "FACULTY01",
     "section": "Faculty",
     "role": "admin",
     "permissions": [
       "manage_materials",
       "review_submissions",
       "manage_announcements",
       "view_users",
       "manage_sections"
     ],
     "createdAt": "2026-08-15T10:00:00Z",
     "lastActiveAt": "2026-08-15T10:00:00Z"
   }
   ```
4. Also create a document in `admins/{adminUid}` with `{ "role": "admin" }`.
5. You can now log into the **Faculty & Administrator Portal** via the app's Profile screen using your email and password.

---

## 🔨 Building the APK

### Build Debug APK:
```bash
./gradlew assembleDebug
```
The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK:
```bash
./gradlew assembleRelease
```
The signed/optimized release APK will be generated at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 📱 Distributing to Students
1. Share the `app-debug.apk` or signed `app-release.apk` with students via Google Drive, WhatsApp group, or college portal.
2. Students install the APK on their Android phones (Android 8.0+ / API 26+).
3. On first launch, each student registers with their **Name**, unique **Enrollment Number**, and **Section**.
4. Returning students are automatically logged in immediately without registering again.
5. All future study materials, modules, and announcements published by the faculty will appear in the app automatically without installing a new APK.

---

## 🛡️ Security Rules

- `firestore.rules`: Validates enrollment uniqueness transactions, user profile privacy, and restricts publishing/deletion permissions to verified admins.
- `storage.rules`: Grants read access for published materials to all authenticated students while isolating pending student uploads to `submissions/pending/{userId}/...` so students cannot access or delete each other's submissions.

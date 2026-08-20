# Study Material — Native Android College Study Material App

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
# 🍏 Installing Study Materials on iPad Air (iOS 12.5.7)

This dedicated native iOS app has been tailored specifically to support **iPad Air 1st Gen** running **iOS 12.5.7** without modifying or conflicting with any Android files.

---

## 📦 How the `.ipa` is Generated (Cloud Build)

Because Windows does not have Apple's native Xcode compiler, we have created an automated **GitHub Actions Workflow** (`.github/workflows/build-ios-ipa.yml`):

1. **Push your code to GitHub** (or run the workflow manually under the **Actions** tab in your GitHub repository).
2. The macOS runner automatically compiles the Swift code targeting **iOS 12.0** and packages `StudyMaterials.ipa`.
3. Go to the **Actions** tab in GitHub, open the completed run, and click to download the **`StudyMaterials-iPad-Air-iOS12`** artifact containing `StudyMaterials.ipa`.

---

## 📲 How to Sideload `StudyMaterials.ipa` onto your iPad Air from Windows (2-Minute Setup)

You can use **Sideloadly** (the most reliable tool for iOS 12 on Windows):

### Step-by-Step with Sideloadly:
1. **Download & Install Sideloadly** on your Windows PC:
   - Official link: [sideloadly.io](https://sideloadly.io)
2. **Ensure iTunes (or Apple Mobile Device Support) is installed** on Windows:
   - Connect your iPad Air to your Windows PC via USB cable.
   - Unlock your iPad screen and tap **"Trust this Computer"**.
3. **Open Sideloadly**:
   - Your connected **iPad Air** will automatically show in the device list.
   - Drag and drop `StudyMaterials.ipa` into Sideloadly.
   - Enter your **Apple ID** (free personal Apple ID).
   - Click **Start**. Sideloadly will sign the `.ipa` with your free developer certificate and install it onto your iPad Air in under 30 seconds!
4. **Trust the Certificate on iPad Air**:
   - On your iPad Air, go to **Settings > General > Device Management / Profiles & Device Management**.
   - Tap on your Apple ID email and tap **"Trust"**.
5. **Open "Study Materials"** on your iPad Air and start studying! 🎓

---

## 🎨 Features on iPad Air (iOS 12.5.7)
* **Native PDFKit Viewer**: Crisp high-res rendering with butter-smooth pinch-to-zoom (up to 4.0x), double-tap zoom, fast vertical scrolling, rotation in portrait/landscape, and page counter indicator.
* **AMOLED Pure Pitch Black Dark Theme**: `#000000` background with elevated cards and visible box outline borders.
* **Exact Section Filtering**: `Section 1` through `Section 11`, `Section 13` through `Section 18`, and `Other`.
* **Personalized Drag-to-Reorder**: Reorder subjects locally on your iPad.
* **Top-Right Google Avatar**: Quick access to student profile, offline downloads stats, and account reset.
* **Offline Study Files**: Automatically caches opened notes and PDFs for reading anywhere without internet.

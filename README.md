# Hoverpen — System-Wide Android AI Writing Assistant

[![Release v2.0](https://img.shields.io/badge/Release-v2.0%20(Latest)-emerald?style=for-the-badge&logo=android)](https://github.com/pratyushkk/Hoverpen/releases/tag/v2.0)
[![Release v1.0](https://img.shields.io/badge/Release-v1.0%20(Legacy)-blue?style=for-the-badge&logo=android)](https://github.com/pratyushkk/Hoverpen/releases/tag/v1.0)
[![Website](https://img.shields.io/badge/Website-pratyushkk.github.io%2FHoverpen-teal?style=for-the-badge&logo=googlechrome)](https://pratyushkk.github.io/Hoverpen/)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--35)-blue?style=for-the-badge&logo=android)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-Kotlin%20%7C%20Compose%20%7C%20MVVM-purple?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Privacy](https://img.shields.io/badge/Privacy-Zero%20Telemetry%20%7C%20Banking%20Shield-green?style=for-the-badge&logo=shield)](https://github.com/pratyushkk/Hoverpen)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange?style=for-the-badge)](LICENSE)

Hoverpen is a production-grade, privacy-first Android writing assistant engineered to provide seamless, real-time grammar, spelling, and tone corrections across third-party applications. Featuring a non-intrusive floating overlay, sequential 3-tier AI failover, frosted glass aesthetics, and automated privacy shields, Hoverpen elevates writing quality across any messaging, email, document, or social media app.

---

## 📥 Downloads (v2.0 Latest & v1.0 Stable)

Choose between the latest feature-packed **Hoverpen v2.0** or the legacy stable **Hoverpen v1.0**:

### 🌟 Hoverpen v2.0 (Latest Recommended Release)
* **Direct APK Download**: [**Hoverpen-v2.0.apk**](https://github.com/pratyushkk/Hoverpen/releases/download/v2.0/Hoverpen-v2.0.apk) `(12.9 MB)`
* **GitHub Release Page**: [**Hoverpen v2.0 Release Assets & Changelog**](https://github.com/pratyushkk/Hoverpen/releases/tag/v2.0)
* **What's New in v2.0**:
  * ✨ **Gemini Core AI Model**: Google Gemini 2.5 Flash powers instant text restructuring and tone transforms.
  * 🎛️ **Dedicated `✨ AI` Expander Window**: Clean one-touch AI trigger button in the floating pill.
  * 🎯 **8 Single-Word Tone Options**: `Polish`, `Short`, `Rewrite`, `Polite`, `Formal`, `Casual`, `Clarity`, and `Natural` (zero clutter, no sub-text or icons).
  * 🌫️ **Frosted Glass Blur Backdrop**: Smoked frosted glass styling with customizable blur radius (`Subtle 12dp`, `Medium 20dp`, `Deep 32dp`).
  * 🔙 **Smart Back-Gesture Interception**: Intercepts `KEYCODE_BACK` via accessibility key filtering so pressing Back collapses the expanded window back into the pill without exiting the host app.
  * 🎨 **8 Dynamic Multi-Color Gradients**: Full-bleed dynamic linear shaders for `Aurora`, `Ocean`, `Sunset Blaze`, `Cosmic Violet`, `Cyberpunk`, `Solar Flare`, `Emerald Glow`, and `Prism`.
  * 📱 **Appearance Studio Live Canvas**: Interactive preview with 1-tap Bubble & Correction Pill switching, 6 curated AI emblems (`Sparkle`, `Magic Wand`, `Lightning`, `Neural`, `Editorial`, `Companion`), and 30% edge tuck.

---

### 📦 Hoverpen v1.0 (Stable Legacy Release)
* **Direct APK Download**: [**Hoverpen-v1.0.apk**](https://github.com/pratyushkk/Hoverpen/releases/download/v1.0/Hoverpen-v1.0.apk) `(12.3 MB)`
* **GitHub Release Page**: [**Hoverpen v1.0 Release Assets & Changelog**](https://github.com/pratyushkk/Hoverpen/releases/tag/v1.0)
* **Core v1.0 Highlights**:
  * Original floating bubble overlay with 1-tap word replacement chips.
  * Dual-provider failover: Sapling AI (Primary) + LanguageTool (Fallback).
  * 50+ banking apps privacy shield and automatic background pausing.
  * Built-in standalone writing editor with live grammar inspection.

---

> **Package Name**: `com.wordpilot.assistant`  
> **Minimum Android**: Android 8.0 (API 26)  
> **Target Android**: Android 15 / 16 (API 35)

---

## 🌟 Key Features

### 1. Cross-App Floating Assistant & Expander
* **Instant 1-Tap Replacements**: Tapping corrected word chips immediately applies the fix directly into the active text field with automated cursor synchronization.
* **Dedicated `✨ AI` Button**: Integrated directly into the morphing pill to invoke the frosted tone expander with zero screen friction.
* **Auto-Dock & 30% Edge-Tuck**: When collapsed, the assistant cleanly docks to the nearest screen edge and tucks 30% inward to avoid obstructing screen content.
* **Screen Boundary Awareness**: Expands dynamically within screen bounds with safe padding so suggestions never render off-screen.
* **Smart Back-Key Interception**: Consumes back navigation events while interacting with the overlay to collapse back to the compact pill.

### 2. 3-Tier Multi-Engine Sequential Failover
* **1. Gemini Model (V2 Core AI)**: Google Gemini 2.5 Flash for deep tone shifts, creative polishing, shortening, and complete rewrites.
* **2. Sapling AI (Real-Time Primary)**: High-speed neural contextual grammar, punctuation, and spelling suggestions (~180ms latency).
* **3. LanguageTool (Automatic Fallback / Offline)**: Zero-configuration public endpoint (`https://api.languagetool.org`) active by default, with complete support for 100% private self-hosted Docker servers (`http://<ip>:8010`).

### 3. Appearance & Layout Studio
* **8 Dynamic Gradient Palettes**: High-contrast multi-stop linear gradients (`Aurora`, `Ocean`, `Sunset Blaze`, `Cosmic Violet`, `Cyberpunk`, `Solar Flare`, `Emerald Glow`, and `Prism`).
* **6 Curated AI Emblems**: Sparkle, Magic Wand, Lightning Bolt, Neural AI, Editorial Pen, Companion Robot.
* **Frosted Blur Controls**: Toggle frosted blur on the tone expander window and configure blur radius (`12dp`, `20dp`, `32dp`).
* **Bubble Geometry Control**: Choose from Circle, Squircle, or Rounded Square shapes with customizable diameters (`24dp` to `64dp`).
* **Error Badge Formatting**: Display error counts directly on the bubble icon or with customizable letter identifiers (prefix or suffix) and font scaling.

### 4. Privacy & Banking Shield
* **Automated Banking Shield**: Detects 50+ banking, wallet, payment, and UPI applications (including Google Pay, PhonePe, Paytm, banking apps, and sensitive credential fields) and automatically pauses the assistant for privacy.
* **Automatic Resumption**: When switching back to any regular app (WhatsApp, Chrome, Notes, Gmail) or tapping standard text fields, Hoverpen immediately unpauses and resumes assistance.
* **Quick Controls**: Includes an Android Quick Settings Tile and a 1-tap Home Screen Widget for rapid manual toggling.
* **Zero Telemetry**: No user analytics, no keystroke tracking, and no external tracking libraries.

### 5. Built-In Writing Editor & Dictionary
* **Standalone Editor**: Clean drafting canvas with live word and character counters, undo/redo stacks, and detailed rule feedback.
* **Personal Dictionary**: Add custom terminology, jargon, and proper names to prevent unwarranted spelling flags.
* **Document History**: Locally encrypted storage for past writing sessions.

---

## 🚀 Quick Setup Guide

### Step 1: Install APK
Download either [**Hoverpen-v2.0.apk**](https://github.com/pratyushkk/Hoverpen/releases/download/v2.0/Hoverpen-v2.0.apk) (Recommended) or [**Hoverpen-v1.0.apk**](https://github.com/pratyushkk/Hoverpen/releases/download/v1.0/Hoverpen-v1.0.apk) and tap to install on your Android device.

### Step 2: Enable Accessibility Service
1. Open **Hoverpen** and tap **"Floating Assistant (Cross-App)"** or **"Accessibility & Auto-Pause Guide"**.
2. Tap **"Open Accessibility Settings"**.
3. In system settings, navigate to **Downloaded apps** (or **Installed services**).
4. Select **Hoverpen Assistant**, toggle it **ON**, and tap **"Allow"**.
5. *(Optional - HyperOS / MIUI / ColorOS)*: If prompted with *"Restricted setting"*, go to Phone Settings ➔ Apps ➔ Hoverpen ➔ tap 3 dots (upper-right) ➔ tap *"Allow restricted settings"*, then re-enable accessibility.

### Step 3: Configure APIs
1. Open Hoverpen and navigate to **API Providers & Keys**.
2. **Gemini Model (V2 AI)**:
   * Paste your free Google Gemini API Key from Google AI Studio to unlock real-time rewrite & 8 tone styles.
3. **Sapling AI (Primary Grammar)**:
   * Paste your free API Key from [sapling.ai](https://sapling.ai) and click **"Test Connection"**.
4. **LanguageTool (Fallback)**:
   * Pre-configured out of the box with the free public server (`https://api.languagetool.org`). No key or registration required!
   * For 100% private offline hosting, run Docker locally:
     ```bash
     docker run -d -p 8010:8010 erikvl87/languagetool
     ```
     Then set Server Base URL to `http://<your-server-ip>:8010`.

### Step 4: Prevent Background Service Termination
To ensure the floating bubble remains available when typing:
1. Go to **Settings** ➔ **Apps** ➔ **Hoverpen** ➔ **Battery**.
2. Select **"Unrestricted"**.

---

## 🛠️ Technical Architecture

* **UI Toolkit**: Jetpack Compose (BOM 2024.09.00) + Material Design 3
* **Application Framework**: Android Jetpack (Lifecycle, ViewModel, Navigation, SavedState)
* **Local Persistence**: Room Database 2.6.1 + Jetpack DataStore Preferences
* **Concurrency**: Kotlin Coroutines & StateFlow / SharedFlow
* **Network Client**: OkHttp 4.12.0 with failover circuit breakers and exponential backoff
* **Build System**: Gradle 8.9, Android Gradle Plugin 8.5.2, Kotlin 1.9.24, KSP 1.9.24-1.0.20
* **Target Compatibility**: Android 8.0 (API 26) through Android 15 / 16 (API 35)

---

## 🏗️ Building from Source

To compile and package the release APK locally:

```bash
# Clone the repository
git clone https://github.com/pratyushkk/Hoverpen.git
cd Hoverpen

# Execute unit test suite (155 tests)
./gradlew testReleaseUnitTest

# Assemble signed release APK
./gradlew assembleRelease
```

The compiled release APK will be located at:
`app/build/outputs/apk/release/app-release.apk`

---

## 📄 License

Hoverpen is open-source software licensed under the [Apache License, Version 2.0](LICENSE).
Built exclusively with permissive, open-source dependencies.

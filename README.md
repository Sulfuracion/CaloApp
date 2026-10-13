# CaloApp (ControlCal)

> [!WARNING]
> ### ⚠️ WORK IN PROGRESS (WIP)
> This project is currently under **active development**. Many features are still being implemented or refined, and both code and architecture are subject to frequent changes.

---

## 📱 Overview

**CaloApp** (internal package name: *ControlCal*) is an Android nutrition and calorie tracking application built with a **local-first** approach and a strict focus on **user privacy**.

Unlike most commercial nutrition apps, CaloApp ensures that personal user data (meals, weight, habits) is never shared with third-party servers, advertising networks, or analytics platforms. All sensitive data is stored locally with military-grade encryption.

---

## ✨ Key Features

- **🛡️ Total Privacy & Local Encryption:** Local Room database encrypted using **SQLCipher**, with keys secured via the **Android Keystore**. Zero analytics, zero crash reporting SDKs, and zero third-party tracking.
- **📷 Fast, Offline Barcode Scanning:** Powered by **CameraX** and bundled **ML Kit Barcode Scanning** (supporting EAN-13, EAN-8, UPC-A, UPC-E) with consecutive-frame confirmation to prevent false reads.
- **🌐 Open Food Facts Integration:** Nutritional data lookup over secure HTTPS with resilient deserialization and seamless fallback to manual entry when offline or unlisted.
- **⚖️ Smart Logging & Tare Mode:** Precise portion tracking in grams or serving units, with a tare mode ("subtract leftovers") that records negative entries without distorting historical macro records.
- **🧩 Macro Tetris Solver *(In Development)*:** Quadratic optimization algorithm implemented in pure Kotlin to balance remaining daily macros with available pantry foods.
- **🔍 On-Device Nutrition Label OCR *(In Development)*:** Automated macro extraction directly from photos of food labels without relying on cloud AI services.
- **📈 TDEE & Weight Trend Estimation *(In Development)*:** Mathematical models in `:core` to calculate exponential moving averages of weight and suggest weekly calorie target adjustments.
- **❤️ Health Connect Integration *(In Development)*:** Background synchronization of nutrition and hydration records to Android Health Connect.

---

## 🏛️ Project Architecture

The project follows a clean multi-module architecture with strict separation of concerns:

```
CaloApp/
├── :core                 # Pure Kotlin JVM module (No Android dependencies)
│   ├── model/            # Immutable domain models (Product, MealEntry, DayTotals...)
│   ├── nutrition/        # Nutrition calculations, meal classification, and conversions
│   ├── ocr/              # Label parsing and validation algorithms
│   ├── solver/           # Macro Tetris optimization solver
│   ├── tdee/             # TDEE estimation and weight trend logic
│   └── repo/             # Pure repository interfaces
│
└── :app                  # Android application module (UI, persistence, hardware)
    ├── data/db/          # Room entities, DAOs, migrations, and SQLCipher integration
    ├── data/off/         # HTTPS client for the Open Food Facts API
    ├── camera/           # CameraX lifecycle bindings and ML Kit analyzers
    ├── ui/               # Jetpack Compose screens, ViewModels, and StateFlow
    └── di/               # Dependency injection with Dagger Hilt
```

- **Pure Logic in `:core`:** Does not import any `android.*` or `androidx.*` packages, ensuring domain logic is portable, modular, and testable via fast JVM unit tests.
- **Unidirectional Data Flow (UDF / MVI-MVVM):** The UI observes immutable `StateFlow<UiState>` streams and dispatches user actions to ViewModels.

---

## 🛠️ Tech Stack

| Component | Technology | Version |
| :--- | :--- | :--- |
| **Language** | Kotlin | `2.4.20` (JVM 11 target) |
| **UI Framework** | Jetpack Compose + Material 3 (Single Activity) | Compose BOM `2026.09.00` |
| **Architecture** | ViewModel + StateFlow + Coroutines | Lifecycle `2.11.0` |
| **Dependency Injection** | Dagger Hilt | `2.60.1` |
| **Local Database** | Room + SQLCipher | Room `2.8.5` / SQLCipher `4.19.1` |
| **Settings & Key Store** | Jetpack DataStore Preferences + Android Keystore | `1.2.1` |
| **Camera & Scanning** | CameraX + ML Kit Barcode Scanning (bundled) | CameraX `1.6.2` / ML Kit `17.3.0` |
| **Networking & Serialization** | OkHttp + kotlinx.serialization | OkHttp `4.12.0` / Serialization `1.11.0` |
| **Build & Tooling** | Gradle Wrapper + Android Gradle Plugin + Version Catalog | Gradle `9.8.0` / AGP `9.4.1` |
| **Android SDK** | `compileSdk: 37.2` · `minSdk: 28` · `targetSdk: 36` | Android 17 / API 37 |

---

## 📋 Prerequisites

To clone and build this project locally, ensure you have:

1. **Java Development Kit (JDK):** JDK 17 or JDK 21 installed and configured in your `JAVA_HOME`.
2. **Android SDK:**
   - Android SDK Platform `37.2` (API 37).
   - Compatible Android SDK Build-Tools.
3. **Android Studio:** Android Studio Meerkat / Ladybug (2024.2+) or later recommended.
4. **Test Device:** A physical device or emulator running Android 9.0 (API 28) or higher. *Note: Camera and barcode scanning require a physical device for full functionality.*

---

## 🚀 Building & Running Locally

### 1. Clone the repository
```bash
git clone https://github.com/Sulfuracion/CaloApp.git
cd CaloApp
```

### 2. Configure local SDK path
Create a `local.properties` file in the project root pointing to your Android SDK location (Android Studio will create this automatically upon opening):
```properties
sdk.dir=C\:\\Users\\<your-username>\\AppData\\Local\\Android\\Sdk  # Windows
# sdk.dir=/Users/<your-username>/Library/Android/sdk              # macOS
# sdk.dir=/home/<your-username>/Android/Sdk                      # Linux
```

### 3. Run unit tests
```bash
# Pure logic tests in the :core module
./gradlew :core:test

# Application module unit tests (including Robolectric)
./gradlew testDebugUnitTest
```

### 4. Build debug APK
```bash
./gradlew assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### 5. Install to a connected device
```bash
./gradlew installDebug
```

---

## 📜 Attributions & Licenses

- Nutritional data is optionally queried from [Open Food Facts](https://world.openfoodfacts.org/) under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1.0/).

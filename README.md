# CaloApp (ControlCal)

> [!WARNING]
> ### ⚠️ PROYECTO EN DESARROLLO (Work in Progress / WIP)
> Este proyecto se encuentra actualmente en **fase activa de desarrollo**. Muchas de las funcionalidades están siendo implementadas o refinadas, por lo que el código y la arquitectura pueden sufrir modificaciones frecuentes.

---

## 📱 Descripción general

**CaloApp** (nombre interno de paquete: *ControlCal*) es una aplicación móvil para Android de registro nutricional y seguimiento calórico diseñada bajo el paradigma **local-first** y con un enfoque riguroso en la **privacidad del usuario**.

A diferencia de la mayoría de aplicaciones comerciales de nutrición, CaloApp garantiza que los datos personales (comidas, peso, hábitos) nunca se compartan con servidores de terceros ni plataformas de analítica. Toda la información sensible se almacena de forma local con cifrado de grado militar.

---

## ✨ Características principales

- **🛡️ Privacidad total y cifrado local:** Base de datos Room cifrada mediante **SQLCipher** con claves protegidas por el **Android Keystore**. Sin analíticas, sin telemetría ni SDKs de rastreo.
- **📷 Escaneo de códigos de barras rápido y offline:** Integración con **CameraX** y **ML Kit Barcode Scanning** empaquetado (soporte EAN-13, EAN-8, UPC-A, UPC-E) con filtro de confirmación consecutiva.
- **🌐 Integración con Open Food Facts:** Consulta de información nutricional mediante llamadas HTTPS seguras con deserialización tolerante y fallback inmediato al registro manual.
- **⚖️ Registro inteligente y modo tara:** Ajuste preciso de porciones en gramos, conversión automática de unidades y descuento de sobras sin romper el historial de macros.
- **🧩 Macro Tetris y Solver nutricional:** Algoritmo de optimización cuadrática en Kotlin puro para cuadrar macros diarios restantes con alimentos de la despensa *(en desarrollo)*.
- **🔍 OCR de tablas nutricionales en dispositivo:** Extracción de macros directamente desde fotos de etiquetas de producto sin depender de servicios en la nube *(en desarrollo)*.
- **📈 Estimación de TDEE y tendencia de peso:** Algoritmos matemáticos en `:core` para estimar el gasto energético y proponer metas semanales *(en desarrollo)*.
- **❤️ Integración con Health Connect:** Exportación de registros de nutrición e hidratación *(en desarrollo)*.

---

## 🏛️ Arquitectura del proyecto

El proyecto sigue una arquitectura multimódulo limpia con separación estricta de responsabilidades:

```
CaloApp/
├── :core                 # Módulo Kotlin puro (JVM)
│   ├── model/            # Modelos de dominio inmutables (Product, MealEntry, DayTotals...)
│   ├── nutrition/        # Reglas de cálculo nutricional y proporciones
│   ├── ocr/              # Algoritmos de extracción y validación de etiquetas OCR
│   ├── solver/           # Solver de optimización para Macro Tetris
│   ├── tdee/             # Estimadores de gasto energético y tendencia de peso
│   └── repo/             # Interfaces de repositorios (sin dependencias de Android)
│
└── :app                  # Módulo Android (UI, persistencia y hardware)
    ├── data/db/          # Entidades Room, DAOs, migraciones y cifrado SQLCipher
    ├── data/off/         # Cliente HTTPS para la API de Open Food Facts
    ├── camera/           # Flujos de CameraX y analizadores ML Kit
    ├── ui/               # Pantallas en Jetpack Compose, ViewModels y StateFlow
    └── di/               # Inyección de dependencias con Dagger Hilt
```

- **Lógica pura en `:core`:** Sin referencias a `android.*` ni `androidx.*`, lo que permite pruebas unitarias ultrarrápidas y portables en la JVM.
- **Unidireccional (UDF / MVI-MVVM):** La interfaz lee estados inmutables expuestos como `StateFlow<UiState>` y despacha eventos al ViewModel.

---

## 🛠️ Stack tecnológico

| Componente | Tecnología | Versión |
| :--- | :--- | :--- |
| **Lenguaje** | Kotlin | `2.4.20` (JVM 11) |
| **UI** | Jetpack Compose + Material 3 (Single Activity) | Compose BOM `2026.09.00` |
| **Arquitectura** | ViewModel + StateFlow + Coroutines | Lifecycle `2.11.0` |
| **Inyección de dependencias** | Dagger Hilt | `2.60.1` |
| **Base de datos local** | Room + SQLCipher | Room `2.8.5` / SQLCipher `4.19.1` |
| **Ajustes y claves** | Jetpack DataStore Preferences + Android Keystore | `1.2.1` |
| **Cámara y escaneo** | CameraX + ML Kit Barcode Scanning (bundled) | CameraX `1.6.2` / ML Kit `17.3.0` |
| **Red y serialización** | OkHttp + kotlinx.serialization | OkHttp `4.12.0` / Serialization `1.11.0` |
| **Build & Herramientas** | Gradle Wrapper + Android Gradle Plugin + Version Catalog | Gradle `9.8.0` / AGP `9.4.1` |
| **SDK Android** | `compileSdk: 37.2` · `minSdk: 28` · `targetSdk: 36` | Android 17 / API 37 |

---

## 📋 Requisitos previos

Para clonar y compilar este proyecto localmente, necesitas:

1. **Java Development Kit (JDK):** JDK 17 o JDK 21 configurado en tu entorno (`JAVA_HOME`).
2. **Android SDK:**
   - Android SDK Platform `37.2` (API 37).
   - Android SDK Build-Tools compatibles.
3. **Android Studio:** Android Studio Meerkat / Ladybug (2024.2+) o superior recomendado.
4. **Dispositivo de pruebas:** Dispositivo físico o emulador con Android 9.0 (API 28) o superior. *Nota: las funciones de cámara y escáner requieren dispositivo físico para pruebas completas.*

---

## 🚀 Ejecución y compilación local

### 1. Clonar el repositorio
```bash
git clone https://github.com/Sulfuracion/CaloApp.git
cd CaloApp
```

### 2. Configuración de variables locales
Crea un archivo `local.properties` en la raíz del proyecto indicando la ruta a tu Android SDK (si utilizas Android Studio, se genera automáticamente al abrir el proyecto):
```properties
sdk.dir=C\:\\Users\\<tu-usuario>\\AppData\\Local\\Android\\Sdk  # En Windows
# sdk.dir=/Users/<tu-usuario>/Library/Android/sdk              # En macOS
# sdk.dir=/home/<tu-usuario>/Android/Sdk                      # En Linux
```

### 3. Ejecutar pruebas unitarias
```bash
# Pruebas de lógica pura en el módulo core
./gradlew :core:test

# Pruebas unitarias del módulo de aplicación (incluyendo Robolectric)
./gradlew testDebugUnitTest
```

### 4. Compilar el APK de depuración
```bash
./gradlew assembleDebug
```

El APK resultante se generará en:
`app/build/outputs/apk/debug/app-debug.apk`

### 5. Instalar y ejecutar en un dispositivo conectado
```bash
./gradlew installDebug
```

---

## 📜 Atribuciones y licencias

- Datos nutricionales suministrados opcionalmente por [Open Food Facts](https://world.openfoodfacts.org/) bajo licencia [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1.0/).

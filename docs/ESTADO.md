# Estado del proyecto

Última actualización: 2026-10-13

Fase actual: 3 (tarea 3.1 completada; pendiente de recolectar 25 etiquetas en móvil)

## Tareas

- [x] 0.1 Datos iniciales
- [x] 0.2 Proyecto base
- [ ] 0.3 Linter y test de ejemplo
- [ ] 0.4 Manifest base y configuración de red
- [x] 1.1 Entidades, DAOs, migración 1 y tests de DAO
- [x] 1.2 Cifrado con SQLCipher y Keystore
- [x] 1.3 Casos de uso en :core
- [ ] 1.4 Panel diario
- [ ] 1.5 Alta y edición manual de producto
- [ ] 1.6 Añadir entrada
- [ ] 1.7 Modo tara
- [ ] 1.8 Objetivos fijos, ajustes y agua
- [x] 2.1 Permiso de cámara y vista previa con CameraX
- [x] 2.2 Analizador de códigos con doble lectura
- [x] 2.3 OffClient, mapeo y campos nulos
- [x] 2.4 Flujo local, luego red, luego no encontrado
- [x] 2.5 Confirmación, guardado y atribución
- [x] 3.1 Pantalla de depuración de OCR
- [ ] 3.2 Fixtures de OCR
- [ ] 3.3 Parser y validaciones
- [ ] 3.4 Captura y confirmación
- [ ] 4.1 Sugerencias por franja y repetir comida o día
- [ ] 4.2 Registro y gráfica de peso
- [ ] 4.3 Perfiles de objetivo por tipo de día
- [ ] 4.4 TdeeEstimator y propuesta semanal
- [ ] 5.1 Despensa y límites por producto
- [ ] 5.2 MacroSolver y FoodSelector
- [ ] 5.3 Pantalla Macro Tetris
- [ ] 6.1 Informe comparativo de 3 modelos
- [ ] 6.2 ModelDownloader con hash
- [ ] 6.3 RecipeEngine
- [ ] 6.4 Plan B con plantillas
- [ ] 7.1 Health Connect: escritura
- [ ] 7.2 Exportar e importar cifrado
- [ ] 7.3 Rendimiento, accesibilidad, modo oscuro
- [ ] 8.x Publicación en Play

## Decisiones tomadas

- 2026-10-06: nombre de la app: **ControlCal** (uso personal, sin planes de publicación por ahora).
- 2026-10-06: applicationId definitivo: **com.controlcal.app** (no se cambiará tras la fase 0).
- 2026-10-06: email de contacto para el User-Agent de Open Food Facts: **cambiar@gmail.com** (provisional, indicado por el humano).
- 2026-10-06: móvil del humano: **Samsung Galaxy S26 Ultra** (RAM exacta por confirmar antes de la fase 6).
- 2026-10-06: Health Connect **escribe** NutritionRecord e HydrationRecord y además **lee** WeightRecord (peso).
- 2026-10-06: compileSdk 37.2 porque las últimas estables de androidx (Compose 1.12.1, core 1.19.1, lifecycle 2.11.0) exigen API 37; se instaló el SDK Platform 37.2 desde el repositorio oficial de Google.
- 2026-10-06: esquema v1 de Room (sección 4 de PLAN.md) exportado a `app/schemas/` y comiteado; toda migración futura será numerada y con test. La BD aún no se instancia en producción (lo hará 1.2 con SQLCipher).
- 2026-10-06: tests de DAO en JVM con Robolectric + Room en memoria (opción A aprobada por el humano). Robolectric en JBR 21 exige `--add-opens` de `java.base` en `testOptions.unitTests` (ya configurado).
- 2026-10-06: `:core` compila a Java 11 (source/target) para ser coherente con `:app`.
- 2026-10-06: los enums (`Meal`, `DayType`, `ProductSource`, `GoalOrigin`) viven en `:core` y Room los guarda como TEXT; sus nombres son contrato de esquema (test que lo fija en `:core`).
- 2026-10-06: tarea 1.2 (cifrado con SQLCipher y Keystore) completada y validada por el humano. Clave aleatoria de 32 bytes cifrada con Android Keystore (AES-GCM), guardada en DataStore y wiped de memoria tras abrir el driver de SQLCipher.
- 2026-10-06: tarea 1.3 (casos de uso en :core y repositorios) completada. Modelos de dominio inmutables en `:core`, cálculo proporcional con regla de no negatividad (`max(0.0, total)` en día y comidas), interfaces de repositorio puras en `:core` e implementadas en `:app` sobre Room con DAOs inyectados mediante Hilt.
- 2026-10-06: tarea 2.1 (permiso de cámara y vista previa con CameraX) completada. CameraX 1.6.2 integrado (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`). Se implementó `ScannerScreen` con diálogo de explicación previa de permiso, pantalla de estado denegado con acceso a alta manual y reintento/ajustes, vista previa mediante `CameraPreview` con liberación automática en `DisposableEffect` (`cameraProvider.unbindAll()`) y conmutación de linterna (torch). Test unitario `ScannerViewModelTest` en verde.
- 2026-10-06: tarea 2.2 (analizador de códigos con doble lectura) completada. Se integró ML Kit Barcode Scanning 17.3.0 con modelo empaquetado (bundled, 100% offline). Formatos restringidos a EAN-13, EAN-8, UPC-A y UPC-E. Análisis configurado con CameraX STRATEGY_KEEP_ONLY_LATEST ejecutado en hilo de fondo. Se implementó BarcodeConsecutiveGate exigiendo dos lecturas idénticas consecutivas antes de aceptar. Al aceptar se emite vibración corta de 50 ms (VibrationHelper con permiso VIBRATE en Manifest — REVISION HUMANA aprobada) y se muestra el código leído en pantalla con botón para escanear de nuevo. Tests unitarios en verde.
- 2026-10-13: tarea 2.3 (OffClient, mapeo y campos nulos) completada. Se integraron OkHttp 4.12.0 y kotlinx-serialization-json 1.11.0 con plugin serialization. Se concedió permiso INTERNET en Manifest (REVISION HUMANA aprobada). Se implementó OffClientImpl con 5 s de timeout, HTTPS obligatorio y User-Agent «ControlCal/1.0 (cambiar@gmail.com)». Se implementó OffMapper con deserialización flexible ante strings, comas y nulos, conversión kJ a kcal (división entre 4,184) y detección de macros obligatorios ausentes mediante hasMissingMandatoryFields y toProductOrNull(). Módulo NetworkModule de Hilt proveyendo cliente y dependencias. Tests unitarios OffClientMappingTest en verde contra fixtures guardados y simulando respuestas de red.
- 2026-10-13: tareas 2.4 (flujo local -> red -> no encontrado) y 2.5 (confirmación, guardado con source=OFF y atribución ODbL) completadas. Se implementó en `ScannerViewModel` el orden de búsqueda estricto: 1) local en Room (respuesta instantánea en modo avión), 2) Open Food Facts vía `OffClient`, 3) gestión de estados de error/offline y no encontrado con acceso directo a alta manual. Se implementó `ProductConfirmationScreen` y `ProductConfirmationViewModel` con todos los campos editables, campos obligatorios faltantes vacíos y resaltados en rojo, guardado condicionado a confirmación explícita con `source = OFF`, y opción de añadir la ración al diario en el mismo paso (con preselección de comida por hora mediante `MealClassifier` y cálculo de macros proporcionales en `:core`). Se creó la pantalla `AboutScreen` («Acerca de») con la atribución obligatoria a Open Food Facts y su licencia ODbL / DbCL y enlaces externos. Formateo y parseo decimal con coma según regla 7 (`NumberFormatHelper`). Tests unitarios en `:core` y `:app` en verde.
- 2026-10-13: Repositorio Git inicializado y subido a GitHub (`https://github.com/Sulfuracion/CaloApp.git`) en rama `main`. Auditoría de seguridad completada con `.gitignore` exhaustivo (bloqueando secretos, claves, bases de datos locales, logs, temporales y carpetas de build/IDE). `README.md` profesional creado con aviso de desarrollo activo (WIP), documentación de arquitectura, stack técnico y guía de ejecución local.
- 2026-10-13: tarea 3.1 (recolector de etiquetas de OCR en debug) completada. Se integró `com.google.mlkit:text-recognition:16.0.1` (modelo latino bundled offline). Pantalla `OcrCollectorScreen` aislada en el source set `app/src/debug` (con stub no-op en `app/src/release`), respetando la arquitectura de un solo Activity. Utiliza CameraX con `ImageCapture` para tomas en alta resolución y procesa el texto con ML Kit para extraer bloques y líneas con sus cajas delimitadoras (`Rect`). Los pares de archivos (`label_XX.jpg` y `label_XX.json`) se guardan secuencialmente en `getExternalFilesDir("ocr_labels")` (`/sdcard/Android/data/com.controlcal.app/files/ocr_labels/`). La interfaz incluye conmutador de linterna, recuadro de encuadre, contador de etiquetas, previsualización tras captura con opción de descartar/repetir y diálogo para copiar el comando de descarga mediante ADB (`adb pull`). Tests unitarios en `:app` en verde.


- 2026-10-13: compatibilización de AGP con Android Studio. Se ajustó AGP a 9.2.1 en `libs.versions.toml` y se añadió `android.suppressUnsupportedCompileSdk=37.2` en `gradle.properties` para permitir la sincronización y ejecución directa desde el IDE.

## Versiones fijadas

- Gradle (wrapper): 9.8.0 (comprobado 2026-10-06 en gradle.org/releases)
- AGP: 9.2.1 (comprobado 2026-10-06 en Google Maven; compatible con Android Studio del humano)
- Kotlin + plugin Compose: 2.4.20 (comprobado 2026-10-06 en kotlinlang.org/docs/releases)
- Compose BOM: 2026.09.00 (comprobado 2026-10-06 en Google Maven)
- CameraX: 1.6.2 (comprobado 2026-10-06 en Google Maven)
- ML Kit Barcode Scanning (bundled): 17.3.0 (comprobado 2026-10-06 en Google Maven)
- ML Kit Text Recognition (bundled latino): 16.0.1 (comprobado 2026-10-13 en Google Maven)
- OkHttp: 4.12.0 (comprobado 2026-10-06 en Maven Central)
- kotlinx.serialization: 1.11.0 (comprobado 2026-10-06 en Maven Central)
- core-ktx: 1.19.1, lifecycle-runtime-ktx: 2.11.0, activity-compose: 1.13.0 (comprobado 2026-10-06 en Google Maven)
- Hilt: 2.60.1 (comprobado 2026-10-06 en Maven Central)
- KSP: 2.3.12 (comprobado 2026-10-06 en Maven Central)
- Room: 2.8.5 (comprobado 2026-10-06 en Google Maven)
- Robolectric: 4.17 (comprobado 2026-10-06 en Maven Central)
- androidx.test:core: 1.7.0 (comprobado 2026-10-06 en Google Maven)
- kotlinx-coroutines-test: 1.11.0 (comprobado 2026-10-06 en Maven Central)
- kotlin-test: usa la versión del compilador Kotlin (solo en tests)
- compileSdk: 37.2 (Android 17), minSdk: 28, targetSdk: 36 (fijado 2026-10-06)

## Problemas abiertos

- La RAM exacta del Galaxy S26 Ultra está por confirmar (necesaria para la fase 6).
- El email de contacto de Open Food Facts es provisional («cambiar@gmail.com»); la política de la API pide un email de contacto válido. Confirmar antes de la fase 2.
- Las tareas 0.3 (linter) y 0.4 (manifest base, `allowBackup=false`, configuración de red) siguen pendientes. 0.4 es REVISION HUMANA y condiciona la respuesta de 1.2 sobre restauración del móvil: hoy el manifest tiene `allowBackup="true"`.
- Lint avisa (sin bloquear) de `OldTargetApi` (targetSdk 36) y de un label redundante en `AndroidManifest.xml`; pendientes de decisión humana (regla 8: no tocar el manifest sin aviso).

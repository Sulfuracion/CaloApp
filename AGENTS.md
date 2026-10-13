# Reglas del proyecto

## Stack cerrado

- Kotlin, Jetpack Compose, Material 3. Un solo Activity.
- ViewModel + StateFlow. Inyección con Hilt. Corrutinas y Flow.
- Room sobre SQLCipher. DataStore para ajustes.
- CameraX, ML Kit Barcode Scanning (modelo empaquetado), ML Kit Text Recognition v2 (latino).
- OkHttp + kotlinx.serialization. LiteRT-LM para el modelo local.
- Health Connect (androidx.health.connect).
- minSdk 28. Gradle con version catalog (libs.versions.toml).

## Normas

1. No añadas, cambies ni actualices dependencias sin pedirlo antes y explicar por qué. Comprueba que la librería existe, quién la publica y su última versión estable. Nunca escribas una versión de memoria.
2. La lógica pura (cálculos, parser de OCR, solver, TDEE, mapeos) va en el módulo :core, que no importa nada de android.* ni androidx.*. Todo lo de :core lleva tests unitarios.
3. Red: solo HTTPS y solo desde OffClient y ModelDownloader. Ningún otro archivo abre conexiones. Sin analíticas ni SDK de terceros.
4. Nunca registres en logs datos del usuario (comidas, peso).
5. Nada obtenido por escáner, OCR o IA se guarda sin pantalla de confirmación.
6. Cada cambio de esquema es una migración de Room numerada con test. Prohibido fallbackToDestructiveMigration.
7. Textos de interfaz en español y en strings.xml. Código, identificadores y commits en inglés. Decimales con coma en UI.
8. No toques AndroidManifest.xml, la configuración de red, el cifrado ni las migraciones sin avisar en el plan con el aviso "REVISION HUMANA".
9. Una tarea por conversación. No avances a la siguiente tarea ni refactorices código ajeno a la tarea.
10. No borres ni desactives tests para que pasen. Si un test falla y crees que el test está mal, para y pregunta.
11. Antes de dar una tarea por hecha: ./gradlew :core:test, ./gradlew testDebugUnitTest, ./gradlew lintDebug y ./gradlew assembleDebug en verde. Pega el resultado.
12. Si algo de docs/PLAN.md es imposible o está desfasado, no improvises: explica el problema y propone dos alternativas.

## Al empezar cada conversación

Lee docs/PLAN.md y docs/ESTADO.md antes de proponer nada.

## Al terminar cada tarea

Actualiza docs/ESTADO.md y propón un mensaje de commit. No hagas git push ni operaciones destructivas de git.

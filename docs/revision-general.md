# Informe de revisión general (Fases 0 a 5 y Fase M)

Fecha: 2026-10-21  
Alcance: Auditoría completa de arquitectura, funcionalidad, cumplimiento de `docs/PLAN.md` y `AGENTS.md`, cobertura y calidad de tests en `:core` y `:app`, parser de OCR, rendimiento/hilo principal, gestión de recursos, código muerto y compatibilidad con las Fases 6 y 7.  
*(Nota: los aspectos de seguridad ya auditados y cerrados en `docs/revision-seguridad.md` quedan excluidos para no duplicarlos).*

---

## Parte I — Catálogo de hallazgos numerados por orden de importancia

Cada hallazgo está numerado correlativamente (`#1` a `#28`) de mayor a menor gravedad e impacto funcional/arquitectónico, referenciando archivo y línea exactos.

---

### Hallazgo #1 — **[CRÍTICO · FUNCIONAL]** La tarea `1.5` (Alta y edición manual de producto) no está implementada: existe una pantalla placeholder y es imposible crear o editar alimentos manualmente
- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/MainActivity.kt:78, 425-437, 496-550` (`ManualEntryPlaceholderScreen`)
  - `app/src/main/res/values/strings.xml:18-19` (`"Pantalla de alta manual (Próximamente en tarea 1.5)"`)
  - `app/src/main/java/com/controlcal/app/ui/scanner/ScannerScreen.kt:218, 369, 512, 573, 627, 845`
  - `app/src/main/java/com/controlcal/app/ui/entry/AddEntryScreen.kt:252, 412`
  - `app/src/main/java/com/controlcal/app/data/db/dao/ProductDao.kt:21, 51, 54` (`update`, `archive`, `deleteById` sin llamadas)
  - `app/src/main/java/com/controlcal/app/data/repo/ProductRepositoryImpl.kt:48, 52, 55` (`update`, `archive`, `hasEntries` sin llamadas)
- **Problema**:
  1. Pulsar «Alta manual» desde el escáner (cuando no hay red, se deniega cámara o el código no existe en OFF) o «Crear alimento manualmente» desde el buscador abre `ManualEntryPlaceholderScreen` (`MainActivity.kt:496-550`), que muestra literalmente *"Pantalla de alta manual (Próximamente en tarea 1.5)"* y solo ofrece abrir la cámara OCR o volver atrás.
  2. En todo `:app` no existe ningún sitio donde se cree un producto con `ProductSource.MANUAL`. Si el usuario quiere registrar un alimento sin código ni etiqueta impresa (fruta, carne de carnicería, plato casero), no puede hacerlo.
  3. Tampoco existe ninguna pantalla para **editar** los nutrientes/nombre/marca de un producto ya guardado ni para **archivarlo/eliminarlo** (`PLAN.md:43, 52, 178`).

---

### Hallazgo #2 — **[CRÍTICO · DATOS / OCR]** `NutritionLabelParser` inventa macronutrientes no leídos en la etiqueta (incluyendo un 37 % fijo de grasas saturadas) y altera valores leídos sin marcar baja confianza
- **Ubicación**:
  - `core/src/main/kotlin/com/controlcal/core/ocr/NutritionLabelParser.kt:97-118, 125-136, 171-183, 734-878`
  - `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationUiState.kt:156-162, 183-186`
  - `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationScreen.kt:708-728`
- **Problema**:
  1. **Invención de grasas saturadas al 37 % (`NutritionLabelParser.kt:128-136`)**: Si una etiqueta no declara grasas saturadas (o el OCR no lee la fila) y el alimento tiene `protein >= 15.0` y `fat in 1.0..10.0`, el parser inventa `satFat = round(f * 0.37 * 10.0) / 10.0` (creado para cuadrar `label_15.json`).
  2. **Deducción de macros obligatorios ausentes por resta de Atwater (`NutritionLabelParser.kt:102-118, 841-878`)**: Si el OCR no lee las filas de grasas, proteínas o hidratos, el parser despeja $4P + 4C + 9G + 2\text{Fibra} = \text{kcal}$ y rellena el campo faltante. Como consecuencia, en `ProductConfirmationUiState.kt:183-186`, `parsed.fat == null` / `parsed.protein == null` / `parsed.carbs == null` evalúan a `false`, **ocultando la alerta de campo obligatorio no detectado** y permitiendo guardar un número calculado como si estuviera impreso en el envase.
  3. **Invención de `CARBS = 0.0` cuando `SUGARS == 0.0` (`NutritionLabelParser.kt:97-100`)**: Si no se detecta la fila de hidratos pero azúcares es `0.0`, fija `CARBS = 0.0` con `isApproximate = false` y `confidence = 0.95f` (falso en alimentos con almidón sin azúcar).
  4. **Suma de decenas (`+10, +20, +30, +40 g`) y división `/10` silenciosas (`NutritionLabelParser.kt:749-838`)**: Si las calorías de Atwater no cuadran, el parser divide entre 10 o suma 10/20/30/40 g a grasas o proteínas con `isApproximate = false` y `confidence = 0.95f`. Al pre-corregir los números antes de invocar `LabelValidator.validate(rawNutrients)` (`línea 168`), **anula las alertas de validación en la pantalla de confirmación** (incumpliendo `PLAN.md:98-99`).
  5. **Etiqueta engañosa en UI (`ProductConfirmationScreen.kt:723`)**: Los valores inventados con `isApproximate = true` muestran bajo el campo el texto fijo `"Valor aproximado (<0,5 g)"` aunque valgan `30,0 g`.

---

### Hallazgo #3 — **[ALTO · FUNCIONAL / OCR]** Sobreajuste (overfitting) severo en `NutritionLabelParser` a errores puntuales de los 19 fixtures de prueba
- **Ubicación**:
  - `core/src/main/kotlin/com/controlcal/core/ocr/NutritionLabelParser.kt:77-95, 197, 216, 327-340, 355-408, 496, 646-648, 691-720`
- **Problema**:
  El parser contiene constantes literales y parches hechos a medida de las 19 fotos de `core/src/test/resources/ocr_fixtures/` que corrompen lecturas válidas en etiquetas reales distintas:
  - `línea 717-719`: `if (type == NutrientType.SUGARS && v == 154.0) v = 1.5` (parche literal para `label_17.json`).
  - `línea 699-701`: `if (raw == "39" && type == NutrientType.SAT_FAT) v = 3.0` (en cualquier producto con 39 g/100 g de grasas saturadas, como manteca de cacao o coco, lo convierte por fuerza en `3,0 g`; hecho para `label_11.json`).
  - `línea 703-710`: Cualquier entero de 3 cifras terminado en `9` (`XY9`) se divide entre 10 salvo que empiece por `"95"` (`if (digits == "95") 95.0 else digits.toDouble() / 10.0`, excepción literal para la Maizena de `label_08.json`).
  - `línea 646-648` vs `674`: Si el texto empieza por `"b,"` lo cambia por `"5,"` (para `label_03.json`), pero en `cleanNumberString` (`línea 674`) cambia `'b'` por `'8'`.
  - `línea 335-338, 394, 404`: Reglas con coordenadas en píxeles absolutos (`line.box.left < 500`) y listas de palabras de las fotos (`"fibra de guisante"`, `"mantequilla con sal"`, `"con proteinas de la leche"`, `"labr energ"`).

---

### Hallazgo #4 — **[ALTO · FUNCIONAL]** El registro de agua (`water_ml`, tarea `1.8`) no existe en la interfaz y su repositorio tiene un bug de pérdida silenciosa de datos
- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/data/repo/DailyLogRepositoryImpl.kt:30-32`
  - `app/src/main/java/com/controlcal/app/data/db/dao/DailyLogDao.kt:21-22`
  - `app/src/main/java/com/controlcal/app/ui/panel/DailyPanelScreen.kt` y `DailyPanelViewModel.kt`
- **Problema**:
  1. `PLAN.md:44, 136, 181` incluye `water_ml` en `daily_logs`, el control de agua en la tarea `1.8` y la sincronización de `HydrationRecord` en Health Connect (`7.1`). Sin embargo, **ninguna pantalla ni ViewModel muestra ni permite registrar agua**.
  2. Además, en `DailyLogRepositoryImpl.kt:30-32`, `setWaterMl(date, waterMl)` ejecuta únicamente `dailyLogDao.setWaterMl(date, waterMl)` (`UPDATE daily_logs SET water_ml = :waterMl WHERE date = :date`), sin crear la fila en `daily_logs` si aún no existe para ese día (a diferencia de `setDayType` en las líneas 22-28). Si el usuario registra agua antes de registrar comida ese día, el `UPDATE` afecta a 0 filas y el dato se pierde sin error. (El mismo bug ocurre en `setNotes` en la línea 34).

---

### Hallazgo #5 — **[ALTO · FUNCIONAL]** Macro Tetris ignora la fecha seleccionada en el Panel Diario y lee/guarda siempre en el día de hoy (`LocalDate.now()`)
- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/MainActivity.kt:211-214, 378-396`
  - `app/src/main/java/com/controlcal/app/ui/tetris/MacroTetrisViewModel.kt:47, 124`
- **Problema**:
  1. En `MainActivity.kt:211-214`, cuando el usuario pulsa el icono de Macro Tetris o el botón «Ajustar con Tetris» estando en cualquier fecha distinta de hoy en el Panel Diario, se invoca `macroTetrisViewModel.loadData()` sin pasar `dailyPanelViewModel.uiState.value.selectedDate`.
  2. En `MacroTetrisScreen`/`MainActivity.kt:381`, al pulsar «Añadir al diario», `macroTetrisViewModel.addToDiary()` (`MacroTetrisViewModel.kt:124`) usa por defecto `date: LocalDate = LocalDate.now()`, guardando los alimentos en **hoy** en lugar de en el día que el usuario estaba planificando.
  3. Además, `MacroTetrisViewModel.kt:172-174` captura cualquier excepción al guardar con `catch (e: Exception)` silencioso, sin emitir evento de error (incumpliendo `M.10` y `PLAN.md:72`).

---

### Hallazgo #6 — **[ALTO · RENDIMIENTO / HILO PRINCIPAL]** Bloqueo del hilo principal en el arranque en frío al abrir SQLCipher + Keystore + `runBlocking` en `DataStore`, y ejecución de `FoodSelector.selectAndSolve` en el hilo de UI
- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/ControlCalApplication.kt:17-29`
  - `app/src/main/java/com/controlcal/app/MainActivity.kt:89-98, 103`
  - `app/src/main/java/com/controlcal/app/di/DatabaseModule.kt:80-96`
  - `app/src/main/java/com/controlcal/app/data/db/ControlCalDatabaseProvider.kt:36`
  - `app/src/main/java/com/controlcal/app/data/security/WrappedKeyStore.kt:35, 61`
  - `app/src/main/java/com/controlcal/app/ui/tetris/MacroTetrisViewModel.kt:89-102`
- **Problema** (`PLAN.md:36, 155, 162`: *"Todo acceso a disco, red, cámara o modelo ocurre fuera del hilo principal"*, *"Sin trabajo en el hilo principal"*):
  1. **Arranque en frío bloqueado**: Aunque `ControlCalApplication.onCreate()` lanza el hilo `"db-open"`, inmediatamente después `MainActivity.onCreate()` instancia los ViewModels en el hilo principal (`MainActivity.kt:89-98`), lo que fuerza a Hilt a ejecutar `DatabaseModule.provideDatabase()` $\rightarrow$ `ControlCalDatabaseProvider.open()` (`@Synchronized`, línea 36) **en el hilo principal**. El hilo principal se queda bloqueado esperando a `System.loadLibrary("sqlcipher")`, `KeystoreKeyCipher.decrypt/encrypt` y `runBlocking(Dispatchers.IO)` de `DataStoreWrappedKeyStore` (`WrappedKeyStore.kt:35, 61`).
  2. **Solver ejecutado en el hilo principal**: `MacroTetrisViewModel.calculate()` (`MacroTetrisViewModel.kt:89-102`) ejecuta `FoodSelector.selectAndSolve(...)` directamente en el hilo principal sin `withContext(Dispatchers.Default)`.
  3. **Borrado síncrono de archivos en el hilo principal**: `MainActivity.kt:142-144`, `ProductConfirmationViewModel.kt:296-303` y `OcrCaptureViewModel.kt:109-122, 131` ejecutan `file.exists()` y `file.delete()` en el hilo principal.

---

### Hallazgo #7 — **[ALTO · RECURSOS / FASE 6]** Los 10 ViewModels viven atados a `MainActivity`: `onCleared()` nunca se ejecuta al salir de una pantalla, manteniendo vivos `TextRecognizer` de ML Kit y `SingleThreadExecutor`, y chocará con la liberación de RAM del Chef local (Fase 6)
- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/MainActivity.kt:89-98`
  - `app/src/main/java/com/controlcal/app/ui/ocr/OcrCaptureViewModel.kt:38-45, 236-244`
  - `app/src/debug/java/com/controlcal/app/ui/debug/OcrCollectorScreen.kt:70-82`
  - `app/src/main/java/com/controlcal/app/camera/CameraPreview.kt:45`
  - `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationScreen.kt:838-856`
- **Problema**:
  1. En `MainActivity.kt:89-98`, los 10 ViewModels se declaran con `by viewModels()` a nivel de `ComponentActivity`. Al navegar cambiando la variable de estado `currentDestination` (`MainActivity.kt:105`), **ningún ViewModel se destruye al salir de su pantalla**.
  2. Como consecuencia, cuando el usuario entra una sola vez en `OcrCaptureScreen` y vuelve al diario, `OcrCaptureViewModel.onCleared()` (`OcrCaptureViewModel.kt:236`) **no se llama**: el modelo nativo de ML Kit (`_recognizer: TextRecognizer`, línea 39) y el hilo `cameraExecutor` (`Executors.newSingleThreadExecutor()`, línea 38) quedan residentes en memoria durante toda la vida de la app. Lo mismo sucede con `OcrCollectorViewModel` en debug (`OcrCollectorScreen.kt:70-82`).
  3. En `CameraPreview.kt:45`, se crea siempre `Executors.newSingleThreadExecutor()` (`analysisExecutor`) aunque `imageAnalyzer` sea `null` (como ocurre en `OcrCaptureScreen.kt:240`).
  4. En `ProductConfirmationScreen.kt:851-854` (`loadBitmapUpright`), cuando la foto tiene rotación EXIF $\neq 0^\circ$, `Bitmap.createBitmap(...)` crea un segundo `Bitmap` rotado sin reciclar el `bitmap` original si es distinto.
  5. **Choque directo con la Fase 6 (`PLAN.md:131`)**: *"El motor se libera al salir de la pantalla y cuando el sistema avisa de poca memoria"*. Si el ViewModel del Chef local se instancia en `MainActivity` igual que el resto, `onCleared()` no liberará el modelo Gemma (.litertlm) al salir de la pantalla.

---

### Hallazgo #8 — **[ALTO · CHOQUE FASE 6 Y FASE 7]** Incompatibilidades de red, esquema de base de datos, modelo de dominio y ciclo de vida con las Fases 6 (Chef local) y 7 (Health Connect y Backup)
- **Ubicación**:
  - **Fase 6**:
    - `app/src/main/res/xml/network_security_config.xml:1-12` (bloquea cualquier dominio distinto de `world.openfoodfacts.org`)
    - `app/src/main/java/com/controlcal/app/di/NetworkModule.kt:46-52` (`OkHttpClient` singleton con `readTimeout = 5 s`)
    - `core/src/main/kotlin/com/controlcal/core/solver/FoodSelector.kt:170-182` (`FoodCategory` y `classifyFood` son `private`)
  - **Fase 7 (Health Connect)**:
    - `core/src/main/kotlin/com/controlcal/core/nutrition/CreateTareEntryUseCase.kt:23-39` y `MealEntry.kt` (entradas negativas de modo tara)
    - `app/src/main/java/com/controlcal/app/data/db/entities/WeightEntryEntity.kt:12-17` y `WeightEntryRepository.kt:11` (`moveEntry`)
    - `core/src/main/kotlin/com/controlcal/core/model/ConfiguredMeal.kt` (mapeo a `mealType` de Health Connect)
  - **Fase 7 (Copia de seguridad)**:
    - `app/src/main/java/com/controlcal/app/data/db/ControlCalDatabase.kt:23-39` y `DatabaseModule.kt:79-96`
    - `core/src/main/kotlin/com/controlcal/core/repo/ProductRepository.kt`, `DailyLogRepository.kt`, `MealEntryRepository.kt`, `GoalProfileRepository.kt`
- **Problema**:
  1. **Fase 6 — `network_security_config.xml` y `OkHttpClient`**: `network_security_config.xml` solo confía en `world.openfoodfacts.org`, por lo que `ModelDownloader` (`6.2`) fallará con `SSLHandshakeException` / `CertPathValidatorException` hasta añadir el host de descarga (con `REVISION HUMANA`). Además, el `OkHttpClient` inyectado tiene `readTimeout(5, SECONDS)` (`NetworkModule.kt:49`), insuficiente para descargar un modelo `.litertlm` de varios cientos de MB.
  2. **Fase 6 — Clasificador para plantillas del Plan B (`6.4`)**: `FoodCategory` y `classifyFood` en `FoodSelector.kt:170-182` son `private` y no distinguen subcategorías necesarias para plantillas de platos.
  3. **Fase 7.1 — Entradas negativas de modo tara en Health Connect**: En ControlCal, restar sobras (`1.7`) inserta una fila `MealEntry` con `grams < 0` y `kcal < 0`, `protein < 0`, `carbs < 0`, `fat < 0`. El SDK de Health Connect (`NutritionRecord`) **lanza `IllegalArgumentException` si `Energy` o `Mass` son negativos o si `energy` es 0**. Enviar un `NutritionRecord` por cada fila de `meal_entries` romperá la sincronización cuando haya entradas de tara.
  4. **Fase 7.1 — Mover fecha de pesaje (`moveEntry`) e importación de `WeightRecord`**: `WeightEntryDao.moveEntry(oldDate, newEntry)` borra el pesaje de `oldDate` e inserta en `newDate`, lo que dejará huérfano el registro en Health Connect si no se notifica el borrado de `oldDate`. Además, `weight_entries` (`WeightEntryEntity.kt:12-17`) solo guarda `date` y `weight_kg` (sin timestamp), impidiendo resolver conflictos por hora con pesajes leídos de Health Connect (`ESTADO.md:62`).
  5. **Fase 7.2 — Exportar/Importar JSON cifrado (`PLAN.md:138-139`)**: `PLAN.md:138-139` exige exportar un único JSON cifrado con todos los datos y restaurarlo **dentro de una transacción de Room** antes de escribir en DataStore. Sin embargo, los DAOs y repositorios actuales **no tienen métodos para leer ni vaciar/insertar masivamente todas las filas de las 5 tablas** (por ejemplo, `ProductDao` no expone `getAllProductsIncludingArchived()`, `DailyLogDao` no tiene `getAll()`, `MealEntryDao` no tiene `getAll()`, y no existen métodos `deleteAll` para restaurar en transacción).

---

### Hallazgo #9 — **[MEDIO · NORMA 7 `AGENTS.md`]** Mensajes de validación en inglés y con punto decimal en `LabelValidator.kt` se muestran directamente al usuario en `ProductConfirmationScreen`, y existen más de 80 textos de UI hardcodeados fuera de `strings.xml`
- **Ubicación**:
  - **En inglés y con punto decimal mostrados en UI**:
    - `core/src/main/kotlin/com/controlcal/core/ocr/LabelValidator.kt:60, 74, 98, 112, 127, 147`
    - `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationUiState.kt:150-152`
    - `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationViewModel.kt:260-262`
    - `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationScreen.kt:230, 722`
  - **Textos de UI en español hardcodeados en `:core`**:
    - `core/src/main/kotlin/com/controlcal/core/solver/FoodSelector.kt:208-255` (mostrados en `MacroTetrisScreen.kt:349-353`)
    - `core/src/main/kotlin/com/controlcal/core/nutrition/QuickCaloriesValidator.kt:15, 38`
    - `core/src/main/kotlin/com/controlcal/core/nutrition/FoodPortionFormatter.kt:33, 36, 38, 42`
    - `core/src/main/kotlin/com/controlcal/core/meal/MealConfigResult.kt:15, 19, 23, 27, 31, 35`
    - `core/src/main/kotlin/com/controlcal/core/tdee/TdeeEstimator.kt:116-120`, `TdeeGoalProposal.kt:24`, `TdeeProposalCalculator.kt:78-90`
    - `core/src/main/kotlin/com/controlcal/core/model/DefaultMeals.kt:16-19`
  - **Textos de UI en español hardcodeados en `:app` (fuera de `strings.xml`)**:
    - `MainActivity.kt:518`
    - `ProductConfirmationScreen.kt:721, 723, 724` y `ProductConfirmationViewModel.kt:103, 116-118, 130-132, 144-146, 158-160, 171, 180, 189, 198, 211, 223, 240-242, 327-331, 345, 414`
    - `MealDetailViewModel.kt:95, 288, 309, 378, 398, 425-427, 444, 699-701, 708`
    - `AddEntryScreen.kt:510, 547, 568, 573, 661` y `AddEntryViewModel.kt:195, 203, 211, 338-340, 381, 402-403, 428`
    - `DailyPanelViewModel.kt:134, 279, 294, 386, 429-431, 462, 621, 632, 639, 647, 658, 666`
    - `PantryScreen.kt:311` y `PantryViewModel.kt:102, 104, 177, 183, 189, 329, 355-358`
    - `ScannerScreen.kt:665-667` y `ScannerViewModel.kt:311`
    - `SettingsScreen.kt:700, 703, 782, 836` y `SettingsViewModel.kt:101-109, 120-194, 216-225, 271, 304, 339, 376, 404, 430, 469`
    - `WeightScreen.kt:409, 413, 417, 454-455`, `WeightTrendChart.kt:195-198, 444` y `WeightViewModel.kt:90, 120, 143, 180, 198, 204, 209, 245, 272, 275, 303, 316, 322, 327, 359, 391, 403`
    - `OcrCaptureViewModel.kt:150, 216, 228`
  - **Identificador de recurso en español**:
    - `app/src/main/res/values/strings.xml:403` y `MealDetailScreen.kt:491`: `R.string.meal_detail_sobras_badge` (`sobras` en español en lugar de `tare` / `leftovers`).
- **Referencia incumplida**: Norma 7 de `AGENTS.md` (*«Textos de interfaz en español y en strings.xml. Código, identificadores y commits en inglés. Decimales con coma en UI»*).

---

### Hallazgo #10 — **[MEDIO · NORMA 2 `AGENTS.md`]** Lógica pura de negocio (mapeo y conversión kJ→kcal de Open Food Facts, moda de frecuentes, escalado de macros TDEE y serialización de patrón semanal) implementada en `:app` en lugar de `:core`
- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/data/off/OffMapper.kt:10-124` y `OffModels.kt:44-116` (conversión `kj / 4.184`, parser regex de `serving_size`, heurística `parseIsLiquid` y mapeo a `Product`, sin imports de Android).
  - `app/src/main/java/com/controlcal/app/ui/entry/AddEntryViewModel.kt:117-153` (cálculo estadístico de moda de gramos y ranking de alimentos frecuentes para el buscador).
  - `app/src/main/java/com/controlcal/app/ui/panel/DailyPanelViewModel.kt:241-252` (fórmula de escalado proporcional de proteínas, carbohidratos y grasas al aceptar una propuesta TDEE).
  - `app/src/main/java/com/controlcal/app/data/repo/UserSettingsRepositoryImpl.kt:61-89` (serialización y parseo de `WeeklyDayPattern`).
- **Referencia incumplida**: Norma 2 de `AGENTS.md` (*«La lógica pura (cálculos, parser de OCR, solver, TDEE, mapeos) va en el módulo :core, que no importa nada de android.* ni androidx.*. Todo lo de :core lleva tests unitarios»*).

---

### Hallazgo #11 — **[MEDIO · FUNCIONAL]** Bugs de interfaz en Modo Tara (comida vacía), «Repetir comida» en Panel Diario, subtítulo de `LocalProductCard` en escáner y cursor táctil de `WeightTrendChart`
- **Ubicación**:
  1. **Modo tara silencioso cuando no hay alimentos elegibles (`MealDetailViewModel.kt:396-401`, `MealDetailScreen.kt:260-263`)**: Si el usuario pulsa «Restar sobras» en una comida vacía o que solo tiene calorías rápidas, `onOpenTareDialog()` escribe en `_uiState.update { it.copy(userMessage = "...") }` (campo obsoleto que `MealDetailScreen` nunca lee) en vez de emitir en `_feedbackEvents`, y ni siquiera cierra el menú desplegable (`isOptionsMenuExpanded = false`).
  2. **Acción «Repetir comida» desconectada en `DailyPanelScreen.kt:121, 151`**: `DailyPanelScreen` recibe el callback `onRepeatMeal: (String) -> Unit` conectado a `DailyPanelViewModel.onRepeatMeal` (`DailyPanelViewModel.kt:627-650`), pero nunca lo invoca en ninguna línea de `DailyPanelScreen.kt`.
  3. **Subtítulo erróneo en `ScannerScreen.kt:713-717` (`LocalProductCard`)**: Pasa `kcal100g` y `protein100g` a `R.string.scanner_product_already_saved_subtitle` (`"%1$s · %2$s kcal/100 g"`, `strings.xml:25`), mostrando p. ej. `"370 · 13,5 kcal/100 g"` (etiquetando los gramos de proteína como `kcal/100 g`).
  4. **Desfase de coordenadas táctiles (`dp` vs `px`) en `WeightTrendChart.kt:489-490`**: En el `Canvas` (`líneas 235-236`) los márgenes se dibujan con `paddingLeft = 44.dp.toPx()` y `paddingRight = 16.dp.toPx()`, pero `findClosestPointIndex` (`líneas 489-490`) calcula la posición X del toque usando `44f` y `16f` en crudo sin convertir `dp` a `px`, desplazando el punto seleccionado en pantallas de alta densidad.
  5. **Sufijo `"g"` fijo para líquidos en chips de sugerencias (`DailyPanelScreen.kt:735`)**: `QuickLogChip` muestra siempre `"${NumberFormatHelper.formatDecimal(suggestion.defaultGrams)} g"` aunque el producto tenga `isLiquid = true` (`ml`).

---

### Hallazgo #12 — **[MEDIO · REQUISITOS `PLAN.md`]** Tareas marcadas o pendientes con requisitos incompletos respecto a `PLAN.md` (`0.3` linter, `1.4` anillo diario, `1.6` selector de comida en buscador, `3.1/3.2` 19 etiquetas en vez de 25)
- **Ubicación**:
  1. **Tarea `0.3` (Linter)**: Sigue pendiente (`ESTADO.md:11, 148`). No hay configuración de `ktlint` ni `detekt` en Gradle.
  2. **Tarea `1.4` (Anillo en Panel Diario)**: `PLAN.md:177` pide *«Panel diario con anillo, macros y lista por comida»*. `DailyPanelScreen.kt:556-566, 638-648` solo usa barras rectas `LinearProgressIndicator`; no hay anillo circular.
  3. **Tarea `1.6` (Selector de comida en `AddEntryScreen`)**: `PLAN.md:68, 179` pide en la pantalla de añadir entrada *«buscador, cantidad, comida (preseleccionada según la hora)»*. En `AddEntryScreen.kt`, la comida viene fijada por la tarjeta desde la que se abrió y no se puede cambiar de comida dentro de `AddEntryScreen`.
  4. **Tareas `3.1`, `3.2` y `3.3` (25 etiquetas reales)**: `PLAN.md:187-189` exige recoger 25 etiquetas, tener 25 casos cargados en `:core` y acertar al menos 20 de 25. Solo se capturaron 22 archivos y `expected_labels.json` (`OcrFixturesIntegrityTest.kt:14`) solo contiene **19 casos**.

---

### Hallazgo #13 — **[MEDIO · TESTS `:core` Y `:app`]** Clases y ramas de `:core` sin tests unitarios (Norma 2) y tests con aserciones incompletas o falsos positivos
- **Ubicación**:
  1. **`GetActiveGoalProfileUseCase.kt:13-22` y `DefaultGoalProfiles.kt:6-32`**: 0 tests unitarios en `core/src/test`.
  2. **Métodos con lógica por defecto en `MealEntryRepository.kt:8-40` (`save`, `saveAll`, `updateLoggedAtForMeal`, `insertAll`) y `ProductRepository.kt:9` (`search`)**: 0 tests unitarios en `core/src/test`.
  3. **`NutritionLabelParserParameterizedTest.kt:38-48`**: Solo comprueba `sugars`, `satFat`, `fiber` y `salt` cuando el valor esperado en `expected_labels.json` **no es `null`** (`expected.fiber?.let { ... }`). Si el parser inventa un valor falso cuando el esperado es `null` (13 de las 19 etiquetas tienen `fiber = null`), el test pasa en verde. Tampoco comprueba nunca `confidence` ni `approximations`.
  4. **Ramas de `NutritionLabelParser.kt` con 0 % de cobertura en tests**: Conversión de solo `kJ` a `kcal` (`parseKjConversion`, `líneas 478-488, 517-530`) y detección de `"trazas"` (`líneas 626, 642-644`), ya que ninguna de las 19 etiquetas del corpus ejercita esas ramas y no existen tests unitarios sintéticos para `NutritionLabelParser`.
  5. **Ramas de `FoodSelector.kt` y `MacroRounding.kt` sin test en `MacroTetrisTest.kt`**: Exclusión de productos con `inPantry = false` o `archived = true` (`FoodSelector.kt:34`), sugerencias `MISSING_FAT` y `CALORIE_MISMATCH` (`FoodSelector.kt:238-259`), redondeo de líquidos a pasos de 10 ml (`MacroRounding.kt:10, 35`) y límites personalizados `minG` / `maxG` (`MacroSolver.kt:28-44`, `MacroRounding.kt:29, 38-39`).
  6. **Fakes de `MealEntryRepository` en todos los tests de ViewModel de `:app`**: Ninguno de los 7 tests de ViewModel sobrescribe `save(entry)`, por lo que heredan el método default de la interfaz y **nunca prueban la rama `MealEntrySaveResult.Merged` ni la acción «Deshacer» de suma (`M.9`) en la capa ViewModel**.
  7. **Tests plantilla vacíos**: `app/src/test/java/com/controlcal/app/ExampleUnitTest.kt:1-17` (`2 + 2 == 4`) y `app/src/androidTest/java/com/controlcal/app/ExampleInstrumentedTest.kt:1-24`.

---

### Hallazgo #14 — **[BAJO · DATOS POR DEFECTO / PLACEHOLDERS]** Datos nutricionales y de peso por defecto hardcodeados fuera de `debug` y plugin de serialización innecesario en `core/src/main`
- **Ubicación**:
  - `core/src/main/kotlin/com/controlcal/core/model/DefaultGoalProfiles.kt:6-32` (`2400 kcal` entreno / `2000 kcal` descanso con fecha `"2020-01-01"`)
  - `app/src/main/java/com/controlcal/app/ui/weight/WeightViewModel.kt:104` (`70.0` kg por defecto al pulsar `±0,1 / ±0,5` sin pesajes previos)
  - `app/src/main/java/com/controlcal/app/data/off/OffClient.kt:42` (`ControlCal/1.0 (cambiar@gmail.com)`)
  - `core/build.gradle.kts:3` (plugin `kotlin.serialization` aplicado al módulo `:core` aunque solo se usa en `core/src/test`)

---

### Hallazgo #15 — **[BAJO · CÓDIGO MUERTO Y ARCHIVOS NO DOCUMENTADOS]** Código muerto en `:core` y `:app`, más de 45 strings huérfanos en `strings.xml` y archivos de herramientas/configuración no reflejados en `PLAN.md` ni `ESTADO.md`
- **Ubicación**:
  - **En `:core`**: `GetDayTotalsUseCase.kt:12-19`, `DateHelper.getTodayUtcMillis` (`DateHelper.kt:51-53`), `MealConfigError.message` (`MealConfigResult.kt:13-36`), `TdeeCalculationResult.InsufficientData.explanation` (`TdeeCalculationResult.kt:29`), `TdeeGoalProposal.explanation` y `disclaimer` (`TdeeGoalProposal.kt:17-25`), y métodos de repositorios nunca llamados (`getProfilesForDayTypeUpTo`, `getByProduct`, `insertAll`, `searchByName`, `search`, `getBetween`).
  - **En `:app`**: Propiedades de mensajes y métodos `on*MessageConsumed()` obsoletos en 7 `UiState`/`ViewModel` tras `M.10`, `app/src/main/res/values/colors.xml:1-10`, >45 entradas `<string>` en `strings.xml` sin referenciar, `.codex/hooks.json`, `.impeccable/config.local.json`, `.agents/skills/impeccable/`, carpeta `ocr_labels/` duplicada en raíz y `README.md` desactualizado (`AGP 9.4.1`).

---

## Parte II — Desarrollo detallado de los 7 bloques solicitados

---

### 1. Tabla con cada tarea de `PLAN.md` (`0.1` a `M.10`): estado real y qué falta

> **Foco especial en `0.3`, `0.4` y `1.4` a `1.8`** (que `docs/ESTADO.md` marca como pendientes `[ ]`, salvo `0.4` que se marcó tras la revisión de seguridad):
> - **`0.3` (Linter y test de ejemplo)**: **Parcial**. Existen tests en `:core`, pero **no se ha configurado ningún linter (`ktlint` ni `detekt`)** en Gradle.
> - **`0.4` (Manifest base, `allowBackup=false`, configuración de red)**: **Hecha** (cerrada el 2026-10-19 con `allowBackup="false"`, `network_security_config.xml` y `data_extraction_rules.xml`).
> - **`1.4` (Panel diario con anillo, macros y lista por comida)**: **Parcial** (desarrollada dentro de `4.1`, `4.3`, `M.3` y `M.4`). Muestra macros y tarjetas por comida con datos reales, pero **falta el anillo de calorías** (`DailyPanelScreen.kt:556-566` usa una barra lineal `LinearProgressIndicator`) y el callback `onRepeatMeal` quedó desconectado en `DailyPanelScreen.kt:121, 151`.
> - **`1.5` (Alta y edición manual de producto)**: **Sin hacer**. Sigue mostrando `ManualEntryPlaceholderScreen` (`MainActivity.kt:496-550`, *"Próximamente en tarea 1.5"*). No se puede dar de alta un producto manualmente sin usar cámara/OFF, ni editar un producto existente, ni archivarlo desde la interfaz.
> - **`1.6` (Añadir entrada: buscador, cantidad, comida)**: **Parcial** (desarrollada dentro de `M.4` y `M.7` en `AddEntryScreen.kt`). Permite buscar desde la primera letra y elegir cantidad en gramos o unidades en $\le 3$ toques, pero **no incluye selector de comida preseleccionada por hora dentro de `AddEntryScreen`** (solo se entra fijando previamente la comida al pulsar `+` en una tarjeta de comida) y el atajo a alta manual lleva al placeholder de la `1.5`.
> - **`1.7` (Modo tara)**: **Parcial** (desarrollada dentro de `M.4` en `CreateTareEntryUseCase.kt` y `MealDetailScreen.kt:652-723`). Resta gramos/macros y los totales diarios y por comida nunca bajan de cero, pero **si se pulsa «Restar sobras» en una comida sin alimentos elegibles no muestra ningún aviso ni cierra el menú** (`MealDetailViewModel.kt:396-401`).
> - **`1.8` (Objetivos fijos, ajustes y agua)**: **Parcial** (objetivos y ajustes se hicieron en `4.3` en `SettingsScreen.kt`). **Falta por completo el registro y visualización de agua (`water_ml`) en la interfaz**, y además `DailyLogRepositoryImpl.setWaterMl` (`línea 31`) tiene un bug que pierde el dato si aún no existe fila en `daily_logs` para esa fecha.

| Tarea | Descripción en `PLAN.md` | Marca en `ESTADO.md` | Estado real en código | Qué falta o qué falla exactamente |
| --- | --- | --- | --- | --- |
| **0.1** | Preguntar al humano los datos de la sección 12 | `[x]` | **Hecha** | Respuestas anotadas en `docs/ESTADO.md:58-62`. Queda pendiente confirmar la RAM exacta del Galaxy S26 Ultra (`ESTADO.md:61, 146`) y sustituir el email provisional `cambiar@gmail.com` si se desea (`ESTADO.md:60, 147`). |
| **0.2** | Proyecto con `:app` y `:core`, version catalog, versiones verificadas | `[x]` | **Hecha** | Compila en ambos módulos con `gradle/libs.versions.toml`. |
| **0.3** | Linter (`ktlint` o `detekt`) y un test de ejemplo en `:core` | `[ ]` | **Parcial** | `:core:test` está en verde, pero **falta integrar `ktlint` o `detekt`** en `libs.versions.toml` y `build.gradle.kts` (`Hallazgo #12`). |
| **0.4** | Manifest base, `allowBackup=false`, configuración de red (`REVISION HUMANA`) | `[x]` | **Hecha** | Completada en `AndroidManifest.xml`, `network_security_config.xml`, `backup_rules.xml` y `data_extraction_rules.xml`. |
| **1.1** | Entidades, DAOs, migración 1 y tests de DAO | `[x]` | **Hecha** | Esquema `1.json`, entidades, DAOs y tests con Robolectric en verde. |
| **1.2** | Cifrado con SQLCipher y Keystore (`REVISION HUMANA`) | `[x]` | **Hecha** | `SQLCipherDriver`, `KeystoreKeyCipher`, `DatabaseKeyResolver` y `controlcal_key_store` operativos. |
| **1.3** | Casos de uso en `:core`: totales del día y restante | `[x]` | **Parcial** | `NutritionCalculator` y `CalculateDayRemainingUseCase` tienen tests, pero **`GetActiveGoalProfileUseCase` no tiene test en `:core`** y **`DailyLogRepositoryImpl.setWaterMl` / `setNotes` (`líneas 31, 34`) fallan silenciosamente si no existe fila previa en `daily_logs`** (`Hallazgos #4 y #13`). |
| **1.4** | Panel diario con anillo, macros y lista por comida | `[ ]` | **Parcial** | Hecha dentro de `4.1`/`4.3`/`M.3`/`M.4` (`DailyPanelScreen.kt`), pero **falta el anillo circular de calorías** (usa `LinearProgressIndicator` en `DailyPanelScreen.kt:556`) y el parámetro `onRepeatMeal` en `DailyPanelScreen.kt:121, 151` no está conectado a ningún botón (`Hallazgos #11 y #12`). |
| **1.5** | Alta y edición manual de producto | `[ ]` | **Sin hacer** | Sigue en `ManualEntryPlaceholderScreen` (`MainActivity.kt:496-550`). **Falta el formulario de alta manual (`ProductSource.MANUAL`), la edición de productos existentes y la opción de archivar/borrar producto** (`Hallazgo #1`). |
| **1.6** | Añadir entrada: buscador, cantidad, comida | `[ ]` | **Parcial** | Hecha en `M.4`/`M.7` (`AddEntryScreen.kt`). **Falta selector de comida con preselección horaria dentro de `AddEntryScreen`** (`PLAN.md:68`) y el botón de crear producto manual lleva al placeholder de la `1.5` (`Hallazgos #1 y #12`). |
| **1.7** | Modo tara | `[ ]` | **Parcial** | Hecha en `M.4` (`CreateTareEntryUseCase.kt`, `MealDetailScreen.kt:652-723`). **Falla al pulsar «Restar sobras» cuando la comida no tiene alimentos elegibles**: escribe en `uiState.userMessage` (no observado por la UI) y no cierra el menú (`MealDetailViewModel.kt:396-401`, `Hallazgo #11`). |
| **1.8** | Objetivos fijos, ajustes y agua | `[ ]` | **Parcial** | Los objetivos y ajustes están en `SettingsScreen.kt` (`4.3`), pero **falta por completo la UI y lógica de ViewModel para registrar y mostrar el agua (`water_ml`)** (`Hallazgo #4`). |
| **2.1** | Permiso de cámara y vista previa con CameraX | `[x]` | **Hecha** | `CameraPreview.kt` y `ScannerScreen.kt` estables con `unbindAll()` al salir. |
| **2.2** | Analizador de códigos con doble lectura | `[x]` | **Hecha** | `BarcodeAnalyzer.kt` y `BarcodeConsecutiveGate.kt` con EAN-13, EAN-8, UPC-A y UPC-E. |
| **2.3** | `OffClient`, mapeo y campos nulos, con tests de mapeo | `[x]` | **Parcial** | `OffClient` y `OffMapper` funcionan y tienen tests, pero **`OffMapper` (lógica pura de mapeo y conversión kJ→kcal) se puso en `:app` en vez de `:core`**, incumpliendo la Norma 2 de `AGENTS.md` (`Hallazgo #10`). |
| **2.4** | Flujo local, luego red, luego no encontrado | `[x]` | **Parcial** | El flujo local → red → no encontrado funciona, pero en `LocalProductCard` (`ScannerScreen.kt:713-717`) el subtítulo formatea los gramos de proteína como `"kcal/100 g"`, y el botón «Alta manual» cuando no se encuentra el código lleva al placeholder de `1.5` (`Hallazgos #1 y #11`). |
| **2.5** | Confirmación, guardado y atribución | `[x]` | **Hecha** | `ProductConfirmationScreen.kt` y `AboutScreen.kt` completos. |
| **3.1** | Pantalla de depuración que guarda foto y salida de ML Kit en JSON | `[x]` | **Parcial** | `OcrCollectorScreen.kt` funciona en `debug`, pero **solo se recogieron 22 etiquetas en lugar de las 25 exigidas** en `PLAN.md:187` (`Hallazgo #12`). |
| **3.2** | Fixtures en `:core` con los valores esperados que escribe el humano | `[x]` | **Parcial** | `PLAN.md:188` exige **25 casos cargados**, pero `expected_labels.json` y `OcrFixturesIntegrityTest.kt:14` solo tienen **19 casos** (3 descartados por desenfoque y 3 nunca capturados) (`Hallazgo #12`). |
| **3.3** | Parser y validaciones | `[x]` | **Parcial** | Pasa los 19 fixtures (menos de los 20 aciertos mínimos de `PLAN.md:189`), pero **`NutritionLabelParser` tiene reglas sobreajustadas a los fixtures e inventa valores no presentes en la etiqueta** (`Hallazgos #2 y #3`), y **`LabelValidator` devuelve mensajes en inglés y con punto decimal** (`Hallazgo #9`). |
| **3.4** | Captura y confirmación con campos dudosos resaltados | `[x]` | **Parcial** | `OcrCaptureScreen` y `ProductConfirmationScreen` funcionan, pero los mensajes de `LabelValidator` se ven **en inglés con punto decimal**, los valores inventados por Atwater se muestran como `"Valor aproximado (<0,5 g)"` o sin aviso con confianza `0.95f`, y `OcrCaptureViewModel` no libera `TextRecognizer` al salir de la pantalla (`Hallazgos #2, #7 y #9`). |
| **4.1** | Sugerencias por franja y repetir comida o día | `[x]` | **Parcial** | `QuickLogRanker`, `RepeatMealUseCase` y `CopyDayUseCase` están hechos, pero **«Repetir comida» quedó desconectado en `DailyPanelScreen.kt:121, 151`** (solo accesible entrando al detalle de comida) y los chips de sugerencia muestran siempre `"g"` aunque el producto sea líquido (`DailyPanelScreen.kt:735`) (`Hallazgo #11`). |
| **4.2** | Registro y gráfica de peso | `[x]` | **Parcial** | `WeightScreen` y `WeightTrendChart` funcionan, pero **el cursor táctil (`scrubber`) calcula mal la coordenada X en pantallas de densidad $\neq 1\text{x}$** al usar `44f`/`16f` en vez de `44.dp.toPx()`/`16.dp.toPx()` (`WeightTrendChart.kt:489-490`, `Hallazgo #11`). |
| **4.3** | Perfiles de objetivo por tipo de día | `[x]` | **Parcial** | Funciona en `SettingsScreen` y `DailyPanelScreen`, pero **`GetActiveGoalProfileUseCase` no tiene test unitario en `:core`** y la serialización de `WeeklyDayPattern` está en `:app` (`Hallazgos #10 y #13`). |
| **4.4** | `TdeeEstimator` en `:core` con tests y propuesta semanal | `[x]` | **Parcial** | `TdeeEstimator` y `TdeeProposalCalculator` funcionan, pero el cálculo de reescalado proporcional de macros al aceptar la propuesta está en `DailyPanelViewModel.kt:241-252` (`:app`) en lugar de `:core` (`Hallazgo #10`). |
| **5.1** | Despensa y límites por producto | `[x]` | **Hecha** | `PantryScreen`, `PantryViewModel` y `PantryLimitsValidator` operativos. |
| **5.2** | `MacroSolver` y `FoodSelector` con tests | `[x]` | **Parcial** | Algoritmo y casos límite principales en verde (< 10 ms), pero `FoodSelector.kt:208-255` tiene textos de interfaz hardcodeados en `:core` y faltan tests para líquidos (`10 ml`), `minG`/`maxG` personalizados y filtro `inPantry`/`archived` (`Hallazgos #9 y #13`). |
| **5.3** | Pantalla Macro Tetris y añadir al diario | `[x]` | **Parcial** | `MacroTetrisScreen` funciona, pero **ejecuta el solver en el hilo principal** (`MacroTetrisViewModel.kt:89-102`) e **ignora la fecha seleccionada en el diario**, leyendo y guardando siempre en `LocalDate.now()` (`Hallazgos #5 y #6`). |
| **M.1** | Selector de fecha con calendario | `[x]` | **Hecha** | `DatePickerDialog` en `DailyPanelScreen` y `WeightScreen` con `DateHelper` en `:core`. |
| **M.2** | Comidas con id y configuración guardada | `[x]` | **Hecha** | `ConfiguredMeal`, casos de uso en `:core` y `MealConfigRepositoryImpl` sobre DataStore. |
| **M.3** | Comidas del día en Ajustes y en el panel | `[x]` | **Hecha** | Gestión en `SettingsScreen`, orden por hora, comidas desactivadas con entradas y sección «Sin asignar». |
| **M.4** | Tarjetas de comida y detalle | `[x]` | **Hecha** | `MealCard` con resumen de 1-2 líneas y `MealDetailScreen` con selector de comida en cabecera y acciones. |
| **M.5** | `[-]` y `[+]` y hora de la comida | `[x]` | **Hecha** | `MealPortionCalculator` y `MealTimeCalculator` preservando el historial ante ediciones del producto. |
| **M.6** | Calorías rápidas (`REVISION HUMANA`) | `[x]` | **Hecha** | Esquema Room v2 (`product_id` nullable), `MIGRATION_1_2` con test y `QuickCaloriesValidator`. |
| **M.7** | Buscador sobre todos los productos locales | `[x]` | **Hecha** | `TextNormalizer` y `ProductSearchEngine` buscando por nombre, marca y código sin tildes ni mayúsculas. |
| **M.8** | Búsqueda por nombre en Open Food Facts (`REVISION HUMANA`) | `[x]` | **Hecha** | Búsqueda manual en `OffClient.searchProductsByName`, `OffRateLimiter` (10 req/min) y paso obligatorio por `ProductConfirmationScreen`. |
| **M.9** | Sumar el mismo alimento en la misma comida | `[x]` | **Parcial** | `MealEntryMerger` y transacción en `MealEntryRepositoryImpl.save` funcionan y están probados en `MealEntryRepositoryTest`, pero **los tests de ViewModels no prueban la rama `Merged`** porque sus fakes no sobrescriben `save` (`Hallazgo #13`). |
| **M.10** | Respuesta en todos los formularios | `[x]` | **Parcial** | Implementado `UiFeedbackEvent` y `HandleFeedbackEvents` en casi toda la app, pero **`MacroTetrisViewModel.addToDiary` (`línea 172`) silencia errores sin snackbar** y **`MealDetailViewModel.onOpenTareDialog` (`línea 398`) no muestra feedback cuando no hay alimentos para restar sobras** (`Hallazgos #5 y #11`). |

---

### 2. Lo que hay en el código y en el repositorio y NO está en `PLAN.md` ni en `ESTADO.md`

1. **Hook de Codex/Claude en `.codex/hooks.json:1-32`**:
   - Define un hook `Stop` que ejecuta `powershell.exe -Command "npx --yes @wanyao/impeccable live- Stop"` (`línea 11`). No figura ni en `PLAN.md` ni en `ESTADO.md`.
2. **Archivo `.impeccable/config.local.json:1-6` y directorio `.agents/skills/impeccable/**`**:
   - `ESTADO.md:96` menciona `.impeccable/config.json` y `PRODUCT.md`, pero no menciona `.impeccable/config.local.json` (`"browserPort": 53797`) ni la copia completa del skill en `.agents/skills/impeccable/`.
3. **Directorio `ocr_labels/` en la raíz del proyecto (`ocr_labels/label_01..22.jpg` y `.json`)**:
   - Carpeta local con 44 archivos (ignorada en `.gitignore:71`) que duplica los JSON copiados en `core/src/test/resources/ocr_fixtures/`.
4. **Inconsistencias en `README.md:1-74`**:
   - `README.md:69` documenta `AGP 9.4.1` (la versión real en `gradle/libs.versions.toml:2` es `9.2.1`).
   - `README.md:23-25` marca *Macro Tetris Solver*, *OCR Label Reading* y *Adaptive TDEE* como `*(In Development)*` pese a estar implementados en las fases 3, 4 y 5.
5. **Archivos residuales de la plantilla inicial de Android Studio**:
   - `app/src/main/res/values/colors.xml:1-10` (colores `purple_200`, `purple_500`, `teal_200`, etc., nunca usados por el tema Compose).
   - `app/src/test/java/com/controlcal/app/ExampleUnitTest.kt:1-17`.
   - `app/src/androidTest/java/com/controlcal/app/ExampleInstrumentedTest.kt:1-24`.
6. **Métodos de consulta y borrado en DAOs/Repositorios no contemplados ni usados**:
   - `ProductDao.deleteById(id: Long)` (`app/src/main/java/com/controlcal/app/data/db/dao/ProductDao.kt:53-54`): método `DELETE FROM products WHERE id = :id` que ni siquiera está en la interfaz `ProductRepository` (y contradice la regla de `PLAN.md:52` de archivar en vez de borrar).

---

### 3. Reglas de `AGENTS.md` incumplidas (con archivo y línea)

#### Norma 2 — *«La lógica pura (cálculos, parser de OCR, solver, TDEE, mapeos) va en el módulo :core, que no importa nada de android.* ni androidx.*. Todo lo de :core lleva tests unitarios.»*
1. **Lógica pura ubicada en `:app` en lugar de `:core`**:
   - `app/src/main/java/com/controlcal/app/data/off/OffMapper.kt:10-124` y `app/src/main/java/com/controlcal/app/data/off/OffModels.kt:44-116`: Mapeo de DTOs de Open Food Facts a modelo de dominio `Product`, conversión de julios a kilocalorías (`KJ_TO_KCAL = 4.184` en `OffMapper.kt:122`), extracción por expresión regular de gramos de ración (`parseServingGrams` en `OffMapper.kt:99-105`) y detección de líquidos (`parseIsLiquid` en `OffMapper.kt:107-119`).
   - `app/src/main/java/com/controlcal/app/ui/entry/AddEntryViewModel.kt:117-153`: Algoritmo de cálculo de frecuencia y moda de gramos (con redondeo a 0,1 g) para ordenar los productos habituales en el buscador.
   - `app/src/main/java/com/controlcal/app/ui/panel/DailyPanelViewModel.kt:241-252`: Cálculo matemático de reescalado proporcional de macronutrientes (`protein`, `carbs`, `fat`) al aceptar un ajuste de kcal del TDEE.
   - `app/src/main/java/com/controlcal/app/data/repo/UserSettingsRepositoryImpl.kt:61-89`: Serialización y parseo puro de `WeeklyDayPattern` (`serializePattern`, `parsePattern`).
2. **Clases y métodos de `:core` sin test unitario**:
   - `core/src/main/kotlin/com/controlcal/core/nutrition/GetActiveGoalProfileUseCase.kt:13-22`.
   - `core/src/main/kotlin/com/controlcal/core/model/DefaultGoalProfiles.kt:6-32`.
   - `core/src/main/kotlin/com/controlcal/core/repo/MealEntryRepository.kt:8-40` (lógica default de `save`, `saveAll`, `updateLoggedAtForMeal`, `insertAll`).
   - `core/src/main/kotlin/com/controlcal/core/repo/ProductRepository.kt:9` (`search`).

#### Norma 5 — *«Nada obtenido por escáner, OCR o IA se guarda sin pantalla de confirmación.»*
1. **Valores inventados por `NutritionLabelParser` en OCR sin advertencia de campo faltante**:
   - `core/src/main/kotlin/com/controlcal/core/ocr/NutritionLabelParser.kt:97-118, 128-136, 749-878` junto con `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationUiState.kt:183-186`: Aunque el usuario pasa por `ProductConfirmationScreen`, el parser rellena campos no leídos por el OCR con valores deducidos (o el 37 % de grasa para saturadas) y desactiva los flags `originallyMissing*`, ocultando que la etiqueta no dio ese dato.
2. **Tarjeta rápida `LocalProductCard` en `ScannerScreen` (matiz sobre productos ya confirmados)**:
   - `app/src/main/java/com/controlcal/app/ui/scanner/ScannerScreen.kt:463-475, 647-780` y `ScannerViewModel.kt:210-297`: Cuando se escanea un código que ya estaba guardado en la base de datos local, no abre `ProductConfirmationScreen`, sino una tarjeta flotante (`LocalProductCard`) con selector de comida, campo de gramos y botón explícito «Añadir al diario».

#### Norma 7 — *«Textos de interfaz en español y en strings.xml. Código, identificadores y commits en inglés. Decimales con coma en UI.»*
1. **Textos en inglés y con punto decimal que llegan a la UI**:
   - `core/src/main/kotlin/com/controlcal/core/ocr/LabelValidator.kt:60, 74, 98, 112, 127, 147`, propagados por `ProductConfirmationUiState.kt:150-152` y `ProductConfirmationViewModel.kt:260-262`, y pintados en `ProductConfirmationScreen.kt:230, 722` (ej. `"Caloric balance mismatch: declared 500.0 kcal vs calculated 250.0 kcal (50.0% difference)"`).
2. **Identificador en español**:
   - `app/src/main/res/values/strings.xml:403` y `app/src/main/java/com/controlcal/app/ui/detail/MealDetailScreen.kt:491`: `meal_detail_sobras_badge`.
3. **Textos en español hardcodeados en código Kotlin fuera de `strings.xml`**:
   - **En `:core`**:
     - `core/src/main/kotlin/com/controlcal/core/solver/FoodSelector.kt:208-255`
     - `core/src/main/kotlin/com/controlcal/core/nutrition/QuickCaloriesValidator.kt:15, 38`
     - `core/src/main/kotlin/com/controlcal/core/nutrition/FoodPortionFormatter.kt:33, 36, 38, 42`
     - `core/src/main/kotlin/com/controlcal/core/meal/MealConfigResult.kt:15, 19, 23, 27, 31, 35`
     - `core/src/main/kotlin/com/controlcal/core/tdee/TdeeEstimator.kt:116-120`
     - `core/src/main/kotlin/com/controlcal/core/tdee/TdeeGoalProposal.kt:24`
     - `core/src/main/kotlin/com/controlcal/core/tdee/TdeeProposalCalculator.kt:78-90`
     - `core/src/main/kotlin/com/controlcal/core/model/DefaultMeals.kt:16-19`
   - **En `:app`**:
     - `app/src/main/java/com/controlcal/app/MainActivity.kt:518`
     - `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationScreen.kt:721, 723, 724`
     - `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationViewModel.kt:103, 116-118, 130-132, 144-146, 158-160, 171, 180, 189, 198, 211, 223, 240-242, 327-331, 345, 414`
     - `app/src/main/java/com/controlcal/app/ui/detail/MealDetailViewModel.kt:95, 288, 309, 378, 398, 425-427, 444, 699-701, 708`
     - `app/src/main/java/com/controlcal/app/ui/entry/AddEntryScreen.kt:510, 547, 568, 573, 661`
     - `app/src/main/java/com/controlcal/app/ui/entry/AddEntryViewModel.kt:195, 203, 211, 338-340, 381, 402-403, 428`
     - `app/src/main/java/com/controlcal/app/ui/panel/DailyPanelViewModel.kt:134, 279, 294, 386, 429-431, 462, 621, 632, 639, 647, 658, 666`
     - `app/src/main/java/com/controlcal/app/ui/pantry/PantryScreen.kt:311`
     - `app/src/main/java/com/controlcal/app/ui/pantry/PantryViewModel.kt:102, 104, 177, 183, 189, 329, 355-358`
     - `app/src/main/java/com/controlcal/app/ui/scanner/ScannerScreen.kt:665-667`
     - `app/src/main/java/com/controlcal/app/ui/scanner/ScannerViewModel.kt:311`
     - `app/src/main/java/com/controlcal/app/ui/settings/SettingsScreen.kt:700, 703, 782, 836`
     - `app/src/main/java/com/controlcal/app/ui/settings/SettingsViewModel.kt:101-109, 120-194, 216-225, 271, 304, 339, 376, 404, 430, 469`
     - `app/src/main/java/com/controlcal/app/ui/weight/WeightScreen.kt:409, 413, 417, 454-455`
     - `app/src/main/java/com/controlcal/app/ui/weight/WeightTrendChart.kt:195-198, 444`
     - `app/src/main/java/com/controlcal/app/ui/weight/WeightViewModel.kt:90, 120, 143, 180, 198, 204, 209, 245, 272, 275, 303, 316, 322, 327, 359, 391, 403`
     - `app/src/main/java/com/controlcal/app/ui/ocr/OcrCaptureViewModel.kt:150, 216, 228`
     - `app/src/debug/java/com/controlcal/app/ui/debug/OcrCollectorScreen.kt:228, 283, 376, 418, 422, 478, 492, 522`, `OcrCameraPreview.kt:36`, `OcrCollectorViewModel.kt:114, 174, 180`

---

### 4. `:core`: Código sin tests, tests que no comprueban nada y análisis del parser de OCR

#### 4.1. Código en `:core` sin tests unitarios
1. **`GetActiveGoalProfileUseCase`** (`core/src/main/kotlin/com/controlcal/core/nutrition/GetActiveGoalProfileUseCase.kt:13-22`): Sin ningún test en `core/src/test` (tampoco se prueba su fallback a `DefaultGoalProfiles.defaultFor(dayType)` en la línea 20).
2. **`DefaultGoalProfiles`** (`core/src/main/kotlin/com/controlcal/core/model/DefaultGoalProfiles.kt:6-32`): Sin ningún test en `core/src/test`.
3. **Lógica en métodos default de `MealEntryRepository`** (`core/src/main/kotlin/com/controlcal/core/repo/MealEntryRepository.kt:8-40`): `save(entry)` (que invoca `MealEntryMerger.findMergeCandidate` y `merge`), `saveAll(entries)`, `updateLoggedAtForMeal(date, meal, loggedAt)` e `insertAll(entries)` no tienen tests en `core/src/test`.
4. **`ProductRepository.search(query)`** (`core/src/main/kotlin/com/controlcal/core/repo/ProductRepository.kt:9`): Sin test ni uso.
5. **Ramas de `NutritionLabelParser`** (`core/src/main/kotlin/com/controlcal/core/ocr/NutritionLabelParser.kt`):
   - `parseKjConversion` (`líneas 478-488, 517-530`, conversión `kj / 4.184` cuando no hay `kcal`): **0 % cubierto**, ninguna de las 19 etiquetas entra por esta rama y no hay tests unitarios sintéticos.
   - Detección de `"trazas"` / `"tracos"` (`líneas 626, 642-644`): **0 % cubierto**.
   - Entrada vacía (`parse(emptyList())`, `línea 37`) o texto sin tabla (`línea 57`): **0 % cubierto**.
6. **Ramas de `FoodSelector`, `MacroSolver` y `MacroRounding`**:
   - Filtro `it.inPantry && !it.archived` (`FoodSelector.kt:34`).
   - Sugerencias `MISSING_FAT` (`FoodSelector.kt:238-246`) y `CALORIE_MISMATCH` (`FoodSelector.kt:249-258`).
   - Clasificación `FoodCategory.VEGETABLE_LIGHT` para `kcal <= 0` y `FoodCategory.MIXED` (`FoodSelector.kt:175-182`).
   - Redondeo de líquidos a múltiplos de `10.0` ml (`MacroRounding.kt:10, 35`) y aplicación de límites personalizados `minG` y `maxG` (`MacroSolver.kt:28-44`, `MacroRounding.kt:29, 38-39`).
7. **Ramas de `NumberFormatHelper.formatDecimal`** (`core/src/main/kotlin/com/controlcal/core/nutrition/NumberFormatHelper.kt:14-27`):
   - Valores no finitos (`Double.NaN`, `Double.POSITIVE_INFINITY` en `línea 15`) y valores de `maxDecimals` distintos del por defecto (`maxDecimals = 0` o `1`).
8. **Ramas de `TdeeEstimator` (`core/src/main/kotlin/com/controlcal/core/tdee/TdeeEstimator.kt:44-53`) y `QuickLogRanker` (`core/src/main/kotlin/com/controlcal/core/suggest/QuickLogRanker.kt:57, 92, 101`)**:
   - Exclusión de `DailyIntake` con `isComplete = false` o fuera de la ventana de 21 días (`TdeeEstimator.kt:44-50`).
   - Exclusión de entradas de calorías rápidas (`productId == null`) y desempate de moda a favor de mayor gramaje en `QuickLogRanker.kt:57, 101`.
9. **Ordenación de múltiples productos recientes con consulta no vacía en `ProductSearchEngine.kt:50-56`**: En `ProductSearchEngineTest.kt:157-170` solo se prueba el orden de recientes con query vacía `""` (que sale en la línea 28 antes de llegar al `sortedWith` de la línea 50).

#### 4.2. Tests que no comprueban nada o tienen aserciones incompletas
1. **`NutritionLabelParserParameterizedTest.kt:38-48`**:
   - Usa `expected.sugars?.let { ... }`, `expected.satFat?.let { ... }`, `expected.fiber?.let { ... }` y `expected.salt?.let { ... }`. Cuando el fixture tiene `null` (p. ej. 13 de las 19 etiquetas tienen `"fiber": null`), **no ejecuta `assertNull(result.fiber)`**.
   - **No comprueba `result.confidence` ni `result.approximations`** en ninguno de los 19 casos.
2. **`MacroTetrisTest.kt:205-225` (`testRounding_DiscreteUnitsAndMultiplesOf5`)**:
   - Crea el producto `milk` con `isLiquid = true` (`línea 208`), lo pasa al solver (`línea 210`), pero en las aserciones (`líneas 213-224`) **solo comprueba `eggItem` y `oatsItem`**, sin comprobar qué hizo el redondeo con `milk`.
3. **`TdeeProposalCalculatorTest.kt:11-88`**:
   - Nunca comprueba `proposal.explanation` ni `proposal.disclaimer`.
4. **En `:app`**:
   - `ExampleUnitTest.kt:12-16` (`assertEquals(4, 2 + 2)`) y `ExampleInstrumentedTest.kt:17-23` (solo comprueba `packageName`).
   - Los 7 tests de ViewModel con `FakeMealEntryRepository` no sobrescriben `save(entry)`, dejando sin probar la respuesta de UI ante una fusión de entradas (`MealEntrySaveResult.Merged`).
   - `OcrCaptureViewModelTest.kt:19-135` solo prueba setters booleanos/strings; `takePhoto` y `processImageFile` no se prueban.

#### 4.3. Parser de OCR (`NutritionLabelParser.kt`): reglas ajustadas a los fixtures vs. reglas que deducen valores no dados por la etiqueta

##### A) Reglas ajustadas ad-hoc a los 19 fixtures (overfitting)
| Archivo y líneas | Regla codificada en `NutritionLabelParser.kt` | Fixture específico al que se ajustó | Riesgo fuera de los fixtures |
| --- | --- | --- | --- |
| `NutritionLabelParser.kt:717-719` | `if (type == NutrientType.SUGARS && v == 154.0) { v = 1.5 }` | `label_17.json:368` (`"154"` leído en vez de `"1,5 g"`) | Número mágico literal para una sola foto. |
| `NutritionLabelParser.kt:699-701` | `if (raw == "39" && type == NutrientType.SAT_FAT) { v = 3.0 }` | `label_11.json:359` (`"39"` leído en vez de `"3g"`) | Cualquier alimento real con 39 g de grasas saturadas ve cambiado su valor a 3,0 g. |
| `NutritionLabelParser.kt:703-710` | En tokens de 3 dígitos terminados en `'9'`: `if (digits == "95") 95.0 else digits.toDouble() / 10.0` | `label_12`, `label_15`, `label_20` vs excepción literal `"95"` para `label_08.json:224` (`"959"` = 95 g de hidratos) | Si un producto tiene 85 g de hidratos y ML Kit lee `"859"` (`85g`), lo convierte en `8,5 g`; pero si tiene `9,5 g` y lee `"959"`, lo deja en `95,0 g`. |
| `NutritionLabelParser.kt:691-697` | Tokens de 3 dígitos que empiezan por `"0"`: si acaban en `'9'` divide por 10 (`"059"` $\rightarrow 0.5$); si no, divide por 100 (`"012"` $\rightarrow 0.12$) | `label_05.json:152` (`"059"`) y `label_01.json:161` (`"012"`) | Si una etiqueta tiene `"0,09 g"` de sal y el OCR lee `"009"`, lo convierte en `0,0 g` en vez de `0,09 g`. |
| `NutritionLabelParser.kt:646-648` vs `674` | `if (clean.startsWith("b,")) clean = "5," + ...` pero en `cleanNumberString` (`línea 674`) `s.replace('b', '8')` | `label_03.json:215` (`"b,3g"` para `5,3 g`) | La misma letra `'b'` vale `5` o `8` según tenga coma detrás o no. |
| `NutritionLabelParser.kt:77-95` | Si no hay fila `SALT`, busca cualquier número $\le 15.0$ situado hasta 150 px debajo de `PROTEIN` | `label_21.json:341` (comentario explícito en `línea 78`: `e.g. label_21`) | Si una tabla no tiene fila de sal (o está debajo de fibra) y debajo de proteínas hay una vitamina, mineral o peso neto $\le 15$, lo asigna como sal. |
| `NutritionLabelParser.kt:197` | `s = s.replace("gras as", "grasas")` | `label_14.json:62` (`"25.10.26 R19 Gras as:"`) | Parche específico para un espacio espurio de una sola foto. |
| `NutritionLabelParser.kt:216` | `after100.indexOfAny(listOf("|", "30", "%", "porc", "rac"))` | `label_07.json:305` (`"o/100 g\| o/30 g %RI"`) | Busca literalmente el número `"30"` para partir la caja de cabecera. |
| `NutritionLabelParser.kt:328-338, 394` | `line.box.left < 500`, `contains("fibra de guisante")`, `endsWith(".grasas")` | `label_11`, `label_15`, `label_18` (`"agua, fibra de guisante"`, `".Grasas"`) | Depende de la resolución en píxeles de la foto (`left < 500`) y de ingredientes concretos (`guisante`). |
| `NutritionLabelParser.kt:356-359, 366-367, 385-386, 404-407` | `"labr energ"`, `"arbono"`, `"midrato"`, `it == "sa"`, `"mantequilla con sal"`, `"con proteinas de la leche"`, `"alto %"` | `label_01`, `label_03`, `label_04`, `label_05`, `label_17`, `label_21` | Parches léxicos específicos de los errores de ML Kit en esas capturas. |

##### B) Reglas que deducen o inventan valores que la etiqueta no da
1. **Estimación de grasas saturadas como el 37 % de las grasas totales (`NutritionLabelParser.kt:128-136`)**:
   - Cuando `SAT_FAT` no se detecta y el producto tiene `protein >= 15.0` y `fat in 1.0..10.0`, crea de la nada `SAT_FAT = round(fat * 0.37 * 10.0) / 10.0` (inventado para acertar el `1.0` de `label_15.json`).
2. **Deducción de `FAT`, `PROTEIN` o `CARBS` ausentes despejando la ecuación de Atwater (`NutritionLabelParser.kt:102-118` y `841-878`)**:
   - Si uno de los tres macronutrientes principales no se detecta en la etiqueta, calcula las calorías restantes ($\text{kcal} - 4P - 4C - 2\text{Fibra}$) y las divide entre 9 o entre 4 para inventar el macronutriente que falta.
3. **Asunción de `CARBS = 0.0` cuando `SUGARS == 0.0` (`NutritionLabelParser.kt:97-100`)**:
   - Si no se detectó la fila de hidratos pero azúcares vale `0.0`, rellena `CARBS = 0.0` con `isApproximate = false` (confianza `0.95f`).
4. **Suma de decenas (`+10, +20, +30, +40 g`) a grasas o proteínas (`NutritionLabelParser.kt:787-812`)**:
   - Si el cálculo de Atwater queda por debajo del 70 % de las `kcal` leídas, prueba a sumar `10.0`, `20.0`, `30.0` o `40.0` a las grasas y `10.0` o `20.0` a las proteínas hasta acercarse al 10 % de las `kcal`, sobrescribiendo el número con `isApproximate = false` (hecho para `label_17.json` donde `42 g` se leyó como `2 g` y `17 g` como `7 g`, y `label_18.json` donde `11,3 g` se leyó como `1,3 g`).
5. **Escalado `/10` de `PROTEIN`, `CARBS`, `FAT`, `SUGARS` y `SAT_FAT` guiado por Atwater o por topes (`NutritionLabelParser.kt:108-111, 749-785, 816-838`)**:
   - Divide entre 10 cualquier macro entero $\ge 10$ si con ello el balance de Atwater entra en el $\pm 15\%$, o si `sugars > carbs` o `satFat > fat`, dejando `isApproximate = false` y `confidence = 0.95f`.

---

### 5. Trabajo en el hilo principal y recursos que no se liberan

#### 5.1. Trabajo en el hilo principal (`Main Thread`)
1. **Apertura de SQLCipher + Keystore + `runBlocking` de `DataStore` en el arranque (`ControlCalDatabaseProvider.kt:36`, `DatabaseModule.kt:80-96`, `WrappedKeyStore.kt:35, 61`, `MainActivity.kt:89-98`)**:
   - `MainActivity` accede a `dailyPanelViewModel` en el primer fotograma, lo que pide a Hilt los DAOs y llama a `ControlCalDatabaseProvider.open()` (`@Synchronized`) en el hilo principal. El hilo principal queda bloqueado esperando a que termine la carga nativa de SQLCipher, el descifrado en Android Keystore y las lecturas `runBlocking(Dispatchers.IO)` de `DataStoreWrappedKeyStore`.
2. **Optimización matemática de Macro Tetris en el hilo principal (`MacroTetrisViewModel.kt:89-102`)**:
   - `calculate()` invoca `FoodSelector.selectAndSolve(...)` directamente en el hilo principal (tanto al cargar datos en `línea 85` como al excluir/incluir productos en `líneas 109, 117` o al pulsar «Recalcular»).
3. **Acceso síncrono a sistema de ficheros (`File.exists()`, `File.delete()`, `File.listFiles()`) en el hilo principal**:
   - `MainActivity.kt:142-144` (`file.exists()`, `file.delete()` al volver atrás desde confirmación).
   - `ProductConfirmationViewModel.kt:296-303` (`cleanupTempPhoto()` en `onConfirmAndSave` y `onDiscard`).
   - `OcrCaptureViewModel.kt:109-112, 116-122, 131` (`onRetryCapture`, `onCancelCapture`, `takePhoto`).
   - `OcrCollectorViewModel.kt:58, 65-66, 99, 104, 115, 175, 181, 217, 228` y `OcrCollectorScreen.kt:467` (en `debug`).
4. **Cálculos de dominio sobre colecciones en `Dispatchers.Main.immediate`**:
   - `PantryViewModel.kt:63-91, 361-378` (`computeDisplayed` filtrando y ordenando productos).
   - `WeightViewModel.kt:53` (`WeightTrend.calculateTrend`).
   - `DailyPanelViewModel.kt:112, 207-215` (`NutritionCalculator.calculateDayTotals`, `QuickLogRanker.rank`, `TdeeEstimator.estimate`).

#### 5.2. Recursos que no se liberan
1. **Los 10 ViewModels de `MainActivity.kt:89-98` nunca se destruyen al cambiar de pantalla**:
   - Al estar registrados con `by viewModels()` en `MainActivity`, salir de cualquier pantalla secundaria no invoca `onCleared()`.
2. **`TextRecognizer` nativo de ML Kit y `cameraExecutor` en `OcrCaptureViewModel.kt:38-45, 236-244`**:
   - Como `ocrCaptureViewModel` vive en `MainActivity`, una vez que el usuario abre la cámara OCR, ni `_recognizer: TextRecognizer` ni `cameraExecutor: Executor` (`Executors.newSingleThreadExecutor()`) se cierran al volver al Panel Diario.
3. **`TextRecognizer` y `cameraExecutor` en `OcrCollectorViewModel.kt:55-56, 231-235` (`OcrCollectorScreen.kt:70-82`)**:
   - `OcrCollectorRoute` registra el ViewModel en el `ViewModelStoreOwner` de `MainActivity`, por lo que tampoco ejecuta `onCleared()` al salir de la pantalla de recolección en `debug`.
4. **`SingleThreadExecutor` innecesario en `CameraPreview.kt:45`**:
   - `val analysisExecutor = remember { Executors.newSingleThreadExecutor() }` crea siempre un hilo nuevo al montar `CameraPreview`, incluso cuando se usa desde `OcrCaptureScreen.kt:240` donde `imageAnalyzer = null`.
5. **`Bitmap` sin reciclar tras rotación EXIF en `ProductConfirmationScreen.kt:851-854`**:
   - En `loadBitmapUpright`, cuando `rotationDegrees != 0f`, se crea un nuevo `Bitmap` rotado mediante `Bitmap.createBitmap(bitmap, ...)` sin liberar (`bitmap.recycle()`) el bitmap original decodificado en la línea 838.

---

### 6. TODO, placeholders, datos de ejemplo fuera de debug y código muerto

#### 6.1. TODO y Placeholders
1. **`ManualEntryPlaceholderScreen`** (`app/src/main/java/com/controlcal/app/MainActivity.kt:78, 425-437, 496-550` y `app/src/main/res/values/strings.xml:18-19`):
   - Pantalla placeholder en producción para toda la tarea `1.5` (*"Pantalla de alta manual (Próximamente en tarea 1.5)"*).
2. **Email placeholder en `User-Agent`** (`app/src/main/java/com/controlcal/app/data/off/OffClient.kt:42`):
   - `DEFAULT_USER_AGENT = "ControlCal/1.0 (cambiar@gmail.com)"`.

#### 6.2. Datos de ejemplo / valores inventados fuera de `debug`
1. **`DefaultGoalProfiles.kt`** (`core/src/main/kotlin/com/controlcal/core/model/DefaultGoalProfiles.kt:6-32`):
   - Perfiles por defecto hardcodeados en producción (`2400 kcal / 160P / 270C / 75G` para entrenamiento y `2000 kcal / 160P / 180C / 70G` para descanso, con `validFrom = "2020-01-01"`), devueltos por `GetActiveGoalProfileUseCase.kt:20` cuando la tabla `goal_profiles` está vacía.
2. **Peso base `70.0` kg en `WeightViewModel.kt:104`**:
   - Si el campo de peso está vacío y no hay pesajes previos, pulsar los botones rápidos `±0,1 / ±0,5 kg` parte de `70.0` kg.

#### 6.3. Código muerto
1. **En `:core`**:
   - `GetDayTotalsUseCase` (`core/src/main/kotlin/com/controlcal/core/nutrition/GetDayTotalsUseCase.kt:12-19`) y su proveedor `RepositoryModule.provideGetDayTotalsUseCase` (`app/src/main/java/com/controlcal/app/di/RepositoryModule.kt:65-66`): nunca inyectado en ningún ViewModel.
   - `DateHelper.getTodayUtcMillis` (`core/src/main/kotlin/com/controlcal/core/date/DateHelper.kt:51-53`): nunca llamado desde `:app`.
   - `NutritionLabelParser.hasUnitSuffix` (`core/src/main/kotlin/com/controlcal/core/ocr/NutritionLabelParser.kt:621-623`): función privada que no se llama ni dentro del propio `NutritionLabelParser`.
   - Propiedades `MealConfigError.message` (`MealConfigResult.kt:13-36`), `TdeeCalculationResult.InsufficientData.explanation` (`TdeeCalculationResult.kt:29`) y `TdeeGoalProposal.explanation` / `disclaimer` (`TdeeGoalProposal.kt:17-25`): nunca leídas en `:app`.
   - Métodos de interfaces de repositorio nunca invocados en producción:
     - `GoalProfileRepository.getProfilesForDayTypeUpTo` (`GoalProfileRepository.kt:9`)
     - `MealEntryRepository.getByProduct` (`MealEntryRepository.kt:31`)
     - `MealEntryRepository.insertAll` (`MealEntryRepository.kt:8`)
     - `ProductRepository.searchByName` y `search` (`ProductRepository.kt:8-9`)
     - `WeightEntryRepository.getBetween` (`WeightEntryRepository.kt:8`)
2. **En `:app`**:
   - Métodos de DAOs y `RepositoryImpl` nunca llamados en producción:
     - `ProductDao.update` (`ProductDao.kt:21`), `searchByName` (`línea 35`), `archive` (`línea 51`), `deleteById` (`línea 54`) y `ProductRepositoryImpl.searchByName`, `update`, `archive`, `hasEntries` (`ProductRepositoryImpl.kt:20, 48, 52, 55`).
     - `DailyLogDao.setWaterMl` (`DailyLogDao.kt:22`), `setNotes` (`línea 25`) y `DailyLogRepositoryImpl.insert`, `setWaterMl`, `setNotes` (`DailyLogRepositoryImpl.kt:17, 31, 34`).
     - `MealEntryDao.entriesForProduct` (`MealEntryDao.kt:30`) y `MealEntryRepositoryImpl.getByProduct` (`MealEntryRepositoryImpl.kt:129`).
     - `GoalProfileDao.profilesForDayTypeUpTo` (`GoalProfileDao.kt:28`) y `GoalProfileRepositoryImpl.getProfilesForDayTypeUpTo` (`GoalProfileRepositoryImpl.kt:21`).
     - `WeightEntryDao.observeAllDesc` (`WeightEntryDao.kt:26`) y `WeightEntryRepositoryImpl.getBetween` (`WeightEntryRepositoryImpl.kt:23`).
   - Campos de `UiState` y callbacks huérfanos tras la tarea `M.10`:
     - `DailyPanelUiState.message` (`DailyPanelUiState.kt:53`), `DailyPanelViewModel.onMessageConsumed()` (`DailyPanelViewModel.kt:671-673`) y `DailyPanelViewModel.onRepeatMeal` (`DailyPanelViewModel.kt:627-650`).
     - `MealDetailUiState.userMessage` (`MealDetailUiState.kt:36`) y `MealDetailViewModel.onUserMessageConsumed()` (`MealDetailViewModel.kt:713-715`).
     - `AddEntryUiState.isAddedSuccessfully` y `userMessage` (`AddEntryUiState.kt:28-29`), y `AddEntryViewModel.onUserMessageConsumed()` (`AddEntryViewModel.kt:434-436`).
     - `PantryUiState.snackbarMessage` (`PantryUiState.kt:23`) y `PantryViewModel.onSnackbarMessageConsumed()` (`PantryViewModel.kt:349-351`).
     - `ScannerUiState.feedbackMessage` (`ScannerUiState.kt:35`) y `ScannerViewModel.onFeedbackMessageConsumed()` (`ScannerViewModel.kt:317-319`).
     - `SettingsUiState.errorMessage` y `infoMessage` (`SettingsUiState.kt:34-35`), y `SettingsViewModel.onMessageConsumed()` (`SettingsViewModel.kt:492-494`).
     - `WeightUiState.message` (`WeightUiState.kt:41`) y `WeightViewModel.onMessageConsumed()` (`WeightViewModel.kt:420-422`).
     - `MacroTetrisUiState.message` y `MacroTetrisViewModel.onMessageConsumed()` (`MacroTetrisViewModel.kt:178-180`).
   - Recursos XML no usados:
     - `app/src/main/res/values/colors.xml:1-10` completo.
     - Más de 45 strings en `app/src/main/res/values/strings.xml` que no se referencian en ningún archivo `.kt` ni `.xml` porque sus textos se escribieron hardcodeados en los ViewModels.

---

### 7. Qué chocará con la Fase 6 (Chef local) y la Fase 7 (Health Connect y copia de seguridad)

#### 7.1. Choques con la Fase 6 (Chef local — `6.1` a `6.4`)
1. **`network_security_config.xml` bloqueará `ModelDownloader` (`6.2`)**:
   - `app/src/main/res/xml/network_security_config.xml:1-12` tiene `<base-config><trust-anchors /></base-config>` y solo autoriza `world.openfoodfacts.org`. Cualquier descarga del modelo `.litertlm` desde Hugging Face / Google Storage será rechazada por el sistema operativo hasta que se añada el dominio del host del modelo con aviso `REVISION HUMANA` (Norma 8 de `AGENTS.md`).
2. **El `OkHttpClient` singleton tiene `readTimeout = 5 s` (`NetworkModule.kt:46-52`)**:
   - `OffClient` requiere un timeout estricto de 5 s (`PLAN.md:84`), pero `ModelDownloader` (`6.2`) necesita descargar cientos de MB con soporte de reanudación (`Range`) y verificación SHA-256; si reutiliza el `OkHttpClient` tal cual o si tiene un `callTimeout` corto, las descargas se cortarán. Deberá crearse un cliente derivado con `okHttpClient.newBuilder()` específico para `ModelDownloader`.
3. **Liberación de memoria del motor LiteRT-LM al salir de la pantalla y ante `onTrimMemory` (`6.3`)**:
   - `PLAN.md:131` exige: *«El motor se libera al salir de la pantalla y cuando el sistema avisa de poca memoria»*.
   - Como `MainActivity.kt:89-98` mantiene todos los ViewModels vivos durante toda la vida de la Activity (ver **Hallazgo #7**), `onCleared()` no se ejecutará al salir de la pantalla del Chef. Además, `MainActivity` / `ControlCalApplication` no tienen aún callback `registerComponentCallbacks` / `onTrimMemory(level)` conectado al motor del modelo.
4. **Visibilidad privada del clasificador de alimentos para el Plan B de plantillas (`6.4`)**:
   - `PLAN.md:132, 211` exige plantillas de texto por tipo de plato en `:core` cuando no hay modelo. En `core/src/main/kotlin/com/controlcal/core/solver/FoodSelector.kt:170-182`, `enum class FoodCategory` y `fun classifyFood(product: Product)` son `private`.
5. **Conexión desde `MacroTetrisScreen` (`5.3` $\rightarrow$ `6.3`)**:
   - `MacroTetrisScreen.kt` y `MainActivity.kt:378-396` no tienen aún el punto de entrada para pasar la `MacroSolution` (lista cerrada de ingredientes y `roundedGrams`) a la pantalla/hoja del Chef local.

#### 7.2. Choques con la Fase 7 (Health Connect y copia de seguridad — `7.1` a `7.3`)
1. **Entradas negativas de modo tara (`1.7`) frente a `NutritionRecord` de Health Connect (`7.1`)**:
   - `PLAN.md:136` dice: *«Escribe NutritionRecord por entrada... con un identificador de cliente estable para poder actualizar y borrar»*.
   - En ControlCal, el modo tara (`CreateTareEntryUseCase.kt:23-39`) guarda una fila `MealEntry` independiente con `grams < 0` y `kcal < 0`, `protein < 0`, `carbs < 0`, `fat < 0`, y las calorías rápidas pueden tener macros en `0`.
   - En `androidx.health.connect.client.records.NutritionRecord`, los constructores `Energy.kilocalories(value)` y `Mass.grams(value)` **no admiten valores negativos** (lanzan `IllegalArgumentException`). Antes de escribir en Health Connect habrá que decidir si la tara descuenta sobre el `NutritionRecord` del alimento original en esa comida o cómo se representa sin enviar energías/masas negativas.
2. **Sincronización de borrados, «Deshacer» y fusiones (`M.5`, `M.9`) con Health Connect (`7.1`)**:
   - En `MealEntryRepositoryImpl.save` (`M.9`), al sumar dos altas del mismo alimento se actualiza la fila con el `id` original; si el usuario pulsa «Deshacer» en el snackbar (`MealDetailViewModel`, `AddEntryViewModel`, `DailyPanelViewModel`, `ScannerViewModel`), se revierte la fila con `mealEntryRepository.update(previousEntry)`. Si se borra una entrada en `MealDetailViewModel.onDeleteEntry`, puede restaurarse con «Deshacer» insertando un **nuevo `id`** (`mealEntryRepository.insert(entry.copy(id = 0))` en `MealDetailViewModel.kt:305`), lo que cambiará el `clientRecordId` si se basa en `entry.id`.
   - Del mismo modo, en `WeightEntryRepository.moveEntry(oldDate, newEntry)` (`M.1`), cambiar la fecha de un pesaje elimina `oldDate` e inserta `newDate`.
3. **Falta de UI de agua (`1.8`) y bug en `setWaterMl` para `HydrationRecord` (`7.1`)**:
   - `PLAN.md:136` exige escribir `HydrationRecord por agua`, pero la app no tiene controles de agua en UI ni funciona `DailyLogRepositoryImpl.setWaterMl` cuando no existe fila previa en `daily_logs` (ver **Hallazgo #4**).
4. **Lectura de `WeightRecord` (`ESTADO.md:62`) y falta de timestamp en `weight_entries`**:
   - `ESTADO.md:62` fijó que Health Connect también **lee** `WeightRecord`. La tabla `weight_entries` (`WeightEntryEntity.kt:12-17`) solo tiene `date` (PK) y `weight_kg`, sin instante de medición (`Instant` / `epochMillis`), por lo que si hay un pesaje manual y otro en Health Connect el mismo día no se puede saber cuál es más reciente sin migrar la tabla o definir una regla de prioridad.
5. **Ausencia de métodos en DAOs y repositorios para Exportar e Importar en transacción (`7.2`)**:
   - `PLAN.md:138-139` exige: *«Exportar: un único archivo con todos los datos en JSON, cifrado con AES-256-GCM... además de la base de datos, incluye la configuración de comidas y las preferencias que el usuario cambia en Ajustes. Nunca la clave de la base de datos ni el estado del dispositivo... Importar: valida versión y esquema antes de tocar la base de datos; restaura dentro de una transacción. Al importar, DataStore se escribe solo después de que la transacción de Room termine bien»*.
   - Actualmente:
     - Ningún DAO ni repositorio expone métodos `getAll()` para volcar **todas** las filas de `products` (incluyendo `archived = true`), `daily_logs`, `meal_entries`, `weight_entries` y `goal_profiles`.
     - Tampoco existen métodos `deleteAll()` / `insertAll()` en las 5 tablas para reemplazar los datos dentro de `database.withTransaction { ... }` respetando las claves foráneas (`meal_entries` referencia a `products(id)` y `daily_logs(date)` con `ON DELETE NO ACTION`, por lo que al restaurar hay que borrar primero `meal_entries`, luego `products` y `daily_logs`, e insertar primero `products` y `daily_logs` preservando sus `id` exactos antes de insertar `meal_entries`).
     - En `UserSettingsRepositoryImpl`, `lastTdeeProposalReviewedDate` y las futuras banderas de dispositivo (sincronización Health Connect, estado del modelo descargado) conviven en `controlcal_preferences`; el exportador deberá seleccionar explícitamente solo las claves de preferencias del usuario y comidas configuradas, y nunca tocar `controlcal_key_store`.

---

## Parte III — Cierre y resolución de hallazgos (2026-10-21)

Todos los hallazgos (`#1` a `#15`) han sido resueltos y verificados el 2026-10-21:
- **Hallazgo #1 (Tarea `1.5`)**: Eliminada `ManualEntryPlaceholderScreen`. Implementado el flujo completo de creación manual (`ProductSource.MANUAL`), edición nutricional de productos existentes y eliminación/archivado (`delete` si no tiene entradas en el diario, `archive` si tiene historial) en `ProductConfirmationViewModel`, `ProductConfirmationScreen`, `PantryScreen` y `MainActivity`.
- **Hallazgos #2 y #3 (`NutritionLabelParser` y `LabelValidator`)**: Eliminadas las heurísticas que inventaban valores no presentes en la etiqueta (deducción por resta de Atwater, 37 % fijo de grasas saturadas, `CARBS = 0.0` por `SUGARS == 0.0`) y las constantes mágicas sobreajustadas a fotos concretas. Actualizado `expected_labels.json` y `NutritionLabelParserParameterizedTest` para comprobar valores `null` reales de ML Kit, aproximaciones (`<0,5 g`) y confianza. Traducidos todos los mensajes de `LabelValidator` al español con coma decimal.
- **Hallazgo #4 (Tarea `1.8` Agua)**: Corregido `DailyLogRepositoryImpl.setWaterMl` y `setNotes` para crear la fila en `daily_logs` si aún no existe para esa fecha. Añadida la tarjeta `WaterIntakeCard` en `DailyPanelScreen` y la gestión de `waterMl` en `DailyPanelViewModel`.
- **Hallazgos #5, #6 y #7 (Macro Tetris, Hilo Principal y Recursos)**: Propagada la fecha seleccionada en el diario a `MacroTetrisViewModel.loadData` y `addToDiary`, añadido feedback de error en `addToDiary`, envuelto `FoodSelector.selectAndSolve` en `withContext(defaultDispatcher)`, movido el borrado de archivos temporales fuera del hilo principal y añadida la liberación explícita (`releaseResources()`) de `TextRecognizer` y `ExecutorService` al salir de `OcrCaptureScreen` y `OcrCollectorScreen`, reciclando además el `Bitmap` intermedio en `loadBitmapUpright` y evitando instanciar `analysisExecutor` en `CameraPreview` cuando `imageAnalyzer == null`.
- **Hallazgos #8, #9, #10, #11, #12, #13, #14 y #15**: Movida la lógica pura a `:core` (`OffParsingHelper`, `QuickLogRanker.rankHabitualsForMeal`, `TdeeProposalCalculator.applyProposalToProfile`, `WeeklyDayPattern.serialize/deserialize`), expuesto `FoodCategory` y `classifyFood` en `FoodSelector`, añadidos métodos `getAll`/`getAllIncludingArchived`/`deleteAll` en repositorios y DAOs para la Fase 7, añadido el anillo circular de calorías en `DailyPanelScreen` (`1.4`), selector de comida en `AddEntryScreen` (`1.6`), aviso de error en Modo Tara con comida vacía (`1.7`), corrección de unidades `ml`/`g` en `QuickLogChip` y `MealDetailScreen`, subtítulo en `ScannerScreen`, conversión `dp.toPx()` en `WeightTrendChart`, limpieza de `colors.xml` y `README.md`, y nuevos tests unitarios en `:core` (241 tests) y `:app` (260 tests).

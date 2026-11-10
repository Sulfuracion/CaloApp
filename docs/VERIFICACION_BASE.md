# Informe de Verificación del Estado Actual (Tarea 0)

> **Fecha de verificación:** 2026-11-10  
> **Estado general del proyecto:** En verde (522 tests unitarios pasando, 0 fallos, 0 errores de compilación, 0 errores de lint).

---

## 1. Ejecución y Anotación de Tareas Gradle

Se ejecutaron las cuatro verificaciones obligatorias exigidas por las normas del proyecto:

### 1.1 `./gradlew :core:test`
- **Resultado:** `BUILD SUCCESSFUL` (en 2.48 s).
- **Número de tests:** 245 tests ejecutados.
- **Fallos:** 0 fallos.
- **Omitidos:** 0 tests.
- **Tasa de éxito:** 100 %.
- **Cobertura principal:** Algoritmo Macro Tetris (`MacroSolver`, `FoodSelector`), parser y validador de OCR (`NutritionLabelParser`, `LabelValidator`), estimador de TDEE y tendencia EMA (`TdeeEstimator`, `WeightTrend`), motor de búsqueda local (`ProductSearchEngine`, `TextNormalizer`), casos de uso de nutrición y repetición (`RepeatMealUseCase`, `CopyDayUseCase`, `MealEntryMerger`), y validadores puros.

### 1.2 `./gradlew testDebugUnitTest`
- **Resultado:** `BUILD SUCCESSFUL` (en 26.08 s).
- **Número de tests:** 277 tests ejecutados.
- **Fallos:** 0 fallos.
- **Omitidos:** 0 tests.
- **Tasa de éxito:** 100 %.
- **Cobertura principal:** Tests de Room en memoria con Robolectric (`MealEntryDaoTest`, `ProductDaoTest`, `WeightEntryDaoTest`, `DailyLogDaoTest`), tests de migraciones de base de datos (`ControlCalDatabaseMigrationTest` cubriendo v1 -> v2 y v2 -> v3), repositorios con cifrado (`UserSettingsRepositoryImplTest`, `MealConfigRepositoryImplTest`), cliente Open Food Facts (`OffClientMappingTest`, `OffRateLimiterTest`), analizadores de cámara y gate de códigos de barras (`BarcodeConsecutiveGateTest`), y ViewModels de todas las pantallas (`DailyPanelViewModelTest`, `MealDetailViewModelTest`, `AddEntryViewModelTest`, `ScannerViewModelTest`, `ProductConfirmationViewModelTest`, `MacroTetrisViewModelTest`, `WeightViewModelTest`, `SettingsViewModelTest`, `PantryViewModelTest`).

### 1.3 `./gradlew lintDebug`
- **Resultado:** `BUILD SUCCESSFUL` (en 17 s).
- **Errores:** 0 errores.
- **Avisos (Warnings):** 135 avisos en total.
- **Avisos relevantes por categoría:**
  - **Correctness (Correctitud):**
    - `DefaultLocale` (4 avisos): En `SettingsScreen.kt` (líneas 649, 900, 901, 1000) se invoca `String.format("%02d:%02d", ...)` sin indicar `Locale` explícito (`Locale.ROOT` o `Locale.getDefault()`).
    - `ExifInterface` (4 avisos): En `ProductConfirmationScreen.kt` y `OcrCollectorViewModel.kt` se utiliza `android.media.ExifInterface` en lugar de la librería AndroidX `androidx.exifinterface.media.ExifInterface` (que contiene correcciones de compatibilidad para dispositivos antiguos y formatos específicos).
    - `EmptySuperCall` (2 avisos): En `OcrCaptureViewModel.kt` (línea 269) y `OcrCollectorViewModel.kt` (línea 256) se invoca `super.onCleared()`, método que en la clase base `ViewModel` está vacío.
    - `OldTargetApi` (1 aviso): `targetSdk` está fijado en 36, mientras que la versión actual de la plataforma en compileSdk es 37.2 (Android 17).
    - `AndroidGradlePluginVersion` (1 aviso) y `NewerVersionAvailable` (6 avisos): Versiones estables posteriores disponibles en repositorios de Google/Maven para bibliotecas del catálogo.
    - `ModifierParameter` (18 avisos): En diversas funciones `@Composable`, el parámetro `modifier: Modifier` no sigue estrictamente la guía de estilo de Compose (no es el primer parámetro opcional o no se encadena al nodo raíz).
  - **Performance (Rendimiento):**
    - `UnusedResources` (87 avisos): Recursos gráficos en `res/drawable/` y cadenas en `res/values/strings.xml` que no están siendo referenciados en el código activo.
    - `NotShrinkingResources` (1 aviso): En `app/build.gradle.kts` (línea 29), la variante `release` tiene `isMinifyEnabled = true`, pero no declara `isShrinkResources = true`.
    - `ObsoleteSdkInt` (1 aviso): Comprobación redundante de `Build.VERSION.SDK_INT >= 28` en un proyecto cuyo `minSdk` ya es 28.
  - **Productivity & Messages:**
    - `UseKtx` (2 avisos): En `AboutScreen.kt` se utiliza `Uri.parse(...)` en lugar de la extensión KTX `String.toUri()`.
    - `PluralsCandidate` (4 avisos) y `Typos` (3 avisos): Cadenas de texto con palabras compuestas o plurales directos en `strings.xml`.

### 1.4 `./gradlew assembleDebug`
- **Resultado:** `BUILD SUCCESSFUL` (en 16 s).
- **Artefacto generado:** APK compilado satisfactoriamente en `app/build/outputs/apk/debug/app-debug.apk`.

---

## 2. Comprobaciones en el Código

### 2.1 Garantía de fila previa en `daily_logs` al guardar en `meal_entries` (Clave Foránea `date`)

**Pregunta:** Al guardar una entrada en `meal_entries`, ¿se garantiza siempre que exista antes la fila de `daily_logs` de esa fecha (clave foránea `date`)? Revisa todos los caminos: alta normal, calorías rápidas, Macro Tetris, repetir comida, copiar día, modo tara y fusión con `MealEntryMerger`.

**Respuesta:** **Sí, se garantiza de manera estricta y universal en todos los caminos.**  
La restricción de integridad referencial está definida en el esquema de Room:

*Ruta de archivo:* [MealEntryEntity.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/data/db/entity/MealEntryEntity.kt#L21-L27)
```kotlin
foreignKeys = [
    ForeignKey(
        entity = DailyLogEntity::class,
        parentColumns = ["date"],
        childColumns = ["date"],
    ),
    ...
]
```

Para asegurar que nunca se produzca una violación de clave foránea en tiempo de ejecución, la capa de persistencia centraliza todo guardado en `MealEntryRepositoryImpl`, donde el método `ensureDailyLogExists` verifica e inserta la cabecera del día antes de cualquier operación de escritura:

*Ruta de archivo:* [MealEntryRepositoryImpl.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/data/repo/MealEntryRepositoryImpl.kt#L35-L63)
```kotlin
private suspend fun ensureDailyLogExists(date: String) {
    if (dailyLogDao.getByDate(date) == null) {
        val dayType = try {
            val localDate = LocalDate.parse(date)
            userSettingsRepository.getWeeklyPattern().dayTypeFor(localDate.dayOfWeek)
        } catch (_: Exception) {
            DayType.REST
        }
        dailyLogDao.insert(DailyLogEntity(date = date, dayType = dayType))
    }
}

override suspend fun save(entry: MealEntry): MealEntrySaveResult {
    return database.useWriterConnection { transactor ->
        transactor.immediateTransaction {
            ensureDailyLogExists(entry.date)
            val existing = mealEntryDao.entriesForDateAndMeal(entry.date, entry.meal).map { it.toDomain() }
            val candidate = MealEntryMerger.findMergeCandidate(existing, entry)
            if (candidate != null) {
                val merged = MealEntryMerger.merge(candidate, entry)
                mealEntryDao.update(merged.toEntity())
                MealEntrySaveResult.Merged(mergedEntry = merged, previousEntry = candidate)
            } else {
                val id = mealEntryDao.insert(entry.toEntity())
                MealEntrySaveResult.Created(entry.copy(id = id))
            }
        }
    }
}
```

Revisión exhaustiva camino por camino:

1. **Alta normal (Buscador / Confirmación / Escáner directo):**
   - Desde buscador / habituales: [AddEntryViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/entry/AddEntryViewModel.kt#L478) invoca `mealEntryRepository.save(entry)`.
   - Desde pantalla de confirmación: [ProductConfirmationViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationViewModel.kt#L506) invoca `mealEntryRepository.save(entry)`.
   - Desde escáner directo local: [ScannerViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/scanner/ScannerViewModel.kt#L240) invoca `mealEntryRepository.save(entry)`.
   - Desde sugerencia en panel diario: [DailyPanelViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/panel/DailyPanelViewModel.kt#L533) invoca `mealEntryRepository.save(entry)`.
2. **Calorías rápidas:**
   - Alta nueva: [MealDetailViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/detail/MealDetailViewModel.kt#L797) invoca `mealEntryRepository.save(newEntry)`.
   - Modificación: [MealDetailViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/detail/MealDetailViewModel.kt#L761) invoca `mealEntryRepository.update(updated)`, el cual en [MealEntryRepositoryImpl.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/data/repo/MealEntryRepositoryImpl.kt#L105) también llama primero a `ensureDailyLogExists(entry.date)`.
3. **Macro Tetris:**
   - Guardado en lote: [MacroTetrisViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/tetris/MacroTetrisViewModel.kt#L195) invoca `mealEntryRepository.saveAll(mealEntries)`, ejecutando `ensureDailyLogExists(entry.date)` por cada elemento dentro de una transacción atómica `transactor.immediateTransaction`.
4. **Repetir comida:**
   - En detalle de comida: [MealDetailViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/detail/MealDetailViewModel.kt#L558) invoca `mealEntryRepository.saveAll(cloned)`.
   - En panel diario: [DailyPanelViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/panel/DailyPanelViewModel.kt#L825) invoca `mealEntryRepository.saveAll(cloned)`.
5. **Copiar día:**
   - En detalle de comida: [MealDetailViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/detail/MealDetailViewModel.kt#L596) invoca `mealEntryRepository.saveAll(cloned)`.
   - En panel diario: [DailyPanelViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/panel/DailyPanelViewModel.kt#L844) invoca `mealEntryRepository.saveAll(cloned)`.
6. **Modo tara:**
   - Restar sobras crea una entrada con gramos negativos (`createTareEntryUseCase`) y [MealDetailViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/detail/MealDetailViewModel.kt#L477) invoca `mealEntryRepository.save(tareEntry)`.
7. **Fusión con `MealEntryMerger`:**
   - La resolución de fusión se realiza exclusivamente dentro de `MealEntryRepositoryImpl.save` y `saveAll` (`findMergeCandidate` y `merge`), ejecutándose **después** de asegurar la existencia de `daily_logs` mediante `ensureDailyLogExists(entry.date)`.

---

### 2.2 Interacción de `MealEntryMerger` con el modo tara (Entradas Negativas)

**Pregunta:** ¿Cómo interactúa `MealEntryMerger` con las entradas negativas del modo tara? ¿Puede una tara fusionarse con la entrada positiva y dejarla en cero o en negativo?

**Respuesta:** **No interactúa de ninguna forma de fusión y es imposible que una tara se fusione con una entrada positiva.**  
El motor de dominio `MealEntryMerger` en `:core` prohíbe explícitamente la fusión si alguna de las dos entradas tiene gramos menores o iguales a cero:

*Ruta de archivo:* [MealEntryMerger.kt](file:///c:/Users/david/Downloads/CaloApp/core/src/main/kotlin/com/controlcal/core/nutrition/MealEntryMerger.kt#L30-L40)
```kotlin
object MealEntryMerger {

    fun canMerge(existing: MealEntry, incoming: MealEntry): Boolean {
        return existing.date == incoming.date &&
            existing.meal == incoming.meal &&
            existing.productId != null &&
            incoming.productId != null &&
            existing.productId == incoming.productId &&
            existing.grams > 0.0 &&
            incoming.grams > 0.0
    }
    ...
```

Por su parte, el caso de uso del modo tara construye entradas donde `grams` es estrictamente negativo:

*Ruta de archivo:* [CreateTareEntryUseCase.kt](file:///c:/Users/david/Downloads/CaloApp/core/src/main/kotlin/com/controlcal/core/nutrition/CreateTareEntryUseCase.kt#L29-L44)
```kotlin
require(leftoverGrams > 0.0) { "Leftover grams must be strictly positive" }

val portion = NutritionCalculator.calculatePortionMacros(product, -leftoverGrams)

return MealEntry(
    id = 0L,
    date = date,
    meal = meal,
    productId = product.id,
    productName = product.name,
    grams = -leftoverGrams,
    ...
```

**Consecuencias de diseño:**
1. Al evaluar `canMerge(existing, incoming)`, la condición `incoming.grams > 0.0` (o `existing.grams > 0.0`) resulta en `false`.
2. Una entrada de tara nunca encuentra candidato de fusión; siempre se crea como una fila separada e independiente en la tabla `meal_entries` con `grams < 0` (`MealEntrySaveResult.Created`).
3. La entrada positiva original permanece intacta en la base de datos (conservando su historial, identificador y valores nutricionales).
4. La visualización de totales por comida y por día suma algebraicamente las filas y aplica la regla de no negatividad fijada en la sección 4 de `PLAN.md`:

*Ruta de archivo:* [NutritionCalculator.kt](file:///c:/Users/david/Downloads/CaloApp/core/src/main/kotlin/com/controlcal/core/nutrition/NutritionCalculator.kt#L49-L55)
```kotlin
return NutrientTotals(
    kcal = max(0.0, kcalSum),
    protein = max(0.0, proteinSum),
    carbs = max(0.0, carbsSum),
    fat = max(0.0, fatSum),
)
```
Por tanto, el total nunca se mostrará por debajo de cero, incluso si los gramos de tara superasen a los registrados previamente.

---

### 2.3 ViewModels en `MainActivity` y Retención de Recursos Pesados

**Pregunta:** ¿Cuántos ViewModels se crean a nivel de Activity en `MainActivity`? ¿Alguno retiene recursos pesados (cámara, ML Kit, executors) mientras no se usa su pantalla?

**Respuesta:** En `MainActivity` se declaran exactamente **10 ViewModels** a nivel de Activity mediante el delegado `by viewModels()`:

*Ruta de archivo:* [MainActivity.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/MainActivity.kt#L62-L71)
```kotlin
private val dailyPanelViewModel: DailyPanelViewModel by viewModels()
private val mealDetailViewModel: MealDetailViewModel by viewModels()
private val addEntryViewModel: AddEntryViewModel by viewModels()
private val scannerViewModel: ScannerViewModel by viewModels()
private val confirmationViewModel: ProductConfirmationViewModel by viewModels()
private val ocrCaptureViewModel: OcrCaptureViewModel by viewModels()
private val weightViewModel: WeightViewModel by viewModels()
private val settingsViewModel: SettingsViewModel by viewModels()
private val pantryViewModel: PantryViewModel by viewModels()
private val macroTetrisViewModel: MacroTetrisViewModel by viewModels()
```

*(Nota: En la variante debug existe un undécimo ViewModel, `OcrCollectorViewModel`, pero este no se declara como propiedad de la Activity, sino que se obtiene dinámicamente mediante `ViewModelProvider` dentro del composable `OcrCollectorRoute`).*

**Análisis de retención de recursos pesados:**  
**Ningún ViewModel retiene la cámara, instancias activas de ML Kit o executors en segundo plano mientras su pantalla no está visible.** La liberación está garantizada por los siguientes mecanismos:

1. **Cámara (CameraX):** Ni `ScannerViewModel` ni `OcrCaptureViewModel` almacenan objetos de CameraX. El enlace de la cámara vive en el componente UI composable `CameraPreview`:
   *Ruta de archivo:* [CameraPreview.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/camera/CameraPreview.kt#L63-L74)
   ```kotlin
   DisposableEffect(lifecycleOwner) {
       onDispose {
           try {
               activeProvider?.unbindAll()
           } catch (_: Throwable) {}
           activeProvider = null
           camera = null
           analysisExecutor?.shutdown()
       }
   }
   ```
   En cuanto el usuario sale de la pantalla de escaneo o de captura OCR, la composición se descarta y el proveedor de cámara se desvincula por completo (`unbindAll()`).

2. **ML Kit Barcode Scanning:** `ScannerViewModel` solo almacena strings y estado de interfaz. El objeto `BarcodeAnalyzer` se instancia localmente en la función composable `ScannerRoute` y se cierra al salir:
   *Ruta de archivo:* [ScannerScreen.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/scanner/ScannerScreen.kt#L104-L108)
   ```kotlin
   DisposableEffect(barcodeAnalyzer) {
       onDispose {
           barcodeAnalyzer.close()
       }
   }
   ```

3. **ML Kit Text Recognition y Executor Service en OCR:** `OcrCaptureViewModel` gestiona bajo demanda su reconocedor (`_recognizer`) y su hilo de ejecución (`_cameraExecutor`). Dado que el ViewModel vive en el scope de la Activity, se implementó una liberación explícita ligada al ciclo de vida composable de `OcrCaptureRoute`:
   *Ruta de archivo:* [OcrCaptureScreen.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/ocr/OcrCaptureScreen.kt#L80-L84)
   ```kotlin
   DisposableEffect(Unit) {
       onDispose {
           viewModel.releaseResources()
       }
   }
   ```
   Donde `releaseResources()` en [OcrCaptureViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/ocr/OcrCaptureViewModel.kt#L66-L73) ejecuta `_recognizer?.close()`, `_cameraExecutor?.shutdown()` y los establece a `null`.

4. **Archivos temporales:** En `ProductConfirmationViewModel`, las fotos temporales (`photoFile`) se eliminan del almacenamiento de caché tras confirmar el producto o al pulsar atrás mediante `cleanupTempPhoto()` invocado desde `MainActivity.kt` (línea 115).

**Observación arquitectónica:** Aunque los recursos de hardware y ML Kit se liberan correctamente, mantener 10 ViewModels a nivel de Activity implica que sus instancias, sus corrutinas de `viewModelScope` y sus estados en memoria (`uiState`, listas de productos y cachés en memoria) no se destruyen mientras la aplicación está en ejecución.

---

### 2.4 Existencia de datos del usuario (Sexo, Edad, Fecha de Nacimiento o Altura)

**Pregunta:** ¿Existen ya en el proyecto datos del usuario como sexo, edad, fecha de nacimiento o altura? ¿Dónde?

**Respuesta:** **No, no existen en ninguna parte del proyecto.**  
No hay ninguna columna, campo, entidad, preferencia ni modelo de dominio que contenga o solicite sexo, género, edad, fecha de nacimiento ni altura del usuario.

**Inspección del código:**
1. **Base de datos Room (`ControlCalDatabase`):**
   - Tabla `products`: nutrientes, raciones, código de barras y metadatos.
   - Tabla `daily_logs`: `date`, `day_type`, `water_ml`, `notes`, `steps`.
   - Tabla `meal_entries`: `id`, `date`, `meal`, `product_id`, `product_name`, `grams`, `kcal`, `protein`, `carbs`, `fat`, `logged_at`.
   - Tabla `weight_entries`: `date`, `weight_kg`.
   - Tabla `goal_profiles`: `id`, `day_type`, `kcal`, `protein_g`, `carbs_g`, `fat_g`, `valid_from`, `origin`.
2. **Preferencias DataStore:**
   *Ruta de archivo:* [UserSettingsRepositoryImpl.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/data/repo/UserSettingsRepositoryImpl.kt#L21-L24)
   - `default_weekly_pattern`: asignación de días de entreno vs descanso (lunes a domingo).
   - `min_kcal_limit`: suelo de seguridad calórica (por defecto 1200 kcal).
   - `last_tdee_proposal_reviewed_date`: fecha de la última propuesta semanal revisada.
   - `health_connect_steps_sync_enabled`: flag de sincronización de pasos.
   - `controlcal_key_store`: clave cifrada de la base de datos SQLCipher.
3. **Módulo de cálculo energético (`TdeeEstimator`):**
   *Ruta de archivo:* [TdeeEstimator.kt](file:///c:/Users/david/Downloads/CaloApp/core/src/main/kotlin/com/controlcal/core/tdee/TdeeEstimator.kt#L7-L13)
   - La app no utiliza fórmulas antropométricas teóricas (como Harris-Benedict, Mifflin-St Jeor o Katch-McArdle) que requerirían sexo, edad o altura. En su lugar, el TDEE es 100 % adaptativo y empírico: calcula el gasto energético observando la ingesta calórica media de 21 días frente a la variación real de la tendencia del peso corporal ($TDEE = \bar{I} - \Delta\text{trend} \times 7700 / 21$).

---

### 2.5 Funcionamiento de `MainNavigationState` y Pasos para Añadir un Nuevo Destino

**Pregunta:** ¿Cómo funciona `MainNavigationState` y qué hay que tocar exactamente para añadir un destino nuevo?

**Respuesta:**

#### ¿Cómo funciona?
`MainNavigationState` es un controlador de navegación personalizado, ligero y sin dependencias de Jetpack Navigation Compose, implementado sobre `rememberSaveable` con un `Saver` que resiste la recreación de la Activity y la muerte del proceso en segundo plano:

*Ruta de archivo:* [MainNavigationState.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/MainNavigationState.kt#L19-L33)
```kotlin
private var destinationStack by mutableStateOf(
    initialBackStack.ifEmpty { listOf(initialDestination) },
)

var currentDestination: MainDestination
    get() = destinationStack.last()
    set(value) {
        if (value == currentDestination) return
        val existingIndex = destinationStack.indexOfLast { it == value }
        destinationStack = if (existingIndex >= 0) {
            destinationStack.take(existingIndex + 1)
        } else {
            destinationStack + value
        }
    }
```

- **Manejo de la pila (`destinationStack`):** Funciona con semántica de *single-top / pop-to*. Al asignar un nuevo destino a `currentDestination`, si este ya se encontraba en el historial, la pila se trunca hasta dicho índice (`take(existingIndex + 1)`), eliminando las pantallas superiores y evitando pantallas duplicadas o bucles infinitos al navegar hacia atrás.
- **Persistencia de contexto:** Almacena variables de paso efímeras pero necesarias entre pantallas: `activeMealContext` (`date`, `mealId`), `manualEntryBarcode` y el flag `returnToPantryAfterConfirmation`.
- **Serialización (`mainNavigationStateSaver`):** Convierte la pila en una cadena de texto separada por barras verticales (`DAILY_PANEL|MEAL_DETAIL|ADD_ENTRY`) y los argumentos contextuales en una lista serializable, restaurándola en `restore`.

#### ¿Qué hay que tocar exactamente para añadir un destino nuevo?
Para incorporar un nuevo destino (por ejemplo `NUEVO_DESTINO`), deben seguirse estos pasos concretos:

1. **Declarar el destino en el enum:**  
   En [MainActivity.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/MainActivity.kt#L44-L57), añadir `NUEVO_DESTINO` a `enum class MainDestination`.
2. **Actualizar el estado de navegación (si requiere argumentos propios):**  
   Si el nuevo destino requiere propiedades de contexto específicas entre pantallas (similar a `manualEntryBarcode`), añadirlas a la clase `MainNavigationState` y a las funciones `save` y `restore` de `mainNavigationStateSaver` en [MainNavigationState.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/MainNavigationState.kt#L43-L79). Si no requiere argumentos adicionales, no hace falta modificar `MainNavigationState.kt` ya que `restore` mapea dinámicamente sobre `MainDestination.entries`.
3. **Definir la navegación hacia atrás (`navigateBack`):**  
   En [MainActivity.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/MainActivity.kt#L85-L154), dentro del bloque lambda `val navigateBack: () -> Unit = { when (currentDestination) { ... } }`, añadir la rama `MainDestination.NUEVO_DESTINO -> { ... }` especificando cuál es el destino de retorno y qué recarga de datos o limpieza de estado debe efectuarse.
4. **Renderizar la pantalla en el árbol de Compose:**  
   En [MainActivity.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/MainActivity.kt#L160-L510), dentro del bloque `when (currentDestination)`, añadir la rama correspondiente para instanciar la ruta:
   ```kotlin
   MainDestination.NUEVO_DESTINO -> {
       NuevoDestinoRoute(
           viewModel = nuevoDestinoViewModel,
           onBack = navigateBack,
       )
   }
   ```
5. **Crear o inyectar el ViewModel (si aplica):**  
   Si el nuevo destino utiliza su propio ViewModel, declararlo en `MainActivity` mediante `private val nuevoDestinoViewModel: NuevoDestinoViewModel by viewModels()` (según la convención actual del proyecto) o dentro del propio composable.
6. **Conectar el enlace de navegación en la pantalla de origen:**  
   En el composable de la pantalla que abre este nuevo flujo, añadir la lambda de evento que ejecute `currentDestination = MainDestination.NUEVO_DESTINO`.
7. **Pruebas unitarias:**  
   En [MainNavigationStateTest.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/test/java/com/controlcal/app/MainNavigationStateTest.kt), verificar que las transiciones hacia y desde el nuevo destino preservan la consistencia de la pila.

---

### 2.6 Valores de `ProductSource`, Mappers y `when`

**Pregunta:** ¿Qué valores de `ProductSource` existen y en qué `when` o mappers se usan?

**Respuesta:**

#### Valores de `ProductSource`:
Definido en el módulo `:core`:

*Ruta de archivo:* [Enums.kt](file:///c:/Users/david/Downloads/CaloApp/core/src/main/kotlin/com/controlcal/core/model/Enums.kt#L9-L14)
```kotlin
/** Where a product came from. */
enum class ProductSource {
    MANUAL,
    OFF,
    OCR,
}
```
1. `MANUAL`: Producto dado de alta manualmente por el usuario.
2. `OFF`: Producto obtenido a través del cliente de red de Open Food Facts (por código de barras o búsqueda por nombre).
3. `OCR`: Producto extraído mediante escaneo fotográfico de tabla nutricional con ML Kit.

#### Mappers donde se utiliza:
1. **De `OffProduct` a Dominio:**  
   [OffModels.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/data/off/OffModels.kt#L90): `OffProduct.toDomainProduct(...)` fija `source = ProductSource.OFF`.
2. **De `OffProduct` a Estado UI de Confirmación:**  
   [ProductConfirmationUiState.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationUiState.kt#L117): `OffProduct.toConfirmationUiState(...)` fija `source = ProductSource.OFF`.
3. **De OCR a Estado UI de Confirmación:**  
   [ProductConfirmationUiState.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationUiState.kt#L172): `toConfirmationUiStateFromOcr(...)` fija `source = ProductSource.OCR`.
4. **De Alta Manual a Estado UI de Confirmación:**  
   [ProductConfirmationUiState.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationUiState.kt#L210): `toManualConfirmationUiState(...)` fija `source = ProductSource.MANUAL`.
5. **De Edición a Estado UI de Confirmación:**  
   [ProductConfirmationUiState.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationUiState.kt#L244): `Product.toEditConfirmationUiState()` preserva el valor original con `source = source`.
6. **Mapeo entre Room (`ProductEntity`) y Dominio (`Product`):**  
   [Mappers.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/data/repo/Mappers.kt#L35-L61): `ProductEntity.toDomain()` y `Product.toEntity()` asignan el enum de forma directa.
7. **Mapeo de Room a SQLite (KSP generado):**  
   En `ProductDao_Impl.kt`, Room genera los conversores de columna:
   ```kotlin
   __ProductSource_enumToString(ProductSource) -> "MANUAL" | "OFF" | "OCR"
   __ProductSource_stringToEnum(String) -> ProductSource.MANUAL | ProductSource.OFF | ProductSource.OCR
   ```

#### Estructuras `when` y condicionales donde se evalúa:
1. **Título de la pantalla de confirmación:**  
   *Ruta de archivo:* [ProductConfirmationScreen.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationScreen.kt#L149-L153)
   ```kotlin
   val titleRes = when {
       uiState.editingProductId != null -> R.string.edit_product_title
       uiState.source == ProductSource.MANUAL -> R.string.manual_entry_title
       else -> R.string.confirm_product_title
   }
   ```
2. **Botón de atajo a captura OCR desde alta manual:**  
   *Ruta de archivo:* [ProductConfirmationScreen.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationScreen.kt#L184)
   ```kotlin
   if (uiState.source == ProductSource.MANUAL && uiState.editingProductId == null) {
       OutlinedButton(
           onClick = { onNavigateToOcrCapture(uiState.barcode.trim().ifEmpty { null }) },
           modifier = Modifier.fillMaxWidth()
       ) {
           Text(text = stringResource(R.string.ocr_capture_action_from_manual))
       }
   }
   ```
3. **Insignia visual de tabla nutricional OCR:**  
   *Ruta de archivo:* [ProductConfirmationScreen.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationScreen.kt#L194)
   ```kotlin
   if (uiState.source == ProductSource.OCR) {
       Surface(...) {
           Text(text = stringResource(R.string.confirm_product_source_ocr_badge), ...)
       }
   }
   ```
4. **Revalidación de reglas Atwater exclusiva para OCR:**  
   *Ruta de archivo:* [ProductConfirmationViewModel.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationViewModel.kt#L312-L314)
   ```kotlin
   private fun revalidateOcr(state: ProductConfirmationUiState): ProductConfirmationUiState {
       if (state.source != ProductSource.OCR) return state
       ...
   ```

---

## 3. Lista de Problemas Encontrados Ordenados por Gravedad

A continuación se detalla el inventario completo de observaciones y problemas identificados durante la verificación técnica:

### Gravedad Alta
*No se encontraron problemas de gravedad alta.*  
La integridad referencial de base de datos (Room sobre SQLCipher), las migraciones de esquema numeradas (v1 -> v2 -> v3), el cifrado en reposo, el aislamiento de red (solo HTTPS a Open Food Facts), la ausencia de telemetría y el 100 % de los 522 tests unitarios se encuentran en verde.

---

### Gravedad Media

1. **Gestión de ciclo de vida de ViewModels a nivel de Activity (`MainActivity.kt`):**  
   - *Ubicación:* [MainActivity.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/MainActivity.kt#L62-L71)
   - *Descripción:* Los 10 ViewModels se instancian con `by viewModels()` vinculados al ciclo de vida de `MainActivity`. Aunque los recursos de hardware (cámara, analizador y text recognizer) se liberan mediante `DisposableEffect`, los 10 ViewModels persisten en memoria durante toda la sesión. Esto provoca que sus `viewModelScope`, estados de interfaz (`StateFlow`) y estructuras cacheadas (por ejemplo, la lista de productos activos cacheados en `AddEntryViewModel`) no se destruyan al salir de sus pantallas, pudiendo presentar estados obsoletos si no se re-inicializan explícitamente al volver a entrar.

2. **Ausencia de `isShrinkResources` con R8 activo en release (`app/build.gradle.kts`):**  
   - *Ubicación:* [app/build.gradle.kts](file:///c:/Users/david/Downloads/CaloApp/app/build.gradle.kts#L29)
   - *Descripción:* Lint reporta la advertencia `NotShrinkingResources`: al tener `isMinifyEnabled = true` en la variante `release`, no se ha activado `isShrinkResources = true`. En un release para producción, esto impide que R8 elimine recursos gráficos, XML y strings sin uso, aumentando innecesariamente el tamaño del APK.

3. **Uso de `android.media.ExifInterface` en lugar de la biblioteca AndroidX (`ProductConfirmationScreen.kt`):**  
   - *Ubicación:* [ProductConfirmationScreen.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationScreen.kt#L707) y `OcrCollectorViewModel.kt`
   - *Descripción:* Lint reporta 4 advertencias `ExifInterface`. La versión de la plataforma Android de `ExifInterface` adolece de errores históricos de lectura de etiquetas de orientación en fotografías JPEG en diversas versiones de Android y capas de personalización. Se recomienda emplear `androidx.exifinterface.media.ExifInterface`.

4. **Uso de `DefaultLocale` en formateo de tiempo (`SettingsScreen.kt`):**  
   - *Ubicación:* [SettingsScreen.kt](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/settings/SettingsScreen.kt#L649)
   - *Descripción:* Lint reporta 4 avisos `DefaultLocale` por invocaciones como `String.format("%02d:%02d", ...)`. En ciertos dispositivos configurados con locales como turco o árabe, el formateo sin `Locale.ROOT` o `Locale.getDefault()` puede generar caracteres inesperados en la representación de dígitos.

---

### Gravedad Baja

1. **87 recursos no utilizados en `res/` (`strings.xml` y `drawables`):**  
   - *Ubicación:* `app/src/main/res/values/strings.xml` y `app/src/main/res/drawable/`
   - *Descripción:* Tras las revisiones y consolidaciones de las fases M y 7, han quedado 87 cadenas e identificadores que no están referenciados por ninguna pantalla (identificados por la regla `UnusedResources` de Lint).

2. **Desalineación de `targetSdk` frente a `compileSdk` (`OldTargetApi`):**  
   - *Ubicación:* `app/build.gradle.kts`
   - *Descripción:* Lint reporta `OldTargetApi` porque `targetSdk = 36` mientras que `compileSdk = 37.2`. Ya está documentado como decisión abierta en `docs/ESTADO.md`.

3. **18 advertencias de diseño de parámetros `Modifier` en Jetpack Compose (`ModifierParameter`):**  
   - *Ubicación:* Múltiples pantallas composables (`DailyPanelScreen.kt`, `MealDetailScreen.kt`, `AddEntryScreen.kt`, `WeightScreen.kt`, etc.).
   - *Descripción:* Ciertas funciones `@Composable` internas no posicionan `modifier: Modifier = Modifier` como primer parámetro opcional o no lo encadenan al elemento visual contenedor.

4. **Llamadas redundantes a `super.onCleared()` (`EmptySuperCall`):**  
   - *Ubicación:* [OcrCaptureViewModel.kt:269](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/ocr/OcrCaptureViewModel.kt#L269) y [OcrCollectorViewModel.kt:256](file:///c:/Users/david/Downloads/CaloApp/app/src/debug/java/com/controlcal/app/ui/debug/OcrCollectorViewModel.kt#L256)
   - *Descripción:* `ViewModel.onCleared()` es un método intencionalmente vacío anotado con `@EmptySuper`. La llamada a `super.onCleared()` es redundante.

5. **Comprobación de versión de SDK obsoleta (`ObsoleteSdkInt`):**  
   - *Ubicación:* Comprobación en código de `Build.VERSION.SDK_INT >= 28` que siempre evalúa a `true` al ser `minSdk 28`.

6. **2 llamadas a `Uri.parse(...)` en lugar de la extensión KTX (`UseKtx`):**  
   - *Ubicación:* [AboutScreen.kt:125](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/about/AboutScreen.kt#L125) y [AboutScreen.kt:161](file:///c:/Users/david/Downloads/CaloApp/app/src/main/java/com/controlcal/app/ui/about/AboutScreen.kt#L161).
   - *Descripción:* Se aconseja utilizar `String.toUri()` de `core-ktx`.

7. **Avisos tipográficos y de plurales en recursos de texto:**  
   - *Ubicación:* `strings.xml`
   - *Descripción:* Lint señala 4 avisos `PluralsCandidate` por el uso de cadenas separadas para singular/plural y 3 avisos `Typos` menores en comentarios/nombres.

# Informe de revisión de seguridad y datos (Fases 0 a 5 y Fase M)

Fecha: 2026-10-18  
Alcance: Código fuente, configuración Gradle, manifiestos (fuente y fusionado), esquemas Room (`1.json`, `2.json`) y tests de `:core` y `:app` tras completar las fases 0 a 5 y M.1 a M.10.

---

## Parte I — Hallazgos numerados y ordenados por importancia

Cada hallazgo se clasifica como **FALLO** (incumplimiento directo de `docs/PLAN.md` o `AGENTS.md`, o defecto funcional de seguridad/datos), **RIESGO** (vulnerabilidad potencial, fragilidad ante fallos o exposición indirecta) o **MEJORA** (higiene técnica, limpieza de dependencias o corrección documental).

---

### Hallazgo #1 — **[FALLO]** `android:allowBackup="true"` y reglas de extracción/backup sin exclusiones permiten extraer la base de datos, DataStore y la clave envuelta

- **Ubicación**:
  - `app/src/main/AndroidManifest.xml:14-16`
  - `app/src/main/res/xml/data_extraction_rules.xml:6-19`
  - `app/src/main/res/xml/backup_rules.xml:8-13`
- **Referencia incumplida**: Sección 6 de `docs/PLAN.md` (*«`android:allowBackup="false"` y reglas de extracción de datos vacías»*) y Sección 5.8 (*«Nunca la clave de la base de datos [en copias]»*). Tarea 0.4 pendiente.
- **Análisis técnico**:
  1. `AndroidManifest.xml:14` declara `android:allowBackup="true"`.
  2. `res/xml/backup_rules.xml` tiene `<full-backup-content>` vacío (con las etiquetas comentadas) y `res/xml/data_extraction_rules.xml` tiene `<cloud-backup>` sin reglas `<exclude>` y `<device-transfer>` comentado. En Android, un bloque `<full-backup-content>` o `<cloud-backup>` vacío sin exclusiones **incluye por defecto todos los directorios internos de la app** (`files/`, `databases/`, `datastore/`, `shared_prefs/`).
  3. Como resultado, tanto la copia en la nube de Google como la transferencia entre dispositivos extraen `controlcal.db` y `datastore/controlcal_preferences.preferences_pb` (que contiene `"wrapped_db_key"`, ajustes y configuración de comidas).
  4. Además, al restaurar en otro dispositivo donde la clave del Android Keystore (`controlcal_db_wrap_key`) no existe, el descifrado falla con `KeyWrapException` y `DatabaseKeyResolver` (`DatabaseKeyResolver.kt:27-32`) borra la base de datos restaurada.

---

### Hallazgo #2 — **[FALLO]** Ausencia de `network_security_config.xml` permite conexiones HTTPS a cualquier dominio (incluyendo telemetría transitiva de ML Kit)

- **Ubicación**:
  - `app/src/main/AndroidManifest.xml:12-21` (falta `android:networkSecurityConfig`)
  - `app/src/main/res/xml/` (no existe `network_security_config.xml`)
  - `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml:24, 147-162`
- **Referencia incumplida**: Sección 6 de `docs/PLAN.md` (*«Configuración de seguridad de red: sin tráfico en claro; dominios permitidos: Open Food Facts y el host de descarga del modelo»*) y Regla 3 de `AGENTS.md` (*«Red: solo HTTPS y solo desde OffClient y ModelDownloader. Ningún otro archivo abre conexiones. Sin analíticas ni SDK de terceros»*). Tarea 0.4 pendiente.
- **Análisis técnico**:
  1. Aunque `minSdk = 28` bloquea tráfico HTTP en claro por defecto en el sistema y `OffClientImpl` verifica `startsWith("https://")` en `OffClient.kt:48`, no existe restricción a nivel de sistema (`network_security_config.xml`) sobre a qué dominios puede conectarse el proceso.
  2. En el manifiesto fusionado (`processDebugMainManifest/AndroidManifest.xml:147-162`) se observa que las librerías empaquetadas de ML Kit (`com.google.mlkit:barcode-scanning:17.3.0` y `com.google.mlkit:text-recognition:16.0.1`) inyectan transitivamente el permiso `android.permission.ACCESS_NETWORK_STATE` (línea 24) y los servicios de `com.google.android.datatransport` (`TransportBackendDiscovery`, `CctBackendFactory`, `JobInfoSchedulerService`, `AlarmManagerSchedulerBroadcastReceiver`).
  3. Dichos componentes de `datatransport` están diseñados para enviar telemetría de uso y rendimiento de ML Kit a servidores de Google (`firebaselogging-pa.googleapis.com`). Sin un `network_security_config.xml` cuyo `<base-config>` tenga `<trust-anchors>` vacío y que solo confíe en certificados del sistema dentro de `<domain-config>` para `world.openfoodfacts.org` (y sin eliminar los servicios de `datatransport` con `tools:node="remove"`), el SDK de ML Kit puede abrir conexiones HTTPS hacia servidores de Google fuera de `OffClient`.

---

### Hallazgo #3 — **[FALLO]** Fotografías temporales de OCR sin cifrar quedan huérfanas en `cacheDir` en varios flujos de salida y error

- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/ui/ocr/OcrCaptureViewModel.kt:121, 186-213`
  - `app/src/main/java/com/controlcal/app/MainActivity.kt:140-152, 181-183`
  - `app/src/main/java/com/controlcal/app/ui/ocr/OcrCaptureScreen.kt:381-411`
- **Referencia incumplida**: Sección 10 de `docs/PLAN.md` (`PLAN.md:228`: *«Qué no debes hacer: Guardar datos fuera de la base de datos cifrada y de DataStore»*) y criterio de tarea 3.4 (`ESTADO.md:81`: *«borrado inmediato del archivo temporal de la foto al confirmar o descartar»*).
- **Análisis técnico**:
  `OcrCaptureViewModel.takePhoto` (`OcrCaptureViewModel.kt:121`) guarda cada foto en texto claro en `File(context.cacheDir, "ocr_temp_${System.currentTimeMillis()}.jpg")`. Existen cuatro rutas donde el archivo **no se borra**:
  1. **Botón o gesto «Atrás» del sistema en `ProductConfirmationScreen`**: En `MainActivity.kt:181-183`, `BackHandler` invoca `navigateBack()`. En la rama `MainDestination.PRODUCT_CONFIRMATION` (`MainActivity.kt:140-152`), se limpia `selectedOcrData = null` y se cambia de pantalla **sin llamar a `confirmationViewModel.onDiscard(...)`**, dejando la foto en `cacheDir`. (Solo el botón de la barra superior y el botón «Descartar» pasan por `viewModel.onDiscard` en `ProductConfirmationScreen.kt:123`).
  2. **Error de reconocimiento en ML Kit o excepción de lectura**: En `OcrCaptureViewModel.kt:196-213`, si `getRecognizer().process(inputImage)` entra en `addOnFailureListener` (línea 196) o en `catch (e: Exception)` (línea 205), se pasa a `OcrProcessingState.Error` sin borrar `photoFile`. Al cerrar el diálogo con `onDismissError()` (`OcrCaptureViewModel.kt:116-118`), tampoco se elimina.
  3. **Salida con «Atrás» del sistema durante el diálogo `NoDataDetected`**: Si el OCR no detecta tabla (`OcrCaptureViewModel.kt:187`) y el usuario pulsa «Atrás» del sistema (`MainActivity.kt:137-139`) en lugar de los botones del diálogo, `photoFile` no se borra.
  4. **Cierre del proceso por el sistema**: No existe limpieza de archivos `ocr_temp_*.jpg` residuales en `cacheDir` al iniciar la aplicación.

---

### Hallazgo #4 — **[FALLO]** Pérdida o corrupción de `DataStore` deja la base de datos `controlcal.db` permanentemente inaccesible al no invocar `onKeyLost()` cuando `stored == null`

- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/data/security/DatabaseKeyResolver.kt:22-35`
  - `app/src/main/java/com/controlcal/app/di/DatabaseModule.kt:43-46`
- **Referencia**: Ciclo de vida de la clave de SQLCipher y recuperación ante pérdida de clave (`DatabaseKeyResolver.kt:10-13`).
- **Análisis técnico**:
  1. `DatabaseModule.provideDataStore` (`DatabaseModule.kt:44-46`) configura `ReplaceFileCorruptionHandler { emptyPreferences() }`. Si el archivo `controlcal_preferences.preferences_pb` (que se modifica frecuentemente al cambiar ajustes, patrón semanal, límite de kcal, fecha de revisión TDEE o comidas configuradas) sufre corrupción por un corte de energía o fallo de E/S, DataStore lo reemplaza por preferencias vacías (`emptyPreferences()`), borrando la entrada `"wrapped_db_key"`.
  2. En el siguiente arranque, `DatabaseKeyResolver.resolve()` (`DatabaseKeyResolver.kt:23-35`) lee `val stored = keyStore.read()`, que devuelve `null`.
  3. `DatabaseKeyResolver` solo llama a `onKeyLost()` (que ejecuta `DatabaseFiles.deleteAll()`) cuando `stored != null` y falla `unwrap(stored)`. Cuando `stored == null`, asume que es el primer arranque limpio y ejecuta directamente `createAndStore()`, generando una **nueva clave aleatoria** sin borrar el archivo `controlcal.db` existente.
  4. A continuación, SQLCipher intenta abrir el `controlcal.db` antiguo (cifrado con la clave anterior) usando la clave nueva, lanzando `SQLiteException` en cada arranque y dejando la app inutilizable.

---

### Hallazgo #5 — **[RIESGO]** Co-ubicación de `"wrapped_db_key"` y preferencias de usuario en el mismo `DataStore<Preferences>` (`controlcal_preferences`)

- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/di/DatabaseModule.kt:39-51`
  - `app/src/main/java/com/controlcal/app/data/security/WrappedKeyStore.kt:28-32`
  - `app/src/main/java/com/controlcal/app/data/repo/UserSettingsRepositoryImpl.kt:16-23`
  - `app/src/main/java/com/controlcal/app/data/repo/MealConfigRepositoryImpl.kt:20-26`
- **Referencia**: Sección 5.8 de `docs/PLAN.md` (`PLAN.md:138`: *«Copia de seguridad: además de la base de datos, incluye la configuración de comidas y las preferencias que el usuario cambia en Ajustes. Nunca la clave de la base de datos»*).
- **Análisis técnico**:
  `DataStoreWrappedKeyStore`, `UserSettingsRepositoryImpl` y `MealConfigRepositoryImpl` inyectan el mismo `DataStore<Preferences>` (`"controlcal_preferences"`). Esto implica dos riesgos:
  1. En la futura tarea 7.2 (exportar/importar copia de seguridad), si se serializa o restaura el mapa del `DataStore` de preferencias sin filtrar explícitamente `"wrapped_db_key"`, se exportaría la clave envuelta en el archivo de backup o se machacaría la clave local al importar.
  2. Cualquier escritura frecuente de ajustes o comidas reescribe el mismo archivo `.preferences_pb` que almacena la clave envuelta de la base de datos (agravando el Hallazgo #4).

---

### Hallazgo #6 — **[RIESGO]** Compilación `release` tiene R8 desactivado (`isMinifyEnabled = false`) y `proguard-rules.pro` vacío

- **Ubicación**:
  - `app/build.gradle.kts:27-35`
  - `app/proguard-rules.pro:1-21`
- **Referencia**: Sección 6 de `docs/PLAN.md` (`PLAN.md:148`: *«Release con R8 y sin logs de depuración»*).
- **Análisis técnico**:
  En `app/build.gradle.kts:29`, el bloque `buildTypes.release` tiene `isMinifyEnabled = false` y `proguard-rules.pro` no contiene reglas específicas ni eliminación de trazas de depuración. Aunque la tarea 8.x contempla el cierre de R8 para publicación, actualmente el artefacto `release` no aplica ofuscación, reducción de código ni limpieza de dependencias transitivas no usadas.

---

### Hallazgo #7 — **[RIESGO]** La clave en claro generada en `DatabaseKeyResolver.createAndStore()` no se limpia en caso de excepción al envolver o persistir en `DataStore`

- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/data/security/DatabaseKeyResolver.kt:37-41`
  - `app/src/main/java/com/controlcal/app/data/db/ControlCalDatabaseProvider.kt:45-53`
- **Referencia**: Sección 6 de `docs/PLAN.md` y comentario de diseño en `DbKeyManager.kt:11-12` (*«the plain bytes live only in memory while the database opens, and [wipe] zeroes them afterwards»*).
- **Análisis técnico**:
  En `DatabaseKeyResolver.createAndStore()`:
  ```kotlin
  private fun createAndStore(): ByteArray {
      val key = keyManager.generateKey()
      keyStore.write(keyManager.wrap(key))
      return key
  }
  ```
  Si `keyManager.wrap(key)` o `keyStore.write(...)` lanza una excepción (por ejemplo, fallo del Keystore o `IOException` en `runBlocking` de `DataStore.edit`), el `ByteArray` `key` con los 32 bytes en claro no pasa por `keyManager.wipe(key)` antes de propagar la excepción, quedando en el heap hasta ser recolectado por el GC.

---

### Hallazgo #8 — **[RIESGO]** Email provisional `cambiar@gmail.com` en el `User-Agent` de `OffClient`

- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/data/off/OffClient.kt:42`
- **Referencia**: Sección 5.2 de `docs/PLAN.md` (`PLAN.md:81, 85`) y problemas abiertos en `docs/ESTADO.md:60, 141`.
- **Análisis técnico**:
  `DEFAULT_USER_AGENT = "ControlCal/1.0 (cambiar@gmail.com)"` se envía en todas las peticiones HTTPS a Open Food Facts (`OffClient.kt:62, 118`). Usar una dirección de correo ajena o ficticia incumple la política de uso de la API de Open Food Facts (riesgo de bloqueo del `User-Agent`) y expone a que cualquier aviso administrativo de OFF se envíe al titular real de esa cuenta en Gmail.

---

### Hallazgo #9 — **[RIESGO]** Doble inicialización en `ProductConfirmationRoute` sobrescribe `targetDate` y `addToDiary` al abrir la confirmación desde una comida

- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/MainActivity.kt:261, 304, 337-343`
  - `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationScreen.kt:94-100`
- **Referencia**: Integridad de datos del diario (Sección 5.1 de `docs/PLAN.md`, propagación de fecha y comida de destino en tareas M.4 y M.8).
- **Análisis técnico**:
  En `MainActivity.kt:261, 304, 337`, antes de navegar a `PRODUCT_CONFIRMATION`, se llama a `confirmationViewModel.initialize(offProduct, ctx?.date, ctx?.mealId)` o `initializeFromOcr(..., targetDate = ctx?.date, targetMeal = ctx?.mealId)`, pasando la fecha y comida seleccionadas por el usuario. Sin embargo, al componerse `ProductConfirmationRoute` (`ProductConfirmationScreen.kt:94-100`), su `LaunchedEffect(offProduct, ocrData)` vuelve a invocar:
  ```kotlin
  if (ocrData != null) {
      viewModel.initializeFromOcr(ocrData.photoFile, ocrData.parsedNutrients, ocrData.barcode)
  } else if (offProduct != null) {
      viewModel.initialize(offProduct)
  }
  ```
  sin pasar `targetDate` ni `targetMeal`. Como consecuencia, `targetDate` se sobrescribe con `LocalDate.now(clock)` (`ProductConfirmationViewModel.kt:64, 85`) y `addToDiary` se restablece a `false` (`ProductConfirmationViewModel.kt:70`). Si el usuario estaba editando un día pasado en el diario y activa «Añadir al diario», la entrada se guarda en **hoy** en lugar de en la fecha seleccionada.

---

### Hallazgo #10 — **[RIESGO]** Fallos de inserción en `ProductConfirmationViewModel` y `AddEntryViewModel` (ej. `UNIQUE` en `barcode`) se silencian sin avisar al usuario

- **Ubicación**:
  - `app/src/main/java/com/controlcal/app/ui/confirm/ProductConfirmationViewModel.kt:357-399`
  - `app/src/main/java/com/controlcal/app/ui/entry/AddEntryViewModel.kt:398-400, 418-420`
- **Referencia**: Sección 5.1 de `docs/PLAN.md` (`PLAN.md:72`: *«Todo formulario responde al guardar: confirmación visible, campo vacío o diálogo cerrado, y errores junto al campo»*) y tarea M.10 (`ESTADO.md:102`: *«En caso de error de persistencia, se emite un snackbar de error»*).
- **Análisis técnico**:
  1. En `ProductConfirmationViewModel.onConfirmAndSave` (`ProductConfirmationViewModel.kt:357`), `productRepository.insert(product)` ejecuta `@Insert` sobre `products`. Si el usuario introduce o confirma un código de barras que ya existe en la tabla `products` (por ejemplo, de un producto archivado o editado en el campo `barcode`), SQLite lanza `SQLiteConstraintException` por el índice único `index_products_barcode`.
  2. El bloque `catch (e: Exception)` en `ProductConfirmationViewModel.kt:397-399` solo hace `_uiState.update { it.copy(isSaving = false) }` sin emitir ningún evento de error ni mostrar mensaje en el campo `barcode`. Lo mismo ocurre en `AddEntryViewModel.kt:398-400` y `418-420`.
  3. Además, en `ProductConfirmationViewModel.kt:357-385`, `productRepository.insert(product)` y `mealEntryRepository.save(entry)` no se ejecutan dentro de una misma transacción.

---

### Hallazgo #11 — **[MEJORA]** Seis dependencias de `androidTest` / `debug` / Compose Preview declaradas en Gradle no se usan e inyectan Activities exportadas en el APK de `debug`

- **Ubicación**:
  - `app/build.gradle.kts:80, 112-117`
  - `gradle/libs.versions.toml:8, 34, 40-43`
  - `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml:50-52, 117-120`
- **Referencia**: Sección 6 de `docs/PLAN.md` (*«Componentes no exportados salvo el Activity principal»*), Sección 9 (*«No hagas tests de interfaz salvo que se pida»*) y Punto 5 de esta revisión.
- **Análisis técnico**:
  1. `debugImplementation(libs.androidx.compose.ui.test.manifest)` (`app/build.gradle.kts:116`) y `debugImplementation(libs.androidx.compose.ui.tooling)` (`app/build.gradle.kts:117`) inyectan en el manifiesto fusionado de `debug` dos Activities con `android:exported="true"`: `androidx.activity.ComponentActivity` (líneas 117-120) y `androidx.compose.ui.tooling.PreviewActivity` (líneas 50-52).
  2. En todo el código fuente no existe ningún `@Preview` ni ningún test de UI de Compose.
  3. Las dependencias `androidx-espresso-core`, `androidx-compose-ui-test-junit4`, `androidx-compose-ui-test-manifest`, `androidx-compose-ui-tooling` y `androidx-compose-ui-tooling-preview` sobran.

---

### Hallazgo #12 — **[MEJORA]** `kotlinx-serialization-json` está declarada como `implementation` en `:core` cuando el parseo de JSON solo se realiza en `core/src/test`

- **Ubicación**:
  - `core/build.gradle.kts:3, 18`
  - `core/src/main/kotlin/com/controlcal/core/ocr/model/OcrModels.kt:3`
  - `core/src/test/kotlin/com/controlcal/core/ocr/OcrFixtureLoader.kt:5`
- **Análisis técnico**:
  En `core/src/main`, `kotlinx.serialization` solo se utiliza para anotar con `@Serializable` las clases de `OcrModels.kt` (`OcrBox`, `OcrLine`, `OcrLabelMetadata`, `ExpectedNutritionValues`). De ellas, `OcrLabelMetadata` y `ExpectedNutritionValues` solo se deserializan en `core/src/test` (`OcrFixtureLoader.kt`), ya que el recolector de `:app` en `debug` define sus propios DTOs en `app/src/debug/java/com/controlcal/app/ui/debug/OcrLabelModels.kt`. Si esos dos modelos de fixture viven en `core/src/test`, `:core` en producción (`src/main`) no necesita el runtime de `kotlinx-serialization-json`.

---

### Hallazgo #13 — **[MEJORA]** Inconsistencia en `docs/ESTADO.md` sobre `ON DELETE CASCADE` (el esquema real usa `ON DELETE NO ACTION`) y omisión de versiones en la tabla de versiones fijadas

- **Ubicación**:
  - `docs/ESTADO.md:95, 117-137`
  - `app/schemas/com.controlcal.app.data.db.ControlCalDatabase/1.json:203, 299-322`
  - `app/schemas/com.controlcal.app.data.db.ControlCalDatabase/2.json:203, 299-322`
  - `app/src/main/java/com/controlcal/app/data/db/Migrations.kt:12-13, 42-43`
- **Análisis técnico**:
  1. `docs/ESTADO.md:95` afirma que la tarea M.6 preservó *«la clave foránea a `products(id)` con `ON DELETE CASCADE`»*. En realidad, tanto `MealEntryEntity.kt:21-32` como `1.json`, `2.json` y `Migrations.kt` definen `ON DELETE NO ACTION` (que es el comportamiento correcto para **impedir** que se borre el historial).
  2. En la sección `## Versiones fijadas` de `docs/ESTADO.md:117-137` faltan librerías presentes en `gradle/libs.versions.toml`: `sqlcipher-android` (`4.19.1`), `androidx.sqlite:sqlite` (`2.7.1`), `androidx.datastore:datastore-preferences` (`1.2.1`), `junit` (`4.13.2`), `androidx.test.ext:junit` (`1.3.0`) y `espresso-core` (`3.7.0`).

---

### Hallazgo #14 — **[MEJORA]** Bug de precedencia de operadores en `DatabaseFilesTest.kt` impide verificar el borrado del archivo principal `controlcal.db`

- **Ubicación**:
  - `app/src/test/java/com/controlcal/app/data/db/DatabaseFilesTest.kt:22`
- **Análisis técnico**:
  En la línea 22:
  ```kotlin
  listOf(base.absolutePath) + sideFiles.forEach { File(it).apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3)) }
  ```
  En Kotlin, `.forEach` se evalúa sobre `sideFiles` y devuelve `Unit`, por lo que `listOf(base.absolutePath) + Unit` crea una lista temporal descartada y **nunca crea el archivo `base` (`controlcal.db`)** antes de llamar a `files.deleteAll()`. Por tanto, `assertFalse(File(base.absolutePath).exists())` en la línea 26 pasa trivialmente porque el archivo nunca existió. Debería ser `(listOf(base.absolutePath) + sideFiles).forEach { ... }`.

---

### Hallazgo #15 — **[MEJORA]** Los esquemas de Room (`app/schemas`) se incluyen en los `assets` del APK de `debug` y existen atributos redundantes en `AndroidManifest.xml`

- **Ubicación**:
  - `app/build.gradle.kts:62-64`
  - `app/src/main/AndroidManifest.xml:25-26`
- **Análisis técnico**:
  1. `app/build.gradle.kts:62-64` añade `$projectDir/schemas` a `sourceSets.getByName("debug").assets`. Como `ControlCalDatabaseMigrationTest` se ejecuta en `testDebugUnitTest` (que usa el sourceSet `test` de las líneas 59-61), incluir `schemas` en `debug` empaqueta `1.json` y `2.json` dentro del APK de `debug` innecesariamente.
  2. En `AndroidManifest.xml:25-26`, `<activity android:name=".MainActivity">` repite `android:label="@string/app_name"` y `android:theme="@style/Theme.ControlCal"`, ya declarados en `<application>` (`AndroidManifest.xml:18, 21`).

---

## Parte II — Desarrollo detallado de los 5 puntos solicitados

---

### 1. Manifest, permisos y configuración de red frente a la sección 6 de `PLAN.md`

| Requisito de la Sección 6 (`docs/PLAN.md`) | Estado actual en el proyecto | Veredicto |
| --- | --- | --- |
| `android:allowBackup="false"` y reglas de extracción vacías | `AndroidManifest.xml:14` tiene `android:allowBackup="true"`. `res/xml/backup_rules.xml` y `res/xml/data_extraction_rules.xml` tienen sus bloques vacíos/comentados (lo que en Android incluye todos los archivos en el backup). | **FALLO** (Hallazgo #1) |
| Permisos: `CAMERA` e `INTERNET`. Ninguno de almacenamiento, ubicación ni contactos | `AndroidManifest.xml:5-7` declara `CAMERA`, `VIBRATE` e `INTERNET`. No hay permisos de almacenamiento, ubicación ni contactos. `VIBRATE` fue aprobado en la tarea 2.2. Además, ML Kit añade transitivamente `ACCESS_NETWORK_STATE` en el manifest fusionado (`processDebugMainManifest/AndroidManifest.xml:24`). | **MEJORA** (Hallazgos #2 y #15) |
| Configuración de seguridad de red: sin tráfico en claro; dominios permitidos: Open Food Facts y host del modelo | No existe `res/xml/network_security_config.xml` ni el atributo `android:networkSecurityConfig` en `AndroidManifest.xml`. Cualquier dominio HTTPS es alcanzable a nivel de SO. | **FALLO** (Hallazgo #2) |
| Componentes no exportados salvo el Activity principal | En `src/main/AndroidManifest.xml:22-32`, solo `MainActivity` tiene `android:exported="true"`. En el manifest fusionado de `debug`, `ui-test-manifest` y `ui-tooling` exportan `ComponentActivity` y `PreviewActivity`, y `profileinstaller` exporta `ProfileInstallReceiver` (protegido con permiso de sistema `android.permission.DUMP`). | **MEJORA** (Hallazgo #11) |
| Release con R8 y sin logs de depuración | En `app/build.gradle.kts:29`, `isMinifyEnabled = false` en `release`. En el código propio no hay llamadas a `Log.*` y SQLCipher silencia su logger con `Logger.setTarget(NoopTarget())` (`ControlCalDatabaseProvider.kt:43`). | **RIESGO** (Hallazgo #6) |
| Sin secretos en el repositorio ni claves de API | Verificado. No hay claves ni secretos en el árbol de código ni en commits; `.gitignore` bloquea `*.jks`, `*.keystore`, `*.key`, `*.pem`, `*.db*` y `local.properties`. | **CUMPLIDO** |

---

### 2. Ciclo de vida de la clave de SQLCipher y Keystore

#### Trazabilidad paso a paso
1. **Generación (`DbKeyManager.kt:20`)**:
   - En el primer arranque, `DbKeyManager.generateKey()` genera 32 bytes aleatorios (256 bits) mediante `java.security.SecureRandom`.
2. **Envoltura con Android Keystore (`KeyCipher.kt:32-70`)**:
   - `KeystoreKeyCipher` genera o recupera una clave simétrica `AES/GCM/NoPadding` de 256 bits no exportable en el proveedor `"AndroidKeyStore"` bajo el alias `"controlcal_db_wrap_key"` (`KeyCipher.kt:54-69`).
   - `encrypt(plain)` cifra los 32 bytes con un IV aleatorio de 12 bytes generado por el Keystore y etiqueta de autenticación GCM de 128 bits, devolviendo `IV || ciphertext || tag` (60 bytes).
3. **Almacenamiento cifrado (`WrappedKeyStore.kt:28-50`)**:
   - `DataStoreWrappedKeyStore` codifica el blob cifrado (`IV || ciphertext || tag`) en Base64 (`NO_WRAP`) y lo guarda bajo la clave `"wrapped_db_key"` en `DataStore<Preferences>` (`controlcal_preferences.preferences_pb`). La clave en claro **nunca** se escribe en disco.
4. **Apertura de SQLCipher y borrado en memoria (`ControlCalDatabaseProvider.kt:40-53`)**:
   - Se silencia el logger interno de SQLCipher: `Logger.setTarget(NoopTarget())` (línea 43).
   - Se obtiene `val key = keyResolver.resolve()` (línea 45), se clona para el driver (`val driverKey = key.copyOf()`, línea 46) y se sobrescribe con ceros el array original (`keyManager.wipe(key)` -> `Arrays.fill(key, 0)`, línea 47).
   - Se activa `PRAGMA cipher_memory_security = ON` en cada conexión SQLCipher mediante `CipherMemorySecurityHook` (`ControlCalDatabaseProvider.kt:60-66`).

#### Verificación frente a logs, repositorio y copias
- **Logs**: Ni la clave en claro ni la clave envuelta ni los alias o excepciones vuelcan bytes de clave en logs. No existe ninguna llamada a `Log.*`, `println` ni `printStackTrace` en el paquete `com.controlcal.app.data.security` ni en `ControlCalDatabaseProvider.kt`.
- **Repositorio**: No existe ningún archivo `.db`, `.preferences_pb` ni clave en el repositorio Git.
- **Copias y recuperación**:
  - **En copias del sistema**: Debido al **Hallazgo #1** (`allowBackup="true"`), Android Auto Backup incluye `controlcal_preferences.preferences_pb` (con `"wrapped_db_key"`).
  - **En futuras copias de la app (Fase 7.2)** y **recuperación ante corrupción**: Debido a los **Hallazgos #4 y #5**, compartir el mismo `DataStore` (`controlcal_preferences`) entre `"wrapped_db_key"` y las preferencias de usuario expone la clave a ser exportada por error en la fase 7.2 o a perderse silenciosamente si actúa el `ReplaceFileCorruptionHandler`, dejando `controlcal.db` bloqueada porque `DatabaseKeyResolver.resolve()` no ejecuta `onKeyLost()` cuando `stored == null`.
  - **En memoria ante fallo de escritura**: Ver **Hallazgo #7** (`createAndStore()` no ejecuta `wipe(key)` si falla `wrap` o `write`).

---

### 3. Esquema de Room exportado, migración 1 a 2 (M.6) y análisis de `ON DELETE CASCADE`

#### 3.1. Comparativa del esquema exportado (`1.json` y `2.json`) frente a la Sección 4 de `PLAN.md`

| Elemento de Sección 4 (`docs/PLAN.md`) | Esquema exportado (`1.json` / `2.json`) | Estado |
| --- | --- | --- |
| `products`: 23 columnas (`id` PK, `barcode` UNIQUE NULL, `name`, `brand` NULL, 4 macros obligatorios, 4 opcionales NULL, `is_liquid`, `unit_name`, `unit_g`, `serving_g`, `min_g`, `max_g`, `in_pantry`, `source`, `archived`, `created_at`, `updated_at`) | Coincidencia exacta de las 23 columnas, tipos SQLite (`INTEGER`, `TEXT`, `REAL`) y nulabilidad en `1.json:8-165` y `2.json:8-165`. | **CORRECTO** |
| Índices de `products`: `products(name)` y un único índice `UNIQUE` en `barcode` | `index_products_barcode` (`UNIQUE` sobre `barcode`) e `index_products_name` (sobre `name`). No hay índices duplicados sobre `barcode`. | **CORRECTO** |
| `daily_logs`: `date` PK, `day_type`, `water_ml`, `notes` NULL | Coincidencia exacta en `1.json:167-200` y `2.json:167-200`. | **CORRECTO** |
| `meal_entries`: `id` PK, `date` FK, `meal` TEXT, `product_id` FK (`NOT NULL` en v1, `NULL` desde v2 / M.6), `product_name`, `grams`, `kcal`, `protein`, `carbs`, `fat`, `logged_at` | Coincidencia exacta en `1.json:202-323` (`notNull: true` en `product_id`) y `2.json:202-322` (`product_id` nullable). | **CORRECTO** |
| Índices de `meal_entries`: `meal_entries(date)` y `meal_entries(product_id, logged_at)` | `index_meal_entries_date` y `index_meal_entries_product_id_logged_at` presentes en `1.json:278-298` y `2.json:277-297`. | **CORRECTO** |
| `weight_entries`: `date` PK, `weight_kg` | Coincidencia exacta en `1.json:325-347` y `2.json:324-346`. | **CORRECTO** |
| `goal_profiles`: `id` PK, `day_type`, `kcal`, `protein_g`, `carbs_g`, `fat_g`, `valid_from`, `origin` | Coincidencia exacta en `1.json:349-407` y `2.json:348-406`. | **CORRECTO** |
| Prohibido `fallbackToDestructiveMigration` (Regla 6) | `ControlCalDatabaseProvider.kt:49-52` no usa `fallbackToDestructiveMigration`; registra explícitamente `.addMigrations(MIGRATION_1_2)`. | **CORRECTO** |

#### 3.2. Revisión de la migración 1 a 2 (`MIGRATION_1_2` en `Migrations.kt`)
- **Implementación (`app/src/main/java/com/controlcal/app/data/db/Migrations.kt:18-68`)**:
  - Sobrescribe tanto `migrate(db: SupportSQLiteDatabase)` como `migrate(connection: SQLiteConnection)`, garantizando compatibilidad tanto con `MigrationTestHelper` en tests como con `SQLCipherDriver` en producción.
  - Sigue el procedimiento estándar de SQLite para cambiar la nulabilidad de una columna (`product_id INTEGER` sin `NOT NULL`):
    1. Crea `meal_entries_new` con la definición exacta de v2.
    2. Copia todas las filas (`id`, `date`, `meal`, `product_id`, `product_name`, `grams`, `kcal`, `protein`, `carbs`, `fat`, `logged_at`).
    3. Elimina `meal_entries` y renombra `meal_entries_new` a `meal_entries`.
    4. Recrea los dos índices (`index_meal_entries_date` e `index_meal_entries_product_id_logged_at`).
- **Test (`app/src/test/java/com/controlcal/app/data/db/ControlCalDatabaseMigrationTest.kt:28-153`)**:
  - Valida el esquema migrado contra `2.json` mediante `helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)`.
  - Comprueba que el número de filas y la suma de `kcal`, `protein`, `carbs` y `fat` por día son idénticos antes y después de migrar, que se pueden insertar entradas con `product_id = NULL` (calorías rápidas) y que un `product_id` inexistente (`99999`) lanza `SQLiteConstraintException`.

#### 3.3. ¿La clave foránea con `ON DELETE CASCADE` puede borrar historial?
- **Estado real en el código y esquemas (`1.json`, `2.json`, `MealEntryEntity.kt`, `Migrations.kt`)**:
  - Pese a lo que afirma erróneamente `docs/ESTADO.md:95` (Hallazgo #13), **el esquema real NO tiene `ON DELETE CASCADE`**.
  - Tanto en `MealEntryEntity.kt:21-32` (donde `onDelete` usa el valor por defecto `ForeignKey.NO_ACTION`), como en `1.json:300-321`, `2.json:299-321` y `Migrations.kt:42-43`, ambas claves foráneas están declaradas como:
    ```sql
    FOREIGN KEY(`date`) REFERENCES `daily_logs`(`date`) ON UPDATE NO ACTION ON DELETE NO ACTION,
    FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
    ```
- **Evaluación de riesgo de borrado de historial**:
  1. Con la configuración actual (`ON DELETE NO ACTION`), **la clave foránea NO puede borrar historial**. Al contrario, actúa como barrera de integridad: si cualquier código intentara ejecutar un `DELETE FROM products` o `DELETE FROM daily_logs` para un producto o día que tenga entradas en `meal_entries`, SQLite abortaría la operación con `SQLiteConstraintException`.
  2. Además, `ProductDao` (`ProductDao.kt:50-51`) carece de método `DELETE` y solo expone `archive(id: Long)` (`UPDATE products SET archived = 1 WHERE id = :id`), y `DailyLogDao` (`DailyLogDao.kt:10-26`) tampoco tiene ningún método `DELETE`.
  3. **Advertencia importante**: Si se cambiara en el futuro a `ON DELETE CASCADE` (siguiendo el texto erróneo de `ESTADO.md:95`), borrar una fila de `products` o de `daily_logs` **sí destruiría silenciosamente todo el historial de `meal_entries` asociado**, violando la Sección 4 de `docs/PLAN.md` (`PLAN.md:51-52`). Por tanto, debe mantenerse `ON DELETE NO ACTION` en el código y corregirse únicamente la nota en `docs/ESTADO.md:95`.

---

### 4. Llamadas de red fuera de `OffClient` y logs con datos del usuario (Reglas 3 y 4)

#### 4.1. Auditoría de llamadas de red (Regla 3: *«Red: solo HTTPS y solo desde OffClient y ModelDownloader. Ningún otro archivo abre conexiones. Sin analíticas ni SDK de terceros»*)

| Archivo y línea | Código / Componente | Análisis frente a Regla 3 |
| --- | --- | --- |
| `app/src/main/java/com/controlcal/app/data/off/OffClient.kt:40-41, 48-50` | `DEFAULT_BASE_URL` y `DEFAULT_SEARCH_BASE_URL` en `https://world.openfoodfacts.org/...` con `require(baseUrl.startsWith("https://") && searchBaseUrl.startsWith("https://"))` | **Cumple**: único cliente HTTP del código propio, restringido a HTTPS. |
| `app/src/main/java/com/controlcal/app/data/off/OffClient.kt:67, 123` | `okHttpClient.newCall(request).execute()` en `getProductByBarcode` y `searchProductsByName` | **Cumple**: únicas llamadas de red en todo el código fuente del proyecto. |
| `app/src/main/java/com/controlcal/app/di/NetworkModule.kt:46-52` | `provideOkHttpClient()` con timeouts de 5 s | **Cumple**: solo se inyecta en `OffClientImpl`. |
| `app/src/main/java/com/controlcal/app/ui/about/AboutScreen.kt:114-118, 150-154` | `Intent(Intent.ACTION_VIEW, Uri.parse("https://world.openfoodfacts.org"))` y `Uri.parse("https://opendatacommons.org/licenses/odbl/")` | **Cumple**: no abre sockets desde la app; delega al navegador del sistema tras toque explícito del usuario para cumplir la atribución ODbL (Sección 5.2). |
| `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml:24, 147-162` | `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery` (`CctBackendFactory`), `JobInfoSchedulerService`, `AlarmManagerSchedulerBroadcastReceiver` (inyectados por ML Kit) | **Incumplimiento potencial (Hallazgo #2)**: SDK transitivo de ML Kit con capacidad de enviar telemetría por red mientras no se bloquee mediante `network_security_config.xml` y/o `tools:node="remove"` en `AndroidManifest.xml`. |

#### 4.2. Auditoría de logs con datos del usuario (Regla 4: *«Nunca registres en logs datos del usuario (comidas, peso)»*)

Se han inspeccionado todos los archivos `.kt` de `app/src/main`, `app/src/debug`, `app/src/release`, `app/src/test`, `app/src/androidTest`, `core/src/main` y `core/src/test` buscando `android.util.Log`, `println`, `print(`, `printStackTrace`, `System.out`, `System.err`, `java.util.logging.Logger` y `net.zetetic.database.Logger`:

| Archivo y línea | Llamada encontrada | ¿Contiene datos del usuario? | Evaluación |
| --- | --- | --- | --- |
| `app/src/main/java/com/controlcal/app/data/db/ControlCalDatabaseProvider.kt:43` | `Logger.setTarget(NoopTarget())` | No (desactiva por completo los logs internos de SQLCipher para evitar que se impriman sentencias SQL). | **Cumple** |
| `app/src/main/**`, `app/src/debug/**`, `app/src/release/**`, `core/src/main/**` | *(Ninguna llamada a `Log.*`, `println`, `print`, `printStackTrace`, `System.out` ni `System.err`)* | No. | **Cumple al 100 %** |
| `core/src/test/kotlin/com/controlcal/core/search/ProductSearchEngineTest.kt:226` | `println("ProductSearchEngine search time for 1,000 products: $elapsed ms")` | No (solo tiempo de ejecución de un test unitario en JVM con datos sintéticos). | **Cumple** |
| `core/src/test/kotlin/com/controlcal/core/solver/MacroTetrisTest.kt:301` | `println("Performance Macro Tetris (30 productos): ${elapsedMs}ms")` | No (solo tiempo de ejecución de un test unitario en JVM). | **Cumple** |

> [!NOTE]
> Aunque no hay ningún log con datos del usuario, el **Hallazgo #3** señala que las fotos temporales de etiquetas nutricionales tomadas con la cámara (`OcrCaptureViewModel.kt:121`) pueden quedar sin borrar en `context.cacheDir` si el usuario sale con el botón «Atrás» del sistema o si falla el OCR.

---

### 5. Dependencias: lista completa y análisis de cuáles sobran

#### 5.1. Plugins declarados (`gradle/libs.versions.toml:62-68` y `settings.gradle.kts:15`)

| Plugin | Versión | Módulos donde se aplica | ¿Necesario? |
| --- | --- | --- | --- |
| `com.android.application` | `9.2.1` | `:app` | Sí |
| `org.jetbrains.kotlin.jvm` | `2.4.20` | `:core` | Sí |
| `org.jetbrains.kotlin.plugin.compose` | `2.4.20` | `:app` | Sí |
| `org.jetbrains.kotlin.plugin.serialization` | `2.4.20` | `:app`, `:core` | Sí en `:app`; en `:core` solo se usa para fixtures de test (Hallazgo #12) |
| `com.google.devtools.ksp` | `2.3.12` | `:app` | Sí (procesador de Hilt y Room) |
| `com.google.dagger.hilt.android` | `2.60.1` | `:app` | Sí |
| `org.gradle.toolchains.foojay-resolver-convention` | `1.0.0` | `settings.gradle.kts:15` | Sí (resolución de JDK de Gradle) |

#### 5.2. Librerías declaradas (`gradle/libs.versions.toml:26-60`, `app/build.gradle.kts`, `core/build.gradle.kts`)

| # | Coordenada Maven (`group:name`) | Versión | Configuración y módulo | Uso real en el proyecto | ¿Sobra? |
| --- | --- | --- | --- | --- | --- |
| 1 | `androidx.compose:compose-bom` | `2026.09.00` | `implementation(platform)` (`:app`) / `androidTestImplementation(platform)` (`:app`) | Alinea versiones de artefactos Compose. | **En `implementation`: NO.<br>En `androidTestImplementation` (`app/build.gradle.kts:112`): SÍ SOBRA.** |
| 2 | `androidx.activity:activity-compose` | `1.13.0` | `implementation` (`:app`) | `setContent`, `BackHandler`, `enableEdgeToEdge`, `rememberLauncherForActivityResult`. | **NO** |
| 3 | `androidx.compose.material3:material3` | *(BOM)* | `implementation` (`:app`) | Componentes Material 3 en todas las pantallas. | **NO** |
| 4 | `androidx.compose.ui:ui` | *(BOM)* | `implementation` (`:app`) | Núcleo de Jetpack Compose UI. | **NO** |
| 5 | `androidx.compose.ui:ui-graphics` | *(BOM)* | `implementation` (`:app`) | `Color`, `Canvas` (`WeightTrendChart`), `asImageBitmap`. | **NO** |
| 6 | `androidx.compose.ui:ui-tooling-preview` | *(BOM)* | `implementation` (`:app`, línea 80) | Ningún archivo del proyecto usa `@Preview`. | **SÍ SOBRA** (Hallazgo #11) |
| 7 | `androidx.compose.ui:ui-tooling` | *(BOM)* | `debugImplementation` (`:app`, línea 117) | No se usa `@Preview` y además exporta `PreviewActivity` en el manifest de `debug`. | **SÍ SOBRA** (Hallazgo #11) |
| 8 | `androidx.compose.ui:ui-test-manifest` | *(BOM)* | `debugImplementation` (`:app`, línea 116) | No hay tests de UI de Compose y además exporta `ComponentActivity` en el manifest de `debug`. | **SÍ SOBRA** (Hallazgo #11) |
| 9 | `androidx.compose.ui:ui-test-junit4` | *(BOM)* | `androidTestImplementation` (`:app`, línea 113) | No se usa en ningún test (`PLAN.md` Sección 9: sin tests de UI). | **SÍ SOBRA** (Hallazgo #11) |
| 10 | `androidx.core:core-ktx` | `1.19.1` | `implementation` (`:app`) | `ContextCompat.checkSelfPermission` en cámara/OCR. | **NO** |
| 11 | `androidx.lifecycle:lifecycle-runtime-ktx` | `2.11.0` | `implementation` (`:app`) | `ViewModel`, `viewModelScope` y ciclo de vida. | **NO** |
| 12 | `com.google.dagger:hilt-android` | `2.60.1` | `implementation` (`:app`) | Inyección de dependencias Hilt en `:app`. | **NO** |
| 13 | `com.google.dagger:hilt-android-compiler` | `2.60.1` | `ksp` (`:app`) | Generación de código Hilt en compilación. | **NO** |
| 14 | `androidx.room:room-runtime` | `2.8.5` | `implementation` (`:app`) | Motor de base de datos Room. | **NO** |
| 15 | `androidx.room:room-ktx` | `2.8.5` | `implementation` (`:app`) | `database.withTransaction` y soporte de corrutinas/Flow en Room. | **NO** |
| 16 | `androidx.room:room-compiler` | `2.8.5` | `ksp` (`:app`) | Generación de DAOs, `ControlCalDatabase_Impl` y exportación de esquemas JSON. | **NO** |
| 17 | `androidx.room:room-testing` | `2.8.5` | `testImplementation` (`:app`) | `MigrationTestHelper` en `ControlCalDatabaseMigrationTest.kt`. | **NO** |
| 18 | `net.zetetic:sqlcipher-android` | `4.19.1` | `implementation` (`:app`) | Cifrado AES-256 de SQLite (`SQLCipherDriver`, `SQLiteDatabaseHook`, `Logger`). | **NO** |
| 19 | `androidx.sqlite:sqlite` | `2.7.1` | `implementation` (`:app`) | `SQLiteConnection` y `execSQL` en `Migrations.kt`. | **NO** |
| 20 | `androidx.datastore:datastore-preferences` | `1.2.1` | `implementation` (`:app`) | Ajustes de usuario, configuración de comidas y almacenamiento de clave envuelta. | **NO** |
| 21 | `androidx.camera:camera-core` | `1.6.2` | `implementation` (`:app`) | `ImageAnalysis`, `ImageCapture`, `Preview`, `CameraSelector`. | **NO** |
| 22 | `androidx.camera:camera-camera2` | `1.6.2` | `implementation` (`:app`) | Implementación Camera2 requerida por CameraX. | **NO** |
| 23 | `androidx.camera:camera-lifecycle` | `1.6.2` | `implementation` (`:app`) | `ProcessCameraProvider.bindToLifecycle`. | **NO** |
| 24 | `androidx.camera:camera-view` | `1.6.2` | `implementation` (`:app`) | `PreviewView` en `CameraPreview.kt` y `OcrCameraPreview.kt`. | **NO** |
| 25 | `com.google.mlkit:barcode-scanning` | `17.3.0` | `implementation` (`:app`) | Escaneo offline de EAN-13, EAN-8, UPC-A y UPC-E. | **NO** |
| 26 | `com.google.mlkit:text-recognition` | `16.0.1` | `implementation` (`:app`) | Reconocimiento de texto latino offline para tablas nutricionales. | **NO** |
| 27 | `com.squareup.okhttp3:okhttp` | `4.12.0` | `implementation` (`:app`) | Cliente HTTPS en `OffClient.kt` y `NetworkModule.kt`. | **NO** |
| 28 | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.11.0` | `implementation` (`:app` y `:core`) | En `:app`: `OffMapper`, `MealConfigRepositoryImpl`, `OcrLabelStorageHelper`. En `:core`: solo `@Serializable` en `OcrModels.kt` y lectura de fixtures en `core/src/test` (`OcrFixtureLoader.kt`). | **En `:app`: NO.<br>En `:core` (`implementation`): mejorable a `testImplementation` si se separan los modelos de fixture (Hallazgo #12).** |
| 29 | `junit:junit` | `4.13.2` | `testImplementation` (`:app` y `:core`) | Todos los tests unitarios JUnit 4 de `:core` y `:app`. | **NO** |
| 30 | `org.jetbrains.kotlin:kotlin-test` | *(2.4.20)* | `testImplementation` (`:app`, línea 106) | Solo se usa para `assertFailsWith` en `ControlCalDatabaseMigrationTest.kt:13` (reemplazable por `org.junit.Assert.assertThrows` de JUnit 4.13.2). | **OPCIONAL / PRESCINDIBLE** |
| 31 | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `1.11.0` | `testImplementation` (`:app`) | `runTest`, `StandardTestDispatcher`, `Dispatchers.setMain` en los tests de ViewModel. | **NO** |
| 32 | `org.robolectric:robolectric` | `4.17` | `testImplementation` (`:app`) | Ejecución en JVM de tests de DAOs, migraciones Room y DataStore. | **NO** |
| 33 | `androidx.test:core` | `1.7.0` | `testImplementation` (`:app`) | `ApplicationProvider.getApplicationContext()` e `InstrumentationRegistry` en tests con Robolectric. | **NO** |
| 34 | `androidx.test.ext:junit` | `1.3.0` | `testImplementation` (`:app`, línea 110) y `androidTestImplementation` (`:app`, línea 115) | `AndroidJUnit4` en `DaoTestBase`, `DatabaseFilesTest` y `DataStoreWrappedKeyStoreTest` (en `src/test`). En `src/androidTest` solo está la plantilla `ExampleInstrumentedTest.kt`. | **En `testImplementation`: NO.<br>En `androidTestImplementation` (`app/build.gradle.kts:115`): SÍ SOBRA.** |
| 35 | `androidx.test.espresso:espresso-core` | `3.7.0` | `androidTestImplementation` (`:app`, línea 114) | No se usa en ningún archivo del proyecto. | **SÍ SOBRA** (Hallazgo #11) |

#### Resumen de dependencias que sobran
1. **En `gradle/libs.versions.toml` y `app/build.gradle.kts`**:
   - `androidx.test.espresso:espresso-core:3.7.0` (`libs.androidx.espresso.core` + versión `espressoCore`)
   - `androidx.compose.ui:ui-test-junit4` (`libs.androidx.compose.ui.test.junit4`)
   - `androidx.compose.ui:ui-test-manifest` (`libs.androidx.compose.ui.test.manifest`)
   - `androidx.compose.ui:ui-tooling` (`libs.androidx.compose.ui.tooling`)
   - `androidx.compose.ui:ui-tooling-preview` (`libs.androidx.compose.ui.tooling.preview`)
   - Las declaraciones `androidTestImplementation(platform(libs.androidx.compose.bom))` y `androidTestImplementation(libs.androidx.junit)` en `app/build.gradle.kts:112, 115` (junto con el archivo plantilla `app/src/androidTest/java/com/controlcal/app/ExampleInstrumentedTest.kt` y `app/src/test/java/com/controlcal/app/ExampleUnitTest.kt`).
2. **En `core/build.gradle.kts`**:
   - `implementation(libs.kotlinx.serialization.json)` puede reducirse a `testImplementation(libs.kotlinx.serialization.json)` si los modelos exclusivos de fixtures (`OcrLabelMetadata`, `ExpectedNutritionValues`) se mueven a `core/src/test`.
3. **En `app/build.gradle.kts`**:
   - `testImplementation(kotlin("test"))` (línea 106) solo se usa en una línea (`ControlCalDatabaseMigrationTest.kt:13` para `assertFailsWith`), que JUnit 4.13.2 ya cubre nativamente con `Assert.assertThrows`.

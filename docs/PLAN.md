# Plan del proyecto — ControlCal

Conversión a Markdown de las secciones 3 a 10 de la especificación
«App de nutrición local-first · Instrucciones para Antigravity» (versión del 5 de octubre de 2026).

## 3. Arquitectura

Dos módulos Gradle. `:core` es Kotlin puro (JVM) y contiene modelos de dominio, casos de uso y algoritmos. `:app` contiene interfaz, base de datos, cámara, red, modelo local y Health Connect. Las interfaces de repositorio se declaran en `:core` y se implementan en `:app`.

```
:core
  model/        Product, MealEntry, DayTotals, MacroTargets...
  nutrition/    totales del día, restante, conversiones
  ocr/          NutritionLabelParser, LabelValidator
  solver/       MacroSolver, FoodSelector, Rounding
  tdee/         TdeeEstimator, WeightTrend
  suggest/      QuickLogRanker
  repo/         interfaces de repositorio

:app
  data/db/      entidades Room, DAOs, migraciones, cifrado
  data/off/     OffClient, mapeo de respuesta
  data/health/  HealthConnectWriter
  data/backup/  exportar e importar
  camera/       CameraX, analizadores de barras y texto
  llm/          ModelDownloader, RecipeEngine
  ui/<pantalla>/  Screen + ViewModel + UiState por pantalla
  di/           módulos Hilt
```

- Flujo de datos en un sentido: UI lee `StateFlow<UiState>` y envía eventos al ViewModel; el ViewModel llama a casos de uso.
- Todo acceso a disco, red, cámara o modelo ocurre fuera del hilo principal.
- Las cantidades se guardan en gramos o mililitros como `Double`. Las unidades (1 huevo) son solo presentación.

## 4. Modelo de datos

| Tabla | Columnas |
| --- | --- |
| products | id PK, barcode TEXT UNIQUE NULL, name, brand NULL, kcal_100g, protein_100g, carbs_100g, fat_100g (obligatorios), fiber_100g, sugars_100g, sat_fat_100g, salt_100g (NULL), is_liquid BOOL, unit_name NULL, unit_g NULL, serving_g NULL, min_g NULL, max_g NULL, in_pantry BOOL, source (MANUAL, OFF, OCR), archived BOOL, created_at, updated_at |
| daily_logs | date PK (ISO local), day_type (TRAINING, REST), water_ml, notes NULL |
| meal_entries | id PK, date FK, meal (id de comida TEXT), product_id FK (NULL para calorías rápidas desde M.6), product_name (copia), grams (puede ser negativo en modo tara), kcal, protein, carbs, fat (copia calculada al guardar), logged_at |
| weight_entries | date PK, weight_kg |
| goal_profiles | id PK, day_type, kcal, protein_g, carbs_g, fat_g, valid_from, origin (MANUAL, TDEE) |

- Índices: meal_entries(date), meal_entries(product_id, logged_at), products(name).
- UNIQUE en barcode ya crea su índice; no añadas otro.
- Los macros se copian en meal_entries para que editar un producto no cambie el historial.
- Un producto con entradas no se borra: se marca archived.
- El total de un día nunca se muestra por debajo de cero aunque haya entradas negativas.
- meal_entries.meal pasa a ser el id (TEXT) de una comida configurada. Las cuatro por defecto conservan los ids actuales: BREAKFAST (Desayuno), LUNCH (Almuerzo), SNACK (Merienda) y DINNER (Cena). Los datos existentes no se migran.
- logged_at siempre cae dentro de date: fecha del día más la hora de la comida. Las copias conservan la hora del original.
- Desde la tarea M.6, meal_entries.product_id admite NULL: una entrada sin producto es de calorías rápidas (kcal, macros opcionales y grams = 0).
- La configuración de comidas vive en DataStore: lista de id, nombre, hora de inicio y activa.
- Una entrada con un id de comida desconocido se muestra en «Sin asignar» y cuenta en el total.

## 5. Especificación de cada función

### 5.1 Diario y alta de entradas

- La app abre en hoy. Deslizar horizontalmente cambia de día. Además de deslizar, al tocar la fecha se abre un calendario para saltar a cualquier día.
- Las comidas dejan de ser fijas. El usuario decide en Ajustes cuántas hay, su nombre y su hora de inicio; por defecto, Desayuno, Almuerzo, Merienda y Cena. El orden y la comida preseleccionada salen de la hora de inicio; antes de la primera, se preselecciona la última del día. Una comida con entradas no se borra: se desactiva.
- Detalle de comida: selector de comida en la cabecera, hora de la comida, resumen de kcal y macros, lista con [-] y [+], «Añadir alimento», «Añadir calorías», «Restar sobras», «Repetir esta comida» y «Hecho». Los cambios se guardan al momento.
- Añadir entrada: buscador con resultados desde la primera letra (productos recientes primero), cantidad en gramos o en unidades si el producto tiene unit_g, y comida preseleccionada según la hora.
- Modo tara: en el detalle de una comida, «Restar sobras» crea una entrada negativa del mismo producto.
- Objetivo del día = perfil de goal_profiles vigente para el day_type de ese día.

### 5.2 Código de barras

- Formatos: EAN-13, EAN-8, UPC-A, UPC-E. Analiza con estrategia de quedarse solo con el último fotograma. Exige dos lecturas iguales seguidas antes de aceptar un código.
- Orden de búsqueda: base de datos local; si no está, Open Food Facts; si no existe, ofrecer foto de la tabla o alta manual con el código ya rellenado.
- Petición: `GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json?fields=product_name,brands,quantity,serving_size,nutriments`.
- Cabecera obligatoria `User-Agent: NombreApp/Version (email de contacto)`. Pide el email al humano. Respeta el límite de peticiones publicado en la documentación de la API.
- Campos de nutriments: energy-kcal_100g, proteins_100g, carbohydrates_100g, fat_100g, fiber_100g, sugars_100g, saturated-fat_100g, salt_100g. Cualquiera puede faltar o venir como texto.
- Si faltan kcal pero hay energy_100g en kJ, convierte dividiendo entre 4,184. Si falta algún macro obligatorio, abre la confirmación con ese campo vacío y marcado.
- Tiempo de espera de 5 s. Sin red: mensaje claro y acceso directo al alta manual.
- Pantalla «Acerca de»: atribución a Open Food Facts y su licencia ODbL.

### 5.3 OCR de la tabla nutricional

ML Kit devuelve bloques, líneas y cajas. El parser vive en `:core` y recibe una lista de líneas con texto y caja; no conoce ML Kit. Se desarrolla contra fotos reales guardadas como fixtures (fase 3).

1. Normaliza: minúsculas, sin tildes, coma decimal a punto, corrige confusiones típicas en contexto numérico (O por 0, l por 1, S por 5).
2. Localiza filas por palabra clave: valor energético o energía; grasas; «de las cuales saturadas»; hidratos de carbono; «de los cuales azúcares»; fibra alimentaria; proteínas; sal.
3. Asocia cada etiqueta con los números de su misma fila usando el solapamiento vertical de las cajas, no el orden del texto.
4. Si hay varias columnas, elige la encabezada «por 100 g» o «por 100 ml». Nunca la de porción ni la de % de ingesta de referencia.
5. Energía: usa el valor seguido de kcal. Si solo hay kJ, divide entre 4,184. Con el formato «1046 kJ / 250 kcal» toma 250.
6. «<0,5 g» se guarda como 0,5 y se marca como aproximado. «Trazas» se guarda como 0.
7. Validaciones: 4·P + 4·C + 9·G + 2·fibra debe quedar a menos del 15 % de las kcal; azúcares no superan hidratos; saturadas no superan grasas; ningún valor por 100 g supera 100; kcal no supera 900.
8. Salida: valor y nivel de confianza por campo. La pantalla de confirmación resalta los campos dudosos o que fallan una validación y muestra la foto al lado.

### 5.4 Registro rápido

Puntuación de cada producto para la franja actual: número de veces registrado en los últimos 60 días dentro de ±90 minutos de la hora actual, con doble peso si coincide el día de la semana, y con decaimiento por antigüedad. Muestra los 6 primeros con su cantidad más frecuente. Un toque añade; mantener pulsado edita la cantidad.

### 5.5 Peso y TDEE

- Tendencia de peso: media móvil exponencial de los pesajes, para amortiguar oscilaciones diarias.
- Ventana de 21 días. Requisitos: al menos 14 días con registro de comida completo y 8 pesajes. Si no se cumplen, no se calcula y se explica qué falta.
- TDEE = ingesta media diaria − (cambio de la tendencia en kg × 7700 / días de la ventana).
- Una vez por semana propone un objetivo nuevo. El cambio se limita a ±150 kcal por semana y el usuario lo acepta o lo rechaza; nunca se aplica solo.
- Nunca propongas un objetivo por debajo del mínimo que el usuario fije en ajustes. Muestra el aviso de que no es consejo médico.

### 5.6 Macro Tetris

Entrada: macros restantes del día b = (kcal, proteína, carbohidratos, grasa) y los productos con in_pantry. Salida: gramos por producto y error por macro.

1. Mínimos cuadrados con límites: minimiza la suma de w_i · ((A·x − b)_i / b_i)² sujeto a min_g ≤ x ≤ max_g por producto. Pesos por defecto: kcal 1, proteína 1,5, carbohidratos 0,7, grasa 0,7.
2. Si un producto no tiene límites: mínimo 0 y máximo 300 g (30 g si su grasa por 100 g supera 80).
3. Resuelve con gradiente proyectado o conjunto activo, implementado a mano en Kotlin. Sin librerías numéricas.
4. Selección: como máximo 5 productos. Búsqueda voraz: añade el producto que más reduce el error y para cuando la mejora es menor del 1 %.
5. Redondeo: a múltiplos de 5 g, o a unidades enteras si el producto tiene unit_g. Recalcula los macros tras redondear.
6. Muestra siempre el error real por macro. Si algún macro queda a más del 10 %, dilo y sugiere qué tipo de alimento falta. No prometas precisión que no se alcanza.
7. Casos límite con test: despensa vacía, macros restantes negativos o cero, un solo producto, productos casi idénticos.

### 5.7 Chef local

- Motor: LiteRT-LM con su API de Kotlin y un modelo Gemma pequeño en formato .litertlm. Empieza con un informe comparativo de tres modelos en el móvil del humano (tokens por segundo, memoria, calidad del español); el humano elige.
- El modelo no va dentro del APK. Se descarga a petición, solo por Wi-Fi salvo que el usuario lo permita, reanudable, verificando SHA-256. Se puede borrar desde ajustes.
- El prompt recibe la lista cerrada de ingredientes y gramos que devolvió el solver. Pide solo título y pasos numerados, en español, sin añadir ingredientes salvo sal, especias y agua.
- Las cantidades y los macros que ve el usuario se pintan desde el solver, nunca desde el texto del modelo.
- Respuesta en streaming, cancelable. El motor se libera al salir de la pantalla y cuando el sistema avisa de poca memoria.
- Plan B obligatorio: si no hay modelo o el dispositivo no llega, plantillas de texto por tipo de plato. Macro Tetris funciona igual.

### 5.8 Health Connect y copia de seguridad

- Escribe NutritionRecord por entrada e HydrationRecord por agua, con un identificador de cliente estable para poder actualizar y borrar. Lectura de WeightRecord solo si el humano lo decide. Health Connect: el mealType de cada NutritionRecord sale de su comida: BREAKFAST, LUNCH, DINNER o SNACK para las cuatro por defecto y SNACK para las creadas por el usuario. Las calorías rápidas también se envían.
- Pide permisos en contexto, la primera vez que el usuario activa la sincronización, con una pantalla que explica para qué. La app funciona completa sin ellos.
- Exportar: un único archivo con todos los datos en JSON, cifrado con AES-256-GCM y clave derivada de una contraseña (PBKDF2 con muchas iteraciones o Argon2 si hay implementación fiable). Selector de archivos del sistema. Copia de seguridad: además de la base de datos, incluye la configuración de comidas y las preferencias que el usuario cambia en Ajustes. Nunca la clave de la base de datos ni el estado del dispositivo (sincronización con Health Connect, modelo descargado).
- Importar: valida versión y esquema antes de tocar la base de datos; restaura dentro de una transacción. Al importar, DataStore se escribe solo después de que la transacción de Room termine bien.

## 6. Seguridad

- SQLCipher con clave aleatoria de 32 bytes generada en el primer arranque, cifrada con una clave de Android Keystore (AES-GCM) y guardada cifrada.
- android:allowBackup="false" y reglas de extracción de datos vacías.
- Permisos: CAMERA e INTERNET. Ninguno de almacenamiento, ubicación ni contactos.
- Configuración de seguridad de red: sin tráfico en claro; dominios permitidos: Open Food Facts y el host de descarga del modelo.
- Componentes no exportados salvo el Activity principal.
- Release con R8 y sin logs de depuración.
- Sin secretos en el repositorio. No hay claves de API en este proyecto; si crees necesitar una, pregunta.

## 7. Rendimiento

| Medida | Objetivo |
| --- | --- |
| Arranque en frío hasta ver el día de hoy | Menos de 1,5 s en gama media |
| Registrar un alimento habitual | 3 toques o menos |
| Código en caché: de lectura a ficha | Menos de 1 s |
| Consultas del panel diario | Menos de 50 ms |
| Solver con 30 productos | Menos de 100 ms |
| Chef local: primer texto | Menos de 3 s con el modelo cargado |

Sin trabajo en el hilo principal, listas con claves estables, y perfil de referencia (Baseline Profile) en la fase 7.

## 8. Fases y tareas

Trabaja en orden. Una tarea por conversación. No abras una fase hasta que el humano confirme el criterio de la anterior.

| Tarea | Qué hay que hacer | Hecho cuando |
| --- | --- | --- |
| 0.1 | Preguntar al humano los datos de la sección 12 | Respuestas anotadas en ESTADO.md |
| 0.2 | Proyecto con :app y :core, version catalog, versiones verificadas | Compila |
| 0.3 | Linter (ktlint o detekt) y un test de ejemplo en :core | ./gradlew :core:test en verde |
| 0.4 | Manifest base, allowBackup=false, configuración de red. REVISION HUMANA | Instala y abre en el móvil |
| 1.1 | Entidades, DAOs, migración 1 y tests de DAO | Tests en verde |
| 1.2 | Cifrado con SQLCipher y Keystore. REVISION HUMANA | El archivo de BD no es legible sin clave |
| 1.3 | Casos de uso en :core: totales del día y restante | Tests en verde |
| 1.4 | Panel diario con anillo, macros y lista por comida | Muestra datos reales |
| 1.5 | Alta y edición manual de producto | Guarda y valida |
| 1.6 | Añadir entrada: buscador, cantidad, comida | 3 toques para un habitual |
| 1.7 | Modo tara | Resta y nunca baja de cero |
| 1.8 | Objetivos fijos, ajustes y agua | Fase 1: una semana de uso real |
| 2.1 | Permiso de cámara y vista previa con CameraX | Vista previa estable |
| 2.2 | Analizador de códigos con doble lectura | Lee EAN-13 y EAN-8 |
| 2.3 | OffClient, mapeo y campos nulos, con tests de mapeo | Tests con respuestas guardadas |
| 2.4 | Flujo local, luego red, luego no encontrado | Segundo escaneo sin red |
| 2.5 | Confirmación, guardado y atribución | Fase 2: 10 productos reales |
| 3.1 | Pantalla de depuración que guarda foto y salida de ML Kit en JSON | El humano recoge 25 etiquetas |
| 3.2 | Fixtures en :core con los valores esperados que escribe el humano | 25 casos cargados |
| 3.3 | Parser y validaciones | 20 de 25 con los 4 macros correctos |
| 3.4 | Captura y confirmación con campos dudosos resaltados | Fase 3: nada se guarda sin confirmar |
| 4.1 | Sugerencias por franja y repetir comida o día | Sugiere bien tras una semana |
| 4.2 | Registro y gráfica de peso | Guarda y dibuja la tendencia |
| 4.3 | Perfiles de objetivo por tipo de día | Cambia según el día |
| 4.4 | TdeeEstimator en :core con tests y propuesta semanal | Fase 4: ajuste explicable |
| 5.1 | Despensa y límites por producto | Marca y edita |
| 5.2 | MacroSolver y FoodSelector con tests | Casos límite en verde |
| 5.3 | Pantalla Macro Tetris y añadir al diario | Fase 5: menos de 100 ms |
| M.1 | Selector de fecha con calendario | Se guarda el día elegido |
| M.2 | Comidas con id y configuración guardada | Nada cambia en pantalla y los datos antiguos siguen intactos |
| M.3 | Comidas del día en Ajustes y en el panel | Renombrar, añadir y desactivar sin perder entradas |
| M.4 | Tarjetas de comida y detalle | 3 toques para un habitual |
| M.5 | [-] y [+] y hora de la comida | Editar un producto no cambia el historial |
| M.6 | Calorías rápidas. REVISION HUMANA | Fase M: datos intactos tras la migración |
| 6.1 | Informe comparativo de 3 modelos en el móvil | El humano elige modelo |
| 6.2 | ModelDownloader con hash. REVISION HUMANA | Reanuda y verifica |
| 6.3 | RecipeEngine, prompt, streaming y memoria | Cifras iguales a las del solver |
| 6.4 | Plan B con plantillas | Fase 6: funciona sin modelo |
| 7.1 | Health Connect: escritura. REVISION HUMANA | Samsung Health muestra la comida |
| 7.2 | Exportar e importar cifrado. REVISION HUMANA | Restaura en instalación limpia |
| 7.3 | Rendimiento, accesibilidad, modo oscuro | Objetivos de la sección 7 |
| 8.x | Firma, R8, política de privacidad, formularios de Play, licencias | Prueba cerrada aprobada |

## 9. Pruebas

- `:core`: tests unitarios de todo. En parser, solver y TDEE el humano define los casos y tú implementas hasta que pasan.
- `:app`: tests de DAOs y migraciones con Room en memoria; tests de mapeo de Open Food Facts con respuestas JSON guardadas.
- No hagas tests de interfaz salvo que se pida. La verificación de interfaz la hace el humano en su móvil.
- Cámara, OCR, modelo local y Health Connect no se pueden validar en emulador. Di qué debe probar el humano y cómo.

## 10. Qué no debes hacer

- Añadir Firebase, analíticas, informes de fallos, anuncios o cualquier SDK no listado.
- Usar una API de IA en la nube para el OCR, las recetas o cualquier otra cosa.
- Guardar datos fuera de la base de datos cifrada y de DataStore.
- Cambiar el applicationId o el nombre del paquete tras la fase 0.
- Reescribir módulos enteros para arreglar un fallo pequeño.
- Generar datos nutricionales inventados para rellenar la base de datos.
- Dar una tarea por hecha sin compilar y sin pasar los tests.

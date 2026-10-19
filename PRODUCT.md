# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Usuario individual avanzado / uso personal. Diseñado como herramienta diaria de alta eficiencia para control nutricional, conteo de calorías y macronutrientes, y seguimiento de peso corporal, priorizando velocidad de registro, densidad de información y privacidad total.

## Product Purpose

ControlCal es una aplicación nativa de Android local-first para el seguimiento diario de nutrición, calorías, macronutrientes y peso. Su propósito es eliminar la fricción del registro diario y ofrecer un control nutricional riguroso y científicamente fundamentado sin depender de la nube, suscripciones ni analíticas externas.

## Positioning

Frente a las aplicaciones comerciales de nutrición (MyFitnessPal, LoseIt, Yazio) que monetizan datos personales, exigen cuentas en la nube, muestran anuncios y dependen de bases de datos imprecisas, ControlCal es un instrumento técnico de precisión local-first:
- 100 % offline por defecto, con base de datos local cifrada mediante SQLCipher y Android Keystore.
- Registro en 3 toques o menos para alimentos habituales.
- Optimización matemática real de macronutrientes (solver Macro Tetris en Kotlin puro).
- Estimación científica del gasto energético (TDEE por tendencia de media móvil exponencial EMA).
- Verificación e integridad nutricional estricta (balance calórico de Atwater, modo tara para sobras, suelo cero en totales).

## Operating Context

- **Entorno de uso**: Uso recurrente diario en cocina (al preparar o pesar alimentos), en mesa (registro de comidas y tara de sobras), en supermercado/despensa (escaneo de códigos de barras) y matutino (pesaje y revisión de objetivos).
- **Dispositivo**: Hardware Android moderno (Samsung Galaxy S26 Ultra, minSdk 28, compileSdk 37.2, targetSdk 36).
- **Ritmo operativo**: Interacciones frecuentes y breves (15-30 segundos por comida) donde la latencia cero y los flujos directos son críticos.

## Capabilities and Constraints

- **Capacidades confirmadas**:
  - Panel diario con selector de fecha (calendario, deslizamiento y salto rápido a hoy).
  - Comidas dinámicas y configurables en DataStore (Desayuno, Almuerzo, Merienda, Cena o personalizadas con horas de inicio).
  - Escáner de código de barras offline con ML Kit (modelo bundled) y consulta HTTPS a Open Food Facts con confirmación obligatoria.
  - OCR de tabla nutricional mediante CameraX y ML Kit (modelo latino offline) con validación Atwater en `:core`.
  - Modo tara (entradas en negativo para restar sobras sin bajar el total diario de cero).
  - Calorías rápidas (registro calórico/macros sin producto asociado, `grams = 0`).
  - Detalle de comida con ajuste rápido `[-]` / `[+]`, selector de hora y reescalado proporcional preservando el historial.
  - Macro Tetris: solver por mínimos cuadrados acotados en Kotlin puro para cuadrar macros restantes con hasta 5 alimentos de la despensa.
  - Registro de peso con gráfica interactiva en `Canvas` de Compose (tendencia EMA $\alpha = 0,1$, filtros temporales y cursor scrubber).
  - Estimación de TDEE y propuesta semanal explicable acotada a $\pm 150$ kcal con suelo de seguridad.
  - Objetivos cíclicos según tipo de día (entrenamiento vs. descanso).
  - Sincronización con Health Connect (escritura de nutrición/hidratación, lectura de peso).
  - Copia de seguridad exportable cifrada con AES-256-GCM.
- **Restricciones técnicas y de arquitectura**:
  - Arquitectura modular: `:core` en Kotlin puro (sin dependencias `android.*` ni `androidx.*`, 100 % cubierto por tests unitarios) y `:app` para Compose, Room, CameraX y Hilt.
  - Un solo Activity en Jetpack Compose con Material Design 3.
  - Base de datos Room sobre SQLCipher cifrada con clave AES-GCM en Keystore. Sin `fallbackToDestructiveMigration` (migraciones numeradas y testeadas).
  - Red estrictamente limitada a Open Food Facts y descarga del modelo local vía HTTPS. Sin SDKs de terceros ni analíticas.
  - Pantalla de confirmación obligatoria para cualquier dato procedente de escáner, OCR o IA antes de persistir.
  - Idioma de interfaz estrictamente en español (`strings.xml`), identificadores y código en inglés, y decimales con coma (`12,5 g`).

## Brand Commitments

- **Nombre**: ControlCal (`com.controlcal.app`).
- **Tono y personalidad**: Preciso, sobrio, técnico y utilitario; un panel de instrumentos confiable antes que una app motivacional o publicitaria.
- **Lenguaje visual**: Material Design 3 con soporte completo para modo oscuro (first-class), tokens de color de Material You y tipografía legible en escala `sp`.
- **Idioma**: Español (textos, mensajes y formatos numéricos).

## Evidence on Hand

- Especificación completa del sistema en `docs/PLAN.md`.
- Registro detallado de decisiones y tareas en `docs/ESTADO.md`.
- 19 fixtures reales de tablas nutricionales con valores esperados en `core/src/test/resources/ocr_fixtures/`.
- Esquema de base de datos Room v2 verificado en `app/schemas/com.controlcal.app.data.db.ControlCalDatabase/2.json`.

## Product Principles

1. **Instrumento de latencia cero**: Toda acción habitual de registro debe completarse en 3 toques o menos. Las consultas y la navegación local deben responder de forma instantánea.
2. **Soberanía y privacidad absoluta**: Los datos del usuario residen cifrados en su propio dispositivo; la aplicación funciona al 100 % sin conexión y sin telemetría.
3. **Rigor e integridad matemática**: Los cálculos nutricionales, validaciones de Atwater, estimaciones de TDEE y el solver de macros no admiten valores inventados ni aproximaciones opacas.
4. **Confirmación humana obligatoria**: Ningún dato capturado por cámara, escáner o IA se incorpora al diario sin una pantalla de confirmación explícita y editable por el usuario.

## Accessibility & Inclusion

- Objetivos táctiles mínimos de 48×48 dp con al menos 8 dp de separación entre controles.
- Soporte estricto de escala tipográfica del sistema (`sp`, validado contra `font_scale 1.3`).
- Modo oscuro diseñado como esquema de primer nivel con contraste adecuado (no una inversión automática).
- Etiquetas semánticas y de accesibilidad en controles de navegación, botones de acción y captura de cámara.

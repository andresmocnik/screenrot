# ScreenRot — Android MVP

> "Your screen time changes your character."

## 1. Validación técnica (hecha antes de escribir código de producto)

- **`UsageStatsManager.queryUsageStats(INTERVAL_BEST, begin, end)`** sigue siendo la API
  vigente para uso agregado por paquete. Requiere el permiso especial
  `android.permission.PACKAGE_USAGE_STATS`, que **no se puede pedir en runtime** — solo se
  otorga manualmente en Settings → Apps → Acceso especial → Uso de datos de apps. Se detecta
  el estado real del permiso con `AppOpsManager.checkOpNoThrow(OPSTR_GET_USAGE_STATS, ...)`,
  no con `PackageManager` (que no refleja este tipo de permiso).
- Desde Android R, si el usuario está en un estado "locked" (`UserManager.isUserUnlocked()`
  false), `queryUsageStats` puede devolver datos incompletos. No es un problema para este MVP
  (siempre se consulta con el usuario activo), pero queda documentado por si aparecen huecos
  de datos en dispositivos con FBE y perfiles de trabajo.
- **`WallpaperService` / `Engine`** (paquete `android.service.wallpaper`) sigue siendo la base
  vigente para live wallpapers: se extiende `WallpaperService`, se implementa
  `onCreateEngine()`, y el `Engine` maneja `onSurfaceCreated/onVisibilityChanged/onSurfaceDestroyed`
  dibujando sobre el `SurfaceHolder`. El servicio requiere `android.permission.BIND_WALLPAPER`.

## 2. Decisiones de arquitectura (y por qué)

| Decisión | Por qué |
|---|---|
| `minSdk 26 / target 34` | Oreo+ para background/JobScheduler consistente; 34 es el estable vigente. |
| `core` como módulo Kotlin/JVM puro (no Android) | El motor matemático no necesita nada de Android → compila y testea en segundos, y es reusable después vía Kotlin Multiplatform para el core de iOS. |
| `queryUsageStats` (agregado) en vez de `queryEvents` (eventos crudos) | Lo que necesitamos es "minutos por app hoy", no sesiones individuales. Menos complejidad para el MVP; se puede migrar a `queryEvents` después si se quiere distinguish "muchos picks cortos" vs "un uso largo". |
| `WorkManager` (no `AlarmManager`) para el refresh periódico | El producto no necesita tiempo real; WorkManager respeta Doze/batería y su intervalo mínimo (15 min) coincide con el cadence objetivo del producto. `AlarmManager` es para timing exacto, que acá activamente no queremos (cuesta batería). |
| Personaje dibujado con `Canvas` procedural (no bitmaps por capas) | Cartoon simple, sin pipeline de arte para el MVP. Cada feature (pelo, ojos, orejas...) es una función que lee un campo de `CharacterState` — migrar a bitmaps reales después es un cambio localizado, no un rediseño. |
| `damage = 1 - exp(-k·minutos)` por canal, con `k` propio por canal | Curva acotada (nunca "rompe"), con cambio visible temprano (curiosidad/viralidad) y rendimientos decrecientes arriba (6h→10h casi no empeora más). Ver `DamageEngine.kt` para el razonamiento completo y cómo se calibró contra los anchors del brief (30min/1h/2h/4h/6h/8h+). |
| Reset diario por comparación de fecha (`storedDate != today`), no por alarma a las 00:00 | Robusto a que Android mate el proceso o el teléfono esté apagado a medianoche — se resuelve solo en el próximo read/write. |
| `DataStore` (no Room) para el estado persistido | Es un solo blob pequeño (el `CharacterState` del día), no datos relacionales. |
| Nada sale del dispositivo; sin cuentas/email | Requisito explícito del brief — local-first. |

## 3. Qué está realmente validado ahora mismo (no solo escrito)

El módulo `core/` (matemática pura, sin Android) fue **compilado y ejecutado de verdad** en
este entorno con `kotlinc`. 20/20 assertions pasaron, incluyendo el chequeo de que la curva de
daño calza con los anchors del brief:

```
0min   -> overallDamage=0.000
30min  -> overallDamage=0.165   (daño chico, "ojeras")
60min  -> overallDamage=0.302   (pelo despeinado)
120min -> overallDamage=0.513   (moderado — ojos cansados, pelo parcial)
240min -> overallDamage=0.763   (delgado, oreja torcida)
360min -> overallDamage=0.885   (casi sin pelo, ojeras profundas)
480min -> overallDamage=0.944
600min -> overallDamage=0.970   (saturado — no sigue creciendo indefinidamente)
```

También se probó: sin uso → personaje pristino; apps desconocidas → categoría genérica sin
romper; múltiples apps acumulando sobre el mismo canal; cambio de día (`DailyReset`);
entradas de 0 minutos; valores extremos (6000 min sigue acotado a 0.97).

**Lo que NO se pudo ejecutar en este entorno** (no hay Android SDK ni emulador instalado, y
la red está restringida a un puñado de dominios que no incluyen `dl.google.com` /
`services.gradle.org` para bajar el SDK o el Gradle wrapper real): el build de Gradle del
módulo `app`, el APK final, y los tests instrumentados. El código de `app/` está escrito de
forma completa y coherente con las APIs reales verificadas arriba, pero su primera compilación
real va a pasar en tu máquina/Android Studio, no acá.

## 4. Cómo abrir esto

1. Abrí la carpeta `ScreenRot/` en Android Studio (Koala o más nuevo). Va a generar el Gradle
   wrapper automáticamente si no lo detecta (o corré `gradle wrapper` una vez si tenés Gradle
   instalado localmente — no incluí el `.jar` del wrapper en este entorno sandboxeado).
2. `./gradlew :core:test` — corre los tests reales de `DamageEngine` / `DailyReset` (los mismos
   casos que ya validé acá con el harness).
3. `./gradlew :app:assembleDebug` — genera el APK debug.
4. Instalar, abrir la app, conceder el permiso de Usage Access cuando lo pida.
5. En un build debug vas a ver el botón "Debug: simulate screen time" en la pantalla del
   personaje — ahí podés simular minutos por app sin esperar horas.

## 5. Success criteria del brief — estado

| Criterio | Estado |
|---|---|
| Instalación | Pendiente de tu build local (código listo) |
| Permissions | Implementado — detección vía AppOps + deep link a Settings |
| Data | Implementado — `UsageStatsRepository` |
| Character | Implementado — `DamageEngine` + `CharacterRenderer`, **matemática validada** |
| Daily reset | Implementado — `DailyReset`, **validado** |
| Wallpaper | Implementado — `ScreenRotWallpaperService` |
| Evolution | Implementado (mismo pipeline que "Character") |
| Animation | Implementado — blink + bob en `CharacterEngine` |
| Performance | Diseñado para: loop de animación se detiene si `!visible`; refresh de daño desacoplado del loop de dibujo (cada 15 min, no cada frame) |
| Sharing | Implementado — `ShareImageGenerator` + Sharesheet |

## 6. Puntos que pueden hacer fracasar el concepto (a vigilar)

- **OEMs agresivos con background** (Xiaomi/Huawei/Samsung con "battery optimization" propio)
  pueden matar el wallpaper o retrasar el WorkManager mucho más que estándar AOSP — vale la
  pena agregar una pantalla que explique "si tu personaje no se actualiza, revisá
  optimización de batería" post-MVP.
- El renderer procedural cartoon es funcional pero visualmente básico; el "shareable/meme-friendly"
  del brief probablemente necesite una pasada de diseño real (o arte bitmap) antes de lanzar,
  no solo formas geométricas.
- `queryUsageStats` puede reportar el tiempo de forma distinta según el fabricante en casos
  raros (hay historial de inconsistencias en algunos Android forks) — vale la pena probar en
  2-3 dispositivos físicos reales antes de confiar en los números para el "daño".

# Contexto del proyecto — PARTIPA Venezuela (`prueba404`)

Documento de traspaso: describe la aplicación actual para usarla como **base de otra aplicación**.
Fuente: lectura directa del código en `C:\iseller`.

> Las secciones 1–10 son el contexto **original** (app electoral "PARTIPA Venezuela"). Sobre esa
> base se está construyendo **iSeller POS**. El estado actual está resumido en la sección 0.

---

## 0. Estado actual (iSeller POS)

| Tema | Estado |
|---|---|
| Paquete / applicationId | `com.iseller.pos` (antes `com.glosteriii.prueba404`) |
| Nombre del proyecto | `iseller_pos` |
| Nombre visible (`app_name`) | **iSeller POS** (antes "PARTICIPA Venezuela") |
| Icono (launcher/splash) | `@mipmap/icono` = logo `i$` en todas las densidades (antes ilustración electoral) |
| Crear cuenta | **Eliminado**: `MainActivity` ya no registra usuarios ni redirige a `Registrar`. |
| Pantalla de inicio | `MainActivity` = **bienvenida** (logo + textos + 2 acciones, con animación de entrada). |
| Login | Movido a `Login.java` / `activity_login.xml` (mismo login, sin botón "Crear cuenta" ni redirect). |
| Ruta de entrega | `RutaEntrega.java` / `activity_ruta_entrega.xml` (placeholder vacío). |
| Fondo de bienvenida | `res/drawable-nodpi/fondo_bienvenida.png` (placeholder generado; reemplazable). |
| Sistema de diseño | `POS_ESTILOS_ANDROID.md` (tokens `--dash-*`, fondo `#181B22`, radios, tipografía). |
| **Color primario** | **`#049FA5`** (teal). Variante oscura / fin del gradiente: `#038086`. |

**Tokens en `res/values/colors.xml`:** `pos_bg` `#181B22` · `pos_surface` `#1A1E24` ·
`pos_border` `#2E353E` · `pos_primary` **`#049FA5`** · `pos_primary_dark` `#038086` ·
`pos_text` `#E8EDF2` · `pos_muted` `#8892A0`.

**Gradiente de marca (botón primario):** `linear-gradient(135deg,#049FA5,#038086)`.

**Flujo de arranque:** `MainActivity` (bienvenida) → "Iniciar sesión en el POS" → `Login` → (tras
autenticar) sigue apuntando al flujo electoral viejo `aperturaMesa`/`Inicio` *(pendiente de
reapuntar al POS)*. "Consultar ruta de entrega" → `RutaEntrega`.

**Pendientes:** `Registrar.java`/`registar.xml` quedaron sin uso (candidatos a borrar) y, tras
autenticar, `Login` sigue navegando al flujo electoral viejo (`aperturaMesa`/`Inicio`).

---

## 1. Qué es

App Android **nativa (Java)** para un operador/testigo electoral ("responsable") durante una
jornada de votación en Venezuela. El usuario:

1. Se registra e inicia sesión (también con huella digital).
2. Reporta la **apertura de mesa** (cantidad de electores).
3. Reporta el **flujo de electores** uno por uno (por cédula), de forma online y offline.
4. Consulta su **dashboard de rendimiento** (metas, asistencia 1x10, centro).
5. Escanea QR con la cámara y consulta una **lista de contactos/jefes** con mapa y WhatsApp.

Los datos se sincronizan contra el backend `https://sigven.net/elecciones/` (PHP).
Nombre de app visible: **"PARTICIPA Venezuela"**. `app_name` en strings = `PARTICIPA Venezuela`,
versión mostrada 4.1.1. El `rootProject.name` y el paquete siguen llamándose `prueba404`.

---

## 2. Stack y build

| Elemento | Valor |
|---|---|
| Lenguaje | Java 8 (`sourceCompatibility 1.8`) |
| Gradle Plugin | Android Gradle Plugin **7.0.0** |
| `compileSdk` / `targetSdk` | 33 |
| `minSdk` | 24 |
| `applicationId` | `com.glosteriii.prueba404` |
| `namespace`/paquete | `com.glosteriii.prueba404` (+ subpaquete `OpenHelpel`) |
| ViewBinding | activado (`viewBinding true`) |
| Repos | `google()`, `mavenCentral()`, `jcenter()` (obsoleto) |

`settings.gradle` usa `FAIL_ON_PROJECT_REPOS` y un único módulo `:app`.

### Dependencias clave (`app/build.gradle`)
- UI: `appcompat 1.6.1`, `material 1.6.0`, `constraintlayout 2.1.4`, `activity 1.7.2`,
  `legacy-support-v4 1.0.0`, `recyclerview 1.3.0`.
- Red: `com.android.volley:volley 1.2.1`.
- Biometría: `androidx.biometric:biometric 1.1.0`.
- QR: `com.google.zxing:core 3.5.1` + `com.journeyapps:zxing-android-embedded 4.3.0`.
- GIF: `pl.droidsonroids.gif:android-gif-drawable 1.2.25`.
- Test: `junit 4.13.2`, `androidx.test.ext:junit 1.1.5`, `espresso-core 3.5.1`.

`app/build.gradle` fuerza versiones en `configurations.all { resolutionStrategy }` para evitar
que las librerías pidan SDK 35: `material 1.6.0`, `profileinstaller 1.3.1`,
`core/core-ktx 1.9.0`, y todo `org.jetbrains.kotlin` a `1.8.0`. Hay un bloque
`packagingOptions` que excluye `.prof` de profileinstaller.

> Nota: el proyecto **no tiene commits** en git (`master` sin historial).

---

## 3. Estructura de código

```
app/src/main/java/com/glosteriii/prueba404/
├── MainActivity.java          // Login (usuario/contraseña + huella). LAUNCHER
├── Registrar.java             // Alta de usuario (máx. 1 usuario)
├── aperturaMesa.java          // Reporte de apertura de mesa
├── Inicio.java                // Dashboard de rendimiento (consulta backend)
├── Reporte_flujo.java         // Reporte de flujo de electores + escaneo QR + offline
├── lista.java                 // Lista de contactos/jefes (JSON + mapa + WhatsApp)
├── MapaActivity.java          // WebView que carga mapa por URL
├── CustomCaptureActivity.java // Wrapper de CaptureActivity (ZXing)
├── CustomAdapter.java         // Adapter de la lista de contactos
├── ChatAdapter.java           // Adapter de "burbujas" del historial de reportes
├── NetworkChangeReceiver.java // BroadcastReceiver de conectividad con listener
├── NetworkUtil.java           // Helper de conectividad (static)
├── FingerprintHandler.java    // Callback FingerprintManager (API < 28 real)
├── Usuario.java               // Modelo de usuario (login local)
├── User.java                  // Modelo de contacto (lista)
├── daoUsuario.java            // DAO de usuarios sobre SQLite (openOrCreateDatabase)
└── OpenHelpel/SQLite_OpenHelper.java  // SQLiteOpenHelper de "reportes"
```

Recursos relevantes: layouts `activity_main`, `registar`, `activity_apertura_mesa`,
`activity_inicio`, `activity_reporte`, `lista`, `list_item`, `chat_bubble`, `activity_mapa`,
`loading_screen`, `pop_up_error`, `layout_custom_dialog`. Iconografía en `drawable/`
(vectoriales) y `mipmap-*/` (PNG/GIF: logo, huella, votación). Tema base
`Theme.AppCompat.Light.NoActionBar`.

---

## 4. Permisos y manifest

Permisos: `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`, `FLASHLIGHT`,
`USE_FINGERPRINT`, `USE_BIOMETRIC`. `usesCleartextTraffic="true"` y
`allowBackup="true"`. Todos los `Activity` están en `exported="true"` (excepto
`MapaActivity`). `NetworkChangeReceiver` escucha `android.net.conn.CONNECTIVITY_CHANGE`.

Actividades declaradas: `MapaActivity`, `CustomCaptureActivity` (portrait, fullscreen),
`NetworkChangeReceiver`, `lista`, `Inicio`, `aperturaMesa`, `Reporte_flujo`, `Registrar`,
`MainActivity` (LAUNCHER).

---

## 5. Modelo de datos y almacenamiento local

Hay **tres almacenes** (inconsistencia importante a limpiar si se reutiliza):

**A. SQLite `BDsistem`, tabla `usuario`** — vía `daoUsuario` (`openOrCreateDatabase`):
```
create table if not exists usuario(
  id integer primary key autoincrement,
  usuario text, nombre text, telefono text, contrasena text)
```

**B. SQLite `BD1`, tabla `reportes`** — vía `OpenHelpel.SQLite_OpenHelper`:
```
create table reportes(
  _ID integer primary key autoincrement,
  electores text, cedulaTestigo text, hora text, status text)
```
`status` ∈ `Enviado` | `Sin Enviar` | `OP` | `NE`.

**C. SharedPreferences** (nombre → clave):
| Archivo | Clave | Uso |
|---|---|---|
| `credenciales` | `cedula` | cédula del usuario logueado (se lee en casi todo) |
| `apertura` | `mesa` | si ya reportó apertura (`""` vs `"Enviado"`/`"Guardado"`) |
| `user` | `bool` | `"1"` = ya existe un usuario (bloquea más registros) |
| `maxVotos` | `votos` | último valor de electores guardado |

---

## 6. API backend (`https://sigven.net/elecciones/app/…`)

Todas por **POST `application/x-www-form-urlencoded`** vía Volley `StringRequest`.

| Endpoint | Params | Respuesta usada |
|---|---|---|
| `get_rendimiento.php` | `responsable`=cédula | JSON: `centro`, `reportes_del_centro`, `electores_centro`, `total_asistencia_1x10`, `meta_inicial` |
| `flujo_electoral.php` | `responsable`, `elector` | Texto: `OK` \| `NE` (no elector) \| `OP` \| otro |
| `flujo_electoral_pendiente.php` | `responsable`, `electores`=JSON array | JSON: `{results:[{elector,status}]}`, status ∈ OK/NE/OP |
| `apertura.php` | `cedula`, `cantidad` | Texto: `"Reporte de apertura enviado correctamente."` |

Además, `lista.java` hace **GET `https://sigven.net`** con headers
`operador`, `fecha`, `token_fcm`, `Accept: application/json`, `User-Agent` de navegador y
`Connection: close`, y espera `{data:[{nombre, latitud, longitud, telefono}]}`.
Con `latitud`/`longitud` construye `https://gitcom-ve.com/mapa_punto.html?lat=..&lon=..`.

---

## 7. Flujos de pantalla

**Login (`MainActivity`)**
- Si no existe usuario → `Toast` + redirige a `Registrar`.
- `btnEntrar`: valida campos, `dao.login(u,p)==1` → guarda cédula → si `apertura.mesa==""`
  va a `aperturaMesa`, si no a `Inicio`.
- `btnRegistrar`: si `user.bool=="1"` bloquea; si no, va a `Registrar`.
- `btnHuella` → diálogo custom + autenticación biométrica → `iniciarSesionConHuella()`.

**Registro (`Registrar`)** valida campos y contraseña ≥ 6, `dao.insertUsuario`, marca
`user.bool="1"`, vuelve a `MainActivity`.

**Apertura (`aperturaMesa`)** envía `cantidad` a `apertura.php`; guarda `apertura.mesa`;
si falla ofrece reintentar / "No enviar" (guarda igual) y pasa a `Reporte_flujo`.

**Reporte de flujo (`Reporte_flujo`)** — pantalla central:
- Campo `electores` + botón enviar + botón escanear QR (`CustomCaptureActivity`) + vaciar BD.
- Al enviar: `flujo_electoral.php`; según respuesta inserta en `reportes` con status
  (`Enviado`/`OP`; `Sin Enviar` si falla conexión, con popup reintentar/cancelar).
- `NetworkChangeReceiver`: al recuperar conexión llama `enviarDatosNoEnviados()`, que envía
  los pendientes en lote y actualiza estados, recursivamente.
- Historial visible con `ChatAdapter` (burbujas con ícono según status).

**Dashboard (`Inicio`)** consulta `get_rendimiento.php` y pinta metas/porcentajes; botón a
`Reporte_flujo` y a `lista`.

**Lista (`lista`)** GET a `sigven.net`, parsea `data`, arma `User` y `CustomAdapter`
(ver mapa / llamar / WhatsApp). Nota: cédula/fecha/token están **hardcodeados**.

**Mapa (`MapaActivity`)** WebView con JS, `DomStorage` y accesos universales habilitados,
carga la `url_mapa` recibida por Intent.

---

## 8. Deuda técnica / riesgos detectados

Cosas a **corregir o no arrastrar** al reutilizar como base:

1. **Lógica de huella invertida**: en `MainActivity` se usa `FingerprintManager` cuando
   `SDK >= M` y `BiometricPrompt` cuando `SDK < M` (al revés). En la práctica la ruta
   biométrica moderna nunca se usa en equipos actuales.
2. **`daoUsuario.getUsuario` y `getUsuarioById`** tienen `if(...);` con `;` → devuelven el
   primer usuario siempre (bug lógico).
3. **Contraseñas en texto plano** en SQLite y comparación directa en `login`.
4. **`if ('a' == 's')`** en `Reporte_flujo.enviarDatos()` es código muerto (nunca entra).
5. **Datos hardcodeados** en `lista.java` (`cedula="27640176"`, `fecha`, `tokenFCM`).
6. **Tres almacenes e IDs de usuario duplicados** (`daoUsuario` vs `SQLite_OpenHelper`),
   ambos con historial de usuario.
7. **Todos los activities `exported="true"`** sin necesidad → superficie expuesta.
8. `usesCleartextTraffic="true"` y WebView con accesos universales → riesgo de seguridad.
9. Sin commits en git; `jcenter()` obsoleto; `versionCode 1` con versión de UI 4.1.1.
10. Nombres de paquete/BD heredados (`prueba404`, `OpenHelpel` con typo).

---

## 9. Qué conviene reutilizar vs. reescribir

**Reutilizable tal cual (patrón sólido):**
- Patrón **offline-first**: cola local en SQLite + sincronización al recuperar red
  (`Reporte_flujo` + `NetworkChangeReceiver`/`NetworkUtil`).
- Integración **ZXing QR** (`CustomCaptureActivity` + `IntentIntegrator`).
- Diálogos de **loading** y **popup de error** (`loading_screen`, `pop_up_error`).
- `MapaActivity` (WebView de mapa) y adapters de lista.
- Esquema de endpoints/contratos del backend PHP.

**Reescribir al portar:**
- Capa de datos de usuario: unificar en un solo almacén, hashear contraseñas.
- Autenticación biométrica: usar `androidx.biometric.BiometricPrompt` en todas las versiones.
- Cliente HTTP: hoy Volley disperso; considerar centralizar (Retrofit/OkHttp) y quitar
  hardcodes, mover URLs y credenciales a configuración/BuildConfig.
- Seguridad: `exported`, `cleartext`, WebView.
- Renombrar paquete/app y limpiar strings lorem ipsum y recursos sin usar.

---

## 10. Cómo compilar

```powershell
./gradlew :app:assembleDebug
```
Requiere `local.properties` con `sdk.dir` (ya presente en el repo, no versionado).
AGP 7.0.0 / SDK 33. Tests: `./gradlew :app:testDebugUnitTest`.

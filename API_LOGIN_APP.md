# Login nativo + WebView (iSeller POS) — contexto para la app Android

Documento de integración para construir la parte Java/Kotlin. Permite que el usuario se
autentique en el **login nativo** de la app y, sin volver a escribir credenciales, abra el
sistema web dentro de un **WebView ya autenticado**.

---

## 1. Cómo funciona (2 endpoints)

```
┌─────────────┐   POST /api/auth_login.php            ┌──────────────┐
│  App Android│   (usuario + password + X-Api-Key)    │  Servidor PHP │
│  Login nativo│ ────────────────────────────────────▶│              │
│             │ ◀──────────  { token, session_url }   │              │
└──────┬──────┘                                        └──────┬───────┘
       │                                                       │
       │  WebView.loadUrl(session_url)                         │
       ▼                                                       │
┌─────────────┐   GET /login/app_autologin.php?token=…         │
│   WebView   │ ──────────────────────────────────────────────┘
│             │ ◀── 302 (Set-Cookie: PHPSESSID) → /ventas.php
└─────────────┘
```

1. La app llama a `auth_login.php` (JSON). El servidor valida igual que el login web y
   devuelve un **token de un solo uso** (60–90 s) y una URL de canje (`session_url`).
2. La app abre `session_url` en el WebView. Ese endpoint **crea la sesión web**, deja la
   cookie `PHPSESSID` en el WebView y redirige al sistema. A partir de ahí el usuario navega
   autenticado sin pasar por `login.php`.

**Ventaja de seguridad:** el `PHPSESSID` **nunca viaja a la app**; lo emite el propio WebView.
El token caduca en segundos y se invalida al primer uso.

---

## 2. Endpoint 1 — `POST /api/auth_login.php`

### Cabeceras
| Cabecera | Valor |
|---|---|
| `Content-Type` | `application/json` (o `application/x-www-form-urlencoded`) |
| `X-Api-Key` | clave `POS_API_KEY` (obligatoria) |

### Cuerpo (JSON)
| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `usuario` | string | Sí | Usuario/login (email o cédula). También se acepta `login`. |
| `password` | string | Sí | Contraseña. También se acepta `contrasena`. |

```json
{ "usuario": "27640176", "password": "123456" }
```

### Respuesta 200
| Campo | Tipo | Descripción |
|---|---|---|
| `ok` | bool | `true` |
| `token` | string | Token de un solo uso (64 hex) |
| `expira_en` | int | Segundos de validez del token (90) |
| `session_url` | string | URL a cargar en el WebView |
| `redirect` | string | Ruta web final esperada (informativo) |
| `usuario` | object | `id`, `nombre`, `usuario`, `nivel` |
| `negocio` | object | `bss_id`, `sucursal`, `sucursal_nombre` |
| `membresia` | object | `tipo`, `estado`, `esta_vencida`, `dias_restantes`, `fecha_fin` |
| `dark_mode` | bool | Preferencia de tema del usuario |

```json
{
  "ok": true,
  "token": "849acc6a0b520f006a6d528376d49e4bf2aedd756b9e23cbdb147da68b288075",
  "expira_en": 90,
  "session_url": "https://pos.dominio.com/login/app_autologin.php?token=849acc…",
  "redirect": "publico/production/ventas.php",
  "usuario": { "id": 15, "nombre": "Ricardo", "usuario": "27640176", "nivel": 1 },
  "negocio": { "bss_id": 3, "sucursal": null, "sucursal_nombre": null },
  "membresia": { "tipo": "anual", "estado": "activa", "esta_vencida": false, "dias_restantes": 730, "fecha_fin": "2028-10-15 23:59:59" },
  "dark_mode": true
}
```

### Errores
| HTTP | `error` | Motivo |
|---|---|---|
| 400 | `Faltan datos` | Falta usuario o contraseña |
| 401 | `Credenciales incorrectas` | Usuario inexistente o clave mala |
| 401 | `No autorizado` | Falta/incorrecta `X-Api-Key` |
| 403 | `Usuario inactivo por incumplimiento de pago` | `usuarios.status = 1` |
| 405 | `Método no permitido` | No es POST |
| 429 | `Demasiados intentos…` | >15 intentos/5 min por IP |
| 500 | `Error interno` | Fallo del servidor |
| 503 | `API no configurada` | Sin `POS_API_KEY` en el servidor |

> La app debe mostrar `error` y **no** abrir el WebView si `ok` no es `true`.

---

## 3. Endpoint 2 — `GET /login/app_autologin.php?token=<64 hex>`

Se abre **solo dentro del WebView** (o navegador); no se llama por API.

- **Éxito:** `302` con `Set-Cookie: PHPSESSID=…` → redirige a `publico/production/…`
  (`ventas.php` para admin de una sola sucursal; `seleccion_sucursal.php` si hay varias;
  `membresia_renovacion.php` si la membresía venció).
- **Token inválido/reutilizado/expirado:** `302` → `login.php?app_error=token`.
- El token es de **un solo uso**: una segunda llamada con el mismo token falla.

---

## 4. Implementación en Android (Java)

### 4.1 Dependencias
```gradle
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

### 4.2 Login nativo (OkHttp)
```java
public class AuthApi {
    public static final String BASE_URL = "https://pos.dominio.com";
    public static final String API_KEY  = "PON_AQUI_POS_API_KEY";
    private static final OkHttpClient client = new OkHttpClient();

    public static class LoginResult {
        public boolean ok;
        public String sessionUrl;
        public String error;
        public String nombre;
        public int nivel;
        public boolean membresiaVencida;
    }

    public static LoginResult login(String usuario, String password) {
        LoginResult r = new LoginResult();
        try {
            JSONObject body = new JSONObject();
            body.put("usuario", usuario);
            body.put("password", password);

            Request req = new Request.Builder()
                    .url(BASE_URL + "/api/auth_login.php")
                    .addHeader("X-Api-Key", API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                JSONObject json = new JSONObject(resp.body().string());
                r.ok = json.optBoolean("ok", false);
                if (r.ok) {
                    r.sessionUrl = json.getString("session_url");
                    r.nombre = json.getJSONObject("usuario").optString("nombre");
                    r.nivel = json.getJSONObject("usuario").optInt("nivel");
                    r.membresiaVencida = json.getJSONObject("membresia")
                            .optBoolean("esta_vencida", false);
                } else {
                    r.error = json.optString("error", "Error de autenticación");
                }
            }
        } catch (Exception e) {
            r.ok = false;
            r.error = "Sin conexión con el servidor";
        }
        return r;
    }
}
```

### 4.3 Cargar el WebView autenticado
```java
@SuppressLint("SetJavaScriptEnabled")
private void abrirSistema(WebView webView, String sessionUrl) {
    WebSettings s = webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);            // el POS usa localStorage/sessionStorage
    s.setDatabaseEnabled(true);
    s.setLoadWithOverviewMode(true);
    s.setUseWideViewPort(true);

    // Cookies habilitadas (el puente dejará aquí el PHPSESSID)
    CookieManager cm = CookieManager.getInstance();
    cm.setAcceptCookie(true);
    cm.setAcceptThirdPartyCookies(webView, true);

    webView.setWebViewClient(new WebViewClient());

    // Cargar la URL de canje: redirige sola al sistema ya autenticado
    webView.loadUrl(sessionUrl);
}
```

Flujo en la Activity de login:
```java
LoginResult r = AuthApi.login(userEdit.getText().toString(), passEdit.getText().toString());
if (r.ok) {
    startActivity(new Intent(this, PosActivity.class)
            .putExtra("session_url", r.sessionUrl));
    finish();
} else {
    Toast.makeText(this, r.error, Toast.LENGTH_LONG).show();
}
```

### 4.4 Cerrar sesión (importante)
Al salir, limpiar la cookie y la caché del WebView para no dejar la sesión viva:
```java
CookieManager.getInstance().removeAllCookies(null);
CookieManager.getInstance().flush();
webView.clearCache(true);
webView.clearHistory();
```

### 4.5 Notas de integración
- **HTTPS obligatorio en producción**: la cookie se emite con `Secure`, así que en `http://`
  el WebView no la guardará. En desarrollo (`http://localhost`) el servidor la emite sin `Secure`.
- El WebView ya navega autenticado tras el primer `loadUrl(sessionUrl)`; **no inyectes cookies**
  a mano.
- Si el usuario ya estaba logueado, no vuelvas a pedir `auth_login.php` en cada navegación:
  llama una sola vez al abrir la app.
- Para detectar el canje fallido desde el WebView, revisa el `WebViewClient.onPageStarted`:
  si la URL es `login.php?app_error=token`, vuelve a la pantalla de login nativo.
- El `session_url` viene construido con el host de la petición; si prefieres, puedes formarlo
  tú con `BASE_URL + "/login/app_autologin.php?token=" + json.getString("token")`.

---

## 5. Configuración del servidor (resumen)

| Variable `.env` | Uso |
|---|---|
| `POS_API_KEY` | Clave compartida que la app envía en `X-Api-Key` |
| `POS_API_ALLOW_NO_KEY=1` | (solo dev) desactiva la exigencia de clave |

Archivos del servidor:
- `api/auth_login.php` — valida credenciales y emite el token.
- `login/app_autologin.php` — canjea el token y crea la sesión.
- `configurar/AppAuth.php` — almacén de tokens de un solo uso (hash en `/tmp`, TTL 90 s).

### Seguridad aplicada
- Token aleatorio de 256 bits, guardado **hasheado** (no se persiste el valor en claro).
- Un solo uso + caducidad 90 s + limpieza oportunista de expirados.
- El `PHPSESSID` lo emite el servidor al WebView; la app nunca lo maneja.
- Rate limit: 15 intentos/5 min por IP; `auth_login` y las rutas exigen `POS_API_KEY`.
- Consultas preparadas, mensajes de error genéricos, cabeceras `nosniff`/`DENY`/`no-referrer`.
- Registro de intentos en `user_log` (éxito/fallo).

---

## 6. Preguntas frecuentes

**¿Puedo usar solo `session_url` y olvidarme del token?** Sí: el token va en la URL, el canje
es transparente en el WebView. Solo preocúpate de cargar `session_url` una vez.

**¿Qué pasa si hay varias sucursales?** El login web no fija sucursal; el sistema muestra el
selector (`seleccion_sucursal.php`). La app no necesita hacer nada especial.

**¿Y si la membresía está vencida?** El canje redirige a `membresia_renovacion.php` (paywall),
igual que el login web. El campo `membresia.esta_vencida` te permite avisar antes de abrir.

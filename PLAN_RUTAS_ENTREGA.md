# Plan de Implementación por Fases: Sistema de Rutas de Entrega (iSeller POS)

Objetivo: Implementar el flujo de consulta, almacenamiento local y visualización en mapa con teselas en caché persistente usando las herramientas y patrones que ya existen en el proyecto (`Volley`, `org.json`, `WebView`, `FilesDir`) sin añadir librerías externas innecesarias.

---

## Fase 1: Interfaz y Captura de Código (`activity_ruta_entrega.xml` y `RutaEntrega.java`)

### 1.1 Diseño visual (`app/src/main/res/layout/activity_ruta_entrega.xml`)
Reutilizar los estilos existentes en `activity_login.xml` (`@color/pos_bg`, `@drawable/bg_pos_input`, `@drawable/btn_pos_primary`):
- **Aviso informativo:** `TextView` explicando que se accede a las rutas generadas desde el POS con el código del administrador y que, tras cargar el mapa por primera vez, las zonas visualizadas se guardarán para uso sin conexión.
- **Campo de código (`etCodigoRuta`):** `EditText` con `maxLength="9"` (4 números + guion + 4 letras), `hint="0000-AAAA"`.
- **Botón principal (`btnSolicitarRuta`):** `AppCompatButton` ("Cargar ruta").
- **Acceso rápido offline (`rvRutasGuardadas` o contenedor simple):** Lista de rutas ya descargadas en el dispositivo para poder abrirlas sin internet ni volver a pedir al endpoint.

### 1.2 Validación y formato en `RutaEntrega.java`
- `TextWatcher` en `etCodigoRuta`:
  - Inserta automáticamente el `-` después del 4.º carácter si el usuario escribe los primeros 4 dígitos.
  - Convierte las letras finales a minúsculas/mayúsculas de forma consistente.
- Validación estricta antes de enviar con expresión regular: `^\d{4}-[a-zA-Z]{4}$`.

---

## Fase 2: Petición HTTPS y Almacenamiento Local por Ruta

### 2.1 Contrato JSON esperado del Endpoint (Pendiente en Backend)
Petición `POST` o `GET` mediante `Volley` (`StringRequest`) a `https://<dominio-pos>/api/ruta_entrega.php?codigo=0000-aaaa`:
```json
{
  "codigo": "1234-abcd",
  "nombre_ruta": "Ruta Norte - Mañana",
  "pedidos": [
    {
      "id_pedido": "PED-001",
      "cliente": "Juan Pérez",
      "telefono": "04120000000",
      "direccion": "Av. Bolívar, Local 4",
      "monto": "45.00",
      "lat": 10.4806,
      "lon": -66.9036
    }
  ]
}
```
*(Incluir un modo mock/simulación temporal en `RutaEntrega.java` mientras el endpoint real no esté programado).*

### 2.2 Guardado en almacenamiento interno (`getFilesDir()`)
Para cumplir con que los datos **no se borren a menos que el usuario borre los datos de la app**, se guarda en `new File(getFilesDir(), "rutas")` (nunca en `getCacheDir()`, que el SO puede limpiar):
- Al recibir el JSON del servidor, se recorre el arreglo `pedidos` y a cada objeto se le inyecta `"estatus_entrega": "PENDIENTE"` (si no existe ya una versión local previa con avance guardado).
- Se guarda el archivo como `<nombre_ruta_sanitizado>.json` (ej. `Ruta_Norte_-_Manana.json`).
- Si la ruta ya existía en local, se conserva el `estatus_entrega` que el repartidor hubiera marcado previamente.

---

## Fase 3: Mapa OpenLayers y Caché Persistente de Teselas (`MapaActivity`)

En lugar de meter una dependencia pesada de mapas nativos, se reutiliza `MapaActivity.java` y su `WebView` con un archivo local `app/src/main/assets/mapa_ruta.html` que incluye **OpenLayers** (`ol.js` y `ol.css` empaquetados en `assets/` para que el visor funcione 100% offline).

### 3.1 Caché inborrable de teselas a nivel nativo
El caché estándar del `WebView` vive en `getCacheDir()` y Android puede vaciarlo si falta espacio. Para garantizar persistencia real:
- En `MapaActivity.java`, sobrescribir `WebViewClient.shouldInterceptRequest(WebView view, WebResourceRequest request)`.
- Detectar peticiones a teselas (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`).
- Ruta local de cada tesela: `new File(getFilesDir(), "tiles/" + z + "/" + x + "_" + y + ".png")`.
  1. **Si el archivo existe en `getFilesDir()`:** devolver `new WebResourceResponse("image/png", "UTF-8", new FileInputStream(tileFile))` inmediatamente (cero red, funciona offline).
  2. **Si no existe:** descargarlo por `HttpsURLConnection` (con `User-Agent` de la app), guardar los bytes en `getFilesDir()/tiles/{z}/{x}_{y}.png` y devolver el stream al `WebView`.
- De esta forma, cada zona por la que el usuario se desplace en el mapa queda guardada permanentemente en el almacenamiento privado de la app y solo se borra desde *Ajustes → Borrar datos*.

### 3.2 Recorrido por calles (OSRM) y caché
- El recorrido se calcula con el enrutador público `router.project-osrm.org` ordenando los pedidos por cercanía desde la posición del repartidor.
- **No se dibuja ninguna línea recta de relleno:** mientras el enrutador responde no hay línea, y si no se puede calcular se avisa con un Toast (`Sin conexión: el recorrido por calles no está disponible.`). El Toast lo muestra Android a través del puente (`AndroidRuta.mostrarToast`).
- El recorrido calculado se guarda en `localStorage` del WebView (clave = conjunto de pedidos de la ruta, no la posición del repartidor). Así:
  - moverse unos metros no recalcula ni hace parpadear la línea (antes se redibujaba en cada actualización del GPS y se quedaba recta mientras respondía OSRM);
  - sin conexión, si esa ruta ya se calculó alguna vez, se pinta al instante desde la caché.
- Para forzar un recálculo desde la posición actual: apagar y encender el botón de recorrido.

---

## Fase 4: Puntos de la Ruta, Interacción y Estatus de Entrega

### 4.1 Renderizado de puntos en OpenLayers (`assets/mapa_ruta.html`)
- `MapaActivity` recibe por `Intent` el `archivo_ruta` guardado en la Fase 2.
- Expone un `@JavascriptInterface` (`AndroidRuta`):
  - `obtenerJsonRuta()`: devuelve el contenido del `.json` local.
  - `actualizarEstatusPedido(numeroPedido, nuevoEstatus)`: marca la entrega en el `.json` local **y la sube** (`RutaSync`, ver 4.3).
  - `actualizarRuta()`: sube la cola y vuelve a bajar la ruta (botón "Actualizar ruta").
  - `mostrarToast(mensaje)`: Toast nativo pedido desde la página.
- En OpenLayers:
  - Se crea una capa vectorial (`ol.layer.Vector` + `ol.source.Vector`).
  - Por cada pedido con `lat` y `lon`, se añade un `ol.Feature` con geometría `ol.geom.Point(ol.proj.fromLonLat([lon, lat]))`.
  - El color del marcador cambia según `estatus_entrega` (`#049FA5` pendiente, `#2DD4A0` entregado).
  - Al cargar, el mapa encuadra automáticamente todos los puntos (`view.fit(vectorSource.getExtent())`).
  - El toque solo selecciona pedidos: el punto azul de ubicación se ignora (el repartidor suele estar
    justo encima del pin y antes se quedaba con el punto de GPS).

### 4.2 Consulta de datos al hacer clic en un punto
- Evento `map.on('singleclick', ...)` con `map.forEachFeatureAtPixel`:
- Al tocar un punto, se abre un panel/modal inferior (o `BottomSheet`/`AlertDialog` nativo invocado desde el `JavascriptInterface`) que muestra:
  - ID del pedido, Cliente, Teléfono (con botón para llamar/WhatsApp como en `CustomAdapter`), Dirección y Monto.
  - Un único cambio de estado disponible: **Entregado**. El botón queda **desactivado** para ese pedido en
    cuanto se marca; si el servidor lo rechaza (pedido borrado, rechazado…) el panel muestra el motivo.

### 4.3 Cola de entregas y sincronización (100% sincronizado)
`RutaSync` (hilo aparte, con cerrojo en los archivos para no perder marcas) hace siempre lo mismo,
en este orden:
1. **Sube la cola**: los pedidos en `estatus_entrega = ENTREGADO` con `sincronizado != true` se envían a
   `POST /api/pedido_entregado.php` (`X-Api-Key`, `codigo` + `numero_pedido`). Sin conexión quedan en cola
   y se reintentan al abrir la app, al abrir el mapa y cuando Android avisa que volvió la red
   (`ConnectivityManager.registerDefaultNetworkCallback`). Un 4xx de datos no se reintenta: se saca de la
   cola y se guarda el motivo en `sync_error`.
2. **Baja la ruta** y la mezcla: gana lo que el repartidor ya marcó, los pedidos nuevos se añaden
   (`ruta_entrega.php` devuelve `estatus_entrega` por pedido) y los que el POS quitó de la ruta se
   eliminan salvo que estén sin subir.

El mapa se sincroniza al abrirse y con el botón **"Actualizar ruta"** (avisa "N pedidos nuevos" si el POS
agregó alguno). `RutaEntrega` mezcla igual al descargar y sube la cola antes de pedir la ruta.

**Pruebas:** `./gradlew :app:testDebugUnitTest` — `RutaSyncTest` cubre la mezcla (entregas sin subir que
no se pueden perder, pedidos quitados, envío ya confirmado, reindexado de claves) y `RutaJsonTest` las
utilidades (nombre de archivo, forma de `pedidos`, mensajes de error del servidor). La página del mapa
(`mapa_ruta.html`) no tiene pruebas unitarias: se verifica en el dispositivo.

---

## Resumen de Archivos a Tocar / Crear

| Archivo | Acción |
|---|---|
| `app/src/main/res/layout/activity_ruta_entrega.xml` | Agregar texto informativo, input `0000-aaaa`, botón de consulta y lista de rutas locales. |
| `app/src/main/java/com/iseller/pos/RutaEntrega.java` | Máscara/validación `0000-aaaa`, petición Volley HTTPS, mezcla con lo local y guardado de `<nombre_ruta>.json` en `getFilesDir()/rutas/`; lanza `MapaActivity`. |
| `app/src/main/java/com/iseller/pos/MapaActivity.java` | Interceptor `shouldInterceptRequest` para caché de teselas en `getFilesDir()/tiles/`, puente JS y sincronización al abrir el mapa. |
| `app/src/main/java/com/iseller/pos/RutaSync.java` | Cola de entregas (subida) y actualización/mezcla de la ruta (bajada). Ver 4.3. |
| `app/src/main/assets/mapa_ruta.html` (+ `ol.js`/`ol.css`) | Mapa OpenLayers que pinta los `Point` del JSON, el recorrido por calles cacheado y el detalle/estatus al tocar un pin. |
| `app/src/main/AndroidManifest.xml` | Eliminar la declaración duplicada de `.MapaActivity` (líneas 20 y 30). |

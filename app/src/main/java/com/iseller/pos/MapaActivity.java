package com.iseller.pos;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Fases 3 y 4: visor OpenLayers (offline, en assets/) con caché persistente de teselas
 * en getFilesDir()/tiles/, puente JS para leer/actualizar el JSON de la ruta y la
 * posición/rumbo del usuario (GPS + brújula).
 *
 * Sincronización: al abrir el mapa se sube la cola de entregas y se vuelve a bajar la ruta
 * (por si el POS agregó pedidos); lo mismo hace el botón "Actualizar ruta". Ver RutaSync.
 */
public class MapaActivity extends AppCompatActivity {

    public static final String EXTRA_ARCHIVO = "archivo_ruta";

    private static final String HOST_TESELAS = "tile.openstreetmap.org/";
    private static final String USER_AGENT = "iSellerPOS/1.0 (Android)";
    private static final int REQ_UBICACION = 41;

    private WebView webView;
    private String archivoRuta;
    private ConnectivityManager.NetworkCallback redCallback;
    private boolean sincronizadoAlAbrir = false;

    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor sensorRotacion;
    private Location ultimaUbicacion;
    private Float ultimoRumbo;
    private long ultimoEnvio = 0L;
    private boolean paginaLista = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapa);

        archivoRuta = getIntent().getStringExtra(EXTRA_ARCHIVO);

        webView = findViewById(R.id.miWebView);
        if (webView == null) {
            finish();
            return;
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new RutaBridge(), "AndroidRuta");
        webView.setWebViewClient(new MapaWebViewClient());
        webView.loadUrl("file:///android_asset/mapa_ruta.html");

        // Al volver la conexión se sube lo que quedó en cola
        ConnectivityManager conectividad = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (conectividad != null) {
            redCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network red) {
                    subirCola();
                }
            };
            conectividad.registerDefaultNetworkCallback(redCallback);
        }

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            sensorRotacion = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        pedirUbicacion();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (redCallback != null) {
            ConnectivityManager conectividad = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (conectividad != null) {
                try {
                    conectividad.unregisterNetworkCallback(redCallback);
                } catch (Exception ignored) {
                }
            }
        }
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {
            }
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorListener);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------
    // Ubicación y brújula
    // ------------------------------------------------------------------

    private void pedirUbicacion() {
        boolean concedido = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (concedido) {
            iniciarUbicacion();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_UBICACION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_UBICACION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarUbicacion();
        }
        // Si se deniega, el mapa funciona igual (sin el punto de ubicación).
    }

    private void iniciarUbicacion() {
        try {
            Location previa = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (previa == null) {
                previa = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (previa != null) {
                ultimaUbicacion = previa;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 3f, locationListener, Looper.getMainLooper());
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10f, locationListener, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException e) {
            // sin permiso efectivo o proveedor no disponible
        }
        if (sensorManager != null && sensorRotacion != null) {
            sensorManager.registerListener(sensorListener, sensorRotacion, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            ultimaUbicacion = location;
            enviarUbicacion(true);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    private final SensorEventListener sensorListener = new SensorEventListener() {
        private final float[] matriz = new float[9];
        private final float[] orientacion = new float[3];

        @Override
        public void onSensorChanged(SensorEvent event) {
            SensorManager.getRotationMatrixFromVector(matriz, event.values);
            SensorManager.getOrientation(matriz, orientacion);
            float grados = (float) Math.toDegrees(orientacion[0]);
            if (grados < 0) {
                grados += 360f;
            }
            // Solo si el rumbo cambió lo suficiente (evita mandar cada milésima).
            if (ultimoRumbo != null && Math.abs(grados - ultimoRumbo) < 3f) {
                return;
            }
            ultimoRumbo = grados;
            enviarUbicacion(false);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    /** Manda a la página la última posición y rumbo conocidos. */
    private void enviarUbicacion(boolean forzar) {
        if (!paginaLista || webView == null || ultimaUbicacion == null) {
            return;
        }
        long ahora = System.currentTimeMillis();
        if (!forzar && ahora - ultimoEnvio < 200) {
            return;
        }
        ultimoEnvio = ahora;
        String rumbo = (ultimoRumbo != null) ? String.valueOf(ultimoRumbo) : "null";
        String js = "if (window.ubicacionUsuario) { window.ubicacionUsuario("
                + ultimaUbicacion.getLatitude() + "," + ultimaUbicacion.getLongitude() + "," + rumbo + "); }";
        webView.evaluateJavascript(js, null);
    }

    // ------------------------------------------------------------------
    // Puente expuesto a la página como window.AndroidRuta
    // ------------------------------------------------------------------

    private class RutaBridge {

        @JavascriptInterface
        public String obtenerJsonRuta() {
            if (archivoRuta == null) {
                return "{}";
            }
            try {
                return RutaJson.leer(new File(RutaJson.carpeta(MapaActivity.this), archivoRuta));
            } catch (IOException e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void actualizarEstatusPedido(String numeroPedido, String nuevoEstatus) {
            if (archivoRuta == null) {
                return;
            }
            if ("ENTREGADO".equals(nuevoEstatus)) {
                RutaSync.marcarEntregado(archivoDeRuta(), numeroPedido);
                subirCola();
            }
        }

        @JavascriptInterface
        public void actualizarRuta() {
            if (archivoRuta != null) {
                MapaActivity.this.actualizarRuta();
            }
        }

        @JavascriptInterface
        public void mostrarToast(String mensaje) {
            aviso(mensaje);
        }
    }

    // ------------------------------------------------------------------
    // Sincronización con el servidor (ver RutaSync)
    // ------------------------------------------------------------------

    private File archivoDeRuta() {
        return new File(RutaJson.carpeta(this), archivoRuta);
    }

    /** Sube la cola de entregas hechas en este dispositivo y refresca el estado en la página. */
    private void subirCola() {
        if (archivoRuta == null) {
            return;
        }
        RutaSync.enviarPendientes(this, archivoDeRuta(), (numero, ok, error) -> evaluar(
                "window.actualizarEstadoPedido(" + JSONObject.quote(numero) + "," + ok + ","
                        + JSONObject.quote(error == null ? "" : error) + ")"));
    }

    /** Primero sube la cola y después vuelve a bajar la ruta (por si hay pedidos nuevos). */
    private void actualizarRuta() {
        RutaSync.actualizar(archivoDeRuta(), (nuevos, fallo) -> {
            if (fallo != null) {
                aviso(fallo);
            } else if (nuevos == 1) {
                aviso("Ruta actualizada: 1 pedido nuevo.");
            } else if (nuevos > 1) {
                aviso("Ruta actualizada: " + nuevos + " pedidos nuevos.");
            }
            evaluar("window.recargarRuta()");
        });
    }

    private void evaluar(final String js) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(js, null);
            }
        });
    }

    private void aviso(final String mensaje) {
        runOnUiThread(() -> Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show());
    }

    /** Teselas desde getFilesDir()/tiles/ + apertura de teléfono/WhatsApp. */
    private class MapaWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            paginaLista = true;
            enviarUbicacion(true);
            // 100% sincronizado al abrir: sube la cola y trae los pedidos nuevos del POS
            if (!sincronizadoAlAbrir && archivoRuta != null) {
                sincronizadoAlAbrir = true;
                actualizarRuta();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.startsWith("tel:") || url.startsWith("whatsapp:") || url.startsWith("mailto:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getApplicationContext().startActivity(intent);
                } catch (Exception ignored) {
                    // no hay app que maneje el esquema
                }
                return true;
            }
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (!esTesela(url)) {
                return null;
            }
            return servirTesela(url);
        }

        private boolean esTesela(String url) {
            return url.contains(HOST_TESELAS) && url.endsWith(".png");
        }

        private WebResourceResponse servirTesela(String url) {
            int idx = url.indexOf(HOST_TESELAS);
            String[] partes = url.substring(idx + HOST_TESELAS.length()).split("/");
            if (partes.length < 3) {
                return null;
            }
            String z = partes[0];
            String x = partes[1];
            String y = partes[2].replace(".png", "");

            File carpeta = new File(getApplicationContext().getFilesDir(), "tiles/" + z);
            File archivo = new File(carpeta, x + "_" + y + ".png");

            if (archivo.exists()) {
                try {
                    return respuestaPng(new FileInputStream(archivo));
                } catch (IOException ignored) {
                    // si no se puede leer, se intenta descargar
                }
            }

            byte[] datos = descargar(url);
            if (datos == null) {
                return null;
            }

            try {
                carpeta.mkdirs();
                FileOutputStream out = new FileOutputStream(archivo);
                try {
                    out.write(datos);
                } finally {
                    out.close();
                }
            } catch (IOException ignored) {
                // si no se puede guardar, igual se devuelve la tesela recién descargada
            }

            return respuestaPng(new ByteArrayInputStream(datos));
        }

        /** Respuesta PNG con CORS habilitado (la página es file://, origen "null"). */
        private WebResourceResponse respuestaPng(InputStream datos) {
            WebResourceResponse respuesta = new WebResourceResponse("image/png", null, datos);
            Map<String, String> cabeceras = new HashMap<>();
            cabeceras.put("Access-Control-Allow-Origin", "*");
            respuesta.setResponseHeaders(cabeceras);
            return respuesta;
        }

        private byte[] descargar(String url) {
            HttpURLConnection conexion = null;
            try {
                conexion = (HttpURLConnection) new URL(url).openConnection();
                conexion.setConnectTimeout(15000);
                conexion.setReadTimeout(15000);
                conexion.setRequestProperty("User-Agent", USER_AGENT);
                if (conexion.getResponseCode() != 200) {
                    return null;
                }
                InputStream in = conexion.getInputStream();
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int leidos;
                    while ((leidos = in.read(buffer)) != -1) {
                        out.write(buffer, 0, leidos);
                    }
                    return out.toByteArray();
                } finally {
                    in.close();
                }
            } catch (Exception e) {
                return null;
            } finally {
                if (conexion != null) {
                    conexion.disconnect();
                }
            }
        }
    }
}

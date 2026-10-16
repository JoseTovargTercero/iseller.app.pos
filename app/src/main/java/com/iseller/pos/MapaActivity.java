package com.iseller.pos;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

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
 * en getFilesDir()/tiles/ y puente JS para leer/actualizar el JSON de la ruta.
 */
public class MapaActivity extends AppCompatActivity {

    public static final String EXTRA_ARCHIVO = "archivo_ruta";

    private static final String HOST_TESELAS = "tile.openstreetmap.org/";
    private static final String USER_AGENT = "iSellerPOS/1.0 (Android)";

    private WebView webView;
    private String archivoRuta;

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
        webView.setWebViewClient(new MapaWebViewClient(this));
        webView.loadUrl("file:///android_asset/mapa_ruta.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** Puente expuesto a la página como window.AndroidRuta. */
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
            File archivo = new File(RutaJson.carpeta(MapaActivity.this), archivoRuta);
            try {
                JSONObject ruta = new JSONObject(RutaJson.leer(archivo));
                JSONObject pedidos = RutaJson.normalizarPedidos(ruta.opt("pedidos"));
                if (pedidos == null) {
                    return;
                }
                Iterator<String> claves = pedidos.keys();
                while (claves.hasNext()) {
                    JSONObject pedido = pedidos.getJSONObject(claves.next());
                    if (numeroPedido.equals(pedido.optString("numero_pedido"))) {
                        pedido.put("estatus_entrega", nuevoEstatus);
                        break;
                    }
                }
                ruta.put("pedidos", pedidos);
                RutaJson.escribir(archivo, ruta.toString());
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Sirve las teselas desde getFilesDir()/tiles/ (descarga+cachea si faltan) y
     * abre teléfono/WhatsApp en apps externas.
     */
    private static class MapaWebViewClient extends WebViewClient {

        private final Context context;

        MapaWebViewClient(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.startsWith("tel:") || url.startsWith("whatsapp:") || url.startsWith("mailto:")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
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

            File carpeta = new File(context.getFilesDir(), "tiles/" + z);
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

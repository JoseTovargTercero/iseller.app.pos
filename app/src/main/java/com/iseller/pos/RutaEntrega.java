package com.iseller.pos;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fases 1 y 2: interfaz/captura del código de ruta, petición al endpoint y
 * guardado local en getFilesDir()/rutas/ (persistente).
 *
 * El contrato del endpoint está en el README de la API (api/README.md).
 */
public class RutaEntrega extends AppCompatActivity {

    private static final String URL_RUTA = BuildConfig.POS_BASE_URL + "/api/ruta_entrega.php?codigo=";
    private static final Pattern FORMATO_CODIGO = Pattern.compile("^\\d{4}-[a-zA-Z]{4}$");

    private EditText etCodigoRuta;
    private LinearLayout contenedorRutas;
    private TextView emptyRutas;
    private boolean ajustandoCodigo = false;
    private boolean borrandoCodigo = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ruta_entrega);

        etCodigoRuta = findViewById(R.id.etCodigoRuta);
        contenedorRutas = findViewById(R.id.contenedorRutas);
        emptyRutas = findViewById(R.id.emptyRutas);

        configurarMascaraCodigo();

        findViewById(R.id.btnSolicitarRuta).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                solicitarRuta();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarRutasGuardadas();
    }

    // ------------------------------------------------------------------
    // Fase 1: máscara y validación del código
    // ------------------------------------------------------------------

    /** Máscara: inserta "-" tras los 4 dígitos y normaliza las letras a mayúsculas. */
    private void configurarMascaraCodigo() {
        etCodigoRuta.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                borrandoCodigo = count > after;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ajustandoCodigo) {
                    return;
                }
                ajustandoCodigo = true;

                // Guion automático tras los 4 primeros dígitos.
                if (s.length() >= 5 && Character.isDigit(s.charAt(3)) && s.charAt(4) != '-') {
                    s.insert(4, "-");
                } else if (s.length() == 4 && !borrandoCodigo && Character.isDigit(s.charAt(3))) {
                    s.append('-');
                }

                // Letras finales en mayúscula (edición in-place para no perder caracteres).
                for (int i = 5; i < s.length(); i++) {
                    char c = s.charAt(i);
                    char up = Character.toUpperCase(c);
                    if (c != up) {
                        s.replace(i, i + 1, String.valueOf(up));
                    }
                }

                ajustandoCodigo = false;
            }
        });
    }

    private void solicitarRuta() {
        String codigo = etCodigoRuta.getText().toString().trim();
        if (!FORMATO_CODIGO.matcher(codigo).matches()) {
            etCodigoRuta.setError("Formato: 0000-AAAA");
            etCodigoRuta.requestFocus();
            return;
        }

        if (BuildConfig.POS_API_KEY.isEmpty()) {
            Toast.makeText(this, "Falta configurar POS_API_KEY en local.properties.", Toast.LENGTH_LONG).show();
            return;
        }

        // Antes de pedir la ruta se sube lo que quedó en cola de otras rutas
        RutaSync.enviarPendientes(this);

        StringRequest peticion = new StringRequest(Request.Method.GET, URL_RUTA + codigo,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        procesarRespuesta(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(RutaEntrega.this,
                                RutaJson.mensajeDeError(error, "No se pudo cargar la ruta. Revisa tu conexión."),
                                Toast.LENGTH_LONG).show();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> cabeceras = new HashMap<>();
                cabeceras.put("Accept", "application/json");
                // La clave va en cabecera (no en la URL) para no quedar en logs de servidor/proxy.
                cabeceras.put("X-Api-Key", BuildConfig.POS_API_KEY);
                return cabeceras;
            }
        };
        Volley.newRequestQueue(this).add(peticion);
    }

    // ------------------------------------------------------------------
    // Fase 2: procesado de la respuesta y guardado local
    // ------------------------------------------------------------------

    private void procesarRespuesta(String respuesta) {
        try {
            JSONObject ruta = new JSONObject(respuesta);

            if (ruta.has("error")) {
                Toast.makeText(this, ruta.optString("error", "Error del servidor"), Toast.LENGTH_LONG).show();
                return;
            }

            String nombre = ruta.optString("nombre", "Ruta sin nombre");
            JSONObject pedidos = RutaJson.normalizarPedidos(ruta.opt("pedidos"));
            if (pedidos == null || pedidos.length() == 0) {
                Toast.makeText(this, "La ruta no tiene pedidos.", Toast.LENGTH_LONG).show();
                return;
            }

            File archivo = new File(RutaJson.carpeta(this), RutaJson.nombreArchivo(nombre));
            boolean existia = archivo.exists();

            // Mezcla con lo que ya había en el dispositivo: las entregas locales se respetan
            // y los pedidos nuevos se cuentan (mismo criterio que al actualizar desde el mapa).
            JSONObject locales = null;
            if (existia) {
                try {
                    locales = RutaJson.normalizarPedidos(new JSONObject(RutaJson.leer(archivo)).opt("pedidos"));
                } catch (Exception ignored) {
                    // archivo corrupto: se regenera desde el servidor
                }
            }
            int nuevos = RutaSync.mezclar(ruta, locales, pedidos);
            RutaJson.escribir(archivo, ruta.toString());

            String mensaje = (existia && nuevos > 0)
                    ? "Ruta actualizada: " + nuevos + (nuevos == 1 ? " pedido nuevo" : " pedidos nuevos")
                    : "Ruta guardada: " + nombre;
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
            etCodigoRuta.setText("");
            cargarRutasGuardadas();
        } catch (Exception e) {
            Toast.makeText(this, "Error al procesar la ruta.", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------
    // Listado de rutas guardadas (offline)
    // ------------------------------------------------------------------

    private void cargarRutasGuardadas() {
        contenedorRutas.removeAllViews();

        File[] archivos = RutaJson.carpeta(this).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                return name.toLowerCase().endsWith(".json");
            }
        });

        if (archivos == null || archivos.length == 0) {
            emptyRutas.setVisibility(View.VISIBLE);
            return;
        }
        emptyRutas.setVisibility(View.GONE);

        Arrays.sort(archivos, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });

        for (final File f : archivos) {
            View fila = getLayoutInflater().inflate(R.layout.item_ruta_guardada, contenedorRutas, false);
            ((TextView) fila.findViewById(R.id.nombreRuta)).setText(nombreDeRuta(f));
            fila.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(RutaEntrega.this, MapaActivity.class);
                    intent.putExtra(MapaActivity.EXTRA_ARCHIVO, f.getName());
                    startActivity(intent);
                }
            });
            contenedorRutas.addView(fila);
        }
    }

    private String nombreDeRuta(File archivo) {
        try {
            JSONObject ruta = new JSONObject(RutaJson.leer(archivo));
            String nombre = ruta.optString("nombre", "");
            if (!nombre.isEmpty()) {
                return nombre;
            }
        } catch (Exception ignored) {
        }
        return archivo.getName().replaceFirst("(?i)\\.json$", "").replace('_', ' ');
    }
}

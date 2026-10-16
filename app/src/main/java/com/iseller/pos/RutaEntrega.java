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

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fases 1 y 2: interfaz/captura del código de ruta, petición al endpoint y
 * guardado local en getFilesDir()/rutas/ (persistente).
 *
 * Mientras el endpoint real no exista, USAR_MOCK simula la respuesta JSON.
 * Contrato del endpoint: ver API_RUTA_ENTREGA.md
 */
public class RutaEntrega extends AppCompatActivity {

    private static final boolean USAR_MOCK = true; // Fase 2: poner en false cuando exista el endpoint
    private static final String URL_RUTA = "https://TU-DOMINIO-POS/api/ruta_entrega.php?codigo=";
    private static final Pattern FORMATO_CODIGO = Pattern.compile("^\\d{4}-[a-zA-Z]{4}$");
    private static final String ESTATUS_PENDIENTE = "PENDIENTE";

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

        if (USAR_MOCK) {
            procesarRespuesta(generarMock());
            return;
        }

        if (BuildConfig.POS_API_KEY.isEmpty()) {
            Toast.makeText(this, "Falta configurar POS_API_KEY en local.properties.", Toast.LENGTH_LONG).show();
            return;
        }

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
                        Toast.makeText(RutaEntrega.this, mensajeDeError(error), Toast.LENGTH_LONG).show();
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

    /** Muestra el mensaje del servidor ({"error": ...}) si viene en el cuerpo del error. */
    private String mensajeDeError(VolleyError error) {
        NetworkResponse respuesta = error.networkResponse;
        if (respuesta != null && respuesta.data != null) {
            try {
                String mensaje = new JSONObject(new String(respuesta.data, "UTF-8")).optString("error", "");
                if (!mensaje.isEmpty()) {
                    return mensaje;
                }
            } catch (Exception ignored) {
                // cuerpo no JSON
            }
            return "Error del servidor (" + respuesta.statusCode + ").";
        }
        return "No se pudo cargar la ruta. Revisa tu conexión.";
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
            JSONObject estatusPrevios = cargarEstatusPrevios(archivo);

            Iterator<String> claves = pedidos.keys();
            while (claves.hasNext()) {
                String clave = claves.next();
                JSONObject pedido = pedidos.getJSONObject(clave);
                String numero = pedido.optString("numero_pedido", clave);
                // Conserva el avance previo si la ruta ya estaba guardada.
                pedido.put("estatus_entrega", estatusPrevios.optString(numero, ESTATUS_PENDIENTE));
            }

            ruta.put("pedidos", pedidos);
            RutaJson.escribir(archivo, ruta.toString());

            Toast.makeText(this, "Ruta guardada: " + nombre, Toast.LENGTH_LONG).show();
            etCodigoRuta.setText("");
            cargarRutasGuardadas();
        } catch (JSONException | java.io.IOException e) {
            Toast.makeText(this, "Error al procesar la ruta.", Toast.LENGTH_LONG).show();
        }
    }

    private JSONObject cargarEstatusPrevios(File archivo) {
        JSONObject estatus = new JSONObject();
        if (!archivo.exists()) {
            return estatus;
        }
        try {
            JSONObject ruta = new JSONObject(RutaJson.leer(archivo));
            JSONObject pedidos = RutaJson.normalizarPedidos(ruta.opt("pedidos"));
            if (pedidos != null) {
                Iterator<String> claves = pedidos.keys();
                while (claves.hasNext()) {
                    String clave = claves.next();
                    JSONObject pedido = pedidos.getJSONObject(clave);
                    estatus.put(pedido.optString("numero_pedido", clave),
                            pedido.optString("estatus_entrega", ESTATUS_PENDIENTE));
                }
            }
        } catch (Exception ignored) {
            // archivo corrupto o ilegible -> se regenera desde cero
        }
        return estatus;
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

    // ------------------------------------------------------------------
    // Mock temporal (Fase 2)
    // ------------------------------------------------------------------

    /** Simula la respuesta del endpoint. Ver API_RUTA_ENTREGA.md. */
    private String generarMock() {
        try {
            JSONObject ruta = new JSONObject();
            ruta.put("fecha_ruta", "2026-10-20");
            ruta.put("nombre", "Ruta Norte - Mañana");

            JSONObject pedidos = new JSONObject();
            pedidos.put("0", mockPedido("10.4806,-66.9036", "Av. Bolívar, Local 4",
                    "Frente a la plaza", "Juan Pérez", "04120000000", "PED-001"));
            pedidos.put("1", mockPedido("10.4899,-66.9210", "Calle 5 con Av. 2",
                    "Casa de reja negra", "María Gómez", "04140000000", "PED-002"));
            pedidos.put("2", mockPedido("10.4700,-66.8900", "Urb. Los Chaguaramos, Torre B",
                    "Piso 3, apartamento 3B", "Carlos Rivas", "04260000000", "PED-003"));
            pedidos.put("3", mockPedido("10.4950,-66.9120", "Av. Sucre, Galpón 2",
                    "Portón azul", "Ana Torres", "04120001111", "PED-004"));
            ruta.put("pedidos", pedidos);

            return ruta.toString();
        } catch (JSONException e) {
            return "{\"error\":\"mock\"}";
        }
    }

    private JSONObject mockPedido(String coords, String direccion, String referencia,
                                  String cliente, String telefono, String numero) throws JSONException {
        JSONObject pedido = new JSONObject();
        pedido.put("coords", coords);
        pedido.put("direccion", direccion);
        pedido.put("puntoreferencia", referencia);
        pedido.put("nombre_cliente", cliente);
        pedido.put("telefono_cliente", telefono);
        pedido.put("numero_pedido", numero);
        return pedido;
    }
}

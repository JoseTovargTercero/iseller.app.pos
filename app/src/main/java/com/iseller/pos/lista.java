package com.iseller.pos;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler; // Importado para el retraso del ciclo de vida
import android.os.Looper;  // Importado para el hilo principal
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class lista extends AppCompatActivity {
    String cedulaUser;
    private ListView listView;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lista);

        cedulaUser = cargarUsuario();
        listView = findViewById(R.id.listView);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Cargando listado, por favor espere...");
        progressDialog.setCancelable(false);

        // Activa el modo rastreo de Volley en el Logcat
        com.android.volley.VolleyLog.DEBUG = true;

        // ðŸ› ï¸ AJUSTE 1: Le damos 350ms a Android para que estabilice el renderizado (Evita Expecting binder but got null!)
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                cargarLista();
            }
        }, 350);
    }

    private String cargarUsuario() {
        SharedPreferences preferences = getSharedPreferences("credenciales", Context.MODE_PRIVATE);
        return preferences.getString("cedula", "no existe");
    }

    public void cargarLista() {
        final String cedula = "27640176";
        final String fechaNacimiento = "1998-10-27";
        final String tokenFCM = "xaxsaxasxasxasx";

        if (progressDialog != null && !progressDialog.isShowing()) {
            progressDialog.show();
        }

        // ðŸ› ï¸ AJUSTE 2: Usamos getApplicationContext() en lugar de 'this' para proteger la cola de red de fugas de memoria
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        String url = "https://sigven.net";
      //  String url = "https://httpbin.org/get";
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }

                        Log.d("respuesta", response);

                        try {
                            JSONObject jsonResponse = new JSONObject(response);

                            // ðŸ› ï¸ AJUSTE 3: Cambiado de "pendientes" a "data" para coincidir exactamente con el JSON de Laravel
                            JSONArray jsonArray = jsonResponse.getJSONArray("data");

                            List<User> items = new ArrayList<>();
                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject item = jsonArray.getJSONObject(i);
                                String name = item.getString("nombre");
                                String lat = item.getString("latitud");
                                String lon = item.getString("longitud");

                                String urlMap = "";
                                if (lat.equals("NG") || lon.equals("NG")) {
                                    urlMap = "";
                                } else {
                                    urlMap = "https://gitcom-ve.com/mapa_punto.html?lat=" + lat + "&lon=" + lon;
                                }

                                String jefe_numero = item.getString("telefono");
                                if (jefe_numero.equals("null")) {
                                    jefe_numero = "";
                                }

                                items.add(new User(name, jefe_numero, urlMap));
                            }

                            CustomAdapter adapter = new CustomAdapter(lista.this, items);
                            listView.setAdapter(adapter);

                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(lista.this, "Error al procesar los datos del servidor.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {

                Log.e("VOLLEY", error.toString());

                Throwable cause = error.getCause();

                while (cause != null) {
                    Log.e("VOLLEY_CAUSE", cause.toString());
                    cause = cause.getCause();
                }
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("operador", cedula);
                headers.put("fecha", fechaNacimiento);
                headers.put("token_fcm", tokenFCM);
                headers.put("Accept", "application/json");
                headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");


                headers.put("Connection", "close");

                headers.put("Accept-Encoding", "identity");

                return headers;
            }
        };

        int timeoutMs = 6000;
        stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                timeoutMs,
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        // ðŸ› ï¸ AJUSTE 4: Deshabilitamos la cachÃ© local de Volley para obligar la salida fÃ­sica del socket de red
        stringRequest.setShouldCache(false);

        // Ejecutar la peticiÃ³n
        queue.add(stringRequest);
    }
}
package com.iseller.pos;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.iseller.pos.OpenHelpel.SQLite_OpenHelper;

import org.json.JSONException;
import org.json.JSONObject;


import java.util.HashMap;
import java.util.Map;


public class Inicio extends AppCompatActivity {
    String cedulaUser;
    TextView electores_centro, unopordiez, reportes_realizados, centro, asistencia_1x10, asistencia_centro, tus_reortes, unopordiez_porcentage, ultima_act;

    RequestQueue requestQueue;
    SQLite_OpenHelper db;

    ChatAdapter adapter;

    SQLite_OpenHelper helper = new SQLite_OpenHelper(this, "BD1", null, 1);

    private ProgressBar progressBar;
    Button btn_report, btn_update;



    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inicio);

        cedulaUser = cargarUsuario();

        centro = findViewById(R.id.centro);
        electores_centro = findViewById(R.id.electores_centro);
        unopordiez = findViewById(R.id.unopordiez);
        reportes_realizados = findViewById(R.id.reportes_realizados);
        asistencia_1x10= findViewById(R.id.asistencia_1x10);
        asistencia_centro=findViewById(R.id.asistencia_centro);
        progressBar = findViewById(R.id.progress_bar);
        tus_reortes = findViewById(R.id.tus_reortes);
        unopordiez_porcentage = findViewById(R.id.unopordiez_porcentage);

      //  enviarDatos();


        btn_report=(Button)findViewById(R.id.btn_report);
        btn_update=(Button)findViewById(R.id.btn_update);

        db = new SQLite_OpenHelper(this, "BD1", null, 1);
        Cursor cursor = db.cantidadReporte("reportes");
        int count = cursor.getCount(); // Obtener el nÃºmero de elementos
        tus_reortes.setText(String.valueOf(count));


    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.btn_report:;
                startActivity(new Intent(Inicio.this, Reporte_flujo.class));
                break;
            case R.id.btn_update:
                enviarDatos();
                break;
            case R.id.lista:
                startActivity(new Intent(Inicio.this, lista.class));
                break;


        }
    }


    public static String calcularPorcentajeCadena(int total, int parte, String type) {
        if (total == 0) {
            return "0.00%"; // Evitar la divisiÃ³n por cero
        }

        double porcentaje = ((double) parte / total) * 100;
        return String.format("%.2f%%", porcentaje);
    }

    public static int calcularPorcentaje(int total, int parte, String type) {
        if (total == 0) {
            return 0; // Evitar la divisiÃ³n por cero y devolver 0 como valor por defecto
        }

        double porcentaje = ((double) parte / total) * 100;

        if ("i".equals(type)) { // Comparar con el contenido de la cadena
            // Retornar como entero si 'type' es 'i'
            return (int) porcentaje;
        } else {
            // Si 'type' no es 'i', puedes optar por retornar un valor predeterminado
            return (int) porcentaje; // Cambiar segÃºn la lÃ³gica que necesites
        }
    }

    public void enviarDatos() {
        final String cedula = cedulaUser;

        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "https://sigven.net/elecciones/app/get_rendimiento.php";
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onResponse(String response) {

                        Cursor cursor = db.cantidadReporte("reportes");
                        int count = cursor.getCount(); // Obtener el nÃºmero de elementos
                        tus_reortes.setText(String.valueOf(count));



                        try {
                            JSONObject jsonResponse = new JSONObject(response);

                            String centroValue = jsonResponse.getString("centro");
                            String reportesCentro = jsonResponse.getString("reportes_del_centro");
                            String electoresCentroTotal = jsonResponse.getString("electores_centro");
                            String total_asistencia_1x10 = jsonResponse.getString("total_asistencia_1x10");
                            String meta_inicial = jsonResponse.getString("meta_inicial");
                            //String total1x10Centro = jsonResponse.getString("total_1x10_centro");
                            //String hora = jsonResponse.getString("hora");

                            electores_centro.setText(electoresCentroTotal);



                            // Realizar la resta de meta
                            Integer resta_meta = Integer.parseInt(jsonResponse.getString("meta_inicial")) - Integer.parseInt(jsonResponse.getString("total_asistencia_1x10"));
                            TextView resta_meta_text_view = findViewById(R.id.resta_meta);
                            resta_meta_text_view.setText(resta_meta.toString());




                            // Obtener los valores del JSON y convertirlos a enteros
                            Integer electores_centro_int = Integer.parseInt(jsonResponse.getString("electores_centro"));
                            Integer reportes_del_centro_int = Integer.parseInt(jsonResponse.getString("reportes_del_centro"));

                            // Realizar la resta de centro
                            Integer resta_centro = electores_centro_int - reportes_del_centro_int;

                            // Actualizar el TextView con la resta de centro
                            TextView resta_centro_text_view = findViewById(R.id.resta_centro);
 //                           resta_centro_text_view.setText(resta_centro.toString());

                            // Calcular el porcentaje de los que faltan y actualizar el TextView correspondiente
                            String porcentaje_faltan_str = String.valueOf(calcularPorcentajeCadena(electores_centro_int, resta_centro, "f"));
                            TextView resta_centro_porcentaje_text_view = findViewById(R.id.resta_centro_pocentaje);
                            resta_centro_porcentaje_text_view.setText(porcentaje_faltan_str);
                            /* BLOQUE DEDICADO AL PORCENTAJE Y TOTAL RESTANTE */


                            String porcentaje_participacion_total = String.valueOf(calcularPorcentajeCadena(Integer.parseInt(electoresCentroTotal), Integer.parseInt(reportesCentro), "f"));
                            asistencia_centro.setText(porcentaje_participacion_total);

                            resta_centro_text_view.setText(porcentaje_participacion_total);


                            Integer porcentajeAvance = calcularPorcentaje(Integer.parseInt(meta_inicial), Integer.parseInt(total_asistencia_1x10), "i");
                            progressBar.setProgress(porcentajeAvance);


                            // porcentage de participacion del unopordiez_porcentage
                            String porcentaje_participacion = String.valueOf(calcularPorcentajeCadena(Integer.parseInt(meta_inicial), Integer.parseInt(total_asistencia_1x10), "f"));
                            unopordiez_porcentage.setText(porcentaje_participacion);



                            asistencia_1x10.setText(" " + total_asistencia_1x10);
                            centro.setText(centroValue);
                            unopordiez.setText(total_asistencia_1x10 + "/" + meta_inicial);
                            reportes_realizados.setText(reportesCentro);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(Inicio.this, "Error parsing JSON", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(Inicio.this, "Para ver el estado del centro, debe disponer de una conexiÃ³n a internet.", Toast.LENGTH_SHORT).show();


                Cursor cursor = db.cantidadReporte("reportes");
                int count = cursor.getCount(); // Obtener el nÃºmero de elementos
                tus_reortes.setText(String.valueOf(count));



            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> paramV = new HashMap<>();
                paramV.put("responsable", cedula);
                return paramV;
            }
        };

        queue.add(stringRequest);
    }


    private String cargarUsuario() {
        SharedPreferences preferences = getSharedPreferences("credenciales", Context.MODE_PRIVATE);
        return preferences.getString("cedula", "no existe");
    }
}

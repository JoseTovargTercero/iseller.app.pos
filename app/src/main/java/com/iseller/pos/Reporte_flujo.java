package com.iseller.pos;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.iseller.pos.OpenHelpel.SQLite_OpenHelper;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.net.ConnectivityManager;
import android.content.IntentFilter;

public class Reporte_flujo extends AppCompatActivity implements NetworkChangeReceiver.NetworkChangeListener {

    EditText e1;

    ImageButton b;
    Button botonScaner, botonVaciar;


    Dialog myDialog;

    RequestQueue requestQueue;
    SQLite_OpenHelper db;

    ListView listadoReportes;
    ArrayList<String> listItem;
    ChatAdapter adapter;

    SQLite_OpenHelper helper = new SQLite_OpenHelper(this, "BD1", null, 1);

    Date date = new Date();
    SimpleDateFormat h = new SimpleDateFormat("hh:mm a");
    String sHora = h.format(date);

    String cedulaUser;
    private NetworkChangeReceiver networkChangeReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reporte);

        myDialog = new Dialog(this);

        e1 = findViewById(R.id.electores);
        b = findViewById(R.id.acceso);
        botonScaner=(Button)findViewById(R.id.btnScan);
        botonVaciar=(Button)findViewById(R.id.vaciar);

        listadoReportes = findViewById(R.id.listadoReportes);

        db = new SQLite_OpenHelper(this, "BD1", null, 1);

        listItem = new ArrayList<>();
        viewData();

        relacionamosVistas();

        cedulaUser = cargarUsuario();



        networkChangeReceiver = new NetworkChangeReceiver(this);

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enviarDatos();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(networkChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(networkChangeReceiver);
    }

    @Override
    public void onNetworkChange(boolean isConnected) {
        if (isConnected) {
            enviarDatosNoEnviados();
            Toast.makeText(getApplicationContext(), "Conectado", Toast.LENGTH_LONG).show();
        }else{
            Toast.makeText(getApplicationContext(), "Desconectado", Toast.LENGTH_LONG).show();
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.btnScan:
                IntentIntegrator integrator = new IntentIntegrator(this);
                integrator.setCaptureActivity(CustomCaptureActivity.class);
                integrator.setPrompt("Escanea el qr que aparece al final de la papeleta");
                integrator.setBeepEnabled(true);
                integrator.setCameraId(0);  // Opcional: usar la cÃ¡mara trasera
                integrator.initiateScan();
                break;
            case R.id.vaciar:
                vaciarBd();
                break;
        }
    }




    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        String datos = result.getContents();
        Toast.makeText(getApplicationContext(), datos, Toast.LENGTH_LONG).show();


  /*      resultadoScaner.setText(datos);

        if(!resultadoScaner.getText().toString().equals("")){
            int logPass = resultadoScaner.getText().toString().length();
            if(logPass > 13){
                botonScaner.setTextColor(Color.GRAY);
                botonScanner.setBackgroundResource(R.drawable.btn_style_secondary);
                botonScaner.setEnabled(false);
                layout_orden.setVisibility(View.GONE);
                layout_seleccion.setVisibility(View.GONE);
                layout_fin.setVisibility(View.VISIBLE);
                // EnviarCierre();
                Toast.makeText(getApplicationContext(), "Escaneado Correctamente",Toast.LENGTH_LONG).show();
                EnviarCierre();
            }else{

                Toast.makeText(getApplicationContext(), "Intentelo nuevamente",Toast.LENGTH_LONG).show();
            }

        }
*/
    }


/*
    public void EnviarCierre(){
        ShowLoading("ON");
        RequestQueue queue = Volley.newRequestQueue(this);
        String url="https://quits-wreck.000webhostapp.com/php/ingreso_cierre.php";
        final String ced=cedula.getText().toString();
        final String resultScan=resultadoScaner.getText().toString();
        final String tipoCargo=tipo.getText().toString();
        final String ordenCandidatos=resultadosGobernador.getText().toString();


        StringQrGenerado = tipoCargo + ced + "Â°" + resultScan + "Â°" + ordenCandidatos;

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {


                        if(response.equals("Datos enviados correctamente.")){

                            Toast.makeText(getApplicationContext(), response, Toast.LENGTH_SHORT).show();
                            ShowLoading("OFF");

                            limpiarSeleccion();


                        }else{
                            Toast.makeText(getApplicationContext(), response, Toast.LENGTH_SHORT).show();
                            ShowLoading("OFF");

                        }



                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

                ShowLoading("OFF");


                ShowPopUp();
            }
        }){
            protected Map<String, String> getParams(){
                Map<String, String> paramV = new HashMap<>();
                paramV.put("StringQr", StringQrGenerado);
                return paramV;
            }
        };
        queue.add(stringRequest);
    }
*/




    private void vaciarBd(){
        db.eliminarTodos();
        listItem.clear();
    }




    private void viewData() {
        Cursor cursor = db.viewData("reportes");
        listItem.clear();
        if (cursor.getCount() == 0) {
            Toast.makeText(getApplicationContext(), "Aun no ha cargado datos", Toast.LENGTH_LONG).show();
        } else {
            while (cursor.moveToNext()) {
                listItem.add(cursor.getString(1) + " - (" + cursor.getString(3) + ") Estatus: " + cursor.getString(4));
            }
            // Invertir el orden de los elementos
            Collections.reverse(listItem);
            adapter = new ChatAdapter(this, listItem);
            listadoReportes.setAdapter(adapter);
            // Desplazarse al Ãºltimo elemento
            listadoReportes.setSelection(adapter.getCount() - 1);

            ShowLoading("OFF");

        }


    }

    public void relacionamosVistas() {
        e1 = findViewById(R.id.electores);
        b = findViewById(R.id.acceso);
    }


    public void enviarDatos() {
        final String electores = e1.getText().toString();
        final String cedula = cedulaUser;

        if (electores.equals("")) {
            e1.setError("Campo obligatorio");
        } else {
            //db.existeCedula(electores)
            if ('a' == 's') {
                Toast.makeText(Reporte_flujo.this, "Ya se ha reportado la participaciÃ³n del elector.", Toast.LENGTH_SHORT).show();
            } else {
                ShowLoading("ON");
                b.setEnabled(false);
                b.setBackgroundResource(R.drawable.btn_style_secondary);

                RequestQueue queue = Volley.newRequestQueue(this);
                String url = "https://sigven.net/elecciones/app/flujo_electoral.php";
                guardarVotos(electores);
                StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                ShowLoading("OFF");

                                if (response.equals("OK")) {
                                    registrarDatos("Enviado");
                                    Toast.makeText(Reporte_flujo.this, "El Reporte_flujo se ha enviado correctamente.", Toast.LENGTH_SHORT).show();
                                }else if (response.equals("NE")){
                                    // SE MUESTRA UNA VENTANA EMERGENTE QUE INDIQUE QUE LA PERSONA NO ES UN ELECTOR
                                    Toast.makeText(Reporte_flujo.this, "AtenciÃ³n: No es un elector.", Toast.LENGTH_SHORT).show();
                                } else if (response.equals("OP")) {
                                    // SE REGISTRAN LOS CON EL ESTATUS OP
                                    registrarDatos("OP");
                                    Toast.makeText(Reporte_flujo.this, "AtenciÃ³n: El Reporte_flujo se ha enviado correctamente.", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(Reporte_flujo.this, response, Toast.LENGTH_SHORT).show();
                                }
                                b.setEnabled(true);
                                b.setBackgroundResource(R.drawable.btn_style);
                            }
                        }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ShowLoading("OFF");
                        errorConexion();
                    }
                }) {
                    protected Map<String, String> getParams() {
                        Map<String, String> paramV = new HashMap<>();
                        paramV.put("responsable", cedula);
                        paramV.put("elector", electores);
                        return paramV;
                    }
                };
                queue.add(stringRequest);
            }
        }
    }

    public void ShowLoading(String action) {
        myDialog.setContentView(R.layout.loading_screen);
        myDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (action.equals("ON")) {
            myDialog.show();
        } else {
            myDialog.dismiss();
        }
    }


    public void enviarDatosNoEnviados() {
        Cursor cursor = db.dataNoEnviada("reportes");

        if (cursor.getCount() != 0) {
            final String cedula = cedulaUser;
            ArrayList<String> datosNoEnviados = new ArrayList<>();

            while (cursor.moveToNext()) {
                if (cursor.getString(4).equals("Sin Enviar")) {
                    datosNoEnviados.add(cursor.getString(1));
                    // ShowLoading("ON");
                }
            }



            if (!datosNoEnviados.isEmpty()) {

                RequestQueue queue = Volley.newRequestQueue(this);
                String url = "https://sigven.net/elecciones/app/flujo_electoral_pendiente.php";
                StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                try {
                                    JSONObject jsonResponse = new JSONObject(response);
                                    JSONArray results = jsonResponse.getJSONArray("results");
                                    for (int i = 0; i < results.length(); i++) {
                                        JSONObject result = results.getJSONObject(i);
                                        String elector = result.getString("elector");
                                        String status = result.getString("status");

                                        if (status.equals("OK")) {
                                            modificarReg(elector, "Enviado");
                                        } else if (status.equals("NE")) {
                                            Toast.makeText(Reporte_flujo.this, "AtenciÃ³n: No es un elector.", Toast.LENGTH_SHORT).show();
                                            modificarReg(elector, "NE");
                                        } else if (status.equals("OP")) {
                                            modificarReg(elector, "OP");
                                        } else {
                                            Toast.makeText(Reporte_flujo.this, status, Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                    viewData();


                                    // Llamar de nuevo al mÃ©todo para enviar los datos pendientes
                                    enviarDatosNoEnviados();

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    Toast.makeText(Reporte_flujo.this, "Error al procesar la respuesta", Toast.LENGTH_SHORT).show();

                                }
                            }
                        }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(Reporte_flujo.this, error.toString(), Toast.LENGTH_SHORT).show();

                        // errorConexion();
                    }
                }) {
                    @Override
                    protected Map<String, String> getParams() {
                        Map<String, String> paramV = new HashMap<>();
                        paramV.put("responsable", cedula);
                        paramV.put("electores", new JSONArray(datosNoEnviados).toString());
                        return paramV;
                    }
                };
                queue.add(stringRequest);
            }
        }
    }




    public void errorConexion() {
        b.setEnabled(true);
        b.setBackgroundResource(R.drawable.btn_style);

        Button btnReintentar, btnCancelar;
        myDialog.setContentView(R.layout.pop_up_error);
        btnCancelar = myDialog.findViewById(R.id.cancelar);
        btnReintentar = myDialog.findViewById(R.id.reintentar);
        btnCancelar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myDialog.dismiss();
                Toast.makeText(getApplicationContext(), "No se envio el Reporte_flujo.", Toast.LENGTH_LONG).show();
                registrarDatos("Sin Enviar");
            }
        });

        btnReintentar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myDialog.dismiss();
                enviarDatos();
            }
        });
        TextView cerrar = myDialog.findViewById(R.id.cerrar);
        cerrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myDialog.dismiss();
            }
        });

        myDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        myDialog.show();
    }



    public void registrarDatos(String status) {
        helper.abrir();
        helper.insertarReg(String.valueOf(e1.getText()), cedulaUser, sHora, status);
        helper.cerrar();
        listItem.clear();
        viewData();
        e1.setText("");
    }

    public void modificarReg(String elec, String newStatus) {
        helper.abrir();
        helper.modificarReg(elec, newStatus);
        helper.cerrar();
    }

    private void guardarVotos(String vtos) {
        SharedPreferences preferences = getSharedPreferences("maxVotos", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("votos", vtos);
        editor.commit();
    }

    private String cargarUsuario() {
        SharedPreferences preferences = getSharedPreferences("credenciales", Context.MODE_PRIVATE);
        return preferences.getString("cedula", "no existe");
    }
}


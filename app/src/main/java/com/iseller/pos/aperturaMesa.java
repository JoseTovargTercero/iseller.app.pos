package com.iseller.pos;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.HashMap;
import java.util.Map;

public class aperturaMesa extends AppCompatActivity {
    Dialog myDialog;
    Button guardar, enviar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apertura_mesa);
        myDialog = new Dialog(this);
       // guardar = findViewById(R.id.guardar);
        enviar = findViewById(R.id.enviar);

        enviar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnviarApertura();
            }
        });

    }

    public void EnviarApertura() {
        SharedPreferences preferences = getSharedPreferences("credenciales", Context.MODE_PRIVATE);
        String cedula = preferences.getString("cedula", "");

        EditText cantidadEditText = findViewById(R.id.cantidad);
        String cantidad = cantidadEditText.getText().toString();

        String url = "https://sigven.net/elecciones/app/apertura.php";

        ShowLoading("ON");
        RequestQueue servicio = Volley.newRequestQueue(this);
        StringRequest respuesta = new StringRequest(
                Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                if (response.equals("Reporte de apertura enviado correctamente.")) {
                    Intent i = new Intent(aperturaMesa.this, Reporte_flujo.class);
                    startActivity(i);
                    finish();

                    guardarApertura("Enviado");

                    Toast.makeText(getApplicationContext(), "Enviado correctamente.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), response, Toast.LENGTH_LONG).show();
                }
                ShowLoading("OFF");
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                ShowLoading("OFF");
                ShowPopUp();
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("cedula", cedula);
                params.put("cantidad", cantidad);
                return params;
            }
        };
        servicio.add(respuesta);
    }

    public void ShowLoading(String accion) {
        myDialog.setContentView(R.layout.loading_screen);
        myDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        if (accion.equals("ON")) {
            myDialog.show();
        } else {
            myDialog.dismiss();
        }
    }

    public void ShowPopUp() {
        enviar.setEnabled(true);
        enviar.setBackgroundResource(R.drawable.btn_style);
        enviar.setTextColor(Color.WHITE);

        Button btnReintentar, btnCancelar;
        myDialog.setContentView(R.layout.pop_up_error);
        btnCancelar = myDialog.findViewById(R.id.cancelar);
        btnReintentar = myDialog.findViewById(R.id.reintentar);
        btnCancelar.setText("No enviar");
        btnCancelar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myDialog.dismiss();
                Toast.makeText(getApplicationContext(), "No se enviÃ³ el Reporte_flujo.", Toast.LENGTH_LONG).show();
                guardarApertura();
            }
        });

        btnReintentar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myDialog.dismiss();
                EnviarApertura();
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

    public void guardarApertura() {
        SharedPreferences preferences = getSharedPreferences("apertura", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("mesa", "Guardado");
        editor.commit();

        Intent i = new Intent(aperturaMesa.this, Reporte_flujo.class);
        startActivity(i);
        finish();

        Toast.makeText(getApplicationContext(), "No fue enviado el Reporte_flujo.", Toast.LENGTH_LONG).show();
    }

    private void guardarApertura(String vtos) {
        SharedPreferences preferences = getSharedPreferences("apertura", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("mesa", vtos);
        editor.commit();
    }
}

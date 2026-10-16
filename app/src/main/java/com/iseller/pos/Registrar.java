package com.iseller.pos;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class Registrar extends AppCompatActivity implements View.OnClickListener {
    EditText Usuario, nombre, telefono, contrasena;
    Button btnRegistrar, r_btnCancelar;
    daoUsuario dao;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registar);

        Usuario= (EditText) findViewById(R.id.r_Usuario);
        nombre= (EditText) findViewById(R.id.r_nombre);
        telefono= (EditText) findViewById(R.id.r_telefono);
        contrasena= (EditText) findViewById(R.id.r_contrasena);

        btnRegistrar=(Button)findViewById(R.id.r_btnRegistrar);
        r_btnCancelar=(Button)findViewById(R.id.r_btnCancelar);
        btnRegistrar.setOnClickListener(this);
        r_btnCancelar.setOnClickListener(this);
        dao = new daoUsuario(this);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.r_btnRegistrar:
                Usuario u = new Usuario();

                u.setUsuario(Usuario.getText().toString());
                u.setNombre(nombre.getText().toString());
                u.setTelefono(telefono.getText().toString());
                u.setContrasena(contrasena.getText().toString());

                String Usua = Usuario.getText().toString();
                String nom = nombre.getText().toString();
                String telef = telefono.getText().toString();
                String pass = contrasena.getText().toString();

                if(!u.isNull()){

                    if(Usua.isEmpty()){  Usuario.setError("Campo obligatorio");  }
                    if(nom.isEmpty()){  nombre.setError("Campo obligatorio");  }
                    if(telef.isEmpty()){  telefono.setError("Campo obligatorio");  }
                    if(pass.isEmpty()){  contrasena.setError("Campo obligatorio");  }

                }else{

                    int logPass = contrasena.getText().toString().length();
                    if(logPass < 6){
                        Toast.makeText(this,"La contraseÃ±a debe tener mas de seis caracteres.", Toast.LENGTH_LONG).show();
                    }else if (dao.insertUsuario(u)){
                        Toast.makeText(this,"Registro Exitoso", Toast.LENGTH_LONG).show();
                        existeUser();
                        Intent i=new Intent(Registrar.this,MainActivity.class);
                        startActivity(i);
                        finish();
                    }else{
                        Toast.makeText(this,"Usuario ya registrado", Toast.LENGTH_LONG).show();
                    }
                }

                break;
            case R.id.r_btnCancelar:
                Intent i=new Intent(Registrar.this,MainActivity.class);
                startActivity(i);
                finish();
                break;
        }
    }


    private void existeUser(){
        SharedPreferences preferences = getSharedPreferences("user", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("bool", "1");

        editor.commit();
    }

}
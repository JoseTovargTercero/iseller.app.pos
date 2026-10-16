package com.iseller.pos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;

public class daoUsuario {
    Context c;
    Usuario u;
    ArrayList<Usuario> Lista;
    SQLiteDatabase sql;
    String bd="BDsistem";
    String tabla="create table if not exists usuario(id integer primary key autoincrement, usuario text, nombre text, telefono text, contrasena text)";

    public daoUsuario(Context c){
        this.c=c;
        sql=c.openOrCreateDatabase(bd,c.MODE_PRIVATE, null);
        sql.execSQL(tabla);
        u=new Usuario();
    }

    public boolean insertUsuario(Usuario u){
        if(buscar(u.getUsuario()) == 0){
            ContentValues cv=new ContentValues();
            cv.put("Usuario",u.getUsuario());
            cv.put("nombre",u.getNombre());
            cv.put("contrasena",u.getContrasena());
            return (sql.insert("Usuario", null, cv)>0);
        }else{
            return false;
        }
    }

    public int buscar(String u){
        int x=0;
        Lista=selectUsuario();
        for(Usuario us: Lista){
            if(us.getUsuario().equals(u)){
                x++;
            }
        }
        return x;
    }

    public ArrayList<Usuario> selectUsuario(){
        ArrayList<Usuario> lista=new ArrayList<Usuario>();
        lista.clear();
        Cursor cr = sql.rawQuery("select * from usuario", null);
                if(cr!=null&&cr.moveToFirst()){
                    do{
                        Usuario u=new Usuario();

                        u.setId(cr.getInt(0));
                        u.setUsuario(cr.getString(1));
                        u.setNombre(cr.getString(2));
                        u.setTelefono(cr.getString(3));
                        u.setContrasena(cr.getString(4));
                        lista.add(u);
                    }while(cr.moveToNext());
                }
                return lista;
        }
public int login(String u, String p){
        int a=0;
    Cursor cr = sql.rawQuery("select * from usuario", null);
    if(cr!=null && cr.moveToFirst()){
        do{
           if(cr.getString(1).equals(u) && cr.getString(4).equals(p)){
                a++;
           }

        }while(cr.moveToNext());
    }
    return a;
}

    public  Usuario getUsuario(String u, String p){
        Lista=selectUsuario();
        for(Usuario us:Lista){
            if(us.getUsuario().equals(u)&&us.getContrasena().equals(p));
            return us;
        }
        return null;
    }
    public  Usuario getUsuarioById(int id){
        Lista=selectUsuario();
        for(Usuario us:Lista){
            if(us.getId()==id);
            return us;
        }
        return null;
    }

    // MÃ©todo para obtener el Ãºnico usuario de la base de datos
    public boolean getUsuarioUnico() {
        Cursor cr = sql.rawQuery("select * from usuario", null);
        if(cr!=null && cr.moveToFirst()){
            return true;
        }else{
            return  false;
        }
    }
    public String getCedulaUnicoUsuario() {
        Cursor cr = sql.rawQuery("SELECT * FROM usuario", null);
        if (cr != null && cr.moveToFirst()) {
            String cedula = cr.getString(1); // Asumiendo que la cÃ©dula estÃ¡ en la segunda columna
            if (!cr.moveToNext()) {
                cr.close();
                return cedula;
            }
        }
        if (cr != null) {
            cr.close();
        }
        return null;
    }
    }

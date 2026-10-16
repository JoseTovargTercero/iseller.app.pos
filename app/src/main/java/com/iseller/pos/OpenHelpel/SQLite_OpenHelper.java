package com.iseller.pos.OpenHelpel;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class SQLite_OpenHelper extends SQLiteOpenHelper {
/*
    private static final String DB_NAME = "BD1.db";
    private static final String DB_TABLE = "reportes";

*/
    public SQLite_OpenHelper(@Nullable Context context, @Nullable String name, @Nullable SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
    String query = "create table reportes (_ID integer primary key autoincrement, electores text, cedulaTestigo text, hora text, status text)";
    sqLiteDatabase.execSQL(query);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }

    //PARA ABRIR LA BD
    public void abrir(){
        this.getWritableDatabase();
    }

    //PARA CERRAR LA BD
    public void cerrar(){
        this.close();
    }


    //PARA INSERTAR REGISTROS
    public void insertarReg(String elec, String ced, String time, String sta){
        ContentValues valores = new ContentValues();
        valores.put("electores", elec);
        valores.put("cedulaTestigo", ced);
        valores.put("hora", time);
        valores.put("status", sta);
        this.getWritableDatabase().insert("reportes",null,valores);
    }
    // METODO PARA ACTUALIZAR LA BASE DA DATOS
    public void modificarReg(String elec, String newStatus){
        ContentValues valores = new ContentValues();
        valores.put("status", newStatus);
        this.getWritableDatabase().update("reportes", valores, "electores = ?", new String[]{elec});
    }

    // METODO PARA ELIMINAR TODOS LOS REGISTROS DE LA TABLA
    public Cursor eliminarTodos() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("reportes", null, null);
        db.close();
        return null;
    }

    //METODO PARA MOSTRAR CONTENIDO DE LA TABLA
    public Cursor viewData(String tabla){
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "Select * from reportes Order by _ID desc";
        Cursor cursor = db.rawQuery(query,  null);
        return cursor;
    }

    public Cursor cantidadReporte(String tabla){
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "Select DISTINCT(electores) from reportes WHERE status='Enviado'";
        Cursor cursor = db.rawQuery(query,  null);
        return cursor;
    }


    //METODO PARA SELECCIONAR NO ENVIADOS
    public Cursor dataNoEnviada(String tabla){
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "Select * from reportes WHERE status = 'Sin Enviar' Order by _ID desc LIMIT 10";
        Cursor cursor = db.rawQuery(query,  null);
        return cursor;
    }

    // En la clase SQLite_OpenHelper
    public boolean existeCedula(String cedula) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT 1 FROM reportes WHERE electores = ?", new String[]{cedula});
        boolean existe = (cursor.getCount() > 0);
        cursor.close();
        return existe;
    }


}

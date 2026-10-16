package com.iseller.pos;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.Normalizer;

/** Utilidades compartidas de las rutas de entrega (archivos en getFilesDir()/rutas/). */
final class RutaJson {

    private RutaJson() {
    }

    static File carpeta(Context context) {
        File dir = new File(context.getFilesDir(), "rutas");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Nombre de archivo seguro a partir del nombre de la ruta. */
    static String nombreArchivo(String nombre) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        base = base.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "ruta";
        }
        return base + ".json";
    }

    /** Acepta "pedidos" como objeto (claves índice) o como array. */
    static JSONObject normalizarPedidos(Object pedidos) throws JSONException {
        if (pedidos instanceof JSONObject) {
            return (JSONObject) pedidos;
        }
        if (pedidos instanceof JSONArray) {
            JSONArray array = (JSONArray) pedidos;
            JSONObject out = new JSONObject();
            for (int i = 0; i < array.length(); i++) {
                out.put(String.valueOf(i), array.getJSONObject(i));
            }
            return out;
        }
        return null;
    }

    static String leer(File archivo) throws IOException {
        FileInputStream in = new FileInputStream(archivo);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int leidos;
            while ((leidos = in.read(buffer)) != -1) {
                out.write(buffer, 0, leidos);
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    static void escribir(File archivo, String contenido) throws IOException {
        FileOutputStream out = new FileOutputStream(archivo);
        try {
            out.write(contenido.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }
}

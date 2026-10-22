package com.iseller.pos;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Comprueba contra /api/app_version.php si hay una versión más reciente en el hosting,
 * avisa al usuario, descarga el APK mostrando progreso y lanza el instalador sin salir de la app.
 */
final class AppUpdater {

    private static final String URL_VERSION = BuildConfig.POS_BASE_URL + "/api/app_version.php";
    private static final String URL_DESCARGA = URL_VERSION + "?descargar=1";
    private static File apkPendiente;
    private static AlertDialog dialogoActivo;

    private AppUpdater() {
    }

    static boolean esVersionNueva(int codigoLocal, int codigoRemoto) {
        return codigoRemoto > codigoLocal;
    }

    static String sha256Hex(File archivo) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(archivo)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    /** Consulta en segundo plano si existe una versión superior a BuildConfig.VERSION_CODE. */
    static void verificar(final AppCompatActivity activity) {
        if (BuildConfig.POS_API_KEY.isEmpty() || (dialogoActivo != null && dialogoActivo.isShowing())) {
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(URL_VERSION).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("X-Api-Key", BuildConfig.POS_API_KEY);
                if (conn.getResponseCode() != 200) {
                    return;
                }
                JSONObject json = new JSONObject(leerTexto(conn.getInputStream()));
                if (!json.optBoolean("ok", false)) {
                    return;
                }
                int remotoCode = json.optInt("version_code", 0);
                if (!esVersionNueva(BuildConfig.VERSION_CODE, remotoCode)) {
                    return;
                }
                String versionName = json.optString("version_name", String.valueOf(remotoCode));
                String notas = json.optString("notas", "");
                String sha256 = json.optString("sha256", "");
                long tamano = json.optLong("tamano", 0L);

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        mostrarDialogo(activity, versionName, notas, sha256, tamano);
                    }
                });
            } catch (Exception ignored) {
                // Sin red o servidor inaccesible: no interrumpir el arranque normal
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private static void mostrarDialogo(final AppCompatActivity activity, String versionName,
                                       String notas, final String sha256Esperado, final long tamanoEsperado) {
        if (dialogoActivo != null && dialogoActivo.isShowing()) {
            return;
        }
        View vista = activity.getLayoutInflater().inflate(R.layout.dialog_actualizacion, null);
        TextView tvMensaje = vista.findViewById(R.id.tvMensajeVersion);
        View bloqueProgreso = vista.findViewById(R.id.bloqueProgreso);
        ProgressBar barraProgreso = vista.findViewById(R.id.barraProgreso);
        TextView tvEstado = vista.findViewById(R.id.tvEstadoProgreso);
        Button btnActualizar = vista.findViewById(R.id.btnActualizar);

        StringBuilder msg = new StringBuilder("Versión ").append(versionName)
                .append(" obligatoria para continuar.");
        if (!notas.isEmpty()) {
            msg.append("\n\n").append(notas);
        }
        tvMensaje.setText(msg.toString());

        dialogoActivo = new AlertDialog.Builder(activity)
                .setView(vista)
                .setCancelable(false)
                .create();
        dialogoActivo.show();
        if (dialogoActivo.getWindow() != null) {
            dialogoActivo.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnActualizar.setOnClickListener(v -> {
            btnActualizar.setEnabled(false);
            bloqueProgreso.setVisibility(View.VISIBLE);
            descargarEInstalar(activity, barraProgreso, tvEstado, btnActualizar,
                    sha256Esperado, tamanoEsperado);
        });
    }

    private static void descargarEInstalar(final AppCompatActivity activity,
                                           final ProgressBar barra, final TextView tvEstado,
                                           final Button btnActualizar,
                                           final String sha256Esperado, final long tamanoEsperado) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            final File carpeta = new File(activity.getCacheDir(), "update");
            // El nombre lleva el sha de la versión: un APK a medias de otra versión no se reutiliza
            String sufijo = sha256Esperado.length() >= 12 ? sha256Esperado.substring(0, 12) : "actual";
            final File destino = new File(carpeta, "iseller_pos_" + sufijo + ".apk");
            try {
                carpeta.mkdirs();
                File[] previos = carpeta.listFiles((dir, nombre) -> nombre.endsWith(".apk"));
                if (previos != null) {
                    for (File previo : previos) {
                        if (!previo.equals(destino)) {
                            previo.delete();
                        }
                    }
                }

                descargar(URL_DESCARGA, BuildConfig.POS_API_KEY, destino, tamanoEsperado, 8,
                        (descargado, total) -> {
                            if (total <= 0) {
                                return;
                            }
                            final int pct = (int) Math.min(100, (descargado * 100) / total);
                            final String texto = String.format(Locale.US, "Descargando %d%% (%.1f / %.1f MB)",
                                    pct, descargado / 1048576.0, total / 1048576.0);
                            ui.post(() -> {
                                barra.setProgress(pct);
                                tvEstado.setText(texto);
                            });
                        });

                if (!sha256Esperado.isEmpty() && !sha256Esperado.equalsIgnoreCase(sha256Hex(destino))) {
                    destino.delete();
                    throw new Exception("El archivo descargado no coincide con la versión publicada.");
                }

                ui.post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        // El diálogo permanece abierto para impedir usar la app si cancela el instalador
                        tvEstado.setText("Descarga completada. Confirma la instalación.");
                        btnActualizar.setEnabled(true);
                        btnActualizar.setText("Instalar actualización");
                        btnActualizar.setOnClickListener(v -> instalarApk(activity, destino));
                        instalarApk(activity, destino);
                    }
                });
            } catch (final Exception e) {
                ui.post(() -> {
                    tvEstado.setText(e.getMessage() != null ? e.getMessage() : "Error al descargar la actualización.");
                    btnActualizar.setEnabled(true);
                    btnActualizar.setText("Reintentar");
                });
            }
        }).start();
    }

    /** Aviso de avance de la descarga. */
    interface Progreso {
        void avance(long descargado, long total);
    }

    /**
     * Descarga el APK reanudando con `Range` cuando el servidor corta la conexión.
     * Cada intento continúa desde los bytes ya escritos; si el servidor ignora el rango
     * (responde 200) se reinicia el archivo. Devuelve el archivo completo o lanza excepción.
     */
    static File descargar(String url, String apiKey, File destino, long tamanoEsperado,
                          int maxIntentos, Progreso progreso) throws Exception {
        File carpeta = destino.getParentFile();
        if (carpeta != null && !carpeta.exists()) {
            carpeta.mkdirs();
        }

        long descargado = destino.exists() ? destino.length() : 0L;
        if (tamanoEsperado > 0 && descargado > tamanoEsperado) {
            destino.delete();
            descargado = 0L;
        }

        String ultimoFallo = null;
        for (int intento = 1; intento <= maxIntentos; intento++) {
            if (tamanoEsperado > 0 && descargado >= tamanoEsperado) {
                break;
            }
            descargado = destino.exists() ? destino.length() : 0L;

            HttpURLConnection conexion = null;
            try {
                conexion = (HttpURLConnection) new URL(url).openConnection();
                conexion.setConnectTimeout(15000);
                conexion.setReadTimeout(60000);
                if (apiKey != null && !apiKey.isEmpty()) {
                    conexion.setRequestProperty("X-Api-Key", apiKey);
                }
                if (descargado > 0) {
                    conexion.setRequestProperty("Range", "bytes=" + descargado + "-");
                }

                int estado = conexion.getResponseCode();
                if (estado == 401 || estado == 403 || estado == 404) {
                    throw new Exception("El servidor no permite la descarga (HTTP " + estado + ").");
                }
                if (estado != 200 && estado != 206) {
                    throw new IOException("Respuesta HTTP " + estado);
                }

                boolean reanuda = (estado == 206);
                if (!reanuda) {
                    descargado = 0L;
                }

                try (InputStream in = conexion.getInputStream();
                     FileOutputStream out = new FileOutputStream(destino, reanuda)) {
                    byte[] buffer = new byte[16384];
                    int leidos;
                    while ((leidos = in.read(buffer)) != -1) {
                        out.write(buffer, 0, leidos);
                        descargado += leidos;
                        if (progreso != null) {
                            progreso.avance(descargado, tamanoEsperado);
                        }
                    }
                }
            } catch (IOException e) {
                // conexión cortada: se reintenta reanudando desde lo ya escrito
                ultimoFallo = e.getMessage();
            } finally {
                if (conexion != null) {
                    conexion.disconnect();
                }
            }

            if (tamanoEsperado > 0 && descargado < tamanoEsperado) {
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        long obtenido = destino.exists() ? destino.length() : 0L;
        if (tamanoEsperado > 0 && obtenido != tamanoEsperado) {
            throw new Exception("Descarga incompleta (" + obtenido + " de " + tamanoEsperado + " bytes)."
                    + (ultimoFallo != null ? " " + ultimoFallo : ""));
        }
        return destino;
    }

    static void instalarApk(AppCompatActivity activity, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            apkPendiente = apk;
            Toast.makeText(activity, "Autoriza instalar actualizaciones desde esta fuente.", Toast.LENGTH_LONG).show();
            Intent permiso = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(permiso);
            return;
        }
        apkPendiente = null;
        Uri uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    /** Si el usuario volvió de conceder el permiso de fuentes desconocidas, retoma la instalación. */
    static void continuarInstalacionPendiente(AppCompatActivity activity) {
        if (apkPendiente != null && apkPendiente.exists()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || activity.getPackageManager().canRequestPackageInstalls())) {
            File apk = apkPendiente;
            apkPendiente = null;
            instalarApk(activity, apk);
        }
    }

    private static String leerTexto(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = input.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }
}

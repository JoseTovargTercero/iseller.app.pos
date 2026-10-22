package com.iseller.pos;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Notificaciones push (FCM).
 * Los mensajes con payload "notification" recibidos en segundo plano los pinta el propio
 * SDK de Firebase; aquí se atienden los de tipo "data" y los que llegan en primer plano.
 * También registra el token del dispositivo en el servidor (ver api/push_registrar.php).
 */
public class PosFcmService extends FirebaseMessagingService {

    private static final String CANAL = "iseller_pos";
    private static final String PREFS = "pos_fcm";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USUARIO = "usuario";

    @Override
    public void onCreate() {
        super.onCreate();
        crearCanal(this);
    }

    /** El canal debe existir antes de publicar: en Android 8+ una notificación sin canal se descarta. */
    static void crearCanal(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CANAL) != null) {
            return;
        }
        NotificationChannel canal = new NotificationChannel(CANAL, "Avisos del POS",
                NotificationManager.IMPORTANCE_HIGH);
        canal.setDescription("Pedidos, rutas de entrega y avisos del negocio");
        nm.createNotificationChannel(canal);
    }

    /** onNewToken solo se dispara al crear o rotar el token, por eso también se pide al arrancar. */
    static void sincronizarToken(final Context ctx) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_TOKEN, token).apply();
            // Si ya se sabe de qué usuario es el teléfono, se registra en el servidor.
            String usuario = ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_USUARIO, null);
            if (usuario != null) {
                enviarRegistro(ctx, usuario, token);
            }
        });
    }

    /** Tras un login correcto: recuerda al usuario y registra el token en el servidor. */
    static void registrarEnServidor(Context ctx, String usuario) {
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_USUARIO, usuario).apply();
        sincronizarToken(ctx);
    }

    /** POST /api/push_registrar.php (X-Api-Key + usuario). Si falla, se reintenta al abrir la app. */
    private static void enviarRegistro(final Context ctx, final String usuario, final String token) {
        if (BuildConfig.POS_API_KEY.isEmpty()) {
            return;
        }
        StringRequest req = new StringRequest(Request.Method.POST,
                BuildConfig.POS_BASE_URL + "/api/push_registrar.php",
                resp -> {
                },
                error -> {
                }) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("usuario", usuario);
                    body.put("token", token);
                    body.put("plataforma", "android");
                    return body.toString().getBytes(StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return new byte[0];
                }
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                headers.put("X-Api-Key", BuildConfig.POS_API_KEY);
                return headers;
            }
        };
        Volley.newRequestQueue(ctx.getApplicationContext()).add(req);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply();
        String usuario = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_USUARIO, null);
        if (usuario != null) {
            enviarRegistro(this, usuario, token);
        }
        // La baja del dispositivo (action=baja) va pendiente: aún no hay cerrar sesión en el POS.
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage mensaje) {
        RemoteMessage.Notification aviso = mensaje.getNotification();
        String titulo = aviso != null ? aviso.getTitle() : mensaje.getData().get("titulo");
        String cuerpo = aviso != null ? aviso.getBody() : mensaje.getData().get("mensaje");
        if (titulo == null && cuerpo == null) {
            return;
        }

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        if (!manager.areNotificationsEnabled()) {
            return;
        }

        int id = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
        PendingIntent toque = PendingIntent.getActivity(this, id,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(R.mipmap.icono)
                .setContentTitle(titulo != null ? titulo : getString(R.string.app_name))
                .setContentText(cuerpo)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(cuerpo))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(toque);

        manager.notify(id, nb.build());
    }
}

package com.iseller.pos;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Pantalla de bienvenida (inicio). Ya no permite crear cuenta: solo ofrece
 * iniciar sesión en el POS o consultar la ruta de entrega.
 *
 * Animación de entrada: el logo arranca centrado y sube a su posición final;
 * al terminar, aparecen el texto y los botones.
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    Button btnLoginPos;
    TextView btnRutaEntrega;
    ImageView logoBienvenida;
    View bloqueTexto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logoBienvenida = findViewById(R.id.logoBienvenida);
        bloqueTexto = findViewById(R.id.bloqueTexto);
        btnLoginPos = findViewById(R.id.btnLoginPos);
        btnRutaEntrega = findViewById(R.id.btnRutaEntrega);

        btnLoginPos.setOnClickListener(this);
        btnRutaEntrega.setOnClickListener(this);

        // Push FCM: guardar el token del dispositivo y pedir permiso de notificaciones (Android 13+)
        PosFcmService.sincronizarToken(this);
        pedirPermisoNotificaciones();

        // Entregas marcadas sin conexión: se intentan subir al abrir la app
        RutaSync.enviarPendientes(this);

        animarEntrada();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppUpdater.continuarInstalacionPendiente(this);
        AppUpdater.verificar(this);
    }

    private void pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnLoginPos:
                startActivity(new Intent(MainActivity.this, Login.class));
                break;
            case R.id.btnRutaEntrega:
                startActivity(new Intent(MainActivity.this, RutaEntrega.class));
                break;
        }
    }

    private void animarEntrada() {
        // Respeta la escala de animación del sistema (accesibilidad / prefers-reduced-motion)
        if (!animacionesActivas()) {
            return;
        }

        final View root = findViewById(R.id.rootBienvenida);
        final View[] resto = { bloqueTexto, btnLoginPos, btnRutaEntrega };

        // OnPreDraw: la primera vez que ya se conoce el layout, fijamos el estado
        // inicial antes del primer dibujo (evita un parpadeo en la posición final).
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);

                for (View v : resto) {
                    v.setAlpha(0f);
                    v.setTranslationY(40f);
                }

                // El logo empieza en el centro vertical de la pantalla
                float centroRoot = root.getHeight() / 2f;
                float centroLogo = logoBienvenida.getTop() + logoBienvenida.getHeight() / 2f;
                logoBienvenida.setAlpha(0f);
                logoBienvenida.setTranslationY(centroRoot - centroLogo);
                logoBienvenida.setScaleX(1.12f);
                logoBienvenida.setScaleY(1.12f);

                logoBienvenida.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(900)
                        .setInterpolator(new DecelerateInterpolator(1.6f))
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                revelarResto(resto);
                            }
                        })
                        .start();

                return true;
            }
        });
    }

    private void revelarResto(View[] resto) {
        for (int i = 0; i < resto.length; i++) {
            resto[i].animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 110L)
                    .setDuration(500)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private boolean animacionesActivas() {
        try {
            float escala = Settings.Global.getFloat(getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return escala != 0f;
        } catch (Exception e) {
            return true;
        }
    }
}

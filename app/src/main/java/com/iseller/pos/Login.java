package com.iseller.pos;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Inicio de sesión nativo del POS + WebView autenticado (ver API_LOGIN_APP.md).
 * Guarda las credenciales cifradas con Android KeyStore tras el primer login
 * para permitir el acceso con huella en los siguientes inicios.
 */
public class Login extends AppCompatActivity implements View.OnClickListener {

    // Endpoint de login (API_LOGIN_APP.md §2)
    private static final String URL_AUTH_LOGIN = "https://iseller-tiendas.com/api/auth_login.php";

    // Almacenamiento seguro (KeyStore AES/GCM + SharedPreferences privadas)
    private static final String PREFS_AUTH = "pos_auth_seguro";
    private static final String KEY_USER = "enc_user";
    private static final String KEY_PASS = "enc_pass";
    private static final String KEY_CRED_ALIAS = "pos_credenciales_key";
    private static final String KEY_NAME = "example_key";

    EditText user, pass;
    Button btnEntrar;
    ImageButton btnHuella;
    private WebView posWebView;
    private AlertDialog dialogoHuella;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private FingerprintManager fingerprintManager;
    private KeyStore keyStore;
    private Cipher cipher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        user = findViewById(R.id.user);
        pass = findViewById(R.id.pass);
        btnEntrar = findViewById(R.id.btnEntrar);
        btnHuella = findViewById(R.id.btnHuella);

        btnEntrar.setOnClickListener(this);
        btnHuella.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnEntrar:
                String u = user.getText().toString().trim();
                String p = pass.getText().toString();
                if (u.isEmpty() || p.isEmpty()) {
                    if (u.isEmpty()) { user.setError("Campo obligatorio"); }
                    if (p.isEmpty()) { pass.setError("Campo obligatorio"); }
                } else {
                    autenticarEnServidor(u, p, true);
                }
                break;

            case R.id.btnHuella:
                // Verificar si existen credenciales guardadas antes de lanzar el lector de huellas
                if (!tieneCredencialesGuardadas()) {
                    Toast.makeText(this, "Inicia sesión con tu usuario y contraseña por primera vez para activar la huella.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (fingerprintManager == null) {
                        fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
                    }
                    if (fingerprintManager != null && fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints()) {
                        mostrarDialogoHuella();
                    } else {
                        Toast.makeText(this, "No hay huellas registradas en el dispositivo.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    configurarBiometricPrompt();
                    mostrarDialogoHuella();
                }
                break;
        }
    }

    // ------------------------------------------------------------------
    // API Login + WebView autenticado (API_LOGIN_APP.md)
    // ------------------------------------------------------------------

    private void autenticarEnServidor(final String usuario, final String password, final boolean guardarAlExito) {
        if (BuildConfig.POS_API_KEY.isEmpty()) {
            Toast.makeText(this, "Falta configurar POS_API_KEY en local.properties.", Toast.LENGTH_LONG).show();
            return;
        }

        btnEntrar.setEnabled(false);

        StringRequest req = new StringRequest(Request.Method.POST, URL_AUTH_LOGIN,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        btnEntrar.setEnabled(true);
                        procesarRespuestaLogin(response, usuario, password, guardarAlExito);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        btnEntrar.setEnabled(true);
                        String mensaje = "Sin conexión con el servidor";
                        if (error.networkResponse != null && error.networkResponse.data != null) {
                            try {
                                JSONObject errJson = new JSONObject(new String(error.networkResponse.data, StandardCharsets.UTF_8));
                                mensaje = errJson.optString("error", mensaje);
                            } catch (Exception ignored) {
                            }
                        }
                        Toast.makeText(Login.this, mensaje, Toast.LENGTH_LONG).show();
                    }
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
                    body.put("password", password);
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

        Volley.newRequestQueue(this).add(req);
    }

    private void procesarRespuestaLogin(String response, String usuario, String password, boolean guardarAlExito) {
        try {
            JSONObject json = new JSONObject(response);
            if (!json.optBoolean("ok", false)) {
                Toast.makeText(this, json.optString("error", "Error de autenticación"), Toast.LENGTH_LONG).show();
                return;
            }

            String sessionUrl = json.getString("session_url");
            JSONObject membresia = json.optJSONObject("membresia");
            if (membresia != null && membresia.optBoolean("esta_vencida", false)) {
                Toast.makeText(this, "Tu membresía está vencida.", Toast.LENGTH_LONG).show();
            }

            if (guardarAlExito) {
                guardarCredencialesSeguras(usuario, password);
            }

            // Compatibilidad con pantallas que leen "credenciales" -> "cedula"
            getSharedPreferences("credenciales", Context.MODE_PRIVATE)
                    .edit()
                    .putString("cedula", usuario)
                    .apply();

            abrirSistemaWebView(sessionUrl);
        } catch (Exception e) {
            Toast.makeText(this, "Respuesta inválida del servidor", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void abrirSistemaWebView(String sessionUrl) {
        posWebView = new WebView(this);
        WebSettings s = posWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(posWebView, true);

        posWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("login.php?app_error=token")) {
                    Toast.makeText(Login.this, "La sesión expiró. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show();
                    recreate();
                    return true;
                }
                return false;
            }
        });

        setContentView(posWebView);
        posWebView.loadUrl(sessionUrl);
    }

    @Override
    public void onBackPressed() {
        if (posWebView != null && posWebView.canGoBack()) {
            posWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------
    // Guardado y lectura segura de credenciales (Android KeyStore AES/GCM)
    // ------------------------------------------------------------------

    private boolean tieneCredencialesGuardadas() {
        SharedPreferences prefs = getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        return prefs.contains(KEY_USER) && prefs.contains(KEY_PASS);
    }

    private void guardarCredencialesSeguras(String usuario, String password) {
        try {
            SecretKey key = obtenerOGenerarClaveCredenciales();
            String encUser = cifrar(usuario, key);
            String encPass = cifrar(password, key);
            getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_USER, encUser)
                    .putString(KEY_PASS, encPass)
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String[] obtenerCredencialesSeguras() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
            String encUser = prefs.getString(KEY_USER, null);
            String encPass = prefs.getString(KEY_PASS, null);
            if (encUser == null || encPass == null) {
                return null;
            }
            SecretKey key = obtenerOGenerarClaveCredenciales();
            return new String[]{ descifrar(encUser, key), descifrar(encPass, key) };
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKey obtenerOGenerarClaveCredenciales() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_CRED_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_CRED_ALIAS, null)).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_CRED_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }

    private String cifrar(String texto, SecretKey key) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = c.getIV();
        byte[] cifrado = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cifrado, Base64.NO_WRAP);
    }

    private String descifrar(String guardado, SecretKey key) throws Exception {
        String[] partes = guardado.split(":", 2);
        byte[] iv = Base64.decode(partes[0], Base64.NO_WRAP);
        byte[] cifrado = Base64.decode(partes[1], Base64.NO_WRAP);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(c.doFinal(cifrado), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Autenticación biométrica
    // ------------------------------------------------------------------

    public void iniciarSesionConHuella() {
        if (dialogoHuella != null && dialogoHuella.isShowing()) {
            dialogoHuella.dismiss();
        }
        String[] creds = obtenerCredencialesSeguras();
        if (creds == null) {
            Toast.makeText(this, "No se encontraron credenciales guardadas.", Toast.LENGTH_LONG).show();
            return;
        }
        autenticarEnServidor(creds[0], creds[1], false);
    }

    private void configurarBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        iniciarSesionConHuella();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(Login.this, "Autenticación fallida. Intente de nuevo.", Toast.LENGTH_SHORT).show();
                    }
                });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Autenticación biométrica")
                .setSubtitle("Usa tu huella digital para iniciar sesión")
                .setNegativeButtonText("Cancelar")
                .build();
    }

    private void mostrarDialogoHuella() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.layout_custom_dialog, null);
        builder.setView(dialogView);

        Button btnRetry = dialogView.findViewById(R.id.btn_retry);

        dialogoHuella = builder.create();
        dialogoHuella.show();
        if (dialogoHuella.getWindow() != null) {
            dialogoHuella.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogoHuella.dismiss();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                generateKey();
                if (initCipher()) {
                    FingerprintManager.CryptoObject cryptoObject = new FingerprintManager.CryptoObject(cipher);
                    FingerprintHandler handler = new FingerprintHandler(Login.this);
                    handler.startAuth(fingerprintManager, cryptoObject);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            biometricPrompt.authenticate(promptInfo);
        }
    }

    private void generateKey() throws Exception {
        keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
        }

        keyGenerator.generateKey();
    }

    private boolean initCipher() {
        try {
            cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME, null);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }
}

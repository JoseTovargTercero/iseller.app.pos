package com.iseller.pos;

import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.M)
public class FingerprintHandler extends FingerprintManager.AuthenticationCallback {

    private Context context;

    public FingerprintHandler(Context context) {
        this.context = context;
    }

    public void startAuth(FingerprintManager manager, FingerprintManager.CryptoObject cryptoObject) {
        FingerprintManager.AuthenticationCallback callback = this;
        manager.authenticate(cryptoObject, null, 0, callback, null);
    }

    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        Toast.makeText(context, "Authentication error\n" + errString, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        Toast.makeText(context, "Authentication help\n" + helpString, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAuthenticationFailed() {
        Toast.makeText(context, "AutenticaciÃ³n incorrecta.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
      //  Toast.makeText(context, "Authentication succeeded.", Toast.LENGTH_LONG).show();
        if (context instanceof Login) {
            ((Login) context).iniciarSesionConHuella();
        }
    }
}

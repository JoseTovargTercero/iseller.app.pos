package com.iseller.pos;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkChangeReceiver extends BroadcastReceiver {
    private NetworkChangeListener listener;

    public NetworkChangeReceiver(NetworkChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        boolean isConnected = isConnectedToInternet(context);

        if (listener != null) {
            listener.onNetworkChange(isConnected);
        }
    }

    private boolean isConnectedToInternet(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }

    public interface NetworkChangeListener {
        void onNetworkChange(boolean isConnected);
    }
}

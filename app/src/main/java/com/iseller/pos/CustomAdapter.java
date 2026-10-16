package com.iseller.pos;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class CustomAdapter extends BaseAdapter {

    private Context context;
    private List<User> userList;

    public CustomAdapter(Context context, List<User> userList) {
        this.context = context;
        this.userList = userList;
    }

    @Override
    public int getCount() {
        return userList.size();
    }

    @Override
    public Object getItem(int position) {
        return userList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.list_item, parent, false);
        }

        TextView nameTextView = convertView.findViewById(R.id.nameTextView);
        TextView statusTextView = convertView.findViewById(R.id.statusTextView);
        TextView jefe = convertView.findViewById(R.id.jefe);
        Button whatsappButton = convertView.findViewById(R.id.whatsappButton);

        User user = userList.get(position);

        nameTextView.setText(user.getName());
        statusTextView.setText(user.getStatus());

        // --- ENLACE INTERACTIVO DEL MAPA ---
        // --- ENLACE INTERACTIVO DEL MAPA (VERSIÃ“N ULTRA SEGURA) ---
        String urlMapa = null;

        try {
            // Usamos un try-catch por si getJefe() llega a fallar o no existe
            urlMapa = user.getJefe();
        } catch (Exception e) {
            urlMapa = null;
        }

// Validamos de manera estricta que NO sea nulo, NO estÃ© vacÃ­o y NO sea la palabra "null" en texto
        if (urlMapa == null || urlMapa.trim().isEmpty() || urlMapa.equals("null")) {
            jefe.setText("Sin ubicaciÃ³n");
            jefe.setTextColor(Color.GRAY);
            jefe.setPaintFlags(jefe.getPaintFlags() & (~Paint.UNDERLINE_TEXT_FLAG));
            jefe.setOnClickListener(null);
        } else {
            jefe.setText("Ver UbicaciÃ³n en Mapa");
            jefe.setTextColor(Color.parseColor("#0066CC"));
            jefe.setPaintFlags(jefe.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

            // Guardamos la variable en una constante final interna para el clic
            final String urlFinal = urlMapa.trim();

            jefe.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, MapaActivity.class);
                    intent.putExtra("url_mapa", urlFinal);
                    context.startActivity(intent);
                }
            });
        }
// ------------------------------------
        // ------------------------------------

        // Clic del botÃ³n de WhatsApp
        whatsappButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phoneNumber = "+58" + user.getStatus();
                String message = "*Falta por participar:* %0A" + user.getName();

                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_VIEW);
                String uri = "whatsapp://send?phone=" + phoneNumber + "&text=" + message;
                sendIntent.setData(Uri.parse(uri));
                context.startActivity(sendIntent);
            }
        });

        // Clic en el nÃºmero de telÃ©fono para llamar
        statusTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + user.getStatus()));
                context.startActivity(intent);
            }
        });

        return convertView;
    }
}
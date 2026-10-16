package com.iseller.pos;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class ChatAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final List<String> items;

    public ChatAdapter(Context context, List<String> items) {
        super(context, R.layout.chat_bubble, items);
        this.context = context;
        this.items = items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.chat_bubble, parent, false);
        }

        TextView tvCombined = convertView.findViewById(R.id.tv_combined);
        TextView tvHora = convertView.findViewById(R.id.hora);
        ImageView ivStatus = convertView.findViewById(R.id.iv_status);

        String currentItem = items.get(position);

        // Assuming the format "Title - (Time) Estatus: SubTitle"
        String[] parts = currentItem.split(" - ");
        String titlePart = parts[0];
        String[] subParts = parts[1].split(" Estatus: ");
        String timePart = subParts[0];
        String statusPart = subParts[1];

        tvCombined.setText(titlePart);
        tvHora.setText(timePart);

        // Set the status icon based on the status in the currentItem string
        if (statusPart.equals("Enviado")) {
            ivStatus.setImageResource(R.drawable.baseline_check_circle_outline_24);
        } else if(statusPart.equals("Sin Enviar")) {
            ivStatus.setImageResource(R.drawable.baseline_radio_button_unchecked_24);
        } else if (statusPart.equals("OP")) {
            ivStatus.setImageResource(R.drawable.baseline_check_circle_outline_25);
        } else if (statusPart.equals("NE")){
            ivStatus.setImageResource(R.drawable.baseline_radio_button_checked_24);
        }

        return convertView;
    }
}

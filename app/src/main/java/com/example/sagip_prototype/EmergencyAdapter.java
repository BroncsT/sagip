package com.example.sagip_prototype;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class EmergencyAdapter extends RecyclerView.Adapter<EmergencyAdapter.EmergencyViewHolder> {

    private List<EmergencyListActivity.EmergencyItem> emergencyList;
    private OnEmergencyClickListener clickListener;

    public interface OnEmergencyClickListener {
        void onEmergencyClick(EmergencyListActivity.EmergencyItem emergency);
    }

    public EmergencyAdapter(List<EmergencyListActivity.EmergencyItem> emergencyList, OnEmergencyClickListener clickListener) {
        this.emergencyList = emergencyList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public EmergencyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency, parent, false);
        return new EmergencyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmergencyViewHolder holder, int position) {
        EmergencyListActivity.EmergencyItem emergency = emergencyList.get(position);
        holder.bind(emergency, clickListener);
    }

    @Override
    public int getItemCount() {
        return emergencyList.size();
    }

    static class EmergencyViewHolder extends RecyclerView.ViewHolder {
        private TextView seniorNameText;
        private TextView locationText;
        private TextView distanceText;
        private TextView timeText;
        private TextView phoneText;
        private Button respondButton;
        private Button callButton;
        private Button navigateButton;

        public EmergencyViewHolder(@NonNull View itemView) {
            super(itemView);
            seniorNameText = itemView.findViewById(R.id.seniorNameText);
            locationText = itemView.findViewById(R.id.locationText);
            distanceText = itemView.findViewById(R.id.distanceText);
            timeText = itemView.findViewById(R.id.timeText);
            phoneText = itemView.findViewById(R.id.phoneText);
            respondButton = itemView.findViewById(R.id.respondButton);
            callButton = itemView.findViewById(R.id.callButton);
            navigateButton = itemView.findViewById(R.id.navigateButton);
        }

        public void bind(EmergencyListActivity.EmergencyItem emergency, OnEmergencyClickListener clickListener) {
            seniorNameText.setText(emergency.seniorName);
            locationText.setText(emergency.locationAddress);
            distanceText.setText("📍 " + emergency.getDistanceText());
            timeText.setText("⏰ " + emergency.getTimeAgo());
            
            if (emergency.seniorPhone != null && !emergency.seniorPhone.isEmpty()) {
                phoneText.setText("📞 " + emergency.seniorPhone);
                phoneText.setVisibility(View.VISIBLE);
                callButton.setVisibility(View.VISIBLE);
            } else {
                phoneText.setVisibility(View.GONE);
                callButton.setVisibility(View.GONE);
            }

            // Set up button listeners
            respondButton.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onEmergencyClick(emergency);
                }
            });

            navigateButton.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onEmergencyClick(emergency);
                }
            });

            callButton.setOnClickListener(v -> {
                if (emergency.seniorPhone != null && !emergency.seniorPhone.isEmpty()) {
                    Intent callIntent = new Intent(Intent.ACTION_CALL);
                    callIntent.setData(Uri.parse("tel:" + emergency.seniorPhone));
                    itemView.getContext().startActivity(callIntent);
                }
            });
        }
    }
}

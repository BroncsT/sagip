package com.example.sagip_prototype;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.GeoPoint;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SOSEmergencyAdapter extends RecyclerView.Adapter<SOSEmergencyAdapter.EmergencyViewHolder> {

    private List<EmergencyQueueManager.EmergencyRequest> emergencyList;
    private OnEmergencyClickListener clickListener;

    public interface OnEmergencyClickListener {
        void onEmergencyClick(EmergencyQueueManager.EmergencyRequest emergency);
        void onRespondClick(EmergencyQueueManager.EmergencyRequest emergency);
        void onNavigateClick(EmergencyQueueManager.EmergencyRequest emergency);
    }

    public SOSEmergencyAdapter(List<EmergencyQueueManager.EmergencyRequest> emergencyList, OnEmergencyClickListener clickListener) {
        this.emergencyList = emergencyList != null ? emergencyList : new ArrayList<>();
        this.clickListener = clickListener;
    }

    public void updateEmergencyList(List<EmergencyQueueManager.EmergencyRequest> newList) {
        this.emergencyList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EmergencyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency, parent, false);
        return new EmergencyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmergencyViewHolder holder, int position) {
        if (position < emergencyList.size()) {
            EmergencyQueueManager.EmergencyRequest emergency = emergencyList.get(position);
            holder.bind(emergency, clickListener);
        }
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

        public void bind(EmergencyQueueManager.EmergencyRequest emergency, OnEmergencyClickListener clickListener) {
            // Set senior name
            seniorNameText.setText(emergency.seniorName != null ? emergency.seniorName : "Unknown Senior");
            
            // Set location
            locationText.setText(emergency.locationAddress != null ? emergency.locationAddress : "Location not available");
            
            // Calculate and display distance (if we have coordinates)
            String distanceStr = "📍 Distance: Calculating...";
            if (emergency.location != null) {
                // Distance would need rescuer's current location - show "Available" for now
                distanceStr = "📍 Location Available";
            }
            distanceText.setText(distanceStr);
            
            // Set time ago
            if (emergency.timestamp > 0) {
                timeText.setText("⏰ " + getTimeAgo(emergency.timestamp));
            } else {
                timeText.setText("⏰ Unknown time");
            }
            
            // Set phone number
            if (emergency.seniorPhone != null && !emergency.seniorPhone.isEmpty()) {
                String formattedPhone = PhoneNumberUtils.formatPhoneNumber(emergency.seniorPhone);
                phoneText.setText("📞 " + formattedPhone);
                phoneText.setVisibility(View.VISIBLE);
                callButton.setVisibility(PhoneNumberUtils.isValidPhoneNumber(emergency.seniorPhone) ? View.VISIBLE : View.GONE);
            } else {
                phoneText.setVisibility(View.GONE);
                callButton.setVisibility(View.GONE);
            }

            // Set up button listeners
            respondButton.setOnClickListener(v -> {
                if (clickListener != null && emergency.requestId != null) {
                    clickListener.onRespondClick(emergency);
                }
            });

            navigateButton.setOnClickListener(v -> {
                if (clickListener != null && emergency.requestId != null) {
                    clickListener.onNavigateClick(emergency);
                }
            });

            callButton.setOnClickListener(v -> {
                if (emergency.seniorPhone != null && !emergency.seniorPhone.isEmpty()) {
                    String callableNumber = PhoneNumberUtils.getCallablePhoneNumber(emergency.seniorPhone);
                    if (callableNumber != null) {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL);
                        callIntent.setData(Uri.parse("tel:" + callableNumber));
                        itemView.getContext().startActivity(callIntent);
                    } else {
                        Toast.makeText(itemView.getContext(), "Invalid phone number format", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        private String getTimeAgo(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;
            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            
            if (minutes < 1) {
                return "Just now";
            } else if (minutes < 60) {
                return minutes + " min ago";
            } else {
                return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            }
        }
    }
}


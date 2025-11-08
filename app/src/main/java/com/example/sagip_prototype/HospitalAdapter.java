package com.example.sagip_prototype;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.List;

public class HospitalAdapter extends RecyclerView.Adapter<HospitalAdapter.HospitalViewHolder> {
    private List<Hospital> hospitals;
    private OnHospitalClickListener listener;

    public interface OnHospitalClickListener {
        void onHospitalClick(Hospital hospital);
    }

    public HospitalAdapter(List<Hospital> hospitals, OnHospitalClickListener listener) {
        this.hospitals = hospitals;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HospitalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.hospital_card_item, parent, false);
        return new HospitalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HospitalViewHolder holder, int position) {
        Hospital hospital = hospitals.get(position);
        holder.bind(hospital, listener);
    }

    @Override
    public int getItemCount() {
        return hospitals.size();
    }

    public void updateHospitals(List<Hospital> newHospitals) {
        this.hospitals = newHospitals;
        notifyDataSetChanged();
    }

    static class HospitalViewHolder extends RecyclerView.ViewHolder {
        private ImageView hospitalImageView;
        private TextView hospitalNameText;
        private TextView hospitalAddressText;
        private TextView hospitalContactText;
        private TextView hospitalEmailText;
        private TextView hospitalStatusText;
        private TextView hospitalBedsText;
        private TextView hospitalSpecializationText;

        public HospitalViewHolder(@NonNull View itemView) {
            super(itemView);
            hospitalImageView = itemView.findViewById(R.id.hospitalImageView);
            hospitalNameText = itemView.findViewById(R.id.hospitalNameText);
            hospitalAddressText = itemView.findViewById(R.id.hospitalAddressText);
            hospitalContactText = itemView.findViewById(R.id.hospitalContactText);
            hospitalEmailText = itemView.findViewById(R.id.hospitalEmailText);
            hospitalStatusText = itemView.findViewById(R.id.hospitalStatusText);
            hospitalBedsText = itemView.findViewById(R.id.hospitalBedsText);
            hospitalSpecializationText = itemView.findViewById(R.id.hospitalSpecializationText);
        }

        public void bind(Hospital hospital, OnHospitalClickListener listener) {
            // Check if there's an incoming emergency
            if (hospital.getHasIncomingEmergency() != null && hospital.getHasIncomingEmergency()) {
                // EMERGENCY MODE: Show only senior information (no hospital name)
                
                // Hide all hospital details including hospital name
                hospitalNameText.setVisibility(View.GONE);
                hospitalAddressText.setVisibility(View.GONE);
                hospitalContactText.setVisibility(View.GONE);
                hospitalEmailText.setVisibility(View.GONE);
                hospitalStatusText.setVisibility(View.GONE);
                hospitalBedsText.setVisibility(View.GONE);
                
                // Show senior information for incoming emergency
                String seniorInfo = itemView.getContext().getString(R.string.incoming_emergency) + "\n";
                seniorInfo += itemView.getContext().getString(R.string.senior_emoji) + " " + itemView.getContext().getString(R.string.senior_label_colon) + " " + 
                             (hospital.getSeniorName() != null ? hospital.getSeniorName() : itemView.getContext().getString(R.string.unknown)) + "\n";
                seniorInfo += itemView.getContext().getString(R.string.phone_emoji) + " " + itemView.getContext().getString(R.string.senior_phone_label) + " " + 
                             (hospital.getSeniorPhone() != null ? hospital.getSeniorPhone() : itemView.getContext().getString(R.string.not_available_short)) + "\n";
                seniorInfo += itemView.getContext().getString(R.string.rescuer_emoji) + " " + itemView.getContext().getString(R.string.rescuer_label_colon) + " " + 
                             (hospital.getRescuerName() != null ? hospital.getRescuerName() : itemView.getContext().getString(R.string.unknown)) + "\n";
                if (hospital.getEstimatedArrivalMinutes() != null) {
                    seniorInfo += itemView.getContext().getString(R.string.eta_emoji) + " " + itemView.getContext().getString(R.string.eta_label) + " " + 
                                 String.format("%.1f", hospital.getEstimatedArrivalMinutes()) + " " + itemView.getContext().getString(R.string.minutes_abbreviation);
                }
                
                hospitalSpecializationText.setText(seniorInfo);
                hospitalSpecializationText.setVisibility(View.VISIBLE);
                hospitalSpecializationText.setTextColor(itemView.getContext().getResources().getColor(R.color.emergency_red));
                hospitalSpecializationText.setTextSize(12);
                
            } else {
                // NORMAL MODE: Show all hospital details
                
                // Show all hospital details including hospital name
                hospitalNameText.setVisibility(View.VISIBLE);
                hospitalAddressText.setVisibility(View.VISIBLE);
                hospitalContactText.setVisibility(View.VISIBLE);
                hospitalEmailText.setVisibility(View.VISIBLE);
                hospitalStatusText.setVisibility(View.VISIBLE);
                hospitalBedsText.setVisibility(View.VISIBLE);
                
                // Set hospital name
                hospitalNameText.setText(hospital.getHospitalName() != null ? hospital.getHospitalName() : itemView.getContext().getString(R.string.unknown_hospital));
                
                // Set address
                hospitalAddressText.setText(hospital.getAddress() != null ? hospital.getAddress() : itemView.getContext().getString(R.string.address_not_available));

                // Set contact number
                hospitalContactText.setText(hospital.getContactNumber() != null ? hospital.getContactNumber() : itemView.getContext().getString(R.string.contact_not_available));

                // Set email
                hospitalEmailText.setText(hospital.getEmail() != null ? hospital.getEmail() : itemView.getContext().getString(R.string.email_not_available));

                // Set status with color coding
                String status = hospital.getStatusDisplay();
                hospitalStatusText.setText(status);
                if (status.equals(itemView.getContext().getString(R.string.open_status))) {
                    hospitalStatusText.setTextColor(itemView.getContext().getResources().getColor(R.color.success_green));
                } else if (status.equals(itemView.getContext().getString(R.string.busy_status))) {
                    hospitalStatusText.setTextColor(itemView.getContext().getResources().getColor(R.color.emergency_red));
                } else {
                    hospitalStatusText.setTextColor(itemView.getContext().getResources().getColor(R.color.gray));
                }

                // Set bed capacity
                hospitalBedsText.setText(hospital.getBedStatus());

                // Set specialization
                if (hospital.getSpecialization() != null && !hospital.getSpecialization().isEmpty()) {
                    hospitalSpecializationText.setText(hospital.getSpecialization());
                    hospitalSpecializationText.setVisibility(View.VISIBLE);
                    hospitalSpecializationText.setTextColor(itemView.getContext().getResources().getColor(R.color.black));
                    hospitalSpecializationText.setTextSize(14);
                } else {
                    hospitalSpecializationText.setVisibility(View.GONE);
                }
            }

            // Set profile image
            if (hospital.getProfileImageUrl() != null && !hospital.getProfileImageUrl().isEmpty()) {
                Picasso.get()
                        .load(hospital.getProfileImageUrl())
                        .placeholder(R.drawable.ic_hospital)
                        .error(R.drawable.ic_hospital)
                        .transform(new CircleTransform())
                        .into(hospitalImageView);
            } else {
                hospitalImageView.setImageResource(R.drawable.ic_hospital);
            }

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onHospitalClick(hospital);
                }
            });
        }
    }
}
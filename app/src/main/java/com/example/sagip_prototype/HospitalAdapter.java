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
        private TextView hospitalStatusText;
        private TextView hospitalBedsText;
        private TextView hospitalSpecializationText;

        public HospitalViewHolder(@NonNull View itemView) {
            super(itemView);
            hospitalImageView = itemView.findViewById(R.id.hospitalImageView);
            hospitalNameText = itemView.findViewById(R.id.hospitalNameText);
            hospitalAddressText = itemView.findViewById(R.id.hospitalAddressText);
            hospitalContactText = itemView.findViewById(R.id.hospitalContactText);
            hospitalStatusText = itemView.findViewById(R.id.hospitalStatusText);
            hospitalBedsText = itemView.findViewById(R.id.hospitalBedsText);
            hospitalSpecializationText = itemView.findViewById(R.id.hospitalSpecializationText);
        }

        public void bind(Hospital hospital, OnHospitalClickListener listener) {
            // Set hospital name
            hospitalNameText.setText(hospital.getHospitalName() != null ? hospital.getHospitalName() : "Unknown Hospital");

            // Set address
            hospitalAddressText.setText(hospital.getAddress() != null ? hospital.getAddress() : "Address not available");

            // Set contact number
            hospitalContactText.setText(hospital.getContactNumber() != null ? hospital.getContactNumber() : "Contact not available");

            // Set status with color coding
            String status = hospital.getStatusDisplay();
            hospitalStatusText.setText(status);
            if (status.equals("Open")) {
                hospitalStatusText.setTextColor(itemView.getContext().getResources().getColor(R.color.success_green));
            } else if (status.equals("Busy")) {
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
            } else {
                hospitalSpecializationText.setVisibility(View.GONE);
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
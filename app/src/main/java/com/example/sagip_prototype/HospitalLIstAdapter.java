package com.example.sagip_prototype;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class HospitalLIstAdapter extends RecyclerView.Adapter<HospitalLIstAdapter.HospitalLIstViewHolder> {
    private List<HospitalLIst> hospitals;
    private OnHospitalLIstClickListener listener;

    public interface OnHospitalLIstClickListener {
        void onHospitalClick(HospitalLIst hospital);
    }

    public HospitalLIstAdapter(List<HospitalLIst> hospitals, OnHospitalLIstClickListener listener) {
        this.hospitals = hospitals;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HospitalLIstViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.hospital_card_item, parent, false);
        return new HospitalLIstViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HospitalLIstViewHolder holder, int position) {
        HospitalLIst hospital = hospitals.get(position);
        holder.bind(hospital, listener);
    }

    @Override
    public int getItemCount() {
        return hospitals.size();
    }

    public void updateHospitals(List<HospitalLIst> newHospitals) {
        this.hospitals = newHospitals;
        notifyDataSetChanged();
    }

    static class HospitalLIstViewHolder extends RecyclerView.ViewHolder {
        private TextView hospitalNameText;
        private TextView hospitalAddressText;
        private TextView hospitalContactText;
        private TextView hospitalStatusText;
        private TextView hospitalBedsText;
        private TextView hospitalSpecializationText;

        public HospitalLIstViewHolder(@NonNull View itemView) {
            super(itemView);
            hospitalNameText = itemView.findViewById(R.id.hospitalNameText);
            hospitalAddressText = itemView.findViewById(R.id.hospitalAddressText);
            hospitalContactText = itemView.findViewById(R.id.hospitalContactText);
            hospitalStatusText = itemView.findViewById(R.id.hospitalStatusText);
            hospitalBedsText = itemView.findViewById(R.id.hospitalBedsText);
            hospitalSpecializationText = itemView.findViewById(R.id.hospitalSpecializationText);
        }

        public void bind(HospitalLIst hospital, OnHospitalLIstClickListener listener) {
            // Set hospital name
            hospitalNameText.setText(hospital.getHospitalName() != null ? hospital.getHospitalName() : "Unknown Hospital");

            // Set address
            hospitalAddressText.setText(hospital.getHospitalAddress() != null ? hospital.getHospitalAddress() : "Address not available");

            // Set contact number (not available in HospitalLIst, so hide it)
            hospitalContactText.setVisibility(View.GONE);

            // Set status with color coding
            String status = hospital.getCalculatedStatus();
            hospitalStatusText.setText(status.toUpperCase());
            hospitalStatusText.setTextColor(hospital.getStatusColor());

            // Set bed capacity
            String bedInfo = hospital.getAvailableBeds() + "/" + hospital.getTotalBeds() + " beds";
            hospitalBedsText.setText(bedInfo);

            // Set specialization (not available in HospitalLIst, so hide it)
            hospitalSpecializationText.setVisibility(View.GONE);

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onHospitalClick(hospital);
                }
            });
        }
    }
}

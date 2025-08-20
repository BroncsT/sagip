package com.example.sagip_prototype;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HospitalAdapter extends RecyclerView.Adapter<HospitalAdapter.HospitalViewHolder> {

    List<HospitalLIst> hospitalList;
    private Context context;

    public HospitalAdapter(List<HospitalLIst> hospitalList, Context context) {
        this.hospitalList = hospitalList;
        this.context = context;
    }

    @NonNull
    @Override
    public HospitalAdapter.HospitalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_data, parent, false);
        return new HospitalViewHolder(view);

    }

    @Override
    public void onBindViewHolder(@NonNull HospitalAdapter.HospitalViewHolder holder, int position) {
        HospitalLIst hospital = hospitalList.get(position);
        
        // Set hospital name
        holder.hospitalNameTextView.setText(hospital.getHospitalName());
        
        // Set hospital address
        if (hospital.getHospitalAddress() != null) {
            holder.hospitalAddressTextView.setText(hospital.getHospitalAddress());
        } else {
            holder.hospitalAddressTextView.setText("Address not available");
        }
        
        // Set status information (automatically calculated)
        String calculatedStatus = hospital.getCalculatedStatus();
        if (!calculatedStatus.equals("unknown")) {
            String statusText = hospital.getStatusEmoji() + " " + 
                              calculatedStatus.toUpperCase();
            holder.hospitalStatusTextView.setText(statusText);
            holder.hospitalStatusTextView.setTextColor(hospital.getStatusColor());
        } else {
            holder.hospitalStatusTextView.setText("⚪ Status not available");
            holder.hospitalStatusTextView.setTextColor(0xFF9E9E9E);
        }
        
        // Set bed information
        if (hospital.getAvailableBeds() != null && hospital.getTotalBeds() != null) {
            String bedInfo = "Beds: " + hospital.getAvailableBeds() + "/" + hospital.getTotalBeds();
            holder.hospitalBedInfoTextView.setText(bedInfo);
        } else {
            holder.hospitalBedInfoTextView.setText("Bed info not available");
        }
        
        // Set doctors information
        if (hospital.getDoctorsAvailable() != null) {
            String doctorInfo = "Doctors: " + hospital.getDoctorsAvailable();
            holder.hospitalDoctorInfoTextView.setText(doctorInfo);
        } else {
            holder.hospitalDoctorInfoTextView.setText("Doctor info not available");
        }
    }

    @Override
    public int getItemCount() {
        return hospitalList.size();
    }

    public static class HospitalViewHolder extends RecyclerView.ViewHolder {
        TextView hospitalNameTextView;
        TextView hospitalAddressTextView;
        TextView hospitalStatusTextView;
        TextView hospitalBedInfoTextView;
        TextView hospitalDoctorInfoTextView;
        CardView cardView;
        
        public HospitalViewHolder(@NonNull View itemView) {
            super(itemView);
            hospitalNameTextView = itemView.findViewById(R.id.hospitalName);
            hospitalAddressTextView = itemView.findViewById(R.id.hospitalAddress);
            hospitalStatusTextView = itemView.findViewById(R.id.hospitalStatus);
            hospitalBedInfoTextView = itemView.findViewById(R.id.hospitalBedInfo);
            hospitalDoctorInfoTextView = itemView.findViewById(R.id.hospitalDoctorInfo);
            cardView = itemView.findViewById(R.id.hospitalCardView);
        }
    }
}

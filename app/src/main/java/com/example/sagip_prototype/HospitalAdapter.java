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
        holder.hospitalNameTextView.setText(hospital.getHospitalName());


    }

    @Override
    public int getItemCount() {
        return hospitalList.size();
    }

    public static class HospitalViewHolder extends RecyclerView.ViewHolder {
        TextView hospitalNameTextView;
        CardView cardView;
        public HospitalViewHolder(@NonNull View itemView) {
            super(itemView);
            hospitalNameTextView = itemView.findViewById(R.id.hospitalName);
            cardView = itemView.findViewById(R.id.hospitalCardView);
        }
    }
}

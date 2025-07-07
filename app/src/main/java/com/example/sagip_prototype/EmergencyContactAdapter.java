package com.example.sagip_prototype;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class EmergencyContactAdapter extends RecyclerView.Adapter<EmergencyContactAdapter.ViewHolder> {

    List<Emergency_Contacts> emergencyContacts;
    private Context context;

    public EmergencyContactAdapter(List<Emergency_Contacts> emergencyContacts, Context context) {
        this.emergencyContacts = emergencyContacts;
        this.context = context;
    }

    @NonNull
    @Override
    public EmergencyContactAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_list, parent, false);
        return new ViewHolder(view);

    }

    @Override
    public void onBindViewHolder(@NonNull EmergencyContactAdapter.ViewHolder holder, int position) {
        Emergency_Contacts contact = emergencyContacts.get(position);
        holder.name.setText(contact.getName());
        holder.number.setText(contact.getNumber());

    }

    @Override
    public int getItemCount() {
        return emergencyContacts.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, number;
         public ViewHolder(@NonNull View itemView) {
            super(itemView);

            name = itemView.findViewById(R.id.contactNameTextView);
            number = itemView.findViewById(R.id.phoneNumberTextView);
        }
    }
}

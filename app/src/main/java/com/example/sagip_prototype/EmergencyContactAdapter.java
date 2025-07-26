package com.example.sagip_prototype;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class EmergencyContactAdapter extends RecyclerView.Adapter<EmergencyContactAdapter.ViewHolder> {

    List<Emergency_Contacts> emergencyContacts;
    private Context context;
    private OnContactActionListener listener;

    // Interface for handling button clicks
    public interface OnContactActionListener {
        void onDeleteContact(int position, Emergency_Contacts contact);
        void onUpdateContact(int position, Emergency_Contacts contact);
    }

    public EmergencyContactAdapter(List<Emergency_Contacts> emergencyContacts, Context context) {
        this.emergencyContacts = emergencyContacts;
        this.context = context;
    }

    // Method to set the listener
    public void setOnContactActionListener(OnContactActionListener listener) {
        this.listener = listener;
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

        // Set click listeners for buttons
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteContact(position, contact);
            }
        });

        holder.updateButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUpdateContact(position, contact);
            }
        });
    }

    @Override
    public int getItemCount() {
        return emergencyContacts.size();
    }

    // Method to remove item from list (call this after successful deletion)
    public void removeItem(int position) {
        emergencyContacts.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, emergencyContacts.size());
    }

    // Method to update item in list (call this after successful update)
    public void updateItem(int position, Emergency_Contacts updatedContact) {
        emergencyContacts.set(position, updatedContact);
        notifyItemChanged(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, number;
        Button deleteButton, updateButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            name = itemView.findViewById(R.id.contactNameTextView);
            number = itemView.findViewById(R.id.phoneNumberTextView);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            updateButton = itemView.findViewById(R.id.updateButton);
        }
    }
}
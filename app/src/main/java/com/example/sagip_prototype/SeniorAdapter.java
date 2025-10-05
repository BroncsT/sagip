package com.example.sagip_prototype;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.List;

public class SeniorAdapter extends RecyclerView.Adapter<SeniorAdapter.SeniorViewHolder> {

    private List<Senior> seniors;
    private OnSeniorClickListener listener;

    // Interface for click events
    public interface OnSeniorClickListener {
        void onSeniorClick(Senior senior);
    }

    public SeniorAdapter(List<Senior> seniors, OnSeniorClickListener listener) {
        this.seniors = seniors;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SeniorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.senior_card_item, parent, false);
        return new SeniorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SeniorViewHolder holder, int position) {
        Senior senior = seniors.get(position);
        android.util.Log.d("SeniorAdapter", "Binding senior at position " + position + ": " + senior.getFullName());
        holder.bind(senior, listener);
    }

    @Override
    public int getItemCount() {
        return seniors.size();
    }

    // Method to update the list
    public void updateSeniors(List<Senior> newSeniors) {
        android.util.Log.d("SeniorAdapter", "Updating seniors list with " + newSeniors.size() + " items");
        this.seniors = newSeniors;
        notifyDataSetChanged();
    }

    // ViewHolder class
    public static class SeniorViewHolder extends RecyclerView.ViewHolder {
        private ImageView seniorImageView;
        private TextView nameText;
        private TextView ageText;
        private TextView addressText;

        public SeniorViewHolder(@NonNull View itemView) {
            super(itemView);
            seniorImageView = itemView.findViewById(R.id.seniorImgView);
            nameText = itemView.findViewById(R.id.seniorProfileName);
            ageText = itemView.findViewById(R.id.seniorAge);
            addressText = itemView.findViewById(R.id.seniorAddress);
        }

        public void bind(Senior senior, OnSeniorClickListener listener) {
            // Set name
            nameText.setText(senior.getFullName());

            // Set age
            int age = senior.getAge();
            if (age > 0) {
                ageText.setText("Age: " + age);
            } else {
                ageText.setText("Age: N/A");
            }

            // Set address (barangay)
            String barangay = senior.getBarangay();
            addressText.setText(String.format(itemView.getContext().getString(R.string.barangay_label), barangay != null ? barangay : "N/A"));

            // Set profile image - prioritize selfie verification image
            String imageUrl = senior.getSelfieVerificationUrl();
            String imageSource = "selfie verification";
            
            // Debug logging
            android.util.Log.d("SeniorAdapter", "Senior: " + senior.getFullName() + 
                " - SelfieVerificationUrl: " + senior.getSelfieVerificationUrl() + 
                " - ProfileImageUrl: " + senior.getProfileImageUrl());
            
            // If no selfie verification image, fall back to profile image
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = senior.getProfileImageUrl();
                imageSource = "profile";
                android.util.Log.d("SeniorAdapter", "No selfie verification image, using profile image: " + imageUrl);
            } else {
                android.util.Log.d("SeniorAdapter", "Using selfie verification image: " + imageUrl);
            }
            
            // Set default image immediately for instant display
            seniorImageView.setImageResource(R.drawable.ic_senior_person);
            
            if (imageUrl != null && !imageUrl.isEmpty()) {
                android.util.Log.d("SeniorAdapter", "Loading " + imageSource + " image for " + senior.getFullName() + ": " + imageUrl);
                
                // Use Picasso with optimized settings for immediate loading
                try {
                    Picasso.get()
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_senior_person)
                            .error(R.drawable.ic_senior_person)
                            .resize(120, 120) // Resize to ensure consistent size
                            .centerCrop() // Center crop before applying circle transform
                            .transform(new CircleTransform())
                            .noFade() // Disable fade animation for immediate display
                            .priority(com.squareup.picasso.Picasso.Priority.HIGH) // High priority loading
                            .into(seniorImageView);
                } catch (Exception e) {
                    android.util.Log.e("SeniorAdapter", "Error loading image: " + e.getMessage());
                    seniorImageView.setImageResource(R.drawable.ic_senior_person);
                }
            } else {
                android.util.Log.d("SeniorAdapter", "No image available for " + senior.getFullName() + ", using default");
                // Default image already set above
            }

            // Set click listener for the entire card
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    android.util.Log.d("SeniorAdapter", "Card clicked for senior: " + senior.getFullName());
                    listener.onSeniorClick(senior);
                }
            });
            
            // Ensure the card is clickable
            itemView.setClickable(true);
            itemView.setFocusable(true);
        }
    }
}

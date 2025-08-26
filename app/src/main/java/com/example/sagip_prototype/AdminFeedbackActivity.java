package com.example.sagip_prototype;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminFeedbackActivity extends AppCompatActivity {

    private static final String TAG = "AdminFeedbackActivity";

    private FirebaseFirestore db;
    private RecyclerView feedbackRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyStateText;
    private AdminFeedbackAdapter feedbackAdapter;
    private List<MyReportsActivity.FeedbackReport> feedbackList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_feedback);

        initializeFirebase();
        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        loadAllFeedback();
    }

    private void initializeFirebase() {
        db = FirebaseFirestore.getInstance();
    }

    private void initializeViews() {
        feedbackRecyclerView = findViewById(R.id.feedbackRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateText = findViewById(R.id.emptyStateText);
    }

    private void setupRecyclerView() {
        feedbackList = new ArrayList<>();
        feedbackAdapter = new AdminFeedbackAdapter(feedbackList);
        feedbackRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        feedbackRecyclerView.setAdapter(feedbackAdapter);
    }

    private void setupClickListeners() {
        swipeRefreshLayout.setOnRefreshListener(this::loadAllFeedback);
    }

    private void loadAllFeedback() {
        db.collection("feedback")
                .orderBy("timestamp")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    feedbackList.clear();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        MyReportsActivity.FeedbackReport feedback = document.toObject(MyReportsActivity.FeedbackReport.class);
                        if (feedback != null) {
                            feedback.setId(document.getId());
                            feedbackList.add(feedback);
                        }
                    }
                    
                    feedbackAdapter.notifyDataSetChanged();
                    updateEmptyState();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading feedback", e);
                    Toast.makeText(this, "Failed to load feedback", Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
                });
    }

    private void updateEmptyState() {
        if (feedbackList.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
        }
    }

    private void showEmptyState() {
        emptyStateText.setVisibility(View.VISIBLE);
        feedbackRecyclerView.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        emptyStateText.setVisibility(View.GONE);
        feedbackRecyclerView.setVisibility(View.VISIBLE);
    }

    // Admin Feedback Adapter
    private class AdminFeedbackAdapter extends RecyclerView.Adapter<AdminFeedbackAdapter.FeedbackViewHolder> {

        private List<MyReportsActivity.FeedbackReport> feedbackList;

        public AdminFeedbackAdapter(List<MyReportsActivity.FeedbackReport> feedbackList) {
            this.feedbackList = feedbackList;
        }

        @NonNull
        @Override
        public FeedbackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_feedback, parent, false);
            return new FeedbackViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FeedbackViewHolder holder, int position) {
            MyReportsActivity.FeedbackReport feedback = feedbackList.get(position);
            holder.bind(feedback);
        }

        @Override
        public int getItemCount() {
            return feedbackList.size();
        }

        class FeedbackViewHolder extends RecyclerView.ViewHolder {
            private TextView subjectText;
            private TextView userTypeText;
            private TextView feedbackTypeText;
            private TextView priorityText;
            private TextView statusText;
            private TextView dateText;
            private TextView messageText;

            public FeedbackViewHolder(@NonNull View itemView) {
                super(itemView);
                subjectText = itemView.findViewById(R.id.subjectText);
                userTypeText = itemView.findViewById(R.id.userTypeText);
                feedbackTypeText = itemView.findViewById(R.id.feedbackTypeText);
                priorityText = itemView.findViewById(R.id.priorityText);
                statusText = itemView.findViewById(R.id.statusText);
                dateText = itemView.findViewById(R.id.dateText);
                messageText = itemView.findViewById(R.id.messageText);
            }

            public void bind(MyReportsActivity.FeedbackReport feedback) {
                subjectText.setText(feedback.getSubject());
                userTypeText.setText("User: " + (feedback.getUserType() != null ? feedback.getUserType() : "Unknown"));
                feedbackTypeText.setText(feedback.getFeedbackType());
                priorityText.setText(feedback.getPriority());
                statusText.setText(feedback.getStatus());

                // Format date
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                String formattedDate = dateFormat.format(feedback.getTimestamp());
                dateText.setText(formattedDate);

                // Truncate message for preview
                String message = feedback.getMessage();
                if (message.length() > 100) {
                    message = message.substring(0, 100) + "...";
                }
                messageText.setText(message);

                // Set status color
                setStatusColor(feedback.getStatus());

                // Set click listener for managing feedback
                itemView.setOnClickListener(v -> showFeedbackManagementDialog(feedback));
            }

            private void setStatusColor(String status) {
                int color;
                switch (status) {
                    case "Pending":
                        color = getResources().getColor(android.R.color.holo_orange_dark);
                        break;
                    case "Under Review":
                        color = getResources().getColor(android.R.color.holo_blue_dark);
                        break;
                    case "Resolved":
                        color = getResources().getColor(android.R.color.holo_green_dark);
                        break;
                    case "Closed":
                        color = getResources().getColor(android.R.color.darker_gray);
                        break;
                    default:
                        color = getResources().getColor(android.R.color.darker_gray);
                        break;
                }
                statusText.setTextColor(color);
            }

            private void showFeedbackManagementDialog(MyReportsActivity.FeedbackReport feedback) {
                View dialogView = LayoutInflater.from(AdminFeedbackActivity.this)
                        .inflate(R.layout.dialog_admin_feedback_management, null);

                TextView subjectText = dialogView.findViewById(R.id.subjectText);
                TextView messageText = dialogView.findViewById(R.id.messageText);
                TextView userInfoText = dialogView.findViewById(R.id.userInfoText);
                TextView currentStatusText = dialogView.findViewById(R.id.currentStatusText);
                EditText adminResponseEditText = dialogView.findViewById(R.id.adminResponseEditText);

                subjectText.setText(feedback.getSubject());
                messageText.setText(feedback.getMessage());
                
                String userInfo = "User Type: " + (feedback.getUserType() != null ? feedback.getUserType() : "Unknown");
                if (!feedback.isAnonymous() && feedback.getUserId() != null) {
                    userInfo += "\nUser ID: " + feedback.getUserId();
                }
                if (feedback.isIncludeContact()) {
                    if (feedback.getContactEmail() != null && !feedback.getContactEmail().isEmpty()) {
                        userInfo += "\nEmail: " + feedback.getContactEmail();
                    }
                    if (feedback.getContactPhone() != null && !feedback.getContactPhone().isEmpty()) {
                        userInfo += "\nPhone: " + feedback.getContactPhone();
                    }
                }
                userInfoText.setText(userInfo);
                currentStatusText.setText("Current Status: " + feedback.getStatus());

                if (feedback.getAdminResponse() != null && !feedback.getAdminResponse().isEmpty()) {
                    adminResponseEditText.setText(feedback.getAdminResponse());
                }

                new AlertDialog.Builder(AdminFeedbackActivity.this)
                        .setTitle("Manage Feedback")
                        .setView(dialogView)
                        .setPositiveButton("Update Status", (dialog, which) -> {
                            String adminResponse = adminResponseEditText.getText().toString().trim();
                            updateFeedbackStatus(feedback, adminResponse);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
            }

            private void updateFeedbackStatus(MyReportsActivity.FeedbackReport feedback, String adminResponse) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("adminResponse", adminResponse);
                updates.put("adminResponseDate", new Date());

                // Determine new status based on admin response
                String newStatus = adminResponse.isEmpty() ? "Under Review" : "Resolved";
                updates.put("status", newStatus);

                db.collection("feedback")
                        .document(feedback.getId())
                        .update(updates)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(AdminFeedbackActivity.this, "Feedback updated successfully", Toast.LENGTH_SHORT).show();
                            loadAllFeedback(); // Refresh the list
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error updating feedback", e);
                            Toast.makeText(AdminFeedbackActivity.this, "Failed to update feedback", Toast.LENGTH_SHORT).show();
                        });
            }
        }
    }
}

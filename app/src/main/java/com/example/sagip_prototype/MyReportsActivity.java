package com.example.sagip_prototype;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyReportsActivity extends AppCompatActivity {

    private static final String TAG = "MyReportsActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;

    private RecyclerView reportsRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyStateText;
    private FloatingActionButton newReportFab;
    private ReportsAdapter reportsAdapter;
    private List<FeedbackReport> reportsList;

    private String userId;
    private String userType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_reports);

        initializeFirebase();
        initializeViews();
        loadUserInfo();
        setupRecyclerView();
        setupClickListeners();
        loadReports();
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
    }

    private void initializeViews() {
        reportsRecyclerView = findViewById(R.id.reportsRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateText = findViewById(R.id.emptyStateText);
        newReportFab = findViewById(R.id.newReportFab);
    }

    private void loadUserInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            userType = sharedPreferences.getString("userType", "unknown");
        }
    }

    private void setupRecyclerView() {
        reportsList = new ArrayList<>();
        reportsAdapter = new ReportsAdapter(reportsList);
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        reportsRecyclerView.setAdapter(reportsAdapter);
    }

    private void setupClickListeners() {
        swipeRefreshLayout.setOnRefreshListener(this::loadReports);
        newReportFab.setOnClickListener(v -> {
            Intent intent = new Intent(this, FeedbackActivity.class);
            startActivity(intent);
        });
    }

    private void loadReports() {
        if (userId == null) {
            showEmptyState();
            return;
        }

        db.collection("feedback")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    reportsList.clear();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        FeedbackReport report = document.toObject(FeedbackReport.class);
                        if (report != null) {
                            report.setId(document.getId());
                            reportsList.add(report);
                        }
                    }
                    
                    reportsAdapter.notifyDataSetChanged();
                    updateEmptyState();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading reports", e);
                    Toast.makeText(this, getString(R.string.failed_to_load_reports), Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
                });
    }

    private void updateEmptyState() {
        if (reportsList.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
        }
    }

    private void showEmptyState() {
        emptyStateText.setVisibility(View.VISIBLE);
        reportsRecyclerView.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        emptyStateText.setVisibility(View.GONE);
        reportsRecyclerView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReports();
    }

    // FeedbackReport data class
    public static class FeedbackReport {
        private String id;
        private String feedbackType;
        private String subject;
        private String message;
        private boolean includeContact;
        private String contactEmail;
        private String contactPhone;
        private String status;
        private Date timestamp;
        private String userType;
        private String userId;
        private String userEmail;
        private String attachmentUrl;
        private String adminResponse;
        private Date adminResponseDate;
        private String priority;
        private boolean anonymous;

        // Default constructor for Firestore
        public FeedbackReport() {}

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getFeedbackType() { return feedbackType; }
        public void setFeedbackType(String feedbackType) { this.feedbackType = feedbackType; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public boolean isIncludeContact() { return includeContact; }
        public void setIncludeContact(boolean includeContact) { this.includeContact = includeContact; }

        public String getContactEmail() { return contactEmail; }
        public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Date getTimestamp() { return timestamp; }
        public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getUserEmail() { return userEmail; }
        public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

        public String getAttachmentUrl() { return attachmentUrl; }
        public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

        public String getAdminResponse() { return adminResponse; }
        public void setAdminResponse(String adminResponse) { this.adminResponse = adminResponse; }

        public Date getAdminResponseDate() { return adminResponseDate; }
        public void setAdminResponseDate(Date adminResponseDate) { this.adminResponseDate = adminResponseDate; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }

        public boolean isAnonymous() { return anonymous; }
        public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }
    }

    // RecyclerView Adapter
    private class ReportsAdapter extends RecyclerView.Adapter<ReportsAdapter.ReportViewHolder> {

        private List<FeedbackReport> reports;

        public ReportsAdapter(List<FeedbackReport> reports) {
            this.reports = reports;
        }

        @NonNull
        @Override
        public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_feedback_report, parent, false);
            return new ReportViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
            FeedbackReport report = reports.get(position);
            holder.bind(report);
        }

        @Override
        public int getItemCount() {
            return reports.size();
        }

        class ReportViewHolder extends RecyclerView.ViewHolder {
            private TextView subjectText;
            private TextView feedbackTypeText;
            private TextView statusText;
            private TextView dateText;
            private TextView adminResponseText;
            private View adminResponseCard;

            public ReportViewHolder(@NonNull View itemView) {
                super(itemView);
                subjectText = itemView.findViewById(R.id.subjectText);
                feedbackTypeText = itemView.findViewById(R.id.feedbackTypeText);
                statusText = itemView.findViewById(R.id.statusText);
                dateText = itemView.findViewById(R.id.dateText);
                adminResponseText = itemView.findViewById(R.id.adminResponseText);
                adminResponseCard = itemView.findViewById(R.id.adminResponseCard);
            }

            public void bind(FeedbackReport report) {
                subjectText.setText(report.getSubject());
                feedbackTypeText.setText(report.getFeedbackType());
                statusText.setText(report.getStatus());

                // Format date
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                String formattedDate = dateFormat.format(report.getTimestamp());
                dateText.setText(formattedDate);

                // Show admin response if available
                if (report.getAdminResponse() != null && !report.getAdminResponse().isEmpty()) {
                    adminResponseCard.setVisibility(View.VISIBLE);
                    adminResponseText.setText(report.getAdminResponse());
                } else {
                    adminResponseCard.setVisibility(View.GONE);
                }

                // Set status color
                setStatusColor(report.getStatus());

                // Set click listener for viewing details
                itemView.setOnClickListener(v -> showReportDetails(report));
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

            private void showReportDetails(FeedbackReport report) {
                new AlertDialog.Builder(MyReportsActivity.this)
                        .setTitle(report.getSubject())
                        .setMessage(createReportDetailsMessage(report))
                        .setPositiveButton(getString(R.string.feedback_view_details), (dialog, which) -> {
                            // You can implement a detailed view activity here
                            dialog.dismiss();
                        })
                        .setNegativeButton(getString(R.string.ok), (dialog, which) -> dialog.dismiss())
                        .show();
            }

            private String createReportDetailsMessage(FeedbackReport report) {
                StringBuilder message = new StringBuilder();
                message.append("Type: ").append(report.getFeedbackType()).append("\n\n");
                message.append("Message:\n").append(report.getMessage()).append("\n\n");
                message.append("Status: ").append(report.getStatus()).append("\n\n");
                
                if (report.getAdminResponse() != null && !report.getAdminResponse().isEmpty()) {
                    message.append("Admin Response:\n").append(report.getAdminResponse());
                } else {
                    message.append(getString(R.string.feedback_no_response));
                }
                
                return message.toString();
            }
        }
    }
}

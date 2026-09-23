package com.example.carelink;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.carelink.model.ChatroomModel;
import com.example.carelink.model.UserModel;
import com.example.carelink.model.VisitReportModel;
import com.example.carelink.util.AndroidUtil;
import com.example.carelink.util.FirebaseUtil;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity for displaying a detailed visit report.
 * Provides distinct views for Patients (limited info) and Caregivers (full clinical details),
 * handles real-time notification clearance, and facilitates chat navigation.
 */
public class MyVisitReportActivity extends AppCompatActivity {

    TextView tvDateTime, tvComplaints, tvReport, tvPlan, tvRecommendations, tvFuture;
    TextView tvLabelReport, tvLabelPlan;
    ImageView btnBack, btnChat;

    String reportId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_visit_report);

        // Bind UI elements
        tvDateTime = findViewById(R.id.tv_report_date_time);
        tvComplaints = findViewById(R.id.tv_report_complaints);
        tvLabelReport = findViewById(R.id.tv_label_report);
        tvReport = findViewById(R.id.tv_report_private);
        tvLabelPlan = findViewById(R.id.tv_label_therapeutic_plan);
        tvPlan = findViewById(R.id.tv_report_plan);
        tvRecommendations = findViewById(R.id.tv_report_recommendations);
        tvFuture = findViewById(R.id.tv_report_future);

        btnBack = findViewById(R.id.btnBack);
        btnChat = findViewById(R.id.btnChat);

        // Check if activity was launched via push notification to adjust navigation behavior
        boolean fromNotification = getIntent().getBooleanExtra("fromNotification", false);

        // Custom back-press handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Return to the main appointments view if the user arrived via notification
                if (fromNotification || isTaskRoot()) {
                    Intent intent = new Intent(MyVisitReportActivity.this, MainActivity.class);
                    intent.putExtra("targetFragment", "AppointmentsFragment");
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
                finish();
            }
        });

        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        reportId = getIntent().getStringExtra("appointmentId");

        if (reportId != null && !reportId.isEmpty()) {
            fetchReportDetails();
        } else {
            Toast.makeText(this, "Error loading report", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Real-time listener for the visit report document.
     * Triggers notification clearance if the patient views a doctor's update.
     */
    private void fetchReportDetails() {
        FirebaseFirestore.getInstance().collection("visit_reports")
                .document(reportId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) { finish(); return; }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        VisitReportModel model = documentSnapshot.toObject(VisitReportModel.class);
                        if (model != null) {
                            // Check if current user is the patient and has not read this specific update
                            boolean isPatient = FirebaseUtil.currentUserId().equals(model.getPatientId());
                            boolean isUnread = isPatient && (model.getReadBy() == null || !model.getReadBy().containsKey(FirebaseUtil.currentUserId()));

                            if (isUnread) clearUnreadCounters(model);

                            populateViews(model, isPatient);
                            setupChatButton(model);
                        }
                    } else {
                        Toast.makeText(this, "This report was removed.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    /**
     * Updates Firestore to mark this report as read and decrements the global unread notification counter.
     */
    private void clearUnreadCounters(VisitReportModel model) {
        String myId = FirebaseUtil.currentUserId();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Mark document as read
        db.collection("visit_reports").document(reportId).update("readBy." + myId, true);

        // Decrement user's global notification badge
        db.collection("users").document(myId).update("unreadMedicalRecords", FieldValue.increment(-1));

        // Decrement notification count in the associated chatroom
        String otherUserId = myId.equals(model.getPatientId()) ? model.getCaregiverId() : model.getPatientId();
        if (otherUserId != null) {
            db.collection("chatrooms").whereArrayContains("userIds", myId).get().addOnSuccessListener(query -> {
                for (DocumentSnapshot doc : query.getDocuments()) {
                    ChatroomModel chat = doc.toObject(ChatroomModel.class);
                    if (chat != null && chat.getUserIds() != null && chat.getUserIds().contains(otherUserId)) {
                        doc.getReference().update("unreadMedicalCount." + myId, FieldValue.increment(-1));
                        break;
                    }
                }
            });
        }
    }

    /** Converts database date format (YYYY-MM-DD) to readable display format (DD/MM/YYYY) */
    private String formatDateToDisplay(String dbDate) {
        if (dbDate == null || dbDate.isEmpty()) return "";
        try {
            SimpleDateFormat sdfIn = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat sdfOut = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date d = sdfIn.parse(dbDate);
            return sdfOut.format(d);
        } catch (Exception e) {
            return dbDate;
        }
    }

    /**
     * Renders report data. Note: Clinical details are hidden from the patient view for privacy reasons.
     */
    private void populateViews(VisitReportModel model, boolean isPatient) {
        String vDate = formatDateToDisplay(model.getVisitDate());
        tvDateTime.setText(vDate + " | " + model.getVisitHours());
        tvComplaints.setText(model.getPatientComplaints() != null && !model.getPatientComplaints().isEmpty() ? model.getPatientComplaints() : "No complaints logged.");
        tvRecommendations.setText(model.getRecommendations() != null && !model.getRecommendations().isEmpty() ? model.getRecommendations() : "No recommendations logged.");

        // Build future visit information section
        if (model.isFutureVisitScheduled()) {
            String fDate = model.getFutureVisitDate() != null ? formatDateToDisplay(model.getFutureVisitDate()) : "N/A";
            String fArr = model.getFutureVisitArrival() != null ? model.getFutureVisitArrival() : "--:--";
            String fDep = model.getFutureVisitDeparture() != null ? model.getFutureVisitDeparture() : "--:--";
            String fNotes = model.getFutureVisitNotes();

            StringBuilder details = new StringBuilder();
            details.append("Date: ").append(fDate).append("\n");
            details.append("Time: ").append(fArr).append(" - ").append(fDep);
            if (fNotes != null && !fNotes.isEmpty()) {
                details.append("\nNotes: ").append(fNotes);
            }
            tvFuture.setText(details.toString());
        } else {
            tvFuture.setText("No future visit scheduled.");
        }

        // Hide clinical fields for patients
        if (isPatient) {
            tvLabelReport.setVisibility(View.GONE);
            tvReport.setVisibility(View.GONE);
            tvLabelPlan.setVisibility(View.GONE);
            tvPlan.setVisibility(View.GONE);
        } else {
            // Show clinical fields for caregivers/doctors
            tvLabelReport.setVisibility(View.VISIBLE);
            tvReport.setVisibility(View.VISIBLE);
            tvReport.setText(model.getReport() != null && !model.getReport().isEmpty() ? model.getReport() : "No report generated.");

            tvLabelPlan.setVisibility(View.VISIBLE);
            tvPlan.setVisibility(View.VISIBLE);
            tvPlan.setText(model.getTherapeuticPlan() != null && !model.getTherapeuticPlan().isEmpty() ? model.getTherapeuticPlan() : "No therapeutic plan provided.");
        }
    }

    /** Sets up click functionality to open chat thread with the associated medical party */
    private void setupChatButton(VisitReportModel model) {
        String myId = FirebaseUtil.currentUserId();
        String otherUserId = myId.equals(model.getPatientId()) ? model.getCaregiverId() : model.getPatientId();

        if (otherUserId != null && !otherUserId.isEmpty()) {
            btnChat.setVisibility(View.VISIBLE);
            btnChat.setOnClickListener(v -> {
                btnChat.setEnabled(false);
                // Fetch other user profile data to start chat activity
                FirebaseFirestore.getInstance().collection("users").document(otherUserId).get()
                        .addOnSuccessListener(doc -> {
                            UserModel otherUser = doc.toObject(UserModel.class);
                            if (otherUser != null) {
                                Intent intent = new Intent(MyVisitReportActivity.this, ChatActivity.class);
                                AndroidUtil.passUserModelAsIntent(intent, otherUser);
                                startActivity(intent);
                            }
                            btnChat.setEnabled(true);
                        }).addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to load chat", Toast.LENGTH_SHORT).show();
                            btnChat.setEnabled(true);
                        });
            });
        }
    }
}
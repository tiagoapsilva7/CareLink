package com.example.carelink;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.carelink.model.VisitModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity that displays the schedule of visits for a specific calendar date.
 * Automatically filters out expired visits and sorts remaining ones by proximity to current time.
 */
public class MyDailyVisitsActivity extends AppCompatActivity {

    private LinearLayout llVisitsContainer;
    private LinearLayout llEmptyState;
    private TextView tvDateHeader;
    private FirebaseFirestore db;
    private String currentUserId;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_daily_visits);

        // UI aesthetics
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.buttons));

        // Binding layout elements
        llVisitsContainer = findViewById(R.id.ll_my_visits_container);
        llEmptyState = findViewById(R.id.ll_empty_state);
        tvDateHeader = findViewById(R.id.tvDateHeader);
        ImageView btnBack = findViewById(R.id.btnBack);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        selectedDate = getIntent().getStringExtra("selectedDate");

        // Format and display the selected date header
        if (selectedDate != null) {
            String displayDate = selectedDate;
            try {
                SimpleDateFormat sdfIn = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat sdfOut = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date dateObj = sdfIn.parse(selectedDate);
                if (dateObj != null) {
                    displayDate = sdfOut.format(dateObj);
                }
            } catch (Exception ignored) { }
            tvDateHeader.setText("Visits for: " + displayDate);
        }

        btnBack.setOnClickListener(v -> finish());
        loadAndSortVisits();
    }

    /**
     * Fetches visits for the logged-in caregiver, performs time-based filtering,
     * and sorts the list by proximity to the current time.
     */
    private void loadAndSortVisits() {
        db.collection("care_visits")
                .whereEqualTo("caregiverId", currentUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<VisitModel> validVisits = new ArrayList<>();
                    SimpleDateFormat fullSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    SimpleDateFormat dateOnlySdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                    Date realCurrentTime = new Date();
                    String todayString = dateOnlySdf.format(realCurrentTime);

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        VisitModel visit = doc.toObject(VisitModel.class);

                        // Only include visits matching the selected date
                        if (visit.getDate() == null || !visit.getDate().equals(selectedDate)) {
                            continue;
                        }

                        // Filtering logic: Hide past visits if today is the selected date
                        // and the visit ended more than 30 minutes ago.
                        if (visit.getDate().equals(todayString)) {
                            if (visit.getDepartureTime() != null && !visit.getDepartureTime().isEmpty()) {
                                try {
                                    String depString = visit.getDate() + " " + visit.getDepartureTime();
                                    Date depDate = fullSdf.parse(depString);
                                    // 1800000ms = 30 minutes
                                    if (realCurrentTime.getTime() > depDate.getTime() + 1800000) {
                                        continue;
                                    }
                                } catch (Exception e) {}
                            }
                        }
                        validVisits.add(visit);
                    }

                    // Sort by proximity to current time (e.g., upcoming visits appear first)
                    Collections.sort(validVisits, (v1, v2) -> {
                        try {
                            Date t1 = fullSdf.parse(v1.getDate() + " " + v1.getArrivalTime());
                            Date t2 = fullSdf.parse(v2.getDate() + " " + v2.getArrivalTime());

                            long diff1 = Math.abs(t1.getTime() - realCurrentTime.getTime());
                            long diff2 = Math.abs(t2.getTime() - realCurrentTime.getTime());

                            return Long.compare(diff1, diff2);
                        } catch (Exception e) { return 0; }
                    });

                    populateUI(validVisits);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load visits", Toast.LENGTH_SHORT).show());
    }

    /**
     * Inflates and populates UI rows for each valid visit model.
     * Fetches patient names and addresses asynchronously from the users collection.
     */
    private void populateUI(List<VisitModel> visits) {
        llVisitsContainer.removeAllViews();

        // Toggle state if no visits found
        if (visits.isEmpty()) {
            llEmptyState.setVisibility(View.VISIBLE);
            llVisitsContainer.setVisibility(View.GONE);
            return;
        }

        llEmptyState.setVisibility(View.GONE);
        llVisitsContainer.setVisibility(View.VISIBLE);

        for (VisitModel visit : visits) {
            View row = getLayoutInflater().inflate(R.layout.item_daily_visit, llVisitsContainer, false);

            TextView tvTime = row.findViewById(R.id.tv_visit_time);
            TextView tvPatientId = row.findViewById(R.id.tv_patient_id);
            TextView tvNotes = row.findViewById(R.id.tv_visit_notes);

            tvTime.setText(visit.getArrivalTime() + " - " + visit.getDepartureTime());
            tvNotes.setText("Address: Fetching...");

            // Resolve patient user details from the global user database
            db.collection("users").document(visit.getPatientId()).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    if (doc.getString("username") != null) {
                        tvPatientId.setText("Patient: " + doc.getString("username"));
                    } else {
                        tvPatientId.setText("Patient ID: " + visit.getPatientId());
                    }

                    if (doc.getString("address") != null && !doc.getString("address").isEmpty()) {
                        tvNotes.setText("Address: " + doc.getString("address"));
                    } else {
                        tvNotes.setText("Address: Not provided");
                    }
                }
            });

            llVisitsContainer.addView(row);
        }
    }
}
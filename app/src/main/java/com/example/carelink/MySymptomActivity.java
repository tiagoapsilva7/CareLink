package com.example.carelink;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.carelink.model.ChatroomModel;
import com.example.carelink.model.SymptomModel;
import com.example.carelink.model.SymptomModel.SecondarySymptom;
import com.example.carelink.model.UserModel;
import com.example.carelink.util.AndroidUtil;
import com.example.carelink.util.FirebaseUtil;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

/**
 * Activity for displaying the detailed view of a specific symptom report.
 * Provides functionality to view primary and secondary symptoms, chat with the relevant
 * medical professional/patient, and marks the record as 'read' upon viewing.
 */
public class MySymptomActivity extends AppCompatActivity {

    TextView tvTitle, tvDate, tvDuration, tvLocation, tvNature, tvProg, tvPain, tvOther;

    // Containers for dynamic secondary symptom list
    LinearLayout llViewSecondarySymptoms, llSecList;

    ImageView btnBack, btnChat;
    String symptomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_symptom);

        // UI View Binding
        tvTitle = findViewById(R.id.tv_symp_title);
        tvDate = findViewById(R.id.tv_symp_date);
        tvDuration = findViewById(R.id.tv_symp_duration);
        tvLocation = findViewById(R.id.tv_symp_location);
        tvNature = findViewById(R.id.tv_symp_nature);
        tvProg = findViewById(R.id.tv_symp_prog);
        tvPain = findViewById(R.id.tv_symp_pain);
        tvOther = findViewById(R.id.tv_symp_other);

        llViewSecondarySymptoms = findViewById(R.id.ll_view_secondary_symptoms);
        llSecList = findViewById(R.id.ll_sec_list);

        btnBack = findViewById(R.id.btnBack);
        btnChat = findViewById(R.id.btnChat);

        // Check if activity was opened via a push notification
        boolean fromNotification = getIntent().getBooleanExtra("fromNotification", false);

        // Custom back-press handling to ensure correct navigation flow
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // If coming from notification or launched from scratch, return to the symptoms list
                if (fromNotification || isTaskRoot()) {
                    Intent intent = new Intent(MySymptomActivity.this, MainActivity.class);
                    intent.putExtra("targetFragment", "SymptomsFragment");
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
                finish();
            }
        });

        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        symptomId = getIntent().getStringExtra("symptomId");

        // Load record data if the ID is valid
        if (symptomId != null) fetchSymptomDetails();
    }

    /**
     * Listens for real-time updates on the symptom document.
     * Automatically marks as read when a doctor opens it.
     */
    private void fetchSymptomDetails() {
        FirebaseFirestore.getInstance().collection("symptoms").document(symptomId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) { finish(); return; }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        SymptomModel model = documentSnapshot.toObject(SymptomModel.class);
                        if (model != null) {

                            // Check if current user is a doctor and if they haven't read this specific entry yet
                            boolean isDoctor = !FirebaseUtil.currentUserId().equals(model.getPatientId());
                            boolean isUnread = isDoctor && (model.getReadBy() == null || !model.getReadBy().containsKey(FirebaseUtil.currentUserId()));

                            if (isUnread) {
                                clearUnreadCounters(model);
                            }

                            populateViews(model);
                            setupChatButton(model);
                        }
                    } else {
                        Toast.makeText(this, "This symptom was removed by the patient.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    /**
     * Decrements the unread notification counters in both the user profile and the specific chatroom.
     */
    private void clearUnreadCounters(SymptomModel model) {
        String myId = FirebaseUtil.currentUserId();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Mark as read in the symptom document
        db.collection("symptoms").document(symptomId).update("readBy." + myId, true);

        // Decrement global unread notification count
        db.collection("users").document(myId).update("unreadMedicalRecords", FieldValue.increment(-1));

        // Decrement specific chatroom unread counter
        String otherUserId = myId.equals(model.getPatientId()) ? model.getDoctorId() : model.getPatientId();
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

    /**
     * Maps the SymptomModel object data to the Activity's UI widgets.
     */
    private void populateViews(SymptomModel model) {
        tvTitle.setText(model.getTitle());
        tvDate.setText(model.getOnsetDate() != null ? model.getOnsetDate() : "Unknown");
        tvDuration.setText(model.getDuration() != null && !model.getDuration().isEmpty() ? model.getDuration() : "Not specified");
        tvLocation.setText(model.getLocation() != null && !model.getLocation().isEmpty() ? model.getLocation() : "Not specified");
        tvNature.setText(model.getNature() != null ? model.getNature() : "Not specified");
        tvProg.setText(model.getProgression() != null ? model.getProgression() : "Not specified");
        tvPain.setText(model.getPainLevel() != null ? model.getPainLevel() : "Not specified");
        tvOther.setText(model.getOtherInfo() != null && !model.getOtherInfo().isEmpty() ? model.getOtherInfo() : "No additional info.");

        // Clear and rebuild dynamic list of secondary symptoms
        llSecList.removeAllViews();
        List<SecondarySymptom> secList = model.getSecondarySymptoms();

        if (secList != null && !secList.isEmpty()) {
            llViewSecondarySymptoms.setVisibility(View.VISIBLE);

            for (int i = 0; i < secList.size(); i++) {
                SecondarySymptom sec = secList.get(i);

                TextView tvEntry = new TextView(this);
                tvEntry.setTextColor(Color.BLACK);
                tvEntry.setTextSize(15f);
                tvEntry.setPadding(16, 16, 16, 16);
                tvEntry.setBackgroundResource(R.drawable.bg_input_field);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, 16);
                tvEntry.setLayoutParams(params);

                String formatted = "• " + sec.getDescription() + "\n";
                if (sec.getOnsetType().equals("Same as the main symptom")) {
                    formatted += "  Onset: Same time as main symptom";
                } else {
                    formatted += "  Onset: " + sec.getTimeAfter() + " after main symptom";
                }

                tvEntry.setText(formatted);
                llSecList.addView(tvEntry);
            }
        } else {
            llViewSecondarySymptoms.setVisibility(View.GONE);
        }
    }

    /**
     * Configures the chat button to navigate to the specific chat thread
     * associated with the relevant patient/doctor.
     */
    private void setupChatButton(SymptomModel model) {
        String myId = FirebaseUtil.currentUserId();
        String otherUserId = myId.equals(model.getPatientId()) ? model.getDoctorId() : model.getPatientId();

        if (otherUserId != null && !otherUserId.isEmpty()) {
            btnChat.setVisibility(View.VISIBLE);
            btnChat.setOnClickListener(v -> {
                btnChat.setEnabled(false);
                FirebaseFirestore.getInstance().collection("users").document(otherUserId).get()
                        .addOnSuccessListener(doc -> {
                            UserModel otherUser = doc.toObject(UserModel.class);
                            if (otherUser != null) {
                                Intent intent = new Intent(MySymptomActivity.this, ChatActivity.class);
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
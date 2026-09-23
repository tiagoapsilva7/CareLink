package com.example.carelink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.carelink.model.ChatroomModel;
import com.example.carelink.model.SymptomModel;
import com.example.carelink.model.SymptomModel.SecondarySymptom;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity for patients to record and report symptoms.
 * Collects details like onset date, duration, location, pain intensity,
 * and associated secondary symptoms, then saves them to Firestore.
 */
public class CreateSymptomActivity extends AppCompatActivity {

    EditText etTitle, etDate, etDuration, etLocation, etPain, etOther;
    CheckBox cbConstant, cbComes, cbWorse, cbSame, cbBetter, cbPainNa;

    LinearLayout llSecondaryContainer;
    ImageView btnAddSecondary;
    private List<View> secondarySymptomViews = new ArrayList<>();

    Button btnSave;
    ImageView btnBack;
    String patientId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_symptom);

        // UI aesthetics
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(getResources().getColor(R.color.buttons));

        // Adjust layout insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            View topBar = findViewById(R.id.top_bar);
            if (topBar != null) topBar.setPadding(topBar.getPaddingLeft(), systemBars.top + 16, topBar.getPaddingRight(), topBar.getPaddingBottom());
            return insets;
        });

        patientId = getIntent().getStringExtra("patientId");

        // View initialization
        etTitle = findViewById(R.id.et_title);
        etDate = findViewById(R.id.et_date);
        etDuration = findViewById(R.id.et_duration);
        etLocation = findViewById(R.id.et_location);
        etPain = findViewById(R.id.et_pain);
        etOther = findViewById(R.id.et_other);

        cbConstant = findViewById(R.id.cb_constant);
        cbComes = findViewById(R.id.cb_comes);
        cbWorse = findViewById(R.id.cb_worse);
        cbSame = findViewById(R.id.cb_same);
        cbBetter = findViewById(R.id.cb_better);
        cbPainNa = findViewById(R.id.cb_pain_na);

        llSecondaryContainer = findViewById(R.id.ll_secondary_symptoms_container);
        btnAddSecondary = findViewById(R.id.btn_add_secondary);

        btnSave = findViewById(R.id.btn_save_symptom);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        btnAddSecondary.setOnClickListener(v -> addSecondarySymptomRow());

        setupLogic();
        btnSave.setOnClickListener(v -> saveSymptom());
    }

    /**
     * Configures input listeners and form validation logic for dynamic UI updates.
     */
    private void setupLogic() {
        // Nature of symptom: ensure mutual exclusivity between Constant and Comes-and-goes
        cbConstant.setOnCheckedChangeListener((buttonView, isChecked) -> { if (isChecked) cbComes.setChecked(false); validateForm(); });
        cbComes.setOnCheckedChangeListener((buttonView, isChecked) -> { if (isChecked) cbConstant.setChecked(false); validateForm(); });

        // Progression: ensure mutual exclusivity between worse/same/better
        cbWorse.setOnCheckedChangeListener((buttonView, isChecked) -> { if (isChecked) { cbSame.setChecked(false); cbBetter.setChecked(false); } validateForm(); });
        cbSame.setOnCheckedChangeListener((buttonView, isChecked) -> { if (isChecked) { cbWorse.setChecked(false); cbBetter.setChecked(false); } validateForm(); });
        cbBetter.setOnCheckedChangeListener((buttonView, isChecked) -> { if (isChecked) { cbWorse.setChecked(false); cbSame.setChecked(false); } validateForm(); });

        // Pain input toggling
        cbPainNa.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) { etPain.setText(""); etPain.setEnabled(false); } else { etPain.setEnabled(true); }
            validateForm();
        });

        // Pain score bounds check (1-10)
        etPain.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    cbPainNa.setChecked(false);
                    try {
                        int val = Integer.parseInt(s.toString());
                        if (val > 10) { etPain.setText("10"); etPain.setSelection(2); }
                        else if (val < 1) { etPain.setText("1"); etPain.setSelection(1); }
                    } catch (Exception ignored) {}
                }
                validateForm();
            }
        });

        // Automated date formatting (dd/mm/yyyy)
        etDate.addTextChangedListener(new TextWatcher() {
            private String current = "";
            private boolean isDeleting = false;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { isDeleting = count > after; }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.toString().equals(current)) return;
                String clean = s.toString().replaceAll("[^\\d]", "");
                if (clean.length() > 8) clean = clean.substring(0, 8);
                StringBuilder formatted = new StringBuilder();
                int i = 0;
                for (char c : clean.toCharArray()) {
                    formatted.append(c);
                    i++;
                    if ((i == 2 || i == 4) && clean.length() > i) { formatted.append("/"); }
                    else if ((i == 2 || i == 4) && clean.length() == i && !isDeleting) { formatted.append("/"); }
                }
                current = formatted.toString();
                etDate.setText(current);
                etDate.setSelection(current.length());
                validateForm();
            }
        });

        TextWatcher validationWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { validateForm(); }
        };
        etTitle.addTextChangedListener(validationWatcher);
        etDuration.addTextChangedListener(validationWatcher);
        etLocation.addTextChangedListener(validationWatcher);
    }

    /**
     * Dynamically adds a secondary symptom row to the container.
     */
    private void addSecondarySymptomRow() {
        View rowView = LayoutInflater.from(this).inflate(R.layout.item_secondary_symptom, llSecondaryContainer, false);

        EditText etDesc = rowView.findViewById(R.id.et_sec_desc);
        CheckBox cbSame = rowView.findViewById(R.id.cb_sec_same);
        CheckBox cbAfter = rowView.findViewById(R.id.cb_sec_after);
        EditText etTimeAfter = rowView.findViewById(R.id.et_sec_time_after);
        ImageView btnRemove = rowView.findViewById(R.id.btn_remove_sec);

        cbSame.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                cbAfter.setChecked(false);
                etTimeAfter.setVisibility(View.GONE);
                etTimeAfter.setText("");
            }
            validateForm();
        });

        cbAfter.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                cbSame.setChecked(false);
                etTimeAfter.setVisibility(View.VISIBLE);
            } else {
                etTimeAfter.setVisibility(View.GONE);
                etTimeAfter.setText("");
            }
            validateForm();
        });

        TextWatcher secWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { validateForm(); }
        };
        etDesc.addTextChangedListener(secWatcher);
        etTimeAfter.addTextChangedListener(secWatcher);

        btnRemove.setOnClickListener(v -> {
            llSecondaryContainer.removeView(rowView);
            secondarySymptomViews.remove(rowView);
            validateForm();
        });

        llSecondaryContainer.addView(rowView);
        secondarySymptomViews.add(rowView);
        validateForm();
    }

    private boolean isValidPastOrPresentDate(String dateString) {
        if (dateString == null || dateString.length() != 10) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            sdf.setLenient(false);
            Date inputDate = sdf.parse(dateString);
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 23);
            today.set(Calendar.MINUTE, 59);
            today.set(Calendar.SECOND, 59);
            return inputDate == null || !inputDate.after(today.getTime());
        } catch (Exception e) { return false; }
    }

    /**
     * Checks form validity to enable/disable the save button.
     */
    private void validateForm() {
        boolean hasTitle = !etTitle.getText().toString().trim().isEmpty();
        boolean hasDate = isValidPastOrPresentDate(etDate.getText().toString().trim());
        boolean hasDuration = !etDuration.getText().toString().trim().isEmpty();
        boolean hasLocation = !etLocation.getText().toString().trim().isEmpty();
        boolean hasNature = cbConstant.isChecked() || cbComes.isChecked();
        boolean hasProgression = cbWorse.isChecked() || cbSame.isChecked() || cbBetter.isChecked();
        boolean hasPain = cbPainNa.isChecked() || !etPain.getText().toString().trim().isEmpty();

        boolean secondaryValid = true;
        for (View row : secondarySymptomViews) {
            EditText etDesc = row.findViewById(R.id.et_sec_desc);
            CheckBox cbSame = row.findViewById(R.id.cb_sec_same);
            CheckBox cbAfter = row.findViewById(R.id.cb_sec_after);
            EditText etTimeAfter = row.findViewById(R.id.et_sec_time_after);

            boolean hasDesc = !etDesc.getText().toString().trim().isEmpty();
            boolean hasOnset = cbSame.isChecked() || cbAfter.isChecked();
            boolean timeValid = !cbAfter.isChecked() || !etTimeAfter.getText().toString().trim().isEmpty();

            if (!hasDesc || !hasOnset || !timeValid) {
                secondaryValid = false;
                break;
            }
        }

        if (hasTitle && hasDate && hasDuration && hasLocation && hasNature && hasProgression && hasPain && secondaryValid) {
            btnSave.setEnabled(true); btnSave.setAlpha(1.0f);
        } else {
            btnSave.setEnabled(false); btnSave.setAlpha(0.5f);
        }
    }

    /**
     * Processes form data, creates a SymptomModel, saves it to Firestore,
     * and notifies caregivers via incremented unread notification counts.
     */
    private void saveSymptom() {
        String title = etTitle.getText().toString().trim();
        String dateString = etDate.getText().toString().trim();

        Timestamp onsetTimestamp;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            sdf.setLenient(false);
            Date parsedDate = sdf.parse(dateString);
            onsetTimestamp = new Timestamp(parsedDate);
        } catch (Exception e) {
            Toast.makeText(this, "Invalid date format.", Toast.LENGTH_LONG).show();
            return;
        }

        String nature = cbConstant.isChecked() ? "Constant" : (cbComes.isChecked() ? "Comes and goes" : "Not specified");
        String progression = cbWorse.isChecked() ? "Getting worse" : (cbSame.isChecked() ? "Staying the same" : (cbBetter.isChecked() ? "Getting better" : "Not specified"));
        String pain = cbPainNa.isChecked() ? "N/A" : etPain.getText().toString().trim();
        if (pain.isEmpty()) pain = "Not specified";

        List<SecondarySymptom> secList = new ArrayList<>();
        for (View row : secondarySymptomViews) {
            EditText etDesc = row.findViewById(R.id.et_sec_desc);
            CheckBox cbSame = row.findViewById(R.id.cb_sec_same);
            EditText etTimeAfter = row.findViewById(R.id.et_sec_time_after);

            String desc = etDesc.getText().toString().trim();
            String onsetType = cbSame.isChecked() ? "Same as the main symptom" : "After the main symptom";
            String timeAfter = cbSame.isChecked() ? "" : etTimeAfter.getText().toString().trim();

            secList.add(new SecondarySymptom(desc, onsetType, timeAfter));
        }

        btnSave.setEnabled(false);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String symptomId = db.collection("symptoms").document().getId();

        SymptomModel model = new SymptomModel(
                symptomId, patientId, "", Timestamp.now(), onsetTimestamp, title, dateString,
                etDuration.getText().toString().trim(), etLocation.getText().toString().trim(),
                nature, progression, pain, etOther.getText().toString().trim(),
                secList,
                new java.util.HashMap<>(), new java.util.HashMap<>()
        );

        // Upload main symptom data
        db.collection("symptoms").document(symptomId).set(model)
                .addOnSuccessListener(aVoid -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();

                    // Find chatrooms to notify linked caregivers
                    db.collection("chatrooms").whereArrayContains("userIds", patientId).get()
                            .addOnSuccessListener(query -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : query.getDocuments()) {
                                    ChatroomModel chat = doc.toObject(ChatroomModel.class);
                                    if (chat != null && chat.getUserIds() != null) {
                                        for (String userId : chat.getUserIds()) {
                                            if (!userId.equals(patientId)) {
                                                // Increment unread notification count
                                                batch.update(db.collection("users").document(userId), "unreadMedicalRecords", com.google.firebase.firestore.FieldValue.increment(1));

                                                java.util.Map<String, Object> unreadData = new java.util.HashMap<>();
                                                unreadData.put(userId, com.google.firebase.firestore.FieldValue.increment(1));
                                                java.util.Map<String, Object> mergeData = new java.util.HashMap<>();
                                                mergeData.put("unreadMedicalCount", unreadData);
                                                batch.set(doc.getReference(), mergeData, SetOptions.merge());
                                            }
                                        }
                                    }
                                }
                                // Commit changes and return to the main dashboard
                                batch.commit().addOnCompleteListener(task -> {
                                    Toast.makeText(this, "Symptom sent to all connected caregivers!", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(CreateSymptomActivity.this, MainActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                    intent.putExtra("targetFragment", "SymptomsFragment");
                                    startActivity(intent);
                                    finish();
                                });
                            });
                }).addOnFailureListener(e -> btnSave.setEnabled(true));
    }
}
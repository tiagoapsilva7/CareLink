package com.example.carelink;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.carelink.model.ChatroomModel;
import com.example.carelink.model.VisitModel;
import com.example.carelink.model.VisitReportModel;
import com.example.carelink.util.FirebaseUtil;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity where caregivers create formal visit reports.
 * Includes scheduling future follow-up visits and automated FCM notification dispatch.
 */
public class CreateVisitReportActivity extends AppCompatActivity {

    // UI Components
    private Spinner spinnerVisits;
    private TextView tvDate, tvHours, tvTitle;
    private EditText etComplaints, etReport, etTherapeuticPlan, etRecommendations;
    private CheckBox cbFutNo, cbFutYes;
    private LinearLayout llFutDetails;
    private EditText etFutDate, etFutArrival, etFutDeparture, etFutNotes;
    private Button btnSave;

    // Logic members
    private String patientId;
    private String currentUserName = "Caregiver";
    private FirebaseFirestore db;
    private List<VisitModel> visitList = new ArrayList<>();
    private List<String> spinnerDisplayList = new ArrayList<>();

    // Prefill data for context-aware report creation
    private String prefillDate;
    private String prefillArrival;
    private String prefillDeparture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_visit_report);

        // System UI configuration
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(getResources().getColor(R.color.buttons));

        // Edge-to-edge support configuration
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            View topBar = findViewById(R.id.top_bar);
            if (topBar != null) {
                topBar.setPadding(topBar.getPaddingLeft(), systemBars.top + 16, topBar.getPaddingRight(), topBar.getPaddingBottom());
            }
            return insets;
        });

        db = FirebaseFirestore.getInstance();

        // Identify the target patient
        patientId = getIntent().getStringExtra("patientId");
        if (patientId == null || patientId.isEmpty()) {
            patientId = getIntent().getStringExtra("userId");
        }

        String patientName = getIntent().getStringExtra("patientName");
        prefillDate = getIntent().getStringExtra("prefillDate");
        prefillArrival = getIntent().getStringExtra("prefillArrival");
        prefillDeparture = getIntent().getStringExtra("prefillDeparture");

        // UI Binding
        tvTitle = findViewById(R.id.tvTitle);
        ImageView btnBack = findViewById(R.id.btnBack);
        spinnerVisits = findViewById(R.id.spinner_visits);
        tvDate = findViewById(R.id.tv_date);
        tvHours = findViewById(R.id.tv_hours);
        etComplaints = findViewById(R.id.et_complaints);
        etReport = findViewById(R.id.et_report);
        etTherapeuticPlan = findViewById(R.id.et_therapeutic_plan);
        etRecommendations = findViewById(R.id.et_recommendations);

        cbFutNo = findViewById(R.id.cb_fut_no);
        cbFutYes = findViewById(R.id.cb_fut_yes);
        llFutDetails = findViewById(R.id.ll_fut_details);
        etFutDate = findViewById(R.id.et_fut_date);
        etFutArrival = findViewById(R.id.et_fut_arrival);
        etFutDeparture = findViewById(R.id.et_fut_departure);
        etFutNotes = findViewById(R.id.et_fut_notes);
        btnSave = findViewById(R.id.btn_save_report);

        btnBack.setOnClickListener(v -> finish());

        if (patientName != null && !patientName.isEmpty()) {
            tvTitle.setText("New Visit Report - " + patientName);
        } else {
            tvTitle.setText("New Visit Report");
        }

        // Fetch current username for reporting attribution
        db.collection("users").document(FirebaseUtil.currentUserId()).get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.getString("username") != null) currentUserName = doc.getString("username");
        });

        setupLogic();
        fetchVisits();
        btnSave.setOnClickListener(v -> handleSaveRoutine());
    }

    /** Helper: Formats stored DB dates (YYYY-MM-DD) for UI display (DD/MM/YYYY) */
    private String formatDateToDisplay(String dbDate) {
        try {
            SimpleDateFormat sdfIn = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat sdfOut = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date d = sdfIn.parse(dbDate);
            return sdfOut.format(d);
        } catch (Exception e) {
            return dbDate;
        }
    }

    /** Helper: Converts UI-friendly DD/MM/YYYY to DB-friendly YYYY-MM-DD */
    private String normalizeDate(String date) {
        if (date == null) return "";
        if (date.contains("/")) {
            String[] parts = date.split("/");
            if (parts.length == 3) {
                return parts[2] + "-" + parts[1] + "-" + parts[0];
            }
        }
        return date;
    }

    /** Comparison helper for date strings of different formats */
    private boolean isSameDate(String d1, String d2) {
        if (d1 == null || d2 == null) return false;
        if (d1.equals(d2)) return true;
        return normalizeDate(d1).equals(normalizeDate(d2));
    }

    /** Verifies if a scheduled visit has already passed, making it eligible for a report */
    private boolean isVisitReadyForReport(String dbDateStr, String arrivalTime) {
        try {
            String normDate = normalizeDate(dbDateStr);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date visitDateTime = sdf.parse(normDate + " " + arrivalTime);
            Date now = new Date();
            return now.getTime() >= visitDateTime.getTime();
        } catch (Exception e) {
            return false;
        }
    }

    /** Retrieves all visits assigned to the caregiver and populates the spinner */
    private void fetchVisits() {
        spinnerDisplayList.add("Select a scheduled visit");

        db.collection("care_visits")
                .whereEqualTo("caregiverId", FirebaseUtil.currentUserId())
                .get().addOnSuccessListener(query -> {
                    List<VisitModel> rawVisits = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        VisitModel v = doc.toObject(VisitModel.class);
                        // Filter by patientId if specified
                        if (patientId != null && !patientId.isEmpty() && !patientId.equals(FirebaseUtil.currentUserId())) {
                            if (v.getPatientId() != null && v.getPatientId().equals(patientId)) {
                                rawVisits.add(v);
                            }
                        } else {
                            rawVisits.add(v);
                        }
                    }

                    // Check which visits already have reports to avoid duplication
                    db.collection("visit_reports")
                            .whereEqualTo("caregiverId", FirebaseUtil.currentUserId())
                            .get().addOnSuccessListener(reportQuery -> {
                                List<VisitReportModel> existingReports = new ArrayList<>();
                                for (QueryDocumentSnapshot rDoc : reportQuery) {
                                    existingReports.add(rDoc.toObject(VisitReportModel.class));
                                }

                                for (VisitModel v : rawVisits) {
                                    if (!isVisitReadyForReport(v.getDate(), v.getArrivalTime())) {
                                        continue;
                                    }

                                    boolean hasReport = false;
                                    String timeWindow = v.getArrivalTime() + " - " + v.getDepartureTime();
                                    for (VisitReportModel r : existingReports) {
                                        if (isSameDate(r.getVisitDate(), v.getDate()) && r.getVisitHours().equals(timeWindow)) {
                                            hasReport = true;
                                            break;
                                        }
                                    }
                                    if (!hasReport) {
                                        visitList.add(v);
                                    }
                                }

                                // Order by newest visit first
                                Collections.sort(visitList, (v1, v2) -> {
                                    String dt1 = normalizeDate(v1.getDate()) + " " + v1.getArrivalTime();
                                    String dt2 = normalizeDate(v2.getDate()) + " " + v2.getArrivalTime();
                                    return dt2.compareTo(dt1);
                                });

                                for (VisitModel v : visitList) {
                                    String formattedDate = formatDateToDisplay(v.getDate());
                                    spinnerDisplayList.add(formattedDate + " (" + v.getArrivalTime() + " - " + v.getDepartureTime() + ")");
                                }

                                // Setup spinner with custom styling
                                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, spinnerDisplayList) {
                                    @Override public boolean isEnabled(int position) { return position != 0; }
                                    @NonNull @Override public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                        TextView tv = (TextView) super.getView(position, convertView, parent);
                                        if (position == 0) { tv.setTextColor(Color.parseColor("#757575")); tv.setTypeface(null, Typeface.NORMAL); }
                                        else { tv.setTextColor(ContextCompat.getColor(getContext(), R.color.buttons)); tv.setTypeface(null, Typeface.BOLD); }
                                        tv.setTextSize(16f); tv.setPadding(32, 0, 0, 0); return tv;
                                    }
                                    @Override public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                        TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                                        tv.setBackgroundColor(Color.WHITE); tv.setTextSize(16f); tv.setPadding(32, 24, 32, 24);
                                        if (position == 0) { tv.setTextColor(Color.parseColor("#A9A9A9")); tv.setTypeface(null, Typeface.NORMAL); }
                                        else { tv.setTextColor(ContextCompat.getColor(getContext(), R.color.buttons)); tv.setTypeface(null, Typeface.BOLD); }
                                        return tv;
                                    }
                                };
                                spinnerVisits.setAdapter(adapter);

                                // Select prefilled item if applicable
                                if (prefillDate != null && prefillArrival != null && prefillDeparture != null) {
                                    String matchString = formatDateToDisplay(prefillDate) + " (" + prefillArrival + " - " + prefillDeparture + ")";
                                    for (int i = 0; i < spinnerDisplayList.size(); i++) {
                                        if (spinnerDisplayList.get(i).equals(matchString)) {
                                            spinnerVisits.setSelection(i);
                                            break;
                                        }
                                    }
                                }
                            });
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load schedules.", Toast.LENGTH_SHORT).show();
                });
    }

    /** Initializes listeners and input formatters for the form */
    private void setupLogic() {
        spinnerVisits.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    VisitModel selectedVisit = visitList.get(position - 1);
                    tvDate.setText(formatDateToDisplay(selectedVisit.getDate()));
                    tvHours.setText(selectedVisit.getArrivalTime() + " - " + selectedVisit.getDepartureTime());
                } else {
                    tvDate.setText("--/--/----");
                    tvHours.setText("--:-- - --:--");
                }
                validateForm();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Toggle visibility for future visit details based on user selection
        cbFutNo.setOnCheckedChangeListener((btn, isChecked) -> { if(isChecked) cbFutYes.setChecked(false); validateForm(); });
        cbFutYes.setOnCheckedChangeListener((btn, isChecked) -> {
            if(isChecked) {
                cbFutNo.setChecked(false);
                llFutDetails.setVisibility(View.VISIBLE);
            } else {
                llFutDetails.setVisibility(View.GONE);
            }
            validateForm();
        });

        // Watchers for form validation
        TextWatcher validationWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { validateForm(); }
        };
        etComplaints.addTextChangedListener(validationWatcher);
        etRecommendations.addTextChangedListener(validationWatcher);

        setupTimeFormatter(etFutArrival, validationWatcher);
        setupTimeFormatter(etFutDeparture, validationWatcher);

        // Date formatter for future visits
        etFutDate.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.toString().equals(current)) return;
                String clean = s.toString().replaceAll("[^\\d]", "");
                if (clean.length() > 8) clean = clean.substring(0, 8);
                StringBuilder formatted = new StringBuilder();
                int i = 0;
                for (char c : clean.toCharArray()) {
                    if (i == 2 || i == 4) formatted.append("/");
                    formatted.append(c);
                    i++;
                }
                current = formatted.toString();
                etFutDate.setText(current);
                etFutDate.setSelection(current.length());
                validateForm();
            }
        });
    }

    /** Auto-format time inputs to HH:mm */
    private void setupTimeFormatter(EditText editText, TextWatcher validationWatcher) {
        editText.addTextChangedListener(new TextWatcher() {
            int prevLength = 0;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { prevLength = s.length(); }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String str = s.toString();
                if (str.length() == 2 && prevLength < 2) {
                    editText.setText(str + ":");
                    editText.setSelection(editText.getText().length());
                }
                validationWatcher.afterTextChanged(s);
            }
        });
    }

    private boolean isValidTimeFormat(String time) {
        return time.matches("([01]?[0-9]|2[0-3]):[0-5][0-9]");
    }

    /** Ensures all required fields are populated before enabling the Save button */
    private void validateForm() {
        boolean isVisitSelected = spinnerVisits.getSelectedItemPosition() > 0;
        boolean isComplaintsFilled = !etComplaints.getText().toString().trim().isEmpty();
        boolean isRecommendationsFilled = !etRecommendations.getText().toString().trim().isEmpty();
        boolean isFutSelected = cbFutNo.isChecked() || cbFutYes.isChecked();

        boolean isFutDetailsValid = true;
        if (cbFutYes.isChecked()) {
            isFutDetailsValid = etFutDate.getText().toString().length() == 10 &&
                    isValidTimeFormat(etFutArrival.getText().toString()) &&
                    isValidTimeFormat(etFutDeparture.getText().toString());
        }

        if (isVisitSelected && isComplaintsFilled && isRecommendationsFilled && isFutSelected && isFutDetailsValid) {
            btnSave.setEnabled(true); btnSave.setAlpha(1.0f);
        } else {
            btnSave.setEnabled(false); btnSave.setAlpha(0.5f);
        }
    }

    private int timeToMinutes(String time) {
        String[] p = time.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** Manages validation of the saving routine, including future date checking and scheduling conflicts */
    private void handleSaveRoutine() {
        btnSave.setEnabled(false);

        int selectedIndex = spinnerVisits.getSelectedItemPosition() - 1;
        VisitModel selectedVisit = visitList.get(selectedIndex);
        String actualPatientId = selectedVisit.getPatientId();

        if (cbFutYes.isChecked()) {
            String arr = etFutArrival.getText().toString().trim();
            String dep = etFutDeparture.getText().toString().trim();
            String inputDateStr = etFutDate.getText().toString().trim();
            String futNotes = etFutNotes.getText().toString().trim();

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);

            String dbDateStr;
            try {
                SimpleDateFormat sdfIn = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                sdfIn.setLenient(false);
                Date pDate = sdfIn.parse(inputDateStr);

                Calendar selectedCal = Calendar.getInstance();
                selectedCal.setTime(pDate);

                if (selectedCal.before(today)) {
                    Toast.makeText(this, "Future visit cannot be in the past.", Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    return;
                }

                SimpleDateFormat sdfDb = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                dbDateStr = sdfDb.format(pDate);

            } catch (Exception e) {
                Toast.makeText(this, "Invalid Date format. Must be DD/MM/YYYY.", Toast.LENGTH_SHORT).show();
                btnSave.setEnabled(true);
                return;
            }

            int arrMins = timeToMinutes(arr);
            int depMins = timeToMinutes(dep);

            if (depMins <= arrMins) {
                Toast.makeText(this, "Departure must be after arrival.", Toast.LENGTH_SHORT).show();
                btnSave.setEnabled(true);
                return;
            }
            if ((depMins - arrMins) > 120) {
                Toast.makeText(this, "Visits cannot exceed 2 hours.", Toast.LENGTH_SHORT).show();
                btnSave.setEnabled(true);
                return;
            }

            // Conflict detection: verify if caregiver or patient has a scheduling overlap
            db.collection("care_visits")
                    .whereEqualTo("date", dbDateStr)
                    .get().addOnSuccessListener(query -> {
                        for (QueryDocumentSnapshot doc : query) {
                            VisitModel v = doc.toObject(VisitModel.class);
                            boolean caregiverBusy = v.getCaregiverId().equals(FirebaseUtil.currentUserId());
                            boolean patientBusy = v.getPatientId().equals(actualPatientId);

                            if (caregiverBusy || patientBusy) {
                                int vArr = timeToMinutes(v.getArrivalTime());
                                int vDep = timeToMinutes(v.getDepartureTime());

                                if (arrMins < vDep && depMins > vArr) {
                                    Toast.makeText(this, "Overlap detected! Caregiver or Patient is busy.", Toast.LENGTH_LONG).show();
                                    btnSave.setEnabled(true);
                                    return;
                                }
                            }
                        }

                        executeSaveWithFutureVisit(dbDateStr, arr, dep, actualPatientId, futNotes);

                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Error checking schedule availability", Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                    });

        } else {
            executeSaveReport(actualPatientId, false, "", "", "", "");
        }
    }

    /** Schedules the future visit model to Firestore */
    private void executeSaveWithFutureVisit(String dbDateStr, String arr, String dep, String actualPatientId, String futNotes) {
        String newVisitId = db.collection("care_visits").document().getId();
        VisitModel newVisit = new VisitModel(newVisitId, actualPatientId, FirebaseUtil.currentUserId(), currentUserName, dbDateStr, arr, dep, futNotes, Timestamp.now());

        db.collection("care_visits").document(newVisitId).set(newVisit)
                .addOnSuccessListener(aVoid -> executeSaveReport(actualPatientId, true, dbDateStr, arr, dep, futNotes))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to schedule future visit", Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                });
    }

    /** Finalizes the visit report in Firestore and pushes notification to the patient */
    private void executeSaveReport(String actualPatientId, boolean futureVisit, String fDate, String fArr, String fDep, String fNotes) {
        String reportId = db.collection("visit_reports").document().getId();

        int selectedIndex = spinnerVisits.getSelectedItemPosition() - 1;
        String rawDbDate = visitList.get(selectedIndex).getDate();
        String vHours = visitList.get(selectedIndex).getArrivalTime() + " - " + visitList.get(selectedIndex).getDepartureTime();

        String complaints = etComplaints.getText().toString().trim();
        String report = etReport.getText().toString().trim();
        String therapeuticPlan = etTherapeuticPlan.getText().toString().trim();
        String recommendations = etRecommendations.getText().toString().trim();

        VisitReportModel vrModel = new VisitReportModel(
                reportId, actualPatientId, FirebaseUtil.currentUserId(), Timestamp.now(),
                rawDbDate, vHours, complaints, report, therapeuticPlan, recommendations,
                futureVisit, fDate, fArr, fDep, fNotes
        );

        db.collection("visit_reports").document(reportId).set(vrModel)
                .addOnSuccessListener(aVoid -> {
                    // Create batch for atomic notification increment updates
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    batch.update(db.collection("users").document(actualPatientId), "unreadMedicalRecords", com.google.firebase.firestore.FieldValue.increment(1));

                    db.collection("chatrooms").whereArrayContains("userIds", actualPatientId).get()
                            .addOnSuccessListener(query -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : query.getDocuments()) {
                                    ChatroomModel chat = doc.toObject(ChatroomModel.class);
                                    if (chat != null && chat.getUserIds() != null && chat.getUserIds().contains(FirebaseUtil.currentUserId())) {
                                        java.util.Map<String, Object> unreadData = new java.util.HashMap<>();
                                        unreadData.put(actualPatientId, com.google.firebase.firestore.FieldValue.increment(1));
                                        java.util.Map<String, Object> mergeData = new java.util.HashMap<>();
                                        mergeData.put("unreadMedicalCount", unreadData);
                                        batch.set(doc.getReference(), mergeData, com.google.firebase.firestore.SetOptions.merge());
                                    }
                                }
                                batch.commit();
                            });

                    // FCM Notification dispatch
                    db.collection("users").document(FirebaseUtil.currentUserId()).get().addOnSuccessListener(docMe -> {
                        String doctorUsername = docMe.getString("username");
                        db.collection("users").document(actualPatientId).get().addOnSuccessListener(docPatient -> {
                            String patientToken = docPatient.getString("fcmToken");
                            if (patientToken != null && !patientToken.isEmpty()) {
                                String body = "Caregiver " + doctorUsername + " has uploaded a new visit report!";
                                sendNotification(patientToken, "New Visit Report", body, "visit_report", reportId);
                            }
                            Toast.makeText(this, "Report saved successfully!", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error saving report", Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                });
    }

    /** Dispatches a server-side push notification via FCM REST API */
    public void sendNotification(String token, String title, String body, String type, String id) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                URL url = new URL("https://fcm.googleapis.com/v1/projects/carelink-54780/messages:send");
                android.content.res.AssetManager assetManager = getAssets();
                java.io.InputStream inputStream = assetManager.open("carelink-54780-firebase-adminsdk-fbsvc-e795cf99fa.json");
                com.google.auth.oauth2.GoogleCredentials googleCredentials = com.google.auth.oauth2.GoogleCredentials.fromStream(inputStream)
                        .createScoped(java.util.Arrays.asList("https://www.googleapis.com/auth/firebase.messaging"));

                googleCredentials.refresh();
                String accessToken = googleCredentials.getAccessToken().getTokenValue();

                java.net.HttpURLConnection httpURLConnection = (java.net.HttpURLConnection) url.openConnection();
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
                httpURLConnection.setRequestProperty("Content-Type", "application/json; UTF-8");
                httpURLConnection.setDoOutput(true);

                String jsonPayload = "{\"message\":{\"token\":\"" + token + "\",\"data\":{\"title\":\"" + title + "\",\"body\":\"" + body + "\",\"type\":\"" + type + "\",\"id\":\"" + id + "\"}}}";

                try (java.io.OutputStream os = httpURLConnection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                httpURLConnection.getResponseCode();
            } catch (Exception ignored) {}
        });
    }
}
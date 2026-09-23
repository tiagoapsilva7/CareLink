package com.example.carelink;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.applandeo.materialcalendarview.CalendarView;
import com.applandeo.materialcalendarview.EventDay;
import com.example.carelink.model.VisitModel;
import com.example.carelink.model.VisitReportModel;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class VisitCalendarActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private LinearLayout llSchedulePanel;
    private EditText etArrivalTime, etDepartureTime, etNotes;
    private Button btnBookVisit, btnSeeAllMyVisits;

    private LinearLayout llReportPanel;
    private LinearLayout llReportList;
    private TextView tvReportPanelTitle;

    private FirebaseFirestore firestore;
    private List<VisitModel> allPatientVisitsList = new ArrayList<>();
    private List<VisitReportModel> allPatientReportsList = new ArrayList<>();

    private String currentUserId;
    private String targetPatientId;
    private String currentSelectedDateDbFormat;
    private String currentUserName = "Caregiver";
    private boolean isCaregiver = false;

    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force English locale for consistent date formatting
        Locale locale = new Locale("en");
        Locale.setDefault(locale);
        Configuration config = new Configuration(getResources().getConfiguration());
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());

        setContentView(R.layout.activity_visit_calendar);

        // UI aesthetics
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.buttons));

        calendarView = findViewById(R.id.calendarView);
        llSchedulePanel = findViewById(R.id.ll_schedule_panel);

        etArrivalTime = findViewById(R.id.et_arrival_time);
        etDepartureTime = findViewById(R.id.et_departure_time);
        etNotes = findViewById(R.id.et_visit_notes);
        btnBookVisit = findViewById(R.id.btn_book_visit);

        llReportPanel = findViewById(R.id.ll_report_panel);
        llReportList = findViewById(R.id.ll_report_list);
        tvReportPanelTitle = findViewById(R.id.tv_report_panel_title);

        btnSeeAllMyVisits = findViewById(R.id.btn_see_all_my_visits);

        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvMainTitle = findViewById(R.id.tvMainTitle);

        firestore = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Determine if viewed from chat context or main navigation
        boolean fromChat = getIntent().getBooleanExtra("fromChat", false);
        String otherUserName = getIntent().getStringExtra("otherUserName");
        String intentUserId = getIntent().getStringExtra("userId");

        if (intentUserId != null && !intentUserId.isEmpty()) {
            targetPatientId = intentUserId;
            isCaregiver = true;
            llSchedulePanel.setVisibility(View.VISIBLE);
            btnSeeAllMyVisits.setVisibility(View.VISIBLE);
            if (otherUserName != null) {
                tvMainTitle.setText("Schedule: " + otherUserName);
                tvMainTitle.setTextSize(16f);
            }
        } else {
            targetPatientId = currentUserId;
            isCaregiver = false;
            llSchedulePanel.setVisibility(View.GONE);
            btnSeeAllMyVisits.setVisibility(View.GONE);
            tvMainTitle.setText("Scheduled visits");
        }

        // Handle navigation back to main activity
        View.OnClickListener backAction = v -> {
            if (fromChat) { finish(); } else {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        };
        btnBack.setOnClickListener(backAction);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { backAction.onClick(null); }
        });

        // Initialize progress indicator
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Loading Schedule").setMessage("Please wait...").setCancelable(false).setView(new ProgressBar(this));
        progressDialog = builder.create();

        // Fetch current user details
        firestore.collection("users").document(currentUserId).get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.getString("username") != null) {
                currentUserName = doc.getString("username");
            }
        });

        calendarView.setOnDayClickListener(eventDay -> handleDateClick(eventDay.getCalendar()));

        setupTimeFormatter(etArrivalTime);
        setupTimeFormatter(etDepartureTime);
        btnBookVisit.setOnClickListener(v -> saveNewVisitToFirebase());

        btnSeeAllMyVisits.setOnClickListener(v -> {
            if (currentSelectedDateDbFormat == null) {
                Toast.makeText(this, "Please select a date first.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, MyDailyVisitsActivity.class);
            intent.putExtra("selectedDate", currentSelectedDateDbFormat);
            startActivity(intent);
        });

        refreshDataSequence();
    }

    // Auto-insert colon during time input
    private void setupTimeFormatter(EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            int prevLength = 0;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                prevLength = s.length();
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String str = s.toString();
                if (str.length() == 2 && prevLength < 2) {
                    editText.setText(str + ":");
                    editText.setSelection(editText.getText().length());
                }
            }
        });
    }

    private boolean isValidTimeFormat(String time) {
        return time.matches("([01]?[0-9]|2[0-3]):[0-5][0-9]");
    }

    // Determine if a visit has already occurred
    private boolean isVisitReadyForReport(String dbDateStr, String arrivalTime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date visitDateTime = sdf.parse(dbDateStr + " " + arrivalTime);
            Date now = new Date();
            return now.getTime() >= visitDateTime.getTime();
        } catch (Exception e) {
            return false;
        }
    }

    // Compare date formats (YYYY-MM-DD vs DD/MM/YYYY)
    private boolean isSameDate(String d1, String d2) {
        if (d1 == null || d2 == null) return false;
        if (d1.equals(d2)) return true;
        return normalizeDate(d1).equals(normalizeDate(d2));
    }

    private String normalizeDate(String date) {
        if (date.contains("/")) {
            String[] parts = date.split("/");
            if (parts.length == 3) {
                return parts[2] + "-" + parts[1] + "-" + parts[0];
            }
        }
        return date;
    }

    // Fetch visits and reports from Firestore
    private void refreshDataSequence() {
        progressDialog.show();

        firestore.collection("care_visits")
                .whereEqualTo("patientId", targetPatientId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        allPatientVisitsList.clear();
                        HashSet<String> uniqueEventDates = new HashSet<>();
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            VisitModel visit = doc.toObject(VisitModel.class);
                            allPatientVisitsList.add(visit);
                        }

                        firestore.collection("visit_reports")
                                .whereEqualTo("patientId", targetPatientId)
                                .get()
                                .addOnCompleteListener(taskReports -> {
                                    progressDialog.dismiss();
                                    if (taskReports.isSuccessful()) {
                                        allPatientReportsList.clear();
                                        for (QueryDocumentSnapshot doc : taskReports.getResult()) {
                                            allPatientReportsList.add(doc.toObject(VisitReportModel.class));
                                        }
                                    }

                                    // Mark dates on calendar that have visits but no reports
                                    for (VisitModel v : allPatientVisitsList) {
                                        boolean hasReport = false;
                                        String timeWindow = v.getArrivalTime() + " - " + v.getDepartureTime();
                                        for (VisitReportModel r : allPatientReportsList) {
                                            if (isSameDate(r.getVisitDate(), v.getDate()) &&
                                                    r.getCaregiverId().equals(v.getCaregiverId()) &&
                                                    r.getVisitHours().equals(timeWindow)) {
                                                hasReport = true;
                                                break;
                                            }
                                        }
                                        if (!hasReport && v.getDate() != null) {
                                            uniqueEventDates.add(v.getDate());
                                        }
                                    }
                                    updateCalendarHighlights(uniqueEventDates);

                                    List<Calendar> selectedDates = calendarView.getSelectedDates();
                                    if (selectedDates != null && !selectedDates.isEmpty()) {
                                        handleDateClick(selectedDates.get(0));
                                    } else {
                                        handleDateClick(Calendar.getInstance());
                                    }
                                });
                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(this, "Failed to load visit schedule.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Process UI for a clicked calendar date
    private void handleDateClick(Calendar clickedDay) {
        if (clickedDay == null) return;

        int year = clickedDay.get(Calendar.YEAR);
        int month = clickedDay.get(Calendar.MONTH) + 1;
        int day = clickedDay.get(Calendar.DAY_OF_MONTH);
        currentSelectedDateDbFormat = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day);

        Calendar normalizedClicked = (Calendar) clickedDay.clone();
        normalizedClicked.set(Calendar.HOUR_OF_DAY, 0); normalizedClicked.set(Calendar.MINUTE, 0); normalizedClicked.set(Calendar.SECOND, 0); normalizedClicked.set(Calendar.MILLISECOND, 0);

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);

        boolean isPast = normalizedClicked.before(today);

        List<VisitModel> dayVisits = new ArrayList<>();
        for (VisitModel v : allPatientVisitsList) {
            if (v.getDate().equals(currentSelectedDateDbFormat)) {

                boolean hasReport = false;
                String timeWindow = v.getArrivalTime() + " - " + v.getDepartureTime();
                for (VisitReportModel r : allPatientReportsList) {
                    if (isSameDate(r.getVisitDate(), v.getDate()) &&
                            r.getCaregiverId().equals(v.getCaregiverId()) &&
                            r.getVisitHours().equals(timeWindow)) {
                        hasReport = true;
                        break;
                    }
                }

                if (!hasReport) {
                    dayVisits.add(v);
                }
            }
        }

        if (!dayVisits.isEmpty()) {
            llReportPanel.setVisibility(View.VISIBLE);
            tvReportPanelTitle.setText("Scheduled visits");

            llReportList.removeAllViews();

            for (int i = 0; i < dayVisits.size(); i++) {
                VisitModel v = dayVisits.get(i);

                TextView tvDetails = new TextView(this);
                tvDetails.setTextColor(Color.BLACK);
                tvDetails.setTextSize(16f);

                String details = "Caregiver: " + v.getCaregiverName() + "\n" +
                        "Time: " + v.getArrivalTime() + " - " + v.getDepartureTime();
                if (v.getNotes() != null && !v.getNotes().isEmpty()) {
                    details += "\nNotes: " + v.getNotes();
                }
                tvDetails.setText(details);
                llReportList.addView(tvDetails);

                boolean isReadyForReport = isVisitReadyForReport(v.getDate(), v.getArrivalTime());

                if (isCaregiver && v.getCaregiverId().equals(currentUserId) && isReadyForReport) {
                    androidx.appcompat.widget.AppCompatButton btnCreateReport = new androidx.appcompat.widget.AppCompatButton(this);
                    btnCreateReport.setText("Create visit report");
                    btnCreateReport.setAllCaps(false);
                    btnCreateReport.setTextColor(Color.WHITE);
                    btnCreateReport.setTextSize(18f);
                    btnCreateReport.setBackgroundResource(R.drawable.main_buttons);
                    btnCreateReport.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.buttons));

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(0, 16, 0, 0);
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    btnCreateReport.setLayoutParams(params);
                    btnCreateReport.setPadding(64, 16, 64, 16);

                    btnCreateReport.setOnClickListener(view -> {
                        Intent intent = new Intent(VisitCalendarActivity.this, CreateVisitReportActivity.class);
                        intent.putExtra("patientId", targetPatientId);
                        intent.putExtra("prefillDate", v.getDate());
                        intent.putExtra("prefillArrival", v.getArrivalTime());
                        intent.putExtra("prefillDeparture", v.getDepartureTime());
                        startActivity(intent);
                    });

                    llReportList.addView(btnCreateReport);
                }

                if (i < dayVisits.size() - 1) {
                    TextView divider = new TextView(this);
                    divider.setText("\n---\n");
                    divider.setTextColor(Color.BLACK);
                    llReportList.addView(divider);
                }
            }
        } else {
            llReportPanel.setVisibility(View.GONE);
        }

        if (!isPast && isCaregiver) {
            llSchedulePanel.setVisibility(View.VISIBLE);
        } else {
            llSchedulePanel.setVisibility(View.GONE);
        }
    }

    private void updateCalendarHighlights(HashSet<String> datesToHighlight) {
        List<EventDay> events = new ArrayList<>();
        for (String dateStr : datesToHighlight) {
            try {
                String[] parts = dateStr.split("-");
                Calendar calendar = Calendar.getInstance();
                calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
                events.add(new EventDay(calendar, R.drawable.dot_buttons));
            } catch (Exception e) {
                Log.e("VisitCalendar", "Error parsing highlight date layout index", e);
            }
        }
        calendarView.setEvents(events);
    }

    // Logic to save a new visit to Firestore
    private void saveNewVisitToFirebase() {
        String arrivalStr = etArrivalTime.getText().toString().trim();
        String departureStr = etDepartureTime.getText().toString().trim();

        if (!isValidTimeFormat(arrivalStr) || !isValidTimeFormat(departureStr)) {
            Toast.makeText(this, "Use HH:MM format.", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);

        String[] dParts = currentSelectedDateDbFormat.split("-");
        Calendar selectedCal = Calendar.getInstance();
        selectedCal.set(Integer.parseInt(dParts[0]), Integer.parseInt(dParts[1]) - 1, Integer.parseInt(dParts[2]), 0, 0, 0);

        if (selectedCal.before(today)) {
            Toast.makeText(this, "Cannot schedule visits in the past.", Toast.LENGTH_SHORT).show();
            return;
        }

        int arrMins = timeToMinutes(arrivalStr);
        int depMins = timeToMinutes(departureStr);

        if (depMins <= arrMins) {
            Toast.makeText(this, "Departure must be after arrival.", Toast.LENGTH_SHORT).show();
            return;
        }
        if ((depMins - arrMins) > 120) {
            Toast.makeText(this, "Visits cannot exceed 2 hours.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnBookVisit.setEnabled(false);
        firestore.collection("care_visits")
                .whereEqualTo("date", currentSelectedDateDbFormat)
                .get().addOnSuccessListener(query -> {
                    for (QueryDocumentSnapshot doc : query) {
                        VisitModel v = doc.toObject(VisitModel.class);
                        boolean caregiverBusy = v.getCaregiverId().equals(currentUserId);
                        boolean patientBusy = v.getPatientId().equals(targetPatientId);

                        if (caregiverBusy || patientBusy) {
                            int vArr = timeToMinutes(v.getArrivalTime());
                            int vDep = timeToMinutes(v.getDepartureTime());

                            if (arrMins < vDep && depMins > vArr) {
                                Toast.makeText(this, "Overlap detected! Caregiver or Patient is busy.", Toast.LENGTH_LONG).show();
                                btnBookVisit.setEnabled(true);
                                return;
                            }
                        }
                    }
                    executeSave(arrivalStr, departureStr, etNotes.getText().toString().trim());
                });
    }

    private void executeSave(String arr, String dep, String notes) {
        String visitId = firestore.collection("care_visits").document().getId();
        VisitModel newVisit = new VisitModel(visitId, targetPatientId, currentUserId, currentUserName, currentSelectedDateDbFormat, arr, dep, notes, Timestamp.now());

        firestore.collection("care_visits").document(visitId).set(newVisit)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Visit scheduled!", Toast.LENGTH_SHORT).show();
                    etArrivalTime.setText("");
                    etDepartureTime.setText("");
                    etNotes.setText("");
                    btnBookVisit.setEnabled(true);
                    refreshDataSequence();
                });
    }

    private int timeToMinutes(String time) {
        String[] p = time.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
}
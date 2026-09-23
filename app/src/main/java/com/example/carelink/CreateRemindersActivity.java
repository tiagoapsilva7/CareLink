package com.example.carelink;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity responsible for scheduling and cancelling automated medication reminders.
 * It utilizes Android's AlarmManager for local notifications and syncs the alarm
 * metadata with Firebase Firestore to maintain state across application sessions.
 */
public class CreateRemindersActivity extends AppCompatActivity {

    // UI Element Declarations
    ImageView btnBack;
    TextView tvDrugName, tvPosology, tvStartDate;
    EditText etDuration, etFrequency;
    Button btnSaveReminders;
    LinearLayout llTimesContainer;

    // Time and Date tracking
    Calendar startDateCal = Calendar.getInstance();
    List<Calendar> selectedTimes = new ArrayList<>();

    // Generalized target: this screen can save reminders against an "appointments" doc
    // or a "prescriptions" doc, depending on which intent extra was passed in.
    String targetCollection;
    String targetDocId;

    // Kept around so it can still be forwarded to ReminderReceiver intents below.
    String appointmentId;
    String prescriptionId;

    // Buffer for holding existing alarm times fetched from the database
    List<String> loadedTimesFromDb = null;

    // State flags and storage for alarm management
    List<Long> activeAlarmCodes = new ArrayList<>();
    boolean isAlarmActive = false;

    // Debounce variables to prevent rapid, unnecessary UI updates when typing frequency
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;
    private TextWatcher frequencyTextWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_reminders);

        // Request notification permissions required for Android 13 (API 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Bind UI components
        btnBack = findViewById(R.id.btnBack);
        tvDrugName = findViewById(R.id.tv_drug_name);
        tvPosology = findViewById(R.id.tv_posology);
        tvStartDate = findViewById(R.id.tv_start_date);
        etDuration = findViewById(R.id.et_duration);
        etFrequency = findViewById(R.id.et_frequency);
        btnSaveReminders = findViewById(R.id.btn_save_reminders);
        llTimesContainer = findViewById(R.id.ll_times_container);

        btnBack.setOnClickListener(v -> finish());

        // Extract metadata passed from the previous activity
        String drug = getIntent().getStringExtra("drugName");
        String posology = getIntent().getStringExtra("posology");
        appointmentId = getIntent().getStringExtra("appointmentId");
        prescriptionId = getIntent().getStringExtra("prescriptionId");

        // Determine which Firestore collection this reminder belongs to
        if (appointmentId != null && !appointmentId.isEmpty()) {
            targetCollection = "appointments";
            targetDocId = appointmentId;
        } else if (prescriptionId != null && !prescriptionId.isEmpty()) {
            targetCollection = "prescriptions";
            targetDocId = prescriptionId;
        }

        // Populate header fields with drug information
        tvDrugName.setText(drug != null && !drug.isEmpty() ? drug : "Medication");
        tvPosology.setText(posology != null && !posology.isEmpty() ? posology : "Instructions");

        // Setup DatePicker for the starting date
        tvStartDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                startDateCal.set(Calendar.YEAR, year);
                startDateCal.set(Calendar.MONTH, month);
                startDateCal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                tvStartDate.setText(dayOfMonth + "/" + (month + 1) + "/" + year);
            }, startDateCal.get(Calendar.YEAR), startDateCal.get(Calendar.MONTH), startDateCal.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Initialize the TextWatcher for the frequency input with a debounce mechanism.
        // This ensures time selectors are only generated after the user stops typing for 600ms.
        frequencyTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                loadedTimesFromDb = null;
                if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);

                debounceRunnable = () -> {
                    String freqStr = s.toString().trim();
                    if (!freqStr.isEmpty()) {
                        try {
                            int freq = Integer.parseInt(freqStr);
                            // Limit frequency to a maximum of 24 doses per day
                            if (freq > 0 && freq <= 24) generateTimeSelectors(freq);
                            else clearTimeSelectors();
                        } catch (NumberFormatException e) {
                            clearTimeSelectors();
                        }
                    } else {
                        clearTimeSelectors();
                    }
                };
                debounceHandler.postDelayed(debounceRunnable, 600);
            }
        };

        // Main action button handles both saving new alarms and cancelling existing ones
        btnSaveReminders.setOnClickListener(v -> {
            if (isAlarmActive) {
                cancelActiveAlarms();
            } else {
                scheduleAlarms();
            }
        });

        // If linking to a specific document, check if alarms already exist.
        // Otherwise, just listen for manual frequency input.
        if (targetDocId != null) {
            fetchExistingAlarms();
        } else {
            etFrequency.addTextChangedListener(frequencyTextWatcher);
        }
    }

    /**
     * Checks Firestore to see if the user has already set up reminders for this specific item.
     * If so, it populates the UI with the saved data and switches the screen to "Cancel" mode.
     */
    private void fetchExistingAlarms() {
        FirebaseFirestore.getInstance().collection(targetCollection).document(targetDocId)
                .get().addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String sDate = document.getString("reminderStartDate");
                        String sDur = document.getString("reminderDuration");
                        String sFreq = document.getString("reminderFrequency");
                        List<String> sTimes = (List<String>) document.get("reminderTimes");
                        List<Long> sCodes = (List<Long>) document.get("reminderRequestCodes");

                        // Validate that complete alarm data exists
                        if (sDate != null && !sDate.isEmpty() && sCodes != null && !sCodes.isEmpty()) {
                            // Alarms are active! Populate fields and freeze inputs.
                            activeAlarmCodes = sCodes;
                            tvStartDate.setText(sDate);
                            etDuration.setText(sDur);
                            loadedTimesFromDb = sTimes;
                            etFrequency.setText(sFreq);

                            // Re-generate the time UI blocks based on saved data
                            generateTimeSelectors(Integer.parseInt(sFreq));

                            setUiToCancelMode();
                        }
                    }
                    // Re-attach the watcher after populating data to avoid premature triggering
                    etFrequency.addTextChangedListener(frequencyTextWatcher);
                }).addOnFailureListener(e -> {
                    etFrequency.addTextChangedListener(frequencyTextWatcher);
                });
    }

    /**
     * Locks the input fields and transforms the main button into a "Cancel Reminders" button.
     */
    private void setUiToCancelMode() {
        isAlarmActive = true;
        tvStartDate.setEnabled(false);
        etDuration.setEnabled(false);
        etFrequency.setEnabled(false);

        btnSaveReminders.setText("Cancel notification reminders");
        btnSaveReminders.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        btnSaveReminders.setVisibility(View.VISIBLE);

        // Disable clicking on the dynamic time blocks
        for (int i = 0; i < llTimesContainer.getChildCount(); i++) {
            llTimesContainer.getChildAt(i).setOnClickListener(null);
        }
    }

    /**
     * Resets the UI back to a blank slate, allowing the user to create new reminders.
     */
    private void setUiToCreateMode() {
        isAlarmActive = false;
        tvStartDate.setEnabled(true);
        etDuration.setEnabled(true);
        etFrequency.setEnabled(true);

        tvStartDate.setText("");
        etDuration.setText("");
        etFrequency.setText("");
        llTimesContainer.removeAllViews();
        selectedTimes.clear();
        activeAlarmCodes.clear();

        btnSaveReminders.setText("Set Reminders");
        btnSaveReminders.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.buttons)));
        btnSaveReminders.setVisibility(View.GONE);
    }

    /**
     * Cancels all pending alarms matching the saved request codes using Android's AlarmManager,
     * and clears the associated metadata from the Firestore document.
     */
    private void cancelActiveAlarms() {
        // Step 1: Remove local system alarms
        if (!activeAlarmCodes.isEmpty()) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, ReminderReceiver.class);
            for (Long code : activeAlarmCodes) {
                // Must construct an identical PendingIntent (with the exact request code) to cancel it
                PendingIntent pi = PendingIntent.getBroadcast(this, code.intValue(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                if (alarmManager != null) alarmManager.cancel(pi);
            }
        }

        // Step 2: Clear metadata from the database
        if (targetDocId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("reminderStartDate", "");
            updates.put("reminderDuration", "");
            updates.put("reminderFrequency", "");
            updates.put("reminderTimes", new ArrayList<String>());
            updates.put("reminderRequestCodes", new ArrayList<Long>());

            FirebaseFirestore.getInstance().collection(targetCollection).document(targetDocId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Reminders canceled successfully.", Toast.LENGTH_SHORT).show();
                        setUiToCreateMode();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update database", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    /**
     * Clears the dynamic time selection views from the UI container.
     */
    private void clearTimeSelectors() {
        llTimesContainer.removeAllViews();
        selectedTimes.clear();
        if (!isAlarmActive) btnSaveReminders.setVisibility(View.GONE);
    }

    /**
     * Dynamically generates UI blocks for the user to select specific times for each dose based on frequency.
     * @param frequency The number of times per day the medication needs to be taken.
     */
    private void generateTimeSelectors(int frequency) {
        llTimesContainer.removeAllViews();
        selectedTimes.clear();

        for (int i = 0; i < frequency; i++) {
            final int index = i;
            Calendar timeCal = Calendar.getInstance();

            // Prioritize loading times from the database if they exist
            if (loadedTimesFromDb != null && index < loadedTimesFromDb.size()) {
                String[] timeParts = loadedTimesFromDb.get(index).split(":");
                if (timeParts.length == 2) {
                    timeCal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeParts[0]));
                    timeCal.set(Calendar.MINUTE, Integer.parseInt(timeParts[1]));
                }
            } else {
                // Default fallback: Space times out by 4 hours, starting at 8:00 AM
                timeCal.set(Calendar.HOUR_OF_DAY, 8 + (i * 4));
                timeCal.set(Calendar.MINUTE, 0);
            }
            timeCal.set(Calendar.SECOND, 0);
            selectedTimes.add(timeCal);

            // Construct the UI element for the time picker
            TextView tvTime = new TextView(this);
            tvTime.setBackgroundResource(R.drawable.bg_input_field);
            tvTime.setPadding(32, 32, 32, 32);
            tvTime.setTextSize(16f);
            tvTime.setTextColor(getColor(R.color.black));
            tvTime.setText("Dose " + (index + 1) + "  -  Tap to set time (" + String.format("%02d:%02d", timeCal.get(Calendar.HOUR_OF_DAY), timeCal.get(Calendar.MINUTE)) + ")");

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 16);
            tvTime.setLayoutParams(params);

            // Launch TimePickerDialog when the block is tapped
            tvTime.setOnClickListener(v -> {
                new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                    timeCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    timeCal.set(Calendar.MINUTE, minute);
                    tvTime.setText("Dose " + (index + 1) + "  -  " + String.format("%02d:%02d", hourOfDay, minute));
                }, timeCal.get(Calendar.HOUR_OF_DAY), timeCal.get(Calendar.MINUTE), true).show();
            });

            llTimesContainer.addView(tvTime);
        }
        btnSaveReminders.setVisibility(View.VISIBLE);
    }

    /**
     * Calculates all future alarm timestamps based on start date, duration, and selected times,
     * registers them with Android's AlarmManager, and saves the configuration to Firestore.
     */
    private void scheduleAlarms() {
        String durStr = etDuration.getText().toString().trim();
        String freqStr = etFrequency.getText().toString().trim();
        String startDateStr = tvStartDate.getText().toString().trim();

        // Basic validation
        if (durStr.isEmpty() || freqStr.isEmpty() || startDateStr.isEmpty() || startDateStr.equals("Select Day")) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        int durationDays = Integer.parseInt(durStr);
        String drug = tvDrugName.getText().toString();
        String posology = tvPosology.getText().toString();

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        int alarmsScheduled = 0;
        List<String> savedTimesList = new ArrayList<>();
        List<Long> generatedRequestCodes = new ArrayList<>();

        // Loop through each day of the duration
        for (int day = 0; day < durationDays; day++) {
            // Loop through each dose time within that day
            for (Calendar t : selectedTimes) {
                // Record the textual time for database storage during the first day loop
                if (day == 0) {
                    savedTimesList.add(String.format("%02d:%02d", t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE)));
                }

                // Calculate the exact timestamp for this specific alarm
                Calendar alarmTime = (Calendar) startDateCal.clone();
                alarmTime.set(Calendar.HOUR_OF_DAY, t.get(Calendar.HOUR_OF_DAY));
                alarmTime.set(Calendar.MINUTE, t.get(Calendar.MINUTE));
                alarmTime.set(Calendar.SECOND, 0);
                alarmTime.add(Calendar.DAY_OF_YEAR, day);

                // Only schedule alarms for times that are in the future
                if (alarmTime.getTimeInMillis() > System.currentTimeMillis()) {
                    Intent intent = new Intent(this, ReminderReceiver.class);
                    intent.putExtra("drugName", drug);
                    intent.putExtra("posology", posology);
                    intent.putExtra("appointmentId", appointmentId);
                    intent.putExtra("prescriptionId", prescriptionId);

                    // Use the timestamp as a unique request code to guarantee each alarm is distinct
                    int requestCode = (int) alarmTime.getTimeInMillis();
                    generatedRequestCodes.add((long) requestCode);

                    PendingIntent pi = PendingIntent.getBroadcast(
                            this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                    if (alarmManager != null) {
                        // Handle Android 12+ (API 31+) exact alarm permission changes
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), pi);
                            } else {
                                // Fallback to inexact alarm if the system denies exact alarm permissions
                                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), pi);
                            }
                        } else {
                            // Standard exact alarm scheduling for older Android versions
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), pi);
                        }
                        alarmsScheduled++;
                    }
                }
            }
        }

        final int finalAlarmsScheduled = alarmsScheduled;

        // Persist the configuration and request codes to Firestore so they can be retrieved/cancelled later
        if (targetDocId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("reminderStartDate", tvStartDate.getText().toString());
            updates.put("reminderDuration", durStr);
            updates.put("reminderFrequency", freqStr);
            updates.put("reminderTimes", savedTimesList);
            updates.put("reminderRequestCodes", generatedRequestCodes);

            FirebaseFirestore.getInstance().collection(targetCollection).document(targetDocId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Scheduled " + finalAlarmsScheduled + " medication reminders!", Toast.LENGTH_LONG).show();
                        finish();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save configuration to cloud.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        } else {
            Toast.makeText(this, "Scheduled " + finalAlarmsScheduled + " medication reminders!", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
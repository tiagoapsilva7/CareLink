package com.example.carelink.model;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A symptom logged by a patient, stored in the Firestore {@code symptoms}
 * collection and listed by SymptomsFragment.
 *
 * The no-argument constructor and public getters/setters are required by
 * Firestore's automatic POJO deserialisation; do not remove them.
 */
public class SymptomModel {
    private String symptomId;
    private String patientId;
    private String doctorId;
    private Timestamp createdTimestamp;
    private Timestamp onsetTimestamp;
    private String title;
    private String onsetDate;
    private String duration;
    private String location;
    private String nature;
    private String progression;
    private String painLevel;
    private String otherInfo;

    // NEW: List to hold secondary symptoms
    private List<SecondarySymptom> secondarySymptoms;

    private Map<String, Boolean> readBy;
    private Map<String, Boolean> hiddenBy;

    public SymptomModel() {}

    public SymptomModel(String symptomId, String patientId, String doctorId, Timestamp createdTimestamp,
                        Timestamp onsetTimestamp, String title, String onsetDate, String duration,
                        String location, String nature, String progression, String painLevel,
                        String otherInfo, List<SecondarySymptom> secondarySymptoms,
                        Map<String, Boolean> readBy, Map<String, Boolean> hiddenBy) {
        this.symptomId = symptomId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.createdTimestamp = createdTimestamp;
        this.onsetTimestamp = onsetTimestamp;
        this.title = title;
        this.onsetDate = onsetDate;
        this.duration = duration;
        this.location = location;
        this.nature = nature;
        this.progression = progression;
        this.painLevel = painLevel;
        this.otherInfo = otherInfo;

        // Ensure it's never strictly null
        this.secondarySymptoms = secondarySymptoms != null ? secondarySymptoms : new ArrayList<>();

        this.readBy = readBy;
        this.hiddenBy = hiddenBy;
    }

    public String getSymptomId() { return symptomId; }
    public String getPatientId() { return patientId; }
    public String getDoctorId() { return doctorId; }
    public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
    public Timestamp getCreatedTimestamp() { return createdTimestamp; }
    public Timestamp getOnsetTimestamp() { return onsetTimestamp; }
    public String getTitle() { return title; }
    public String getOnsetDate() { return onsetDate; }
    public String getDuration() { return duration; }
    public String getLocation() { return location; }
    public String getNature() { return nature; }
    public String getProgression() { return progression; }
    public String getPainLevel() { return painLevel; }
    public String getOtherInfo() { return otherInfo; }

    public List<SecondarySymptom> getSecondarySymptoms() { return secondarySymptoms; }
    public void setSecondarySymptoms(List<SecondarySymptom> secondarySymptoms) { this.secondarySymptoms = secondarySymptoms; }

    public Map<String, Boolean> getReadBy() { return readBy; }
    public void setReadBy(Map<String, Boolean> readBy) { this.readBy = readBy; }
    public Map<String, Boolean> getHiddenBy() { return hiddenBy; }
    public void setHiddenBy(Map<String, Boolean> hiddenBy) { this.hiddenBy = hiddenBy; }

    // --- INNER CLASS FOR SECONDARY SYMPTOMS ---
    public static class SecondarySymptom {
        private String description;
        private String onsetType; // "Same as main" or "After main"
        private String timeAfter; // e.g., "2 days"

        public SecondarySymptom() {} // Required by Firebase

        public SecondarySymptom(String description, String onsetType, String timeAfter) {
            this.description = description;
            this.onsetType = onsetType;
            this.timeAfter = timeAfter;
        }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getOnsetType() { return onsetType; }
        public void setOnsetType(String onsetType) { this.onsetType = onsetType; }
        public String getTimeAfter() { return timeAfter; }
        public void setTimeAfter(String timeAfter) { this.timeAfter = timeAfter; }
    }
}
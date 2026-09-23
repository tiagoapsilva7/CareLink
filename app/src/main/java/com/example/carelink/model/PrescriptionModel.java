package com.example.carelink.model;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;

/**
 * A medication prescribed to a patient, stored in the Firestore
 * {@code prescriptions} collection and rendered by PrescriptionsFragment.
 *
 * The no-argument constructor and public getters/setters are required by
 * Firestore's automatic POJO deserialisation; do not remove them.
 */
public class PrescriptionModel {

    private String id;
    private String patientId;
    private String pdfUrl;
    private String compound;
    private String posology;

    private String reminderStartDate;
    private String reminderDuration;
    private String reminderFrequency;
    private List<String> reminderTimes;
    private List<Long> reminderRequestCodes;

    private Timestamp createdAt;

    public PrescriptionModel() {
        // Required empty constructor for Firestore deserialization
    }

    public PrescriptionModel(String id, String patientId, String pdfUrl, String compound,
                             String posology, Timestamp createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.pdfUrl = pdfUrl;
        this.compound = compound;
        this.posology = posology;
        this.reminderStartDate = "";
        this.reminderDuration = "";
        this.reminderFrequency = "";
        this.reminderTimes = new ArrayList<>();
        this.reminderRequestCodes = new ArrayList<>();
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }

    public String getCompound() { return compound; }
    public void setCompound(String compound) { this.compound = compound; }

    public String getPosology() { return posology; }
    public void setPosology(String posology) { this.posology = posology; }

    public String getReminderStartDate() { return reminderStartDate; }
    public void setReminderStartDate(String reminderStartDate) { this.reminderStartDate = reminderStartDate; }

    public String getReminderDuration() { return reminderDuration; }
    public void setReminderDuration(String reminderDuration) { this.reminderDuration = reminderDuration; }

    public String getReminderFrequency() { return reminderFrequency; }
    public void setReminderFrequency(String reminderFrequency) { this.reminderFrequency = reminderFrequency; }

    public List<String> getReminderTimes() { return reminderTimes; }
    public void setReminderTimes(List<String> reminderTimes) { this.reminderTimes = reminderTimes; }

    public List<Long> getReminderRequestCodes() { return reminderRequestCodes; }
    public void setReminderRequestCodes(List<Long> reminderRequestCodes) { this.reminderRequestCodes = reminderRequestCodes; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
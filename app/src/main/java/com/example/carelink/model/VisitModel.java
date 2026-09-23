package com.example.carelink.model;

import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

/**
 * A scheduled caregiver visit shown on the visit calendar.
 *
 * Distinct from {@link VisitReportModel}: this is the appointment itself,
 * whereas a report is the record written after a visit has taken place.
 *
 * The no-argument constructor and public getters/setters are required by
 * Firestore's automatic POJO deserialisation; do not remove them.
 */
public class VisitModel {
    private String visitId;
    private String patientId;
    private String caregiverId;
    private String caregiverName;
    private String date;

    // New explicitly defined fields
    private String arrivalTime;
    private String departureTime;
    private String notes;
    private String timeWindow; // Retained for backwards compatibility if needed

    private Timestamp createdAt;

    // Soft-delete: when a user removes a visit report from their own list, their uid
    // is added here as a key (value true) instead of deleting the document outright,
    // so the other party (caregiver or patient) still sees their copy.
    private Map<String, Boolean> hiddenBy;

    public VisitModel() {}

    public VisitModel(String visitId, String patientId, String caregiverId, String caregiverName, String date, String arrivalTime, String departureTime, String notes, Timestamp createdAt) {
        this.visitId = visitId;
        this.patientId = patientId;
        this.caregiverId = caregiverId;
        this.caregiverName = caregiverName;
        this.date = date;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.notes = notes;
        this.timeWindow = arrivalTime + " - " + departureTime;
        this.createdAt = createdAt;
        this.hiddenBy = new HashMap<>();
    }

    public String getVisitId() { return visitId; }
    public void setVisitId(String visitId) { this.visitId = visitId; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getCaregiverId() { return caregiverId; }
    public void setCaregiverId(String caregiverId) { this.caregiverId = caregiverId; }

    public String getCaregiverName() { return caregiverName; }
    public void setCaregiverName(String caregiverName) { this.caregiverName = caregiverName; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }

    public String getDepartureTime() { return departureTime; }
    public void setDepartureTime(String departureTime) { this.departureTime = departureTime; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getTimeWindow() { return timeWindow; }
    public void setTimeWindow(String timeWindow) { this.timeWindow = timeWindow; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Map<String, Boolean> getHiddenBy() { return hiddenBy; }
    public void setHiddenBy(Map<String, Boolean> hiddenBy) { this.hiddenBy = hiddenBy; }
}
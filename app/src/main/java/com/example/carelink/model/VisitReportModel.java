package com.example.carelink.model;

import com.google.firebase.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * A caregiver's report of a completed home visit, stored in the Firestore
 * {@code visit_reports} collection.
 *
 * A report may also schedule the next visit: when {@code futureVisitScheduled}
 * is true the {@code futureVisit*} fields carry that appointment's details and
 * are surfaced in the visit calendar.
 *
 * {@code readBy} and {@code hiddenBy} are per-user maps keyed by user id, which
 * let each participant track and dismiss a report independently without
 * affecting what the other party sees.
 *
 * The no-argument constructor and public getters/setters are required by
 * Firestore's automatic POJO deserialisation; do not remove them.
 */
public class VisitReportModel {
    private String reportId;
    public Map<String, Boolean> hiddenBy;
    private String patientId;
    private String caregiverId;
    private Timestamp createdAt;
    private String visitDate;
    private String visitHours;
    private String patientComplaints;
    private String report;
    private String therapeuticPlan;
    private String recommendations;
    private boolean futureVisitScheduled;

    // Future visit specific fields
    private String futureVisitDate;
    private String futureVisitArrival;
    private String futureVisitDeparture;
    private String futureVisitNotes;

    private Map<String, Boolean> readBy;

    public VisitReportModel() {}

    public VisitReportModel(String reportId, String patientId, String caregiverId, Timestamp createdAt,
                            String visitDate, String visitHours, String patientComplaints,
                            String report, String therapeuticPlan, String recommendations,
                            boolean futureVisitScheduled, String futureVisitDate,
                            String futureVisitArrival, String futureVisitDeparture, String futureVisitNotes) {
        this.reportId = reportId;
        this.patientId = patientId;
        this.caregiverId = caregiverId;
        this.createdAt = createdAt;
        this.visitDate = visitDate;
        this.visitHours = visitHours;
        this.patientComplaints = patientComplaints;
        this.report = report;
        this.therapeuticPlan = therapeuticPlan;
        this.recommendations = recommendations;
        this.futureVisitScheduled = futureVisitScheduled;

        this.futureVisitDate = futureVisitDate;
        this.futureVisitArrival = futureVisitArrival;
        this.futureVisitDeparture = futureVisitDeparture;
        this.futureVisitNotes = futureVisitNotes;

        this.readBy = new HashMap<>();
        this.hiddenBy = new HashMap<>();
    }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getCaregiverId() { return caregiverId; }
public Map<String, Boolean> getHiddenBy() {
    return hiddenBy;
}
public void setHiddenBy(Map<String, Boolean> getHiddenBy) {
    this.hiddenBy = getHiddenBy;
}
public void setCaregiverId(String caregiverId) { this.caregiverId = caregiverId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getVisitDate() { return visitDate; }
    public void setVisitDate(String visitDate) { this.visitDate = visitDate; }

    public String getVisitHours() { return visitHours; }
    public void setVisitHours(String visitHours) { this.visitHours = visitHours; }

    public String getPatientComplaints() { return patientComplaints; }
    public void setPatientComplaints(String patientComplaints) { this.patientComplaints = patientComplaints; }

    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }

    public String getTherapeuticPlan() { return therapeuticPlan; }
    public void setTherapeuticPlan(String therapeuticPlan) { this.therapeuticPlan = therapeuticPlan; }

    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }

    public boolean isFutureVisitScheduled() { return futureVisitScheduled; }
    public void setFutureVisitScheduled(boolean futureVisitScheduled) { this.futureVisitScheduled = futureVisitScheduled; }

    public String getFutureVisitDate() { return futureVisitDate; }
    public void setFutureVisitDate(String futureVisitDate) { this.futureVisitDate = futureVisitDate; }

    public String getFutureVisitArrival() { return futureVisitArrival; }
    public void setFutureVisitArrival(String futureVisitArrival) { this.futureVisitArrival = futureVisitArrival; }

    public String getFutureVisitDeparture() { return futureVisitDeparture; }
    public void setFutureVisitDeparture(String futureVisitDeparture) { this.futureVisitDeparture = futureVisitDeparture; }

    public String getFutureVisitNotes() { return futureVisitNotes; }
    public void setFutureVisitNotes(String futureVisitNotes) { this.futureVisitNotes = futureVisitNotes; }

    public Map<String, Boolean> getReadBy() { return readBy; }
    public void setReadBy(Map<String, Boolean> readBy) { this.readBy = readBy; }
}
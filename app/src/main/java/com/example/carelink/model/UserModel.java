package com.example.carelink.model;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * A CareLink user account, stored in the Firestore {@code users} collection.
 *
 * The same model backs both roles: {@link #getRole()} distinguishes a patient
 * from a caregiver, and each role leaves the other's fields unset. Patients
 * populate the clinical fields (age, height, weight, diagnosed illnesses,
 * surgery history) while caregivers populate hospital and specialty.
 *
 * {@link #getFcmToken()} is the device token used to address push
 * notifications at this user, and is refreshed on each login.
 *
 * The no-argument constructor and public getters/setters are required by
 * Firestore's automatic POJO deserialisation; do not remove them.
 */
public class UserModel {
    private String username;
    private String email;
    private Timestamp createdTimestamp;
    private int age;
    private String userId;
    private String fcmToken;
    private String role;
    private List<String> diagnosedIllnesses;
    private String surgeryHistory;
    private String hospital;
    private String specialty;
    private int height;
    private double weight;
    private String gender;
    private String address; // NEW: Field for Address
    private Timestamp lastOnline;
    private int unreadMedicalRecords = 0;

    public UserModel() {
        this.diagnosedIllnesses = new ArrayList<>();
        this.address = "";
    }

    public UserModel(String username, String email, Timestamp createdTimestamp, String userId, String role, int age, String hospital) {
        this.username = username;
        this.email = email;
        this.createdTimestamp = createdTimestamp;
        this.userId = userId;
        this.role = role;
        this.age = age;
        this.hospital = hospital;
        this.diagnosedIllnesses = new ArrayList<>();
        this.surgeryHistory = "";
        this.specialty = "";
        this.height = 0;
        this.weight = 0.0;
        this.gender = "";
        this.address = ""; // Initialized
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Timestamp getCreatedTimestamp() { return createdTimestamp; }
    public void setCreatedTimestamp(Timestamp createdTimestamp) { this.createdTimestamp = createdTimestamp; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public List<String> getDiagnosedIllnesses() {
        if (diagnosedIllnesses == null) {
            diagnosedIllnesses = new ArrayList<>();
        }
        return diagnosedIllnesses;
    }
    public void setDiagnosedIllnesses(List<String> diagnosedIllnesses) { this.diagnosedIllnesses = diagnosedIllnesses; }

    public String getSurgeryHistory() { return surgeryHistory == null ? "" : surgeryHistory; }
    public void setSurgeryHistory(String surgeryHistory) { this.surgeryHistory = surgeryHistory; }

    public String getHospital() { return hospital == null ? "" : hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }

    public String getSpecialty() { return specialty == null ? "" : specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public String getGender() { return gender == null ? "" : gender; }
    public void setGender(String gender) { this.gender = gender; }

    // NEW Getter and Setter for Address
    public String getAddress() { return address == null ? "" : address; }
    public void setAddress(String address) { this.address = address; }

    public Timestamp getLastOnline() { return lastOnline; }
    public void setLastOnline(Timestamp lastOnline) { this.lastOnline = lastOnline; }

    public int getUnreadMedicalRecords() { return unreadMedicalRecords; }
    public void setUnreadMedicalRecords(int unreadMedicalRecords) { this.unreadMedicalRecords = unreadMedicalRecords; }

    @Override
    public String toString() {
        return "UserModel{" +
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
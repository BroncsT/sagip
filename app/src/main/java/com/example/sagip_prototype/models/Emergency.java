package com.example.sagip_prototype.models;

import com.google.firebase.firestore.GeoPoint;
import java.util.List;
import java.util.Map;

public class Emergency {
    public String emergencyId;
    public String helpRequestId;
    public String seniorUid;
    public String seniorName;
    public String seniorPhone;
    public GeoPoint location;
    public String locationAddress;
    
    // Emergency Details
    public String emergencyType;
    public String severity; // "low", "medium", "high", "critical"
    public String description;
    public long timestamp;
    public String status; // "active", "responded", "completed", "cancelled"
    
    // Patient Information
    public int patientAge;
    public String patientGender;
    public List<String> medicalHistory;
    public List<String> allergies;
    public String currentMedications;
    public String vitalSigns;
    
    // Response Information
    public String rescuerId;
    public String rescuerName;
    public GeoPoint rescuerLocation;
    public long responseTime;
    public String selectedHospitalId;
    public String selectedHospitalName;
    
    // AI Context
    public Map<String, Double> aiScores;
    public String aiRecommendation;
    public double confidenceScore;
    public List<String> alternativeHospitals;
    
    public Emergency() {
        // Default constructor for Firestore
    }
    
    public Emergency(String emergencyId, String seniorUid, String seniorName, 
                    GeoPoint location, String emergencyType, String severity) {
        this.emergencyId = emergencyId;
        this.seniorUid = seniorUid;
        this.seniorName = seniorName;
        this.location = location;
        this.emergencyType = emergencyType;
        this.severity = severity;
        this.timestamp = System.currentTimeMillis();
        this.status = "active";
    }
    
    // Getters and Setters
    public String getEmergencyId() { return emergencyId; }
    public void setEmergencyId(String emergencyId) { this.emergencyId = emergencyId; }
    
    public String getSeniorName() { return seniorName; }
    public void setSeniorName(String seniorName) { this.seniorName = seniorName; }
    
    public String getEmergencyType() { return emergencyType; }
    public void setEmergencyType(String emergencyType) { this.emergencyType = emergencyType; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    // Helper methods
    public boolean isCritical() {
        return "critical".equals(severity);
    }
    
    public boolean isHighPriority() {
        return "high".equals(severity) || "critical".equals(severity);
    }
    
    public long getAgeInMinutes() {
        return (System.currentTimeMillis() - timestamp) / (1000 * 60);
    }
    
    public double getPriorityScore() {
        double baseScore = 1.0;
        
        // Severity multiplier
        switch (severity) {
            case "critical": baseScore *= 4.0; break;
            case "high": baseScore *= 3.0; break;
            case "medium": baseScore *= 2.0; break;
            case "low": baseScore *= 1.0; break;
        }
        
        // Age multiplier (older emergencies get higher priority)
        long ageMinutes = getAgeInMinutes();
        if (ageMinutes > 30) baseScore *= 1.5;
        if (ageMinutes > 60) baseScore *= 2.0;
        
        return baseScore;
    }
}

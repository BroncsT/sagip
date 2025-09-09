package com.example.sagip_prototype.models;

import com.google.firebase.firestore.GeoPoint;
import java.util.List;
import java.util.Map;

public class Hospital {
    public String hospitalId;
    public String name;
    public GeoPoint location;
    public String address;
    public String phone;
    public String email;
    
    // Capacity Information
    public int totalBeds;
    public int availableBeds;
    public int icuBeds;
    public int availableIcuBeds;
    public int emergencyBeds;
    public int availableEmergencyBeds;
    
    // Specializations
    public List<String> specializations;
    public List<String> services;
    public Map<String, Boolean> emergencyServices;
    
    // Performance Metrics
    public double avgResponseTime; // in minutes
    public double successRate; // 0.0 to 1.0
    public double patientSatisfaction; // 1.0 to 5.0
    public int totalCasesHandled;
    public int successfulCases;
    
    // Real-time Status
    public double currentLoad; // 0.0 to 1.0
    public int waitingTime; // in minutes
    public double staffAvailability; // 0.0 to 1.0
    public boolean isOperational;
    public String operationalStatus; // "operational", "busy", "overcrowded", "closed"
    
    // AI Scores (calculated by algorithms)
    public double topsisScore;
    public double mlScore;
    public double finalScore;
    public int ranking;
    
    // Route Information
    public double distanceFromSenior; // in km
    public double distanceFromRescuer; // in km
    public int estimatedTravelTime; // in minutes
    public double trafficLevel; // 0.0 to 1.0
    
    public Hospital() {
        // Default constructor for Firestore
    }
    
    public Hospital(String hospitalId, String name, GeoPoint location, String address) {
        this.hospitalId = hospitalId;
        this.name = name;
        this.location = location;
        this.address = address;
        this.isOperational = true;
        this.operationalStatus = "operational";
    }
    
    // Getters and Setters
    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public double getAvailabilityScore() {
        if (totalBeds == 0) return 0.0;
        return (double) availableBeds / totalBeds;
    }
    
    public double getCapacityScore() {
        if (totalBeds == 0) return 0.0;
        return (double) (totalBeds - availableBeds) / totalBeds;
    }
    
    public double getResponseTimeScore() {
        return 1.0 / (1.0 + avgResponseTime / 10.0); // Normalize to 0-1
    }
    
    public boolean hasSpecialization(String specialization) {
        return specializations != null && specializations.contains(specialization);
    }
    
    public boolean isEmergencyServiceAvailable(String service) {
        return emergencyServices != null && emergencyServices.getOrDefault(service, false);
    }
    
    public double getOverallScore() {
        return (topsisScore * 0.4) + (mlScore * 0.6);
    }
}

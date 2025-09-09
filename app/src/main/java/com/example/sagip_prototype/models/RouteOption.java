package com.example.sagip_prototype.models;

import com.google.firebase.firestore.GeoPoint;
import java.util.List;

public class RouteOption {
    public String routeId;
    public GeoPoint startLocation;
    public GeoPoint endLocation;
    public List<GeoPoint> waypoints;
    public List<RouteStep> steps;
    
    // Route Metrics
    public double totalDistance; // in meters
    public int totalDuration; // in seconds
    public int trafficDuration; // in seconds (with traffic)
    public double trafficLevel; // 0.0 to 1.0
    public String polyline; // Encoded polyline for Google Maps
    
    // Route Quality
    public List<String> roadTypes; // "highway", "arterial", "local", etc.
    public int trafficLights;
    public int intersections;
    public double roadQuality; // 0.0 to 1.0
    public boolean hasTolls;
    public boolean hasHighways;
    
    // Emergency-specific factors
    public double emergencyScore;
    public double hospitalProximityScore;
    public double accessibilityScore;
    public double safetyScore;
    
    // AI Scores
    public double topsisScore;
    public double astarScore;
    public double mlScore;
    public double finalScore;
    
    public RouteOption() {
        // Default constructor
    }
    
    public RouteOption(GeoPoint start, GeoPoint end) {
        this.startLocation = start;
        this.endLocation = end;
        this.trafficLevel = 0.0;
        this.roadQuality = 1.0;
        this.hasTolls = false;
        this.hasHighways = false;
    }
    
    // Getters and Setters
    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    
    public GeoPoint getStartLocation() { return startLocation; }
    public void setStartLocation(GeoPoint startLocation) { this.startLocation = startLocation; }
    
    public GeoPoint getEndLocation() { return endLocation; }
    public void setEndLocation(GeoPoint endLocation) { this.endLocation = endLocation; }
    
    public double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }
    
    public int getTotalDuration() { return totalDuration; }
    public void setTotalDuration(int totalDuration) { this.totalDuration = totalDuration; }
    
    public double getTrafficLevel() { return trafficLevel; }
    public void setTrafficLevel(double trafficLevel) { this.trafficLevel = trafficLevel; }
    
    // Helper methods
    public double getDistanceInKm() {
        return totalDistance / 1000.0;
    }
    
    public int getDurationInMinutes() {
        return totalDuration / 60;
    }
    
    public double getAverageSpeed() {
        if (totalDuration == 0) return 0.0;
        return (totalDistance / 1000.0) / (totalDuration / 3600.0); // km/h
    }
    
    public boolean isHighwayRoute() {
        return roadTypes != null && roadTypes.contains("highway");
    }
    
    public double getEfficiencyScore() {
        if (totalDuration == 0) return 0.0;
        return totalDistance / totalDuration; // meters per second
    }
    
    public double getOverallScore() {
        return (topsisScore * 0.3) + (astarScore * 0.4) + (mlScore * 0.3);
    }
}

// Route step for detailed navigation
class RouteStep {
    public String instruction;
    public double distance;
    public int duration;
    public GeoPoint startLocation;
    public GeoPoint endLocation;
    public String maneuver; // "turn-left", "turn-right", "straight", etc.
    public String roadName;
    
    public RouteStep() {}
    
    public RouteStep(String instruction, double distance, int duration) {
        this.instruction = instruction;
        this.distance = distance;
        this.duration = duration;
    }
}

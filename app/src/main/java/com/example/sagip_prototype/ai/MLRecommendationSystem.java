package com.example.sagip_prototype.ai;

import com.example.sagip_prototype.models.Hospital;
import com.example.sagip_prototype.models.Emergency;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.*;

public class MLRecommendationSystem {
    
    private FirebaseFirestore db;
    private Map<String, Double> featureWeights;
    private Map<String, Double> historicalSuccessRates;
    
    public MLRecommendationSystem(FirebaseFirestore db) {
        this.db = db;
        initializeFeatureWeights();
        loadHistoricalData();
    }
    
    private void initializeFeatureWeights() {
        featureWeights = new HashMap<>();
        featureWeights.put("distance", 0.25);
        featureWeights.put("availability", 0.20);
        featureWeights.put("specialization", 0.18);
        featureWeights.put("response_time", 0.15);
        featureWeights.put("capacity", 0.10);
        featureWeights.put("time_of_day", 0.05);
        featureWeights.put("day_of_week", 0.03);
        featureWeights.put("weather", 0.02);
        featureWeights.put("traffic", 0.02);
    }
    
    private void loadHistoricalData() {
        // Load historical success rates from Firestore
        db.collection("Sagip")
          .document("hospitalPerformance")
          .collection("historicalData")
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              historicalSuccessRates = new HashMap<>();
              for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                  String hospitalId = document.getId();
                  Double successRate = document.getDouble("successRate");
                  if (successRate != null) {
                      historicalSuccessRates.put(hospitalId, successRate);
                  }
              }
          })
          .addOnFailureListener(e -> {
              // Use default success rates if loading fails
              historicalSuccessRates = getDefaultSuccessRates();
          });
    }
    
    private Map<String, Double> getDefaultSuccessRates() {
        Map<String, Double> defaultRates = new HashMap<>();
        defaultRates.put("default", 0.85); // 85% default success rate
        return defaultRates;
    }
    
    public MLRecommendationResult recommendHospital(List<Hospital> hospitals, Emergency emergency, 
                                                   double rescuerLat, double rescuerLon) {
        
        if (hospitals == null || hospitals.isEmpty()) {
            return new MLRecommendationResult(null, new ArrayList<>());
        }
        
        List<HospitalMLScore> hospitalScores = new ArrayList<>();
        
        for (Hospital hospital : hospitals) {
            double mlScore = calculateMLScore(hospital, emergency, rescuerLat, rescuerLon);
            hospital.mlScore = mlScore;
            hospitalScores.add(new HospitalMLScore(hospital, mlScore));
        }
        
        // Sort by ML score (highest first)
        hospitalScores.sort((a, b) -> Double.compare(b.score, a.score));
        
        // Update hospital rankings
        for (int i = 0; i < hospitalScores.size(); i++) {
            hospitalScores.get(i).hospital.ranking = i + 1;
        }
        
        Hospital bestHospital = hospitalScores.isEmpty() ? null : hospitalScores.get(0).hospital;
        
        return new MLRecommendationResult(bestHospital, hospitalScores);
    }
    
    private double calculateMLScore(Hospital hospital, Emergency emergency, 
                                  double rescuerLat, double rescuerLon) {
        
        // Extract features
        Map<String, Double> features = extractFeatures(hospital, emergency, rescuerLat, rescuerLon);
        
        // Calculate weighted score
        double totalScore = 0.0;
        double totalWeight = 0.0;
        
        for (Map.Entry<String, Double> entry : features.entrySet()) {
            String feature = entry.getKey();
            Double value = entry.getValue();
            Double weight = featureWeights.get(feature);
            
            if (weight != null && value != null) {
                totalScore += value * weight;
                totalWeight += weight;
            }
        }
        
        // Normalize score
        if (totalWeight > 0) {
            totalScore = totalScore / totalWeight;
        }
        
        // Apply historical success rate
        double historicalRate = historicalSuccessRates.getOrDefault(hospital.hospitalId, 0.85);
        totalScore = (totalScore * 0.7) + (historicalRate * 0.3);
        
        return Math.min(1.0, Math.max(0.0, totalScore));
    }
    
    private Map<String, Double> extractFeatures(Hospital hospital, Emergency emergency, 
                                              double rescuerLat, double rescuerLon) {
        
        Map<String, Double> features = new HashMap<>();
        
        // Distance feature
        double distance = calculateDistance(
            emergency.location.getLatitude(), emergency.location.getLongitude(),
            hospital.location.getLatitude(), hospital.location.getLongitude()
        );
        features.put("distance", 1.0 / (1.0 + distance / 5.0)); // Normalize to 5km
        
        // Availability feature
        features.put("availability", hospital.getAvailabilityScore());
        
        // Specialization feature
        features.put("specialization", calculateSpecializationMatch(hospital, emergency.emergencyType));
        
        // Response time feature
        features.put("response_time", hospital.getResponseTimeScore());
        
        // Capacity feature
        features.put("capacity", hospital.getCapacityScore());
        
        // Time-based features
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        
        // Time of day feature (0-1 scale)
        double timeOfDayScore = calculateTimeOfDayScore(hour);
        features.put("time_of_day", timeOfDayScore);
        
        // Day of week feature (0-1 scale)
        double dayOfWeekScore = calculateDayOfWeekScore(dayOfWeek);
        features.put("day_of_week", dayOfWeekScore);
        
        // Weather feature (simplified)
        features.put("weather", 0.8); // Assume good weather
        
        // Traffic feature
        features.put("traffic", 1.0 / (1.0 + hospital.trafficLevel));
        
        return features;
    }
    
    private double calculateSpecializationMatch(Hospital hospital, String emergencyType) {
        if (hospital.specializations == null || emergencyType == null) {
            return 0.5;
        }
        
        // Map emergency types to specializations
        Map<String, String> emergencyToSpecialization = new HashMap<>();
        emergencyToSpecialization.put("cardiac_arrest", "cardiology");
        emergencyToSpecialization.put("heart_attack", "cardiology");
        emergencyToSpecialization.put("stroke", "neurology");
        emergencyToSpecialization.put("head_injury", "neurology");
        emergencyToSpecialization.put("trauma", "trauma");
        emergencyToSpecialization.put("accident", "trauma");
        emergencyToSpecialization.put("respiratory", "pulmonology");
        emergencyToSpecialization.put("breathing", "pulmonology");
        emergencyToSpecialization.put("pediatric", "pediatrics");
        
        String requiredSpecialization = emergencyToSpecialization.getOrDefault(emergencyType, "emergency_medicine");
        
        if (hospital.hasSpecialization(requiredSpecialization)) {
            return 1.0; // Perfect match
        }
        
        // Check for related specializations
        for (String specialization : hospital.specializations) {
            if (isRelatedSpecialization(requiredSpecialization, specialization)) {
                return 0.7; // Good match
            }
        }
        
        return 0.3; // Poor match
    }
    
    private boolean isRelatedSpecialization(String required, String available) {
        // Define related specializations
        if (required.equals("cardiology") && available.equals("emergency_medicine")) return true;
        if (required.equals("neurology") && available.equals("emergency_medicine")) return true;
        if (required.equals("trauma") && available.equals("emergency_medicine")) return true;
        if (required.equals("pulmonology") && available.equals("emergency_medicine")) return true;
        return false;
    }
    
    private double calculateTimeOfDayScore(int hour) {
        // Peak hours have lower scores due to higher traffic and congestion
        if (hour >= 7 && hour <= 9) { // Morning rush
            return 0.6;
        } else if (hour >= 17 && hour <= 19) { // Evening rush
            return 0.6;
        } else if (hour >= 12 && hour <= 14) { // Lunch time
            return 0.7;
        } else if (hour >= 22 || hour <= 5) { // Night time
            return 0.9; // Better conditions at night
        } else {
            return 0.8; // Normal hours
        }
    }
    
    private double calculateDayOfWeekScore(int dayOfWeek) {
        // Weekends generally have better conditions
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return 0.9;
        } else {
            return 0.7; // Weekdays
        }
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c;
        
        return distance;
    }
    
    // Method to update historical data (called after successful emergency response)
    public void updateHistoricalData(String hospitalId, boolean success, double responseTime, 
                                   String emergencyType, double patientSatisfaction) {
        
        Map<String, Object> performanceData = new HashMap<>();
        performanceData.put("hospitalId", hospitalId);
        performanceData.put("success", success);
        performanceData.put("responseTime", responseTime);
        performanceData.put("emergencyType", emergencyType);
        performanceData.put("patientSatisfaction", patientSatisfaction);
        performanceData.put("timestamp", System.currentTimeMillis());
        
        // Add to historical data collection
        db.collection("Sagip")
          .document("hospitalPerformance")
          .collection("historicalData")
          .add(performanceData)
          .addOnSuccessListener(documentReference -> {
              // Update success rate for this hospital
              updateHospitalSuccessRate(hospitalId);
          })
          .addOnFailureListener(e -> {
              // Log error
              System.err.println("Failed to update historical data: " + e.getMessage());
          });
    }
    
    private void updateHospitalSuccessRate(String hospitalId) {
        // Calculate new success rate based on recent data
        db.collection("Sagip")
          .document("hospitalPerformance")
          .collection("historicalData")
          .whereEqualTo("hospitalId", hospitalId)
          .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
          .limit(100) // Last 100 cases
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              int totalCases = queryDocumentSnapshots.size();
              int successfulCases = 0;
              
              for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                  Boolean success = document.getBoolean("success");
                  if (success != null && success) {
                      successfulCases++;
                  }
              }
              
              double successRate = totalCases > 0 ? (double) successfulCases / totalCases : 0.85;
              
              // Update success rate
              historicalSuccessRates.put(hospitalId, successRate);
              
              // Save to Firestore
              Map<String, Object> hospitalPerformance = new HashMap<>();
              hospitalPerformance.put("successRate", successRate);
              hospitalPerformance.put("totalCases", totalCases);
              hospitalPerformance.put("successfulCases", successfulCases);
              hospitalPerformance.put("lastUpdated", System.currentTimeMillis());
              
              db.collection("Sagip")
                .document("hospitalPerformance")
                .collection("hospitalStats")
                .document(hospitalId)
                .set(hospitalPerformance);
          });
    }
    
    // Result classes
    public static class MLRecommendationResult {
        public Hospital bestHospital;
        public List<HospitalMLScore> hospitalScores;
        
        public MLRecommendationResult(Hospital bestHospital, List<HospitalMLScore> hospitalScores) {
            this.bestHospital = bestHospital;
            this.hospitalScores = hospitalScores;
        }
    }
    
    public static class HospitalMLScore {
        public Hospital hospital;
        public double score;
        
        public HospitalMLScore(Hospital hospital, double score) {
            this.hospital = hospital;
            this.score = score;
        }
    }
}

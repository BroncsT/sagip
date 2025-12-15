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
        featureWeights.put("distance", 0.50);
        featureWeights.put("er_availability", 0.50);
    }
    
    private void loadHistoricalData() {
        // Initialize with default rates immediately to prevent null pointer
        historicalSuccessRates = getDefaultSuccessRates();
        
        // Load historical success rates from Firestore
        db.collection("Sagip")
          .document("hospitalPerformance")
          .collection("historicalData")
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              // Update with actual data when loaded
              for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                  String hospitalId = document.getId();
                  Double successRate = document.getDouble("successRate");
                  if (successRate != null) {
                      historicalSuccessRates.put(hospitalId, successRate);
                  }
              }
          })
          .addOnFailureListener(e -> {
              // Already initialized with defaults, just log the error
              android.util.Log.e("MLRecommendation", "Failed to load historical data", e);
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

        // ER Availability feature (based on ER status and ER bed availability)
        features.put("er_availability", getErAvailabilityScore(hospital));
        
        return features;
    }

    private double getErAvailabilityScore(Hospital hospital) {
        // Prefer ER-specific beds if present
        double erBedScore = 0.5;
        if (hospital.emergencyBeds > 0) {
            erBedScore = (double) hospital.availableEmergencyBeds / (double) hospital.emergencyBeds;
        } else if (hospital.totalBeds > 0) {
            erBedScore = hospital.getAvailabilityScore();
        }

        double statusScore = 0.5;
        if (hospital.operationalStatus != null) {
            switch (hospital.operationalStatus.toLowerCase()) {
                case "available":
                case "operational":
                    statusScore = 1.0;
                    break;
                case "busy":
                    statusScore = 0.7;
                    break;
                case "crowded":
                case "overcrowded":
                    statusScore = 0.3;
                    break;
                case "full":
                case "closed":
                    statusScore = 0.0;
                    break;
                default:
                    statusScore = 0.5;
                    break;
            }
        }

        // Combine bed availability and status.
        return Math.min(1.0, Math.max(0.0, (0.5 * erBedScore) + (0.5 * statusScore)));
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

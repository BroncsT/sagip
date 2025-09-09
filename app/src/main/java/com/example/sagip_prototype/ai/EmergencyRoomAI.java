package com.example.sagip_prototype.ai;

import com.example.sagip_prototype.models.Hospital;
import com.example.sagip_prototype.models.Emergency;
import com.example.sagip_prototype.models.RouteOption;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.*;

public class EmergencyRoomAI {
    
    // Callback interfaces for async operations
    public interface HospitalSelectionCallback {
        void onResult(AIRecommendationResult result);
    }
    
    public interface HospitalListCallback {
        void onHospitalsLoaded(List<Hospital> hospitals);
        void onError(String error);
    }
    
    private FirebaseFirestore db;
    private TOPSISAlgorithm topsis;
    private AStarAlgorithm astar;
    private MLRecommendationSystem mlRecommender;
    
    public EmergencyRoomAI(FirebaseFirestore db) {
        this.db = db;
        this.topsis = new TOPSISAlgorithm();
        this.astar = new AStarAlgorithm();
        this.mlRecommender = new MLRecommendationSystem(db);
    }
    
    public void selectOptimalHospital(Emergency emergency, 
                                    double rescuerLat, double rescuerLon,
                                    HospitalSelectionCallback callback) {
        
        // Step 1: Get available hospitals within radius (async)
        getAvailableHospitals(emergency, 15.0, new HospitalListCallback() {
            @Override
            public void onHospitalsLoaded(List<Hospital> availableHospitals) {
                if (availableHospitals.isEmpty()) {
                    callback.onResult(new AIRecommendationResult(null, null, "No hospitals available within 15km radius", 0.0));
                    return;
                }
                
                // Step 2: Apply TOPSIS algorithm
                TOPSISAlgorithm.TOPSISResult topsisResult = topsis.evaluateHospitals(
                    availableHospitals, emergency, rescuerLat, rescuerLon);
                
                // Step 3: Apply ML recommendation
                MLRecommendationSystem.MLRecommendationResult mlResult = mlRecommender.recommendHospital(
                    availableHospitals, emergency, rescuerLat, rescuerLon);
                
                // Step 4: Combine results using hybrid approach
                Hospital bestHospital = combineResults(topsisResult, mlResult, availableHospitals);
                
                // Step 5: Calculate optimal route
                RouteOption optimalRoute = calculateOptimalRoute(
                    rescuerLat, rescuerLon, 
                    bestHospital.location.getLatitude(), bestHospital.location.getLongitude(),
                    emergency);
                
                // Step 6: Calculate confidence score
                double confidenceScore = calculateConfidenceScore(bestHospital, topsisResult, mlResult);
                
                // Step 7: Get alternative options
                List<Hospital> alternatives = getAlternativeHospitals(availableHospitals, bestHospital, 3);
                
                callback.onResult(new AIRecommendationResult(bestHospital, optimalRoute, alternatives, confidenceScore));
            }
            
            @Override
            public void onError(String error) {
                callback.onResult(new AIRecommendationResult(null, null, "Error loading hospitals: " + error, 0.0));
            }
        });
    }
    
    private void getAvailableHospitals(Emergency emergency, double radiusKm, HospitalListCallback callback) {
        List<Hospital> hospitals = new ArrayList<>();
        
        // Query hospitals from Firestore
        db.collection("Sagip")
          .document("users")
          .collection("hospital")
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                  // Create hospital object manually to handle field name differences
                  Hospital hospital = new Hospital();
                  hospital.hospitalId = document.getId();
                  
                  // Map field names from your database structure
                  hospital.name = document.getString("hospitalName");
                  if (hospital.name == null) {
                      hospital.name = document.getString("name");
                  }
                  
                  // Handle location field - try different possible field names
                  com.google.firebase.firestore.GeoPoint location = document.getGeoPoint("currentLocation");
                  if (location == null) {
                      location = document.getGeoPoint("location");
                  }
                  hospital.location = location;
                  
                  // Set other fields
                  hospital.address = document.getString("hospitalAddress");
                  if (hospital.address == null) {
                      hospital.address = document.getString("address");
                  }
                  
                  hospital.phone = document.getString("mobileNumber");
                  if (hospital.phone == null) {
                      hospital.phone = document.getString("phone");
                  }
                  
                  // Set operational status - default to true if not specified
                  Boolean isOperational = document.getBoolean("isOperational");
                  hospital.isOperational = isOperational != null ? isOperational : true;
                  
                  // Only process if we have required fields
                  if (hospital.name != null && hospital.location != null) {
                      // Check if within radius
                      double distance = calculateDistance(
                          emergency.location.getLatitude(), emergency.location.getLongitude(),
                          hospital.location.getLatitude(), hospital.location.getLongitude()
                      );
                      
                      if (distance <= radiusKm) {
                          hospital.distanceFromSenior = distance;
                          hospitals.add(hospital);
                      }
                  }
              }
              
              // Call callback with loaded hospitals
              callback.onHospitalsLoaded(hospitals);
          })
          .addOnFailureListener(e -> {
              // Log error and call callback with error
              System.err.println("Failed to load hospitals: " + e.getMessage());
              callback.onError("Failed to load hospitals: " + e.getMessage());
          });
    }
    
    private Hospital combineResults(TOPSISAlgorithm.TOPSISResult topsisResult, 
                                  MLRecommendationSystem.MLRecommendationResult mlResult,
                                  List<Hospital> hospitals) {
        
        // Create a map of hospital scores
        Map<String, Double> combinedScores = new HashMap<>();
        
        // Add TOPSIS scores (40% weight)
        for (TOPSISAlgorithm.HospitalScore score : topsisResult.hospitalScores) {
            String hospitalId = score.hospital.hospitalId;
            double topsisScore = score.score;
            combinedScores.put(hospitalId, topsisScore * 0.4);
        }
        
        // Add ML scores (60% weight)
        for (MLRecommendationSystem.HospitalMLScore score : mlResult.hospitalScores) {
            String hospitalId = score.hospital.hospitalId;
            double mlScore = score.score;
            double currentScore = combinedScores.getOrDefault(hospitalId, 0.0);
            combinedScores.put(hospitalId, currentScore + (mlScore * 0.6));
        }
        
        // Find hospital with highest combined score
        Hospital bestHospital = null;
        double bestScore = -1.0;
        
        for (Hospital hospital : hospitals) {
            double combinedScore = combinedScores.getOrDefault(hospital.hospitalId, 0.0);
            hospital.finalScore = combinedScore;
            
            if (combinedScore > bestScore) {
                bestScore = combinedScore;
                bestHospital = hospital;
            }
        }
        
        return bestHospital;
    }
    
    private RouteOption calculateOptimalRoute(double startLat, double startLon, 
                                           double endLat, double endLon, Emergency emergency) {
        
        GeoPoint start = new GeoPoint(startLat, startLon);
        GeoPoint end = new GeoPoint(endLat, endLon);
        
        // Create constraints for route optimization
        Map<String, Object> constraints = new HashMap<>();
        constraints.put("emergencyType", emergency.emergencyType);
        constraints.put("severity", emergency.severity);
        constraints.put("weather", "clear"); // Could be fetched from weather API
        constraints.put("timeOfDay", Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        constraints.put("dayOfWeek", Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
        
        // Apply A* algorithm
        AStarAlgorithm.AStarResult astarResult = astar.findOptimalRoute(start, end, constraints);
        
        return astarResult.bestRoute;
    }
    
    private double calculateConfidenceScore(Hospital hospital, 
                                         TOPSISAlgorithm.TOPSISResult topsisResult,
                                         MLRecommendationSystem.MLRecommendationResult mlResult) {
        
        // Get individual scores
        double topsisScore = hospital.topsisScore;
        double mlScore = hospital.mlScore;
        double finalScore = hospital.finalScore;
        
        // Calculate confidence based on score consistency
        double scoreVariance = Math.abs(topsisScore - mlScore);
        double consistencyScore = 1.0 - scoreVariance; // Higher consistency = higher confidence
        
        // Calculate confidence based on final score
        double scoreConfidence = finalScore;
        
        // Calculate confidence based on hospital performance
        double performanceConfidence = hospital.successRate;
        
        // Weighted combination
        double confidence = (
            0.4 * consistencyScore +
            0.4 * scoreConfidence +
            0.2 * performanceConfidence
        );
        
        return Math.min(1.0, Math.max(0.0, confidence));
    }
    
    private List<Hospital> getAlternativeHospitals(List<Hospital> hospitals, Hospital bestHospital, int count) {
        List<Hospital> alternatives = new ArrayList<>();
        
        // Sort hospitals by final score (excluding the best one)
        List<Hospital> sortedHospitals = new ArrayList<>(hospitals);
        sortedHospitals.remove(bestHospital);
        sortedHospitals.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
        
        // Get top alternatives
        for (int i = 0; i < Math.min(count, sortedHospitals.size()); i++) {
            alternatives.add(sortedHospitals.get(i));
        }
        
        return alternatives;
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
    
    // Method to update AI performance after emergency response
    public void updateAIPerformance(String hospitalId, String emergencyId, boolean success, 
                                  double responseTime, double patientSatisfaction) {
        
        // Update ML system with new data
        mlRecommender.updateHistoricalData(hospitalId, success, responseTime, 
                                         "general", patientSatisfaction);
        
        // Log AI decision for analysis
        Map<String, Object> aiLog = new HashMap<>();
        aiLog.put("hospitalId", hospitalId);
        aiLog.put("emergencyId", emergencyId);
        aiLog.put("success", success);
        aiLog.put("responseTime", responseTime);
        aiLog.put("patientSatisfaction", patientSatisfaction);
        aiLog.put("timestamp", System.currentTimeMillis());
        
        db.collection("Sagip")
          .document("aiPerformance")
          .collection("decisionLogs")
          .add(aiLog);
    }
    
    // Result class
    public static class AIRecommendationResult {
        public Hospital recommendedHospital;
        public RouteOption optimalRoute;
        public List<Hospital> alternativeHospitals;
        public double confidenceScore;
        public String message;
        
        public AIRecommendationResult(Hospital recommendedHospital, RouteOption optimalRoute, 
                                    List<Hospital> alternativeHospitals, double confidenceScore) {
            this.recommendedHospital = recommendedHospital;
            this.optimalRoute = optimalRoute;
            this.alternativeHospitals = alternativeHospitals;
            this.confidenceScore = confidenceScore;
            this.message = generateMessage();
        }
        
        public AIRecommendationResult(Hospital recommendedHospital, RouteOption optimalRoute, 
                                    String message, double confidenceScore) {
            this.recommendedHospital = recommendedHospital;
            this.optimalRoute = optimalRoute;
            this.confidenceScore = confidenceScore;
            this.message = message;
            this.alternativeHospitals = new ArrayList<>();
        }
        
        private String generateMessage() {
            if (recommendedHospital == null) {
                return "No suitable hospital found within the specified radius.";
            }
            
            StringBuilder message = new StringBuilder();
            message.append("AI recommends: ").append(recommendedHospital.name).append("\n");
            message.append("Distance: ").append(String.format("%.1f km", recommendedHospital.distanceFromSenior)).append("\n");
            message.append("Confidence: ").append(String.format("%.1f%%", confidenceScore * 100)).append("\n");
            
            if (optimalRoute != null) {
                message.append("Estimated travel time: ").append(optimalRoute.getDurationInMinutes()).append(" minutes");
            }
            
            return message.toString();
        }
        
        public boolean isHighConfidence() {
            return confidenceScore >= 0.8;
        }
        
        public boolean isMediumConfidence() {
            return confidenceScore >= 0.6 && confidenceScore < 0.8;
        }
        
        public boolean isLowConfidence() {
            return confidenceScore < 0.6;
        }
    }
}

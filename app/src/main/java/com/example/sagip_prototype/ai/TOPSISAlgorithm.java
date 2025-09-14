package com.example.sagip_prototype.ai;

import com.example.sagip_prototype.models.Hospital;
import com.example.sagip_prototype.models.Emergency;
import java.util.List;
import java.util.ArrayList;

public class TOPSISAlgorithm {
    
    // Criteria weights for hospital evaluation
    private static final double WEIGHT_DISTANCE = 0.20;
    private static final double WEIGHT_ER_STATUS = 0.25;        // ER status is most important for emergencies
    private static final double WEIGHT_AVAILABILITY = 0.15;
    private static final double WEIGHT_SPECIALIZATION = 0.15;
    private static final double WEIGHT_RESPONSE_TIME = 0.10;
    private static final double WEIGHT_CAPACITY = 0.08;
    private static final double WEIGHT_TRAFFIC = 0.04;
    private static final double WEIGHT_WEATHER = 0.03;
    
    public TOPSISResult evaluateHospitals(List<Hospital> hospitals, Emergency emergency, 
                                        double rescuerLat, double rescuerLon) {
        
        if (hospitals == null || hospitals.isEmpty()) {
            return new TOPSISResult(null, new ArrayList<>());
        }
        
        // Step 1: Create decision matrix
        double[][] decisionMatrix = createDecisionMatrix(hospitals, emergency, rescuerLat, rescuerLon);
        
        // Step 2: Normalize the decision matrix
        double[][] normalizedMatrix = normalizeMatrix(decisionMatrix);
        
        // Step 3: Calculate weighted normalized matrix
        double[] weights = {WEIGHT_DISTANCE, WEIGHT_ER_STATUS, WEIGHT_AVAILABILITY, WEIGHT_SPECIALIZATION, 
                           WEIGHT_RESPONSE_TIME, WEIGHT_CAPACITY, WEIGHT_TRAFFIC, WEIGHT_WEATHER};
        double[][] weightedMatrix = calculateWeightedMatrix(normalizedMatrix, weights);
        
        // Step 4: Determine ideal and negative ideal solutions
        double[] idealSolution = calculateIdealSolution(weightedMatrix);
        double[] negativeIdealSolution = calculateNegativeIdealSolution(weightedMatrix);
        
        // Step 5: Calculate separation measures
        double[] separationFromIdeal = calculateSeparationMeasures(weightedMatrix, idealSolution);
        double[] separationFromNegativeIdeal = calculateSeparationMeasures(weightedMatrix, negativeIdealSolution);
        
        // Step 6: Calculate relative closeness to ideal solution
        double[] relativeCloseness = calculateRelativeCloseness(separationFromIdeal, separationFromNegativeIdeal);
        
        // Step 7: Rank hospitals
        List<HospitalScore> hospitalScores = new ArrayList<>();
        for (int i = 0; i < hospitals.size(); i++) {
            Hospital hospital = hospitals.get(i);
            hospital.topsisScore = relativeCloseness[i];
            hospitalScores.add(new HospitalScore(hospital, relativeCloseness[i]));
        }
        
        // Sort by score (highest first)
        hospitalScores.sort((a, b) -> Double.compare(b.score, a.score));
        
        // Update hospital rankings
        for (int i = 0; i < hospitalScores.size(); i++) {
            hospitalScores.get(i).hospital.ranking = i + 1;
        }
        
        Hospital bestHospital = hospitalScores.isEmpty() ? null : hospitalScores.get(0).hospital;
        
        return new TOPSISResult(bestHospital, hospitalScores);
    }
    
    private double[][] createDecisionMatrix(List<Hospital> hospitals, Emergency emergency, 
                                          double rescuerLat, double rescuerLon) {
        
        int numHospitals = hospitals.size();
        int numCriteria = 8; // distance, er_status, availability, specialization, response_time, capacity, traffic, weather
        
        double[][] matrix = new double[numHospitals][numCriteria];
        
        for (int i = 0; i < numHospitals; i++) {
            Hospital hospital = hospitals.get(i);
            
            // Criterion 1: Distance (lower is better - inverse)
            double distance = calculateDistance(
                emergency.location.getLatitude(), emergency.location.getLongitude(),
                hospital.location.getLatitude(), hospital.location.getLongitude()
            );
            matrix[i][0] = 1.0 / (1.0 + distance); // Inverse distance
            
            // Criterion 2: ER Status (most important for emergencies)
            matrix[i][1] = calculateERStatusScore(hospital);
            
            // Criterion 3: Availability (higher is better)
            matrix[i][2] = hospital.getAvailabilityScore();
            
            // Criterion 4: Specialization match (higher is better)
            matrix[i][3] = calculateSpecializationScore(hospital, emergency.emergencyType);
            
            // Criterion 5: Response time (lower is better - inverse)
            matrix[i][4] = hospital.getResponseTimeScore();
            
            // Criterion 6: Capacity utilization (higher is better)
            matrix[i][5] = hospital.getCapacityScore();
            
            // Criterion 7: Traffic level (lower is better - inverse)
            matrix[i][6] = 1.0 / (1.0 + hospital.trafficLevel);
            
            // Criterion 8: Weather impact (higher is better)
            matrix[i][7] = calculateWeatherScore(hospital.location);
        }
        
        return matrix;
    }
    
    private double[][] normalizeMatrix(double[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        double[][] normalized = new double[rows][cols];
        
        // Calculate column sums for normalization
        double[] columnSums = new double[cols];
        for (int j = 0; j < cols; j++) {
            for (int i = 0; i < rows; i++) {
                columnSums[j] += matrix[i][j] * matrix[i][j];
            }
            columnSums[j] = Math.sqrt(columnSums[j]);
        }
        
        // Normalize each element
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (columnSums[j] != 0) {
                    normalized[i][j] = matrix[i][j] / columnSums[j];
                }
            }
        }
        
        return normalized;
    }
    
    private double[][] calculateWeightedMatrix(double[][] normalizedMatrix, double[] weights) {
        int rows = normalizedMatrix.length;
        int cols = normalizedMatrix[0].length;
        double[][] weighted = new double[rows][cols];
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                weighted[i][j] = normalizedMatrix[i][j] * weights[j];
            }
        }
        
        return weighted;
    }
    
    private double[] calculateIdealSolution(double[][] weightedMatrix) {
        int cols = weightedMatrix[0].length;
        double[] ideal = new double[cols];
        
        for (int j = 0; j < cols; j++) {
            double max = Double.MIN_VALUE;
            for (int i = 0; i < weightedMatrix.length; i++) {
                if (weightedMatrix[i][j] > max) {
                    max = weightedMatrix[i][j];
                }
            }
            ideal[j] = max;
        }
        
        return ideal;
    }
    
    private double[] calculateNegativeIdealSolution(double[][] weightedMatrix) {
        int cols = weightedMatrix[0].length;
        double[] negativeIdeal = new double[cols];
        
        for (int j = 0; j < cols; j++) {
            double min = Double.MAX_VALUE;
            for (int i = 0; i < weightedMatrix.length; i++) {
                if (weightedMatrix[i][j] < min) {
                    min = weightedMatrix[i][j];
                }
            }
            negativeIdeal[j] = min;
        }
        
        return negativeIdeal;
    }
    
    private double[] calculateSeparationMeasures(double[][] weightedMatrix, double[] idealSolution) {
        int rows = weightedMatrix.length;
        double[] separation = new double[rows];
        
        for (int i = 0; i < rows; i++) {
            double sum = 0.0;
            for (int j = 0; j < weightedMatrix[i].length; j++) {
                double diff = weightedMatrix[i][j] - idealSolution[j];
                sum += diff * diff;
            }
            separation[i] = Math.sqrt(sum);
        }
        
        return separation;
    }
    
    private double[] calculateRelativeCloseness(double[] separationFromIdeal, double[] separationFromNegativeIdeal) {
        int length = separationFromIdeal.length;
        double[] relativeCloseness = new double[length];
        
        for (int i = 0; i < length; i++) {
            double denominator = separationFromIdeal[i] + separationFromNegativeIdeal[i];
            if (denominator != 0) {
                relativeCloseness[i] = separationFromNegativeIdeal[i] / denominator;
            }
        }
        
        return relativeCloseness;
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
    
    private double calculateSpecializationScore(Hospital hospital, String emergencyType) {
        if (hospital.specializations == null || emergencyType == null) {
            return 0.5; // Neutral score
        }
        
        // Map emergency types to specializations
        String requiredSpecialization = mapEmergencyTypeToSpecialization(emergencyType);
        
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
    
    private String mapEmergencyTypeToSpecialization(String emergencyType) {
        switch (emergencyType.toLowerCase()) {
            case "cardiac_arrest":
            case "heart_attack":
                return "cardiology";
            case "stroke":
            case "head_injury":
                return "neurology";
            case "trauma":
            case "accident":
                return "trauma";
            case "respiratory":
            case "breathing":
                return "pulmonology";
            case "pediatric":
                return "pediatrics";
            default:
                return "emergency_medicine";
        }
    }
    
    private boolean isRelatedSpecialization(String required, String available) {
        // Define related specializations
        if (required.equals("cardiology") && available.equals("emergency_medicine")) return true;
        if (required.equals("neurology") && available.equals("emergency_medicine")) return true;
        if (required.equals("trauma") && available.equals("emergency_medicine")) return true;
        return false;
    }
    
    private double calculateWeatherScore(com.google.firebase.firestore.GeoPoint location) {
        // Simplified weather score - in real implementation, you'd call a weather API
        // For now, return a neutral score
        return 0.8; // Assume good weather conditions
    }
    
    // Result classes
    public static class TOPSISResult {
        public Hospital bestHospital;
        public List<HospitalScore> hospitalScores;
        
        public TOPSISResult(Hospital bestHospital, List<HospitalScore> hospitalScores) {
            this.bestHospital = bestHospital;
            this.hospitalScores = hospitalScores;
        }
    }
    
    public static class HospitalScore {
        public Hospital hospital;
        public double score;
        
        public HospitalScore(Hospital hospital, double score) {
            this.hospital = hospital;
            this.score = score;
        }
    }
    
    private double calculateERStatusScore(Hospital hospital) {
        if (hospital.operationalStatus == null) {
            return 0.5; // Default score if status unknown
        }
        
        String status = hospital.operationalStatus.toLowerCase();
        switch (status) {
            case "available":
                return 1.0; // Best score - ER is available and ready
            case "busy":
                return 0.6; // Moderate score - ER is busy but operational
            case "overcrowded":
                return 0.3; // Lower score - ER is overcrowded
            default:
                return 0.5; // Default score for unknown status
        }
    }
}

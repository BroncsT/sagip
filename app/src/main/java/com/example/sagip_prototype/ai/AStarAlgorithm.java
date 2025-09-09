package com.example.sagip_prototype.ai;

import com.example.sagip_prototype.models.RouteOption;
import com.google.firebase.firestore.GeoPoint;
import java.util.*;

public class AStarAlgorithm {
    
    private static final double EARTH_RADIUS = 6371.0; // km
    
    public AStarResult findOptimalRoute(GeoPoint start, GeoPoint end, 
                                      Map<String, Object> constraints) {
        
        // Create route options using Google Maps API simulation
        List<RouteOption> routeOptions = generateRouteOptions(start, end, constraints);
        
        // Apply A* optimization to each route
        List<RouteScore> optimizedRoutes = new ArrayList<>();
        
        for (RouteOption route : routeOptions) {
            double astarScore = calculateAStarScore(route, constraints);
            route.astarScore = astarScore;
            optimizedRoutes.add(new RouteScore(route, astarScore));
        }
        
        // Sort by A* score (highest first)
        optimizedRoutes.sort((a, b) -> Double.compare(b.score, a.score));
        
        RouteOption bestRoute = optimizedRoutes.isEmpty() ? null : optimizedRoutes.get(0).route;
        
        return new AStarResult(bestRoute, optimizedRoutes);
    }
    
    private List<RouteOption> generateRouteOptions(GeoPoint start, GeoPoint end, 
                                                 Map<String, Object> constraints) {
        
        List<RouteOption> routes = new ArrayList<>();
        
        // Route 1: Direct route
        RouteOption directRoute = createDirectRoute(start, end);
        routes.add(directRoute);
        
        // Route 2: Highway route (if applicable)
        if (shouldUseHighway(start, end)) {
            RouteOption highwayRoute = createHighwayRoute(start, end);
            routes.add(highwayRoute);
        }
        
        // Route 3: Alternative route
        RouteOption alternativeRoute = createAlternativeRoute(start, end);
        routes.add(alternativeRoute);
        
        // Apply real-time factors
        for (RouteOption route : routes) {
            applyRealTimeFactors(route, constraints);
        }
        
        return routes;
    }
    
    private RouteOption createDirectRoute(GeoPoint start, GeoPoint end) {
        RouteOption route = new RouteOption(start, end);
        route.routeId = "direct_" + System.currentTimeMillis();
        
        // Calculate direct distance
        route.totalDistance = calculateDistance(start, end) * 1000; // Convert to meters
        
        // Estimate duration based on distance and average speed
        double avgSpeed = 30.0; // km/h in city traffic
        route.totalDuration = (int) ((route.totalDistance / 1000.0) / avgSpeed * 3600); // seconds
        
        route.roadTypes = Arrays.asList("arterial", "local");
        route.trafficLights = estimateTrafficLights(route.totalDistance);
        route.intersections = estimateIntersections(route.totalDistance);
        route.roadQuality = 0.8;
        route.hasTolls = false;
        route.hasHighways = false;
        
        return route;
    }
    
    private RouteOption createHighwayRoute(GeoPoint start, GeoPoint end) {
        RouteOption route = new RouteOption(start, end);
        route.routeId = "highway_" + System.currentTimeMillis();
        
        // Highway routes are typically longer but faster
        double directDistance = calculateDistance(start, end);
        route.totalDistance = directDistance * 1.3 * 1000; // 30% longer
        
        // Higher average speed on highways
        double avgSpeed = 60.0; // km/h on highways
        route.totalDuration = (int) ((route.totalDistance / 1000.0) / avgSpeed * 3600);
        
        route.roadTypes = Arrays.asList("highway", "arterial");
        route.trafficLights = 2; // Few traffic lights on highways
        route.intersections = 1; // Few intersections
        route.roadQuality = 0.9;
        route.hasTolls = true;
        route.hasHighways = true;
        
        return route;
    }
    
    private RouteOption createAlternativeRoute(GeoPoint start, GeoPoint end) {
        RouteOption route = new RouteOption(start, end);
        route.routeId = "alternative_" + System.currentTimeMillis();
        
        // Alternative route with different path
        double directDistance = calculateDistance(start, end);
        route.totalDistance = directDistance * 1.2 * 1000; // 20% longer
        
        // Moderate speed
        double avgSpeed = 25.0; // km/h on local roads
        route.totalDuration = (int) ((route.totalDistance / 1000.0) / avgSpeed * 3600);
        
        route.roadTypes = Arrays.asList("local", "arterial");
        route.trafficLights = estimateTrafficLights(route.totalDistance);
        route.intersections = estimateIntersections(route.totalDistance);
        route.roadQuality = 0.7;
        route.hasTolls = false;
        route.hasHighways = false;
        
        return route;
    }
    
    private void applyRealTimeFactors(RouteOption route, Map<String, Object> constraints) {
        
        // Apply traffic conditions
        double trafficMultiplier = getTrafficMultiplier(route, constraints);
        route.trafficDuration = (int) (route.totalDuration * trafficMultiplier);
        route.trafficLevel = Math.min(1.0, (trafficMultiplier - 1.0) * 2.0);
        
        // Apply weather conditions
        double weatherMultiplier = getWeatherMultiplier(constraints);
        route.totalDuration = (int) (route.totalDuration * weatherMultiplier);
        
        // Apply time of day factors
        double timeMultiplier = getTimeOfDayMultiplier(constraints);
        route.totalDuration = (int) (route.totalDuration * timeMultiplier);
        
        // Calculate emergency-specific scores
        route.emergencyScore = calculateEmergencyScore(route, constraints);
        route.hospitalProximityScore = calculateHospitalProximityScore(route, constraints);
        route.accessibilityScore = calculateAccessibilityScore(route);
        route.safetyScore = calculateSafetyScore(route);
    }
    
    private double calculateAStarScore(RouteOption route, Map<String, Object> constraints) {
        
        // Distance score (shorter is better)
        double distanceScore = 1.0 / (1.0 + route.getDistanceInKm() / 10.0);
        
        // Time score (faster is better)
        double timeScore = 1.0 / (1.0 + route.getDurationInMinutes() / 30.0);
        
        // Traffic score (less traffic is better)
        double trafficScore = 1.0 / (1.0 + route.trafficLevel);
        
        // Emergency score (higher is better)
        double emergencyScore = route.emergencyScore;
        
        // Hospital proximity score (higher is better)
        double hospitalScore = route.hospitalProximityScore;
        
        // Accessibility score (higher is better)
        double accessibilityScore = route.accessibilityScore;
        
        // Safety score (higher is better)
        double safetyScore = route.safetyScore;
        
        // Road quality score (higher is better)
        double roadQualityScore = route.roadQuality;
        
        // Weighted combination
        double totalScore = (
            0.20 * distanceScore +
            0.20 * timeScore +
            0.15 * trafficScore +
            0.15 * emergencyScore +
            0.10 * hospitalScore +
            0.10 * accessibilityScore +
            0.05 * safetyScore +
            0.05 * roadQualityScore
        );
        
        return totalScore;
    }
    
    private double getTrafficMultiplier(RouteOption route, Map<String, Object> constraints) {
        // Simulate traffic based on route type and time
        double baseMultiplier = 1.0;
        
        if (route.hasHighways) {
            baseMultiplier += 0.2; // Highways can have traffic
        }
        
        if (route.roadTypes.contains("local")) {
            baseMultiplier += 0.3; // Local roads have more traffic
        }
        
        // Time-based traffic
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        
        if (hour >= 7 && hour <= 9) { // Morning rush
            baseMultiplier += 0.4;
        } else if (hour >= 17 && hour <= 19) { // Evening rush
            baseMultiplier += 0.4;
        } else if (hour >= 12 && hour <= 14) { // Lunch time
            baseMultiplier += 0.2;
        }
        
        return Math.min(2.0, baseMultiplier); // Cap at 2x
    }
    
    private double getWeatherMultiplier(Map<String, Object> constraints) {
        // Simulate weather impact
        String weather = (String) constraints.getOrDefault("weather", "clear");
        
        switch (weather.toLowerCase()) {
            case "rain":
            case "storm":
                return 1.3;
            case "fog":
                return 1.2;
            case "snow":
                return 1.5;
            default:
                return 1.0;
        }
    }
    
    private double getTimeOfDayMultiplier(Map<String, Object> constraints) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        
        // Weekend vs weekday
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return 0.9; // Less traffic on weekends
        }
        
        // Night time
        if (hour >= 22 || hour <= 5) {
            return 0.8; // Less traffic at night
        }
        
        return 1.0; // Normal traffic
    }
    
    private double calculateEmergencyScore(RouteOption route, Map<String, Object> constraints) {
        String emergencyType = (String) constraints.getOrDefault("emergencyType", "general");
        String severity = (String) constraints.getOrDefault("severity", "medium");
        
        double score = 0.5; // Base score
        
        // Severity multiplier
        switch (severity.toLowerCase()) {
            case "critical":
                score = 1.0;
                break;
            case "high":
                score = 0.8;
                break;
            case "medium":
                score = 0.6;
                break;
            case "low":
                score = 0.4;
                break;
        }
        
        // Emergency type considerations
        if (emergencyType.equals("cardiac_arrest") && route.hasHighways) {
            score += 0.2; // Highways better for critical emergencies
        }
        
        return Math.min(1.0, score);
    }
    
    private double calculateHospitalProximityScore(RouteOption route, Map<String, Object> constraints) {
        // Check if route passes near hospitals
        List<GeoPoint> hospitals = (List<GeoPoint>) constraints.get("nearbyHospitals");
        
        if (hospitals == null || hospitals.isEmpty()) {
            return 0.5; // Neutral score
        }
        
        double maxProximityScore = 0.0;
        
        for (GeoPoint hospital : hospitals) {
            double minDistance = Double.MAX_VALUE;
            
            // Check distance to route waypoints
            for (GeoPoint waypoint : route.waypoints) {
                double distance = calculateDistance(waypoint, hospital);
                minDistance = Math.min(minDistance, distance);
            }
            
            // Closer hospitals get higher scores
            double proximityScore = 1.0 / (1.0 + minDistance);
            maxProximityScore = Math.max(maxProximityScore, proximityScore);
        }
        
        return maxProximityScore;
    }
    
    private double calculateAccessibilityScore(RouteOption route) {
        double score = 0.5; // Base score
        
        // Fewer traffic lights and intersections are better
        if (route.trafficLights < 5) score += 0.2;
        if (route.intersections < 10) score += 0.2;
        
        // Good road quality
        score += route.roadQuality * 0.3;
        
        return Math.min(1.0, score);
    }
    
    private double calculateSafetyScore(RouteOption route) {
        double score = 0.5; // Base score
        
        // Highways are generally safer
        if (route.hasHighways) score += 0.2;
        
        // Good road quality
        score += route.roadQuality * 0.2;
        
        // Fewer intersections are safer
        if (route.intersections < 5) score += 0.1;
        
        return Math.min(1.0, score);
    }
    
    private boolean shouldUseHighway(GeoPoint start, GeoPoint end) {
        double distance = calculateDistance(start, end);
        return distance > 5.0; // Use highway for distances > 5km
    }
    
    private int estimateTrafficLights(double distance) {
        return (int) (distance / 1000.0 * 2); // Approximately 2 traffic lights per km
    }
    
    private int estimateIntersections(double distance) {
        return (int) (distance / 1000.0 * 5); // Approximately 5 intersections per km
    }
    
    private double calculateDistance(GeoPoint point1, GeoPoint point2) {
        return calculateDistance(point1.getLatitude(), point1.getLongitude(),
                               point2.getLatitude(), point2.getLongitude());
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
    
    // Result classes
    public static class AStarResult {
        public RouteOption bestRoute;
        public List<RouteScore> routeScores;
        
        public AStarResult(RouteOption bestRoute, List<RouteScore> routeScores) {
            this.bestRoute = bestRoute;
            this.routeScores = routeScores;
        }
    }
    
    public static class RouteScore {
        public RouteOption route;
        public double score;
        
        public RouteScore(RouteOption route, double score) {
            this.route = route;
            this.score = score;
        }
    }
}

package com.example.sagip_prototype;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.firebase.firestore.GeoPoint;

public class EmergencyQueueManager {
    private static final String TAG = "EmergencyQueueManager";
    private static final String EMERGENCY_REQUESTS_COLLECTION = "Sagip/emergencyRequests/activeRequests";
    private static EmergencyQueueManager instance;
    private List<EmergencyRequest> activeEmergencies;
    private FirebaseFirestore db;
    private Context context;
    
    public static synchronized EmergencyQueueManager getInstance(Context context) {
        if (instance == null) {
            instance = new EmergencyQueueManager(context);
        }
        return instance;
    }
    
    private EmergencyQueueManager(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.activeEmergencies = new ArrayList<>();
    }
    
    public static class EmergencyRequest {
        public String requestId;
        public String seniorUid; // Add senior UID field
        public String seniorName;
        public String seniorPhone;
        public String locationAddress;
        public String barangay; // Add barangay field
        public GeoPoint location; // Add location coordinates
        public long timestamp;
        public String status; // "pending", "assigned", "completed"
        public String assignedRescuerId;
        public int priority; // 1 = highest, 5 = lowest
        public String emergencyType; // "medical", "fall", "other"
        
        public EmergencyRequest(String requestId, String seniorUid, String seniorName, String seniorPhone, 
                              String locationAddress, String barangay, long timestamp, String emergencyType) {
            this(requestId, seniorUid, seniorName, seniorPhone, locationAddress, barangay, timestamp, emergencyType, null);
        }
        
        public EmergencyRequest(String requestId, String seniorUid, String seniorName, String seniorPhone, 
                              String locationAddress, String barangay, long timestamp, String emergencyType, GeoPoint location) {
            this.requestId = requestId;
            this.seniorUid = seniorUid;
            this.seniorName = seniorName;
            this.seniorPhone = seniorPhone;
            this.locationAddress = locationAddress;
            this.barangay = barangay;
            this.timestamp = timestamp;
            this.emergencyType = emergencyType;
            this.location = location;
            this.status = "pending";
            this.assignedRescuerId = null;
            this.priority = calculatePriority(emergencyType);
        }
        
        private int calculatePriority(String emergencyType) {
            switch (emergencyType.toLowerCase()) {
                case "medical":
                case "heart_attack":
                case "stroke":
                    return 1; // Highest priority
                case "fall":
                case "injury":
                    return 2;
                case "panic":
                case "anxiety":
                    return 3;
                case "other":
                default:
                    return 4;
            }
        }
    }
    
    public void addEmergencyRequest(EmergencyRequest request) {
        Log.d(TAG, "🚨 Adding emergency request: " + request.seniorName + " (Priority: " + request.priority + ")");
        
        // Check if request already exists
        for (EmergencyRequest existing : activeEmergencies) {
            if (existing.requestId != null && existing.requestId.equals(request.requestId)) {
                Log.d(TAG, "⚠️ Emergency request already exists: " + request.requestId);
                // Update existing request with new data
                existing.seniorName = request.seniorName;
                existing.seniorPhone = request.seniorPhone;
                existing.locationAddress = request.locationAddress;
                existing.timestamp = request.timestamp;
                existing.emergencyType = request.emergencyType;
                existing.status = request.status;
                existing.assignedRescuerId = request.assignedRescuerId;
                existing.priority = request.priority;
                Log.d(TAG, "✅ Updated existing emergency request: " + request.requestId);
                return;
            }
        }
        
        activeEmergencies.add(request);
        sortByFIFO();
        
        // Save to database
        saveEmergencyToDatabase(request);
        
        // Notify rescuers
        notifyRescuersOfNewEmergency(request);
        
        // Notify barangay users
        notifyBarangayUsers(request);
        
        Log.d(TAG, "📊 Total active emergencies: " + activeEmergencies.size());
    }
    
    public void removeEmergencyRequest(String requestId) {
        activeEmergencies.removeIf(request -> request.requestId != null && request.requestId.equals(requestId));
        Log.d(TAG, "✅ Removed emergency request: " + requestId);
        Log.d(TAG, "📊 Remaining active emergencies: " + activeEmergencies.size());
    }
    
    public void assignRescuer(String requestId, String rescuerId) {
        Log.d(TAG, "🔍 [EMERGENCY_QUEUE_MANAGER] Attempting to assign rescuer " + rescuerId + " to emergency: " + requestId);
        Log.d(TAG, "🔍 [EMERGENCY_QUEUE_MANAGER] Active emergencies count: " + activeEmergencies.size());
        
        boolean found = false;
        for (EmergencyRequest request : activeEmergencies) {
            Log.d(TAG, "🔍 Checking emergency: " + request.requestId + " vs " + requestId);
            if (request.requestId != null && request.requestId.equals(requestId)) {
                request.status = "assigned";
                request.assignedRescuerId = rescuerId;
                Log.d(TAG, "👤 Assigned rescuer " + rescuerId + " to emergency: " + request.seniorName);
                
                // Update the database with the assignment
                updateEmergencyInDatabase(request);
                
                // Send notification to senior about rescuer response
                sendRescuerResponseNotificationToSenior(requestId, rescuerId);
                
                found = true;
                break;
            }
        }
        
        if (!found) {
            Log.w(TAG, "⚠️ Emergency not found for assignment: " + requestId);
        }
    }
    
    private void updateEmergencyInDatabase(EmergencyRequest request) {
        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("status", request.status);
        updates.put("assignedRescuerId", request.assignedRescuerId);
        updates.put("assignedAt", System.currentTimeMillis());
        
        // Update emergency assignment in database
        db.collection("Sagip/emergencyRequests/activeRequests").document(request.requestId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "💾 Emergency assignment updated in database: " + request.requestId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to update emergency assignment in database: " + e.getMessage());
                });
    }
    
    private void sendRescuerResponseNotificationToSenior(String requestId, String rescuerId) {
        Log.d(TAG, "📤 [EMERGENCY_QUEUE_MANAGER] Sending rescuer response notification to senior for help request: " + requestId);
        Log.d(TAG, "📤 [EMERGENCY_QUEUE_MANAGER] Rescuer ID: " + rescuerId);
        Log.d(TAG, "📤 [EMERGENCY_QUEUE_MANAGER] Starting notification process...");
        
        // First get the help request details to find the senior information
        String helpRequestPath = "Sagip/emergencyRequests/activeRequests";
        Log.d(TAG, "📤 Querying help request from path: " + helpRequestPath + "/" + requestId);
        
        // Try the correct path structure - should match where we save the data
        db.collection("Sagip")
                .document("emergencyRequests")
                .collection("activeRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Log.d(TAG, "📤 Help request document exists: " + documentSnapshot.exists());
                    if (documentSnapshot.exists()) {
                        String seniorUid = documentSnapshot.getString("seniorUid");
                        String seniorName = documentSnapshot.getString("seniorName");
                        String seniorPhone = documentSnapshot.getString("seniorPhone");
                        String locationAddress = documentSnapshot.getString("locationAddress");
                        
                        // Get senior location for ETA calculation
                        GeoPoint seniorLocation = documentSnapshot.getGeoPoint("location");
                        final double[] seniorLat = {0.0};
                        final double[] seniorLong = {0.0};
                        if (seniorLocation != null) {
                            seniorLat[0] = seniorLocation.getLatitude();
                            seniorLong[0] = seniorLocation.getLongitude();
                            Log.d(TAG, "📍 Senior location found: " + seniorLat[0] + ", " + seniorLong[0]);
                        } else {
                            Log.w(TAG, "⚠️ No senior location found for ETA calculation");
                        }
                        
                        Log.d(TAG, "📤 Help request details - Senior UID: " + seniorUid + ", Name: " + seniorName);
                        Log.d(TAG, "📤 Help request details - Phone: " + seniorPhone + ", Location: " + locationAddress);
                        
                        if (seniorUid != null && !seniorUid.isEmpty()) {
                            // Get actual rescuer information from database
                            getRescuerInfoFromDatabase(rescuerId, new RescuerInfoCallback() {
                                @Override
                                public void onRescuerInfoReceived(String rescuerName, String rescuerPhone, String rescuerTeam) {
                                    Log.d(TAG, "📤 [EMERGENCY_QUEUE_MANAGER] Rescuer info received - Name: " + rescuerName + ", Phone: " + rescuerPhone + ", Team: " + rescuerTeam);
                                    
                                    // Calculate ETA for the notification
                                    calculateETAForNotification(rescuerId, seniorLat[0], seniorLong[0], (etaMinutes, distance) -> {
                                        // Create notification data for senior
                                        Map<String, Object> rescuerResponseNotification = new java.util.HashMap<>();
                                        rescuerResponseNotification.put("type", "RESCUER_RESPONSE");
                                        rescuerResponseNotification.put("title", "🚑 Help is on the way! (Queue Manager)");
                                        
                                        // Create message with ETA
                                        String etaText = etaMinutes > 0 ? String.format("ETA: %.0f min", etaMinutes) : "ETA: Calculating...";
                                        String message = rescuerName + " from " + (rescuerTeam != null ? rescuerTeam : "Rescue Team") + " is responding to your emergency. " + etaText;
                                        rescuerResponseNotification.put("message", message);
                                        
                                        rescuerResponseNotification.put("contactPerson", rescuerName);
                                        rescuerResponseNotification.put("rescuerName", rescuerName);
                                        rescuerResponseNotification.put("rescuerPhone", rescuerPhone);
                                        rescuerResponseNotification.put("rescuerTeam", rescuerTeam);
                                        rescuerResponseNotification.put("requestId", requestId);
                                        rescuerResponseNotification.put("emergency_status", "assigned");
                                        rescuerResponseNotification.put("assigned_rescuer_id", rescuerId);
                                        rescuerResponseNotification.put("locationAddress", locationAddress);
                                        rescuerResponseNotification.put("etaMinutes", etaMinutes);
                                        rescuerResponseNotification.put("distanceKm", distance);
                                        rescuerResponseNotification.put("timestamp", System.currentTimeMillis());
                                        rescuerResponseNotification.put("isRead", false);
                                        rescuerResponseNotification.put("isActive", true);
                                        
                                        // Send notification to senior's notification collection
                                        String notificationPath = "Sagip/users/seniors/" + seniorUid + "/notifications";
                                        Log.d(TAG, "📤 Sending notification to path: " + notificationPath);
                                        Log.d(TAG, "📤 Notification data: " + rescuerResponseNotification.toString());
                                        Log.d(TAG, "📤 Senior UID for notification path: " + seniorUid);
                                        Log.d(TAG, "📤 ETA in notification: " + etaMinutes + " minutes, Distance: " + distance + " km");
                                        
                                        db.collection(notificationPath)
                                                .add(rescuerResponseNotification)
                                                .addOnSuccessListener(documentReference -> {
                                                    Log.d(TAG, "✅ Rescuer response notification sent to senior: " + seniorName);
                                                    Log.d(TAG, "📱 Notification ID: " + documentReference.getId());
                                                    Log.d(TAG, "📱 Notification details - Rescuer: " + rescuerName + ", Phone: " + rescuerPhone + ", Team: " + rescuerTeam);
                                                    Log.d(TAG, "📱 ETA: " + etaMinutes + " minutes, Distance: " + distance + " km");
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "❌ Failed to send rescuer response notification to senior", e);
                                                    Log.e(TAG, "❌ Error details: " + e.getMessage());
                                                });
                                    });
                                }
                            });
                        } else {
                            Log.w(TAG, "⚠️ Senior UID not found in help request: " + requestId);
                        }
                    } else {
                        Log.w(TAG, "⚠️ Help request not found: " + requestId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error getting help request details for notification", e);
                    Log.e(TAG, "❌ Error details: " + e.getMessage());
                });
    }
    
    private String getRescuerName(String rescuerId) {
        // Get rescuer name from database
        return "Rescuer " + rescuerId.substring(0, Math.min(8, rescuerId.length()));
    }
    
    private String getRescuerPhone(String rescuerId) {
        // Get rescuer phone from database
        return "Not available";
    }
    
    private String getRescuerTeam(String rescuerId) {
        // Get rescuer team from database
        return "Emergency Response Team";
    }
    
    // Method to get actual rescuer information from database
    private void getRescuerInfoFromDatabase(String rescuerId, RescuerInfoCallback callback) {
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("rescuegroup");
                        String phone = documentSnapshot.getString("mobileNumber");
                        String team = documentSnapshot.getString("rescuegroup");
                        
                        if (name == null || name.isEmpty()) {
                            name = "Rescuer " + rescuerId.substring(0, Math.min(8, rescuerId.length()));
                        }
                        if (phone == null || phone.isEmpty()) {
                            phone = "Not available";
                        }
                        if (team == null || team.isEmpty()) {
                            team = "Emergency Response Team";
                        }
                        
                        callback.onRescuerInfoReceived(name, phone, team);
                    } else {
                        // Fallback to default values
                        callback.onRescuerInfoReceived(
                            "Rescuer " + rescuerId.substring(0, Math.min(8, rescuerId.length())),
                            "Not available",
                            "Emergency Response Team"
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting rescuer info: " + e.getMessage());
                    // Fallback to default values
                    callback.onRescuerInfoReceived(
                        "Rescuer " + rescuerId.substring(0, Math.min(8, rescuerId.length())),
                        "Not available",
                        "Emergency Response Team"
                    );
                });
    }
    
    // Callback interface for rescuer info
    private interface RescuerInfoCallback {
        void onRescuerInfoReceived(String name, String phone, String team);
    }
    
    // Method to notify barangay users about emergency in their area
    private void notifyBarangayUsers(EmergencyRequest request) {
        if (request.barangay == null || request.barangay.isEmpty()) {
            Log.w(TAG, "⚠️ No barangay information available for emergency: " + request.requestId);
            return;
        }
        
        Log.d(TAG, "🏘️ Notifying barangay users for emergency in: " + request.barangay);
        Log.d(TAG, "🏘️ Emergency details - Senior: " + request.seniorName + ", Request ID: " + request.requestId);
        
        // Find all barangay users in the same barangay
        db.collection("Sagip")
                .document("users")
                .collection("barangay")
                .whereEqualTo("barangayName", request.barangay)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "🏘️ Found " + querySnapshot.size() + " barangay users in " + request.barangay);
                    
                    if (querySnapshot.isEmpty()) {
                        Log.w(TAG, "⚠️ No barangay users found for barangay: " + request.barangay);
                        Log.d(TAG, "🏘️ Query was: whereEqualTo('barangayName', '" + request.barangay + "')");
                    }
                    
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String barangayUserId = document.getId();
                        String barangayName = document.getString("barangayName");
                        String contactPerson = document.getString("contactPerson");
                        
                        // Create notification for barangay user
                        Map<String, Object> barangayNotification = new java.util.HashMap<>();
                        barangayNotification.put("type", "EMERGENCY_ALERT");
                        barangayNotification.put("title", "🚨 Emergency Alert in " + request.barangay);
                        barangayNotification.put("message", "Senior " + request.seniorName + " needs emergency assistance in your barangay");
                        barangayNotification.put("seniorName", request.seniorName);
                        barangayNotification.put("seniorPhone", request.seniorPhone);
                        barangayNotification.put("locationAddress", request.locationAddress);
                        barangayNotification.put("barangay", request.barangay);
                        barangayNotification.put("requestId", request.requestId);
                        barangayNotification.put("emergencyType", request.emergencyType);
                        barangayNotification.put("timestamp", System.currentTimeMillis());
                        barangayNotification.put("isRead", false);
                        barangayNotification.put("isActive", true);
                        
                        // Send notification to barangay user
                        String notificationPath = "Sagip/users/barangay/" + barangayUserId + "/notifications";
                        Log.d(TAG, "🏘️ Sending barangay notification to: " + contactPerson + " (" + barangayUserId + ")");
                        Log.d(TAG, "🏘️ Notification path: " + notificationPath);
                        
                        db.collection(notificationPath)
                                .add(barangayNotification)
                                .addOnSuccessListener(documentReference -> {
                                    Log.d(TAG, "✅ Barangay notification sent to: " + contactPerson + " in " + barangayName);
                                    Log.d(TAG, "✅ Notification document ID: " + documentReference.getId());
                                    
                                    // Also send FCM notification for background delivery
                                    sendFCMNotificationToBarangay(barangayUserId, barangayNotification);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ Failed to send barangay notification to: " + contactPerson, e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error finding barangay users for: " + request.barangay, e);
                });
    }
    
    // Test method to debug barangay notifications
    public void testBarangayNotification(String testBarangay) {
        Log.d(TAG, "🧪 Testing barangay notification for: " + testBarangay);
        
        // Create a test emergency request
        EmergencyRequest testRequest = new EmergencyRequest(
                "TEST_" + System.currentTimeMillis(),
                "test_senior_uid",
                "Test Senior",
                "1234567890",
                "Test Location",
                testBarangay,
                System.currentTimeMillis(),
                "medical"
        );
        
        // Test the notification
        notifyBarangayUsers(testRequest);
    }
    
    
    private String extractUserIdFromRequestId(String requestId) {
        // Request ID format: "SOS_timestamp_userId"
        String[] parts = requestId.split("_");
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }
    
    public List<EmergencyRequest> getActiveEmergencies() {
        return new ArrayList<>(activeEmergencies);
    }
    
    public List<EmergencyRequest> getPendingEmergencies() {
        List<EmergencyRequest> pending = new ArrayList<>();
        for (EmergencyRequest request : activeEmergencies) {
            if ("pending".equals(request.status)) {
                pending.add(request);
            }
        }
        return pending;
    }
    
    public EmergencyRequest getNextEmergency() {
        if (activeEmergencies.isEmpty()) {
            return null;
        }
        return activeEmergencies.get(0); // First item after FIFO sorting
    }
    
    public EmergencyRequest getEmergencyById(String requestId) {
        for (EmergencyRequest request : activeEmergencies) {
            if (request.requestId != null && request.requestId.equals(requestId)) {
                return request;
            }
        }
        return null;
    }
    
    public void loadEmergencyByIdFromDatabase(String requestId, EmergencyLoadCallback callback) {
        db.collection("Sagip/emergencyRequests/activeRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> data = documentSnapshot.getData();
                        if (data != null) {
                            EmergencyRequest request = new EmergencyRequest(
                                    (String) data.get("requestId"),
                                    (String) data.get("seniorUid"),
                                    (String) data.get("seniorName"),
                                    (String) data.get("seniorPhone"),
                                    (String) data.get("locationAddress"),
                                    (String) data.get("barangay"),
                                    data.get("timestamp") != null ? ((Long) data.get("timestamp")) : System.currentTimeMillis(),
                                    data.get("emergencyType") != null ? (String) data.get("emergencyType") : "medical"
                            );
                            request.status = (String) data.get("status");
                            request.assignedRescuerId = (String) data.get("assignedRescuerId");
                            // Handle null priority field
                            Long priorityLong = (Long) data.get("priority");
                            if (priorityLong != null) {
                                request.priority = priorityLong.intValue();
                            } else {
                                request.priority = 4; // Default priority
                            }
                            
                            // Add to local queue if not already there
                            addEmergencyRequest(request);
                            
                            callback.onEmergencyLoaded(request);
                        } else {
                            callback.onEmergencyLoaded(null);
                        }
                    } else {
                        callback.onEmergencyLoaded(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load emergency from database: " + e.getMessage());
                    callback.onEmergencyLoaded(null);
                });
    }
    
    public interface EmergencyLoadCallback {
        void onEmergencyLoaded(EmergencyRequest emergency);
    }
    
    public void loadEmergencyByRequestIdFromDatabase(String requestId, EmergencyLoadCallback callback) {
        db.collection("Sagip/emergencyRequests/activeRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> data = documentSnapshot.getData();
                        if (data != null) {
                            EmergencyRequest request = new EmergencyRequest(
                                    (String) data.get("requestId"),
                                    (String) data.get("seniorUid"),
                                    (String) data.get("seniorName"),
                                    (String) data.get("seniorPhone"),
                                    (String) data.get("locationAddress"),
                                    (String) data.get("barangay"),
                                    data.get("timestamp") != null ? ((Long) data.get("timestamp")) : System.currentTimeMillis(),
                                    data.get("emergencyType") != null ? (String) data.get("emergencyType") : "medical"
                            );
                            request.status = (String) data.get("status");
                            request.assignedRescuerId = (String) data.get("assignedRescuerId");
                            // Handle null priority field
                            Long priorityLong = (Long) data.get("priority");
                            if (priorityLong != null) {
                                request.priority = priorityLong.intValue();
                            } else {
                                request.priority = 4; // Default priority
                            }
                            
                            // Add to local queue if not already there
                            addEmergencyRequest(request);
                            
                            callback.onEmergencyLoaded(request);
                        } else {
                            callback.onEmergencyLoaded(null);
                        }
                    } else {
                        callback.onEmergencyLoaded(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load emergency from database: " + e.getMessage());
                    callback.onEmergencyLoaded(null);
                });
    }
    
    public void getEmergencyStatusFromDatabase(String requestId, EmergencyStatusCallback callback) {
        db.collection("Sagip/emergencyRequests/activeRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> data = documentSnapshot.getData();
                        if (data != null) {
                            String status = (String) data.get("status");
                            String assignedRescuerId = (String) data.get("assignedRescuerId");
                            Long assignedAt = (Long) data.get("assignedAt");
                            
                            callback.onStatusLoaded(status, assignedRescuerId, assignedAt);
                        } else {
                            callback.onStatusLoaded(null, null, null);
                        }
                    } else {
                        callback.onStatusLoaded(null, null, null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load emergency status from database: " + e.getMessage());
                    callback.onStatusLoaded(null, null, null);
                });
    }
    
    public interface EmergencyStatusCallback {
        void onStatusLoaded(String status, String assignedRescuerId, Long assignedAt);
    }
    
    private void sortByFIFO() {
        Collections.sort(activeEmergencies, new Comparator<EmergencyRequest>() {
            @Override
            public int compare(EmergencyRequest a, EmergencyRequest b) {
                // FIFO: Sort only by timestamp (oldest first)
                return Long.compare(a.timestamp, b.timestamp);
            }
        });
    }
    
    private void saveEmergencyToDatabase(EmergencyRequest request) {
        Map<String, Object> emergencyData = new java.util.HashMap<>();
        emergencyData.put("requestId", request.requestId);
        emergencyData.put("seniorUid", request.seniorUid);
        emergencyData.put("seniorName", request.seniorName);
        emergencyData.put("seniorPhone", request.seniorPhone);
        emergencyData.put("locationAddress", request.locationAddress);
        emergencyData.put("barangay", request.barangay);
        emergencyData.put("timestamp", request.timestamp);
        emergencyData.put("status", request.status);
        emergencyData.put("priority", request.priority);
        emergencyData.put("emergencyType", request.emergencyType);
        emergencyData.put("assignedRescuerId", request.assignedRescuerId);
        
        // Add senior location coordinates if available
        if (request.location != null) {
            emergencyData.put("location", request.location);
            Log.d(TAG, "📍 Saving senior location to database: " + request.location.getLatitude() + ", " + request.location.getLongitude());
            Log.d(TAG, "📍 Emergency data being saved: " + emergencyData.toString());
        } else {
            Log.w(TAG, "⚠️ No senior location coordinates available for emergency: " + request.requestId);
            Log.w(TAG, "⚠️ Emergency data being saved without location: " + emergencyData.toString());
        }
        
        db.collection("Sagip/emergencyRequests/activeRequests")
                .document(request.requestId)
                .set(emergencyData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Emergency request saved to database: " + request.requestId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save emergency request: " + e.getMessage());
                });
    }
    
    private void notifyRescuersOfNewEmergency(EmergencyRequest request) {
        // Query all rescuers
        db.collection("Sagip/users/rescuer")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String rescuerId = document.getId();
                        sendEmergencyNotificationToRescuer(rescuerId, request);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to query rescuers: " + e.getMessage());
                });
    }
    
    private void sendEmergencyNotificationToRescuer(String rescuerId, EmergencyRequest request) {
        Map<String, Object> notificationData = new java.util.HashMap<>();
        notificationData.put("type", "EMERGENCY_SOS");
        notificationData.put("title", "🚨 EMERGENCY SOS - " + request.seniorName);
        notificationData.put("message", "Senior needs immediate help!");
        notificationData.put("seniorName", request.seniorName);
        notificationData.put("seniorPhone", request.seniorPhone);
        notificationData.put("locationAddress", request.locationAddress);
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("isRead", false);
        notificationData.put("requestId", request.requestId);
        notificationData.put("priority", request.priority);
        notificationData.put("emergencyType", request.emergencyType);
        
        db.collection("Sagip/users/rescuer/" + rescuerId + "/emergencyNotifications")
                .add(notificationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "📤 Emergency notification sent to rescuer: " + rescuerId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to send notification to rescuer " + rescuerId + ": " + e.getMessage());
                });
    }
    
    public void loadActiveEmergenciesFromDatabase() {
        db.collection("Sagip/emergencyRequests/activeRequests")
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    activeEmergencies.clear();
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Map<String, Object> data = document.getData();
                        EmergencyRequest request = new EmergencyRequest(
                                data.get("requestId") != null ? (String) data.get("requestId") : "unknown_" + System.currentTimeMillis(),
                                data.get("seniorUid") != null ? (String) data.get("seniorUid") : "unknown_senior",
                                data.get("seniorName") != null ? (String) data.get("seniorName") : "Unknown Senior",
                                data.get("seniorPhone") != null ? (String) data.get("seniorPhone") : "Not available",
                                data.get("locationAddress") != null ? (String) data.get("locationAddress") : "Unknown location",
                                data.get("barangay") != null ? (String) data.get("barangay") : "Unknown barangay",
                                data.get("timestamp") != null ? ((Long) data.get("timestamp")) : System.currentTimeMillis(),
                                data.get("emergencyType") != null ? (String) data.get("emergencyType") : "medical"
                        );
                        request.status = (String) data.get("status");
                        request.assignedRescuerId = (String) data.get("assignedRescuerId");
                        
                        // Handle null priority field
                        Long priorityLong = (Long) data.get("priority");
                        if (priorityLong != null) {
                            request.priority = priorityLong.intValue();
                        } else {
                            request.priority = 4; // Default priority
                        }
                        activeEmergencies.add(request);
                    }
                    sortByFIFO();
                    Log.d(TAG, "📊 Loaded " + activeEmergencies.size() + " active emergencies from database");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load active emergencies: " + e.getMessage());
                });
    }
    
    // FCM Notification Methods for Background Delivery
    private void sendFCMNotificationToBarangay(String barangayUserId, Map<String, Object> notificationData) {
        Log.d(TAG, "📱 Sending FCM notification to barangay user: " + barangayUserId);
        
        // Get FCM token for the barangay user
        db.collection("Sagip")
                .document("users")
                .collection("barangay")
                .document(barangayUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String fcmToken = documentSnapshot.getString("fcmToken");
                        if (fcmToken != null && !fcmToken.isEmpty()) {
                            Log.d(TAG, "📱 FCM Token found for barangay user: " + fcmToken.substring(0, Math.min(20, fcmToken.length())) + "...");
                            // TODO: Send FCM message using Firebase Admin SDK or HTTP API
                            // For now, we'll log that we would send it
                            Log.d(TAG, "📱 Would send FCM notification with data: " + notificationData);
                        } else {
                            Log.w(TAG, "⚠️ No FCM token found for barangay user: " + barangayUserId);
                        }
                    } else {
                        Log.w(TAG, "⚠️ Barangay user document not found: " + barangayUserId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error getting FCM token for barangay user: " + barangayUserId, e);
                });
    }
    
    private void sendFCMNotificationToSenior(String seniorUserId, Map<String, Object> notificationData) {
        Log.d(TAG, "📱 Sending FCM notification to senior user: " + seniorUserId);
        
        // Get FCM token for the senior user
        db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .document(seniorUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String fcmToken = documentSnapshot.getString("fcmToken");
                        if (fcmToken != null && !fcmToken.isEmpty()) {
                            Log.d(TAG, "📱 FCM Token found for senior user: " + fcmToken.substring(0, Math.min(20, fcmToken.length())) + "...");
                            // TODO: Send FCM message using Firebase Admin SDK or HTTP API
                            // For now, we'll log that we would send it
                            Log.d(TAG, "📱 Would send FCM notification with data: " + notificationData);
                        } else {
                            Log.w(TAG, "⚠️ No FCM token found for senior user: " + seniorUserId);
                        }
                    } else {
                        Log.w(TAG, "⚠️ Senior user document not found: " + seniorUserId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error getting FCM token for senior user: " + seniorUserId, e);
                });
    }
    
    private void addHospitalInfoToNotification(Map<String, Object> notification, String rescuerId, String requestId) {
        Log.d(TAG, "🏥 Adding hospital information to notification for rescuer: " + rescuerId);
        
        // Try to get hospital information from rescuer's active assignment
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .collection("activeAssignments")
                .whereEqualTo("emergencyId", requestId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot assignmentDoc = querySnapshot.getDocuments().get(0);
                        String hospitalId = assignmentDoc.getString("hospitalId");
                        String hospitalName = assignmentDoc.getString("hospitalName");
                        String hospitalAddress = assignmentDoc.getString("hospitalAddress");
                        String hospitalPhone = assignmentDoc.getString("hospitalPhone");
                        
                        if (hospitalId != null && !hospitalId.isEmpty()) {
                            Log.d(TAG, "✅ Found hospital assignment: " + hospitalName);
                            
                            // Add hospital information to notification
                            notification.put("hospitalId", hospitalId);
                            notification.put("hospitalName", hospitalName != null ? hospitalName : "Hospital");
                            notification.put("hospitalAddress", hospitalAddress != null ? hospitalAddress : "Address not available");
                            notification.put("hospitalPhone", hospitalPhone != null ? hospitalPhone : "Contact hospital directly");
                            
                            Log.d(TAG, "🏥 Hospital info added to notification: " + hospitalName + " at " + hospitalAddress);
                        } else {
                            Log.d(TAG, "⚠️ Assignment found but no hospital assigned");
                            addDefaultHospitalInfo(notification);
                        }
                    } else {
                        Log.d(TAG, "⚠️ No active assignment found for rescuer, using default hospital info");
                        addDefaultHospitalInfo(notification);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading hospital assignment: " + e.getMessage());
                    addDefaultHospitalInfo(notification);
                });
    }
    
    private void addDefaultHospitalInfo(Map<String, Object> notification) {
        // Add default hospital information if no assignment found
        notification.put("hospitalId", "default");
        notification.put("hospitalName", "Emergency Response Hospital");
        notification.put("hospitalAddress", "Will be determined by AI");
        notification.put("hospitalPhone", "Contact emergency services");

        Log.d(TAG, "🏥 Added default hospital info to notification");
    }
    
    private void selectAndAssignBestHospital(EmergencyRequest request, String rescuerId) {
        Log.d(TAG, "🏥 Selecting best hospital for emergency: " + request.requestId);
        
        // Get senior's location from the database
        db.collection("Sagip")
                .document("emergencyRequests")
                .collection("activeRequests")
                .document(request.requestId)
                .get()
                .addOnSuccessListener(emergencyDoc -> {
                    if (!emergencyDoc.exists()) {
                        Log.w(TAG, "⚠️ Emergency document not found for hospital selection");
                        return;
                    }
                    
                    GeoPoint seniorLocation = emergencyDoc.getGeoPoint("location");
                    if (seniorLocation == null) {
                        Log.w(TAG, "⚠️ No senior location available for hospital selection");
                        return;
                    }
                    
                    double seniorLat = seniorLocation.getLatitude();
                    double seniorLong = seniorLocation.getLongitude();
                    Log.d(TAG, "📍 Senior location for hospital selection: " + seniorLat + ", " + seniorLong);
                    
                    // Find the nearest available hospital
                    db.collection("Sagip")
                            .document("hospitals")
                            .collection("hospitals")
                            .whereEqualTo("status", "available")
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                Log.d(TAG, "🏥 Found " + querySnapshot.size() + " available hospitals for AI selection");
                                
                                final String[] bestHospitalId = {null};
                                final String[] bestHospitalName = {null};
                                final String[] bestHospitalAddress = {null};
                                final String[] bestHospitalPhone = {null};
                                final double[] minDistance = {Double.MAX_VALUE};
                                
                                for (DocumentSnapshot doc : querySnapshot) {
                                    GeoPoint hospitalLocation = doc.getGeoPoint("location");
                                    if (hospitalLocation != null) {
                                        double distance = calculateDistance(
                                            seniorLat, seniorLong,
                                            hospitalLocation.getLatitude(), hospitalLocation.getLongitude()
                                        );
                                        
                                        if (distance < minDistance[0]) {
                                            minDistance[0] = distance;
                                            bestHospitalId[0] = doc.getId();
                                            bestHospitalName[0] = doc.getString("name");
                                            bestHospitalAddress[0] = doc.getString("address");
                                            bestHospitalPhone[0] = doc.getString("phone");
                                        }
                                    }
                                }
                                
                                if (bestHospitalId[0] != null) {
                                    Log.d(TAG, "✅ AI selected best hospital: " + bestHospitalName[0] + " (Distance: " + String.format("%.2f km", minDistance[0]) + ")");
                                    
                                    // Save hospital assignment to rescuer's activeAssignments
                                    Map<String, Object> assignmentData = new HashMap<>();
                                    assignmentData.put("emergencyId", request.requestId);
                                    assignmentData.put("hospitalId", bestHospitalId[0]);
                                    assignmentData.put("hospitalName", bestHospitalName[0] != null ? bestHospitalName[0] : "Hospital");
                                    assignmentData.put("hospitalAddress", bestHospitalAddress[0] != null ? bestHospitalAddress[0] : "Address not available");
                                    assignmentData.put("hospitalPhone", bestHospitalPhone[0] != null ? bestHospitalPhone[0] : "Contact hospital directly");
                                    assignmentData.put("assignedAt", System.currentTimeMillis());
                                    assignmentData.put("status", "active");
                                    
                                    db.collection("Sagip")
                                            .document("users")
                                            .collection("rescuer")
                                            .document(rescuerId)
                                            .collection("activeAssignments")
                                            .document(request.requestId)
                                            .set(assignmentData)
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "✅ Hospital assignment saved to rescuer's activeAssignments: " + bestHospitalName[0]);
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "❌ Failed to save hospital assignment: " + e.getMessage());
                                            });
                                } else {
                                    Log.w(TAG, "⚠️ No available hospitals found for AI selection");
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ Error selecting hospital: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading emergency for hospital selection: " + e.getMessage());
                });
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // Distance in km
    }
    
    private void calculateETAForNotification(String rescuerId, double seniorLat, double seniorLong, ETACallback callback) {
        Log.d(TAG, "🔄 Calculating ETA for notification - Senior: " + seniorLat + ", " + seniorLong);
        
        // Get rescuer's current location from database
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        GeoPoint rescuerLocation = documentSnapshot.getGeoPoint("currentLocation");
                        if (rescuerLocation != null) {
                            double rescuerLat = rescuerLocation.getLatitude();
                            double rescuerLong = rescuerLocation.getLongitude();
                            
                            Log.d(TAG, "📍 Rescuer location found: " + rescuerLat + ", " + rescuerLong);
                            
                            // Calculate distance
                            double distance = calculateDistance(seniorLat, seniorLong, rescuerLat, rescuerLong);
                            
                            // Estimate travel time based on distance
                            // Assuming average speed of 30 km/h in urban areas
                            double etaMinutes = (distance / 30.0) * 60;
                            
                            Log.d(TAG, "📊 ETA calculation - Distance: " + String.format("%.1f km", distance) + 
                                  ", ETA: " + String.format("%.0f min", etaMinutes));
                            
                            callback.onETACalculated(etaMinutes, distance);
                        } else {
                            Log.w(TAG, "⚠️ No rescuer location found, using default ETA");
                            callback.onETACalculated(0, 0); // Will show "ETA: Calculating..."
                        }
                    } else {
                        Log.w(TAG, "⚠️ Rescuer document not found, using default ETA");
                        callback.onETACalculated(0, 0); // Will show "ETA: Calculating..."
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error getting rescuer location for ETA: " + e.getMessage());
                    callback.onETACalculated(0, 0); // Will show "ETA: Calculating..."
                });
    }
    
    // Callback interface for ETA calculation
    private interface ETACallback {
        void onETACalculated(double etaMinutes, double distanceKm);
    }
}

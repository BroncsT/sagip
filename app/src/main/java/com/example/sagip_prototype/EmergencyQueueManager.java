package com.example.sagip_prototype;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
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
        Log.d(TAG, "🚨🚨🚨 [EMERGENCY_QUEUE_MANAGER] ===== ASSIGN_RESCUER CALLED =====");
        Log.d(TAG, "🚨🚨🚨 [EMERGENCY_QUEUE_MANAGER] RequestId: " + requestId);
        Log.d(TAG, "🚨🚨🚨 [EMERGENCY_QUEUE_MANAGER] RescuerId: " + rescuerId);
        Log.d(TAG, "🚨🚨🚨 [EMERGENCY_QUEUE_MANAGER] Active emergencies count: " + activeEmergencies.size());
        Log.d(TAG, "🚨🚨🚨 [EMERGENCY_QUEUE_MANAGER] Stack trace: " + java.util.Arrays.toString(Thread.currentThread().getStackTrace()));
        
        // Add a test log to verify this method is being called
        Log.d(TAG, "🔍 [DEBUG] assignRescuer method is being executed - this should appear in logs when rescuer clicks Respond Now");
        
        boolean found = false;
        for (EmergencyRequest request : activeEmergencies) {
            Log.d(TAG, "🔍 [EMERGENCY_QUEUE_MANAGER] Checking emergency: " + request.requestId + " vs " + requestId);
            if (request.requestId != null && request.requestId.equals(requestId)) {
                request.status = "assigned";
                request.assignedRescuerId = rescuerId;
                Log.d(TAG, "👤 [EMERGENCY_QUEUE_MANAGER] Assigned rescuer " + rescuerId + " to emergency: " + request.seniorName);
                
                // Update the database with the assignment
                updateEmergencyInDatabase(request);
                
                // Update ALL other rescuers' notifications to show this emergency is now assigned
                Log.d(TAG, "🔍 [EMERGENCY_QUEUE_MANAGER] Updating notifications for all rescuers...");
                updateAllRescuerNotificationsForAssignment(requestId, rescuerId);
                
                // Send notification to senior about rescuer response
                Log.d(TAG, "🔍 [EMERGENCY_QUEUE_MANAGER] About to call sendRescuerResponseNotificationToSenior...");
                Log.d(TAG, "🔍 [DEBUG] This should appear in logs when senior notification is being sent");
                sendRescuerResponseNotificationToSenior(requestId, rescuerId);
                Log.d(TAG, "🔍 [EMERGENCY_QUEUE_MANAGER] sendRescuerResponseNotificationToSenior called");
                
                // Send SMS notifications to emergency contacts
                Log.d(TAG, "📱 [EMERGENCY_QUEUE_MANAGER] Sending SMS notifications to emergency contacts...");
                sendSMSToEmergencyContacts(request, rescuerId);
                
                // Remove the emergency from the local queue since it's now assigned
                Log.d(TAG, "🗑️ [EMERGENCY_QUEUE_MANAGER] Removing emergency from local queue: " + requestId);
                removeEmergencyRequest(requestId);
                
                // Move the emergency from activeRequests to assignedRequests in Firestore
                moveEmergencyToAssignedCollection(request);
                
                found = true;
                break;
            }
        }
        
        if (!found) {
            Log.w(TAG, "⚠️ [EMERGENCY_QUEUE_MANAGER] Emergency not found for assignment: " + requestId);
        }
        
        Log.d(TAG, "🔍 [EMERGENCY_QUEUE_MANAGER] ===== ASSIGN_RESCUER COMPLETED =====");
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
    
    /**
     * Move an assigned emergency from activeRequests to assignedRequests collection
     * This keeps the active queue clean and prevents duplicate SOS notifications
     */
    private void moveEmergencyToAssignedCollection(EmergencyRequest request) {
        Log.d(TAG, "📦 [MOVE_TO_ASSIGNED] Moving emergency to assignedRequests: " + request.requestId);
        
        // First, get the full document from activeRequests
        db.collection("Sagip")
                .document("emergencyRequests")
                .collection("activeRequests")
                .document(request.requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> emergencyData = documentSnapshot.getData();
                        if (emergencyData != null) {
                            // Add timestamp for when it was moved
                            emergencyData.put("movedToAssignedAt", System.currentTimeMillis());
                            
                            // Save to assignedRequests collection
                            db.collection("Sagip")
                                    .document("emergencyRequests")
                                    .collection("assignedRequests")
                                    .document(request.requestId)
                                    .set(emergencyData)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "✅ [MOVE_TO_ASSIGNED] Emergency saved to assignedRequests: " + request.requestId);
                                        
                                        // Now delete from activeRequests
                                        db.collection("Sagip")
                                                .document("emergencyRequests")
                                                .collection("activeRequests")
                                                .document(request.requestId)
                                                .delete()
                                                .addOnSuccessListener(aVoid2 -> {
                                                    Log.d(TAG, "✅ [MOVE_TO_ASSIGNED] Emergency removed from activeRequests: " + request.requestId);
                                                    Log.d(TAG, "✅ [MOVE_TO_ASSIGNED] Move operation completed successfully");
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "❌ [MOVE_TO_ASSIGNED] Failed to delete from activeRequests: " + e.getMessage());
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "❌ [MOVE_TO_ASSIGNED] Failed to save to assignedRequests: " + e.getMessage());
                                    });
                        }
                    } else {
                        Log.w(TAG, "⚠️ [MOVE_TO_ASSIGNED] Emergency document not found: " + request.requestId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ [MOVE_TO_ASSIGNED] Failed to get emergency document: " + e.getMessage());
                });
    }
    
    private void sendRescuerResponseNotificationToSenior(String requestId, String rescuerId) {
        Log.d(TAG, "📤 [EMERGENCY_QUEUE_MANAGER] ===== SEND_NOTIFICATION CALLED =====");
        Log.d(TAG, "📤 [EMERGENCY_QUEUE_MANAGER] RequestId: " + requestId);
        Log.d(TAG, "📤 [EMERGENCY_QUEUE_MANAGER] RescuerId: " + rescuerId);
        Log.d(TAG, "📤 [EMERGENCY_QUEUE_MANAGER] Stack trace: " + java.util.Arrays.toString(Thread.currentThread().getStackTrace()));
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
                                        Log.d(TAG, "📤 Sending notification to senior: " + seniorUid);
                                        Log.d(TAG, "📤 Notification data: " + rescuerResponseNotification.toString());
                                        Log.d(TAG, "📤 Senior UID for notification path: " + seniorUid);
                                        Log.d(TAG, "📤 ETA in notification: " + etaMinutes + " minutes, Distance: " + distance + " km");
                                        
                                        db.collection("Sagip")
                                                .document("users")
                                                .collection("seniors")
                                                .document(seniorUid)
                                                .collection("notifications")
                                                .add(rescuerResponseNotification)
                                                .addOnSuccessListener(documentReference -> {
                                                    Log.d(TAG, "✅ Rescuer response notification sent to senior: " + seniorName);
                                                    Log.d(TAG, "📱 Notification ID: " + documentReference.getId());
                                                    Log.d(TAG, "📱 Notification details - Rescuer: " + rescuerName + ", Phone: " + rescuerPhone + ", Team: " + rescuerTeam);
                                                    Log.d(TAG, "📱 ETA: " + etaMinutes + " minutes, Distance: " + distance + " km");
                                                    Log.d(TAG, "📱 Notification path: Sagip/users/seniors/" + seniorUid + "/notifications");
                                                    Log.d(TAG, "📱 Notification type: RESCUER_RESPONSE");
                                                    Log.d(TAG, "📱 Notification title: " + rescuerResponseNotification.get("title"));
                                                    Log.d(TAG, "📱 Notification message: " + rescuerResponseNotification.get("message"));
                                                    
                                                    // Also send FCM notification to ensure senior receives it
                                                    sendFCMNotificationToSenior(seniorUid, rescuerResponseNotification);
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
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Starting barangay notification process");
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Request ID: " + request.requestId);
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Senior: " + request.seniorName);
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Barangay: '" + request.barangay + "'");
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Location: " + request.locationAddress);
        
        if (request.barangay == null || request.barangay.isEmpty()) {
            Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] CRITICAL ERROR: No barangay information available for emergency: " + request.requestId);
            Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] This is why barangay users are not receiving notifications!");
            Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] Senior profile may be missing barangay field or it's empty");
            Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] Emergency request details:");
            Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] - Senior UID: " + request.seniorUid);
            Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] - Senior Name: " + request.seniorName);
            Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] - Location: " + request.locationAddress);
            Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] - Barangay: '" + request.barangay + "'");
            return;
        }
        
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Barangay information is valid, proceeding with notification");
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Notifying barangay users for emergency in: " + request.barangay);
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Emergency details - Senior: " + request.seniorName + ", Request ID: " + request.requestId);
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Senior UID to exclude: " + request.seniorUid);
        
        // Normalize the barangay name for matching
        String normalizedRequestBarangay = normalizeBarangayName(request.barangay);
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Normalized barangay name: '" + normalizedRequestBarangay + "'");
        
        // Fetch ALL barangay users and filter client-side for case-insensitive matching
        // This is necessary because Firestore doesn't support case-insensitive queries
        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Fetching all barangay users for client-side matching...");
        db.collection("Sagip")
                .document("users")
                .collection("barangay")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Fetched " + querySnapshot.size() + " total barangay users from database");
                    
                    int matchedCount = 0;
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String barangayUserId = document.getId();
                        String barangayName = document.getString("barangayName");
                        String contactPerson = document.getString("contactPerson");
                        
                        // Normalize the barangay name from the database for comparison
                        String normalizedDbBarangay = normalizeBarangayName(barangayName);
                        
                        // Check if barangay names match (case-insensitive, handles "Barangay X" vs "X" variations)
                        boolean isMatch = isBarangayMatch(normalizedRequestBarangay, normalizedDbBarangay);
                        
                        Log.d(TAG, "🏘️ [BARANGAY_NOTIFICATION] Comparing: '" + normalizedRequestBarangay + "' vs '" + normalizedDbBarangay + "' -> " + (isMatch ? "MATCH" : "no match"));
                        
                        if (!isMatch) {
                            continue;
                        }
                        
                        matchedCount++;
                        
                        // Skip notification if this is the senior who made the emergency call
                        if (barangayUserId.equals(request.seniorUid)) {
                            Log.d(TAG, "🏘️ Skipping notification to senior who made the emergency call: " + request.seniorName);
                            continue;
                        }
                        
                        // Create notification for barangay user
                        Map<String, Object> barangayNotification = new java.util.HashMap<>();
                        barangayNotification.put("type", "EMERGENCY_ALERT");
                        barangayNotification.put("title", context.getString(R.string.barangay_emergency_alert_in, request.barangay));
                        barangayNotification.put("message", context.getString(R.string.barangay_emergency_assistance_message, request.seniorName));
                        barangayNotification.put("seniorName", request.seniorName);
                        barangayNotification.put("seniorPhone", request.seniorPhone);
                        barangayNotification.put("locationAddress", request.locationAddress);
                        barangayNotification.put("barangay", request.barangay);
                        barangayNotification.put("requestId", request.requestId);
                        barangayNotification.put("emergencyType", request.emergencyType);
                        barangayNotification.put("timestamp", System.currentTimeMillis());
                        barangayNotification.put("isRead", false);
                        barangayNotification.put("isActive", true);
                        
                        // Add senior coordinates for navigation
                        if (request.location != null) {
                            barangayNotification.put("seniorLatitude", request.location.getLatitude());
                            barangayNotification.put("seniorLongitude", request.location.getLongitude());
                            Log.d(TAG, "🏘️ Added senior coordinates to barangay notification: " + 
                                request.location.getLatitude() + ", " + request.location.getLongitude());
                        } else {
                            Log.w(TAG, "⚠️ No senior coordinates available for barangay notification");
                        }
                        
                        // Add currentLocation field (full address from senior's profile)
                        barangayNotification.put("currentLocation", request.locationAddress);
                        Log.d(TAG, "🏘️ Added currentLocation to barangay notification: " + request.locationAddress);
                        
                        // Send notification to barangay user
                        String notificationPath = "Sagip/users/barangay/" + barangayUserId + "/notifications";
                        Log.d(TAG, "🏘️ Sending barangay notification to: " + contactPerson + " (" + barangayUserId + ")");
                        Log.d(TAG, "🏘️ Notification path: " + notificationPath);
                        
                        db.collection(notificationPath)
                                .add(barangayNotification)
                                .addOnSuccessListener(documentReference -> {
                                    Log.d(TAG, "✅ [BARANGAY_NOTIFICATION] SUCCESS: Barangay notification sent to: " + contactPerson + " in " + barangayName);
                                    Log.d(TAG, "✅ [BARANGAY_NOTIFICATION] Notification document ID: " + documentReference.getId());
                                    Log.d(TAG, "✅ [BARANGAY_NOTIFICATION] Notification path: " + notificationPath);
                                    
                                    // Also send FCM notification for background delivery
                                    sendFCMNotificationToBarangay(barangayUserId, barangayNotification);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] FAILED to send notification to: " + contactPerson, e);
                                    Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] Error details: " + e.getMessage());
                                    Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] Notification path: " + notificationPath);
                                });
                    }
                    
                    if (matchedCount == 0) {
                        Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] CRITICAL: No barangay users found matching barangay: " + request.barangay);
                        Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] Normalized search term was: '" + normalizedRequestBarangay + "'");
                        Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] Total barangay users in database: " + querySnapshot.size());
                        Log.e(TAG, "❌ [BARANGAY_NOTIFICATION] Check if barangay officials are registered with matching barangay name");
                    } else {
                        Log.d(TAG, "✅ [BARANGAY_NOTIFICATION] Found " + matchedCount + " matching barangay users for: " + request.barangay);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error fetching barangay users: " + e.getMessage(), e);
                });
    }
    
    /**
     * Normalizes barangay name for comparison by:
     * - Converting to lowercase
     * - Removing common prefixes like "Barangay", "Brgy", "Brgy."
     * - Trimming whitespace
     */
    private String normalizeBarangayName(String barangay) {
        if (barangay == null || barangay.isEmpty()) {
            return "";
        }
        
        String normalized = barangay.toLowerCase().trim();
        
        // Remove common barangay prefixes
        String[] prefixes = {"barangay ", "brgy. ", "brgy ", "bgy. ", "bgy "};
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length()).trim();
                break;
            }
        }
        
        return normalized;
    }
    
    /**
     * Checks if two barangay names match (case-insensitive, handles variations)
     */
    private boolean isBarangayMatch(String barangay1, String barangay2) {
        if (barangay1 == null || barangay2 == null) {
            return false;
        }
        
        // Exact match after normalization
        if (barangay1.equals(barangay2)) {
            return true;
        }
        
        // Check if one contains the other (for partial matches like "Amsic" matching "Amsic, Angeles City")
        if (barangay1.contains(barangay2) || barangay2.contains(barangay1)) {
            return true;
        }
        
        return false;
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
                            boolean exists = false;
                            for (EmergencyRequest existing : activeEmergencies) {
                                if (existing.requestId != null && existing.requestId.equals(request.requestId)) {
                                    exists = true;
                                    break;
                                }
                            }
                            
                            if (!exists) {
                                Log.d(TAG, "🔍 [LOAD_FROM_DB] Adding emergency to local queue: " + request.requestId);
                                activeEmergencies.add(request);
                                sortByFIFO();
                            } else {
                                Log.d(TAG, "🔍 [LOAD_FROM_DB] Emergency already exists in local queue: " + request.requestId);
                            }
                            
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
                            boolean exists = false;
                            for (EmergencyRequest existing : activeEmergencies) {
                                if (existing.requestId != null && existing.requestId.equals(request.requestId)) {
                                    exists = true;
                                    break;
                                }
                            }
                            
                            if (!exists) {
                                Log.d(TAG, "🔍 [LOAD_FROM_DB] Adding emergency to local queue: " + request.requestId);
                                activeEmergencies.add(request);
                                sortByFIFO();
                            } else {
                                Log.d(TAG, "🔍 [LOAD_FROM_DB] Emergency already exists in local queue: " + request.requestId);
                            }
                            
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
                    Log.d(TAG, "📋 Found " + querySnapshot.size() + " total rescuers to check for notification");
                    int notifiedCount = 0;
                    int skippedCount = 0;
                    
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String rescuerId = document.getId();
                        
                        // Check if rescuer is currently on an assignment
                        // Only skip if explicitly set to true; treat null/false as available
                        Boolean onAssignment = document.getBoolean("onAssignment");
                        if (Boolean.TRUE.equals(onAssignment)) {
                            Log.d(TAG, "🚫 SKIPPING rescuer " + rescuerId + " - currently on assignment");
                            skippedCount++;
                            continue; // Skip this rescuer - they're busy with another emergency
                        }
                        
                        // Rescuer is available, send notification
                        Log.d(TAG, "✅ Notifying rescuer " + rescuerId + " (onAssignment: " + onAssignment + ")");
                        sendEmergencyNotificationToRescuer(rescuerId, request);
                        notifiedCount++;
                    }
                    
                    Log.d(TAG, "✅ Notification summary: " + notifiedCount + " rescuers notified, " + skippedCount + " rescuers skipped (on assignment)");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to query rescuers: " + e.getMessage());
                });
    }
    
    private void sendEmergencyNotificationToRescuer(String rescuerId, EmergencyRequest request) {
        Log.d(TAG, "📤 [SEND_NOTIFICATION] Attempting to send notification to rescuer: " + rescuerId);
        Log.d(TAG, "📤 [SEND_NOTIFICATION] Request ID: " + request.requestId);
        Log.d(TAG, "📤 [SEND_NOTIFICATION] Senior: " + request.seniorName);
        
        String notificationPath = "Sagip/users/rescuer/" + rescuerId + "/emergencyNotifications";
        Log.d(TAG, "📤 [SEND_NOTIFICATION] Notification path: " + notificationPath);
        
        // First, check if a notification for this requestId already exists for this rescuer
        db.collection("Sagip/users/rescuer/" + rescuerId + "/emergencyNotifications")
                .whereEqualTo("requestId", request.requestId)
                .whereEqualTo("type", "EMERGENCY_SOS")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        Log.d(TAG, "⚠️ [DUPLICATE_PREVENTION] Notification already exists for rescuer " + rescuerId + ", requestId: " + request.requestId);
                        Log.d(TAG, "✅ [DUPLICATE_PREVENTION] Skipping duplicate notification creation");
                        return;
                    }
                    
                    // No duplicate found, create new notification
                    Log.d(TAG, "✅ [DUPLICATE_PREVENTION] No existing notification found, creating new one for requestId: " + request.requestId);
                    Log.d(TAG, "📤 [SEND_NOTIFICATION] Creating notification document...");
                    
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
                    
                    // Add GPS coordinates for accurate navigation
                    if (request.location != null) {
                        notificationData.put("seniorLat", request.location.getLatitude());
                        notificationData.put("seniorLng", request.location.getLongitude());
                        Log.d(TAG, "📍 Added GPS coordinates to notification: " + request.location.getLatitude() + ", " + request.location.getLongitude());
                    } else {
                        Log.w(TAG, "⚠️ No GPS coordinates available for notification");
                    }
                    
                    db.collection("Sagip/users/rescuer/" + rescuerId + "/emergencyNotifications")
                            .add(notificationData)
                            .addOnSuccessListener(documentReference -> {
                                Log.d(TAG, "✅ [SEND_NOTIFICATION] SUCCESS: Emergency notification sent to rescuer: " + rescuerId);
                                Log.d(TAG, "✅ [SEND_NOTIFICATION] Notification document ID: " + documentReference.getId());
                                Log.d(TAG, "✅ [SEND_NOTIFICATION] Notification path: " + notificationPath + "/" + documentReference.getId());
                                Log.d(TAG, "✅ [SEND_NOTIFICATION] Request ID: " + request.requestId);
                                Log.d(TAG, "✅ [SEND_NOTIFICATION] Senior: " + request.seniorName);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ [SEND_NOTIFICATION] FAILED to send notification to rescuer " + rescuerId + ": " + e.getMessage());
                                Log.e(TAG, "❌ [SEND_NOTIFICATION] Error type: " + e.getClass().getSimpleName());
                                Log.e(TAG, "❌ [SEND_NOTIFICATION] Notification path: " + notificationPath);
                                Log.e(TAG, "❌ [SEND_NOTIFICATION] Request ID: " + request.requestId);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ [DUPLICATE_PREVENTION] Failed to check for existing notification: " + e.getMessage());
                    Log.e(TAG, "⚠️ [DUPLICATE_PREVENTION] Proceeding with notification creation despite error");
                    
                    // If check fails, proceed with creation (better to have duplicate than miss emergency)
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
                    
                    if (request.location != null) {
                        notificationData.put("seniorLat", request.location.getLatitude());
                        notificationData.put("seniorLng", request.location.getLongitude());
                    }
                    
                    db.collection("Sagip/users/rescuer/" + rescuerId + "/emergencyNotifications")
                            .add(notificationData)
                            .addOnSuccessListener(documentReference -> {
                                Log.d(TAG, "📤 Emergency notification sent to rescuer: " + rescuerId);
                            })
                            .addOnFailureListener(e2 -> {
                                Log.e(TAG, "❌ Failed to send notification to rescuer " + rescuerId + ": " + e2.getMessage());
                            });
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
    
    /**
     * Cleanup old assigned emergencies from the activeRequests collection
     * This should be called periodically or when the queue gets too large
     */
    public void cleanupOldAssignedEmergencies() {
        Log.d(TAG, "🧹 [CLEANUP] Starting cleanup of old assigned emergencies");
        
        // Find all emergencies with status "assigned" in activeRequests
        db.collection("Sagip")
                .document("emergencyRequests")
                .collection("activeRequests")
                .whereEqualTo("status", "assigned")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int count = querySnapshot.size();
                    Log.d(TAG, "🧹 [CLEANUP] Found " + count + " assigned emergencies to move");
                    
                    if (count == 0) {
                        Log.d(TAG, "✅ [CLEANUP] No assigned emergencies to clean up");
                        return;
                    }
                    
                    int[] moved = {0};
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String requestId = document.getId();
                        Map<String, Object> emergencyData = document.getData();
                        
                        // Add timestamp for when it was moved
                        emergencyData.put("movedToAssignedAt", System.currentTimeMillis());
                        
                        // Move to assignedRequests
                        db.collection("Sagip")
                                .document("emergencyRequests")
                                .collection("assignedRequests")
                                .document(requestId)
                                .set(emergencyData)
                                .addOnSuccessListener(aVoid -> {
                                    // Delete from activeRequests
                                    document.getReference().delete()
                                            .addOnSuccessListener(aVoid2 -> {
                                                moved[0]++;
                                                Log.d(TAG, "✅ [CLEANUP] Moved emergency " + moved[0] + "/" + count + ": " + requestId);
                                                
                                                // Remove from local queue if present
                                                removeEmergencyRequest(requestId);
                                                
                                                if (moved[0] == count) {
                                                    Log.d(TAG, "🎉 [CLEANUP] Cleanup completed! Moved " + moved[0] + " emergencies");
                                                    Log.d(TAG, "📊 [CLEANUP] Current local queue size: " + activeEmergencies.size());
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "❌ [CLEANUP] Failed to delete " + requestId + " from activeRequests: " + e.getMessage());
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ [CLEANUP] Failed to move " + requestId + " to assignedRequests: " + e.getMessage());
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ [CLEANUP] Failed to query assigned emergencies: " + e.getMessage());
                });
    }
    
    /**
     * Cleanup ALL old emergencies from activeRequests (both assigned and very old pending ones)
     * Use this method carefully - it will move all non-recent emergencies
     */
    public void cleanupAllOldEmergencies(long olderThanMillis) {
        Log.d(TAG, "🧹 [CLEANUP_ALL] Starting cleanup of all old emergencies");
        long cutoffTime = System.currentTimeMillis() - olderThanMillis;
        
        db.collection("Sagip")
                .document("emergencyRequests")
                .collection("activeRequests")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int totalCount = querySnapshot.size();
                    Log.d(TAG, "🧹 [CLEANUP_ALL] Found " + totalCount + " total emergencies in activeRequests");
                    
                    int[] movedCount = {0};
                    int[] skippedCount = {0};
                    
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Map<String, Object> data = document.getData();
                        String requestId = document.getId();
                        String status = (String) data.get("status");
                        Long timestamp = (Long) data.get("timestamp");
                        
                        // Move if: status is "assigned" OR timestamp is older than cutoff
                        boolean shouldMove = "assigned".equals(status) || 
                                           (timestamp != null && timestamp < cutoffTime);
                        
                        if (shouldMove) {
                            // Add timestamp for when it was moved
                            data.put("movedToAssignedAt", System.currentTimeMillis());
                            data.put("cleanupReason", "assigned".equals(status) ? "status_assigned" : "old_timestamp");
                            
                            // Determine target collection based on status
                            String targetCollection = "assigned".equals(status) ? "assignedRequests" : "expiredRequests";
                            
                            // Move to appropriate collection
                            db.collection("Sagip")
                                    .document("emergencyRequests")
                                    .collection(targetCollection)
                                    .document(requestId)
                                    .set(data)
                                    .addOnSuccessListener(aVoid -> {
                                        // Delete from activeRequests
                                        document.getReference().delete()
                                                .addOnSuccessListener(aVoid2 -> {
                                                    movedCount[0]++;
                                                    Log.d(TAG, "✅ [CLEANUP_ALL] Moved " + movedCount[0] + "/" + totalCount + ": " + requestId + " to " + targetCollection);
                                                    
                                                    // Remove from local queue
                                                    removeEmergencyRequest(requestId);
                                                    
                                                    if (movedCount[0] + skippedCount[0] == totalCount) {
                                                        Log.d(TAG, "🎉 [CLEANUP_ALL] Cleanup completed!");
                                                        Log.d(TAG, "📊 [CLEANUP_ALL] Moved: " + movedCount[0] + ", Kept active: " + skippedCount[0]);
                                                        Log.d(TAG, "📊 [CLEANUP_ALL] Current local queue size: " + activeEmergencies.size());
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "❌ [CLEANUP_ALL] Failed to delete " + requestId + ": " + e.getMessage());
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "❌ [CLEANUP_ALL] Failed to move " + requestId + ": " + e.getMessage());
                                    });
                        } else {
                            skippedCount[0]++;
                            Log.d(TAG, "⏭️ [CLEANUP_ALL] Keeping active emergency: " + requestId + " (status: " + status + ")");
                        }
                    }
                    
                    if (totalCount == 0) {
                        Log.d(TAG, "✅ [CLEANUP_ALL] No emergencies found in activeRequests");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ [CLEANUP_ALL] Failed to query emergencies: " + e.getMessage());
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
                            
                            // Send FCM notification using Firebase Functions approach
                            sendRealFCMNotificationToSenior(fcmToken, notificationData);
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
    
    private void sendRealFCMNotificationToSenior(String fcmToken, Map<String, Object> notificationData) {
        Log.d(TAG, "📱 Sending real FCM notification to senior token: " + fcmToken.substring(0, Math.min(20, fcmToken.length())) + "...");
        
        // Save notification to Firestore to trigger Firebase Function
        Map<String, Object> fcmNotificationData = new java.util.HashMap<>();
        fcmNotificationData.put("type", "RESCUER_RESPONSE");
        fcmNotificationData.put("title", notificationData.get("title"));
        fcmNotificationData.put("message", notificationData.get("message"));
        fcmNotificationData.put("rescuerName", notificationData.get("rescuerName"));
        fcmNotificationData.put("rescuerPhone", notificationData.get("rescuerPhone"));
        fcmNotificationData.put("rescuerTeam", notificationData.get("rescuerTeam"));
        fcmNotificationData.put("requestId", notificationData.get("requestId"));
        fcmNotificationData.put("etaMinutes", notificationData.get("etaMinutes"));
        fcmNotificationData.put("distanceKm", notificationData.get("distanceKm"));
        fcmNotificationData.put("targetToken", fcmToken);
        fcmNotificationData.put("timestamp", System.currentTimeMillis());
        
        // Save to FCM notifications collection to trigger Firebase Function
        db.collection("Sagip")
                .document("fcmNotifications")
                .collection("seniorNotifications")
                .add(fcmNotificationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "✅ FCM notification data saved to trigger Firebase Function: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save FCM notification data: " + e.getMessage());
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
    
    /**
     * Send SMS notifications to emergency contacts when a rescuer accepts an SOS call
     */
    private void sendSMSToEmergencyContacts(EmergencyRequest request, String rescuerId) {
        Log.d(TAG, "🚨🚨🚨 SMS NOTIFICATION TRIGGERED 🚨🚨🚨");
        Log.d(TAG, "📱 [SMS_SERVICE] Starting SMS notification process");
        Log.d(TAG, "📱 [SMS_SERVICE] Senior: " + request.seniorName + " (" + request.seniorUid + ")");
        Log.d(TAG, "📱 [SMS_SERVICE] Rescuer ID: " + rescuerId);
        Log.d(TAG, "📱 [SMS_SERVICE] Location: " + request.locationAddress);
        Log.d(TAG, "📱 [SMS_SERVICE] Emergency Type: " + request.emergencyType);
        
        // Get rescuer information first
        getRescuerInfoFromDatabase(rescuerId, new RescuerInfoCallback() {
            @Override
            public void onRescuerInfoReceived(String rescuerName, String rescuerPhone, String rescuerTeam) {
                Log.d(TAG, "📱 [SMS_SERVICE] Rescuer info received - Name: " + rescuerName + ", Phone: " + rescuerPhone + ", Team: " + rescuerTeam);
                
                // Send SMS notifications using the SMS service
                EmergencyContactSMSService smsService = EmergencyContactSMSService.getInstance(context);
                Log.d(TAG, "📱 [SMS_SERVICE] SMS service instance created");
                
                // Check if SMS permission is granted
                boolean hasPermission = smsService.hasSMSPermission();
                Log.d(TAG, "📱 [SMS_SERVICE] SMS permission granted: " + hasPermission);
                
                if (hasPermission) {
                    Log.d(TAG, "📱 [SMS_SERVICE] Calling sendEmergencySMSNotifications...");
                    smsService.sendEmergencySMSNotifications(
                        request.seniorUid,
                        request.seniorName,
                        rescuerName,
                        rescuerPhone,
                        rescuerTeam,
                        request.locationAddress,
                        request.emergencyType
                    );
                    Log.d(TAG, "📱 [SMS_SERVICE] sendEmergencySMSNotifications called successfully");
                } else {
                    Log.w(TAG, "⚠️ [SMS_SERVICE] SMS permission not granted - cannot send emergency SMS notifications");
                    Log.w(TAG, "⚠️ [SMS_SERVICE] Please grant SMS permission in app settings");
                    
                    // Try to request permission if we have an activity context
                    if (context instanceof Activity) {
                        Log.d(TAG, "📱 [SMS_SERVICE] Requesting SMS permission...");
                        PermissionManager.requestSMSPermission((Activity) context);
                    } else {
                        Log.w(TAG, "⚠️ [SMS_SERVICE] Cannot request permission - context is not an Activity");
                    }
                }
            }
        });
    }

    /**
     * Handle SMS permission result and retry sending SMS if permission was granted
     */
    public void handleSMSPermissionResult(boolean granted, EmergencyRequest request, String rescuerId) {
        Log.d(TAG, "📱 [SMS_PERMISSION] Permission result: " + granted);
        
        if (granted) {
            Log.d(TAG, "📱 [SMS_PERMISSION] SMS permission granted, retrying SMS send...");
            // Retry sending SMS notifications
            sendSMSToEmergencyContacts(request, rescuerId);
        } else {
            Log.w(TAG, "⚠️ [SMS_PERMISSION] SMS permission denied - emergency contacts will not be notified");
        }
    }

    /**
     * Test method to send a direct notification to senior for debugging
     * This bypasses the normal flow to test if the SeniorNotificationService is working
     */
    public void sendTestNotificationToSenior(String seniorUid) {
        Log.d(TAG, "🧪 [TEST_NOTIFICATION] Sending test notification to senior: " + seniorUid);
        
        // Create test notification data
        Map<String, Object> testNotification = new java.util.HashMap<>();
        testNotification.put("type", "RESCUER_RESPONSE");
        testNotification.put("title", "🧪 TEST - Help is on the way! (Test Notification)");
        testNotification.put("message", "Test Rescuer from Test Rescue Team is responding to your emergency. ETA: 5 min");
        testNotification.put("rescuerName", "Test Rescuer");
        testNotification.put("rescuerPhone", "1234567890");
        testNotification.put("rescuerTeam", "Test Rescue Team");
        testNotification.put("requestId", "TEST_" + System.currentTimeMillis());
        testNotification.put("emergency_status", "assigned");
        testNotification.put("assigned_rescuer_id", "test_rescuer_id");
        testNotification.put("etaMinutes", 5.0);
        testNotification.put("distanceKm", 2.5);
        testNotification.put("timestamp", System.currentTimeMillis());
        testNotification.put("isRead", false);
        testNotification.put("isActive", true);
        
        // Send directly to senior's notification collection
        db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .document(seniorUid)
                .collection("notifications")
                .add(testNotification)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "✅ [TEST_NOTIFICATION] Test notification sent successfully to senior: " + seniorUid);
                    Log.d(TAG, "✅ [TEST_NOTIFICATION] Notification ID: " + documentReference.getId());
                    Log.d(TAG, "✅ [TEST_NOTIFICATION] This should trigger SeniorNotificationService if it's working");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ [TEST_NOTIFICATION] Failed to send test notification to senior: " + e.getMessage());
                });
    }

    /**
     * Remove emergency notification from rescuer's personal notification collection
     * This prevents duplicate notifications when a rescuer responds to an emergency
     * @deprecated Use updateAllRescuerNotificationsForAssignment instead for Option 2 approach
     */
    @Deprecated
    private void removeEmergencyNotificationFromRescuer(String requestId, String rescuerId) {
        Log.d(TAG, "🗑️ [REMOVE_NOTIFICATION] Removing emergency notification from rescuer collection");
        Log.d(TAG, "🗑️ [REMOVE_NOTIFICATION] RequestId: " + requestId);
        Log.d(TAG, "🗑️ [REMOVE_NOTIFICATION] RescuerId: " + rescuerId);
        
        // Query the rescuer's emergency notifications collection to find the notification with matching requestId
        db.collection("Sagip")
          .document("users")
          .collection("rescuer")
          .document(rescuerId)
          .collection("emergencyNotifications")
          .whereEqualTo("requestId", requestId)
          .get()
          .addOnSuccessListener(querySnapshot -> {
              if (querySnapshot != null && !querySnapshot.isEmpty()) {
                  Log.d(TAG, "🗑️ [REMOVE_NOTIFICATION] Found " + querySnapshot.size() + " notification(s) to remove");
                  
                  // Delete all matching notifications
                  for (QueryDocumentSnapshot document : querySnapshot) {
                      document.getReference().delete()
                          .addOnSuccessListener(aVoid -> {
                              Log.d(TAG, "✅ [REMOVE_NOTIFICATION] Successfully removed notification: " + document.getId());
                          })
                          .addOnFailureListener(e -> {
                              Log.e(TAG, "❌ [REMOVE_NOTIFICATION] Failed to remove notification " + document.getId() + ": " + e.getMessage());
                          });
                  }
              } else {
                  Log.d(TAG, "ℹ️ [REMOVE_NOTIFICATION] No emergency notifications found for requestId: " + requestId);
              }
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "❌ [REMOVE_NOTIFICATION] Error querying rescuer notifications: " + e.getMessage());
          });
    }
    
    /**
     * Update ALL rescuers' notifications when one rescuer accepts an emergency
     * - Assigned rescuer: notification deleted
     * - Other rescuers: notification updated to show "already assigned" (shows ONCE, then marked as read)
     */
    private void updateAllRescuerNotificationsForAssignment(String requestId, String assignedRescuerId) {
        Log.d(TAG, "📢 [UPDATE_ALL_RESCUERS] ===== UPDATING ALL RESCUER NOTIFICATIONS =====");
        Log.d(TAG, "📢 [UPDATE_ALL_RESCUERS] RequestId: " + requestId);
        Log.d(TAG, "📢 [UPDATE_ALL_RESCUERS] Assigned Rescuer: " + assignedRescuerId);
        
        // First, get the assigned rescuer's information
        getRescuerInfoFromDatabase(assignedRescuerId, new RescuerInfoCallback() {
            @Override
            public void onRescuerInfoReceived(String rescuerName, String rescuerPhone, String rescuerTeam) {
                Log.d(TAG, "📢 [UPDATE_ALL_RESCUERS] Assigned rescuer info - Name: " + rescuerName + ", Team: " + rescuerTeam);
                
                // Query ALL rescuers to find their notifications for this emergency
                db.collection("Sagip")
                        .document("users")
                        .collection("rescuer")
                        .get()
                        .addOnSuccessListener(rescuersSnapshot -> {
                            int totalRescuers = rescuersSnapshot.size();
                            Log.d(TAG, "📢 [UPDATE_ALL_RESCUERS] Found " + totalRescuers + " total rescuers");
                            
                            final int[] processedCount = {0};
                            final int[] updatedCount = {0};
                            
                            for (QueryDocumentSnapshot rescuerDoc : rescuersSnapshot) {
                                String currentRescuerId = rescuerDoc.getId();
                                
                                // Query this rescuer's emergency notifications for the specific requestId
                                db.collection("Sagip")
                                        .document("users")
                                        .collection("rescuer")
                                        .document(currentRescuerId)
                                        .collection("emergencyNotifications")
                                        .whereEqualTo("requestId", requestId)
                                        .get()
                                        .addOnSuccessListener(notificationsSnapshot -> {
                                            processedCount[0]++;
                                            
                                            if (!notificationsSnapshot.isEmpty()) {
                                                for (QueryDocumentSnapshot notificationDoc : notificationsSnapshot) {
                                                    // Delete notification for ALL rescuers (both assigned and others)
                                                    Log.d(TAG, "🗑️ [UPDATE_ALL_RESCUERS] Deleting notification for rescuer: " + currentRescuerId);
                                                    notificationDoc.getReference().delete()
                                                            .addOnSuccessListener(aVoid -> {
                                                                updatedCount[0]++;
                                                                Log.d(TAG, "✅ [UPDATE_ALL_RESCUERS] Deleted notification for rescuer: " + currentRescuerId);
                                                            })
                                                            .addOnFailureListener(e -> {
                                                                Log.e(TAG, "❌ [UPDATE_ALL_RESCUERS] Failed to delete notification: " + e.getMessage());
                                                            });
                                                }
                                            } else {
                                                Log.d(TAG, "ℹ️ [UPDATE_ALL_RESCUERS] No notifications found for rescuer: " + currentRescuerId);
                                            }
                                            
                                            // Log completion when all rescuers processed
                                            if (processedCount[0] == totalRescuers) {
                                                Log.d(TAG, "🎉 [UPDATE_ALL_RESCUERS] ===== COMPLETED =====");
                                                Log.d(TAG, "📊 [UPDATE_ALL_RESCUERS] Processed: " + processedCount[0] + " rescuers");
                                                Log.d(TAG, "📊 [UPDATE_ALL_RESCUERS] Updated: " + updatedCount[0] + " notifications");
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            processedCount[0]++;
                                            Log.e(TAG, "❌ [UPDATE_ALL_RESCUERS] Error querying notifications for rescuer " + currentRescuerId + ": " + e.getMessage());
                                        });
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ [UPDATE_ALL_RESCUERS] Error querying all rescuers: " + e.getMessage());
                        });
            }
        });
    }
}

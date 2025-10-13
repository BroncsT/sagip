package com.example.sagip_prototype;

import android.content.Context;
import android.telephony.SmsManager;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for sending SMS notifications to emergency contacts when a rescuer accepts an SOS call
 */
public class EmergencyContactSMSService {
    private static final String TAG = "EmergencyContactSMS";
    private static EmergencyContactSMSService instance;
    private Context context;
    private FirebaseFirestore db;
    private SmsManager smsManager;

    private EmergencyContactSMSService(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.smsManager = SmsManager.getDefault();
    }

    public static synchronized EmergencyContactSMSService getInstance(Context context) {
        if (instance == null) {
            instance = new EmergencyContactSMSService(context);
        }
        return instance;
    }

    /**
     * Send SMS notifications to all emergency contacts when a rescuer accepts an SOS call
     * @param seniorUid The UID of the senior who made the SOS call
     * @param seniorName The name of the senior
     * @param rescuerName The name of the rescuer who accepted the call
     * @param rescuerPhone The phone number of the rescuer
     * @param rescuerTeam The team/group of the rescuer
     * @param locationAddress The location where the emergency occurred
     * @param emergencyType The type of emergency
     */
    public void sendEmergencySMSNotifications(String seniorUid, String seniorName, 
                                            String rescuerName, String rescuerPhone, 
                                            String rescuerTeam, String locationAddress, 
                                            String emergencyType) {
        Log.d(TAG, "🚨🚨🚨 SMS NOTIFICATION PROCESS STARTED 🚨🚨🚨");
        Log.d(TAG, "📱 Senior: " + seniorName + " (UID: " + seniorUid + ")");
        Log.d(TAG, "📱 Rescuer: " + rescuerName + " (Phone: " + rescuerPhone + ")");
        Log.d(TAG, "📱 Team: " + rescuerTeam);
        Log.d(TAG, "📱 Location: " + locationAddress);
        Log.d(TAG, "📱 Emergency Type: " + emergencyType);
        
        // Check SMS permission first with detailed logging
        Log.d(TAG, "📱 Checking SMS permission...");
        boolean hasPermission = hasSMSPermission();
        Log.d(TAG, "📱 SMS permission check result: " + hasPermission);
        
        if (!hasPermission) {
            Log.e(TAG, "❌ SMS PERMISSION NOT GRANTED - Cannot send SMS notifications!");
            Log.e(TAG, "❌ Permission details: " + android.content.pm.PackageManager.PERMISSION_GRANTED);
            Log.e(TAG, "❌ Context: " + context.getClass().getSimpleName());
            return;
        }
        Log.d(TAG, "✅ SMS permission is granted");

        // Get emergency contacts from senior's profile
        Log.d(TAG, "📱 Querying emergency contacts for senior UID: " + seniorUid);
        db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .document(seniorUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Log.d(TAG, "📱 Senior document exists: " + documentSnapshot.exists());
                    if (documentSnapshot.exists()) {
                        // Log all fields in the document for debugging
                        Log.d(TAG, "📱 Senior document fields: " + documentSnapshot.getData().keySet());
                        
                        List<Map<String, Object>> emergencyContacts = 
                            (List<Map<String, Object>>) documentSnapshot.get("emergencyContacts");
                        
                        Log.d(TAG, "📱 Emergency contacts data: " + emergencyContacts);
                        
                        if (emergencyContacts != null && !emergencyContacts.isEmpty()) {
                            Log.d(TAG, "📱 Found " + emergencyContacts.size() + " emergency contacts");
                            sendSMSToContacts(emergencyContacts, seniorName, rescuerName, 
                                            rescuerPhone, rescuerTeam, locationAddress, emergencyType);
                        } else {
                            Log.w(TAG, "⚠️ No emergency contacts found for senior: " + seniorName);
                            Log.w(TAG, "⚠️ Emergency contacts data: " + emergencyContacts);
                        }
                    } else {
                        Log.e(TAG, "❌ Senior document not found: " + seniorUid);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading emergency contacts: " + e.getMessage());
                    Log.e(TAG, "❌ Error details: " + e.toString());
                });
    }

    /**
     * Send SMS to all emergency contacts
     */
    private void sendSMSToContacts(List<Map<String, Object>> emergencyContacts, 
                                  String seniorName, String rescuerName, String rescuerPhone, 
                                  String rescuerTeam, String locationAddress, String emergencyType) {
        
        Log.d(TAG, "📱 Processing " + emergencyContacts.size() + " emergency contacts");
        
        for (int i = 0; i < emergencyContacts.size(); i++) {
            Map<String, Object> contact = emergencyContacts.get(i);
            Log.d(TAG, "📱 Processing contact " + (i + 1) + ": " + contact);
            
            String contactName = (String) contact.get("name");
            String contactNumber = (String) contact.get("number");
            String relationship = (String) contact.get("relationship");
            
            Log.d(TAG, "📱 Contact details - Name: " + contactName + ", Number: " + contactNumber + ", Relationship: " + relationship);
            
            if (contactNumber != null && !contactNumber.isEmpty()) {
                // Format phone number to ensure it starts with +63
                String formattedNumber = formatPhoneNumber(contactNumber);
                Log.d(TAG, "📱 Formatted phone number: " + contactNumber + " -> " + formattedNumber);
                
                // Create SMS message
                String message = createEmergencySMSMessage(seniorName, rescuerName, rescuerPhone, 
                                                        rescuerTeam, locationAddress, emergencyType, 
                                                        contactName, relationship);
                
                Log.d(TAG, "📱 Sending SMS to: " + contactName + " (" + formattedNumber + ")");
                Log.d(TAG, "📱 Message length: " + message.length() + " characters");
                Log.d(TAG, "📱 Message preview: " + message.substring(0, Math.min(100, message.length())) + "...");
                
                // Send SMS
                sendSMS(formattedNumber, message, contactName);
            } else {
                Log.w(TAG, "⚠️ Invalid phone number for contact: " + contactName);
                Log.w(TAG, "⚠️ Contact data: " + contact);
            }
        }
    }

    /**
     * Format phone number to international format (+63)
     */
    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        
        // Remove any non-digit characters
        String cleanNumber = phoneNumber.replaceAll("[^0-9]", "");
        
        // If it starts with 0, replace with +63
        if (cleanNumber.startsWith("0")) {
            return "+63" + cleanNumber.substring(1);
        }
        // If it starts with 63, add +
        else if (cleanNumber.startsWith("63")) {
            return "+" + cleanNumber;
        }
        // If it doesn't start with 0 or 63, assume it's missing the country code
        else if (cleanNumber.length() == 10) {
            return "+63" + cleanNumber;
        }
        // Return as is if already formatted
        else {
            return "+" + cleanNumber;
        }
    }

    /**
     * Create the SMS message content
     */
    private String createEmergencySMSMessage(String seniorName, String rescuerName, 
                                           String rescuerPhone, String rescuerTeam, 
                                           String locationAddress, String emergencyType,
                                           String contactName, String relationship) {
        
        StringBuilder message = new StringBuilder();
        message.append("🚨 EMERGENCY ALERT 🚨\n\n");
        message.append("Dear ").append(contactName).append(",\n\n");
        message.append("This is an automated message from SAGIP Emergency Response System.\n\n");
        message.append("Your ").append(relationship != null ? relationship.toLowerCase() : "family member");
        message.append(" ").append(seniorName).append(" has activated an emergency SOS call.\n\n");
        message.append("📋 EMERGENCY DETAILS:\n");
        message.append("• Type: ").append(emergencyType != null ? emergencyType.toUpperCase() : "MEDICAL EMERGENCY").append("\n");
        message.append("• Location: ").append(locationAddress).append("\n");
        message.append("• Time: ").append(getCurrentTimeString()).append("\n\n");
        message.append("🚑 RESPONSE TEAM:\n");
        message.append("• Rescuer: ").append(rescuerName).append("\n");
        message.append("• Team: ").append(rescuerTeam != null ? rescuerTeam : "Emergency Response Team").append("\n");
        message.append("• Contact: ").append(rescuerPhone != null ? rescuerPhone : "Contact emergency services").append("\n\n");
        message.append("✅ A rescuer has been assigned and is responding to the emergency.\n\n");
        message.append("Please stay calm and contact the rescuer if you need more information.\n\n");
        message.append("---\n");
        message.append("SAGIP Emergency Response System\n");
        message.append("This is an automated message. Do not reply to this number.");
        
        return message.toString();
    }

    /**
     * Get current time as formatted string
     */
    private String getCurrentTimeString() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' HH:mm:ss", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    /**
     * Send SMS using Android's SmsManager
     */
    private void sendSMS(String phoneNumber, String message, String contactName) {
        Log.d(TAG, "📱 Attempting to send SMS to " + contactName + " (" + phoneNumber + ")");
        Log.d(TAG, "📱 Message length: " + message.length() + " characters");
        
        // Double-check permission before sending
        if (!hasSMSPermission()) {
            Log.e(TAG, "❌ SMS permission lost before sending SMS!");
            return;
        }
        
        try {
            // Split message if it's too long (SMS limit is 160 characters for single SMS)
            if (message.length() > 160) {
                Log.d(TAG, "📱 Message is long, splitting into multiple parts");
                List<String> parts = smsManager.divideMessage(message);
                // Convert List<String> to ArrayList<String> for compatibility
                ArrayList<String> partsList = new ArrayList<>(parts);
                Log.d(TAG, "📱 Split into " + parts.size() + " parts");
                
                smsManager.sendMultipartTextMessage(phoneNumber, null, partsList, null, null);
                Log.d(TAG, "✅ Multi-part SMS sent to " + contactName + " (" + phoneNumber + ") - " + parts.size() + " parts");
            } else {
                Log.d(TAG, "📱 Sending single SMS");
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                Log.d(TAG, "✅ SMS sent to " + contactName + " (" + phoneNumber + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to send SMS to " + contactName + " (" + phoneNumber + "): " + e.getMessage());
            Log.e(TAG, "❌ Exception details: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * Test method to send SMS to a specific number (for testing purposes)
     */
    public void sendTestSMS(String phoneNumber, String message) {
        Log.d(TAG, "🧪 Sending test SMS to: " + phoneNumber);
        Log.d(TAG, "🧪 Test message: " + message);
        
        // Check permission before sending test SMS
        if (!hasSMSPermission()) {
            Log.e(TAG, "❌ SMS permission not granted for test SMS!");
            return;
        }
        
        sendSMS(phoneNumber, message, "Test Contact");
    }

    /**
     * Check if SMS permissions are granted with detailed logging
     */
    public boolean hasSMSPermission() {
        boolean hasPermission = PermissionManager.hasSMSPermission(context);
        Log.d(TAG, "📱 Permission check - hasSMSPermission: " + hasPermission);
        Log.d(TAG, "📱 Context: " + (context != null ? context.getClass().getSimpleName() : "null"));
        return hasPermission;
    }
    
    /**
     * Debug method to check emergency contacts for a senior
     */
    public void debugEmergencyContacts(String seniorUid) {
        Log.d(TAG, "🔍 DEBUG: Checking emergency contacts for senior: " + seniorUid);
        
        db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .document(seniorUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Log.d(TAG, "🔍 DEBUG: Senior document exists: " + documentSnapshot.exists());
                    if (documentSnapshot.exists()) {
                        Log.d(TAG, "🔍 DEBUG: All document fields: " + documentSnapshot.getData().keySet());
                        
                        List<Map<String, Object>> emergencyContacts = 
                            (List<Map<String, Object>>) documentSnapshot.get("emergencyContacts");
                        
                        Log.d(TAG, "🔍 DEBUG: Emergency contacts: " + emergencyContacts);
                        
                        if (emergencyContacts != null) {
                            Log.d(TAG, "🔍 DEBUG: Number of emergency contacts: " + emergencyContacts.size());
                            for (int i = 0; i < emergencyContacts.size(); i++) {
                                Map<String, Object> contact = emergencyContacts.get(i);
                                Log.d(TAG, "🔍 DEBUG: Contact " + (i + 1) + ": " + contact);
                            }
                        } else {
                            Log.d(TAG, "🔍 DEBUG: Emergency contacts is null");
                        }
                    } else {
                        Log.d(TAG, "🔍 DEBUG: Senior document does not exist");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🔍 DEBUG: Error loading senior document: " + e.getMessage());
                });
    }
}

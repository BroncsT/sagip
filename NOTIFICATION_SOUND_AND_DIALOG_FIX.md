# Notification Sound and Dialog Fix

## Issues Identified

Based on the logs provided on October 28, 2025 at 08:43:44, two critical issues were identified:

### Issue 1: Sound Plays But Dialog Doesn't Show
**Symptom:** The emergency sound starts playing, but the dialog is not shown because the activity reports `isFinishing() = true` and `isDestroyed() = true`.

**Log Evidence:**
```
2025-10-28 08:43:45.221 W Cannot show emergency alert dialog - activity is not in valid state (finishing: true, destroyed: true)
2025-10-28 08:43:45.224 D 🔊 Emergency sound started successfully
```

**Problem:** 
- Sound is played immediately when a notification is received (line 2384 in `handleEmergencySOSNotification`)
- The notification is queued for processing
- When the queue processes the emergency and tries to show the dialog, the activity state check fails
- The sound continues playing with no way for the user to stop it (no "Respond" or "Decline" buttons)

### Issue 2: Multiple Listener Registrations Causing Duplicate Processing
**Symptom:** The same notification is processed 9 times within 59 milliseconds.

**Log Evidence:**
```
2025-10-28 08:43:44.878 D Processing notification - Type: EMERGENCY_SOS, IsRead: false
2025-10-28 08:43:44.884 D Processing notification - Type: EMERGENCY_SOS, IsRead: false
2025-10-28 08:43:44.886 D Processing notification - Type: EMERGENCY_SOS, IsRead: false
... (6 more times)
```

**Problem:**
- `startEmergencySOSListener()` was being called in `onResume()` without checking if a listener was already registered
- No cleanup of old listeners before creating new ones
- Every time the activity resumed, a new listener was registered, accumulating multiple listeners
- All listeners would fire when a notification was added/modified, causing duplicate processing

## Solutions Implemented

### Fix 1: Stop Sound When Dialog Cannot Be Shown

**Files Modified:** `app/src/main/java/com/example/sagip_prototype/Rescuer_Dashboard.java`

**Changes:**

1. **In `showEmergencySOSAlertWithLocation()` (lines 2423-2427):**
```java
// Enhanced activity state check
if (isFinishing() || isDestroyed()) {
    Log.w(TAG, "Cannot show emergency alert dialog - activity is not in valid state (finishing: " + isFinishing() + ", destroyed: " + isDestroyed() + ")");
    // Stop sound since we can't show the dialog
    stopEmergencySound();
    return;
}
```

2. **In the synchronized block (lines 2439-2443):**
```java
// Double-check activity state after acquiring lock
if (isFinishing() || isDestroyed()) {
    Log.w(TAG, "Cannot show emergency alert dialog - activity state changed during lock acquisition");
    // Stop sound since we can't show the dialog
    stopEmergencySound();
    return;
}
```

3. **Same changes in `showEmergencySOSAlert()` method (lines 2615-2619 and 2631-2635)**

**Result:** Now when the activity is finishing/destroyed and cannot show the dialog, the sound is stopped immediately, preventing the issue where sound plays indefinitely without any UI to stop it.

### Fix 2: Proper Listener Management to Prevent Duplicates

**Files Modified:** `app/src/main/java/com/example/sagip_prototype/Rescuer_Dashboard.java`

**Changes:**

1. **Added listener tracking variable (line 680):**
```java
private ListenerRegistration emergencySOSListener; // Track emergency SOS listener
```

2. **Updated `onResume()` to check before registering (lines 958-977):**
```java
if (emergencySOSListener != null) {
    emergencySOSListener.remove();
    emergencySOSListener = null;
}
// ...
// Start emergency SOS notification listener (only if not already started)
if (emergencySOSListener == null) {
    Log.d(TAG, "Starting emergency SOS listener in onResume()");
    startEmergencySOSListener();
} else {
    Log.d(TAG, "Emergency SOS listener already active, skipping start");
}
```

3. **Modified `startEmergencySOSListener()` to cleanup and store registration (lines 2261-2274):**
```java
// Clean up existing listener to prevent duplicates
if (emergencySOSListener != null) {
    Log.d(TAG, "🧹 Cleaning up existing emergency SOS listener before creating new one");
    emergencySOSListener.remove();
    emergencySOSListener = null;
}

// ...
emergencySOSListener = db.collection("Sagip")
    .document("users")
    .collection("rescuer")
    .document(userId)
    .collection("emergencyNotifications")
    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
    .addSnapshotListener((querySnapshot, error) -> {
        // ... listener code ...
    });
```

4. **Added cleanup in `onPause()` (lines 1050-1055):**
```java
// Stop emergency SOS listener when app goes to background - EmergencySOSBackgroundService will handle it
if (emergencySOSListener != null) {
    Log.d(TAG, "🚨 Stopping emergency SOS listener in activity - EmergencySOSBackgroundService will handle background notifications");
    emergencySOSListener.remove();
    emergencySOSListener = null;
}
```

5. **Added cleanup in `onDestroy()` (lines 1083-1087):**
```java
// Remove emergency SOS listener
if (emergencySOSListener != null) {
    emergencySOSListener.remove();
    emergencySOSListener = null;
}
```

**Result:** Now the listener is properly managed:
- Only one listener is active at a time
- Old listeners are cleaned up before creating new ones
- Listeners are properly removed when the activity is paused or destroyed
- This prevents the duplicate processing issue where notifications were being handled multiple times

## Testing Recommendations

1. **Test Sound Stop on Activity Destruction:**
   - Open the rescuer dashboard
   - Trigger an emergency notification
   - Immediately press the home button or switch to another app
   - Verify that the sound stops when the activity is no longer in a valid state

2. **Test Listener Management:**
   - Open the rescuer dashboard
   - Check logcat for "Starting emergency SOS listener in onResume()"
   - Press home button and wait a few seconds
   - Check logcat for "Stopping emergency SOS listener in activity"
   - Return to the dashboard
   - Verify only one "Starting emergency SOS listener in onResume()" message appears
   - Trigger an emergency notification
   - Verify it's only processed once (not multiple times)

3. **Test Normal Flow:**
   - With the dashboard open, trigger an emergency notification
   - Verify the dialog appears and sound plays
   - Tap "Respond Now" or "Decline"
   - Verify the sound stops and dialog dismisses correctly

## Expected Behavior After Fix

1. ✅ Sound will only play when a dialog can be shown
2. ✅ If the activity becomes invalid (finishing/destroyed), sound will stop immediately
3. ✅ Each notification will be processed only once, even when the activity resumes multiple times
4. ✅ No duplicate listeners accumulating in memory
5. ✅ Proper cleanup of listeners when activity is paused/destroyed
6. ✅ Background service will continue to handle notifications when the app is in the background

## Related Files

- `app/src/main/java/com/example/sagip_prototype/Rescuer_Dashboard.java` - Main fixes implemented here
- `app/src/main/java/com/example/sagip_prototype/EmergencySOSBackgroundService.java` - Handles notifications in background (no changes needed)


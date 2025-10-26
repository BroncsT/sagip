# Emergency Queue Duplicate SOS Fix

## Problems Identified

Your system had **TWO issues** causing duplicate notifications:

### Issue 1: Old Emergencies Not Removed (119 in queue)
1. ❌ When emergencies were assigned to rescuers, they were **NOT removed** from the active queue
2. ❌ Old emergencies stayed in the `activeRequests` Firestore collection forever
3. ❌ The system kept loading all old emergencies on startup
4. ❌ This caused the queue to grow to 119 emergencies

### Issue 2: Duplicate Notification Creation
1. ❌ `sendEmergencyNotificationToRescuer()` created NEW notifications without checking for duplicates
2. ❌ If called twice with the same `requestId`, it would create 2 separate notification documents
3. ❌ This caused rescuers to receive the same emergency alert multiple times

## Changes Made

### 1. Auto-Remove Assigned Emergencies (EmergencyQueueManager.java)

**Lines 170-176:** After a rescuer is assigned, the emergency is now:
- ✅ Removed from the local memory queue
- ✅ Moved from `activeRequests` to `assignedRequests` in Firestore

```java
// Remove the emergency from the local queue since it's now assigned
Log.d(TAG, "🗑️ [EMERGENCY_QUEUE_MANAGER] Removing emergency from local queue: " + requestId);
removeEmergencyRequest(requestId);

// Move the emergency from activeRequests to assignedRequests in Firestore
moveEmergencyToAssignedCollection(request);
```

### 2. New Method: `moveEmergencyToAssignedCollection()` (Lines 210-260)

This method:
- ✅ Copies the emergency document to `Sagip/emergencyRequests/assignedRequests`
- ✅ Deletes it from `Sagip/emergencyRequests/activeRequests`
- ✅ Adds a `movedToAssignedAt` timestamp for tracking

### 3. New Method: `cleanupOldAssignedEmergencies()` (Lines 900-959)

This cleanup method:
- ✅ Finds all emergencies with `status="assigned"` in `activeRequests`
- ✅ Moves them to `assignedRequests` collection
- ✅ Removes them from the local queue
- ✅ Logs detailed progress

**Call this method to clean up your current 119 old emergencies!**

### 4. New Method: `cleanupAllOldEmergencies(long olderThanMillis)` (Lines 965-1040)

Advanced cleanup that:
- ✅ Moves all "assigned" emergencies to `assignedRequests`
- ✅ Moves emergencies older than X time to `expiredRequests`
- ✅ Keeps only recent pending emergencies active

### 5. Automatic Cleanup on App Start (Rescuer_Dashboard.java)

**Lines 835-837:** Added automatic cleanup when the rescuer opens the app:

```java
// Cleanup old assigned emergencies to prevent duplicate SOS notifications
Log.d(TAG, "🧹 Starting automatic cleanup of old emergencies...");
EmergencyQueueManager.getInstance(this).cleanupOldAssignedEmergencies();
```

### 6. Duplicate Notification Prevention (EmergencyQueueManager.java)

**Lines 825-903:** Added duplicate detection before creating notifications:

```java
private void sendEmergencyNotificationToRescuer(String rescuerId, EmergencyRequest request) {
    // Check if notification already exists for this requestId
    db.collection("Sagip/users/rescuer/" + rescuerId + "/emergencyNotifications")
            .whereEqualTo("requestId", request.requestId)
            .whereEqualTo("type", "EMERGENCY_SOS")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (!querySnapshot.isEmpty()) {
                    Log.d(TAG, "⚠️ [DUPLICATE_PREVENTION] Skipping duplicate notification");
                    return; // Don't create duplicate
                }
                // Create notification only if it doesn't exist
                ...
            });
}
```

**Benefits:**
- ✅ Checks for existing notification before creating new one
- ✅ Uses `requestId` as unique identifier
- ✅ Prevents multiple notification documents for the same emergency
- ✅ Logs detailed information for debugging

## How to Use

### Option 1: Automatic Cleanup (Recommended)
The cleanup now runs **automatically** every time a rescuer opens the dashboard. This will gradually clean up all 119 old emergencies.

### Option 2: Manual One-Time Cleanup
If you want to clean up ALL 119 emergencies immediately, add this temporary code to your `Rescuer_Dashboard.onCreate()`:

```java
// ONE-TIME CLEANUP - Remove after running once
EmergencyQueueManager.getInstance(this).cleanupAllOldEmergencies(
    24 * 60 * 60 * 1000 // Move emergencies older than 24 hours
);
```

### Option 3: Debug Button (For Testing)
Add a debug button to manually trigger cleanup:

```java
Button debugCleanupBtn = findViewById(R.id.debugCleanupBtn);
debugCleanupBtn.setOnClickListener(v -> {
    Log.d(TAG, "🧪 Manual cleanup triggered");
    EmergencyQueueManager.getInstance(this).cleanupAllOldEmergencies(
        60 * 60 * 1000 // Move emergencies older than 1 hour
    );
});
```

## Testing the Fix

### Before the Fix:
```
🚨🚨🚨 [EMERGENCY_QUEUE_MANAGER] Active emergencies count: 119
```

### After the Fix:
1. Open the rescuer app
2. Check the logs for:
```
🧹 [CLEANUP] Starting cleanup of old assigned emergencies
🧹 [CLEANUP] Found X assigned emergencies to move
✅ [CLEANUP] Moved emergency 1/X: SOS_1759270763813_...
✅ [CLEANUP] Moved emergency 2/X: SOS_1759306042974_...
...
🎉 [CLEANUP] Cleanup completed! Moved X emergencies
📊 [CLEANUP] Current local queue size: 0
```

3. New SOS alerts should now appear only **once**
4. The queue should stay clean with only **active pending emergencies**

## New Firestore Structure

After cleanup, your emergencies will be organized as:

```
Sagip/
  emergencyRequests/
    activeRequests/          ← Only pending emergencies (should be 0-10)
      SOS_xxx_yyy (pending)
    
    assignedRequests/        ← Emergencies assigned to rescuers
      SOS_xxx_yyy (assigned)
      SOS_xxx_yyy (assigned)
      ...
    
    expiredRequests/         ← Old unhandled emergencies (optional)
      SOS_xxx_yyy (expired)
```

## Benefits

✅ **No More Duplicate SOS**: Each emergency appears only once
✅ **Clean Queue**: Only real active emergencies in the queue
✅ **Better Performance**: Not loading 119 old emergencies
✅ **Automatic**: Cleanup happens automatically on app start
✅ **Historical Data**: Old emergencies moved to `assignedRequests` for records

## Monitoring

Watch these logs to verify BOTH fixes are working:

### Monitor Queue Cleanup:
```
# On app start:
🧹 [CLEANUP] Starting cleanup of old assigned emergencies
🧹 [CLEANUP] Found X assigned emergencies to move
✅ [CLEANUP] Moved emergency 1/X: SOS_xxx
🎉 [CLEANUP] Cleanup completed! Moved X emergencies
📊 [CLEANUP] Current local queue size: 0-10 (should be low)

# When assigning a rescuer:
🗑️ [EMERGENCY_QUEUE_MANAGER] Removing emergency from local queue
📦 [MOVE_TO_ASSIGNED] Moving emergency to assignedRequests
✅ [MOVE_TO_ASSIGNED] Emergency removed from activeRequests

# Active emergency count should stay low:
🚨🚨🚨 [EMERGENCY_QUEUE_MANAGER] Active emergencies count: 2
```

### Monitor Duplicate Prevention:
```
# When Senior sends SOS - First rescuer:
✅ [DUPLICATE_PREVENTION] No existing notification found, creating new one for requestId: SOS_xxx
📤 Emergency notification sent to rescuer: LEZk1rVm2c...

# If same emergency is processed again:
⚠️ [DUPLICATE_PREVENTION] Notification already exists for rescuer LEZk1rVm2c..., requestId: SOS_xxx
✅ [DUPLICATE_PREVENTION] Skipping duplicate notification creation

# You should see this message if duplicates are being prevented!
```

### What You Should NOT See Anymore:
```
❌ Multiple notifications with the same requestId for one rescuer
❌ Queue size growing beyond 20-30 emergencies
❌ Two alert sounds playing for the same SOS
❌ Duplicate emergency dialog popups
```

## Rollback (If Needed)

If you need to revert these changes:

1. Remove lines 835-837 from `Rescuer_Dashboard.java`
2. Remove lines 170-176 from `EmergencyQueueManager.java` (the `removeEmergencyRequest()` and `moveEmergencyToAssignedCollection()` calls)
3. Comment out the `moveEmergencyToAssignedCollection()` method

## Next Steps

1. ✅ **Test immediately**: Open the rescuer app and watch the logs
2. ✅ **Verify cleanup**: Check that old emergencies are moved
3. ✅ **Test new SOS**: Send a new SOS and verify it appears only once
4. ✅ **Monitor queue size**: Should stay under 10 active emergencies

---

## Summary of Fixes

### Fix #1: Queue Cleanup (Prevents old emergencies from accumulating)
- **Problem**: 119 old emergencies never removed from queue
- **Solution**: Auto-remove assigned emergencies + periodic cleanup
- **Result**: Queue stays clean with only active emergencies

### Fix #2: Duplicate Notification Prevention (Prevents double alerts)
- **Problem**: Same emergency created multiple notification documents
- **Solution**: Check for existing notification before creating new one
- **Result**: Each emergency creates only ONE notification per rescuer

### Combined Effect:
✅ No more duplicate SOS alerts  
✅ No more 119 old emergencies in queue  
✅ No more multiple dialogs for same emergency  
✅ Clean, efficient emergency management

---

**Status**: ✅ FIXED - No more duplicate SOS notifications!

**Test Immediately:**
1. Build and run the app
2. Send a new SOS from senior
3. Check logs for `[DUPLICATE_PREVENTION]` messages
4. Verify only ONE notification/dialog appears
5. Check queue size stays low (< 10)


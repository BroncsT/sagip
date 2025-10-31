# Rescuer Real-Time SOS Alert Filter Fix

## Problem Description

When a rescuer logged in to the app, they were receiving alerts for **ALL unread SOS emergencies** in their notification collection, not just real-time ones that occurred after login. This meant:

- Old emergencies (from hours or days ago) would trigger alerts
- Rescuers would hear emergency sounds for already-handled cases
- Multiple emergency popups would appear on login
- This created confusion and alert fatigue

## Root Cause

The Firestore snapshot listeners in both:
1. `Rescuer_Dashboard.java` (in-app alerts)
2. `EmergencySOSBackgroundService.java` (background notifications)

Were listening to ALL documents in the `emergencyNotifications` collection without any timestamp filter. When a snapshot listener is first attached, it fires `ADDED` events for ALL existing documents that match the query, not just new ones.

### Code Issue Example (Before Fix)
```java
// This listens to ALL documents, triggering alerts for old emergencies
emergencySOSListener = db.collection("Sagip")
  .document("users")
  .collection("rescuer")
  .document(userId)
  .collection("emergencyNotifications")
  .orderBy("timestamp", Query.Direction.DESCENDING)
  .addSnapshotListener((querySnapshot, error) -> {
    // Process ALL documents on initial load
  });
```

## Solution Implemented

Added timestamp filtering using `whereGreaterThan()` to only listen for emergencies that occur **AFTER** the listener starts:

### 1. Rescuer_Dashboard.java Changes

**File Location:** `app/src/main/java/com/example/sagip_prototype/Rescuer_Dashboard.java`

**Changes Made:**
- Added `whereGreaterThan("timestamp", lastLoginTime)` to the emergency listener query
- Updated `lastLoginTime` to current time when starting the listener (line 2603)
- This ensures only NEW emergencies trigger in-app alerts

**Code After Fix:**
```java
private void startEmergencySOSListener() {
    // ... validation code ...
    
    // Update login time to current time when starting listener
    // This ensures only NEW emergencies (after this moment) will trigger alerts
    lastLoginTime = System.currentTimeMillis();
    
    Log.d(TAG, "⏰ Listener start time (for filtering): " + lastLoginTime);
    
    // Listen for emergency SOS notifications created AFTER listener start
    emergencySOSListener = db.collection("Sagip")
      .document("users")
      .collection("rescuer")
      .document(userId)
      .collection("emergencyNotifications")
      .whereGreaterThan("timestamp", lastLoginTime)  // FILTER OLD NOTIFICATIONS
      .orderBy("timestamp", Query.Direction.DESCENDING)
      .addSnapshotListener((querySnapshot, error) -> {
        // Only process real-time emergencies
      });
}
```

### 2. EmergencySOSBackgroundService.java Changes

**File Location:** `app/src/main/java/com/example/sagip_prototype/EmergencySOSBackgroundService.java`

**Changes Made:**
- Added static variable `listenerStartTime` to track when service starts listening
- Added `whereGreaterThan("timestamp", listenerStartTime)` to the emergency listener query
- This ensures only NEW emergencies trigger background notifications

**Code After Fix:**
```java
// Static variable to track listener start time
private static long listenerStartTime = 0;

private void startEmergencySOSListener() {
    // ... validation code ...
    
    // Set listener start time to filter out old notifications
    listenerStartTime = System.currentTimeMillis();
    
    Log.d(TAG, "⏰ Listener start time (for filtering): " + listenerStartTime);
    
    // Listen for emergency SOS notifications created AFTER service start
    emergencyListener = db.collection("Sagip")
      .document("users")
      .collection("rescuer")
      .document(userId)
      .collection("emergencyNotifications")
      .whereGreaterThan("timestamp", listenerStartTime)  // FILTER OLD NOTIFICATIONS
      .orderBy("timestamp", Query.Direction.DESCENDING)
      .addSnapshotListener((querySnapshot, error) -> {
        // Only process real-time emergencies
      });
}
```

## How It Works Now

### Scenario 1: Rescuer Logs In (Dashboard Active)
1. `startEmergencySOSListener()` is called in `onResume()`
2. `lastLoginTime` is set to current timestamp (e.g., 1730000000000)
3. Query filters: `WHERE timestamp > 1730000000000`
4. Result: **Only emergencies created AFTER login will trigger alerts**
5. Old emergencies are ignored, no sounds or popups for them

### Scenario 2: App in Background (Background Service)
1. `EmergencySOSBackgroundService` starts
2. `listenerStartTime` is set to current timestamp
3. Query filters: `WHERE timestamp > listenerStartTime`
4. Result: **Only NEW emergencies trigger background notifications**
5. Old emergencies are ignored

### Scenario 3: Real-Time Emergency Occurs
1. Senior sends SOS at timestamp 1730000100000
2. Firebase creates notification in rescuer's collection
3. Snapshot listener detects NEW document with timestamp > lastLoginTime
4. Alert is triggered ✅
5. Sound plays, popup shows, rescuer is notified

### Scenario 4: Old Emergency Exists (Before Login)
1. Old SOS from 2 hours ago at timestamp 1729990000000
2. Rescuer logs in at timestamp 1730000000000
3. Query filters: `WHERE timestamp > 1730000000000`
4. Old emergency (1729990000000) does NOT match filter
5. No alert triggered ✅
6. Rescuer is NOT bothered by old emergencies

## Testing Recommendations

### Test Case 1: Login with Old Emergencies
1. Create test emergency in Firestore with old timestamp (e.g., 1 hour ago)
2. Mark it as unread (`isRead: false`)
3. Log in as rescuer
4. **Expected:** No alert should trigger for the old emergency
5. **Expected:** Only new emergencies after login should alert

### Test Case 2: Real-Time Emergency
1. Log in as rescuer
2. Send SOS from senior account
3. **Expected:** Alert triggers immediately for rescuer
4. **Expected:** Sound plays, popup shows
5. **Expected:** Background notification appears if app is closed

### Test Case 3: Multiple Old Emergencies
1. Create 5 test emergencies with old timestamps
2. Mark all as unread
3. Log in as rescuer
4. **Expected:** NO alerts for any old emergencies
5. **Expected:** Clean login experience

### Test Case 4: Service Restart
1. Log in as rescuer
2. Force stop background service
3. Service restarts automatically
4. **Expected:** `listenerStartTime` is reset to current time
5. **Expected:** Only emergencies after restart trigger alerts

## Benefits of This Fix

✅ **Clean Login Experience** - No alert spam when rescuers log in

✅ **Real-Time Alerts Only** - Rescuers only hear about current emergencies

✅ **Reduced Alert Fatigue** - No false alarms from old emergencies

✅ **Better User Experience** - Rescuers can focus on active emergencies

✅ **Consistent Behavior** - Works same way in both dashboard and background service

✅ **Proper Filtering** - Uses Firestore query filtering (server-side) for efficiency

## Important Notes

1. **Existing Notifications**: Old unread notifications will still exist in Firestore, but won't trigger alerts

2. **Cleanup**: Consider implementing a cleanup job to mark old notifications as read or delete them

3. **Timestamp Field**: This fix requires that all emergency notifications have a `timestamp` field

4. **Index**: Firestore may require a composite index for `timestamp` ordering with `whereGreaterThan`

5. **Time Sync**: Relies on device time being reasonably accurate (within a few minutes)

## Potential Edge Cases

### Edge Case 1: Clock Skew
- If device clock is significantly wrong, filtering might fail
- Mitigation: Use server timestamp in emergency creation

### Edge Case 2: Very Fast Emergency (< 1 second)
- If emergency is created in the same millisecond as listener start
- Current behavior: Would be filtered out (timestamp NOT greater than)
- Mitigation: Use `>=` instead of `>` if this is a concern

### Edge Case 3: Service Restart During Active Emergency
- If service restarts while emergency is ongoing
- New listener would have new `listenerStartTime`
- Ongoing emergency might be filtered out
- Mitigation: Already handled by dashboard listener (redundant notifications)

## Files Modified

1. **Rescuer_Dashboard.java**
   - Line 2603: Set `lastLoginTime` when starting listener
   - Line 2614: Added `whereGreaterThan()` filter to query

2. **EmergencySOSBackgroundService.java**
   - Line 54: Added static `listenerStartTime` variable
   - Line 387: Set `listenerStartTime` when starting listener
   - Line 401: Added `whereGreaterThan()` filter to query

## Deployment Checklist

- [✓] Code changes implemented
- [✓] No linter errors
- [ ] Test with old emergencies in database
- [ ] Test with real-time emergencies
- [ ] Verify background service behavior
- [ ] Check Firestore indexes (may need to create composite index)
- [ ] Deploy to test environment
- [ ] User acceptance testing
- [ ] Deploy to production

## Related Documentation

- Firebase Firestore Queries: https://firebase.google.com/docs/firestore/query-data/queries
- Snapshot Listeners: https://firebase.google.com/docs/firestore/query-data/listen
- Composite Indexes: https://firebase.google.com/docs/firestore/query-data/indexing

---

**Fix Date:** October 30, 2025  
**Developer:** AI Assistant (Claude)  
**Issue:** Rescuers getting alerts for all SOS emergencies on login  
**Status:** ✅ Fixed and Ready for Testing


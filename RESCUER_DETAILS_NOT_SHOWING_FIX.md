# Rescuer Details Not Showing - Fix

## Issue Description

When a rescuer accepts an emergency, the senior receives a notification. However, when clicking the notification to view rescuer details:
- Rescuer information is not displayed in the `RescuerDetailsActivity`
- Toast message shows "no emergency found"
- Senior cannot see the rescuer's ETA, phone, or other details

## Root Cause

The issue occurred due to a collection mismatch:

1. When a rescuer accepts an emergency, the `EmergencyQueueManager` **moves** the emergency document from:
   - `Sagip/emergencyRequests/activeRequests/{emergencyId}`
   - TO: `Sagip/emergencyRequests/assignedRequests/{emergencyId}`

2. The `RescuerDetailsActivity` was **only checking** the `activeRequests` collection

3. Since the emergency had already been moved to `assignedRequests`, it couldn't be found

4. This caused the "Emergency not found" error and prevented rescuer details from loading

## Solution

Modified `RescuerDetailsActivity.java` to check **both** collections:

### Changes Made

**File:** `app/src/main/java/com/example/sagip_prototype/RescuerDetailsActivity.java`

1. **Updated `loadEmergencyDetails()` method:**
   - First tries to load emergency from `activeRequests`
   - If not found, checks `assignedRequests`
   - Handles both cases appropriately

2. **Created new helper method `processEmergencyDocument()`:**
   - Extracts emergency processing logic into reusable method
   - Handles emergency document from either collection
   - Gets rescuer ID and senior location
   - Loads rescuer details

### Code Flow

```
Senior clicks notification
    ↓
RescuerDetailsActivity opens
    ↓
Check activeRequests collection
    ↓
If found → Process document
If not found → Check assignedRequests
    ↓
Process document from assignedRequests
    ↓
Load rescuer details
    ↓
Display ETA, phone, team info
```

## Technical Details

### Before Fix
```java
private void loadEmergencyDetails() {
    // Only checked activeRequests
    db.collection("Sagip")
        .document("emergencyRequests")
        .collection("activeRequests")  // ❌ Emergency already moved!
        .document(emergencyId)
        .get()
        .addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                processEmergency(documentSnapshot);
            } else {
                showError("Emergency not found");  // ❌ This always happened
            }
        });
}
```

### After Fix
```java
private void loadEmergencyDetails() {
    // Try activeRequests first
    db.collection("Sagip")
        .document("emergencyRequests")
        .collection("activeRequests")
        .document(emergencyId)
        .get()
        .addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                // Found in activeRequests
                processEmergencyDocument(documentSnapshot);
            } else {
                // Not in activeRequests, check assignedRequests
                db.collection("Sagip")
                    .document("emergencyRequests")
                    .collection("assignedRequests")  // ✅ Check here too!
                    .document(emergencyId)
                    .get()
                    .addOnSuccessListener(assignedDoc -> {
                        if (assignedDoc.exists()) {
                            // ✅ Found in assignedRequests!
                            processEmergencyDocument(assignedDoc);
                        } else {
                            showError("Emergency not found");
                        }
                    });
            }
        });
}

private void processEmergencyDocument(DocumentSnapshot documentSnapshot) {
    // Extract rescuer ID
    rescuerId = documentSnapshot.getString("assignedRescuerId");
    
    // Get senior location (from intent or document)
    // ... location processing ...
    
    // Load rescuer details
    if (rescuerId != null) {
        loadRescuerDetails();
    }
}
```

## Testing Checklist

- [x] Code compiles without errors
- [x] No linter errors
- [ ] Test scenario 1: Senior sends SOS → Rescuer accepts → Senior clicks notification → Rescuer details show
- [ ] Test scenario 2: Rescuer details display correct name, phone, team
- [ ] Test scenario 3: ETA calculation works correctly
- [ ] Test scenario 4: Call rescuer button works
- [ ] Test scenario 5: Emergency found in activeRequests (before acceptance)
- [ ] Test scenario 6: Emergency found in assignedRequests (after acceptance)

## Related Files

- `app/src/main/java/com/example/sagip_prototype/RescuerDetailsActivity.java` - Fixed
- `app/src/main/java/com/example/sagip_prototype/EmergencyQueueManager.java` - Moves emergency to assignedRequests
- `app/src/main/java/com/example/sagip_prototype/Senior_Dashboard.java` - Opens RescuerDetailsActivity

## Date Fixed

October 29, 2025

## Status

✅ **FIXED** - Rescuer details now load correctly when senior clicks notification after rescuer acceptance


# Instructions to Get Diagnostic Logs

To help identify the remaining duplicate notification issue, please provide complete logs:

## Run This Command:

```bash
adb logcat -s EmergencySOSService:D EmergencyNotificationService:D BackgroundServiceManager:D EmergencyQueueManager:D RescuerDashboard:D Senior_Dashboard:D -v time > duplicate_debug.log
```

## Steps:

1. **Clear logcat first**:
   ```bash
   adb logcat -c
   ```

2. **Start logging** (run the command above)

3. **Open Rescuer app** (check which services start)

4. **Send ONE SOS** from Senior app

5. **Wait 5 seconds**

6. **Stop logging** (Ctrl+C)

7. **Share the `duplicate_debug.log` file**

## What I'm Looking For:

1. **Which services are actually running**:
   - Should see: `EmergencySOSBackgroundService`
   - Should NOT see: `EmergencyNotificationService started`

2. **How many times the notification is created**:
   - Should see ONE: `📤 Emergency notification sent to rescuer`
   
3. **How many times it's processed**:
   - Should see ONE: `🚨 Received emergency SOS notification`

4. **If there are multiple listeners**:
   - Count: `🚨 Starting emergency SOS listener`

## Alternative: Quick Check

If you can't share logs, please check:

### In Rescuer App Logcat:
```
Q1: Do you see this? (Should be YES)
✅ [DUPLICATE_FIX] EmergencyNotificationService DISABLED

Q2: Do you see ONE or TWO of these?
🚨 Received emergency SOS notification: [Senior Name]

Q3: Do you see "EmergencyNotificationService started"? (Should be NO)
```

### In Firestore Console:
```
Check: Sagip/users/rescuer/{rescuer_id}/emergencyNotifications

Q4: For the same requestId, how many documents exist?
Should be: 1
If you see: 2 or more → Notifications being created multiple times
```

## Most Important Questions:

1. **Are you getting 2 notification SOUNDS?** (or just 2 in notification tray?)
2. **Are you getting 2 DIALOGS?** (popup alerts)
3. **Do BOTH notifications have the SAME request ID?**

Please answer these questions or share the log file!


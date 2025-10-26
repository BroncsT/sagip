# Firebase Function Deployment Instructions

## Problem Fixed
The rescuer notification issue has been fixed! The Firebase function now listens to the correct collection where emergency SOS notifications are saved.

## What was changed
- Added a new Firebase Cloud Function `sendEmergencySOSNotification` in `firebase-functions/index.js`
- This function listens to: `Sagip/users/rescuer/{rescuerId}/emergencyNotifications`
- It sends FCM push notifications to rescuers when a senior calls for SOS

## Deployment Steps

### Step 1: Install Node.js
1. Download Node.js from: https://nodejs.org/en/download/
2. Download the **LTS version** (recommended for most users)
3. Run the installer and follow the installation wizard
4. Restart your terminal/PowerShell after installation

### Step 2: Verify Node.js Installation
Open a new PowerShell window and run:
```bash
node --version
npm --version
```
Both commands should show version numbers.

### Step 3: Install Firebase CLI
Run this command in PowerShell:
```bash
npm install -g firebase-tools
```

### Step 4: Login to Firebase
```bash
firebase login
```
This will open a browser window for you to login with your Google account.

### Step 5: Navigate to Project Directory
```bash
cd "C:\Users\Anthony\StudioProjects\sagip"
```

### Step 6: Deploy the Functions
```bash
firebase deploy --only functions
```

This will deploy all Firebase functions including the new `sendEmergencySOSNotification` function.

### Step 7: Verify Deployment
After deployment completes, you should see output like:
```
✔  functions: Finished running predeploy script.
✔  functions[sendEmergencySOSNotification(us-central1)]: Successful create operation.
✔  Deploy complete!
```

### Step 8: Test the Fix
1. Open the senior app and log in as a senior
2. Press the SOS button
3. Check if the rescuer receives a push notification immediately

## Troubleshooting

### If Firebase CLI fails to install
Try running PowerShell as Administrator:
1. Right-click on PowerShell
2. Select "Run as Administrator"
3. Try the installation command again

### If deployment fails with permissions error
Make sure you're logged into the correct Firebase account:
```bash
firebase logout
firebase login
```

### If notifications still don't work after deployment
1. Check Firebase Console → Functions → Logs
2. Look for any errors in the function execution
3. Verify that rescuers have valid FCM tokens saved in their user documents

## Alternative: Manual Deployment via Cloud Shell

If you can't install Node.js locally, you can use Google Cloud Shell:

1. Go to Firebase Console: https://console.firebase.google.com/
2. Click the terminal icon (>_) in the top right corner
3. This opens Cloud Shell with Node.js and Firebase CLI pre-installed
4. Clone your project or upload the files
5. Run `firebase deploy --only functions`

## Need Help?
If you encounter any issues during deployment, please share:
- The exact error message
- The output from `node --version` and `npm --version`
- Any logs from Firebase Console → Functions → Logs


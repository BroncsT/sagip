#!/bin/bash

echo "Deploying Firebase Function for FCM notifications..."
echo

# Check if Firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    echo "Firebase CLI is not installed. Please install it first:"
    echo "npm install -g firebase-tools"
    exit 1
fi

# Login to Firebase (if not already logged in)
echo "Logging in to Firebase..."
firebase login

# Deploy the function
echo
echo "Deploying Firebase Function..."
firebase deploy --only functions

echo
echo "Firebase Function deployed successfully!"
echo "Your FCM notifications should now work when the app is closed."

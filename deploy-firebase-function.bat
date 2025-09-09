@echo off
echo Deploying Firebase Function for FCM notifications...
echo.

REM Check if Firebase CLI is installed
firebase --version >nul 2>&1
if %errorlevel% neq 0 (
    echo Firebase CLI is not installed. Please install it first:
    echo npm install -g firebase-tools
    pause
    exit /b 1
)

REM Login to Firebase (if not already logged in)
echo Logging in to Firebase...
firebase login

REM Deploy the function
echo.
echo Deploying Firebase Function...
firebase deploy --only functions

echo.
echo Firebase Function deployed successfully!
echo Your FCM notifications should now work when the app is closed.
pause

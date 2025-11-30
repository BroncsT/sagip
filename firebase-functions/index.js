const functions = require('firebase-functions');
const admin = require('firebase-admin');
const nodemailer = require('nodemailer');

// Initialize Firebase Admin SDK
admin.initializeApp();

// Configure email transporter
// IMPORTANT: You need to set these environment variables using:
// firebase functions:config:set email.user="your-email@gmail.com" email.pass="your-app-password"
// For Gmail, use an App Password (not your regular password): https://support.google.com/accounts/answer/185833
const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
        user: functions.config().email?.user || process.env.EMAIL_USER,
        pass: functions.config().email?.pass || process.env.EMAIL_PASS
    }
});

// Generate a unique verification token
function generateToken() {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let token = '';
    for (let i = 0; i < 32; i++) {
        token += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return token;
}

// Send Login Verification Link via Email
exports.sendLoginVerificationLink = functions.https.onCall(async (data, context) => {
    const { email, uid } = data;
    
    if (!email || !uid) {
        throw new functions.https.HttpsError('invalid-argument', 'Email and UID are required');
    }
    
    // Generate unique token
    const token = generateToken();
    const expiresAt = Date.now() + (10 * 60 * 1000); // 10 minutes expiry
    
    try {
        // Store token in Firestore
        await admin.firestore()
            .collection('Sagip')
            .doc('loginVerification')
            .collection('tokens')
            .doc(uid)
            .set({
                token: token,
                email: email,
                expiresAt: expiresAt,
                createdAt: Date.now(),
                verified: false
            });
        
        // Get the project ID for the verification URL
        const projectId = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT || 'sagip-app';
        const verificationUrl = `https://us-central1-${projectId}.cloudfunctions.net/verifyLoginLink?token=${token}&uid=${uid}`;
        
        // Send email with verification link
        const mailOptions = {
            from: `SAGIP App <${functions.config().email?.user || process.env.EMAIL_USER}>`,
            to: email,
            subject: 'SAGIP Login Verification',
            html: `
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="text-align: center; padding: 20px; background-color: #2196F3; color: white; border-radius: 10px 10px 0 0;">
                        <h1 style="margin: 0;">SAGIP</h1>
                        <p style="margin: 5px 0 0 0;">Emergency Response System</p>
                    </div>
                    <div style="padding: 30px; background-color: #f9f9f9; border: 1px solid #ddd; border-top: none; border-radius: 0 0 10px 10px;">
                        <h2 style="color: #333;">Verify Your Login</h2>
                        <p style="color: #666; font-size: 16px;">You are attempting to log in to your SAGIP account. Please click the button below to verify your login:</p>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="${verificationUrl}" style="display: inline-block; padding: 15px 40px; background-color: #2196F3; color: white; font-size: 18px; font-weight: bold; text-decoration: none; border-radius: 10px;">
                                ✓ Verify My Login
                            </a>
                        </div>
                        <p style="color: #666; font-size: 14px;">This link will expire in <strong>10 minutes</strong>.</p>
                        <p style="color: #999; font-size: 12px;">If the button doesn't work, copy and paste this link in your browser:</p>
                        <p style="color: #2196F3; font-size: 12px; word-break: break-all;">${verificationUrl}</p>
                        <p style="color: #999; font-size: 12px; margin-top: 30px;">If you did not attempt to log in, please ignore this email or contact support if you have concerns about your account security.</p>
                    </div>
                    <div style="text-align: center; padding: 15px; color: #999; font-size: 12px;">
                        <p>&copy; 2024 SAGIP Emergency Response System</p>
                    </div>
                </div>
            `
        };
        
        await transporter.sendMail(mailOptions);
        
        console.log(`Login verification link sent to ${email} for user ${uid}`);
        
        return { success: true, message: 'Verification link sent to your email' };
        
    } catch (error) {
        console.error('Error sending login verification link:', error);
        throw new functions.https.HttpsError('internal', 'Failed to send verification link');
    }
});

// HTTP endpoint to verify login link (called when user clicks the link)
exports.verifyLoginLink = functions.https.onRequest(async (req, res) => {
    const { token, uid } = req.query;
    
    if (!token || !uid) {
        return res.status(400).send(getVerificationPage(false, 'Invalid verification link.'));
    }
    
    try {
        const tokenDoc = await admin.firestore()
            .collection('Sagip')
            .doc('loginVerification')
            .collection('tokens')
            .doc(uid)
            .get();
        
        if (!tokenDoc.exists) {
            return res.status(404).send(getVerificationPage(false, 'Verification link not found or already used.'));
        }
        
        const tokenData = tokenDoc.data();
        
        // Check if token has expired
        if (Date.now() > tokenData.expiresAt) {
            await tokenDoc.ref.delete();
            return res.status(410).send(getVerificationPage(false, 'Verification link has expired. Please request a new one from the app.'));
        }
        
        // Check if token matches
        if (tokenData.token !== token) {
            return res.status(403).send(getVerificationPage(false, 'Invalid verification link.'));
        }
        
        // Mark as verified
        await tokenDoc.ref.update({ verified: true, verifiedAt: Date.now() });
        
        console.log(`Login verified successfully for user ${uid}`);
        
        return res.status(200).send(getVerificationPage(true, 'Your login has been verified! You can now return to the SAGIP app.'));
        
    } catch (error) {
        console.error('Error verifying login link:', error);
        return res.status(500).send(getVerificationPage(false, 'An error occurred. Please try again.'));
    }
});

// Check if login has been verified (called by Android app)
exports.checkLoginVerification = functions.https.onCall(async (data, context) => {
    const { uid } = data;
    
    if (!uid) {
        throw new functions.https.HttpsError('invalid-argument', 'UID is required');
    }
    
    try {
        const tokenDoc = await admin.firestore()
            .collection('Sagip')
            .doc('loginVerification')
            .collection('tokens')
            .doc(uid)
            .get();
        
        if (!tokenDoc.exists) {
            return { verified: false, expired: true };
        }
        
        const tokenData = tokenDoc.data();
        
        // Check if expired
        if (Date.now() > tokenData.expiresAt) {
            await tokenDoc.ref.delete();
            return { verified: false, expired: true };
        }
        
        if (tokenData.verified) {
            // Clean up the token after successful verification check
            await tokenDoc.ref.delete();
            return { verified: true, expired: false };
        }
        
        return { verified: false, expired: false };
        
    } catch (error) {
        console.error('Error checking login verification:', error);
        throw new functions.https.HttpsError('internal', 'Failed to check verification status');
    }
});

// Helper function to generate verification result HTML page
function getVerificationPage(success, message) {
    const bgColor = success ? '#4CAF50' : '#f44336';
    const icon = success ? '✓' : '✗';
    
    return `
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>SAGIP - Login Verification</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    min-height: 100vh;
                    margin: 0;
                    background-color: #f5f5f5;
                }
                .container {
                    text-align: center;
                    padding: 40px;
                    background: white;
                    border-radius: 20px;
                    box-shadow: 0 4px 20px rgba(0,0,0,0.1);
                    max-width: 400px;
                    margin: 20px;
                }
                .icon {
                    width: 80px;
                    height: 80px;
                    border-radius: 50%;
                    background-color: ${bgColor};
                    color: white;
                    font-size: 40px;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    margin: 0 auto 20px;
                }
                h1 {
                    color: #333;
                    margin-bottom: 10px;
                }
                p {
                    color: #666;
                    font-size: 16px;
                    line-height: 1.5;
                }
                .logo {
                    color: #2196F3;
                    font-size: 24px;
                    font-weight: bold;
                    margin-bottom: 30px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="logo">🚨 SAGIP</div>
                <div class="icon">${icon}</div>
                <h1>${success ? 'Verified!' : 'Verification Failed'}</h1>
                <p>${message}</p>
            </div>
        </body>
        </html>
    `;
}

// Function to send FCM notifications to all rescuers when hospital status updates
exports.sendHospitalUpdateNotification = functions.firestore
    .document('Sagip/users/rescuer/{rescuerId}/notifications/{notificationId}')
    .onCreate(async (snap, context) => {
        const notificationData = snap.data();
        
        // Check if this is a hospital status update notification
        if (notificationData.type === 'hospital_status_update' && 
            (notificationData.source === 'fcm_real' || notificationData.source === 'native_fcm')) {
            
            console.log('Hospital status update detected:', notificationData);
            
            // Get all rescuer FCM tokens
            const rescuersSnapshot = await admin.firestore()
                .collection('Sagip/users/rescuer')
                .get();
            
            const tokens = [];
            rescuersSnapshot.forEach(doc => {
                const fcmToken = doc.data().fcmToken;
                if (fcmToken) {
                    tokens.push(fcmToken);
                }
            });
            
            if (tokens.length === 0) {
                console.log('No FCM tokens found for rescuers');
                return;
            }
            
            // Prepare the FCM message
            const hospitalName = notificationData.hospitalName;
            const hospitalStatus = notificationData.hospitalStatus;
            const availableBeds = notificationData.availableBeds;
            const availableDoctors = notificationData.availableDoctors;
            
            const statusEmoji = getStatusEmoji(hospitalStatus);
            
            const message = {
                notification: {
                    title: '🏥 Hospital Status Updated',
                    body: `${hospitalName} is now ${statusEmoji} ${hospitalStatus.toUpperCase()}`
                },
                data: {
                    type: 'hospital_status_update',
                    hospitalName: hospitalName,
                    hospitalStatus: hospitalStatus,
                    availableBeds: availableBeds.toString(),
                    availableDoctors: availableDoctors.toString(),
                    timestamp: notificationData.timestamp.toString()
                },
                android: {
                    priority: 'high',
                    notification: {
                        sound: 'default',
                        vibrateTimingsMillis: [0, 500, 200, 500],
                        lightSettings: {
                            color: {
                                red: 0.13,
                                green: 0.59,
                                blue: 0.95
                            },
                            lightOnDurationMillis: 1000,
                            lightOffDurationMillis: 1000
                        }
                    }
                },
                tokens: tokens
            };
            
            try {
                // Send individual FCM messages for better reliability when app is closed
                const sendPromises = tokens.map(async (token) => {
                    try {
                        const individualMessage = {
                            ...message,
                            token: token
                        };
                        delete individualMessage.tokens; // Remove tokens array for individual send
                        
                        const response = await admin.messaging().send(individualMessage);
                        console.log(`Hospital notification sent successfully to token: ${token.substring(0, 20)}...`);
                        return { success: true, token };
                    } catch (error) {
                        console.error(`Failed to send hospital notification to token ${token.substring(0, 20)}...:`, error);
                        return { success: false, token, error };
                    }
                });
                
                const results = await Promise.all(sendPromises);
                const successCount = results.filter(r => r.success).length;
                const failureCount = results.filter(r => !r.success).length;
                
                console.log(`Hospital notification sent to ${successCount} rescuers`);
                console.log(`Failed to send to ${failureCount} rescuers`);
                
                // Clean up invalid tokens
                const failedTokens = results.filter(r => !r.success).map(r => r.token);
                if (failedTokens.length > 0) {
                    await cleanupInvalidTokens(failedTokens);
                }
                
            } catch (error) {
                console.error('Error sending FCM messages:', error);
            }
        }
    });

// Function to send FCM notifications for emergency help requests
exports.sendEmergencyNotification = functions.firestore
    .document('Sagip/users/{userType}/{userId}/notifications/{notificationId}')
    .onCreate(async (snap, context) => {
        const notificationData = snap.data();
        
        // Check if this is an emergency help request notification
        if (notificationData.type === 'emergency_help_request' && 
            notificationData.source === 'native_fcm_emergency') {
            
            console.log('Emergency help request detected:', notificationData);
            
            // Get the specific user's FCM token
            const userType = context.params.userType;
            const userId = context.params.userId;
            
            const userDoc = await admin.firestore()
                .collection('Sagip/users/' + userType)
                .doc(userId)
                .get();
            
            if (!userDoc.exists) {
                console.log('User document not found:', userId);
                return;
            }
            
            const fcmToken = userDoc.data().fcmToken;
            if (!fcmToken) {
                console.log('No FCM token found for user:', userId);
                return;
            }
            
            const tokens = [fcmToken];
            
            // Prepare the FCM message
            const seniorName = notificationData.seniorName;
            const emergencyType = notificationData.emergencyType;
            const location = notificationData.location;
            const phoneNumber = notificationData.phoneNumber;
            
            const emergencyEmoji = getEmergencyEmoji(emergencyType);
            
            const message = {
                notification: {
                    title: '🚨 EMERGENCY HELP REQUEST',
                    body: `${seniorName} needs ${emergencyEmoji} ${emergencyType}`
                },
                data: {
                    type: 'emergency_help_request',
                    seniorName: seniorName,
                    emergencyType: emergencyType,
                    location: location,
                    phoneNumber: phoneNumber,
                    timestamp: notificationData.timestamp.toString()
                },
                tokens: tokens
            };
            
            try {
                // Send individual FCM messages for better reliability when app is closed
                const sendPromises = tokens.map(async (token) => {
                    try {
                        const individualMessage = {
                            ...message,
                            token: token
                        };
                        delete individualMessage.tokens; // Remove tokens array for individual send
                        
                        const response = await admin.messaging().send(individualMessage);
                        console.log(`Emergency notification sent successfully to token: ${token.substring(0, 20)}...`);
                        return { success: true, token };
                    } catch (error) {
                        console.error(`Failed to send emergency notification to token ${token.substring(0, 20)}...:`, error);
                        return { success: false, token, error };
                    }
                });
                
                const results = await Promise.all(sendPromises);
                const successCount = results.filter(r => r.success).length;
                const failureCount = results.filter(r => !r.success).length;
                
                console.log(`Emergency notification sent to ${successCount} users`);
                console.log(`Failed to send to ${failureCount} users`);
                
            } catch (error) {
                console.error('Error sending emergency notifications:', error);
            }
        }
    });

// Helper function to get status emoji
function getStatusEmoji(status) {
    switch (status.toLowerCase()) {
        case 'available':
            return '🟢';
        case 'busy':
            return '🟡';
        case 'full':
            return '🔴';
        default:
            return '⚪';
    }
}

// Helper function to get emergency emoji
function getEmergencyEmoji(emergencyType) {
    switch (emergencyType.toLowerCase()) {
        case 'medical':
            return '🏥';
        case 'fall':
            return '⚠️';
        case 'accident':
            return '🚑';
        case 'fire':
            return '🔥';
        case 'police':
            return '👮';
        case 'other':
            return '🆘';
        default:
            return '🚨';
    }
}

// Function to send FCM notifications for emergency SOS alerts to rescuers
exports.sendEmergencySOSNotification = functions.firestore
    .document('Sagip/users/rescuer/{rescuerId}/emergencyNotifications/{notificationId}')
    .onCreate(async (snap, context) => {
        const notificationData = snap.data();
        const rescuerId = context.params.rescuerId;
        
        console.log('Emergency SOS notification detected for rescuer:', rescuerId);
        console.log('Notification data:', notificationData);
        
        // Get the rescuer's FCM token
        const rescuerDoc = await admin.firestore()
            .collection('Sagip/users/rescuer')
            .doc(rescuerId)
            .get();
        
        if (!rescuerDoc.exists) {
            console.log('Rescuer document not found:', rescuerId);
            return;
        }
        
        const fcmToken = rescuerDoc.data().fcmToken;
        if (!fcmToken) {
            console.log('No FCM token found for rescuer:', rescuerId);
            return;
        }
        
        // Prepare the FCM message
        const seniorName = notificationData.seniorName || 'A senior';
        const locationAddress = notificationData.locationAddress || 'Unknown location';
        const emergencyType = notificationData.emergencyType || 'medical';
        
        const emergencyEmoji = getEmergencyEmoji(emergencyType);
        
        const message = {
            notification: {
                title: '🚨 EMERGENCY SOS ALERT',
                body: `${seniorName} needs ${emergencyEmoji} ${emergencyType} help at ${locationAddress}`
            },
            data: {
                type: 'emergency_sos',
                seniorName: seniorName,
                seniorPhone: notificationData.seniorPhone || '',
                locationAddress: locationAddress,
                emergencyType: emergencyType,
                requestId: notificationData.requestId || '',
                timestamp: notificationData.timestamp ? notificationData.timestamp.toString() : Date.now().toString(),
                seniorLat: notificationData.seniorLat ? notificationData.seniorLat.toString() : '0',
                seniorLng: notificationData.seniorLng ? notificationData.seniorLng.toString() : '0'
            },
            android: {
                priority: 'high',
                notification: {
                    sound: 'default',
                    channelId: 'emergency_sos_channel',
                    priority: 'max',
                    vibrateTimingsMillis: [0, 1000, 500, 1000],
                    lightSettings: {
                        color: {
                            red: 1.0,
                            green: 0.0,
                            blue: 0.0
                        },
                        lightOnDurationMillis: 1000,
                        lightOffDurationMillis: 1000
                    }
                }
            },
            token: fcmToken
        };
        
        try {
            const response = await admin.messaging().send(message);
            console.log(`Emergency SOS notification sent successfully to rescuer ${rescuerId}:`, response);
        } catch (error) {
            console.error(`Failed to send emergency SOS notification to rescuer ${rescuerId}:`, error);
            
            // If token is invalid, clean it up
            if (error.code === 'messaging/invalid-registration-token' ||
                error.code === 'messaging/registration-token-not-registered') {
                await cleanupInvalidTokens([fcmToken]);
            }
        }
    });

// Helper function to clean up invalid FCM tokens
async function cleanupInvalidTokens(invalidTokens) {
    const batch = admin.firestore().batch();
    
    for (const token of invalidTokens) {
        const rescuersSnapshot = await admin.firestore()
            .collection('Sagip/users/rescuer')
            .where('fcmToken', '==', token)
            .get();
        
        rescuersSnapshot.forEach(doc => {
            batch.update(doc.ref, {
                fcmToken: admin.firestore.FieldValue.delete(),
                tokenUpdatedAt: admin.firestore.FieldValue.delete()
            });
        });
    }
    
    await batch.commit();
    console.log('Cleaned up invalid FCM tokens');
}

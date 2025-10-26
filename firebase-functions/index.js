const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
admin.initializeApp();

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

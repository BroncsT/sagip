package com.example.sagip_prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;


public class GlobalTimerService extends Service {
    private static final String TAG = "GlobalTimerService";
    private static final long STATUS_UPDATE_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes
    
    private CountDownTimer globalTimer;
    private long timerStartTime;
    private long timerDuration;
    private boolean isTimerRunning = false;
    
    // List of listeners to notify when timer updates
    private List<TimerUpdateListener> listeners = new ArrayList<>();
    
    // Binder for activity to communicate with service
    private final IBinder binder = new TimerBinder();
    
    public interface TimerUpdateListener {
        void onTimerUpdate(long remainingTimeMs);
        void onTimerFinished();
    }
    
    public class TimerBinder extends Binder {
        public GlobalTimerService getService() {
            return GlobalTimerService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "GlobalTimerService created");
        
        // Create notification channel for foreground service
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "GlobalTimerService started with intent: " + (intent != null ? intent.getAction() : "null"));
        
        if (intent != null) {
            String action = intent.getStringExtra("action");
            Log.d(TAG, "Action received: " + action);
            
            if ("start_timer".equals(action)) {
                long timeRemainingMs = intent.getLongExtra("time_remaining_ms", STATUS_UPDATE_INTERVAL_MS);
                Log.d(TAG, "Starting timer with " + (timeRemainingMs / 1000) + " seconds");
                startGlobalTimer(timeRemainingMs);
            } else if ("stop_timer".equals(action)) {
                Log.d(TAG, "Stopping timer");
                stopGlobalTimer();
            } else if ("reset_timer".equals(action)) {
                Log.d(TAG, "Resetting timer");
                resetGlobalTimer();
            } else if ("ensure_running".equals(action)) {
                Log.d(TAG, "Ensuring timer is running");
                if (!isTimerRunning) {
                    Log.d(TAG, "Timer not running, starting default timer");
                    startGlobalTimer(STATUS_UPDATE_INTERVAL_MS);
                } else {
                    Log.d(TAG, "Timer already running");
                }
            } else {
                // No action specified, check if timer should be running
                Log.d(TAG, "No action specified, checking timer state");
                if (!isTimerRunning) {
                    Log.d(TAG, "No timer running, starting default timer");
                    startGlobalTimer(STATUS_UPDATE_INTERVAL_MS);
                }
            }
        } else {
            // Service started without intent, start default timer
            Log.d(TAG, "Service started without intent, starting default timer");
            if (!isTimerRunning) {
                startGlobalTimer(STATUS_UPDATE_INTERVAL_MS);
            }
        }
        
        // Start as foreground service to ensure it keeps running
        startForegroundService();
        
        return START_STICKY; // Restart service if killed
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "GlobalTimerService destroyed");
        stopGlobalTimer();
    }
    
    /**
     * Starts the global countdown timer
     */
    public void startGlobalTimer(long timeRemainingMs) {
        Log.d(TAG, "Starting global timer for: " + (timeRemainingMs / 1000) + " seconds");
        
        // Cancel existing timer if running
        if (globalTimer != null) {
            globalTimer.cancel();
        }
        
        // Store timer information
        timerStartTime = System.currentTimeMillis();
        timerDuration = timeRemainingMs;
        isTimerRunning = true;
        
        globalTimer = new CountDownTimer(timeRemainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Notify all listeners of timer update
                notifyTimerUpdate(millisUntilFinished);
            }
            
            @Override
            public void onFinish() {
                Log.d(TAG, "Global timer finished");
                isTimerRunning = false;
                // Notify all listeners that timer finished
                notifyTimerFinished();
            }
        };
        
        globalTimer.start();
    }
    
    /**
     * Stops the global timer
     */
    public void stopGlobalTimer() {
        Log.d(TAG, "Stopping global timer");
        if (globalTimer != null) {
            globalTimer.cancel();
            globalTimer = null;
        }
        isTimerRunning = false;
    }
    
    /**
     * Resets the global timer to full duration
     */
    public void resetGlobalTimer() {
        Log.d(TAG, "Resetting global timer");
        startGlobalTimer(STATUS_UPDATE_INTERVAL_MS);
    }
    
    /**
     * Gets the current remaining time
     */
    public long getRemainingTime() {
        if (isTimerRunning && timerStartTime > 0) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - timerStartTime;
            long remainingTime = timerDuration - elapsedTime;
            return Math.max(0, remainingTime);
        }
        return 0;
    }
    
    /**
     * Checks if timer is currently running
     */
    public boolean isTimerRunning() {
        return isTimerRunning;
    }
    
    /**
     * Restores timer state from database
     * This is called when the service starts to ensure timer state is correct
     */
    public void restoreTimerStateFromDatabase(String userId) {
        if (userId == null) {
            Log.w(TAG, "Cannot restore timer state - userId is null");
            return;
        }
        
        Log.d(TAG, "Restoring timer state from database for userId: " + userId);
        
        // This method should be called from the activity to restore timer state
        // The actual database query will be handled by the activity
        // This is just a placeholder for the service to know it should restore state
    }
    
    /**
     * Adds a listener for timer updates
     */
    public void addTimerUpdateListener(TimerUpdateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            Log.d(TAG, "Added timer listener, total listeners: " + listeners.size());
        }
    }
    
    /**
     * Removes a listener for timer updates
     */
    public void removeTimerUpdateListener(TimerUpdateListener listener) {
        listeners.remove(listener);
        Log.d(TAG, "Removed timer listener, total listeners: " + listeners.size());
    }
    
    /**
     * Notifies all listeners of timer update
     */
    private void notifyTimerUpdate(long remainingTimeMs) {
        // Update notification with current timer status
        updateNotification(remainingTimeMs);
        
        for (TimerUpdateListener listener : listeners) {
            try {
                listener.onTimerUpdate(remainingTimeMs);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying timer listener", e);
            }
        }
    }
    
    /**
     * Notifies all listeners that timer finished
     */
    private void notifyTimerFinished() {
        for (TimerUpdateListener listener : listeners) {
            try {
                listener.onTimerFinished();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying timer listener", e);
            }
        }
    }
    
    /**
     * Creates notification channel for foreground service
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "timer_service_channel",
                "Timer Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the hospital status timer running continuously");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Starts the service as a foreground service
     */
    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, Hospital_Dashboard.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new Notification.Builder(this, "timer_service_channel")
            .setContentTitle("Hospital Status Timer")
            .setContentText("Timer is running continuously")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires foreground service type
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }
        Log.d(TAG, "Started as foreground service");
    }
    
    /**
     * Updates the notification with current timer status
     */
    private void updateNotification(long remainingTimeMs) {
        if (remainingTimeMs > 0) {
            long totalSeconds = remainingTimeMs / 1000;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            
            String timeText = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            
            Intent notificationIntent = new Intent(this, Hospital_Dashboard.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            Notification notification = new Notification.Builder(this, "timer_service_channel")
                .setContentTitle("Hospital Status Timer")
                .setContentText("Next update in: " + timeText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
            
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(1, notification);
        }
    }
}

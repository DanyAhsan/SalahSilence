package com.dany.salahsilence;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class SilentModeService extends Service {
    private static final String CHANNEL_ID = "SalahSilenceServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        acquireWakeLock();
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "SalahSilence::SilentModeWakeLock");
        wakeLock.acquire(10*60*1000L /*10 minutes*/);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getStringExtra("action");
        String prayer = intent.getStringExtra("prayer");

        // Create notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SalahSilence Active")
                .setContentText("Managing silent mode during " + prayer)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();

        // Start foreground service
        startForeground(NOTIFICATION_ID, notification);

        // Handle silent mode
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !notificationManager.isNotificationPolicyAccessGranted()) {
            stopSelf();
            return START_STICKY;
        }

        try {
            if ("start".equals(action)) {
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                Log.d("SilentModeService", "Silent mode activated for " + prayer);
            } else if ("end".equals(action)) {
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                Log.d("SilentModeService", "Silent mode deactivated for " + prayer);
                stopSelf();
            }
        } catch (SecurityException e) {
            Log.e("SilentModeService", "Security exception: " + e.getMessage());
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "SalahSilence Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            serviceChannel.setDescription("Manages silent mode during prayer times");
            serviceChannel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 
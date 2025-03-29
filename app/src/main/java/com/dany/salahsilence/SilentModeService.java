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

import androidx.core.app.NotificationCompat;

public class SilentModeService extends Service {
    private static final String CHANNEL_ID = "SalahSilenceServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getStringExtra("action");
        String prayer = intent.getStringExtra("prayer");

        // Create notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SalahSilence Active")
                .setContentText("Managing silent mode during " + prayer)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
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
            return START_NOT_STICKY;
        }

        try {
            if ("start".equals(action)) {
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            } else if ("end".equals(action)) {
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                stopSelf();
            }
        } catch (SecurityException e) {
            stopSelf();
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "SalahSilence Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 
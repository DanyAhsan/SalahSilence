package com.dany.salahsilence;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.Calendar;

public class SilentModeReceiver extends BroadcastReceiver {
    
    private static final String CHANNEL_ID = "SalahSilenceChannel";
    private static final String ACTION_SILENT_MODE_START = "com.dany.salahsilence.ACTION_SILENT_MODE_START";
    private static final String ACTION_SILENT_MODE_END = "com.dany.salahsilence.ACTION_SILENT_MODE_END";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Check if Do Not Disturb permission is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !notificationManager.isNotificationPolicyAccessGranted()) {

            // Create a notification to prompt user to grant permission
            showDoNotDisturbPermissionNotification(context);
            return;
        }

        String action = intent.getAction();
        String prayer = intent.getStringExtra("prayer");
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        try {
            if (ACTION_SILENT_MODE_START.equals(action)) {
                // Attempt to set silent mode
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                showNotification(context, prayer, "Silent mode enabled for " + prayer);
            } else if (ACTION_SILENT_MODE_END.equals(action)) {
                // Restore normal mode
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                showNotification(context, prayer, "Silent mode disabled for " + prayer);
            }
        } catch (SecurityException e) {
            // If permission is revoked, show a notification
            showDoNotDisturbPermissionNotification(context);
        }
    }

    private void showDoNotDisturbPermissionNotification(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create notification channel for Android Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SalahSilence Permissions",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        // Create an intent to open Do Not Disturb access settings
        Intent settingsIntent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("SalahSilence Permission Required")
                .setContentText("Please grant Do Not Disturb access to enable silent mode")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(999, builder.build());
    }

    public static void scheduleSilentMode(Context context, PrayerTime prayerTime) {
        // Check Do Not Disturb permission before scheduling
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !notificationManager.isNotificationPolicyAccessGranted()) {
            // Prompt user to grant permission
            Intent settingsIntent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            context.startActivity(settingsIntent);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Parse start and end times (existing parsing logic)
        String[] startTimeParts = prayerTime.getStartTime().split(":");
        String[] endTimeParts = prayerTime.getEndTime().split(":");

        // Create calendar instances for start and end times
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(startTimeParts[0]));
        startCalendar.set(Calendar.MINUTE, Integer.parseInt(startTimeParts[1]));
        startCalendar.set(Calendar.SECOND, 0);

        Calendar endCalendar = Calendar.getInstance();
        endCalendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(endTimeParts[0]));
        endCalendar.set(Calendar.MINUTE, Integer.parseInt(endTimeParts[1]));
        endCalendar.set(Calendar.SECOND, 0);

        // Create intents for start and end of silent mode
        Intent startIntent = new Intent(context, SilentModeReceiver.class);
        startIntent.setAction(ACTION_SILENT_MODE_START);
        startIntent.putExtra("prayer", prayerTime.getName());

        Intent endIntent = new Intent(context, SilentModeReceiver.class);
        endIntent.setAction(ACTION_SILENT_MODE_END);
        endIntent.putExtra("prayer", prayerTime.getName());

        // Create pending intents
        int startRequestCode = prayerTime.getName().hashCode() + 1000;
        int endRequestCode = prayerTime.getName().hashCode() + 2000;

        PendingIntent startPendingIntent = PendingIntent.getBroadcast(
                context,
                startRequestCode,
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent endPendingIntent = PendingIntent.getBroadcast(
                context,
                endRequestCode,
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Schedule alarms
        if (prayerTime.isEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startCalendar.getTimeInMillis(), startPendingIntent);
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endCalendar.getTimeInMillis(), endPendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, startCalendar.getTimeInMillis(), startPendingIntent);
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, endCalendar.getTimeInMillis(), endPendingIntent);
            }
        } else {
            // Cancel existing alarms if the prayer time is disabled
            alarmManager.cancel(startPendingIntent);
            alarmManager.cancel(endPendingIntent);
        }
    }

    private void showNotification(Context context, String prayer, String message) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SalahSilence Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("SalahSilence")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(prayer.hashCode(), builder.build());
    }
}
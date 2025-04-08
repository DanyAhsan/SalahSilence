package com.dany.salahsilence;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
        String action = intent.getAction();
        String prayer = intent.getStringExtra("prayer");

        // Start the foreground service
        Intent serviceIntent = new Intent(context, SilentModeService.class);
        serviceIntent.putExtra("action", ACTION_SILENT_MODE_START.equals(action) ? "start" : "end");
        serviceIntent.putExtra("prayer", prayer);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    public static void scheduleSilentMode(Context context, PrayerTime prayerTime) {
        // Check Do Not Disturb permission
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !notificationManager.isNotificationPolicyAccessGranted()) {
            showDoNotDisturbPermissionNotification(context);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Parse start and end times
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

        // If time has passed for today, schedule for tomorrow
        Calendar now = Calendar.getInstance();
        if (startCalendar.before(now)) {
            startCalendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        if (endCalendar.before(now)) {
            endCalendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Create intents for start and end of silent mode
        Intent startIntent = new Intent(context, SilentModeReceiver.class);
        startIntent.setAction(ACTION_SILENT_MODE_START);
        startIntent.putExtra("prayer", prayerTime.getName());

        Intent endIntent = new Intent(context, SilentModeReceiver.class);
        endIntent.setAction(ACTION_SILENT_MODE_END);
        endIntent.putExtra("prayer", prayerTime.getName());

        // Create unique request codes based on prayer name and time
        int startRequestCode = (prayerTime.getName() + "_start").hashCode();
        int endRequestCode = (prayerTime.getName() + "_end").hashCode();

        // Use FLAG_UPDATE_CURRENT and FLAG_IMMUTABLE for better compatibility
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

        // Schedule alarms with exact timing and wake up
        if (prayerTime.isEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setAlarmClock for better reliability on newer Android versions
                alarmManager.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(startCalendar.getTimeInMillis(), startPendingIntent),
                    startPendingIntent
                );
                alarmManager.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(endCalendar.getTimeInMillis(), endPendingIntent),
                    endPendingIntent
                );
            } else {
                // For older versions, use setExactAndAllowWhileIdle
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        startCalendar.getTimeInMillis(),
                        startPendingIntent
                    );
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endCalendar.getTimeInMillis(),
                        endPendingIntent
                    );
                } else {
                    // For very old versions, use setExact
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        startCalendar.getTimeInMillis(),
                        startPendingIntent
                    );
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        endCalendar.getTimeInMillis(),
                        endPendingIntent
                    );
                }
            }
        } else {
            // Cancel existing alarms if the prayer time is disabled
            alarmManager.cancel(startPendingIntent);
            alarmManager.cancel(endPendingIntent);
        }
    }

    private static void showDoNotDisturbPermissionNotification(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SalahSilence Permissions",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent settingsIntent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("SalahSilence Permission Required")
                .setContentText("Please grant Do Not Disturb access to enable silent mode")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(999, builder.build());
    }
}
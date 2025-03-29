package com.dany.salahsilence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences sharedPreferences = context.getSharedPreferences("PrayerTimes", Context.MODE_PRIVATE);
            
            // Reschedule all enabled prayer times
            String[] prayers = {"fajr", "zuhr", "asr", "maghrib", "isha"};
            for (String prayer : prayers) {
                boolean isEnabled = sharedPreferences.getBoolean(prayer + "_enabled", false);
                if (isEnabled) {
                    String startTime = sharedPreferences.getString(prayer + "_start", "00:00");
                    String endTime = sharedPreferences.getString(prayer + "_end", "00:00");
                    PrayerTime prayerTime = new PrayerTime(prayer, startTime, endTime, true);
                    SilentModeReceiver.scheduleSilentMode(context, prayerTime);
                }
            }
        }
    }
} 
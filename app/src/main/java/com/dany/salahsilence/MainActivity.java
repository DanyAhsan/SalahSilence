package com.dany.salahsilence;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PrayerTimeAdapter.OnItemClickListener {

    private SharedPreferences sharedPreferences;
    private AlarmManager alarmManager;
    private PrayerTimeAdapter adapter;
    private List<PrayerTime> prayerTimes;
    private static final int REQUEST_CODE_EXACT_ALARM_PERMISSION = 1;

    private static final int PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("PrayerTimes", Context.MODE_PRIVATE);
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        RecyclerView recyclerView = findViewById(R.id.prayer_times_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        prayerTimes = new ArrayList<>();
        adapter = new PrayerTimeAdapter(this, prayerTimes, this);
        recyclerView.setAdapter(adapter);

        loadPrayerTimes();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SCHEDULE_EXACT_ALARM)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SCHEDULE_EXACT_ALARM},
                    REQUEST_CODE_EXACT_ALARM_PERMISSION);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        requestPermissions();
    }

    private void requestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // Add permissions you need
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Add other permissions as needed, such as:
        // - Manifest.permission.READ_CALENDAR
        // - Other location or system-related permissions

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permissions", "Permission granted: " + permissions[i]);
                } else {
                    Log.d("Permissions", "Permission denied: " + permissions[i]);
                    // Optionally, show a dialog explaining why the permission is needed
                }
            }
        }
    }

    private void loadPrayerTimes() {
        prayerTimes.add(new PrayerTime("Fajr", sharedPreferences.getString("fajr_start", "00:00"), sharedPreferences.getString("fajr_end", "00:00"), sharedPreferences.getBoolean("fajr_enabled", false)));
        prayerTimes.add(new PrayerTime("Zuhr", sharedPreferences.getString("zuhr_start", "00:00"), sharedPreferences.getString("zuhr_end", "00:00"), sharedPreferences.getBoolean("zuhr_enabled", false)));
        prayerTimes.add(new PrayerTime("Asr", sharedPreferences.getString("asr_start", "00:00"), sharedPreferences.getString("asr_end", "00:00"), sharedPreferences.getBoolean("asr_enabled", false)));
        prayerTimes.add(new PrayerTime("Maghrib", sharedPreferences.getString("maghrib_start", "00:00"), sharedPreferences.getString("maghrib_end", "00:00"), sharedPreferences.getBoolean("maghrib_enabled", false)));
        prayerTimes.add(new PrayerTime("Isha", sharedPreferences.getString("isha_start", "00:00"), sharedPreferences.getString("isha_end", "00:00"), sharedPreferences.getBoolean("isha_enabled", false)));
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onSetStartTimeClick(PrayerTime prayerTime) {
        showTimePickerDialog(prayerTime, true);
    }

    @Override
    public void onSetEndTimeClick(PrayerTime prayerTime) {
        showTimePickerDialog(prayerTime, false);
    }

    @Override
    public void onEnableSwitchChange(PrayerTime prayerTime, boolean isChecked) {
        sharedPreferences.edit().putBoolean(prayerTime.getName().toLowerCase() + "_enabled", isChecked).apply();
        if (isChecked) {
            scheduleAlarm(prayerTime.getName().toLowerCase(), prayerTime.getStartTime(), prayerTime.getEndTime());
        } else {
            cancelAlarm(prayerTime.getName().toLowerCase());
        }
    }

    private void showTimePickerDialog(PrayerTime prayerTime, boolean isStartTime) {
        TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String time = String.format("%02d:%02d", hourOfDay, minute);
            if (isStartTime) {
                prayerTime.setStartTime(time);
                sharedPreferences.edit().putString(prayerTime.getName().toLowerCase() + "_start", time).apply();
            } else {
                prayerTime.setEndTime(time);
                sharedPreferences.edit().putString(prayerTime.getName().toLowerCase() + "_end", time).apply();
            }
            adapter.notifyDataSetChanged();
        }, 0, 0, false);
        timePickerDialog.show();
    }

    private void scheduleAlarm(String prayerKey, String startTime, String endTime) {
        Calendar startCalendar = Calendar.getInstance();
        Calendar endCalendar = Calendar.getInstance();

        String[] startParts = startTime.split(":");
        String[] endParts = endTime.split(":");

        startCalendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(startParts[0]));
        startCalendar.set(Calendar.MINUTE, Integer.parseInt(startParts[1]));
        endCalendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(endParts[0]));
        endCalendar.set(Calendar.MINUTE, Integer.parseInt(endParts[1]));

        Intent startIntent = new Intent(this, SilentModeReceiver.class);
        startIntent.putExtra("prayer", prayerKey);
        startIntent.putExtra("action", "start");
        PendingIntent startPendingIntent = PendingIntent.getBroadcast(this, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent endIntent = new Intent(this, SilentModeReceiver.class);
        endIntent.putExtra("prayer", prayerKey);
        endIntent.putExtra("action", "end");
        PendingIntent endPendingIntent = PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.setExact(AlarmManager.RTC_WAKEUP, startCalendar.getTimeInMillis(), startPendingIntent);
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, endCalendar.getTimeInMillis(), endPendingIntent);


    }

    private void cancelAlarm(String prayerKey) {
        Intent startIntent = new Intent(this, SilentModeReceiver.class);
        startIntent.putExtra("prayer", prayerKey);
        startIntent.putExtra("action", "start");
        PendingIntent startPendingIntent = PendingIntent.getBroadcast(this, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent endIntent = new Intent(this, SilentModeReceiver.class);
        endIntent.putExtra("prayer", prayerKey);
        endIntent.putExtra("action", "end");
        PendingIntent endPendingIntent = PendingIntent.getBroadcast(this, 0, endIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(startPendingIntent);
        alarmManager.cancel(endPendingIntent);
    }
}

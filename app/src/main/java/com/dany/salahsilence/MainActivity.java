package com.dany.salahsilence;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.NotificationManager;
import android.app.AlertDialog;
import android.widget.Toast;

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
import com.google.android.material.snackbar.Snackbar;
import com.dany.salahsilence.api.PrayerTimesApi;
import com.dany.salahsilence.api.PrayerTimesResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class MainActivity extends AppCompatActivity implements PrayerTimeAdapter.OnItemClickListener {

    private static final String BASE_URL = "https://api.aladhan.com/";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    private static final int METHOD_ID = 2; // Islamic Society of North America (ISNA)

    private SharedPreferences sharedPreferences;
    private AlarmManager alarmManager;
    private PrayerTimeAdapter adapter;
    private List<PrayerTime> prayerTimes;
    private static final int REQUEST_CODE_EXACT_ALARM_PERMISSION = 1;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1001;
    private static final int REQUEST_NOTIFICATION_POLICY = 1002;
    private static final int REQUEST_OVERLAY_PERMISSION = 1003;
    private FusedLocationProviderClient fusedLocationClient;
    private PrayerTimesApi prayerTimesApi;
    private FloatingActionButton autoFetchButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupRetrofit();
        setupViews();
        checkAndRequestPermissions();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Request battery optimization immediately after onCreate
        requestBatteryOptimization();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check battery optimization status when app resumes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                requestBatteryOptimization();
            }
        }
    }

    private void setupRetrofit() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        prayerTimesApi = retrofit.create(PrayerTimesApi.class);
    }

    private void setupViews() {
        sharedPreferences = getSharedPreferences("PrayerTimes", Context.MODE_PRIVATE);
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        RecyclerView recyclerView = findViewById(R.id.prayer_times_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        prayerTimes = new ArrayList<>();
        adapter = new PrayerTimeAdapter(this, prayerTimes, this);
        recyclerView.setAdapter(adapter);

        autoFetchButton = findViewById(R.id.auto_fetch_button);
        autoFetchButton.setOnClickListener(v -> fetchPrayerTimes());

        loadPrayerTimes();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SCHEDULE_EXACT_ALARM)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SCHEDULE_EXACT_ALARM},
                    REQUEST_CODE_EXACT_ALARM_PERMISSION);
        }
    }

    private void fetchPrayerTimes() {
        // First check if location services are enabled
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // Location is not enabled, show dialog to enable it
            new AlertDialog.Builder(this)
                .setTitle("Location Required")
                .setMessage("Please enable location services to fetch prayer times automatically.")
                .setPositiveButton("Open Settings", (dialogInterface, i) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }

        // Location is enabled, proceed with permission check and location fetch
        if (checkLocationPermission()) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            callPrayerTimesApi(location.getLatitude(), location.getLongitude());
                        } else {
                            Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error getting location: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void callPrayerTimesApi(double latitude, double longitude) {
        prayerTimesApi.getPrayerTimes(latitude, longitude, METHOD_ID)
                .enqueue(new Callback<PrayerTimesResponse>() {
                    @Override
                    public void onResponse(Call<PrayerTimesResponse> call, 
                            Response<PrayerTimesResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            updatePrayerTimes(response.body().data.timings);
                        } else {
                            Toast.makeText(MainActivity.this, 
                                "Error fetching prayer times", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PrayerTimesResponse> call, Throwable t) {
                        Toast.makeText(MainActivity.this, 
                            "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updatePrayerTimes(PrayerTimesResponse.Timings timings) {
        // Update Fajr
        updatePrayerTime("fajr", timings.fajr, 30);
        // Update Dhuhr (Zuhr)
        updatePrayerTime("zuhr", timings.dhuhr, 30);
        // Update Asr
        updatePrayerTime("asr", timings.asr, 30);
        // Update Maghrib
        updatePrayerTime("maghrib", timings.maghrib, 30);
        // Update Isha
        updatePrayerTime("isha", timings.isha, 30);

        loadPrayerTimes(); // Reload the UI
        Toast.makeText(this, "Prayer times updated successfully", Toast.LENGTH_SHORT).show();
    }

    private void updatePrayerTime(String prayer, String startTime, int durationMinutes) {
        // Parse the time (format: HH:mm)
        String[] parts = startTime.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        // Calculate end time
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.add(Calendar.MINUTE, durationMinutes);

        String endTime = String.format("%02d:%02d", 
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));

        // Save to SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(prayer + "_start", startTime);
        editor.putString(prayer + "_end", endTime);
        editor.apply();
    }

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                String packageName = getPackageName();
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                }
            } catch (Exception e) {
                Log.e("BatteryOptimization", "Error requesting battery optimization: " + e.getMessage());
                // Show a dialog to guide user to manually disable battery optimization
                showManualBatteryOptimizationDialog();
            }
        }
    }

    private void showManualBatteryOptimizationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Battery Optimization")
                .setMessage("For reliable operation, please manually disable battery optimization for SalahSilence:\n\n" +
                        "1. Go to Settings\n" +
                        "2. Search for 'Battery Optimization'\n" +
                        "3. Find 'SalahSilence'\n" +
                        "4. Select 'Don't optimize'")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void checkAndRequestPermissions() {
        // Check for battery optimization again in case the first request failed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            String packageName = getPackageName();
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryOptimizationDialog();
            }
        }

        // Check for DND permission
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
                !notificationManager.isNotificationPolicyAccessGranted()) {
            showDNDPermissionDialog();
        }

        // Check for overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
                !Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog();
        }
    }

    private void showBatteryOptimizationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Battery Optimization")
                .setMessage("To ensure SalahSilence works properly, please disable battery optimization for this app.")
                .setPositiveButton("Disable", (dialog, which) -> {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void showDNDPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Do Not Disturb Permission")
                .setMessage("SalahSilence needs permission to change sound settings during prayer times.")
                .setPositiveButton("Grant", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    startActivityForResult(intent, REQUEST_NOTIFICATION_POLICY);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void showOverlayPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Overlay Permission")
                .setMessage("SalahSilence needs permission to show over other apps to ensure reliable operation.")
                .setPositiveButton("Grant", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                })
                .setNegativeButton("Later", null)
                .show();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        switch (requestCode) {
            case REQUEST_IGNORE_BATTERY_OPTIMIZATIONS:
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                boolean isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(getPackageName());
                if (isIgnoringBatteryOptimizations) {
                    Toast.makeText(this, "Battery optimization disabled", Toast.LENGTH_SHORT).show();
                }
                break;
                
            case REQUEST_NOTIFICATION_POLICY:
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm.isNotificationPolicyAccessGranted()) {
                    Toast.makeText(this, "DND permission granted", Toast.LENGTH_SHORT).show();
                }
                break;
                
            case REQUEST_OVERLAY_PERMISSION:
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void loadPrayerTimes() {
        prayerTimes.clear();
        prayerTimes.add(new PrayerTime("Fajr", 
            sharedPreferences.getString("fajr_start", "00:00"),
            sharedPreferences.getString("fajr_end", "00:00"),
            sharedPreferences.getBoolean("fajr_enabled", false)));
        prayerTimes.add(new PrayerTime("Zuhr",
            sharedPreferences.getString("zuhr_start", "00:00"),
            sharedPreferences.getString("zuhr_end", "00:00"),
            sharedPreferences.getBoolean("zuhr_enabled", false)));
        prayerTimes.add(new PrayerTime("Asr",
            sharedPreferences.getString("asr_start", "00:00"),
            sharedPreferences.getString("asr_end", "00:00"),
            sharedPreferences.getBoolean("asr_enabled", false)));
        prayerTimes.add(new PrayerTime("Maghrib",
            sharedPreferences.getString("maghrib_start", "00:00"),
            sharedPreferences.getString("maghrib_end", "00:00"),
            sharedPreferences.getBoolean("maghrib_enabled", false)));
        prayerTimes.add(new PrayerTime("Isha",
            sharedPreferences.getString("isha_start", "00:00"),
            sharedPreferences.getString("isha_end", "00:00"),
            sharedPreferences.getBoolean("isha_enabled", false)));
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
        String prayerKey = prayerTime.getName().toLowerCase();
        sharedPreferences.edit().putBoolean(prayerKey + "_enabled", isChecked).apply();
        prayerTime.setEnabled(isChecked);
        SilentModeReceiver.scheduleSilentMode(this, prayerTime);
    }

    private void showTimePickerDialog(PrayerTime prayerTime, boolean isStartTime) {
        TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String time = String.format("%02d:%02d", hourOfDay, minute);
            String prayerKey = prayerTime.getName().toLowerCase();
            
            if (isStartTime) {
                prayerTime.setStartTime(time);
                sharedPreferences.edit().putString(prayerKey + "_start", time).apply();
            } else {
                prayerTime.setEndTime(time);
                sharedPreferences.edit().putString(prayerKey + "_end", time).apply();
            }
            
            adapter.notifyDataSetChanged();
            
            if (prayerTime.isEnabled()) {
                SilentModeReceiver.scheduleSilentMode(this, prayerTime);
            }
        }, 0, 0, false);
        timePickerDialog.show();
    }
}

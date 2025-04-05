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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
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
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
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

import android.view.Menu;
import android.view.MenuItem;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.provider.FontsContractCompat;
import androidx.core.graphics.TypefaceCompat;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Environment;
import androidx.core.content.FileProvider;
import java.io.File;
import com.dany.salahsilence.updater.GitHubApiService;
import com.dany.salahsilence.updater.GitHubRelease;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class MainActivity extends AppCompatActivity implements PrayerTimeAdapter.OnItemClickListener {

    private static final String BASE_URL = "https://api.aladhan.com/";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    private static final int METHOD_ID = 1; // Islamic Society of karachi

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
    private static final String PREF_KEY_THEME = "pref_theme";
    private Toolbar toolbar;

    private static final String GITHUB_API_BASE_URL = "https://api.github.com/";
    private static final String GITHUB_USER = "DanyAhsan";
    private static final String GITHUB_REPO = "SalahSilence";
    private Retrofit githubRetrofitClient = null;
    private DownloadManager downloadManager;
    /**
     * The ID of the last initiated download.  This is used to track downloads across app sessions
     * and resume them if necessary.  A value of -1L indicates that no download is currently active.
     */
    private long lastDownloadId = -1L;

    // BroadcastReceiver to listen for download completion
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == lastDownloadId) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(id);
                android.database.Cursor cursor = downloadManager.query(query);
                if (cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusIndex);
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        String downloadedFileUriString = cursor.getString(uriIndex);
                        Log.d("UpdateDownload", "Download successful. URI: " + downloadedFileUriString);
                        // Convert content:// URI to File path if needed, or directly use URI
                        // For simplicity, let's assume DownloadManager saves where we can access it
                        // NOTE: Getting a File object directly from content URI can be tricky. Install directly from URI.
                        Uri downloadedUri = Uri.parse(downloadedFileUriString);
                        checkInstallPermissionAndInstall(downloadedUri); // Use the URI directly
                    } else {
                        int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                        int reason = cursor.getInt(reasonIndex);
                        Log.e("UpdateDownload", "Download failed. Status: " + status + ", Reason: " + reason);
                        Toast.makeText(context, "Update download failed.", Toast.LENGTH_LONG).show();
                    }
                }
                 cursor.close();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Apply the saved theme before setting the content view
        applyTheme(); 
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Remove default title to set a custom one with font
        if (getSupportActionBar() != null) {
           getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        // Apply custom font to Toolbar title
        applyToolbarFont(); 

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

        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        // Register the receiver for download completion
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the receiver to avoid leaks
        unregisterReceiver(downloadReceiver);
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
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && 
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) { // Check both GPS and Network
            // Location is not enabled, show dialog to enable it
            new AlertDialog.Builder(this)
                .setTitle("Location Required")
                .setMessage("Please enable location services (GPS or Network) to fetch prayer times automatically.")
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
            // Try getting the last known location first (fast)
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, lastLocation -> {
                    if (lastLocation != null) {
                        Log.d("Location", "Using last known location.");
                        callPrayerTimesApi(lastLocation.getLatitude(), lastLocation.getLongitude());
                    } else {
                        // Last location is null, request current location (might take time)
                        Log.d("Location", "Last known location is null. Requesting current location.");
                        Toast.makeText(this, "Getting current location...", Toast.LENGTH_SHORT).show(); 
                        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                            .addOnSuccessListener(this, currentLocation -> {
                                if (currentLocation != null) {
                                    Log.d("Location", "Successfully got current location.");
                                    callPrayerTimesApi(currentLocation.getLatitude(), currentLocation.getLongitude());
                                } else {
                                    Log.w("Location", "getCurrentLocation returned null.");
                                    Toast.makeText(this, "Unable to get current location.", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener(this, e -> {
                                Log.e("Location", "Error getting current location", e);
                                Toast.makeText(this, "Error getting current location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                    }
                })
                .addOnFailureListener(this, e -> {
                    // This failure is for getLastLocation, but we should still try getCurrentLocation
                    Log.e("Location", "Error getting last location, trying current", e);
                    Toast.makeText(this, "Getting current location...", Toast.LENGTH_SHORT).show();
                    CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
                     fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                        .addOnSuccessListener(this, currentLocation -> {
                             if (currentLocation != null) {
                                Log.d("Location", "Successfully got current location after lastLocation failure.");
                                callPrayerTimesApi(currentLocation.getLatitude(), currentLocation.getLongitude());
                             } else {
                                Log.w("Location", "getCurrentLocation returned null after lastLocation failure.");
                                Toast.makeText(this, "Unable to get current location.", Toast.LENGTH_SHORT).show();
                             }
                        })
                        .addOnFailureListener(this, currentE -> {
                            Log.e("Location", "Error getting current location after lastLocation failure", currentE);
                            Toast.makeText(this, "Error getting current location: " + currentE.getMessage(), Toast.LENGTH_SHORT).show();
                        });
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

    private void applyTheme() {
        sharedPreferences = getSharedPreferences("PrayerTimes", Context.MODE_PRIVATE);
        int savedThemeMode = sharedPreferences.getInt(PREF_KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedThemeMode);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; // Default
        int itemId = item.getItemId();

        if (itemId == R.id.theme_light) {
            themeMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (itemId == R.id.theme_dark) {
            themeMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else if (itemId == R.id.theme_system) {
            themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else if (itemId == R.id.action_check_update) {
             checkForUpdates(); // Call the update check method
             return true;
        } else {
            return super.onOptionsItemSelected(item);
        }

        // Save the selected theme mode
        sharedPreferences.edit().putInt(PREF_KEY_THEME, themeMode).apply();
        // Apply the theme immediately
        AppCompatDelegate.setDefaultNightMode(themeMode);
        // The activity will be recreated to apply the theme change
        
        return true;
    }

    private void applyToolbarFont() {
        try {
            // Load the font
            Typeface outfitFont = ResourcesCompat.getFont(this, R.font.outfitregular);

            if (outfitFont != null && toolbar != null) {
                // Get the title string
                CharSequence title = "SalahSilence"; // Or get dynamically if needed
                
                // Create a SpannableString
                SpannableString spannableTitle = new SpannableString(title);
                
                // Apply the font using TypefaceSpan (handle potential API level differences)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    spannableTitle.setSpan(new TypefaceSpan(outfitFont), 0, spannableTitle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    // For older versions, create a custom span or use deprecated TypefaceSpan constructor if available
                    // This custom span is a safer approach for older APIs
                    spannableTitle.setSpan(new CustomTypefaceSpan("", outfitFont), 0, spannableTitle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                
                // Set the title with the custom font
                toolbar.setTitle(spannableTitle);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error loading or applying font to toolbar title", e);
            // Fallback if font loading fails - set plain title
            if (toolbar != null) {
                 toolbar.setTitle("SalahSilence");
            }
        }
    }

    // Custom TypefaceSpan for older APIs (if needed)
    public static class CustomTypefaceSpan extends TypefaceSpan {
        private final Typeface newType;

        public CustomTypefaceSpan(String family, Typeface type) {
            super(family); // Pass family string for compatibility
            newType = type;
        }

        @Override
        public void updateDrawState(android.text.TextPaint ds) {
            applyCustomTypeFace(ds, newType);
        }

        @Override
        public void updateMeasureState(android.text.TextPaint paint) {
            applyCustomTypeFace(paint, newType);
        }

        private static void applyCustomTypeFace(android.graphics.Paint paint, Typeface tf) {
            int oldStyle;
            Typeface old = paint.getTypeface();
            if (old == null) {
                oldStyle = 0;
            } else {
                oldStyle = old.getStyle();
            }

            int fake = oldStyle & ~tf.getStyle();
            if ((fake & Typeface.BOLD) != 0) {
                paint.setFakeBoldText(true);
            }

            if ((fake & Typeface.ITALIC) != 0) {
                paint.setFakeBoldText(true);
            }

            paint.setTypeface(tf);
        }
    }

    // Initialize Retrofit client for GitHub API
    private GitHubApiService getGitHubApiClient() {
        if (githubRetrofitClient == null) {
            // You might want a separate OkHttpClient instance if you need specific config for GitHub
            githubRetrofitClient = new Retrofit.Builder()
                    .baseUrl(GITHUB_API_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create()) // Ensure Gson converter
                    .client(new OkHttpClient.Builder().build()) // Use default or configure as needed
                    .build();
        }
        return githubRetrofitClient.create(GitHubApiService.class);
    }

    // Method to initiate the update check
    private void checkForUpdates() {
        Log.d("UpdateCheck", "Checking for updates...");
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection.", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentVersionName = "";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersionName = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("UpdateCheck", "Failed to get package info", e);
            Toast.makeText(this, "Error getting app version.", Toast.LENGTH_SHORT).show();
            return;
        }

        GitHubApiService service = getGitHubApiClient();
        Call<GitHubRelease> call = service.getLatestRelease(GITHUB_USER, GITHUB_REPO);
        final String finalCurrentVersionName = currentVersionName; // For use in callback

        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show(); // Indicate check started

        call.enqueue(new Callback<GitHubRelease>() {
            @Override
            public void onResponse(Call<GitHubRelease> call, Response<GitHubRelease> response) {
                if (response.isSuccessful() && response.body() != null) {
                    GitHubRelease latestRelease = response.body();
                    Log.d("UpdateCheck", "Latest release tag: " + latestRelease.tagName);
                    compareVersionsAndPrompt(latestRelease, finalCurrentVersionName);
                } else {
                    Log.e("UpdateCheck", "GitHub API error: " + response.code() + " - " + response.message());
                     if (response.code() == 404) {
                         Toast.makeText(MainActivity.this, "No releases found for this repository.", Toast.LENGTH_LONG).show();
                     } else {
                         Toast.makeText(MainActivity.this, "Failed to check for updates (API error).", Toast.LENGTH_SHORT).show();
                     }
                }
            }

            @Override
            public void onFailure(Call<GitHubRelease> call, Throwable t) {
                Log.e("UpdateCheck", "Network error checking GitHub API", t);
                Toast.makeText(MainActivity.this, "Network error checking updates.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void compareVersionsAndPrompt(GitHubRelease latestRelease, String currentVersionName) {
        if (latestRelease == null || latestRelease.tagName == null) {
             Log.e("UpdateCheck", "Invalid release data received.");
             Toast.makeText(this, "Error reading update information.", Toast.LENGTH_SHORT).show();
             return;
        }
        
        String latestVersionName = latestRelease.tagName.replaceFirst("^[vV]", ""); // Remove leading 'v' or 'V'
        String currentVersionClean = currentVersionName.replaceFirst("^[vV]", "");

        Log.d("UpdateCheck", "Comparing Latest: '" + latestVersionName + "' with Current: '" + currentVersionClean + "'");

        if (isNewerVersion(latestVersionName, currentVersionClean)) {
            Log.d("UpdateCheck", "Update available.");
            showUpdateDialog(latestRelease);
        } else {
            Log.d("UpdateCheck", "App is up to date or versions are equal/invalid.");
            Toast.makeText(this, "App is up to date.", Toast.LENGTH_SHORT).show();
        }
    }

    // Basic Version Comparison Helper (Refined for simplicity: X.Y)
    private boolean isNewerVersion(String latestVersion, String currentVersion) {
         if (latestVersion == null || currentVersion == null) return false;
         try {
             String[] latestParts = latestVersion.split("\\.");
             String[] currentParts = currentVersion.split("\\.");

             if (latestParts.length < 2 || currentParts.length < 2) {
                 Log.w("VersionCompare", "Assuming simple format X.Y failed. Versions: " + latestVersion + ", " + currentVersion);
                 // Fallback to simple string comparison if format is unexpected
                 return latestVersion.compareTo(currentVersion) > 0; 
             }

             int latestMajor = Integer.parseInt(latestParts[0]);
             int latestMinor = Integer.parseInt(latestParts[1]);
             int currentMajor = Integer.parseInt(currentParts[0]);
             int currentMinor = Integer.parseInt(currentParts[1]);

             if (latestMajor > currentMajor) return true;
             if (latestMajor == currentMajor && latestMinor > currentMinor) return true;

             return false; // current is same or newer
         } catch (NumberFormatException e) {
             Log.e("VersionCompare", "Invalid version number format", e);
             return false; // Cannot compare
         }
    }

    private void showUpdateDialog(GitHubRelease release) {
        String releaseNotes = release.body != null ? release.body : "No details provided.";
        String title = release.releaseName != null ? release.releaseName : "Version " + release.tagName;
        new AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage(title + "\n\nWhat's New:\n" + releaseNotes + "\n\nDownload and install?")
            .setPositiveButton("Update Now", (dialog, which) -> {
                findAndStartDownload(release);
            })
            .setNegativeButton("Later", null)
            .show();
    }

    private void findAndStartDownload(GitHubRelease release) {
        String apkUrl = null;
        String apkName = "SalahSilence_update.apk"; // Default name
        if (release.assets != null) {
            for (GitHubRelease.Asset asset : release.assets) {
                if (asset.name != null && asset.name.toLowerCase().endsWith(".apk")) {
                    apkUrl = asset.downloadUrl;
                    // Try to use the release name + version for a better filename
                    apkName = asset.name; // Use the actual asset name from GitHub
                    break;
                }
            }
        }

        if (apkUrl != null) {
            Log.d("UpdateDownload", "Found APK URL: " + apkUrl);
            startDownload(apkUrl, release.tagName, apkName);
        } else {
            Log.e("UpdateDownload", "Could not find APK download link in release assets.");
            Toast.makeText(this, "Could not find APK download link.", Toast.LENGTH_LONG).show();
        }
    }

    private void startDownload(String url, String version, String fileName) {
         Log.d("UpdateDownload", "Starting download for: " + url);
         Toast.makeText(this, "Starting update download...", Toast.LENGTH_SHORT).show();

         Uri downloadUri = Uri.parse(url);
         DownloadManager.Request request = new DownloadManager.Request(downloadUri);

         request.setTitle("Downloading SalahSilence Update");
         request.setDescription("Version " + version);
         request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
         // Ensure MIME type is correct for APK
         request.setMimeType("application/vnd.android.package-archive");

         // Save to public Downloads directory with the specific filename from GitHub release
         request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

         try {
            lastDownloadId = downloadManager.enqueue(request);
            Log.d("UpdateDownload", "Download enqueued with ID: " + lastDownloadId);
         } catch (Exception e) {
             Log.e("UpdateDownload", "Failed to enqueue download", e);
             Toast.makeText(this, "Failed to start download.", Toast.LENGTH_SHORT).show();
             lastDownloadId = -1L;
         }
    }

    // Check install permission (Android 8+) and trigger install
    private void checkInstallPermissionAndInstall(Uri apkUri) {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             if (!getPackageManager().canRequestPackageInstalls()) {
                 showUnknownSourcesPermissionDialog();
             } else {
                 triggerInstall(apkUri);
             }
         } else {
             triggerInstall(apkUri);
         }
     }

    // Show dialog guiding user to grant install permission
    private void showUnknownSourcesPermissionDialog() {
         new AlertDialog.Builder(this)
             .setTitle("Permission Required")
             .setMessage("To install the update, please allow SalahSilence to install apps from unknown sources in Settings.")
             .setPositiveButton("Open Settings", (dialog, which) -> {
                 Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                 intent.setData(Uri.parse("package:" + getPackageName()));
                 // Consider using startActivityForResult if you want to check immediately after returning
                 startActivity(intent);
             })
             .setNegativeButton("Cancel", null)
             .show();
     }

    // Trigger the system package installer
    private void triggerInstall(Uri apkUri) {
        if (apkUri == null) {
            Log.e("UpdateInstall", "APK URI is null, cannot install.");
            Toast.makeText(this, "Update file URI not found.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d("UpdateInstall", "Attempting to install from URI: " + apkUri.toString());

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Crucial for content URIs

        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Log.e("UpdateInstall", "Error starting package installer", e);
            Toast.makeText(this, "Could not start installer. No app found to handle installation.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e("UpdateInstall", "Generic error starting package installer", e);
            Toast.makeText(this, "Could not start installer.", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper to check network availability
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}

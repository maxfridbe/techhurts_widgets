package com.techhurts.weatherwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WidgetConfigureActivity extends Activity {

    private static final String PREFS_NAME = "com.techhurts.weatherwidget.prefs";
    private static final String PREF_LAT_KEY = "latitude_";
    private static final String PREF_LON_KEY = "longitude_";
    private static final String PREF_LOCATION_NAME_KEY = "location_name_";
    private static final String PREF_WEATHER_DATA_KEY = "weather_data_";


    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    EditText mEditTextZipCode;
    TextView mTextViewLocationResult;
    Button mButtonAcceptLocation;
    Button mButtonConfirm; // Renamed for clarity

    private Location mAcceptedLocation;
    private String mAcceptedLocationName;

    // Executor for background tasks
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Future<?> currentGeocodeTask;


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_configure);

        mEditTextZipCode = findViewById(R.id.editTextZipCode);
        mTextViewLocationResult = findViewById(R.id.textViewLocationResult);
        mButtonAcceptLocation = findViewById(R.id.buttonAcceptLocation);
        mButtonConfirm = findViewById(R.id.buttonConfirm); // Assign to the new member variable

        // Initially disable confirm button
        mButtonConfirm.setEnabled(false);

        // Set up TextWatcher for zip code input
        mEditTextZipCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancel any previous geocode task to avoid stale results
                if (currentGeocodeTask != null && !currentGeocodeTask.isDone()) {
                    currentGeocodeTask.cancel(true);
                }
                mTextViewLocationResult.setVisibility(View.GONE);
                mButtonAcceptLocation.setEnabled(false);
                mButtonAcceptLocation.setVisibility(View.GONE);
                mButtonConfirm.setEnabled(false); // Disable confirm until location is accepted
                mAcceptedLocation = null;
                mAcceptedLocationName = null;
            }

            @Override
            public void afterTextChanged(Editable s) {
                String zipCode = s.toString();
                if (zipCode.length() == 5) {
                    // Start a new geocode task
                    currentGeocodeTask = executorService.submit(() -> lookupLocation(zipCode));
                } else {
                    mTextViewLocationResult.setText("");
                    mTextViewLocationResult.setVisibility(View.GONE);
                }
            }
        });

        mButtonAcceptLocation.setOnClickListener(v -> {
            if (mAcceptedLocation != null && mAcceptedLocationName != null) {
                // Hide keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                mButtonConfirm.setEnabled(true);
                Toast.makeText(WidgetConfigureActivity.this, "Location accepted: " + mAcceptedLocationName, Toast.LENGTH_SHORT).show();
            }
        });

        mButtonConfirm.setOnClickListener(v -> {
            final Context context = WidgetConfigureActivity.this;

            if (mAcceptedLocation != null && mAcceptedLocationName != null) {
                saveLocationDataPref(context, mAppWidgetId, mAcceptedLocation.getLatitude(), mAcceptedLocation.getLongitude(), mAcceptedLocationName);

                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                WeatherWidgetProvider.updateAppWidget(context, appWidgetManager, mAppWidgetId);

                requestBatteryOptimizationExemption();

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                setResult(RESULT_OK, resultValue);
                finish();
            } else {
                Toast.makeText(context, "Please accept a location first.", Toast.LENGTH_SHORT).show();
            }
        });


        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void lookupLocation(String zipCode) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(zipCode, 1);
            runOnUiThread(() -> {
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    // Prioritize city, then locality, then postal code
                    String city = address.getLocality() != null ? address.getLocality() : address.getAdminArea();
                    String state = address.getAdminArea();
                    String country = address.getCountryCode();

                    String locationText;
                    if (city != null && state != null) {
                        locationText = String.format(Locale.getDefault(), "%s, %s, %s", city, state, country);
                    } else if (state != null) {
                        locationText = String.format(Locale.getDefault(), "%s, %s", state, country);
                    } else {
                        locationText = String.format(Locale.getDefault(), "%s", country);
                    }

                    mTextViewLocationResult.setText("Found: " + locationText);
                    mTextViewLocationResult.setVisibility(View.VISIBLE);

                    mAcceptedLocation = new Location("");
                    mAcceptedLocation.setLatitude(address.getLatitude());
                    mAcceptedLocation.setLongitude(address.getLongitude());
                    mAcceptedLocationName = locationText;

                    mButtonAcceptLocation.setEnabled(true);
                    mButtonAcceptLocation.setVisibility(View.VISIBLE);

                } else {
                    mTextViewLocationResult.setText("Location not found for zip code: " + zipCode);
                    mTextViewLocationResult.setVisibility(View.VISIBLE);
                    mButtonAcceptLocation.setEnabled(false);
                    mButtonAcceptLocation.setVisibility(View.GONE);
                }
            });
        } catch (IOException e) {
            runOnUiThread(() -> {
                mTextViewLocationResult.setText("Error looking up location: " + e.getMessage());
                mTextViewLocationResult.setVisibility(View.VISIBLE);
                mButtonAcceptLocation.setEnabled(false);
                mButtonAcceptLocation.setVisibility(View.GONE);
            });
        }
    }

    static void saveLocationDataPref(Context context, int appWidgetId, double latitude, double longitude, String locationName) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putString(PREF_LAT_KEY + appWidgetId, String.valueOf(latitude));
        prefs.putString(PREF_LON_KEY + appWidgetId, String.valueOf(longitude));
        prefs.putString(PREF_LOCATION_NAME_KEY + appWidgetId, locationName);
        prefs.apply();
    }

    static LocationData loadLocationDataPref(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        String latString = prefs.getString(PREF_LAT_KEY + appWidgetId, null);
        String lonString = prefs.getString(PREF_LON_KEY + appWidgetId, null);
        String locationName = prefs.getString(PREF_LOCATION_NAME_KEY + appWidgetId, null);

        if (latString != null && lonString != null && locationName != null) {
            try {
                double latitude = Double.parseDouble(latString);
                double longitude = Double.parseDouble(lonString);
                return new LocationData(latitude, longitude, locationName);
            } catch (NumberFormatException e) {
                WidgetLogger.log("NumberFormatException in loadLocationDataPref for appWidgetId " + appWidgetId + ": " + e.getMessage());
                return null;
            }
        }
        WidgetLogger.log("loadLocationDataPref returning null for appWidgetId " + appWidgetId + ": latString=" + latString + ", lonString=" + lonString + ", locationName=" + locationName);
        return null;
    }

    static void deleteLocationDataPref(Context context, int appWidgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.remove(PREF_LAT_KEY + appWidgetId);
        prefs.remove(PREF_LON_KEY + appWidgetId);
        prefs.remove(PREF_LOCATION_NAME_KEY + appWidgetId);
        prefs.remove(PREF_WEATHER_DATA_KEY + appWidgetId);
        prefs.apply();
    }

    static void saveWeatherData(Context context, int appWidgetId, String[] weatherData) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        JSONObject json = new JSONObject();
        try {
            json.put("name", weatherData[0]);
            json.put("temperature", weatherData[1]);
            json.put("shortForecast", weatherData[2]);
            json.put("timestamp", System.currentTimeMillis());
            prefs.putString(PREF_WEATHER_DATA_KEY + appWidgetId, json.toString());
            prefs.apply();
        } catch (JSONException e) {
            WidgetLogger.log("Error saving weather data: " + e.getMessage());
        }
    }

    static String[] loadWeatherData(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        String jsonString = prefs.getString(PREF_WEATHER_DATA_KEY + appWidgetId, null);
        if (jsonString != null) {
            try {
                JSONObject json = new JSONObject(jsonString);
                long timestamp = json.getLong("timestamp");
                if (System.currentTimeMillis() - timestamp < 8 * 60 * 60 * 1000) { // 8 hours
                    return new String[]{
                            json.getString("name"),
                            json.getString("temperature"),
                            json.getString("shortForecast")
                    };
                }
            } catch (JSONException e) {
                WidgetLogger.log("Error loading weather data: " + e.getMessage());
            }
        }
        return null;
    }


    // Helper class to return multiple location data
    public static class LocationData {
        public final double latitude;
        public final double longitude;
        public final String locationName;

        public LocationData(double latitude, double longitude, String locationName) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.locationName = locationName;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow(); // Shut down the executor service
    }
}
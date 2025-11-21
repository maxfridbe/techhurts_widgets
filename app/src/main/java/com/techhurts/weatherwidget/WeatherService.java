package com.techhurts.weatherwidget;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

import org.json.JSONObject;
import java.util.Arrays;

public class WeatherService {


    private static final String TAG = "WeatherService";
    private static final String ZIPPOPOTAM_API_URL = "https://api.zippopotam.us/us/";
    private static final String WEATHER_API_BASE_URL = "https://api.weather.gov/points/";

    public static String[] getWeatherData(Context context, String zipCode) {
        WidgetLogger.log("getWeatherData started with zip code: " + zipCode);
        if (zipCode == null || zipCode.length() != 5) {
            WidgetLogger.log("Invalid zip code: " + zipCode);
            return null;
        }

        try {
            // 1. Geocode zip code to latitude and longitude using zippopotam.us
            WidgetLogger.log("Starting geocoding for zip code: " + zipCode);
            double[] coords = getCoordsFromZip(zipCode);
            if (coords == null) {
                WidgetLogger.log("Could not get coordinates for zip code: " + zipCode);
                return null;
            }
            double lat = coords[0];
            double lon = coords[1];
            WidgetLogger.log("Geocoding successful. Lat: " + lat + ", Lon: " + lon);

            // 2. Get gridpoint URL
            WidgetLogger.log("Getting gridpoint URL for " + lat + "," + lon);
            String gridpointUrl = getGridpointUrl(lat, lon);
            if (gridpointUrl == null) {
                WidgetLogger.log("Failed to get gridpoint URL.");
                return null;
            }
            WidgetLogger.log("Gridpoint URL: " + gridpointUrl);

            // 3. Get forecast
            WidgetLogger.log("Getting forecast from: " + gridpointUrl);
            String[] forecast = getForecast(gridpointUrl);
            WidgetLogger.log("Forecast data: " + (forecast != null ? java.util.Arrays.toString(forecast) : "null"));
            return forecast;

        } catch (Exception e) {
            WidgetLogger.log("Exception in getWeatherData: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                WidgetLogger.log("    " + ste.toString());
            }
            return null;
        }
    }

    private static double[] getCoordsFromZip(String zipCode) {
        String fullUrl = ZIPPOPOTAM_API_URL + zipCode;
        WidgetLogger.log("getCoordsFromZip URL: " + fullUrl);
        try {
            URL url = new URL(fullUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            WidgetLogger.log("getCoordsFromZip response code: " + responseCode);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();

            String jsonResponse = result.toString();
            WidgetLogger.log("getCoordsFromZip JSON response: " + jsonResponse);

            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject place = jsonObject.getJSONArray("places").getJSONObject(0);
            double lat = Double.parseDouble(place.getString("latitude"));
            double lon = Double.parseDouble(place.getString("longitude"));

            return new double[]{lat, lon};
        } catch (UnknownHostException e) {
            WidgetLogger.log("Error getting coordinates: Unknown host - " + e.getMessage() + ". Please check internet connection and DNS settings.");
            return null;
        } catch (Exception e) {
            WidgetLogger.log("Error getting coordinates from zip code: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                WidgetLogger.log("    " + ste.toString());
            }
            return null;
        }
    }

    public static String getGridpointUrl(double lat, double lon) {
        String fullUrl = WEATHER_API_BASE_URL + lat + "," + lon;
        WidgetLogger.log("getGridpointUrl URL: " + fullUrl);
        try {
            URL url = new URL(fullUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "MyWeatherWidget/1.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            WidgetLogger.log("getGridpointUrl response code: " + responseCode);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();

            String jsonResponse = result.toString();
            WidgetLogger.log("getGridpointUrl JSON response: " + jsonResponse);

            JSONObject jsonObject = new JSONObject(jsonResponse);
            return jsonObject.getJSONObject("properties").getString("forecast");
        } catch (UnknownHostException e) {
            WidgetLogger.log("Error getting gridpoint URL: Unknown host - " + e.getMessage() + ". Please check internet connection and DNS settings.");
            return null;
        } catch (Exception e) {
            WidgetLogger.log("Error getting gridpoint URL: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                WidgetLogger.log("    " + ste.toString());
            }
            return null;
        }
    }

    public static String[] getForecast(String forecastUrl) {
        WidgetLogger.log("getForecast URL: " + forecastUrl);
        try {
            URL url = new URL(forecastUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "MyWeatherWidget/1.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            WidgetLogger.log("getForecast response code: " + responseCode);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();

            String jsonResponse = result.toString();
            WidgetLogger.log("getForecast JSON response: " + jsonResponse);

            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject period = jsonObject.getJSONObject("properties").getJSONArray("periods").getJSONObject(0);

            String name = period.getString("name");
            String temperature = String.valueOf(period.getInt("temperature"));
            String shortForecast = period.getString("shortForecast");

            return new String[]{name, temperature, shortForecast};
        } catch (UnknownHostException e) {
            WidgetLogger.log("Error getting forecast: Unknown host - " + e.getMessage() + ". Please check internet connection and DNS settings.");
            return null;
        } catch (Exception e) {
            WidgetLogger.log("Error getting forecast: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                WidgetLogger.log("    " + ste.toString());
            }
            return null;
        }
    }
}
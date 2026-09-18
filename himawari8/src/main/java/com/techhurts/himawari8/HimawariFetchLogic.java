package com.techhurts.himawari8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Pure-Java (no Android imports) fetch logic — testable without the Android SDK.
 *
 * Data source: CIRA SLIDER (Colorado State University). The old RAMMB path
 * (ramsdis/online/images/latest_hi_res/himawari-8/) stopped updating in
 * September 2021, well before Himawari-9 took over as the operational
 * satellite, so it served a five-year-old image.
 *
 * SLIDER publishes the available frame times as JSON, then serves each frame as
 * map tiles. Zoom level 0 is the whole disk in one ~800 KB PNG, which is all a
 * widget needs.
 */
class HimawariFetchLogic {

    /** Frame timestamps, newest first, as "timestamps_int": [20260917234000, ...]. */
    static final String LATEST_TIMES_URL =
            "https://slider.cira.colostate.edu/data/json/himawari/full_disk/geocolor/latest_times.json";
    static final String TILE_HOST = "https://slider.cira.colostate.edu";
    static final String USER_AGENT = "TechHurtsHimawari8/1.0 (Android)";

    /** Looks up the newest frame and returns its whole-disk tile URL. */
    static String fetchLatestImageUrl() throws IOException {
        String json = new String(httpGetBytes(LATEST_TIMES_URL, 30_000), "UTF-8");
        String timestamp = firstTimestamp(json);
        if (timestamp == null) {
            throw new IOException("no frame timestamps in " + LATEST_TIMES_URL);
        }
        return tileUrl(timestamp);
    }

    /** First 14-digit timestamp (YYYYMMDDHHMMSS) in the JSON, newest first. */
    static String firstTimestamp(String json) {
        int digits = 0;
        for (int i = 0; i < json.length(); i++) {
            if (Character.isDigit(json.charAt(i))) {
                digits++;
                if (digits == 14) {
                    boolean moreDigits = i + 1 < json.length() && Character.isDigit(json.charAt(i + 1));
                    if (!moreDigits) return json.substring(i - 13, i + 1);
                }
            } else {
                digits = 0;
            }
        }
        return null;
    }

    /** Zoom 0 is the full disk as a single tile. */
    static String tileUrl(String timestamp) {
        String year = timestamp.substring(0, 4);
        String month = timestamp.substring(4, 6);
        String day = timestamp.substring(6, 8);
        return TILE_HOST + "/data/imagery/" + year + "/" + month + "/" + day
                + "/himawari---full_disk/geocolor/" + timestamp + "/00/000_000.png";
    }

    static byte[] downloadImageBytes(String urlStr) throws IOException {
        return httpGetBytes(urlStr, 120_000); // large image — generous timeout
    }

    static byte[] httpGetBytes(String urlStr, int timeoutMs) throws IOException {
        HttpURLConnection conn = openConn(urlStr, timeoutMs);
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32768]; int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally { conn.disconnect(); }
    }

    static HttpURLConnection openConn(String urlStr, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setConnectTimeout(timeoutMs / 4);
        c.setReadTimeout(timeoutMs);
        c.setInstanceFollowRedirects(true);
        return c;
    }
}

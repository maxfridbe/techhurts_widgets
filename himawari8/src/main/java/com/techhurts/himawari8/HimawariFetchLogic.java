package com.techhurts.himawari8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Pure-Java (no Android imports) fetch logic — testable without the Android SDK.
 *
 * Data source: RAMMB/CIRA (Colorado State University) — globally accessible.
 * URL: https://rammb.cira.colostate.edu/ramsdis/online/images/latest_hi_res/himawari-8/full_disk_ahi_true_color.jpg
 * Always serves the latest Himawari-8 AHI true-color full-disk JPEG (~21 MB at full res).
 * Updated approximately every 10 minutes.
 */
class HimawariFetchLogic {

    static final String LATEST_URL =
            "https://rammb.cira.colostate.edu/ramsdis/online/images/latest_hi_res/himawari-8/full_disk_ahi_true_color.jpg";
    static final String USER_AGENT = "TechHurtsHimawari8/1.0 (Android)";

    /** Returns the always-current Himawari-8 full-disk URL. No API call needed. */
    static String fetchLatestImageUrl() {
        return LATEST_URL;
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

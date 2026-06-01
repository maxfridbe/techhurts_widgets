package com.techhurts.goeseast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Pure-Java (no Android imports) fetch logic.
 * Kept in its own class so JVM unit tests can exercise it without the Android SDK.
 *
 * NOAA CDN publishes two always-current static URLs for GOES-19 East full-disk GEOCOLOR:
 *   1808x1808.jpg  ~1.7 MB  (our preferred download size)
 *   latest.jpg     ~10 MB   (full 10848x10848)
 *
 * We simply poll the static 1808x1808 URL every 10 minutes and save it with the
 * current wall-clock timestamp, building our own 24-hour archive locally.
 */
class GoesEastFetchLogic {

    static final String LATEST_URL =
            "https://cdn.star.nesdis.noaa.gov/GOES19/ABI/FD/GEOCOLOR/1808x1808.jpg";
    static final String USER_AGENT = "TechHurtsGoesEast/1.0 (Android)";

    // Top-left 60×60% crop: keeps North America + Arctic, discards southern hemisphere
    // and eastern Atlantic. Produces a square region with no aspect-ratio distortion.
    static final double CROP_LEFT_FRAC   = 0.0;
    static final double CROP_RIGHT_FRAC  = 0.40;
    static final double CROP_TOP_FRAC    = 0.0;
    static final double CROP_BOTTOM_FRAC = 0.40;

    /** Returns the URL of the current GOES-19 East 1808×1808 GEOCOLOR image. */
    static String fetchLatestImageUrl() {
        return LATEST_URL;
    }

    /**
     * Downloads the image and returns the raw JPEG bytes.
     * Caller is responsible for decoding / cropping.
     */
    static byte[] downloadImageBytes(String imageUrl) throws IOException {
        return httpGetBytes(imageUrl, 90_000);
    }

    // ── HTTP helpers (package-private so tests can call them) ────────────────

    static byte[] httpGetBytes(String urlStr, int timeoutMs) throws IOException {
        HttpURLConnection conn = openConn(urlStr, timeoutMs);
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    static HttpURLConnection openConn(String urlStr, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setConnectTimeout(timeoutMs / 3);
        c.setReadTimeout(timeoutMs);
        c.setInstanceFollowRedirects(true);
        return c;
    }
}

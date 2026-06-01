package com.techhurts.goeseast;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies the GOES-19 East fetch logic end-to-end.
 * Runs on the JVM (no Android SDK) because GoesEastFetchLogic has no Android imports.
 */
public class GoesEastFetchTest {

    // ── Static URL sanity checks (no network) ────────────────────────────────

    @Test
    public void latestUrl_isCorrectGoes19CdnPath() {
        String url = GoesEastFetchLogic.fetchLatestImageUrl();
        assertTrue("URL starts with https", url.startsWith("https"));
        assertTrue("URL targets GOES19",    url.contains("GOES19"));
        assertTrue("URL targets GEOCOLOR",  url.contains("GEOCOLOR"));
        assertTrue("URL ends with .jpg",    url.endsWith(".jpg"));
        assertTrue("URL targets 1808 res",  url.contains("1808x1808"));
        System.out.println("[PASS] fetchLatestImageUrl = " + url);
    }

    @Test
    public void cropConstants_topLeft60Percent() {
        assertEquals("no left trim",    0.0,  GoesEastFetchLogic.CROP_LEFT_FRAC,   0.001);
        assertEquals("no top trim",     0.0,  GoesEastFetchLogic.CROP_TOP_FRAC,    0.001);
        assertEquals("right trim 40%",  0.40, GoesEastFetchLogic.CROP_RIGHT_FRAC,  0.001);
        assertEquals("bottom trim 40%", 0.40, GoesEastFetchLogic.CROP_BOTTOM_FRAC, 0.001);
        System.out.println("[PASS] 60%×60% top-left crop constants verified");
    }

    // ── Live network tests ────────────────────────────────────────────────────

    @Test
    public void downloadImageBytes_returnsValidJpeg() throws Exception {
        String url = GoesEastFetchLogic.fetchLatestImageUrl();
        System.out.println("[LIVE] Downloading: " + url);
        byte[] data = GoesEastFetchLogic.downloadImageBytes(url);

        assertTrue("Image data non-empty", data.length > 10_000);
        assertEquals("JPEG magic byte 0",  (byte) 0xFF, data[0]);
        assertEquals("JPEG magic byte 1",  (byte) 0xD8, data[1]);
        assertEquals("JPEG magic byte 2",  (byte) 0xFF, data[2]);

        System.out.printf("[PASS] Downloaded %,d bytes — valid JPEG ✓%n", data.length);
    }

    @Test
    public void cropDimensions_squareResultForSquareSource() {
        // 1808×1808 source, 60%×60% top-left → result must be square and exactly 60%
        int w = 1808, h = 1808;
        int left   = (int)(w * GoesEastFetchLogic.CROP_LEFT_FRAC);
        int top    = (int)(h * GoesEastFetchLogic.CROP_TOP_FRAC);
        int right  = (int)(w * GoesEastFetchLogic.CROP_RIGHT_FRAC);
        int bottom = (int)(h * GoesEastFetchLogic.CROP_BOTTOM_FRAC);
        int cropW  = w - left - right;
        int cropH  = h - top - bottom;

        assertEquals("Left offset 0", 0, left);
        assertEquals("Top  offset 0", 0, top);
        assertEquals("CropW = cropH (square)", cropW, cropH);
        assertTrue("Crop width  ≈ 60% of source", Math.abs(cropW - (int)(w * 0.60)) <= 2);
        assertTrue("Crop within bounds", left + cropW <= w && top + cropH <= h);
        System.out.printf("[PASS] 1808×1808 → %d×%d square (%.1f%% of source)%n",
                cropW, cropH, 100.0 * cropW / w);
    }
}

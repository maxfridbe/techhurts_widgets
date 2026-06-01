package com.techhurts.himawari8;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import java.net.InetSocketAddress;
import java.net.Socket;
import static org.junit.Assert.*;

public class HimawariServiceTest {

    private static boolean sRammb;

    @BeforeClass
    public static void checkConnectivity() {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("rammb.cira.colostate.edu", 443), 4_000);
            s.close();
            sRammb = true;
        } catch (Exception e) {
            sRammb = false;
            System.out.println("[SKIP] RAMMB unreachable: " + e.getMessage());
        }
    }

    @Test
    public void fetchLatestImageUrl_isRammbUrl() {
        String url = HimawariFetchLogic.fetchLatestImageUrl();
        assertTrue("starts with RAMMB host", url.startsWith("https://rammb.cira.colostate.edu/"));
        assertTrue("ends with .jpg", url.endsWith(".jpg"));
        assertTrue("contains himawari", url.toLowerCase().contains("himawari"));
        System.out.println("[PASS] url = " + url);
    }

    @Test
    public void downloadImage_returnsJpeg() throws Exception {
        Assume.assumeTrue("RAMMB not reachable — skipping live test", sRammb);
        String url = HimawariFetchLogic.fetchLatestImageUrl();
        System.out.println("[LIVE] Downloading first 64 KB from: " + url);
        // Only download the start of the file to verify JPEG magic bytes quickly
        java.net.HttpURLConnection conn = HimawariFetchLogic.openConn(url, 30_000);
        byte[] buf = new byte[64];
        conn.getInputStream().read(buf);
        conn.disconnect();
        assertEquals("JPEG SOI [0]", (byte)0xFF, buf[0]);
        assertEquals("JPEG SOI [1]", (byte)0xD8, buf[1]);
        System.out.println("[PASS] JPEG magic bytes confirmed");
    }
}

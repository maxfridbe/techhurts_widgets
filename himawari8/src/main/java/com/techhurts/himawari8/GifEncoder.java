package com.techhurts.himawari8;

import android.graphics.Bitmap;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * Minimal streaming GIF89a encoder with no external dependencies.
 *
 * Usage (streaming — only one Bitmap in memory at a time):
 *   GifEncoder.writeHeader(out, w, h, true);
 *   for each frame:
 *     GifEncoder.writeFrame(out, bitmap, delayMs);
 *     bitmap.recycle();
 *   GifEncoder.writeTrailer(out);
 *
 * Palette: 6×6×6 RGB cube (indices 0–215) + 40 uniform grays (216–255).
 * Grays are sampled separately so cloud detail is rendered well.
 */
class GifEncoder {

    // ── Palette ──────────────────────────────────────────────────────────────

    static final int[] PALETTE = buildPalette();

    private static int[] buildPalette() {
        int[] p = new int[256];
        int i = 0;
        int[] lv = {0, 51, 102, 153, 204, 255};
        for (int r : lv) for (int g : lv) for (int b : lv)
            p[i++] = (r << 16) | (g << 8) | b;
        for (int j = 0; j < 40; j++) {
            int v = Math.round(j * 255f / 39f);
            p[i++] = (v << 16) | (v << 8) | v;
        }
        return p;
    }

    /** Map an ARGB pixel to the nearest palette index. */
    static int mapToIndex(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >>  8) & 0xFF;
        int b =  argb        & 0xFF;
        // Near-gray → use the dedicated gray entries (better cloud detail)
        int maxDiff = Math.max(Math.abs(r - g), Math.max(Math.abs(g - b), Math.abs(r - b)));
        if (maxDiff < 24) {
            int v = (r + g + b) / 3;
            return 216 + Math.min(39, Math.round(v * 39f / 255f));
        }
        int r6 = Math.min(5, (r + 25) / 51);
        int g6 = Math.min(5, (g + 25) / 51);
        int b6 = Math.min(5, (b + 25) / 51);
        return r6 * 36 + g6 * 6 + b6;
    }

    // ── Public streaming API ─────────────────────────────────────────────────

    static void writeHeader(OutputStream out, int width, int height, boolean loop)
            throws IOException {
        out.write(new byte[]{'G','I','F','8','9','a'});
        writeShort(out, width);
        writeShort(out, height);
        out.write(0xF7); // global CT, color res 8, CT size = 256
        out.write(0);    // bg color index
        out.write(0);    // aspect ratio (none)
        // Global Color Table (256 × 3 bytes)
        for (int c : PALETTE) {
            out.write((c >> 16) & 0xFF);
            out.write((c >>  8) & 0xFF);
            out.write( c        & 0xFF);
        }
        if (loop) {
            out.write(0x21); out.write(0xFF); out.write(11);
            out.write(new byte[]{'N','E','T','S','C','A','P','E','2','.','0'});
            out.write(3); out.write(1);
            writeShort(out, 0); // loop count 0 = infinite
            out.write(0);
        }
    }

    static void writeFrame(OutputStream out, Bitmap frame, int delayMs) throws IOException {
        int w = frame.getWidth(), h = frame.getHeight();
        // Quantize pixels
        int[] pixels = new int[w * h];
        frame.getPixels(pixels, 0, w, 0, 0, w, h);
        byte[] indices = new byte[w * h];
        for (int i = 0; i < pixels.length; i++)
            indices[i] = (byte) mapToIndex(pixels[i]);

        // Graphic Control Extension
        out.write(0x21); out.write(0xF9); out.write(4);
        out.write(0); // disposal: none
        writeShort(out, Math.max(2, delayMs / 10)); // delay in centiseconds
        out.write(0); out.write(0);

        // Image Descriptor
        out.write(0x2C);
        writeShort(out, 0); writeShort(out, 0);
        writeShort(out, w); writeShort(out, h);
        out.write(0); // no local CT, not interlaced

        // LZW-compressed pixel data
        writeLzw(out, indices, 8);
    }

    static void writeTrailer(OutputStream out) throws IOException {
        out.write(0x3B);
    }

    // ── LZW ─────────────────────────────────────────────────────────────────

    private static void writeLzw(OutputStream out, byte[] pixels, int minCode)
            throws IOException {
        out.write(minCode);
        int clearCode = 1 << minCode;
        int eoi = clearCode + 1;
        int codeSize = minCode + 1, maxCode = 1 << codeSize, next = eoi + 1;

        HashMap<Integer, Integer> table = new HashMap<>(5003);
        BitPacker bits = new BitPacker();
        bits.add(clearCode, codeSize);

        int cur = pixels[0] & 0xFF;
        for (int i = 1; i < pixels.length; i++) {
            int suf = pixels[i] & 0xFF;
            Integer found = table.get((cur << 8) | suf);
            if (found != null) {
                cur = found;
            } else {
                bits.add(cur, codeSize);
                if (next < 4096) {
                    table.put((cur << 8) | suf, next++);
                    if (next > maxCode && codeSize < 12) { codeSize++; maxCode <<= 1; }
                } else {
                    bits.add(clearCode, codeSize);
                    table.clear();
                    codeSize = minCode + 1; maxCode = 1 << codeSize; next = eoi + 1;
                }
                cur = suf;
            }
        }
        bits.add(cur, codeSize);
        bits.add(eoi, codeSize);
        bits.flush();
        bits.writeSubBlocks(out);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void writeShort(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF); out.write((v >> 8) & 0xFF);
    }

    private static final class BitPacker {
        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(65536);
        private int pending = 0, bits = 0;

        void add(int code, int n) {
            pending |= code << bits; bits += n;
            while (bits >= 8) { buf.write(pending & 0xFF); pending >>= 8; bits -= 8; }
        }
        void flush() {
            if (bits > 0) { buf.write(pending & 0xFF); pending = 0; bits = 0; }
        }
        void writeSubBlocks(OutputStream out) throws IOException {
            byte[] data = buf.toByteArray();
            int i = 0;
            while (i < data.length) {
                int sz = Math.min(255, data.length - i);
                out.write(sz);
                out.write(data, i, sz);
                i += sz;
            }
            out.write(0);
        }
    }
}

package com.techhurts.goeseast;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * Encodes a sequence of Bitmaps into an H.264 MP4 using MediaCodec + EGL.
 * Each Bitmap is uploaded to a GL texture and rendered to the codec's input
 * Surface one frame at a time — no more than one decoded Bitmap in RAM at once.
 */
class Mp4Encoder {

    private static final int TIMEOUT_US = 10_000;
    // EGL_RECORDABLE_ANDROID — required so the EGL surface feeds into MediaCodec
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    interface Progress { void update(int done, int total); }

    /**
     * Encode frames from disk into an MP4.
     *
     * @param frames  list of frame files (loaded one at a time to save RAM)
     * @param fps     playback frame rate (matches the animation setting)
     * @param outFile destination file (will be overwritten)
     * @param cb      optional progress callback (runs on the caller's thread)
     */
    static void encode(List<File> frames, int fps, File outFile, Progress cb) throws Exception {
        if (frames.isEmpty()) throw new IllegalArgumentException("No frames");

        // Determine output size from first frame (ensure even dimensions for H.264)
        android.graphics.BitmapFactory.Options probe = new android.graphics.BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(frames.get(0).getPath(), probe);
        int w = (probe.outWidth  > 0 ? probe.outWidth  : 480) & ~1;
        int h = (probe.outHeight > 0 ? probe.outHeight : 480) & ~1;

        // --- MediaCodec setup ---
        MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        encoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface inputSurface = encoder.createInputSurface();
        encoder.start();

        // --- EGL + GL setup ---
        EglHelper egl = new EglHelper(inputSurface);

        // --- MediaMuxer ---
        MediaMuxer muxer = new MediaMuxer(outFile.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int trackIndex = -1;
        boolean muxerStarted = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long frameDurationUs = 1_000_000L / fps;

        try {
            for (int i = 0; i < frames.size(); i++) {
                if (cb != null) cb.update(i, frames.size());

                // Load and draw frame
                Bitmap raw = android.graphics.BitmapFactory.decodeFile(frames.get(i).getPath());
                if (raw == null) continue;
                Bitmap scaled = (raw.getWidth() == w && raw.getHeight() == h) ? raw
                        : Bitmap.createScaledBitmap(raw, w, h, true);
                if (scaled != raw) raw.recycle();

                egl.drawBitmap(scaled);
                if (scaled != raw) scaled.recycle(); else raw.recycle();

                // Set presentation timestamp and submit frame
                long ptsNs = (long) i * frameDurationUs * 1000L;
                EGLExt.eglPresentationTimeANDROID(egl.display, egl.surface, ptsNs);
                EGL14.eglSwapBuffers(egl.display, egl.surface);

                // Drain encoder output
                drain:
                while (true) {
                    int idx = encoder.dequeueOutputBuffer(info, TIMEOUT_US);
                    switch (idx) {
                        case MediaCodec.INFO_TRY_AGAIN_LATER: break drain;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            trackIndex = muxer.addTrack(encoder.getOutputFormat());
                            muxer.start();
                            muxerStarted = true;
                            break;
                        default:
                            if (idx >= 0) {
                                writeBuffer(encoder, muxer, trackIndex, muxerStarted, info, idx);
                                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                                    break drain;
                            }
                    }
                }
            }

            // Signal end and drain remaining
            encoder.signalEndOfInputStream();
            drain:
            while (true) {
                int idx = encoder.dequeueOutputBuffer(info, 50_000);
                switch (idx) {
                    case MediaCodec.INFO_TRY_AGAIN_LATER: break drain;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.getOutputFormat());
                            muxer.start(); muxerStarted = true;
                        }
                        break;
                    default:
                        if (idx >= 0) {
                            writeBuffer(encoder, muxer, trackIndex, muxerStarted, info, idx);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                                break drain;
                        }
                }
            }
        } finally {
            if (muxerStarted) { muxer.stop(); }
            muxer.release();
            encoder.stop();
            encoder.release();
            egl.release();
            inputSurface.release();
        }
    }

    private static void writeBuffer(MediaCodec enc, MediaMuxer muxer, int track,
                                     boolean muxerStarted, MediaCodec.BufferInfo info, int idx) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                && muxerStarted && track >= 0 && info.size > 0) {
            ByteBuffer buf = enc.getOutputBuffer(idx);
            if (buf != null) muxer.writeSampleData(track, buf, info);
        }
        enc.releaseOutputBuffer(idx, false);
    }

    // ── EGL / OpenGL helper ───────────────────────────────────────────────

    private static final class EglHelper {
        final EGLDisplay display;
        final EGLSurface surface;
        final EGLContext context;
        private final int program, posLoc, texLoc, samplerLoc;
        private final int[] texId = new int[1];
        private final FloatBuffer posBuf, texBuf;

        EglHelper(Surface codecSurface) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            EGL14.eglInitialize(display, null, 0, null, 0);

            int[] cfgAttribs = {
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE
            };
            EGLConfig[] cfgs = new EGLConfig[1]; int[] n = new int[1];
            EGL14.eglChooseConfig(display, cfgAttribs, 0, cfgs, 0, 1, n, 0);

            context = EGL14.eglCreateContext(display, cfgs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            surface = EGL14.eglCreateWindowSurface(display, cfgs[0], codecSurface,
                    new int[]{EGL14.EGL_NONE}, 0);
            EGL14.eglMakeCurrent(display, surface, surface, context);

            // Compile shaders
            String vs = "attribute vec4 pos; attribute vec2 tc; varying vec2 v; void main(){gl_Position=pos;v=tc;}";
            String fs = "precision mediump float; uniform sampler2D s; varying vec2 v; void main(){gl_FragColor=texture2D(s,v);}";
            program  = buildProgram(vs, fs);
            posLoc   = GLES20.glGetAttribLocation(program, "pos");
            texLoc   = GLES20.glGetAttribLocation(program, "tc");
            samplerLoc = GLES20.glGetUniformLocation(program, "s");

            // Fullscreen quad (flip Y so bitmap top → GL bottom)
            posBuf = floatBuf(new float[]{-1f,-1f,  1f,-1f, -1f,1f,  1f,1f});
            texBuf = floatBuf(new float[]{ 0f, 1f,  1f, 1f,  0f,0f,  1f,0f});

            GLES20.glGenTextures(1, texId, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }

        void drawBitmap(Bitmap bmp) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
            GLES20.glUniform1i(samplerLoc, 0);
            GLES20.glEnableVertexAttribArray(posLoc);
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, posBuf);
            GLES20.glEnableVertexAttribArray(texLoc);
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texBuf);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        void release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            GLES20.glDeleteTextures(1, texId, 0);
            GLES20.glDeleteProgram(program);
            EGL14.eglDestroySurface(display, surface);
            EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
        }

        private static int buildProgram(String vs, String fs) {
            int v = compile(GLES20.GL_VERTEX_SHADER, vs);
            int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f); GLES20.glLinkProgram(p);
            return p;
        }
        private static int compile(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src); GLES20.glCompileShader(s); return s;
        }
        private static FloatBuffer floatBuf(float[] a) {
            FloatBuffer b = ByteBuffer.allocateDirect(a.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            b.put(a); b.position(0); return b;
        }
    }
}

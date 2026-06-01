package com.techhurts.goeseast;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GoesEastAnimationActivity extends Activity {

    private static final int[] FPS_PRESETS = {1, 2, 5, 10, 15, 24, 30};
    private static final SimpleDateFormat TS_FMT = new SimpleDateFormat("MM/dd HH:mm", Locale.US);
    private static final int GIF_FRAME_SIZE = 320;

    private ImageView   mImageView;
    private TextView    mTimestampView;
    private TextView    mCounterView;
    private TextView    mFpsView;
    private ProgressBar mLoadingView;
    private Button      mBtnCrop;
    private Button      mBtnShareMp4;
    private Button      mBtnShareGif;
    private Button      mBtnShareWebp;
    private Button      mBtnClear;
    private Button      mBtnClose;

    private List<File> mFrames;
    private int        mCurrentFrame = 0;
    private int        mFpsIndex = 0;
    private Bitmap     mCurrentBitmap;
    private boolean    mGenerating = false;

    private final Handler  mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mAdvance = new Runnable() {
        @Override public void run() { advanceFrame(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goeseast_animation);

        mImageView     = findViewById(R.id.animation_image);
        mTimestampView = findViewById(R.id.animation_timestamp);
        mCounterView   = findViewById(R.id.animation_counter);
        mFpsView       = findViewById(R.id.tv_fps);
        mLoadingView   = findViewById(R.id.animation_loading);
        mBtnCrop       = findViewById(R.id.btn_crop);
        mBtnShareMp4   = findViewById(R.id.btn_share_mp4);
        mBtnShareGif   = findViewById(R.id.btn_share_gif);
        mBtnShareWebp  = findViewById(R.id.btn_share_webp);
        mBtnClear      = findViewById(R.id.btn_clear);
        mBtnClose      = findViewById(R.id.btn_close);

        mBtnClose.setOnClickListener(v -> finish());
        mBtnClear.setOnClickListener(v -> clearHistory());
        mBtnCrop.setOnClickListener(v -> showCropDialog());
        mBtnShareMp4.setOnClickListener(v -> { if (!mGenerating) generateAndShare("mp4"); });
        mBtnShareGif.setOnClickListener(v -> { if (!mGenerating) generateAndShare("gif"); });
        mBtnShareWebp.setOnClickListener(v -> { if (!mGenerating) generateAndShare("webp"); });
        findViewById(R.id.btn_fps_down).setOnClickListener(v -> changeFps(-1));
        findViewById(R.id.btn_fps_up).setOnClickListener(v -> changeFps(+1));

        mFrames = ImageStore.getLast24h(this);
        updateFpsLabel();
        updateGifButtonLabel();
        if (mFrames.isEmpty()) {
            mLoadingView.setVisibility(View.GONE);
            mTimestampView.setText("No images stored yet — check back in 10 minutes.");
            return;
        }
        mLoadingView.setVisibility(View.GONE);
        mHandler.post(mAdvance);
    }

    // ── Crop config dialog ────────────────────────────────────────────────

    private void showCropDialog() {
        final float[] cfg = {CropConfig.getZoom(this), CropConfig.getX(this), CropConfig.getY(this)};

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, dp(12), pad, dp(8));
        root.setBackgroundColor(0xFF1a1a1a);

        // Live preview — full-disk thumbnail with dimmed surround + red crop border
        CropPreviewView preview = new CropPreviewView(this, cfg[0], cfg[1], cfg[2]);
        LinearLayout.LayoutParams pvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(260));
        root.addView(preview, pvLp);

        // Load the saved full-disk thumbnail (300×300) in background
        File pf = ImageStore.getPreview(this);
        if (pf != null) {
            new Thread(() -> {
                Bitmap bmp = android.graphics.BitmapFactory.decodeFile(pf.getPath());
                if (bmp != null) preview.post(() -> preview.setBitmap(bmp));
            }).start();
        }

        // Labels
        final TextView tvZoom = label("");
        final TextView tvX    = label("");
        final TextView tvY    = label("");

        // Single update routine called by all sliders + presets
        final Runnable sync = () -> {
            tvZoom.setText("Zoom: " + String.format(Locale.US, "%.1f×", cfg[0]));
            tvX.setText("X centre: " + pct(cfg[1]));
            tvY.setText("Y centre: " + pct(cfg[2]));
            preview.setCrop(cfg[0], cfg[1], cfg[2]);
        };
        sync.run(); // populate initial labels

        SeekBar sbZ = seekBar(0, 40, Math.round((cfg[0] - 1f) * 10), p -> {
            cfg[0] = 1f + p / 10f; sync.run();
        });
        SeekBar sbX = seekBar(0, 100, Math.round(cfg[1] * 100), p -> {
            cfg[1] = p / 100f; sync.run();
        });
        SeekBar sbY = seekBar(0, 100, Math.round(cfg[2] * 100), p -> {
            cfg[2] = p / 100f; sync.run();
        });

        // Presets
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setPadding(0, dp(8), 0, 0);
        addPreset(presets, "Full Disk", 1.0f,  0.50f, 0.50f, cfg, sbZ, sbX, sbY, sync);
        addPreset(presets, "Default",   1.67f, 0.30f, 0.30f, cfg, sbZ, sbX, sbY, sync);

        root.addView(tvZoom); root.addView(sbZ);
        root.addView(tvX);    root.addView(sbX);
        root.addView(tvY);    root.addView(sbY);
        root.addView(presets);

        new AlertDialog.Builder(this)
                .setTitle("Region")
                .setView(root)
                .setPositiveButton("Apply & Refresh", (d, w) -> {
                    CropConfig.save(this, cfg[0], cfg[1], cfg[2]);
                    GoesEastFetchReceiver.triggerFetch(this);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addPreset(LinearLayout row, String lbl, float z, float x, float y,
                            float[] cfg, SeekBar sbZ, SeekBar sbX, SeekBar sbY, Runnable sync) {
        Button b = new Button(this);
        b.setText(lbl);
        b.setTextSize(11);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginEnd(dp(4));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> {
            cfg[0] = z; cfg[1] = x; cfg[2] = y;
            sbZ.setProgress(Math.round((z - 1f) * 10));
            sbX.setProgress(Math.round(x * 100));
            sbY.setProgress(Math.round(y * 100));
            sync.run();
        });
        row.addView(b);
    }

    // ── FPS control ───────────────────────────────────────────────────────

    private void updateGifButtonLabel() {
        int n = mFrames == null ? 0 : mFrames.size();
        mBtnShareGif.setText(n <= 1 ? "GIF (1fr—static)" : "GIF (" + n + "fr)");
    }

    private void changeFps(int delta) {
        mFpsIndex = Math.max(0, Math.min(FPS_PRESETS.length - 1, mFpsIndex + delta));
        updateFpsLabel();
    }

    private void updateFpsLabel() { mFpsView.setText(FPS_PRESETS[mFpsIndex] + "fps"); }
    private long frameDelayMs()   { return 1000L / FPS_PRESETS[mFpsIndex]; }

    // ── Animation ─────────────────────────────────────────────────────────

    private void advanceFrame() {
        if (isFinishing() || mFrames.isEmpty()) return;
        final int idx = mCurrentFrame;
        final long start = SystemClock.elapsedRealtime();
        final File f = mFrames.get(idx);
        // Stored files are already cropped+burned at fetch time — display as-is
        new Thread(() -> {
            Bitmap bmp = BitmapFactory.decodeFile(f.getPath());
            runOnUiThread(() -> {
                if (isFinishing()) { if (bmp != null) bmp.recycle(); return; }
                if (mCurrentBitmap != null) mCurrentBitmap.recycle();
                mCurrentBitmap = bmp;
                mImageView.setImageBitmap(bmp);
                mTimestampView.setText(TS_FMT.format(new Date(f.lastModified())));
                if (!mGenerating) mCounterView.setText((idx + 1) + "/" + mFrames.size());
                mCurrentFrame = (idx + 1) % mFrames.size();
                long elapsed = SystemClock.elapsedRealtime() - start;
                mHandler.postDelayed(mAdvance, Math.max(0, frameDelayMs() - elapsed));
            });
        }).start();
    }

    // ── Share ─────────────────────────────────────────────────────────────

    private void generateAndShare(String type) {
        if (mFrames.isEmpty()) { Toast.makeText(this, "No frames", Toast.LENGTH_SHORT).show(); return; }
        mGenerating = true;
        setShareButtonsEnabled(false);

        final List<File> frames = ImageStore.getLast24h(this);
        final int fps = FPS_PRESETS[mFpsIndex];
        final int delayMs = 1000 / fps;

        new Thread(() -> {
            try {
                String mime, ext;
                File outFile;

                if ("mp4".equals(type)) {
                    ext = "mp4"; mime = "video/mp4";
                    outFile = new File(getCacheDir(), "goeseast_animation.mp4");
                    // Stored frames already have crop+timestamp — encode directly
                    Mp4Encoder.encode(frames, fps, outFile, (done, total) ->
                            runOnUiThread(() -> mCounterView.setText("MP4 " + done + "/" + total)));

                } else if ("gif".equals(type)) {
                    ext = "gif"; mime = "image/gif";
                    outFile = new File(getCacheDir(), "goeseast_animation.gif");
                    BitmapFactory.Options probe = new BitmapFactory.Options();
                    probe.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(frames.get(0).getPath(), probe);
                    int sz = Math.min(GIF_FRAME_SIZE, Math.min(probe.outWidth, probe.outHeight));
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        GifEncoder.writeHeader(fos, sz, sz, true);
                        for (int i = 0; i < frames.size(); i++) {
                            final int idx = i;
                            runOnUiThread(() -> mCounterView.setText("GIF " + (idx+1) + "/" + frames.size()));
                            Bitmap raw = BitmapFactory.decodeFile(frames.get(i).getPath());
                            if (raw == null) continue;
                            Bitmap scaled = Bitmap.createScaledBitmap(raw, sz, sz, true);
                            raw.recycle();
                            GifEncoder.writeFrame(fos, scaled, delayMs);
                            scaled.recycle();
                        }
                        GifEncoder.writeTrailer(fos);
                    }
                } else {
                    ext = "webp"; mime = "image/webp";
                    outFile = new File(getCacheDir(), "goeseast_latest.webp");
                    File latestFile = ImageStore.getLatest(this);
                    if (latestFile == null) throw new Exception("No frames");
                    Bitmap bmp = BitmapFactory.decodeFile(latestFile.getPath());
                    if (bmp == null) throw new Exception("Decode failed");
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        Bitmap.CompressFormat fmt = Build.VERSION.SDK_INT >= 30
                                ? Bitmap.CompressFormat.WEBP_LOSSLESS : Bitmap.CompressFormat.WEBP;
                        bmp.compress(fmt, 90, fos);
                    }
                    bmp.recycle();
                }

                Uri uri = Uri.parse("content://" + GoesEastContentProvider.AUTHORITY
                        + "/cache/" + outFile.getName());
                Intent share = new Intent(Intent.ACTION_SEND).setType(mime)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> {
                    mGenerating = false; setShareButtonsEnabled(true);
                    mCounterView.setText((mCurrentFrame + 1) + "/" + mFrames.size());
                    startActivity(Intent.createChooser(share, "Share GOES East " + ext.toUpperCase()));
                });
            } catch (Throwable t) {
                android.util.Log.e("GoesEast", "Share failed", t);
                runOnUiThread(() -> {
                    mGenerating = false; setShareButtonsEnabled(true);
                    Toast.makeText(this, "Failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void setShareButtonsEnabled(boolean en) {
        mBtnShareMp4.setEnabled(en); mBtnShareGif.setEnabled(en);
        mBtnShareWebp.setEnabled(en); mBtnClear.setEnabled(en);
    }

    // ── Clear ─────────────────────────────────────────────────────────────

    private void clearHistory() {
        mHandler.removeCallbacks(mAdvance);
        File[] files = ImageStore.getDir(this).listFiles();
        if (files != null) for (File f : files) f.delete();
        GoesEastWidgetProvider.updateAllWidgets(this);
        GoesEastFetchReceiver.triggerFetch(this);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mAdvance);
        if (mCurrentBitmap != null) mCurrentBitmap.recycle();
    }

    // ── UI helpers ────────────────────────────────────────────────────────

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setTextColor(0xFFCCCCCC);
        tv.setTextSize(13);
        tv.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private SeekBar seekBar(int min, int max, int progress, java.util.function.IntConsumer onChange) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(progress - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { onChange.accept(p + min); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        return sb;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static String pct(float f) { return Math.round(f * 100) + "%"; }
}

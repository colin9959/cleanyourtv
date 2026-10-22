package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Idle screensaver scene: full-bleed crossfading pictures from the Bing wallpaper collection and the
 * phone-uploaded folder, over a quiet clock. Any key leaves (handled by the host activity).
 */
final class SaverScene {

    private static final long PERIOD_MS = 8_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ImageView image;
    private final List<File> files = new ArrayList<>();
    private final Random random = new Random();
    private final int screenW, screenH;
    private boolean active;
    private int index = -1;
    private String shown = "";

    SaverScene(Context ctx, View root) {
        image = root.findViewById(R.id.saverImage);
        DisplayMetrics m = ctx.getResources().getDisplayMetrics();
        screenW = m.widthPixels;
        screenH = m.heightPixels;
        File base = ctx.getExternalFilesDir(null);
        if (base != null) {
            addDir(new File(base, "wallpapers"));
            addDir(new File(base, "uploads"));
        }
    }

    private void addDir(File dir) {
        files.addAll(WallpaperRotation.images(dir));
    }

    void start() {
        active = true;
        if (files.isEmpty()) return;
        handler.removeCallbacks(tick);
        showNext();
        handler.postDelayed(tick, PERIOD_MS);
    }

    void stop() {
        active = false;
        handler.removeCallbacks(tick);
        worker.shutdownNow();
    }

    /** Show one picture, then schedule the next so the collection keeps cycling. */
    private final Runnable tick = this::onTick;

    private void onTick() {
        showNext();
        if (active) handler.postDelayed(tick, PERIOD_MS);
    }

    private void showNext() {
        if (!active || files.isEmpty()) return;
        int next;
        if (files.size() == 1) {
            next = 0;
        } else {
            do {
                next = random.nextInt(files.size());
            } while (next == index);
        }
        index = next;
        File file = files.get(next);
        worker.execute(() -> {
            Bitmap bitmap = decode(file);
            handler.post(() -> {
                if (!active || bitmap == null) return;
                image.animate().alpha(0f).setDuration(600).withEndAction(() -> {
                    image.setImageBitmap(bitmap);
                    image.animate().alpha(1f).setDuration(900).start();
                }).start();
                shown = file.getName();
            });
        });
    }

    private Bitmap decode(File file) {
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), o);
            int sample = WallpaperSize.sampleSize(o.outWidth, o.outHeight, screenW, screenH);
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = BitmapFactory.decodeFile(file.getPath(), o);
        } catch (Exception | OutOfMemoryError ignored) {
            return null;
        }
        if (bitmap == null) return null;
        int degrees = orientationDegrees(file);
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    private static int orientationDegrees(File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getPath());
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
}

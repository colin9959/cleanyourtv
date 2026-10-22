package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.File;

/**
 * Phone-upload page: QR for the LAN upload server; the phone opens it and sends album pictures into
 * uploads/, which the idle screensaver rotates. UI is built in code so centering does not depend on
 * the theme's inherited gravity.
 */
public class UploadActivity extends Activity {

    private UploadServer server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File uploads = new File(getExternalFilesDir(null), "uploads");
        server = new UploadServer(uploads);
        server.start();

        String url = "http://" + lanIp() + ":" + UploadServer.PORT;
        float dp = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101418);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = text("手机扫码上传屏保照片", 32, 0xFFFFFFFF, 28, dp);
        col.addView(title);

        ImageView qr = new ImageView(this);
        int qrSize = (int) (280 * dp);
        FrameLayout.LayoutParams qrLp = new FrameLayout.LayoutParams(qrSize, qrSize);
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding((int) (14 * dp), (int) (14 * dp), (int) (14 * dp), (int) (14 * dp));
        Bitmap bitmap = qr(url, 600);
        if (bitmap != null) qr.setImageBitmap(bitmap);
        col.addView(qr, qrLp);

        col.addView(text(url, 22, 0xB3FFFFFF, 24, dp));
        col.addView(text("手机与电视连同一 WiFi，扫码选相册图片即可", 16, 0xFF7A8794, 10, dp));

        FrameLayout.LayoutParams colLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(col, colLp);
        setContentView(root);
    }

    private TextView text(String value, float sp, int color, float marginTopDp, float dp) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (marginTopDp * dp);
        t.setLayoutParams(lp);
        return t;
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    private String lanIp() {
        WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        int ip = wm == null ? 0 : wm.getConnectionInfo().getIpAddress();
        return String.format("%d.%d.%d.%d", ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
    }

    private static Bitmap qr(String text, int size) {
        try {
            BitMatrix m = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                    bmp.setPixel(x, y, m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }
}

package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Remote-friendly manager for phone-uploaded screensaver pictures. Rows are focused with D-pad;
 * the focused row highlights and CENTER opens a confirm-delete dialog. No small side button to reach.
 */
public class UploadsManageActivity extends Activity {

    private File uploads;
    private float dp;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        uploads = new File(getExternalFilesDir(null), "uploads");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101418);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (48 * dp);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        render();
    }

    private void render() {
        root.removeAllViews();

        TextView title = text("上传的屏保图片（" + count() + " 张）", 30, 0xFFFFFFFF, 0);
        root.addView(title);

        TextView hint = text("上下键选择图片，按「确定」删除，返回键退出", 16, 0xFF8FA0AD, 14);
        root.addView(hint);

        List<File> files = files();
        if (files.isEmpty()) {
            TextView empty = text("暂无上传图片，可在设置页扫码上传。", 18, 0xFF7A8794, 28);
            root.addView(empty);
            return;
        }
        for (int i = 0; i < files.size(); i++) {
            LinearLayout r = row(files.get(i), i + 1);
            if (i == 0) r.post(r::requestFocus);
            root.addView(r);
        }
    }

    private LinearLayout row(File file, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundColor(0xFF1B222B);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (112 * dp));
        rp.bottomMargin = (int) (14 * dp);
        rp.leftMargin = rp.rightMargin = (int) (8 * dp);
        row.setLayoutParams(rp);

        // Visible cursor: ▶ only on the focused row, so the current pick is unambiguous.
        TextView cursor = new TextView(this);
        cursor.setText("▶");
        cursor.setTextSize(26);
        cursor.setTextColor(0xFF4FC3F7);
        cursor.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                (int) (44 * dp), LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = (int) (12 * dp);
        cp.rightMargin = (int) (6 * dp);
        cursor.setLayoutParams(cp);
        row.addView(cursor);

        ImageView thumb = new ImageView(this);
        int size = (int) (78 * dp);
        Bitmap bmp = thumbnail(file);
        if (bmp != null) thumb.setImageBitmap(bmp);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(size, size);
        tp.rightMargin = (int) (20 * dp);
        thumb.setLayoutParams(tp);
        row.addView(thumb);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(index + ".  " + file.getName());
        name.setTextSize(19);
        name.setTextColor(0xFFFFFFFF);
        textCol.addView(name);
        TextView sub = new TextView(this);
        sub.setText("按「确定」删除这张");
        sub.setTextSize(14);
        sub.setTextColor(0xFF8FA0AD);
        textCol.addView(sub);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(textCol, np);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackgroundColor(hasFocus ? 0xFF2E5A86 : 0xFF1B222B);
            cursor.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            name.setTextColor(hasFocus ? 0xFFFFFFFF : 0xFFD7DEE5);
            sub.setTextColor(hasFocus ? 0xFFFFB3A8 : 0xFF8FA0AD);
            v.setSelected(hasFocus);
        });
        row.setOnClickListener(v -> confirmDelete(file));
        return row;
    }

    private void confirmDelete(File file) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除这张屏保图片？")
                .setMessage(file.getName())
                .setPositiveButton("删除", (d, w) -> {
                    if (file.delete()) {
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                        render();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        // Default focus "取消" so an accidental CENTER/BACK never confirms deletion.
        dialog.setOnShowListener(d -> {
            android.widget.Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (cancel != null) cancel.requestFocus();
        });
        dialog.show();
    }

    private List<File> files() {
        File[] all = uploads.listFiles();
        if (all == null) return new ArrayList<>();
        Arrays.sort(all, (a, b) -> a.getName().compareTo(b.getName()));
        return new ArrayList<>(Arrays.asList(all));
    }

    private int count() {
        File[] all = uploads.listFiles();
        return all == null ? 0 : all.length;
    }

    private Bitmap thumbnail(File file) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), o);
            int target = (int) (76 * dp);
            int sample = 1;
            int side = Math.max(o.outWidth, o.outHeight);
            while (side / sample > target * 2) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private TextView text(String value, float sp, int color, float marginTopDp) {
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
}

package com.LT.SegNBPicHooker.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.LT.SegNBPicHooker.R;
import com.LT.SegNBPicHooker.data.VehImageStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 模块界面：选择车辆图 / 恢复默认。
 *
 * <p>选中的图片按原始字节存进模块私有目录，并给状态打上新的 rev；
 * 九号出行进程里的 hook 会通过 {@link com.LT.SegNBPicHooker.provider.VehImageProvider}
 * 拉取这份图片，因此两边不需要共享存储权限。
 */
public class MainActivity extends AppCompatActivity {

    private static final long MAX_PICK_BYTES = 24L * 1024 * 1024;
    private static final int PREVIEW_MAX = 1280;

    private ImageView preview;
    private View emptyHint;
    private TextView status;
    private TextView detail;

    private final ActivityResultLauncher<String> pickLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.preview);
        emptyHint = findViewById(R.id.empty_hint);
        status = findViewById(R.id.status);
        detail = findViewById(R.id.detail);

        MaterialButton pick = findViewById(R.id.btn_pick);
        MaterialButton reset = findViewById(R.id.btn_reset);
        pick.setOnClickListener(v -> pickLauncher.launch("image/*"));
        reset.setOnClickListener(v -> restoreDefault());

        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    // ------------------------------------------------------------------ 选图

    private void onPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        File tmp = new File(getCacheDir(), "picked.tmp");
        try {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) {
                    throw new IllegalStateException(getString(R.string.err_read));
                }
                copyLimited(in, out);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getAbsolutePath(), opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                throw new IllegalStateException(getString(R.string.err_not_image));
            }
            String ext = VehImageStore.pickExt(getContentResolver().getType(uri), uri.getLastPathSegment());
            VehImageStore.save(this, tmp, ext, nameOf(uri), opts.outWidth, opts.outHeight);
            snack(getString(R.string.snack_picked));
        } catch (Exception e) {
            snack(getString(R.string.snack_failed, String.valueOf(e.getMessage())));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            render();
        }
    }

    private void restoreDefault() {
        VehImageStore.reset(this);
        snack(getString(R.string.snack_reset));
        render();
    }

    // ------------------------------------------------------------------ 渲染

    private void render() {
        boolean active = VehImageStore.active(this);
        File f = VehImageStore.imageFile(this);

        Bitmap bm = active ? decodePreview(f) : null;
        if (bm != null) {
            preview.setImageBitmap(bm);
            preview.setVisibility(View.VISIBLE);
            emptyHint.setVisibility(View.GONE);
            status.setText(R.string.ui_status_on);
            detail.setText(getString(R.string.ui_detail_on,
                    emptyToDash(VehImageStore.srcName(this)),
                    VehImageStore.srcWidth(this),
                    VehImageStore.srcHeight(this),
                    readableSize(VehImageStore.srcSize(this)),
                    timeText(VehImageStore.updatedAt(this))));
        } else {
            preview.setImageDrawable(null);
            preview.setVisibility(View.INVISIBLE);
            emptyHint.setVisibility(View.VISIBLE);
            status.setText(R.string.ui_status_off);
            detail.setText(R.string.ui_detail_off);
        }
    }

    private static Bitmap decodePreview(File f) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= PREVIEW_MAX
                    || bounds.outHeight / (sample * 2) >= PREVIEW_MAX) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 工具

    private static void copyLimited(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[64 * 1024];
        long total = 0L;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_PICK_BYTES) {
                throw new IllegalStateException("图片过大");
            }
            out.write(buf, 0, n);
        }
        out.flush();
    }

    private static String nameOf(Uri uri) {
        String s = uri.getLastPathSegment();
        return s == null ? "" : s;
    }

    private static String emptyToDash(String s) {
        return s == null || s.length() == 0 ? "-" : s;
    }

    private static String readableSize(long bytes) {
        if (bytes <= 0L) {
            return "-";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String timeText(long at) {
        if (at <= 0L) {
            return "-";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(at));
    }

    private void snack(String text) {
        Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG).show();
    }
}

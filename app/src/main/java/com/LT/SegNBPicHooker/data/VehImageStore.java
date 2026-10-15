package com.LT.SegNBPicHooker.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * 模块界面与 hook 之间的共享状态。
 *
 * <p>界面进程负责写入（用户选图 / 恢复默认），被 hook 的九号出行进程通过
 * {@link com.LT.SegNBPicHooker.provider.VehImageProvider} 读取，
 * 因此这里定义的所有常量同时被两侧使用。
 */
public final class VehImageStore {

    /** 供九号出行进程读取图片的 ContentProvider authority。 */
    public static final String AUTHORITY = "com.LT.SegNBPicHooker.vehimg";
    public static final String BASE_URI = "content://" + AUTHORITY + "/";
    public static final String METHOD_STATE = "state";

    public static final String K_ENABLED = "enabled";
    public static final String K_REV = "rev";
    public static final String K_EXT = "ext";
    public static final String K_NAME = "name";
    public static final String K_W = "w";
    public static final String K_H = "h";
    public static final String K_SIZE = "size";
    public static final String K_TIME = "time";

    private static final String PREFS = "veh_image";
    private static final String BASE_NAME = "vehicle";
    private static final String[] EXTS = {"png", "jpg", "jpeg", "webp", "gif", "bmp"};

    private VehImageStore() {
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 当前自定义图片的扩展名（默认 png）。 */
    public static String ext(Context c) {
        return prefs(c).getString(K_EXT, "png");
    }

    /** 自定义图片在模块私有目录里的路径。 */
    public static File imageFile(Context c) {
        return new File(c.getFilesDir(), BASE_NAME + "." + ext(c));
    }

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(K_ENABLED, false);
    }

    /** 每次更换/恢复都会 +1，用来让被 hook 的 App 以及图片库的缓存失效。 */
    public static long rev(Context c) {
        return prefs(c).getLong(K_REV, 0L);
    }

    public static String srcName(Context c) {
        return prefs(c).getString(K_NAME, "");
    }

    public static int srcWidth(Context c) {
        return prefs(c).getInt(K_W, 0);
    }

    public static int srcHeight(Context c) {
        return prefs(c).getInt(K_H, 0);
    }

    public static long srcSize(Context c) {
        return prefs(c).getLong(K_SIZE, 0L);
    }

    public static long updatedAt(Context c) {
        return prefs(c).getLong(K_TIME, 0L);
    }

    /** 图片是否处于「已启用且文件可用」状态。 */
    public static boolean active(Context c) {
        File f = imageFile(c);
        return isEnabled(c) && f.exists() && f.length() > 0L;
    }

    /**
     * 保存用户选择的图片：按原始字节拷贝，不做二次编码，避免掉画质。
     *
     * @param src 选图后落到缓存目录的临时文件
     */
    public static void save(Context c, File src, String ext, String name, int w, int h) throws Exception {
        File dst = new File(c.getFilesDir(), BASE_NAME + "." + ext);
        deleteOtherImages(c, dst);
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            copy(in, out);
        }
        prefs(c).edit()
                .putBoolean(K_ENABLED, true)
                .putLong(K_REV, rev(c) + 1L)
                .putString(K_EXT, ext)
                .putString(K_NAME, name == null ? "" : name)
                .putInt(K_W, w)
                .putInt(K_H, h)
                .putLong(K_SIZE, dst.length())
                .putLong(K_TIME, System.currentTimeMillis())
                .apply();
    }

    /** 恢复九号出行默认车辆图：停用替换并删除自定义图片。 */
    public static void reset(Context c) {
        prefs(c).edit()
                .putBoolean(K_ENABLED, false)
                .putLong(K_REV, rev(c) + 1L)
                .remove(K_NAME)
                .remove(K_W)
                .remove(K_H)
                .remove(K_SIZE)
                .remove(K_TIME)
                .apply();
        deleteOtherImages(c, null);
    }

    private static void deleteOtherImages(Context c, File keep) {
        for (String e : EXTS) {
            File f = new File(c.getFilesDir(), BASE_NAME + "." + e);
            if (!f.equals(keep)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /** 由 MIME 或文件名推断扩展名。 */
    public static String pickExt(String mime, String displayName) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.US);
        if (m.contains("png")) {
            return "png";
        }
        if (m.contains("webp")) {
            return "webp";
        }
        if (m.contains("gif")) {
            return "gif";
        }
        if (m.contains("bmp")) {
            return "bmp";
        }
        if (m.contains("jpeg") || m.contains("jpg")) {
            return "jpg";
        }
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.US);
        for (String e : EXTS) {
            if (name.endsWith("." + e)) {
                return e;
            }
        }
        return "png";
    }

    public static String mimeOf(String ext) {
        if (ext == null) {
            return "image/png";
        }
        switch (ext.toLowerCase(Locale.US)) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "webp":
                return "image/webp";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            default:
                return "image/png";
        }
    }

    public static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        out.flush();
    }
}

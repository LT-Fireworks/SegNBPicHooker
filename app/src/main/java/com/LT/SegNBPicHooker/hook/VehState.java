package com.LT.SegNBPicHooker.hook;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import com.LT.SegNBPicHooker.data.VehImageStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * 九号出行进程内的车辆图状态。
 *
 * <p>状态来自模块界面的 ContentProvider；为省掉每次图片加载的跨进程调用，结果按 TTL 做短缓存。
 * 取到图片后会在宿主自己的外部私有目录里落一份副本，再交给宿主原有的「file:// + Glide」链路，
 * 这与不带模块时宿主自己的加载方式一致，兼容性最好；副本文件名带 rev，天然绕开图片库的磁盘缓存。
 */
final class VehState {

    private static final long TTL_MS = 2000L;
    private static final long MAX_BYTES = 24L * 1024 * 1024;
    private static final String CACHE_PREFIX = "segvehimg-";

    private static long checkedAt = 0L;
    private static boolean enabled = false;
    private static long rev = -1L;
    private static String ext = "png";

    private static File localFile = null;
    private static long localRev = -1L;
    private static final Object LOCK = new Object();

    private VehState() {
    }

    /** 车辆图 URL 的识别规则：沿用原模块的判断（device-config / category 目录下的图片资源）。 */
    static boolean isVehicleImage(String url) {
        if (url == null) {
            return false;
        }
        String s = url.toLowerCase(Locale.US);
        if (s.indexOf("device-config") < 0 && s.indexOf("/category/") < 0) {
            return false;
        }
        return s.indexOf(".png") >= 0
                || s.indexOf(".webp") >= 0
                || s.indexOf(".jpg") >= 0
                || s.indexOf(".jpeg") >= 0;
    }

    /** 替换是否开启（界面里选了图片且未恢复默认）。 */
    static boolean enabled(Context ctx) {
        synchronized (LOCK) {
            refresh(ctx);
            return enabled;
        }
    }

    /** 取本地副本；必要时从模块界面把图片拉过来。失败返回 null（此时保持宿主原图）。 */
    static File ensureLocal(Context ctx) {
        synchronized (LOCK) {
            return ensureLocalLocked(ctx);
        }
    }

    private static File ensureLocalLocked(Context ctx) {
        if (!refresh(ctx)) {
            return null;
        }
        if (localFile != null && localRev == rev && localFile.exists() && localFile.length() > 0L) {
            return localFile;
        }
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) {
            dir = ctx.getFilesDir();
        }
        if (dir == null) {
            return null;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            Logs.i("无法创建图片副本目录：" + dir.getAbsolutePath());
            return null;
        }
        File dst = new File(dir, CACHE_PREFIX + rev + "." + ext);
        File tmp = new File(dir, dst.getName() + ".part");
        try (InputStream in = ctx.getContentResolver()
                .openInputStream(Uri.parse(VehImageStore.BASE_URI + "image/" + rev));
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) {
                return null;
            }
            copyLimited(in, out);
        } catch (Throwable t) {
            Logs.i("车辆图取图失败: " + t);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        if (tmp.length() <= 0L) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        //noinspection ResultOfMethodCallIgnored
        dst.delete();
        if (!tmp.renameTo(dst)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        cleanup(dir, dst.getName());
        localFile = dst;
        localRev = rev;
        Logs.i("车辆图副本就绪：" + dst.getAbsolutePath() + "（" + dst.length() + "B, rev=" + rev + "）");
        return dst;
    }

    private static boolean refresh(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - checkedAt < TTL_MS) {
            return enabled;
        }
        checkedAt = now;
        Bundle b = null;
        try {
            b = ctx.getContentResolver().call(
                    Uri.parse(VehImageStore.BASE_URI + "state"), VehImageStore.METHOD_STATE, null, null);
        } catch (Throwable t) {
            Logs.diag("state(读取模块状态失败) " + t);
        }
        boolean wasEnabled = enabled;
        long wasRev = rev;
        enabled = b != null && b.getBoolean(VehImageStore.K_ENABLED, false);
        rev = b == null ? -1L : b.getLong(VehImageStore.K_REV, -1L);
        String e = b == null ? null : b.getString(VehImageStore.K_EXT);
        if (e != null && e.length() > 0) {
            ext = e;
        }
        if (wasEnabled != enabled || wasRev != rev) {
            localFile = null;
            localRev = -1L;
            Logs.i("车辆图替换状态：开关=" + (enabled ? "开" : "关") + "  rev=" + rev + "  格式=" + ext);
        }
        return enabled;
    }

    private static void copyLimited(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[64 * 1024];
        long total = 0L;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_BYTES) {
                throw new IllegalStateException("图片过大: >" + MAX_BYTES + "B");
            }
            out.write(buf, 0, n);
        }
        out.flush();
    }

    private static void cleanup(File dir, String keep) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(CACHE_PREFIX) && !name.equals(keep)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }
}

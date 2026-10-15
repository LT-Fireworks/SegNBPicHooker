package com.LT.SegNBPicHooker.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.LT.SegNBPicHooker.data.VehImageStore;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 把模块界面里选好的车辆图交给被 hook 的九号出行进程。
 *
 * <p>九号出行与模块是两个不同的 App（不同 UID、各自独立的数据目录），
 * 私有目录无法互相读取，所以这里用一个只读、无写入能力的 ContentProvider 做单向下发：
 * <ul>
 *   <li>{@code call("state")} → 返回 {enabled, rev, ext, name, w, h}</li>
 *   <li>{@code openFile(".../image/<rev>")} → 返回 PNG/JPG 字节流</li>
 * </ul>
 * 调用方只放行本模块自身与 {@code cn.ninebot.*} 进程。
 */
public class VehImageProvider extends ContentProvider {

    private static final String NINEBOT_PREFIX = "cn.ninebot";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        Bundle out = new Bundle();
        if (!callerAllowed()) {
            out.putBoolean(VehImageStore.K_ENABLED, false);
            return out;
        }
        Context c = getContext();
        if (c == null) {
            out.putBoolean(VehImageStore.K_ENABLED, false);
            return out;
        }
        out.putBoolean(VehImageStore.K_ENABLED, VehImageStore.active(c));
        out.putLong(VehImageStore.K_REV, VehImageStore.rev(c));
        out.putString(VehImageStore.K_EXT, VehImageStore.ext(c));
        out.putString(VehImageStore.K_NAME, VehImageStore.srcName(c));
        out.putInt(VehImageStore.K_W, VehImageStore.srcWidth(c));
        out.putInt(VehImageStore.K_H, VehImageStore.srcHeight(c));
        return out;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (!callerAllowed()) {
            throw new FileNotFoundException("caller not allowed");
        }
        Context c = getContext();
        File f = c == null ? null : VehImageStore.imageFile(c);
        if (f == null || !f.exists() || f.length() <= 0L) {
            throw new FileNotFoundException("no vehicle image");
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        Context c = getContext();
        return VehImageStore.mimeOf(c == null ? "png" : VehImageStore.ext(c));
    }

    /** 只读：不接受任何写入操作。 */
    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }

    private boolean callerAllowed() {
        int uid = Binder.getCallingUid();
        Context c = getContext();
        if (c == null) {
            return false;
        }
        if (uid == android.os.Process.myUid()) {
            return true;
        }
        PackageManager pm = c.getPackageManager();
        String[] pkgs = pm == null ? null : pm.getPackagesForUid(uid);
        if (pkgs != null) {
            for (String p : pkgs) {
                if (p != null && p.startsWith(NINEBOT_PREFIX)) {
                    return true;
                }
            }
        }
        return false;
    }
}

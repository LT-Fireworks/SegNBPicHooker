package com.LT.SegNBPicHooker.hook;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

/** 日志（输出到 LSPosed 日志面板），带图片诊断去重。 */
final class Logs {

    private static final String TAG = "SegNBPicHooker: ";
    private static final int DIAG_MAX = 200;

    private static final Set<String> DIAG = Collections.synchronizedSet(new LinkedHashSet<String>());
    private static final Set<String> INSTALLED = Collections.synchronizedSet(new LinkedHashSet<String>());

    private Logs() {
    }

    static void i(String msg) {
        try {
            XposedBridge.log(TAG + msg);
        } catch (Throwable ignored) {
            // 日志失败不能影响宿主
        }
    }

    /** 图片诊断：同样的内容只记一次，且总量封顶，避免刷爆日志。 */
    static void diag(String line) {
        if (DIAG.size() >= DIAG_MAX) {
            return;
        }
        if (DIAG.add(line)) {
            i("img " + line);
        }
    }

    static void hookOk(String what) {
        INSTALLED.add("OK   " + what);
        i("hook 成功 " + what);
    }

    static void hookFail(String what, Throwable t) {
        INSTALLED.add("FAIL " + what + " -> " + t);
        i("hook 失败 " + what + " -> " + t);
    }

    static void summary() {
        int ok = 0;
        int fail = 0;
        synchronized (INSTALLED) {
            for (String s : INSTALLED) {
                if (s.startsWith("OK")) {
                    ok++;
                } else {
                    fail++;
                }
            }
        }
        i("hook 安装汇总：成功 " + ok + " 个，失败 " + fail + " 个"
                + (fail == 0 ? "" : "（失败明细见上面的 hook 失败 行）"));
    }

    static String trunc(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}

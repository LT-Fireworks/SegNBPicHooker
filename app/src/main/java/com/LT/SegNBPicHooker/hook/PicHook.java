package com.LT.SegNBPicHooker.hook;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 模块入口：把九号出行 App 首页的车辆图替换成用户在模块界面里选择的图片。
 *
 * <p>只做一件事——在宿主图片加载链路的入口（Glide 的 {@code RequestBuilder#load}）
 * 把车辆图 URL 换成模块提供的本地副本。除此之外挂的都是只读诊断日志，
 * 用来在宿主改版、hook 点失效时能一眼看出问题。
 */
public class PicHook implements IXposedHookLoadPackage {

    /** 目标 App 包名（九号出行）。 */
    private static final String PKG = "cn.ninebot.ninebot";

    private static volatile Context appCtx;
    private static volatile String curActivity = "-";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (lp == null || !PKG.equals(lp.packageName)) {
            return;
        }
        Logs.i("注入成功 process=" + lp.processName);
        hookAppContext(lp);
        hookLifecycle(lp);
        hookGlide(lp);
        hookImageDiag(lp);
        Logs.summary();
    }

    // ------------------------------------------------------------------ 上下文

    private void hookAppContext(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate",
                    Application.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object a = param.args != null && param.args.length > 0 ? param.args[0] : null;
                                if (a instanceof Context) {
                                    appCtx = (Context) a;
                                }
                                Logs.i("应用 onCreate，模块开始待命");
                            } catch (Throwable t) {
                                Logs.i("callApplicationOnCreate 处理失败: " + t);
                            }
                        }
                    });
            Logs.hookOk("Instrumentation#callApplicationOnCreate");
        } catch (Throwable t) {
            Logs.hookFail("Instrumentation#callApplicationOnCreate", t);
        }
    }

    private void hookLifecycle(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object o = param.thisObject;
                        if (o != null) {
                            curActivity = o.getClass().getSimpleName();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            Logs.hookOk("Activity#onResume");
        } catch (Throwable t) {
            Logs.hookFail("Activity#onResume", t);
        }
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (appCtx == null && param.thisObject instanceof Context) {
                            appCtx = (Context) param.thisObject;
                            Logs.i("Application#onCreate 兜住上下文");
                        }
                        warmUp();
                    } catch (Throwable ignored) {
                    }
                }
            });
            Logs.hookOk("Application#onCreate");
        } catch (Throwable t) {
            Logs.hookFail("Application#onCreate", t);
        }
    }

    /** 宿主上下文：优先 onCreate 抓到的，兜底用 Xposed 提供的当前 Application。 */
    static Context appContext() {
        Context c = appCtx;
        if (c != null) {
            return c;
        }
        try {
            Object o = XposedHelpers.callStaticMethod(
                    Class.forName("android.app.AndroidAppHelper"), "currentApplication");
            if (o instanceof Context) {
                c = (Context) o;
                appCtx = c;
            }
        } catch (Throwable ignored) {
        }
        return c;
    }

    /**
     * 后台预热：提前把模块状态和车辆图副本准备好，避免首页第一次加载车辆图时
     * 在主线程里现跨进程取图（那会带来一次明显卡顿）。
     */
    private static void warmUp() {
        try {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Context c = appContext();
                        if (c != null && VehState.enabled(c)) {
                            VehState.ensureLocal(c);
                        }
                    } catch (Throwable t) {
                        Logs.i("预热失败: " + t);
                    }
                }
            }, "segvehimg-warmup");
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ 车辆图替换

    private void hookGlide(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> builder = XposedHelpers.findClass("com.bumptech.glide.RequestBuilder", lp.classLoader);
            XposedBridge.hookAllMethods(builder, "load", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    replaceVehicleImage(param);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    img("glide", arg0(param), param.thisObject);
                }
            });
            Logs.hookOk("Glide RequestBuilder#load（车辆图替换）");
        } catch (Throwable t) {
            Logs.hookFail("Glide RequestBuilder#load", t);
        }
    }

    private static void replaceVehicleImage(XC_MethodHook.MethodHookParam param) {
        try {
            Object[] args = param.args;
            if (args == null || args.length != 1 || !(args[0] instanceof String)) {
                return;
            }
            String url = (String) args[0];
            if (!VehState.isVehicleImage(url)) {
                return;
            }
            Context ctx = appContext();
            if (ctx == null) {
                Logs.diag("glide(车辆图·上下文未就绪) " + Logs.trunc(url));
                return;
            }
            if (!VehState.enabled(ctx)) {
                Logs.diag("glide(车辆图·替换未开启) " + Logs.trunc(url));
                return;
            }
            File local = VehState.ensureLocal(ctx);
            if (local == null) {
                Logs.diag("glide(车辆图·副本不可用) " + Logs.trunc(url));
                return;
            }
            args[0] = "file://" + local.getAbsolutePath();
            Logs.diag("glide(车辆图·已替换) " + Logs.trunc(url) + "  ->  file://" + local.getAbsolutePath());
        } catch (Throwable t) {
            Logs.i("车辆图替换失败: " + t);
        }
    }

    // ------------------------------------------------------------ 诊断日志

    private void hookImageDiag(XC_LoadPackage.LoadPackageParam lp) {
        String[] drawee = {"com.facebook.drawee.view.SimpleDraweeView", "com.facebook.drawee.view.DraweeView"};
        for (String name : drawee) {
            try {
                Class<?> c = XposedHelpers.findClass(name, lp.classLoader);
                XposedBridge.hookAllMethods(c, "setImageURI", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        img("drawee", arg0(param), param.thisObject);
                    }
                });
                Logs.hookOk(name + "#setImageURI");
            } catch (Throwable t) {
                Logs.hookFail(name + "#setImageURI", t);
            }
        }
        try {
            Class<?> c = XposedHelpers.findClass("com.facebook.react.views.image.ReactImageView", lp.classLoader);
            XposedBridge.hookAllMethods(c, "setSource", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    img("rn", arg0(param), param.thisObject);
                }
            });
            Logs.hookOk("ReactImageView#setSource");
        } catch (Throwable t) {
            Logs.hookFail("ReactImageView#setSource", t);
        }
        try {
            XposedBridge.hookAllMethods(ImageView.class, "setImageResource", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object a = arg0(param);
                    int id = a instanceof Integer ? (Integer) a : 0;
                    img("setImageResource", resName(id), param.thisObject);
                }
            });
            XposedBridge.hookAllMethods(ImageView.class, "setImageBitmap", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    img("setImageBitmap", arg0(param), param.thisObject);
                }
            });
            XposedBridge.hookAllMethods(ImageView.class, "setImageDrawable", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    img("setImageDrawable", arg0(param), param.thisObject);
                }
            });
            Logs.hookOk("ImageView#setImageResource/setImageBitmap/setImageDrawable");
        } catch (Throwable t) {
            Logs.hookFail("ImageView#setImage*", t);
        }
    }

    private static Object arg0(XC_MethodHook.MethodHookParam param) {
        Object[] args = param == null ? null : param.args;
        return args != null && args.length > 0 ? args[0] : null;
    }

    private static void img(String tag, Object value, Object view) {
        try {
            Logs.diag(tag + " act=" + curActivity + " view=" + viewName(view) + " val=" + describe(value));
        } catch (Throwable ignored) {
        }
    }

    private static String viewName(Object view) {
        if (!(view instanceof View)) {
            return "-";
        }
        String name = view.getClass().getSimpleName();
        if (name == null || name.length() == 0) {
            name = view.getClass().getName();
        }
        int id = ((View) view).getId();
        if (id == 0) {
            return name + "#noid";
        }
        try {
            return name + "#" + ((View) view).getResources().getResourceEntryName(id);
        } catch (Throwable t) {
            return name + "#0x" + Integer.toHexString(id);
        }
    }

    private static String describe(Object v) {
        if (v == null) {
            return "null";
        }
        try {
            if (v instanceof String) {
                return Logs.trunc((String) v);
            }
            if (v instanceof Uri) {
                return "uri:" + Logs.trunc(String.valueOf(v));
            }
            if (v instanceof byte[]) {
                return "bytes[" + ((byte[]) v).length + "]";
            }
            if (v instanceof Bitmap) {
                return "bitmap:" + ((Bitmap) v).getWidth() + "x" + ((Bitmap) v).getHeight();
            }
            if (v instanceof Drawable) {
                return "drawable:" + v.getClass().getSimpleName();
            }
            if (v instanceof Integer) {
                return "res:0x" + Integer.toHexString((Integer) v);
            }
            return v.getClass().getSimpleName() + ":" + Logs.trunc(String.valueOf(v));
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String resName(int id) {
        if (id == 0) {
            return "0";
        }
        Context c = appCtx;
        if (c == null) {
            return "res:0x" + Integer.toHexString(id);
        }
        try {
            return c.getResources().getResourcePackageName(id)
                    + ":drawable/" + c.getResources().getResourceEntryName(id)
                    + "(0x" + Integer.toHexString(id) + ")";
        } catch (Throwable t) {
            return "res:0x" + Integer.toHexString(id);
        }
    }
}

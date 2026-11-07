package com.sidekey.lsp;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 单击 / 双击 / 长按 判定，全部在自己的线程里跑，不阻塞输入线程 */
final class Engine {
    private static Engine sInstance;

    static synchronized Engine get() {
        if (sInstance == null) sInstance = new Engine();
        return sInstance;
    }

    private final XSharedPreferences prefs = new XSharedPreferences(Cfg.PKG, Cfg.PREFS);
    private final Handler h;

    private volatile int scanCode = Cfg.DEF_SCANCODE;
    private volatile boolean swallow = true;
    private volatile long lastCheck;

    private int clicks;
    private boolean longFired;
    private boolean downNow;

    private final Runnable singleR = () -> { clicks = 0; fire("single"); };
    private final Runnable longR = () -> { longFired = true; fire("long"); };

    private Engine() {
        HandlerThread t = new HandlerThread("SideKey");
        t.start();
        h = new Handler(t.getLooper());
        reload(true);
    }

    /** 输入线程上调用：只做一次 stat（节流 1.5s）和整数比较 */
    boolean matches(KeyEvent ev) {
        long now = SystemClock.uptimeMillis();
        if (now - lastCheck > 1500) { lastCheck = now; reload(false); }
        return ev.getScanCode() == scanCode;
    }

    boolean swallow() { return swallow; }

    private void reload(boolean force) {
        try {
            if (force || prefs.hasFileChanged()) {
                prefs.reload();
                scanCode = prefs.getInt(Cfg.SCANCODE, Cfg.DEF_SCANCODE);
                swallow = prefs.getBoolean(Cfg.SWALLOW, true);
            }
        } catch (Throwable t) {
            XposedBridge.log("[SideKey] prefs: " + t);
        }
    }

    void onKey(KeyEvent ev) {
        int action = ev.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            if (ev.getRepeatCount() > 0) return;
            h.post(this::down);
        } else if (action == KeyEvent.ACTION_UP) {
            h.post(this::up);
        }
    }

    private boolean has(String g) {
        String a = prefs.getString(Cfg.ACT_PREFIX + g, "");
        return a != null && !a.isEmpty() && !"none".equals(a);
    }

    private void down() {
        if (downNow) return;
        downNow = true;
        h.removeCallbacks(longR);
        longFired = false;
        if (h.hasCallbacks(singleR)) { h.removeCallbacks(singleR); clicks = 2; }
        else clicks = 1;
        if (has("long")) h.postDelayed(longR, prefs.getInt(Cfg.LONG_MS, Cfg.DEF_LONG_MS));
    }

    private void up() {
        downNow = false;
        h.removeCallbacks(longR);
        if (longFired) { clicks = 0; return; }
        if (clicks == 2) { clicks = 0; fire("double"); }
        else if (has("double")) h.postDelayed(singleR, prefs.getInt(Cfg.DOUBLE_MS, Cfg.DEF_DOUBLE_MS));
        else { clicks = 0; fire("single"); }
    }

    private void fire(String gesture) {
        try {
            Context c = ctx();
            if (c == null) return;
            String spec = prefs.getString(Cfg.ACT_PREFIX + gesture, "");
            if (spec == null || spec.isEmpty() || "none".equals(spec)) return;
            if (prefs.getBoolean(Cfg.HAPTIC, true)) buzz(c);
            Actions.run(c, spec, prefs.getString(Cfg.MEDIA_TARGET, ""));
        } catch (Throwable t) {
            XposedBridge.log("[SideKey] fire " + gesture + ": " + t);
        }
    }

    private static void buzz(Context c) {
        try {
            Vibrator v = c.getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        } catch (Throwable ignored) {}
    }

    private Context context;

    private Context ctx() {
        if (context != null) return context;
        try {
            Object at = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread");
            context = (Context) XposedHelpers.callMethod(at, "getSystemContext");
        } catch (Throwable t) {
            XposedBridge.log("[SideKey] context: " + t);
        }
        return context;
    }
}

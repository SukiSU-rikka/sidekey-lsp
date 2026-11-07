package com.sidekey.lsp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;

/** 单击 / 双击 / 长按 判定，在自己的线程里跑，不阻塞输入线程 */
final class Engine {
    private static volatile Engine sInstance;

    static Engine get() {
        Engine e = sInstance;
        if (e != null) return e;
        synchronized (Engine.class) {
            if (sInstance == null) {
                SideKeyModule m = SideKeyModule.sInstance;
                if (m == null) return null;
                try {
                    sInstance = new Engine(m.getRemotePreferences(Cfg.PREFS));
                } catch (Throwable t) {
                    SideKeyModule.logE("engine init", t);
                    return null;
                }
            }
            return sInstance;
        }
    }

    private final SharedPreferences prefs;
    private final Handler h;
    private volatile int scanCode;
    private volatile boolean swallow;
    // 远程配置的变更监听，必须强引用持有
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (sp, key) -> refresh();

    private int clicks;
    private boolean longFired;
    private boolean downNow;
    private Context context;

    private final Runnable singleR = () -> { clicks = 0; fire("single"); };
    private final Runnable longR = () -> { longFired = true; fire("long"); };

    private Engine(SharedPreferences prefs) {
        this.prefs = prefs;
        HandlerThread t = new HandlerThread("SideKey");
        t.start();
        h = new Handler(t.getLooper());
        refresh();
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    private void refresh() {
        scanCode = prefs.getInt(Cfg.SCANCODE, Cfg.DEF_SCANCODE);
        swallow = prefs.getBoolean(Cfg.SWALLOW, true);
    }

    boolean matches(KeyEvent ev) { return ev.getScanCode() == scanCode; }

    boolean swallow() { return swallow; }

    void onKey(KeyEvent ev) {
        int a = ev.getAction();
        if (a == KeyEvent.ACTION_DOWN) {
            if (ev.getRepeatCount() > 0) return;
            h.post(this::down);
        } else if (a == KeyEvent.ACTION_UP) {
            h.post(this::up);
        }
    }

    private String act(String g) { return prefs.getString(Cfg.ACT_PREFIX + g, ""); }

    private boolean has(String g) {
        String a = act(g);
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
            if (!has(gesture)) return;
            Context c = ctx();
            if (c == null) return;
            if (prefs.getBoolean(Cfg.HAPTIC, true)) buzz(c);
            Actions.run(c, act(gesture), prefs.getString(Cfg.MEDIA_TARGET, ""));
        } catch (Throwable t) {
            SideKeyModule.logE("fire " + gesture, t);
        }
    }

    private static void buzz(Context c) {
        try {
            Vibrator v = c.getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        } catch (Throwable ignored) {}
    }

    private Context ctx() {
        if (context != null) return context;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = Refl.call(at, "currentActivityThread");
            context = (Context) Refl.call(thread, "getSystemContext");
        } catch (Throwable t) {
            SideKeyModule.logE("context", t);
        }
        return context;
    }
}

package com.sidekey.lsp;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.android.material.color.DynamicColors;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** 模块激活后 LSPosed 会把服务推过来，配置通过它的 Remote Preferences 实时同步到 system_server */
public class App extends Application implements XposedServiceHelper.OnServiceListener {

    static volatile SharedPreferences prefs;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        try {
            prefs = service.getRemotePreferences(Cfg.PREFS);
        } catch (Throwable t) {
            prefs = null;
        }
        notifyChanged();
    }

    @Override
    public void onServiceDied(XposedService service) {
        prefs = null;
        notifyChanged();
    }

    static void addListener(Runnable r) { listeners.add(r); }

    static void removeListener(Runnable r) { listeners.remove(r); }

    private static void notifyChanged() {
        main.post(() -> { for (Runnable r : listeners) r.run(); });
    }
}

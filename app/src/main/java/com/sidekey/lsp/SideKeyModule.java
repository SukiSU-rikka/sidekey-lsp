package com.sidekey.lsp;

import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 入口（libxposed API 102）。只作用于 system_server：
 *  - InputManagerService.interceptKeyBeforeQueueing：原始事件第一次进 Java，在这里识别手势
 *  - InputManagerService.filterInputEvent：ColorOS 的 InputFilter 会截走再注入，在这里直接丢掉，系统原功能收不到
 * 全部用 PROTECTIVE 模式：hooker 抛异常会被框架吞掉并按原方法执行，不会让 system_server 崩。
 */
public class SideKeyModule extends XposedModule {
    static final String TAG = "SideKey";
    private static final int POLICY_FLAG_INJECTED = 0x01000000;

    static volatile SideKeyModule sInstance;

    public SideKeyModule() {
        super();
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        sInstance = this;
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        sInstance = this;
        try {
            Class<?> ims = param.getClassLoader().loadClass("com.android.server.input.InputManagerService");
            int n = 0;
            for (Method m : ims.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("interceptKeyBeforeQueueing") && p.length >= 2
                        && p[0] == KeyEvent.class && p[1] == int.class) {
                    hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        KeyEvent ev = (KeyEvent) chain.getArg(0);
                        Engine e = Engine.get();
                        if (e != null && e.matches(ev)) {
                            int flags = (Integer) chain.getArg(1);
                            if ((flags & POLICY_FLAG_INJECTED) == 0) e.onKey(ev);
                            if (e.swallow()) return 0;
                        }
                        return chain.proceed();
                    });
                    n++;
                } else if (m.getName().equals("filterInputEvent") && p.length == 2
                        && p[0] == InputEvent.class && p[1] == int.class) {
                    hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object ev = chain.getArg(0);
                        if (ev instanceof KeyEvent) {
                            Engine e = Engine.get();
                            if (e != null && e.matches((KeyEvent) ev) && e.swallow()) return false;
                        }
                        return chain.proceed();
                    });
                    n++;
                }
            }
            log(Log.INFO, TAG, "hooked " + n + " methods");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "init failed", t);
        }
    }

    static void logE(String msg, Throwable t) {
        SideKeyModule m = sInstance;
        if (m != null) m.log(Log.ERROR, TAG, msg, t);
    }
}

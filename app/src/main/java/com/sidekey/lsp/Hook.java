package com.sidekey.lsp;

import android.view.InputEvent;
import android.view.KeyEvent;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 挂在 system_server 输入链最前面：
 *  - InputManagerService.interceptKeyBeforeQueueing：原始事件第一次进 Java 的地方，在这里识别手势
 *  - InputManagerService.filterInputEvent：ColorOS 的 InputFilter 会截走再重新注入（policyFlags 带 FILTERED|INJECTED），
 *    在这里直接丢掉，系统自带的快捷键功能就收不到了
 * 所有回调都包 try/catch：system_server 里抛异常 = 软重启。
 */
public class Hook implements IXposedHookLoadPackage {
    private static final int POLICY_FLAG_INJECTED = 0x01000000;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"android".equals(lp.packageName)) return;
        try {
            Class<?> ims = XposedHelpers.findClass("com.android.server.input.InputManagerService", lp.classLoader);
            int hooked = 0;
            for (Method m : ims.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("interceptKeyBeforeQueueing") && p.length >= 2
                        && p[0] == KeyEvent.class && p[1] == int.class) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                KeyEvent ev = (KeyEvent) param.args[0];
                                Engine e = Engine.get();
                                if (!e.matches(ev)) return;
                                int flags = (int) param.args[1];
                                if ((flags & POLICY_FLAG_INJECTED) == 0) e.onKey(ev);
                                if (e.swallow()) param.setResult(0);
                            } catch (Throwable t) {
                                XposedBridge.log("[SideKey] intercept: " + t);
                            }
                        }
                    });
                    hooked++;
                } else if (m.getName().equals("filterInputEvent") && p.length == 2
                        && p[0] == InputEvent.class && p[1] == int.class) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!(param.args[0] instanceof KeyEvent)) return;
                                Engine e = Engine.get();
                                if (e.matches((KeyEvent) param.args[0]) && e.swallow()) {
                                    param.setResult(false); // false = 已被过滤器消费，不再分发
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[SideKey] filter: " + t);
                            }
                        }
                    });
                    hooked++;
                }
            }
            XposedBridge.log("[SideKey] hooked " + hooked + " methods");
        } catch (Throwable t) {
            XposedBridge.log("[SideKey] init failed: " + t);
        }
    }
}

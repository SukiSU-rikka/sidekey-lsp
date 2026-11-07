package com.sidekey.lsp;

import android.app.BroadcastOptions;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.util.List;


/** 在 system_server 里直接调系统 API 执行动作，不起 shell */
final class Actions {
    private Actions() {}

    static void run(Context c, String spec, String mediaTarget) {
        long id = Binder.clearCallingIdentity();
        try {
            if (spec.startsWith("app:")) launchApp(c, spec.substring(4));
            else if (spec.startsWith("url:")) openUrl(c, spec.substring(4));
            else if (spec.startsWith("media:")) media(c, spec.substring(6), mediaTarget);
            else if (spec.equals("vol:up")) volume(c, AudioManager.ADJUST_RAISE);
            else if (spec.equals("vol:down")) volume(c, AudioManager.ADJUST_LOWER);
            else if (spec.equals("dnd")) toggleDnd(c);
            else if (spec.equals("ringer")) toggleRinger(c);
            else if (spec.equals("wifi")) toggleWifi(c);
            else if (spec.equals("bt")) toggleBt();
            else if (spec.equals("screenshot")) injectKey(c, KeyEvent.KEYCODE_SYSRQ);
            else if (spec.equals("lock")) lock(c);
            else if (spec.startsWith("key:")) injectKey(c, Integer.parseInt(spec.substring(4).trim()));
            else if (spec.startsWith("sh:")) shell(c, spec.substring(3));
        } catch (Throwable t) {
            SideKeyModule.logE("action " + spec, t);
        } finally {
            Binder.restoreCallingIdentity(id);
        }
    }

    // ---------- 应用 / 链接 ----------

    private static void launchApp(Context c, String pkg) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(c, i);
    }

    private static void openUrl(Context c, String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(c, i);
    }

    private static void startActivity(Context c, Intent i) {
        try {
            Refl.call(c, "startActivityAsUser", i, currentUser());
        } catch (Throwable t) {
            c.startActivity(i);
        }
    }

    private static UserHandle currentUser() throws Exception {
        return (UserHandle) Refl.getStatic(UserHandle.class, "CURRENT");
    }

    // ---------- 媒体 ----------

    private static void media(Context c, String cmd, String target) {
        if (target != null && !target.isEmpty()) {
            MediaSessionManager msm = c.getSystemService(MediaSessionManager.class);
            List<MediaController> list = msm.getActiveSessions(null);
            for (MediaController mc : list) {
                if (target.equals(mc.getPackageName())) {
                    transport(mc, cmd);
                    return;
                }
            }
            // 目标播放器没在跑：给它的媒体按键接收器发一个真实的按键，后台拉起播放
            if (!cmd.equals("pause")) mediaButtonTo(c, target, KeyEvent.KEYCODE_MEDIA_PLAY);
            return;
        }
        // 没指定：发给系统当前的媒体按键会话（和耳机线控一样）
        int code;
        switch (cmd) {
            case "next": code = KeyEvent.KEYCODE_MEDIA_NEXT; break;
            case "previous": code = KeyEvent.KEYCODE_MEDIA_PREVIOUS; break;
            case "play": code = KeyEvent.KEYCODE_MEDIA_PLAY; break;
            case "pause": code = KeyEvent.KEYCODE_MEDIA_PAUSE; break;
            default: code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        }
        AudioManager am = c.getSystemService(AudioManager.class);
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    private static void transport(MediaController mc, String cmd) {
        MediaController.TransportControls tc = mc.getTransportControls();
        switch (cmd) {
            case "next": tc.skipToNext(); break;
            case "previous": tc.skipToPrevious(); break;
            case "play": tc.play(); break;
            case "pause": tc.pause(); break;
            default:
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) tc.pause();
                else tc.play();
        }
    }

    private static void mediaButtonTo(Context c, String pkg, int code) {
        Intent q = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg);
        List<ResolveInfo> rs = c.getPackageManager().queryBroadcastReceivers(q, 0);
        if (rs == null || rs.isEmpty()) {
            return;
        }
        ResolveInfo r = rs.get(0);
        ComponentName cn = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
        Bundle opts = allowlistOptions();
        long t = SystemClock.uptimeMillis();
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(cn)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(t, t, action, code, 0));
            if (opts != null && Build.VERSION.SDK_INT >= 34) c.sendBroadcast(i, null, opts);
            else c.sendBroadcast(i);
        }
    }

    /** 和系统 MediaSessionService 一样，给接收方临时的前台服务豁免，否则后台起不了播放服务 */
    private static Bundle allowlistOptions() {
        try {
            BroadcastOptions bo = BroadcastOptions.makeBasic();
            Class<?> pem = Class.forName("android.os.PowerExemptionManager");
            int type = (Integer) Refl.getStatic(pem, "TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED");
            int reason;
            try { reason = (Integer) Refl.getStatic(pem, "REASON_MEDIA_BUTTON"); }
            catch (Throwable e) { reason = 0; }
            Refl.call(bo, "setTemporaryAppAllowlist", 10_000L, type, reason, "sidekey");
            return bo.toBundle();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void volume(Context c, int dir) {
        c.getSystemService(AudioManager.class)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
    }

    // ---------- 系统开关 ----------

    private static void toggleDnd(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        int f = nm.getCurrentInterruptionFilter();
        nm.setInterruptionFilter(f == NotificationManager.INTERRUPTION_FILTER_ALL
                ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                : NotificationManager.INTERRUPTION_FILTER_ALL);
    }

    private static void toggleRinger(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        am.setRingerMode(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
                ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL);
    }

    @SuppressWarnings("deprecation")
    private static void toggleWifi(Context c) {
        WifiManager wm = c.getSystemService(WifiManager.class);
        wm.setWifiEnabled(!wm.isWifiEnabled());
    }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    private static void toggleBt() {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) return;
        if (a.isEnabled()) a.disable(); else a.enable();
    }

    private static void lock(Context c) throws Exception {
        PowerManager pm = c.getSystemService(PowerManager.class);
        Refl.call(pm, "goToSleep", SystemClock.uptimeMillis());
    }

    private static void injectKey(Context c, int code) throws Exception {
        Object im = c.getSystemService(Context.INPUT_SERVICE);
        long t = SystemClock.uptimeMillis();
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent ev = new KeyEvent(t, t, action, code, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
            Refl.call(im, "injectInputEvent", ev, 0); // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
        }
    }

    // ---------- Shell：交给模块 App 用 su 执行 ----------

    private static void shell(Context c, String cmd) {
        Intent i = new Intent("com.sidekey.lsp.RUN_SHELL")
                .setComponent(new ComponentName(Cfg.PKG, Cfg.PKG + ".CmdReceiver"))
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra("cmd", cmd);
        try {
            c.sendBroadcastAsUser(i, currentUser());
        } catch (Throwable t) {
            c.sendBroadcast(i);
        }
    }

}

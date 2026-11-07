package com.sidekey.lsp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 只有系统能发（manifest 里要求 DEVICE_POWER 权限），以 root 执行 Shell 动作 */
public class CmdReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String cmd = intent.getStringExtra("cmd");
        if (cmd == null || cmd.isEmpty()) return;
        PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
                p.getInputStream().close();
                p.waitFor();
            } catch (Throwable ignored) {
            } finally {
                pr.finish();
            }
        }).start();
    }
}

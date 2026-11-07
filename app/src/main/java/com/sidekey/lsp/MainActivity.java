package com.sidekey.lsp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    /** {显示名, 编码前缀/固定值, 需要参数的类型: 0=无 1=应用 2=文本} */
    private static final String[][] TYPES = {
            {"不做事", "none", "0"},
            {"打开应用", "app:", "1"},
            {"打开链接 / URL Scheme", "url:", "2"},
            {"媒体：播放 / 暂停", "media:play-pause", "0"},
            {"媒体：下一首", "media:next", "0"},
            {"媒体：上一首", "media:previous", "0"},
            {"媒体：播放", "media:play", "0"},
            {"媒体：暂停", "media:pause", "0"},
            {"媒体音量 +", "vol:up", "0"},
            {"媒体音量 −", "vol:down", "0"},
            {"切换勿扰", "dnd", "0"},
            {"切换 响铃 / 振动", "ringer", "0"},
            {"切换 Wi-Fi", "wifi", "0"},
            {"切换 蓝牙", "bt", "0"},
            {"截屏", "screenshot", "0"},
            {"锁屏", "lock", "0"},
            {"模拟按键 (keycode)", "key:", "2"},
            {"Shell 命令 (root)", "sh:", "2"},
    };
    private static final String[][] GESTURES = {{"single", "单击"}, {"double", "双击"}, {"long", "长按"}};

    private SharedPreferences prefs;
    private boolean moduleActive;
    private final Spinner[] typeSp = new Spinner[3];
    private final EditText[] argEt = new EditText[3];
    private final Button[] pickBtn = new Button[3];
    private EditText scanEt, dblEt, longEt, targetEt;
    private Switch swallowSw, hapticSw;
    private volatile List<AppItem> apps;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            //noinspection deprecation
            prefs = getSharedPreferences(Cfg.PREFS, Context.MODE_WORLD_READABLE);
            moduleActive = true;
        } catch (SecurityException e) {
            prefs = getSharedPreferences(Cfg.PREFS, Context.MODE_PRIVATE);
            moduleActive = false;
        }
        setContentView(buildUi());
        load();
        new Thread(this::loadApps).start();
    }

    // ---------------- UI ----------------

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(32));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("快捷键增强");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView st = new TextView(this);
        st.setText(moduleActive
                ? "模块已在 LSPosed 中启用（作用域：系统框架）。改完点保存，按一下快捷键即生效。"
                : "⚠ 模块未启用：去 LSPosed 勾选本模块并勾选「系统框架」，然后重启手机。");
        st.setPadding(0, dp(6), 0, dp(10));
        root.addView(st);

        section(root, "按键");
        swallowSw = sw(root, "拦截系统原本的快捷键功能");
        hapticSw = sw(root, "识别到手势时轻振一下");
        scanEt = field(root, "扫描码（Ace 6 快捷键是 735）", InputType.TYPE_CLASS_NUMBER);

        for (int i = 0; i < 3; i++) gestureBlock(root, i);

        section(root, "媒体");
        targetEt = field(root, "指定播放器包名（留空 = 系统当前播放器）", InputType.TYPE_CLASS_TEXT);
        Button tp = new Button(this);
        tp.setText("选择播放器");
        tp.setOnClickListener(v -> pickApp(pkg -> targetEt.setText(pkg)));
        root.addView(tp);
        hint(root, "指定后，媒体动作会直接发给这个 App（比如波点 cn.wenyu.bodian），它没在运行时也会在后台被拉起播放，不弹界面。");

        section(root, "手感");
        dblEt = field(root, "双击间隔 (ms)", InputType.TYPE_CLASS_NUMBER);
        longEt = field(root, "长按时长 (ms)", InputType.TYPE_CLASS_NUMBER);
        hint(root, "设了双击时，单击要等「双击间隔」过完才能确认；没设双击则单击即时触发。");

        Button save = new Button(this);
        save.setText("保存");
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        root.addView(save, lp);
        return sv;
    }

    private void section(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(4));
        root.addView(t);
    }

    private void hint(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setAlpha(0.7f);
        t.setPadding(0, dp(4), 0, 0);
        root.addView(t);
    }

    private Switch sw(LinearLayout root, String text) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setPadding(0, dp(6), 0, dp(6));
        root.addView(s);
        return s;
    }

    private EditText field(LinearLayout root, String label, int type) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setPadding(0, dp(8), 0, 0);
        root.addView(l);
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setSingleLine(true);
        root.addView(e);
        return e;
    }

    private void gestureBlock(LinearLayout root, int idx) {
        section(root, GESTURES[idx][1]);
        Spinner sp = new Spinner(this);
        String[] names = new String[TYPES.length];
        for (int i = 0; i < TYPES.length; i++) names[i] = TYPES[i][0];
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        root.addView(sp);

        EditText et = new EditText(this);
        et.setSingleLine(true);
        root.addView(et);

        Button pick = new Button(this);
        pick.setText("选择应用");
        pick.setOnClickListener(v -> pickApp(pkg -> et.setText(pkg)));
        root.addView(pick);

        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { syncType(idx); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        typeSp[idx] = sp;
        argEt[idx] = et;
        pickBtn[idx] = pick;
    }

    private void syncType(int idx) {
        String[] t = TYPES[typeSp[idx].getSelectedItemPosition()];
        EditText et = argEt[idx];
        et.setVisibility(t[2].equals("0") ? View.GONE : View.VISIBLE);
        pickBtn[idx].setVisibility(t[2].equals("1") ? View.VISIBLE : View.GONE);
        switch (t[1]) {
            case "app:": et.setHint("包名，如 cn.wenyu.bodian"); break;
            case "url:": et.setHint("https://… 或 xxx://…"); break;
            case "key:": et.setHint("keycode，如 3 = 主屏幕"); break;
            case "sh:": et.setHint("以 root 执行的命令"); break;
        }
    }

    // ---------------- 读写配置 ----------------

    private void load() {
        swallowSw.setChecked(prefs.getBoolean(Cfg.SWALLOW, true));
        hapticSw.setChecked(prefs.getBoolean(Cfg.HAPTIC, true));
        scanEt.setText(String.valueOf(prefs.getInt(Cfg.SCANCODE, Cfg.DEF_SCANCODE)));
        dblEt.setText(String.valueOf(prefs.getInt(Cfg.DOUBLE_MS, Cfg.DEF_DOUBLE_MS)));
        longEt.setText(String.valueOf(prefs.getInt(Cfg.LONG_MS, Cfg.DEF_LONG_MS)));
        targetEt.setText(prefs.getString(Cfg.MEDIA_TARGET, ""));
        for (int i = 0; i < 3; i++) {
            String spec = prefs.getString(Cfg.ACT_PREFIX + GESTURES[i][0], "");
            int pos = 0;
            String arg = "";
            for (int k = 0; k < TYPES.length; k++) {
                String code = TYPES[k][1];
                if (code.endsWith(":") && spec.startsWith(code) && !code.startsWith("media") && !code.startsWith("vol")) {
                    pos = k; arg = spec.substring(code.length()); break;
                } else if (spec.equals(code)) {
                    pos = k; break;
                }
            }
            typeSp[i].setSelection(pos);
            argEt[i].setText(arg);
            syncType(i);
        }
    }

    private int num(EditText e, int def, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(e.getText().toString().trim()))); }
        catch (Exception ex) { return def; }
    }

    private void save() {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(Cfg.SWALLOW, swallowSw.isChecked());
        ed.putBoolean(Cfg.HAPTIC, hapticSw.isChecked());
        ed.putInt(Cfg.SCANCODE, num(scanEt, Cfg.DEF_SCANCODE, 1, 1023));
        ed.putInt(Cfg.DOUBLE_MS, num(dblEt, Cfg.DEF_DOUBLE_MS, 100, 800));
        ed.putInt(Cfg.LONG_MS, num(longEt, Cfg.DEF_LONG_MS, 250, 3000));
        ed.putString(Cfg.MEDIA_TARGET, targetEt.getText().toString().trim());
        for (int i = 0; i < 3; i++) {
            String[] t = TYPES[typeSp[i].getSelectedItemPosition()];
            String spec;
            if (t[2].equals("0")) spec = t[1].equals("none") ? "" : t[1];
            else {
                String a = argEt[i].getText().toString().trim();
                spec = a.isEmpty() ? "" : t[1] + a;
            }
            ed.putString(Cfg.ACT_PREFIX + GESTURES[i][0], spec);
        }
        ed.commit();
        Toast.makeText(this, moduleActive ? "已保存，立即生效" : "已保存，但模块未启用", Toast.LENGTH_SHORT).show();
    }

    // ---------------- 应用选择 ----------------

    private static final class AppItem {
        String pkg, label;
        Drawable icon;
    }

    private interface OnPick { void pick(String pkg); }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppItem> list = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
            AppItem a = new AppItem();
            a.pkg = r.activityInfo.packageName;
            a.label = String.valueOf(r.loadLabel(pm));
            a.icon = r.loadIcon(pm);
            boolean dup = false;
            for (AppItem x : list) if (x.pkg.equals(a.pkg)) { dup = true; break; }
            if (!dup) list.add(a);
        }
        java.text.Collator col = java.text.Collator.getInstance(java.util.Locale.CHINA);
        list.sort((x, y) -> col.compare(x.label, y.label));
        apps = list;
    }

    private void pickApp(OnPick cb) {
        if (apps == null) { Toast.makeText(this, "应用列表加载中…", Toast.LENGTH_SHORT).show(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        EditText search = new EditText(this);
        search.setHint("搜索");
        search.setSingleLine(true);
        box.addView(search);
        android.widget.ListView lv = new android.widget.ListView(this);
        box.addView(lv, new LinearLayout.LayoutParams(-1, dp(420)));

        final List<AppItem> shown = new ArrayList<>(apps);
        BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                LinearLayout row;
                if (v == null) {
                    row = new LinearLayout(MainActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(8), 0, dp(8));
                    ImageView iv = new ImageView(MainActivity.this);
                    row.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
                    TextView tv = new TextView(MainActivity.this);
                    tv.setPadding(dp(12), 0, 0, 0);
                    row.addView(tv);
                } else row = (LinearLayout) v;
                AppItem a = shown.get(i);
                ((ImageView) row.getChildAt(0)).setImageDrawable(a.icon);
                ((TextView) row.getChildAt(1)).setText(a.label + "\n" + a.pkg);
                return row;
            }
        };
        lv.setAdapter(ad);
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("选择应用").setView(box)
                .setNegativeButton("取消", null).create();
        lv.setOnItemClickListener((p, v, i, id) -> { cb.pick(shown.get(i).pkg); dlg.dismiss(); });
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String k = s.toString().trim().toLowerCase();
                shown.clear();
                for (AppItem a : apps)
                    if (k.isEmpty() || a.label.toLowerCase().contains(k) || a.pkg.toLowerCase().contains(k)) shown.add(a);
                ad.notifyDataSetChanged();
            }
        });
        dlg.show();
    }
}

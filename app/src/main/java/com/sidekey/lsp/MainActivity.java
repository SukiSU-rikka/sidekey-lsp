package com.sidekey.lsp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    /** 动作定义：编码、名字、图标、参数类型(0 无 / 1 应用 / 2 文本)、分组 */
    private static final class Act {
        final String code, name, group; final int icon, arg;
        Act(String group, String code, String name, int icon, int arg) {
            this.group = group; this.code = code; this.name = name; this.icon = icon; this.arg = arg;
        }
    }

    private static final Act[] ACTS = {
            new Act("常用", "none", "不做事", R.drawable.ic_block, 0),
            new Act("常用", "app:", "打开应用", R.drawable.ic_apps, 1),
            new Act("常用", "url:", "打开链接 / URL Scheme", R.drawable.ic_link, 2),
            new Act("媒体", "media:play-pause", "播放 / 暂停", R.drawable.ic_play_pause, 0),
            new Act("媒体", "media:next", "下一首", R.drawable.ic_skip_next, 0),
            new Act("媒体", "media:previous", "上一首", R.drawable.ic_skip_prev, 0),
            new Act("媒体", "media:play", "播放", R.drawable.ic_play, 0),
            new Act("媒体", "media:pause", "暂停", R.drawable.ic_pause, 0),
            new Act("媒体", "vol:up", "媒体音量 +", R.drawable.ic_vol_up, 0),
            new Act("媒体", "vol:down", "媒体音量 −", R.drawable.ic_vol_down, 0),
            new Act("系统", "dnd", "切换勿扰", R.drawable.ic_dnd, 0),
            new Act("系统", "ringer", "切换 响铃 / 振动", R.drawable.ic_bell, 0),
            new Act("系统", "wifi", "切换 Wi-Fi", R.drawable.ic_wifi, 0),
            new Act("系统", "bt", "切换 蓝牙", R.drawable.ic_bt, 0),
            new Act("系统", "screenshot", "截屏", R.drawable.ic_screenshot, 0),
            new Act("系统", "lock", "锁屏", R.drawable.ic_lock, 0),
            new Act("系统", "key:", "模拟按键 (keycode)", R.drawable.ic_key, 2),
            new Act("高级", "sh:", "Shell 命令 (root)", R.drawable.ic_terminal, 2),
    };

    private static final String[][] GESTURES = {
            {"single", "单击"}, {"double", "双击"}, {"long", "长按"}};
    private static final int[] GESTURE_ICONS = {
            R.drawable.ic_tap_single, R.drawable.ic_tap_double, R.drawable.ic_tap_long};

    private final View[] gestureRows = new View[3];
    private View playerRow, scanRow;
    private MaterialSwitch swSwallow, swHaptic;
    private Slider slDouble, slLong;
    private TextView tvDouble, tvLong;
    private boolean binding;

    private volatile List<AppItem> apps;
    private final Map<String, AppItem> appIndex = new HashMap<>();
    private final Runnable onService = this::render;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        LinearLayout gestureBox = findViewById(R.id.gestureBox);
        LayoutInflater inf = getLayoutInflater();
        for (int i = 0; i < 3; i++) {
            View row = inf.inflate(R.layout.item_row, gestureBox, false);
            final int idx = i;
            row.setOnClickListener(v -> chooseAction(idx));
            gestureBox.addView(row);
            gestureRows[i] = row;
        }
        LinearLayout playerBox = findViewById(R.id.playerBox);
        playerRow = inf.inflate(R.layout.item_row, playerBox, false);
        playerRow.setOnClickListener(v -> choosePlayer());
        playerBox.addView(playerRow);

        LinearLayout keyBox = findViewById(R.id.keyBox);
        scanRow = inf.inflate(R.layout.item_row, keyBox, false);
        scanRow.setOnClickListener(v -> editScanCode());
        keyBox.addView(scanRow);

        swSwallow = findViewById(R.id.swSwallow);
        swHaptic = findViewById(R.id.swHaptic);
        swSwallow.setOnCheckedChangeListener((v, c) -> { if (!binding) put(e -> e.putBoolean(Cfg.SWALLOW, c)); });
        swHaptic.setOnCheckedChangeListener((v, c) -> { if (!binding) put(e -> e.putBoolean(Cfg.HAPTIC, c)); });

        slDouble = findViewById(R.id.slDouble);
        slLong = findViewById(R.id.slLong);
        tvDouble = findViewById(R.id.tvDouble);
        tvLong = findViewById(R.id.tvLong);
        slDouble.addOnChangeListener((s, v, user) -> tvDouble.setText((int) v + " ms"));
        slLong.addOnChangeListener((s, v, user) -> tvLong.setText((int) v + " ms"));
        slDouble.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider s) {}
            @Override public void onStopTrackingTouch(Slider s) { put(e -> e.putInt(Cfg.DOUBLE_MS, (int) s.getValue())); }
        });
        slLong.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider s) {}
            @Override public void onStopTrackingTouch(Slider s) { put(e -> e.putInt(Cfg.LONG_MS, (int) s.getValue())); }
        });

        App.addListener(onService);
        render();
        new Thread(this::loadApps).start();
    }

    @Override
    protected void onDestroy() {
        App.removeListener(onService);
        super.onDestroy();
    }

    // ---------------- 渲染 ----------------

    private SharedPreferences prefs() { return App.prefs; }

    private interface Edit { void apply(SharedPreferences.Editor e); }

    private void put(Edit edit) {
        SharedPreferences p = prefs();
        if (p == null) {
            Toast.makeText(this, "模块未激活，改动无法生效", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor e = p.edit();
        edit.apply(e);
        e.apply();
        render();
    }

    private String getStr(String k, String d) { SharedPreferences p = prefs(); return p == null ? d : p.getString(k, d); }
    private int getInt(String k, int d) { SharedPreferences p = prefs(); return p == null ? d : p.getInt(k, d); }
    private boolean getBool(String k, boolean d) { SharedPreferences p = prefs(); return p == null ? d : p.getBoolean(k, d); }

    private void render() {
        boolean active = prefs() != null;
        MaterialCardView card = findViewById(R.id.statusCard);
        ImageView si = findViewById(R.id.statusIcon);
        TextView st = findViewById(R.id.statusTitle), sd = findViewById(R.id.statusDesc);
        int bg = MaterialColors.getColor(card, active
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorErrorContainer);
        int fg = MaterialColors.getColor(card, active
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnErrorContainer);
        card.setCardBackgroundColor(bg);
        si.setImageResource(active ? R.drawable.ic_check_circle : R.drawable.ic_error);
        ImageViewCompat.setImageTintList(si, ColorStateList.valueOf(fg));
        st.setTextColor(fg);
        sd.setTextColor(fg);
        st.setText(active ? "已激活" : "未激活");
        sd.setText(active ? "改动会实时同步到系统，不用重启"
                : "在 LSPosed 里启用本模块（作用域：系统框架）后重启");

        for (int i = 0; i < 3; i++) {
            String spec = getStr(Cfg.ACT_PREFIX + GESTURES[i][0], "");
            setRow(gestureRows[i], GESTURE_ICONS[i], GESTURES[i][1], describe(spec));
            Drawable appIcon = appIconFor(spec);
            if (appIcon != null) setRowAppIcon(gestureRows[i], appIcon);
        }

        String target = getStr(Cfg.MEDIA_TARGET, "");
        AppItem t = target.isEmpty() ? null : appIndex.get(target);
        setRow(playerRow, R.drawable.ic_music, "媒体动作发给",
                target.isEmpty() ? "跟随系统（当前播放器）" : (t != null ? t.label : target));
        if (t != null) setRowAppIcon(playerRow, t.icon);

        setRow(scanRow, R.drawable.ic_side_key, "扫描码",
                getInt(Cfg.SCANCODE, Cfg.DEF_SCANCODE) + "（一加 Ace 6 快捷键为 735）");

        binding = true;
        swSwallow.setChecked(getBool(Cfg.SWALLOW, true));
        swHaptic.setChecked(getBool(Cfg.HAPTIC, true));
        slDouble.setValue(snap(getInt(Cfg.DOUBLE_MS, Cfg.DEF_DOUBLE_MS), 120, 600, 10));
        slLong.setValue(snap(getInt(Cfg.LONG_MS, Cfg.DEF_LONG_MS), 300, 1500, 50));
        tvDouble.setText((int) slDouble.getValue() + " ms");
        tvLong.setText((int) slLong.getValue() + " ms");
        binding = false;
    }

    private static float snap(int v, int min, int max, int step) {
        v = Math.max(min, Math.min(max, v));
        return min + Math.round((v - min) / (float) step) * step;
    }

    private void setRow(View row, int icon, String title, String sub) {
        ImageView iv = row.findViewById(R.id.icon);
        FrameLayout box = row.findViewById(R.id.iconBox);
        box.setBackgroundResource(R.drawable.bg_icon_circle);
        ViewGroup.LayoutParams lp = iv.getLayoutParams();
        lp.width = lp.height = dp(22);
        iv.setLayoutParams(lp);
        iv.setImageResource(icon);
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(
                MaterialColors.getColor(iv, com.google.android.material.R.attr.colorOnSecondaryContainer)));
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.subtitle)).setText(sub);
    }

    private void setRowAppIcon(View row, Drawable d) {
        ImageView iv = row.findViewById(R.id.icon);
        row.findViewById(R.id.iconBox).setBackground(null);
        ViewGroup.LayoutParams lp = iv.getLayoutParams();
        lp.width = lp.height = dp(40);
        iv.setLayoutParams(lp);
        ImageViewCompat.setImageTintList(iv, null);
        iv.setImageDrawable(d);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static Act actOf(String spec) {
        if (spec == null || spec.isEmpty()) return ACTS[0];
        for (Act a : ACTS) {
            if (a.arg == 0 ? spec.equals(a.code) : spec.startsWith(a.code)) return a;
        }
        return ACTS[0];
    }

    private String describe(String spec) {
        Act a = actOf(spec);
        if (a.arg == 0) return a.name;
        String v = spec.substring(a.code.length());
        if (a.arg == 1) {
            AppItem it = appIndex.get(v);
            return "打开 " + (it != null ? it.label : v);
        }
        return a.name + "：" + v;
    }

    private Drawable appIconFor(String spec) {
        if (spec == null || !spec.startsWith("app:")) return null;
        AppItem it = appIndex.get(spec.substring(4));
        return it == null ? null : it.icon;
    }

    // ---------------- 选择动作 ----------------

    private void chooseAction(int idx) {
        if (prefs() == null) { put(e -> {}); return; }
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        NestedScrollView sv = new NestedScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(8), 0, dp(24));
        sv.addView(list);

        TextView head = (TextView) getLayoutInflater().inflate(R.layout.item_header, list, false);
        head.setText(GESTURES[idx][1] + "时");
        head.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        head.setTextColor(MaterialColors.getColor(head, com.google.android.material.R.attr.colorOnSurface));
        list.addView(head);

        Act cur = actOf(getStr(Cfg.ACT_PREFIX + GESTURES[idx][0], ""));
        String lastGroup = "";
        for (Act a : ACTS) {
            if (!a.group.equals(lastGroup)) {
                TextView h = (TextView) getLayoutInflater().inflate(R.layout.item_header, list, false);
                h.setText(a.group);
                list.addView(h);
                lastGroup = a.group;
            }
            View row = getLayoutInflater().inflate(R.layout.item_row, list, false);
            setRow(row, a.icon, a.name, a == cur ? "当前" : "");
            row.findViewById(R.id.subtitle).setVisibility(a == cur ? View.VISIBLE : View.GONE);
            row.findViewById(R.id.chevron).setVisibility(a.arg != 0 ? View.VISIBLE : View.GONE);
            row.setMinimumHeight(dp(60));
            row.setOnClickListener(v -> {
                sheet.dismiss();
                pickArg(idx, a);
            });
            list.addView(row);
        }
        sheet.setContentView(sv);
        sheet.show();
    }

    private void pickArg(int idx, Act a) {
        String key = Cfg.ACT_PREFIX + GESTURES[idx][0];
        if (a.arg == 0) {
            put(e -> e.putString(key, a.code.equals("none") ? "" : a.code));
        } else if (a.arg == 1) {
            pickApp(pkg -> put(e -> e.putString(key, "app:" + pkg)));
        } else {
            String cur = getStr(key, "");
            String init = cur.startsWith(a.code) ? cur.substring(a.code.length()) : "";
            String hint;
            int type = InputType.TYPE_CLASS_TEXT;
            switch (a.code) {
                case "url:": hint = "https://… 或 xxx://…"; type |= InputType.TYPE_TEXT_VARIATION_URI; break;
                case "key:": hint = "keycode，例：3 = 主屏幕"; type = InputType.TYPE_CLASS_NUMBER; break;
                default: hint = "以 root 执行的命令"; break;
            }
            textDialog(a.name, hint, init, type, v -> put(e -> e.putString(key, v.isEmpty() ? "" : a.code + v)));
        }
    }

    private interface OnText { void done(String v); }

    private void textDialog(String title, String hint, String init, int type, OnText cb) {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint);
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(type);
        et.setText(init);
        til.addView(et);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(24), dp(8), dp(24), 0);
        wrap.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(wrap)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> cb.done(String.valueOf(et.getText()).trim()))
                .show();
    }

    private void editScanCode() {
        textDialog("扫描码", "按键扫描码", String.valueOf(getInt(Cfg.SCANCODE, Cfg.DEF_SCANCODE)),
                InputType.TYPE_CLASS_NUMBER, v -> {
                    try {
                        int n = Integer.parseInt(v);
                        put(e -> e.putInt(Cfg.SCANCODE, n));
                    } catch (NumberFormatException ignored) {}
                });
    }

    private void choosePlayer() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("媒体动作发给")
                .setItems(new String[]{"跟随系统（当前播放器）", "选择一个播放器…"}, (d, w) -> {
                    if (w == 0) put(e -> e.putString(Cfg.MEDIA_TARGET, ""));
                    else pickApp(pkg -> put(e -> e.putString(Cfg.MEDIA_TARGET, pkg)));
                })
                .show();
    }

    // ---------------- 应用列表 ----------------

    private static final class AppItem { String pkg, label; Drawable icon; }

    private interface OnPick { void pick(String pkg); }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, AppItem> idx = new HashMap<>();
        List<AppItem> list = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
            String pkg = r.activityInfo.packageName;
            if (idx.containsKey(pkg)) continue;
            AppItem a = new AppItem();
            a.pkg = pkg;
            a.label = String.valueOf(r.loadLabel(pm));
            a.icon = r.loadIcon(pm);
            idx.put(pkg, a);
            list.add(a);
        }
        Collator col = Collator.getInstance(Locale.CHINA);
        list.sort((x, y) -> col.compare(x.label, y.label));
        runOnUiThread(() -> {
            appIndex.clear();
            appIndex.putAll(idx);
            apps = list;
            render();
        });
    }

    private void pickApp(OnPick cb) {
        List<AppItem> all = apps;
        if (all == null) { Toast.makeText(this, "应用列表加载中…", Toast.LENGTH_SHORT).show(); return; }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextInputLayout til = new TextInputLayout(this);
        til.setHint("搜索应用");
        TextInputEditText search = new TextInputEditText(til.getContext());
        search.setSingleLine(true);
        til.addView(search);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.setMargins(dp(24), dp(8), dp(24), dp(8));
        box.addView(til, tlp);

        ListView lv = new ListView(this);
        lv.setDivider(null);
        box.addView(lv, new LinearLayout.LayoutParams(-1, (int) (getResources().getDisplayMetrics().heightPixels * 0.55)));

        final List<AppItem> shown = new ArrayList<>(all);
        BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                View row = v != null ? v : getLayoutInflater().inflate(R.layout.item_row, parent, false);
                AppItem a = shown.get(i);
                ((TextView) row.findViewById(R.id.title)).setText(a.label);
                ((TextView) row.findViewById(R.id.subtitle)).setText(a.pkg);
                row.findViewById(R.id.chevron).setVisibility(View.GONE);
                setRowAppIcon(row, a.icon);
                row.setBackground(null);
                return row;
            }
        };
        lv.setAdapter(ad);

        androidx.appcompat.app.AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setTitle("选择应用")
                .setView(box)
                .setNegativeButton("取消", null)
                .create();
        lv.setOnItemClickListener((p, v, i, id) -> { cb.pick(shown.get(i).pkg); dlg.dismiss(); });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String k = s.toString().trim().toLowerCase(Locale.ROOT);
                shown.clear();
                for (AppItem a : all)
                    if (k.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(k)
                            || a.pkg.toLowerCase(Locale.ROOT).contains(k)) shown.add(a);
                ad.notifyDataSetChanged();
            }
        });
        dlg.show();
    }
}

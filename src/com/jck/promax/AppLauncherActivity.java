package com.jck.promax;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppLauncherActivity extends Activity {

    // ========== 应用信息模型 ==========
    static class AppItem {
        String label;
        String packageName;
        Drawable icon;
        boolean isSystem;
    }

    // ========== Activity信息模型 ==========
    static class ActivityInfoItem {
        String name;
        boolean isLauncher;
        String exported;
    }

    // ========== 图标缓存 ==========
    private Map<String, SoftReference<Drawable>> iconCache = new HashMap<>();

    // ========== UI组件 ==========
    private EditText searchInput;
    private LinearLayout appListContainer;
    private ScrollView appListScroll;
    private TextView statusText;
    private TextView filterInfoText;
    private TextView emptyText;
    private Button toggleBtn;
    private Button backBtn;
    private LinearLayout loadingContainer;
    private ProgressBar loadingBar;

    // ========== 数据 ==========
    private List<AppItem> allApps = new ArrayList<>();
    private List<AppItem> filteredApps = new ArrayList<>();
    private boolean showSystem = false;
    private PackageManager packageManager;

    // ========== 搜索节流 ==========
    private Handler searchHandler = new Handler();
    private Runnable pendingSearch;
    private static final long SEARCH_DEBOUNCE_MS = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_launcher);

        packageManager = getPackageManager();
        initViews();
        setupListeners();
        loadApps();
    }

    // ========== 初始化视图 ==========
    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        toggleBtn = findViewById(R.id.toggleBtn);
        searchInput = findViewById(R.id.searchInput);
        appListContainer = findViewById(R.id.appListContainer);
        appListScroll = findViewById(R.id.appListScroll);
        statusText = findViewById(R.id.statusText);
        filterInfoText = findViewById(R.id.filterInfoText);
        emptyText = findViewById(R.id.emptyText);
        loadingContainer = findViewById(R.id.loadingContainer);
        loadingBar = findViewById(R.id.loadingBar);
    }

    // ========== 设置监听器 ==========
    private void setupListeners() {
        // 返回按钮
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 切换用户/系统应用
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSystem = !showSystem;
                toggleBtn.setText(showSystem ? "系统应用" : "用户应用");
                applyFilterAndRender();
            }
        });

        // 搜索框实时过滤（300ms节流）
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingSearch != null) {
                    searchHandler.removeCallbacks(pendingSearch);
                }
                pendingSearch = new Runnable() {
                    @Override
                    public void run() {
                        applyFilterAndRender();
                    }
                };
                searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
            }
        });
    }

    // ========== 后台加载应用列表 ==========
    private void loadApps() {
        new AsyncTask<Void, Void, List<AppItem>>() {
            @Override
            protected List<AppItem> doInBackground(Void... voids) {
                List<AppItem> apps = new ArrayList<>();
                try {
                    List<PackageInfo> packages = packageManager.getInstalledPackages(
                            PackageManager.GET_ACTIVITIES);
                    for (PackageInfo pkgInfo : packages) {
                        if (pkgInfo.applicationInfo == null) continue;

                        AppItem item = new AppItem();
                        item.label = pkgInfo.applicationInfo.loadLabel(packageManager).toString();
                        item.packageName = pkgInfo.packageName;
                        item.isSystem = (pkgInfo.applicationInfo.flags
                                & ApplicationInfo.FLAG_SYSTEM) != 0;

                        // 使用缓存加载图标
                        item.icon = getCachedIcon(pkgInfo.packageName,
                                pkgInfo.applicationInfo);
                        apps.add(item);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // 按名称排序
                Collections.sort(apps, new Comparator<AppItem>() {
                    @Override
                    public int compare(AppItem a, AppItem b) {
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });

                return apps;
            }

            @Override
            protected void onPostExecute(List<AppItem> result) {
                if (isFinishing()) return;
                allApps = result;
                loadingContainer.setVisibility(View.GONE);
                applyFilterAndRender();
            }
        }.execute();
    }

    // ========== 图标缓存 ==========
    private Drawable getCachedIcon(String packageName, ApplicationInfo appInfo) {
        if (appInfo == null) return null;

        SoftReference<Drawable> ref = iconCache.get(packageName);
        Drawable cached = (ref != null) ? ref.get() : null;
        if (cached != null) return cached;

        try {
            Drawable icon = appInfo.loadIcon(packageManager);
            iconCache.put(packageName, new SoftReference<>(icon));
            return icon;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 过滤并渲染 ==========
    private void applyFilterAndRender() {
        String query = searchInput.getText().toString().trim().toLowerCase();

        filteredApps.clear();
        for (AppItem app : allApps) {
            // 过滤系统/用户应用
            if (!showSystem && app.isSystem) continue;
            if (showSystem && !app.isSystem) continue;

            // 搜索过滤
            if (!query.isEmpty()) {
                boolean matchName = app.label.toLowerCase().contains(query);
                boolean matchPkg = app.packageName.toLowerCase().contains(query);
                if (!matchName && !matchPkg) continue;
            }

            filteredApps.add(app);
        }

        renderAppList();
        updateStatus(query);
    }

    // ========== 更新底部状态 ==========
    private void updateStatus(String query) {
        String type = showSystem ? "系统应用" : "用户应用";
        statusText.setText("共 " + filteredApps.size() + " 个" + type);

        if (query != null && !query.isEmpty()) {
            filterInfoText.setText("搜索: \"" + query + "\"");
        } else {
            filterInfoText.setText("");
        }
    }

    // ========== 渲染应用列表 ==========
    private void renderAppList() {
        appListContainer.removeAllViews();

        if (filteredApps.isEmpty()) {
            appListScroll.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
            return;
        }

        appListScroll.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        for (int i = 0; i < filteredApps.size(); i++) {
            final AppItem app = filteredApps.get(i);
            View card = createAppCard(app, i);
            appListContainer.addView(card);
        }
    }

    // ========== 创建应用卡片 ==========
    private View createAppCard(final AppItem app, int index) {
        // 卡片容器 - 水平布局
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dpToPx(12);
        int padV = dpToPx(10);
        card.setPadding(padH, padV, padH, padV);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setClickable(true);
        card.setFocusable(true);

        // 设置卡片间距
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dpToPx(4), 0, dpToPx(4));
        card.setLayoutParams(cardParams);

        // 应用图标 - 40dp
        ImageView iconView = new ImageView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dpToPx(40), dpToPx(40));
        iconView.setLayoutParams(iconParams);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        ApplicationInfo appInfo = getApplicationInfo(app.packageName);
        Drawable icon = (appInfo != null)
                ? getCachedIcon(app.packageName, appInfo) : null;
        if (icon != null) {
            iconView.setImageDrawable(icon);
        } else {
            // 使用文字首字母作为占位
            iconView.setBackgroundResource(R.drawable.icon_placeholder_bg);
            iconView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        }
        card.addView(iconView);

        // 中间文字区域 - 名称 + 包名
        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        textParams.setMargins(dpToPx(10), 0, dpToPx(8), 0);
        textContainer.setLayoutParams(textParams);

        // 应用名称
        TextView nameView = new TextView(this);
        nameView.setText(app.label);
        nameView.setTextColor(getResources().getColor(R.color.md3_on_surface));
        nameView.setTextSize(14);
        nameView.setMaxLines(1);
        textContainer.addView(nameView);

        // 包名
        TextView pkgView = new TextView(this);
        pkgView.setText(app.packageName);
        pkgView.setTextColor(getResources().getColor(R.color.md3_on_surface_variant));
        pkgView.setTextSize(11);
        pkgView.setMaxLines(1);
        textContainer.addView(pkgView);

        // 系统应用标记
        if (app.isSystem) {
            TextView tagView = new TextView(this);
            tagView.setText("系统");
            tagView.setTextSize(9);
            tagView.setTextColor(getResources().getColor(R.color.md3_tag_orange));
            tagView.setPadding(0, dpToPx(2), 0, 0);
            textContainer.addView(tagView);
        }

        card.addView(textContainer);

        // 右侧箭头
        TextView arrowView = new TextView(this);
        arrowView.setText("▶");
        arrowView.setTextColor(getResources().getColor(R.color.md3_outline));
        arrowView.setTextSize(14);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        arrowView.setLayoutParams(arrowParams);
        card.addView(arrowView);

        // 点击事件 - 显示Activity列表
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showActivityDialog(app);
            }
        });

        return card;
    }

    // ========== 安全获取ApplicationInfo ==========
    private ApplicationInfo getApplicationInfo(String packageName) {
        try {
            return packageManager.getApplicationInfo(packageName, 0);
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 显示Activity对话框 ==========
    private void showActivityDialog(final AppItem app) {
        // 加载该包的所有Activity
        new AsyncTask<Void, Void, List<ActivityInfoItem>>() {
            @Override
            protected List<ActivityInfoItem> doInBackground(Void... voids) {
                List<ActivityInfoItem> activities = new ArrayList<>();
                try {
                    PackageInfo pkgInfo = packageManager.getPackageInfo(
                            app.packageName, PackageManager.GET_ACTIVITIES);

                    if (pkgInfo.activities != null) {
                        // 获取launcher activities
                        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
                        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                        launcherIntent.setPackage(app.packageName);
                        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(
                                launcherIntent, 0);

                        List<String> launcherActivityNames = new ArrayList<>();
                        for (ResolveInfo ri : resolveInfos) {
                            if (ri.activityInfo != null) {
                                launcherActivityNames.add(ri.activityInfo.name);
                            }
                        }

                        for (ActivityInfo actInfo : pkgInfo.activities) {
                            ActivityInfoItem item = new ActivityInfoItem();
                            item.name = actInfo.name;
                            item.isLauncher = launcherActivityNames.contains(actInfo.name);
                            item.exported = actInfo.exported ? "exported" : "not exported";
                            activities.add(item);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // 排序：launcher activity优先
                Collections.sort(activities, new Comparator<ActivityInfoItem>() {
                    @Override
                    public int compare(ActivityInfoItem a, ActivityInfoItem b) {
                        if (a.isLauncher && !b.isLauncher) return -1;
                        if (!a.isLauncher && b.isLauncher) return 1;
                        return a.name.compareToIgnoreCase(b.name);
                    }
                });

                return activities;
            }

            @Override
            protected void onPostExecute(List<ActivityInfoItem> result) {
                if (isFinishing()) return;
                buildActivityDialog(app, result);
            }
        }.execute();
    }

    // ========== 构建Activity选择对话框 ==========
    private void buildActivityDialog(final AppItem app, List<ActivityInfoItem> activities) {
        if (activities.isEmpty()) {
            Toast.makeText(this, "未找到可启动的Activity", Toast.LENGTH_SHORT).show();
            return;
        }

        // 构建选项列表
        final List<String> displayNames = new ArrayList<>();
        final List<String> actionKeys = new ArrayList<>();

        // 标准启动选项
        Intent stdLaunch = packageManager.getLaunchIntentForPackage(app.packageName);
        if (stdLaunch != null) {
            displayNames.add("[标准启动] LaunchIntent for package");
            actionKeys.add("std_launch");
        }

        displayNames.add("--- 指定Activity启动 ---");
        actionKeys.add("separator");

        // 列出所有Activity
        for (ActivityInfoItem act : activities) {
            String prefix = act.isLauncher ? "[Launcher] " : "           ";
            String suffix = " (" + act.exported + ")";
            // 简化显示名称
            String shortName = act.name;
            if (shortName.startsWith(app.packageName)) {
                shortName = shortName.substring(app.packageName.length());
            }
            displayNames.add(prefix + shortName + suffix);
            actionKeys.add("activity:" + act.name);
        }

        CharSequence[] items = displayNames.toArray(new CharSequence[0]);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(app.label + "\n" + app.packageName)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String key = actionKeys.get(which);
                        if ("separator".equals(key)) return; // 分隔行不可点击

                        if ("std_launch".equals(key)) {
                            launchStandard(app);
                        } else if (key.startsWith("activity:")) {
                            String activityName = key.substring("activity:".length());
                            showLaunchOptions(app, activityName);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .create();

        dialog.show();
    }

    // ========== 显示启动方式选择 ==========
    private void showLaunchOptions(final AppItem app, final String activityName) {
        String shortName = activityName;
        if (shortName.startsWith(app.packageName)) {
            shortName = shortName.substring(app.packageName.length());
        }

        final String[] options = {
                "普通启动 (setComponent)",
                "清除栈启动 (CLEAR_TOP | NEW_TASK)",
                "复制Activity名称到剪贴板"
        };

        new AlertDialog.Builder(this)
                .setTitle("选择启动方式\n" + shortName)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                launchActivity(app.packageName, activityName, false);
                                break;
                            case 1:
                                launchActivity(app.packageName, activityName, true);
                                break;
                            case 2:
                                copyToClipboard(activityName);
                                break;
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ========== 标准启动 ==========
    private void launchStandard(AppItem app) {
        try {
            Intent intent = packageManager.getLaunchIntentForPackage(app.packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(this, "正在启动 " + app.label, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "该应用没有标准启动入口", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ========== 指定Activity启动 ==========
    private void launchActivity(String packageName, String activityName, boolean clearStack) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(packageName, activityName));

            if (clearStack) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            startActivity(intent);

            String shortName = activityName;
            if (shortName.startsWith(packageName)) {
                shortName = shortName.substring(packageName.length());
            }
            Toast.makeText(this, "正在启动 " + shortName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ========== 复制到剪贴板 ==========
    private void copyToClipboard(String text) {
        try {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText(
                    "Activity名称", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    // ========== dp转px ==========
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理搜索Handler，防止内存泄漏
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        iconCache.clear();
    }
}

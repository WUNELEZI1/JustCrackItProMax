package com.jck.promax;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;

public class DetailActivity extends Activity {

    private String sn;
    private int appId;
    private String appName;
    private String iconUrl;
    private String developer;
    private long apkSize;
    private Button installBtn;
    private Button backBtn;
    private Button favBtn;
    private Button shareBtn;
    private ProgressDialog installDialog;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        prefs = new Prefs(this);
        sn = getIntent().getStringExtra("sn");
        appId = getIntent().getIntExtra("appId", 0);

        backBtn = findViewById(R.id.backBtn);
        installBtn = findViewById(R.id.installBtn);
        favBtn = findViewById(R.id.favBtn);
        shareBtn = findViewById(R.id.shareBtn);

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        // 绑定数据
        ImageView icon = findViewById(R.id.appIcon);
        TextView name = findViewById(R.id.appName);
        TextView developerView = findViewById(R.id.appDeveloper);
        TextView size = findViewById(R.id.appSize);
        TextView infoPkg = findViewById(R.id.infoPackage);
        TextView infoVer = findViewById(R.id.infoVersion);
        TextView infoTime = findViewById(R.id.infoUploadTime);
        TextView infoIcp = findViewById(R.id.infoIcp);
        TextView summary = findViewById(R.id.appSummary);
        TextView remark = findViewById(R.id.appRemark);
        TextView changelog = findViewById(R.id.appChangelog);
        TextView perms = findViewById(R.id.permissionsList);
        TextView title = findViewById(R.id.titleText);

        Intent i = getIntent();
        appName = i.getStringExtra("name");
        title.setText(appName);
        name.setText(appName);
        developer = i.getStringExtra("developer");
        developerView.setText(developer);

        long sizeVal = i.getLongExtra("size", 0);
        apkSize = sizeVal;
        AppModel tmp = new AppModel();
        tmp.apkSize = sizeVal;
        size.setText(tmp.getSizeDisplay());

        String pkg = i.getStringExtra("apkName");
        infoPkg.setText("包名: " + (pkg != null && !pkg.isEmpty() ? pkg : "未知"));

        String ver = i.getStringExtra("apkVersion");
        infoVer.setText("版本: " + (ver != null && !ver.isEmpty() ? ver : "未知"));

        String uploadTime = i.getStringExtra("uploadTime");
        tmp.uploadTime = uploadTime != null ? uploadTime : "";
        infoTime.setText("上架时间: " + tmp.getUploadDateDisplay());

        String icp = i.getStringExtra("icpNumber");
        infoIcp.setText("ICP备案: " + (icp != null && !icp.isEmpty() ? icp : "无"));

        summary.setText(i.getStringExtra("summary") != null ? i.getStringExtra("summary") : "暂无简介");

        String remarkStr = i.getStringExtra("remark");
        if (remarkStr != null && !remarkStr.isEmpty()) {
            findViewById(R.id.remarkSection).setVisibility(View.VISIBLE);
            remark.setText(remarkStr);
        }

        String changeLogStr = i.getStringExtra("changeLog");
        if (changeLogStr != null && !changeLogStr.isEmpty()) {
            findViewById(R.id.changelogSection).setVisibility(View.VISIBLE);
            changelog.setText(changeLogStr);
        }

        // 权限
        String permsJson = i.getStringExtra("permissions");
        if (permsJson != null && !permsJson.isEmpty() && !permsJson.equals("[]")) {
            findViewById(R.id.permissionsSection).setVisibility(View.VISIBLE);
            try {
                JSONArray arr = new JSONArray(permsJson);
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < arr.length(); j++) {
                    sb.append("• ").append(arr.getString(j)).append("\n");
                }
                perms.setText(sb.toString());
            } catch (Exception e) {
                perms.setText(permsJson);
            }
        }

        // 标签
        String tagsJson = i.getStringExtra("tags");
        LinearLayout tagsContainer = findViewById(R.id.tagsContainer);
        if (tagsJson != null && !tagsJson.isEmpty() && !tagsJson.equals("[]")) {
            try {
                JSONArray arr = new JSONArray(tagsJson);
                if (arr.length() > 0) {
                    tagsContainer.setVisibility(View.VISIBLE);
                    for (int j = 0; j < arr.length(); j++) {
                        String tag = arr.getString(j);
                        TextView tagView = new TextView(this);
                        tagView.setText("  " + tag + "  ");
                        tagView.setTextColor(getResources().getColor(R.color.md3_on_primary));
                        tagView.setTextSize(11);
                        tagView.setPadding(8, 4, 8, 4);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.setMargins(0, 0, 6, 0);
                        tagView.setLayoutParams(lp);
                        int bgColor = R.color.md3_tag_orange;
                        if (tag.contains("高") || tag.contains("风险")) bgColor = R.color.md3_tag_red;
                        else if (tag.contains("家长") || tag.contains("管控")) bgColor = R.color.md3_tag_blue;
                        else if (tag.contains("推荐")) bgColor = R.color.md3_tag_green;
                        tagView.setBackgroundResource(R.drawable.tag_bg);
                        tagView.setBackgroundColor(getResources().getColor(bgColor));
                        tagsContainer.addView(tagView);
                    }
                }
            } catch (Exception e) {}
        }

        // 图标
        iconUrl = i.getStringExtra("icon");
        if (iconUrl != null && !iconUrl.isEmpty()) {
            ImageLoader.load(iconUrl, icon);
        }

        // 收藏按钮状态
        updateFavButton();
        favBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFavorite();
            }
        });

        // 分享按钮
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareApp();
            }
        });

        // 安装按钮
        installBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doInstall();
            }
        });
    }

    private void updateFavButton() {
        boolean isFav = prefs.isFavorite(appId);
        favBtn.setText(isFav ? "♥" : "♡");
    }

    private void toggleFavorite() {
        boolean isFav = prefs.isFavorite(appId);
        if (isFav) {
            prefs.removeFavorite(appId);
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            prefs.addFavorite(appId, appName, iconUrl != null ? iconUrl : "", developer != null ? developer : "", apkSize);
            Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show();
        }
        updateFavButton();
    }

    private void shareApp() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(appName).append("】\n");
        sb.append("开发者: ").append(developer != null ? developer : "未知").append("\n");
        sb.append("应用ID: ").append(appId).append("\n");
        sb.append("via JCKProMax");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "分享应用"));
    }

    private void doInstall() {
        // 防重复安装检查
        if (ApiHelper.isInstalling(sn, appId)) {
            Toast.makeText(this, "该应用正在安装中，请勿重复操作", Toast.LENGTH_LONG).show();
            return;
        }

        installBtn.setEnabled(false);
        installBtn.setText("安装中...");

        installDialog = new ProgressDialog(this);
        installDialog.setMessage("正在发送安装指令...\nSN: " + sn + "\nAppID: " + appId);
        installDialog.setCancelable(false);
        installDialog.show();

        new AsyncTask<Void, Void, ApiHelper.InstallResult>() {
            @Override
            protected ApiHelper.InstallResult doInBackground(Void... voids) {
                return ApiHelper.installApp(sn, appId);
            }

            @Override
            protected void onPostExecute(ApiHelper.InstallResult result) {
                if (installDialog != null && installDialog.isShowing()) {
                    installDialog.dismiss();
                }
                installBtn.setEnabled(true);
                installBtn.setText("安装");

                if (result.success) {
                    Toast.makeText(DetailActivity.this, "✅ 安装指令已发送! [" + result.source + "] " + result.msg, Toast.LENGTH_LONG).show();
                } else if ("dedup".equals(result.source)) {
                    Toast.makeText(DetailActivity.this, "⊘ 该应用正在安装中，请勿重复操作", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(DetailActivity.this, "❌ 安装失败 [" + result.source + "]: " + result.msg, Toast.LENGTH_LONG).show();
                }

                // 记录安装历史（防重复的不记录）
                if (!"dedup".equals(result.source)) {
                    prefs.addInstallRecord(appId, appName, result.success, result.msg);
                    prefs.appendGlobalLog("安装 " + appName + " (ID=" + appId + "): " + (result.success ? "成功" : "失败") + " [" + result.source + "] " + result.msg);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}

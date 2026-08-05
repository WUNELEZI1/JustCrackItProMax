package com.jck.promax;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = Obfuscator.dec(
        new byte[]{(byte)0xae,(byte)0xa7,(byte)0xaf,(byte)0x9b,(byte)0xb4,(byte)0xb6,(byte)0xab,(byte)0xa9,(byte)0xa5,(byte)0xbc,(byte)0x9b,(byte)0xb4,(byte)0xb6,(byte)0xa1,(byte)0xa2,(byte)0xb7}, (byte)0xc4);
    private static final String KEY_DISCLAIMER_AGREED = Obfuscator.dec(
        new byte[]{(byte)0xdb,(byte)0xd6,(byte)0xcc,(byte)0xdc,(byte)0xd3,(byte)0xde,(byte)0xd6,(byte)0xd2,(byte)0xda,(byte)0xcd,(byte)0xe0,(byte)0xde,(byte)0xd8,(byte)0xcd,(byte)0xda,(byte)0xda,(byte)0xdb}, (byte)0xbf);

    // 全屏更新阻断覆盖层
    private FrameLayout mUpdateOverlay;
    private TextView mOverlayTitle;
    private TextView mOverlayMessage;
    private ProgressBar mOverlayProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 在setContentView之前应用主题
        Prefs prefs = new Prefs(this);
        String theme = prefs.getTheme();
        if ("dark".equals(theme)) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
        } else if ("orange".equals(theme)) {
            setTheme(R.style.Theme_JCKProMax);
        } else {
            setTheme(R.style.Theme_JCKProMax);
        }

        setContentView(R.layout.activity_main);

        // 初始化全屏阻断覆盖层
        mUpdateOverlay = (FrameLayout) findViewById(R.id.updateOverlay);
        mOverlayTitle = (TextView) findViewById(R.id.overlayTitle);
        mOverlayMessage = (TextView) findViewById(R.id.overlayMessage);
        mOverlayProgress = (ProgressBar) findViewById(R.id.overlayProgress);

        // 动态设置底部版本号（从 PackageManager 读取真实版本）
        TextView footerVersion = (TextView) findViewById(R.id.footerVersion);
        if (footerVersion != null) {
            try {
                String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                footerVersion.setText("JCKProMax v" + v + "\n作者: flashcat (QQ: 3886773505)\n仅供学习交流 严禁用于非法用途");
            } catch (Exception e) {
                footerVersion.setText("JCKProMax\n作者: flashcat (QQ: 3886773505)\n仅供学习交流 严禁用于非法用途");
            }
        }

        // 先检查更新，在免责声明之前
        checkForUpdates();

        // 4个模块
        View storeBtn = findViewById(R.id.storeBtn);
        View browserBtn = findViewById(R.id.browserBtn);
        View broadcastBtn = findViewById(R.id.broadcastBtn);
        View zybosBtn = findViewById(R.id.zybosBtn);
        Button settingsBtn = findViewById(R.id.settingsBtn);
        Button menuBtn = findViewById(R.id.menuBtn);

        storeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String sn = new Prefs(MainActivity.this).getSN();
                Intent intent = new Intent(MainActivity.this, StoreActivity.class);
                if (!sn.isEmpty()) {
                    intent.putExtra("sn", sn);
                }
                startActivity(intent);
            }
        });

        browserBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, BrowserActivity.class));
            }
        });

        broadcastBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, BroadcastInstallActivity.class));
            }
        });

        zybosBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ZybosToolsActivity.class));
            }
        });

        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TerminalActivity.class));
            }
        });

        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenuDialog();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次恢复时检查并请求运行时权限（确保已授权）
        PermissionHelper.checkAndRequestAll(this);
        // 用户从安装器返回（取消安装），立即锁定窗口+阻断+重试
        if (UpdateHelper.isPendingUpdate()) {
            // 确保覆盖层显示，锁定窗口
            showUpdateOverlay("正在处理更新", "请稍候，正在重新打开安装器...");
            lockWindow();
            handlePendingUpdate();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 窗口重新获得焦点（包括从系统弹窗返回），立即重新锁定
        if (hasFocus && UpdateHelper.isPendingUpdate()) {
            // 确保覆盖层显示，用户无法看到任何主界面内容
            showUpdateOverlay("正在处理更新", "请稍候，正在重新打开安装器...");
            lockWindow();
            handlePendingUpdate();
        }
    }

    @Override
    public void onBackPressed() {
        // 有更新挂起时，禁止返回键
        if (UpdateHelper.isPendingUpdate()) {
            return;
        }
        super.onBackPressed();
    }

    /**
     * 显示全屏阻断覆盖层，物理遮挡所有主界面内容
     */
    public void showUpdateOverlay(String title, String message) {
        if (mOverlayTitle != null && title != null) {
            mOverlayTitle.setText(title);
        }
        if (mOverlayMessage != null && message != null) {
            mOverlayMessage.setText(message);
        }
        if (mUpdateOverlay != null && mUpdateOverlay.getVisibility() != View.VISIBLE) {
            mUpdateOverlay.setVisibility(View.VISIBLE);
            mUpdateOverlay.bringToFront();
            mUpdateOverlay.requestFocus();
        }
    }

    /**
     * 更新覆盖层消息
     */
    public void updateOverlayMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mOverlayMessage != null) {
                    mOverlayMessage.setText(message);
                }
            }
        });
    }

    /**
     * 更新覆盖层进度条
     */
    public void updateOverlayProgress(final int progress, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mOverlayProgress != null) {
                    mOverlayProgress.setVisibility(View.VISIBLE);
                    mOverlayProgress.setProgress(progress);
                }
                if (mOverlayMessage != null && message != null) {
                    mOverlayMessage.setText(message);
                }
            }
        });
    }

    /**
     * 隐藏全屏阻断覆盖层
     */
    public void hideUpdateOverlay() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mUpdateOverlay != null) {
                    mUpdateOverlay.setVisibility(View.GONE);
                }
                if (mOverlayProgress != null) {
                    mOverlayProgress.setVisibility(View.GONE);
                    mOverlayProgress.setProgress(0);
                }
            }
        });
    }

    /**
     * 锁定窗口——禁止触摸交互 + 禁止截屏
     * 覆盖层提供物理遮挡，FLAG 提供系统级锁定
     */
    public void lockWindow() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    /**
     * 解锁窗口
     */
    public void unlockWindow() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    /**
     * 处理挂起的更新——显示阻断弹窗并尝试重新打开安装器
     * 弹窗和覆盖层保持可见，不消失
     */
    private void handlePendingUpdate() {
        // 先确保覆盖层和锁定
        showUpdateOverlay("正在处理更新", "请稍候，正在重新打开安装器...");
        lockWindow();

        // 尝试重新打开安装器
        boolean opened = UpdateHelper.retryInstall(this);
        if (opened) {
            // 安装器已打开，弹窗和覆盖层保持可见（不消失）
            // 用户安装完成后 app 会重启，弹窗自然消失
            // 如果用户取消安装，onResume/onWindowFocusChanged 会再次触发
        } else {
            // 安装器打不开，检查 APK 还在不在
            if (UpdateHelper.isApkDownloaded(this)) {
                // APK 还在但安装器打不开——显示错误弹窗
                showInstallFailedDialog();
            } else {
                // APK 没了——重新下载
                checkForUpdates();
            }
        }
    }

    /**
     * 安装器打不开时的错误弹窗，不可取消，只能重试
     */
    private void showInstallFailedDialog() {
        // 先解锁窗口，否则 FLAG_NOT_TOUCHABLE 可能影响对话框的按钮点击
        // 覆盖层保持显示，确保用户仍看不到主界面
        unlockWindow();
        new AlertDialog.Builder(this)
            .setTitle("安装失败")
            .setMessage("无法打开系统安装器，请点击重试再次尝试")
            .setCancelable(false)
            .setPositiveButton("重试", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    lockWindow();
                    handlePendingUpdate();
                }
            })
            .show();
    }

    private void showMenuDialog() {
        final String[] items = {"终端", "切换主题", "关于 JCKProMax"};
        new AlertDialog.Builder(this)
            .setTitle("菜单")
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case 0:
                            startActivity(new Intent(MainActivity.this, TerminalActivity.class));
                            break;
                        case 1:
                            showThemeDialog();
                            break;
                        case 2:
                            showAboutDialog();
                            break;
                    }
                }
            })
            .show();
    }

    private void showThemeDialog() {
        final String[] themes = {"浅色（默认）", "深色", "橙色"};
        final String[] values = {"light", "dark", "orange"};
        Prefs prefs = new Prefs(this);
        String current = prefs.getTheme();
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
            .setTitle("选择主题")
            .setSingleChoiceItems(themes, checked, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Prefs p = new Prefs(MainActivity.this);
                    p.setTheme(values[which]);
                    dialog.dismiss();
                    recreate();
                }
            })
            .show();
    }

    private void showAboutDialog() {
        String version = "未知";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {}
        new AlertDialog.Builder(this)
            .setTitle("关于 JCKProMax")
            .setMessage("JCKProMax v" + version + "\n\n作者: flashcat\nQQ: 3886773505\n\n声明: 本工具仅供学习交流、安全研究使用，\n严禁用于任何违法违规用途。\n\n使用者必须自行承担所有使用风险，\n作者不对任何损失负责。\n\n功能模块:\n• 应用商店 - 搜索安装应用\n• 浏览器 - 内置网页浏览\n• 直链安装 - APK安装\n• 系统工具 - 系统级API调用\n\n⚠ 请勿用于非法用途")
            .setPositiveButton("确定", null)
            .show();
    }

    private void showDisclaimerDialog() {
        ScrollView scrollView = new ScrollView(this);
        TextView content = new TextView(this);
        content.setPadding(48, 32, 48, 32);
        content.setTextSize(13);
        content.setTextColor(0xFF333333);
        content.setLineSpacing(8, 1);

        StringBuilder sb = new StringBuilder();
        sb.append("请仔细阅读以下声明：\n\n");
        sb.append("【免责及使用条款】\n\n");
        sb.append("1. 本软件（JCKProMax）仅供学习交流、安全研究使用，不得用于任何违法违规用途。\n\n");
        sb.append("2. 本软件涉及的功能（包括但不限于广播安装、系统工具、批量安装等）可能被滥用于未经授权的设备操作。");
        sb.append("使用者必须确保所有操作均已获得设备所有者的明确授权。\n\n");
        sb.append("3. 严禁将本软件用于以下行为：");
        sb.append("\n   - 未经授权向他人设备安装或卸载应用");
        sb.append("\n   - 对他人设备进行\"轰炸\"、骚扰或破坏性操作");
        sb.append("\n   - 绕过他人设备的安全管控措施");
        sb.append("\n   - 任何侵犯他人隐私或财产权益的行为\n\n");
        sb.append("4. 因使用本软件产生的一切后果（包括但不限于设备损坏、数据丢失、法律纠纷等），");
        sb.append("均由使用者自行承担，软件作者不承担任何责任。\n\n");
        sb.append("5. 如您发现有人利用本软件从事违法活动，请立即停止使用并向有关部门举报。\n\n");
        sb.append("6. 继续使用本软件即表示您已阅读、理解并同意以上全部条款。");

        content.setText(sb.toString());
        scrollView.addView(content);

        new AlertDialog.Builder(this)
            .setTitle("⚠ 免责声明")
            .setView(scrollView)
            .setCancelable(false)
            .setPositiveButton("我已阅读并同意", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_DISCLAIMER_AGREED, true)
                        .apply();
                }
            })
            .setNegativeButton("不同意，退出", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            })
            .show();
    }

    /**
     * 检查更新，有更新则强制更新
     * 无更新则显示免责声明（如果未同意）
     */
    private void checkForUpdates() {
        new AsyncTask<Void, Void, UpdateHelper.UpdateInfo>() {
            @Override
            protected UpdateHelper.UpdateInfo doInBackground(Void... voids) {
                return UpdateHelper.checkUpdate(MainActivity.this);
            }

            @Override
            protected void onPostExecute(UpdateHelper.UpdateInfo info) {
                if (info != null && info.hasUpdate) {
                    // 检测到更新——立即显示全屏覆盖层 + 锁定窗口
                    // 此时主界面内容完全被物理遮挡，用户无法看到也无法操作
                    showUpdateOverlay("检测到新版本", "版本: v" + info.latestVersion + "\n\n正在自动下载更新...");
                    lockWindow();
                    UpdateHelper.showUpdateDialog(MainActivity.this, info);
                } else {
                    SharedPreferences sprefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    boolean agreed = sprefs.getBoolean(KEY_DISCLAIMER_AGREED, false);
                    if (!agreed) {
                        showDisclaimerDialog();
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean allGranted = PermissionHelper.handleResult(requestCode, permissions, grantResults);
        if (!allGranted) {
            // 部分权限被拒绝，提示用户
            // 不阻止使用，但记录
        }
    }
}
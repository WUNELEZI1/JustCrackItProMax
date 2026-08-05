package com.jck.promax;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateHelper {

    private static final String API_URL = Obfuscator.dec3(
        new byte[]{(byte)0x20,(byte)0x38,(byte)0xe6,(byte)0x38,(byte)0x3f,(byte)0xa8,(byte)0x67,(byte)0x63,(byte)0xf5,(byte)0x21,(byte)0x38,(byte)0xf7,(byte)0x2d,(byte)0x62,(byte)0xf1,(byte)0x27,(byte)0x21,(byte)0xbd,(byte)0x29,(byte)0x3c,(byte)0xfb,(byte)0x67,(byte)0x3a,(byte)0xa7,(byte)0x67,(byte)0x3e,(byte)0xf7,(byte)0x38,(byte)0x23,(byte)0xe1,(byte)0x67,(byte)0x1b,(byte)0xc7,(byte)0x06,(byte)0x09,(byte)0xde,(byte)0x0d,(byte)0x16,(byte)0xdb,(byte)0x79,(byte)0x63,(byte)0xf8,(byte)0x2b,(byte)0x27,(byte)0xbf,(byte)0x38,(byte)0x3e,(byte)0xfd,(byte)0x65,(byte)0x21,(byte)0xf3,(byte)0x30,(byte)0x63,(byte)0xe0,(byte)0x2d,(byte)0x20,(byte)0xf7,(byte)0x29,(byte)0x3f,(byte)0xf7,(byte)0x3b,(byte)0x63,(byte)0xfe,(byte)0x29,(byte)0x38,(byte)0xf7,(byte)0x3b,(byte)0x38},
        (byte)0x48, (byte)0x4c, (byte)0x92);
    private static final String FILE_NAME = Obfuscator.dec2(
        new byte[]{(byte)0xe1,(byte)0x54,(byte)0xe0,(byte)0x47,(byte)0xd9,(byte)0x78,(byte)0xe6,(byte)0x76,(byte)0xd3,(byte)0x3a,(byte)0xde,(byte)0x67,(byte)0xcf,(byte)0x76,(byte)0xdf,(byte)0x72,(byte)0x85,(byte)0x76,(byte)0xdb,(byte)0x7c},
        (byte)0xab, (byte)0x17);
    private static String sLastDownloadUrl = "";

    /** 标记是否有更新处于挂起状态 */
    private static boolean sPendingUpdate = false;

    /** 持久化的更新弹窗——全程保持，不消失 */
    private static AlertDialog sUpdateDialog = null;

    /** 缓存发行说明，更新进度时保持显示 */
    private static String sReleaseNotes = "";

    public static class UpdateInfo {
        public String latestVersion;
        public String downloadUrl;
        public String tagName;
        public String releaseNotes;
        public boolean hasUpdate;
    }

    public static boolean isPendingUpdate() {
        return sPendingUpdate;
    }

    public static boolean isApkDownloaded(Context context) {
        File apkFile = new File(context.getFilesDir(), FILE_NAME);
        return apkFile.exists() && apkFile.length() > 0;
    }

    /**
     * 重新尝试安装，返回 true 表示安装器成功打开
     */
    public static boolean retryInstall(final Activity activity) {
        File apkFile = new File(activity.getFilesDir(), FILE_NAME);
        if (apkFile.exists() && apkFile.length() > 0) {
            return openInstaller(activity, apkFile);
        }
        return false;
    }

    public static UpdateInfo checkUpdate(Context context) {
        UpdateInfo info = new UpdateInfo();
        info.hasUpdate = false;
        try {
            String currentVersion = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0)
                .versionName;

            HttpHelper.trustAllCertificates();
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "JCKProMax/Update");
            conn.setRequestProperty("Accept", "application/json");
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code != 200) return info;

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();

            JSONObject json = new JSONObject(sb.toString());
            String tagName = json.optString("tag_name", "");
            String releaseBody = json.optString("body", "");

            String latestVersion = "";
            if (tagName.toLowerCase().contains("jckpromax")) {
                latestVersion = tagName.substring("JckProMaxV".length());
            }
            if (latestVersion.isEmpty()) return info;

            info.tagName = tagName;
            info.latestVersion = latestVersion;
            info.releaseNotes = releaseBody;

            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null) {
                        String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            info.downloadUrl = asset.optString("browser_download_url", "");
                            break;
                        }
                    }
                }
            }

            info.hasUpdate = compareVersions(latestVersion, currentVersion) > 0;
        } catch (Exception e) {
            info.hasUpdate = false;
        }
        return info;
    }

    private static int compareVersions(String v1, String v2) {
        try {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            int len = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < len; i++) {
                int n1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int n2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
                if (n1 != n2) return n1 - n2;
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 显示一个完全阻断的弹窗——用户无法关闭，无法点击，只能等待
     * 安装器打开后由调用方关闭此弹窗
     */
    public static AlertDialog showBlockingDialog(Activity activity, String title, String message) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .show();
        dialog.setCanceledOnTouchOutside(false);
        // 移除所有按钮，只显示标题和内容（Builder 没设按钮，getButton 可能为 null）
        android.view.View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) positive.setVisibility(android.view.View.GONE);
        android.view.View negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) negative.setVisibility(android.view.View.GONE);
        return dialog;
    }

    /**
     * 强制更新弹窗——全程保持，不消失
     * 显示版本信息 + 更新内容 + 下载进度，直到安装完成
     * 覆盖层 + FLAG_NOT_TOUCHABLE 提供双重锁定
     */
    public static void showUpdateDialog(final Activity activity, final UpdateInfo info) {
        sPendingUpdate = true;

        // 缓存发行说明
        sReleaseNotes = "";
        if (info.releaseNotes != null && !info.releaseNotes.isEmpty()) {
            sReleaseNotes = "更新内容:\n" + info.releaseNotes;
        }

        // 构建弹窗内容：版本信息 + 更新内容
        StringBuilder msg = new StringBuilder();
        msg.append("当前版本: v").append(getCurrentVersion(activity)).append("\n");
        msg.append("新版本: v").append(info.latestVersion).append("\n\n");
        if (!sReleaseNotes.isEmpty()) {
            msg.append(sReleaseNotes).append("\n\n");
        }
        msg.append("正在连接服务器...");

        // 显示一个持久化的阻断弹窗——无按钮、不可取消、全程保持
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("强制更新")
            .setMessage(msg.toString())
            .setCancelable(false)
            .show();
        dialog.setCanceledOnTouchOutside(false);
        // 隐藏所有按钮
        android.view.View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) positive.setVisibility(android.view.View.GONE);
        android.view.View negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) negative.setVisibility(android.view.View.GONE);

        // 保存引用，后续更新弹窗内容
        sUpdateDialog = dialog;

        // 同步更新覆盖层
        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            main.showUpdateOverlay("检测到新版本 v" + info.latestVersion,
                msg.toString().replace("正在连接服务器...", "正在自动下载更新..."));
        }

        // 直接开始下载
        downloadAndInstall(activity, info.downloadUrl);
    }

    /**
     * 更新弹窗的消息内容（保持弹窗可见，只更新文字）
     * 始终在顶部保留发行说明
     */
    private static void updateDialogMessage(final Activity activity, final String message) {
        String fullMsg = sReleaseNotes.isEmpty() ? message : (sReleaseNotes + "\n\n" + message);
        if (sUpdateDialog != null && sUpdateDialog.isShowing()) {
            sUpdateDialog.setMessage(fullMsg);
        }
        // 同时更新覆盖层
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).updateOverlayMessage(fullMsg);
        }
    }

    private static String getCurrentVersion(Context context) {
        try {
            return context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0)
                .versionName;
        } catch (Exception e) {
            return "未知";
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        else if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        else return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    /**
     * 下载APK到 getFilesDir() 并安装
     * 不创建 ProgressDialog，直接更新持久化弹窗 + 覆盖层
     */
    public static void downloadAndInstall(final Activity activity, final String downloadUrl) {
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            showErrorDialog(activity, "下载链接无效", null);
            return;
        }
        sLastDownloadUrl = downloadUrl;

        // 弹窗全程保持，不创建 ProgressDialog，直接在弹窗和覆盖层上更新进度
        updateDialogMessage(activity, "正在连接服务器...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Handler handler = new Handler(Looper.getMainLooper());
                HttpURLConnection conn = null;
                try {
                    HttpHelper.trustAllCertificates();

                    URL url = new URL(downloadUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(120000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36");
                    conn.setRequestProperty("Accept", "*/*");
                    conn.setInstanceFollowRedirects(true);
                    conn.connect();

                    int code = conn.getResponseCode();
                    if (code != 200) {
                        showDownloadError(activity, "服务器返回" + code);
                        return;
                    }

                    final long contentLength = conn.getContentLengthLong();
                    final String totalSizeStr = formatFileSize(contentLength > 0 ? contentLength : 0);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            String msg = "下载中 (" + totalSizeStr + ")";
                            updateDialogMessage(activity, msg);
                        }
                    });

                    // 保存到 getFilesDir()
                    File dataDir = activity.getFilesDir();
                    final File apkFile = new File(dataDir, FILE_NAME);
                    if (apkFile.exists()) apkFile.delete();

                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(apkFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    long total = 0;
                    int lastProgress = -1;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        total += len;
                        if (contentLength > 0) {
                            final int pct = (int) (total * 100 / contentLength);
                            if (pct != lastProgress) {
                                lastProgress = pct;
                                final long downloaded = total;
                                final String msg = "下载中 " + formatFileSize(downloaded) + "/" + totalSizeStr;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateDialogMessage(activity, msg);
                                        // 更新覆盖层进度条
                                        if (activity instanceof MainActivity) {
                                            ((MainActivity) activity).updateOverlayProgress(pct, msg);
                                        }
                                    }
                                });
                            }
                        }
                    }
                    fos.close();
                    is.close();
                    if (conn != null) conn.disconnect();

                    final long fileSize = total;
                    if (fileSize == 0) {
                        showDownloadError(activity, "下载文件为空");
                        apkFile.delete();
                        return;
                    }

                    // 下载完成——弹窗和覆盖层保持，更新消息后打开安装器
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateDialogMessage(activity, "下载完成，正在打开安装器...");
                            if (activity instanceof MainActivity) {
                                ((MainActivity) activity).updateOverlayProgress(100, "下载完成，正在打开安装器...");
                            }
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    tryOpenInstaller(activity, apkFile);
                                }
                            }, 600);
                        }
                    });

                } catch (final Exception e) {
                    showDownloadError(activity, e.getMessage());
                }
            }
        }).start();
    }

    /**
     * 尝试多种方式打开系统安装器
     * 弹窗和覆盖层保持可见——不消失，直到用户安装完成/重启
     */
    private static void tryOpenInstaller(final Activity activity, final File apkFile) {
        if (tryOpenInstallerAll(activity, apkFile)) {
            // 安装器打开成功——弹窗和覆盖层保持可见
            // 用户安装完成后 app 会重启，弹窗自然消失
            // 如果用户取消安装，onResume/onWindowFocusChanged 会重新处理
        } else {
            showInstallError(activity, "无法打开安装器", null);
        }
    }

    /**
     * 尝试所有方式打开系统安装器，返回 true 表示成功
     */
    private static boolean tryOpenInstallerAll(Activity activity, File apkFile) {
        // 方式1: ACTION_INSTALL_PACKAGE + content:// URI
        try {
            Uri uri = Uri.parse(Obfuscator.dec3(new byte[]{(byte)0x93, (byte)0x10, (byte)0x3d, (byte)0x84, (byte)0x1a, (byte)0x3d, (byte)0x84, (byte)0x45, (byte)0x7c, (byte)0xdf, (byte)0x1c, (byte)0x3c, (byte)0x9d, (byte)0x51, (byte)0x39, (byte)0x93, (byte)0x14, (byte)0x7d, (byte)0x80, (byte)0x0d, (byte)0x3c, (byte)0x9d, (byte)0x1e, (byte)0x2b, (byte)0xde, (byte)0x0a, (byte)0x23, (byte)0x94, (byte)0x1e, (byte)0x27, (byte)0x95, (byte)0x19, (byte)0x3a, (byte)0x9c, (byte)0x1a, (byte)0x23, (byte)0x82, (byte)0x10, (byte)0x25, (byte)0x99, (byte)0x1b, (byte)0x36, (byte)0x82, (byte)0x50}, (byte)0xf0, (byte)0x7f, (byte)0x53) + FILE_NAME);
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(uri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (Exception ignored) {}

        // 方式2: ACTION_VIEW + content:// URI
        try {
            Uri uri = Uri.parse(Obfuscator.dec3(new byte[]{(byte)0x93, (byte)0x10, (byte)0x3d, (byte)0x84, (byte)0x1a, (byte)0x3d, (byte)0x84, (byte)0x45, (byte)0x7c, (byte)0xdf, (byte)0x1c, (byte)0x3c, (byte)0x9d, (byte)0x51, (byte)0x39, (byte)0x93, (byte)0x14, (byte)0x7d, (byte)0x80, (byte)0x0d, (byte)0x3c, (byte)0x9d, (byte)0x1e, (byte)0x2b, (byte)0xde, (byte)0x0a, (byte)0x23, (byte)0x94, (byte)0x1e, (byte)0x27, (byte)0x95, (byte)0x19, (byte)0x3a, (byte)0x9c, (byte)0x1a, (byte)0x23, (byte)0x82, (byte)0x10, (byte)0x25, (byte)0x99, (byte)0x1b, (byte)0x36, (byte)0x82, (byte)0x50}, (byte)0xf0, (byte)0x7f, (byte)0x53) + FILE_NAME);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (Exception ignored) {}

        // 方式3: file:// URI（Android 7- 兼容）
        try {
            Uri uri = Uri.fromFile(apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 24) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            activity.startActivity(intent);
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * 安装失败弹窗——弹窗和覆盖层保持
     */
    private static void showInstallError(final Activity activity, final String title, final String detail) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 更新弹窗内容为错误信息，弹窗保持可见
                String msg = (detail != null) ? title + "\n\n" + detail : title;
                msg += "\n\n请点击重试重新下载安装";
                if (sUpdateDialog != null && sUpdateDialog.isShowing()) {
                    sUpdateDialog.setMessage(msg);
                    // 添加重试按钮
                    sUpdateDialog.setButton(AlertDialog.BUTTON_POSITIVE, "重试",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                // 恢复消息
                                sUpdateDialog.setMessage("正在重新下载...");
                                // 隐藏按钮
                                android.view.View btn = sUpdateDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                                if (btn != null) btn.setVisibility(android.view.View.GONE);
                                downloadAndInstall(activity, sLastDownloadUrl);
                            }
                        });
                } else {
                    // 弹窗已消失，重新创建
                    new AlertDialog.Builder(activity)
                        .setTitle("安装失败")
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton("重试", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                downloadAndInstall(activity, sLastDownloadUrl);
                            }
                        })
                        .show();
                }
            }
        });
    }

    /**
     * 下载失败弹窗——弹窗保持，更新内容为错误信息
     */
    private static void showDownloadError(final Activity activity, final String msg) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String errorMsg = "下载错误: " + msg + "\n\n请检查网络连接后点击 [重试]";
                if (sUpdateDialog != null && sUpdateDialog.isShowing()) {
                    sUpdateDialog.setMessage(errorMsg);
                    // 添加重试按钮
                    sUpdateDialog.setButton(AlertDialog.BUTTON_POSITIVE, "重试",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                sUpdateDialog.setMessage("正在重新下载...");
                                android.view.View btn = sUpdateDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                                if (btn != null) btn.setVisibility(android.view.View.GONE);
                                downloadAndInstall(activity, sLastDownloadUrl);
                            }
                        });
                } else {
                    new AlertDialog.Builder(activity)
                        .setTitle("更新失败")
                        .setMessage(errorMsg)
                        .setCancelable(false)
                        .setPositiveButton("重试", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                downloadAndInstall(activity, sLastDownloadUrl);
                            }
                        })
                        .show();
                }
            }
        });
    }

    /**
     * 通用错误弹窗
     */
    private static void showErrorDialog(final Activity activity, final String msg, final Runnable retryAction) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setTitle("更新失败")
                    .setMessage("错误: " + msg)
                    .setCancelable(false);
                if (retryAction != null) {
                    builder.setPositiveButton("重试", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            retryAction.run();
                        }
                    });
                } else {
                    builder.setPositiveButton("确定", null);
                }
                AlertDialog errorDialog = builder.show();
                errorDialog.setCanceledOnTouchOutside(false);
            }
        });
    }

    /**
     * 打开系统安装器，返回 true 表示成功打开
     * 使用与 tryOpenInstaller 相同的尝试逻辑
     */
    private static boolean openInstaller(Activity activity, File apkFile) {
        return tryOpenInstallerAll(activity, apkFile);
    }
}
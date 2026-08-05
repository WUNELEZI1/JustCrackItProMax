package com.jck.promax;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行时权限请求工具
 * 
 * 在 Android 6.0 (API 23) 以上，危险权限需要运行时动态申请。
 * 本类统一管理所有运行时权限请求，避免代码分散在各 Activity 中。
 */
public class PermissionHelper {

    // ==================== 需要运行时申请的权限（危险权限组） ====================
    public static final int REQ_STORAGE = 1001;
    public static final int REQ_LOCATION = 1002;
    public static final int REQ_PHONE_STATE = 1003;
    public static final int REQ_WIFI = 1004;
    public static final int REQ_BLUETOOTH = 1005;
    public static final int REQ_ALL = 2000;

    /** 需要动态申请的危险权限集合 */
    public static final String[] DANGEROUS_PERMS = {
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.CHANGE_WIFI_STATE,
        android.Manifest.permission.BLUETOOTH,
        android.Manifest.permission.BLUETOOTH_ADMIN,
    };

    /**
     * 检查并请求所有危险权限
     * 返回 true 表示已全部授权，false 表示需要弹窗请求
     */
    public static boolean checkAndRequestAll(Activity activity) {
        if (Build.VERSION.SDK_INT < 23) return true; // 6.0以下自动授权

        List<String> needRequest = new ArrayList<>();
        for (String perm : DANGEROUS_PERMS) {
            if (activity.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                needRequest.add(perm);
            }
        }

        if (needRequest.isEmpty()) return true;

        activity.requestPermissions(needRequest.toArray(new String[0]), REQ_ALL);
        return false;
    }

    /**
     * 检查并请求存储权限
     */
    public static boolean checkStorage(Activity activity) {
        if (Build.VERSION.SDK_INT < 23) return true;
        if (activity.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) return true;
        activity.requestPermissions(
            new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                         android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
            REQ_STORAGE);
        return false;
    }

    /**
     * 检查 WRITE_SETTINGS 特殊权限（需要跳转系统设置页面）
     */
    public static boolean checkWriteSettings(Activity activity) {
        if (Build.VERSION.SDK_INT < 23) return true;
        if (Settings.System.canWrite(activity)) return true;
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(intent);
        Toast.makeText(activity, "请在设置中允许修改系统设置", Toast.LENGTH_LONG).show();
        return false;
    }

    /**
     * 检查 SYSTEM_ALERT_WINDOW 特殊权限（悬浮窗）
     */
    public static boolean checkOverlay(Activity activity) {
        if (Build.VERSION.SDK_INT < 23) return true;
        if (Settings.canDrawOverlays(activity)) return true;
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(intent);
        Toast.makeText(activity, "请在设置中允许显示悬浮窗", Toast.LENGTH_LONG).show();
        return false;
    }

    /**
     * 检查 REQUEST_INSTALL_PACKAGES 特殊权限（安装未知来源）
     */
    public static boolean checkInstallPackages(Activity activity) {
        if (Build.VERSION.SDK_INT < 23) return true;
        if (Build.VERSION.SDK_INT >= 26) {
            if (activity.getPackageManager().canRequestPackageInstalls()) return true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            Toast.makeText(activity, "请在设置中允许安装未知来源应用", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    /**
     * 处理权限请求结果——在 Activity.onRequestPermissionsResult 中调用
     * 返回 true 表示全部授权，false 表示有权限被拒绝
     */
    public static boolean handleResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_ALL || requestCode == REQ_STORAGE
            || requestCode == REQ_LOCATION || requestCode == REQ_PHONE_STATE
            || requestCode == REQ_WIFI || requestCode == REQ_BLUETOOTH) {
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 检查是否有 ROOT 权限
     */
    public static boolean checkRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "echo root_ok"});
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            p.destroy();
            return "root_ok".equals(line);
        } catch (Exception e) {
            return false;
        }
    }
}
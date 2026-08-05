package com.jck.promax;

import android.app.Activity;
import android.content.SharedPreferences;

public class Prefs {
    private static final String NAME = Obfuscator.dec(
        new byte[]{(byte)0xd6,(byte)0xdf,(byte)0xd7,(byte)0xe3,(byte)0xdf,(byte)0xd3,(byte)0xd2,(byte)0xda,(byte)0xd5,(byte)0xdb}, (byte)0xbc);
    private SharedPreferences sp;

    public Prefs(Activity ctx) {
        sp = ctx.getSharedPreferences(NAME, Activity.MODE_PRIVATE);
    }

    public void setSN(String sn) {
        sp.edit().putString("sn", sn).apply();
    }

    public String getSN() {
        return sp.getString("sn", "");
    }

    // ==================== 合规SN历史 ====================
    public void addValidSN(String sn) {
        if (sn == null || sn.isEmpty()) return;
        String current = sp.getString("valid_sn_history", "");
        if (current.contains(sn)) {
            String[] parts = current.split("\\|");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (!p.equals(sn)) {
                    if (sb.length() > 0) sb.append("|");
                    sb.append(p);
                }
            }
            current = sb.toString();
        }
        String newHist = current.isEmpty() ? sn : sn + "|" + current;
        if (newHist.length() > 300) newHist = newHist.substring(0, 300);
        sp.edit().putString("valid_sn_history", newHist).apply();
    }

    public String[] getValidSNHistory() {
        String current = sp.getString("valid_sn_history", "");
        if (current.isEmpty()) return new String[0];
        return current.split("\\|");
    }

    public void clearValidSNHistory() {
        sp.edit().remove("valid_sn_history").apply();
    }

    // ==================== 全局日志 ====================
    public void appendGlobalLog(String msg) {
        String current = sp.getString("global_log", "");
        String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        String line = "[" + time + "] " + msg + "\n";
        String newLog = line + current;
        if (newLog.length() > 20000) newLog = newLog.substring(0, 20000);
        sp.edit().putString("global_log", newLog).apply();
    }

    public String getGlobalLog() {
        return sp.getString("global_log", "");
    }

    public void clearGlobalLog() {
        sp.edit().remove("global_log").apply();
    }

    // ==================== 排序设置 ====================
    public void setSortMode(int mode) {
        sp.edit().putInt("sort_mode", mode).apply();
    }

    public int getSortMode() {
        return sp.getInt("sort_mode", 0);
    }

    // ==================== 应用缓存 ====================
    public void saveAppCache(String json) {
        sp.edit().putString("app_cache", json).apply();
    }

    public String getAppCache() {
        return sp.getString("app_cache", "");
    }

    public void clearAppCache() {
        sp.edit().remove("app_cache").apply();
    }

    public long getAppCacheTime() {
        return sp.getLong("app_cache_time", 0);
    }

    public void setAppCacheTime(long time) {
        sp.edit().putLong("app_cache_time", time).apply();
    }

    // ==================== 搜索历史 ====================
    public void addSearchHistory(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return;
        String current = sp.getString("search_history", "");
        String[] parts = current.isEmpty() ? new String[0] : current.split("\\|");
        StringBuilder sb = new StringBuilder(keyword);
        for (String p : parts) {
            if (!p.equals(keyword) && !p.isEmpty()) {
                sb.append("|").append(p);
            }
        }
        String result = sb.toString();
        if (result.length() > 200) result = result.substring(0, 200);
        sp.edit().putString("search_history", result).apply();
    }

    public String[] getSearchHistory() {
        String current = sp.getString("search_history", "");
        if (current.isEmpty()) return new String[0];
        return current.split("\\|");
    }

    public void clearSearchHistory() {
        sp.edit().remove("search_history").apply();
    }

    // ==================== 安装记录 ====================
    public void addInstallRecord(int appId, String appName, boolean success, String msg) {
        String current = sp.getString("install_history", "");
        String ts = String.valueOf(System.currentTimeMillis());
        String entry = ts + "#" + appId + "#" + appName + "#" + (success ? "1" : "0") + "#" + msg;
        String newHist = current.isEmpty() ? entry : entry + "|" + current;
        if (newHist.length() > 5000) newHist = newHist.substring(0, 5000);
        sp.edit().putString("install_history", newHist).apply();
    }

    public InstallRecord[] getInstallHistory() {
        String current = sp.getString("install_history", "");
        if (current.isEmpty()) return new InstallRecord[0];
        String[] parts = current.split("\\|");
        InstallRecord[] records = new InstallRecord[parts.length];
        for (int i = 0; i < parts.length; i++) {
            records[i] = InstallRecord.fromString(parts[i]);
        }
        return records;
    }

    public void clearInstallHistory() {
        sp.edit().remove("install_history").apply();
    }

    // ==================== 收藏 ====================
    // 格式: appId#appName#icon#developer#apkSize
    public void addFavorite(int appId, String appName, String icon, String developer, long apkSize) {
        String current = sp.getString("favorites", "");
        String entry = appId + "#" + appName + "#" + icon + "#" + developer + "#" + apkSize;
        if (current.contains(appId + "#")) {
            String[] parts = current.split("\\|");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (!p.startsWith(appId + "#")) {
                    if (sb.length() > 0) sb.append("|");
                    sb.append(p);
                }
            }
            current = sb.toString();
        }
        String newFav = current.isEmpty() ? entry : entry + "|" + current;
        if (newFav.length() > 6000) newFav = newFav.substring(0, 6000);
        sp.edit().putString("favorites", newFav).apply();
    }

    public void removeFavorite(int appId) {
        String current = sp.getString("favorites", "");
        if (current.isEmpty()) return;
        String[] parts = current.split("\\|");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.startsWith(appId + "#")) {
                if (sb.length() > 0) sb.append("|");
                sb.append(p);
            }
        }
        sp.edit().putString("favorites", sb.toString()).apply();
    }

    public boolean isFavorite(int appId) {
        String current = sp.getString("favorites", "");
        return current.contains(appId + "#");
    }

    public FavoriteItem[] getFavorites() {
        String current = sp.getString("favorites", "");
        if (current.isEmpty()) return new FavoriteItem[0];
        String[] parts = current.split("\\|");
        FavoriteItem[] items = new FavoriteItem[parts.length];
        for (int i = 0; i < parts.length; i++) {
            items[i] = FavoriteItem.fromString(parts[i]);
        }
        return items;
    }

    // ==================== 最近浏览 ====================
    // 格式: appId#appName#icon#developer#apkSize
    public void addRecentView(int appId, String appName, String icon, String developer, long apkSize) {
        String current = sp.getString("recent_views", "");
        String entry = appId + "#" + appName + "#" + icon + "#" + developer + "#" + apkSize;
        if (current.contains(appId + "#")) {
            String[] parts = current.split("\\|");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (!p.startsWith(appId + "#")) {
                    if (sb.length() > 0) sb.append("|");
                    sb.append(p);
                }
            }
            current = sb.toString();
        }
        String newRec = current.isEmpty() ? entry : entry + "|" + current;
        if (newRec.length() > 5000) newRec = newRec.substring(0, 5000);
        sp.edit().putString("recent_views", newRec).apply();
    }

    public FavoriteItem[] getRecentViews() {
        String current = sp.getString("recent_views", "");
        if (current.isEmpty()) return new FavoriteItem[0];
        String[] parts = current.split("\\|");
        FavoriteItem[] items = new FavoriteItem[Math.min(parts.length, 20)];
        for (int i = 0; i < items.length; i++) {
            items[i] = FavoriteItem.fromString(parts[i]);
        }
        return items;
    }

    // ==================== 主题 ====================
    public void setTheme(String theme) {
        sp.edit().putString("app_theme", theme).apply();
    }

    public String getTheme() {
        return sp.getString("app_theme", "light");
    }

    // ==================== API模式 ====================
    // "auto" = 自动(主API优先，失败回退备用), "main" = 仅主API, "backup" = 仅备用API
    public void setApiMode(String mode) {
        sp.edit().putString("api_mode", mode).apply();
    }

    public String getApiMode() {
        return sp.getString("api_mode", "auto");
    }

    // ==================== 数据类 ====================
    public static class InstallRecord {
        public long time;
        public int appId;
        public String appName;
        public boolean success;
        public String msg;

        public static InstallRecord fromString(String s) {
            InstallRecord r = new InstallRecord();
            String[] p = s.split("#", 5);
            if (p.length >= 1) try { r.time = Long.parseLong(p[0]); } catch (Exception e) {}
            if (p.length >= 2) try { r.appId = Integer.parseInt(p[1]); } catch (Exception e) {}
            if (p.length >= 3) r.appName = p[2];
            if (p.length >= 4) r.success = p[3].equals("1");
            if (p.length >= 5) r.msg = p[4];
            return r;
        }
    }

    public static class FavoriteItem {
        public int appId;
        public String appName;
        public String icon;
        public String developer;
        public long apkSize;

        public static FavoriteItem fromString(String s) {
            FavoriteItem f = new FavoriteItem();
            String[] p = s.split("#", 5);
            if (p.length >= 1) try { f.appId = Integer.parseInt(p[0]); } catch (Exception e) {}
            if (p.length >= 2) f.appName = p[1];
            if (p.length >= 3) f.icon = p[2];
            if (p.length >= 4) f.developer = p[3];
            if (p.length >= 5) try { f.apkSize = Long.parseLong(p[4]); } catch (Exception e) {}
            return f;
        }
    }
}

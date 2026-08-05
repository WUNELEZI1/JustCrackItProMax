package com.jck.promax;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AppModel {
    public int appId;
    public String name = "";
    public String enName = "";
    public String summary = "";
    public String remark = "";
    public String icon = "";
    public String apkName = "";
    public String apkVersion = "";
    public String developer = "";
    public long apkSize = 0;
    public String changeLog = "";
    public String uploadTime = "";
    public String icpNumber = "";
    public List<String> permissions = new ArrayList<>();
    public List<String> tags = new ArrayList<>();

    public static AppModel fromJson(JSONObject item) {
        AppModel m = new AppModel();
        m.appId = item.optInt("id", item.optInt("appId", 0));
        m.name = item.optString("name", item.optString("appName", "未知"));
        m.enName = item.optString("enName", "");
        m.summary = item.optString("summary", "");
        m.remark = item.optString("remark", "");
        m.icon = item.optString("icon", item.optString("iconUrl", ""));
        m.apkName = item.optString("apkName", item.optString("packageName", item.optString("pkg", "")));
        m.apkVersion = item.optString("apkVersion", item.optString("version", ""));
        m.developer = item.optString("developer", item.optString("dev", ""));
        m.apkSize = item.optLong("apkSize", item.optLong("size", 0));
        m.changeLog = item.optString("changeLog", "");
        m.uploadTime = item.optString("uploadTime", "");
        m.icpNumber = item.optString("icpNumber", "");

        JSONArray perms = item.optJSONArray("permissions");
        if (perms != null) {
            for (int i = 0; i < perms.length(); i++) {
                Object p = perms.opt(i);
                if (p instanceof String) {
                    m.permissions.add((String) p);
                } else if (p instanceof JSONObject) {
                    JSONObject po = (JSONObject) p;
                    m.permissions.add(po.optString("name", po.optString("permission", "")));
                }
            }
        }

        JSONArray tagsArr = item.optJSONArray("tags");
        if (tagsArr != null) {
            for (int i = 0; i < tagsArr.length(); i++) {
                Object t = tagsArr.opt(i);
                if (t instanceof String) {
                    m.tags.add((String) t);
                } else if (t instanceof JSONObject) {
                    JSONObject to = (JSONObject) t;
                    m.tags.add(to.optString("name", to.optString("tag", "")));
                }
            }
        }
        return m;
    }

    public String getSizeDisplay() {
        if (apkSize <= 0) return "未知";
        double mb = apkSize / (1024.0 * 1024.0);
        if (mb >= 1024) {
            return String.format("%.2f GB", mb / 1024);
        }
        return String.format("%.1f MB", mb);
    }

    public String getUploadDateDisplay() {
        if (uploadTime == null || uploadTime.isEmpty()) return "未知";
        // 尝试解析时间戳或日期字符串
        try {
            if (uploadTime.matches("\\d+")) {
                long ts = Long.parseLong(uploadTime);
                if (ts < 1e12) ts *= 1000;
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                return sdf.format(new java.util.Date(ts));
            }
            return uploadTime.length() > 10 ? uploadTime.substring(0, 10) : uploadTime;
        } catch (Exception e) {
            return uploadTime;
        }
    }
}

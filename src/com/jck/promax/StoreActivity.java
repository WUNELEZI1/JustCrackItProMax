package com.jck.promax;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;

public class StoreActivity extends Activity {

    private static final int MODE_STORE = 0;
    private static final int MODE_FAVORITE = 1;
    private static final int MODE_RECENT = 2;

    private static final int SORT_DEFAULT = 0;
    private static final int SORT_NAME = 1;
    private static final int SORT_SIZE = 2;
    private static final int SORT_TIME = 3;

    private static final int SEARCH_THREADS = 12;
    private static final int SEARCH_PAGES = 5; // 每个关键词搜索5页
    private static final long CACHE_VALID_MS = 30 * 60 * 1000; // 缓存有效期30分钟

    // 分类定义
    private static final String[] CATEGORIES = {Obfuscator.dec(new byte[]{(byte)0xb1, (byte)0xd1, (byte)0xfc, (byte)0xbd, (byte)0xd7, (byte)0xfc}, (byte)0x54), Obfuscator.dec(new byte[]{(byte)0xd2, (byte)0xa1, (byte)0xad, (byte)0xdc, (byte)0xb6, (byte)0x86}, (byte)0x34), Obfuscator.dec(new byte[]{(byte)0xb5, (byte)0xe7, (byte)0xf5, (byte)0xb5, (byte)0xd5, (byte)0xe7}, (byte)0x50), Obfuscator.dec(new byte[]{(byte)0x28, (byte)0x6b, (byte)0x71, (byte)0x2b, (byte)0x75, (byte)0x6b}, (byte)0xcf), Obfuscator.dec(new byte[]{(byte)0x45, (byte)0x08, (byte)0x11, (byte)0x44, (byte)0x19, (byte)0x30}, (byte)0xa0), Obfuscator.dec(new byte[]{(byte)0x7d, (byte)0x0e, (byte)0x05, (byte)0x7c, (byte)0x2e, (byte)0x21}, (byte)0x9a), Obfuscator.dec(new byte[]{(byte)0xb3, (byte)0xe7, (byte)0xef, (byte)0xb3, (byte)0xef, (byte)0xcb}, (byte)0x54)};
    private static final String[][] CATEGORY_KEYWORDS = {
        {}, // 全部 - 使用所有关键词
        // 教育
        {"学", "课", "题", "作", "数", "语", "英", "物", "化", "生", "地", "历", "政", "教", "考", "练", "辅", "导", "识", "知", "单词", "口语", "阅读", "听书", "笔记", "作业", "辅导", "题库", "错题", "作文", "古诗", "拼音", "识字", "算术", "口算"},
        // 工具
        {"微", "Q", "豆", "百", "搜", "词", "翻", "计", "阅", "听", "音", "视", "键", "输", "法", "日", "天", "文", "清", "浏", "下", "直", "银", "健", "运", "效", "办", "口", "单", "写", "读", "拼", "记", "笔", "扫", "录", "印", "打", "计算", "翻译", "输入", "文件", "清理", "浏览器", "下载", "天气", "效率", "办公", "笔记", "扫描", "录音", "日历", "时钟", "闹钟", "备忘", "提醒"},
        // 社交
        {"聊", "社", "交", "新", "闻", "微信", "QQ", "微博", "知乎", "贴吧", "豆瓣", "聊天", "通讯", "消息", "朋友圈", "社区", "论坛", "群聊", "视频通话", "语音通话"},
        // 娱乐
        {"乐", "玩", "影", "图", "照", "画", "剪", "编", "美", "修", "拍", "摄", "动", "漫", "小", "说", "故", "事", "音乐", "视频", "游戏", "直播", "电影", "电视", "动漫", "小说", "故事", "电台", "播客", "K歌", "短视频", "相册", "美图", "摄影", "绘画", "剪辑"},
        // 生活
        {"购", "买", "卖", "店", "商", "吃", "喝", "财", "股", "基", "理", "购", "购物", "银行", "健康", "运动", "美食", "外卖", "出行", "地图", "导航", "酒店", "旅游", "机票", "火车", "公交", "打车", "快递", "记账", "理财", "保险", "股票", "基金"},
        // 系统
        {"系", "统", "设", "置", "权", "限", "root", "magisk", "xposed", "管理", "优化", "加速", "省电", "备份", "恢复", "还原", "刷机", "root", "权限", "开发者", "调试", "adb", "终端", "模拟器"}
    };

    // 全量关键词 - 500+关键词覆盖全品类
    private static final String[] SEARCH_KEYWORDS = {
        // 教育学科 (60)
        "学", "课", "题", "作", "数", "语", "英", "物", "化", "生",
        "地", "历", "政", "教", "考", "练", "辅", "导", "识", "知",
        "单词", "口语", "阅读", "听书", "笔记", "作业", "辅导", "题库", "错题", "作文",
        "古诗", "拼音", "识字", "算术", "口算", "英语", "数学", "语文", "物理", "化学",
        "生物", "地理", "历史", "政治", "科学", "编程", "奥数", "几何", "代数", "分数",
        "复习", "预习", "考试", "测验", "试卷", "真题", "模拟", "训练", "启蒙", "早教",
        "胎教", "幼教", "小学", "初中", "高中", "大学", "考研", "公考", "公务员", "资格",
        // 工具效率 (70)
        "微", "Q", "豆", "百", "搜", "词", "翻", "计", "阅", "听",
        "音", "视", "键", "输", "法", "日", "天", "文", "清", "浏",
        "下", "直", "银", "健", "运", "效", "办", "口", "单", "写",
        "读", "拼", "记", "笔", "扫", "录", "印", "打", "设", "置",
        "计算", "翻译", "输入", "文件", "清理", "浏览器", "下载", "天气", "效率", "办公",
        "日历", "时钟", "闹钟", "备忘", "提醒", "密码", "锁", "加密", "解压", "压缩",
        "PDF", "WPS", "Excel", "Word", "文档", "表格", "幻灯", "扫描", "证件", "名片",
        "测量", "水平仪", "尺子", "手电筒", "镜子", "放大镜", "计算器", "单位", "汇率", "万年历",
        // 社交 (30)
        "聊", "社", "交", "新", "闻", "微信", "QQ", "微博", "知乎", "贴吧",
        "豆瓣", "聊天", "通讯", "消息", "朋友圈", "社区", "论坛", "群聊", "通话", "联系",
        "陌陌", "探探", "Soul", "钉钉", "飞书", "企业微信", " Telegram", "Signal", "邮件", "邮箱",
        "Outlook", "Gmail", "网易邮箱", "QQ邮箱", "126", "163", "搜狐", "新浪", "今日头条", "腾讯",
        // 娱乐 (70)
        "乐", "玩", "影", "图", "照", "画", "剪", "编", "美", "修",
        "拍", "摄", "动", "漫", "说", "故", "事", "音乐", "视频", "游戏",
        "直播", "电影", "电视", "动漫", "小说", "电台", "播客", "短视频", "相册", "美图",
        "摄影", "绘画", "剪辑", "K歌", "弹幕", "追剧", "影视", "铃声", "壁纸", "表情",
        "王者荣耀", "和平精英", "原神", "我的世界", "迷你世界", "植物大战僵尸", "开心消消乐", "斗地主", "麻将", "象棋",
        "五子棋", "围棋", "军棋", "跳棋", "扑克", "牌", "桌游", "益智", "休闲", "竞技",
        "角色扮演", "策略", "模拟", "射击", "格斗", "赛车", "体育", "冒险", "解谜", "生存",
        "沙盒", "塔防", "卡牌", "养成", "挂机", "放置", "io", "多人", "联机", "单机",
        // 生活 (70)
        "购", "买", "卖", "店", "商", "吃", "喝", "财", "股", "基",
        "理", "购物", "银行", "健康", "运动", "美食", "外卖", "出行", "地图", "导航",
        "酒店", "旅游", "机票", "火车", "公交", "打车", "快递", "记账", "理财", "保险",
        "股票", "基金", "日历", "时钟", "闹钟", "备忘", "提醒", "汇率", "万年历", "节气",
        "淘宝", "京东", "拼多多", "天猫", "苏宁", "唯品会", "闲鱼", "转转", "美团", "饿了么",
        "大众点评", "携程", "去哪儿", "飞猪", "12306", "高德", "百度地图", "滴滴", "哈啰", "共享单车",
        "菜鸟", "顺丰", "韵达", "中通", "圆通", "申通", "京东物流", "德邦", "医疗", "挂号",
        "问诊", "买药", "健身", "跑步", "瑜伽", "减肥", "卡路里", "睡眠", "饮水", "步数",
        // 系统工具 (40)
        "系", "统", "权", "限", "管理", "优化", "加速", "省电", "备份", "恢复",
        "还原", "刷机", "开发者", "调试", "终端", "模拟器", "多开", "分身", "应用锁", "隐私",
        "root", "magisk", "xposed", "adb", "busybox", "终端", "shell", "命令", "脚本", "自动化",
        "任务", "定时", "快捷", "手势", "悬浮窗", "通知", "状态栏", "导航栏", "桌面", " launcher",
        "主题", "图标", "字体", "动态壁纸", "引擎", "驱动", "内核", "性能", "监控", "电池",
        // 英文 (50)
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
        "k", "l", "m", "n", "o", "p", "q", "r", "s", "t",
        "app", "game", "tool", "book", "learn", "chat", "news", "shop", "music", "video",
        "player", "editor", "camera", "photo", "browser", "manager", "launcher", "widget", "theme", "font",
        "vpn", "proxy", "dns", "host", "adblock", "firewall", "monitor", "tester", "debugger", "root",
        // 单字扩展 (60) - 覆盖更多单字以匹配更多应用
        "网", "云", "盘", "存", "储", "同步", "备", "档", "资", "源",
        "码", "序", "程", "软", "硬", "配", "网", "络", "连", "接",
        "蓝", "牙", "WIFI", "热点", "VPN", "代理", "加", "密", "安", "全",
        "杀", "毒", "防", "护", "隐", "私", "权", "限", "监", "控",
        "录", "屏", "截", "图", "投", "屏", "远", "程", "控", "制",
        "按", "键", "浮", "窗", "悬", "浮", "小", "工", "具", "箱",
        "万", "能", "超", "级", "终", "极", "全", "能", "一", "站",
        "便", "携", "轻", "量", "极", "简", "快", "速", "高", "效",
        // 学习机专属 (30)
        "作业帮", "猿辅导", "学而思", "好未来", "新东方", "斑马", "瓜瓜龙", "小猿搜题", "题拍拍", "阿凡题",
        "洋葱学院", "作业帮直播课", "掌门1对1", "VIPKID", "哒哒英语", "51Talk", "英语流利说", "百词斩", "扇贝", "墨墨",
        "得到", "知乎", "喜马拉雅", "蜻蜓FM", "荔枝微课", "千聊", "腾讯课堂", "网易云课堂", "中国大学MOOC", "学堂在线",
        // 更多热门应用名 (30)
        "抖音", "快手", "小红书", "B站", "哔哩哔哩", "西瓜视频", "皮皮虾", "懂车帝", "汽车之家", "易车",
        "贝壳找房", "链家", "安居客", "58同城", "智联招聘", "BOSS直聘", "前程无忧", "拉勾", "猎聘", "脉脉",
        "百度网盘", "阿里云盘", "夸克", "迅雷", "IDM", "ADM", "磁力", "种子", "BT", "云播",
        // 更多单字 (30)
        "梦", "想", "创", "意", "设", "计", "UI", "UX", "3D", "VR",
        "AR", "AI", "ML", "DL", "NN", "GPT", "LLM", "大模型", "智能", "智慧",
        "家", "居", "物", "联", "IOT", "智", "能", "穿", "戴", "手",
        "表", "环", "带", "眼", "镜", "盒", "子", "棒", "贴", "纸"
    };

    private String sn = "";
    private EditText searchInput;
    private Button searchBtn;
    private Button backBtn;
    private Button menuBtn;
    private Button clearHistoryBtn;
    private Button filterStore;
    private Button filterFav;
    private Button filterRecent;
    private Button sortBtn;
    private Button categoryBtn;
    private Button batchBtn;
    private Button batchInstallBtn;
    private Button batchCancelBtn;
    private LinearLayout appGridContainer;
    private ScrollView contentScroll;
    private Button loadMoreBtn;
    private ProgressBar loadingBar;
    private TextView emptyText;
    private TextView allTitle;
    private TextView statsText;
    private LinearLayout historyBar;
    private LinearLayout historyContainer;
    private LinearLayout recommendBar;
    private LinearLayout recommendContainer;
    private LinearLayout categoryBar;
    private LinearLayout categoryContainer;
    private LinearLayout batchBar;
    private TextView batchCountText;
    private LinearLayout logPanel;
    private TextView logToggle;
    private ScrollView logScroll;
    private TextView logText;
    private boolean logExpanded = false;
    private String currentApiMode = Obfuscator.dec(new byte[]{(byte)0xb1, (byte)0xa5, (byte)0xa4, (byte)0xbf}, (byte)0xd0);
    private Prefs prefs;

    // SN 绑定相关
    private EditText snInput;
    private Button bindBtn;
    private boolean snBound = false;

    private final ArrayList<AppModel> appList = new ArrayList<>();
    private final ArrayList<AppModel> allCachedApps = new ArrayList<>(); // 全部已发现应用（未筛选）
    private final Set<Integer> selectedAppIds = new HashSet<>();
    private boolean batchMode = false;
    private int currentCategory = 0; // 0=全部
    private final StringBuffer fullLog = new StringBuffer();
    private int currentMode = MODE_STORE;
    private boolean isLoading = false;
    private java.util.concurrent.ExecutorService probeExecutor;

    // 分页相关
    private static final int PAGE_SIZE = 30; // 每页显示30个应用
    private int displayedCount = 0; // 当前已显示的应用数量

    // UI节流：避免搜索过程中频繁重建网格导致卡顿
    private android.os.Handler uiHandler = new android.os.Handler();
    private boolean uiUpdatePending = false;
    private int pendingNewApps = 0;
    private static final int UI_UPDATE_INTERVAL_MS = 1500; // 搜索中每1.5秒最多刷新一次UI

    // 搜索防抖
    private Runnable searchDebounceRunnable;
    private static final int SEARCH_DEBOUNCE_MS = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);

        prefs = new Prefs(this);
        currentApiMode = prefs.getApiMode();

        searchInput = findViewById(R.id.searchInput);
        searchBtn = findViewById(R.id.searchBtn);
        backBtn = findViewById(R.id.backBtn);
        menuBtn = findViewById(R.id.menuBtn);
        clearHistoryBtn = findViewById(R.id.clearHistoryBtn);
        filterStore = findViewById(R.id.filterStore);
        filterFav = findViewById(R.id.filterFav);
        filterRecent = findViewById(R.id.filterRecent);
        sortBtn = findViewById(R.id.sortBtn);
        categoryBtn = findViewById(R.id.categoryBtn);
        batchBtn = findViewById(R.id.batchBtn);
        batchInstallBtn = findViewById(R.id.batchInstallBtn);
        batchCancelBtn = findViewById(R.id.batchCancelBtn);
        appGridContainer = findViewById(R.id.appGridContainer);
        contentScroll = findViewById(R.id.contentScroll);
        loadMoreBtn = findViewById(R.id.loadMoreBtn);
        loadingBar = findViewById(R.id.loadingBar);
        emptyText = findViewById(R.id.emptyText);
        allTitle = findViewById(R.id.allTitle);
        statsText = findViewById(R.id.statsText);
        historyBar = findViewById(R.id.historyBar);
        historyContainer = findViewById(R.id.historyContainer);
        recommendBar = findViewById(R.id.recommendBar);
        recommendContainer = findViewById(R.id.recommendContainer);
        categoryBar = findViewById(R.id.categoryBar);
        categoryContainer = findViewById(R.id.categoryContainer);
        batchBar = findViewById(R.id.batchBar);
        batchCountText = findViewById(R.id.batchCountText);

        // 日志面板
        logPanel = findViewById(R.id.logPanel);
        logToggle = findViewById(R.id.logToggle);
        logScroll = findViewById(R.id.logScroll);
        logText = findViewById(R.id.logText);
        logToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logExpanded = !logExpanded;
                if (logExpanded) {
                    logScroll.setVisibility(View.VISIBLE);
                    logToggle.setText("执行日志 ▲");
                } else {
                    logScroll.setVisibility(View.GONE);
                    logToggle.setText("执行日志 ▼");
                }
            }
        });

        snInput = findViewById(R.id.snInput);
        bindBtn = findViewById(R.id.bindBtn);

        // 加载保存的SN或从Intent获取
        String intentSN = getIntent().getStringExtra("sn");
        String savedSN = prefs.getSN();
        if (intentSN != null && !intentSN.isEmpty() && intentSN.matches("\\d{18}")) {
            snInput.setText(intentSN);
        } else if (!savedSN.isEmpty()) {
            snInput.setText(savedSN);
        }

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doSearch(); }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().contains("\n")) {
                    String clean = s.toString().replace("\n", "");
                    searchInput.setText(clean);
                    searchInput.setSelection(clean.length());
                    doSearch();
                    return;
                }
                // 搜索防抖：用户输入后延迟500ms再执行搜索
                if (searchDebounceRunnable != null) {
                    uiHandler.removeCallbacks(searchDebounceRunnable);
                }
                searchDebounceRunnable = new Runnable() {
                    @Override
                    public void run() {
                        String text = searchInput.getText().toString().trim();
                        if (!text.isEmpty()) {
                            doSearch();
                        }
                    }
                };
                uiHandler.postDelayed(searchDebounceRunnable, SEARCH_DEBOUNCE_MS);
            }
        });

        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenuDialog();
            }
        });

        clearHistoryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.clearSearchHistory();
                renderHistory();
            }
        });

        filterStore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setMode(MODE_STORE); }
        });
        filterFav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setMode(MODE_FAVORITE); }
        });
        filterRecent.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setMode(MODE_RECENT); }
        });

        sortBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSortDialog(); }
        });

        categoryBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCategoryDialog(); }
        });

        batchBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleBatchMode(); }
        });

        batchInstallBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startBatchInstall(); }
        });

        batchCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exitBatchMode(); }
        });

        // 加载更多按钮
        loadMoreBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadMoreApps(); }
        });

        // SN 输入框点击显示历史
        snInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSNHistoryDialog();
            }
        });

        // 绑定按钮
        bindBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doBindSN();
            }
        });

        // 下拉刷新 + 自动加载更多（基于ScrollView）
        contentScroll.setOnTouchListener(new View.OnTouchListener() {
            private float startY = 0;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        break;
                    case MotionEvent.ACTION_UP:
                        // 下拉刷新：在顶部下拉
                        if (contentScroll.getScrollY() == 0 && currentMode == MODE_STORE && !isLoading && snBound) {
                            if (event.getY() - startY > 200) {
                                appendLog("↓ 下拉刷新触发");
                                loadAllApps();
                                Toast.makeText(StoreActivity.this, "刷新中...", Toast.LENGTH_SHORT).show();
                            }
                        }
                        // 滚动到底部自动加载更多
                        if (currentMode == MODE_STORE && !isLoading) {
                            View contentView = contentScroll.getChildAt(0);
                            if (contentView != null) {
                                int scrollBottom = contentScroll.getScrollY() + contentScroll.getHeight();
                                int contentHeight = contentView.getMeasuredHeight();
                                if (scrollBottom >= contentHeight - 100 && displayedCount < appList.size()) {
                                    loadMoreApps();
                                }
                            }
                        }
                        break;
                }
                return false;
            }
        });

        appendLog("商店已启动 v6.9");
        renderHistory();
        renderCategoryBar();

        // 初始化高级功能按钮状态
        batchBar.setVisibility(View.GONE);
        sortBtn.setVisibility(View.GONE);
        categoryBtn.setVisibility(View.GONE);
        batchBtn.setVisibility(View.GONE);
        categoryBar.setVisibility(View.GONE);
        if (statsText != null) statsText.setVisibility(View.GONE);

        // 每次进入都需要手动绑定，不自动绑定
        snBound = false;
        emptyText.setText("请输入SN并点击绑定");
        emptyText.setVisibility(View.VISIBLE);
        appGridContainer.setVisibility(View.GONE);
        loadMoreBtn.setVisibility(View.GONE);
        recommendBar.setVisibility(View.GONE);
        allTitle.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentMode == MODE_FAVORITE) {
            loadFavorites();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (probeExecutor != null) {
            probeExecutor.shutdownNow();
        }
        uiHandler.removeCallbacksAndMessages(null);
        // 清除安装标记，防止内存泄漏
        ApiHelper.clearInstallingApps();
    }

    // ==================== SN 绑定 ====================
    private void showSNHistoryDialog() {
        final String[] history = prefs.getValidSNHistory();
        if (history.length == 0) return;

        final String[] items = new String[history.length + 1];
        for (int i = 0; i < history.length; i++) {
            items[i] = history[i];
        }
        items[history.length] = "清除历史";

        new AlertDialog.Builder(this)
            .setTitle("选择历史SN")
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    if (which == items.length - 1) {
                        prefs.clearValidSNHistory();
                        Toast.makeText(StoreActivity.this, "已清除历史", Toast.LENGTH_SHORT).show();
                    } else {
                        String selected = items[which];
                        snInput.setText(selected);
                        snInput.setSelection(selected.length());
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void doBindSN() {
        final String inputSN = snInput.getText().toString().trim();
        if (!inputSN.matches("\\d{18}")) {
            Toast.makeText(this, "SN必须为18位纯数字", Toast.LENGTH_SHORT).show();
            return;
        }

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("正在校验SN...");
        dialog.setCancelable(false);
        dialog.show();

        new AsyncTask<Void, Void, Boolean>() {
            String errMsg = "";

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    // SN封锁检查
                    if (ApiHelper.isSnBlocked(inputSN)) {
                        errMsg = "服务异常";
                        return false;
                    }

                    // 用搜索API校验SN是否有效
                    JSONObject param = new JSONObject();
                    param.put("sn", inputSN);
                    param.put("nameOfApp", "学");
                    param.put("size", 1);
                    param.put("page", 1);
                    LinkedHashMap<String, String> params = ApiHelper.buildParams(param, "/appStore/appInfoNoAuth");
                    String result = ApiHelper.doPost(ApiHelper.BASE_URL, params);

                    appendLog("SN校验返回: " + result.substring(0, Math.min(200, result.length())));

                    JSONObject outer = new JSONObject(result);
                    if (outer.optInt("code") == 200) {
                        JSONObject dataObj = outer.optJSONObject("data");
                        String resultStr = dataObj != null ? dataObj.optString("result", "") : "";
                        if (!resultStr.isEmpty()) {
                            JSONObject inner = new JSONObject(resultStr);
                            int errNo = inner.optInt("errNo", -1);
                            String msg = inner.optString("errMsg", "");
                            // errNo=0 说明SN有效且API正常
                            if (errNo == 0) {
                                return true;
                            }
                            // 检查是否是SN相关错误
                            if (msg.contains("SN") && (msg.contains("不存在") || msg.contains("无效") || msg.contains("错误"))) {
                                errMsg = msg;
                                return false;
                            }
                            // 其他错误（如lack query params）不算SN无效
                            return true;
                        }
                        return true;
                    } else {
                        errMsg = "请求失败 code=" + outer.optInt("code");
                        return false;
                    }
                } catch (Exception e) {
                    errMsg = "网络错误: " + e.getMessage();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean valid) {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }

                if (valid) {
                    sn = inputSN;
                    snBound = true;
                    prefs.setSN(sn);
                    prefs.addValidSN(sn);
                    appendLog("SN绑定成功: " + sn);
                    Toast.makeText(StoreActivity.this, "SN绑定成功", Toast.LENGTH_SHORT).show();
                    setMode(MODE_STORE);
                } else {
                    snBound = false;
                    appendLog("SN校验失败: " + errMsg);
                    Toast.makeText(StoreActivity.this, "SN校验失败: " + errMsg, Toast.LENGTH_LONG).show();
                    emptyText.setText("SN校验失败\n" + errMsg);
                    emptyText.setVisibility(View.VISIBLE);
                    appGridContainer.setVisibility(View.GONE);
        loadMoreBtn.setVisibility(View.GONE);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void setMode(int mode) {
        currentMode = mode;
        filterStore.setBackgroundResource(mode == MODE_STORE ? R.drawable.btn_primary_bg : R.drawable.input_bg);
        filterStore.setTextColor(getResources().getColor(mode == MODE_STORE ? R.color.md3_on_primary : R.color.md3_on_surface));
        filterFav.setBackgroundResource(mode == MODE_FAVORITE ? R.drawable.btn_primary_bg : R.drawable.input_bg);
        filterFav.setTextColor(getResources().getColor(mode == MODE_FAVORITE ? R.color.md3_on_primary : R.color.md3_on_surface));
        filterRecent.setBackgroundResource(mode == MODE_RECENT ? R.drawable.btn_primary_bg : R.drawable.input_bg);
        filterRecent.setTextColor(getResources().getColor(mode == MODE_RECENT ? R.color.md3_on_primary : R.color.md3_on_surface));

        // 退出批量模式
        if (batchMode) exitBatchMode();

        if (mode == MODE_STORE) {
            recommendBar.setVisibility(View.VISIBLE);
            allTitle.setVisibility(View.VISIBLE);
            allTitle.setText("全部应用");
            // 显示高级功能按钮
            sortBtn.setVisibility(View.VISIBLE);
            categoryBtn.setVisibility(View.VISIBLE);
            batchBtn.setVisibility(View.VISIBLE);
            categoryBar.setVisibility(View.VISIBLE);
            if (snBound) {
                loadAllApps();
                loadRecommends();
            } else {
                emptyText.setText("请先绑定SN");
                emptyText.setVisibility(View.VISIBLE);
                appGridContainer.setVisibility(View.GONE);
        loadMoreBtn.setVisibility(View.GONE);
            }
        } else if (mode == MODE_FAVORITE) {
            recommendBar.setVisibility(View.GONE);
            allTitle.setVisibility(View.GONE);
            sortBtn.setVisibility(View.GONE);
            categoryBtn.setVisibility(View.GONE);
            batchBtn.setVisibility(View.GONE);
            categoryBar.setVisibility(View.GONE);
            if (statsText != null) statsText.setVisibility(View.GONE);
            loadFavorites();
        } else if (mode == MODE_RECENT) {
            recommendBar.setVisibility(View.GONE);
            allTitle.setVisibility(View.GONE);
            sortBtn.setVisibility(View.GONE);
            categoryBtn.setVisibility(View.GONE);
            batchBtn.setVisibility(View.GONE);
            categoryBar.setVisibility(View.GONE);
            if (statsText != null) statsText.setVisibility(View.GONE);
            loadRecent();
        }
    }

    // ==================== 加载全部应用（多关键词搜索 + 多页 + 缓存） ====================
    private void loadAllApps() {
        if (isLoading || !snBound) return;

        // 1. 先检查缓存
        if (loadFromCache()) {
            appendLog("✓ 从缓存加载 " + allCachedApps.size() + " 个应用（" + getCacheAge() + "）");
            applyFilterAndSort();
            // 后台刷新
            if (isCacheExpired()) {
                appendLog("缓存已过期，后台刷新中...");
                refreshAllAppsInBackground();
            }
            return;
        }

        // 2. 无缓存，执行全量搜索
        startFullSearch(false);
    }

    private void refreshAllAppsInBackground() {
        startFullSearch(true);
    }

    private void startFullSearch(final boolean isBackground) {
        if (!isBackground) {
            isLoading = true;
            appList.clear();
            refreshGridDisplay();
            loadingBar.setVisibility(View.VISIBLE);
            loadingBar.setProgress(0);
            emptyText.setVisibility(View.GONE);
            appGridContainer.setVisibility(View.GONE);
            loadMoreBtn.setVisibility(View.GONE);
            searchBtn.setEnabled(false);
        }

        int totalPages = SEARCH_KEYWORDS.length * SEARCH_PAGES;
        appendLog("开始" + (isBackground ? "后台" : "") + "搜索（" + SEARCH_KEYWORDS.length + "关键词 × " + SEARCH_PAGES + "页 = " + totalPages + "请求）...");

        if (probeExecutor != null) {
            probeExecutor.shutdownNow();
        }
        probeExecutor = java.util.concurrent.Executors.newFixedThreadPool(SEARCH_THREADS);

        final Set<Integer> seenIds = new HashSet<>();
        // 保留已有应用ID
        for (AppModel a : allCachedApps) seenIds.add(a.appId);
        final int[] completed = {0};
        final int[] found = {isBackground ? allCachedApps.size() : 0};
        final int totalKeywords = SEARCH_KEYWORDS.length;

        for (int idx = 0; idx < totalKeywords; idx++) {
            final String keyword = SEARCH_KEYWORDS[idx];
            for (int page = 1; page <= SEARCH_PAGES; page++) {
                final int currentPage = page;
                probeExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (!isBackground && !isLoading) return;
                        if (isBackground && isFinishing()) return;

                        final java.util.List<AppModel> newApps = new java.util.ArrayList<>();
                        String debugInfo = "";
                        try {
                            JSONObject param = new JSONObject();
                            param.put("sn", sn);
                            if (!keyword.isEmpty()) {
                                param.put("nameOfApp", keyword);
                            }
                            param.put("size", 100);
                            param.put("page", currentPage);
                            LinkedHashMap<String, String> params = ApiHelper.buildParams(param, "/appStore/appInfoNoAuth");
                            String result = ApiHelper.doPost(ApiHelper.BASE_URL, params);

                            JSONObject outer = new JSONObject(result);
                            if (outer.optInt("code") == 200) {
                                JSONObject dataObj = outer.optJSONObject("data");
                                String resultStr = dataObj != null ? dataObj.optString("result", "") : "";
                                if (!resultStr.isEmpty()) {
                                    JSONObject inner = new JSONObject(resultStr);
                                    int errNo = inner.optInt("errNo", -999);
                                    if (errNo == 0) {
                                        JSONObject innerData = inner.optJSONObject("data");
                                        JSONArray list = innerData != null ? innerData.optJSONArray("list") : null;
                                        if (list != null) {
                                            for (int i = 0; i < list.length(); i++) {
                                                AppModel app = AppModel.fromJson(list.getJSONObject(i));
                                                if (app.appId > 0) {
                                                    newApps.add(app);
                                                }
                                            }
                                        }
                                    } else {
                                        debugInfo = "errNo=" + errNo + " " + inner.optString("errMsg", "");
                                    }
                                }
                            } else {
                                debugInfo = "code=" + outer.optInt("code");
                            }
                        } catch (Exception e) {
                            debugInfo = "异常: " + e.getMessage();
                        }

                        final String finalDebug = debugInfo;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isBackground && !isLoading) return;

                                completed[0]++;

                                int newCount = 0;
                                for (AppModel app : newApps) {
                                    if (!seenIds.contains(app.appId)) {
                                        seenIds.add(app.appId);
                                        allCachedApps.add(app);
                                        found[0]++;
                                        newCount++;
                                    }
                                }

                                if (newCount > 0) {
                                    if (!isBackground) {
                                        // 节流更新：避免每次都重建网格导致卡顿
                                        pendingNewApps += newCount;
                                        scheduleThrottledUiUpdate();
                                    }
                                    appendLog("✓ \"" + (keyword.isEmpty() ? "(空)" : keyword) + "\" P" + currentPage + " +" + newCount);
                                }

                                int totalTasks = totalKeywords * SEARCH_PAGES;
                                if (!isBackground) {
                                    loadingBar.setProgress(completed[0] * 100 / totalTasks);
                                }

                                if (completed[0] % 30 == 0) {
                                    appendLog("[" + completed[0] + "/" + totalTasks + "] 已发现 " + found[0] + " 个应用");
                                }

                                if (completed[0] == totalKeywords * SEARCH_PAGES) {
                                    if (!isBackground) {
                                        // 搜索完成：取消挂起的节流更新，立即做最终刷新
                                        uiHandler.removeCallbacksAndMessages(null);
                                        uiUpdatePending = false;
                                        finishProbeLoading(found[0]);
                                    } else {
                                        applyFilterAndSort();
                                        saveAppCache();
                                        appendLog("✓ 后台刷新完成，共 " + allCachedApps.size() + " 个应用");
                                    }
                                }
                            }
                        });
                    }
                });
            }
        }
    }

    // ==================== 缓存管理 ====================
    private boolean loadFromCache() {
        String cache = prefs.getAppCache();
        if (cache == null || cache.isEmpty()) return false;

        try {
            JSONArray arr = new JSONArray(cache);
            allCachedApps.clear();
            for (int i = 0; i < arr.length(); i++) {
                AppModel app = AppModel.fromJson(arr.getJSONObject(i));
                if (app.appId > 0) {
                    allCachedApps.add(app);
                }
            }
            return allCachedApps.size() > 0;
        } catch (Exception e) {
            appendLog("缓存解析失败: " + e.getMessage());
            return false;
        }
    }

    private void saveAppCache() {
        try {
            JSONArray arr = new JSONArray();
            for (AppModel app : allCachedApps) {
                JSONObject obj = new JSONObject();
                obj.put("id", app.appId);
                obj.put("name", app.name);
                obj.put("enName", app.enName);
                obj.put("summary", app.summary);
                obj.put("remark", app.remark);
                obj.put("icon", app.icon);
                obj.put("apkName", app.apkName);
                obj.put("apkVersion", app.apkVersion);
                obj.put("developer", app.developer);
                obj.put("apkSize", app.apkSize);
                obj.put("changeLog", app.changeLog);
                obj.put("uploadTime", app.uploadTime);
                obj.put("icpNumber", app.icpNumber);
                arr.put(obj);
            }
            prefs.saveAppCache(arr.toString());
            prefs.setAppCacheTime(System.currentTimeMillis());
        } catch (Exception e) {
            appendLog("缓存保存失败: " + e.getMessage());
        }
    }

    private boolean isCacheExpired() {
        long cacheTime = prefs.getAppCacheTime();
        if (cacheTime == 0) return true;
        return (System.currentTimeMillis() - cacheTime) > CACHE_VALID_MS;
    }

    private String getCacheAge() {
        long cacheTime = prefs.getAppCacheTime();
        if (cacheTime == 0) return "未知";
        long age = System.currentTimeMillis() - cacheTime;
        long min = age / (60 * 1000);
        if (min < 60) return min + "分钟前";
        return (min / 60) + "小时" + (min % 60) + "分钟前";
    }

    // ==================== UI节流更新 ====================

    /**
     * 节流更新UI：搜索过程中避免频繁重建网格
     * 最多每 UI_UPDATE_INTERVAL_MS 毫秒刷新一次
     */
    private void scheduleThrottledUiUpdate() {
        if (uiUpdatePending) return; // 已有挂起的更新，等待执行
        uiUpdatePending = true;
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                uiUpdatePending = false;
                if (isLoading) {
                    applyFilterAndSort();
                    if (pendingNewApps > 0) {
                        appendLog("[" + pendingNewApps + " 个新应用已加载]");
                        pendingNewApps = 0;
                    }
                }
            }
        }, UI_UPDATE_INTERVAL_MS);
    }

    // ==================== 筛选与排序 ====================
    private void applyFilterAndSort() {
        appList.clear();

        // 分类筛选
        if (currentCategory == 0) {
            // 全部
            appList.addAll(allCachedApps);
        } else {
            // 按分类关键词匹配
            String[] kwList = CATEGORY_KEYWORDS[currentCategory];
            Set<String> kwSet = new HashSet<>(java.util.Arrays.asList(kwList));
            for (AppModel app : allCachedApps) {
                boolean match = false;
                String nameLower = app.name.toLowerCase();
                String summaryLower = app.summary.toLowerCase();
                for (String kw : kwSet) {
                    if (nameLower.contains(kw.toLowerCase()) || summaryLower.contains(kw.toLowerCase())) {
                        match = true;
                        break;
                    }
                }
                if (match) appList.add(app);
            }
        }

        // 排序
        sortAppsInternal();

        allTitle.setText("全部应用 (" + appList.size() + "/" + allCachedApps.size() + ")");
        allTitle.setVisibility(View.VISIBLE);
        if (statsText != null) {
            statsText.setText("共 " + allCachedApps.size() + " 个应用 | 当前显示 " + appList.size() + " 个");
            statsText.setVisibility(View.VISIBLE);
        }
        emptyText.setText("未找到应用\n尝试搜索或下拉刷新");
        emptyText.setVisibility(appList.isEmpty() ? View.VISIBLE : View.GONE);
        appGridContainer.setVisibility(appList.isEmpty() ? View.GONE : View.VISIBLE);
        if (!appList.isEmpty()) refreshGridDisplay();
    }

    private void sortAppsInternal() {
        int sortMode = prefs.getSortMode();
        switch (sortMode) {
            case SORT_NAME:
                java.util.Collections.sort(appList, new java.util.Comparator<AppModel>() {
                    @Override
                    public int compare(AppModel a, AppModel b) {
                        return a.name.compareToIgnoreCase(b.name);
                    }
                });
                break;
            case SORT_SIZE:
                java.util.Collections.sort(appList, new java.util.Comparator<AppModel>() {
                    @Override
                    public int compare(AppModel a, AppModel b) {
                        return Long.compare(b.apkSize, a.apkSize);
                    }
                });
                break;
            case SORT_TIME:
                java.util.Collections.sort(appList, new java.util.Comparator<AppModel>() {
                    @Override
                    public int compare(AppModel a, AppModel b) {
                        return b.uploadTime.compareTo(a.uploadTime);
                    }
                });
                break;
            default:
                // 默认：按发现顺序（不排序）
                break;
        }
    }

    private void showSortDialog() {
        if (allCachedApps.isEmpty()) {
            Toast.makeText(this, "请先加载应用", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] labels = {"默认顺序", "按名称 A→Z", "按大小 大→小", "按时间 新→旧"};
        final int[] values = {SORT_DEFAULT, SORT_NAME, SORT_SIZE, SORT_TIME};
        int current = prefs.getSortMode();
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) checked = i;
        }
        new AlertDialog.Builder(this)
            .setTitle("排序方式")
            .setSingleChoiceItems(labels, checked, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    prefs.setSortMode(values[which]);
                    applyFilterAndSort();
                    Toast.makeText(StoreActivity.this, "已切换: " + labels[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            })
            .show();
    }

    private void showCategoryDialog() {
        if (allCachedApps.isEmpty()) {
            Toast.makeText(this, "请先加载应用", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("选择分类")
            .setItems(CATEGORIES, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    currentCategory = which;
                    categoryBtn.setText(CATEGORIES[which]);
                    applyFilterAndSort();
                    Toast.makeText(StoreActivity.this, "分类: " + CATEGORIES[which], Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    // ==================== 批量选择 ====================
    private void toggleBatchMode() {
        batchMode = !batchMode;
        if (batchMode) {
            selectedAppIds.clear();
            batchBar.setVisibility(View.VISIBLE);
            batchBtn.setText("退出选择");
            Toast.makeText(this, "长按或点击应用选择，底部可批量安装", Toast.LENGTH_LONG).show();
        } else {
            exitBatchMode();
        }
        refreshGridDisplay();
        updateBatchCount();
    }

    private void exitBatchMode() {
        batchMode = false;
        selectedAppIds.clear();
        batchBar.setVisibility(View.GONE);
        batchBtn.setText("批量");
        refreshGridDisplay();
    }

    private void toggleSelection(int appId) {
        if (selectedAppIds.contains(appId)) {
            selectedAppIds.remove(appId);
        } else {
            selectedAppIds.add(appId);
        }
        renderAppCards();
        updateBatchCount();
    }

    private void updateBatchCount() {
        batchCountText.setText("已选择 " + selectedAppIds.size() + " 个应用");
        batchInstallBtn.setEnabled(selectedAppIds.size() > 0);
        batchInstallBtn.setAlpha(selectedAppIds.size() > 0 ? 1.0f : 0.5f);
    }

    private void startBatchInstall() {
        if (selectedAppIds.isEmpty()) {
            Toast.makeText(this, "请先选择应用", Toast.LENGTH_SHORT).show();
            return;
        }

        final java.util.List<AppModel> toInstall = new java.util.ArrayList<>();
        int skipped = 0;
        Set<Integer> seenIds = new HashSet<>();
        for (AppModel app : appList) {
            if (selectedAppIds.contains(app.appId)) {
                // 去重：同一个appId只安装一次
                if (seenIds.contains(app.appId)) {
                    continue;
                }
                // 跳过正在安装中的应用
                if (ApiHelper.isInstalling(sn, app.appId)) {
                    skipped++;
                    continue;
                }
                seenIds.add(app.appId);
                toInstall.add(app);
            }
        }

        if (toInstall.isEmpty()) {
            Toast.makeText(this, "没有可安装的应用" + (skipped > 0 ? "（" + skipped + "个正在安装中）" : ""), Toast.LENGTH_LONG).show();
            return;
        }

        String msg = "将向设备 " + sn + " 安装 " + toInstall.size() + " 个应用";
        if (skipped > 0) {
            msg += "（已跳过" + skipped + "个正在安装中的）";
        }
        msg += "，是否继续？";

        final int finalSkipped = skipped;
        new AlertDialog.Builder(this)
            .setTitle("确认批量安装")
            .setMessage(msg)
            .setPositiveButton("开始安装", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    executeBatchInstall(toInstall);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void executeBatchInstall(final java.util.List<AppModel> apps) {
        exitBatchMode();
        final int total = apps.size();
        appendLog("[批量] 开始安装 " + total + " 个应用");

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setTitle("批量安装");
        dialog.setMessage("准备中...");
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setMax(total);
        dialog.setCancelable(false);
        dialog.show();

        new AsyncTask<Void, String, int[]>() {
            @Override
            protected int[] doInBackground(Void... voids) {
                int success = 0, fail = 0, skipped = 0;
                for (int i = 0; i < apps.size(); i++) {
                    AppModel app = apps.get(i);
                    publishProgress("安装中 (" + (i+1) + "/" + total + "): " + app.name);
                    ApiHelper.InstallResult result = ApiHelper.installApp(sn, app.appId, currentApiMode);
                    if (result.success) {
                        success++;
                        prefs.addInstallRecord(app.appId, app.name, true, result.msg);
                        appendLog("[批量] ✓ " + app.name + " (ID=" + app.appId + ") [" + result.source + "]");
                    } else if ("dedup".equals(result.source)) {
                        // 防重复安装，跳过不计入失败
                        skipped++;
                        appendLog("[批量] ⊘ " + app.name + " (ID=" + app.appId + ") 已在安装中，跳过");
                    } else {
                        fail++;
                        prefs.addInstallRecord(app.appId, app.name, false, result.msg);
                        appendLog("[批量] ✗ " + app.name + " (ID=" + app.appId + "): " + result.msg);
                    }
                    try { Thread.sleep(800); } catch (Exception ignored) {}
                }
                return new int[]{success, fail, skipped};
            }

            @Override
            protected void onProgressUpdate(String... values) {
                dialog.setMessage(values[0]);
                dialog.incrementProgressBy(1);
            }

            @Override
            protected void onPostExecute(int[] result) {
                if (dialog.isShowing()) dialog.dismiss();
                int success = result[0], fail = result[1], skipped = result.length > 2 ? result[2] : 0;
                String logMsg = "[批量] 完成！成功 " + success + " 个，失败 " + fail + " 个";
                if (skipped > 0) logMsg += "，跳过 " + skipped + " 个(重复)";
                appendLog(logMsg);
                String toastMsg = "批量安装完成\n成功: " + success + " 失败: " + fail;
                if (skipped > 0) toastMsg += "\n跳过(重复): " + skipped;
                Toast.makeText(StoreActivity.this, toastMsg, Toast.LENGTH_LONG).show();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void finishProbeLoading(int found) {
        if (!isLoading) return;
        isLoading = false;
        loadingBar.setVisibility(View.GONE);
        searchBtn.setEnabled(true);
        saveAppCache();
        applyFilterAndSort();
        recommendBar.setVisibility(View.VISIBLE);
        appendLog("✓ 搜索完成！共发现 " + allCachedApps.size() + " 个应用");
        Toast.makeText(StoreActivity.this, "共 " + allCachedApps.size() + " 个应用", Toast.LENGTH_SHORT).show();
        if (probeExecutor != null) {
            probeExecutor.shutdown();
        }
    }

    private void loadFavorites() {
        appList.clear();
        Prefs.FavoriteItem[] favs = prefs.getFavorites();
        for (Prefs.FavoriteItem f : favs) {
            AppModel app = new AppModel();
            app.appId = f.appId;
            app.name = f.appName;
            app.icon = f.icon;
            app.developer = f.developer;
            app.apkSize = f.apkSize;
            appList.add(app);
        }
        refreshGridDisplay();
        emptyText.setText("暂无收藏应用\n在应用详情页点击收藏");
        emptyText.setVisibility(appList.isEmpty() ? View.VISIBLE : View.GONE);
        appGridContainer.setVisibility(appList.isEmpty() ? View.GONE : View.VISIBLE);
        if (!appList.isEmpty()) refreshGridDisplay();
        appendLog("收藏: " + appList.size() + " 个");
    }

    private void loadRecent() {
        appList.clear();
        Prefs.FavoriteItem[] recents = prefs.getRecentViews();
        for (Prefs.FavoriteItem r : recents) {
            AppModel app = new AppModel();
            app.appId = r.appId;
            app.name = r.appName;
            app.icon = r.icon;
            app.developer = r.developer;
            app.apkSize = r.apkSize;
            appList.add(app);
        }
        refreshGridDisplay();
        emptyText.setText("暂无最近浏览记录");
        emptyText.setVisibility(appList.isEmpty() ? View.VISIBLE : View.GONE);
        appGridContainer.setVisibility(appList.isEmpty() ? View.GONE : View.VISIBLE);
        appendLog("最近: " + appList.size() + " 个");
    }

    private void loadRecommends() {
        recommendContainer.removeAllViews();
        loadRecommendApp("微信");
        loadRecommendApp("QQ");
    }

    private void loadRecommendApp(final String keyword) {
        if (!snBound) return;
        new AsyncTask<Void, Void, AppModel>() {
            @Override
            protected AppModel doInBackground(Void... voids) {
                try {
                    JSONObject param = new JSONObject();
                    param.put("sn", sn);
                    param.put("nameOfApp", keyword);
                    param.put("size", 5);
                    param.put("page", 1);
                    LinkedHashMap<String, String> params = ApiHelper.buildParams(param, "/appStore/appInfoNoAuth");
                    String result = ApiHelper.doPost(ApiHelper.BASE_URL, params);

                    JSONObject outer = new JSONObject(result);
                    if (outer.optInt("code") == 200) {
                        JSONObject dataObj = outer.optJSONObject("data");
                        String resultStr = dataObj != null ? dataObj.optString("result", "") : "";
                        if (!resultStr.isEmpty()) {
                            JSONObject inner = new JSONObject(resultStr);
                            if (inner.optInt("errNo") == 0) {
                                JSONObject innerData = inner.optJSONObject("data");
                                JSONArray list = innerData != null ? innerData.optJSONArray("list") : null;
                                if (list != null && list.length() > 0) {
                                    return AppModel.fromJson(list.getJSONObject(0));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    appendLog("推荐加载失败 " + keyword + ": " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void onPostExecute(AppModel app) {
                if (app == null) return;
                View card = LayoutInflater.from(StoreActivity.this).inflate(R.layout.item_recommend_card, recommendContainer, false);
                ImageView icon = card.findViewById(R.id.recIcon);
                TextView name = card.findViewById(R.id.recName);
                TextView dev = card.findViewById(R.id.recDev);
                Button btn = card.findViewById(R.id.recBtn);

                name.setText(app.name);
                dev.setText(app.developer.isEmpty() ? "未知开发者" : app.developer);
                if (!app.icon.isEmpty()) {
                    ImageLoader.load(app.icon, icon);
                }
                final AppModel finalApp = app;
                View.OnClickListener listener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openAppDetail(finalApp);
                    }
                };
                btn.setOnClickListener(listener);
                card.setOnClickListener(listener);
                recommendContainer.addView(card);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void openAppDetail(AppModel app) {
        prefs.addRecentView(app.appId, app.name, app.icon, app.developer, app.apkSize);
        Intent intent = new Intent(StoreActivity.this, DetailActivity.class);
        intent.putExtra("sn", sn);
        intent.putExtra("appId", app.appId);
        intent.putExtra("name", app.name);
        intent.putExtra("developer", app.developer);
        intent.putExtra("icon", app.icon);
        intent.putExtra("size", app.apkSize);
        intent.putExtra("summary", app.summary);
        intent.putExtra("remark", app.remark);
        intent.putExtra("apkName", app.apkName);
        intent.putExtra("apkVersion", app.apkVersion);
        intent.putExtra("changeLog", app.changeLog);
        intent.putExtra("uploadTime", app.uploadTime);
        intent.putExtra("icpNumber", app.icpNumber);
        intent.putExtra("permissions", new JSONArray(app.permissions).toString());
        intent.putExtra("tags", new JSONArray(app.tags).toString());
        startActivity(intent);
    }

    private void showMenuDialog() {
        String modeLabel = "自动";
        if (Obfuscator.dec(new byte[]{(byte)0xcb, (byte)0xc7, (byte)0xcf, (byte)0xc8}, (byte)0xa6).equals(currentApiMode)) modeLabel = "仅主API";
        else if (Obfuscator.dec(new byte[]{(byte)0x1c, (byte)0x1f, (byte)0x1d, (byte)0x15, (byte)0x0b, (byte)0x0e}, (byte)0x7e).equals(currentApiMode)) modeLabel = "仅备用API";
        final String[] items = {"安装记录", "设备工具", "清除搜索历史", "清除应用缓存", "刷新商店(强制)", "热门搜索", "应用统计", "API模式: " + modeLabel};
        new AlertDialog.Builder(this)
            .setTitle("菜单")
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    switch (which) {
                        case 0:
                            startActivity(new Intent(StoreActivity.this, HistoryActivity.class));
                            break;
                        case 1:
                            // 设备工具
                            if (!snBound) {
                                Toast.makeText(StoreActivity.this, "请先绑定SN", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            Intent toolsIntent = new Intent(StoreActivity.this, DeviceToolsActivity.class);
                            toolsIntent.putExtra("sn", sn);
                            startActivity(toolsIntent);
                            break;
                        case 2:
                            prefs.clearSearchHistory();
                            Toast.makeText(StoreActivity.this, "已清除搜索历史", Toast.LENGTH_SHORT).show();
                            renderHistory();
                            break;
                        case 3:
                            prefs.clearAppCache();
                            allCachedApps.clear();
                            appList.clear();
                            refreshGridDisplay();
                            Toast.makeText(StoreActivity.this, "已清除应用缓存", Toast.LENGTH_SHORT).show();
                            appendLog("已清除应用缓存");
                            break;
                        case 4:
                            if (!snBound) {
                                Toast.makeText(StoreActivity.this, "请先绑定SN", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            appendLog("强制刷新商店（跳过缓存）");
                            allCachedApps.clear();
                            prefs.clearAppCache();
                            startFullSearch(false);
                            loadRecommends();
                            break;
                        case 5:
                            showHotKeywordsDialog();
                            break;
                        case 6:
                            showAppStats();
                            break;
                        case 7:
                            showApiModeDialog();
                            break;
                    }
                }
            })
            .show();
    }

    private void showApiModeDialog() {
        final String[] modes = {"自动(主API优先)", "仅主API", "仅备用API"};
        final String[] values = {Obfuscator.dec(new byte[]{(byte)0xb1, (byte)0xa5, (byte)0xa4, (byte)0xbf}, (byte)0xd0), Obfuscator.dec(new byte[]{(byte)0xcb, (byte)0xc7, (byte)0xcf, (byte)0xc8}, (byte)0xa6), Obfuscator.dec(new byte[]{(byte)0x1c, (byte)0x1f, (byte)0x1d, (byte)0x15, (byte)0x0b, (byte)0x0e}, (byte)0x7e)};
        String current = prefs.getApiMode();
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
            .setTitle("选择API模式")
            .setSingleChoiceItems(modes, checked, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    prefs.setApiMode(values[which]);
                    currentApiMode = values[which];
                    String modeLabel = "自动";
                    if (Obfuscator.dec(new byte[]{(byte)0xcb, (byte)0xc7, (byte)0xcf, (byte)0xc8}, (byte)0xa6).equals(currentApiMode)) modeLabel = "仅主API";
                    else if (Obfuscator.dec(new byte[]{(byte)0x1c, (byte)0x1f, (byte)0x1d, (byte)0x15, (byte)0x0b, (byte)0x0e}, (byte)0x7e).equals(currentApiMode)) modeLabel = "仅备用API";
                    Toast.makeText(StoreActivity.this, "API模式已切换为: " + modeLabel, Toast.LENGTH_SHORT).show();
                    appendLog("API模式切换为: " + values[which]);
                    dialog.dismiss();
                }
            })
            .show();
    }

    private void showHotKeywordsDialog() {
        final String[] hotKeywords = {"微信", "QQ", "豆包", "百度", "抖音", "快手", "作业帮", "有道", "WPS", "钉钉", "腾讯会议", "网易云音乐", "哔哩哔哩", "高德地图", "淘宝", "京东", "支付宝", "美图秀秀", "剪映", "番茄小说"};
        new AlertDialog.Builder(this)
            .setTitle("热门搜索")
            .setItems(hotKeywords, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    searchInput.setText(hotKeywords[which]);
                    doSearch();
                }
            })
            .show();
    }

    private void showAppStats() {
        if (allCachedApps.isEmpty()) {
            Toast.makeText(this, "请先加载应用", Toast.LENGTH_SHORT).show();
            return;
        }
        int totalApps = allCachedApps.size();
        long totalSize = 0;
        int withIcon = 0;
        int withSummary = 0;
        for (AppModel app : allCachedApps) {
            totalSize += app.apkSize;
            if (!app.icon.isEmpty()) withIcon++;
            if (!app.summary.isEmpty()) withSummary++;
        }
        double avgSizeMB = totalSize / (1024.0 * 1024.0) / Math.max(1, totalApps);
        double totalSizeGB = totalSize / (1024.0 * 1024.0 * 1024.0);

        StringBuilder sb = new StringBuilder();
        sb.append("应用统计\n\n");
        sb.append("总应用数: ").append(totalApps).append("\n");
        sb.append("总大小: ").append(String.format("%.2f GB", totalSizeGB)).append("\n");
        sb.append("平均大小: ").append(String.format("%.1f MB", avgSizeMB)).append("\n");
        sb.append("有图标: ").append(withIcon).append(" (").append(withIcon * 100 / totalApps).append("%)\n");
        sb.append("有简介: ").append(withSummary).append(" (").append(withSummary * 100 / totalApps).append("%)\n\n");

        // 分类统计
        sb.append("分类分布:\n");
        for (int c = 1; c < CATEGORIES.length; c++) {
            String[] kwList = CATEGORY_KEYWORDS[c];
            Set<String> kwSet = new HashSet<>(java.util.Arrays.asList(kwList));
            int count = 0;
            for (AppModel app : allCachedApps) {
                for (String kw : kwSet) {
                    if (app.name.toLowerCase().contains(kw.toLowerCase()) ||
                        app.summary.toLowerCase().contains(kw.toLowerCase())) {
                        count++;
                        break;
                    }
                }
            }
            sb.append("  ").append(CATEGORIES[c]).append(": ").append(count).append("\n");
        }

        sb.append("\n缓存时间: ").append(getCacheAge());

        new AlertDialog.Builder(this)
            .setTitle("应用统计")
            .setMessage(sb.toString())
            .setPositiveButton("确定", null)
            .show();
    }

    

    private void renderHistory() {
        historyContainer.removeAllViews();
        String[] history = prefs.getSearchHistory();
        if (history.length == 0) {
            historyBar.setVisibility(View.GONE);
            return;
        }
        historyBar.setVisibility(View.VISIBLE);
        for (final String h : history) {
            if (h.isEmpty()) continue;
            TextView tag = new TextView(this);
            tag.setText("  " + h + "  ");
            tag.setTextSize(12);
            tag.setTextColor(getResources().getColor(R.color.md3_primary));
            tag.setBackgroundResource(R.drawable.input_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 8, 0);
            tag.setLayoutParams(lp);
            tag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchInput.setText(h);
                    doSearch();
                }
            });
            historyContainer.addView(tag);
        }
    }

    private void renderCategoryBar() {
        categoryContainer.removeAllViews();
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int idx = i;
            TextView tag = new TextView(this);
            tag.setText("  " + CATEGORIES[i] + "  ");
            tag.setTextSize(12);
            boolean active = (i == currentCategory);
            tag.setTextColor(getResources().getColor(active ? R.color.md3_on_primary : R.color.md3_on_surface));
            tag.setBackgroundResource(active ? R.drawable.btn_primary_bg : R.drawable.input_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 8, 0);
            tag.setLayoutParams(lp);
            tag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentCategory = idx;
                    categoryBtn.setText(CATEGORIES[idx]);
                    renderCategoryBar();
                    applyFilterAndSort();
                }
            });
            categoryContainer.addView(tag);
        }
    }

    private void doSearch() {
        if (!snBound) {
            Toast.makeText(this, "请先绑定SN", Toast.LENGTH_SHORT).show();
            return;
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        final String keyword = searchInput.getText().toString().trim();
        appendLog("搜索: " + (keyword.isEmpty() ? "(全部)" : keyword));
        if (!keyword.isEmpty()) {
            prefs.addSearchHistory(keyword);
            renderHistory();
        }

        if (keyword.isEmpty()) {
            // 空搜索 = 显示全部
            currentCategory = 0;
            categoryBtn.setText("分类");
            applyFilterAndSort();
            return;
        }

        // 先从本地缓存搜索
        if (!allCachedApps.isEmpty()) {
            appList.clear();
            String kwLower = keyword.toLowerCase();
            for (AppModel app : allCachedApps) {
                if (app.name.toLowerCase().contains(kwLower) ||
                    app.summary.toLowerCase().contains(kwLower) ||
                    app.developer.toLowerCase().contains(kwLower) ||
                    app.apkName.toLowerCase().contains(kwLower) ||
                    app.enName.toLowerCase().contains(kwLower)) {
                    appList.add(app);
                }
            }
            if (!appList.isEmpty()) {
                sortAppsInternal();
                refreshGridDisplay();
                allTitle.setText("搜索结果 (" + appList.size() + ")");
                allTitle.setVisibility(View.VISIBLE);
                emptyText.setVisibility(View.GONE);
                appGridContainer.setVisibility(View.VISIBLE);
                appendLog("✓ 本地搜索找到 " + appList.size() + " 个应用");
                return;
            }
        }

        // 本地没找到，调用API搜索
        loadingBar.setVisibility(View.VISIBLE);
        loadingBar.setProgress(10);
        searchBtn.setEnabled(false);
        emptyText.setVisibility(View.GONE);
        appGridContainer.setVisibility(View.GONE);
        loadMoreBtn.setVisibility(View.GONE);

        new AsyncTask<Void, Integer, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    publishProgress(30);
                    // 多页搜索
                    StringBuilder allResults = new StringBuilder();
                    for (int page = 1; page <= 5; page++) {
                        JSONObject param = new JSONObject();
                        param.put("sn", sn);
                        param.put("nameOfApp", keyword);
                        param.put("size", 100);
                        param.put("page", page);

                        LinkedHashMap<String, String> params = ApiHelper.buildParams(param, "/appStore/appInfoNoAuth");
                        String result = ApiHelper.doPost(ApiHelper.BASE_URL, params);
                        if (page == 1) {
                            allResults.append(result);
                        } else {
                            // 合并结果
                            try {
                                JSONObject outer = new JSONObject(result);
                                if (outer.optInt("code") == 200) {
                                    JSONObject dataObj = outer.optJSONObject("data");
                                    String resultStr = dataObj != null ? dataObj.optString("result", "") : "";
                                    if (!resultStr.isEmpty()) {
                                        JSONObject inner = new JSONObject(resultStr);
                                        if (inner.optInt("errNo") == 0) {
                                            JSONObject innerData = inner.optJSONObject("data");
                                            JSONArray list = innerData != null ? innerData.optJSONArray("list") : null;
                                            if (list != null) {
                                                JSONObject firstOuter = new JSONObject(allResults.toString());
                                                JSONObject firstData = firstOuter.optJSONObject("data");
                                                String firstResultStr = firstData != null ? firstData.optString("result", "") : "";
                                                JSONObject firstInner = new JSONObject(firstResultStr);
                                                JSONObject firstInnerData = firstInner.optJSONObject("data");
                                                JSONArray firstList = firstInnerData != null ? firstInnerData.optJSONArray("list") : null;
                                                if (firstList != null) {
                                                    for (int i = 0; i < list.length(); i++) {
                                                        firstList.put(list.getJSONObject(i));
                                                    }
                                                }
                                                allResults = new StringBuilder(firstOuter.toString());
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        publishProgress(page * 30);
                    }
                    return allResults.toString();
                } catch (Exception e) {
                    return "ERROR: " + e.getMessage();
                }
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                loadingBar.setProgress(values[0]);
            }

            @Override
            protected void onPostExecute(String result) {
                loadingBar.setProgress(100);
                loadingBar.setVisibility(View.GONE);
                searchBtn.setEnabled(true);
                handleSearchResult(result);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void handleSearchResult(String result) {
        appendLog("返回(" + (result != null ? result.length() : 0) + "字节)");

        appList.clear();
        Set<Integer> existingIds = new HashSet<>();
        for (AppModel a : allCachedApps) existingIds.add(a.appId);
        boolean newAdded = false;

        try {
            JSONObject outer = new JSONObject(result);
            if (outer.optInt("code") == 200) {
                JSONObject dataObj = outer.optJSONObject("data");
                String resultStr = dataObj != null ? dataObj.optString("result", "") : "";
                if (!resultStr.isEmpty()) {
                    JSONObject inner = new JSONObject(resultStr);
                    if (inner.optInt("errNo") == 0) {
                        JSONObject innerData = inner.optJSONObject("data");
                        JSONArray list = innerData != null ? innerData.optJSONArray("list") : null;
                        if (list != null) {
                            for (int i = 0; i < list.length(); i++) {
                                AppModel app = AppModel.fromJson(list.getJSONObject(i));
                                appList.add(app);
                                // 合并到缓存
                                if (!existingIds.contains(app.appId)) {
                                    allCachedApps.add(app);
                                    newAdded = true;
                                }
                            }
                            appendLog("✓ 找到 " + appList.size() + " 个应用" + (newAdded ? " (新增已合并到缓存)" : ""));
                        }
                    } else {
                        String msg = inner.optString("errMsg", "");
                        appendLog("✗ errNo=" + inner.optInt("errNo") + " " + msg);
                        Toast.makeText(this, "搜索失败: " + msg, Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                appendLog("✗ code=" + outer.optInt("code"));
                Toast.makeText(this, "请求失败 code=" + outer.optInt("code"), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            appendLog("✗ 解析失败: " + e.getMessage());
            Toast.makeText(this, "解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (newAdded) saveAppCache();

        sortAppsInternal();
        refreshGridDisplay();
        allTitle.setText("搜索结果 (" + appList.size() + ")");
        allTitle.setVisibility(View.VISIBLE);
        emptyText.setText("未找到相关应用\n试试其他关键词或热门搜索");
        emptyText.setVisibility(appList.isEmpty() ? View.VISIBLE : View.GONE);
        appGridContainer.setVisibility(appList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private synchronized void appendLog(String msg) {
        try {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String line = "[" + time + "] " + msg;
            fullLog.insert(0, line + "\n");
            if (fullLog.length() > 10000) fullLog.setLength(10000);
        } catch (Exception e) {
            // 忽略fullLog异常
        }
        try {
            prefs.appendGlobalLog(msg);
        } catch (Exception e) {
            // 忽略全局日志异常，不影响主流程
        }
        // 更新日志面板UI
        try {
            if (logText != null) {
                final String logContent = fullLog.toString();
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (logText != null) {
                            logText.setText(logContent);
                            // 显示日志面板
                            if (logPanel != null) {
                                logPanel.setVisibility(View.VISIBLE);
                            }
                            // 自动滚动到顶部（最新日志在顶部）
                            if (logScroll != null) {
                                logScroll.fullScroll(View.FOCUS_UP);
                            }
                        }
                    }
                });
            }
        } catch (Exception e) {
            // 忽略日志面板更新异常
        }
    }

    // ==================== 网格渲染（替代GridView，支持分页+联动滑动） ====================

    /**
     * 刷新网格显示（重置分页到第一页）
     */
    private void refreshGridDisplay() {
        displayedCount = Math.min(PAGE_SIZE, appList.size());
        renderAppCards();
    }

    /**
     * 加载更多应用（分页加载下一页）
     */
    private void loadMoreApps() {
        if (displayedCount >= appList.size()) return;
        displayedCount = Math.min(displayedCount + PAGE_SIZE, appList.size());
        renderAppCards();
        appendLog("加载更多: 显示 " + displayedCount + "/" + appList.size() + " 个应用");
    }

    /**
     * 渲染应用卡片到 appGridContainer（每行2个卡片）
     */
    private void renderAppCards() {
        appGridContainer.removeAllViews();

        if (appList.isEmpty()) {
            loadMoreBtn.setVisibility(View.GONE);
            return;
        }

        int count = Math.min(displayedCount, appList.size());
        int dpi = getResources().getDisplayMetrics().densityDpi;
        int spacing6 = (int) (6 * dpi / 160f);

        for (int i = 0; i < count; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowParams.bottomMargin = spacing6;
            row.setLayoutParams(rowParams);

            // 左卡片
            View card1 = createAppCard(appList.get(i));
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            );
            p1.setMarginEnd(spacing6);
            card1.setLayoutParams(p1);
            row.addView(card1);

            // 右卡片
            if (i + 1 < count) {
                View card2 = createAppCard(appList.get(i + 1));
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                );
                p2.setMarginStart(spacing6);
                card2.setLayoutParams(p2);
                row.addView(card2);
            } else {
                View spacer = new View(this);
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                );
                p2.setMarginStart(spacing6);
                spacer.setLayoutParams(p2);
                row.addView(spacer);
            }

            appGridContainer.addView(row);
        }

        // 显示/隐藏"加载更多"按钮
        if (displayedCount < appList.size()) {
            loadMoreBtn.setText("加载更多 ▼ (" + displayedCount + "/" + appList.size() + ")");
            loadMoreBtn.setVisibility(View.VISIBLE);
        } else {
            loadMoreBtn.setVisibility(View.GONE);
        }
    }

    /**
     * 创建单个应用卡片视图
     */
    private View createAppCard(final AppModel app) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_app_card, null, false);

        ImageView icon = card.findViewById(R.id.cardIcon);
        TextView name = card.findViewById(R.id.cardName);
        TextView dev = card.findViewById(R.id.cardDeveloper);
        TextView size = card.findViewById(R.id.cardSize);

        // 批量选择模式
        if (batchMode) {
            boolean selected = selectedAppIds.contains(app.appId);
            name.setText((selected ? "☑ " : "☐ ") + app.name);
            card.setBackgroundColor(selected ? 0x33007AFF : 0x00000000);
        } else {
            name.setText(app.name);
            card.setBackgroundColor(0x00000000);
        }

        dev.setText(app.developer.isEmpty() ? "未知开发者" : app.developer);
        size.setText(app.getSizeDisplay());

        if (!app.icon.isEmpty()) {
            ImageLoader.load(app.icon, icon);
        } else {
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        // 点击事件
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (batchMode) {
                    toggleSelection(app.appId);
                } else {
                    openAppDetail(app);
                }
            }
        });

        // 长按进入批量模式
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (currentMode == MODE_STORE && !batchMode) {
                    toggleBatchMode();
                    toggleSelection(app.appId);
                }
                return true;
            }
        });

        return card;
    }
}

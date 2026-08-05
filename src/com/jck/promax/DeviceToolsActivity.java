package com.jck.promax;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DeviceToolsActivity extends Activity {

    private String sn;
    private String childId = "";
    private TextView snText;
    private TextView childIdText;
    private TextView statusText;
    private LinearLayout resultContainer;
    private TextView rawJsonText;
    private ProgressBar loadingBar;
    private Button btnBehaviorHistory;
    private Button btnUsingTime;
    private Button btnPisouHistory;
    private Button btnCopyResult;
    private Button btnToggleRaw;
    private Button refreshBtn;
    private Button backBtn;

    private String lastRawJson = "";
    private boolean showRaw = false;
    private int currentTool = -1; // 0=behavior, 1=using, 2=pisou

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_tools);

        sn = getIntent().getStringExtra("sn");
        if (sn == null || sn.isEmpty()) {
            sn = new Prefs(this).getSN();
        }

        initViews();
        setupListeners();

        snText.setText(sn != null ? sn : "未绑定");
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        refreshBtn = findViewById(R.id.refreshBtn);
        snText = findViewById(R.id.snText);
        childIdText = findViewById(R.id.childIdText);
        statusText = findViewById(R.id.statusText);
        resultContainer = findViewById(R.id.resultContainer);
        rawJsonText = findViewById(R.id.rawJsonText);
        loadingBar = findViewById(R.id.loadingBar);
        btnBehaviorHistory = findViewById(R.id.btnBehaviorHistory);
        btnUsingTime = findViewById(R.id.btnUsingTime);
        btnPisouHistory = findViewById(R.id.btnPisouHistory);
        btnCopyResult = findViewById(R.id.btnCopyResult);
        btnToggleRaw = findViewById(R.id.btnToggleRaw);
    }

    private void setupListeners() {
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (currentTool >= 0) {
                    executeTool(currentTool);
                }
            }
        });

        btnBehaviorHistory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setActiveButton(btnBehaviorHistory);
                executeTool(0);
            }
        });

        btnUsingTime.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setActiveButton(btnUsingTime);
                executeTool(1);
            }
        });

        btnPisouHistory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setActiveButton(btnPisouHistory);
                executeTool(2);
            }
        });

        btnCopyResult.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                copyResultToClipboard();
            }
        });

        btnToggleRaw.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showRaw = !showRaw;
                rawJsonText.setVisibility(showRaw ? View.VISIBLE : View.GONE);
                btnToggleRaw.setText(showRaw ? "隐藏原始数据" : "显示原始数据");
            }
        });
    }

    private void setActiveButton(Button active) {
        btnBehaviorHistory.setBackgroundResource(R.drawable.input_bg);
        btnBehaviorHistory.setTextColor(getResources().getColor(R.color.md3_on_surface));
        btnUsingTime.setBackgroundResource(R.drawable.input_bg);
        btnUsingTime.setTextColor(getResources().getColor(R.color.md3_on_surface));
        btnPisouHistory.setBackgroundResource(R.drawable.input_bg);
        btnPisouHistory.setTextColor(getResources().getColor(R.color.md3_on_surface));

        active.setBackgroundResource(R.drawable.btn_primary_bg);
        active.setTextColor(getResources().getColor(R.color.md3_on_primary));
    }

    private void executeTool(final int toolIndex) {
        currentTool = toolIndex;

        if (sn == null || sn.isEmpty()) {
            Toast.makeText(this, "请先绑定SN", Toast.LENGTH_SHORT).show();
            return;
        }

        // 需要先获取childId（如果没有的话）
        if (childId.isEmpty()) {
            // 所有工具都需要childId，先获取
            fetchChildIdAndThen(toolIndex);
            return;
        }

        loadingBar.setVisibility(View.VISIBLE);
        loadingBar.setProgress(30);
        statusText.setText("正在加载...");
        statusText.setVisibility(View.VISIBLE);
        resultContainer.removeAllViews();
        resultContainer.setVisibility(View.GONE);

        final String[] toolNames = {"行为历史", "使用时间", "批欧历史"};

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                switch (toolIndex) {
                    case 0: return ApiHelper.getBehaviorHistory(childId);
                    case 1: return ApiHelper.getUsingTime(childId);
                    case 2: return ApiHelper.getPisouHistory(childId);
                    default: return "{\"code\":-1,\"message\":\"未知工具\"}";
                }
            }

            @Override
            protected void onPostExecute(String result) {
                loadingBar.setVisibility(View.GONE);
                lastRawJson = result;
                rawJsonText.setText(formatJson(result));

                try {
                    JSONObject json = new JSONObject(result);
                    int code = json.optInt("code", -1);

                    if (code == 200) {
                        statusText.setVisibility(View.GONE);
                        resultContainer.setVisibility(View.VISIBLE);
                        parseAndDisplay(toolIndex, json);
                    } else {
                        String msg = json.optString("message", json.optString("errMsg", "未知错误"));
                        statusText.setText("请求失败: " + msg);
                        statusText.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    statusText.setText("解析失败: " + e.getMessage() + "\n\n原始数据: " + result.substring(0, Math.min(200, result.length())));
                    statusText.setVisibility(View.VISIBLE);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void fetchChildIdAndThen(final int toolIndex) {
        loadingBar.setVisibility(View.VISIBLE);
        loadingBar.setProgress(20);
        statusText.setText("正在获取设备信息...");

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                // 方式1: 从 SuperviseProvider 获取 child_id
                try {
                    Uri uri = Uri.parse(Obfuscator.dec3(
                        new byte[]{(byte)0xa7,(byte)0xf3,(byte)0x12,(byte)0xb0,(byte)0xf9,(byte)0x12,(byte)0xb0,(byte)0xa6,(byte)0x53,(byte)0xeb,(byte)0xff,(byte)0x13,(byte)0xa9,(byte)0xb2,(byte)0x06,(byte)0xb1,(byte)0xf3,(byte)0x05,(byte)0xa1,(byte)0xfe,(byte)0x1d,(byte)0xaa,(byte)0xfb,(byte)0x52,(byte)0xad,(byte)0xf3,(byte)0x08,(byte)0xea,(byte)0xec,(byte)0x1d,(byte)0xa0,(byte)0xb2,(byte)0x0f,(byte)0xb1,(byte)0xec,(byte)0x19,(byte)0xb6,(byte)0xea,(byte)0x15,(byte)0xb7,(byte)0xf9,(byte)0x53,(byte)0xaf,(byte)0xf9,(byte)0x05,(byte)0x9b,(byte)0xea,(byte)0x1d,(byte)0xa8,(byte)0xe9,(byte)0x19},
                        (byte)0xc4, (byte)0x9c, (byte)0x7c));
                    Cursor c = getContentResolver().query(uri, null, null, null, null);
                    if (c != null) {
                        if (c.moveToFirst()) {
                            do {
                                try {
                                    String key = c.getString(c.getColumnIndex("key"));
                                    if ("child_id".equals(key) || "childId".equals(key)) {
                                        String val = c.getString(c.getColumnIndex("value"));
                                        if (val != null && !val.isEmpty() && !val.equals("null")) {
                                            c.close();
                                            return val;
                                        }
                                    }
                                } catch (Exception ignored) {}
                            } while (c.moveToNext());
                        }
                        c.close();
                    }
                } catch (Exception e) {
                    // SuperviseProvider 不可用
                }

                // 方式2: 从云端配置 ContentProvider 获取
                try {
                    Uri uri = Uri.parse(Obfuscator.dec3(
                        new byte[]{(byte)0x8b,(byte)0x4f,(byte)0x28,(byte)0x9c,(byte)0x5a,(byte)0x3e,(byte)0x90,(byte)0x4e,(byte)0x2b,(byte)0x89,(byte)0x5e,(byte)0x35,(byte)0x8a,(byte)0x44,(byte)0x21,(byte)0x95,(byte)0x40,(byte)0x3d,(byte)0x9b,(byte)0x4b,(byte)0x20,(byte)0x8a,(byte)0x4e,(byte)0x3a,(byte)0x94,(byte)0x59,(byte)0x2c,(byte)0x95,(byte)0x41,(byte)0x24,(byte)0x8b,(byte)0x4e,(byte)0x3d,(byte)0x8a,(byte)0x4e,(byte)0x3c,(byte)0x87,(byte)0x4e,(byte)0x3c,(byte)0x99,(byte)0x5f,(byte)0x28,(byte)0x84,(byte)0x44,(byte)0x3e,(byte)0x9b,(byte)0x4b,(byte)0x28,(byte)0x97,(byte)0x4e,(byte)0x3e,(byte)0x9b,(byte)0x4b,(byte)0x28,(byte)0x97,(byte)0x4e,(byte)0x3e,(byte)0x9b,(byte)0x4b,(byte)0x28},
                        (byte)0xec, (byte)0x3c, (byte)0x5d));
                    Cursor c = getContentResolver().query(uri, null, null, null, null);
                    if (c != null) {
                        if (c.moveToFirst()) {
                            try {
                                int idx = c.getColumnIndex("child_id");
                                if (idx >= 0) {
                                    String val = c.getString(idx);
                                    if (val != null && !val.isEmpty() && !val.equals("null")) {
                                        c.close();
                                        return val;
                                    }
                                }
                                // 尝试其他列名
                                String[] cols = c.getColumnNames();
                                for (String col : cols) {
                                    if (col.toLowerCase().contains("child")) {
                                        String val = c.getString(c.getColumnIndex(col));
                                        if (val != null && !val.isEmpty() && !val.equals("null") && !val.equals("0")) {
                                            c.close();
                                            return val;
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        c.close();
                    }
                } catch (Exception e) {
                    // 云端配置不可用
                }

                // 方式3: 从 SuperviseProvider /advance 获取
                try {
                    Uri uri = Uri.parse(Obfuscator.dec3(
                        new byte[]{(byte)0x5b,(byte)0x7b,(byte)0x7c,(byte)0x4c,(byte)0x7d,(byte)0x70,(byte)0x5e,(byte)0x7a,(byte)0x7d,(byte)0x57,(byte)0x70,(byte)0x3b,(byte)0x5a,(byte)0x60,(byte)0x7c,(byte)0x4c,(byte)0x62,(byte)0x5a,(byte)0x5b,(byte)0x7a,(byte)0x7d,(byte)0x57,(byte)0x73,(byte)0x3b,(byte)0x5a,(byte)0x6d,(byte)0x7c,(byte)0x4c,(byte)0x70,(byte)0x5a,(byte)0x57,(byte)0x60,(byte)0x7c,(byte)0x4c,(byte)0x72,(byte)0x3b,(byte)0x5a,(byte)0x60,(byte)0x74,(byte)0x7d,(byte)0x7c,(byte)0x75,(byte)0x7e,(byte)0x3b,(byte)0x43},
                        (byte)0x2a, (byte)0x1a, (byte)0x0c));
                    Cursor c = getContentResolver().query(uri, null, null, null, null);
                    if (c != null) {
                        if (c.moveToFirst()) {
                            try {
                                String[] cols = c.getColumnNames();
                                for (String col : cols) {
                                    if (col.toLowerCase().contains("child")) {
                                        String val = c.getString(c.getColumnIndex(col));
                                        if (val != null && !val.isEmpty() && !val.equals("null") && !val.equals("0")) {
                                            c.close();
                                            return val;
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        c.close();
                    }
                } catch (Exception e) {
                    // advance 不可用
                }

                return null; // 所有方式都失败
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null && !result.isEmpty() && !result.equals("0")) {
                    childId = result;
                    childIdText.setText("ChildID: " + childId + " (自动获取)");
                    executeToolDirect(toolIndex);
                } else {
                    // 自动获取失败，弹出手动输入对话框
                    loadingBar.setVisibility(View.GONE);
                    showChildIdInputDialog(toolIndex);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void showChildIdInputDialog(final int toolIndex) {
        final EditText input = new EditText(this);
        input.setHint("输入child_id");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
            .setTitle("需要ChildID")
            .setMessage("无法自动获取child_id。\n\n请手动输入child_id，或点击取消返回。\n\n提示: 可先在Zybos系统工具中使用「管控探针」查看。")
            .setView(input)
            .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    String val = input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        childId = val;
                        childIdText.setText("ChildID: " + childId + " (手动输入)");
                        executeToolDirect(toolIndex);
                    } else {
                        Toast.makeText(DeviceToolsActivity.this, "请输入有效的ChildID", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show();
    }

    private void executeToolDirect(int toolIndex) {
        loadingBar.setProgress(50);
        statusText.setText("正在加载...");

        final int idx = toolIndex;
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                switch (idx) {
                    case 0: return ApiHelper.getBehaviorHistory(childId);
                    case 1: return ApiHelper.getUsingTime(childId);
                    case 2: return ApiHelper.getPisouHistory(childId);
                    default: return "{\"code\":-1,\"message\":\"未知工具\"}";
                }
            }

            @Override
            protected void onPostExecute(String result) {
                loadingBar.setVisibility(View.GONE);
                lastRawJson = result;
                rawJsonText.setText(formatJson(result));

                try {
                    JSONObject json = new JSONObject(result);
                    int code = json.optInt("code", -1);

                    if (code == 200) {
                        statusText.setVisibility(View.GONE);
                        resultContainer.setVisibility(View.VISIBLE);
                        parseAndDisplay(idx, json);
                    } else {
                        String msg = json.optString("message", json.optString("errMsg", "未知错误"));
                        statusText.setText("请求失败: " + msg);
                        statusText.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    statusText.setText("解析失败: " + e.getMessage());
                    statusText.setVisibility(View.VISIBLE);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void parseAndDisplay(int toolIndex, JSONObject json) {
        resultContainer.removeAllViews();

        try {
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                addInfoCard("提示", "返回数据为空");
                return;
            }

            switch (toolIndex) {
                case 0: // 行为历史
                    parseBehaviorHistory(data);
                    break;
                case 1: // 使用时间
                    parseUsingTime(data);
                    break;
                case 2: // 批欧历史
                    parsePisouHistory(data);
                    break;
            }
        } catch (Exception e) {
            addInfoCard("解析错误", e.getMessage());
        }
    }

    private void parseBehaviorHistory(JSONObject data) {
        try {
            JSONArray list = data.optJSONArray("list");
            if (list == null || list.length() == 0) {
                addInfoCard("行为历史", "暂无行为记录");
                return;
            }

            addInfoCard("行为历史", "共 " + list.length() + " 条记录");

            for (int i = 0; i < Math.min(list.length(), 50); i++) {
                JSONObject item = list.getJSONObject(i);
                String title = item.optString("title", "未知");
                String content = item.optString("content", "");
                long time = item.optLong("createTime", 0);
                String timeStr = time > 0 ? formatTimestamp(time) : "未知时间";

                addRecordCard(title, content, timeStr);
            }

            if (list.length() > 50) {
                addInfoCard("提示", "仅显示前50条，共 " + list.length() + " 条");
            }
        } catch (Exception e) {
            addInfoCard("解析错误", e.getMessage());
        }
    }

    private void parseUsingTime(JSONObject data) {
        try {
            // 解析使用时间数据
            String todayUse = data.optString("todayUseTime", "0");
            String weekUse = data.optString("weekUseTime", "0");
            String monthUse = data.optString("monthUseTime", "0");

            StringBuilder sb = new StringBuilder();
            sb.append("今日使用: ").append(formatMinutes(todayUse)).append("\n");
            sb.append("本周使用: ").append(formatMinutes(weekUse)).append("\n");
            sb.append("本月使用: ").append(formatMinutes(monthUse));

            addInfoCard("使用时间统计", sb.toString());

            // 尝试解析详细数据
            JSONArray detailList = data.optJSONArray("detailList");
            if (detailList != null && detailList.length() > 0) {
                addInfoCard("详细记录", "共 " + detailList.length() + " 条");
                for (int i = 0; i < Math.min(detailList.length(), 20); i++) {
                    JSONObject item = detailList.getJSONObject(i);
                    String date = item.optString("date", "");
                    String useTime = item.optString("useTime", "0");
                    addRecordCard(date, "使用时长: " + formatMinutes(useTime), "");
                }
            }
        } catch (Exception e) {
            addInfoCard("使用时间", data.toString());
        }
    }

    private void parsePisouHistory(JSONObject data) {
        try {
            JSONArray list = data.optJSONArray("list");
            if (list == null || list.length() == 0) {
                addInfoCard("批欧历史", "暂无批欧记录");
                return;
            }

            addInfoCard("批欧历史", "共 " + list.length() + " 条记录");

            for (int i = 0; i < Math.min(list.length(), 50); i++) {
                JSONObject item = list.getJSONObject(i);
                String subject = item.optString("subject", "未知科目");
                String score = item.optString("score", "0");
                long time = item.optLong("createTime", 0);
                String timeStr = time > 0 ? formatTimestamp(time) : "未知时间";
                String detail = item.optString("detail", "");

                addRecordCard(subject + " - " + score + "分", detail, timeStr);
            }
        } catch (Exception e) {
            addInfoCard("解析错误", e.getMessage());
        }
    }

    // ==================== UI辅助方法 ====================

    private void addInfoCard(String title, String content) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(12, 10, 12, 10);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8);
        card.setLayoutParams(lp);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getResources().getColor(R.color.md3_primary));
        titleView.setTextSize(14);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(titleView);

        TextView contentView = new TextView(this);
        contentView.setText(content);
        contentView.setTextColor(getResources().getColor(R.color.md3_on_surface));
        contentView.setTextSize(13);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 4, 0, 0);
        contentView.setLayoutParams(clp);
        card.addView(contentView);

        resultContainer.addView(card);
    }

    private void addRecordCard(String title, String content, String time) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(10, 8, 10, 8);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setBackgroundColor(0xFFF8F8F8);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 6);
        card.setLayoutParams(lp);

        // 标题行
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getResources().getColor(R.color.md3_on_surface));
        titleView.setTextSize(13);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleView.setLayoutParams(tlp);
        titleRow.addView(titleView);

        if (!time.isEmpty()) {
            TextView timeView = new TextView(this);
            timeView.setText(time);
            timeView.setTextColor(getResources().getColor(R.color.md3_outline));
            timeView.setTextSize(11);
            titleRow.addView(timeView);
        }

        card.addView(titleRow);

        // 内容
        if (!content.isEmpty()) {
            TextView contentView = new TextView(this);
            contentView.setText(content);
            contentView.setTextColor(getResources().getColor(R.color.md3_on_surface));
            contentView.setTextSize(12);
            contentView.setAlpha(0.8f);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, 2, 0, 0);
            contentView.setLayoutParams(clp);
            card.addView(contentView);
        }

        resultContainer.addView(card);
    }

    // ==================== 工具方法 ====================

    private String formatTimestamp(long timestamp) {
        try {
            // 判断是秒还是毫秒
            if (timestamp > 10000000000L) {
                // 毫秒
                return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
            } else {
                // 秒
                return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp * 1000));
            }
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    private String formatMinutes(String minutesStr) {
        try {
            int minutes = Integer.parseInt(minutesStr);
            if (minutes < 60) {
                return minutes + "分钟";
            }
            int hours = minutes / 60;
            int mins = minutes % 60;
            if (mins == 0) return hours + "小时";
            return hours + "小时" + mins + "分钟";
        } catch (Exception e) {
            return minutesStr + "分钟";
        }
    }

    private String formatJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            return json.toString(2);
        } catch (Exception e) {
            return jsonStr;
        }
    }

    private void copyResultToClipboard() {
        if (lastRawJson.isEmpty()) {
            Toast.makeText(this, "暂无结果可复制", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("设备工具结果", lastRawJson);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }
}

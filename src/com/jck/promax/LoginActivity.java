package com.jck.promax;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.LinkedHashMap;

public class LoginActivity extends Activity {

    private EditText snInput;
    private Button enterBtn;
    private TextView snError;
    private TextView snCount;
    private LinearLayout historyContainer;
    private TextView historyLabel;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = new Prefs(this);
        snInput = findViewById(R.id.snInput);
        enterBtn = findViewById(R.id.enterBtn);
        snError = findViewById(R.id.snError);
        snCount = findViewById(R.id.snCount);
        historyContainer = findViewById(R.id.snHistoryContainer);
        historyLabel = findViewById(R.id.snHistoryLabel);

        // 加载已保存的SN
        String savedSN = prefs.getSN();
        if (!savedSN.isEmpty()) {
            snInput.setText(savedSN);
            snInput.setSelection(savedSN.length());
            snCount.setText(savedSN.length() + "/18");
            if (savedSN.matches("\\d{18}")) {
                enterBtn.setEnabled(true);
                enterBtn.setBackgroundResource(R.drawable.btn_primary_bg);
            }
        }

        // 显示历史SN
        renderSNHistory();

        snInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                String sn = s.toString();
                snCount.setText(sn.length() + "/18");

                boolean valid = sn.length() == 18 && sn.matches("\\d{18}");
                if (sn.length() > 0 && !valid) {
                    snError.setVisibility(View.VISIBLE);
                    if (!sn.matches("\\d*")) {
                        snError.setText("SN只能包含数字");
                    } else if (sn.length() != 18) {
                        snError.setText("SN必须为18位（当前" + sn.length() + "位）");
                    }
                } else {
                    snError.setVisibility(View.GONE);
                }

                enterBtn.setEnabled(valid);
                enterBtn.setBackgroundResource(valid ? R.drawable.btn_primary_bg : R.drawable.btn_disabled_bg);
            }
        });

        enterBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doValidateAndEnter();
            }
        });
    }

    private void renderSNHistory() {
        historyContainer.removeAllViews();
        String[] history = prefs.getValidSNHistory();
        if (history.length == 0) {
            historyLabel.setVisibility(View.GONE);
            historyContainer.setVisibility(View.GONE);
            return;
        }
        historyLabel.setVisibility(View.VISIBLE);
        historyContainer.setVisibility(View.VISIBLE);

        for (final String h : history) {
            if (h.isEmpty()) continue;
            TextView tag = new TextView(this);
            tag.setText("  " + h + "  ");
            tag.setTextSize(12);
            tag.setTextColor(getResources().getColor(R.color.md3_on_primary));
            tag.setBackgroundResource(R.drawable.btn_primary_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 8, 0);
            tag.setLayoutParams(lp);
            tag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snInput.setText(h);
                    snInput.setSelection(h.length());
                    snCount.setText(h.length() + "/18");
                    enterBtn.setEnabled(true);
                    enterBtn.setBackgroundResource(R.drawable.btn_primary_bg);
                    snError.setVisibility(View.GONE);
                }
            });
            historyContainer.addView(tag);
        }

        // 加一个清除按钮
        TextView clearTag = new TextView(this);
        clearTag.setText("  清除记录  ");
        clearTag.setTextSize(12);
        clearTag.setTextColor(getResources().getColor(R.color.md3_error));
        clearTag.setBackgroundResource(R.drawable.input_bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 0);
        clearTag.setLayoutParams(lp);
        clearTag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.clearValidSNHistory();
                renderSNHistory();
                Toast.makeText(LoginActivity.this, "已清除历史记录", Toast.LENGTH_SHORT).show();
            }
        });
        historyContainer.addView(clearTag);
    }

    private void doValidateAndEnter() {
        final String sn = snInput.getText().toString().trim();

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("正在校验SN...");
        dialog.setCancelable(false);
        dialog.show();

        new AsyncTask<Void, Void, Boolean>() {
            String errMsg = "";

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    // 用探测appid的方式校验SN是否有效
                    JSONObject param = new JSONObject();
                    param.put("sn", sn);
                    param.put("appId", 1);
                    LinkedHashMap<String, String> params = ApiHelper.buildParams(param, Obfuscator.dec2(new byte[]{(byte)0xa9, (byte)0x54, (byte)0xf6, (byte)0x45, (byte)0xd5, (byte)0x41, (byte)0xe9, (byte)0x47, (byte)0xe3, (byte)0x1a, (byte)0xe7, (byte)0x45, (byte)0xf6, (byte)0x7c, (byte)0xe8, (byte)0x53, (byte)0xe9, (byte)0x7b, (byte)0xe9, (byte)0x74, (byte)0xf3, (byte)0x41, (byte)0xee}, (byte)0x86, (byte)0x35));
                    String result = ApiHelper.doPost(ApiHelper.BASE_URL, params);

                    JSONObject outer = new JSONObject(result);
                    if (outer.optInt("code") == 200) {
                        JSONObject dataObj = outer.optJSONObject("data");
                        String resultStr = dataObj != null ? dataObj.optString("result", "") : "";
                        if (!resultStr.isEmpty()) {
                            JSONObject inner = new JSONObject(resultStr);
                            int errNo = inner.optInt("errNo", -1);
                            String msg = inner.optString("errMsg", "");
                            // errNo=0 或 errNo=-1(lack query params) 都算SN有效
                            // 但lack query params说明参数格式不对，不是SN问题
                            // 如果 errMsg 包含 "SN" "不存在" "无效" 等关键词，说明SN无效
                            if (msg.contains("SN") && (msg.contains("不存在") || msg.contains("无效") || msg.contains("错误"))) {
                                errMsg = msg;
                                return false;
                            }
                            // 如果返回了正常的应用数据或errNo=0，说明SN有效
                            // 或者返回了 lack query params 这种参数错误（不是SN问题）
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
                    // SN校验通过，保存并记录到历史
                    prefs.setSN(sn);
                    prefs.addValidSN(sn);
                    Intent intent = new Intent(LoginActivity.this, StoreActivity.class);
                    intent.putExtra("sn", sn);
                    startActivity(intent);
                    finish();
                } else {
                    snError.setText(errMsg.isEmpty() ? "SN校验失败，请检查SN是否正确" : errMsg);
                    snError.setVisibility(View.VISIBLE);
                    Toast.makeText(LoginActivity.this, "SN校验失败: " + errMsg, Toast.LENGTH_LONG).show();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}

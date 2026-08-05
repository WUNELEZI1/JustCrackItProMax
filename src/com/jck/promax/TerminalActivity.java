package com.jck.promax;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.Button;

@SuppressWarnings("deprecation")
public class TerminalActivity extends Activity {

    private TextView terminalText;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        terminalText = findViewById(R.id.terminalText);
        prefs = new Prefs(this);

        loadLog();

        Button copyBtn = findViewById(R.id.copyBtn);
        Button backBtn = findViewById(R.id.backBtn);
        Button clearBtn = findViewById(R.id.clearBtn);

        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("log", terminalText.getText().toString());
                cm.setPrimaryClip(clip);
                Toast.makeText(TerminalActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.clearGlobalLog();
                terminalText.setText("");
                Toast.makeText(TerminalActivity.this, "日志已清除", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLog();
    }

    private void loadLog() {
        String globalLog = prefs.getGlobalLog();
        if (globalLog == null || globalLog.isEmpty()) {
            globalLog = "[提示] 暂无终端日志\n\n请在应用商店或详情页进行操作后查看日志。\n日志包含应用搜索、探测、安装等操作的详细信息。";
        }
        terminalText.setText(globalLog);
        terminalText.scrollTo(0, 0);
    }
}
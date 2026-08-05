package com.jck.promax;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends Activity {

    private ListView listView;
    private TextView emptyText;
    private Button backBtn;
    private Button clearBtn;
    private Prefs prefs;
    private HistoryAdapter adapter;
    private Prefs.InstallRecord[] records;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        prefs = new Prefs(this);
        listView = findViewById(R.id.historyList);
        emptyText = findViewById(R.id.emptyText);
        backBtn = findViewById(R.id.backBtn);
        clearBtn = findViewById(R.id.clearHistoryBtn);

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(HistoryActivity.this)
                    .setTitle("确认清除")
                    .setMessage("确定要清空所有安装记录吗？")
                    .setPositiveButton("清除", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            prefs.clearInstallHistory();
                            loadData();
                            Toast.makeText(HistoryActivity.this, "已清空", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        });

        loadData();
    }

    private void loadData() {
        records = prefs.getInstallHistory();
        adapter = new HistoryAdapter();
        listView.setAdapter(adapter);
        emptyText.setVisibility(records.length == 0 ? View.VISIBLE : View.GONE);
        listView.setVisibility(records.length == 0 ? View.GONE : View.VISIBLE);
    }

    private class HistoryAdapter extends BaseAdapter {
        public int getCount() { return records.length; }
        public Object getItem(int p) { return records[p]; }
        public long getItemId(int p) { return p; }

        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) {
                cv = LayoutInflater.from(HistoryActivity.this).inflate(R.layout.item_history, parent, false);
            }
            Prefs.InstallRecord r = records[pos];

            TextView name = cv.findViewById(R.id.histName);
            TextView time = cv.findViewById(R.id.histTime);
            TextView status = cv.findViewById(R.id.histStatus);
            TextView msg = cv.findViewById(R.id.histMsg);

            name.setText(r.appName != null ? r.appName : "未知应用");

            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            time.setText(sdf.format(new Date(r.time)));

            if (r.success) {
                status.setText("成功");
                status.setTextColor(getResources().getColor(R.color.md3_success));
            } else {
                status.setText("失败");
                status.setTextColor(getResources().getColor(R.color.md3_error));
            }

            msg.setText(r.msg != null && !r.msg.isEmpty() ? r.msg : "无详情");

            return cv;
        }
    }
}

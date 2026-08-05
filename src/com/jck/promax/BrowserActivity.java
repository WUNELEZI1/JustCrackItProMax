package com.jck.promax;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class BrowserActivity extends Activity {

    private WebView webView;
    private EditText urlInput;
    private Button backBtn;
    private Button goBtn;
    private Button refreshBtn;
    private Button homeBtn;
    private ProgressBar progressBar;
    private TextView titleText;

    private static final String HOME_URL = Obfuscator.dec3(new byte[]{(byte)0x9b, (byte)0x8f, (byte)0xc1, (byte)0x83, (byte)0x88, (byte)0x8f, (byte)0xdc, (byte)0xd4, (byte)0xc2, (byte)0x84, (byte)0x8c, (byte)0x9b, (byte)0x91, (byte)0x92, (byte)0xdb, (byte)0x94, (byte)0xd5, (byte)0xd6, (byte)0x9c, (byte)0x96}, (byte)0xf3, (byte)0xfb, (byte)0xb5);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        webView = findViewById(R.id.webView);
        urlInput = findViewById(R.id.urlInput);
        backBtn = findViewById(R.id.backBtn);
        goBtn = findViewById(R.id.goBtn);
        refreshBtn = findViewById(R.id.refreshBtn);
        homeBtn = findViewById(R.id.homeBtn);
        progressBar = findViewById(R.id.progressBar);
        titleText = findViewById(R.id.titleText);

        // WebView 设置 - 适配设备屏幕
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
        settings.setTextZoom(100);
        settings.setSupportMultipleWindows(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        // 设置移动端User-Agent，让网页返回适合设备的版本
        String ua = settings.getUserAgentString();
        if (!ua.contains("Mobile")) {
            settings.setUserAgentString(ua + " Mobile");
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                urlInput.setText(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                urlInput.setText(url);
                titleText.setText(view.getTitle());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
        });

        // 加载首页
        loadUrl(HOME_URL);

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });

        goBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doGo(); }
        });

        urlInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                doGo();
                return true;
            }
        });

        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { webView.reload(); }
        });

        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadUrl(HOME_URL); }
        });
    }

    private void doGo() {
        String input = urlInput.getText().toString().trim();
        if (input.isEmpty()) return;

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);

        String url;
        if (input.startsWith("http://") || input.startsWith("https://")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            url = "https://" + input;
        } else {
            url = "https://www.bing.com/search?q=" + java.net.URLEncoder.encode(input);
        }
        loadUrl(url);
    }

    private void loadUrl(String url) {
        webView.loadUrl(url);
        urlInput.setText(url);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}

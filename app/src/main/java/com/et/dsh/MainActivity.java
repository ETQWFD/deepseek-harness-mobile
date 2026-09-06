package com.et.dsh;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.ImageView;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
public class MainActivity extends AppCompatActivity {
    private TextView logView;
    private ScrollView logScroll;
    private LinearLayout loadingLayout;
    private LinearLayout readyLayout;
    private WebView webView;
    private TextView urlText;
    private TextView lanUrlText;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView percentText;
    private String currentLanUrl;
    private StatusReceiver receiver;
    private LinearLayout root;
    private ImageView bgImageView;
    private Handler wallpaperHandler;
    private Runnable wallpaperRunnable;
    private boolean wallpaperEnabled = false;
    private static final int REQUEST_STORAGE = 1001;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 根布局 - 深色渐变背景
        this.root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0A0A14, 0xFF0D1020, 0xFF0A0A14});
        root.setBackground(bg);
        // 背景图片层（半透明二次元壁纸）
        bgImageView = new ImageView(this);
        bgImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgImageView.setAlpha(0.15f);
        bgImageView.setVisibility(View.GONE);
        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.addView(bgImageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        rootContainer.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(rootContainer);
        // 标题栏
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(48, 56, 48, 20);
        TextView title = new TextView(this);
        title.setText("DeepSeek Harness");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.getPaint().setFakeBoldText(true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        titleBar.addView(title);
        Button settingsBtn = new Button(this);
        settingsBtn.setText("⚙ 设置");
        settingsBtn.setBackgroundColor(0xFF1A1A2E);
        settingsBtn.setTextColor(0xFF8888AA);
        settingsBtn.setTextSize(12);
        settingsBtn.setPadding(28, 18, 28, 18);
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        titleBar.addView(settingsBtn);
        root.addView(titleBar);
        // 副标题
        TextView subtitle = new TextView(this);
        subtitle.setText("v2.43 · ETC+KU终极版 · 移动端运行时");
        subtitle.setTextColor(0xFF555577);
        subtitle.setTextSize(12);
        subtitle.setPadding(48, 0, 48, 24);
        root.addView(subtitle);
        // 加载中布局
        loadingLayout = new LinearLayout(this);
        loadingLayout.setOrientation(LinearLayout.VERTICAL);
        loadingLayout.setPadding(48, 16, 48, 24);
        // 状态文字 + 百分比
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        progressText = new TextView(this);
        progressText.setText("准备启动...");
        progressText.setTextColor(0xFFDDDDEE);
        progressText.setTextSize(15);
        progressText.getPaint().setFakeBoldText(true);
        progressText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        statusRow.addView(progressText);
        percentText = new TextView(this);
        percentText.setText("0%");
        percentText.setTextColor(0xFF64B5F6);
        percentText.setTextSize(18);
        percentText.getPaint().setFakeBoldText(true);
        statusRow.addView(percentText);
        loadingLayout.addView(statusRow);
        // 进度条
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setScaleY(4.0f);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbParams.setMargins(0, 20, 0, 20);
        progressBar.setLayoutParams(pbParams);
        // 自定义进度条颜色
        try {
            progressBar.getProgressDrawable().setColorFilter(0xFF4A90D9, android.graphics.PorterDuff.Mode.SRC_IN);
        } catch (Exception e) {}
        loadingLayout.addView(progressBar);
        // 日志区域
        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(0x08000000);
        logView = new TextView(this);
        logView.setTextColor(0xFF555577);
        logView.setTextSize(11);
        logView.setPadding(20, 16, 20, 16);
        logView.setLineSpacing(4, 1);
        logScroll.addView(logView);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        logScroll.setLayoutParams(logParams);
        loadingLayout.addView(logScroll);
        root.addView(loadingLayout);
        // 就绪布局
        readyLayout = new LinearLayout(this);
        readyLayout.setOrientation(LinearLayout.VERTICAL);
        readyLayout.setPadding(24, 12, 24, 0);
        readyLayout.setVisibility(View.GONE);
        TextView readyLabel = new TextView(this);
        readyLabel.setText("● 服务运行中");
        readyLabel.setTextColor(0xFF4CAF50);
        readyLabel.setTextSize(14);
        readyLabel.getPaint().setFakeBoldText(true);
        readyLayout.addView(readyLabel);
        urlText = new TextView(this);
        urlText.setTextColor(0xFF777799);
        urlText.setTextSize(11);
        urlText.setPadding(0, 8, 0, 2);
        readyLayout.addView(urlText);
        lanUrlText = new TextView(this);
        lanUrlText.setTextColor(0xFF64B5F6);
        lanUrlText.setTextSize(13);
        lanUrlText.setPadding(0, 2, 0, 12);
        lanUrlText.setTextIsSelectable(true);
        readyLayout.addView(lanUrlText);
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button copyBtn = new Button(this);
        copyBtn.setText("复制链接");
        copyBtn.setBackgroundColor(0xFF1E3A5F);
        copyBtn.setTextColor(0xFFFFFFFF);
        copyBtn.setTextSize(11);
        copyBtn.setOnClickListener(v -> copyLanUrl());
        btnRow.addView(copyBtn);
        Button openBtn = new Button(this);
        openBtn.setText("外部浏览器");
        openBtn.setBackgroundColor(0xFF1A1A2E);
        openBtn.setTextColor(0xFFAAAAAA);
        openBtn.setTextSize(11);
        openBtn.setOnClickListener(v -> openInBrowser());
        btnRow.addView(openBtn);
        Button restartBtn = new Button(this);
        restartBtn.setText("重启服务");
        restartBtn.setBackgroundColor(0xFF3A1E1E);
        restartBtn.setTextColor(0xFFAAAAAA);
        restartBtn.setTextSize(11);
        restartBtn.setOnClickListener(v -> restartService());
        btnRow.addView(restartBtn);
        readyLayout.addView(btnRow);
        webView = new WebView(this);
        LinearLayout.LayoutParams wvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        wvParams.setMargins(0, 12, 0, 0);
        webView.setLayoutParams(wvParams);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        readyLayout.addView(webView);
        root.addView(readyLayout);
        // 注册广播
        receiver = new StatusReceiver();
        IntentFilter filter = new IntentFilter(NodeService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        // 申请权限
        requestPermissions();
        // 引导电池优化白名单
        requestBatteryOptimization();
        // 启动服务
        appendLog("正在启动 DeepSeek Harness 服务...");
        startDshService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyBackground();
    }

    private void applyBackground() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("dsh_settings", MODE_PRIVATE);
            boolean useBg = prefs.getBoolean("bg_starry", false);
            if (useBg) {
                wallpaperEnabled = true;
                bgImageView.setVisibility(View.VISIBLE);
                root.setBackgroundColor(0xFF0A0A14);
                startWallpaperTimer();
            } else {
                wallpaperEnabled = false;
                stopWallpaperTimer();
                bgImageView.setVisibility(View.GONE);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0xFF0A0A14, 0xFF0D1020, 0xFF0A0A14});
                root.setBackground(bg);
            }
        } catch (Exception e) {
            android.util.Log.w("MainActivity", "应用背景失败", e);
        }
    }

    private void startWallpaperTimer() {
        if (wallpaperHandler == null) {
            wallpaperHandler = new Handler(Looper.getMainLooper());
        }
        if (wallpaperRunnable == null) {
            wallpaperRunnable = new Runnable() {
                @Override
                public void run() {
                    if (wallpaperEnabled) {
                        loadWallpaper();
                        wallpaperHandler.postDelayed(this, 60000);
                    }
                }
            };
        }
        wallpaperHandler.removeCallbacks(wallpaperRunnable);
        wallpaperHandler.post(wallpaperRunnable);
    }

    private void stopWallpaperTimer() {
        if (wallpaperHandler != null && wallpaperRunnable != null) {
            wallpaperHandler.removeCallbacks(wallpaperRunnable);
        }
    }

    private void loadWallpaper() {
        new Thread(() -> {
            try {
                String[] urls = {
                    "https://api.btstu.cn/sjbz/?lx=dongman&format=images",
                    "https://api.vvhan.com/api/acgimg",
                    "https://api.ixiaowai.cn/api/api.php",
                    "https://api.ghser.com/random/pc.php"
                };
                for (String urlStr : urls) {
                    try {
                        URL url = new URL(urlStr);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(8000);
                        conn.setInstanceFollowRedirects(true);
                        InputStream is = conn.getInputStream();
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        is.close();
                        conn.disconnect();
                        if (bitmap != null) {
                            runOnUiThread(() -> bgImageView.setImageBitmap(bitmap));
                            return;
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("Wallpaper", "加载壁纸失败", e);
            }
        }).start();
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_STORAGE);
            }
        }
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    // 某些设备不支持，忽略
                }
            }
        }
    }
    private void startDshService() {
        Intent serviceIntent = new Intent(this, NodeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
    private void restartService() {
        stopService(new Intent(this, NodeService.class));
        Toast.makeText(this, "正在重启服务...", Toast.LENGTH_SHORT).show();
        loadingLayout.setVisibility(View.VISIBLE);
        readyLayout.setVisibility(View.GONE);
        progressBar.setProgress(0);
        percentText.setText("0%");
        progressText.setText("正在重启...");
        logView.setText("");
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            runOnUiThread(this::startDshService);
        }).start();
    }
    private void appendLog(String text) {
        runOnUiThread(() -> {
            logView.append(text + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }
    private void updateProgress(int percent, String message) {
        runOnUiThread(() -> {
            if (percent >= 0 && percent <= 100) {
                progressBar.setProgress(percent);
                percentText.setText(percent + "%");
            }
            if (message != null) progressText.setText(message);
        });
    }
    private void onServiceReady(String localUrl, String lanUrl) {
        runOnUiThread(() -> {
            currentLanUrl = lanUrl;
            loadingLayout.setVisibility(View.GONE);
            readyLayout.setVisibility(View.VISIBLE);
            urlText.setText("本地: " + localUrl);
            lanUrlText.setText("局域网: " + lanUrl);
            webView.loadUrl(localUrl);
        });
    }
    private void copyLanUrl() {
        if (currentLanUrl != null) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("DSH URL", currentLanUrl));
            Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show();
        }
    }
    private void openInBrowser() {
        if (currentLanUrl != null) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentLanUrl));
            startActivity(browserIntent);
        }
    }
    @Override
    public void onBackPressed() {
        if (readyLayout.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver != null) unregisterReceiver(receiver);
    }
    private class StatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = intent.getStringExtra(NodeService.EXTRA_MESSAGE);
            boolean ready = intent.getBooleanExtra(NodeService.EXTRA_READY, false);
            int progress = intent.getIntExtra(NodeService.EXTRA_PROGRESS, -1);
            if (ready) {
                String localUrl = intent.getStringExtra(NodeService.EXTRA_URL);
                String lanUrl = intent.getStringExtra(NodeService.EXTRA_LAN_URL);
                onServiceReady(localUrl, lanUrl);
            } else if (msg != null) {
                // 所有带进度的消息都更新进度条，不带进度的追加到日志
                if (progress >= 0) {
                    updateProgress(progress, msg);
                } else {
                    appendLog(msg);
                }
            }
        }
    }
}

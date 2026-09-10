package com.et.dsh;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText apiBaseUrlInput;
    private EditText apiKeyInput;
    private EditText modelNameInput;
    private RadioGroup modelModeGroup;
    private CheckBox bgCheckBox;
    private CheckBox shizukuCheckBox;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("dsh_settings", MODE_PRIVATE);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0D0D1A);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.getPaint().setFakeBoldText(true);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("修改后需重启服务生效");
        subtitle.setTextColor(0xFF666688);
        subtitle.setTextSize(12);
        subtitle.setPadding(0, 8, 0, 32);
        root.addView(subtitle);

        // API Base URL
        TextView label1 = new TextView(this);
        label1.setText("AI API 地址");
        label1.setTextColor(0xFFCCCCCC);
        label1.setTextSize(14);
        label1.getPaint().setFakeBoldText(true);
        root.addView(label1);

        apiBaseUrlInput = new EditText(this);
        apiBaseUrlInput.setText(prefs.getString("api_base_url", ""));
        apiBaseUrlInput.setHint("https://api.deepseek.com/v1");
        apiBaseUrlInput.setTextColor(0xFFFFFFFF);
        apiBaseUrlInput.setHintTextColor(0xFF444466);
        apiBaseUrlInput.setBackgroundColor(0xFF1A1A2E);
        apiBaseUrlInput.setPadding(32, 24, 32, 24);
        apiBaseUrlInput.setTextSize(13);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etParams.setMargins(0, 8, 0, 24);
        apiBaseUrlInput.setLayoutParams(etParams);
        root.addView(apiBaseUrlInput);

        TextView hint1 = new TextView(this);
        hint1.setText("默认使用官方 DeepSeek 地址。如需使用第三方中转或兼容 OpenAI 格式的服务，请填写对应地址。");
        hint1.setTextColor(0xFF666688);
        hint1.setTextSize(11);
        hint1.setPadding(0, 0, 0, 24);
        root.addView(hint1);

        // API Key
        TextView label2 = new TextView(this);
        label2.setText("API Key");
        label2.setTextColor(0xFFCCCCCC);
        label2.setTextSize(14);
        label2.getPaint().setFakeBoldText(true);
        root.addView(label2);

        apiKeyInput = new EditText(this);
        apiKeyInput.setText(prefs.getString("api_key", ""));
        apiKeyInput.setHint("sk-xxxxxxxxxxxxxxxx");
        apiKeyInput.setTextColor(0xFFFFFFFF);
        apiKeyInput.setHintTextColor(0xFF444466);
        apiKeyInput.setBackgroundColor(0xFF1A1A2E);
        apiKeyInput.setPadding(32, 24, 32, 24);
        apiKeyInput.setTextSize(13);
        apiKeyInput.setLayoutParams(etParams);
        root.addView(apiKeyInput);

        TextView hint2 = new TextView(this);
        hint2.setText("请输入官方 DeepSeek API Key。Key 仅保存在本地，不会上传。");
        hint2.setTextColor(0xFF666688);
        hint2.setTextSize(11);
        hint2.setPadding(0, 0, 0, 24);
        root.addView(hint2);
        // 模型名称
        TextView label2b = new TextView(this);
        label2b.setText("模型名称 (Model Name) *必填");
        label2b.setTextColor(0xFFCCCCCC);
        label2b.setTextSize(14);
        label2b.getPaint().setFakeBoldText(true);
        root.addView(label2b);
        modelNameInput = new EditText(this);
        modelNameInput.setText(prefs.getString("model_name", "deepseek-chat"));
        modelNameInput.setHint("deepseek-chat");
        modelNameInput.setTextColor(0xFFFFFFFF);
        modelNameInput.setHintTextColor(0xFF444466);
        modelNameInput.setBackgroundColor(0xFF1A1A2E);
        modelNameInput.setPadding(32, 24, 32, 24);
        modelNameInput.setTextSize(13);
        modelNameInput.setLayoutParams(etParams);
        root.addView(modelNameInput);
        TextView hint2b = new TextView(this);
        hint2b.setText("在线API使用的模型名称。常用: deepseek-chat, deepseek-reasoner, gpt-4o, claude-3-opus。不填默认 deepseek-chat。");
        hint2b.setTextColor(0xFF666688);
        hint2b.setTextSize(11);
        hint2b.setPadding(0, 0, 0, 32);
        root.addView(hint2b);

        // 模型模式选择
        TextView label3 = new TextView(this);
        label3.setText("模型模式");
        label3.setTextColor(0xFFCCCCCC);
        label3.setTextSize(14);
        label3.getPaint().setFakeBoldText(true);
        root.addView(label3);

        modelModeGroup = new RadioGroup(this);
        modelModeGroup.setOrientation(LinearLayout.HORIZONTAL);
        modelModeGroup.setPadding(0, 8, 0, 8);

        String currentMode = prefs.getString("model_mode", "auto");
        String[] modes = {"auto", "local", "online"};
        String[] modeNames = {"自动", "本地模型", "在线模型"};
        for (int i = 0; i < modes.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(modeNames[i]);
            rb.setTextColor(0xFFCCCCCC);
            rb.setTextSize(13);
            rb.setTag(modes[i]);
            if (modes[i].equals(currentMode)) rb.setChecked(true);
            modelModeGroup.addView(rb);
        }
        root.addView(modelModeGroup);

        TextView hint3 = new TextView(this);
        hint3.setText("自动：有网络且配置了在线API时使用在线模型，否则使用本地模型。\n本地模型：始终使用内置的 DeepSeek-R1-1.5B 离线模型。\n在线模型：始终使用配置的在线 API。");
        hint3.setTextColor(0xFF666688);
        hint3.setTextSize(11);
        hint3.setPadding(0, 0, 0, 32);
        root.addView(hint3);

        // 背景图开关
        TextView label4 = new TextView(this);
        label4.setText("界面设置");
        label4.setTextColor(0xFFCCCCDD);
        label4.setTextSize(14);
        label4.getPaint().setFakeBoldText(true);
        label4.setPadding(0, 24, 0, 12);
        root.addView(label4);

        bgCheckBox = new CheckBox(this);
        bgCheckBox.setText("启用动态壁纸（每分钟更换二次元美女壁纸，半透明）");
        bgCheckBox.setTextColor(0xFFAAAAAA);
        bgCheckBox.setTextSize(13);
        bgCheckBox.setPadding(16, 12, 16, 12);
        bgCheckBox.setChecked(prefs.getBoolean("bg_starry", false));
        root.addView(bgCheckBox);

        // 其他权限
        TextView label5 = new TextView(this);
        label5.setText("高级权限");
        label5.setTextColor(0xFFCCCCDD);
        label5.setTextSize(14);
        label5.getPaint().setFakeBoldText(true);
        label5.setPadding(0, 24, 0, 12);
        root.addView(label5);

        shizukuCheckBox = new CheckBox(this);
        shizukuCheckBox.setText("其他权限（Shizuku）- 开启后可获得更高系统权限，普通使用无需开启");
        shizukuCheckBox.setTextColor(0xFFAAAAAA);
        shizukuCheckBox.setTextSize(12);
        shizukuCheckBox.setPadding(16, 12, 16, 12);
        shizukuCheckBox.setChecked(prefs.getBoolean("shizuku_perm", false));
        root.addView(shizukuCheckBox);

        TextView hint5 = new TextView(this);
        hint5.setText("Shizuku权限需要设备安装Shizuku服务并授权，普通用户无需开启。开启后可执行更多系统级命令。");
        hint5.setTextColor(0xFF666688);
        hint5.setTextSize(10);
        hint5.setPadding(16, 4, 16, 24);
        root.addView(hint5);

        // 按钮行
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button saveBtn = new Button(this);
        saveBtn.setText("保存设置");
        saveBtn.setBackgroundColor(0xFF1E3A5F);
        saveBtn.setTextColor(0xFFFFFFFF);
        saveBtn.setTextSize(14);
        saveBtn.setPadding(48, 24, 48, 24);
        saveBtn.setOnClickListener(v -> saveSettings());
        btnRow.addView(saveBtn);

        Button resetBtn = new Button(this);
        resetBtn.setText("清空");
        resetBtn.setBackgroundColor(0xFF3A1E1E);
        resetBtn.setTextColor(0xFFAAAAAA);
        resetBtn.setTextSize(14);
        resetBtn.setPadding(48, 24, 48, 24);
        resetBtn.setOnClickListener(v -> {
            apiBaseUrlInput.setText("");
            apiKeyInput.setText("");
            Toast.makeText(this, "已清空，点击保存生效", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(resetBtn);

        root.addView(btnRow);

        // 清除数据按钮
        Button clearBtn = new Button(this);
        clearBtn.setText("清除应用数据（重新解压）");
        clearBtn.setBackgroundColor(0xFF2A1A1A);
        clearBtn.setTextColor(0xFFCC6666);
        clearBtn.setTextSize(12);
        clearBtn.setPadding(32, 20, 32, 20);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clearParams.setMargins(0, 32, 0, 0);
        clearBtn.setLayoutParams(clearParams);
        clearBtn.setOnClickListener(v -> clearAppData());
        root.addView(clearBtn);

        // 检测更新按钮
        Button updateBtn = new Button(this);
        updateBtn.setText("🔄 检测更新");
        updateBtn.setBackgroundColor(0xFF1A2A3A);
        updateBtn.setTextColor(0xFF64B5F6);
        updateBtn.setTextSize(13);
        updateBtn.setPadding(32, 20, 32, 20);
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        updateParams.setMargins(0, 16, 0, 0);
        updateBtn.setLayoutParams(updateParams);
        updateBtn.setOnClickListener(v -> checkUpdate());
        root.addView(updateBtn);

        TextView versionInfo = new TextView(this);
        versionInfo.setText("当前版本: v2.44 · ETC+KU终极版\n更新源: GitHub Release\n\n© 2026 ETC | MIT License\n官网: https://etqwfd.github.io/deepseek-harness-mobile");
        versionInfo.setTextColor(0xFF555577);
        versionInfo.setTextSize(10);
        versionInfo.setPadding(16, 8, 16, 0);
        root.addView(versionInfo);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("api_base_url", apiBaseUrlInput.getText().toString().trim());
        editor.putString("api_key", apiKeyInput.getText().toString().trim());
        editor.putString("model_name", modelNameInput.getText().toString().trim());
        int selectedId = modelModeGroup.getCheckedRadioButtonId();
        if (selectedId != -1) {
            RadioButton rb = findViewById(selectedId);
            editor.putString("model_mode", (String) rb.getTag());
        }
        editor.putBoolean("bg_starry", bgCheckBox.isChecked());
        editor.putBoolean("shizuku_perm", shizukuCheckBox.isChecked());
        editor.apply();
        Toast.makeText(this, "设置已保存，返回后生效", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void clearAppData() {
        java.io.File marker = new java.io.File(getFilesDir(), "dsh-app/.extracted");
        if (marker.exists()) marker.delete();
        Toast.makeText(this, "已标记重新解压，重启应用后生效", Toast.LENGTH_LONG).show();
        finish();
    }

    private void checkUpdate() {
        Toast.makeText(this, "正在检测更新...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String repoUrl = "https://api.github.com/repos/ETQWFD/deepseek-harness-mobile/releases/latest";
                java.net.URL url = new java.net.URL(repoUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "DeepSeek-Harness-Android");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    String json = response.toString();
                    // 解析tag_name
                    String tagName = "";
                    String htmlUrl = "";
                    String body = "";
                    int tagIdx = json.indexOf("\"tag_name\"");
                    if (tagIdx >= 0) {
                        int start = json.indexOf("\"", tagIdx + 11) + 1;
                        int end = json.indexOf("\"", start);
                        tagName = json.substring(start, end);
                    }
                    int urlIdx = json.indexOf("\"html_url\"");
                    if (urlIdx >= 0) {
                        int start = json.indexOf("\"", urlIdx + 11) + 1;
                        int end = json.indexOf("\"", start);
                        htmlUrl = json.substring(start, end);
                    }
                    int bodyIdx = json.indexOf("\"body\"");
                    if (bodyIdx >= 0) {
                        int start = json.indexOf("\"", bodyIdx + 7) + 1;
                        int end = json.indexOf("\"", start);
                        if (end - start > 0 && end - start < 500) {
                            body = json.substring(start, end);
                        }
                    }
                    final String latestVersion = tagName.replace("v", "").trim();
                    final String releaseUrl = htmlUrl;
                    final String releaseNotes = body;
                    final String currentVersion = "2.40";
                    runOnUiThread(() -> {
                        if (latestVersion.isEmpty()) {
                            Toast.makeText(SettingsActivity.this, "无法获取版本信息", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // 比较版本
                        String[] currentParts = currentVersion.split("\\.");
                        String[] latestParts = latestVersion.split("\\.");
                        boolean hasUpdate = false;
                        for (int i = 0; i < Math.max(currentParts.length, latestParts.length); i++) {
                            int c = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                            int l = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                            if (l > c) { hasUpdate = true; break; }
                            if (l < c) break;
                        }
                        if (hasUpdate) {
                            // 显示更新对话框
                            new androidx.appcompat.app.AlertDialog.Builder(SettingsActivity.this)
                                    .setTitle("发现新版本 v" + latestVersion)
                                    .setMessage("当前版本: v" + currentVersion + "\n\n更新内容:\n" + (releaseNotes.isEmpty() ? "暂无更新说明" : releaseNotes) + "\n\n是否前往下载更新？")
                                    .setPositiveButton("立即更新", (dialog, which) -> {
                                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(releaseUrl));
                                        startActivity(browserIntent);
                                    })
                                    .setNegativeButton("暂不更新", null)
                                    .show();
                        } else {
                            Toast.makeText(SettingsActivity.this, "已是最新版本 v" + currentVersion, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else if (responseCode == 404) {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "暂无发布版本", Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "检测更新失败 (HTTP " + responseCode + ")", Toast.LENGTH_SHORT).show());
                }
                conn.disconnect();
            } catch (Exception e) {
                final String err = e.getMessage();
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "检测更新失败: " + err, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}

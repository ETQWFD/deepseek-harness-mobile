package com.et.dsh;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NodeService extends Service {
    private static final String TAG = "DshNodeService";
    private static final String CHANNEL_ID = "dsh_node_channel";
    private static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_STATUS = "com.et.dsh.STATUS";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_LAN_URL = "lan_url";
    public static final String EXTRA_READY = "ready";
    public static final String EXTRA_PROGRESS = "progress";

    private Process nodeProcess;
    private Thread outputThread;
    private boolean running = false;
    private boolean ready = false;
    private String filesDir;
    private String nativeLibDir;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        filesDir = getFilesDir().getAbsolutePath();
        nativeLibDir = getApplicationInfo().nativeLibraryDir;
        createNotificationChannel();
        acquireWakeLock();
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Dsh:NodeWakeLock");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire wakeLock", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("DeepSeek Harness", "正在启动..."));
        if (!running) {
            running = true;
            new Thread(this::startNode).start();
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartIntent = new Intent(getApplicationContext(), NodeService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        try {
            pendingIntent.send();
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart service", e);
        }
        super.onTaskRemoved(rootIntent);
    }

    private void startNode() {
        try {
            broadcastStatus("正在初始化运行环境...", false, null, null, 0);

            // 创建外部工作文件夹 /storage/emulated/0/DeepSeek Harness
            File workDir = new File("/storage/emulated/0/DeepSeek Harness");
            if (!workDir.exists()) {
                workDir.mkdirs();
                new File(workDir, "plugins").mkdirs();
                new File(workDir, "data").mkdirs();
                new File(workDir, "logs").mkdirs();
                Log.d(TAG, "工作文件夹已创建: " + workDir.getAbsolutePath());
            }

            File appDir = new File(filesDir, "dsh-app");
            File markerFile = new File(appDir, ".extracted");
            if (!appDir.exists() || !markerFile.exists() || !new File(appDir, "start.js").exists()) {
                broadcastStatus("正在解压应用文件，请稍候...", false, null, null, 0);
                try {
                    extractAppFiles();
                    try { new FileOutputStream(markerFile).close(); } catch (Exception ignored) {}
                    broadcastStatus("解压完成，正在启动服务...", false, null, null, 100);
                } catch (Exception e) {
                    Log.e(TAG, "解压失败", e);
                    broadcastStatus("解压失败: " + e.getMessage() + "，请清除数据后重试", false, null, null, 0);
                    return;
                }
            }

            // 读取配置
            SharedPreferences prefs = getSharedPreferences("dsh_settings", MODE_PRIVATE);
            String apiBaseUrl = prefs.getString("api_base_url", "");
            String apiKey = prefs.getString("api_key", "");
            String modelMode = prefs.getString("model_mode", "auto"); // auto/local/online

            // 检测网络状态
            boolean hasNetwork = isNetworkAvailable();
            String effectiveMode = modelMode;
            if ("auto".equals(modelMode)) {
                effectiveMode = hasNetwork && !apiBaseUrl.isEmpty() ? "online" : "local";
            }
            Log.d(TAG, "模型模式: " + effectiveMode + " (网络: " + hasNetwork + ")");

            // 使用Android系统的nativeLibraryDir（jniLibs自动提取，有执行权限）
            // 这是终极修复：files目录是noexec挂载，chmod也无法执行
            File libDir = new File(nativeLibDir);
            Log.d(TAG, "使用nativeLibraryDir: " + nativeLibDir);
            // 验证libnode.so存在且可执行
            File nodeLibCheck = new File(libDir, "libnode.so");
            if (nodeLibCheck.exists()) {
                Log.d(TAG, "libnode.so存在: " + nodeLibCheck.getAbsolutePath() + ", 可执行: " + nodeLibCheck.canExecute());
            } else {
                Log.e(TAG, "libnode.so在nativeLibraryDir中不存在！列出目录内容:");
                File[] files = libDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        Log.e(TAG, "  " + f.getName() + " (" + f.length() + " bytes, exec=" + f.canExecute() + ")");
                    }
                }
            }

            String ldLibraryPath = libDir.getAbsolutePath();
            String path = libDir.getAbsolutePath() + ":" + filesDir + "/usr/bin:" + filesDir + "/bin:/system/bin:/system/xbin";
            String home = filesDir;
            String nodePath = new File(appDir, "node_modules").getAbsolutePath();

            // 创建基础Linux目录结构（修复"没有根目录的那些东西"问题）
            broadcastStatus("正在初始化Linux环境...", false, null, null, 5);
            String[] linuxDirs = {
                "bin", "usr/bin", "usr/lib", "usr/local/bin", "usr/local/lib",
                "etc", "tmp", "var", "var/log", "var/tmp", "home", "root",
                "opt", "sbin", "lib", "mnt", "media", "proc", "sys", "dev"
            };
            for (String dir : linuxDirs) {
                File d = new File(filesDir, dir);
                if (!d.exists()) d.mkdirs();
            }
            // 创建基础配置文件
            try {
                File profile = new File(filesDir, "etc/profile");
                if (!profile.exists()) {
                    FileOutputStream pfos = new FileOutputStream(profile);
                    pfos.write(("export PATH=" + path + "\nexport HOME=" + home + "\nexport LD_LIBRARY_PATH=" + ldLibraryPath + "\n").getBytes());
                    pfos.close();
                }
                File hosts = new File(filesDir, "etc/hosts");
                if (!hosts.exists()) {
                    FileOutputStream hfos = new FileOutputStream(hosts);
                    hfos.write("127.0.0.1 localhost\n::1 localhost\n".getBytes());
                    hfos.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "创建基础配置文件失败", e);
            }
            Log.d(TAG, "基础Linux目录结构创建完成");

            String nodeBin = new File(libDir, "libnode.so").getAbsolutePath();

            // 模型路径
            File modelFile = new File(filesDir, "model.gguf");
            File modelMarker = new File(filesDir, ".model_extracted");
            if (!modelMarker.exists()) {
                broadcastStatus("正在解压AI模型，请耐心等待...", false, null, null, 0);
                try {
                    InputStream mis = getAssets().open("model.gguf");
                    long modelTotalSize = 0;
                    try {
                        android.content.res.AssetFileDescriptor afd = getAssets().openFd("model.gguf");
                        modelTotalSize = afd.getLength();
                        afd.close();
                    } catch (Exception e) {
                        modelTotalSize = 500 * 1024 * 1024L; // 估算500MB
                    }
                    Log.d(TAG, "模型总大小: " + modelTotalSize + " bytes");
                    FileOutputStream mos = new FileOutputStream(modelFile);
                    byte[] mbuf = new byte[1024 * 1024]; // 1MB buffer
                    int mlen;
                    long total = 0;
                    int lastProgress = -1;
                    while ((mlen = mis.read(mbuf)) > 0) {
                        mos.write(mbuf, 0, mlen);
                        total += mlen;
                        int progress = (int)(total * 100 / modelTotalSize);
                        if (progress != lastProgress && progress <= 100) {
                            lastProgress = progress;
                            broadcastStatus("正在解压AI模型... " + progress + "%", false, null, null, progress);
                        }
                    }
                    mos.close();
                    mis.close();
                    new FileOutputStream(modelMarker).close();
                    broadcastStatus("AI模型解压完成", false, null, null, 100);
                    Log.d(TAG, "模型解压完成: " + modelFile.length() + " bytes");
                } catch (Exception e) {
                    Log.e(TAG, "模型解压失败", e);
                    broadcastStatus("模型解压失败: " + e.getMessage(), false, null, null, 0);
                }
            }
            String startScript = new File(appDir, "start.js").getAbsolutePath();

            if (!new File(nodeBin).exists()) {
                broadcastStatus("错误: Node.js 运行时不存在 (" + nodeBin + ")", false, null, null, 0);
                return;
            }
            if (!new File(startScript).exists()) {
                broadcastStatus("错误: 启动脚本不存在 (" + startScript + ")", false, null, null, 0);
                return;
            }

            List<String> command = new ArrayList<>();
            command.add(nodeBin);
            command.add(startScript);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("LD_LIBRARY_PATH", ldLibraryPath);
            pb.environment().put("PATH", path);
            pb.environment().put("HOME", home);
            pb.environment().put("NODE_PATH", nodePath);
            pb.environment().put("DSH_ROOT", appDir.getAbsolutePath());
            pb.environment().put("TERM", "xterm");
            // 工作文件夹（限制AI操作范围）
            pb.environment().put("DSH_WORK_DIR", workDir.getAbsolutePath());
            pb.environment().put("DSH_PLUGIN_DIR", new File(workDir, "plugins").getAbsolutePath());
            // llama.cpp 本地模型配置
            pb.environment().put("LLAMA_LIB_DIR", libDir.getAbsolutePath());
            pb.environment().put("LLAMA_MODEL_PATH", modelFile.getAbsolutePath());
            pb.environment().put("MODEL_MODE", effectiveMode);
            pb.environment().put("HAS_NETWORK", String.valueOf(hasNetwork));
            // 传递AI配置 - 使用多种常见环境变量名确保兼容
            if (!apiBaseUrl.isEmpty()) {
                pb.environment().put("DSH_API_BASE_URL", apiBaseUrl);
                pb.environment().put("OPENAI_BASE_URL", apiBaseUrl);
                pb.environment().put("DEEPSEEK_BASE_URL", apiBaseUrl);
                pb.environment().put("OPENAI_API_BASE", apiBaseUrl);
                pb.environment().put("API_BASE_URL", apiBaseUrl);
            }
            if (!apiKey.isEmpty()) {
                pb.environment().put("DSH_API_KEY", apiKey);
                pb.environment().put("OPENAI_API_KEY", apiKey);
                pb.environment().put("DEEPSEEK_API_KEY", apiKey);
                pb.environment().put("API_KEY", apiKey);
            }
            pb.directory(appDir);
            pb.redirectErrorStream(true);

            nodeProcess = pb.start();
            outputThread = new Thread(() -> readProcessOutput(nodeProcess));
            outputThread.start();

        } catch (Exception e) {
            Log.e(TAG, "启动失败", e);
            broadcastStatus("启动失败: " + e.getMessage(), false, null, null, 0);
        }
    }

    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void extractAppFiles() throws IOException {
        File appDir = new File(filesDir, "dsh-app");
        if (appDir.exists()) deleteRecursive(appDir);
        appDir.mkdirs();

        // 从assets复制zip到本地
        InputStream is = getAssets().open("dsh-app.zip");
        File zipFile = new File(filesDir, "dsh-app.zip");
        FileOutputStream fos = new FileOutputStream(zipFile);
        byte[] buffer = new byte[128 * 1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fos.close();
        is.close();

        // 用Android原生ZipFile解压（绝对可靠）
        ZipFile zip = new ZipFile(zipFile);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        int totalEntries = 0;
        int extractedCount = 0;
        // 先统计总数
        List<ZipEntry> entryList = new ArrayList<>();
        while (entries.hasMoreElements()) {
            entryList.add(entries.nextElement());
        }
        totalEntries = entryList.size();

        byte[] buf = new byte[64 * 1024];
        int lastPercent = -1;
        for (ZipEntry entry : entryList) {
            String name = entry.getName();
            // 安全检查：防止路径遍历
            if (name.contains("..")) {
                extractedCount++;
                continue;
            }
            File outFile = new File(appDir, name);
            // 再次安全检查
            if (!outFile.getCanonicalPath().startsWith(appDir.getCanonicalPath())) {
                extractedCount++;
                continue;
            }
            if (entry.isDirectory()) {
                outFile.mkdirs();
            } else {
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                InputStream zis = zip.getInputStream(entry);
                OutputStream os = new FileOutputStream(outFile);
                int readLen;
                while ((readLen = zis.read(buf)) > 0) {
                    os.write(buf, 0, readLen);
                }
                os.close();
                zis.close();
                // 设置可执行权限
                if (name.endsWith(".sh") || name.contains("/bin/")) {
                    outFile.setExecutable(true, false);
                }
            }
            extractedCount++;
            int percent = (int) (extractedCount * 100L / totalEntries);
            if (percent != lastPercent && percent % 5 == 0) {
                lastPercent = percent;
                broadcastStatus("正在解压应用文件... " + percent + "%", false, null, null, percent);
            }
        }
        zip.close();
        zipFile.delete();
        Log.d(TAG, "应用文件解压完成");
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }

    private String getDeviceAbi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String[] abis = Build.SUPPORTED_ABIS;
            if (abis != null && abis.length > 0) {
                return abis[0];
            }
        }
        return Build.CPU_ABI;
    }

    private void extractNativeLibs(File libDir) throws IOException {
        if (libDir.exists()) deleteRecursive(libDir);
        libDir.mkdirs();

        String abi = getDeviceAbi();
        String zipName = "native-comp-" + abi + ".zip";
        Log.d(TAG, "设备ABI: " + abi + ", 解压: " + zipName);

        // 尝试从assets解压
        InputStream is = null;
        try {
            is = getAssets().open(zipName);
        } catch (IOException e) {
            // 如果精确匹配失败，尝试备选架构
            String fallback = null;
            if (abi.contains("arm64")) fallback = "native-comp-arm64-v8a.zip";
            else if (abi.contains("arm")) fallback = "native-comp-armeabi-v7a.zip";
            else if (abi.contains("x86_64")) fallback = "native-comp-x86_64.zip";
            else if (abi.contains("x86")) fallback = "native-comp-x86.zip";
            if (fallback != null) {
                try {
                    is = getAssets().open(fallback);
                    Log.d(TAG, "使用备选架构: " + fallback);
                } catch (IOException e2) {
                    Log.e(TAG, "找不到原生库: " + zipName + " 或 " + fallback);
                    throw new IOException("设备架构 " + abi + " 不支持，请使用 arm64 设备");
                }
            } else {
                throw e;
            }
        }

        File zipFile = new File(filesDir, "native-libs-tmp.zip");
        FileOutputStream fos = new FileOutputStream(zipFile);
        byte[] buf = new byte[128 * 1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            fos.write(buf, 0, len);
        }
        fos.close();
        is.close();

        ZipFile zip = new ZipFile(zipFile);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        byte[] extractBuf = new byte[64 * 1024];
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            File outFile = new File(libDir, entry.getName());
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            InputStream zis = zip.getInputStream(entry);
            OutputStream os = new FileOutputStream(outFile);
            int readLen;
            while ((readLen = zis.read(extractBuf)) > 0) {
                os.write(extractBuf, 0, readLen);
            }
            os.close();
            zis.close();
        }
        zip.close();
        zipFile.delete();
        Log.d(TAG, "原生库解压完成: " + libDir.getAbsolutePath() + ", 文件数: " + libDir.list().length);

        // 关键：设置所有原生库的执行权限（修复 error=13 Permission denied）
        Log.d(TAG, "设置原生库执行权限...");
        File[] libFiles = libDir.listFiles();
        if (libFiles != null) {
            for (File f : libFiles) {
                if (f.isFile()) {
                    f.setReadable(true, false);
                    f.setExecutable(true, false);
                    f.setWritable(true, false);
                }
            }
        }
        // 额外用chmod命令确保权限正确
        try {
            Process chmod = Runtime.getRuntime().exec("chmod 755 " + libDir.getAbsolutePath() + "/*");
            chmod.waitFor();
            Log.d(TAG, "chmod 755 执行完成");
        } catch (Exception e) {
            Log.w(TAG, "chmod命令失败，使用Java权限设置", e);
        }

        // 验证libnode.so权限
        File nodeLib = new File(libDir, "libnode.so");
        if (nodeLib.exists()) {
            boolean canExecute = nodeLib.canExecute();
            Log.d(TAG, "libnode.so 可执行: " + canExecute + ", 路径: " + nodeLib.getAbsolutePath());
            if (!canExecute) {
                Log.e(TAG, "libnode.so 仍然无法执行！尝试再次设置...");
                nodeLib.setExecutable(true, false);
            }
        } else {
            Log.e(TAG, "libnode.so 不存在！路径: " + nodeLib.getAbsolutePath());
        }
    }

    private void readProcessOutput(Process process) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String lanUrl = null;
            String localUrl = null;
            while ((line = reader.readLine()) != null) {
                Log.d(TAG, "node: " + line);
                if (line.startsWith("DSH_LAN_URL=")) {
                    lanUrl = line.substring("DSH_LAN_URL=".length()).trim();
                } else if (line.startsWith("DSH_LOCAL_URL=")) {
                    localUrl = line.substring("DSH_LOCAL_URL=".length()).trim();
                } else if (line.startsWith("DSH_READY=1") && !ready) {
                    ready = true;
                    if (lanUrl == null) lanUrl = "http://" + getLANIP() + ":8080";
                    if (localUrl == null) localUrl = "http://127.0.0.1:3080";
                    broadcastStatus("服务已启动！局域网访问：" + lanUrl, true, localUrl, lanUrl, 100);
                    updateNotification("DeepSeek Harness 运行中", lanUrl);
                }
                if (!line.startsWith("DSH_")) {
                    broadcastStatus(line, false, null, null, 0);
                }
            }
            int exitCode = process.waitFor();
            Log.d(TAG, "Node进程退出，代码: " + exitCode);
            if (running && !ready) {
                broadcastStatus("进程异常退出 (code=" + exitCode + ")，3秒后自动重启...", false, null, null, 0);
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                if (running) {
                    ready = false;
                    new Thread(this::startNode).start();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "读取输出失败", e);
        }
    }

    private String getLANIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取IP失败", e);
        }
        return "127.0.0.1";
    }

    private void broadcastStatus(String message, boolean isReady, String localUrl, String lanUrl, int progress) {
        Intent intent = new Intent(ACTION_STATUS);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_READY, isReady);
        intent.putExtra(EXTRA_PROGRESS, progress);
        if (localUrl != null) intent.putExtra(EXTRA_URL, localUrl);
        if (lanUrl != null) intent.putExtra(EXTRA_LAN_URL, lanUrl);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "DeepSeek Harness", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Node.js 后台服务");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String title, String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(title, text));
    }

    @Override
    public void onDestroy() {
        running = false;
        if (nodeProcess != null) nodeProcess.destroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

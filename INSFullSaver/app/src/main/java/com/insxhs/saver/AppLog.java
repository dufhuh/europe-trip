package com.insxhs.saver;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class AppLog {
    private static final String FILE_NAME = "last_run.log";
    private final Context context;
    private final File file;

    AppLog(Context context) {
        this.context = context.getApplicationContext();
        file = new File(this.context.getFilesDir(), FILE_NAME);
    }

    synchronized void reset() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writer.write("INSDL " + appVersion() + "\n");
            writer.write("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n");
            writer.write("Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n");
            writer.write("Cookies: content intentionally omitted\n");
        } catch (IOException ignored) {
        }
    }

    synchronized void line(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            writer.write(time + "  " + sanitize(message) + "\n");
        } catch (IOException ignored) {
        }
    }

    synchronized String read() {
        try {
            return new String(
                    java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return "暂无诊断日志";
        }
    }

    private String appVersion() {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "unknown" : info.versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("(?i)sessionid=[^;\\s]+", "sessionid=[REDACTED]")
                .replaceAll("(?i)csrftoken=[^;\\s]+", "csrftoken=[REDACTED]");
    }
}

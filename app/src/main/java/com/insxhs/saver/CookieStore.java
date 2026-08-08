package com.insxhs.saver;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class CookieStore {
    private static final String FILE_NAME = "instagram_cookies.txt";
    private final Context context;

    CookieStore(Context context) {
        this.context = context.getApplicationContext();
    }

    File file() {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    boolean existsAndValid() {
        try {
            return parse(file()).containsKey("sessionid");
        } catch (Exception ignored) {
            return false;
        }
    }

    void importFrom(Uri uri) throws IOException {
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(temp)) {
            if (input == null) {
                throw new IOException("无法读取所选文件");
            }
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        }

        Map<String, String> cookies = parse(temp);
        String sessionId = cookies.get("sessionid");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            temp.delete();
            throw new IOException("文件中未找到有效的 Instagram sessionid。请导出 Netscape cookies.txt 格式。");
        }

        File target = file();
        if (target.exists() && !target.delete()) {
            temp.delete();
            throw new IOException("无法替换旧 cookies 文件");
        }
        if (!temp.renameTo(target)) {
            try (FileInputStream input = new FileInputStream(temp);
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
            }
            temp.delete();
        }
        target.setReadable(false, false);
        target.setReadable(true, true);
        target.setWritable(false, false);
        target.setWritable(true, true);
    }

    void clear() {
        File target = file();
        if (target.exists()) {
            target.delete();
        }
    }

    String cookieHeader() throws IOException {
        Map<String, String> cookies = parse(file());
        if (!cookies.containsKey("sessionid")) {
            throw new IOException("尚未导入有效 cookies");
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    String csrfToken() throws IOException {
        String value = parse(file()).get("csrftoken");
        return value == null ? "" : value;
    }

    private static Map<String, String> parse(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("cookies 文件不存在");
        }

        Map<String, String> result = new LinkedHashMap<>();
        long nowSeconds = System.currentTimeMillis() / 1000L;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#HttpOnly_")) {
                    line = line.substring("#HttpOnly_".length());
                } else if (line.startsWith("#")) {
                    continue;
                }

                String[] fields = line.split("\\t", 7);
                if (fields.length < 7) {
                    continue;
                }
                String domain = fields[0].toLowerCase();
                if (!domain.equals("instagram.com") && !domain.endsWith(".instagram.com")) {
                    continue;
                }

                long expiry = 0L;
                try {
                    expiry = Long.parseLong(fields[4]);
                } catch (NumberFormatException ignored) {
                }
                if (expiry > 0 && expiry < nowSeconds) {
                    continue;
                }

                String name = fields[5].trim();
                String value = fields[6].trim();
                if (!name.isEmpty() && !value.isEmpty()) {
                    result.put(name, value);
                }
            }
        }
        return result;
    }
}

package com.insxhs.saver;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Locale;

final class MediaSaver {
    interface ProgressCallback {
        void onProgress(int completed, int total, String message);
    }

    private final Context context;
    private final InstagramClient client;
    private final AppLog log;

    MediaSaver(Context context, InstagramClient client, AppLog log) {
        this.context = context.getApplicationContext();
        this.client = client;
        this.log = log;
    }

    int savePost(InstagramClient.Post post, ProgressCallback callback) throws IOException {
        String postUrl = "https://www.instagram.com/p/" + post.shortcode + "/";
        int total = post.media.size();
        int saved = 0;

        for (int index = 0; index < total; index++) {
            InstagramClient.MediaItem item = post.media.get(index);
            String filename = String.format(Locale.US, "%s_%02d.%s",
                    post.shortcode, index + 1, item.extension());
            callback.onProgress(saved, total, "正在保存 " + (index + 1) + "/" + total);
            saveOne(item, filename, post.shortcode, postUrl);
            saved++;
            log.line("Saved " + filename);
            callback.onProgress(saved, total, "已保存 " + saved + "/" + total);
        }
        return saved;
    }

    private void saveOne(
            InstagramClient.MediaItem item,
            String filename,
            String shortcode,
            String postUrl
    ) throws IOException {
        HttpURLConnection connection = client.openMedia(item.url, postUrl);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("下载媒体失败（HTTP " + status + "）");
        }

        String folder = Environment.DIRECTORY_PICTURES + "/INS全图保存/" + shortcode;
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType());
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
        values.put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000L);
        values.put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        if (!item.video) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        }

        ContentResolver resolver = context.getContentResolver();
        Uri collection = item.video
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = resolver.insert(collection, values);
        if (uri == null) {
            connection.disconnect();
            throw new IOException("Android 媒体库拒绝创建文件");
        }

        boolean success = false;
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IOException("无法写入 Android 媒体库");
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            output.flush();
            success = true;
        } finally {
            connection.disconnect();
            if (!success) {
                resolver.delete(uri, null, null);
            }
        }

        ContentValues complete = new ContentValues();
        complete.put(MediaStore.MediaColumns.IS_PENDING, 0);
        complete.put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L);
        resolver.update(uri, complete, null, null);
    }
}

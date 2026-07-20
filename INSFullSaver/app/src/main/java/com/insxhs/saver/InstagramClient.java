package com.insxhs.saver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InstagramClient {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?instagram\\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE);
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36";

    static final class MediaItem {
        final String url;
        final boolean video;

        MediaItem(String url, boolean video) {
            this.url = url;
            this.video = video;
        }

        String extension() {
            return video ? "mp4" : "jpg";
        }

        String mimeType() {
            return video ? "video/mp4" : "image/jpeg";
        }
    }

    static final class Post {
        final String shortcode;
        final String username;
        final List<MediaItem> media;

        Post(String shortcode, String username, List<MediaItem> media) {
            this.shortcode = shortcode;
            this.username = username;
            this.media = Collections.unmodifiableList(media);
        }
    }

    private final CookieStore cookies;
    private final AppLog log;

    InstagramClient(CookieStore cookies, AppLog log) {
        this.cookies = cookies;
        this.log = log;
    }

    String extractPostUrl(String text) throws IOException {
        if (text == null) {
            throw new IOException("没有收到 Instagram 链接");
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new IOException("未识别到 Instagram 帖子或 Reel 链接");
        }
        return matcher.group(0);
    }

    Post fetchPost(String sharedText) throws IOException, JSONException {
        String postUrl = extractPostUrl(sharedText);
        Matcher matcher = URL_PATTERN.matcher(postUrl);
        if (!matcher.find()) {
            throw new IOException("无法提取帖子编号");
        }
        String shortcode = matcher.group(1);
        String mediaId = shortcodeToMediaId(shortcode);
        String endpoint = "https://www.instagram.com/api/v1/media/" + mediaId + "/info/";
        log.line("Fetching post metadata for shortcode " + shortcode);

        HttpURLConnection connection = open(endpoint, postUrl);
        int status = connection.getResponseCode();
        String response = readText(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();

        if (status == 401 || status == 403) {
            throw new IOException("Instagram 拒绝了登录态（HTTP " + status + "）。请重新导出 cookies。");
        }
        if (status < 200 || status >= 300) {
            throw new IOException("读取帖子失败（HTTP " + status + "）：" + shortError(response));
        }

        JSONObject root = new JSONObject(response);
        JSONArray items = root.optJSONArray("items");
        if (items == null || items.length() == 0) {
            String message = root.optString("message", "Instagram 没有返回帖子内容");
            throw new IOException(message);
        }

        JSONObject post = items.getJSONObject(0);
        String username = "instagram";
        JSONObject user = post.optJSONObject("user");
        if (user != null) {
            username = user.optString("username", username);
        }

        List<MediaItem> media = new ArrayList<>();
        JSONArray carousel = post.optJSONArray("carousel_media");
        if (carousel != null && carousel.length() > 0) {
            for (int index = 0; index < carousel.length(); index++) {
                media.add(parseMedia(carousel.getJSONObject(index)));
            }
        } else {
            media.add(parseMedia(post));
        }

        if (media.isEmpty()) {
            throw new IOException("帖子中没有可下载的图片或视频");
        }
        log.line("Metadata loaded: " + media.size() + " media item(s)");
        return new Post(shortcode, username, media);
    }

    HttpURLConnection openMedia(String mediaUrl, String postUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(mediaUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(25_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Referer", postUrl);
        return connection;
    }

    private HttpURLConnection open(String endpoint, String referer) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(25_000);
        connection.setReadTimeout(45_000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");
        connection.setRequestProperty("Cookie", cookies.cookieHeader());
        connection.setRequestProperty("X-CSRFToken", cookies.csrfToken());
        connection.setRequestProperty("X-IG-App-ID", "936619743392459");
        connection.setRequestProperty("X-ASBD-ID", "129477");
        connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        connection.setRequestProperty("Referer", referer);
        return connection;
    }

    private static MediaItem parseMedia(JSONObject object) throws JSONException, IOException {
        int type = object.optInt("media_type", 1);
        boolean video = type == 2 || object.optBoolean("is_video", false);
        if (video) {
            JSONArray versions = object.optJSONArray("video_versions");
            String url = bestCandidate(versions);
            if (url == null) {
                throw new IOException("某段视频没有可用下载地址");
            }
            return new MediaItem(url, true);
        }

        JSONObject imageVersions = object.optJSONObject("image_versions2");
        JSONArray candidates = imageVersions == null ? null : imageVersions.optJSONArray("candidates");
        String url = bestCandidate(candidates);
        if (url == null) {
            throw new IOException("某张图片没有可用下载地址");
        }
        return new MediaItem(url, false);
    }

    private static String bestCandidate(JSONArray candidates) throws JSONException {
        if (candidates == null || candidates.length() == 0) {
            return null;
        }
        long bestArea = -1L;
        String bestUrl = null;
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.getJSONObject(i);
            long width = candidate.optLong("width", 0L);
            long height = candidate.optLong("height", 0L);
            long area = width * height;
            String url = candidate.optString("url", null);
            if (url != null && (bestUrl == null || area > bestArea)) {
                bestArea = area;
                bestUrl = url.replace("\\u0026", "&");
            }
        }
        return bestUrl;
    }

    private static String shortcodeToMediaId(String shortcode) throws IOException {
        BigInteger value = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(64L);
        for (int i = 0; i < shortcode.length(); i++) {
            int digit = ALPHABET.indexOf(shortcode.charAt(i));
            if (digit < 0) {
                throw new IOException("帖子编号包含无效字符");
            }
            value = value.multiply(base).add(BigInteger.valueOf(digit));
        }
        return value.toString();
    }

    private static String readText(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedInputStream input = new BufferedInputStream(stream);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String shortError(String response) {
        if (response == null || response.isEmpty()) {
            return "无响应内容";
        }
        String compact = response.replaceAll("\\s+", " ").trim();
        return compact.length() > 180 ? compact.substring(0, 180) + "…" : compact;
    }
}

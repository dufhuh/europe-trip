package com.insxhs.saver;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int REQUEST_COOKIES = 1001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private CookieStore cookieStore;
    private AppLog log;
    private EditText urlInput;
    private TextView cookieState;
    private TextView status;
    private ProgressBar progress;
    private Button importCookiesButton;
    private Button downloadButton;
    private Button clearCookiesButton;
    private Button copyLogButton;
    private String pendingSharedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cookieStore = new CookieStore(this);
        log = new AppLog(this);
        createUi();
        updateCookieState();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void createUi() {
        int padding = dp(20);
        int gap = dp(12);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("INS 全图保存", 26, true);
        root.addView(title);

        TextView intro = text(
                "导入一次 Instagram cookies.txt。以后在 Instagram 里点击“分享”并选择本应用，即可按轮播顺序保存全部图片和视频到系统相册。",
                15,
                false);
        intro.setTextColor(Color.DKGRAY);
        addWithTopMargin(root, intro, gap);

        cookieState = text("", 15, true);
        addWithTopMargin(root, cookieState, dp(22));

        importCookiesButton = button("导入 cookies.txt");
        importCookiesButton.setOnClickListener(view -> chooseCookieFile());
        addWithTopMargin(root, importCookiesButton, gap);

        clearCookiesButton = button("清除登录态");
        clearCookiesButton.setOnClickListener(view -> {
            if (busy.get()) {
                toast("下载过程中不能清除 cookies");
                return;
            }
            cookieStore.clear();
            updateCookieState();
            setStatus("已清除本机保存的 cookies。", false);
        });
        addWithTopMargin(root, clearCookiesButton, dp(8));

        TextView urlLabel = text("Instagram 帖子链接", 15, true);
        addWithTopMargin(root, urlLabel, dp(26));

        urlInput = new EditText(this);
        urlInput.setHint("https://www.instagram.com/p/…/");
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setSingleLine(false);
        urlInput.setMinLines(2);
        urlInput.setMaxLines(4);
        urlInput.setTextSize(15);
        urlInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        addWithTopMargin(root, urlInput, dp(8));

        downloadButton = button("下载全部媒体到相册");
        downloadButton.setOnClickListener(view -> startDownload(urlInput.getText().toString()));
        addWithTopMargin(root, downloadButton, gap);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        addWithTopMargin(root, progress, dp(18));

        status = text("准备就绪。", 15, false);
        status.setTextIsSelectable(true);
        addWithTopMargin(root, status, gap);

        copyLogButton = button("复制诊断信息");
        copyLogButton.setOnClickListener(view -> copyDiagnosticLog());
        addWithTopMargin(root, copyLogButton, dp(24));

        TextView privacy = text(
                "隐私说明：cookies 仅复制到本应用的私有沙盒，不会写入相册、日志或上传到其他服务器。应用只直接连接 Instagram 与其媒体 CDN。",
                13,
                false);
        privacy.setTextColor(Color.GRAY);
        addWithTopMargin(root, privacy, gap);

        setContentView(scroll);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        String type = intent.getType();
        if (type == null || !type.startsWith("text/")) {
            return;
        }
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared == null || shared.trim().isEmpty()) {
            return;
        }
        pendingSharedText = shared;
        urlInput.setText(shared);
        if (cookieStore.existsAndValid()) {
            startDownload(shared);
        } else {
            setStatus("已收到 Instagram 链接。请先导入有效 cookies.txt，导入后会自动开始下载。", false);
        }
    }

    private void chooseCookieFile() {
        if (busy.get()) {
            toast("请等待当前下载完成");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_COOKIES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_COOKIES || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            setStatus("未选择 cookies 文件。", true);
            return;
        }
        try {
            cookieStore.importFrom(uri);
            updateCookieState();
            setStatus("cookies 已导入。可以粘贴链接或从 Instagram 分享帖子。", false);
            String toDownload = pendingSharedText;
            pendingSharedText = null;
            if (toDownload != null && !toDownload.trim().isEmpty()) {
                startDownload(toDownload);
            }
        } catch (IOException exception) {
            cookieStore.clear();
            updateCookieState();
            setStatus(exception.getMessage(), true);
        }
    }

    private void startDownload(String sharedText) {
        if (!busy.compareAndSet(false, true)) {
            toast("已有下载任务正在运行");
            return;
        }
        if (!cookieStore.existsAndValid()) {
            busy.set(false);
            pendingSharedText = sharedText;
            setStatus("请先导入包含 sessionid 的 Instagram cookies.txt。", true);
            chooseCookieFile();
            return;
        }

        setControlsEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        setStatus("正在读取帖子信息……", false);
        log.reset();

        executor.execute(() -> {
            try {
                InstagramClient client = new InstagramClient(cookieStore, log);
                String normalizedUrl = client.extractPostUrl(sharedText);
                runOnUiThread(() -> urlInput.setText(normalizedUrl));

                InstagramClient.Post post = client.fetchPost(normalizedUrl);
                runOnUiThread(() -> {
                    progress.setIndeterminate(false);
                    progress.setMax(post.media.size());
                    progress.setProgress(0);
                    setStatus("已识别 " + post.media.size() + " 个媒体文件，开始保存……", false);
                });

                MediaSaver saver = new MediaSaver(this, client, log);
                int count = saver.savePost(post, (completed, total, message) -> runOnUiThread(() -> {
                    progress.setIndeterminate(false);
                    progress.setMax(total);
                    progress.setProgress(completed);
                    setStatus(message, false);
                }));

                log.line("Completed successfully");
                runOnUiThread(() -> {
                    progress.setProgress(count);
                    setStatus("完成：已保存 " + count + " 个文件。相册位置：图片/INS全图保存/" + post.shortcode, false);
                    toast("已保存到相册：" + count + " 个文件");
                });
            } catch (IOException | JSONException exception) {
                log.line("ERROR: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                runOnUiThread(() -> setStatus("下载失败：" + safeMessage(exception), true));
            } catch (RuntimeException exception) {
                log.line("ERROR: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                runOnUiThread(() -> setStatus("发生意外错误。请复制诊断信息发给我。", true));
            } finally {
                busy.set(false);
                runOnUiThread(() -> setControlsEnabled(true));
            }
        });
    }

    private void updateCookieState() {
        boolean ready = cookieStore.existsAndValid();
        cookieState.setText(ready ? "登录态：已导入 ✓" : "登录态：尚未导入");
        cookieState.setTextColor(ready ? Color.rgb(24, 120, 68) : Color.rgb(180, 50, 50));
        clearCookiesButton.setEnabled(ready && !busy.get());
    }

    private void setControlsEnabled(boolean enabled) {
        importCookiesButton.setEnabled(enabled);
        downloadButton.setEnabled(enabled);
        urlInput.setEnabled(enabled);
        clearCookiesButton.setEnabled(enabled && cookieStore.existsAndValid());
        copyLogButton.setEnabled(true);
    }

    private void setStatus(String message, boolean error) {
        status.setText(message == null ? "" : message);
        status.setTextColor(error ? Color.rgb(180, 40, 40) : Color.rgb(45, 45, 45));
    }

    private void copyDiagnosticLog() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("INS 全图保存诊断", log.read()));
        toast("诊断信息已复制，不包含 cookies 内容");
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setLineSpacing(0f, 1.16f);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private void addWithTopMargin(LinearLayout parent, View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        parent.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }
}

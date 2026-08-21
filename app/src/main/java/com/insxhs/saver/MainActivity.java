package com.insxhs.saver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int REQUEST_COOKIES = 1001;
    private static final String VERSION = "1.4.5";

    private static final int BG = Color.parseColor("#FFFFFF");
    private static final int PANEL = Color.parseColor("#FFFFFF");
    private static final int PANEL_2 = Color.parseColor("#F7F7F7");
    private static final int PANEL_PRESSED = Color.parseColor("#EEEEEE");
    private static final int TEXT = Color.parseColor("#151515");
    private static final int MUTED = Color.parseColor("#6B6B6F");
    private static final int SUBTLE = Color.parseColor("#9A9A9F");
    private static final int LINE = Color.parseColor("#E7E7E7");
    private static final int LINE_STRONG = Color.parseColor("#DDDDDD");
    private static final int GREEN = Color.parseColor("#16803A");
    private static final int GREEN_BG = Color.parseColor("#EFF8F1");
    private static final int RED = Color.parseColor("#B42318");
    private static final int RED_BG = Color.parseColor("#FDF1EF");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private CookieStore cookieStore;
    private AppLog log;
    private EditText urlInput;
    private TextView status;
    private TextView statusDetail;
    private TextView statusPercent;
    private ImageView statusIcon;
    private LinearLayout downloadStateCard;
    private ProgressBar progress;
    private View downloadButton;
    private LinearLayout recentList;
    private String pendingSharedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        cookieStore = new CookieStore(this);
        log = new AppLog(this);
        createUi();
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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(4), dp(20), dp(30));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int statusBar = insets.getSystemWindowInsetTop();
            int navigationBar = insets.getSystemWindowInsetBottom();
            view.setPadding(dp(20), statusBar + dp(4), dp(20), Math.max(dp(30), navigationBar + dp(18)));
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.requestApplyInsets();

        root.addView(buildTopBar());
        add(root, buildHero(), 22);
        add(root, buildLinkField(), 23);

        downloadButton = primaryButton();
        downloadButton.setOnClickListener(v -> startDownload(urlInput.getText().toString()));
        add(root, downloadButton, 10);

        downloadStateCard = buildDownloadState();
        add(root, downloadStateCard, 10);

        add(root, buildQuickActions(), 30);
        add(root, buildRecentHeader(), 30);

        recentList = new LinearLayout(this);
        recentList.setOrientation(LinearLayout.VERTICAL);
        add(root, recentList, 9);
        renderRecent();
        setContentView(scroll);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(dp(54));

        ImageView settings = icon(R.drawable.ic_settings, 20);
        settings.setPadding(dp(10), dp(10), dp(10), dp(10));
        settings.setClickable(true);
        settings.setFocusable(true);
        settings.setContentDescription("Settings");
        settings.setBackground(pressedRounded(Color.TRANSPARENT, PANEL_2, 14));
        settings.setOnClickListener(v -> showSettings());
        bar.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text("INSDL", 16, true, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        title.setLetterSpacing(-0.01f);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.START);
        hero.setPadding(dp(4), 0, dp(4), 0);
        TextView title = text("Instagram Downloader", 28, true, TEXT);
        title.setGravity(Gravity.START);
        title.setIncludeFontPadding(false);
        title.setLetterSpacing(-0.02f);
        hero.addView(title);
        TextView subtitle = text("Paste a link to download all images from Instagram posts and carousels.", 14, false, MUTED);
        subtitle.setGravity(Gravity.START);
        subtitle.setLineSpacing(0f, 1.42f);
        add(hero, subtitle, 9);
        return hero;
    }

    private View buildLinkField() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.CENTER_VERTICAL);
        shell.setMinimumHeight(dp(60));
        shell.setBackground(insetRounded(PANEL, LINE_STRONG, 26));
        shell.setPadding(dp(12), dp(7), dp(8), dp(7));
        ImageView linkIcon = softIcon(R.drawable.ic_link, 34, 18, 13);
        shell.addView(linkIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        urlInput = new EditText(this);
        urlInput.setHint("Paste Instagram link here");
        urlInput.setHintTextColor(SUBTLE);
        urlInput.setTextColor(TEXT);
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setSingleLine(true);
        urlInput.setBackground(null);
        urlInput.setPadding(dp(12), 0, dp(8), 0);
        shell.addView(urlInput, new LinearLayout.LayoutParams(0, dp(42), 1f));

        TextView paste = text("Paste", 13, true, TEXT);
        paste.setGravity(Gravity.CENTER);
        paste.setIncludeFontPadding(false);
        paste.setClickable(true);
        paste.setFocusable(true);
        paste.setBackground(pressedRounded(PANEL_2, PANEL_PRESSED, 17));
        paste.setOnClickListener(v -> pasteFromClipboard());
        shell.addView(paste, new LinearLayout.LayoutParams(dp(66), dp(42)));
        return shell;
    }

    private View primaryButton() {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setMinimumHeight(dp(56));
        button.setElevation(0f);
        button.setStateListAnimator(null);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, insetRounded(PANEL_2, Color.parseColor("#ECECEC"), 24));
        states.addState(new int[]{android.R.attr.state_pressed}, insetRounded(PANEL_PRESSED, LINE_STRONG, 24));
        states.addState(new int[]{}, insetRounded(PANEL_2, LINE_STRONG, 24));
        button.setBackground(states);
        ImageView download = icon(R.drawable.ic_download, 18);
        button.addView(download, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView label = text("Download", 15, true, TEXT);
        label.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dp(9);
        button.addView(label, labelParams);
        return button;
    }

    private LinearLayout buildDownloadState() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(insetRounded(PANEL_2, LINE, 22));
        card.setVisibility(View.GONE);

        statusIcon = softIcon(R.drawable.ic_download, 34, 17, 13);
        statusIcon.setImageTintList(ColorStateList.valueOf(MUTED));
        card.addView(statusIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(11);
        card.addView(copy, copyParams);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = text("", 14, true, TEXT);
        status.setIncludeFontPadding(false);
        status.setTextIsSelectable(true);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusPercent = text("", 12, false, MUTED);
        statusPercent.setIncludeFontPadding(false);
        statusPercent.setGravity(Gravity.END);
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        percentParams.leftMargin = dp(8);
        titleRow.addView(statusPercent, percentParams);

        statusDetail = text("", 12, false, MUTED);
        statusDetail.setIncludeFontPadding(false);
        statusDetail.setSingleLine(true);
        statusDetail.setEllipsize(TextUtils.TruncateAt.END);
        statusDetail.setVisibility(View.GONE);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(3);
        copy.addView(statusDetail, detailParams);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        progress.setProgressTintList(ColorStateList.valueOf(TEXT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#DEDEDE")));
        progress.setIndeterminateTintList(ColorStateList.valueOf(TEXT));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.topMargin = dp(8);
        copy.addView(progress, progressParams);
        return card;
    }

    private View buildQuickActions() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(insetRounded(PANEL, LINE, 26));
        View instagram = actionRow(R.drawable.ic_instagram, "Open Instagram", "Share a post to INSDL from Instagram", MUTED);
        instagram.setOnClickListener(v -> openInstagram());
        card.addView(instagram, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        addDivider(card, 64, 14);
        boolean valid = cookieStore.existsAndValid();
        View cookies = actionRow(R.drawable.ic_cookie, "Cookies", valid ? "Valid" : "Not imported", valid ? GREEN : MUTED);
        cookies.setOnClickListener(v -> chooseCookieFile());
        card.addView(cookies, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        return card;
    }

    private View actionRow(int iconRes, String titleText, String subtitleText, int subtitleColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(pressedRounded(Color.TRANSPARENT, Color.parseColor("#FAFAFA"), 18));
        ImageView itemIcon = softIcon(iconRes, 38, 20, 14);
        row.addView(itemIcon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(12);
        row.addView(labels, labelsParams);
        TextView title = text(titleText, 14, true, TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setIncludeFontPadding(false);
        labels.addView(title);
        TextView subtitle = text(subtitleText, 12, false, subtitleColor);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setIncludeFontPadding(false);
        add(labels, subtitle, 3);
        ImageView chevron = icon(R.drawable.ic_chevron_right, 17);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        return row;
    }

    private View buildRecentHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), 0, dp(2), 0);
        TextView title = text("Recent Downloads", 15, true, TEXT);
        title.setIncludeFontPadding(false);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = text("View all", 13, false, MUTED);
        all.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        all.setIncludeFontPadding(false);
        all.setClickable(true);
        all.setFocusable(true);
        all.setPadding(dp(12), 0, 0, 0);
        all.setOnClickListener(v -> showDownloads());
        row.addView(all, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        return row;
    }

    private void renderRecent() {
        if (recentList == null) return;
        recentList.removeAllViews();
        SharedPreferences prefs = getSharedPreferences("history", MODE_PRIVATE);
        String id = prefs.getString("last_id", "");
        if (id == null || id.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.HORIZONTAL);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setPadding(dp(14), 0, dp(14), 0);
            empty.setBackground(insetRounded(Color.parseColor("#FBFBFB"), Color.parseColor("#ECECEC"), 26));
            ImageView emptyIcon = softIcon(R.drawable.ic_image, 40, 20, 14);
            empty.addView(emptyIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            copyParams.leftMargin = dp(12);
            empty.addView(copy, copyParams);
            TextView title = text("No downloads yet", 14, true, TEXT);
            title.setIncludeFontPadding(false);
            copy.addView(title);
            TextView subtitle = text("Completed downloads will appear here.", 12, false, MUTED);
            subtitle.setIncludeFontPadding(false);
            add(copy, subtitle, 3);
            recentList.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
            return;
        }
        int count = prefs.getInt("last_count", 0);
        String time = prefs.getString("last_time", "");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(insetRounded(PANEL, LINE, 26));
        card.setPadding(dp(14), 0, dp(14), 0);
        ImageView thumbnail = softIcon(R.drawable.ic_image, 40, 20, 14);
        card.addView(thumbnail, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        detailsParams.leftMargin = dp(12);
        card.addView(details, detailsParams);
        TextView idText = text(id, 14, true, TEXT);
        idText.setSingleLine(true);
        idText.setEllipsize(TextUtils.TruncateAt.END);
        idText.setIncludeFontPadding(false);
        details.addView(idText);
        TextView meta = text(count + " files  ·  " + time, 12, false, MUTED);
        meta.setIncludeFontPadding(false);
        add(details, meta, 3);
        ImageView done = softIcon(R.drawable.ic_check, 30, 15, 11);
        done.setBackground(rounded(GREEN_BG, 11));
        done.setImageTintList(ColorStateList.valueOf(GREEN));
        card.addView(done, new LinearLayout.LayoutParams(dp(30), dp(30)));
        recentList.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String type = intent.getType();
        if (type == null || !type.startsWith("text/")) return;
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared == null || shared.trim().isEmpty()) return;
        pendingSharedText = shared;
        urlInput.setText(shared);
        if (cookieStore.existsAndValid()) startDownload(shared);
        else setStatus("Import cookies, then share the post again.", false);
    }

    private void chooseCookieFile() {
        if (busy.get()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_COOKIES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_COOKIES || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            cookieStore.importFrom(uri);
            toast("Cookies imported");
            String shared = pendingSharedText;
            pendingSharedText = null;
            if (shared != null && !shared.trim().isEmpty()) startDownload(shared);
            else recreate();
        } catch (IOException exception) {
            cookieStore.clear();
            showErrorState("Cookie import failed", safeMessage(exception));
        }
    }

    private void startDownload(String sharedText) {
        if (!busy.compareAndSet(false, true)) {
            toast("A download is already running");
            return;
        }
        if (TextUtils.isEmpty(sharedText) || sharedText.trim().isEmpty()) {
            busy.set(false);
            showErrorState("Paste an Instagram link first", "The link field is empty.");
            return;
        }
        if (!cookieStore.existsAndValid()) {
            busy.set(false);
            pendingSharedText = sharedText;
            showErrorState("Cookies required", "Import a cookies.txt containing sessionid.");
            chooseCookieFile();
            return;
        }
        setControlsEnabled(false);
        showRunningState("Reading post…", "", -1, -1, true);
        log.reset();
        executor.execute(() -> {
            try {
                InstagramClient client = new InstagramClient(cookieStore, log);
                String normalizedUrl = client.extractPostUrl(sharedText);
                runOnUiThread(() -> urlInput.setText(normalizedUrl));
                InstagramClient.Post post = client.fetchPost(normalizedUrl);
                runOnUiThread(() -> showRunningState("Found media", post.media.size() + (post.media.size() == 1 ? " file" : " files"), 0, post.media.size(), false));
                MediaSaver saver = new MediaSaver(this, client, log);
                int count = saver.savePost(post, (completed, total, message) -> runOnUiThread(() -> showRunningState(message, "Pictures/INSDL/" + post.shortcode, completed, total, false)));
                saveLastDownload(post.shortcode, count);
                runOnUiThread(() -> {
                    showCompletedState("Pictures/INSDL/" + post.shortcode);
                    renderRecent();
                    toast("Saved " + count + " files");
                });
            } catch (IOException | JSONException exception) {
                log.line("ERROR: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                runOnUiThread(() -> showErrorState("Download failed", safeMessage(exception)));
            } catch (RuntimeException exception) {
                log.line("ERROR: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                runOnUiThread(() -> showErrorState("Unexpected error", "Copy diagnostic log for details."));
            } finally {
                busy.set(false);
                runOnUiThread(() -> setControlsEnabled(true));
            }
        });
    }

    private void saveLastDownload(String id, int count) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        getSharedPreferences("history", MODE_PRIVATE).edit().putString("last_id", id).putInt("last_count", count).putString("last_time", time).apply();
    }

    private void showDownloads() {
        SharedPreferences prefs = getSharedPreferences("history", MODE_PRIVATE);
        String id = prefs.getString("last_id", "");
        if (id == null || id.isEmpty()) {
            toast("No downloads yet");
            return;
        }
        int count = prefs.getInt("last_count", 0);
        String time = prefs.getString("last_time", "");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(insetRounded(PANEL, LINE, 24));
        ImageView thumbnail = softIcon(R.drawable.ic_image, 40, 20, 14);
        row.addView(thumbnail, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        detailParams.leftMargin = dp(12);
        row.addView(details, detailParams);
        TextView idView = text(id, 14, true, TEXT);
        idView.setIncludeFontPadding(false);
        details.addView(idView);
        TextView meta = text(count + " files  ·  " + time, 12, false, MUTED);
        meta.setIncludeFontPadding(false);
        add(details, meta, 3);
        TextView path = text("Pictures/INSDL/" + id, 12, false, MUTED);
        path.setIncludeFontPadding(false);
        add(details, path, 3);
        list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        showCustomDialog("Downloads", list);
    }

    private void showSettings() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(insetRounded(PANEL, LINE, 24));
        boolean valid = cookieStore.existsAndValid();
        View cookies = dialogRow("Cookies", valid ? "Valid" : "Not imported", valid ? GREEN : MUTED, false, true);
        cookies.setOnClickListener(v -> chooseCookieFile());
        list.addView(cookies, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        addDialogDivider(list);
        list.addView(dialogRow("Save path", "Pictures/INSDL/", MUTED, false, false), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        addDialogDivider(list);
        list.addView(dialogRow("Version", VERSION, MUTED, false, false), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        addDialogDivider(list);
        View copy = dialogRow("Copy diagnostic log", "›", MUTED, false, true);
        copy.setOnClickListener(v -> copyDiagnosticLog());
        list.addView(copy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        addDialogDivider(list);
        View clear = dialogRow("Clear cookies", "›", RED, true, true);
        clear.setOnClickListener(v -> {
            cookieStore.clear();
            toast("Cookies cleared");
            recreate();
        });
        list.addView(clear, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        showCustomDialog("Settings", list);
    }

    private View dialogRow(String name, String value, int valueColor, boolean danger, boolean interactive) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setClickable(interactive);
        row.setFocusable(interactive);
        row.setBackground(interactive ? pressedRounded(Color.TRANSPARENT, Color.parseColor("#FAFAFA"), 16) : rounded(Color.TRANSPARENT, 0));
        TextView nameView = text(name, 14, false, danger ? RED : TEXT);
        nameView.setIncludeFontPadding(false);
        row.addView(nameView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = text(value, 13, false, valueColor);
        valueView.setIncludeFontPadding(false);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addDialogDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.leftMargin = dp(14);
        params.rightMargin = dp(14);
        parent.addView(divider, params);
    }

    private void showCustomDialog(String titleText, View body) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(10), dp(10), dp(10), dp(10));
        container.setBackground(insetRounded(PANEL, LINE, 32));
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), 0, dp(2), 0);
        container.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView title = text(titleText, 18, true, TEXT);
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text("×", 24, false, TEXT);
        close.setGravity(Gravity.CENTER);
        close.setIncludeFontPadding(false);
        close.setClickable(true);
        close.setFocusable(true);
        close.setContentDescription("Close");
        close.setBackground(pressedRounded(Color.TRANSPARENT, PANEL_2, 14));
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        container.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setView(container);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int available = getResources().getDisplayMetrics().widthPixels - dp(36);
            window.setLayout(Math.min(available, dp(420)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void openInstagram() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.instagram.android");
            if (launch != null) startActivity(launch);
            else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/")));
        } catch (Exception exception) {
            toast("Unable to open Instagram");
        }
    }

    private void copyDiagnosticLog() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("INSDL log", log.read()));
        toast("Diagnostic log copied");
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        ClipData data = clipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) return;
        CharSequence value = data.getItemAt(0).coerceToText(this);
        if (value != null) urlInput.setText(value.toString().trim());
    }

    private void setControlsEnabled(boolean enabled) {
        downloadButton.setEnabled(enabled);
        downloadButton.setAlpha(enabled ? 1f : 0.58f);
        urlInput.setEnabled(enabled);
    }

    private void setStatus(String message, boolean error) {
        String safe = message == null ? "" : message.trim();
        if (safe.isEmpty()) {
            if (downloadStateCard != null) downloadStateCard.setVisibility(View.GONE);
            return;
        }
        if (error) showErrorState("Download failed", safe);
        else showNoticeState(safe);
    }

    private void showNoticeState(String titleText) {
        applyStateFrame(PANEL_2, LINE, R.drawable.ic_download, MUTED, Color.parseColor("#EAEAEA"));
        setStateText(titleText, "", "");
        progress.setVisibility(View.GONE);
        downloadStateCard.setVisibility(View.VISIBLE);
    }

    private void showRunningState(String titleText, String detailText, int completed, int total, boolean indeterminate) {
        applyStateFrame(PANEL_2, LINE, R.drawable.ic_download, MUTED, Color.parseColor("#EAEAEA"));
        String percentText = "";
        if (!indeterminate && total > 0 && completed >= 0) {
            int percentValue = Math.max(0, Math.min(100, Math.round(completed * 100f / total)));
            percentText = percentValue + "%";
            progress.setIndeterminate(false);
            progress.setMax(100);
            progress.setProgress(percentValue);
        } else {
            progress.setIndeterminate(true);
        }
        setStateText(titleText, detailText, percentText);
        progress.setVisibility(View.VISIBLE);
        downloadStateCard.setVisibility(View.VISIBLE);
    }

    private void showCompletedState(String path) {
        applyStateFrame(GREEN_BG, Color.parseColor("#DBEEDD"), R.drawable.ic_check, GREEN, Color.parseColor("#DFF0E3"));
        setStateText("Completed", path, "");
        progress.setIndeterminate(false);
        progress.setVisibility(View.GONE);
        downloadStateCard.setVisibility(View.VISIBLE);
    }

    private void showErrorState(String titleText, String detailText) {
        applyStateFrame(RED_BG, Color.parseColor("#F1D7D2"), R.drawable.ic_error, RED, Color.parseColor("#F8DDDA"));
        setStateText(titleText, detailText, "");
        progress.setIndeterminate(false);
        progress.setVisibility(View.GONE);
        downloadStateCard.setVisibility(View.VISIBLE);
    }

    private void applyStateFrame(int fill, int stroke, int iconRes, int iconTint, int iconFill) {
        downloadStateCard.setBackground(insetRounded(fill, stroke, 22));
        downloadStateCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        statusIcon.setImageResource(iconRes);
        statusIcon.setImageTintList(ColorStateList.valueOf(iconTint));
        statusIcon.setBackground(rounded(iconFill, 13));
    }

    private void setStateText(String titleText, String detailText, String percentText) {
        status.setText(titleText == null ? "" : titleText);
        status.setTextColor(TEXT);
        String detail = detailText == null ? "" : detailText.trim();
        statusDetail.setText(detail);
        statusDetail.setTextColor(MUTED);
        statusDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        String percent = percentText == null ? "" : percentText.trim();
        statusPercent.setText(percent);
        statusPercent.setTextColor(MUTED);
        statusPercent.setVisibility(percent.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private ImageView icon(int drawableRes, int visualDp) {
        ImageView view = new ImageView(this);
        view.setImageResource(drawableRes);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.max(0, (dp(24) - dp(visualDp)) / 2);
        if (pad > 0) view.setPadding(pad, pad, pad, pad);
        return view;
    }

    private ImageView softIcon(int drawableRes, int containerDp, int visualDp, int radiusDp) {
        ImageView view = new ImageView(this);
        view.setImageResource(drawableRes);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setBackground(rounded(PANEL_2, radiusDp));
        int padding = Math.max(0, (dp(containerDp) - dp(visualDp)) / 2);
        view.setPadding(padding, padding, padding, padding);
        return view;
    }

    private StateListDrawable pressedRounded(int normal, int pressed, int radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(pressed, radiusDp));
        states.addState(new int[]{}, rounded(normal, radiusDp));
        return states;
    }

    private InsetDrawable insetRounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = rounded(fill, radiusDp);
        drawable.setStroke(dp(1), stroke);
        int inset = dp(1);
        return new InsetDrawable(drawable, inset, inset, inset, inset);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private void add(LinearLayout parent, View view, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        parent.addView(view, params);
    }

    private void addDivider(LinearLayout parent, int leftDp, int rightDp) {
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.leftMargin = dp(leftDp);
        params.rightMargin = dp(rightDp);
        parent.addView(divider, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? exception.getClass().getSimpleName() : message;
    }
}

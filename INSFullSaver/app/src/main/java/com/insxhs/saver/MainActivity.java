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
import android.widget.EditText;
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
    private static final String VERSION = "1.3.4";

    private static final int BG = Color.parseColor("#FAFAF8");
    private static final int PANEL = Color.parseColor("#FFFFFF");
    private static final int PANEL_2 = Color.parseColor("#F2F2F0");
    private static final int TEXT = Color.parseColor("#0D0D0D");
    private static final int MUTED = Color.parseColor("#6B6B6B");
    private static final int LINE = Color.parseColor("#E3E3DF");
    private static final int ICON = Color.parseColor("#535353");
    private static final int GREEN = Color.parseColor("#268A63");
    private static final int RED = Color.parseColor("#D24B4B");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private CookieStore cookieStore;
    private AppLog log;
    private EditText urlInput;
    private TextView status;
    private ProgressBar progress;
    private TextView downloadButton;
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(30));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int statusBar = insets.getSystemWindowInsetTop();
            int navigationBar = insets.getSystemWindowInsetBottom();
            view.setPadding(
                    dp(20),
                    statusBar + dp(8),
                    dp(20),
                    Math.max(dp(30), navigationBar + dp(18)));
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.requestApplyInsets();

        root.addView(buildTopBar());
        add(root, buildHero(), 32);
        add(root, buildLinkField(), 30);

        downloadButton = primaryButton("Download   ↓");
        downloadButton.setOnClickListener(v -> startDownload(urlInput.getText().toString()));
        add(root, downloadButton, 14);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        progress.setProgressTintList(ColorStateList.valueOf(TEXT));
        progress.setIndeterminateTintList(ColorStateList.valueOf(TEXT));
        add(root, progress, 12);

        add(root, buildShortcuts(), 20);
        add(root, buildRecentHeader(), 30);

        recentList = new LinearLayout(this);
        recentList.setOrientation(LinearLayout.VERTICAL);
        add(root, recentList, 12);
        renderRecent();

        status = text("Ready", 13, false, MUTED);
        status.setGravity(Gravity.CENTER);
        status.setTextIsSelectable(true);
        add(root, status, 24);

        setContentView(scroll);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView menu = text("☰", 25, false, TEXT);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> showSettings());
        bar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("INSDL", 19, true, TEXT);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(48), dp(48)));
        return bar;
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = text("Instagram Downloader", 29, true, TEXT);
        title.setGravity(Gravity.CENTER);
        hero.addView(title);

        TextView subtitle = text(
                "Paste a link to download all images\nfrom Instagram posts and carousels.",
                14, false, MUTED);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(0f, 1.28f);
        add(hero, subtitle, 10);
        return hero;
    }

    private View buildLinkField() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.CENTER_VERTICAL);
        shell.setPadding(dp(14), dp(7), dp(7), dp(7));
        GradientDrawable background = rounded(PANEL, 30);
        background.setStroke(dp(1), LINE);
        shell.setBackground(background);

        TextView linkIcon = text("↗", 19, true, ICON);
        linkIcon.setGravity(Gravity.CENTER);
        shell.addView(linkIcon, new LinearLayout.LayoutParams(dp(34), dp(50)));

        urlInput = new EditText(this);
        urlInput.setHint("Paste Instagram link here");
        urlInput.setHintTextColor(Color.parseColor("#8A8A86"));
        urlInput.setTextColor(TEXT);
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setSingleLine(true);
        urlInput.setBackground(null);
        shell.addView(urlInput, new LinearLayout.LayoutParams(0, dp(54), 1f));

        TextView paste = text("Paste", 14, true, TEXT);
        paste.setGravity(Gravity.CENTER);
        GradientDrawable pasteBackground = rounded(BG, 24);
        pasteBackground.setStroke(dp(1), LINE);
        paste.setBackground(pasteBackground);
        paste.setOnClickListener(v -> pasteFromClipboard());
        shell.addView(paste, new LinearLayout.LayoutParams(dp(78), dp(48)));
        return shell;
    }

    private View buildShortcuts() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        View instagram = shortcut("◎", "Open Instagram", "Share to INSDL");
        instagram.setOnClickListener(v -> openInstagram());
        row.addView(instagram, new LinearLayout.LayoutParams(0, dp(94), 1f));

        String cookieSub = cookieStore.existsAndValid() ? "Valid" : "Manage Cookies";
        View cookies = shortcut("▣", "Cookies", cookieSub);
        cookies.setOnClickListener(v -> chooseCookieFile());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(94), 1f);
        params.leftMargin = dp(10);
        row.addView(cookies, params);
        return row;
    }

    private View shortcut(String symbol, String title, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(10), dp(12));
        GradientDrawable background = rounded(PANEL, 24);
        background.setStroke(dp(1), LINE);
        card.setBackground(background);

        TextView icon = text(symbol, 22, true, ICON);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(PANEL_2, 20));
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(9);
        card.addView(labels, labelParams);
        labels.addView(text(title, 13, true, TEXT));
        add(labels, text(subtitle, 11, false, MUTED), 4);

        TextView chevron = text("›", 22, false, MUTED);
        chevron.setGravity(Gravity.CENTER);
        card.addView(chevron, new LinearLayout.LayoutParams(dp(20), dp(44)));
        return card;
    }

    private View buildRecentHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text("Recent Downloads", 17, true, TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = text("View All  ›", 13, false, MUTED);
        all.setOnClickListener(v -> showDownloads());
        row.addView(all);
        return row;
    }

    private void renderRecent() {
        if (recentList == null) return;
        recentList.removeAllViews();
        SharedPreferences prefs = getSharedPreferences("history", MODE_PRIVATE);
        String id = prefs.getString("last_id", "");
        if (id == null || id.isEmpty()) {
            TextView empty = text("No downloads yet", 14, false, MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            GradientDrawable background = rounded(PANEL, 24);
            background.setStroke(dp(1), LINE);
            empty.setBackground(background);
            recentList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        int count = prefs.getInt("last_count", 0);
        String time = prefs.getString("last_time", "");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable background = rounded(PANEL, 24);
        background.setStroke(dp(1), LINE);
        card.setBackground(background);

        TextView thumbnail = text("▧", 27, false, ICON);
        thumbnail.setGravity(Gravity.CENTER);
        thumbnail.setBackground(rounded(PANEL_2, 18));
        card.addView(thumbnail, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        detailsParams.leftMargin = dp(12);
        card.addView(details, detailsParams);
        details.addView(text(id, 15, true, TEXT));
        add(details, text(count + " files  ·  " + time, 12, false, MUTED), 5);

        TextView done = text("✓", 16, true, GREEN);
        done.setGravity(Gravity.CENTER);
        done.setBackground(rounded(PANEL_2, 999));
        card.addView(done, new LinearLayout.LayoutParams(dp(38), dp(38)));
        recentList.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
            setStatus(exception.getMessage(), true);
        }
    }

    private void startDownload(String sharedText) {
        if (!busy.compareAndSet(false, true)) {
            toast("A download is already running");
            return;
        }
        if (TextUtils.isEmpty(sharedText) || sharedText.trim().isEmpty()) {
            busy.set(false);
            setStatus("Paste an Instagram link first.", true);
            return;
        }
        if (!cookieStore.existsAndValid()) {
            busy.set(false);
            pendingSharedText = sharedText;
            setStatus("Import a cookies.txt containing sessionid.", true);
            chooseCookieFile();
            return;
        }

        setControlsEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        setStatus("Reading post…", false);
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
                });

                MediaSaver saver = new MediaSaver(this, client, log);
                int count = saver.savePost(post, (completed, total, message) -> runOnUiThread(() -> {
                    progress.setIndeterminate(false);
                    progress.setMax(total);
                    progress.setProgress(completed);
                    setStatus(message, false);
                }));

                saveLastDownload(post.shortcode, count);
                runOnUiThread(() -> {
                    progress.setProgress(count);
                    setStatus("Completed · Pictures/INSDL/" + post.shortcode, false);
                    renderRecent();
                    toast("Saved " + count + " files");
                });
            } catch (IOException | JSONException exception) {
                log.line("ERROR: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                runOnUiThread(() -> setStatus("Download failed: " + safeMessage(exception), true));
            } catch (RuntimeException exception) {
                log.line("ERROR: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                runOnUiThread(() -> setStatus("Unexpected error. Copy diagnostic log.", true));
            } finally {
                busy.set(false);
                runOnUiThread(() -> setControlsEnabled(true));
            }
        });
    }

    private void saveLastDownload(String id, int count) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        getSharedPreferences("history", MODE_PRIVATE).edit()
                .putString("last_id", id)
                .putInt("last_count", count)
                .putString("last_time", time)
                .apply();
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
        new AlertDialog.Builder(this)
                .setTitle("Downloads")
                .setMessage(id + "\n" + count + " files · " + time + "\n\nPictures/INSDL/" + id)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showSettings() {
        String cookie = cookieStore.existsAndValid() ? "Valid" : "Not imported";
        String[] items = new String[]{
                "Cookies: " + cookie,
                "Save Path: Pictures/INSDL/",
                "Version: " + VERSION,
                "Copy diagnostic log",
                "Clear cookies"
        };
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) chooseCookieFile();
                    else if (which == 3) copyDiagnosticLog();
                    else if (which == 4) {
                        cookieStore.clear();
                        toast("Cookies cleared");
                        recreate();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
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
        urlInput.setEnabled(enabled);
    }

    private void setStatus(String message, boolean error) {
        if (status == null) return;
        status.setText(message == null ? "" : message);
        status.setTextColor(error ? RED : MUTED);
    }

    private TextView primaryButton(String label) {
        TextView button = text(label, 17, true, TEXT);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setIncludeFontPadding(false);
        button.setHeight(dp(60));
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setElevation(0f);
        button.setStateListAnimator(null);

        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{-android.R.attr.state_enabled},
                insetRounded(Color.parseColor("#F5F5F3"), Color.parseColor("#ECECE8"), 30));
        states.addState(
                new int[]{android.R.attr.state_pressed},
                insetRounded(Color.parseColor("#E9E9E6"), Color.parseColor("#D7D7D2"), 30));
        states.addState(
                new int[]{},
                insetRounded(PANEL_2, LINE, 30));
        button.setBackground(states);

        button.setTextColor(new ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{
                        Color.parseColor("#A2A29D"),
                        TEXT
                }));
        return button;
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
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

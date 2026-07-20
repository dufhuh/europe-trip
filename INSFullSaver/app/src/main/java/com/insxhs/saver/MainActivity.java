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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int REQUEST_COOKIES = 1001;
    private static final String VERSION = "1.3.1";

    private static final int BG = Color.parseColor("#0B1020");
    private static final int PANEL = Color.parseColor("#151B2F");
    private static final int PANEL_2 = Color.parseColor("#1C2440");
    private static final int TEXT = Color.parseColor("#F7F8FF");
    private static final int MUTED = Color.parseColor("#A6AEC4");
    private static final int LINE = Color.parseColor("#2A3458");
    private static final int PURPLE = Color.parseColor("#8B5CF6");
    private static final int BLUE = Color.parseColor("#4F7CF6");
    private static final int CYAN = Color.parseColor("#35C4FF");
    private static final int GREEN = Color.parseColor("#34D399");
    private static final int RED = Color.parseColor("#FF6B7A");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private CookieStore cookieStore;
    private AppLog log;
    private EditText urlInput;
    private TextView status;
    private ProgressBar progress;
    private Button downloadButton;
    private LinearLayout recentList;
    private String pendingSharedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
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
        root.setPadding(dp(20), dp(12), dp(20), dp(34));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int statusBar = insets.getSystemWindowInsetTop();
            int navigationBar = insets.getSystemWindowInsetBottom();
            view.setPadding(
                    dp(20),
                    statusBar + dp(10),
                    dp(20),
                    Math.max(dp(34), navigationBar + dp(20)));
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.requestApplyInsets();

        root.addView(buildTopBar());
        add(root, buildHero(), 22);
        add(root, buildLinkField(), 22);

        downloadButton = gradientButton("Download     ↓");
        downloadButton.setOnClickListener(v -> startDownload(urlInput.getText().toString()));
        add(root, downloadButton, 14);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        progress.setProgressTintList(ColorStateList.valueOf(CYAN));
        progress.setIndeterminateTintList(ColorStateList.valueOf(CYAN));
        add(root, progress, 12);

        add(root, buildShortcuts(), 18);
        add(root, buildRecentHeader(), 26);

        recentList = new LinearLayout(this);
        recentList.setOrientation(LinearLayout.VERTICAL);
        add(root, recentList, 10);
        renderRecent();

        status = text("Ready", 13, false, MUTED);
        status.setGravity(Gravity.CENTER);
        status.setTextIsSelectable(true);
        add(root, status, 22);

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

        TextView icon = text("↓", 42, true, TEXT);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(gradientRing());
        hero.addView(icon, new LinearLayout.LayoutParams(dp(108), dp(108)));

        TextView title = text("Instagram Downloader", 28, true, TEXT);
        title.setGravity(Gravity.CENTER);
        add(hero, title, 18);

        TextView subtitle = text(
                "Paste a link to download all images\nfrom Instagram posts and carousels.",
                14, false, MUTED);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(0f, 1.25f);
        add(hero, subtitle, 9);
        return hero;
    }

    private View buildLinkField() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.CENTER_VERTICAL);
        shell.setPadding(dp(14), dp(7), dp(7), dp(7));
        GradientDrawable background = rounded(PANEL, 18);
        background.setStroke(dp(1), LINE);
        shell.setBackground(background);

        TextView linkIcon = text("↗", 20, true, CYAN);
        linkIcon.setGravity(Gravity.CENTER);
        shell.addView(linkIcon, new LinearLayout.LayoutParams(dp(34), dp(50)));

        urlInput = new EditText(this);
        urlInput.setHint("Paste Instagram link here");
        urlInput.setHintTextColor(Color.parseColor("#75809D"));
        urlInput.setTextColor(TEXT);
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setSingleLine(true);
        urlInput.setBackground(null);
        shell.addView(urlInput, new LinearLayout.LayoutParams(0, dp(54), 1f));

        TextView paste = text("▣", 22, false, TEXT);
        paste.setGravity(Gravity.CENTER);
        paste.setBackground(rounded(PANEL_2, 13));
        paste.setOnClickListener(v -> pasteFromClipboard());
        shell.addView(paste, new LinearLayout.LayoutParams(dp(50), dp(50)));
        return shell;
    }

    private View buildShortcuts() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        View instagram = shortcut("◎", "Open Instagram", "Share to INSDL", PURPLE);
        instagram.setOnClickListener(v -> openInstagram());
        row.addView(instagram, new LinearLayout.LayoutParams(0, dp(96), 1f));

        String cookieSub = cookieStore.existsAndValid() ? "Valid" : "Manage Cookies";
        View cookies = shortcut("▣", "Cookies", cookieSub, CYAN);
        cookies.setOnClickListener(v -> chooseCookieFile());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(96), 1f);
        params.leftMargin = dp(10);
        row.addView(cookies, params);
        return row;
    }

    private View shortcut(String symbol, String title, String subtitle, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        GradientDrawable background = rounded(PANEL, 18);
        background.setStroke(dp(1), LINE);
        card.setBackground(background);

        TextView icon = text(symbol, 23, true, accent);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(PANEL_2, 13));
        card.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(9);
        card.addView(labels, labelParams);
        labels.addView(text(title, 13, true, TEXT));
        add(labels, text(subtitle, 11, false, MUTED), 4);
        return card;
    }

    private View buildRecentHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text("Recent Downloads", 17, true, TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = text("View All", 13, true, CYAN);
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
            GradientDrawable background = rounded(PANEL, 18);
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
        GradientDrawable background = rounded(PANEL, 18);
        background.setStroke(dp(1), LINE);
        card.setBackground(background);

        TextView thumbnail = text("▧", 28, false, CYAN);
        thumbnail.setGravity(Gravity.CENTER);
        thumbnail.setBackground(rounded(PANEL_2, 14));
        card.addView(thumbnail, new LinearLayout.LayoutParams(dp(58), dp(58)));

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
        done.setBackground(rounded(Color.parseColor("#183A35"), 999));
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
        downloadButton.setAlpha(enabled ? 1f : 0.65f);
    }

    private void setStatus(String message, boolean error) {
        if (status == null) return;
        status.setText(message == null ? "" : message);
        status.setTextColor(error ? RED : MUTED);
    }

    private Button gradientButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PURPLE, BLUE});
        background.setCornerRadius(dp(18));
        button.setBackground(background);
        button.setMinHeight(dp(60));
        return button;
    }

    private LayerDrawable gradientRing() {
        GradientDrawable outer = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#FF5CA8"), PURPLE, CYAN});
        outer.setCornerRadius(dp(28));
        GradientDrawable inner = rounded(BG, 24);
        LayerDrawable layer = new LayerDrawable(new Drawable[]{outer, inner});
        layer.setLayerInset(1, dp(5), dp(5), dp(5), dp(5));
        return layer;
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

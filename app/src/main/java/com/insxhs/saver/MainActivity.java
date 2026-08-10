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
    private static final String VERSION = "1.4.3";

    private static final int BG = Color.parseColor("#F7F7F5");
    private static final int PANEL = Color.parseColor("#FFFFFF");
    private static final int PANEL_2 = Color.parseColor("#F1F1EF");
    private static final int TEXT = Color.parseColor("#111111");
    private static final int MUTED = Color.parseColor("#6F6F6B");
    private static final int SUBTLE = Color.parseColor("#92928D");
    private static final int LINE = Color.parseColor("#E5E5E1");
    private static final int LINE_STRONG = Color.parseColor("#D8D8D3");
    private static final int RED = Color.parseColor("#C84A4A");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private CookieStore cookieStore;
    private AppLog log;
    private EditText urlInput;
    private TextView status;
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
        root.setPadding(dp(18), dp(4), dp(18), dp(28));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int statusBar = insets.getSystemWindowInsetTop();
            int navigationBar = insets.getSystemWindowInsetBottom();
            view.setPadding(
                    dp(18),
                    statusBar + dp(4),
                    dp(18),
                    Math.max(dp(28), navigationBar + dp(18)));
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.requestApplyInsets();

        root.addView(buildTopBar());
        add(root, buildHero(), 20);
        add(root, buildLinkField(), 22);

        downloadButton = primaryButton();
        downloadButton.setOnClickListener(v -> startDownload(urlInput.getText().toString()));
        add(root, downloadButton, 10);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        progress.setProgressTintList(ColorStateList.valueOf(TEXT));
        progress.setIndeterminateTintList(ColorStateList.valueOf(TEXT));
        add(root, progress, 10);

        add(root, buildQuickActions(), 25);
        add(root, buildRecentHeader(), 25);

        recentList = new LinearLayout(this);
        recentList.setOrientation(LinearLayout.VERTICAL);
        add(root, recentList, 10);
        renderRecent();

        status = text("", 12, false, MUTED);
        status.setGravity(Gravity.CENTER);
        status.setTextIsSelectable(true);
        status.setVisibility(View.GONE);
        add(root, status, 14);

        setContentView(scroll);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView menu = icon(R.drawable.ic_menu, 20);
        menu.setPadding(dp(10), dp(10), dp(10), dp(10));
        menu.setClickable(true);
        menu.setFocusable(true);
        menu.setBackground(pressedRounded(Color.TRANSPARENT, PANEL_2, 12));
        menu.setOnClickListener(v -> showSettings());
        bar.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text("INSDL", 16, true, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.START);
        hero.setPadding(dp(4), 0, dp(4), 0);

        TextView title = text("Instagram Downloader", 27, true, TEXT);
        title.setGravity(Gravity.START);
        title.setIncludeFontPadding(false);
        title.setLetterSpacing(-0.02f);
        hero.addView(title);

        TextView subtitle = text(
                "Paste a link to download all images from Instagram posts and carousels.",
                14, false, MUTED);
        subtitle.setGravity(Gravity.START);
        subtitle.setLineSpacing(0f, 1.38f);
        add(hero, subtitle, 10);
        return hero;
    }

    private View buildLinkField() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.CENTER_VERTICAL);
        shell.setMinimumHeight(dp(56));
        shell.setBackground(insetRounded(PANEL, LINE, 18));
        shell.setPadding(dp(12), dp(7), dp(8), dp(7));

        ImageView linkIcon = softIcon(R.drawable.ic_link, 34, 18, 11);
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
        shell.addView(urlInput, new LinearLayout.LayoutParams(0, dp(40), 1f));

        TextView paste = text("Paste", 13, true, TEXT);
        paste.setGravity(Gravity.CENTER);
        paste.setIncludeFontPadding(false);
        paste.setClickable(true);
        paste.setFocusable(true);
        paste.setBackground(pressedRounded(BG, Color.parseColor("#EAEAE7"), 12));
        paste.setOnClickListener(v -> pasteFromClipboard());
        shell.addView(paste, new LinearLayout.LayoutParams(dp(66), dp(38)));
        return shell;
    }

    private View primaryButton() {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setMinimumHeight(dp(54));
        button.setElevation(0f);
        button.setStateListAnimator(null);

        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{-android.R.attr.state_enabled},
                insetRounded(Color.parseColor("#F5F5F3"), Color.parseColor("#ECECE8"), 16));
        states.addState(
                new int[]{android.R.attr.state_pressed},
                insetRounded(Color.parseColor("#EAEAE7"), LINE_STRONG, 16));
        states.addState(new int[]{}, insetRounded(PANEL_2, LINE_STRONG, 16));
        button.setBackground(states);

        TextView label = text("Download", 15, true, TEXT);
        label.setIncludeFontPadding(false);
        button.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView download = icon(R.drawable.ic_download, 19);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.leftMargin = dp(8);
        button.addView(download, iconParams);
        return button;
    }

    private View buildQuickActions() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(insetRounded(PANEL, LINE, 18));

        View instagram = actionRow(
                R.drawable.ic_instagram,
                "Open Instagram",
                "Share a post to INSDL from Instagram");
        instagram.setOnClickListener(v -> openInstagram());
        card.addView(instagram, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.leftMargin = dp(58);
        dividerParams.rightMargin = dp(14);
        card.addView(divider, dividerParams);

        String cookieState = cookieStore.existsAndValid() ? "Valid" : "Not imported";
        View cookies = actionRow(R.drawable.ic_cookie, "Cookies", cookieState);
        cookies.setOnClickListener(v -> chooseCookieFile());
        card.addView(cookies, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        return card;
    }

    private View actionRow(int iconRes, String titleText, String subtitleText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setClickable(true);
        row.setFocusable(true);

        ImageView itemIcon = softIcon(iconRes, 34, 19, 11);
        row.addView(itemIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(12);
        row.addView(labels, labelsParams);

        TextView title = text(titleText, 14, true, TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setIncludeFontPadding(false);
        labels.addView(title);

        if (subtitleText != null && !subtitleText.isEmpty()) {
            TextView subtitle = text(subtitleText, 12, false, MUTED);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setIncludeFontPadding(false);
            add(labels, subtitle, 4);
        }

        ImageView chevron = icon(R.drawable.ic_chevron_right, 17);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        return row;
    }

    private View buildRecentHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), 0, dp(2), 0);
        row.addView(text("Recent Downloads", 16, true, TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = text("View all", 13, false, MUTED);
        all.setClickable(true);
        all.setFocusable(true);
        all.setPadding(dp(12), dp(7), 0, dp(7));
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
            TextView empty = text("No downloads yet", 13, false, MUTED);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setBackground(insetRounded(PANEL, LINE, 18));
            empty.setPadding(dp(16), 0, 0, 0);
            recentList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
            return;
        }
        int count = prefs.getInt("last_count", 0);
        String time = prefs.getString("last_time", "");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(insetRounded(PANEL, LINE, 18));
        card.setPadding(dp(14), 0, dp(14), 0);

        ImageView thumbnail = softIcon(R.drawable.ic_image, 42, 20, 13);
        card.addView(thumbnail, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        detailsParams.leftMargin = dp(12);
        card.addView(details, detailsParams);
        TextView idText = text(id, 14, true, TEXT);
        idText.setSingleLine(true);
        idText.setEllipsize(TextUtils.TruncateAt.END);
        idText.setIncludeFontPadding(false);
        details.addView(idText);
        TextView meta = text(count + " files  ·  " + time, 12, false, MUTED);
        meta.setIncludeFontPadding(false);
        add(details, meta, 4);

        ImageView done = softIcon(R.drawable.ic_check, 30, 15, 10);
        card.addView(done, new LinearLayout.LayoutParams(dp(30), dp(30)));
        recentList.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
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
        downloadButton.setAlpha(enabled ? 1f : 0.58f);
        urlInput.setEnabled(enabled);
    }

    private void setStatus(String message, boolean error) {
        if (status == null) return;
        String safe = message == null ? "" : message.trim();
        status.setText(safe);
        status.setTextColor(error ? RED : MUTED);
        status.setVisibility(safe.isEmpty() ? View.GONE : View.VISIBLE);
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

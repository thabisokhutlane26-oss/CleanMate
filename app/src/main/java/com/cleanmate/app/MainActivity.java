package com.cleanmate.app;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_MEDIA = 100;
    private static final int REQUEST_DELETE = 200;
    private static final int REQUEST_USAGE = 300;
    private static final int REQUEST_UNINSTALL = 400;

    private static final long LARGE_FILE_SIZE = 50L * 1024L * 1024L;
    private static final long OLD_FILE_AGE = 365L * 24L * 60L * 60L;

    private static final int BG = Color.rgb(7, 17, 18);
    private static final int CARD = Color.rgb(17, 35, 34);
    private static final int CARD_ALT = Color.rgb(22, 48, 43);
    private static final int GREEN = Color.rgb(0, 255, 136);
    private static final int GREEN_DARK = Color.rgb(13, 112, 75);
    private static final int WHITE = Color.rgb(239, 250, 245);
    private static final int MUTED = Color.rgb(145, 173, 163);
    private static final int BORDER = Color.rgb(40, 92, 75);

    private LinearLayout root;
    private LinearLayout page;
    private TextView scanSummary;
    private TextView dashboardStatus;
    private Button scanButton;

    private boolean scanCompleted = false;
    private boolean listMode = false;
    private boolean pinVerified = false;
    private boolean pinScreenVisible = false;
    private boolean returningFromExternalScreen = false;

    private String currentCategory = "HOME";
    private String currentScreen = "HOME";
    private String pendingScreen = "HOME";

    private final List<FileInfo> allFiles = new ArrayList<>();
    private final List<FileInfo> visibleFiles = new ArrayList<>();
    private final List<AppInfo> installedApps = new ArrayList<>();
    private final List<String> uninstallQueue = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MediaPlayer mediaPlayer;
    private Button playingButton;
    private int uninstallIndex = 0;

    private EditText appSearch;
    private LinearLayout appListContainer;
    private String appSearchText = "";
    private boolean sortAppsBySize = true;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarDividerColor(BORDER);
        }

        boolean lockEnabled = getLockPreferences().getBoolean("enabled", false);
        if (lockEnabled) {
            pinVerified = false;
            showPinGate();
        } else {
            pinVerified = true;
            showDashboard();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!pinScreenVisible && !returningFromExternalScreen
                && getLockPreferences().getBoolean("enabled", false)
                && !pinVerified) {
            showPinGate();
        }

        returningFromExternalScreen = false;
    }

    private GradientDrawable panelBackground(int fill, int radius) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(245, Color.red(fill), Color.green(fill), Color.blue(fill)),
                        Color.argb(225, 8, 35, 29)
                }
        );
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), BORDER);
        return d;
    }

    private GradientDrawable solidBackground(int fill, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), BORDER);
        return d;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        t.setTypeface(Typeface.create(
                "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL
        ));
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this), lp(1, dp(height)));
    }

    private void hgap(LinearLayout parent, int width) {
        parent.addView(new View(this), lp(dp(width), 1));
    }

    private LinearLayout card() {
        LinearLayout c = vertical();
        c.setPadding(dp(13), dp(10), dp(13), dp(10));
        c.setBackground(panelBackground(CARD, 18));
        return c;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.rgb(3, 30, 19) : WHITE);
        b.setPadding(dp(9), 0, dp(9), 0);
        b.setMinHeight(dp(40));
        b.setBackground(solidBackground(primary ? GREEN : CARD_ALT, 13));
        return b;
    }

    private void setPage(boolean scrollable) {
        stopAudio();

        root = vertical();
        root.setBackgroundColor(BG);

        page = vertical();
        page.setPadding(dp(14), dp(7), dp(14), dp(8));

        if (scrollable) {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            scroll.addView(page);
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        setContentView(root);
    }

    private void addHeader(boolean showShare) {
        LinearLayout header = horizontal();

        LinearLayout brand = vertical();
        brand.addView(text("DroidTidy", 24, WHITE, true));
        brand.addView(text(
                "PHONE CLEANER  •  STORAGE MANAGER",
                8.5f,
                MUTED,
                true
        ));

        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        if (showShare) {
            Button share = button("↗ Share", false);
            header.addView(share, lp(dp(72), dp(36)));
            hgap(header, 6);

            TextView ready = text("● READY", 9, GREEN, true);
            ready.setGravity(Gravity.CENTER);
            ready.setPadding(dp(8), dp(7), dp(8), dp(7));
            ready.setBackground(solidBackground(Color.rgb(14, 53, 39), 20));
            header.addView(ready);
            share.setOnClickListener(v -> shareApp());
        } else {
            Button back = button("‹ Back", false);
            header.addView(back);
            back.setOnClickListener(v -> showDashboard());
        }

        page.addView(header);
    }

    private void showDashboard() {
        currentScreen = "HOME";
        setPage(true);
        addHeader(true);

        gap(page, 8);

        LinearLayout welcome = horizontal();
        welcome.setPadding(dp(12), dp(9), dp(12), dp(9));
        welcome.setBackground(panelBackground(Color.rgb(13, 54, 39), 16));

        LinearLayout welcomeText = vertical();
        welcomeText.addView(text("A FRESHER PHONE STARTS HERE", 10, GREEN, true));
        gap(welcomeText, 3);
        welcomeText.addView(text(
                "Clean up unwanted files. Keep what matters.",
                11,
                WHITE,
                false
        ));

        welcome.addView(welcomeText, new LinearLayout.LayoutParams(0, -2, 1));
        welcome.addView(text("✦  ♻", 23, GREEN, true));
        page.addView(welcome);

        gap(page, 8);
        addCompactStorageCard();
        gap(page, 8);

        LinearLayout scanRow = horizontal();
        scanRow.setPadding(dp(12), dp(8), dp(12), dp(8));
        scanRow.setBackground(panelBackground(CARD, 15));

        LinearLayout scanCopy = vertical();
        scanCopy.addView(text("SMART PHONE SCAN", 11, WHITE, true));
        scanCopy.addView(text("Find files taking up space", 9, MUTED, false));
        scanRow.addView(scanCopy, new LinearLayout.LayoutParams(0, -2, 1));

        scanButton = button("✦  SCAN NOW", true);
        scanRow.addView(scanButton, lp(dp(118), dp(43)));
        scanButton.setOnClickListener(v -> startScan());
        page.addView(scanRow);

        gap(page, 7);

        LinearLayout summary = horizontal();
        summary.setPadding(dp(10), dp(7), dp(10), dp(7));
        summary.setBackground(solidBackground(Color.rgb(11, 43, 33), 12));
        summary.addView(text("SCAN", 9, GREEN, true));
        hgap(summary, 8);

        scanSummary = text(
                scanCompleted
                        ? "Scan complete · " + allFiles.size() + " files found"
                        : "Ready to scan accessible media",
                9.5f,
                WHITE,
                false
        );
        scanSummary.setMaxLines(1);
        scanSummary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        summary.addView(scanSummary);
        page.addView(summary);

        gap(page, 9);

        LinearLayout toolTitle = horizontal();
        toolTitle.addView(
                text("CLEANING TOOLS", 11, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        Button viewMode = button(listMode ? "Grid" : "List", false);
        viewMode.setMinHeight(dp(30));
        toolTitle.addView(viewMode, lp(dp(58), dp(30)));
        page.addView(toolTitle);

        viewMode.setOnClickListener(v -> {
            listMode = !listMode;
            showDashboard();
        });

        addCompactTools();

        gap(page, 8);
        page.addView(text("POWER TOOLS", 10, GREEN, true));
        gap(page, 5);

        LinearLayout power = horizontal();
        addPowerButton(power, "▦ App Storage", this::showAppStorage);
        hgap(power, 5);
        addPowerButton(power, "⌑ App Locker", this::showAppLocker);
        hgap(power, 5);
        addPowerButton(power, "ϟ Battery", this::openBatterySettings);
        page.addView(power);

        gap(page, 8);
        dashboardStatus = text(
                scanCompleted
                        ? "Scan completed successfully."
                        : "Your files stay yours. Review before deleting.",
                8.5f,
                MUTED,
                false
        );
        dashboardStatus.setGravity(Gravity.CENTER);
        page.addView(dashboardStatus);
    }

    private void addCompactStorageCard() {
        LinearLayout storage = horizontal();
        storage.setPadding(dp(10), dp(6), dp(10), dp(6));
        storage.setBackground(panelBackground(CARD, 17));

        long total = 0;
        long available = 0;

        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            total = stat.getTotalBytes();
            available = stat.getAvailableBytes();
        } catch (Exception ignored) {
        }

        long used = Math.max(0, total - available);

        StorageGaugeView gauge = new StorageGaugeView(
                this, used, total, GREEN, CARD_ALT, WHITE, MUTED
        );
        storage.addView(gauge, lp(dp(104), dp(104)));
        hgap(storage, 10);

        LinearLayout stats = vertical();
        stats.addView(text("INTERNAL STORAGE", 10, MUTED, true));

        int percent = total > 0 ? (int) Math.min(100, used * 100L / total) : 0;
        gap(stats, 5);
        stats.addView(text(percent + "% USED", 18, GREEN, true));
        gap(stats, 4);
        stats.addView(text(formatSize(used) + " used", 12, WHITE, true));
        stats.addView(text(formatSize(available) + " available", 11, MUTED, false));
        gap(stats, 3);
        stats.addView(text("Reported by Android", 8, MUTED, false));

        storage.addView(stats, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(storage);
    }

    private void addPowerButton(LinearLayout parent, String label, Runnable action) {
    LinearLayout tile = new LinearLayout(this);
    tile.setOrientation(LinearLayout.VERTICAL);
    tile.setGravity(Gravity.CENTER);
    tile.setPadding(dp(4), dp(8), dp(4), dp(8));
    tile.setBackground(panelBackground(CARD_ALT, 15));

    String icon = "▦";
    String title = label;

    if (label.contains("Storage")) {
        icon = "▦";
        title = "App Storage";
    } else if (label.contains("Locker")) {
        icon = "⌑";
        title = "App Locker";
    } else if (label.contains("Battery")) {
        icon = "ϟ";
        title = "Battery";
    }

    TextView iconView = new TextView(this);
    iconView.setText(icon);
    iconView.setTextSize(29);
    iconView.setGravity(Gravity.CENTER);
    iconView.setTextColor(GREEN);

    TextView titleView = new TextView(this);
    titleView.setText(title);
    titleView.setTextSize(11);
    titleView.setGravity(Gravity.CENTER);
    titleView.setTextColor(Color.WHITE);

    tile.addView(iconView);
    tile.addView(titleView);

    LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(0, dp(82), 1);
    params.setMargins(dp(3), 0, dp(3), 0);

    parent.addView(tile, params);
    tile.setOnClickListener(v -> action.run());
}

    private void addCompactTools() {
        String[][] tools = {
                {"📸", "Duplicates", "DUPLICATES"},
                {"🖼", "Similar", "SIMILAR"},
                {"📅", "Old photos", "OLD"},
                {"📦", "Large files", "LARGE"},
                {"▣", "Screenshots", "SCREENSHOTS"},
                {"▶", "Videos", "VIDEOS"},
                {"♫", "Audio", "AUDIO"},
                {"🎙", "WhatsApp audio", "WHATSAPP_AUDIO"}
        };

        if (listMode) {
            for (String[] item : tools) {
                LinearLayout row = horizontal();
                row.setPadding(dp(8), dp(5), dp(8), dp(5));
                row.setBackground(panelBackground(CARD, 10));
                row.addView(text(item[0], 16, GREEN, true));
                hgap(row, 8);
                row.addView(text(item[1], 10, WHITE, true),
                        new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(text("›", 18, MUTED, true));

                LinearLayout.LayoutParams rlp = lp(-1, dp(34));
                rlp.setMargins(0, dp(2), 0, dp(2));
                page.addView(row, rlp);
                row.setOnClickListener(v -> openCategory(item[2]));
            }
            return;
        }

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(false);

        for (String[] item : tools) {
            LinearLayout tile = vertical();
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(3), dp(4), dp(3), dp(4));
            tile.setBackground(panelBackground(CARD, 12));

            TextView icon = text(item[0], 17, GREEN, true);
            icon.setGravity(Gravity.CENTER);
            tile.addView(icon);

            TextView label = text(item[1], 8.5f, WHITE, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(label);

            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 0;
            gp.height = dp(50);
            gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            gp.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(tile, gp);
            tile.setOnClickListener(v -> openCategory(item[2]));
        }

        page.addView(grid);
    }

    private void shareApp() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "DroidTidy");
        i.putExtra(Intent.EXTRA_TEXT,
                "Check out DroidTidy — Phone Cleaner & Storage Manager.\n"
                        + "https://github.com/thabisokhutlane26-oss/CleanMate");
        startActivity(Intent.createChooser(i, "Share DroidTidy"));
    }

    // MEDIA PERMISSIONS AND SCANNING

    private boolean granted(String permission) {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasMediaAccess() {
        if (Build.VERSION.SDK_INT < 33) {
            return granted(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        return granted(Manifest.permission.READ_MEDIA_IMAGES)
                || granted(Manifest.permission.READ_MEDIA_VIDEO)
                || granted(Manifest.permission.READ_MEDIA_AUDIO)
                || (Build.VERSION.SDK_INT >= 34
                && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED));
    }

    private void startScan() {
        if (!hasMediaAccess()) {
            if (Build.VERSION.SDK_INT >= 34) {
                requestPermissions(new String[]{
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                }, REQUEST_MEDIA);
            } else if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                }, REQUEST_MEDIA);
            } else {
                requestPermissions(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQUEST_MEDIA);
            }
            return;
        }
        runScan();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode == REQUEST_MEDIA) {
            if (hasMediaAccess()) {
                runScan();
            } else {
                Toast.makeText(this,
                        "Allow media access to scan your files.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void runScan() {
        if (scanButton != null) {
            scanButton.setEnabled(false);
            scanButton.setText("SCANNING");
        }
        if (scanSummary != null) scanSummary.setText("Scanning accessible media…");

        executor.execute(() -> {
            List<FileInfo> found = new ArrayList<>();

            try {
                if (Build.VERSION.SDK_INT < 33) {
                    scanCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "IMAGE", found);
                    scanCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "VIDEO", found);
                    scanCollection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "AUDIO", found);
                } else {
                    boolean selected = Build.VERSION.SDK_INT >= 34
                            && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);

                    if (granted(Manifest.permission.READ_MEDIA_IMAGES) || selected) {
                        scanCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "IMAGE", found);
                    }
                    if (granted(Manifest.permission.READ_MEDIA_VIDEO) || selected) {
                        scanCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "VIDEO", found);
                    }
                    if (granted(Manifest.permission.READ_MEDIA_AUDIO)) {
                        scanCollection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "AUDIO", found);
                    }
                }

                allFiles.clear();
                allFiles.addAll(found);
                scanCompleted = true;

                handler.post(() -> {
                    showDashboard();
                    scanSummary.setText("Scan complete · " + allFiles.size() + " accessible files");
                    dashboardStatus.setText("Last scan: " + java.text.DateFormat
                            .getDateTimeInstance().format(new java.util.Date()));
                    Toast.makeText(this, "Scan complete: " + allFiles.size() + " files",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    showDashboard();
                    Toast.makeText(this, "Scan failed. Check media permissions.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void scanCollection(Uri collection, String type, List<FileInfo> output) {
        String[] projection;
        if (Build.VERSION.SDK_INT >= 29) {
            projection = new String[]{
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.RELATIVE_PATH
            };
        } else {
            projection = new String[]{
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED
            };
        }

        try (Cursor c = getContentResolver().query(collection, projection, null, null, null)) {
            if (c == null) return;

            int idCol = c.getColumnIndex(MediaStore.MediaColumns._ID);
            int nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE);
            int dateCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
            int pathCol = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);

            while (c.moveToNext()) {
                long id = c.getLong(idCol);
                String name = nameCol >= 0 ? c.getString(nameCol) : "Unknown file";
                long size = sizeCol >= 0 ? Math.max(0, c.getLong(sizeCol)) : 0;
                long date = dateCol >= 0 ? c.getLong(dateCol) : 0;
                String folder = pathCol >= 0 ? c.getString(pathCol) : "";

                Uri uri = Uri.withAppendedPath(collection, String.valueOf(id));
                output.add(new FileInfo(name, size, date, type, uri, folder));
            }
        }
    }

    private void openCategory(String category) {
        if (!scanCompleted) {
            Toast.makeText(this, "Run a scan first.", Toast.LENGTH_SHORT).show();
            return;
        }

        currentCategory = category;
        visibleFiles.clear();

        long oldLimit = System.currentTimeMillis() / 1000L - OLD_FILE_AGE;
        Map<Long, Integer> counts = new HashMap<>();

        for (FileInfo f : allFiles) {
            if ("IMAGE".equals(f.type)) {
                counts.put(f.size, counts.containsKey(f.size)
                        ? counts.get(f.size) + 1 : 1);
            }
        }

        for (FileInfo f : allFiles) {
            boolean include = false;

            switch (category) {
                case "PHOTOS":
                    include = "IMAGE".equals(f.type);
                    break;
                case "VIDEOS":
                    include = "VIDEO".equals(f.type);
                    break;
                case "AUDIO":
                    include = "AUDIO".equals(f.type);
                    break;
                case "WHATSAPP_AUDIO":
                    String folder = f.folder.toLowerCase(Locale.US);
                    String name = f.name.toLowerCase(Locale.US);
                    include = "AUDIO".equals(f.type)
                            && (folder.contains("whatsapp")
                            || name.contains("ptt")
                            || name.contains("whatsapp"));
                    break;
                case "LARGE":
                    include = f.size >= LARGE_FILE_SIZE;
                    break;
                case "OLD":
                    include = "IMAGE".equals(f.type)
                            && f.dateModified > 0
                            && f.dateModified < oldLimit;
                    break;
                case "SCREENSHOTS":
                    String n = f.name.toLowerCase(Locale.US);
                    String p = f.folder.toLowerCase(Locale.US);
                    include = "IMAGE".equals(f.type)
                            && (n.contains("screenshot")
                            || n.contains("screen_shot")
                            || n.contains("screen-shot")
                            || p.contains("screenshot"));
                    break;
                case "DUPLICATES":
                case "SIMILAR":
                    include = "IMAGE".equals(f.type)
                            && counts.containsKey(f.size)
                            && counts.get(f.size) > 1;
                    break;
            }

            if (include) visibleFiles.add(f);
        }

        Collections.sort(visibleFiles, (a, b) -> Long.compare(b.size, a.size));
        showFileCategory(category);
    }

    private void showFileCategory(String category) {
        currentScreen = "FILES";
        setPage(true);
        addHeader(false);

        gap(page, 10);
        page.addView(text(categoryTitle(category), 20, WHITE, true));
        gap(page, 5);
        page.addView(text(visibleFiles.size() + " files · review before deleting",
                11, MUTED, false));
        gap(page, 10);

        LinearLayout actions = horizontal();
        Button select = button("Select all", false);
        Button delete = button("Delete selected", true);

        actions.addView(select, new LinearLayout.LayoutParams(0, dp(42), 1));
        hgap(actions, 7);
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(42), 1));
        page.addView(actions);

        select.setOnClickListener(v -> {
            boolean all = true;
            for (FileInfo f : visibleFiles) {
                if (!f.selected) {
                    all = false;
                    break;
                }
            }
            for (FileInfo f : visibleFiles) f.selected = !all;
            showFileCategory(category);
        });

        delete.setOnClickListener(v -> confirmDeleteSelected());
        gap(page, 9);

        if (visibleFiles.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("Nothing found here", 17, WHITE, true));
            gap(empty, 5);
            empty.addView(text(
                    category.equals("DUPLICATES") || category.equals("SIMILAR")
                            ? "No same-size candidates were found. Same size does not prove identical content."
                            : "No matching accessible files were found.",
                    12, MUTED, false));
            page.addView(empty);
            return;
        }

        if ("DUPLICATES".equals(category) || "SIMILAR".equals(category)) {
            page.addView(text(
                    "Candidates are grouped by file size, not image content. Compare each file before deleting.",
                    10, MUTED, false));
            gap(page, 6);
        }

        for (FileInfo f : visibleFiles) addFileRow(f);
    }

    private void addFileRow(FileInfo f) {
        LinearLayout row = horizontal();
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        row.setBackground(panelBackground(CARD, 12));

        CheckBox check = new CheckBox(this);
        check.setButtonTintList(ColorStateList.valueOf(GREEN));
        check.setChecked(f.selected);
        check.setOnCheckedChangeListener((b, checked) -> f.selected = checked);
        row.addView(check);

        if ("IMAGE".equals(f.type)) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);

            try {
                image.setImageURI(f.uri);
            } catch (Exception ignored) {
                image.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            LinearLayout.LayoutParams ilp = lp(dp(58), dp(58));
            ilp.setMargins(0, 0, dp(8), 0);
            row.addView(image, ilp);
            image.setOnClickListener(v -> openImage(f));
        }

        LinearLayout details = vertical();
        TextView name = text(f.name, 12, WHITE, true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(name);
        details.addView(text(formatSize(f.size) + "  •  " + f.type,
                10, MUTED, false));

        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        if ("AUDIO".equals(f.type)) {
            Button play = button("▶", false);
            row.addView(play, lp(dp(48), dp(40)));
            play.setOnClickListener(v -> playAudio(f, play));
        }

        LinearLayout.LayoutParams rlp = lp(-1, -2);
        rlp.setMargins(0, dp(2), 0, dp(2));
        page.addView(row, rlp);
    }

    private void openImage(FileInfo f) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(f.uri, "image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No photo viewer is available.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void playAudio(FileInfo f, Button b) {
        try {
            if (mediaPlayer != null && playingButton == b) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    b.setText("▶");
                } else {
                    mediaPlayer.start();
                    b.setText("Ⅱ");
                }
                return;
            }

            stopAudio();
            mediaPlayer = MediaPlayer.create(this, f.uri);

            if (mediaPlayer == null) {
                Toast.makeText(this, "This audio format cannot be played.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            playingButton = b;
            mediaPlayer.setOnCompletionListener(mp -> stopAudio());
            mediaPlayer.start();
            b.setText("Ⅱ");

        } catch (Exception e) {
            stopAudio();
            Toast.makeText(this, "Unable to play this audio file.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (playingButton != null) {
            playingButton.setText("▶");
            playingButton = null;
        }
    }

    private void confirmDeleteSelected() {
        int count = 0;
        for (FileInfo f : visibleFiles) if (f.selected) count++;

        if (count == 0) {
            Toast.makeText(this, "Select files first.", Toast.LENGTH_SHORT).show();
            return;
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Review deletion")
                .setMessage("Ask Android to delete " + count
                        + " selected file(s)? Android will show its own confirmation.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> deleteSelected())
                .show();
    }

    private void deleteSelected() {
        ArrayList<Uri> uris = new ArrayList<>();
        for (FileInfo f : visibleFiles) {
            if (f.selected) uris.add(f.uri);
        }

        if (uris.isEmpty()) {
            Toast.makeText(this, "Select files first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= 30) {
            try {
                PendingIntent request = MediaStore.createDeleteRequest(
                        getContentResolver(), uris);
                returningFromExternalScreen = true;
                startIntentSenderForResult(
                        request.getIntentSender(), REQUEST_DELETE,
                        null, 0, 0, 0);
            } catch (Exception e) {
                Toast.makeText(this,
                        "Could not open Android deletion confirmation.",
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this,
                    "Safe multi-file deletion requires Android 11 or newer.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        returningFromExternalScreen = false;

        if (requestCode == REQUEST_DELETE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Android accepted the deletion request.",
                        Toast.LENGTH_SHORT).show();
                scanCompleted = false;
                startScan();
            } else {
                Toast.makeText(this, "Deletion cancelled.",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_USAGE) {
            showAppStorage();
        } else if (requestCode == REQUEST_UNINSTALL) {
            // Continue the queue even when the user cancels one uninstall.
            uninstallIndex++;
            launchNextUninstall();
        }
    }

    private String categoryTitle(String category) {
        switch (category) {
            case "DUPLICATES": return "Duplicate photo candidates";
            case "SIMILAR": return "Similar photo candidates";
            case "OLD": return "Old photos";
            case "LARGE": return "Large files";
            case "SCREENSHOTS": return "Screenshots";
            case "VIDEOS": return "Videos";
            case "AUDIO": return "Audio files";
            case "WHATSAPP_AUDIO": return "WhatsApp voice notes";
            case "PHOTOS": return "All photos";
            default: return "Files";
        }
    }

    // APP STORAGE MANAGER

    private boolean hasUsageAccess() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(
                    Context.APP_OPS_SERVICE);

            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    getPackageName());

            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void requestUsageAccess() {
        try {
            returningFromExternalScreen = true;
            startActivityForResult(
                    new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                    REQUEST_USAGE);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Open Settings > Special app access > Usage access for DroidTidy.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showAppStorage() {
        currentScreen = "APPS";
        setPage(true);
        addHeader(false);

        gap(page, 10);
        page.addView(text("APP STORAGE MANAGER", 19, WHITE, true));
        gap(page, 4);
        page.addView(text(
                "Search installed apps and review their available storage information.",
                11, MUTED, false));
        gap(page, 8);

        if (!hasUsageAccess()) {
            LinearLayout access = card();
            access.addView(text("Enable Usage Access", 15, GREEN, true));
            gap(access, 5);
            access.addView(text(
                    "Android requires permission to show app usage history. Some app storage details may remain unavailable.",
                    11, MUTED, false));
            gap(access, 8);

            Button grant = button("Open Usage Access settings", true);
            access.addView(grant);
            grant.setOnClickListener(v -> requestUsageAccess());
            page.addView(access);
            gap(page, 8);
        }

        LinearLayout searchRow = horizontal();
        appSearch = new EditText(this);
        appSearch.setSingleLine(true);
        appSearch.setTextSize(12);
        appSearch.setHint("Search installed apps");
        appSearch.setHintTextColor(MUTED);
        appSearch.setTextColor(WHITE);
        appSearch.setPadding(dp(12), 0, dp(12), 0);
        appSearch.setBackground(solidBackground(CARD_ALT, 12));
        searchRow.addView(appSearch, new LinearLayout.LayoutParams(0, dp(44), 1));
        page.addView(searchRow);

        appSearch.setText(appSearchText);
        appSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                appSearchText = s.toString();
                renderAppRows();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        gap(page, 8);

        LinearLayout summary = horizontal();
        summary.addView(text(installedApps.size() + " apps loaded",
                11, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));

        Button refresh = button("↻ Refresh", false);
        summary.addView(refresh);
        page.addView(summary);
        refresh.setOnClickListener(v -> loadInstalledApps());

        gap(page, 8);

        LinearLayout controls = horizontal();
        Button selectAll = button("Select all", false);
        Button uninstall = button("Uninstall selected", true);
        controls.addView(selectAll, new LinearLayout.LayoutParams(0, dp(42), 1));
        hgap(controls, 6);
        controls.addView(uninstall, new LinearLayout.LayoutParams(0, dp(42), 1));
        page.addView(controls);

        selectAll.setOnClickListener(v -> {
            boolean all = true;
            for (AppInfo a : installedApps) {
                if (matchesAppSearch(a) && !a.selected) {
                    all = false;
                    break;
                }
            }
            for (AppInfo a : installedApps) {
                if (matchesAppSearch(a)) a.selected = !all;
            }
            renderAppRows();
        });
        uninstall.setOnClickListener(v -> beginUninstall());

        gap(page, 8);

        LinearLayout sortRow = horizontal();
        sortRow.addView(text("SORT", 9, MUTED, true));
        hgap(sortRow, 8);

        Button bySize = button("Largest first", false);
        Button byName = button("Name A–Z", false);
        sortRow.addView(bySize);
        hgap(sortRow, 5);
        sortRow.addView(byName);
        page.addView(sortRow);

        bySize.setOnClickListener(v -> {
            sortAppsBySize = true;
            Collections.sort(installedApps,
                    (a, b) -> Long.compare(b.sizeBytes, a.sizeBytes));
            renderAppRows();
        });

        byName.setOnClickListener(v -> {
            sortAppsBySize = false;
            Collections.sort(installedApps,
                    (a, b) -> a.label.compareToIgnoreCase(b.label));
            renderAppRows();
        });

        appListContainer = vertical();
        page.addView(appListContainer);

        if (installedApps.isEmpty()) {
            appListContainer.addView(text(
                    "Loading installed apps…", 12, MUTED, false));
            loadInstalledApps();
        } else {
            renderAppRows();
        }
    }

    private boolean matchesAppSearch(AppInfo app) {
        String q = appSearchText == null
                ? "" : appSearchText.trim().toLowerCase(Locale.US);
        return q.isEmpty()
                || app.label.toLowerCase(Locale.US).contains(q)
                || app.packageName.toLowerCase(Locale.US).contains(q);
    }

    private void loadInstalledApps() {
        executor.execute(() -> {
            List<AppInfo> result = new ArrayList<>();
            Map<String, Long> lastUsed = getLastUsedMap();
            PackageManager pm = getPackageManager();

            try {
                List<ApplicationInfo> apps =
                        pm.getInstalledApplications(PackageManager.GET_META_DATA);

                for (ApplicationInfo ai : apps) {
                    if (ai.packageName.equals(getPackageName())) continue;

                    Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
                    if (launch == null) continue;

                    String label;
                    try {
                        label = pm.getApplicationLabel(ai).toString();
                    } catch (Exception e) {
                        label = ai.packageName;
                    }

                    long size = queryAppSize(ai.packageName);
                    long used = lastUsed.containsKey(ai.packageName)
                            ? lastUsed.get(ai.packageName) : 0L;

                    result.add(new AppInfo(label, ai.packageName, size, used));
                }
            } catch (Exception ignored) {
            }

            if (sortAppsBySize) {
                Collections.sort(result,
                        (a, b) -> Long.compare(b.sizeBytes, a.sizeBytes));
            } else {
                Collections.sort(result,
                        (a, b) -> a.label.compareToIgnoreCase(b.label));
            }

            handler.post(() -> {
                installedApps.clear();
                installedApps.addAll(result);
                if ("APPS".equals(currentScreen)) {
                    showAppStorage();
                }
            });
        });
    }

    private Map<String, Long> getLastUsedMap() {
        Map<String, Long> map = new HashMap<>();
        if (!hasUsageAccess()) return map;

        try {
            UsageStatsManager manager =
                    (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            long end = System.currentTimeMillis();
            long start = end - 365L * 24L * 60L * 60L * 1000L;

            List<UsageStats> stats = manager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, start, end);

            if (stats != null) {
                for (UsageStats s : stats) {
                    long previous = map.containsKey(s.getPackageName())
                            ? map.get(s.getPackageName()) : 0L;
                    if (s.getLastTimeUsed() > previous) {
                        map.put(s.getPackageName(), s.getLastTimeUsed());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private long queryAppSize(String packageName) {
        if (Build.VERSION.SDK_INT < 26 || !hasUsageAccess()) return -1L;

        try {
            StorageStatsManager manager =
                    (StorageStatsManager) getSystemService(
                            Context.STORAGE_STATS_SERVICE);
            StorageStats stats = manager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    packageName,
                    android.os.Process.myUserHandle());

            return stats.getAppBytes() + stats.getDataBytes() + stats.getCacheBytes();
        } catch (Exception e) {
            return -1L;
        }
    }

    private void renderAppRows() {
        if (!"APPS".equals(currentScreen) || appListContainer == null) return;

        appListContainer.removeAllViews();
        int shown = 0;

        for (AppInfo app : installedApps) {
            if (!matchesAppSearch(app)) continue;
            shown++;

            LinearLayout row = horizontal();
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            row.setBackground(panelBackground(CARD, 12));

            CheckBox cb = new CheckBox(this);
            cb.setButtonTintList(ColorStateList.valueOf(GREEN));
            cb.setChecked(app.selected);
            cb.setOnCheckedChangeListener((b, checked) -> app.selected = checked);
            row.addView(cb);

            LinearLayout details = vertical();
            details.addView(text(app.label, 12, WHITE, true));
            details.addView(text(app.packageName, 8, MUTED, false));

            String size = app.sizeBytes >= 0
                    ? formatSize(app.sizeBytes) : "Size unavailable";
            String last = app.lastUsed > 0
                    ? "Last used " + relativeTime(app.lastUsed)
                    : (hasUsageAccess()
                    ? "No recent usage history" : "Usage access not granted");

            details.addView(text(size + "  •  " + last, 9, MUTED, false));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

            Button remove = button("Remove", false);
            remove.setTextSize(9);
            row.addView(remove, lp(dp(60), dp(36)));

            remove.setOnClickListener(v -> {
                uninstallQueue.clear();
                uninstallQueue.add(app.packageName);
                uninstallIndex = 0;
                launchNextUninstall();
            });

            LinearLayout.LayoutParams rlp = lp(-1, -2);
            rlp.setMargins(0, dp(2), 0, dp(2));
            appListContainer.addView(row, rlp);
        }

        if (shown == 0) {
            appListContainer.addView(text(
                    "No matching apps found.", 12, MUTED, false));
        }
    }

    private void beginUninstall() {
        uninstallQueue.clear();
        for (AppInfo app : installedApps) {
            if (app.selected && matchesAppSearch(app)) {
                uninstallQueue.add(app.packageName);
            }
        }

        if (uninstallQueue.isEmpty()) {
            Toast.makeText(this, "Select at least one app.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        uninstallIndex = 0;
        Toast.makeText(this,
                "Android will ask you to confirm each uninstall separately.",
                Toast.LENGTH_LONG).show();
        launchNextUninstall();
    }

    private void launchNextUninstall() {
        if (uninstallIndex >= uninstallQueue.size()) {
            uninstallQueue.clear();
            uninstallIndex = 0;
            loadInstalledApps();
            return;
        }

        String packageName = uninstallQueue.get(uninstallIndex);
        try {
            Intent i = new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:" + packageName));
            returningFromExternalScreen = true;
            startActivityForResult(i, REQUEST_UNINSTALL);
        } catch (Exception e) {
            uninstallIndex++;
            launchNextUninstall();
        }
    }

    // APP LOCKER - PBKDF2 HASHED PIN

    private android.content.SharedPreferences getLockPreferences() {
        return getSharedPreferences("droidtidy_lock", MODE_PRIVATE);
    }

    private byte[] createSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private byte[] hashPin(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
                pin.toCharArray(), salt, 120000, 256);
        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private boolean verifyPin(String pin) {
        try {
            String saltText = getLockPreferences().getString("salt", null);
            String hashText = getLockPreferences().getString("hash", null);
            if (saltText == null || hashText == null) return false;

            byte[] salt = android.util.Base64.decode(saltText,
                    android.util.Base64.NO_WRAP);
            byte[] expected = android.util.Base64.decode(hashText,
                    android.util.Base64.NO_WRAP);
            byte[] actual = hashPin(pin, salt);

            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private void savePin(String pin) throws Exception {
        byte[] salt = createSalt();
        byte[] hash = hashPin(pin, salt);

        getLockPreferences().edit()
                .putString("salt", android.util.Base64.encodeToString(
                        salt, android.util.Base64.NO_WRAP))
                .putString("hash", android.util.Base64.encodeToString(
                        hash, android.util.Base64.NO_WRAP))
                .putBoolean("enabled", true)
                .apply();
    }

    private void showPinGate() {
        pinScreenVisible = true;
        currentScreen = "PIN_GATE";
        setPage(false);

        LinearLayout container = vertical();
        container.setGravity(Gravity.CENTER);
        container.setPadding(dp(22), dp(20), dp(22), dp(20));
        root.addView(container, new LinearLayout.LayoutParams(-1, -1));

        TextView logo = text("DroidTidy", 28, WHITE, true);
        logo.setGravity(Gravity.CENTER);
        container.addView(logo);

        gap(container, 8);
        TextView heading = text("APP LOCKED", 13, GREEN, true);
        heading.setGravity(Gravity.CENTER);
        container.addView(heading);

        gap(container, 8);
        TextView description = text(
                "Enter your PIN to continue.",
                12, MUTED, false);
        description.setGravity(Gravity.CENTER);
        container.addView(description);

        gap(container, 18);
        EditText pin = new EditText(this);
        pin.setSingleLine(true);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setHint("Enter PIN");
        pin.setHintTextColor(MUTED);
        pin.setTextColor(WHITE);
        pin.setGravity(Gravity.CENTER);
        pin.setBackground(solidBackground(CARD_ALT, 12));
        container.addView(pin, lp(-1, dp(50)));

        gap(container, 12);
        Button unlock = button("UNLOCK", true);
        container.addView(unlock, lp(-1, dp(46)));

        TextView error = text("", 11, Color.rgb(255, 110, 110), false);
        error.setGravity(Gravity.CENTER);
        container.addView(error);

        unlock.setOnClickListener(v -> {
            String value = pin.getText().toString();

            if (verifyPin(value)) {
                pinVerified = true;
                pinScreenVisible = false;
                showDashboard();
            } else {
                error.setText("Incorrect PIN. Try again.");
                pin.setText("");
            }
        });

        pin.setOnEditorActionListener((v, actionId, event) -> {
            unlock.performClick();
            return true;
        });
    }

    private void showAppLocker() {
        currentScreen = "LOCKER";
        setPage(true);
        addHeader(false);

        gap(page, 12);
        page.addView(text("APP LOCKER", 20, WHITE, true));
        gap(page, 8);

        LinearLayout info = card();
        info.addView(text("Protect DroidTidy with a private PIN",
                15, GREEN, true));
        gap(info, 7);
        info.addView(text(
                "This PIN protects DroidTidy itself when you reopen it. Android does not allow ordinary apps to silently lock other apps.",
                11, MUTED, false));
        gap(info, 12);

        boolean enabled = getLockPreferences().getBoolean("enabled", false);
        info.addView(text(enabled ? "PIN protection is ON" : "PIN protection is OFF",
                12, enabled ? GREEN : MUTED, true));
        gap(info, 8);

        EditText currentPin = new EditText(this);
        currentPin.setSingleLine(true);
        currentPin.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        currentPin.setHint(enabled ? "Current PIN" : "Optional: existing PIN");
        currentPin.setHintTextColor(MUTED);
        currentPin.setTextColor(WHITE);
        currentPin.setBackground(solidBackground(CARD_ALT, 12));
        info.addView(currentPin, lp(-1, dp(46)));

        gap(info, 7);
        EditText newPin = new EditText(this);
        newPin.setSingleLine(true);
        newPin.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        newPin.setHint("New PIN (4–8 digits)");
        newPin.setHintTextColor(MUTED);
        newPin.setTextColor(WHITE);
        newPin.setBackground(solidBackground(CARD_ALT, 12));
        info.addView(newPin, lp(-1, dp(46)));

        gap(info, 8);
        Button save = button(enabled ? "Change PIN" : "Enable PIN protection", true);
        info.addView(save);

        save.setOnClickListener(v -> {
            String oldValue = currentPin.getText().toString();
            String newValue = newPin.getText().toString();

            if (enabled && !verifyPin(oldValue)) {
                Toast.makeText(this, "Current PIN is incorrect.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newValue.matches("[0-9]{4,8}")) {
                Toast.makeText(this, "Enter a PIN with 4 to 8 digits.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                savePin(newValue);
                Toast.makeText(this, "PIN protection enabled.",
                        Toast.LENGTH_SHORT).show();
                showAppLocker();
            } catch (Exception e) {
                Toast.makeText(this, "Could not save PIN securely.",
                        Toast.LENGTH_LONG).show();
            }
        });

        gap(info, 8);
        Button remove = button("Disable PIN protection", false);
        info.addView(remove);

        remove.setOnClickListener(v -> {
            if (!enabled) {
                Toast.makeText(this, "PIN protection is already off.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String oldValue = currentPin.getText().toString();
            if (!verifyPin(oldValue)) {
                Toast.makeText(this, "Enter your current PIN first.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            getLockPreferences().edit().clear().apply();
            pinVerified = true;
            Toast.makeText(this, "PIN protection disabled.",
                    Toast.LENGTH_SHORT).show();
            showAppLocker();
        });

        page.addView(info);
    }

    // BATTERY SETTINGS

    private void openBatterySettings() {
        Intent i = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
        if (i.resolveActivity(getPackageManager()) == null) {
            i = new Intent(Settings.ACTION_SETTINGS);
        }

        try {
            returningFromExternalScreen = true;
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Battery settings are not available on this device.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // HELPERS

    private String relativeTime(long timestamp) {
        long diff = Math.max(0, System.currentTimeMillis() - timestamp);
        long days = diff / (24L * 60L * 60L * 1000L);

        if (days == 0) return "today";
        if (days == 1) return "yesterday";
        if (days < 30) return days + " days ago";

        long months = days / 30;
        if (months < 12) return months + (months == 1 ? " month ago" : " months ago");

        long years = days / 365;
        return years + (years == 1 ? " year ago" : " years ago");
    }

    private String formatSize(long bytes) {
        if (bytes < 0) return "Unavailable";

        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.2f GB",
                    bytes / (1024.0 * 1024.0 * 1024.0));
        }
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB",
                    bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private static class FileInfo {
        String name;
        long size;
        long dateModified;
        String type;
        Uri uri;
        String folder;
        boolean selected;

        FileInfo(String name, long size, long dateModified,
                 String type, Uri uri, String folder) {
            this.name = name;
            this.size = size;
            this.dateModified = dateModified;
            this.type = type;
            this.uri = uri;
            this.folder = folder == null ? "" : folder;
        }
    }

    private static class AppInfo {
        String label;
        String packageName;
        long sizeBytes;
        long lastUsed;
        boolean selected;

        AppInfo(String label, String packageName,
                long sizeBytes, long lastUsed) {
            this.label = label;
            this.packageName = packageName;
            this.sizeBytes = sizeBytes;
            this.lastUsed = lastUsed;
        }
    }

    @Override
    public void onBackPressed() {
        if (!pinVerified && getLockPreferences().getBoolean("enabled", false)) {
            showPinGate();
            return;
        }

        if (!"HOME".equals(currentScreen)) {
            showDashboard();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        stopAudio();
        executor.shutdownNow();
        super.onDestroy();
    }
}
package com.cleanmate.app;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_STORAGE = 100;
    private static final int REQUEST_DELETE = 200;
    private static final long LARGE_FILE_SIZE = 50L * 1024L * 1024L;
    private static final long OLD_FILE_AGE = 365L * 24L * 60L * 60L;

    private static final int BG = Color.rgb(10, 16, 24);
    private static final int CARD = Color.rgb(20, 29, 40);
    private static final int CARD_LIGHT = Color.rgb(29, 41, 54);
    private static final int GREEN = Color.rgb(42, 220, 130);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(155, 169, 184);

    private LinearLayout root;
    private LinearLayout content;
    private TextView status;
    private TextView storageText;
    private TextView scanSummary;
    private Button scanButton;

    private boolean listMode = false;
    private boolean scanCompleted = false;

    private final List<FileInfo> allFiles = new ArrayList<>();
    private final List<FileInfo> visibleFiles = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private String currentCategory = "HOME";

    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildDashboard();
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView makeText(
            String value,
            float size,
            int color,
            boolean bold) {

        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams params(
            int width,
            int height) {

        return new LinearLayout.LayoutParams(width, height);
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, params(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(height)));
    }

    private TextView sectionTitle(String title) {
        TextView view = makeText(title, 15, WHITE, true);
        view.setPadding(0, dp(10), 0, dp(10));
        return view;
    }

    private TextView pill(String label) {
        TextView view = makeText(label, 12, GREEN, true);
        view.setPadding(dp(12), dp(7), dp(12), dp(7));
        view.setBackground(shape(
                Color.rgb(24, 58, 47), 30));
        return view;
    }

    private Button styledButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(shape(CARD_LIGHT, 14));
        return button;
    }

    private LinearLayout card() {
        LinearLayout layout = vertical();
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(shape(CARD, 20));
        return layout;
    }

    private void buildDashboard() {
        root = vertical();
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        content = vertical();
        content.setPadding(dp(18), dp(22), dp(18), dp(28));

        scroll.addView(content);
        root.addView(scroll);

        setContentView(root);

        showHome();
    }

    private void showHome() {
        currentCategory = "HOME";
        content.removeAllViews();

        LinearLayout header = horizontal();

        LinearLayout brand = vertical();
        brand.addView(makeText(
                "DroidTidy", 29, WHITE, true));

        brand.addView(makeText(
                "PHONE CLEANER  •  STORAGE MANAGER",
                10, MUTED, true));

        header.addView(brand,
                new LinearLayout.LayoutParams(
                        0, -2, 1));

        header.addView(pill("● READY"));
        content.addView(header);

        addSpace(content, 22);

        addStorageCard();

        addSpace(content, 18);

        LinearLayout scanCard = card();
        scanCard.setGravity(Gravity.CENTER);

        scanCard.addView(makeText(
                "SMART PHONE SCAN",
                13, MUTED, true));

        addSpace(scanCard, 14);

        scanButton = new Button(this);
        scanButton.setText("SCAN");
        scanButton.setTextSize(21);
        scanButton.setTypeface(
                Typeface.DEFAULT, Typeface.BOLD);
        scanButton.setTextColor(Color.rgb(7, 27, 18));
        scanButton.setAllCaps(false);
        scanButton.setBackground(shape(GREEN, 100));

        LinearLayout.LayoutParams scanParams =
                params(dp(132), dp(132));

        scanCard.addView(scanButton, scanParams);

        addSpace(scanCard, 12);

        scanCard.addView(makeText(
                "Find files taking up space",
                13, MUTED, false));

        scanButton.setOnClickListener(v -> startScan());

        content.addView(scanCard);

        addSpace(content, 18);

        LinearLayout summaryCard = card();

        summaryCard.addView(makeText(
                "SMART CLEANUP",
                13, GREEN, true));

        addSpace(summaryCard, 8);

        scanSummary = makeText(
                scanCompleted
                        ? "Your latest scan is ready."
                        : "Scan your phone to discover files you can review.",
                14, WHITE, false);

        summaryCard.addView(scanSummary);

        content.addView(summaryCard);

        addSpace(content, 18);

        LinearLayout toolsHeader = horizontal();

        toolsHeader.addView(sectionTitle("CLEANING TOOLS"),
                new LinearLayout.LayoutParams(0, -2, 1));

        Button gridButton = styledButton(
                listMode ? "Grid" : "List");

        toolsHeader.addView(gridButton);
        content.addView(toolsHeader);

        gridButton.setOnClickListener(v -> {
            listMode = !listMode;
            showHome();
        });

        addTools();

        addSpace(content, 18);

        status = makeText(
                scanCompleted
                        ? "Last scan completed."
                        : "Ready when you are.",
                12, MUTED, false);

        status.setGravity(Gravity.CENTER);
        content.addView(status);
    }

    private void addStorageCard() {
        LinearLayout storage = card();

        LinearLayout row = horizontal();

        row.addView(makeText(
                "PHONE STORAGE",
                13, MUTED, true));

        storage.addView(row);

        addSpace(storage, 10);

        long total = 0;
        long available = 0;

        try {
            StatFs stat = new StatFs(
                    Environment.getDataDirectory().getPath());

            total = stat.getTotalBytes();
            available = stat.getAvailableBytes();

        } catch (Exception ignored) {
        }

        long used = Math.max(0, total - available);

        storageText = makeText(
                formatSize(used) + " used",
                23, WHITE, true);

        storage.addView(storageText);

        addSpace(storage, 5);

        storage.addView(makeText(
                formatSize(available) + " available",
                12, MUTED, false));

        addSpace(storage, 12);

        android.widget.ProgressBar progress =
                new android.widget.ProgressBar(
                        this, null,
                        android.R.attr.progressBarStyleHorizontal);

        progress.setMax(100);

        int percentage = total > 0
                ? (int) ((used * 100L) / total)
                : 0;

        progress.setProgress(Math.min(100, percentage));
        progress.setProgressTintList(
                android.content.res.ColorStateList.valueOf(GREEN));

        progress.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        CARD_LIGHT));

        storage.addView(progress,
                params(-1, dp(8)));

        content.addView(storage);
    }

    private void addTools() {
        String[][] tools = {
                {"📸", "Duplicate photos", "DUPLICATES"},
                {"🖼", "Similar photos", "SIMILAR"},
                {"📅", "Old photos", "OLD"},
                {"📦", "Large files", "LARGE"},
                {"📱", "Screenshots", "SCREENSHOTS"},
                {"🎬", "Videos", "VIDEOS"},
                {"🎵", "Audio files", "AUDIO"},
                {"🖼", "All photos", "PHOTOS"}
        };

        if (listMode) {
            for (String[] tool : tools) {
                addToolRow(tool[0], tool[1], tool[2]);
            }
        } else {
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(2);
            grid.setUseDefaultMargins(false);

            for (String[] tool : tools) {
                LinearLayout tile = vertical();
                tile.setGravity(Gravity.CENTER_VERTICAL);
                tile.setPadding(dp(14), dp(14), dp(10), dp(14));
                tile.setBackground(shape(CARD, 16));

                TextView icon = makeText(
                        tool[0], 24, WHITE, false);

                TextView name = makeText(
                        tool[1], 13, WHITE, true);

                tile.addView(icon);
                addSpace(tile, 8);
                tile.addView(name);

                GridLayout.LayoutParams gp =
                        new GridLayout.LayoutParams();

                gp.width = 0;
                gp.height = dp(102);
                gp.columnSpec =
                        GridLayout.spec(GridLayout.UNDEFINED, 1f);

                gp.setMargins(dp(4), dp(4), dp(4), dp(4));

                grid.addView(tile, gp);

                tile.setOnClickListener(v ->
                        openCategory(tool[2]));
            }

            content.addView(grid);
        }
    }

    private void addToolRow(
            String icon,
            String name,
            String category) {

        LinearLayout row = horizontal();
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackground(shape(CARD, 14));

        TextView iconView = makeText(
                icon, 23, WHITE, false);

        row.addView(iconView);
        addHorizontalSpace(row, 14);

        row.addView(makeText(
                name, 14, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));

        row.addView(makeText(
                "›", 25, MUTED, false));

        LinearLayout.LayoutParams lp =
                params(-1, dp(62));

        lp.setMargins(0, dp(4), 0, dp(4));
        content.addView(row, lp);

        row.setOnClickListener(v ->
                openCategory(category));
    }

    private void addHorizontalSpace(
            LinearLayout parent,
            int width) {

        View space = new View(this);
        parent.addView(space,
                params(dp(width), 1));
    }

    private boolean hasPermission(String permission) {
        return Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(permission) ==
                        PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAnyMediaPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return hasPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        boolean images = hasPermission(
                Manifest.permission.READ_MEDIA_IMAGES);

        boolean videos = hasPermission(
                Manifest.permission.READ_MEDIA_VIDEO);

        boolean audio = hasPermission(
                Manifest.permission.READ_MEDIA_AUDIO);

        boolean selected = Build.VERSION.SDK_INT >= 34 &&
                hasPermission(
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);

        return images || videos || audio || selected;
    }

    private void startScan() {
        if (!hasAnyMediaPermission()) {
            requestStoragePermissions();
            return;
        }

        runScan();
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissions(new String[] {
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            }, REQUEST_STORAGE);

        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] {
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            }, REQUEST_STORAGE);

        } else {
            requestPermissions(new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, REQUEST_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == REQUEST_STORAGE) {
            if (hasAnyMediaPermission()) {
                runScan();
            } else {
                Toast.makeText(this,
                        "Allow media access to scan your phone.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void runScan() {
        scanCompleted = false;

        if (scanButton != null) {
            scanButton.setEnabled(false);
            scanButton.setText("...");
        }

        if (status != null) {
            status.setText("Scanning your accessible files...");
        }

        Toast.makeText(this,
                "Scanning phone media...",
                Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            List<FileInfo> found = new ArrayList<>();

            try {
                if (Build.VERSION.SDK_INT < 33) {
                    scanCollection(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            "IMAGE", found);

                    scanCollection(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            "VIDEO", found);

                    scanCollection(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            "AUDIO", found);

                } else {
                    boolean selected = Build.VERSION.SDK_INT >= 34 &&
                            hasPermission(
                                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);

                    if (hasPermission(
                            Manifest.permission.READ_MEDIA_IMAGES) || selected) {
                        scanCollection(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                "IMAGE", found);
                    }

                    if (hasPermission(
                            Manifest.permission.READ_MEDIA_VIDEO) || selected) {
                        scanCollection(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                "VIDEO", found);
                    }

                    if (hasPermission(
                            Manifest.permission.READ_MEDIA_AUDIO)) {
                        scanCollection(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                "AUDIO", found);
                    }
                }

                allFiles.clear();
                allFiles.addAll(found);

                scanCompleted = true;

                handler.post(() -> {
                    showHome();

                    if (scanSummary != null) {
                        scanSummary.setText(
                                "Scan complete. Found " +
                                allFiles.size() +
                                " accessible media files. Choose a tool below to review them.");
                    }

                    if (status != null) {
                        status.setText("Scan completed successfully.");
                    }

                    Toast.makeText(this,
                            "Scan completed: " + allFiles.size() + " files",
                            Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                handler.post(() -> {
                    showHome();

                    Toast.makeText(this,
                            "Scan failed. Check media permissions.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void scanCollection(
            Uri collection,
            String type,
            List<FileInfo> output) {

        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
        };

        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                null,
                null,
                null)) {

            if (cursor == null) return;

            int idColumn = cursor.getColumnIndex(
                    MediaStore.MediaColumns._ID);

            int nameColumn = cursor.getColumnIndex(
                    MediaStore.MediaColumns.DISPLAY_NAME);

            int sizeColumn = cursor.getColumnIndex(
                    MediaStore.MediaColumns.SIZE);

            int dateColumn = cursor.getColumnIndex(
                    MediaStore.MediaColumns.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);

                String name = nameColumn >= 0
                        ? cursor.getString(nameColumn)
                        : "Unknown file";

                long size = sizeColumn >= 0
                        ? cursor.getLong(sizeColumn)
                        : 0;

                long date = dateColumn >= 0
                        ? cursor.getLong(dateColumn)
                        : 0;

                if (size < 0) size = 0;

                Uri fileUri = Uri.withAppendedPath(
                        collection, String.valueOf(id));

                FileInfo file = new FileInfo(
                        name, size, date, type, fileUri);

                output.add(file);
            }
        }
    }

    private void openCategory(String category) {
        if (!scanCompleted) {
            Toast.makeText(this,
                    "Please scan your phone first.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        currentCategory = category;
        visibleFiles.clear();

        long oldLimit =
                System.currentTimeMillis() / 1000L - OLD_FILE_AGE;

        Map<Long, Integer> sizeCounts = new HashMap<>();

        for (FileInfo file : allFiles) {
            if ("IMAGE".equals(file.type)) {
                sizeCounts.put(file.size,
                        sizeCounts.containsKey(file.size)
                                ? sizeCounts.get(file.size) + 1 : 1);
            }
        }

        for (FileInfo file : allFiles) {
            boolean include = false;

            switch (category) {
                case "PHOTOS":
                    include = "IMAGE".equals(file.type);
                    break;

                case "VIDEOS":
                    include = "VIDEO".equals(file.type);
                    break;

                case "AUDIO":
                    include = "AUDIO".equals(file.type);
                    break;

                case "LARGE":
                    include = file.size >= LARGE_FILE_SIZE;
                    break;

                case "OLD":
                    include = "IMAGE".equals(file.type) &&
                            file.dateModified > 0 &&
                            file.dateModified < oldLimit;
                    break;

                case "SCREENSHOTS":
                    String lower = file.name.toLowerCase(Locale.US);
                    include = "IMAGE".equals(file.type) &&
                            (lower.contains("screenshot") ||
                             lower.contains("screen_shot") ||
                             lower.contains("screen-shot"));
                    break;

                case "DUPLICATES":
                case "SIMILAR":
                    include = "IMAGE".equals(file.type) &&
                            sizeCounts.get(file.size) != null &&
                            sizeCounts.get(file.size) > 1;
                    break;
            }

            if (include) {
                visibleFiles.add(file);
            }
        }

        showFileCategory(category);
    }

    private void showFileCategory(String category) {
        content.removeAllViews();

        LinearLayout top = horizontal();

        Button back = styledButton("‹ Back");
        top.addView(back);

        top.addView(makeText(
                categoryTitle(category),
                18, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));

        content.addView(top);

        back.setOnClickListener(v -> showHome());

        addSpace(content, 16);

        LinearLayout info = card();

        info.addView(makeText(
                visibleFiles.size() + " files found",
                19, WHITE, true));

        addSpace(info, 5);

        info.addView(makeText(
                "Review files before choosing what to remove.",
                12, MUTED, false));

        content.addView(info);

        addSpace(content, 12);

        LinearLayout actions = horizontal();

        Button selectAll = styledButton("Select all");
        Button delete = styledButton("Delete selected");

        actions.addView(selectAll,
                new LinearLayout.LayoutParams(0, dp(48), 1));

        addHorizontalSpace(actions, 8);

        actions.addView(delete,
                new LinearLayout.LayoutParams(0, dp(48), 1));

        content.addView(actions);

        selectAll.setOnClickListener(v -> {
            for (FileInfo file : visibleFiles) {
                file.selected = true;
            }
            showFileCategory(category);
        });

        delete.setOnClickListener(v -> deleteSelected());

        addSpace(content, 12);

        if (visibleFiles.isEmpty()) {
            LinearLayout empty = card();

            empty.addView(makeText(
                    "Nothing found here",
                    18, WHITE, true));

            addSpace(empty, 8);

            empty.addView(makeText(
                    category.equals("DUPLICATES")
                            ? "No same-size duplicate candidates were found."
                            : "There are no matching accessible files in this category.",
                    13, MUTED, false));

            content.addView(empty);
            return;
        }

        if (category.equals("SIMILAR") ||
                category.equals("DUPLICATES")) {

            TextView note = makeText(
                    category.equals("DUPLICATES")
                            ? "These are same-size candidates. File size alone does not prove two photos are identical."
                            : "These are same-size photo candidates for you to compare manually.",
                    12, MUTED, false);

            note.setPadding(dp(4), dp(4), dp(4), dp(12));
            content.addView(note);
        }

        for (FileInfo file : visibleFiles) {
            addFileRow(file);
        }
    }

    private void addFileRow(FileInfo file) {
        LinearLayout row = horizontal();
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackground(shape(CARD, 13));

        CheckBox check = new CheckBox(this);
        check.setButtonTintList(
                android.content.res.ColorStateList.valueOf(GREEN));
        check.setChecked(file.selected);

        check.setOnCheckedChangeListener(
                (button, checked) -> file.selected = checked);

        row.addView(check);

        LinearLayout details = vertical();

        details.addView(makeText(
                file.name, 13, WHITE, true));

        addSpace(details, 4);

        details.addView(makeText(
                formatSize(file.size) + "  •  " +
                        file.type,
                11, MUTED, false));

        row.addView(details,
                new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams lp =
                params(-1, -2);

        lp.setMargins(0, dp(3), 0, dp(3));

        content.addView(row, lp);
    }

    private String categoryTitle(String category) {
        switch (category) {
            case "DUPLICATES": return "Duplicate candidates";
            case "SIMILAR": return "Similar photo candidates";
            case "OLD": return "Old photos";
            case "LARGE": return "Large files";
            case "SCREENSHOTS": return "Screenshots";
            case "VIDEOS": return "Videos";
            case "AUDIO": return "Audio files";
            case "PHOTOS": return "All photos";
            default: return "Files";
        }
    }

    private void deleteSelected() {
        ArrayList<Uri> selectedUris = new ArrayList<>();

        for (FileInfo file : visibleFiles) {
            if (file.selected) {
                selectedUris.add(file.uri);
            }
        }

        if (selectedUris.isEmpty()) {
            Toast.makeText(this,
                    "Select at least one file first.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= 30) {
            try {
                PendingIntent request =
                        MediaStore.createDeleteRequest(
                                getContentResolver(),
                                selectedUris);

                startIntentSenderForResult(
                        request.getIntentSender(),
                        REQUEST_DELETE,
                        null,
                        0,
                        0,
                        0);

            } catch (Exception e) {
                Toast.makeText(this,
                        "Could not open Android deletion confirmation.",
                        Toast.LENGTH_LONG).show();
            }

        } else {
            Toast.makeText(this,
                    "Safe system deletion requires Android 11 or newer in this version.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode, resultCode, data);

        if (requestCode == REQUEST_DELETE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this,
                        "Android confirmed the deletion request.",
                        Toast.LENGTH_LONG).show();

                runScan();
            } else {
                Toast.makeText(this,
                        "Deletion cancelled.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.2f GB",
                    bytes / (1024.0 * 1024.0 * 1024.0));
        }

        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.2f MB",
                    bytes / (1024.0 * 1024.0));
        }

        if (bytes >= 1024L) {
            return String.format(Locale.US, "%.2f KB",
                    bytes / 1024.0);
        }

        return bytes + " bytes";
    }

    private static class FileInfo {
        String name;
        long size;
        long dateModified;
        String type;
        Uri uri;
        boolean selected = false;

        FileInfo(
                String name,
                long size,
                long dateModified,
                String type,
                Uri uri) {

            this.name = name;
            this.size = size;
            this.dateModified = dateModified;
            this.type = type;
            this.uri = uri;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
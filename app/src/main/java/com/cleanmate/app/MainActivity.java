package com.cleanmate.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_STORAGE = 100;
    private static final long LARGE_FILE_SIZE = 50L * 1024L * 1024L;

    private TextView status;
    private TextView results;
    private Button scanButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button menuButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(34, 48, 58));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(58));

        params.setMargins(0, dp(6), 0, dp(6));
        button.setLayoutParams(params);

        return button;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int background = Color.rgb(16, 24, 32);
        int white = Color.WHITE;
        int light = Color.LTGRAY;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(background);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(20), dp(28), dp(20), dp(28));

        TextView title = text("CleanMate", 32, white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = text(
                "Clean your phone. Free up space.",
                17, light);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(24));

        main.addView(title);
        main.addView(subtitle);

        TextView section = text(
                "PHONE CLEANER",
                14,
                Color.rgb(120, 200, 140));
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.setPadding(0, dp(8), 0, dp(8));
        main.addView(section);

        scanButton = menuButton("🔍  Scan Phone");
        Button duplicates = menuButton("📸  Duplicate Photos");
        Button similar = menuButton("🖼️  Similar Photos");
        Button oldPhotos = menuButton("📅  Old Photos");
        Button largeFiles = menuButton("📦  Large Files");
        Button screenshots = menuButton("📱  Screenshots");

        main.addView(scanButton);
        main.addView(duplicates);
        main.addView(similar);
        main.addView(oldPhotos);
        main.addView(largeFiles);
        main.addView(screenshots);

        status = text("Ready to scan your phone", 15, light);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(24), 0, dp(12));
        main.addView(status);

        TextView resultTitle = text(
                "SCAN RESULTS", 16,
                Color.rgb(120, 200, 140));
        resultTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        resultTitle.setPadding(0, dp(12), 0, dp(8));
        main.addView(resultTitle);

        results = text(
                "No scan performed yet.",
                15, white);
        results.setPadding(dp(14), dp(14), dp(14), dp(14));
        results.setBackgroundColor(Color.rgb(25, 35, 45));
        main.addView(results);

        scanButton.setOnClickListener(v -> startScan());

        duplicates.setOnClickListener(v ->
                status.setText("Run Scan Phone first."));

        similar.setOnClickListener(v ->
                status.setText("Run Scan Phone first."));

        oldPhotos.setOnClickListener(v ->
                status.setText("Run Scan Phone first."));

        largeFiles.setOnClickListener(v ->
                status.setText("Run Scan Phone first."));

        screenshots.setOnClickListener(v ->
                status.setText("Run Scan Phone first."));

        scrollView.addView(main);
        setContentView(scrollView);
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
                status.setText("Storage permission was not granted.");
                Toast.makeText(this,
                        "Allow media access to scan your phone.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void runScan() {
        scanButton.setEnabled(false);
        scanButton.setText("Scanning...");
        status.setText("Scanning accessible media files...");
        results.setText("Please wait while CleanMate scans your phone.");

        executor.execute(() -> {
            try {
                ScanData data = new ScanData();

                if (Build.VERSION.SDK_INT < 33) {
                    scanCollection(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            data);
                    scanCollection(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            data);
                    scanCollection(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            data);
                } else {
                    boolean selected = Build.VERSION.SDK_INT >= 34 &&
                            hasPermission(
                                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);

                    if (hasPermission(
                            Manifest.permission.READ_MEDIA_IMAGES) || selected) {
                        scanCollection(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                data);
                    }

                    if (hasPermission(
                            Manifest.permission.READ_MEDIA_VIDEO) || selected) {
                        scanCollection(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                data);
                    }

                    if (hasPermission(
                            Manifest.permission.READ_MEDIA_AUDIO)) {
                        scanCollection(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                data);
                    }
                }

                Collections.sort(data.largestFiles,
                        (a, b) -> Long.compare(b.size, a.size));

                handler.post(() -> showResults(data));

            } catch (Exception e) {
                handler.post(() -> {
                    status.setText("Scan could not be completed.");
                    results.setText(
                            "Please check media permissions and try again.");
                    scanButton.setEnabled(true);
                    scanButton.setText("🔍  Scan Phone");
                });
            }
        });
    }

    private void scanCollection(Uri uri, ScanData data) {
        String[] projection = {
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE
        };

        try (Cursor cursor = getContentResolver().query(
                uri,
                projection,
                null,
                null,
                null)) {

            if (cursor == null) return;

            int nameColumn = cursor.getColumnIndex(
                    MediaStore.MediaColumns.DISPLAY_NAME);
            int sizeColumn = cursor.getColumnIndex(
                    MediaStore.MediaColumns.SIZE);

            while (cursor.moveToNext()) {
                String name = nameColumn >= 0
                        ? cursor.getString(nameColumn)
                        : "Unknown file";

                long size = sizeColumn >= 0
                        ? cursor.getLong(sizeColumn)
                        : 0L;

                if (size < 0) size = 0;

                data.fileCount++;
                data.totalBytes += size;

                if (size >= LARGE_FILE_SIZE) {
                    data.largeFileCount++;
                }

                if (data.largestFiles.size() < 5 ||
                        size > data.largestFiles.get(4).size) {

                    data.largestFiles.add(
                            new FileInfo(name, size));

                    Collections.sort(data.largestFiles,
                            (a, b) -> Long.compare(b.size, a.size));

                    if (data.largestFiles.size() > 5) {
                        data.largestFiles.remove(5);
                    }
                }
            }
        }
    }

    private void showResults(ScanData data) {
        StringBuilder output = new StringBuilder();

        output.append("Files scanned: ")
                .append(data.fileCount).append("\n\n");

        output.append("Storage used by scanned media: ")
                .append(formatSize(data.totalBytes))
                .append("\n\n");

        output.append("Files larger than 50 MB: ")
                .append(data.largeFileCount)
                .append("\n\n");

        output.append("FIVE LARGEST FILES\n");

        if (data.largestFiles.isEmpty()) {
            output.append("No accessible media files found.");
        } else {
            for (int i = 0; i < data.largestFiles.size(); i++) {
                FileInfo file = data.largestFiles.get(i);

                output.append("\n")
                        .append(i + 1).append(". ")
                        .append(file.name).append("\n")
                        .append("   ")
                        .append(formatSize(file.size))
                        .append("\n");
            }
        }

        output.append("\n\nScan complete.");
        output.append("\nOnly accessible media was scanned.");

        results.setText(output.toString());
        status.setText("Scan completed successfully.");
        scanButton.setEnabled(true);
        scanButton.setText("🔍  Scan Again");
    }

    private String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(
                    java.util.Locale.US, "%.2f GB",
                    bytes / (1024.0 * 1024.0 * 1024.0));
        }

        if (bytes >= 1024L * 1024L) {
            return String.format(
                    java.util.Locale.US, "%.2f MB",
                    bytes / (1024.0 * 1024.0));
        }

        if (bytes >= 1024L) {
            return String.format(
                    java.util.Locale.US, "%.2f KB",
                    bytes / 1024.0);
        }

        return bytes + " bytes";
    }

    private static class ScanData {
        int fileCount = 0;
        int largeFileCount = 0;
        long totalBytes = 0L;
        List<FileInfo> largestFiles = new ArrayList<>();
    }

    private static class FileInfo {
        String name;
        long size;

        FileInfo(String name, long size) {
            this.name = name;
            this.size = size;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
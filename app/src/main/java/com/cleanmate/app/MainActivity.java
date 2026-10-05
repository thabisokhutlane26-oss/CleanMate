package com.cleanmate.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
                17,
                light);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(24));

        main.addView(title);
        main.addView(subtitle);

        TextView section = text("PHONE CLEANER", 14, Color.rgb(120, 200, 140));
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.setPadding(0, dp(8), 0, dp(8));
        main.addView(section);

        Button scan = menuButton("🔍  Scan Phone");
        Button duplicates = menuButton("📸  Duplicate Photos");
        Button similar = menuButton("🖼️  Similar Photos");
        Button oldPhotos = menuButton("📅  Old Photos");
        Button largeFiles = menuButton("📦  Large Files");
        Button screenshots = menuButton("📱  Screenshots");

        main.addView(scan);
        main.addView(duplicates);
        main.addView(similar);
        main.addView(oldPhotos);
        main.addView(largeFiles);
        main.addView(screenshots);

        TextView status = text(
                "Ready to scan your phone",
                15,
                light);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(24), 0, dp(8));
        main.addView(status);

        scan.setOnClickListener(v ->
                status.setText("Scanner ready — we will build the scanner next."));

        duplicates.setOnClickListener(v ->
                status.setText("Duplicate photo scanner coming next."));

        similar.setOnClickListener(v ->
                status.setText("Similar photo scanner coming next."));

        oldPhotos.setOnClickListener(v ->
                status.setText("Old photo scanner coming next."));

        largeFiles.setOnClickListener(v ->
                status.setText("Large file scanner coming next."));

        screenshots.setOnClickListener(v ->
                status.setText("Screenshot scanner coming next."));

        scrollView.addView(main);
        setContentView(scrollView);
    }
}

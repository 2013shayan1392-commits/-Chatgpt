package org.shayan.skvalexcompanion;

import android.app.Activity;
import android.content.Intent;
import android.content.ContentResolver;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int REQ_AUDIO = 4101;
    private static final int REQ_FOLDER = 4102;

    private TextView selectedFile;
    private TextView selectedFolder;
    private TextView status;
    private Button playButton;
    private Button pauseButton;
    private Button stopButton;

    private Uri audioUri;
    private MediaPlayer player;
    private boolean preparing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        String savedFolder = getPreferences(MODE_PRIVATE).getString("folder_uri", null);
        if (savedFolder != null) {
            selectedFolder.setText("پوشهٔ انتخاب‌شده: " + savedFolder);
        }
        String savedAudio = getPreferences(MODE_PRIVATE).getString("audio_uri", null);
        if (savedAudio != null) {
            audioUri = Uri.parse(savedAudio);
            selectedFile.setText("فایل قبلی: " + displayName(audioUri));
        }
        updateControls(false);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setGravity(Gravity.TOP);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("مدیریت فایل و پخش محلی");
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());

        TextView description = new TextView(this);
        description.setText("این ابزار فقط فایل صوتی‌ای را پخش می‌کند که خودت انتخاب کرده‌ای. قابلیت ضبط تماس یا دسترسی به صدای تماس در آن وجود ندارد.");
        description.setTextSize(15);
        description.setPadding(0, dp(12), 0, dp(16));
        root.addView(description, params());

        Button chooseAudio = button("انتخاب فایل صوتی");
        chooseAudio.setOnClickListener(v -> chooseAudio());
        root.addView(chooseAudio, params());

        selectedFile = new TextView(this);
        selectedFile.setText("فایلی انتخاب نشده است");
        selectedFile.setTextIsSelectable(true);
        selectedFile.setPadding(0, dp(10), 0, dp(12));
        root.addView(selectedFile, params());

        Button chooseFolder = button("انتخاب پوشه (دسترسی محدود و مجاز اندروید)");
        chooseFolder.setOnClickListener(v -> chooseFolder());
        root.addView(chooseFolder, params());

        selectedFolder = new TextView(this);
        selectedFolder.setText("پوشه‌ای انتخاب نشده است");
        selectedFolder.setTextIsSelectable(true);
        selectedFolder.setPadding(0, dp(10), 0, dp(12));
        root.addView(selectedFolder, params());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        playButton = button("پخش");
        pauseButton = button("مکث");
        stopButton = button("توقف");
        controls.addView(playButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        controls.addView(pauseButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        controls.addView(stopButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(controls, params());

        playButton.setOnClickListener(v -> startPlayback());
        pauseButton.setOnClickListener(v -> pausePlayback());
        stopButton.setOnClickListener(v -> stopPlayback());

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(18), 0, 0);
        root.addView(status, params());
        setContentView(scroll);
    }

    private void chooseAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_AUDIO);
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int grantFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            getContentResolver().takePersistableUriPermission(uri, grantFlags);
        } catch (SecurityException | IllegalArgumentException ex) {
            // Some document providers return a temporary grant only; current-session access can still work.
        }

        if (requestCode == REQ_AUDIO) {
            stopPlayback();
            audioUri = uri;
            getPreferences(MODE_PRIVATE).edit().putString("audio_uri", uri.toString()).apply();
            selectedFile.setText("فایل انتخاب‌شده: " + displayName(uri) + "\n" + uri);
            status.setText("فایل انتخاب شد. برای شروع، «پخش» را بزن.");
            updateControls(false);
        } else if (requestCode == REQ_FOLDER) {
            getPreferences(MODE_PRIVATE).edit().putString("folder_uri", uri.toString()).apply();
            selectedFolder.setText("پوشهٔ انتخاب‌شده: " + uri + "\nمجوز دسترسی پایدار در صورت پشتیبانی ارائه‌دهنده ذخیره شد.");
        }
    }

    private void startPlayback() {
        if (audioUri == null) {
            status.setText("ابتدا یک فایل صوتی انتخاب کن.");
            return;
        }
        if (player != null && !preparing) {
            try {
                if (!player.isPlaying()) {
                    player.start();
                    status.setText("در حال پخش");
                    updateControls(true);
                }
                return;
            } catch (IllegalStateException ignored) {
                releasePlayer();
            }
        }

        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, audioUri);
            preparing = true;
            updateControls(false);
            status.setText("در حال آماده‌سازی فایل…");
            player.setOnPreparedListener(mp -> {
                preparing = false;
                mp.start();
                status.setText("در حال پخش: " + displayName(audioUri));
                updateControls(true);
            });
            player.setOnCompletionListener(mp -> {
                status.setText("پخش به پایان رسید.");
                updateControls(false);
            });
            player.setOnErrorListener((mp, what, extra) -> {
                preparing = false;
                status.setText("پخش این فایل ناموفق بود یا قالب آن پشتیبانی نمی‌شود.");
                releasePlayer();
                updateControls(false);
                return true;
            });
            player.prepareAsync();
        } catch (Exception ex) {
            preparing = false;
            releasePlayer();
            status.setText("فایل قابل دسترسی نیست. ممکن است مجوز آن لغو شده یا فایل حذف شده باشد.");
            updateControls(false);
        }
    }

    private void pausePlayback() {
        if (player == null || preparing) return;
        try {
            if (player.isPlaying()) {
                player.pause();
                status.setText("پخش متوقف موقت شد.");
                updateControls(false);
            }
        } catch (IllegalStateException ex) {
            status.setText("وضعیت پخش در دسترس نیست.");
        }
    }

    private void stopPlayback() {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (IllegalStateException ignored) {
            }
            releasePlayer();
        }
        preparing = false;
        updateControls(false);
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.reset(); } catch (IllegalStateException ignored) {}
            try { player.release(); } catch (IllegalStateException ignored) {}
            player = null;
        }
        preparing = false;
    }

    private void updateControls(boolean playing) {
        if (playButton == null) return;
        playButton.setEnabled(audioUri != null && !playing && !preparing);
        pauseButton.setEnabled(playing);
        stopButton.setEnabled(player != null || preparing);
    }

    private String displayName(Uri uri) {
        if (uri == null) return "نامشخص";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return uri.getLastPathSegment() == null ? uri.toString() : uri.getLastPathSegment();
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}

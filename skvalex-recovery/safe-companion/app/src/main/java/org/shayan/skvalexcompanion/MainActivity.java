package org.shayan.skvalexcompanion;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQ_AUDIO = 4101;
    private static final int REQ_FOLDER = 4102;
    private static final int REQ_PHONE_STATE = 4103;
    private static final int REQ_NOTIFICATIONS = 4104;
    private static final String PREFS = "safe_companion";
    private static final String STATE_ACTION = "org.shayan.skvalexcompanion.PLAYBACK_STATE";

    private LinearLayout root;
    private TextView selectedFile;
    private TextView selectedFolder;
    private TextView status;
    private TextView simInfo;
    private Button playButton;
    private Button pauseButton;
    private Button stopButton;
    private Uri audioUri;
    private Uri folderUri;
    private boolean receiverRegistered;

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!STATE_ACTION.equals(intent.getAction())) return;
            String message = intent.getStringExtra("message");
            if (status != null && message != null) status.setText(message);
            boolean playing = intent.getBooleanExtra("playing", false);
            boolean active = intent.getBooleanExtra("active", false);
            updateControls(playing, active);
        }
    };

    @Override protected void onCreate(Bundle state) {
        String mode = getPreferences(MODE_PRIVATE).getString("theme", "system");
        if ("dark".equals(mode)) setTheme(android.R.style.Theme_Material_NoActionBar);
        else setTheme(android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        audioUri = savedUri("audio_uri");
        folderUri = savedUri("folder_uri");
        buildUi();
        refreshSavedLabels();
        updateControls(false, false);
        requestNotificationPermissionIfNeeded();
    }

    private Uri savedUri(String key) {
        String value = getPreferences(MODE_PRIVATE).getString(key, null);
        return value == null ? null : Uri.parse(value);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setGravity(Gravity.TOP);
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean dark = "dark".equals(getPreferences(MODE_PRIVATE).getString("theme", "system"))
                || ("system".equals(getPreferences(MODE_PRIVATE).getString("theme", "system")) && night == Configuration.UI_MODE_NIGHT_YES);
        root.setBackgroundColor(dark ? 0xFF171717 : 0xFFF7F7F7);
        scroll.addView(root);
        TextView title = label("مدیریت فایل و پخش محلی", 23, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());
        TextView description = label("فقط فایل‌های صوتی انتخاب‌شده توسط کاربر پخش می‌شوند. این برنامه ضبط تماس یا دریافت صدای مکالمه انجام نمی‌دهد.", 14, false);
        description.setPadding(0, dp(10), 0, dp(14));
        root.addView(description, params());

        addButton("انتخاب فایل صوتی", v -> chooseAudio());
        selectedFile = label("فایلی انتخاب نشده است", 13, false);
        selectedFile.setTextIsSelectable(true);
        root.addView(selectedFile, paddedParams());

        addButton("انتخاب پوشه", v -> chooseFolder());
        addButton("مرور فایل‌های داخل پوشه", v -> showFolderBrowser());
        selectedFolder = label("پوشه‌ای انتخاب نشده است", 13, false);
        selectedFolder.setTextIsSelectable(true);
        root.addView(selectedFolder, paddedParams());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        playButton = button("پخش");
        pauseButton = button("مکث");
        stopButton = button("توقف");
        controls.addView(playButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        controls.addView(pauseButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        controls.addView(stopButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(controls, params());
        playButton.setOnClickListener(v -> sendPlayback(PlaybackService.ACTION_PLAY, audioUri));
        pauseButton.setOnClickListener(v -> sendPlayback(PlaybackService.ACTION_PAUSE, null));
        stopButton.setOnClickListener(v -> sendPlayback(PlaybackService.ACTION_STOP, null));

        status = label("آماده", 14, false);
        status.setPadding(0, dp(14), 0, dp(8));
        root.addView(status, params());
        addButton("اطلاعات سیم‌کارت (فقط نمایش)", v -> showSimInformation());
        simInfo = label("اطلاعات سیم‌کارت هنوز خوانده نشده است.", 13, false);
        root.addView(simInfo, paddedParams());
        addButton("تنظیمات", v -> showSettings());
        addButton("درباره و سازگاری", v -> showAbout());
        setContentView(scroll);
    }

    private void refreshSavedLabels() {
        if (selectedFile != null) selectedFile.setText(audioUri == null ? "فایلی انتخاب نشده است" : "فایل ذخیره‌شده: " + displayName(audioUri));
        if (selectedFolder != null) selectedFolder.setText(folderUri == null ? "پوشه‌ای انتخاب نشده است" : "پوشهٔ ذخیره‌شده: " + folderUri);
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

    @SuppressLint("WrongConstant")
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int grants = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContentResolver().takePersistableUriPermission(uri, grants); }
        catch (SecurityException | IllegalArgumentException ignored) { }
        if (requestCode == REQ_AUDIO) {
            audioUri = uri;
            getPreferences(MODE_PRIVATE).edit().putString("audio_uri", uri.toString()).apply();
            refreshSavedLabels();
            status.setText("فایل انتخاب شد. برای پخش، دکمهٔ پخش را بزن.");
            updateControls(false, false);
        } else if (requestCode == REQ_FOLDER) {
            folderUri = uri;
            getPreferences(MODE_PRIVATE).edit().putString("folder_uri", uri.toString()).apply();
            refreshSavedLabels();
            status.setText("پوشه ذخیره شد. اکنون می‌توانی محتویات آن را مرور کنی.");
        }
    }

    private void showFolderBrowser() {
        if (folderUri == null) {
            status.setText("ابتدا یک پوشه انتخاب کن.");
            return;
        }
        final ArrayList<String> stack = new ArrayList<>();
        try { stack.add(DocumentsContract.getTreeDocumentId(folderUri)); }
        catch (Exception ex) { status.setText("شناسهٔ پوشه از ارائه‌دهنده دریافت نشد."); return; }
        showFolderLevel(stack);
    }

    private void showFolderLevel(ArrayList<String> stack) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        panel.addView(label("فقط پوشه‌ها و فایل‌های صوتی نمایش داده می‌شوند.", 13, false), params());
        Button up = button("بازگشت به پوشهٔ قبلی");
        up.setEnabled(stack.size() > 1);
        panel.addView(up, params());
        ScrollView listScroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(list);
        panel.addView(listScroll, new LinearLayout.LayoutParams(-1, dp(360)));
        final AlertDialog[] dialogRef = new AlertDialog[1];
        up.setOnClickListener(v -> {
            if (stack.size() > 1) {
                stack.remove(stack.size() - 1);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                showFolderLevel(stack);
            }
        });
        String currentId = stack.get(stack.size() - 1);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, currentId);
        String[] projection = new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };
        int count = 0;
        try (Cursor cursor = getContentResolver().query(childrenUri, projection, null, null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
                int sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
                while (cursor.moveToNext()) {
                    String id = cursor.getString(idCol);
                    String name = cursor.getString(nameCol);
                    String mime = cursor.getString(mimeCol);
                    long size = sizeCol >= 0 && !cursor.isNull(sizeCol) ? cursor.getLong(sizeCol) : -1;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        Button folder = button("📁  " + name);
                        folder.setOnClickListener(v -> {
                            stack.add(id);
                            if (dialogRef[0] != null) dialogRef[0].dismiss();
                            showFolderLevel(stack);
                        });
                        list.addView(folder, params());
                        count++;
                    } else if (mime != null && mime.startsWith("audio/")) {
                        Button file = button("♫  " + name + (size >= 0 ? "  ·  " + formatBytes(size) : ""));
                        file.setOnClickListener(v -> {
                            audioUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id);
                            getPreferences(MODE_PRIVATE).edit().putString("audio_uri", audioUri.toString()).apply();
                            refreshSavedLabels();
                            status.setText("فایل از پوشه انتخاب شد: " + name);
                            updateControls(false, false);
                            if (dialogRef[0] != null) dialogRef[0].dismiss();
                        });
                        list.addView(file, params());
                        count++;
                    }
                }
            }
        } catch (Exception ex) {
            list.addView(label("این ارائه‌دهنده امکان فهرست‌کردن محتویات پوشه را نداد. فایل را جداگانه انتخاب کن.", 13, false), params());
        }
        if (count == 0 && list.getChildCount() == 0) list.addView(label("فایل صوتی یا زیرپوشه‌ای در این مسیر پیدا نشد.", 13, false), params());
        dialogRef[0] = new AlertDialog.Builder(this).setTitle("مرور پوشه").setView(panel)
                .setNegativeButton("بستن", null).create();
        dialogRef[0].show();
    }

    private String formatBytes(long value) {
        if (value < 1024) return value + " B";
        if (value < 1024L * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", value / 1024.0);
        return String.format(java.util.Locale.getDefault(), "%.1f MB", value / (1024.0 * 1024.0));
    }

    private void sendPlayback(String action, Uri uri) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action);
        if (uri != null) intent.putExtra(PlaybackService.EXTRA_URI, uri.toString());
        try {
            if (Build.VERSION.SDK_INT >= 26 && PlaybackService.ACTION_PLAY.equals(action)) startForegroundService(intent);
            else startService(intent);
        } catch (Exception ex) {
            status.setText("شروع پخش پس‌زمینه ممکن نشد: " + ex.getClass().getSimpleName());
        }
    }

    private void updateControls(boolean playing, boolean active) {
        if (playButton == null) return;
        playButton.setEnabled(audioUri != null && !active);
        pauseButton.setText(playing ? "مکث" : "ادامه");
        pauseButton.setEnabled(active);
        stopButton.setEnabled(active);
    }

    private void showSimInformation() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQ_PHONE_STATE);
            simInfo.setText("برای نمایش نام اپراتور و شمارهٔ شیار سیم‌کارت، مجوز اطلاعات تلفن لازم است. این برنامه تماس برقرار نمی‌کند.");
            return;
        }
        renderSimInformation();
    }

    private void renderSimInformation() {
        if (Build.VERSION.SDK_INT < 22) { simInfo.setText("اطلاعات سیم‌کارت در این نسخهٔ اندروید پشتیبانی نمی‌شود."); return; }
        try {
            SubscriptionManager manager = (SubscriptionManager) getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);
            List<SubscriptionInfo> items = manager == null ? null : manager.getActiveSubscriptionInfoList();
            if (items == null || items.isEmpty()) { simInfo.setText("سیم‌کارت فعالی از طریق API عمومی اندروید گزارش نشد."); return; }
            StringBuilder result = new StringBuilder();
            for (SubscriptionInfo item : items) {
                if (result.length() > 0) result.append("\n\n");
                result.append("شیار ").append(item.getSimSlotIndex() + 1)
                        .append("\nاپراتور: ").append(item.getCarrierName())
                        .append("\nنام نمایشی: ").append(item.getDisplayName())
                        .append("\nنوع: ").append(item.isEmbedded() ? "eSIM" : "سیم‌کارت فیزیکی");
            }
            simInfo.setText(result.toString());
        } catch (SecurityException ex) {
            simInfo.setText("سیستم اجازهٔ خواندن اطلاعات سیم‌کارت را نداد.");
        } catch (Exception ex) {
            simInfo.setText("اطلاعات سیم‌کارت در این دستگاه در دسترس نیست.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PHONE_STATE && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) renderSimInformation();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void showSettings() {
        String[] items = {
                "ظاهر: همگام با سیستم",
                "ظاهر: روشن",
                "ظاهر: تیره",
                "پاک کردن فایل انتخاب‌شده",
                "پاک کردن پوشهٔ ذخیره‌شده"
        };
        new AlertDialog.Builder(this).setTitle("تنظیمات").setItems(items, (dialog, which) -> {
            if (which <= 2) {
                String value = which == 1 ? "light" : (which == 2 ? "dark" : "system");
                getPreferences(MODE_PRIVATE).edit().putString("theme", value).apply();
                recreate();
            } else if (which == 3) {
                audioUri = null;
                getPreferences(MODE_PRIVATE).edit().remove("audio_uri").apply();
                refreshSavedLabels();
                updateControls(false, false);
                status.setText("فایل ذخیره‌شده پاک شد.");
            } else {
                folderUri = null;
                getPreferences(MODE_PRIVATE).edit().remove("folder_uri").apply();
                refreshSavedLabels();
                status.setText("پوشهٔ ذخیره‌شده پاک شد.");
            }
        }).setNegativeButton("بستن", null).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("دربارهٔ برنامه")
                .setMessage("Safe Companion یک ابزار مستقل و غیرضبطی است. پخش فایل‌های صوتی انتخاب‌شده، مرور SAF، نمایش اطلاعات پایهٔ سیم‌کارت و اعلان پخش را ارائه می‌کند. حداقل نسخه: Android 10 (API 29). طراحی برای Android 10 تا 16؛ نتیجهٔ نهایی به سازنده و ارائه‌دهندهٔ فایل بستگی دارد.")
                .setPositiveButton("باشه", null).show();
    }

    private void addButton(String text, View.OnClickListener listener) {
        Button b = button(text);
        b.setOnClickListener(listener);
        root.addView(b, params());
    }

    private TextView label(String text, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        String themeMode = getPreferences(MODE_PRIVATE).getString("theme", "system");
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean darkMode = "dark".equals(themeMode)
                || ("system".equals(themeMode) && nightMode == Configuration.UI_MODE_NIGHT_YES);
        t.setTextColor(darkMode ? 0xFFF1F3F4 : 0xFF202124);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams paddedParams() {
        LinearLayout.LayoutParams p = params();
        p.topMargin = dp(8);
        p.bottomMargin = dp(12);
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String displayName(Uri uri) {
        if (uri == null) return "نامشخص";
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? uri.toString() : uri.getLastPathSegment();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(STATE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(playbackReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(playbackReceiver, filter);
        receiverRegistered = true;
        sendPlayback(PlaybackService.ACTION_QUERY_STATE, null);
    }

    @Override protected void onStop() {
        if (receiverRegistered) {
            try { unregisterReceiver(playbackReceiver); } catch (IllegalArgumentException ignored) { }
            receiverRegistered = false;
        }
        super.onStop();
    }
}

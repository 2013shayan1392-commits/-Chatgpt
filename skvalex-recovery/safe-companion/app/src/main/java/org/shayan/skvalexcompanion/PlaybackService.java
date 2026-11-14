package org.shayan.skvalexcompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

public final class PlaybackService extends Service {
    public static final String ACTION_PLAY = "org.shayan.skvalexcompanion.action.PLAY";
    public static final String ACTION_PAUSE = "org.shayan.skvalexcompanion.action.PAUSE";
    public static final String ACTION_STOP = "org.shayan.skvalexcompanion.action.STOP";
    public static final String EXTRA_URI = "audio_uri";
    private static final String CHANNEL_ID = "local_audio_playback";
    private static final int NOTIFICATION_ID = 701;

    private MediaPlayer player;
    private boolean preparing;
    private boolean playing;
    private String currentUri;
    private String currentName = "فایل صوتی";

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            String value = intent.getStringExtra(EXTRA_URI);
            if (value == null) {
                publishState("آدرس فایل معتبر نیست.", false, false);
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            currentUri = value;
            currentName = resolveName(Uri.parse(value));
            startAsForeground();
            prepare(Uri.parse(value));
        } else if (ACTION_PAUSE.equals(action)) {
            pause();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        Notification notification = buildNotification("در حال آماده‌سازی…", false);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void prepare(Uri uri) {
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, uri);
            preparing = true;
            playing = false;
            publishState("در حال آماده‌سازی: " + currentName, false, true);
            updateNotification("در حال آماده‌سازی…", false);
            player.setOnPreparedListener(mp -> {
                preparing = false;
                try {
                    mp.start();
                    playing = true;
                    publishState("در حال پخش: " + currentName, true, true);
                    updateNotification("در حال پخش: " + currentName, true);
                } catch (IllegalStateException ex) {
                    fail("پخش فایل شروع نشد.");
                }
            });
            player.setOnCompletionListener(mp -> {
                playing = false;
                preparing = false;
                publishState("پخش به پایان رسید.", false, false);
                stopForeground(true);
                releasePlayer();
                stopSelf();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                fail("پخش فایل ناموفق بود یا قالب آن پشتیبانی نمی‌شود.");
                return true;
            });
            player.prepareAsync();
        } catch (Exception ex) {
            fail("فایل قابل دسترسی نیست؛ مجوز یا فایل را بررسی کن.");
        }
    }

    private void pause() {
        if (player == null || preparing) return;
        try {
            if (player.isPlaying()) {
                player.pause();
                playing = false;
                publishState("پخش متوقف موقت شد.", false, true);
                updateNotification("مکث: " + currentName, false);
            } else {
                player.start();
                playing = true;
                publishState("در حال پخش: " + currentName, true, true);
                updateNotification("در حال پخش: " + currentName, true);
            }
        } catch (IllegalStateException ex) {
            fail("وضعیت پخش دیگر معتبر نیست.");
        }
    }

    private void stopPlayback() {
        releasePlayer();
        playing = false;
        preparing = false;
        publishState("پخش متوقف شد.", false, false);
        stopForeground(true);
        stopSelf();
    }

    private void fail(String message) {
        releasePlayer();
        playing = false;
        preparing = false;
        publishState(message, false, false);
        stopForeground(true);
        stopSelf();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.reset(); } catch (IllegalStateException ignored) { }
            try { player.release(); } catch (IllegalStateException ignored) { }
            player = null;
        }
        preparing = false;
    }

    private void publishState(String message, boolean isPlaying, boolean active) {
        Intent state = new Intent("org.shayan.skvalexcompanion.PLAYBACK_STATE");
        state.setPackage(getPackageName());
        state.putExtra("message", message);
        state.putExtra("playing", isPlaying);
        state.putExtra("active", active);
        sendBroadcast(state);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "پخش فایل صوتی", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("کنترل پخش فایل‌های صوتی انتخاب‌شده");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text, boolean isPlaying) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 700,
                open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String action = isPlaying ? ACTION_PAUSE : ACTION_PAUSE;
        String actionLabel = isPlaying ? "مکث" : "ادامه";
        PendingIntent toggle = PendingIntent.getService(this, 701,
                new Intent(this, PlaybackService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 702,
                new Intent(this, PlaybackService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play)
                .setContentTitle(currentName)
                .setContentText(text)
                .setContentIntent(content)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOngoing(true)
                .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        actionLabel, toggle)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "توقف", stop);
        return builder.build();
    }

    private void updateNotification(String text, boolean isPlaying) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text, isPlaying));
    }

    private String resolveName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return "فایل صوتی";
    }

    @Override public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

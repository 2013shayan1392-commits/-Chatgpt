package org.shayan.skvalexcompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;

public final class PlaybackService extends Service {
    public static final String ACTION_PLAY = "org.shayan.skvalexcompanion.action.PLAY";
    public static final String ACTION_PAUSE = "org.shayan.skvalexcompanion.action.PAUSE";
    public static final String ACTION_STOP = "org.shayan.skvalexcompanion.action.STOP";
    public static final String EXTRA_URI = "audio_uri";
    private static final String CHANNEL_ID = "local_audio_playback";
    private static final int NOTIFICATION_ID = 701;
    private static final String STATE_ACTION = "org.shayan.skvalexcompanion.PLAYBACK_STATE";

    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private AudioAttributes audioAttributes;
    private MediaSession mediaSession;
    private boolean preparing;
    private boolean playing;
    private boolean hasAudioFocus;
    private boolean resumeAfterFocusGain;
    private boolean focusDucked;
    private boolean noisyReceiverRegistered;
    private String currentName = "فایل صوتی";

    private final AudioManager.OnAudioFocusChangeListener focusListener = this::onAudioFocusChanged;

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()) && player != null) {
                resumeAfterFocusGain = false;
                pausePlayback("پخش به‌دلیل جداشدن هدفون متوقف شد.");
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        createMediaSession();
        createNotificationChannel();
        registerNoisyReceiver();
    }

    private void createMediaSession() {
        mediaSession = new MediaSession(this, "SafeCompanionPlayback");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resumePlayback(); }
            @Override public void onPause() { pausePlayback("پخش متوقف موقت شد."); }
            @Override public void onStop() { stopPlayback(); }
            @Override public void onSeekTo(long position) {
                if (player == null || preparing) return;
                try {
                    player.seekTo((int) Math.max(0, Math.min(position, player.getDuration())));
                    updateMediaSessionState();
                } catch (IllegalStateException ignored) { }
            }
            @Override public void onMediaButtonEvent(Intent mediaButtonIntent) {
                super.onMediaButtonEvent(mediaButtonIntent);
            }
        });
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
            Uri uri = Uri.parse(value);
            currentName = resolveName(uri);
            try {
                startAsForeground();
                prepare(uri);
            } catch (RuntimeException ex) {
                fail("سیستم اجازهٔ شروع پخش پس‌زمینه را نداد.");
            }
        } else if (ACTION_PAUSE.equals(action)) {
            togglePlayback();
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
            player.setAudioAttributes(audioAttributes);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(this, uri);
            preparing = true;
            playing = false;
            publishState("در حال آماده‌سازی: " + currentName, false, true);
            updateNotification("در حال آماده‌سازی…", false);
            player.setOnPreparedListener(mp -> {
                preparing = false;
                updateMediaMetadata();
                resumePlayback();
            });
            player.setOnCompletionListener(mp -> {
                playing = false;
                preparing = false;
                publishState("پخش به پایان رسید.", false, false);
                updateMediaSessionState();
                if (mediaSession != null) mediaSession.setActive(false);
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
            fail("فایل قابل دسترسی نیست؛ مجوز یا خود فایل را بررسی کن.");
        }
    }

    private void resumePlayback() {
        if (player == null || preparing) return;
        if (!requestAudioFocus()) {
            publishState("صدای سیستم فعلاً در اختیار برنامهٔ دیگری است.", false, true);
            updateNotification("در انتظار دسترسی صوتی…", false);
            return;
        }
        try {
            if (!player.isPlaying()) player.start();
            player.setVolume(1.0f, 1.0f);
            playing = true;
            focusDucked = false;
            resumeAfterFocusGain = false;
            if (mediaSession != null) mediaSession.setActive(true);
            publishState("در حال پخش: " + currentName, true, true);
            updateNotification("در حال پخش: " + currentName, true);
        } catch (IllegalStateException ex) {
            fail("پخش فایل شروع نشد.");
        }
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return false;
        final int result;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(audioAttributes)
                        .setOnAudioFocusChangeListener(focusListener)
                        .setWillPauseWhenDucked(false)
                        .build();
            }
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasAudioFocus;
    }

    private void abandonAudioFocus() {
        if (audioManager == null || !hasAudioFocus) return;
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            audioManager.abandonAudioFocus(focusListener);
        }
        hasAudioFocus = false;
        focusDucked = false;
        resumeAfterFocusGain = false;
    }

    private void onAudioFocusChanged(int change) {
        if (player == null) return;
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            resumeAfterFocusGain = false;
            pausePlayback("پخش به‌دلیل استفادهٔ برنامه‌ای دیگر از صدا متوقف شد.");
            abandonAudioFocus();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            resumeAfterFocusGain = playing;
            pausePlayback("پخش موقتاً برای صدای دیگری مکث شد.");
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (playing) {
                try {
                    player.setVolume(0.20f, 0.20f);
                    focusDucked = true;
                } catch (IllegalStateException ignored) { }
            }
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (focusDucked && player != null) {
                try { player.setVolume(1.0f, 1.0f); } catch (IllegalStateException ignored) { }
                focusDucked = false;
            }
            if (resumeAfterFocusGain) {
                resumeAfterFocusGain = false;
                resumePlayback();
            }
        }
    }

    private void togglePlayback() {
        if (player == null || preparing) return;
        if (playing) {
            resumeAfterFocusGain = false;
            pausePlayback("پخش متوقف موقت شد.");
        } else {
            resumePlayback();
        }
    }

    private void pausePlayback(String message) {
        if (player == null || preparing) return;
        try {
            if (player.isPlaying()) player.pause();
            playing = false;
            publishState(message, false, true);
            updateNotification("مکث: " + currentName, false);
        } catch (IllegalStateException ex) {
            fail("توقف موقت پخش ممکن نشد.");
        }
    }

    private void stopPlayback() {
        releasePlayer();
        playing = false;
        preparing = false;
        publishState("پخش متوقف شد.", false, false);
        updateMediaSessionState();
        if (mediaSession != null) mediaSession.setActive(false);
        stopForeground(true);
        stopSelf();
    }

    private void fail(String message) {
        releasePlayer();
        playing = false;
        preparing = false;
        publishState(message, false, false);
        updateMediaSessionState();
        if (mediaSession != null) mediaSession.setActive(false);
        stopForeground(true);
        stopSelf();
    }

    private void releasePlayer() {
        abandonAudioFocus();
        if (player != null) {
            try { player.reset(); } catch (IllegalStateException ignored) { }
            try { player.release(); } catch (IllegalStateException ignored) { }
            player = null;
        }
        preparing = false;
        playing = false;
    }

    private void updateMediaMetadata() {
        if (mediaSession == null || player == null) return;
        try {
            android.media.MediaMetadata metadata = new android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, currentName)
                    .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, player.getDuration())
                    .build();
            mediaSession.setMetadata(metadata);
        } catch (IllegalStateException ignored) { }
    }

    private void updateMediaSessionState() {
        if (mediaSession == null) return;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_SEEK_TO;
        int state = PlaybackState.STATE_STOPPED;
        long position = 0;
        float speed = 0f;
        if (player != null && !preparing) {
            try {
                position = Math.max(0, player.getCurrentPosition());
                if (playing) {
                    state = PlaybackState.STATE_PLAYING;
                    speed = 1f;
                } else {
                    state = PlaybackState.STATE_PAUSED;
                }
            } catch (IllegalStateException ignored) { }
        } else if (preparing) {
            state = PlaybackState.STATE_BUFFERING;
        }
        PlaybackState playbackState = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, position, speed)
                .build();
        mediaSession.setPlaybackState(playbackState);
    }

    private void registerNoisyReceiver() {
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(noisyReceiver, filter);
        }
        noisyReceiverRegistered = true;
    }

    private void publishState(String message, boolean isPlaying, boolean active) {
        Intent state = new Intent(STATE_ACTION);
        state.setPackage(getPackageName());
        state.putExtra("message", message);
        state.putExtra("playing", isPlaying);
        state.putExtra("active", active);
        sendBroadcast(state);
        updateMediaSessionState();
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
        PendingIntent toggle = PendingIntent.getService(this, 701,
                new Intent(this, PlaybackService.class).setAction(ACTION_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 702,
                new Intent(this, PlaybackService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1);
        builder.setSmallIcon(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play)
                .setContentTitle(currentName)
                .setContentText(text)
                .setContentIntent(content)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setStyle(style)
                .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying ? "مکث" : "ادامه", toggle)
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
        if (noisyReceiverRegistered) {
            try { unregisterReceiver(noisyReceiver); } catch (IllegalArgumentException ignored) { }
            noisyReceiverRegistered = false;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

package com.fongmi.android.tv.player;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import com.github.catvod.crawler.SpiderDebug;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.danmaku.DanmakuConfig;
import androidx.media3.ui.danmaku.DanmakuController;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.player.danmaku.*;
import android.os.SystemClock;
import java.util.ArrayList;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerManager implements ParseCallback {

    private static final String TAG_DANMAKU = "DanmakuED";   // 弹幕全链路诊断日志

    private final Runnable runnable;
    private final Callback callback;
    private DanmakuController danmakuController;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;
    private String currentDanmakuUrl;
    private String currentDanmakuKey;
    private String loadingDanmakuKey;
    private long danmakuLoadStartedAtMs;
    private boolean danmakuLoadInProgress;
    private final Runnable liveDanmakuMetricsRunnable;
    private final LiveDanmakuBatcher liveDanmakuBatcher;
    private final LiveDanmakuBuffer liveDanmakuBuffer;
    private final LiveDanmakuMetrics liveDanmakuMetrics;
    private LiveDanmakuWebSocketSession liveDanmakuSession;
    private volatile long liveDanmakuGeneration;
    private volatile boolean liveDanmakuPlaybackActive;
    private boolean danmakuForeground = true;
    private static final long LIVE_DANMAKU_METRICS_INTERVAL_MS = 60_000L;
    private static final long DANMAKU_FORCE_RELOAD_DEBOUNCE_MS = 10000;

    private boolean initTrack;
    private int retry;

    public PlayerManager(Callback callback) {
        this.runnable = () -> callback.onError(ResUtil.getString(R.string.error_play_timeout));
        this.liveDanmakuMetricsRunnable = () -> logLiveDanmakuMetrics("periodic", true);
        this.liveDanmakuBuffer = new LiveDanmakuBuffer();
        this.liveDanmakuMetrics = new LiveDanmakuMetrics();
        this.liveDanmakuBatcher = new LiveDanmakuBatcher(liveDanmakuBuffer, this::onLiveDanmakuBatch);
        this.engine = new ExoPlayerEngine(PlayerEngine.HARD, listener);
        this.player = engine.getPlayer();
        this.callback = callback;
    }

    public void release() {
        player.removeListener(listener);
        App.removeCallbacks(runnable);
        clearDanmaku("release_controller");
        releaseLiveDanmakuSession();
        liveDanmakuBatcher.release();
        App.removeCallbacks(liveDanmakuMetricsRunnable);
        if (engine == null) return;
        engine.release();
        engine = null;
        player = null;
    }

    public Player getPlayer() {
        return player;
    }

    public Tracks getCurrentTracks() {
        return engine.getCurrentTracks();
    }

    public List<MediaEdition> getCurrentMediaEditions() {
        return engine.getCurrentMediaEditions();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : null;
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return player.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return engine.isLive();
    }

    public boolean isVod() {
        return engine.isVod();
    }

    public boolean haveTrack(int type) {
        return engine.haveTrack(type);
    }

    public boolean haveTitle() {
        return engine.haveTitle();
    }

    public boolean haveDanmaku() {
        return getDanmakus() != null && getDanmakus().stream().anyMatch(Danmaku::isSelected);
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public String getSizeText() {
        return (getVideoWidth() == 0 && getVideoHeight() == 0) ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        return engine.getDecodeText();
    }

    public String getPositionTime(long delta) {
        long time = Math.max(0, Math.min(getPosition() + delta, Math.max(0, getDuration())));
        return Util.timeMs(time);
    }

    public long getDuration() {
        return player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (spec != null) spec.setSub(sub);
        setMediaItem();
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        setMediaItem();
    }

    public void setEdition(MediaEdition edition) {
        if (spec != null) spec.setUrl(spec.getUri().buildUpon().fragment("edition=" + edition.index).build().toString());
        setMediaItem();
        seekTo(0);
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void setMetadata(MediaMetadata data) {
        if (spec != null) spec.setMetadata(data);
        engine.setMetadata(data);
    }

    public void setDanmakuController(DanmakuController controller) {
        if (danmakuController == controller) {
            configureDanmakuController(controller);
            return;
        }
        if (danmakuController != null) {
            danmakuController.setListener(null);
            danmakuController.clearItems();
        }
        danmakuController = controller;
        if (danmakuController == null) return;
        configureDanmakuController(danmakuController);
        restoreDanmakuDataSource();
    }

    private void configureDanmakuController(DanmakuController controller) {
        controller.setOkHttpClient(OkHttp.player());
        controller.setConfig(DanmakuSetting.getConfig());
        controller.setEnabled(DanmakuSetting.isShow());
        Log.d(TAG_DANMAKU, "controller ready: enabled=" + DanmakuSetting.isShow() + " (isShow 默认已改为开，关过就是 false)");
        controller.setListener(new DanmakuController.Listener() {
            @Override
            public void onLoadCompleted(Uri uri, int count) {
                // 无条件打日志：以前只有开 SpiderDebug 才记，真机上永远是黑的，
                // 分不清「没发请求」「请求失败」还是「加载成功但没显示」
                Log.d(TAG_DANMAKU, "load completed count=" + count + " url=" + (uri == null ? "" : uri));
                logDanmakuLoad("completed", uri, count, null);
                finishDanmakuLoad(uri);
            }

            @Override
            public void onLoadError(Uri uri, IOException error) {
                Log.d(TAG_DANMAKU, "load error url=" + (uri == null ? "" : uri) + " err=" + (error == null ? "" : error.getMessage()));
                logDanmakuLoad("error", uri, -1, error);
                finishDanmakuLoad(uri);
            }
        });
    }

    private void restoreDanmakuDataSource() {
        if (danmakuController == null || TextUtils.isEmpty(currentDanmakuUrl)) return;
        if (!DanmakuUrlPolicy.classify(currentDanmakuUrl).isStatic()) return;
        loadingDanmakuKey = currentDanmakuKey;
        danmakuLoadStartedAtMs = SystemClock.elapsedRealtime();
        danmakuLoadInProgress = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "restore controller %s key=%s", DanmakuUrlPolicy.logSummary(currentDanmakuUrl), summarizeUrl(currentDanmakuKey));
        danmakuController.setDataSource(Uri.parse(currentDanmakuUrl));
    }

    public void setDanmakuConfig(DanmakuConfig config) {
        if (danmakuController != null) danmakuController.setConfig(config);
    }

    public void setDanmakuEnabled(boolean enabled) {
        if (danmakuController != null) danmakuController.setEnabled(enabled);
        if (!enabled) {
            stopLiveDanmakuSession("hidden");
        } else if (danmakuForeground && DanmakuUrlPolicy.classify(currentDanmakuUrl).isLive()) {
            connectLiveDanmakuSession(currentDanmakuUrl);
        }
    }

    public void setDanmakuForeground(boolean foreground) {
        if (danmakuForeground == foreground) return;
        danmakuForeground = foreground;
        if (!foreground) {
            stopLiveDanmakuSession("background");
            discardLiveDanmakuPending();
        } else if (DanmakuSetting.isShow() && DanmakuUrlPolicy.classify(currentDanmakuUrl).isLive()) {
            connectLiveDanmakuSession(currentDanmakuUrl);
        }
    }

    public void sendDanmaku(String text) {
        if (danmakuController != null) danmakuController.sendNow(text);
    }

    public String setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        return setSpeed(Math.min(getSpeed() + value, 5));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.max(getSpeed() - value, 0.25f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? PlayerSetting.getSpeed() : 1);
    }

    public void setTrack(List<Track> tracks) {
        if (!tracks.isEmpty()) engine.setTrack(tracks);
    }

    public void play() {
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        player.stop();
        stopParse();
    }

    public void clearMediaItems() {
        player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return engine.isRepeatOne();
    }

    public void setRepeatOne(boolean repeat) {
        engine.setRepeatOne(repeat);
    }

    public void seekTo(long time) {
        player.seekTo(time);
    }

    public long getTextOffsetMs() {
        return 0;
    }

    public void setTextOffsetMs(long offsetMs) {
    }

    public long getAudioOffsetMs() {
        return 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
    }

    public void reset() {
        App.removeCallbacks(runnable);
        retry = 0;
    }

    public void clear() {
        spec = null;
    }

    public void resetTrack() {
        engine.resetTrack();
    }

    public void toggleDecode() {
        engine.setDecode(engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD);
        rebuildPlayer();
        setMediaItem();
    }

    private void rebuildPlayer() {
        player = engine.rebuild(listener);
        callback.onPlayerRebuild(player);
    }

    public void browse(PlaySpec spec) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY);
    }

    public void start(PlaySpec spec, long timeout) {
        this.spec = spec;
        setMediaItem(timeout);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        stopParse();
        spec = PlaySpec.fromParse(result, key, metadata);
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    public void setMediaItem() {
        setMediaItem(Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(long timeout) {
        if (spec == null || spec.getUrl() == null) return;
        setDanmakus(spec.getDanmakus());
        engine.start(spec.checkUa());
        App.post(runnable, timeout);
        callback.onPrepare();
        initTrack = false;
    }

    private void setDanmakus(List<Danmaku> items) {
        setDanmaku(items == null || items.isEmpty() ? Danmaku.empty() : items.get(0));
    }

    public void setDanmaku(Danmaku item) {
        setDanmaku(item, false);
    }

    public void reloadDanmaku(Danmaku item) {
        setDanmaku(item, true);
    }

    private void setDanmaku(Danmaku item, boolean force) {
        if (item.isEmpty()) {
            if (spec != null) spec.setDanmaku(item);
            Log.d(TAG_DANMAKU, "drop: empty source (接口没返回可用弹幕地址) name=" + item.getName());
            clearDanmaku("empty_source");
            return;
        }
        if (danmakuController == null) {
            Log.d(TAG_DANMAKU, "drop: controller 还没挂上，弹幕被丢弃 name=" + item.getName());
            return;
        }
        String url = DanmakuUrlPolicy.normalize(DanmakuSetting.getValidApiUrl(), item.getRealUrl());
        DanmakuUrlPolicy.SourceType sourceType = DanmakuUrlPolicy.classify(url);
        if (!sourceType.isSupported()) {
            if (spec != null) spec.setDanmaku(item);
            Log.d(TAG_DANMAKU, "drop: unsupported source " + DanmakuUrlPolicy.logSummary(url));
            clearDanmaku("unsupported_source");
            return;
        }
        String key = normalizeDanmakuKey(url);
        if (!force && TextUtils.equals(currentDanmakuUrl, url)) {
            Log.d(TAG_DANMAKU, "skip: same url " + DanmakuUrlPolicy.logSummary(url));
            return;
        }
        if (force && shouldSkipForcedDanmakuReload(key)) {
            Log.d(TAG_DANMAKU, "skip: duplicate reload " + summarizeUrl(key));
            return;
        }
        if (spec != null) spec.setDanmaku(item);
        if (force && currentDanmakuUrl != null) danmakuController.clearItems();
        currentDanmakuUrl = url;
        currentDanmakuKey = key;
        if (sourceType.isLive()) {
            danmakuController.clearItems();
            loadingDanmakuKey = null;
            danmakuLoadStartedAtMs = 0;
            danmakuLoadInProgress = false;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "select live source pending websocket connection %s", DanmakuUrlPolicy.logSummary(url));
            if (danmakuForeground && DanmakuSetting.isShow()) connectLiveDanmakuSession(url);
            return;
        }
        stopLiveDanmakuSession("static_source");
        loadingDanmakuKey = key;
        danmakuLoadStartedAtMs = SystemClock.elapsedRealtime();
        danmakuLoadInProgress = true;
        Log.d(TAG_DANMAKU, (force ? "reload" : "load") + " name=" + item.getName() + " " + DanmakuUrlPolicy.logSummary(url) + " show=" + DanmakuSetting.isShow());
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "%s name=%s %s key=%s", force ? "reload" : "load", item.getName(), DanmakuUrlPolicy.logSummary(url), summarizeUrl(key));
        danmakuController.setDataSource(Uri.parse(url));
    }

    private boolean shouldSkipForcedDanmakuReload(String key) {
        if (TextUtils.isEmpty(key) || !TextUtils.equals(currentDanmakuKey, key) || danmakuLoadStartedAtMs <= 0) return false;
        if (danmakuLoadInProgress && (TextUtils.isEmpty(loadingDanmakuKey) || TextUtils.equals(loadingDanmakuKey, key))) return true;
        long elapsed = SystemClock.elapsedRealtime() - danmakuLoadStartedAtMs;
        return elapsed >= 0 && elapsed < DANMAKU_FORCE_RELOAD_DEBOUNCE_MS;
    }

    private void finishDanmakuLoad(Uri uri) {
        String key = normalizeDanmakuKey(uri == null ? "" : uri.toString());
        if (!TextUtils.isEmpty(loadingDanmakuKey) && !TextUtils.equals(loadingDanmakuKey, key)) return;
        danmakuLoadInProgress = false;
        loadingDanmakuKey = null;
    }

    private void clearDanmakuState() {
        currentDanmakuUrl = null;
        currentDanmakuKey = null;
        loadingDanmakuKey = null;
        danmakuLoadStartedAtMs = 0;
        danmakuLoadInProgress = false;
    }

    private void logDanmakuLoad(String event, Uri uri, int count, IOException error) {
        if (!SpiderDebug.isEnabled()) return;
        long elapsed = danmakuLoadStartedAtMs <= 0 ? -1 : SystemClock.elapsedRealtime() - danmakuLoadStartedAtMs;
        if (error == null) {
            SpiderDebug.log("danmaku", "load %s count=%d elapsed=%dms %s", event, count, elapsed, DanmakuUrlPolicy.logSummary(uri == null ? "" : uri.toString()));
        } else {
            SpiderDebug.log("danmaku", "load %s elapsed=%dms %s error=%s", event, elapsed, DanmakuUrlPolicy.logSummary(uri == null ? "" : uri.toString()), error.getMessage());
        }
    }

    private static String normalizeDanmakuKey(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        try {
            Uri uri = Uri.parse(value);
            String nested = getNestedDanmakuUrl(uri);
            return TextUtils.isEmpty(nested) ? value : normalizeDanmakuKey(nested);
        } catch (Throwable e) {
            return value;
        }
    }

    private static String getNestedDanmakuUrl(Uri uri) {
        if (uri == null) return "";
        String path = uri.getPath();
        if (TextUtils.isEmpty(path) || !path.endsWith("/danmaku")) return "";
        return uri.getQueryParameter("url");
    }

    private static String summarizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getPath();
        StringBuilder builder = new StringBuilder();
        builder.append(uri.getScheme()).append("://");
        builder.append(TextUtils.isEmpty(host) ? "unknown" : host);
        if (port > 0) builder.append(':').append(port);
        if (!TextUtils.isEmpty(path)) builder.append(path.length() > 48 ? path.substring(0, 48) + "..." : path);
        builder.append(" len=").append(url.length());
        return builder.toString();
    }

    private void clearDanmaku(String reason) {
        Log.d(TAG_DANMAKU, "clear reason=" + reason + " current=" + DanmakuUrlPolicy.logSummary(currentDanmakuUrl));
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "clear reason=%s current=%s", reason, DanmakuUrlPolicy.logSummary(currentDanmakuUrl));
        stopLiveDanmakuSession(reason);
        liveDanmakuBatcher.clear();
        liveDanmakuBuffer.clear();
        if (danmakuController != null) danmakuController.clearItems();
        clearDanmakuState();
    }


    private void connectLiveDanmakuSession(String url) {
        if (TextUtils.isEmpty(url)) return;
        if (liveDanmakuSession == null) {
            liveDanmakuSession = new LiveDanmakuWebSocketSession(App.get(), new LiveDanmakuWebSocketSession.Listener() {
                @Override
                public void onStateChanged(LiveDanmakuWebSocketSession.State state, long generation, String sourceUrl, int code, String detail) {
                    liveDanmakuGeneration = generation;
                    liveDanmakuMetrics.onState(state, code, SystemClock.elapsedRealtime());
                    if (state == LiveDanmakuWebSocketSession.State.CONNECTING) {
                        liveDanmakuBuffer.reset(generation);
                        liveDanmakuBatcher.reset(generation);
                    } else if (state == LiveDanmakuWebSocketSession.State.RETRY_WAIT || state == LiveDanmakuWebSocketSession.State.STOPPED || state == LiveDanmakuWebSocketSession.State.RELEASED) {
                        liveDanmakuBatcher.clear();
                        liveDanmakuBuffer.clear();
                    }
                    if (state != LiveDanmakuWebSocketSession.State.OPEN) {
                        App.post(() -> logLiveDanmakuMetrics("state_" + state, false));
                        clearLiveDanmakuRenderer(generation);
                    }
                    if (state == LiveDanmakuWebSocketSession.State.CONNECTING || state == LiveDanmakuWebSocketSession.State.OPEN || state == LiveDanmakuWebSocketSession.State.RETRY_WAIT) scheduleLiveDanmakuMetrics();
                    else App.removeCallbacks(liveDanmakuMetricsRunnable);
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku-ws", "state=%s generation=%d code=%d detail=%s %s", state, generation, code, detail, DanmakuUrlPolicy.logSummary(sourceUrl));
                }

                @Override
                public void onMessage(long generation, String text) {
                    if (generation != liveDanmakuGeneration) return;
                    liveDanmakuMetrics.onFrame();
                    long parseStartedNs = SystemClock.elapsedRealtimeNanos();
                    LiveDanmakuParser.Result result = LiveDanmakuParser.parse(text, generation, SystemClock.elapsedRealtime());
                    liveDanmakuMetrics.onParse(result, SystemClock.elapsedRealtimeNanos() - parseStartedNs);
                    if (!result.isAccepted()) return;
                    LiveDanmakuWebSocketSession session = liveDanmakuSession;
                    if (session != null) session.markMessageAccepted(generation);
                    if (result.kind() == LiveDanmakuParser.Kind.ONLINE) {
                        liveDanmakuBuffer.updateOnline(generation, result.online());
                    } else {
                        if (!liveDanmakuPlaybackActive) return;
                        LiveDanmakuMessage message = result.message();
                        LiveDanmakuBuffer.OfferResult offer = liveDanmakuBuffer.offer(message);
                        liveDanmakuMetrics.onOffer(offer);
                        if (offer != LiveDanmakuBuffer.OfferResult.STALE) liveDanmakuBatcher.requestDrain(generation);
                    }
                }
            });
        }
        liveDanmakuGeneration = liveDanmakuSession.connect(url);
    }

    private void stopLiveDanmakuSession(String reason) {
        if (liveDanmakuSession != null) liveDanmakuGeneration = liveDanmakuSession.stop(reason);
    }

    private void releaseLiveDanmakuSession() {
        if (liveDanmakuSession == null) return;
        liveDanmakuSession.release();
        liveDanmakuSession = null;
    }

    private void onLiveDanmakuBatch(long generation, java.util.List<LiveDanmakuMessage> messages) {
        long scheduledAtMs = SystemClock.elapsedRealtime();
        App.post(() -> {
            if (generation != liveDanmakuGeneration || messages.isEmpty() || danmakuController == null || !DanmakuSetting.isShow()) return;
            liveDanmakuMetrics.onBatch(messages.size(), SystemClock.elapsedRealtime() - scheduledAtMs);
            java.util.List<androidx.media3.ui.danmaku.Danmaku> batch = new java.util.ArrayList<>(messages.size());
            for (LiveDanmakuMessage message : messages) {
                int pool = message.type() == LiveDanmakuMessage.Type.SUPER_CHAT ? androidx.media3.ui.danmaku.Danmaku.POOL_SPECIAL : androidx.media3.ui.danmaku.Danmaku.POOL_NORMAL;
                long ttlMs = message.type() == LiveDanmakuMessage.Type.SUPER_CHAT ? LiveDanmakuBuffer.DEFAULT_PRIORITY_TTL_MS : LiveDanmakuBuffer.DEFAULT_NORMAL_TTL_MS;
                batch.add(new androidx.media3.ui.danmaku.Danmaku(message.text(), 0L, androidx.media3.ui.danmaku.Danmaku.TYPE_SCROLL, message.colorArgb(), 0f, pool, "", 0L)
                        .setLiveExpiryElapsedRealtimeMs(message.receivedAtMs() + ttlMs));
            }
            danmakuController.offerLiveBatch(batch);
        });
    }

    private void clearLiveDanmakuRenderer(long generation) {
        App.post(() -> {
            if (generation != liveDanmakuGeneration || danmakuController == null) return;
            danmakuController.clearLiveItems();
        });
    }

    private void discardLiveDanmakuPending() {
        liveDanmakuBuffer.discardPending();
        liveDanmakuBatcher.reset(liveDanmakuGeneration);
        clearLiveDanmakuRenderer(liveDanmakuGeneration);
    }

    private void scheduleLiveDanmakuMetrics() {
        if (!SpiderDebug.isEnabled()) return;
        App.post(liveDanmakuMetricsRunnable, LIVE_DANMAKU_METRICS_INTERVAL_MS);
    }

    private void logLiveDanmakuMetrics(String reason, boolean reschedule) {
        if (!SpiderDebug.isEnabled()) return;
        long nowMs = SystemClock.elapsedRealtime();
        LiveDanmakuMetrics.Snapshot metrics = liveDanmakuMetrics.snapshotAndReset(nowMs);
        LiveDanmakuBuffer.Snapshot buffer = liveDanmakuBuffer.snapshot();
        SpiderDebug.log("danmaku-ws-metrics", "reason=" + reason + " state=" + metrics.state() + " received=" + metrics.received() + " parsed=" + metrics.parsed() + " queued=" + metrics.queued());
        if (reschedule && liveDanmakuSession != null && liveDanmakuSession.state() != LiveDanmakuWebSocketSession.State.STOPPED && liveDanmakuSession.state() != LiveDanmakuWebSocketSession.State.RELEASED) scheduleLiveDanmakuMetrics();
    }

    public void addDanmaku(Danmaku item) {
        if (danmakuController == null || item.isEmpty()) return;
        if (spec != null) spec.addDanmaku(item);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        setMediaItem();
    }

    @Override
    public void onParseError() {
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onTitlesChanged();

        void onError(String msg);

        void onPlayerRebuild(Player newPlayer);
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            liveDanmakuPlaybackActive = isPlaying;
            if (!isPlaying) discardLiveDanmakuPending();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state != Player.STATE_IDLE) App.removeCallbacks(runnable);
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (tracks.isEmpty() || initTrack) return;
            setTrack(Track.find(getKey()));
            callback.onTracksChanged();
            initTrack = true;
        }

        @Override
        public void onMediaEditionsChanged(@NonNull List<MediaEdition> titles) {
            callback.onTitlesChanged();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            PlayerEngine.ErrorAction action = engine.handleError(e);
            if (action == PlayerEngine.ErrorAction.RECOVERED) {
                setDanmakus(spec.getDanmakus());
            } else if (action == PlayerEngine.ErrorAction.FATAL) {
                callback.onError(engine.getErrorMessage(e));
            } else if (++retry > 1) {
                callback.onError(engine.getErrorMessage(e));
            } else {
                toggleDecode();
            }
        }
    };
}

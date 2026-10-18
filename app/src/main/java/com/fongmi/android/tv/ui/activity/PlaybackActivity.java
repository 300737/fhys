package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.ai.AiQuality;
import com.fongmi.android.tv.player.ai.AiQualitySurface;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.common.util.concurrent.ListenableFuture;

public abstract class PlaybackActivity extends BaseActivity implements MediaController.Listener, Player.Listener, ServiceConnection {

    private ListenableFuture<MediaController> mControllerFuture;
    private MediaController mController;
    private PlaybackService mService;
    private boolean audioOnly;
    private boolean redirect;
    private boolean bound;
    private boolean stop;
    private boolean lock;
    private AiQualitySurface aiQuality;

    protected MediaController controller() {
        return mController;
    }

    protected PlaybackService service() {
        return mService;
    }

    protected PlayerManager player() {
        return mService.player();
    }

    protected boolean isRedirect() {
        return redirect;
    }

    protected void setRedirect(boolean redirect) {
        this.redirect = redirect;
        if (mService != null) mService.setNavigationCallback(redirect ? null : getNavigationCallback(), getPlaybackKey());
    }

    protected void updateNavigationKey() {
        if (mService != null) mService.setNavigationCallback(getNavigationCallback(), getPlaybackKey());
    }

    protected boolean isAudioOnly() {
        return audioOnly;
    }

    protected void setAudioOnly(boolean audioOnly) {
        this.audioOnly = audioOnly;
    }

    protected boolean isStop() {
        return stop;
    }

    protected void setStop(boolean stop) {
        this.stop = stop;
    }

    protected boolean isLock() {
        return lock;
    }

    protected void setLock(boolean lock) {
        this.lock = lock;
    }

    protected abstract PlaybackService.NavigationCallback getNavigationCallback();

    protected abstract CustomSeekView getSeekView();

    protected abstract PlayerView getExoView();

    protected abstract String getPlaybackKey();

    protected boolean isOwner() {
        String key = getPlaybackKey();
        boolean owner = key == null || (mService != null && key.equals(player().getKey()));
        android.util.Log.d("AntigravityLive", "isOwner: key=" + key + ", playerKey=" + (player() != null ? player().getKey() : "null") + ", result=" + owner);
        return owner;
    }

    protected boolean isIdle() {
        return mController.getPlaybackState() == Player.STATE_IDLE;
    }

    protected boolean isEnded() {
        return mController.getPlaybackState() == Player.STATE_ENDED;
    }

    protected boolean isBuffering() {
        return mController.getPlaybackState() == Player.STATE_BUFFERING;
    }

    protected boolean isPaused() {
        return !isBuffering() && !isIdle();
    }

    protected void onServiceConnected() {
    }

    protected void onPrepare() {
    }

    protected void onTracksChanged() {
    }

    protected void onTitlesChanged() {
    }

    protected void onError(String msg) {
    }

    protected void onPlayingChanged(boolean isPlaying) {
    }

    protected void onStateChanged(int state) {
    }

    protected void onSizeChanged(VideoSize size) {
    }

    protected void onReclaim() {
    }

    protected void seekTo(long time) {
        mController.seekTo(player().getPosition() + time);
        mController.play();
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout, MediaMetadata metadata) {
        android.util.Log.d("AntigravityLive", "startPlayer: key=" + key + ", realUrl=" + result.getRealUrl() + ", needParse=" + result.needParse() + ", useParse=" + useParse);
        if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) {
            onError(ResUtil.getString(R.string.error_play_drm));
        } else if (result.hasMsg()) {
            onError(result.getMsg());
        } else if (result.getRealUrl().isEmpty()) {
            onError(ResUtil.getString(R.string.error_play_url));
        } else if (result.needParse() || useParse) {
            attachSurface();
            player().parse(key, result, useParse, metadata);
        } else {
            attachSurface();
            player().start(PlaySpec.from(result, key, metadata), timeout);
        }
    }

    private void bindPlaybackService() {
        startService(new Intent(this, PlaybackService.class));
        bindService(new Intent(this, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION), this, BIND_AUTO_CREATE);
        buildControllerAsync();
        bound = true;
    }

    private void buildControllerAsync() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        mControllerFuture = new MediaController.Builder(this, token).setListener(this).buildAsync();
        mControllerFuture.addListener(this::onControllerConnected, ContextCompat.getMainExecutor(this));
    }

    private void onControllerConnected() {
        try {
            mController = mControllerFuture.get();
            getSeekView().setPlayer(mController);
            mController.addListener(this);
        } catch (Exception ignored) {
        }
    }

    private PendingIntent buildSessionIntent() {
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        Bundle extras = getIntent().getExtras();
        if (extras != null) intent.putExtras(extras);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private boolean shouldReclaim() {
        return mService != null && !isOwner();
    }

    private void closePiP() {
        if (!isInPictureInPictureMode()) return;
        detach();
        finish();
    }

    private void attachSurface() {
        if (mService != null && getExoView().getPlayer() == null) getExoView().setPlayer(player().getPlayer());
        detachQuality();
        if (mService != null && player() != null) aiQuality = AiQualitySurface.attach(getExoView(), player().getPlayer());
    }

    private void detachSurface() {
        detachQuality();
        getExoView().setPlayer(null);
    }

    private void detachQuality() {
        if (aiQuality != null) {
            aiQuality.release();
            aiQuality = null;
        }
    }

    /**
     * 画质档位变化后实时生效，调用方（播放页菜单）切换档位时必须调用。
     * <ul>
     *   <li>基本（原画）→ 立即拆掉管线，解码器直出，零开销</li>
     *   <li>增强 / AI增强 → 立即挂上管线（但受【自动直出】约束：1080P+/HDR/杜比视界不开）</li>
     *   <li>增强 ↔ AI增强 互切 → 管线已挂载时什么都不用做：
     *       渲染线程每帧读取档位，切换后下一帧立刻按新档位渲染</li>
     * </ul>
     */
    public void updateAiQuality() {
        if (!AiQuality.isEnabled()) {
            detachQuality();
            return;
        }
        if (mService != null && player() != null) {
            Player p = player().getPlayer();
            // 【自动直出判定】视频分辨率/HDR 信息变化（换源、切清晰度）时同样重新评估
            if (p != null && AiQuality.shouldBypass(p.getVideoSize(), AiQuality.getVideoFormat(p))) {
                detachQuality();
                return;
            }
            if (aiQuality == null) aiQuality = AiQualitySurface.attach(getExoView(), p);
        }
    }

    private void setRender() {
        getExoView().setRender(PlayerSetting.getRender());
        detachSurface();
        attachSurface();
    }

    private void releasePlaybackService() {
        detachSurface();
        if (mService != null) releaseService(isOwner());
        detach();
    }

    private void releaseService(boolean owner) {
        mService.removePlayerCallback(mPlayerCallback);
        if (owner) mService.setNavigationCallback(null, null);
        if (mService.hasExternalClient() || mService.hasPlayerCallback()) {
            if (owner) mService.suspend();
            mService.resetSessionActivity();
        } else if (owner) {
            if (isFinishing() && getClass().getSimpleName().equals("LiveActivity") && LiveSetting.isPreview()) {
                // Do not shutdown if finishing and returning to home screen with preview enabled
            } else {
                mService.shutdown();
            }
        }
    }

    private void detach() {
        releaseController();
        releaseBinding();
    }

    private void releaseController() {
        if (mControllerFuture != null) MediaController.releaseFuture(mControllerFuture);
        if (mController != null) mController.removeListener(this);
        mControllerFuture = null;
        mController = null;
    }

    private void releaseBinding() {
        if (!bound) return;
        bound = false;
        if (mService != null) mService.removePlayerCallback(mPlayerCallback);
        unbindService(this);
        mService = null;
    }

    private final PlaybackService.PlayerCallback mPlayerCallback = new PlaybackService.PlayerCallback() {

        @Override
        public void onPrepare() {
            if (isOwner()) PlaybackActivity.this.onPrepare();
        }

        @Override
        public void onTracksChanged() {
            if (isOwner()) PlaybackActivity.this.onTracksChanged();
        }

        @Override
        public void onTitlesChanged() {
            if (isOwner()) PlaybackActivity.this.onTitlesChanged();
        }

        @Override
        public void onError(String msg) {
            if (isOwner()) PlaybackActivity.this.onError(msg);
        }

        @Override
        public void onPlayerRebuild(Player player) {
            if (isOwner()) setRender();
        }
    };

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        ExoUtil.setPlayerView(getExoView());
        bindPlaybackService();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (!isOwner()) return;
        if (isPlaying) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else if (!isBuffering()) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        onPlayingChanged(isPlaying);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        android.util.Log.d("AntigravityLive", "onPlaybackStateChanged: state=" + state + ", isOwner=" + isOwner());
        if (isOwner()) onStateChanged(state);
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize size) {
        if (aiQuality != null) aiQuality.setSource(size.width, size.height);
        // 自动直出：起播拿到真实分辨率（或 HDR 信息）后重新评估，1080P+/HDR 拆掉管线
        updateAiQuality();
        if (isOwner()) onSizeChanged(size);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        mService = ((PlaybackService.LocalBinder) binder).getService();
        mService.replaceBinding(this::closePiP);
        mService.setSessionActivity(buildSessionIntent());
        mService.setNavigationCallback(getNavigationCallback(), getPlaybackKey());
        mService.addPlayerCallback(mPlayerCallback);
        if (mService != null && player() != null && player().getPlayer() != null) {
            player().getPlayer().setVolume(1.0f);
        }
        if (mService != null && player() != null) {
            player().setDanmakuForeground(true);
        }
        onServiceConnected();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mService != null && player() != null) {
            player().setDanmakuForeground(true);
        }
        setRedirect(false);
        if (shouldReclaim()) {
            detachSurface();
            onReclaim();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRedirect() && mController != null) mController.pause();
    }

    @Override
    protected void onStop() {
        if (mService != null && player() != null) {
            player().setDanmakuForeground(false);
        }
        super.onStop();

        if (isOwner() && PlayerSetting.isBackgroundOff() && mController != null) {
            if (isFinishing() && getClass().getSimpleName().equals("LiveActivity") && LiveSetting.isPreview()) {
                // Do not pause if finishing and returning to home screen with preview enabled
            } else {
                mController.pause();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlaybackService();
    }
}

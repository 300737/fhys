package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.PlayerManager;
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
    }

    private void detachSurface() {
        getExoView().setPlayer(null);
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
        applyFrameRate();
        if (isOwner()) onSizeChanged(size);
    }

    /**
     * 帧率自适应输出：把视频真实帧率声明给系统合成器，
     * 支持的设备（如 SHIELD）会自动把显示刷新率切到视频帧率的整数倍，
     * 24p 电影不再经过 3:2 pulldown 造成的不均匀抖动。
     * 不支持的设备该调用是无害 no-op，任何异常都不影响播放。
     * 注意：media3 的 VideoSize 没有 frameRate，帧率在选中视频轨道的 Format.frameRate 里。
     */
    private void applyFrameRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        if (mService == null || player() == null) {
            Log.d("FrameRate", "skip | service/player not ready");
            return;
        }
        float rate = getVideoFrameRate();
        if (rate <= 0f) {
            Log.d("FrameRate", "skip | video frame rate unknown (" + rate + ")");
            return;
        }
        View video = getExoView().getVideoSurfaceView();
        if (!(video instanceof SurfaceView)) {
            Log.d("FrameRate", "skip | video surface is not SurfaceView");
            return;
        }
        Surface surface = ((SurfaceView) video).getHolder().getSurface();
        if (surface == null || !surface.isValid()) {
            Log.d("FrameRate", "skip | surface not valid");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ALWAYS);
            } else {
                surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
            }
            Log.d("FrameRate", "setFrameRate(" + rate + "fps) requested");
        } catch (Exception e) {
            Log.d("FrameRate", "setFrameRate failed: " + e);
        }
    }

    /** 从当前选中的视频轨道取帧率，取不到返回 0 */
    private float getVideoFrameRate() {
        try {
            for (Tracks.Group group : player().getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSelected(i)) return group.getTrackFormat(i).frameRate;
                }
                return group.getTrackFormat(0).frameRate;
            }
        } catch (Exception e) {
            Log.d("FrameRate", "frame rate lookup failed: " + e);
        }
        return 0f;
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

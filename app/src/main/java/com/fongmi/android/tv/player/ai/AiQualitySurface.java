package com.fongmi.android.tv.player.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.setting.PlayerSetting;

/**
 * 把自研画质管线挂进播放器底层的桥接器。
 *
 * <p>原理：PlayerView 默认把它的 SurfaceView 直接交给 ExoPlayer 作为解码输出。
 * 这里在 SurfaceView 就绪后接管该链路：
 * <pre>
 *   解码输出 = 管线创建的 OES Surface（不再是屏幕）
 *   屏幕 Surface = 管线的 EGL window（由我们的着色器写入）
 * </pre>
 * 因此每一帧都必须先经过 EASU / CAS 着色器才能上屏，属于播放链路内的处理，
 * 与设备是否带硬件 AI 无关。
 *
 * <p>任何一步失败（拿不到 SurfaceView、EGL/GL 初始化失败）都会自动回退到
 * 解码器直出，保证播放不中断。
 */
public class AiQualitySurface implements SurfaceHolder.Callback, GLQualityPipeline.Callback {

    private final PlayerView view;
    private final Player player;
    /**
     * media3 的 Player 只能在创建它的线程（主线程）访问，否则抛
     * "Player is accessed on the wrong thread"。
     * 管线的初始化回调发生在 GL 线程，所以这里统一用主线程 Handler 转发。
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SurfaceView surfaceView;
    private GLQualityPipeline pipeline;
    /** 已释放标记：防止 GL 线程晚到的 onReady 把已释放的 Surface 塞给播放器 */
    private volatile boolean released;

    private AiQualitySurface(@NonNull PlayerView view, @NonNull Player player) {
        this.view = view;
        this.player = player;
    }

    /** 画质开关关闭或条件不满足时返回 null，调用方无需额外判断 */
    public static AiQualitySurface attach(PlayerView view, Player player) {
        if (view == null || player == null || !AiQuality.isEnabled()) {
            Log.d(AiQuality.TAG, "attach skipped | view=" + (view != null)
                    + " player=" + (player != null)
                    + " enabled=" + AiQuality.isEnabled());
            return null;
        }
        AiQualitySurface holder = new AiQualitySurface(view, player);
        return holder.bind() ? holder : null;
    }

    private boolean bind() {
        View surface = view.getVideoSurfaceView();
        if (!(surface instanceof SurfaceView)) {
            // 管线需要一个真实的 Surface 作为 EGL window，TextureView 无法提供，切回 SurfaceView
            // setRender 必须在主线程且同步返回，这样能立刻拿到重建后的视图
            if (Looper.myLooper() != Looper.getMainLooper()) return false;
            view.setRender(0);
            surface = view.getVideoSurfaceView();
            if (!(surface instanceof SurfaceView)) return false;
        }
        surfaceView = (SurfaceView) surface;
        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(this);
        Surface current = holder.getSurface();
        if (current != null && current.isValid()) surfaceCreated(holder);
        return true;
    }

    /** 视频分辨率变化（起播拿到真实尺寸、切换清晰度）时同步给着色器 */
    public void setSource(int width, int height) {
        if (pipeline != null) pipeline.setSource(width, height);
    }

    /** 画质档位调整后重建管线，让新参数立即生效 */
    public void rebuild() {
        if (surfaceView == null) return;
        surfaceCreated(surfaceView.getHolder());
    }

    public void release() {
        released = true;
        if (surfaceView != null) {
            surfaceView.getHolder().removeCallback(this);
            surfaceView = null;
        }
        releasePipeline();
        fallback();
    }

    // ---------------- Surface 生命周期 ----------------

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        releasePipeline();
        Surface output = holder.getSurface();
        if (output == null || !output.isValid()) return;
        int width = surfaceView.getWidth();
        int height = surfaceView.getHeight();
        if (width <= 0 || height <= 0) {
            width = view.getWidth();
            height = view.getHeight();
        }
        if (width <= 0 || height <= 0) {
            width = 1920;
            height = 1080;
        }
        // 伪 4K：先把画面超分渲染到 4K 缓冲，再缩放上屏，等效 4 倍超采样
        if (PlayerSetting.isUpscale4K() && width < 3840) {
            width = 3840;
            height = 2160;
        }
        Log.d(AiQuality.TAG, "start pipeline | mode=" + AiQuality.getLevelName()
                + " | targetBuffer=" + width + "x" + height + " | surface=" + output);
        pipeline = new GLQualityPipeline(output, width, height, this);
        VideoSize size = player.getVideoSize();
        if (size != null && size.width > 0 && size.height > 0) pipeline.setSource(size.width, size.height);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // FBO 已按首次创建的显示尺寸分配，旋转/缩放由视图层处理，无需重建
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        releasePipeline();
        if (player.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) player.clearVideoSurface();
    }

    // ---------------- 管线回调 ----------------

    @Override
    public void onReady(@NonNull Surface decoderSurface) {
        // 初始化完成回调发生在 GL 线程；Player 只能在主线程访问，必须切换
        mainHandler.post(() -> {
            if (released || surfaceView == null) return;
            if (!player.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) {
                Log.w(AiQuality.TAG, "COMMAND_SET_VIDEO_SURFACE unavailable, fallback");
                fallback();
                return;
            }
            player.setVideoSurface(decoderSurface);
            Log.d(AiQuality.TAG, "decoder output hijacked -> pipeline OES surface (ALL frames now go through shaders)");
        });
    }

    @Override
    public void onError() {
        mainHandler.post(this::fallback);
    }

    private void releasePipeline() {
        if (pipeline != null) {
            pipeline.release();
            pipeline = null;
        }
    }

    /** 回退到解码器直出，保证任何异常下画面不黑 */
    private void fallback() {
        if (surfaceView == null) return;
        if (player.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) {
            player.setVideoSurfaceView(surfaceView);
        }
    }
}

package com.fongmi.android.tv.player.exo;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

/**
 * 全局音频直通渲染器工厂。
 *
 * <p>与默认链路的区别：解码后的音频不再经过软件混音/重采样，而是以 offload 方式
 * 直送音频输出端（HDMI/SPDIF 的功放、回音壁负责最终解码），同时开启浮点输出
 * 保留动态范围。多声道音轨（AC3/E-AC3/DTS 等）因此不再被降级为立体声。
 *
 * <p>这是纯软件层的配置，依赖的是 Android 标准的 AudioTrack offload 能力，
 * 不依赖任何设备私有接口；若设备不支持 offload，会自动退回普通链路，不影响播放。
 */
public final class AudioPassthroughRenderersFactory extends DefaultRenderersFactory {

    public AudioPassthroughRenderersFactory(Context context) {
        super(context);
    }

    @Override
    @Nullable
    protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParameters) {
        DefaultAudioSink sink = new DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(true)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
                .build();
        // 开启 offload：压缩音频直通输出端，绕过软件解码后处理。
        // 用 GAPLESS_NOT_REQUIRED —— 不强制要求无缝衔接，能启用 offload 的设备更多。
        sink.setOffloadMode(AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED);
        return sink;
    }
}

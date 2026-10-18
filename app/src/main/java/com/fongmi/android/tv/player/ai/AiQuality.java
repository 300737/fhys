package com.fongmi.android.tv.player.ai;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;

import com.github.catvod.utils.Prefers;

/**
 * 软件自研画质引擎的档位与开关。
 *
 * <p>注意：这是 App 自己的画质管线，不依赖任何设备的硬件 AI 能力
 * （不使用 NVIDIA SHIELD 的 AI Upscaling、不调用厂商私有 API），
 * 全部由 GLSL 着色器在 GPU 上完成，因此在任何支持 OpenGL ES 3.0 的设备上行为一致。
 *
 * <p>档位说明：
 * <ul>
 *   <li>BASIC   —— 基本模式（原画直出）：关闭全部滤镜，解码器直出，零开销，低端设备省电</li>
 *   <li>ENHANCE —— 增强模式：仅开启 CAS 对比度自适应锐化，提升通透感，1:1 不做超分</li>
 *   <li>AI      —— AI增强模式：火力全开，EASU 边缘自适应超分 + CAS 锐化，
 *       重点给低清老片做画质修复</li>
 * </ul>
 */
public class AiQuality {

    /** 日志 tag，抓 logcat 时用 `adb logcat -s AI-Quality` */
    public static final String TAG = "AI-Quality";

    /** 基本（原画模式） */
    public static final int BASIC = 0;
    /** 增强（CAS 锐化） */
    public static final int ENHANCE = 1;
    /** AI 增强（EASU 超分 + CAS 锐化） */
    public static final int AI = 2;

    public static final int COUNT = 3;

    /** 锐化强度默认值（百分比） */
    public static final int DEFAULT_SHARPNESS = 50;
    /** 降噪强度默认值（百分比） */
    public static final int DEFAULT_DENOISE = 30;

    /** 引擎是否启用（总开关：非基本档即启用管线） */
    public static boolean isEnabled() {
        return getLevel() != BASIC;
    }

    /**
     * 【自动直出判定，写死不可关】
     * 1080P 及以上（宽≥1920 或 高≥1080）或 HDR / 杜比视界源，一律原画直出，
     * 只有低清老片（720P 以下 SDR）才允许开滤镜。
     *
     * <p>原因：软件管线对高清源是负优化——高清本身细节充足，锐化只会放大噪点、
     * 变形人脸；HDR/杜比视界经 OES→FBO→窗口两跳渲染还会损失动态范围。
     * 滤镜的价值场景只有低清老片（≤720P SDR）的超分与降噪。
     */
    public static boolean shouldBypass(@Nullable VideoSize size, @Nullable Format format) {
        int w = size == null ? 0 : size.width;
        int h = size == null ? 0 : size.height;
        if (w >= 1920 || h >= 1080) {
            Log.d(TAG, "auto bypass | " + w + "x" + h + " >= 1080P, direct output (no filters)");
            return true;
        }
        if (format != null) {
            String mime = format.sampleMimeType;
            if (mime != null && (mime.contains("dvh") || mime.contains("dva") || mime.contains("dolby"))) {
                Log.d(TAG, "auto bypass | Dolby Vision source, direct output");
                return true;
            }
            ColorInfo ci = format.colorInfo;
            if (ci != null && (ci.colorTransfer == C.COLOR_TRANSFER_ST2084
                    || ci.colorTransfer == C.COLOR_TRANSFER_HLG)) {
                Log.d(TAG, "auto bypass | HDR (PQ/HLG) source, direct output");
                return true;
            }
        }
        return false;
    }

    /**
     * 从播放器当前轨道取选中的视频 Format。
     * media3 的 Player 接口不暴露 getVideoFormat()，只能从 Tracks 里拿选中轨道的格式，
     * HDR/杜比视界的判定信息（colorTransfer、采样 mime）都在这里。
     */
    @Nullable
    public static Format getVideoFormat(@Nullable Player player) {
        if (player == null) return null;
        try {
            for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSelected(i)) return group.getTrackFormat(i);
                }
                return group.getTrackFormat(0);
            }
        } catch (Exception e) {
            Log.d(TAG, "video format lookup failed: " + e);
        }
        return null;
    }

    /** 当前档位，默认基本（原画） */
    public static int getLevel() {
        return Prefers.getInt("ai_quality_level", BASIC);
    }

    public static void putLevel(int level) {
        Prefers.put("ai_quality_level", Math.max(BASIC, Math.min(AI, level)));
        Log.d(TAG, "switch level -> " + getLevelName()
                + " | upscale=" + needUpscale() + " sharpen=" + needSharpen() + " denoise=" + needEnhance());
    }

    /** 当前档位的可读名称 */
    public static String getLevelName() {
        switch (getLevel()) {
            case ENHANCE:
                return "ENHANCE(CAS锐化)";
            case AI:
                return "AI(EASU超分+CAS)";
            default:
                return "BASIC(原画直出)";
        }
    }

    /** 锐化强度 0.0 ~ 1.0，默认 0.5 */
    public static float getSharpness() {
        return Prefers.getInt("ai_quality_sharpness", DEFAULT_SHARPNESS) / 100f;
    }

    public static void putSharpness(int percent) {
        Prefers.put("ai_quality_sharpness", Math.max(0, Math.min(100, percent)));
        Log.d(TAG, "sharpness -> " + percent + "%");
    }

    /** 降噪强度 0.0 ~ 1.0，默认 0.3（过强会糊掉细节） */
    public static float getDenoise() {
        return Prefers.getInt("ai_quality_denoise", DEFAULT_DENOISE) / 100f;
    }

    public static void putDenoise(int percent) {
        Prefers.put("ai_quality_denoise", Math.max(0, Math.min(100, percent)));
        Log.d(TAG, "denoise -> " + percent + "%");
    }

    /** 是否需要执行 EASU 超分 pass（仅 AI 增强档） */
    public static boolean needUpscale() {
        return getLevel() == AI;
    }

    /** 是否需要执行 CAS 锐化 pass（增强与 AI 增强档） */
    public static boolean needSharpen() {
        return getLevel() >= ENHANCE;
    }

    /** 是否需要执行降噪与色彩增强（AI 增强档） */
    public static boolean needEnhance() {
        return getLevel() >= AI;
    }

    /** 档位名称，供播放器菜单与设置页显示 */
    public static String[] getLevelNames() {
        return new String[]{"基本", "增强", "AI增强"};
    }
}

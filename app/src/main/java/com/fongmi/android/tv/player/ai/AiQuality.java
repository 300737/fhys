package com.fongmi.android.tv.player.ai;

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

    /** 当前档位，默认基本（原画） */
    public static int getLevel() {
        return Prefers.getInt("ai_quality_level", BASIC);
    }

    public static void putLevel(int level) {
        Prefers.put("ai_quality_level", Math.max(BASIC, Math.min(AI, level)));
    }

    /** 锐化强度 0.0 ~ 1.0，默认 0.5 */
    public static float getSharpness() {
        return Prefers.getInt("ai_quality_sharpness", DEFAULT_SHARPNESS) / 100f;
    }

    public static void putSharpness(int percent) {
        Prefers.put("ai_quality_sharpness", Math.max(0, Math.min(100, percent)));
    }

    /** 降噪强度 0.0 ~ 1.0，默认 0.3（过强会糊掉细节） */
    public static float getDenoise() {
        return Prefers.getInt("ai_quality_denoise", DEFAULT_DENOISE) / 100f;
    }

    public static void putDenoise(int percent) {
        Prefers.put("ai_quality_denoise", Math.max(0, Math.min(100, percent)));
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

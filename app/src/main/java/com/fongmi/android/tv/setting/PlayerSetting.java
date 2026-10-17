package com.fongmi.android.tv.setting;

import android.content.Intent;
import android.provider.Settings;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Prefers;

public class PlayerSetting {

    public static int getRender() {
        return Prefers.getInt("render", 0);
    }

    public static void putRender(int render) {
        Prefers.put("render", render);
    }

    public static int getSize() {
        return Prefers.getInt("size", 2);
    }

    public static void putSize(int size) {
        Prefers.put("size", size);
    }

    public static int getScale() {
        return Prefers.getInt("scale");
    }

    public static void putScale(int scale) {
        Prefers.put("scale", scale);
    }

    public static int getBuffer() {
        return Math.min(Math.max(Prefers.getInt("buffer"), 1), 10);
    }

    public static void putBuffer(int buffer) {
        Prefers.put("buffer", buffer);
    }

    public static int getBackground() {
        return Prefers.getInt("background", Util.isLeanback() ? 0 : 2);
    }

    public static void putBackground(int background) {
        Prefers.put("background", background);
    }

    public static boolean isBackgroundOff() {
        return getBackground() == 0;
    }

    public static boolean isBackgroundOn() {
        return getBackground() == 1 || getBackground() == 2;
    }

    public static boolean isBackgroundPiP() {
        return getBackground() == 2;
    }

    public static boolean isHomeMute() {
        return Prefers.getBoolean("home_mute", false);
    }

    public static void putHomeMute(boolean homeMute) {
        Prefers.put("home_mute", homeMute);
    }

    public static float getSpeed() {
        return Math.min(Math.max(Prefers.getFloat("speed", 3), 2), 5);
    }

    public static void putSpeed(float speed) {
        Prefers.put("speed", speed);
    }

    public static boolean isCaption() {
        return Prefers.getBoolean("caption");
    }

    public static void putCaption(boolean caption) {
        Prefers.put("caption", caption);
    }

    public static boolean hasCaption() {
        return new Intent(Settings.ACTION_CAPTIONING_SETTINGS).resolveActivity(App.get().getPackageManager()) != null;
    }

    public static boolean isTunnel() {
        return Prefers.getBoolean("tunnel");
    }

    public static void putTunnel(boolean tunnel) {
        Prefers.put("tunnel", tunnel);
    }

    public static boolean isAudioPrefer() {
        return Prefers.getBoolean("audio_prefer");
    }

    public static void putAudioPrefer(boolean audioPrefer) {
        Prefers.put("audio_prefer", audioPrefer);
    }

    public static boolean isVideoPrefer() {
        return Prefers.getBoolean("video_prefer");
    }

    public static void putVideoPrefer(boolean videoPrefer) {
        Prefers.put("video_prefer", videoPrefer);
    }

    public static boolean isPreferAAC() {
        return Prefers.getBoolean("prefer_aac");
    }

    public static void putPreferAAC(boolean preferAAC) {
        Prefers.put("prefer_aac", preferAAC);
    }

    public static float getSubtitleTextSize() {
        return Prefers.getFloat("subtitle_text_size");
    }

    public static void putSubtitleTextSize(float value) {
        Prefers.put("subtitle_text_size", value);
    }

    public static float getSubtitlePosition() {
        return Prefers.getFloat("subtitle_position");
    }

    public static void putSubtitlePosition(float value) {
        Prefers.put("subtitle_position", value);
    }

    public static boolean isAutoplay() {
        return Prefers.getBoolean("autoplay", true);
    }

    public static void putAutoplay(boolean autoplay) {
        Prefers.put("autoplay", autoplay);
    }

    /**
     * 全局音频直通：音频走 offload 链路直送输出端，绕过软件混音/重采样，
     * 并启用浮点输出保留动态范围。适合带功放/回音壁的电视与盒子。
     */
    public static boolean isPassthrough() {
        return Prefers.getBoolean("audio_passthrough");
    }

    public static void putPassthrough(boolean passthrough) {
        Prefers.put("audio_passthrough", passthrough);
    }

    /**
     * 伪 4K：即使屏幕只有 1080p，也先把画面超分渲染到 4K 缓冲再缩放上屏，
     * 等效于 4 倍超采样，边缘与锐化细节明显优于直接放大。
     */
    public static boolean isUpscale4K() {
        return Prefers.getBoolean("upscale_4k");
    }

    public static void putUpscale4K(boolean upscale4K) {
        Prefers.put("upscale_4k", upscale4K);
    }

    /** 秒切线路：起播后预热备选线路的连接，切换时跳过 DNS/TLS 握手 */
    public static boolean isPreloadLine() {
        return Prefers.getBoolean("preload_line", true);
    }

    public static void putPreloadLine(boolean preload) {
        Prefers.put("preload_line", preload);
    }
}

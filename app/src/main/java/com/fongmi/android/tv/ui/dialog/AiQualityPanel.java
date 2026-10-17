package com.fongmi.android.tv.ui.dialog;

import android.view.View;
import android.widget.TextView;

import com.fongmi.android.tv.databinding.DialogAiQualityBinding;
import com.fongmi.android.tv.player.ai.AiQuality;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * 画质面板的交互逻辑。
 *
 * <p>档位与强度全部落到 {@link AiQuality} 的偏好里，渲染线程每帧读取，
 * 所以这里不需要主动通知管线——改完下一帧立刻生效。
 * 唯一需要外部介入的是"基本档要拆管线、其余档要挂管线"，通过 {@code onApply} 回调。
 */
final class AiQualityPanel {

    private final DialogAiQualityBinding binding;
    private final Runnable onApply;

    AiQualityPanel(DialogAiQualityBinding binding, Runnable onApply) {
        this.binding = binding;
        this.onApply = onApply;
    }

    void bind() {
        initModes();
        setupSlider(binding.sharpnessSlider, binding.sharpnessValue, Math.round(AiQuality.getSharpness() * 100), AiQuality::putSharpness);
        setupSlider(binding.denoiseSlider, binding.denoiseValue, Math.round(AiQuality.getDenoise() * 100), AiQuality::putDenoise);
        binding.basic.setOnClickListener(this::onMode);
        binding.enhance.setOnClickListener(this::onMode);
        binding.ai.setOnClickListener(this::onMode);
        binding.reset.setOnClickListener(this::onReset);
        syncModes();
    }

    private void initModes() {
        String[] names = AiQuality.getLevelNames();
        binding.basic.setText(names[AiQuality.BASIC]);
        binding.enhance.setText(names[AiQuality.ENHANCE]);
        binding.ai.setText(names[AiQuality.AI]);
    }

    private void onMode(View view) {
        int level;
        if (view == binding.basic) level = AiQuality.BASIC;
        else if (view == binding.enhance) level = AiQuality.ENHANCE;
        else level = AiQuality.AI;
        AiQuality.putLevel(level);
        syncModes();
        if (onApply != null) onApply.run();
    }

    private void onReset(View view) {
        AiQuality.putSharpness(AiQuality.DEFAULT_SHARPNESS);
        AiQuality.putDenoise(AiQuality.DEFAULT_DENOISE);
        binding.sharpnessSlider.setValue(AiQuality.DEFAULT_SHARPNESS);
        binding.denoiseSlider.setValue(AiQuality.DEFAULT_DENOISE);
        binding.sharpnessValue.setText(format(AiQuality.DEFAULT_SHARPNESS));
        binding.denoiseValue.setText(format(AiQuality.DEFAULT_DENOISE));
    }

    /** 高亮当前档位；基本档没有滤镜可调，强度滑杆置灰 */
    private void syncModes() {
        int level = AiQuality.getLevel();
        highlight(binding.basic, level == AiQuality.BASIC);
        highlight(binding.enhance, level == AiQuality.ENHANCE);
        highlight(binding.ai, level == AiQuality.AI);
        boolean tunable = level != AiQuality.BASIC;
        binding.sharpnessSlider.setEnabled(tunable);
        binding.denoiseSlider.setEnabled(tunable);
        binding.sharpnessSlider.setAlpha(tunable ? 1f : 0.4f);
        binding.denoiseSlider.setAlpha(tunable ? 1f : 0.4f);
    }

    private void highlight(MaterialButton button, boolean selected) {
        button.setAlpha(selected ? 1f : 0.45f);
    }

    private void setupSlider(Slider slider, TextView label, int value, IntConsumer setter) {
        float clamped = Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), value));
        slider.clearOnChangeListeners();
        slider.setLabelFormatter(this::format);
        slider.setValue(clamped);
        label.setText(format(clamped));
        slider.addOnChangeListener((source, v, fromUser) -> {
            if (!fromUser) return;
            int percent = Math.round(v);
            setter.accept(percent);
            label.setText(format(v));
        });
    }

    private String format(float value) {
        return String.format(Locale.getDefault(), "%d%%", Math.round(value));
    }
}

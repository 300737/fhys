package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogAiQualityBinding;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

/**
 * 播放页内的画质面板：三档切换 + 锐化/降噪强度。
 *
 * <p>所有改动直接写入 {@link com.fongmi.android.tv.player.ai.AiQuality} 的偏好存储，
 * 渲染线程每帧读取档位与强度，因此拖动滑杆的下一帧画面就会跟着变。
 * 档位切换通过 {@code onApply} 回调通知播放页挂载或拆除管线。
 */
public final class AiQualityDialog {

    private final Runnable onApply;

    private AiQualityDialog(Runnable onApply) {
        this.onApply = onApply;
    }

    public static AiQualityDialog create(Runnable onApply) {
        return new AiQualityDialog(onApply);
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        for (Fragment f : manager.getFragments()) if (f instanceof BaseBottomSheetDialog || f instanceof BaseSideSheetDialog) return;
        if (Util.isFullscreenLand(activity) || Util.isLeanback()) new SideSheet(onApply).show(manager, null);
        else new BottomSheet(onApply).show(manager, null);
    }

    private static DialogAiQualityBinding inflate(LayoutInflater inflater, ViewGroup container) {
        return DialogAiQualityBinding.inflate(inflater, container, false);
    }

    public static final class BottomSheet extends BaseBottomSheetDialog {

        private DialogAiQualityBinding binding;
        private final Runnable onApply;

        BottomSheet(Runnable onApply) {
            this.onApply = onApply;
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = AiQualityDialog.inflate(inflater, container);
        }

        @Override
        protected void initView() {
            new AiQualityPanel(binding, onApply).bind();
        }
    }

    public static final class SideSheet extends BaseSideSheetDialog {

        private DialogAiQualityBinding binding;
        private final Runnable onApply;

        SideSheet(Runnable onApply) {
            this.onApply = onApply;
        }

        @Override
        protected int getWidth() {
            return Math.min(ResUtil.dp2px(360), ResUtil.getScreenWidth() / 2);
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = AiQualityDialog.inflate(inflater, container);
        }

        @Override
        protected void initView() {
            new AiQualityPanel(binding, onApply).bind();
        }
    }
}

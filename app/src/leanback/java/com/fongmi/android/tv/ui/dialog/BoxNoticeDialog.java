package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.databinding.DialogBoxNoticeBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 首页弹窗公告：内容来自后台「TV首页公告」表（box_home_notice），
 * 有图就显示图，没图只显示文字。
 */
public class BoxNoticeDialog extends BaseAlertDialog {

    private DialogBoxNoticeBinding binding;
    private String title;
    private String content;
    private String image;
    private Runnable onClose;

    public static BoxNoticeDialog create() {
        return new BoxNoticeDialog();
    }

    public BoxNoticeDialog title(String title) {
        this.title = title;
        return this;
    }

    public BoxNoticeDialog content(String content) {
        this.content = content;
        return this;
    }

    public BoxNoticeDialog image(String image) {
        this.image = image;
        return this;
    }

    /** 关掉之后要接着做的事（比如再弹版本更新），保证两个弹窗不会同时抢焦点。 */
    public BoxNoticeDialog onClose(Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        Runnable runnable = onClose;
        onClose = null;
        if (runnable != null) runnable.run();
    }

    public BoxNoticeDialog show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing()) return this;
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogBoxNoticeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot()).setCancelable(true);
    }

    @Override
    protected void initView() {
        // 弹的是后台配的「内容」，标题只是后台记录用的名字，不往屏幕上放。
        // 只有内容为空时才退回显示标题，免得弹出一个空框。
        boolean hasContent = !TextUtils.isEmpty(content);
        binding.content.setText(hasContent ? content : (TextUtils.isEmpty(title) ? "" : title));
        binding.title.setVisibility(View.GONE);
        if (TextUtils.isEmpty(image)) {
            binding.image.setVisibility(View.GONE);
        } else {
            binding.image.setVisibility(View.VISIBLE);
            Glide.with(this).load(image).into(binding.image);
        }
        setWidth(0.6f);
    }

    @Override
    protected void initEvent() {
        binding.confirm.setOnClickListener(view -> dismissAllowingStateLoss());
    }
}

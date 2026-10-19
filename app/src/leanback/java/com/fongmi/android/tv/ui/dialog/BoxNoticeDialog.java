package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;

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
        binding.title.setText(TextUtils.isEmpty(title) ? "" : title);
        binding.content.setText(TextUtils.isEmpty(content) ? "" : content);
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

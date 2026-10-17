package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogContentBinding;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.utils.ImgUtil;
import com.github.bassaer.library.MDColor;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ContentDialog extends BaseAlertDialog {

    private DialogContentBinding binding;
    private CharSequence content;
    private String name;
    private String pic;

    public static ContentDialog create() {
        return new ContentDialog();
    }

    public ContentDialog name(String name) {
        this.name = name;
        return this;
    }

    public ContentDialog pic(String pic) {
        this.pic = pic;
        return this;
    }

    public ContentDialog content(CharSequence content) {
        this.content = content;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogContentBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.text.setText(content, TextView.BufferType.SPANNABLE);
        binding.text.setLinkTextColor(MDColor.YELLOW_500);
        if (binding.image != null && !TextUtils.isEmpty(pic)) {
            ImgUtil.load(name, pic, binding.image);
            binding.image.setVisibility(View.VISIBLE);
        } else if (binding.image != null) {
            binding.image.setVisibility(View.GONE);
        }
        CustomMovement.bind(binding.text);
    }
}

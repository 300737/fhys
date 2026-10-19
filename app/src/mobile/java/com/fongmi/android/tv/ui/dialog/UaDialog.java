package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogUaBinding;
import com.fongmi.android.tv.impl.UaListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class UaDialog extends BaseAlertDialog {

    private DialogUaBinding binding;
    private boolean append = true;

    public static void show(Fragment fragment) {
        new UaDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogUaBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.player_ua).setView(getBinding().getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        // 打开必须是空的：以前这里会把上次保存的 UA 原样填回来，
        // 只要误存过一段乱七八糟的文字（比如把聊天内容粘进来），以后每次打开都带着。
        String text = Setting.getUa();
        // UA 里不可能有中文，也不该超过 300 字；命中就当脏数据清掉
        if (!TextUtils.isEmpty(text) && (text.length() > 300 || text.matches(".*[\\u4e00-\\u9fa5].*"))) {
            Setting.putUa("");
            text = "";
        }
        binding.text.setText("");
        binding.text.setHint(TextUtils.isEmpty(text) ? "UA" : text);
    }

    @Override
    protected void initEvent() {
        binding.text.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private void detect(String s) {
        if (append && "c".equalsIgnoreCase(s)) {
            append = false;
            binding.text.setText(com.github.catvod.utils.Util.CHROME);
        } else if (append && "o".equalsIgnoreCase(s)) {
            append = false;
            binding.text.setText(com.github.catvod.utils.Util.OKHTTP);
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    private void onPositive(DialogInterface dialog, int which) {
        ((UaListener) requireParentFragment()).setUa(binding.text.getText().toString().trim());
        dismiss();
    }
}
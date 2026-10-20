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
        String text = Setting.getUa();
        // 误存过的脏值（含中文 / 超 300 字，比如把聊天内容粘进来过）直接清掉：
        // 这种值换源码、清缓存、重装都消不掉，只能在这里主动清。
        if (!TextUtils.isEmpty(text) && (text.length() > 300 || text.matches(".*[\\u4e00-\\u9fa5].*"))) {
            Setting.putUa("");
            text = "";
        }
        // 打开一律空白：原版会把存档值回填进输入框，脏值就永远躺在框里。
        // 干净的旧值只放 hint 展示，不进正文。
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
        String v = binding.text.getText().toString().trim();
        // 打开即空白的设计下，没打字直接点确定 = 保持原值，防止误清正常 UA。
        // 真正的脏值由 initView 的自动清理兜底，不需要用户手动删。
        if (v.isEmpty()) v = Setting.getUa();
        ((UaListener) requireParentFragment()).setUa(v);
        dismiss();
    }
}
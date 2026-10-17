package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogConfigBinding;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.FileChooser;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConfigDialog extends BaseAlertDialog {

    private DialogConfigBinding binding;
    private boolean append = true;
    private boolean edit;
    private String ori;
    private int type;
    private Config config;

    public static ConfigDialog create() {
        return new ConfigDialog();
    }

    public ConfigDialog type(int type) {
        this.type = type;
        return this;
    }

    public ConfigDialog config(Config config) {
        this.config = config;
        return this;
    }

    public ConfigDialog vod() {
        type = 0;
        return this;
    }

    public ConfigDialog live() {
        type = 1;
        return this;
    }

    public ConfigDialog wall() {
        type = 2;
        return this;
    }

    public ConfigDialog edit() {
        edit = true;
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogConfigBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(type == 0 ? R.string.setting_vod : type == 1 ? R.string.setting_live : R.string.setting_wall).setView(getBinding().getRoot()).setPositiveButton(edit ? R.string.dialog_edit : R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        binding.name.setText(getConfig().getName());
        binding.url.setText(ori = getConfig().getUrl());
        binding.ua.setText(getConfig().getUa());
        binding.input.setVisibility(type == 1 || edit ? View.VISIBLE : View.GONE);
        binding.inputUa.setVisibility(type == 1 ? View.VISIBLE : View.GONE);
        binding.url.setSelection(TextUtils.isEmpty(ori) ? 0 : ori.length());
    }

    @Override
    protected void initEvent() {
        binding.choose.setEndIconOnClickListener(this::onChoose);
        binding.url.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private Config getConfig() {
        if (config != null) return config;
        return switch (type) {
            case 0 -> VodConfig.get().getConfig();
            case 1 -> LiveConfig.get().getConfig();
            case 2 -> WallConfig.get().getConfig();
            default -> new Config();
        };
    }

    private void onChoose(View view) {
        if (type != 0) {
            FileChooser.from(launcher).show();
            return;
        }
        android.widget.PopupMenu popup = new android.widget.PopupMenu(getActivity(), view);
        popup.getMenu().add(0, 1, 0, R.string.dialog_config_choose_file);
        popup.getMenu().add(0, 2, 0, R.string.dialog_config_choose_folder);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                FileChooser.from(launcher).show();
            } else {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                folderLauncher.launch(Intent.createChooser(intent, ""));
            }
            return true;
        });
        popup.show();
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ile://");
        } else if (append && "a".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ssets://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    private void onPositive(DialogInterface dialog, int which) {
        String url = binding.url.getText().toString().trim();
        String name = binding.name.getText().toString().trim();
        String ua = type == 1 ? binding.ua.getText().toString().trim() : "";
        if (edit) Config.find(ori, type).url(url).name(name).ua(ua).update();
        if (url.isEmpty()) Config.delete(ori, type);
        ((ConfigListener) requireParentFragment()).setConfig(Config.find(url, type).name(name).ua(ua).save());
        dismiss();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        ((ConfigListener) requireParentFragment()).setConfig(Config.find("file:/" + FileChooser.getPathFromUri(result.getData().getData()).replace(Path.rootPath(), ""), type));
        dismiss();
    });

    private final ActivityResultLauncher<Intent> folderLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (path != null) {
            if (!path.endsWith("/")) path = path + "/";
            ((ConfigListener) requireParentFragment()).setConfig(Config.find("file:/" + path.replace(Path.rootPath(), ""), type));
            dismiss();
        }
    });
}
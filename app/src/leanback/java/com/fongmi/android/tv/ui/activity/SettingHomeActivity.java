package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingHomeBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class SettingHomeActivity extends BaseActivity {

    private ActivitySettingHomeBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingHomeActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.livePreview.requestFocus();
        mBinding.livePreviewText.setText(getSwitch(LiveSetting.isPreview()));
        mBinding.homeMuteText.setText(getSwitch(PlayerSetting.isHomeMute()));
        mBinding.historyText.setText(getSwitch(Setting.isHistory()));
    }

    @Override
    protected void initEvent() {
        mBinding.livePreview.setOnClickListener(this::setLivePreview);
        mBinding.homeMute.setOnClickListener(this::setHomeMute);
        mBinding.history.setOnClickListener(this::setHistory);
    }

    private void setLivePreview(View view) {
        LiveSetting.putPreview(!LiveSetting.isPreview());
        mBinding.livePreviewText.setText(getSwitch(LiveSetting.isPreview()));
        RefreshEvent.home();
    }

    private void setHomeMute(View view) {
        PlayerSetting.putHomeMute(!PlayerSetting.isHomeMute());
        mBinding.homeMuteText.setText(getSwitch(PlayerSetting.isHomeMute()));
    }

    private void setHistory(View view) {
        Setting.putHistory(!Setting.isHistory());
        mBinding.historyText.setText(getSwitch(Setting.isHistory()));
        RefreshEvent.history();
    }
}

package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.HistoryAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HistoryActivity extends BaseActivity implements HistoryAdapter.OnClickListener {

    private ActivityHistoryBinding mBinding;
    private HistoryAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HistoryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        getHistory();
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter = new HistoryAdapter(this));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getHistory() {
        mAdapter.setItems(History.get(), () -> mBinding.progressLayout.showContent(true, mAdapter.getItemCount()));
    }

    private void loadConfig(Config config, History item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.HISTORY) getHistory();
    }

    @Override
    public void onItemClick(History item) {
        Config config = Config.find(item.getCid());
        if (config == null) CollectActivity.start(this, item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mAdapter.remove(item.delete(), () -> {
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
        });
        RefreshEvent.history();
        com.fongmi.android.tv.utils.WebDavSync.upload(null);
    }

    @Override
    public boolean onLongClick() {
        if (mAdapter.isDelete() && mAdapter.getItemCount() > 0) {
            confirmDeleteAll();
            return true;
        }
        mAdapter.setDelete(true);
        Notify.show(R.string.history_delete_hint);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && mAdapter.isDelete() && mAdapter.getItemCount() > 0) {
            confirmDeleteAll();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void confirmDeleteAll() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.history_delete_all)
                .setMessage(R.string.history_delete_all_msg)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> deleteAll())
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void deleteAll() {
        AppDatabase.get().getHistoryDao().delete();
        AppDatabase.get().getTrackDao().deleteAll();
        mAdapter.setItems(new ArrayList<>(), () -> mBinding.progressLayout.showContent(true, mAdapter.getItemCount()));
        mAdapter.setDelete(false);
        RefreshEvent.history();
        com.fongmi.android.tv.utils.WebDavSync.upload(null);
        Notify.show(R.string.history_delete_all_done);
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackInvoked();
    }
}

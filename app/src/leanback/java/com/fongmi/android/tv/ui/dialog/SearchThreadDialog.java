package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogSearchThreadBinding;
import com.fongmi.android.tv.ui.adapter.SearchThreadAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;

public class SearchThreadDialog extends BaseAlertDialog implements SearchThreadAdapter.OnClickListener {

    // 固定五个选项，与需求完全一致
    private static final List<String> THREADS = Arrays.asList("12", "16", "24", "36", "48");

    private DialogSearchThreadBinding binding;
    private SearchThreadAdapter adapter;
    private int index;

    public static SearchThreadDialog create() {
        return new SearchThreadDialog();
    }

    public SearchThreadDialog index(int index) {
        this.index = index;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSearchThreadBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new SearchThreadAdapter(this, THREADS, index);
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelect()));
    }

    @Override
    public void onItemClick(String item) {
        ((Listener) requireActivity()).setSearchThreads(Integer.parseInt(item));
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.5f);
        // 弹窗背景与设置页完全一致（bg_page 星河霓虹渐变）
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(com.fongmi.android.tv.R.drawable.bg_page);
        }
    }

    public interface Listener {
        void setSearchThreads(int threads);
    }
}

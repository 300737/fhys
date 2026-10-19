package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.databinding.AdapterHeaderBinding;
import com.fongmi.android.tv.utils.ResUtil;

public class HeaderPresenter extends Presenter {

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new HeaderPresenter.ViewHolder(AdapterHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        HeaderPresenter.ViewHolder holder = (HeaderPresenter.ViewHolder) viewHolder;
        CharSequence text;
        if (object instanceof com.fongmi.android.tv.bean.RowTitle) text = ((com.fongmi.android.tv.bean.RowTitle) object).getText();
        else if (object instanceof String) text = object.toString();
        else text = ResUtil.getString((int) object);
        holder.binding.text.setText(text);
        // 标题文本为空时隐藏整行，不留空白（如已清空的「更新推荐」）
        boolean empty = text == null || text.length() == 0;
        holder.binding.text.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
        // 仅隐藏 TextView 高度不会塌缩（GONE 视图仍参与行测量），
        // 需把整行高度显式归零，否则空标题行会在 banner 与海报行之间留出一道空白。
        ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
        if (params != null) {
            params.height = empty ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.binding.getRoot().setLayoutParams(params);
        }
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterHeaderBinding binding;

        public ViewHolder(@NonNull AdapterHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
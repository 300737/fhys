package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterSearchWordBinding;

public class WordAdapter extends BaseDiffAdapter<Word.Data, WordAdapter.ViewHolder> {

    private final OnClickListener listener;

    public WordAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(String text);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchWordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    /** 分组标题前缀：带这个符号的条目渲染成「独占一行的分类标题」 */
    public static final String DIVIDER = "▎";

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Word.Data item = getItem(position);
        String text = item.getTitle();
        boolean header = text != null && text.startsWith(DIVIDER);
        if (header) {
            // 分组标题：独占一行、去掉按钮背景、不参与焦点
            holder.binding.text.setText(text.substring(DIVIDER.length()));
            holder.binding.text.setTextSize(18);
            holder.binding.text.setBackground(null);
            holder.binding.text.setFocusable(false);
            holder.binding.text.setFocusableInTouchMode(false);
            holder.binding.text.setOnClickListener(null);
        } else {
            // 普通词条：样式要还原，否则复用会带着标题的样式
            holder.binding.text.setText(text);
            holder.binding.text.setTextSize(14);
            holder.binding.text.setBackgroundResource(R.drawable.selector_item_round);
            holder.binding.text.setFocusable(true);
            holder.binding.text.setFocusableInTouchMode(true);
            holder.binding.text.setOnClickListener(v -> listener.onItemClick(text));
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchWordBinding binding;

        public ViewHolder(@NonNull AdapterSearchWordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

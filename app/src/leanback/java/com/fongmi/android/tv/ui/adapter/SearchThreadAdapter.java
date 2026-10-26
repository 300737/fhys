package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterSearchThreadBinding;

import java.util.List;

public class SearchThreadAdapter extends RecyclerView.Adapter<SearchThreadAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<String> mItems;
    private int select;

    public SearchThreadAdapter(OnClickListener listener, List<String> items, int select) {
        this.listener = listener;
        this.mItems = items;
        this.select = select;
    }

    public interface OnClickListener {
        void onItemClick(String item);
    }

    public void setSelect(int select) {
        this.select = select;
    }

    public int getSelect() {
        return select;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchThreadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = mItems.get(position);
        holder.binding.text.setText(item);
        holder.binding.text.setSelected(select == position);
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchThreadBinding binding;

        public ViewHolder(@NonNull AdapterSearchThreadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

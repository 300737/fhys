package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterVodListBinding;
import com.fongmi.android.tv.databinding.AdapterVodOvalBinding;
import com.fongmi.android.tv.databinding.AdapterVodRectBinding;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.holder.VodListHolder;
import com.fongmi.android.tv.ui.holder.VodOvalHolder;
import com.fongmi.android.tv.ui.holder.VodRectHolder;
import com.fongmi.android.tv.ui.fragment.CollectFragment;

public class VodPresenter extends Presenter {

    private final OnClickListener listener;
    private final Style style;
    private final int[] size;
    /** 海报图高度缩放系数，默认 1 不缩放。首页海报墙用 0.8：压低卡片高度，
     *  让底下的集数条和片名有足够空间，同时不至于把海报压成正方形。 */
    private float scale = 1f;

    public VodPresenter(OnClickListener listener) {
        this(listener, Style.rect());
    }

    public VodPresenter(OnClickListener listener, Style style) {
        this.listener = listener;
        this.style = style;
        this.size = Product.getSpec(style, listener instanceof CollectFragment);
    }

    public VodPresenter scale(float value) {
        this.scale = value;
        return this;
    }

    public interface OnClickListener {

        void onItemClick(Vod item);

        boolean onLongClick(Vod item);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        // 只缩海报图高度；卡片根布局仍是 wrap_content，片名/集数文字区不受挤压
        int[] spec = scale == 1f ? size : new int[]{size[0], Math.round(size[1] * scale)};
        return switch (style.getViewType()) {
            case ViewType.LIST -> new VodListHolder(AdapterVodListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
            case ViewType.OVAL -> new VodOvalHolder(AdapterVodOvalBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener).size(spec);
            default -> new VodRectHolder(AdapterVodRectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener).size(spec);
        };
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        ((BaseVodHolder) viewHolder).initView((Vod) object);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ((BaseVodHolder) viewHolder).unbind();
    }
}
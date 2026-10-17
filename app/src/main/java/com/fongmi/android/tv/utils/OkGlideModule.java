package com.fongmi.android.tv.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;
import com.github.catvod.net.OkHttp;

import java.io.InputStream;

@GlideModule
public class OkGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setLogLevel(Log.ERROR);
        // 海报源图普遍是 1200x1200 / 2260x744 这类高清大图，而 TV 上 ImageView 只有
        // 几百 dp。Glide 默认的 CENTER_INSIDE / AT_MOST 会按 View 尺寸把大图降采样后再
        // centerCrop，高分辨率被白白丢掉，表现为「轮播图 / 右侧卡图发糊」。
        // 改为 AT_LEAST：不低于 View 目标尺寸，保留源图细节，由 GPU 做缩放。
        //
        // 注意：本项目用的是 Glide 5.0.7，GlideBuilder **没有** setDownsampleStrategy，
        // 只能通过 setDefaultRequestOptions 传（v4 才有 builder 级方法）。
        // ARGB_8888 是 Glide 默认格式，显式写出以免被全局降成 RGB_565（16bit 色深会出色带）。
        builder.setDefaultRequestOptions(
                new RequestOptions()
                        .downsample(DownsampleStrategy.AT_LEAST)
                        .format(DecodeFormat.PREFER_ARGB_8888));
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, Registry registry) {
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(OkHttp.client()));
    }
}
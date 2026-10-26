package com.fongmi.android.tv.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Task {

    private static final ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
    private static final ListeningExecutorService largeExecutor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(20));
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // 搜索并发线程池：核心/最大线程数运行时可调，选中数字即真实并发上限
    private static final ThreadPoolExecutor searchPool = new ThreadPoolExecutor(24, 24, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    private static final ListeningExecutorService searchExecutor = MoreExecutors.listeningDecorator(searchPool);

    public static ListeningExecutorService executor() {
        return executor;
    }

    public static ListeningExecutorService largeExecutor() {
        return largeExecutor;
    }

    public static ListeningExecutorService searchExecutor() {
        return searchExecutor;
    }

    public static void setSearchThreads(int n) {
        if (n < 1) n = 1;
        searchPool.setCorePoolSize(n);
        searchPool.setMaximumPoolSize(n);
    }

    public static ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public static Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    public static Future<?> submitLarge(Runnable task) {
        return largeExecutor.submit(task);
    }

    public static void execute(Runnable task) {
        executor.execute(task);
    }

    public static void schedule(Runnable task, long delay, TimeUnit unit) {
        scheduler.schedule(task, delay, unit);
    }

    public static <T> FutureCallback<T> callback(Consumer<T> onSuccess) {
        return callback(onSuccess, null);
    }

    public static <T> FutureCallback<T> callback(Consumer<T> onSuccess, @Nullable Consumer<Throwable> onFailure) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onFailure(@NonNull Throwable error) {
                if (onFailure != null) onFailure.accept(error);
            }
        };
    }
}

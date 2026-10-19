package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.NoticeApi;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.HistoryRow;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.HistoryRowPresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.common.collect.Lists;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import com.fongmi.android.tv.bean.HomeBanner;
import com.fongmi.android.tv.ui.presenter.HomeBannerPresenter;
import com.fongmi.android.tv.model.LiveViewModel;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Task;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.widget.Toast;
import androidx.media3.ui.PlayerView;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.List;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.fragment.FolderFragment;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener, TypeAdapter.OnClickListener {

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mFuncAdapter;
    private ArrayObjectAdapter mAdapter;
    private HistoryPresenter mPresenter;
    private SiteViewModel mViewModel;
    private Result mResult;
    private Clock mClock;
    private LiveViewModel mLiveViewModel;
    private TypeAdapter mTypeAdapter;
    private View mOldView;
    private ServiceConnection mPlaybackServiceConnection;
    private PlaybackService mPlaybackService;
    private PlayerView mPreviewPlayerView;
    private com.fongmi.android.tv.ui.custom.LaunchSplashView launchSplash;
    private long lastBackAt;
    private Observer<Result> mObserveLiveUrl;
    private final BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateNetworkState();
        }
    };

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 从桌面再次点开（进程还活着）：每次启动都重播开机动画
        if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction())) {
            com.fongmi.android.tv.ui.custom.LaunchSplashView.reset();
            showLaunchSplash();
        }
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 冷启动开机动画：4K视界式全屏大图 + 进度状态 + 招牌金字，5 秒，任意按键跳过
        // 启动途中 Activity 可能被 recreate，shouldShow() 保证重建后自动补挂并接续剩余时间
        showLaunchSplash();
    }

    /** 挂上开机动画层（旧层先移除，避免叠加） */
    private void showLaunchSplash() {
        if (launchSplash != null && launchSplash.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) launchSplash.getParent()).removeView(launchSplash);
        }
        launchSplash = new com.fongmi.android.tv.ui.custom.LaunchSplashView(this);
        ((android.view.ViewGroup) findViewById(android.R.id.content)).addView(launchSplash);
    }

    private final List<Vod> mIqiyiRecommends = new ArrayList<>();
    /** 当前站点的豆瓣数据：按站点单独拉取，切换站点会清空重拉 */
    private final List<Vod> mDoubanItems = new ArrayList<>();
    private String mDoubanSiteKey = "";
    private ListRow mDoubanRow;

    /**
     * 读取内置的爱奇艺推荐数据（assets/iqiyi/recommends.json）。
     * 由 fetch_iqiyi.py 离线生成，用于设备端 Python 爬虫不可用时的兜底，
     * 保证 banner 的「大图轮播 + 右侧 3 张卡片」不会退化成「暂无推荐」占位。
     */
    private String loadIqiyiAsset() {
        // 用 BufferedReader 而非 readAllBytes()，避免依赖 API 33+（本工程 minSdk 24）
        try (java.io.InputStream is = getAssets().open("iqiyi/recommends.json");
             java.io.BufferedReader reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void loadIqiyiRecommends() {
        Site iqiyiSite = new Site();
        iqiyiSite.setKey("iqiyi");
        iqiyiSite.setName("爱奇艺首页");
        iqiyiSite.setApi("iqiyi.py");
        iqiyiSite.setType(3);

        String cache = com.fongmi.android.tv.setting.Setting.getIqiyiRecommends();
        if (cache.isEmpty()) cache = loadIqiyiAsset();
        if (!cache.isEmpty()) {
            try {
                com.fongmi.android.tv.bean.Result cachedResult = com.fongmi.android.tv.bean.Result.fromJson(cache);
                if (cachedResult != null && cachedResult.getList() != null && !cachedResult.getList().isEmpty()) {
                    mIqiyiRecommends.clear();
                    for (Vod vod : cachedResult.getList()) {
                        vod.setSite(iqiyiSite);
                        mIqiyiRecommends.add(vod);
                    }
                    setHomeBanner(mIqiyiRecommends);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        com.fongmi.android.tv.utils.Task.executor().submit(() -> {
            try {
                com.fongmi.android.tv.bean.Result result = com.fongmi.android.tv.api.SiteApi.homeContent(iqiyiSite);
                if (result != null && result.getList() != null && !result.getList().isEmpty()) {
                    com.fongmi.android.tv.setting.Setting.putIqiyiRecommends(result.toString());
                    List<Vod> list = result.getList();
                    mIqiyiRecommends.clear();
                    for (Vod vod : list) {
                        vod.setSite(iqiyiSite);
                        mIqiyiRecommends.add(vod);
                    }
                runOnUiThread(() -> {
                    setHomeBanner(mIqiyiRecommends);
                });
            } else {
                runOnUiThread(this::loadDoubanRecommends);
            }
        } catch (Exception e) {
            runOnUiThread(this::loadDoubanRecommends);
        }
    });
}

/**
 * 首页推荐兜底：爱奇艺首页无数据时，改用豆瓣公开榜单填充轮播。
 * 条目的 siteKey 留空，点击后会用当前站点按片名搜索详情。
 */
private void loadDoubanRecommends() {
    com.fongmi.android.tv.api.Douban.getHotList(list -> {
        if (list == null || list.isEmpty()) return;
        mIqiyiRecommends.clear();
        mIqiyiRecommends.addAll(list);
        setHomeBanner(mIqiyiRecommends);
    });
}

/**
 * 首页豆瓣行：每个站点单独拉取、单独缓存，切站点会作废重拉，绝不共用一份榜单。
 * 豆瓣请求会带上当前站点的 key，服务端（自建接口）可以按站点返回不同榜单。
 */
private void loadDoubanForSite() {
    Site home = getHome();
    if (home == null) return;
    String key = home.getKey();
    if (key.equals(mDoubanSiteKey) && !mDoubanItems.isEmpty()) {
        addDoubanRow();
        return;
    }
    mDoubanSiteKey = key;
    mDoubanItems.clear();
    removeDoubanRow();
    com.fongmi.android.tv.api.Douban.getHotList(home, list -> {
        if (list == null || list.isEmpty()) return;
        if (!key.equals(mDoubanSiteKey)) return; // 站点已切换，丢弃旧结果
        Site marker = new Site();
        marker.setKey("douban");
        marker.setName("豆瓣");
        for (Vod vod : list) vod.setSite(marker);
        mDoubanItems.clear();
        mDoubanItems.addAll(list);
        addDoubanRow();
    });
}

/** 把豆瓣行插到 banner/历史行之后、站点首页内容之前；重复调用先移除旧行再插入，幂等 */
private void addDoubanRow() {
    if (mDoubanItems.isEmpty()) return;
    if (!mDoubanSiteKey.equals(getHome() == null ? "" : getHome().getKey())) return; // 站点不匹配则不插
    int old = mDoubanRow == null ? -1 : mAdapter.indexOf(mDoubanRow);
    if (old != -1) mAdapter.removeItems(old, 1);
    ArrayObjectAdapter adapter = new ArrayObjectAdapter(new VodPresenter(this, Style.rect()).scale(0.8f));
    adapter.addAll(0, mDoubanItems);
    mDoubanRow = new ListRow(adapter);
    mAdapter.add(Math.min(getRecommendIndex(), mAdapter.size()), mDoubanRow);
}

private void removeDoubanRow() {
    if (mDoubanRow == null) return;
    int old = mAdapter.indexOf(mDoubanRow);
    if (old != -1) mAdapter.removeItems(old, 1);
    mDoubanRow = null;
}

    @Override
    protected void initView(Bundle savedInstanceState) {
        mResult = Result.empty();
        mClock = Clock.create(mBinding.clock).format("MM/dd E HH:mm");
        mBinding.progressLayout.showProgress();
        PermissionUtil.requestNotify(this);
        DLNARendererService.start(this);
        Updater.create().start(this);
        setRecyclerView();
        setTypeAdapter();
        setViewModel();
        setAdapter();
        if (com.fongmi.android.tv.setting.Setting.isSyncAutoSync()) {
            checkSync();
        } else {
            initConfig();
        }
        bindPlaybackService();
        loadIqiyiRecommends();
    }

    private boolean mSyncDone;

    private void checkSync() {
        mSyncDone = false;
        Notify.show(R.string.sync_syncing);
        com.fongmi.android.tv.utils.WebDavSync.download(new Callback() {
            @Override
            public void success() {
                if (mSyncDone) return;
                mSyncDone = true;
                Notify.show(R.string.sync_success);
                initConfig();
            }

            @Override
            public void error() {
                if (mSyncDone) return;
                mSyncDone = true;
                Notify.show(R.string.sync_fail);
                initConfig();
            }
        });
        App.post(() -> {
            if (mSyncDone) return;
            mSyncDone = true;
            Notify.show(R.string.sync_fail);
            initConfig();
        }, 3000);
    }

    @Override
    protected void initEvent() {
        mBinding.search.setOnClickListener(view -> SearchActivity.start(this));
        if (mBinding.quickSearch != null) mBinding.quickSearch.setOnClickListener(view -> SearchActivity.start(this));
        mBinding.push.setOnClickListener(view -> PushActivity.start(this));
        if (mBinding.history != null) mBinding.history.setOnClickListener(view -> HistoryActivity.start(this));
        mBinding.setting.setOnClickListener(view -> SettingActivity.start(this));
        mBinding.net.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                } catch (Exception e2) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (Exception e3) {
                        // ignore
                    }
                }
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                boolean isTop = (position == 0);
                mBinding.toolbar.setVisibility(isTop ? View.VISIBLE : View.GONE);
                mBinding.recyclerType.setVisibility((isTop && mTypeAdapter != null && mTypeAdapter.getItemCount() > 0) ? View.VISIBLE : View.GONE);
                if (mPresenter.isDelete()) setHistoryDelete(false);
            }
        });
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.recyclerType.setSelectedPosition(position + 1);
                mBinding.recyclerType.requestFocus();
            }
        });
        mBinding.recyclerType.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(HomeBanner.class, new HomeBannerPresenter(this));
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), FuncPresenter.class);
        selector.addPresenter(HistoryRow.class, new HistoryRowPresenter());
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
     }

    private void setTypeAdapter() {
        mBinding.recyclerType.setHorizontalSpacing(ResUtil.dp2px(16));
        // 行高用 WRAP_CONTENT + 固定 paddingTop/Bottom(8dp)：聚焦态 scale 1.15 倍时
        // 高度仍由 padding 决定，不会因 setRowHeight 固定值而裁掉放大后的背景。
        mBinding.recyclerType.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recyclerType.setAdapter(mTypeAdapter = new TypeAdapter(this));

        List<Class> items = new ArrayList<>();
        Class home = new Class();
        home.setTypeName(ResUtil.getString(R.string.vod_home));
        home.setTypeId("home");
        items.add(home);
        mTypeAdapter.addAll(items);
        mBinding.recyclerType.setVisibility(View.VISIBLE);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            mAdapter.remove("progress");
            int index = getRecommendIndex();
            if (mAdapter.size() > index) {
                mAdapter.removeItems(index, mAdapter.size() - index);
            }
            addDoubanRow();
            addVideo(mResult = result);
            Cache.clear().put(result);
            setTypes(result.getTypes());

            if (result != null && result.getList() != null && !result.getList().isEmpty()) {
                Result cacheResult = new Result();
                cacheResult.setList(result.getList());
                com.fongmi.android.tv.setting.Setting.putHomeRecommend(getHome().getKey(), cacheResult.toString());
            }
        });
        mLiveViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mObserveLiveUrl = this::startLivePreview;
        mLiveViewModel.url().observeForever(mObserveLiveUrl);
    }

    private void setAdapter() {
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        setHomeBanner(new ArrayList<>());
        // 不再插入空的「更新推荐」标题行：该字符串为空，行视图仍会占约 21dp 高度，
        // 在 banner 与首排海报之间留出一道空白，且与其它行距不一致。直接不插入即可。
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                showContent();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                showContent();
            }
        };
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
        checkAction(getIntent());
        setFocus();
        setNotice();
    }

    // 顶栏中间的跑马灯公告。
    // 内容优先取「繁华影视」后台「跑马灯公告」表（NoticeApi 实时拉），
    // 拉不到再回退到站点配置 JSON 的 notice 字段 —— 两条都是后台下发，代码里不写死文案。
    private void setNotice() {
        if (mBinding.notice == null) return;
        String text = VodConfig.get().getConfig() == null ? "" : VodConfig.get().getConfig().getNotice();
        applyNotice(text);
        NoticeApi.load(this::applyNotice);
    }

    private void applyNotice(String text) {
        if (mBinding.notice == null) return;
        if (TextUtils.isEmpty(text)) {
            mBinding.notice.setVisibility(View.GONE);
            return;
        }
        mBinding.notice.setVisibility(View.VISIBLE);
        // marquee 只在文字宽度超出控件宽度时才滚动。公告常常刚好填满药丸框（溢出几个像素），
        // 肉眼看起来就是不动，因此尾部补一段空白，确保一定产生横向溢出。
        // 必须等布局完成后再算宽度，否则 getWidth() 还是 0。
        mBinding.notice.post(() -> {
            if (mBinding.notice == null) return;
            int spaces = Math.max(12, (int) (mBinding.notice.getWidth() / Math.max(1f, mBinding.notice.getTextSize()) / 2));
            StringBuilder sb = new StringBuilder(text);
            for (int i = 0; i < spaces; i++) sb.append(' ');
            mBinding.notice.setText(sb);
            mBinding.notice.setSelected(true);
        });
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    private void setFocus() {
        App.post(() -> {
            // 首次打开：焦点停在「首页」标签上（而非 banner 中间的大图），
            // 方便用户直接用方向键切分类。
            if (mBinding.recyclerType.getVisibility() == View.VISIBLE
                    && mBinding.recyclerType.requestFocus()) return;
            RecyclerView.ViewHolder holder = mBinding.recycler.findViewHolderForAdapterPosition(0);
            if (holder instanceof ItemBridgeAdapter.ViewHolder) {
                View middleCard = ((ItemBridgeAdapter.ViewHolder) holder).itemView.findViewById(R.id.middleCard);
                if (middleCard != null && middleCard.isFocusable()) {
                    middleCard.requestFocus();
                    return;
                }
            }
            mBinding.recycler.requestFocus();
        }, 200);
    }

    private void getVideo() {
        mResult = Result.empty();
        int index = getRecommendIndex();
        boolean gone = mAdapter.indexOf("progress") == -1;
        boolean hasItem = gone && mAdapter.size() > index;
        if (hasItem) mAdapter.removeItems(index, mAdapter.size() - index);

        if (mTypeAdapter != null) {
            List<Class> items = new ArrayList<>();
            Class home = new Class();
            home.setTypeName(ResUtil.getString(R.string.vod_home));
            home.setTypeId("home");
            items.add(home);
            mTypeAdapter.addAll(items);
            mBinding.recyclerType.setVisibility(View.VISIBLE);
        }

        String cache = com.fongmi.android.tv.setting.Setting.getHomeRecommend(getHome().getKey());
        if (!cache.isEmpty()) {
            try {
                Result cachedResult = Result.fromJson(cache);
                if (cachedResult != null && cachedResult.getList() != null && !cachedResult.getList().isEmpty()) {
                    addVideo(mResult = cachedResult);
                    gone = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (gone) mAdapter.add("progress");
        loadDoubanForSite();
        mViewModel.homeContent();
    }

    private void addVideo(Result result) {
        List<Vod> list = result.getList();
        List<Vod> gridList = new ArrayList<>(list);
        
        setHomeBanner(mIqiyiRecommends);

        Style style = result.getStyle(getHome().getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), gridList);
        else addGrid(gridList, style);
    }

    private void addGrid(List<Vod> items, Style style) {
        List<ListRow> rows = new ArrayList<>();
        // 海报图高度统一缩到 0.8：卡片变矮后底下的集数条和片名有足够空间完整显示
        VodPresenter presenter = new VodPresenter(this, style).scale(0.8f);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
            adapter.addAll(0, part);
            rows.add(new ListRow(adapter));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private void setFunc() {
        setHomeBanner(mIqiyiRecommends);
    }

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        List<History> items = com.fongmi.android.tv.setting.Setting.isHistory() ? History.get() : new java.util.ArrayList<>();
        int historyIndex = getHistoryIndex();
        boolean exist = historyIndex != -1;
        if (renew) mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        if (items.isEmpty() && exist) {
            mAdapter.removeItems(historyIndex, 1);
        } else if (!items.isEmpty()) {
            if (!exist) {
                mAdapter.add(1, new HistoryRow(mHistoryAdapter));
            } else if (renew) {
                mAdapter.removeItems(historyIndex, 1);
                mAdapter.add(historyIndex, new HistoryRow(mHistoryAdapter));
            }
        }
        mHistoryAdapter.setItems(items, new BaseDiffCallback<History>());
    }

    private void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    private void clearHistory() {
        mAdapter.removeItems(getHistoryIndex(), 1);
        History.delete(VodConfig.getCid());
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
        com.fongmi.android.tv.utils.WebDavSync.upload(null);
    }

    private int getHistoryIndex() {
        for (int i = 0; i < mAdapter.size(); i++) {
            if (mAdapter.get(i) instanceof HistoryRow) return i;
        }
        return -1;
    }

    private int getRecommendIndex() {
        // 空标题行已移除，改为「历史行之后」即为海报插入点；无历史行时紧跟 banner。
        int historyIndex = getHistoryIndex();
        return historyIndex == -1 ? 1 : historyIndex + 1;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.history();
                RefreshEvent.home();
                break;
            case COMMON:
                setFunc();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                getVideo();
                break;
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                getVideo();
                getHistory(true);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(this, event.history().save(VodConfig.getCid()));
        } else {
            VodConfig.load(event.config(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onItemClick(Func item) {
        if (item.getResId() == R.string.home_vod) VodActivity.start(this, mResult);
        else if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_keep) KeepActivity.start(this);
        else if (item.getResId() == R.string.home_local) LocalResourceActivity.start(this);
        else if (item.getResId() == R.string.home_push) PushActivity.start(this);
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) mViewModel.action(getHome().getKey(), item.getAction());
        else if (getHome().isIndex()) CollectActivity.start(this, item.getName());
        else {
            String siteKey = TextUtils.isEmpty(item.getSiteKey()) ? getHome().getKey() : item.getSiteKey();
            if ("iqiyi".equals(siteKey) || "douban".equals(siteKey)) {
                // 爱奇艺推荐 / 豆瓣热播条目没有站点真实 id，统一按片名走搜索
                SearchActivity.start(this, item.getName());
            } else {
                VideoActivity.start(this, siteKey, item.getId(), item.getName(), item.getPic());
            }
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete());
        if (mHistoryAdapter.size() > 0) return;
        mAdapter.removeItems(getHistoryIndex(), 1);
        mPresenter.setDelete(false);
        com.fongmi.android.tv.utils.WebDavSync.upload(null);
    }

    @Override
    public boolean onLongClick() {
        if (mPresenter.isDelete()) clearHistory();
        else setHistoryDelete(true);
        return true;
    }

    @Override
    public void showDialog() {
        SiteDialog.create().show(this);
    }

    @Override
    public void onRefresh() {
        getVideo();
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        // 换站点就作废上一站的豆瓣数据，避免串站显示
        mDoubanSiteKey = "";
        mDoubanItems.clear();
        removeDoubanRow();
    }

    private void updateNetworkState() {
        int state = Util.getNetworkState();
        if (state == 1) {
            mBinding.net.setImageResource(R.drawable.ic_net_wifi);
            mBinding.net.setVisibility(View.VISIBLE);
        } else if (state == 2) {
            mBinding.net.setImageResource(R.drawable.ic_net_ethernet);
            mBinding.net.setVisibility(View.VISIBLE);
        } else {
            mBinding.net.setImageResource(R.drawable.ic_net_disconnected);
            mBinding.net.setVisibility(View.VISIBLE);
        }
    }

    private boolean isToolbarFocused() {
        View focus = getCurrentFocus();
        return focus == mBinding.search || focus == mBinding.push || focus == mBinding.history || focus == mBinding.setting || focus == mBinding.net;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (launchSplash != null && launchSplash.getParent() != null) {
            launchSplash.skip();
            launchSplash = null;
        }
        if (KeyUtil.isMenuKey(event)) showDialog();
        if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && isToolbarFocused()) {
            if (mBinding.recyclerType.getVisibility() == View.VISIBLE) {
                return mBinding.recyclerType.requestFocus();
            } else if (mBinding.recycler.getChildCount() > 0) {
                return mBinding.recycler.getChildAt(0).requestFocus();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        registerReceiver(mNetworkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        updateNetworkState();
        loadLivePreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
        unregisterReceiver(mNetworkReceiver);
        stopPreview();
    }

    private FolderFragment getFragment() {
        if (mBinding.pager.getAdapter() == null || mBinding.pager.getAdapter().getCount() == 0) return null;
        return (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    @Override
    protected void onBackInvoked() {
        FolderFragment folder = getFragment();
        if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mPresenter.isDelete()) {
            setHistoryDelete(false);
        } else if (mBinding.pager.getVisibility() == View.VISIBLE && folder != null && folder.canBack()) {
            folder.goBack();
        } else if (mBinding.recyclerType.getVisibility() == View.VISIBLE && mBinding.recyclerType.getSelectedPosition() != 0) {
            mBinding.recyclerType.setSelectedPosition(0);
            onCategoryClick(0);
        } else if (mBinding.recycler.getVisibility() == View.VISIBLE && mBinding.recycler.getSelectedPosition() != 0) {
            mBinding.recycler.scrollToPosition(0);
        } else if (SystemClock.elapsedRealtime() - lastBackAt < 2000) {
            // 首页返回 = 彻底退出：杀掉进程，保证下次打开必然冷启动走开机动画
            finishAffinity();
            App.post(() -> android.os.Process.killProcess(android.os.Process.myPid()), 400);
        } else {
            lastBackAt = SystemClock.elapsedRealtime();
            Toast.makeText(this, R.string.exit_hint, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        unbindPlaybackService();
        if (mLiveViewModel != null && mObserveLiveUrl != null) {
            mLiveViewModel.url().removeObserver(mObserveLiveUrl);
        }
        DLNARendererService.stop(this);
        LiveConfig.get().clear();
        VodConfig.get().clear();
        AppDatabase.backup();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }

    private Channel mPreviewChannel;

    private void setHomeBanner(List<Vod> recommends) {
        List<Func> funcs = new ArrayList<>();
        funcs.add(Func.create(R.string.home_keep));
        if (LiveConfig.hasUrl()) funcs.add(Func.create(R.string.home_live));
        funcs.add(Func.create(R.string.home_local));

        HomeBanner banner = new HomeBanner(funcs, recommends, LiveSetting.isPreview());
        if (mAdapter.size() > 0 && mAdapter.get(0) instanceof HomeBanner) {
            mAdapter.replace(0, banner);
        } else {
            mAdapter.add(0, banner);
        }
    }

    private void bindPlaybackService() {
        bindService(new Intent(this, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION), mPlaybackServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                mPlaybackService = ((PlaybackService.LocalBinder) binder).getService();
                if (mPreviewPlayerView != null) {
                    mPreviewPlayerView.setPlayer(mPlaybackService.player().getPlayer());
                }
                loadLivePreview();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mPlaybackService = null;
            }
        }, BIND_AUTO_CREATE);
    }

    private void unbindPlaybackService() {
        if (mPlaybackServiceConnection != null) {
            unbindService(mPlaybackServiceConnection);
            mPlaybackServiceConnection = null;
            mPlaybackService = null;
        }
    }

    private void loadLivePreview() {
        if (mPlaybackService == null || !LiveSetting.isPreview()) return;
        if (mPreviewPlayerView != null) {
            mPreviewPlayerView.setPlayer(mPlaybackService.player().getPlayer());
        }
        Task.executor().submit(() -> {
            LiveConfig.get().ensureLoaded();
            runOnUiThread(() -> {
                List<Group> groups = LiveConfig.get().getHome().getGroups();
                if (groups.isEmpty()) return;
                int[] position = LiveConfig.get().findKeepPosition(groups);
                Group group = groups.get(position[0]);
                Channel channel = group.getChannel().get(position[1]);
                mPreviewChannel = channel;
                mPlaybackService.player().getPlayer().setVolume(PlayerSetting.isHomeMute() ? 0f : 1.0f);
                mLiveViewModel.getUrl(channel);
            });
        });
    }

    private void startLivePreview(Result result) {
        if (mPlaybackService == null || !LiveSetting.isPreview() || mPreviewPlayerView == null || mPreviewChannel == null) return;
        mPlaybackService.player().getPlayer().setVolume(PlayerSetting.isHomeMute() ? 0f : 1.0f);
        MediaMetadata metadata = PlayerManager.buildMetadata(mPreviewChannel.getName(), "", "");
        mPlaybackService.player().start(com.fongmi.android.tv.player.engine.PlaySpec.from(result, result.getRealUrl(), metadata), LiveConfig.get().getHome().getTimeout());
    }

    public void attachPreviewPlayer(PlayerView view) {
        this.mPreviewPlayerView = view;
        if (mPlaybackService != null && mPreviewPlayerView != null) {
            mPreviewPlayerView.setPlayer(mPlaybackService.player().getPlayer());
            loadLivePreview();
        }
    }

    public void detachPreviewPlayer() {
        if (mPreviewPlayerView != null) {
            mPreviewPlayerView.setPlayer(null);
            mPreviewPlayerView = null;
        }
    }

    public void stopPreview() {
        if (mPreviewPlayerView != null) {
            mPreviewPlayerView.setPlayer(null);
        }
        if (mPlaybackService != null) {
            mPlaybackService.player().stop();
        }
    }

    private void setTypes(List<Class> types) {
        if (types.isEmpty()) {
            mBinding.recyclerType.setVisibility(View.GONE);
            mBinding.pager.setVisibility(View.GONE);
            mBinding.progressLayout.setVisibility(View.VISIBLE);
            return;
        }

        List<Class> items = new ArrayList<>();
        Class home = new Class();
        home.setTypeName(ResUtil.getString(R.string.vod_home));
        home.setTypeId("home");
        items.add(home);
        items.addAll(types);

        mTypeAdapter.addAll(items);
        mBinding.recyclerType.setVisibility(View.VISIBLE);
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager(), types));

        onCategoryClick(0);
    }

    private void onCategoryClick(int position) {
        if (position == 0) {
            mBinding.progressLayout.setVisibility(View.VISIBLE);
            mBinding.pager.setVisibility(View.GONE);
        } else {
            mBinding.progressLayout.setVisibility(View.GONE);
            mBinding.pager.setVisibility(View.VISIBLE);
            mBinding.pager.setCurrentItem(position - 1, false);
        }
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            int position = mBinding.recyclerType.getSelectedPosition();
            onCategoryClick(position);
        }
    };

    @Override
    public void onItemClick(Class item) {
        // 标签行第一个「首页」：点击直接弹出站点选择弹窗（可切换当前站点）
        if (item != null && "home".equals(item.getTypeId())) {
            showDialog();
            return;
        }
        int position = mTypeAdapter.indexOf(item);
        onCategoryClick(position);
    }

    @Override
    public void onRefresh(Class item) {
        if (mBinding.pager.getAdapter() != null) {
            int pagePos = mTypeAdapter.indexOf(item) - 1;
            if (pagePos >= 0 && pagePos < mBinding.pager.getAdapter().getCount()) {
                FolderFragment fragment = (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, pagePos);
                fragment.onRefresh();
            }
        }
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        private final List<Class> mTypes;

        public PageAdapter(@NonNull FragmentManager fm, List<Class> types) {
            super(fm);
            this.mTypes = types;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mTypes.get(position);
            return FolderFragment.newInstance(getHome().getKey(), type);
        }

        @Override
        public int getCount() {
            return mTypes.size();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
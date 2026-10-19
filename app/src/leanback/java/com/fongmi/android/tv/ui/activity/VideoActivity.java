package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.ArrayAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.PartAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.dialog.ContentDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PartUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class VideoActivity extends PlaybackActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, ArrayAdapter.OnClickListener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, Clock.Callback {

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private boolean mInit = true;
    private boolean mPlaybackStarted;

    // 片尾自动跳集的三重保护。onTimeChanged 由时钟每秒回调，而切集链路
    // （onRefresh -> getPlayer -> 网络解析 -> onPrepare）是异步的，缺了这些保护
    // 就会出现"一秒跳一集"的连环跳：同一次片尾被反复判定，新集刚起播又被判为播完。
    private boolean mEndingSkipFired;      // 一次性闭锁：本集只允许触发一次
    private boolean mEndingSkipArmed;      // 保险栓：必须先在「片尾区之前」见过一次进度才允许跳
    private long mLastSkipAtMs;            // 防抖时间戳
    private static final long SKIP_DEBOUNCE_MS = 3000L;
    private static final long SKIP_ARM_MARGIN_MS = 3000L;  // 退出片尾区要留出这么多余量才算真的播过了
    private static final String TAG_SKIP = "SkipED";   // 片尾跳集诊断日志
    private static final String TAG_DANMAKU = "DanmakuED";  // 弹幕全链路诊断日志
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ArrayAdapter mArrayAdapter;
    private ParseAdapter mParseAdapter;
    private QuickAdapter mQuickAdapter;
    private FlagAdapter mFlagAdapter;
    private PartAdapter mPartAdapter;
    private CustomKeyDownVod mKeyDown;
    private SiteViewModel mViewModel;
    private List<String> mBroken;
    private History mHistory;
    private boolean fullscreen;
    private boolean initAuto;
    private boolean autoMode;
    private boolean useParse;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private long mLastBackTime = 0;
    private final Runnable mHideInfoRunnable = this::hideInfoLayout;

    public static void push(FragmentActivity activity, String text) {
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name);
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, false, true);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true, false);
    }

    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    private boolean isCast() {
        return getIntent().getBooleanExtra("cast", false);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Flag getFlag() {
        return mFlagAdapter.getActivated();
    }

    private Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : PlayerSetting.getScale();
    }

    private boolean isReplay() {
        return Setting.getReset() == 1;
    }

    private boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    private final Runnable mLrcRunnable = this::updateLrc;

    private void checkArtworkScale() {
        if (isFinishing() || isDestroyed() || mBinding == null) return;
        android.widget.ImageView artworkView = mBinding.exo.findViewById(androidx.media3.ui.R.id.exo_artwork);
        if (artworkView != null && artworkView.getScaleType() != android.widget.ImageView.ScaleType.CENTER_CROP) {
            artworkView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        }
    }

    private void updateLrc() {
        if (isFinishing() || isDestroyed() || mBinding == null) return;
        checkArtworkScale();
        if (mBinding.lrcView != null && player() != null) {
            mBinding.lrcView.updateTime(player().getPosition());
        }
        App.post(mLrcRunnable, 100);
    }

    private void updateLrcButtonText() {
        int mode = Setting.getLrcMode();
        String text = mode == 0 ? "歌词：双行" : "歌词：多行";
        mBinding.control.action.lrc.setText(text);
    }

    private void onLrcModeToggle() {
        int nextMode = Setting.getLrcMode() == 0 ? 1 : 0;
        Setting.putLrcMode(nextMode);
        mBinding.lrcView.setLrcMode(nextMode);
        updateLrcButtonText();
        Notify.show(nextMode == 0 ? "已切换到双行歌词模式" : "已切换到多行歌词模式");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected PlayerView getExoView() {
        return mBinding.exo;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        player().setDanmakuController(mBinding.exo.getDanmakuController());
        player().setDanmakuEnabled(DanmakuSetting.isShow());
        checkId();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String oldId = getId();
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(oldId)) return;
        getIntent().putExtras(intent);
        saveHistory();
        mInit = true;
        mPlaybackStarted = false;
        mBinding.backgroundCover.setImageDrawable(null);
        mBinding.backgroundCoverBlur.setImageDrawable(null);
        if (!PlayerSetting.isAutoplay()) {
            mBinding.backgroundCoverLayout.setVisibility(View.VISIBLE);
            mBinding.video.setVisibility(View.GONE);
        }
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        mBinding.video.setForeground(null);
        mFrameParams = mBinding.video.getLayoutParams();
        mClock = Clock.create(mBinding.widget.clock);
        mKeyDown = CustomKeyDownVod.create(this);
        mKeyDown.setFull(true);
        setFullscreen(true);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mBroken = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showEmpty;
        if (!PlayerSetting.isAutoplay()) {
            mBinding.backgroundCoverLayout.setVisibility(View.VISIBLE);
            mBinding.video.setVisibility(View.GONE);
        }
        setRecyclerView();
        setVideoView();
        setViewModel();
        checkCast();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change1.setOnClickListener(view -> onChange());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.lrc.setOnClickListener(view -> onLrcModeToggle());
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.action.speed.setDownListener(this::onSpeedSub);
        mBinding.control.action.ending.setUpListener(this::onEndingAdd);
        mBinding.control.action.ending.setDownListener(this::onEndingSub);
        mBinding.control.action.opening.setUpListener(this::onOpeningAdd);
        mBinding.control.action.opening.setDownListener(this::onOpeningSub);
        mBinding.control.action.text.setUpListener(this::onSubtitleClick);
        mBinding.control.action.text.setDownListener(this::onSubtitleClick);
        mBinding.control.action.next.setOnClickListener(view -> checkNext());
        mBinding.control.action.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.title.setOnClickListener(view -> onTitle());
        mBinding.control.action.player.setOnClickListener(view -> onChoose());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.change2.setOnClickListener(view -> onChange());
        mBinding.control.action.danmakuShow.setOnClickListener(view -> onDanmakuShow());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mFlagAdapter.getItemCount() > 0) onItemClick(mFlagAdapter.get(position));
            }
        });
        mBinding.episode.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null && mBinding.video != mFocus1) mFocus1 = child.itemView;
            }
        });
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mEpisodeAdapter.getItemCount() > 20 && position > 1) mBinding.episode.setSelectedPosition((position - 2) * 20);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this));
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(mArrayAdapter = new ArrayAdapter(this));
        mBinding.part.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.part.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.part.setAdapter(mPartAdapter = new PartAdapter(item -> initSearch(item, false)));
        mBinding.quick.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        mBinding.control.parse.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.control.parse.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(this));
        mParseAdapter.addAll(VodConfig.get().getParses());
    }

    private void setVideoView() {
        mBinding.control.action.danmakuShow.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        checkDanmakuStatus();
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
    }

    private void setScale(int scale) {
        mHistory.setScale(scale);
        mBinding.exo.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observeForever(mObserveDetail);
        mViewModel.getPlayer().observeForever(mObservePlayer);
        mViewModel.getSearch().observeForever(mObserveSearch);
    }

    private void checkCast() {
        if (isCast() && !isFullscreen()) enterFullscreen();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        if (getId().startsWith("push://")) getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    private void getDetail() {
        mViewModel.detailContent(getKey(), getId());
    }

    private void getDetail(Vod item) {
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        player().reset();
        player().stop();
        saveHistory();
        mInit = true;
        mPlaybackStarted = false;
        mBinding.backgroundCover.setImageDrawable(null);
        mBinding.backgroundCoverBlur.setImageDrawable(null);
        if (!PlayerSetting.isAutoplay()) {
            mBinding.backgroundCoverLayout.setVisibility(View.VISIBLE);
            mBinding.video.setVisibility(View.GONE);
        }
        getDetail();
    }

    private void setDetail(Result result) {
        if (result.getList().isEmpty()) setEmpty(result.hasMsg());
        else setDetail(result.getVod());
        Notify.show(result.getMsg());
    }

    private void setEmpty(boolean finish) {
        if (isFromCollect() || finish) {
            finish();
        } else if (getName().isEmpty()) {
            showEmpty();
        } else {
            mBinding.name.setText(getName());
            App.post(mR4, 10000);
            checkSearch(false);
        }
    }

    private void showEmpty() {
        mBinding.progressLayout.showEmpty();
    }

    private void setDetail(Vod item) {
        item.checkPic(getPic());
        item.checkName(getName());
        mBinding.progressLayout.showContent();
        setName(item);
        if (mBinding.poster != null) ImgUtil.load(item.getName(), item.getPic(), mBinding.poster);
        mFlagAdapter.addAll(item.getFlags());
        App.removeCallbacks(mR4);
        checkHistory(item);
        fetchDoubanBanner(item);
        checkFlag(item);
        checkKeepImg();
        setText(item);
        updateKeep();
        showInfoLayout();
        mInit = false;
    }

    private void setName(Vod item) {
        String name = item.getName();
        String year = item.getYear();
        if (!TextUtils.isEmpty(year) && !name.contains(year)) {
            String yearText = " (" + year + ")";
            SpannableStringBuilder builder = new SpannableStringBuilder(name).append(yearText);
            builder.setSpan(new RelativeSizeSpan(0.75f), name.length(), name.length() + yearText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            mBinding.name.setText(builder);
        } else {
            mBinding.name.setText(name);
        }
        mBinding.name.setSelected(true);
    }

    private void setText(Vod item) {
        mBinding.content.setTag(item.getContent());
        mBinding.content.setText(item.getContent());
        setText(mBinding.type, R.string.detail_type, item.getTypeName());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        mBinding.change1.setText(TextUtils.isEmpty(getSite().getName()) ? getString(R.string.play_change) : getSite().getName());
        /*
        setText(mBinding.year, R.string.detail_year, item.getYear());
        setText(mBinding.area, R.string.detail_area, item.getArea());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.remark, 0, item.getRemarks());
        */
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (view.getParent() instanceof View) {
            View parent = (View) view.getParent();
            if (parent.getId() == R.id.typeLayout || parent.getId() == R.id.actorLayout) {
                parent.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            }
        }
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                VodActivity.start(getActivity(), getKey(), result);
                setRedirect(true);
            }
        };
    }

    private void getPlayer(Flag flag, Episode episode) {
        mBinding.widget.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());
        mBinding.widget.title.setSelected(true);
        updateHistory(episode);
        showProgress();
    }

    private void setPlayer(Result result) {
        if (isFinishing() || isDestroyed()) return;
        mQualityAdapter.addAll(result);
        setUseParse(result.shouldUseParse());
        setQualityVisible(result.getUrl().isMulti());
        result.getUrl().set(mQualityAdapter.getPosition());
        if (result.hasArtwork()) setArtwork(result.getArtwork());
        if (result.hasDesc()) {
            mBinding.content.setTag(result.getDesc());
            mBinding.content.setText(result.getDesc());
        }
        if (result.hasPosition()) mHistory.setPosition(result.getPosition());
        mBinding.control.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);

        mBinding.lrcView.c();
        String lrc = result.getLrc();
        boolean hasLrc = !TextUtils.isEmpty(lrc);
        mBinding.control.action.lrc.setVisibility(hasLrc ? View.VISIBLE : View.GONE);
        if (hasLrc) {
            updateLrcButtonText();
            if (lrc.startsWith("http")) {
                com.github.catvod.net.OkHttp.newCall(lrc).enqueue(new okhttp3.Callback() {
                    @Override
                    public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                    }

                    @Override
                    public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws java.io.IOException {
                        if (response.body() != null) {
                            String lrcText = response.body().string();
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed() || mBinding == null) return;
                                mBinding.lrcView.setData(lrcText);
                            });
                        }
                    }
                });
            } else {
                mBinding.lrcView.setData(lrc);
            }
        }

        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        if (DanmakuApi.canSearch()) {
            DanmakuApi.search(mHistory.getVodName(), getEpisode().getName(), result.getRealUrl(), danmaku -> {
                if (DanmakuSetting.isSpiderFirst() && !result.getDanmaku().isEmpty()) player().addDanmaku(danmaku);
                else player().setDanmaku(danmaku);
            });
        } else {
            Log.d(TAG_DANMAKU, "不发弹幕请求: isLoad=" + DanmakuSetting.isLoad() + " isAuto=" + DanmakuSetting.isAuto() + " api=" + DanmakuSetting.getEffectiveApiUrl());
        }
    }

    @Override
    public void onItemClick(Flag item) {
        if (mFlagAdapter.getItemCount() == 0 || item.isSelected()) return;
        mFlagAdapter.setSelected(item);
        mBinding.flag.setSelectedPosition(mFlagAdapter.indexOf(item));
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        setEpisodeAdapter(item.getEpisodes());
        setQualityVisible(false);
        seamless(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mEpisodeAdapter.addAll(items);
        setArrayAdapter(items.size());
        setR2Callback();
    }

    private void seamless(Flag flag) {
        Episode episode = flag.find(mHistory.getVodRemarks(), getMark().isEmpty());
        setQualityVisible(episode != null && episode.isSelected() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isSelected()) return;
        mHistory.setVodRemarks(episode.getName());
        if (mInit && !PlayerSetting.isAutoplay()) {
            mFlagAdapter.toggle(episode);
            mBinding.episode.setSelectedPosition(mEpisodeAdapter.getPosition());
            mBinding.backgroundCoverLayout.setVisibility(View.VISIBLE);
            mBinding.video.setVisibility(View.GONE);
            return;
        }
        onItemClick(episode);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        // 换集：清掉上一集的片尾闭锁与防抖基准，并撤掉保险栓，保证新集从头判定
        mEndingSkipFired = true;
        mEndingSkipArmed = false;
        mLastSkipAtMs = SystemClock.elapsedRealtime();
        Log.d(TAG_SKIP, "onItemClick: LOCK fired=true armed=false ep=" + item.getName());
        mFlagAdapter.toggle(item);
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        mBinding.episode.setSelectedPosition(mEpisodeAdapter.getPosition());
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        mBinding.backgroundCoverLayout.setVisibility(View.GONE);
        mBinding.video.setVisibility(View.VISIBLE);
        onRefresh();
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        setR2Callback();
    }

    @Override
    public void onItemClick(Result result) {
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) mBinding.episode.setSelectedPosition(mEpisodeAdapter.getPosition());
    }

    @Override
    public void onItemClick(Parse item) {
        setParse(item);
        onRefresh();
    }

    private void setParse(Parse item) {
        VodConfig.get().setParse(item);
        notifyItemChanged(mBinding.control.parse, mParseAdapter);
    }

    private void setArrayAdapter(int size) {
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        mBinding.array.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mHistory.isRevSort()) for (int i = size; i > 0; i -= 20) items.add(i + "-" + Math.max(i - 19, 1));
        else for (int i = 0; i < size; i += 20) items.add((i + 1) + "-" + Math.min(i + 20, size));
        mArrayAdapter.addAll(items);
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episode, R.id.array, R.id.part, R.id.quick);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episode, R.id.array, R.id.part, R.id.quick);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        mPartAdapter.setNextFocusUp(findFocusUp(4));
        mEpisodeAdapter.setNextFocusUp(findFocusUp(2));
        mFlagAdapter.setNextFocusDown(findFocusDown(0));
        mEpisodeAdapter.setNextFocusDown(findFocusDown(2));
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        notifyItemChanged(mBinding.part, mPartAdapter);
        notifyItemChanged(mBinding.flag, mFlagAdapter);
    }

    @Override
    public void onRevSort() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        mHistory.setRevPlay(!mHistory.isRevPlay());
        view.setText(mHistory.getRevPlayText());
        Notify.show(mHistory.getRevPlayHint());
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        mKeyDown.setFull(true);
        setFullscreen(true);
    }

    private void exitFullscreen() {
    }

    private void showInfoLayout() {
        mBinding.infoLayout.setVisibility(View.VISIBLE);
        getFocus1().requestFocus();
        resetInfoTimer();
    }

    private void hideInfoLayout() {
        mBinding.infoLayout.setVisibility(View.GONE);
        mBinding.video.requestFocus();
        cancelInfoTimer();
    }

    private void resetInfoTimer() {
        App.removeCallbacks(mHideInfoRunnable);
        if (mBinding.infoLayout.getVisibility() == View.VISIBLE) {
            if (PlayerSetting.isAutoplay() || mPlaybackStarted) {
                App.post(mHideInfoRunnable, 5000);
            }
        }
    }

    private void cancelInfoTimer() {
        App.removeCallbacks(mHideInfoRunnable);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        resetInfoTimer();
    }

    private void onContent() {
        if (mBinding.content.getTag() == null) return;
        ContentDialog.create().name(mBinding.name.getText().toString()).pic(getPic()).content(mBinding.content.getTag().toString()).show(this);
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
        com.fongmi.android.tv.utils.WebDavSync.upload(null);
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        checkSearch(true);
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        if (mHistory.isRevPlay()) onPrev(notify);
        else onNext(notify);
    }

    private void checkPrev() {
        if (mHistory.isRevPlay()) onNext(true);
        else onPrev(true);
    }

    private void onNext(boolean notify) {
        Episode item = mEpisodeAdapter.getNext();
        Log.d(TAG_SKIP, "onNext: cur=" + (getEpisode() == null ? "" : getEpisode().getName()) + " next=" + (item == null ? "null" : item.getName()) + " nextSelected=" + (item != null && item.isSelected()));
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
    }

    private void onPrev(boolean notify) {
        Episode item = mEpisodeAdapter.getPrev();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        setScale(index == array.length - 1 ? 0 : ++index);
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(player().addSpeed());
        mHistory.setSpeed(player().getSpeed());
    }

    private void onSpeedAdd() {
        mBinding.control.action.speed.setText(player().addSpeed(0.25f));
        mHistory.setSpeed(player().getSpeed());
    }

    private void onSpeedSub() {
        mBinding.control.action.speed.setText(player().subSpeed(0.25f));
        mHistory.setSpeed(player().getSpeed());
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(player().toggleSpeed());
        mHistory.setSpeed(player().getSpeed());
        return true;
    }

    private void onReset() {
        if (isReplay()) onReplay();
        else onRefresh();
    }

    private void onReplay() {
        mHistory.setPosition(C.TIME_UNSET);
        if (player().isEmpty()) onRefresh();
        else player().setMediaItem();
    }

    private void onRefresh() {
        mPlaybackStarted = true;
        player().stop();
        player().clear();
        mClock.setCallback(null);
        if (mFlagAdapter.getItemCount() == 0) return;
        if (mEpisodeAdapter.getItemCount() == 0) return;
        getPlayer(getFlag(), getEpisode());
        // 必须在 getPlayer 之后落库：updateHistory 在那里才把集名/地址/进度改成新集的。
        // 之前先 saveHistory 会把"上一集的集名 + 新集的进度"写进历史，
        // 下次进来 seamless() 按集名找，就会强行跳回上一集（点第1集却播第4集）。
        saveHistory();
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
    }

    private void onOpeningAdd() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) + 1000));
    }

    private void onOpeningSub() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) - 1000));
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        Log.d(TAG_SKIP, "setOpening=" + opening);
        mHistory.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (!player().canSetEnding(position, duration)) return;
        setEnding(duration - position);
        // 用户亲手打的片尾点，本集立刻获得跳集资格（打个点的当下就应当跳下一集）
        mEndingSkipFired = false;
        mEndingSkipArmed = true;
        Log.d(TAG_SKIP, "ARMED by user marking ending (pos=" + position + " dur=" + duration + ")");
    }

    private void onEndingAdd() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) + 1000));
    }

    private void onEndingSub() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) - 1000));
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        Log.d(TAG_SKIP, "setEnding=" + ending + " (pos=" + player().getPosition() + " dur=" + player().getDuration() + ")");
        mHistory.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
    }

    private void onChoose() {
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.widget.title.getText());
        setRedirect(true);
    }

    private void onDecode() {
        mClock.setCallback(null);
        player().toggleDecode();
        setDecode();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).show(this);
        hideControl();
    }

    private void onTitle() {
        TitleDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onDanmakuShow() {
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        checkDanmakuStatus();
        showDanmaku();
    }

    private void checkDanmakuStatus() {
        String status = ResUtil.getString(DanmakuSetting.isShow() ? R.string.setting_on : R.string.setting_off);
        mBinding.control.action.danmakuShow.setText(ResUtil.getString(R.string.danmaku_status, status));
    }

    private void showDanmaku() {
        player().setDanmakuEnabled(DanmakuSetting.isShow());
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl(getFocus2());
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR3, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR3);
        Traffic.reset();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showInfo() {
        mBinding.widget.top.setVisibility(View.VISIBLE);
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(0));
        updateAlwaysTime();
        updateAlwaysProgress();
    }

    private void hideInfo() {
        mBinding.widget.top.setVisibility(View.GONE);
        mBinding.widget.center.setVisibility(View.GONE);
        updateAlwaysTime();
        updateAlwaysProgress();
    }

    private void showControl(View view) {
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        view.requestFocus();
        setR1Callback();
        updateAlwaysTime();
        updateAlwaysProgress();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR1);
        updateAlwaysTime();
        updateAlwaysProgress();
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        hideInfo();
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR3, 1000);
    }

    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            if (mBinding.alwaysTimeText != null && isVisible(mBinding.alwaysTimeText)) {
                mBinding.alwaysTimeText.setText(java.time.LocalTime.now().format(com.fongmi.android.tv.utils.Formatters.TIME));
                App.post(this, 1000);
            }
        }
    };

    private void updateAlwaysTime() {
        if (mBinding.alwaysTimeText == null) return;
        boolean show = com.fongmi.android.tv.setting.Setting.isAlwaysTime()
                && !isVisible(mBinding.control.getRoot())
                && !isVisible(mBinding.widget.top)
                && !isVisible(mBinding.widget.center);
        if (show) {
            mBinding.alwaysTimeText.setVisibility(View.VISIBLE);
            mBinding.alwaysTimeText.setText(java.time.LocalTime.now().format(com.fongmi.android.tv.utils.Formatters.TIME));
            App.removeCallbacks(mClockRunnable);
            App.post(mClockRunnable, 1000);
        } else {
            mBinding.alwaysTimeText.setVisibility(View.GONE);
            App.removeCallbacks(mClockRunnable);
        }
    }

    private void updateAlwaysProgress() {
        if (mBinding.alwaysProgressText == null) return;
        boolean show = com.fongmi.android.tv.setting.Setting.isAlwaysProgress()
                && !isVisible(mBinding.control.getRoot())
                && !isVisible(mBinding.widget.top)
                && !isVisible(mBinding.widget.center);
        if (show && player().getDuration() > 0) {
            mBinding.alwaysProgressText.setVisibility(View.VISIBLE);
            long position = player().getPosition();
            long duration = player().getDuration();
            long remaining = duration - position;
            if (remaining < 0) remaining = 0;

            String progress = player().getPositionTime(0);
            String total = player().getDurationTime();

            long finishTime = System.currentTimeMillis() + remaining;
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
            String finish = sdf.format(new java.util.Date(finishTime));

            mBinding.alwaysProgressText.setText(getString(R.string.always_progress_format, progress, total, finish));
        } else {
            mBinding.alwaysProgressText.setVisibility(View.GONE);
        }
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback() {
        App.post(mR2, 500);
    }

    private void setArtwork(String url) {
        mHistory.setVodPic(url);
        setArtwork();
    }

    private void setArtwork() {
        ImgUtil.load(this, mHistory.getVodPic(), new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.exo.setDefaultArtwork(resource);
                checkArtworkScale();
                mBinding.exo.setDefaultArtwork(resource);
                checkArtworkScale();
                boolean vertical = resource.getIntrinsicHeight() > resource.getIntrinsicWidth();
                RelativeLayout.LayoutParams maskParams = (RelativeLayout.LayoutParams) mBinding.backgroundCoverMask.getLayoutParams();
                FrameLayout.LayoutParams coverParams = (FrameLayout.LayoutParams) mBinding.backgroundCover.getLayoutParams();
                if (vertical) {
                    maskParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    maskParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    maskParams.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE);
                    maskParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
                    mBinding.backgroundCoverMask.setLayoutParams(maskParams);

                    coverParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    coverParams.height = (int) (com.fongmi.android.tv.utils.ResUtil.getScreenHeight() * 1.5f);
                    coverParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                    mBinding.backgroundCover.setLayoutParams(coverParams);
                    mBinding.backgroundCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
                } else {
                    maskParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    maskParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    mBinding.backgroundCoverMask.setLayoutParams(maskParams);

                    coverParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    coverParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    coverParams.gravity = Gravity.NO_GRAVITY;
                    mBinding.backgroundCover.setLayoutParams(coverParams);
                    mBinding.backgroundCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }
                mBinding.backgroundCover.setImageDrawable(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                mBinding.exo.setDefaultArtwork(errorDrawable);
                mBinding.backgroundCover.setImageDrawable(errorDrawable);
            }
        });
        if (!PlayerSetting.isAutoplay() && !mPlaybackStarted) {
            ImgUtil.load(mHistory.getVodPic(), new CustomTarget<>() {
                @Override
                public void onResourceReady(@NonNull android.graphics.Bitmap resource, @Nullable Transition<? super android.graphics.Bitmap> transition) {
                    android.graphics.Bitmap blurred = blur(VideoActivity.this, resource, 25f);
                    mBinding.backgroundCoverBlur.setImageBitmap(blurred);
                }
            });
        }
    }

    private void fetchDoubanBanner(Vod item) {
        if (item == null) return;
        // 记录发起请求时的条目身份：豆瓣图是异步回调，若期间用户已切到别的站点/条目，
        // 迟到的回调会污染共享的 mHistory.vodPic（表现为「只有第一个站点能出图，后面全不出」）。
        final String reqSite = item.getSiteKey();
        final String reqId = item.getId();
        com.fongmi.android.tv.api.Douban.getBanner(item.getName(), item.getYear(), getSite(), bannerUrl -> {
            if (android.text.TextUtils.isEmpty(bannerUrl) || isFinishing()) return;
            // 已经不是同一个条目了（mHistory 是 History 类型），丢弃这次迟到的结果
            History cur = mHistory;
            if (cur == null || !reqId.equals(cur.getVodId()) || !reqSite.equals(cur.getSiteKey())) return;
            setArtwork(bannerUrl);
        });
    }

    private android.graphics.Bitmap blur(android.content.Context context, android.graphics.Bitmap image, float radius) {
        if (image == null) return null;
        try {
            android.graphics.Bitmap input = image.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
            android.graphics.Bitmap output = android.graphics.Bitmap.createBitmap(input.getWidth(), input.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            android.renderscript.RenderScript rs = android.renderscript.RenderScript.create(context);
            android.renderscript.ScriptIntrinsicBlur theIntrinsic = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs));
            android.renderscript.Allocation tmpIn = android.renderscript.Allocation.createFromBitmap(rs, input);
            android.renderscript.Allocation tmpOut = android.renderscript.Allocation.createFromBitmap(rs, output);
            theIntrinsic.setRadius(radius);
            theIntrinsic.setInput(tmpIn);
            theIntrinsic.forEach(tmpOut);
            tmpOut.copyTo(output);
            rs.destroy();
            return output;
        } catch (Throwable e) {
            e.printStackTrace();
            return image;
        }
    }


    private void setPartAdapter() {
        mPartAdapter.addAll(PartUtil.split(mHistory.getVodName()));
        mBinding.part.setVisibility(View.GONE);
        setR2Callback();
    }

    private void checkFlag(Vod item) {
        boolean empty = item.getFlags().isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            startFlow();
        } else {
            onItemClick(mHistory.getFlag());
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
    }

    private void checkHistory(Vod item) {
        mHistory = History.find(getHistoryKey());
        mHistory = mHistory == null ? createHistory(item) : mHistory;
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        if (Setting.isIncognito() && mHistory.getKey().equals(getHistoryKey())) mHistory.delete();
        mBinding.control.action.opening.setText(mHistory.getOpening() <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
        mBinding.control.action.ending.setText(mHistory.getEnding() <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
        mBinding.control.action.speed.setText(player().setSpeed(mHistory.getSpeed()));
        mHistory.setVodName(item.getName());
        setArtwork(!item.getBanner().isEmpty() ? item.getBanner() : item.getPic());
        setScale(getScale());
        setPartAdapter();
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getName());
        history.findEpisode(item.getFlags());
        return history;
    }

    private void saveHistory() {
        saveHistory(false);
    }

    private void saveHistory(boolean exit) {
        if (mHistory != null && mHistory.canSave() && !Setting.isIncognito()) Task.execute(() -> {
            mHistory.merge().save();
            if (exit) {
                RefreshEvent.history();
                com.fongmi.android.tv.utils.WebDavSync.upload(null);
            }
        });
    }

    private void syncHistory(boolean force) {
        if (mHistory != null && !Setting.isIncognito()) Task.execute(() -> {
            mHistory.save();
            if (force || Setting.getSyncInterval() > 0) {
                com.fongmi.android.tv.utils.WebDavSync.upload(null);
            }
        });
    }

    private void updateHistory(Episode item) {
        mHistory.setPosition(item.matchesName(mHistory.getEpisode()) ? mHistory.getPosition() : C.TIME_UNSET);
        mHistory.setVodFlag(getFlag().getFlag());
        mHistory.setVodRemarks(item.getName());
        mHistory.setEpisodeUrl(item.getUrl());
    }

    private void checkKeepImg() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(Keep.find(getHistoryKey()) == null ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(mHistory.getVodPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            keep.setVodPic(mHistory.getVodPic());
            keep.save();
        }
    }

    private void updateVod(Vod item) {
        boolean id = !item.getId().isEmpty();
        boolean pic = !item.getPic().isEmpty();
        boolean name = !item.getName().isEmpty();
        if (id) getIntent().putExtra("id", item.getId());
        if (id) mHistory.replace(getHistoryKey());
        if (name) mHistory.setVodName(item.getName());
        if (name) setName(item);
        if (name) mBinding.widget.title.setText(item.getName());
        updateFlag(getFlag(), item.getFlags());
        if (pic) setArtwork(!item.getBanner().isEmpty() ? item.getBanner() : item.getPic());
        if (pic || name) setMetadata();
        if (pic || name) syncHistory(true);
        if (pic || name) updateKeep();
        if (id) updateNavigationKey();
        if (name) setPartAdapter();
        setText(item);
    }

    private void updateFlag(Flag activated, List<Flag> items) {
        items.forEach(item -> mFlagAdapter.getItems().stream()
                .filter(item::equals).findFirst().ifPresentOrElse(target -> {
                    target.mergeEpisodes(item.getEpisodes(), mHistory.isRevSort());
                    if (target.equals(activated)) setEpisodeAdapter(target.getEpisodes());
                }, () -> mFlagAdapter.add(item)));
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finish();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onPrepare() {
        setDecode();
        setPosition();
        // 新的一集已经准备好，解锁片尾跳集（每集仅一次）。
        // 保险栓先撤掉：此刻播放器常常只拿到一个假的时长（实测起播瞬间会报 205000），
        // 而 setPosition 又把进度 seek 到了片头结尾处，若不撤栓就会被当场判成「播完」。
        mEndingSkipFired = false;
        mEndingSkipArmed = false;
        mLastSkipAtMs = SystemClock.elapsedRealtime();
        Log.d(TAG_SKIP, "onPrepare: UNLOCK fired=false armed=false (ep=" + (getEpisode() == null ? "" : getEpisode().getName()) + ")");
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
        mClock.setCallback(this);
    }

    @Override
    protected void onTitlesChanged() {
        setTitleVisible();
    }

    @Override
    protected void onError(String msg) {
        Track.delete(player().getKey());
        mClock.setCallback(null);
        player().resetTrack();
        player().reset();
        player().stop();
        showError(msg);
        startFlow();
    }

    @Override
    protected void onReclaim() {
        Result result = mViewModel.getPlayer().getValue();
        if (result != null) setPlayer(result);
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                hideProgress();
                player().reset();
                break;
            case Player.STATE_ENDED:
                checkEnded(true);
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying) {
            hideCenter();
            App.post(mLrcRunnable);
        } else if (isPaused()) {
            if (isFullscreen()) showInfo();
            else hideInfo();
            App.removeCallbacks(mLrcRunnable);
        }
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).show(this);
        App.post(this::hideControl, 100);
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isOwner()) return;
        if (!player().isPlaying()) return;
        long position, duration;
        mHistory.setCreateTime(time);
        mHistory.setPosition(position = player().getPosition());
        mHistory.setDuration(duration = player().getDuration());
        if (mHistory.canSave() && mHistory.canSync()) syncHistory(false);
        updateEndingSkipArmed(position, duration);
        boolean skip = shouldSkipEnding(position, duration);
        Log.d(TAG_SKIP, "tick pos=" + position + " dur=" + duration + " op=" + mHistory.getOpening() + " ed=" + mHistory.getEnding() + " fired=" + mEndingSkipFired + " armed=" + mEndingSkipArmed + " debounce=" + (SystemClock.elapsedRealtime() - mLastSkipAtMs) + " skip=" + skip);
        if (skip) {
            mEndingSkipFired = true;
            mEndingSkipArmed = false;
            mLastSkipAtMs = SystemClock.elapsedRealtime();
            Log.d(TAG_SKIP, ">>> TRIGGER ending-skip, call checkEnded(false)");
            checkEnded(false);
        }
        updateAlwaysProgress();
    }

    /**
     * 保险栓：只有亲眼见过「进度还在片尾区之前」，才允许这一集触发跳集。
     * 切集瞬间播放器会先报一个假时长（实测起播瞬间 dur=205000，几秒后才变真实值），
     * 而 setPosition 又会把进度 seek 到片头结尾处（约 100s），
     * 于是「片尾 + 进度 >= 时长」在起播第一秒就成立 —— 这才是连环跳的真正源头。
     * 有了保险栓，新集必须先老老实实播一段、进度落到片尾区之前，才具备跳集资格。
     */
    private void updateEndingSkipArmed(long position, long duration) {
        if (mEndingSkipArmed) return;
        long ending = mHistory.getEnding();
        if (ending <= 0 || duration <= 0) return;
        if (duration <= position) return;                                       // 假时长：比当前进度还短
        if (duration <= Math.max(0, mHistory.getOpening()) + ending) return;    // 片头+片尾比整集还长，时长不可信
        if (position + ending + SKIP_ARM_MARGIN_MS >= duration) return;         // 还在片尾区里，不算数
        mEndingSkipArmed = true;
        Log.d(TAG_SKIP, "ARMED (pos=" + position + " dur=" + duration + " ed=" + ending + ")");
    }

    /**
     * 片尾跳集的判定：条件之外还要过四道闸，缺一就会出现连环跳。
     * 1. mEndingSkipFired —— 一次性闭锁，本集跳过就不再触发，等新集 onPrepare 才解锁
     * 2. mEndingSkipArmed —— 保险栓，本集必须先在片尾区之前播过，杜绝起播即跳
     * 3. 防抖窗口         —— 两次自动跳集至少间隔 SKIP_DEBOUNCE_MS
     * 4. position > opening —— 必须真的播过了才可能到片尾，防止新集刚起播（进度被
     *    seek 到片尾区）时立刻被误判成播完
     */
    private boolean shouldSkipEnding(long position, long duration) {
        if (mEndingSkipFired) return false;
        if (!mEndingSkipArmed) return false;
        long ending = mHistory.getEnding();
        if (ending <= 0 || duration <= 0) return false;
        if (duration <= position) return false;                                 // 假时长
        if (duration <= Math.max(0, mHistory.getOpening()) + ending) return false;  // 片头+片尾比整集还长
        if (ending + position < duration) return false;
        if (position <= Math.max(0, mHistory.getOpening())) return false;
        return SystemClock.elapsedRealtime() - mLastSkipAtMs >= SKIP_DEBOUNCE_MS;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.VOD) updateVod(event.getVod());
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().setDanmaku(Danmaku.from(event.getPath()));
    }

    private void setPosition() {
        if (mHistory == null) return;
        long opening = Math.max(0, mHistory.getOpening());
        long target = Math.max(opening, mHistory.getPosition());
        long duration = player().getDuration();
        long ending = mHistory.getEnding();
        // 起播位置落在片尾区间（多半是上一集残留的进度），退回片头之后。
        // 否则新一集刚起播就满足片尾条件，会被立刻判为播完并再次跳集。
        if (duration > 0 && ending > 0 && target + ending >= duration) target = opening;
        player().seekTo(target);
    }

    private void checkEnded(boolean notify) {
        checkNext(notify);
    }

    private void setTrackVisible() {
        mBinding.control.action.text.setVisibility(player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod() ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(player().haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    }

    private void setTitleVisible() {
        mBinding.control.action.title.setVisibility(player().haveTitle() ? View.VISIBLE : View.GONE);
    }

    private MediaMetadata buildMetadata() {
        String title = mHistory.getVodName();
        String episode = getEpisode().getName();
        boolean empty = episode.isEmpty() || title.equals(episode);
        String artist = empty ? "" : episode;
        return PlayerManager.buildMetadata(title, artist, mHistory.getVodPic());
    }

    private void setMetadata() {
        player().setMetadata(buildMetadata());
    }

    private void startFlow() {
        if (!getSite().isChangeable()) return;
        if (isUseParse()) checkParse();
        else checkFlag();
    }

    private void checkParse() {
        int position = mParseAdapter.getPosition();
        boolean last = position == mParseAdapter.getItemCount() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) checkFlag();
        else nextParse(position);
    }

    private void initParse() {
        if (mParseAdapter.getItemCount() == 0) return;
        setParse(mParseAdapter.first());
    }

    private void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    private void checkSearch(boolean force) {
        if (mQuickAdapter.getItemCount() == 0) initSearch(mBinding.name.getText().toString(), true);
        else if (isAutoMode() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        setAutoMode(auto);
        setInitAuto(auto);
        startSearch(keyword);
        mBinding.part.setTag(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private void startSearch(String keyword) {
        mQuickAdapter.clear();
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (isPass(site)) sites.add(site);
        mViewModel.searchContent(sites, keyword, true);
    }

    private void setSearch(Result result) {
        List<Vod> items = result.getList();
        items.removeIf(this::mismatch);
        mQuickAdapter.addAll(items);
        mBinding.quick.setVisibility(View.VISIBLE);
        if (isInitAuto()) nextSite();
        if (items.isEmpty()) return;
        App.removeCallbacks(mR4);
    }

    @Override
    public void onItemClick(Vod item) {
        setAutoMode(false);
        getDetail(item);
    }

    private boolean mismatch(Vod item) {
        if (getId().equals(item.getId())) return true;
        if (mBroken.contains(item.getId())) return true;
        String keyword = Objects.toString(mBinding.part.getTag(), "");
        if (isAutoMode()) return !item.getName().equals(keyword);
        else return !item.getName().contains(keyword);
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_parse, parse.getName()));
        onItemClick(parse);
    }

    private void nextFlag(int position) {
        Flag flag = mFlagAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
        onItemClick(flag);
    }

    private void nextSite() {
        if (mQuickAdapter.getItemCount() == 0) return;
        Vod item = mQuickAdapter.get(0);
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
        mQuickAdapter.remove(0);
        mBroken.add(getId());
        setInitAuto(false);
        getDetail(item);
    }

    private void onPaused() {
        controller().pause();
        syncHistory(true);
    }

    private void onPlay() {
        if (mHistory != null && isEnded()) controller().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        controller().play();
        syncHistory(true);
    }

    private boolean onSeekBack() {
        controller().seekBack();
        return true;
    }

    private boolean onSeekForward() {
        controller().seekForward();
        return true;
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    private boolean isInitAuto() {
        return initAuto;
    }

    private void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    private boolean isAutoMode() {
        return autoMode;
    }

    private void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    private View getFocus1() {
        return mFocus1 == null || mFocus1.getVisibility() != View.VISIBLE ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE || mFocus2 == mBinding.control.action.opening || mFocus2 == mBinding.control.action.ending ? mBinding.control.action.next : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isFullscreen() && KeyUtil.isMenuKey(event)) onToggle();
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (isFullscreen() && isGone(mBinding.control.getRoot()) && isGone(mBinding.infoLayout) && mKeyDown.hasEvent(event) && service() != null) return mKeyDown.onKeyDown(event);
        if (KeyUtil.isMediaFastForward(event)) return onSeekForward();
        if (KeyUtil.isMediaRewind(event)) return onSeekBack();
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        mKeyDown.reset();
        seekTo(time);
        syncHistory(true);
    }

    @Override
    public void onSpeedUp() {
        if (!player().isPlaying()) return;
        mBinding.widget.speed.setVisibility(View.VISIBLE);
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getSpeed()));
    }

    @Override
    public void onSpeedEnd() {
        mBinding.widget.speed.clearAnimation();
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.control.action.speed.setText(player().setSpeed(mHistory.getSpeed()));
    }

    @Override
    public void onKeyUp() {
        showControl(getFocus2());
    }

    @Override
    public void onKeyDown() {
        showInfoLayout();
    }

    @Override
    public void onKeyCenter() {
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
        hideControl();
    }

    @Override
    public void onSingleTap() {
        if (isFullscreen()) onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isFullscreen()) onKeyCenter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveHistory(true);
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (!PlayerSetting.isAutoplay() && !mPlaybackStarted) {
            super.onBackInvoked();
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.infoLayout)) {
            hideInfoLayout();
        } else if (isVisible(mBinding.widget.center)) {
            hideCenter();
        } else {
            long currentTime = System.currentTimeMillis();
            if (currentTime - mLastBackTime < 2000) {
                mViewModel.stopSearch();
                if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                super.onBackInvoked();
            } else {
                mLastBackTime = currentTime;
                Notify.show(R.string.play_exit_hint);
            }
        }
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        saveHistory(true);
        DanmakuApi.cancel();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4, mClockRunnable, mLrcRunnable);
        mViewModel.getResult().removeObserver(mObserveDetail);
        mViewModel.getPlayer().removeObserver(mObservePlayer);
        mViewModel.getSearch().removeObserver(mObserveSearch);
        super.onDestroy();
    }
}

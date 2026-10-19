package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.BoxApi;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyboard;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Response;

public class SearchActivity extends BaseActivity implements WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CustomKeyboard.Callback {

    private ActivitySearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SearchActivity.class));
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        String keyword = getIntent().getStringExtra("keyword");
        return keyword != null ? keyword : "";
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        CustomKeyboard.init(this, mBinding);
        setRecyclerView();
        checkKeyword();
        onSearch();
    }

    @Override
    protected void initEvent() {
        mBinding.clear.setOnClickListener(v -> mRecordAdapter.clear());
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onSearch();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                getWord(s.toString());
            }
        });
        mBinding.mic.setOnClickListener(v -> mBinding.mic.start());
        mBinding.mic.setListener(this, new CustomTextListener() {
            @Override
            public void onResults(String result) {
                if (!result.isEmpty()) setKeyword(result);
                mBinding.keyword.requestFocus();
            }
        });
    }

    private void setRecyclerView() {
        mBinding.wordRecycler.setItemAnimator(null);
        mBinding.wordRecycler.setHasFixedSize(false);
        // 热搜用固定 4 列网格：分类标题占满一整行，词条一格一个，左右留白天然对称
        androidx.recyclerview.widget.GridLayoutManager manager =
                new androidx.recyclerview.widget.GridLayoutManager(this, 4);
        manager.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (mWordAdapter == null || position < 0 || position >= mWordAdapter.getItemCount()) return 1;
                Word.Data data = mWordAdapter.getItem(position);
                String title = data == null ? "" : data.getTitle();
                return title.startsWith(WordAdapter.DIVIDER) ? 4 : 1;
            }
        });
        mBinding.wordRecycler.setLayoutManager(manager);
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
    }

    private void checkKeyword() {
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void getWord(String text) {
        if (text.isEmpty()) getHot();
        else getSuggest(text);
    }

    // 搜索页热搜按分类出，内容全部是豆瓣当前高分
    // 豆瓣没有「短剧」分类，取不到就不放这一类，不硬凑
    private static final String[][] HOT_CATS = {
            {"电视剧榜", "电视剧"}, {"电影榜", "电影"}, {"综艺榜", "综艺"}, {"动漫榜", "动漫"}};
    /** 分类分隔符：带这个前缀的条目只当标题用，点了不搜索（样式由 WordAdapter 处理） */
    private static final String HOT_DIVIDER = WordAdapter.DIVIDER;

    private void getHot() {
        mBinding.word.setText(R.string.search_hot);
        mWordAdapter.setItems(Word.objectFrom(Setting.getHot()).getData());
        loadCategoryHot();
    }

    private void loadCategoryHot() {
        final int total = HOT_CATS.length;
        final java.util.List<java.util.List<com.fongmi.android.tv.bean.Vod>> results =
                new java.util.ArrayList<>(java.util.Collections.nCopies(total, (java.util.List<com.fongmi.android.tv.bean.Vod>) null));
        for (int i = 0; i < total; i++) {
            final int index = i;
            // force=true：每次进搜索页都实时向豆瓣要最新榜（60 秒内不重复打，避免限流）
            com.fongmi.android.tv.api.Douban.getCategoryList(HOT_CATS[i][1], 4, true, list -> {
                results.set(index, list);
                // 哪一类先回来就先显示哪一类，不再死等 4 类全齐（之前要等十几秒）
                App.post(() -> applyCategoryHot(results));
            });
        }
    }

    /** 五类都到齐再一次性铺开，保证「电视剧/电影/综艺/动漫/短剧」顺序固定。 */
    private void applyCategoryHot(java.util.List<java.util.List<com.fongmi.android.tv.bean.Vod>> results) {
        if (isFinishing()) return;
        // 用户已经在输入了，别拿热搜盖掉联想词
        if (!empty()) return;
        try {
            org.json.JSONArray data = new org.json.JSONArray();
            boolean any = false;
            for (int i = 0; i < HOT_CATS.length; i++) {
                java.util.List<com.fongmi.android.tv.bean.Vod> list = results.get(i);
                if (list == null || list.isEmpty()) continue;
                data.put(new org.json.JSONObject().put("title", HOT_DIVIDER + HOT_CATS[i][0]));
                for (com.fongmi.android.tv.bean.Vod vod : list) {
                    String name = vod.getName();
                    if (TextUtils.isEmpty(name)) continue;
                    data.put(new org.json.JSONObject().put("title", name));
                }
                any = true;
            }
            if (!any) {
                // 豆瓣一个都没拉到，退回后台热词 / 360kan
                BoxApi.table("box_hotsearch", rows -> {
                    String json = hotFromTable(rows);
                    if (!TextUtils.isEmpty(json)) {
                        App.post(() -> setAdapter(json, true));
                        return;
                    }
                    OkHttp.newCall("https://api.web.360kan.com/v1/rank?cat=1", Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(getCallback(true));
                });
                return;
            }
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("data", data);
            android.util.Log.d("HotED", "apply 分类=" + HOT_CATS.length + " 词条=" + data.length() + " 各类=" + describeResults(results));
            // 直接铺词条：setAdapter(save=false) 在关键词为空时会直接 return（那是给联想词用的），
            // 而且这里也不该写进热搜缓存（缓存带 ▎ 分隔符，下次读出来很怪）
            mWordAdapter.setItems(Word.objectFrom(root.toString()).getData());
        } catch (Throwable e) {
            android.util.Log.d("HotED", "applyCategoryHot error: " + e);
        }
    }

    /** 把后台的搜索推荐表转成 Word 认的 {"data":[{"title":...}]} 结构。 */
    private String hotFromTable(org.json.JSONArray rows) {
        if (rows == null) return "";
        try {
            java.util.List<org.json.JSONObject> list = new java.util.ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                org.json.JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                if (!BoxApi.enabled(row) || !BoxApi.matchApp(row)) continue;
                if (TextUtils.isEmpty(row.optString("title"))) continue;
                list.add(row);
            }
            if (list.isEmpty()) return "";
            list.sort((a, b) -> b.optInt("weigh") - a.optInt("weigh"));
            org.json.JSONArray data = new org.json.JSONArray();
            for (org.json.JSONObject row : list) {
                org.json.JSONObject item = new org.json.JSONObject();
                item.put("title", row.optString("title"));
                data.put(item);
            }
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("data", data);
            return root.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    private void getSuggest(String text) {
        mBinding.word.setText(R.string.search_suggest);
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(ZhuToPin.get(text))).enqueue(getCallback(false));
    }

    private Callback getCallback(boolean hot) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> setAdapter(result, hot));
            }
        };
    }

    private void setAdapter(String result, boolean save) {
        if (!save && empty()) return;
        if (save) Setting.putHot(result);
        mWordAdapter.setItems(Word.objectFrom(result).getData());
    }

    @Override
    public void onItemClick(String text) {
        // 分类分隔条（▎电视剧 这种）只作标题，点了不搜索
        if (text != null && text.startsWith(HOT_DIVIDER)) return;
        setKeyword(text);
        onSearch();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.recordLayout.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        if (size == 0) focusFirst(mBinding.wordRecycler);
    }

    @Override
    public void onSearch() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        CollectActivity.start(this, keyword);
    }

    @Override
    public void showDialog() {
        SiteDialog.create().search().show(this);
    }

    @Override
    public void onRemote() {
        PushActivity.start(this, 1);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        if (KeyUtil.isActionDown(event) && findFocus(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean findFocus(KeyEvent event) {
        View current = getCurrentFocus();
        if (current == mBinding.keyword) return handleKeywordKey(event);
        View inKeyboard = mBinding.keyboard.findContainingItemView(current);
        View inWord = mBinding.wordRecycler.findContainingItemView(current);
        View inRecord = mBinding.recordRecycler.findContainingItemView(current);
        if (inKeyboard != null) return handleKeyboardKey(event, inKeyboard);
        if (inRecord != null) return handleRecordKey(event, inRecord);
        if (inWord != null) return handleWordKey(event, inWord);
        return false;
    }

    private View findNearestInLastRow(RecyclerView rv, int targetLeft) {
        if (rv.getChildCount() == 0) return null;
        int lastTop = rv.getChildAt(rv.getChildCount() - 1).getTop();
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == lastTop) {
                int dist = Math.abs(child.getLeft() - targetLeft);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = child;
                }
            }
        }
        return nearest;
    }

    private boolean isFirstRow(RecyclerView rv, View item) {
        View first = rv.getChildAt(0);
        return first != null && item.getTop() == first.getTop();
    }

    private boolean isLastRow(RecyclerView rv, View item) {
        View last = rv.getChildAt(rv.getChildCount() - 1);
        return last != null && item.getTop() == last.getTop();
    }

    private boolean isFirstInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int left = focused.getLeft();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getLeft() < left) return false;
        }
        return true;
    }

    private boolean isLastInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int right = focused.getRight();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getRight() > right) return false;
        }
        return true;
    }

    private boolean handleKeywordKey(KeyEvent event) {
        if (!KeyUtil.isRightKey(event)) return false;
        if (mBinding.keyword.getSelectionEnd() < mBinding.keyword.getText().length()) return false;
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        return focusFirst(hasRecord ? mBinding.recordRecycler : mBinding.wordRecycler);
    }

    private boolean handleKeyboardKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.keyboard, item)) {
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.keyboard, item)) return true;
        return KeyUtil.isDownKey(event) && isLastRow(mBinding.keyboard, item);
    }

    private boolean handleWordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.wordRecycler, item);
        if (KeyUtil.isDownKey(event)) return isLastRow(mBinding.wordRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.wordRecycler, item)) {
            if (mBinding.recordLayout.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.recordRecycler, item.getLeft());
                if (child != null) {
                    mBinding.scroll.smoothScrollTo(0, 0);
                    child.requestFocus();
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleRecordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.recordRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.recordRecycler, item)) {
            mBinding.clear.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.recordRecycler, item)) return focusFirst(mBinding.wordRecycler);
        return false;
    }

    private boolean focusFirst(RecyclerView rv) {
        View child = rv.getChildAt(0);
        if (child == null) return false;
        child.requestFocus();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBinding.mic.setFocusable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBinding.mic.setFocusable(true);
        mBinding.keyword.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBinding.mic.destroy();
    }
}

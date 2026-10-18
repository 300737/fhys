# 自建 spider.jar（繁华影视）

不再依赖别人的 `9527.jar`。**类名与 `wmz.json` 里 `sites[].api` 的 `csp_*` 严格一一对应，
一个萝卜一个坑**：73 个类一个不少，宿主反射实例化不会报 `ClassNotFoundException`。

## 文件

| 文件 | 说明 |
|---|---|
| `spider.jar` | 编译产物（54 KB / 84 个 class），直接丢到配置同目录 |
| `wmz-local.json` | 你的原配置，只改了一处：`"spider": "./9527.jar"` → `"./spider.jar"` |
| `spider-src/` | 全部源码，`build.sh` 一键重编译 |

## 怎么用

1. 把 `spider.jar` 和 `wmz-local.json` 放在同一个目录；
2. TVBox 里填 `wmz-local.json` 的地址（本地/网络都行）；
3. 完事。

## 实现情况

### 真跑通的（有数据、能播）

| 类 | 数据源 | 状态 |
|---|---|---|
| `csp_Douban` | 豆瓣 rexxar 官方接口 | 5 个分类（热映/剧集/综艺/即将上映/Top250）、搜索、详情齐全，**播放内容为官方预告片 mp4 直链** |
| `csp_AppYsV2` | 电影天堂 `39.105.18.5:5565` | 11.6 万条资源、6 条线路，**m3u8 线路自动排第一**，点开直连播放 |
| `csp_PanWebShare` 系 | 苹果CMS 网盘站 | 分类/列表/搜索/详情全通，详情挖 `data-clipboard-text` 拿网盘直链 |

### 通用引擎 `MacCms`

所有 `csp_*` 都继承它，双模式自动降级：

- **优先 JSON**：`{host}/api.php/provide/vod/`（`ac=list` / `ac=detail` / `wd=`）；
- **兜底 HTML**：`/index.php/vod/show/id/{tid}/page/{pg}.html`（网盘站没开 JSON 接口就走这条）。

所以：**任何一个苹果CMS 站点，只要在配置的 `ext` 里补上 `{"site":["域名"]}`，对应的
`csp_*` 立刻就能用，不用改 jar、不用重新编译。**

细节处理（都是踩过的坑）：

- 部分站点在收到 `Accept: application/json` 时会把整页 HTML 当成 JSON 字符串返回，
  HTML 与 JSON 两套请求头分开，并对 `"..."` 包裹的响应自动解包；
- 网盘站同一行有「复制」「下载」两个按钮指向同一链接，按 URL 去重，集数从
  `复制《片名》第1集下载地址` 里抠；
- 卡片解析按「本详情链接 → 下一个详情链接」切区间，因为不少模板把 `<img>` 放在 `<a>` 外面；
- 封面空串不拼域名，避免出现 `http://站点/` 假图；
- 多线路按「直连 m3u8/mp4」优先重排，`from` 与 `url` 平行数组同步换位。

### 占位（有类名、无数据）

剩下 60 多个 `csp_*`（夸克/百度/115 网盘、B站、短剧、体育直播、听书、DJ、音乐…）
需要各家 OAuth 授权或私有签名算法，属另一套工程量。它们**不报错、返回合法空结构**，
界面不会崩。给它们补 `ext.site` 后会自动走上文通用引擎。

## 重编译

```bash
cd spider-src
bash build.sh                       # 产出 spider.jar
bash test.sh csp_AppYsV2 '' home    # 单跑某个源
java -cp "build/stubs;build/classes;build/test" Smoke $(ls ...)  # 全量冒烟
```

`stubs/` 里是编译占位（`android.content.Context`、宿主 `Spider`），**不会打进 jar**，
运行时用的是宿主自己的那份，签名保持一致。

## 这次改动只碰了 jar，没碰 App

App 侧（开机动画、返回退出、seek 容差、太极缓冲、暂停徽章）在上一个包里，这里不含。

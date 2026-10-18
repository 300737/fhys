# 自建 spider.jar

不依赖任何第三方 jar。所有代码在本目录 `spider-src/` 里，`bash build.sh` 一条命令重编译。

## 类名规则（必须先看懂这条）

宿主 `app/src/main/java/com/fongmi/android/tv/api/loader/JarLoader.java` 第 135、164 行：

```java
String className = "com.github.catvod.spider." + api.split("csp_")[1];
```

也就是说配置里写 `"api": "csp_Douban"`，宿主实际加载的是
**`com.github.catvod.spider.Douban`**（去掉 `csp_` 前缀）。

所以 jar 里每个站点都有两个类：

| 类名 | 作用 |
|---|---|
| `Douban` | 宿主真正加载的入口 |
| `csp_Douban` | 别名，`extends Douban`，兼容按全名加载的分支 |

`WMZ` 配置里 73 个 `csp_*` 全部落地。其中一个 `csp_4KZhinan` 去掉前缀后是 `4KZhinan`，
以数字开头不是合法 Java 标识符，任何 jar 都造不出这个类名——该站在本分支上本来就加载不了，
与 jar 无关。

## 文件

| 文件 | 说明 |
|---|---|
| `spider.jar` | 编译产物（156 个 class） |
| `demo.json` | 示例配置：3 个站点，可直接加载 |
| `spider-src/` | 全部源码 + `build.sh` |
| `README.md` | 本文件 |

## jar 和 json 的关系

TVBox 只认配置 json，不认 jar。json 里的 `spider` 字段告诉它 jar 在哪，
`sites` 数组告诉它要加载哪些站点、每个站点用哪个类。所以 **jar 必须配一份 json 才有用**。

`spider` 路径在宿主代码里只有两种能生效（`JarLoader.parseJar`）：

- `http` / `https` 开头 → 下载到缓存
- `file` 开头 → 按本地文件读

`./spider.jar` 这种相对路径**不会被加载**，别写。

## 怎么用

1. 把 `spider.jar` 和 `demo.json` 一起放到盒子的 `/sdcard/TVBox/` 目录；
2. `demo.json` 里的 `spider` 写死成 `file:///sdcard/TVBox/spider.jar`；
3. TVBox → 设置 → 配置 → 地址填 `/sdcard/TVBox/demo.json`（或 `file:///sdcard/TVBox/demo.json`）；
4. 确定，等它加载完。

`demo.json` 只有 3 个站点，是给你验证 jar 能跑起来用的：

| key | 名称 | 类 |
|---|---|---|
| `douban` | 豆瓣精选 | `csp_Douban` |
| `dytt` | 电影天堂 | `csp_AppYsV2` |
| `panshare` | 网盘分享 | `csp_PanWebShare` |

想加站，照格式往 `sites` 里加一条就行，`api` 填配置里的 `csp_*`，`key` 随便起但别重复。
如果你的配置是 http 托管的，把 `spider` 换成 `"http://你的地址/spider.jar"`。

## 引擎

`MacCms` —— 通用苹果CMS引擎，所有站点类都继承它，双模式自动降级：

- **JSON 优先**：`{host}/api.php/provide/vod/`（`ac=list` / `ac=detail` / `wd=`）
- **HTML 兜底**：`/index.php/vod/show/id/{tid}/page/{pg}.html` + 导航/卡片正则

任何苹果CMS 站点，只要 `ext` 里给出 `{"site":["域名"]}`，对应类立刻可用，不用改 jar。

细节（踩过的坑）：

- 部分站点收到 `Accept: application/json` 会把整页 HTML 当 JSON 字符串回，HTML/JSON 两套请求头分开设置，并对引号包裹的响应自动解包
- 卡片解析按「本详情链接 → 下一个详情链接」切区间，因为很多模板把 `<img>` 放在 `<a>` 外面
- 网盘站同一行「复制」「下载」两个按钮指向同一链接，按 URL 去重，集数从 `复制《片名》第1集下载地址` 里抠
- 封面空串不拼域名，避免 `http://站点/` 假图
- 多线路按「直连 m3u8/mp4」优先重排，`from` 与 `url` 平行数组同步换位

## 数据源

| 类 | 数据源 |
|---|---|
| `Douban` | 豆瓣 rexxar 接口：5 个分类 + 搜索 + 详情，播放内容为官方预告片 mp4 直链（豆瓣本身无正片） |
| `AppYsV2` | 电影天堂 `39.105.18.5:5565`，JSON API，m3u8 线路自动排第一 |
| `PanWebShare` 系 | 苹果CMS 网盘站，详情从 `data-clipboard-text` 挖网盘直链 |

其余站点类是同一引擎的实例，默认域名取自配置 `ext`；没有域名的运行时由 `ext.site` / `ext.host` 提供，
拿不到就返回合法空结构，不抛异常。

## 重编译

```bash
cd spider-src
bash build.sh                    # 产出 spider.jar
bash test.sh Douban '' home      # 单跑某个源
java -cp "build/stubs;build/classes;build/test" Smoke ...   # 全量冒烟
```

`stubs/` 是编译占位（`android.content.Context`、宿主 `Spider`），**不进 jar**，
运行时用宿主自己那份，签名一致。

## 说明

- 配置文件原样使用，只改了 `spider` 一行；jar 源码里不含任何配置文本。
- App 侧改动（开机动画、返回退出、seek 容差、太极缓冲、暂停徽章）在另一个包里，这里不含。

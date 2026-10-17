import re
import json
import requests
from base.spider import Spider


class Spider(Spider):
    """
    爱奇艺首页推荐（App 首页「中间大图轮播 + 右侧 3 张卡片」的数据源）

    2026-10 爱奇艺改版后原脚本失效，本版修了两个叠加的坑：

    坑1 接口格式变了
        旧脚本：先抓 https://www.iqiyi.com/ 的 HTML，正则找 src="//www.iqiyi.com/prelw/portal/lw/v7/channel/recommend?lwaFastKey=..."，
                再请求该地址，然后在返回里找 JS 包裹 `qyMesh.preload['Page_recommend_1']= { response: {...} }`。
        实测：带 ?lwaFastKey= 的地址现在返回 **200 + 0 字节**（空响应）；
                而去掉查询参数的裸地址 https://www.iqiyi.com/prelw/portal/lw/v7/channel/recommend
                返回 **200 + 约 480KB 纯 JSON** {"code":0,"cname":"推荐首页-v7","items":[...]}。
        所以：优先打裸地址并按纯 JSON 解析，老写法保留作兜底。

    坑2 图片变成 AVIF（这才是"拿不到真图"的真凶）
        爱奇艺 CDN 会按 User-Agent 做格式协商：现代 Chrome UA 下发 image/avif（实测 175KB，头 00000020 6674 即 AVIF），
        老 Android（TV 盒子普遍 Android 7/9/11）解不了 AVIF，Glide 解码失败后降级成"首字色块"→ 看起来就是没图。
        双保险：
          a) 把 UA 换成 Android TV UA，接口直接下发 .jpg；
          b) 万一仍是 .avif，直接把后缀改成 .jpg —— 实测同一路径换后缀后 CDN 稳定返回真 JPEG（image/jpeg，头 ffd8ffe0）。
    """

    API_BARE = 'https://www.iqiyi.com/prelw/portal/lw/v7/channel/recommend'
    API_QUERY = 'https://www.iqiyi.com/prelw/portal/lw/v7/channel/recommend?lwaFastKey=Page_recommend_1&v=17.063.25600'
    MARKER = "qyMesh.preload['Page_recommend_1']= { response: "
    HOME = 'https://www.iqiyi.com/'

    def getName(self):
        return "爱奇艺首页"

    def init(self, extend=""):
        self.headers = {
            # 不要用 Chrome 1xx 的 UA，否则图片会被协商成 AVIF
            'User-Agent': 'Mozilla/5.0 (Linux; Android 9; TV Build/PTT1.190718.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/74.0.3729.186 Safari/537.36',
            'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
            'Accept': '*/*',
            'Referer': 'https://www.iqiyi.com/',
        }

    # ---------- 工具 ----------

    def _get(self, url, timeout=10):
        try:
            res = requests.get(url, headers=self.headers, timeout=timeout)
            if res.status_code == 200 and res.text:
                return res.text
        except Exception:
            pass
        return ''

    def _parse(self, text):
        """兼容两种返回：新=纯 JSON；旧=qyMesh.preload JS 包裹"""
        if not text:
            return None
        try:
            data = json.loads(text)
            if isinstance(data, dict) and data.get('items'):
                return data
        except Exception:
            pass
        pos = text.find(self.MARKER)
        if pos == -1:
            return None
        start = pos + len(self.MARKER)
        depth = 0
        for idx in range(start, len(text)):
            char = text[idx]
            if char == '{':
                depth += 1
            elif char == '}':
                depth -= 1
                if depth == 0:
                    try:
                        return json.loads(text[start:idx + 1])
                    except Exception:
                        return None
        return None

    def _fix_pic(self, url):
        """把 AVIF 链接改回 JPEG（同一路径 CDN 会返回真 JPEG），并补全 // 协议"""
        if not url:
            return ''
        url = str(url).strip()
        if url.startswith('//'):
            url = 'https:' + url
        if url.lower().endswith('.avif'):
            url = url[:-5] + '.jpg'
        return url

    def _is_ad(self, item):
        if item.get('isAd') is True:
            return True
        if item.get('isPpcAd') is True:
            return True
        if item.get('isNativePriceAd') is True:
            return True
        if item.get('ad') is not None:
            return True
        return False

    # ---------- 主逻辑 ----------

    def homeContent(self, filter):
        data = None

        # 1) 优先：裸接口（无查询参数）返回纯 JSON —— 目前唯一稳定可用的
        data = self._parse(self._get(self.API_BARE))

        # 2) 兜底：带参数的老地址（现在多半返回 0 字节）
        if not data:
            data = self._parse(self._get(self.API_QUERY))

        # 3) 再兜底：抓首页 HTML 里的 src=... 再请求（老路子）
        if not data:
            html = self._get(self.HOME)
            match = re.search(r'src=["\'](//www\.iqiyi\.com/prelw/portal/lw/v7/channel/recommend[^"\']*)["\']', html or '')
            if match:
                data = self._parse(self._get('https:' + match.group(1)))

        if not data:
            return {"class": [{"type_id": "iqiyi_home", "type_name": "爱奇艺精选"}], "list": []}

        # 4) 找 adScene=focus 的轮播位（首页大图那一组）
        focus_item = None
        for item in data.get('items', []):
            if (item.get('temp') or {}).get('adScene') == 'focus':
                focus_item = item
                break

        vod_list = []
        if focus_item:
            video = focus_item.get('video') or []
            rows = (video[0].get('data') or []) if video else []
            for item in rows:
                title = (item.get('display_name') or item.get('title') or '').strip()
                if not title:
                    continue
                if self._is_ad(item):
                    continue
                subtitle = item.get('desc') or item.get('desc1') or item.get('description') or ''
                subtitle = str(subtitle).strip()
                if '广告' in subtitle:
                    continue
                image = self._fix_pic(
                    item.get('banner_image_url')
                    or item.get('image_url_normal')
                    or item.get('image_cover')
                    or ''
                )
                link = (item.get('page_url') or item.get('url') or item.get('play_url') or '').strip()
                title_logo = self._fix_pic(item.get('banner_logo_url') or '')
                vod_list.append({
                    "vod_id": link,
                    "vod_name": title,
                    "vod_pic": image,
                    "vod_remarks": subtitle,
                    "vod_tag": title_logo,
                })

        # 5) 按片名去重（同一影片可能重复出现）
        seen = set()
        unique_list = []
        for vod in vod_list:
            key = vod['vod_name']
            if key in seen:
                continue
            seen.add(key)
            unique_list.append(vod)

        return {
            "class": [{"type_id": "iqiyi_home", "type_name": "爱奇艺精选"}],
            "list": unique_list
        }

    def detailContent(self, ids):
        url = ids[0] if ids else ''
        vod = {
            "vod_id": url,
            "vod_name": "爱奇艺精选视频",
            "vod_pic": "",
            "vod_remarks": "第三方解析播放",
            "vod_actor": "未知",
            "vod_director": "未知",
            "vod_content": "此视频是爱奇艺首页推荐内容，点击将尝试通过默认解析接口播放。"
        }
        return {"list": [vod]}

    def playerContent(self, flag, id, vipFlags):
        play_url = "https://jx.jsonplayer.com/vip/?url=" + id
        return {
            "parse": 1,
            "url": play_url,
            "header": {"User-Agent": "Mozilla/5.0"}
        }

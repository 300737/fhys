# 外部 spider jar（网盘线路等所有 csp_ 站点）按原始类名引用宿主里的 catvod 类：
# com.github.catvod.net.OkHttp / utils.Path / utils.Util / utils.Json / bean.* 等。
# 一旦被 R8 重命名，jar 类加载即抛 NoClassDefFoundError，getSpider 兜底成 SpiderNull，
# 表现为所有网盘线路首页分类加载不出来。此文件经 consumerProguardFiles 参与宿主 R8。
-keep class com.github.catvod.** { *; }
-keep class * extends com.github.catvod.crawler.Spider
-keep class com.google.gson.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

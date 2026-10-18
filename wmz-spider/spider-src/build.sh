#!/usr/bin/env bash
# 编译自建 spider jar
#
# 关键：Android 的 DexClassLoader 只能加载含 classes.dex 的 jar。
# 纯 .class 的 jar 推到盒子上会 ClassNotFoundException（桌面 JVM 测不出来），
# 所以最后必须用 build-tools 里的 d8 把 .class 转成 dex 再打包。
set -e
cd "$(dirname "$0")"

OUT=build
JAR=spider.jar
D8="${D8:-C:/Users/Administrator/AppData/Local/Android/Sdk/build-tools/35.0.0/d8.bat}"

rm -rf "$OUT" "$JAR"
mkdir -p "$OUT/stubs" "$OUT/classes" "$OUT/dex"

echo "[1/4] 编译 stubs（编译占位，不进 jar）..."
javac -encoding UTF-8 -nowarn -source 8 -target 8 -d "$OUT/stubs" \
  $(find stubs -name '*.java') 2>&1 | grep -viE "警告|warning|deprecat|bootstrap|已过时" || true

echo "[2/4] 编译 spider 源码 ..."
javac -encoding UTF-8 -nowarn -source 8 -target 8 -cp "$OUT/stubs" -d "$OUT/classes" \
  $(find src -name '*.java') 2>&1 | grep -viE "警告|warning|deprecat|bootstrap|已过时" || true
# 宿主自带这两组类，绝不能打进 jar
rm -rf "$OUT/classes/android" "$OUT/classes/com/github/catvod/crawler"

echo "[3/4] class -> dex ..."
( cd "$OUT/classes" && jar cf ../input.jar . )
( cd "$OUT/stubs"  && jar cf ../stubs.jar . )   # 给 d8 当 --lib，解析宿主类型，不打包
"$D8" --min-api 21 --release --lib "$OUT/stubs.jar" --output "$OUT/dex" "$OUT/input.jar"

echo "[4/4] 打包 $JAR ..."
( cd "$OUT/dex" && jar cf "../../$JAR" classes.dex )

echo "OK -> $(pwd)/$JAR  ($(du -h "$JAR" | cut -f1), classes.dex 内含全部站点类)"

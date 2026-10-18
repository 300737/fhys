#!/usr/bin/env bash
# 编译自建 spider jar：stubs 只做编译占位，不进 jar（宿主自带同名类）
set -e
cd "$(dirname "$0")"

OUT=build
JAR=spider.jar
rm -rf "$OUT" "$JAR"
mkdir -p "$OUT/stubs" "$OUT/classes"

echo "[1/3] 编译 stubs ..."
javac -encoding UTF-8 -nowarn -source 8 -target 8 -d "$OUT/stubs" \
  $(find stubs -name '*.java') 2>&1 | grep -v "^警告\|^warning\|bootstrap class path\|已过时\|deprecat" || true

echo "[2/3] 编译 spider 源码 ..."
javac -encoding UTF-8 -nowarn -source 8 -target 8 -cp "$OUT/stubs" -d "$OUT/classes" \
  $(find src -name '*.java') 2>&1 | grep -v "^警告\|^warning\|bootstrap class path\|已过时\|deprecat" || true

echo "[3/3] 打包 $JAR ..."
rm -rf "$OUT/classes/android" "$OUT/classes/com/github/catvod/crawler"
cd "$OUT/classes" && jar cf "../../$JAR" . && cd ../..

echo "OK -> $(pwd)/$JAR  ($(unzip -l "$JAR" | tail -1))"

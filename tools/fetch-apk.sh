#!/bin/bash
# 从 GitHub Actions 取最新构建产物。加速地址写死在下面，不用每次手拼。
#
# 用法（推荐直接双击桌面「取最新包.bat」）：
#   bash tools/fetch-apk.sh                      # 电视版 leanback arm64
#   bash tools/fetch-apk.sh mobile               # 手机版
#   bash tools/fetch-apk.sh leanback armeabi-v7a
#   WAIT_MIN=0 bash tools/fetch-apk.sh           # 不等构建，只取已成功的那次
set -uo pipefail

# ==================== 配置区：只改这里 ====================
ACCEL="https://cf.612526.xyz"      # 加速反代地址；留空就直连 GitHub
REPO="300737/fhys"
FLAVOR="${1:-leanback}"            # leanback(电视) / mobile(手机)
ABI="${2:-arm64-v8a}"              # arm64-v8a / armeabi-v7a
WAIT_MIN="${WAIT_MIN:-25}"         # 最新一次构建还没跑完时，最多等多少分钟
OUT="${OUT:-/c/Users/Administrator/Desktop}"
# =========================================================

if [ -n "$ACCEL" ]; then
  API="$ACCEL/https://api.github.com/repos/$REPO"
else
  API="https://api.github.com/repos/$REPO"
fi
WANT="apk-$FLAVOR-$ABI-release"

# token 从 git 凭据管理器取，不用手工填
TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill 2>/dev/null \
        | sed -n 's/^password=//p' | tr -d '\r')
if [ -z "$TOKEN" ]; then
  echo "取不到 GitHub token，先手动 git push 一次让凭据管理器记住。"
  exit 1
fi

api() {
  curl -g -s -k -m 40 \
    -H "Authorization: token $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    "$1"
}

echo "加速通道: ${ACCEL:-直连 GitHub}"
echo "目标产物: $WANT"

# ---------- 1. 找最新一次构建，没跑完就等 ----------
RUN=$(api "$API/actions/runs?per_page=1" \
      | python -c "import json,sys;d=json.load(sys.stdin);print(d['workflow_runs'][0]['id'] if d.get('workflow_runs') else '')")
if [ -z "$RUN" ]; then echo "查不到任何构建记录。"; exit 1; fi
echo "最新构建: $RUN"

DEADLINE=$(( $(date +%s) + WAIT_MIN * 60 ))
while :; do
  INFO=$(api "$API/actions/runs/$RUN" \
        | python -c "import json,sys;d=json.load(sys.stdin);print(d.get('status',''),d.get('conclusion') or '-',d['head_commit']['message'].splitlines()[0][:40])")
  STATUS=${INFO%% *}
  if [ "$STATUS" = "completed" ]; then break; fi
  if [ "$(date +%s)" -ge "$DEADLINE" ]; then
    echo "等了 ${WAIT_MIN} 分钟还没跑完（当前：$INFO）。"
    echo "想直接拿上一次成功的包，就跑：WAIT_MIN=0 bash tools/fetch-apk.sh"
    exit 1
  fi
  echo "  构建中（$INFO），20 秒后再看…"
  sleep 20
done
echo "构建结果: $INFO"

CONC=$(echo "$INFO" | cut -d' ' -f2)
if [ "$CONC" != "success" ]; then
  echo "这次构建没成功（$CONC），改用最近一次成功的构建。"
  RUN=$(api "$API/actions/runs?status=success&per_page=1" \
        | python -c "import json,sys;d=json.load(sys.stdin);print(d['workflow_runs'][0]['id'] if d.get('workflow_runs') else '')")
  [ -z "$RUN" ] && { echo "连一次成功的构建都没有。"; exit 1; }
  echo "改用构建: $RUN"
fi

# ---------- 2. 找匹配的产物 ----------
ART=$(api "$API/actions/runs/$RUN/artifacts" | python -c "
import json,sys
want='$WANT'.lower()
arts=json.load(sys.stdin).get('artifacts',[])
for a in arts:
    if a['name'].lower()==want:
        print(a['id'],a['name']); break
else:
    print('', '|'.join(a['name'] for a in arts))
")
ART_ID=${ART%% *}
ART_NAME=${ART##* }
if [ -z "$ART_ID" ]; then
  echo "这次构建里没有 $WANT，可用的有："
  echo "$ART_NAME" | tr '|' '\n' | sed 's/^/  /'
  exit 1
fi
echo "产物: $ART_NAME ($ART_ID)"

# ---------- 3. 两步下载（反代会转发 Authorization，第二步必须不带 token）----------
ZIP_URL="$API/actions/artifacts/$ART_ID/zip"
if [ -z "$ACCEL" ]; then ZIP_URL="https://api.github.com/repos/$REPO/actions/artifacts/$ART_ID/zip"; fi

LOC=$(curl -s -k --max-time 30 -D - -o /dev/null -H "Authorization: token $TOKEN" "$ZIP_URL" \
      | tr -d '\r' | awk 'tolower($1)=="location:"{print $2; exit}')
[ -z "$LOC" ] && { echo "拿不到下载地址，token 可能过期。"; exit 1; }
case "$LOC" in
  http*) ;;
  *) LOC="$ACCEL$LOC" ;;   # 反代返回相对路径，补成绝对地址
esac

ZIP="$(mktemp -d)/$ART_NAME.zip"
echo "开始下载…"
curl -g -s -k -L --max-time 900 -o "$ZIP" \
  -w "下载完成: %{size_download} 字节, 用时 %{time_total}s, 均速 %{speed_download} B/s\n" "$LOC"
[ -s "$ZIP" ] || { echo "下载失败，文件是空的。"; exit 1; }

# ---------- 4. 解出 APK 到桌面 ----------
TMPOUT="$(mktemp -d)"
unzip -q -o "$ZIP" -d "$TMPOUT"
APK=$(find "$TMPOUT" -name "*.apk" | head -1)
[ -z "$APK" ] && { echo "zip 里没有 apk。"; exit 1; }
cp -f "$APK" "$OUT/$ART_NAME.apk"
echo
echo "=============================================="
echo " APK 已就位: $OUT/$ART_NAME.apk"
echo " 大小: $(stat -c%s "$OUT/$ART_NAME.apk") 字节"
echo "=============================================="

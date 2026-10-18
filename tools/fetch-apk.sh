#!/bin/bash
# 从 GitHub Actions 取最新构建产物，走 cf.612526.xyz 反代加速下载
# 用法：
#   bash tools/fetch-apk.sh                     # 下电视版 leanback arm64
#   bash tools/fetch-apk.sh mobile              # 下手机版
#   bash tools/fetch-apk.sh leanback armeabi-v7a
set -euo pipefail

REPO="300737/fhys"
PROXY="https://cf.612526.xyz"
DESKTOP="/c/Users/Administrator/Desktop"
FLAVOR="${1:-leanback}"
ABI="${2:-arm64-v8a}"

# token：从 git 凭据管理器取，不必手工填
TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill 2>/dev/null \
        | grep '^password=' | cut -d= -f2 | tr -d '\r')
[ -z "$TOKEN" ] && { echo "取不到 GitHub token，先 git push 一次让凭据管理器记住"; exit 1; }

API="https://api.github.com/repos/$REPO"

# 最近一次成功的 run
RUN_ID=$(curl -g -s -k -H "Authorization: token $TOKEN" \
  "$PROXY/$API/actions/runs?status=success&per_page=1" \
  | python -c "import json,sys; d=json.load(sys.stdin); print(d['workflow_runs'][0]['id'] if d.get('workflow_runs') else '')")
[ -z "$RUN_ID" ] && { echo "没有成功的构建记录"; exit 1; }
echo "run: $RUN_ID"

# 该 run 下匹配的 artifact
ART=$(curl -g -s -k -H "Authorization: token $TOKEN" \
  "$PROXY/$API/actions/runs/$RUN_ID/artifacts" \
  | python -c "
import json,sys
want='apk-$FLAVOR-$ABI-release'.lower()
for a in json.load(sys.stdin).get('artifacts',[]):
    if a['name'].lower()==want:
        print(a['id'],a['name']); break
")
[ -z "$ART" ] && { echo "找不到 apk-$FLAVOR-$ABI-release，可用列表："; curl -g -s -k -H "Authorization: token $TOKEN" "$PROXY/$API/actions/runs/$RUN_ID/artifacts" | python -c "import json,sys;[print(' ',a['name']) for a in json.load(sys.stdin).get('artifacts',[])]"; exit 1; }

ART_ID=${ART%% *}
ART_NAME=${ART##* }
echo "artifact: $ART_NAME ($ART_ID)"

# 第一步：带 token 过 GitHub 认证，拿反代的 302 地址（反代会转发 Authorization 头）
LOC=$(curl -s -k --max-time 25 -D - -o /dev/null -H "Authorization: token $TOKEN" \
  "$PROXY/$API/actions/artifacts/$ART_ID/zip" \
  | grep -i "^location:" | cut -d' ' -f2 | tr -d '\r\n\t ')
case "$LOC" in
  http*) ;;
  *) LOC="$PROXY$LOC" ;;   # 反代返回的是相对路径，要补成绝对地址
esac

# 第二步：不带 token，让反代去取 Azure（SAS 签名已在 URL 里）
#         -g 关掉 curl 的 glob，防止 URL 里的特殊字符被误判
ZIP="/tmp/$ART_NAME.zip"
curl -g -s -k -L --max-time 600 -o "$ZIP" \
  -w "下载: %{size_download} 字节, %{time_total}s, %{speed_download}B/s\n" "$LOC"

# 解出 APK 到桌面
rm -rf /tmp/apkout && mkdir -p /tmp/apkout
unzip -q -o "$ZIP" -d /tmp/apkout
APK=$(find /tmp/apkout -name "*.apk" | head -1)
cp "$APK" "$DESKTOP/$ART_NAME.apk"
echo "APK 已放到桌面: $DESKTOP/$ART_NAME.apk ($(stat -c%s "$DESKTOP/$ART_NAME.apk") 字节)"

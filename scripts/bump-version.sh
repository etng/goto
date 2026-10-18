#!/usr/bin/env bash
#
# 自增 Goto 的 Android 版本号。
#   - versionCode 始终 +1（Android 要求升级包版本号必须递增，否则装不上）
#   - versionName(semVer) 按指定级别递增，默认自增 minor（次版本号，中间位）
#
# 用法:
#   ./scripts/bump-version.sh            # 默认: 自增 minor   (0.1.0 -> 0.2.0)
#   ./scripts/bump-version.sh minor      # 同上
#   ./scripts/bump-version.sh major      # 0.1.0 -> 1.0.0
#   ./scripts/bump-version.sh patch      # 0.1.0 -> 0.1.1
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FILE="$REPO_ROOT/android/app/build.gradle.kts"

LEVEL="${1:-minor}"
case "$LEVEL" in
  major|minor|patch) ;;
  *) echo "用法: $0 [major|minor|patch]" >&2; exit 1 ;;
esac

if [ ! -f "$FILE" ]; then
  echo "找不到 $FILE" >&2
  exit 1
fi

CODE="$(grep -oE 'versionCode = [0-9]+' "$FILE" | grep -oE '[0-9]+' | head -1)"
NAME="$(grep -oE 'versionName = "[0-9]+\.[0-9]+\.[0-9]+"' "$FILE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"

if [ -z "$CODE" ] || [ -z "$NAME" ]; then
  echo "无法解析 versionCode/versionName，请检查 $FILE" >&2
  exit 1
fi

IFS='.' read -r MAJ MIN PAT <<< "$NAME"

case "$LEVEL" in
  major) MAJ=$((MAJ + 1)); MIN=0; PAT=0 ;;
  minor) MIN=$((MIN + 1)); PAT=0 ;;
  patch) PAT=$((PAT + 1)) ;;
esac

NEWNAME="$MAJ.$MIN.$PAT"
NEWCODE=$((CODE + 1))

if sed -i.bak -E "s/(versionCode = )[0-9]+/\1$NEWCODE/" "$FILE" && \
   sed -i.bak -E "s/(versionName = \")([0-9]+\.[0-9]+\.[0-9]+)(\")/\1$NEWNAME\3/" "$FILE"; then
  rm -f "$FILE.bak"
else
  rm -f "$FILE.bak"
  echo "写回失败" >&2
  exit 1
fi

echo "versionName: $NAME -> $NEWNAME  ($LEVEL)"
echo "versionCode: $CODE -> $NEWCODE"

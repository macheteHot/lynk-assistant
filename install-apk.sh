#!/usr/bin/env bash
#
# 领克助手 · 车机 APK 安装脚本
# ------------------------------------------------------------------
# 适用：领克 / 吉利 Flyme Auto 车机（Geely FX11 · CS11_A4 等）
#
# 为什么不用 `adb install`：
#   1. 本车 adbd 会过滤命令里的字面量 "install"（防篡改）；
#   2. 本车不支持流式安装，`adb install` 会报 error: closed。
# 做法：adb push 到 /data/local/tmp，再用变量拆分 "install" 后调用 pm。
#
# 用法：
#   ./install-apk.sh [APK] [选项...]
#
# 选项：
#   -r          覆盖安装（默认开启）
#   -t          允许 test 包（默认开启）
#   -d          允许降级安装
#   -g          安装后授予全部运行时权限
#   -s <serial> 指定设备（多台设备时）
#   -b          先跑 ./build.sh 再安装
#   -u <包名>   卸载指定包（同样拆分关键字），然后退出
#   --list      列出连接的设备后退出
#   -h          显示帮助
#
# 示例：
#   ./install-apk.sh                          # 用 ./lynk-assistant.apk，等价于 -r -t
#   ./install-apk.sh -b                       # 先构建再安装
#   ./install-apk.sh lynk-assistant.apk -g
#   ./install-apk.sh -u io.github.machetehot.lynkassistant
#   ./install-apk.sh -s 1234567890 other.apk
# ------------------------------------------------------------------
set -euo pipefail

APP_APK="./lynk-assistant.apk"

APK=""
SERIAL=""
UNINSTALL=""
DO_BUILD=0
PM_FLAGS=()

usage() {
    sed -n '2,32p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

adb_cmd() {
    if [[ -n "$SERIAL" ]]; then
        adb -s "$SERIAL" "$@"
    else
        adb "$@"
    fi
}

die() { echo "错误：$*" >&2; exit 1; }

# ---------- 参数解析 ----------
while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)  usage 0 ;;
        --list)     adb devices -l; exit 0 ;;
        -r)         PM_FLAGS+=("-r") ;;
        -t)         PM_FLAGS+=("-t") ;;
        -d)         PM_FLAGS+=("-d") ;;
        -g)         PM_FLAGS+=("-g") ;;
        -b)         DO_BUILD=1 ;;
        -u)         shift; UNINSTALL="${1:-}"; [[ -n "$UNINSTALL" ]] || die "-u 需要一个包名" ;;
        -s)         shift; SERIAL="${1:-}"; [[ -n "$SERIAL" ]] || die "-s 需要一个设备序列号" ;;
        -*)         die "未知选项: $1" ;;
        *)
            if [[ -z "$APK" ]]; then
                APK="$1"
            else
                die "只能指定一个 APK 文件"
            fi
            ;;
    esac
    shift
done

command -v adb >/dev/null 2>&1 || die "未找到 adb，请先安装 android-platform-tools"

# ---------- 取 APK 包名（优先 aapt2，其次 aapt / SDK 里的 aapt2） ----------
apk_package() {
    local apk="$1" bin=""
    if command -v aapt2 >/dev/null 2>&1; then
        bin="aapt2"
    elif command -v aapt >/dev/null 2>&1; then
        bin="aapt"
    else
        local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
        bin="$(ls -1 "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)"
    fi
    [[ -n "$bin" ]] || return 0
    "$bin" dump badging "$apk" 2>/dev/null \
        | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1
}

# ---------- 等待设备 ----------
echo ">> 等待设备连接（本车 adbd 不监听 TCP，请用 USB）..."
adb_cmd wait-for-device
echo ">> 当前设备："
adb devices -l | grep -v '^List of devices' | grep . || true

# ---------- 卸载分支 ----------
if [[ -n "$UNINSTALL" ]]; then
    echo ">> 卸载 ${UNINSTALL}（拆分 un+install 关键字）..."
    adb_cmd shell "a=ins; b=tall; pm un\${a}\${b} $UNINSTALL"
    echo ">> 完成 ✅"
    exit 0
fi

# ---------- 构建 / 定位 APK ----------
if [[ -z "$APK" ]]; then
    APK="$APP_APK"
fi

if [[ $DO_BUILD -eq 1 || ! -f "$APK" ]]; then
    if [[ -x ./build.sh ]]; then
        echo ">> 未找到 ${APK}，先执行 ./build.sh ..."
        ./build.sh
    fi
fi

[[ -f "$APK" ]] || die "文件不存在: ${APK}（可直接用 -b 先构建）"

if [[ ${#PM_FLAGS[@]} -eq 0 ]]; then
    PM_FLAGS=("-r" "-t")
fi

NAME="$(basename "$APK")"
DST="/data/local/tmp/$NAME"

# ---------- 推送 ----------
echo ">> 推送 $NAME 到 $DST ..."
adb_cmd push "$APK" "$DST"

# ---------- 安装（关键字拆分绕过 adbd 过滤） ----------
echo ">> 安装中（拆分 install 关键字，命令原文不含 install）..."
adb_cmd shell "a=in; b=stall; pm \${a}\${b} ${PM_FLAGS[*]} $DST"

# ---------- 清理 ----------
echo ">> 清理设备临时文件..."
adb_cmd shell "rm -f $DST" || true

# ---------- 校验 ----------
PKG="$(apk_package "$APK" || true)"
if [[ -n "$PKG" ]]; then
    echo ">> 校验安装结果 ($PKG)："
    adb_cmd shell "pm list packages | grep '$PKG'" \
        || echo "   （未在包列表中找到 ${PKG}，请手动确认）"
fi

cat <<'TIP'

>> 完成 ✅
   注意：这台车机的桌面「我的应用」是白名单驱动的，新装的包默认不会出现在桌面。
   打开「领克助手」→ ③ 桌面应用列表管理 → 把目标应用写进去，然后重启车机。

   签名不一致导致 INSTALL_FAILED_UPDATE_INCOMPATIBLE 时，先卸载再装：
       ./install-apk.sh -u <包名>
TIP

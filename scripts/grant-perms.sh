#!/bin/bash
# 新车首次授权：给 Lynk 管家开安装能力
#
# 用法：
#   1. 车机连电脑，车机弹「允许 USB 调试吗？」→ 勾选「一律允许」→ 允许
#   2. 双击本文件，或在 Git Bash 里执行：
#        bash E:/Code/Lynk-Manager/scripts/grant-perms.sh
#
# 两条命令各自的作用：
#   第一条 appops  → 允许本应用发起安装（否则点记录直接被系统拒绝）
#   第二条 pm grant → 允许本应用写系统设置，用于自己开启无障碍助手
#                     代点系统安装框上的「安装」按钮（那个按钮有防点击保护，
#                     手指点不动，只能用无障碍 performAction 绕过）
#
# 一次生效，重启不丢、覆盖升级不丢。只有「卸载重装」才需要重做。

set -u

PKG='com.duan.lynkmanager'
SERIAL=''

# 找 adb：优先项目内自带，其次 PATH
if [ -x '/e/Code/_tools/platform-tools/adb.exe' ]; then
  ADB='/e/Code/_tools/platform-tools/adb.exe'
elif command -v adb >/dev/null 2>&1; then
  ADB='adb'
else
  echo '找不到 adb。请把 platform-tools 放到 E:\Code\_tools\platform-tools\ 或加入 PATH。'
  exit 1
fi

echo "adb: $ADB"
echo

# 支持传入序列号：bash grant-perms.sh 6a2ba8bf
if [ $# -ge 1 ] && [ -n "$1" ]; then
  SERIAL="$1"
fi

echo '=== 1. 检测设备 ==='
"$ADB" devices -l
echo

if [ -n "$SERIAL" ]; then
  DEV="$SERIAL"
else
  # 取第一台状态为 device 的
  DEV=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')
fi

if [ -z "${DEV:-}" ]; then
  echo '没有检测到已授权的设备。'
  echo '请确认：车机已连电脑、车机屏幕上点了「允许 USB 调试」。'
  exit 1
fi

echo "使用设备: $DEV"
echo

echo '=== 2. 确认本应用已安装 ==='
if ! "$ADB" -s "$DEV" shell "pm list packages $PKG" | grep -q "$PKG"; then
  echo "车机上没找到 $PKG，请先安装 Lynk-Manager APK。"
  exit 1
fi
"$ADB" -s "$DEV" shell "dumpsys package $PKG" | grep -E 'versionName|versionCode'
echo

echo '=== 3. 授权：允许发起安装 ==='
"$ADB" -s "$DEV" shell "appops set $PKG REQUEST_INSTALL_PACKAGES allow"
echo

echo '=== 4. 授权：WRITE_SECURE_SETTINGS（用于自动点「安装」）==='
"$ADB" -s "$DEV" shell "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
echo

echo '=== 5. 验证 ==='
echo '--- 安装权限（应为 allow）---'
"$ADB" -s "$DEV" shell "appops get $PKG REQUEST_INSTALL_PACKAGES"
echo '--- WRITE_SECURE_SETTINGS（应为 granted=true）---'
"$ADB" -s "$DEV" shell "dumpsys package $PKG" | grep 'WRITE_SECURE_SETTINGS'
echo

echo '=== 完成 ==='
echo '拔掉 USB 线，车机上打开 Lynk 管家：'
echo '  · 顶部红条应当消失'
echo '  · 点「传文件」→ 手机扫码 → 上传 APK → 车机点记录 → 自动装完'

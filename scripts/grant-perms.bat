@echo off
chcp 65001 >nul
setlocal

rem 新车首次授权：给 Lynk 管家开安装能力
rem 用法：车机连电脑并允许 USB 调试后，双击本文件即可。

set "PKG=com.duan.lynkmanager"
set "ADB=E:\Code\_tools\platform-tools\adb.exe"

if not exist "%ADB%" (
  where adb >nul 2>&1
  if errorlevel 1 (
    echo 找不到 adb。请把 platform-tools 放到 E:\Code\_tools\platform-tools\ 或加入 PATH。
    pause
    exit /b 1
  )
  set "ADB=adb"
)

echo === 1. 检测设备 ===
"%ADB%" devices -l
echo.

for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
  if "%%B"=="device" (
    set "DEV=%%A"
    goto :found
  )
)

echo 没有检测到已授权的设备。
echo 请确认：车机已连电脑、车机屏幕上点了「允许 USB 调试」。
pause
exit /b 1

:found
echo 使用设备: %DEV%
echo.

echo === 2. 确认本应用已安装 ===
"%ADB%" -s %DEV% shell "pm list packages %PKG%"
echo.

echo === 3. 授权：允许发起安装 ===
"%ADB%" -s %DEV% shell "appops set %PKG% REQUEST_INSTALL_PACKAGES allow"
echo.

echo === 4. 授权：WRITE_SECURE_SETTINGS（用于自动点「安装」）===
"%ADB%" -s %DEV% shell "pm grant %PKG% android.permission.WRITE_SECURE_SETTINGS"
echo.

echo === 5. 验证 ===
echo --- 安装权限（应为 allow）---
"%ADB%" -s %DEV% shell "appops get %PKG% REQUEST_INSTALL_PACKAGES"
echo --- WRITE_SECURE_SETTINGS（应为 granted=true）---
"%ADB%" -s %DEV% shell "dumpsys package %PKG%"
echo.

echo === 完成 ===
echo 拔掉 USB 线，车机上打开 Lynk 管家，顶部红条应消失。
pause

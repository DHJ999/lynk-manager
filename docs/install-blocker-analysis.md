# 车机安装 APK：问题全景、根因与实验方案

> 适用范围：领克 CS11_A4 车机（Android 11 / msmnile_gvmq），serial `6a2ba8bf`。
> 本文是完整经验总结 + 下次连接车机时的实验验证方案。
> 项目：`E:\Code\Lynk-Manager`，当前最新版本 **v1.4.3 (versionCode 53)**。

---

## 一、问题：为什么「装在车上的软件」不能安装 APK

一句话根因：**这台车机的 ROM 从系统层面对「非应用商店来源」的安装做了三重封锁**，
任何一重都能独立地让普通第三方应用的安装功能失效。

### 封锁 1：授权入口被整体移除（最表层）

Android 8.0 起，应用安装 APK 需要持有 `REQUEST_INSTALL_PACKAGES` 的 appops 授权，
授权入口是系统设置里的「安装未知应用」页面。

实测这台车机：

```
MANAGE_UNKNOWN_APP_SOURCES 解析 → com.android.car.frameworkpackagestubs.Stubs$SettingsStub
                                 （Android Automotive 空壳占位，无任何内容）
APPLICATION_SETTINGS            → 0 个 Activity
MANAGE_APPLICATIONS_SETTINGS    → 0 个 Activity
原厂设置 com.geely.settings     → 209 个组件里没有一个是应用管理/权限页
```

车厂（吉利/亿咖通）把整块「应用管理 + 特殊权限」设置砍掉了。
**用户没有任何界面可以点「允许」**——这在标准 Android 上是不存在的困境。

结论：第三方应用即使正确声明了权限，也永远处于「默认拒绝」状态，且无处自救。

### 封锁 2：「安装」按钮被防点击劫持过滤（最隐蔽）

即使通过 adb 把 appops 设成 allow（绕过封锁 1），安装确认界面会正常弹出，
但**点「安装」没有任何反应**；「取消」却完全正常。

```
实测证据（input tap 坐标只差 190px，同一行）：
tap (831,847)  → 「取消」→ 对话框关闭，有效
tap (1021,847) → 「安装」→ 无响应，系统日志无任何安装动作
真手指点击      → 同样无响应（用户亲测）
```

根因：车机版 `PackageInstallerActivity` 的「安装」按钮设置了
`filterTouchesWhenObscured=true`（防点击劫持）。同屏存在一个
`DeleteStagedFileOnResult` 辅助窗口（32px 高，touchableRegion 却是全屏），
使「安装」按钮永久处于「被遮挡」判定，所有触摸事件被 View 自身丢弃。
「取消」按钮没设这个 flag，所以能点。

这是 ROM 层面针对非商店安装的**故意限制**，不是 bug。

### 封锁 3：staged 安装分支损坏（针对 file:// 路径）

`InstallStart` 收到 `file://` 或部分 `content://` Intent 时走「staged 安装」分支
（先把 APK 复制到私有目录再装）。这条分支在这台 ROM 上是坏的：

```
同一个 APK：
  pm install（shell 通道）        → Success
  InstallStart（file/content）    → InstallFailed「未安装应用。」
    任务栈显示 baseActivity=DeleteStagedFileOnResult
    （它的职责是装完删源文件，但装这一步就失败了）
```

大 APK（130MB 汽水音乐）100% 复现；这解释了「为什么用户上传的 APK 点安装失败，
adb 却能装」。

---

## 二、已验证的解法（v1.4.3 实现并实测通过）

针对三重封锁，逐层绕过：

| 封锁 | 解法 | 验证结果 |
|---|---|---|
| 授权无处点 | `adb shell appops set ... REQUEST_INSTALL_PACKAGES allow` + `pm grant ... WRITE_SECURE_SETTINGS`，一次有效、重启不丢 | ✅ |
| 「安装」点不动 | 自带无障碍服务 `InstallClickService`，用 `performAction(ACTION_CLICK)` 点按钮（走无障碍通道，不经触摸分发，无视 filterTouchesWhenObscured）；应用持 WRITE_SECURE_SETTINGS 可**自己把自己写进无障碍白名单**，无需用户操作 | ✅ 全新安装 5 秒自动完成 |
| staged 分支损坏 | 不走 InstallStart，改用 **PackageInstaller 会话 API**（createSession → openWrite → fsync → commit），会话机制不经过坏分支 | ✅ 130MB APK 正常安装 |
| 服务重绑超时 | 预启用（`onResume` 即启用不武装）+ 点安装时才武装 + 12 秒等待 + 第 8/20/32 轮重挂 | ✅ 3/3 首点成功 |

关键实现细节（踩过的坑）：

1. **无障碍服务必须「摘除再挂回」**：应用被 force-stop/回收后服务掉线，
   重写相同的 enabled_accessibility_services 值不会触发重绑，
   必须先移除自己再写回，制造真实值变化。
2. **提交会话前必须等服务就绪**（`InstallClickService.isReady()`）：
   确认界面先弹出来而服务没连上 = 没人点按钮 = 白等。未就绪时干脆不提交，
   提示用户重试，宁可失败也不让用户面对"点了没反应"的困惑界面。
3. **武装用静态标志不用广播**：服务和 Activity 同进程，广播会在服务绑定完成前
   发出而丢失；`static volatile boolean armed` + `trigger()` 可靠。
4. **点击匹配必须严格**：只认文本恰为「安装」的 Button，
   避免「安装未知应用」等文案被误点。

### 已知遗留边界（v1.4.4 已解决）

- ~~**force-stop 后立即安装**：无障碍服务重绑偶发超时（3 轮摘挂仍不生效），
  1.4.3 会明确提示「请重新点一次」。~~
  **v1.4.4 已修复**：改为「预启用 + 按需武装」分离设计——
  `onResume` 时就启用无障碍服务但**不武装**（避免误点别的应用的安装窗口），
  用户点安装时才 `trigger()` 武装。这样在用户上传/选文件那几十秒里
  服务早已连上，点安装即可立刻代点。
  等待窗口同时从 4.8 秒（16×300ms）延长到 12 秒（40×300ms），
  重挂时机加密到第 8/20/32 轮。

  实测（三次连续，最恶劣时序：force-stop 后 1 秒即进入并立刻点记录）：
  ```
  无障碍助手已预启用（未武装）
  无障碍服务已重挂（摘除再挂回以触发重绑）
  无障碍助手就绪
  会话写入完成 130437097 B
  无障碍点击安装 → true
  [第5秒] ✅ 已装上
  ```
  **3/3 首点即成功，每次 5 秒**，不再需要重试。

---

## 三、未验证的替代方案（下次连接车机时按此实验）

用户手里有一个反编译的原版 AOSP Settings（`E:\Code\_scratch\lynk-setting`，
`com.android.settings` v9，sharedUserId=android.uid.system），
**manifest 确认包含完整的 `ManageAppExternalSourcesActivity`（安装未知应用授权页）
及全套应用管理**。如果它能装上车，就能从根上恢复官方授权入口，
此后任何应用都能自己申请授权，不再依赖无障碍方案。

车机上实际运行的 `com.geely.settings`（`/system/app/OneOS-Settings/OneOS-Settings.apk`，
161MB）**不包含**该页面（aapt 解析确认 0 处声明）。
原版从未装机：`pm list packages | grep com.android.settings` 为空。

### 实验步骤（按风险从低到高，逐步推进）

**步骤 0：签名一致性分析（零风险，纯本地，先做）**

```bash
# 车机 platform 签名的公钥来源：任何一个系统 app 的签名块
D:/Git/bin/bash.exe -lc "
export MSYS_NO_PATHCONV=1
A=/e/Code/_tools/platform-tools/adb.exe
S=6a2ba8bf
# 拉一个已知的 platform 签名应用作参照（framework-res 或 geely.settings）
\$A -s \$S shell 'cp /system/framework/framework-res.apk /sdcard/fr.apk'
\$A -s \$S pull /sdcard/fr.apk 'E:/Code/_scratch/fr.apk'
"
# 本地用 apksigner 或 keytool 对比：
apksigner verify --print-certs E:/Code/_scratch/fr.apk
apksigner verify --print-certs E:/Code/_scratch/OneOS-Settings.apk
apksigner verify --print-certs E:/Code/_scratch/lynk-setting/classes.dex 对应的原 apk
# 判断：反编译的原版 Settings 是否用同一 platform keystore 签名。
# 若签名不同 → sharedUser 校验必失败，实验到此结束，不再往车机装任何东西。
```

**步骤 1：车机侧非破坏性校验（低风险）**

车机 ROM 的 `pm install` 不支持 `--dry-run`（实测 Unknown option），
改用「真装但立即可回滚」的方式，且**先做备份**：

```bash
# 1) 备份现有 geely.settings（万一要还原）
adb -s 6a2ba8bf pull /system/app/OneOS-Settings/OneOS-Settings.apk E:/Code/_scratch/OneOS-Settings.apk.bak
# （已有一份拉取副本：E:/Code/_scratch/OneOS-Settings.apk，161MB）

# 2) 记录当前状态快照，便于回滚比对
adb -s 6a2ba8bf shell 'settings get secure enabled_accessibility_services
  appops get com.duan.lynkmanager REQUEST_INSTALL_PACKAGES
  pm list packages -s | grep -i settings' > E:/Code/_logs/pre_settings_swap.txt
```

**步骤 2：尝试安装原版 Settings（中风险，可回滚）**

```bash
# 把反编译包重新打包成 apk（用户手里是反编译目录，需要 apktool b 回编）
# 回编后必须签名：优先用步骤 0 分析出的 platform 签名；
# 若拿不到 keystore，此路不通，直接放弃（sharedUser 不匹配装不上）。
apktool b lynk-setting -o AOSP-Settings-rebuild.apk
apksigner sign --ks <platform keystore> AOSP-Settings-rebuild.apk

# 推送安装
adb -s 6a2ba8bf push AOSP-Settings-rebuild.apk /data/local/tmp/aosp_settings.apk
adb -s 6a2ba8bf shell pm install -r /data/local/tmp/aosp_settings.apk
# 预期失败模式：INSTALL_FAILED_SHARED_USER_INCOMPATIBLE（签名不符）
#              INSTALL_FAILED_ALREADY_EXISTS（包名冲突）等
```

**步骤 3：若安装成功，验证授权页**

```bash
adb -s 6a2ba8bf shell am start -a android.settings.MANAGE_UNKNOWN_APP_SOURCES \
  -d package:com.duan.lynkmanager
# 预期：弹出「允许 Lynk 管家安装应用」的开关页（即 ExternalSourcesDetails）
# 若弹出 → 从此应用可引导用户自己开授权，无障碍方案降级为兜底
```

**步骤 4：若失败，回滚与清理**

```bash
adb -s 6a2ba8bf shell pm uninstall com.android.settings   # 若装的是 data 区
# 装不进 data 区（sharedUser 应用通常要进 /system）则说明需要 root/刷机，
# 当前会话没有 root，直接放弃该方案，继续用 1.4.3 的无障碍方案（已稳定可用）。
adb -s 6a2ba8bf shell rm /sdcard/fr.apk /data/local/tmp/aosp_settings.apk
```

### 决策树

```
步骤 0 签名一致？
├─ 否 → 放弃替换 Settings 方案；1.4.3 无障碍方案即最终方案
└─ 是 → 步骤 2 真装
   ├─ INSTALL_FAILED_SHARED_USER_INCOMPATIBLE → 同上，放弃
   ├─ 装上 → 步骤 3 授权页可用？
   │  ├─ 可用 → 双保险：官方页 + 无障碍兜底，体验最佳
   │  └─ 不可用（ROM 仍拦截）→ 卸掉恢复原状，无障碍方案为准
   └─ 装不上/装坏 → pm uninstall 回滚，无障碍方案为准
```

### 重要提醒

- 反编译目录**不是可直接安装的 APK**，需要 apktool 回编 + 签名，
  回编一个 5.5MB dex 的系统应用存在兼容风险（资源 id 重排等），
  若能找到**原始未拆包的** `com.android.settings` APK 是首选。
- 任何一步失败都不影响现有功能：1.4.3 已独立闭环。
- 操作前后各拍一次 `settings get secure` 快照，保证可回滚。

---

## 四、经验沉淀（通用规律）

1. **车机/车厂 ROM 对第三方安装的限制是体系化的**：授权入口、运行时按钮、
   安装分支三层一起封。单点绕过（比如只拿 appops allow）必然撞到下一层。
2. **`filterTouchesWhenObscured` 是隐蔽的「按钮级封锁」**：表象是"点了没反应"，
   实际触摸被 View 丢弃，日志毫无痕迹。诊断方法：对比同屏其它按钮的响应、
   检查窗口栈里的辅助窗口与 touchableRegion。
3. **无障碍是应用层唯一能"代点"受限按钮的通道**：前提是能拿到
   `WRITE_SECURE_SETTINGS`（adb 授予一次）或用户手动开启。
   系统 shell 的 `input tap/keyevent` 都过不了按钮级过滤。
4. **`pm install`（shell 通道）与应用层安装是两条完全不同的链路**：
   shell 能装 ≠ 应用能装。排查时必须用应用自己的身份走完整流程。
5. **系统应用的 sharedUserId 是硬门槛**：不带 platform 签名，
   任何针对 system uid 应用的安装尝试都会失败，且失败信息只在
   `logcat`/`PackageManager` 侧，`pm install` 的 stderr 可能很含糊。

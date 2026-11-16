<h1 align="center">Lynk-Manager · 车机应用管家</h1>

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0.3-blue" alt="version">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Gradle-9.1.0-02303A?logo=gradle&logoColor=white" alt="Gradle">
  <img src="https://img.shields.io/badge/license-PolyForm%20Noncommercial%201.0.0-orange" alt="license">
  <img src="https://img.shields.io/badge/status-personal%20use-lightgrey" alt="status">
</p>

领克车机的应用管理与调试工具：列出已安装应用、卸载、强制停止、导出 APK、跳转系统详情，
并内置 ADB 开关——**不用再进工厂模式**。

> **⚠️ 免责声明**
>
> 个人自用工具，与吉利、领克、亿咖通（ECARX）及任何车机厂商无关，非官方、未授权、未做安全审计。
> 它会直接改动系统状态：写 `persist.sv.debug.adb_enable` 等系统属性、调 `PackageManager` 卸载应用、
> `killBackgroundProcesses` 停进程。这些操作有实际风险，可能导致功能异常、数据丢失，甚至需要刷机恢复。
> 请只在**你本人拥有或有权操作**的设备上使用；因使用本工具造成的任何直接或间接损失，作者不承担责任。
> **若不同意上述条款，请立即停止使用并删除本应用。**

- 包名 `com.duan.lynkmanager` · 版本 1.4.6（versionCode 56）
- minSdk 23 / targetSdk 25 / compileSdk 36
- 仅二维码用到 ZXing core，其余纯平台 API

> **🚗 新车首次使用必须先做的两步授权**：详见
> [docs/first-setup.md](docs/first-setup.md)。
> U 盘装完后还不能直接装 APK，需要在电脑上跑**两条 adb 命令**
> （`appops` 开安装权限 + `pm grant` 给 WRITE_SECURE_SETTINGS），
> 一次生效、重启不丢、只有卸载才需重做。之后全程手机操作。
> 未授权时应用顶部会常驻红条提示，点开可一键复制命令。

> **📋 车机安装 APK 的三重封锁与解法**：详见
> [docs/install-blocker-analysis.md](docs/install-blocker-analysis.md)。
> 包含完整根因分析（授权入口被砍 / 安装按钮防点击过滤 / staged 分支损坏）、
> v1.4.3 的无障碍自动点击方案，以及下次连接车机时
> 验证"替换原版 Settings"替代方案的完整实验步骤与决策树。
>
> **🎲 替代方案成功率推演**：详见
> [docs/alt-plan-probability.md](docs/alt-plan-probability.md)。
> 分步概率计算：签名一致 ~5% × 回编成功 ~35% × 安装接受 ~40% × 授权页可用 ~50%
> → **总成功率约 0.35%**。结论：下次先花 5 分钟做签名分析（零风险判决），
> 95% 概率直接排除该方案，届时集中精力修 1.4.3 的 force-stop 边界。
>
> **✅ 步骤0已实测：方案排除**：详见
> [docs/step0-signature-result.md](docs/step0-signature-result.md)。
> 三方签名比对完成：车机平台/原厂设置均为 **DesaySV（德赛西威）** 签发
> (`F5:1E:54:A4:…:2B:03:B5`)，而反编译包是 **EcarX（亿咖通）** 2018 年签发
> (`3D:89:C5:63:…:C3:CC:73`)，四维全不一致。
> `sharedUserId=android.uid.system` 的签名校验必然失败
> (`INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`)，**替换 Settings 方案终止**。
> 无障碍方案是唯一可行路径。

## 功能

### 应用管理

| 功能 | 说明 |
|---|---|
| 应用网格 | 卡片视图：图标、软件名、包名、版本/大小；包名只留后两段（`com.ecarx.cs11.launcher` → `cs11.launcher`），版本号超 12 字符截断，系统应用橙色、用户应用蓝色 |
| 刷新列表 | 筛选行右侧「刷新」，重扫已安装应用；保留当前搜索词与筛选档位，完成后提示总数 |
| 搜索 / 筛选 | 按名称或包名实时过滤；全部 / 用户应用 / 系统应用 |
| 点击卡片 | 菜单：强制停止 / 卸载 / 导出 APK / 应用详情；**系统应用无「卸载」** |
| 强制停止 | `killBackgroundProcesses`，只对后台进程有效 |
| 导出 APK | 复制到 `files/export/`，在「传文件」页启动服务后用浏览器下载取走 |
| 应用详情 | 跳转系统应用信息页，可看权限与存储占用 |

### 界面密度放大

车机主屏 `2560x1600 @160dpi`，1dp = 1px，系统密度下界面偏小。
`BaseActivity.attachBaseContext()` 把资源密度放大 `UI_SCALE = 1.75`，逻辑尺寸约 `1463x914dp`；
要更大或更密只改这一个数。

> 必须做在 **Activity** 上。放在 `Application.attachBaseContext()` 不生效——Activity 的
> Resources 来自 ActivityThread。

### ADB 开关（右上角）

**不进工厂模式也能开**。这台车机的 SELinux 是 `Permissive`，普通应用执行 `setprop`
不会被拦（实测 `setprop persist.sv.debug.adb_enable` 返回 `rc=0`），所以能直接改到真正的开关上。

| 操作 | 说明 |
|---|---|
| 单击 | **只读**。弹出面板列出：系统 `adb_enabled`、厂商 `persist.sv.debug.adb_enable`、`adbd` 服务、`sys.usb.config`、写入权限 |
| 长按 | 真正开关 ADB，二次确认后写三个属性 |
| 按钮三态 | 绿「ADB 已通」= USB 配置含 adb 且 adbd 在跑且厂商开关为开；黄「ADB 待开启」= 通道在但厂商开关是关的；红「ADB 未通」 |

长按后写入的三个属性：

| 属性 | 作用 |
|---|---|
| `persist.sv.debug.adb_enable` | 车机真正的调试开关，`adbd` 靠它启动 |
| `persist.sys.usb.config=adb` | 重启后 USB 仍带 adb 功能 |
| `sys.usb.config=adb` | 让 init 立即重配 USB，`adbd` 当场生效 |

**代价**：第三个属性会触发 USB 重新枚举，电脑端掉线约 1 分钟才恢复。
这是让 adbd 立刻生效的唯一办法，不是 bug。

开机自动开启（`BootReceiver`）也是写 `persist.sv.debug.adb_enable`，
不再依赖无效的 `Settings.Global.ADB_ENABLED`，所以重启后不需要再进工厂模式。

> 早期版本只写 `Settings.Global.ADB_ENABLED`，那个值在这台车机上不启动 adbd，
> 还会让 USB gadget 重枚举成 `PID_4EE7` 诊断模式、直接把连接弄断。

### 传文件（上传 / 下载）

进入页面后**自动启动服务**，屏幕显示二维码和地址，同一局域网内用浏览器打开：

| 地址 | 作用 |
|---|---|
| `/` | 上传页：**只收 APK**，选文件后可看文件类型徽章、文件名、大小，支持单个移除与多次追加，带进度条 |
| `/dl` | 下载页：列出上传的文件与导出的 APK，点「下载」直接存到本机 |

#### WiFi 直连（两个方向都支持）

车机设置里没有 WiFi 页面，所以联网能力做在应用内。

**点「启动服务」后全自动**：

1. 检测有没有可用局域网 IP；没有就自动开热点（`startLocalOnlyHotspot`）
2. 屏幕出现**两个二维码**
   - **① 扫码连热点** —— 内容是标准 `WIFI:T:WPA;S:<ssid>;P:<pass>;;`，手机自带相机/微信扫一扫直接弹「加入网络」，不用手输随机密码
   - **② 扫码上传** —— 内容是 `http://<ip>:8080`，连上热点后扫它打开上传页
3. DHCP 拿到 IP 后**自动启动服务**，②号码随即生效
4. 点「停止服务」→ **同时关闭服务和热点**，二维码一并清空
5. 下次再点「启动服务」→ 重新开热点（系统会重新生成一组 SSID/密码）→ 重新扫码

| 按钮 | 做什么 | 什么时候用 |
|---|---|---|
| 启动服务 | 有局域网就直接起服务；没有就自动开热点并出二维码 | 日常唯一入口 |

> 「车机开热点 / 连手机热点」两个手动按钮已移除：启动服务本身就自动处理了开热点，
> 需要连手机热点的场景直接在系统 WiFi 里连即可，留着反而增加界面噪音。

- 网络变化时地址栏和二维码**自动刷新**（注册 `ConnectivityManager` 回调）
- 状态行如实区分三种情况：`已连接 + IP` / `WiFi 已开启但没连上热点` / `WiFi 未开启`
- 多个私有网段时，地址栏下方列出全部候选地址，逐个试

> `startLocalOnlyHotspot` 的 SSID 固定 `AndroidShare_` 前缀，密码系统随机生成。
> Android 11 上 `getWifiConfiguration()` 字段会被抹空，改读 `getSoftApConfiguration()` 能拿到 SSID 与 passphrase（需 `ACCESS_FINE_LOCATION`）。
> 该热点与车机原厂热点是两套，不互相干扰。热点租约跟着应用进程，退出应用即关闭。

- **只接受 `.apk`**：页面选择框限定 `.apk`，前端会先把非 APK 挑出来提示，服务端再校验一次扩展名，非 APK 一律拒绝并回 `SKIP xxx 不是 apk`
**点记录直接安装**：上传列表里点任意一条 APK 记录即可安装。

安装走 `PackageInstaller` 会话（API 21+），不是 `ACTION_VIEW` Intent：

```java
PackageInstaller.SessionParams p = new SessionParams(MODE_FULL_INSTALL);
int id = pi.createSession(p);
Session s = pi.openSession(id);
// 写入 APK 字节流 → fsync → commit(PendingIntent)
```

`InstallResultReceiver` 处理回传结果：`STATUS_PENDING_USER_ACTION` 时拉起系统确认界面，
`STATUS_SUCCESS` 弹「安装成功」，其余错误码翻译成中文提示（签名冲突/空间不足/被拦截等）。
会话异常时自动回退到 `ACTION_VIEW + content://`（走 `ApkProvider`）。

> **为什么不用「去设置」引导授权**：这台车机的原厂系统**没有** `MANAGE_UNKNOWN_APP_SOURCES` 页面
> （`query-activities` 只能查到 `com.android.car.frameworkpackagestubs.Stubs$SettingsStub`，
> 那是 Android Automotive 的空壳占位，点进去什么都没有）。同理 `APPLICATION_SETTINGS`、
> `MANAGE_APPLICATIONS_SETTINGS`、`APPLICATION_DETAILS_SETTINGS` 也都是 0 个 Activity。
> 所以授权只能靠电脑执行一次，完整两条命令见下方
> [首次安装后需要在电脑执行一次性授权](#首次安装后需要在电脑执行一次性授权)。
> 应用内点安装若发现未授权，会直接弹出这两条命令并提供「复制命令」，不再弹「去设置」。
- 页面左上角有「← 返回」，不用靠系统手势退出
- 上传落到 `files/uploads/`，导出落到 `files/export/`
- 两个记录列表都直接扫描目录生成，文件在记录就在；删除带二次确认，同时删本地文件
- 手写极简 HTTP 实现，不引框架；`ServerSocket` 显式绑 `0.0.0.0:8080`（覆盖全部网卡），端口 8080 起自动避让，单次上传上限 256 MB

**实车验证（2026-11-14 / v1.2.2）**：

```
点启动服务 → 自动开热点 AndroidShare_5498
           → 网络就绪 192.168.123.68
           → 服务地址 http://192.168.123.68:8080
两个二维码 bounds：[859,370][1244,755] / [1314,370][1699,755]
           → 同 y=370、同尺寸 385×385，水平对齐
中文文件名上传：测试应用-中文名.apk → OK，磁盘落地同名文件（1.1 MB）
点停止服务 → 「已停止服务并关闭热点」，wlan1 无 IP、8080 消失
```

**中文文件名乱码的修法**：multipart 分段头按 `ISO-8859-1` 解析（1 字节=1 字符，保证二进制安全），
但浏览器发的是 UTF-8 字节。所以取到文件名后要「按 ISO-8859-1 还原成字节 → 再按 UTF-8 解码」，
并额外兼容 RFC 5987 的 `filename*=UTF-8''...` 形式。纯 ASCII 文件名经这条路径保持不变。

**卸载清理**：所有产物都在应用私有目录（`/data/data/<包名>` 与 `Android/data/<包名>`），
卸载时由系统整体删除，无需额外清理代码。前提是不往公共目录（`Download/`、U 盘）写文件。

## 安装

传到车机用 [lynk-adb-tool](https://github.com/DHJ999/lynk-adb-tool)：图形界面选 APK → 点安装，
不用敲命令，也不用记参数。也可以 U 盘拷 APK 到车机，用车机文件管理器点开安装。

装完还要在电脑上执行**两条**授权命令（`appops` + `pm grant`），见下一节。

> 文件名不要含 `install` 字样——车机 adbd 会过滤含该字符串的整条 shell 命令。

## 首次安装后需要在电脑执行一次性授权

装完 APK 之后**还不能直接装应用**，需要两条 adb 命令，**一次性、约 1 分钟**：

```bash
adb shell appops set com.duan.lynkmanager REQUEST_INSTALL_PACKAGES allow
adb shell pm grant com.duan.lynkmanager android.permission.WRITE_SECURE_SETTINGS
```

| 命令 | 作用 | 不做会怎样 |
|---|---|---|
| `appops set ... REQUEST_INSTALL_PACKAGES allow` | 允许本应用发起安装 | 点记录后系统直接拒绝，日志 `User rejected permissions` |
| `pm grant ... WRITE_SECURE_SETTINGS` | 允许本应用写系统设置 | 安装框弹出但「安装」按钮点不动 |

**第二条为什么必需**：车机的「安装」按钮带防点击劫持保护（`filterTouchesWhenObscured`），
手指点、`input tap`、`keyevent` 全部无效——同一个对话框的「取消」能点，两个按钮只差 190px。
唯一可行的是无障碍 `performAction(ACTION_CLICK)`；而开启无障碍服务需要写
`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`，这就要 `WRITE_SECURE_SETTINGS`。

两条都属于 `signature|privileged|development` 级，普通安装拿不到，但带 `development`
标志所以可用 adb 授予，**不需要 root**。

**一键脚本**（省得手敲）：

- `scripts/grant-perms.bat` — Windows 双击运行
- `scripts/grant-perms.sh` — Git Bash 用

脚本会检测设备 → 确认应用已装 → 执行两条授权 → 打印验证结果。

授权后重启、断电、拔插都不会丢，只有卸载重装才需重新授权。
应用列表、搜索、强制停止、导出这些功能**不需要**该授权。

> 未授权时应用主界面顶部会常驻红色提示条，点开可一键复制这两条命令，
> 授权完成后切回应用红条自动消失。

详细说明见 [docs/first-setup.md](docs/first-setup.md)。

## 构建

用 Android Studio 打开即可构建，命令行则是：

```bash
# JDK 17+，Android Studio 自带的 jbr 也可以
export JAVA_HOME=/path/to/jdk
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

`sdk.dir` 写在 `local.properties`（该文件不进版本库），指向本机 SDK 路径。

产物 `app/build/outputs/apk/release/app-release.apk`，归档为 `_dist/Lynk-Manager-v<版本>.apk`。
release 用 debug keystore 签名，仅供内部测试，不能上架。

> `gradlew` 首次运行需要联网下载 Gradle 发行版；网络受限时换镜像，或在本机已装 Gradle 9.1
> 的环境直接执行 `gradle assembleRelease`。

## 结构

```
app/src/main/java/com/duan/lynkmanager/
  MainActivity.java        应用列表、搜索、筛选、操作菜单
  AppListAdapter.java      列表行渲染（图标/名称/包名/版本/大小/标签）
  AppInfo.java             数据模型
  AdbSwitchActivity.java   ADB 开关页（写入、回读校验、授权命令、日志）
  LanTransferActivity.java 传文件页：启动服务、二维码、上传与导出记录、点记录安装
  SimpleHttpServer.java    极简 HTTP 服务：上传页（只收 APK）/ 下载页 / multipart 解析 / 文件下载
  ApkProvider.java         只暴露 uploads/export 下 .apk 的 ContentProvider，供系统安装器读取
  BootReceiver.java        开机自动写回 adb_enabled=1
app/src/main/res/drawable-nodpi/   图片资源（不随屏幕密度缩放）
app/src/main/AndroidManifest.xml
```

## 已知限制

- **系统应用无卸载入口**：需要 `DELETE_PACKAGES` 特权权限，普通签名拿不到
- **不能清数据、不能冻结/停用**：分别需要 `CLEAR_APP_USER_DATA`、`CHANGE_COMPONENT_ENABLED_STATE`
- **读不到 logcat**：`READ_LOGS` 是 signature 级权限，抓日志用 [lynk-adb-tool](https://github.com/DHJ999/lynk-adb-tool)
- **强制停止只对后台进程有效**：前台或常驻进程停不掉，这是 Android 机制
- **ADB 开关依赖 SELinux Permissive**：本机车机是 Permissive，应用能直接 `setprop`；若换到 Enforcing 的机型，开关会失败并在弹窗里显示命令回执
- **开启 ADB 会掉线约 1 分钟**：写 `sys.usb.config` 必然触发 USB 重枚举，属正常代价
- **USB 物理掉线属于系统层**：线材接触、车机休眠、adbd 半死都会让 `adb devices` 变 offline，adbd 重启实测约 70 秒，应用侧无法代劳
- **开机自启可能被拦**：部分省电策略拦截开机广播，需加自启动白名单
- **传文件要求同一局域网**：车机开热点或与设备连同一 Wi-Fi，4G/5G 下不互通
- **服务仅前台可靠**：退出应用或进程被回收后服务停止，需重新启动
- **明文 HTTP**：仅限局域网使用，不要在公共网络上开

## 许可

[PolyForm Noncommercial 1.0.0](LICENSE)

可以自由使用、修改、分发本项目和它的编译产物，**但仅限非商业目的**：不得用于销售、
付费服务、广告变现，也不得集成进商业产品。完整条款见 [LICENSE](LICENSE)。

本项目按「现状」提供，不附带任何明示或暗示的担保。使用前请阅读文首的免责声明；
一旦使用、复制或分发本项目的任何部分，即视为你已理解并接受上述条款。

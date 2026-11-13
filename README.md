<h1 align="center">Lynk-Manager · 车机应用管家</h1>

<p align="center">
  <img src="https://img.shields.io/badge/version-1.3.2-blue" alt="version">
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

- 包名 `com.duan.lynkmanager` · 版本 1.3.2（versionCode 27）
- minSdk 26 / targetSdk 34 / compileSdk 36
- 仅二维码用到 ZXing core，其余纯平台 API

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

启动服务后屏幕显示二维码和地址，同一局域网内用浏览器打开：

| 地址 | 作用 |
|---|---|
| `/` | 上传页：选文件后可看缩略图 / 扩展名、文件名、大小，支持单个移除与多次追加，带进度条 |
| `/dl` | 下载页：列出上传的文件与导出的 APK，点「下载」直接存到本机 |

- 上传落到 `files/uploads/`，导出落到 `files/export/`
- 两个记录列表都直接扫描目录生成，文件在记录就在；删除带二次确认，同时删本地文件
- 手写极简 HTTP 实现，不引框架；端口 8080 起自动避让，单次上传上限 256 MB

**卸载清理**：所有产物都在应用私有目录（`/data/data/<包名>` 与 `Android/data/<包名>`），
卸载时由系统整体删除，无需额外清理代码。前提是不往公共目录（`Download/`、U 盘）写文件。

## 安装

传到车机用 [lynk-adb-tool](https://github.com/DHJ999/lynk-adb-tool)：图形界面选 APK → 点安装，
不用敲命令，也不用记参数。

装完还要在电脑上执行一次授权，见下一节。

> 文件名不要含 `install` 字样——车机 adbd 会过滤。

## 首次安装后需要在电脑执行一次性授权

`WRITE_SECURE_SETTINGS` 属于 `signature|privileged|development` 级权限，普通安装拿不到，
但带 `development` 标志，可以用 adb 授予，不需要 root：

```bash
adb shell pm grant com.duan.lynkmanager android.permission.WRITE_SECURE_SETTINGS
```

授权后重启、断电、拔插都不会丢，只有卸载重装才需重新授权；之后开关 ADB 全在应用里点，
开发者选项可以一直关着。应用列表、搜索、强制停止、导出这些功能**不需要**该授权。

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
  LanTransferActivity.java 传文件页：启动服务、二维码、上传与导出记录
  SimpleHttpServer.java    极简 HTTP 服务：上传页 / 下载页 / multipart 解析 / 文件下载
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

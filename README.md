<h1 align="center">Lynk-Manager · 车机应用管家</h1>

<p align="center">
  <img src="https://img.shields.io/badge/language-Java-orange?logo=openjdk&logoColor=white" alt="language">
  <img src="https://img.shields.io/badge/arch-universal-blue" alt="arch">
  <img src="https://img.shields.io/badge/license-PolyForm%20Noncommercial%201.0.0-orange" alt="license">
  <img src="https://img.shields.io/github/v/release/DHJ999/lynk-manager?display_name=tag&sort=semver" alt="version">
  <img src="https://img.shields.io/github/downloads/DHJ999/lynk-manager/total" alt="downloads">
  <img src="https://img.shields.io/github/stars/DHJ999/lynk-manager" alt="stars">
</p>

领克车机的应用管理工具：列出应用、卸载、强停、导出 APK，内置 ADB 开关，
还能局域网传 APK 并直接安装。

## ⚠️ 免责声明

**请在使用前完整阅读本节。一旦安装、使用或分发本工具，即视为你已理解并接受以下全部条款。**

### 本工具是什么

个人自用的技术学习产物，非官方、非商业、未授权、未经过任何形式的安全审计与兼容性测试。
与吉利汽车、领克汽车、亿咖通（ECARX）、德赛西威（DesaySV）及任何车机厂商、
经销商、服务商均无关联，不代表其立场，也未获得其认可或授权。

### 它会做什么

本工具会**直接修改车机的系统状态**，包括但不限于：

- 写入系统属性（`persist.sv.debug.adb_enable`、`sys.usb.config` 等），开启或关闭 ADB 调试通道
- 调用 `PackageManager` 卸载应用、通过 `PackageInstaller` 安装任意 APK
- 通过 `killBackgroundProcesses` 强制停止进程
- 开启本地热点、启动局域网 HTTP 服务、在系统无障碍服务列表中注册自身

这些操作**超出普通应用的权限边界**，属于系统级改动。

### 可能造成的后果

以下风险真实存在，且部分不可逆：

| 风险 | 具体表现 |
|---|---|
| **系统功能异常** | 卸载系统应用导致车机功能缺失（车控、语音、导航等），可能需要刷机恢复 |
| **数据丢失** | 卸载应用会连带清除其数据，且无法找回 |
| **ADB 连接中断** | 开关 ADB 会触发 USB 重新枚举，电脑端掉线约 1 分钟，运行中的调试会话会中断 |
| **车机变砖** | 极端情况下错误操作可能导致系统无法启动，需到 4S 店或专业维修恢复，费用自负 |
| **保修失效** | 自行修改系统状态可能被厂商认定为人为损坏，导致保修条款失效 |
| **安全隐患** | 开启 ADB 后，任何物理接触车机 USB 接口的设备都可获得调试权限；局域网传文件为明文 HTTP，同网络下可被嗅探 |
| **法律风险** | 在非本人所有或未获授权的车辆上使用，可能违反相关法律法规或车辆使用协议 |

### 责任划分

**你需自行承担使用本工具的全部风险与后果。**

作者不对因使用或无法使用本工具而产生的任何直接、间接、偶然、特殊或后果性损失负责，
包括但不限于：车辆损坏、数据丢失、业务中断、保修失效、维修费用、法律纠纷、
第三方索赔、利润损失等。即使作者已被告知此类损失的可能性，亦不承担责任。

**若你不同意上述任何一条，请立即停止使用并删除本应用。**

### 使用前提

使用本工具即表示你确认：

- 你是该车辆的**所有权人或已获得明确授权的使用者**
- 你理解相关操作的原理与风险，具备相应的技术判断能力
- 你接受修改系统状态可能带来的全部后果
- 你不会将本工具用于任何商业用途或非法目的

## ⚠️ 装完必须做这一步

APK 装上车后**还不能直接装应用**，要在电脑上跑两条命令，一次性：

```bash
adb shell appops set com.duan.lynkmanager REQUEST_INSTALL_PACKAGES allow
adb shell pm grant com.duan.lynkmanager android.permission.WRITE_SECURE_SETTINGS
```
或者双击 `scripts/grant-perms.bat`（Git Bash 用 `grant-perms.sh`），自动执行并验证。

| 命令 | 作用 | 不做会怎样 |
|---|---|---|
| `appops ... allow` | 允许发起安装 | 点装记录直接被系统拒绝 |
| `pm grant ...` | 允许写系统设置 | 安装框弹出，但「安装」按钮点不动 |

**为什么必须两条**：这台车机的「安装」按钮有防误触保护，手指点、`input tap`、
`keyevent` 全部无效（同屏的「取消」却能点）。只能用无障碍服务代替点击，
而开启无障碍需要写系统设置 → 所以要第二条权限。

授权一次，重启/断电/升级都不丢，**只有卸载重装才需重做**。
没授权时应用顶部会有红条提示，点开能一键复制命令。

## 功能

### 应用管理

| 功能 | 说明 |
|---|---|
| 应用网格 | 图标、名称、包名、版本/大小；系统应用橙色、用户应用蓝色 |
| 搜索筛选 | 按名称或包名实时过滤；全部 / 用户 / 系统 |
| 点击卡片 | 强制停止 / 卸载 / 导出 APK / 应用详情（系统应用不显示卸载） |
| 刷新 | 重扫已装应用，保留当前搜索和筛选 |

### ADB 开关

右上角按钮，**不用进工厂模式**。

| 操作 | 效果 |
|---|---|
| 单击 | 只读，弹面板显示 adb_enabled / 厂商开关 / adbd / USB 配置 |
| 长按 | 真正开关，二次确认后写三个属性 |
| 三态 | 绿=已通，黄=待开启，红=未通 |

开 ADB 会让 USB 重新枚举，电脑端掉线约 1 分钟，属正常现象。

### 传文件

进页面自动启动服务，手机连同一 WiFi 后浏览器访问，方向双向。

**用法**：点「启动服务」→ 屏幕出现两个二维码 → 手机扫①连热点 → 扫②打开上传页 → 选 APK 上传。

| 地址 | 作用 |
|---|---|
| `/` | 上传页，只收 APK，带进度条 |
| `/dl` | 下载页，取走上传的文件和导出的 APK |

- 没网络时自动开热点（`AndroidShare_xxxx`），有网络直接起服务
- 点「停止服务」同时关闭热点
- 上传中的文件不能删（删除按钮变灰），防止进度卡死
- 中文文件名不会乱码

### 安装 APK

上传后点记录即可安装，走 `PackageInstaller` 会话，系统确认框会自动点掉。
装完 5 秒左右，全程不用碰车机屏幕。

## 构建

```bash
export JAVA_HOME=/path/to/jdk    # JDK 17+
export ANDROID_HOME=/path/to/sdk
./gradlew assembleRelease
```

`sdk.dir` 写在 `local.properties`，不进版本库。

产物：`app/build/outputs/apk/release/app-release.apk`
归档：`_dist/Lynk-Manager-v<版本>.apk`

release 用 debug 签名，仅供自用，不能上架。

## 目录结构

```
app/src/main/java/com/duan/lynkmanager/
  MainActivity.java          应用列表、搜索、筛选、操作菜单
  AppListAdapter.java        卡片渲染
  AppInfo.java               数据模型
  BaseActivity.java          密度放大（UI_SCALE = 1.75）
  AdbSwitchActivity.java     ADB 开关页
  LanTransferActivity.java   传文件页：二维码、上传下载、安装
  SimpleHttpServer.java      极简 HTTP 服务（上传/下载/multipart 解析）
  WifiDirect.java            热点启停、连网
  ApkProvider.java           暴露 uploads/export 下的 APK 给安装器
  InstallClickService.java   无障碍服务，代点系统「安装」按钮
  InstallResultReceiver.java 安装结果回调
  TransferService.java       前台服务，防止后台被杀
  BootReceiver.java          开机自动开 ADB
scripts/
  grant-perms.bat/.sh        一键授权脚本
```

## 已知限制

| 限制 | 原因 |
|---|---|
| 系统应用不能卸载 | 需要 `DELETE_PACKAGES` 特权，普通签名拿不到 |
| 不能清数据/冻结 | 需要对应的系统级权限 |
| 读不到 logcat | `READ_LOGS` 是 signature 级 |
| 强停只对后台有效 | 前台/常驻进程停不掉，Android 机制 |
| ADB 开关依赖 SELinux Permissive | 本车是 Permissive；Enforcing 机型会失败 |
| 传文件要同一局域网 | 4G/5G 下不互通 |
| 服务只在应用进程活着时有效 | 进程被杀则停止，重开应用即可 |
| 明文 HTTP | 只在局域网用，别在公共网络开 |

## 许可

[PolyForm Noncommercial 1.0.0](LICENSE) — 可自由使用、修改、分发，
**仅限非商业目的**：不得用于销售、付费服务、广告变现，也不得集成进商业产品。

本项目按「现状」提供，不附带任何明示或暗示的担保。
完整条款见 [LICENSE](LICENSE)。

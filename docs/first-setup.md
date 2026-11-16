# 新车首次使用：装完必须做的两步授权

> 适用：任何一台领克/吉利车机（CS11_A4 等），从 U 盘安装 Lynk 管家之后。
> 本文回答「装完还要不要做什么」——**要，且只有一次，共两条命令。**

---

## 短答

U 盘装完 `Lynk-Manager-v1.4.6.apk` **还不能直接装 APK**。
需要在电脑上对车机执行两条 adb 命令，共约 1 分钟：

```bash
adb shell appops set com.duan.lynkmanager REQUEST_INSTALL_PACKAGES allow
adb shell pm grant com.duan.lynkmanager android.permission.WRITE_SECURE_SETTINGS
```

执行完就永久生效（重启不丢），之后全部操作在车机+手机之间完成，不再需要电脑。
**只有卸载重装才需要重做。**

---

## 为什么非要 adb

这两条都是普通权限/特殊 appops，**系统不允许应用自己给自己授权**。
而这台车机的原厂设置把授权页面砍掉了：

```
MANAGE_UNKNOWN_APP_SOURCES → 只有 com.android.car.frameworkpackagestubs 空壳
APPLICATION_SETTINGS       → 0 个 Activity
com.geely.settings         → 209 个组件，无一个是应用管理/权限页
```

所以用户没有"去设置里点一下允许"这条路，只能靠 adb 一次。

### 两条命令各自的作用

| 命令 | 作用 | 缺了会怎样 |
|---|---|---|
| `appops set ... REQUEST_INSTALL_PACKAGES allow` | 允许本应用发起安装 | 点记录后系统直接拒绝，日志 `User rejected permissions` |
| `pm grant ... WRITE_SECURE_SETTINGS` | 允许本应用写系统设置 | 无法自动开启无障碍助手 → 安装框弹出但「安装」按钮点不动 |

第二条尤其关键：车机的「安装」按钮带防点击劫持保护，**手指点、adb input tap 全都无效**
（实测同一个对话框的「取消」能点，「安装」点不动）。唯一可行的是无障碍
`performAction(ACTION_CLICK)`，而开启无障碍服务需要写
`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`，那个需要 WRITE_SECURE_SETTINGS。

---

## 完整流程（新车上从零开始）

### 第 1 步：U 盘装本应用

1. 把 `Lynk-Manager-v1.4.6.apk` 拷到 U 盘
2. U 盘插车机 USB 口
3. 车机文件管理器找到 APK，点击安装
4. 若弹出「禁止安装未知应用」：这类车机通常也拦 U 盘安装，
   需要先在电脑上给文件管理器授权，或直接用 adb 装：

```bash
adb install -r Lynk-Manager-v1.4.6.apk
```

### 第 2 步：adb 授权（唯一需要电脑的环节）

```bash
# 1) 车机连电脑，车机屏幕弹「允许 USB 调试吗？」→ 勾选「一律允许」→ 允许
# 2) 确认能看到设备
adb devices
# 应输出类似：6a2ba8bf    device

# 3) 执行两条授权
adb shell appops set com.duan.lynkmanager REQUEST_INSTALL_PACKAGES allow
adb shell pm grant com.duan.lynkmanager android.permission.WRITE_SECURE_SETTINGS

# 4) 验证（都应显示 allow / granted=true）
adb shell appops get com.duan.lynkmanager REQUEST_INSTALL_PACKAGES
adb shell dumpsys package com.duan.lynkmanager | grep WRITE_SECURE_SETTINGS
```

### 第 3 步：拔线，正常使用

1. 车机打开 Lynk 管家 → 看顶部红条是否消失（消失=授权成功）
2. 点「传文件」→ 自动开热点 + 生成两个二维码
3. 手机扫 ① 号二维码连热点，再扫 ② 号打开上传页
4. 选 APK 上传 → 车机点记录 → 系统确认框自动被点 → 5 秒装完

---

## 应用内的自检与引导（v1.4.6 新增）

为了让你在**任何一台新车上**都知道该做什么，应用做了两层自检：

### 主界面顶部红条

未授权时常驻显示：

```
⚠ 还需一次性授权才能安装 APK：安装权限已关闭 · 缺少 WRITE_SECURE_SETTINGS（点这里看怎么执行）
```

点一下弹出完整说明 + 「复制命令」按钮，命令已经填好本机包名，直接粘到电脑执行。

### 传文件页红条

只针对安装权限：

```
⚠ 安装权限已被关闭（deny），点这里查看修复方法
```

点「安装」时若权限是 deny，会先拦下来弹这个框，而不是让你看着「已提交安装」干等。

**授权完成后切回应用，红条自动消失**（`onResume` 每次重新检测），不用重启。

---

## 常见问题

**Q: 一定要用电脑吗？能不能车机自己搞定？**
A: 第一次必须。系统明确禁止应用给自己授这两个权限。授权完成后就再也不需要电脑。

**Q: 换一台新车要重做吗？**
A: 要。授权是写进那台车机的 appops/权限数据库的，跟着设备走，不跟 APK 走。

**Q: 车机没开 USB 调试怎么办？**
A: 本应用自带的 ADB 按钮可以查状态。若完全没开，需要进工厂模式或让 4S 开——
这部分与本应用无关，是车机本身的前置条件。

**Q: 授权会丢吗？**
A: 重启不丢、升级覆盖安装不丢。**只有卸载才会清空**，重装后需重做一次。

**Q: 我不想用无障碍，能不能只授权第一条？**
A: 能装，但你得自己点「安装」按钮——而这台车机的那个按钮点不动。
所以实际不可用。两条都要。

---

## 一句话总结

| 阶段 | 需要什么 |
|---|---|
| U 盘装本应用 | 车机允许装第三方 APK（或直接 adb install） |
| 首次授权 | **电脑 + USB 线，两条命令，一次** |
| 日常使用 | 只要手机 + 车机，全自动 |

# 替代方案成功率推演

> 针对 docs/install-blocker-analysis.md 中"替换原版 Settings"实验方案的
> 分步概率推演。推演基于本会话实测数据 + Android 平台机制。
> 推演日期：2026-11-14。

---

## 方案回顾

把用户手里反编译的原版 AOSP Settings（`com.android.settings` v9，
含 `ManageAppExternalSourcesActivity` 授权页）回编、签名、装上车机，
恢复被吉利砍掉的「安装未知应用」官方授权入口。

---

## 分步概率推演

### 步骤 0：平台签名一致 → 概率 ~5%

这是整条链路的**总闸门**，且先验极不乐观：

- 原版包 `platformBuildVersionCode=28`（Android 9 构建），
  车机是 Android 11（SDK 30）。跨大版本的 OTA，车厂几乎必然**换签**
  或至少轮换 keystore（安全审计的基本要求）。
- 该反编译包来源不明（非从这台车机拉取——车机上根本没有这个包）。
  它更可能来自另一台设备、模拟器镜像或 AOSP 开源构建产物，
  其签名是 AOSP 公开的 testkey 而非吉利 platform key 的概率极高。
- 车机自产的 `com.geely.settings`（sharedUser=android.uid.system）
  用的必然是这台车的 platform keystore。

独立小概率事件叠加：
- P(吉利沿用 Android 9 时代的同一 platform keystore 到 Android 11) ≈ 15%
- P(手里的包恰好是用那把 key 签的，而非 testkey/其它设备 key) ≈ 30%

**P(步骤0通过) ≈ 0.15 × 0.30 ≈ 4.5%**

注：步骤 0 本身零成本且结果确定，推演价值在于尽早止损——
如果签名不一致，后面全部概率清零。

### 步骤 1：备份与快照 → 概率 ~99%

纯 adb pull + settings get，无失败因素。唯一风险是车机断连
（本会话观察到的 adbd 偶发掉线），重试即可。视为可完成。

### 步骤 2a：apktool 回编成功 → 概率 ~35%

回编一个 Android 9 时代的大型系统应用（5.5MB dex、209 个 Activity、
7.7MB resources.arsc）在 2024+ 的 apktool（支持到 SDK 34 语法）上
失败点很多：

| 失败点 | 先验概率 | 说明 |
|---|---|---|
| 资源编译失败 | 高 | resources.arsc 中 SDK 30+ 新增资源类型（如 API 31+ 的可折叠、plash）在旧包中不存在，反而是旧格式在新 aapt2 下可能报错；更常见的是 `@android:` 内部引用缺失 |
| dex 不回编 | 中 | apktool 对 dex 只做 smali 回译，5.5MB 的 Settings dex 涉及大量优化指令（API 28 的 invokedynamic、 Lambda），smali→dex 回译可能有版本兼容问题 |
| Manifest 二进制异常 | 低 | 用户手里 manifest.txt 是 aapt dump 的文本，说明原 apktool d 已成功，b 失败概率降低 |
| sharedUser 声明被 apktool 拒绝 | 低 | 可手动改 |

综合：**P(回编产出可签名 APK) ≈ 35%**
（如果能找到原始未拆包的 APK 而不是回编，此项直接变 100%——
推演按"只有反编译目录"的已知条件计算）

### 步骤 2b：pm install 接受 → 概率 ~40%（条件于步骤 0 通过）

即使签名一致，安装环节还有独立失败点：

| 失败点 | 先验概率 | 说明 |
|---|---|---|
| versionCode 降级拒绝 | 低 | 车机上无同包名应用（com.android.settings 未装），INSTALL_FAILED_VERSION_DOWNGRADE 不适用 |
| SDK 版本校验 | 中 | targetSdk 28 的应用装在 SDK 30 上通常允许（向下兼容），但系统应用目录规则可能更严 |
| sharedUser 冲突 | 低（已过滤） | 步骤 0 已确认签名一致才会走到这 |
| dexopt/verify 失败 | 中 | API 28 编译的 dex 在 SDK 30 的 ART 上 verify 失败的概率存在，尤其系统应用走 strict 模式 |
| /data 区 vs /system 区策略 | 中 | sharedUserId=android.uid.system 的应用，部分 ROM 强制要求装在 system 分区（pm install 装不进 /system），adb shell 的 pm install 只能装到 /data/app，可能被 UID 共享检查拒 |

综合（条件概率）：**P(安装成功 | 签名一致) ≈ 40%**

### 步骤 3：授权页实际可用 → 概率 ~50%（条件于安装成功）

这是最容易被低估的一步。即使原版 Settings 装上了：

| 失败点 | 先验概率 | 说明 |
|---|---|---|
| MANAGE_UNKNOWN_APP_SOURCES intent 仍被 frameworkpackagestubs 抢占 | 高 | 车机 ROM 在 framework 层把该 action 硬编码指向 stub（实测如此），新装的 Settings 组件不一定能在 resolve 中胜出，除非 ROM 的 resolver 按 priority 选择——stub priority=-1，原版 priority=1，理论上原版胜，**但** framework 可能直接查 stub 而不走 resolver |
| ExternalSourcesDetails 打开但开关无效 | 中 | 开关写 appops 需要调用方持有 WRITE_SECURE_SETTINGS——原版 Settings 有，但 ROM 可能对非预装 uid 的 appops 写入做了额外拦截 |
| PackageInstaller 仍读 stub 的授权状态 | 中 | 封锁 2 的防点击过滤独立于授权状态存在——**即使授权开关打开，「安装」按钮的点不动问题依然在** |
| Settings 与 OneOS-Settings 组件冲突 | 低 | 包名不同，无直接冲突；但两者可能都注册系统设置相关 provider，导致 OneOS-Settings 异常 |

综合：**P(授权页真正解决问题 | 安装成功) ≈ 50%**

**关键认知**：即使步骤 3 全部成功，它只解决封锁 1（授权入口），
封锁 2（按钮点不动）和封锁 3（staged 分支）仍然存在。
无障碍代点方案依然是必须的。所以这个方案的实际增益是
「让权限管理不再依赖 adb 授权一次」——而不是解决安装问题本身。

---

## 总概率

```
P(整条链路成功) = P(步骤0) × P(回编) × P(安装) × P(授权页可用)
              ≈ 0.05 × 0.35 × 0.40 × 0.50
              ≈ 0.35%    （约 1/286）
```

## 提升 odds 的改法

| 改动 | 提升 |
|---|---|
| 找到**原始未拆包**的 com.android.settings APK（跳过回编） | 步骤 2a 35%→100%，总概率升到 ~1% |
| 拿到吉利 platform keystore（几乎不可能，除非泄密） | 步骤 0 5%→100%，总概率升到 ~7% |
| 两者同时满足 | 总概率 ≈ 0.5 × 0.40 × 0.50 ≈ **10%** |
| 改推「不需要 system uid 的第三方设置应用」（如某些开源的 appops 前端，配 adb 授 WRITE_SECURE_SETTINGS） | 绕开签名问题，但同样受"授权页 intent 抢占"与"按钮过滤"限制，且这类应用需要 targetSdk≥26 又触发 appops 默认拒绝的死结 |

## 对比基线

| 方案 | 当前成功率 | 已验证 |
|---|---|---|
| **v1.4.3 无障碍自动点击**（现装车机） | 正常场景 ~100%，force-stop 边缘场景失败后重试一次 ~95% | ✅ 实测 |
| 替换原版 Settings | **~0.35%（推演）** | 未实测 |
| 两者叠加 | 无额外增益（封锁 2/3 不因此消失） | — |

## 结论

1. **推演总概率 ≈ 0.35%**，属于"值得记录但不必优先执行"的实验。
2. 主要死亡点按顺序是：签名不一致（95%）→ 回编失败（65%）→
   授权页被 ROM 抢占/无效（50%）。
3. 即使全部成功，**封锁 2 与封锁 3 仍需无障碍方案兜底**——
   替换 Settings 方案的收益只是"把一次 adb 授权变成用户可在界面操作"，
   不改变 v1.4.3 的必要性。
4. 因此下次连接车机的建议顺序：
   - **先做步骤 0 签名分析**（5 分钟，零风险，直接判决）
   - 签名不一致（95% 的可能）→ 放弃替代方案，把精力放在
     1.4.3 的 force-stop 边界修复（延长重绑等待 + 失败重试提示）
   - 签名一致（5% 的可能）→ 才继续步骤 1-4

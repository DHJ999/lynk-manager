# 步骤 0 实测结论：签名不一致，方案终止

> 实测日期：2026-11-14 20:51
> 车机：CS11_A4 / 6a2ba8bf / Android 11 / `CS11A4.IC3084.41.SOC.E331.202511261310.R`
> 结论：**三方签名比对完成，替换原版 Settings 方案判定不可行（概率推演中 95% 的死亡点命中）**

---

## 实测数据

### 1. 车机平台签名（`/system/framework/framework-res.apk`）

```
sha256 = F5:1E:54:A4:F3:B4:8D:87:2B:1E:C7:10:9D:34:10:18:58:C9:38:CF:29:90:C7:15:3D:13:DC:E8:68:2B:03:B5
subject = C=CN, ST=GuangDong, L=HuiZhou, O=DesaySV, OU=ICI2, CN=SW
          emailAddress=desaysv@desay-svautomotive.com
issuer  = 同上（自签）
serial  = BB2AF3B47D5ECA13
notBefore = Dec 20 06:01:32 2021 GMT
```

→ 车机的系统级签名由 **DesaySV（德赛西威）** 签发。

### 2. 车机原厂设置（`/system/app/OneOS-Settings/OneOS-Settings.apk`）

```
sha256 = F5:1E:54:A4:F3:B4:8D:87:2B:1E:C7:10:9D:34:10:18:58:C9:38:CF:29:90:C7:15:3D:13:DC:E8:68:2B:03:B5
subject = 同 DesaySV
serial  = 同 BB2AF3B47D5ECA13
```

→ **与平台签名完全一致**。它确实是系统级预装应用（`sharedUser=android.uid.system`）。

### 3. 用户手里的反编译原版 Settings（`lynk-setting/META-INF/CERT.RSA`）

```
sha256 = 3D:89:C5:63:36:5E:86:30:55:1C:BD:47:06:9F:38:14:0D:8E:4D:88:A0:9C:8B:6E:89:FC:FC:AB:DB:C3:CC:73
subject = C=CN, ST=LiaoNing, L=DaLian, O=EcarX, OU=Android, CN=Android
          emailAddress=android@android.com
issuer  = 同上（自签）
serial  = BB8D25E664751177
notBefore = Oct 16 09:57:35 2018 GMT
```

→ 它由 **EcarX（亿咖通）** 在 **2018 年**签发。

## 判定

| 对比项 | 车机平台 | 反编译包 | 一致？ |
|---|---|---|---|
| 签发方 | DesaySV 德赛西威 | EcarX 亿咖通 | ❌ |
| sha256 | F5:1E:54:A4:…:2B:03:B5 | 3D:89:C5:63:…:C3:CC:73 | ❌ |
| serial | BB2AF3B47D5ECA13 | BB8D25E664751177 | ❌ |
| 签发时间 | 2021-12 | 2018-10 | ❌ |
| 目标系统 | Android 11 (SDK 30) | Android 9 (SDK 28) | ❌ |

**四维全部不一致。** 该反编译包来自另一台 **EcarX 座舱**（很可能是更早的吉利/领克车型），
不是本车（本车是 DesaySV 硬件 + `com.geely.settings` 软件栈）。

## 为什么这个方案必须停

`com.android.settings` 声明了 `sharedUserId="android.uid.system"`。安装时 PackageManager 会做：

```
1. 解析 APK 的 sharedUserId → android.uid.system
2. 找到该 uid 现有成员（com.geely.settings, framework-res, ...）
3. 校验待装 APK 的签名 == 现有成员的签名
4. 不一致 → INSTALL_FAILED_SHARED_USER_INCOMPATIBLE，直接拒绝
```

签名不同意味着**这一步必然失败**，且失败发生在 dexopt/资源解析之前，
与包完整性、apktool 回编质量都无关。即使回编完美、签名齐全（用自己的 key），
也过不了 uid 共享校验。

结论：**不需要也不应该执行步骤 1–4**，任何往车机装这个包的动作都是纯粹的浪费，
且真装（如果误用 `-d` 等强制参数）可能触发系统设置异常。

## 决策树落点

```
步骤 0 签名一致？
└─ 否（实测命中）→ 放弃替换 Settings 方案；1.4.3 无障碍方案即最终方案 ✅ 当前在此
```

## 附带收获（写进项目认知）

1. **本车真实技术栈**：DesaySV 硬件/系统签名 + Geely 应用层 + ECARX 部分中间件
   （`com.ecarx.car`、`ecarx.pki.autoinstall` 等），三方厂混合，
   不是单一供应商。这解释了为什么"从别的车上扒的包"用不上。
2. **跨车移植系统应用的可行性≈0**：只要带 `sharedUserId`，
   就绑定到具体车厂的 speckeystore，换车即失效。
   除非能拿到对应车厂的 platform keystore（不可能）。
3. **`framework-res.apk` 的签名=Platform 签名**：这是判定任意车机平台签名的
   通用方法，比找任何单个系统应用都可靠（framework-res 必然由 platform key 签）。

## 后续：把精力转到已验证有效的方向

替代方案排除后，v1.4.3 的无障碍方案是唯一可行路径，其唯一已知边界是
**force-stop 后无障碍服务重绑偶发超时**。下一步针对该边界做加固：

1. 应用启动/进入传文件页时**预启用**无障碍服务（不等点安装才启用），
   让服务在用户操作前就 ready；
2. 点安装时若仍未 ready，延长等待并给明确的一次性重试指引；
3. 目标：把 force-stop 场景的成功率从"重试后 ~95%"提到"首点即成功"。

详见代码提交与 `docs/install-blocker-analysis.md` 更新。

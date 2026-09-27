# 快捷记账DB (QuickLedgerDB)

一款安卓自动记账应用。读取你支付时产生的数据，自动生成账单，由你决定是否保存。所有数据仅保存在本机，不上传任何服务器。

> ⚠️ 本应用处于 beta 版，可能有未知错误。

## 下载

前往 [Releases](https://github.com/D727jipo/expense-tracker-app/releases) 下载最新 APK。要求 **Android 10（API 29）** 及以上。

## 功能

- **自动记账**：检测到支付后自动保存（可在设置中关闭，改为弹窗询问），保存后可点击通知修改或删除
- **手动记账**：首页「+」记一笔，金额、来源、备注、分类、收支类型均可编辑
- **账单导入**：支持导入微信支付 / 支付宝官方导出的 CSV 账单文件（自动识别编码与格式，自动跳过退款、不计收支的记录）
- **账单修改与删除**：点击任意流水条目即可编辑
- **流水筛选**：综合（默认）/ 收入 / 支出，支出红底、收入绿底
- **总消费统计**：月支出 / 年支出滑动切换，支持按日期精确查找当天账单
- **深色模式**：跟随系统 / 浅色 / 深色

## 三种账单捕获方式

在首次进入的引导页或设置中选择：

| 方式 | 精度 | 说明 | 需要 |
| --- | --- | --- | --- |
| 通知权限 | 最低 | 读取微信/支付宝的支付通知，解析金额与备注 | 无 |
| 无障碍 + Shizuku | **推荐** | 直接读取支付结果页面的数据；装了 Shizuku 可一键自动开启无障碍 | 无 / Shizuku |
| LSPosed 模块 | 最高 | 直接 Hook 微信支付数据，需在 LSPosed 管理器中启用本模块并勾选微信后重启微信 | root + LSPosed |

**留存后台**：应用会申请电池优化豁免，避免捕获服务被系统清理导致漏记账单。

## 工作原理

- 通知捕获：系统 `NotificationListenerService` 监听微信/支付宝通知，用正则解析金额、收款方与备注
- 无障碍捕获：`AccessibilityService` 监听支付结果页面，从界面文本中提取数据
- LSPosed 模块：Hook 微信支付页 Activity，收集界面文本后通过带 token 校验的广播回传给本应用
- 解析到的数据先经过本地数据库前的自动分类（餐饮/交通/购物等），支持去重（2 分钟内同一笔只提示一次）

## 自行构建

```bash
git clone https://github.com/D727jipo/expense-tracker-app.git
cd expense-tracker-app
# 在 local.properties 中配置 sdk.dir 后
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

环境要求：JDK 17+、Android SDK 35。项目内含 `xposed-stubs` 本地编译桩模块（Xposed API 在运行时由 LSPosed 提供，不会打包进 APK）。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

## 免责声明

本应用与微信、支付宝、Shizuku、LSPosed 均无关联。捕获支付信息仅为个人记账用途，请在遵守相关服务条款的前提下使用，由此产生的任何问题由使用者自行承担。

# 更新日志

按日期倒序记录功能更新与重大缺陷修复（新内容在最上面）。

## 2026-10-26 寻路日志默认关闭 + 脚本级开关

**修改内容**

- 新增 `WalkerLogSwitch`（`microbot/util/walker/WalkerLogSwitch.java`），统一控制寻路日志的三个 logger 前缀：
  - `net.runelite.client.plugins.microbot.util.walker`（原版 walker）
  - `net.runelite.client.plugins.microbot.shortestpath`（原版最短路径 / pathfinder）
  - `net.runelite.client.plugins.microbot.walkerfst`（FST 版本全包）
- 默认状态为**关闭**：上述 logger 级别抬到 `ERROR`，INFO/WARN 的寻路日志（`[WebWalk]`、`[Walker]`、`[WalkerTelemetry]`、pathfinder 诊断等）不再输出，也不会被 `GameChatAppender` 镜像到游戏聊天框；真正的 ERROR 仍然保留。
- 打开后级别为 `DEBUG`，输出完整寻路细节。
- 脚本入口（两个 Walker 共用同一开关）：
  ```java
  Rs2Walker.setWalkerLoggingEnabled(true);    // 或 Rs2WalkerFst
  Rs2Walker.isWalkerLoggingEnabled();
  ```
- 原有配置项 `verboseWalkerLogging` 保留同一个 config key（兼容旧配置），显示名改为 **Walker logging**，改为委托 `WalkerLogSwitch`；两个 shortestpath 插件的 `startUp()` / `startUpHeadless()` 与 `onConfigChanged` 都会同步该状态，客户端启动时即生效。
- 顺带修复：FST 版本的 logger 前缀列表原先漏了 `walkerfst` 包本身，导致 FST 的 debug 日志不受开关控制。

**使用说明**

- 默认无需操作，寻路日志静默；控制台和游戏聊天框不会再刷寻路信息。
- 排查"目标不可达 / 找不到路径"这类问题时，注意关闭状态下原有 WARN 诊断也不会出现，需要先用 `setWalkerLoggingEnabled(true)` 或打开配置项 `Walker logging`。
- `microbot.util.leaguetransport`、`microbot.util.poh` 等相邻包不在静音范围内（非严格寻路日志）。

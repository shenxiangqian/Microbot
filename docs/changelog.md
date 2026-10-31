# 更新日志

按日期倒序记录功能更新与重大缺陷修复（新内容在最上面）。

## 2026-10-30 修复 `walkStepFull` 换目标后仍返回旧终态的缺陷

**问题现象**

用 `walkStepFull` 走到目的地返回 `ARRIVED` 之后，换脚本换一个目的地再调用 `walkStepFull`，
仍然直接返回上一次的 `ARRIVED`，寻路完全不再执行；只有重启客户端才能恢复。

**原因**

`walkStepFull` 里的会话终态复述判断写在了「目标/距离是否变化」判断**之前**：

```java
if (session != null && session.finished) {
    return session.finalState;      // ← 先看终态
}
if (session == null || !target.equals(session.requestedTarget) || ...) {
    session = new WalkStepSession(...);   // ← 换目标的分支根本走不到
}
```

于是任何一次调用（不论目标是什么）都会先命中终态复述，直接把上一次的 `ARRIVED` 返回给调用方。

**修改内容**

- `Rs2Walker.walkStepFull` 的会话判定改为**先比较目标与距离**：
  - 目标或距离变了 → 重开一段寻路（`WalkStepSession` 重建），不复述旧终态；
  - 目标没变 → 若上一段已到终态，再判断玩家是否仍在到达半径内：
    - 仍在 → 复述该终态；
    - 已走远 → 终态不再成立，重开一段寻路。
- 新增私有辅助方法 `isWalkStepStillArrived(WorldPoint, int)`：按玩家当前格与目标的距离判断
  `ARRIVED` 是否仍然成立。

**使用说明**

- 行为变化只有一处：换目标后不再返回旧终态，会正常开始新的一段寻路。
- 同一目标重复调用时，若玩家已离开目标附近，现在会重新寻路而不是复述旧的 `ARRIVED`；
  需要强制重开同一目标时仍可调用 `Rs2Walker.resetWalkStep()`。

## 2026-10-30 核心包分步寻路 API `walkStepFull` 已实现

**修改内容**

- 新增 `util/walker/state/WalkStepSession.java`：分步寻路的跨调用会话对象，承载原尾循环体里
  13 个跨迭代局部变量（部分路径重试预算、client 线程超时重试、寻路器缺失绕行次数、豁免迭代计数、
  上次小地图点击及其结果、寻路器 pending 计时、可达格缓存及其原点、尾循环计数）以及会话生命周期
  （请求目标、当前目标、是否已准备、是否已到终态）。
- `Rs2Walker` 重构（阻塞版对外行为不变）：
  - `processWalk` 三参重载的尾循环体（1633 行）**整体提取**为 `processWalkPass(WalkStepSession)`
    单趟方法，循环体内部逻辑一行未改写，只有那 13 个跨迭代局部变量换成会话字段；
  - 三参重载改为 `driveWalkPasses(session)` 驱动，尾循环预算与豁免扣减（`processWalkTail--`）语义与原来一致；
  - 原尾循环体里的 `continue` 由新增的单趟壳 `for (int passOnce = 0; passOnce < 1; passOnce++)` 承接，
    15 处外层 `continue` 无需改写；体内 4 处属于内层 `for` 的 `continue` 语义不变；
  - 尾循环预算耗尽的收尾抽成 `tailBudgetExceeded(target)`，阻塞版与分步版共用。
- 抽出两段共用准备逻辑：`prepareWalk(target, distance)`（到达判定、`setTarget`、`routeState` 复位、
  关闭世界地图/银行、标记会话开始）与 `prepareBasementEntry(target)`（地窖入口拉杆谜题）。
- 新增公开 API `Rs2Walker.walkStepFull(WorldPoint, int)` 与 `Rs2Walker.resetWalkStep()`。
- sealed rim 改道：阻塞版保持原递归（另起一段完整寻路）；分步版改为「改写会话目标 + 结束本趟」，
  下一趟以新目标继续。
- 同步更新 `src/test/resources/threadsafety/client-thread-guardrail-baseline.txt`：
  客户端线程调用点的归属方法由 `processWalk` / `walkWithStateInternal` 变为
  `processWalkPass` / `prepareWalk`，违规条目本身没有增减。

**使用说明**

- `walkStepFull` 走的是与阻塞版 `walkTo` **完全相同**的流水线（门、楼梯、传送、魔法、弹窗、停滞恢复），
  区别只是「一趟做完就交还控制权」。现有的 `walkStep(WorldPoint, int)` 保持原样（只点一次小地图，
  不走传送/门/停滞恢复流水线）。
- 调用方循环调用直到终态：

  ```java
  WalkerState state;
  do {
      state = Rs2Walker.walkStepFull(target, 1);
      // 两步之间可以插入自己的逻辑：检查 NPC、对话、打断点……
  } while (state == WalkerState.MOVING);
  ```

- 会话规则：目标或距离变化即重开一段寻路；到达终态后再次调用同一目标会直接复述该终态，
  想用同一目标重走一遍时调用 `Rs2Walker.resetWalkStep()`。
- 每趟单独获取/释放 `walkerLock`，因此两次调用之间可能被其它寻路插入；一旦被抢走，
  尾循环体内部的取消检查会以 `EXIT` 收尾。
- 详细设计与实现说明见 `docs/2510/30核心包walkStep分步寻路方案.md`。

## 2026-10-30 核心包 walkStep 分步寻路方案（设计文档，未实现）

**修改内容**

- 新增方案文档 `docs/2510/30核心包walkStep分步寻路方案.md`，设计一个与阻塞版 `walkTo` 等效、
  但每执行完一步就返回一次的分步寻路 API（处理门、楼梯、传送、魔法、弹窗）。
- 方案基于对现有 `Rs2Walker.processWalk` 尾循环的实测控制流分析，结论是**尾循环体可整体提取**：
  - 19 处 `continue` 中 15 处属于尾循环本身、4 处属于内层 `for`（提取后保持原样）；
  - 41 处 `break` 全部在内层循环内，无标签 break、无跳出方法的 break；
  - 28 处 `return` 全部带返回值，无 `void return;`。
- 已确定两项设计决策：跨调用状态放在**共享静态会话**；**一次尾循环迭代 = 一步**。
- 已梳理出必须提升为会话字段的 13 个跨迭代局部变量、7 处 `processWalkTail--` 豁免扣减，
  以及 `processWalk` 的 4 个调用点（其中 2107 行的 sealed rim 递归在分步语义下需改写）。
- 本次**未修改任何源码**，仅产出设计文档。

**使用说明**

- 本条目记录的是设计阶段成果，功能尚未实现，暂无可用 API。
- 后续实现时需一并确认：新方法命名（建议 `walkStepFull`，避免与现有 `walkStep(WorldPoint,int)` 签名冲突）、
  以及是否顺带把阻塞版 `walkTo` 改为构建在分步之上（方案中列为不建议同批做的备选 A）。

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

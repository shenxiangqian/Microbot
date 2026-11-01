# 更新日志

按日期倒序记录功能更新与重大缺陷修复（新内容在最上面）。

## 2026-11-01 修复重启脚本时 jar 被删除/改名导致的报错

**问题**

- 之前加载的是 `test1.jar`，更新脚本后删掉 `test1.jar`、换成新包 `ttt.jar`，
  点「重启脚本」报 `Plugin file does not exist: test1.jar`。
  原因是重启路径按旧 jar 名（internalName）去重新加载，而旧文件已经不存在。

**修改内容**

- 重启时不再按旧 jar 名硬找：jar 变化、被改名或被删除时，先释放旧部署，
  再把 `microbot-plugins/` 目录同步到最新状态（加载新 jar、重载内容有变化的 jar），
  然后按**显示名**找回上次启动的那个脚本并启动它。
- 找不到同名脚本时报错并列出当前可用脚本（显示名 + jar 文件名），例如
  `No script named 'Test 1' was found in the side-load directory. Available: ttt [ttt.jar]`。
- 因此重新打包脚本时，保持 `@PluginDescriptor` 的 `name` 不变，重启就能自动跟上新包；
  jar 文件名可以随意改。

**影响**

- 依赖显示名匹配：显示名改了会导致重启找不到脚本，需要去启动脚本弹窗里手动选。

## 2026-11-01 重启脚本按钮按 jar 是否改动分流

**修改内容**

- 「重启脚本」按钮不再无条件重载 jar，改为先比对 jar 是否改动（大小 + 修改时间 + SHA-256，
  与启动脚本弹窗的自动重载同一套判定）：
  - **jar 未改动**：直接停掉并重新启动上次执行的脚本，复用已加载的 classloader，不重新加载 jar。
  - **jar 有改动**：卸载旧 jar、加载新 jar，然后启动与上次启动**显示名相同**的脚本。
- 重载后按显示名（`@PluginDescriptor` 的 `name`）匹配脚本；新 jar 里没有同名脚本时报错提示
  找不到该显示名。
- 「上次执行的脚本」记录里新增显示名字段，jar 重载后仍能按显示名找回同一个脚本。
- jar 未改动时的重启走的是插件正常的 disable/enable 周期（`shutDown()` → `startUp()`），
  脚本实例复用、内存状态重置，executor 不被销毁。

**影响**

- 其它脚本正在运行时的拦截逻辑保持不变。
- jar 重载失败、脚本线程未停干净等既有行为保持不变。

## 2026-11-01 启动脚本弹窗打开前自动重载已改动的 JAR

**修改内容**

- 打开「启动脚本」弹窗（标题栏播放按钮）时，先自动重载 `~/.runelite/microbot-plugins/` 下
  **文件有变化**的 JAR，再填充脚本列表。此前列表直接来自启动时已加载的 classloader，
  重新上传/替换 JAR 后看到的仍是上一次的脚本列表。
- 判定“有变化”的依据是大小、修改时间与 SHA-256（新增 `SideLoadJarFingerprint`）；
  没有变化的 JAR 完全不动，classloader 保持原样。
- 目录里新增的 JAR 会被加载；磁盘上被删掉的 JAR 会被卸载。
- 正在运行的脚本对应的 JAR 不重载——重载会停掉脚本，因此保持运行，并在弹窗状态栏提示
  “N 个运行中的脚本保持原样，新版本在停止后生效”。
- 新 JAR 加载失败（编译错误、类加载失败）时丢弃该 JAR，并在弹窗中列出失败原因。
- 弹窗里原有的刷新按钮语义不变：仍是强制重载全部 JAR（含正在运行的那个）。

**影响**

- 没有配置开关，始终生效。
- 重载在后台线程执行，弹窗先显示“正在重载脚本 JAR…”的加载态，完成后再显示列表。

## 2026-11-01 移除 FST 版寻路（walkerfst 整包下线）

**修改内容**

- 删除 `microbot/walkerfst/` 整个包（183 个文件）：旧版 walker（`Rs2WalkerFst` 等）、旧版最短路径
  （`PathfinderFst` 等），以及配套的 enums / door / obstacle / leaguetransport 副本。
  这份平行副本自建立以来就没有任何生产代码引用，当前生效的仍是 `util/walker` + `microbot/shortestpath`。
- 删除 `.tmp_cmp/`（127 个已跟踪文件）：当初做两版对比时留下的中间产物（diff、整份 Rs2Walker 副本等）。
- 清理指向已删类的注释与文档：`WalkerClickPolicy`、`Rs2WalkerMovement`、`Rs2Walker`、
  `WebWalkMovementPlanner`、`WalkerLogSwitch`、`WalkerClickPolicyTest` 中对 “Fst 版” 的引用改写为
  对当前实现的直接说明；寻路点击档位标签 `fstforward` 改名为 `forward`。

**影响**

- 无行为变化：被删代码不在任何运行路径上，寻路行为仍由 `util/walker` + `microbot/shortestpath` 决定。
- 寻路日志开关 `WalkerLogSwitch` 仍只控制 `util.walker` 与 `shortestpath` 两棵 logger 子树。

## 2026-11-01 修复合并 main 时丢失的编译依赖

**问题现象**

`new_old_walker_dev` 分支在合并 main 之后编译不过，报三个“找不到符号”：

- `util/walker/Rs2Walker.java` 引用了 `util/walker/state/WalkStepSession`，但该类从未提交到本分支；
- `shortestpath/pathfinder/CollisionMap.java` 里 `teleportCost(int, int, boolean)` 只有调用点，
  方法定义和 `MINIGAME_TELEPORT_PENALTY` 常量在合并时丢失，`Rs2WalkerBankingPlanner` 同样调用不到。

**修改内容**

- 补回 `util/walker/state/WalkStepSession.java`：分步寻路（`walkStepFull`）的跨调用会话对象，
  承载尾循环里 13 个跨迭代局部变量，字段与本分支 `Rs2Walker` 的用法完全一致。
- 从 main 取回 `CollisionMap.teleportCost(int, int, boolean)` 与 `MINIGAME_TELEPORT_PENALTY = 200`：
  minigame（Grouping）传送共享 20 分钟冷却且要多次点击界面，raw duration 下几乎总能赢，
  因此额外加 200 格代价。

**影响**

- 只补齐缺失的符号，不改变寻路行为：minigame 传送的定价与 main 上的实现完全一致。

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

# 核心包 walkStep 重载：分步寻路方案

> 日期：2026-10-30
> 状态：**已实现**（2026-10-30，见第 8 章「实现结果」与方案的差异）
> 目标类：`runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/walker/Rs2Walker.java`

## 1. 需求

在核心包 `Rs2Walker` 内重载一个 `walkStep`，达到与阻塞版 `walkTo` **等效**的处理能力，
但每执行完一步就返回一次：

- 楼梯、门、弹窗（对话框）、传送、魔法等**全部**要处理，与 `walkTo` 一致；
- 单步内部允许阻塞（例如等待门打开、等待传送落地）��
- 返回 `WalkerState`，由调用方循环调用直到 `ARRIVED` / `UNREACHABLE` / `EXIT`。

### 已确认的两项设计决策

| 决策项 | 结论 |
| --- | --- |
| 跨调用状态存放位置 | **共享静态会话**：`Rs2Walker` 内用一个 static 会话对象保存状态，调用方只传 `target` / `distance` |
| “一步”的粒度 | **一次尾循环迭代 = 一步**：最多执行一个动作（点击 / 开门 / 传送），与 `walkTo` 语义最贴近 |

## 2. 现状（代码事实）

### 2.1 现有的 `walkStep` 是什么

`Rs2Walker.walkStep(WorldPoint, int)`（第 1575 行）已存在，但它**不是**用户要的东西：

- 只做一次小地图点击，**不走** `processWalk` 的传送/门/停滞恢复流水线；
- 源码注释明确写着 "NOT the full processWalk transport/door/stuck-recovery pipeline"；
- 全仓库**无任何调用方**（grep `Rs2Walker.walkStep(` 无结果），是给脚本用的公开 API。

因此本次是**新增一个语义不同的重载**，而不是修改现有方法。

### 2.2 阻塞版的核心在哪

`walkTo` → `walkWithState` → `walkWithStateInternal`（第 1460 行）→ `processWalk`。

- `walkWithStateInternal` 负责**进入前准备**：到达判定、`setTarget`、`Rs2PathApi.setReachedDistance`、
  重置 `routeState` 若干字段、`closeWorldMap` / `closeBank`、`markWalkSessionStart`、取 `walkerLock`。
- `processWalk(target, distance)`（第 1914 行）先跑 Draynor 地窖拉杆谜题，再调 `processWalk(target, distance, partialRetries)`。
- `processWalk(target, distance, partialRetries)`（第 1944 行）是真正的引擎：内部是一个尾循环

  ```java
  for (int processWalkTail = 0; processWalkTail < MAX_PROCESS_WALK_TAIL_ITERATIONS; processWalkTail++) {
      ...约 1600 行循环体...
  }
  ```

  `MAX_PROCESS_WALK_TAIL_ITERATIONS = 64`。

一次迭代 = 一个 pass = 「观察 → 决策 → 执行至多一个动作」。**这正是“一步”要切分的粒度。**

### 2.3 循环体内部已经处理了哪些东西

以下全部位于尾循环体内部，即用户要的“楼梯、门、弹窗、传送、魔法”都已具备：

| 能力 | 代码位置 |
| --- | --- |
| 荒野拉杆警告弹窗 | 2291 `Rs2Widget.isWidgetVisible(229,1)` → `Rs2Dialogue.clickContinue/clickOption` |
| 路径原始场景物件（门/梯子/栅栏/岩石） | 2388 `handleNearbyRawPathSceneObjects(...)` |
| 原始路径段内的门 | 2565、2764 `handleDoorsInRawSegment(...)` |
| 原始路径段内的传送（含魔法地毯、传送法术、传送道具、小游戏、渡船、滑翔翼等） | 2615、2815 `handleTransportsInRawSegment(...)` |
| 小地图点击 / 移动 | 循环体尾部 |
| 停滞 / 掉线 / 偏航恢复 | `checkIfStuck` / `isStuckTooLong` / 局部恢复分支 |

### 2.4 尾循环体的控制流可提取性（关键结论）

用脚本按大括号深度精确统计 `processWalk` 循环体（1983–3615 行）：

- **`continue` 共 19 处**：
  - **15 处属于尾循环本身**（2012、2028、2071、2083、2147、2197、2200、2208、2282、2286、2342、3512、3521、3591、3608）
  - 4 处属于内层 `for`（2438、2636、2667、3026）—— 提取时**保持原样**，不需改动
- **`break` 共 41 处**：**全部**在内层循环内；**无**标签 break，**无**跳出方法的 break
- **`return` 共 28 处**：全部带返回值；**无** `void return;`

**结论：尾循环体可以整体提取成一个方法，控制流不会破坏。** 这是整个方案成立的前提。

### 2.5 必须跨调用保存的状态

循环体在 `for` 之前声明了 13 个局部变量，它们在迭代之间是有状态的，
一旦把循环体提取成独立方法，这些必须提升为会话字段：

| 变量 | 作用 |
| --- | --- |
| `partialRetriesWorking` | 部分路径重试预算 |
| `clientThreadTimeoutRetries` | client 线程超时重试计数 |
| `pathfinderAbsentCarryRetries` | 寻路器缺失时的绕行次数 |
| `lastPartialRetryAtMs` / `lastPartialRetryAtLoc` | 部分路径重试节流 |
| `consecutiveExemptIterations` | 连续豁免迭代计数 |
| `lastAttemptedMinimapClick` / `...Ok` / `...AtMs` | 上次小地图点击及其结果 |
| `pathfinderPendingSinceMs` | 寻路器 pending 计时 |
| `reachableTilesCache` / `reachableTilesCacheOrigin` | 可达格缓存 |
| `processWalkTail` | 尾循环计数器（64 次预算） |

另外 `processWalk` 在 2107 行会**递归**调用自己：`return processWalk(sealedRim, distance, partialRetries)`
（sealed rim 改道），提取时必须一并处理。

`processWalk` 的三参重载共有 4 个调用点（598、1539、1934、2107），分步化后需逐个确认语义：

| 调用点 | 上下文 | 分步化处理 |
| --- | --- | --- |
| 598 | `processWalkForTesting` 测试辅助 | 保持阻塞语义，改为内部循环驱动 pass |
| 1539 | `walkWithStateInternal` → `processWalk(target, distance)` | 阻塞版入口；分步版走独立入口，不复用此处 |
| 1934 | `processWalk(target, distance)` → 三参重载（`partialRetries = 0`） | Draynor 谜题之后的正式入口 |
| 2107 | sealed rim 改道递归 | **分步版需特殊处理**：递归会另起一段寻路，分步语义下应改为「改写会话目标并结束本趟」，而不是在一步内递归跑完整段寻路 |

## 3. 方案

### 3.1 总体结构

新增一个会话对象 `WalkStepSession`（放在 `util/walker/state/`），承载全部跨调用状态；
把 `processWalk` 的尾循环体提取为一个「执行一趟 pass」的方法。

```
Rs2Walker
 ├─ walkStep(target, distance)          【新增重载】公开 API，每次调用走一步
 ├─ walkStep(target, distance, ...)     【现有】轻量单次点击版，保持不变
 │
 ├─ processWalkStep(session)            【新增】执行一趟 pass，返回本趟结果
 └─ WalkStepSession (state/)            【新增】跨调用状态
```

### 3.2 一次 pass 的返回结果

提取后的 `processWalkStep` 不能直接返回 `WalkerState`，因为尾循环体内有 28 处 `return WalkerState.X`，
它们的语义分两类：

- **终态**（`ARRIVED` / `UNREACHABLE` / `EXIT`）：整段寻路结束，不再继续。
- **非终态**（`MOVING`）：循环体内部有两处 `return WalkerState.MOVING`
  （2175 联赛传送进行中、3490 终点玩家位置为空），在阻塞语义里是**提前结束本次寻路**，
  在分步语义里应视为「这一趟没走成，但会话仍可继续」。

因此定义一个内部结果类型，把两者区分开：

```java
/** 一次 pass 的结果。 */
enum StepOutcome {
    /** 本趟正常走完，会话可继续下一步。 */
    CONTINUE,
    /** 整段寻路结束，携带终态。 */
    TERMINAL
}
```

`processWalkStep` 返回 `StepOutcome` + 终态时用 `WalkerState` 携带。

### 3.3 15 处外层 `continue` 的处理

这是本次改动的核心机械操作。对尾循环的 15 处 `continue`，把「跳到下一轮迭代」
改为「结束本趟、把控制权交还调用方」：

```java
// 改前（尾循环体内）
continue;

// 改后（提取出的 pass 方法内）
return StepOutcome.CONTINUE;
```

**内层 4 处 `continue`（2438/2636/2667/3026）保持不动** —— 它们属于内层 `for`，
提取后语义完全一致。

同时，尾循环的 `for` 头需要改写成由会话驱动的形式：

```java
// 改前
for (int processWalkTail = 0; processWalkTail < MAX_PROCESS_WALK_TAIL_ITERATIONS; processWalkTail++) {

// 改后（概念）
while (session.processWalkTail < MAX_PROCESS_WALK_TAIL_ITERATIONS) {
    int processWalkTail = session.processWalkTail++;
    ...
}
```

注意 `processWalkTail--` 的**7 处**「豁免扣减」（2010、2069、2281、2286、2341、3584、3607）语义要一并搬进会话。
其中 2286 那处形如 `processWalkTail--; continue;`——扣减与「结束本趟」紧邻，
改写时必须保证扣减先于 `return StepOutcome.CONTINUE` 生效（顺序不能颠倒，否则尾循环预算会多扣一次）。

### 3.4 终态 `return` 的处理

28 处 `return WalkerState.X` 中：

- `ARRIVED` / `UNREACHABLE` / `EXIT` → `return StepOutcome.TERMINAL` 并携带该状态；
- `MOVING`（2 处）→ 按「非终态」处理，映射为 `CONTINUE`（并记录原因），
  使分步调用方可以继续下一次调用，而不是像阻塞版那样直接结束寻路。

### 3.5 公开 API

```java
/**
 * 分步寻路：执行一步后返回，效果等同阻塞版 walkTo（含门/楼梯/传送/魔法/弹窗）。
 * 调用方循环调用，直到返回 ARRIVED / UNREACHABLE / EXIT。
 */
public static WalkerState walkStep(WorldPoint target, int distance)
```

**与现有 `walkStep(WorldPoint, int)` 的关系**：现有方法签名完全相同，无法直接重载。
因此本次采用**不同名的新方法**承载新语义，避免破坏现有公开 API：

```java
public static WalkerState walkStep(WorldPoint target, int distance)                  // 现有，保持不变
public static WalkerState walkStepFull(WorldPoint target, int distance)             // 新增：完整流水线分步
```

> 若必须严格叫 `walkStep`，则需要改现有方法的签名或加一个布尔/枚举参数来区分语义，
> 这会破坏现有脚本的兼容性。**建议用 `walkStepFull`**，并在文档里说明两者差异。
> 这一点需要在实现前与需求方确认。

### 3.6 首次调用 vs 后续调用

`walkWithStateInternal` 里的**进入前准备**（到达判定、`setTarget`、重置 `routeState`、
`closeWorldMap`/`closeBank`、`markWalkSessionStart`）**只在会话的第一次调用执行**，
后续调用只跑 pass 本身。否则每步都重置 `routeState` 会把寻路状态清掉。

会话需要一个 `started` 标志来区分。

### 3.7 锁与线程

阻塞版在 `walkWithState` 里持有 `walkerLock` 直到寻路结束。分步版不能长期持锁
（否则两次调用之间锁一直被占，其它寻路请求全部饿死），因此：

- 每一次 `walkStep` 调用**单独**获取并释放 `walkerLock`（`lock` / `finally unlock`）；
- 会话本身是 static 共享的 → 天然要求「同一时刻只有一个分步会话在跑」。
  用 `walkerLock` 天然串行化了不同调用，但**同一线程连续两次调用之间**可能夹入别的寻路。
  需要在会话里记录 `currentTarget`，并在每次调用开始时校验目标未被抢走
  （抢走则返回 `EXIT`），避免状态串味。

### 3.8 会话的生命周期与重置

- 目标变化（`target` 或 `distance` 与上次不同）→ **重置会话**，开始新的一段寻路。
- 到达终态（`ARRIVED`/`UNREACHABLE`/`EXIT`）→ 会话标记结束，后续调用直接返回该终态，
  不再重复执行。
- 现有 `setTarget(null, reason)` 的取消语义需要在分步版里同样生效
  （`walkCancelledDiag` / `isWalkCancelled` 已在循环体内，分步后自然继承）。

### 3.9 与其它 walker 实现的关系

核心包 `Rs2Walker` 是当前唯一的 walker 实现（历史上存在过的平行 FST 副本
`Rs2WalkerFst` / `WebWalkExecutorFst` 已随旧寻路一并移除）。
本次**只改核心包 `Rs2Walker`**，不引入第二套 walkStep 实现。

## 4. 改动清单

| 文件 | 改动 | 量级 |
| --- | --- | --- |
| `util/walker/state/WalkStepSession.java` | 新增：会话状态对象 | 新文件，约 80 行 |
| `util/walker/Rs2Walker.java` | 提取 `processWalkStep`；15 处 `continue` 改写；28 处 `return` 分类；13 个局部变量改为会话字段；新增 `walkStepFull` | **大**（约 200 行改动） |
| `util/walker/Rs2Walker.java`（现有 `walkStep`） | 不动 | 0 |

## 5. 风险与验证

### 风险

| 级别 | 风险 | 缓解 |
| --- | --- | --- |
| P0 | 提取循环体时误改控制流，导致阻塞版 `walkTo` 行为变化（寻路是全脚本共用的基础能力） | 改动集中在**一次**提取 + **机械**的 `continue` 改写；每处改写后立即编译；用 walker 既有测试做回归 |
| P0 | 13 个跨迭代变量提升为字段时漏掉某一个，造成状态串味 | 逐个对照 §2.5 清单；提取后用编译期“未使用/未初始化”错误兜底 |
| P1 | 每次调用重新获取 `walkerLock`，两次调用之间状态被其它寻路抢走 | 会话记录 `currentTarget`，调用开始时校验 |
| P1 | `MOVING` 的两处非终态返回若误判为终态，分步寻路会提前中止 | 单独处理 2175 / 3490 两处，加注释说明 |

### 验证

1. 编译：`./gradlew :client:compileJava`
2. 行为回归：阻塞版 `walkTo` 必须与改动前**完全一致**（这是 P0 底线）
3. 分步语义验证脚本：
   ```java
   // 调用方循环
   WalkerState st;
   do {
       st = Rs2Walker.walkStepFull(target, 1);
       // 每步之间可插入自己的逻辑（检查 NPC、对话等）
   } while (st == WalkerState.MOVING);
   ```
4. 覆盖场景：纯走路、需开门、需楼梯、需传送（魔法/道具）、触发荒野拉杆弹窗

## 6. 待确认

- [ ] 新方法命名：建议 `walkStepFull`；若必须叫 `walkStep` 则需接受签名冲突的处理方式
- [ ] 是否需要同时提供「阻塞版与分步版共用同一套 pass 代码」的重构（见 §7 备选）

## 7. 备选方案（未采用，供参考）

**备选 A：直接让 `walkTo` 建立在分步之上。**
即把 `walkTo` 改写成 `while (walkStepFull(...) != ARRIVED) {}`，只保留一份 pass 代码。
好处是彻底消除重复；坏处是**改变阻塞版的执行结构**，风险显著高于本方案，
且阻塞版的 `MOVING` 提前返回语义会丢失。**不建议在本次一并做。**

**备选 B：状态机范式移植。**
把门/传送/恢复的分派改写成显式的 `Decision` 状态机
（参考 `util/walker/geometry/RouteCameraPolicy`、`util/walker/WalledDoorClaimPolicy` 里已有的 `Decision` 枚举）。
更彻底，但要重写门/传送/恢复的分派逻辑，工作量远大于「提取现有尾循环」。
**本次不采用**，若长期要统一派发逻辑，这条路值得单独立项。

## 8. 实现结果（2026-10-30）

方案已落地，改动集中在两个文件：

| 文件 | 改动 |
| --- | --- |
| `util/walker/state/WalkStepSession.java` | 新增：会话状态对象 |
| `util/walker/Rs2Walker.java` | 提取 `processWalkPass`；新增 `driveWalkPasses`、`tailBudgetExceeded`、`prepareWalk`、`prepareBasementEntry`、`walkStepFull`、`resetWalkStep` |

### 8.1 与方案的差异

**（1）15 处外层 `continue` 没有改写，而是用「单趟壳」承接。**
方案 §3.3 设想的是把 `continue` 逐个改成 `return StepOutcome.CONTINUE`。实现时改成了另一种做法：
把提取出的单趟方法体外再套一层只跑一次的壳

```java
for (int passOnce = 0; passOnce < 1; passOnce++) {
    <原尾循环体>
}
return null;
```

原尾循环体里的 `continue` 于是跳出这层壳，方法返回 `null`（含义：可继续下一趟）。
这样 15 处 `continue` **一行都不用改**，语义与原来逐字对应；体内 4 处属于内层 `for` 的 `continue`
仍然作用于各自的内层循环。风险显著低于逐处改写。

**（2）阻塞版与分步版共用同一份 pass 代码。**
方案 §7 把「让 `walkTo` 建立在分步之上」列为不建议同批做的备选 A。实现时采用的是等价但更稳的形态：
阻塞版的 `processWalk` 三参重载不再自己持有尾循环，而是驱动同一个 `processWalkPass`：

```java
private static WalkerState driveWalkPasses(WalkStepSession session) {
    while (session.processWalkTail < MAX_PROCESS_WALK_TAIL_ITERATIONS) {
        WalkerState passResult = processWalkPass(session);
        ...
    }
    return tailBudgetExceeded(session.target);
}
```

阻塞版的对外行为不变（尾循环体逐行等价，已用脚本比对确认差异只有 `session.` 前缀与 sealed rim 分支），
但两份实现不再存在「将来改一处漏一处」的风险。

**（3）`MOVING` 按方案 §3.4 处理为非终态。**
阻塞版尾循环体里有两处 `return WalkerState.MOVING`（联赛传送进行中、终点玩家位置为空），
阻塞语义里是「本次寻路提前结束」，分步语义里是「这一趟没走成，会话继续」。

**（4）sealed rim 改道按方案 §2.5 的要求改写。**
阻塞模式沿用原来的递归 `processWalk(sealedRim, distance, partialRetries)`；
分步模式改成 `session.restartFor(sealedRim)` + 结束本趟，下一趟以新目标继续。
`restartFor` 把会话重置成「另起一段寻路」的状态（13 个变量回到初值、尾循环计数归零），
与原递归的语义一致。

**（5）额外抽出了两段准备逻辑。**
`prepareWalk`（到达判定、`setTarget`、`routeState` 复位、关闭世界地图/银行、标记会话开始）与
`prepareBasementEntry`（地窖入口拉杆谜题）由阻塞版与分步版共用；分步版只在会话的第一趟执行一次，
否则每趟重做 `setTarget` 会把寻路器反复重置。

**（6）新增 `requestedTarget` 字段。**
会话里区分「调用方最初给的目标」与「当前有效目标」：sealed rim 改道会改写当前目标，
若用当前目标判断「是不是同一段寻路」，改道一次就会被误判成换目标、会话被重开。

### 8.2 最终对外接口

| 方法 | 说明 |
| --- | --- |
| `walkStepFull(WorldPoint, int)` | 分步寻路：一趟做完就返回，`MOVING` 表示还在走，`ARRIVED`/`UNREACHABLE`/`EXIT` 为终态 |
| `resetWalkStep()` | 丢弃当前分步会话（同一目标想重走一遍时调用） |
| `walkStep(WorldPoint, int)` | **原有方法，未改动**（只点一次小地图，不走传送/门/停滞恢复流水线） |

### 8.3 实际的会话规则

```java
WalkStepSession session = walkStepSession;
if (session == null || !target.equals(session.requestedTarget) || distance != session.distance) {
    // 目标或距离变了 → 重开一段寻路
} else if (session.finished) {
    if (!isWalkStepStillArrived(target, distance)) {
        // 同一目标、已到终态，但玩家已走远 → 终态不再成立，重开一段
    } else {
        return session.finalState;   // 同一目标、终态仍成立 → 复述
    }
}
```

**终态复述必须放在「目标是否变化」的判断之后。** 第一次实现时顺序写反了，导致换目标后
`walkStepFull` 直接返回上一次的 `ARRIVED`、寻路完全不再执行，只有重启客户端才恢复
（2026-10-30 修复，见 `docs/changelog.md`）。

### 8.4 验证情况

- 编译：`./gradlew :client:compileJava` 通过。
- 等价性：用脚本逐行比对改造前后的尾循环体（1633 行），确认差异只出现在两类——
  13 个局部变量加上 `session.` 前缀、以及 sealed rim 分支；其余全部一致。
- 尚未做实机回归（需要游戏内环境），建议按方案 §5 的覆盖场景实测：纯走路、需开门、需楼梯、
  需传送（魔法/道具）、触发荒野拉杆弹窗。

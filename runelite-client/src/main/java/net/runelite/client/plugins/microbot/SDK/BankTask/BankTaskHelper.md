# BankTasks 使用指南

BankTasks 是一套银行任务框架，提供声明式的物品管理能力，支持装备栏和背包的存入/取出操作，自动化处理银行与背包之间的物品流转。

---

## 核心类概览

| 类 | 作用 |
|---|---|
| `BankTask` | 任务主体，支持 Builder 模式构建任务，执行存入/取出/装备操作 |
| `BankAmount` | 数量规格枚举，定义 EXACT / RANGE / FILL / FILL_BUT_ONE 四种模式 |
| `BankAmount.Amount` | 数量规格的不可变容器，封装 mode、targetMin、targetMax |
| `ItemRequirement` | 背包物品需求，关联物品 ID 与数量规格 |
| `EquipmentReq` | 装备栏需求，关联装备槽位、物品 ID 与数量规格 |
| `BankHelper` | 银行操作的工具类，提供走向银行、打开/关闭银行等基础能力 |
| `BankExecuteResult` | 执行结果容器，包含状态码、消息和未满足的需求列表 |

---

## 数量规格模式（BankAmount）

所有数量的指定都通过 `BankAmount` 的工厂方法创建 `Amount` 对象：

```java
BankAmount.of(100)            // EXACT:   精确 100 个
BankAmount.range(50, 200)     // RANGE:   至少 50 个（200 仅作信息参考）
BankAmount.fill(6000)        // FILL:    补充到 6000 个
BankAmount.fillButOne(100)    // FILL_BUT_ONE: 补充到 100 个，但银行保留 1 个
```

### 各模式行为详解

| 模式 | 存入行为 | 取出行为 | 满足条件 |
|---|---|---|---|
| `EXACT` | 超出目标的部分存入银行 | 取出到恰好等于目标数量 | 背包中恰好等于目标 |
| `RANGE` | 不存入（无上限） | 取出直到达到最小目标 | 背包中 >= 最小目标 |
| `FILL` | 超出目标的部分存入银行 | 取出直到达到目标数量 | 背包中 >= 目标 |
| `FILL_BUT_ONE` | 同 FILL | 同 FILL（银行始终保留 1 个） | 同 FILL |

---

## BankTask 快速入门

```java
// 构建任务：背包中精确 100 个金币，戒指栏装备一件物品
BankTask task = BankTask.builder()
    .addInvItem(995, BankAmount.of(100))                                     // 金币精确 100
    .addInvItem(12345, BankAmount.fill(28))                                // 补充药水上 28
    .addEquipmentItem(BankTask.EquipmentReq(EquipmentSlot.RING).item(123, BankAmount.of(1)))
    .build();

// 检查是否已满足（无需银行操作）
if (!task.isSatisfied()) {
    // 执行银行任务（自动走向银行、打开、存入、取出、装备、关闭）
    BankExecuteResult result = task.execute();
    Logger.info(result);
}
```

---

## BankTask 方法详解

### 构建相关

#### BankTask.builder()

创建新的 Builder 实例，支持链式调用添加需求：

```java
BankTask task = BankTask.builder()
    .addInvItem(...)
    .addEquipmentItem(...)
    .build();
```

#### Builder.addInvItem(int itemId, BankAmount.Amount amount)

添加背包物品需求，使用数量规格对象指定数量：

```java
.addInvItem(995, BankAmount.of(100))        // 精确 100
.addInvItem(995, BankAmount.range(50,200))  // 至少 50
.addInvItem(995, BankAmount.fill(6000))     // 补充到 6000
```

#### Builder.addInvItem(int itemId, int amount)

添加背包物品需求，指定精确数量（等价于 `addInvItem(itemId, BankAmount.of(amount))`）：

```java
.addInvItem(995, 10000)  // 精确 10000 个金币
```

#### Builder.addInvItem(int itemId, int min, int max)

添加背包物品需求，指定范围（等价于 `addInvItem(itemId, BankAmount.range(min, max))`）：

```java
.addInvItem(995, 100, 200)  // 100 到 200
```

#### Builder.addInvItemFill(int itemId, int target)

添加背包物品需求，补充到目标数量（等价于 `addInvItem(itemId, BankAmount.fill(target))`）：

```java
.addInvItemFill(995, 6000)  // 补充到 6000
```

#### Builder.addInvItem(String itemName, ...)

通过物品名称（而非物品 ID）添加背包需求。物品 ID 在 `execute()` 时自动从银行/背包缓存中解析：

```java
.addInvItem("Coins", 10000)                          // 精确名称匹配
.addInvItem("Coins", BankAmount.fill(6000))
.addInvItem("Coins", BankAmount.range(1000, 10000))
```

#### Builder.addInvItem(Filter\<Item\> filter, ...)

通过过滤器添加背包需求，优先扫描银行，未找到时扫描背包：

```java
.addInvItem(i -> i.getName().contains("Logs"), BankAmount.of(100))
.addInvItem(i -> i.getName().contains("Logs"), BankAmount.fill(100))
.addInvItem(i -> i.getName().contains("Logs"), 50, 200)
```

#### Builder.addEquipmentItem(EquipmentReq requirement)

添加装备栏需求，使用 `BankTask.EquipmentReq(slot)` 或 `EquipmentReq.slot(slot)` 创建：

```java
.addEquipmentItem(BankTask.EquipmentReq(EquipmentSlot.RING).item(123, BankAmount.of(1)))
.addEquipmentItem(BankTask.EquipmentReq(EquipmentSlot.CAPE).item("Cape of legends", BankAmount.of(1)))
.addEquipmentItem(BankTask.EquipmentReq(EquipmentSlot.AMMO).item(882, BankAmount.fill(600)))
```

#### Builder.addAll(BankTask other)

合并另一个 BankTask 的所有需求：

```java
BankTask base = BankTask.builder().addInvItem(995, 1000).build();
BankTask extended = BankTask.builder().addAll(base).addInvItem(1234, 5).build();
```

#### Builder.build()

构建不可变的 BankTask 实例。执行冲突检测：同一物品 ID 不能同时出现在装备需求和背包需求中：

```java
BankTask task = BankTask.builder()
    .addInvItem(123, 10)
    .addEquipmentItem(BankTask.EquipmentReq(EquipmentSlot.RING).item(123, BankAmount.of(1)))
    .build();
// 抛出 IllegalStateException: Conflict: item ... is required in both...
```

---

### 查询方法

#### isSatisfied()

检查当前背包和装备是否已满足所有需求（无需银行操作）。如果背包中有不在需求范围内的物品，会先将其存入银行后再判断：

```java
if (task.isSatisfied()) {
    Logger.info("Already equipped and stocked.");
}
```

执行逻辑：

1. 调用 `depositInventoryItemsNotInRequirements()` 存入所有无关物品
2. 检查所有装备栏需求是否满足
3. 检查所有背包需求是否满足（inventory + equipment 合计）

#### hasRequiredItems()

检查银行中是否有足够的物品来满足所有需求。用于在执行前预判是否会因物品不足而失败：

```java
if (!task.hasRequiredItems()) {
    Logger.error("Missing required items in bank!");
    return;
}
```

此方法不会触发任何银行操作，仅查询银行缓存数据。

#### getUnsatisfiedRequirements()

返回当前无法满足的需求列表（银行 + 背包 + 装备总数不足）：

```java
List<String> missing = task.getUnsatisfiedRequirements();
missing.forEach(r -> Logger.warn("Missing: " + r));
```

#### getStatusSummary()

返回人类可读的状态摘要，包含每个需求的当前值、目标值和满足状态：

```java
Logger.info(task.getStatusSummary());
// 输出示例：
// === BankTask Status ===
//   [OK] RING: Ring of duelling (1/1)
//   [MISSING] Coins (50/100)
```

#### getEquipmentRequirements() / getInvRequirements()

返回装备/背包需求列表（不可变）：

```java
List<EquipmentReq> equipReqs = task.getEquipmentRequirements();
List<ItemRequirement> invReqs = task.getInvRequirements();
```

---

### 执行方法

#### execute()

执行完整的银行任务流程。按以下顺序执行：

1. **检查银行是否打开** — 若未打开则返回 `BANK_CLOSED`
2. **解析懒加载的物品 ID** — 将名称/过滤器形式的物品解析为物品 ID
3. **预检物品可用性** — 调用 `hasRequiredItems()`，不足则返回 `MISSING_ITEMS`
4. **预清理背包空间** — 若背包已满，先存入多余物品和非需求物品
5. **装备处理** — 遍历每个装备需求：卸下错误物品、从银行取出、装备
6. **背包处理** — 遍历每个背包需求：存入多余物品、从银行取出
7. **存入非需求物品** — 将背包中所有不在需求范围内的物品存入银行
8. **关闭银行**
9. **验证** — 调用 `isSatisfied()` 确认完成

```java
BankExecuteResult result = task.execute();
if (result.isSuccess()) {
    Logger.info("All done!");
} else if (result.isPartial()) {
    Logger.warn("Partially done: " + result.getUnsatisfiedRequirements());
} else {
    Logger.error("Failed: " + result.getStatus() + " — " + result.getMessage());
}
```

#### executeOrFatal()

执行任务并在失败时输出错误日志：

```java
task.executeOrFatal();  // 失败时 Logger.error("Fatal: " + result.getMessage())
```

---

## EquipmentReq 方法详解

### EquipmentReq.slot(EquipmentSlot slot)

静态工厂方法，指定目标装备槽位，返回 `SlotBuilder` 用于链式调用：

```java
EquipmentReq.slot(EquipmentSlot.RING)
EquipmentReq.slot(EquipmentSlot.AMMO)
EquipmentReq.slot(EquipmentSlot.CAPE)
```

支持的槽位：`HEAD`, `CAPE`, `AMULET`, `WEAPON`, `BODY`, `SHIELD`, `LEGS`, `HANDS`, `FEET`, `RING`, `AMMO`

### SlotBuilder.item(itemId, amount)

指定物品 ID 和数量规格：

```java
.slot(EquipmentSlot.RING).item(12345, BankAmount.of(1))
.slot(EquipmentSlot.AMMO).item(882, BankAmount.fill(600))
```

### SlotBuilder.item(itemName, amount)

通过名称指定物品，ID 在执行时解析：

```java
.slot(EquipmentSlot.RING).item("Ring of duelling", BankAmount.of(1))
```

### SlotBuilder.item(Filter\<Item\> filter, amount)

通过过滤器指定物品，优先扫描银行：

```java
.slot(EquipmentSlot.CAPE).item(i -> i.getName().contains("Cape"), BankAmount.of(1))
```

### SlotBuilder.empty()

确保槽位为空（不装备任何物品）：

```java
.slot(EquipmentSlot.RING).empty()
```

### 查询方法

```java
req.getSlot()                    // 装备槽位
req.getItemId()                  // 物品 ID
req.getItemName()                // 物品名称
req.getTargetMin()               // 目标最小数量
req.getMode()                    // 数量模式
req.getCurrentEquipmentCount()   // 当前装备数量
req.getCurrentBankCount()        // 银行中该物品数量
req.getCurrentInventoryCount()   // 背包中该物品数量
req.getTotalAvailableCount()     // 三处合计总数
req.isSatisfiedInEquipment()     // 装备槽是否已满足
req.hasRequiredItemsInBank()     // 银行中物品是否足够满足需求
req.needsUnequip()               // 是否需要卸下
req.needsEquip()                 // 是否需要装备
```

---

## ItemRequirement 方法详解

ItemRequirement 的工厂方法与 Builder 的 `addInvItem` 一一对应，通过包级私有静态方法创建：

```java
ItemRequirement.of(995, 100)                    // 精确数量
ItemRequirement.range(995, 50, 200)            // 范围
ItemRequirement.fill(995, 6000)                // 补充到目标
ItemRequirement.fillButOne(995, 100)           // 补充但保留 1 个
ItemRequirement.of("Coins", 10000)             // 按名称
ItemRequirement.of(i -> i.getName().contains("Logs"), 50)  // 按过滤器
```

### 查询方法

```java
req.getItemId()                // 物品 ID（支持懒加载解析）
req.getItemName()              // 物品名称
req.getTargetMin()             // 目标最小数量
req.getTargetMax()             // 目标最大数量
req.getMode()                  // 数量模式
req.getAmount()                // 数量规格 Amount 对象
req.getCurrentInventoryCount() // 当前背包数量
req.getCurrentBankCount()      // 银行中数量
req.getCurrentEquippedCount()  // 已装备数量
req.isSatisfiedInInventory()   // 背包中是否满足（不含装备）
req.isSatisfied()              // 背包 + 装备合计是否满足
req.hasRequiredItemsInBank()    // 三处合计是否足够满足需求
req.canWithdraw()              // 是否需要从银行取出
req.getWithdrawAmount()        // 应取出数量（0 表示已满足）
req.getDepositAmount()         // 应存入数量（0 表示无需存入）
```

### resolve()

触发懒加载解析，将名称/过滤器转换为物品 ID。由 `BankTask.execute()` 在银行打开时自动调用，用户无需手动调用。

---

## BankHelper 方法详解

BankHelper 提供银行基础操作的可靠封装，包含超时等待和错误处理。

### walkToBank() / walkToNearestBank()

走向最近的银行，使用 `BankLocation.getNearest()` 定位：

```java
if (BankHelper.walkToBank()) {
    // 已到达银行附近
}
```

内部逻辑：

1. 若已在银行附近（距离 <= 3），直接返回 true
2. 否则使用 `Walking.walk(target)` 走向银行
3. 等待最多 30 秒到达银行附近

### walkToBank(BankLocation location)

走向指定银行位置：

```java
BankLocation falador = BankLocation.getLocations().stream()
    .filter(l -> l.getName().contains("Falador"))
    .findFirst().orElse(null);
BankHelper.walkToBank(falador);
```

### openBank() / openBank(long timeoutMs)

打开银行界面，等待银行加载完成：

```java
if (BankHelper.openBank(6000)) {  // 默认 6 秒超时
    // 银行已打开
}
```

若银行已打开，直接返回 true。`openBank()` 等待 `Bank.isOpen() && Bank.isLoaded()` 同时成立，避免银行加载动画期间误判。

### closeBank() / closeBank(long timeoutMs)

关闭银行界面：

```java
BankHelper.closeBank(3000);  // 默认 3 秒超时
```

### isNearBank()

检查玩家是否在银行交互范围内（距离 <= 3）：

```java
if (BankHelper.isNearBank()) {
    BankHelper.openBank();
}
```

### isBankOpen()

检查银行界面是否已打开：

```java
if (BankHelper.isBankOpen()) {
    // 执行银行操作
}
```

### getClosestBankLocation()

获取最近的银行位置（用于调试或自定义行为）：

```java
BankLocation nearest = BankHelper.getClosestBankLocation();
```

---

## BankExecuteResult 方法详解

### 工厂方法（由 BankTask 内部调用）

```java
BankExecuteResult.success(task)              // 全部满足
BankExecuteResult.missingItems(task)         // 银行物品不足
BankExecuteResult.partial(task, unsatisfied) // 部分满足
BankExecuteResult.failure(task, status, msg)  // 执行失败
```

### 查询方法

```java
result.getStatus()                  // Status 枚举值
result.getMessage()                 // 人类可读消息
result.isSuccess()                  // 全部满足
result.isPartial()                  // 部分满足
result.isFailure()                  // 执行失败
result.getUnsatisfiedRequirements() // 未满足的需求列表
result.getTask()                    // 关联的 BankTask
```

### Status 枚举值

| 状态码 | 含义 |
|---|---|
| `SUCCESS` | 所有需求已满足 |
| `PARTIAL` | 部分需求未满足 |
| `MISSING_ITEMS` | 银行缺少所需物品 |
| `WALK_FAILED` | 无法走到银行 |
| `OPEN_FAILED` | 无法打开银行界面 |
| `CLOSE_FAILED` | 无法关闭银行界面 |
| `WITHDRAW_FAILED` | 取款操作失败 |
| `WITHDRAW_INCOMPLETE` | 取款后仍未满足需求 |
| `DEPOSIT_FAILED` | 存款操作失败 |
| `EQUIP_FAILED` | 装备操作失败 |
| `UNEQUIP_FAILED` | 卸下装备操作失败 |
| `BANK_CLOSED` | 银行在操作期间意外关闭 |

---

## BankAmount.Amount 方法详解

`Amount` 是 `BankAmount` 模式的不可变容器：

```java
Amount amount = BankAmount.of(100);
// amount.getMode()     -> EXACT
// amount.getTargetMin() -> 100
// amount.getTargetMax() -> 100
```

### Amount.isSatisfiedInInventory(int itemId)

检查背包中该物品是否满足此数量规格：

```java
amount.isSatisfiedInInventory(995);  // true 如果背包中金币恰好等于目标
```

### Amount.hasItemsInBank(int itemId)

检查银行中是否有足够的物品满足此数量规格：

```java
amount.hasItemsInBank(995);  // true 如果银行中金币 >= 目标最小值
```

### Amount.withdrawAmount(int invCount, int bankCount)

计算从银行取出的数量：

```java
amount.withdrawAmount(30, 100);  // 精确模式：需 100-30=70；范围/填充模式：取 min(needed, bank)
```

### Amount.depositAmount(int invCount)

计算应存入银行的超额数量：

```java
amount.depositAmount(150);  // 精确模式：150-100=50；范围模式：0
```

---

## 完整示例

```java
// 构建一个 Woodcutting 脚本的银行任务
BankTask bankTask = BankTask.builder()
    // 背包保留 26 个 normal logs，多的存入银行
    .addInvItem("Logs", BankAmount.range(26, 28))
    // 装备斗篷
    .addEquipmentItem(BankTask.EquipmentReq(EquipmentSlot.CAPE).item("Mithril cape", BankAmount.of(1)))
    // 戒指补充到满（从装备栏取出）
    .addEquipmentItem(BankTask.EquipmentReq(EquipmentSlot.RING).item("Ring of duelling(8)", BankAmount.of(1)))
    .build();

// 游戏主循环中
@Override
public void onLoop() {
    // 若当前状态不满足需求，执行银行任务
    if (!bankTask.isSatisfied()) {
        BankExecuteResult result = bankTask.execute();
        if (result.isFailure()) {
            Logger.error("Bank task failed: " + result.getStatus() + " — " + result.getMessage());
            return;
        }
    }

    // 执行砍树逻辑...
}

// 或使用 hasRequiredItems() 预检
@Override
public void onLoop() {
    if (!bankTask.hasRequiredItems()) {
        Logger.error("Missing items in bank!");
        return;
    }

    if (!bankTask.isSatisfied()) {
        bankTask.executeOrFatal();  // 失败则 fatal 日志
    }

    // 执行砍树逻辑...
}
```

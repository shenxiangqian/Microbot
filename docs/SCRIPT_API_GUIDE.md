# Microbot 脚本开发 API 完整指南

## 📖 目录

1. [快速开始](#快速开始)
2. [脚本基础架构](#脚本基础架构)
3. [线程模型与规则](#线程模型与规则)
4. [缓存与查询 API](#缓存与查询-api)
5. [核心工具类详解](#核心工具类详解)
6. [实体交互模式](#实体交互模式)
7. [游戏画面 Overlay 绘制](#游戏画面-overlay-绘制)
8. [常用开发模式](#常用开发模式)
9. [调试与测试](#调试与测试)
10. [最佳实践](#最佳实践)
11. [常见陷阱与解决方案](#常见陷阱与解决方案)

---

## 快速开始

### 最小可运行脚本

```java
@PluginDescriptor(
    name = "我的脚本",
    description = "脚本描述",
    tags = {"combat", "automation"},
    enabledByDefault = false
)
public class MyScriptPlugin extends Plugin {
    @Inject
    private MyScriptConfig config;
    
    private MyScript script;
    
    @Override
    protected void startUp() {
        if (script == null) {
            script = new MyScript();
        }
        script.run(config);
    }
    
    @Override
    protected void shutDown() {
        script.shutdown();
    }
}

public class MyScript extends Script {
    public boolean run(MyScriptConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;  // 阻塞事件检查
                
                // 你的脚本逻辑
                Microbot.status = "运行中...";
                
                // 使用缓存 API 查找 NPC
                Rs2NpcModel banker = Microbot.getRs2NpcCache().query()
                    .withName("Banker")
                    .nearestOnClientThread();
                
                if (banker != null) {
                    banker.click("Bank");
                    sleepUntil(() -> Rs2Bank.isOpen(), 3000);
                }
                
            } catch (Exception e) {
                Microbot.log("错误: " + e.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }
}
```

---

## 脚本基础架构

### 脚本生命周期

```
用户启用插件 → startUp()
              ↓
         创建 Script 实例
              ↓
         调用 script.run(config)
              ↓
    启动后台调度循环（ScheduledExecutorService）
              ↓
         每 tick 执行脚本逻辑
              ↓
用户禁用插件 → shutDown()
              ↓
         script.shutdown()
              ↓
         取消所有调度任务
```

### Script 基类

```java
public abstract class Script extends Global implements IScript {
    protected ScheduledExecutorService scheduledExecutorService;
    protected ScheduledFuture<?> mainScheduledFuture;
    
    // 检查脚本是否运行
    public boolean isRunning() {
        return mainScheduledFuture != null && !mainScheduledFuture.isDone();
    }
    
    // 清理资源
    public void shutdown() {
        if (mainScheduledFuture != null) {
            mainScheduledFuture.cancel(true);
        }
        // 重置共享状态
        Microbot.pauseAllScripts.set(false);
        Rs2Walker.disableTeleports = false;
    }
    
    // 默认执行前检查（阻塞事件、暂停标志等）
    public boolean run() {
        if (Microbot.getBlockingEventManager().shouldBlockAndProcess()) {
            return false;  // 有阻塞事件正在处理
        }
        if (Microbot.pauseAllScripts.get()) {
            return false;  // 脚本已暂停
        }
        if (Thread.currentThread().isInterrupted()) {
            return false;  // 线程已中断
        }
        return true;
    }
}
```

---

## 线程模型与规则

### ⚠️ 硬性规则

#### 1. 永远不要在客户端线程上阻塞

```java
// ❌ 错误 - 在客户端线程上 sleep
Microbot.getClientThread().invoke(() -> {
    Thread.sleep(1000);  // 会冻结游戏！
});

// ✅ 正确 - 在后台线程上 sleep
mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
    sleep(1000);  // 安全
}, 0, 600, TimeUnit.MILLISECONDS);
```

#### 2. 游戏状态读写必须在客户端线程

```java
// ❌ 错误 - 直接在后台线程访问游戏状态
Player player = Microbot.getClient().getLocalPlayer();  // 线程不安全！
String name = player.getName();  // 可能崩溃

// ✅ 正确 - 通过客户端线程包装
String name = Microbot.getClientThread()
    .runOnClientThreadOptional(() -> {
        Player p = Microbot.getClient().getLocalPlayer();
        return p != null ? p.getName() : null;
    })
    .orElse(null);
```

#### 3. 使用 sleepUntil 而不是固定 sleep 等待游戏状态

```java
// ❌ 错误 - 固定延迟等待
banker.click("Bank");
sleep(2000);  // 可能太长或太短
if (Rs2Bank.isOpen()) { ... }

// ✅ 正确 - 条件等待
banker.click("Bank");
sleepUntil(() -> Rs2Bank.isOpen(), 3000);  // 最多等待 3 秒，一旦银行打开立即继续
```

### 线程辅助方法

| 方法 | 用途 | 线程要求 |
|------|------|----------|
| `sleep(ms)` | 固定延迟 | 后台线程 |
| `sleepUntil(condition, timeout)` | 条件等待 | 后台线程 |
| `sleepUntilOnClientThread(condition, timeout)` | 客户端线程条件轮询 | 后台线程调用 |
| `sleepUntilNextTick()` | 等待下一个游戏 tick | 后台线程 |
| `sleepTicks(n)` | 等待 N 个游戏 tick | 后台线程 |
| `clientThread.invoke(runnable)` | 同步执行 | 任意线程 |
| `clientThread.runOnClientThreadOptional(supplier)` | 同步返回值 | 任意线程 |

---

## 缓存与查询 API

### ⚠️ 强制规则：始终使用单例缓存

```java
// ❌ 错误 - 永远不要直接实例化
Rs2NpcCache cache = new Rs2NpcCache();  // 违规！

// ✅ 正确 - 使用 Microbot 单例
Rs2NpcCache cache = Microbot.getRs2NpcCache();
```

### 可用缓存

| 缓存类型 | 获取方法 | 用途 |
|---------|----------|------|
| `Rs2NpcCache` | `Microbot.getRs2NpcCache()` | NPC 查询 |
| `Rs2PlayerCache` | `Microbot.getRs2PlayerCache()` | 玩家查询 |
| `Rs2TileItemCache` | `Microbot.getRs2TileItemCache()` | 地面物品查询 |
| `Rs2TileObjectCache` | `Microbot.getRs2TileObjectCache()` | 游戏对象查询 |

### 查询模式

#### 基础查询结构

```java
缓存.query()
    .过滤条件1()
    .过滤条件2()
    .终止操作();
```

#### 终止操作

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `.nearest()` | 单个实体 | 最近的匹配项 |
| `.nearestOnClientThread()` | 单个实体 | 在客户端线程上执行（推荐） |
| `.first()` | 单个实体 | 第一个匹配项（不保证最近） |
| `.firstOnClientThread()` | 单个实体 | 在客户端线程上执行 |
| `.toList()` | 列表 | 所有匹配项 |
| `.toListOnClientThread()` | 列表 | 在客户端线程上执行 |
| `.count()` | 整数 | 匹配项数量 |

#### 过滤条件

```java
// 按名称过滤
.withName("Banker")
.withNames("Banker", "Bank clerk")

// 按 ID 过滤
.withId(1234)
.withIds(1234, 5678)

// 按距离过滤
.within(10)  // 玩家 10 格内
.within(worldPoint, 15)  // 特定点 15 格内

// 自定义条件
.where(npc -> !npc.isInteracting())
.where(npc -> npc.getHealthRatio() > 0)
```

### 完整示例

```java
// 查找最近的未交互的银行员
Rs2NpcModel banker = Microbot.getRs2NpcCache().query()
    .withName("Banker")
    .where(npc -> !npc.isInteracting())
    .nearestOnClientThread();

if (banker != null) {
    banker.click("Bank");
}

// 查找 10 格内所有满血的牛
List<Rs2NpcModel> cows = Microbot.getRs2NpcCache().query()
    .withName("Cow")
    .where(npc -> !npc.isInteracting())
    .where(npc -> npc.getHealthRatio() > 0)
    .within(10)
    .toListOnClientThread();

// 查找最近的值钱掉落物
Rs2TileItemModel loot = Microbot.getRs2TileItemCache().query()
    .where(item -> item.getTotalGeValue() >= 5000)
    .where(Rs2TileItemModel::isLootAble)
    .nearestOnClientThread();

// 查找最近的树
Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache().query()
    .withName("Tree")
    .nearestOnClientThread();
```

---

## 核心工具类详解

### Rs2Inventory - 背包管理

```java
// 检查物品
boolean hasItem = Rs2Inventory.contains("Coins");
boolean hasItems = Rs2Inventory.contains(995, 561);
int count = Rs2Inventory.count("Shark");
boolean isFull = Rs2Inventory.isFull();
boolean isEmpty = Rs2Inventory.isEmpty();
int emptySlots = Rs2Inventory.emptySlotCount();

// 获取物品
Rs2Item item = Rs2Inventory.get("Shark");
Rs2Item itemById = Rs2Inventory.get(995);
List<Rs2Item> allItems = Rs2Inventory.items();

// 交互
Rs2Inventory.interact("Shark", "Eat");
Rs2Inventory.use("Knife");  // 选中物品
Rs2Inventory.combine("Knife", "Logs");  // 组合物品
Rs2Inventory.drop("Bones");
Rs2Inventory.dropAll("Bones");
Rs2Inventory.dropAllExcept("Coins", "Food");

// 装备
Rs2Inventory.wield("Bronze sword");
Rs2Inventory.equip(1277);  // 装备 ID
```

### Rs2Bank - 银行操作

```java
// 打开/关闭
boolean opened = Rs2Bank.openBank();  // 自动寻找最近的银行
Rs2Bank.closeBank();
boolean isOpen = Rs2Bank.isOpen();

// 存款
Rs2Bank.depositAll();
Rs2Bank.depositAll("Bones");
Rs2Bank.depositAllExcept("Coins", "Food");
Rs2Bank.depositEquipment();
Rs2Bank.depositOne("Logs");
Rs2Bank.depositX("Logs", 10);

// 取款
Rs2Bank.withdrawOne("Shark");
Rs2Bank.withdrawAll("Shark");
Rs2Bank.withdrawX("Shark", 5);
Rs2Bank.withdrawAndEquip(1277);  // 取款并装备

// 查询
boolean hasItem = Rs2Bank.hasBankItem("Shark");
int count = Rs2Bank.count("Shark");
Rs2Item item = Rs2Bank.findBankItem("Shark");

// 导航
boolean walked = Rs2Bank.walkToBank();
BankLocation nearest = Rs2Bank.getNearestBank();
```

### Rs2Player - 玩家状态

```java
// 位置
WorldPoint location = Rs2Player.getWorldLocation();
boolean isMoving = Rs2Player.isMoving();
boolean isWalking = Rs2Player.isWalking();
boolean isAnimating = Rs2Player.isAnimating();

// 战斗
boolean inCombat = Rs2Player.isInCombat();
boolean inMulti = Rs2Player.isInMulti();
int combatLevel = Rs2Player.getCombatLevel();

// 生命值
int hp = Rs2Player.getHp();
int maxHp = Rs2Player.getMaxHp();
boolean fullHealth = Rs2Player.isFullHealth();
Rs2Player.eatAt(50);  // 低于 50% 血量时吃食物

// 能量
int energy = Rs2Player.getRunEnergy();
boolean isRunEnabled = Rs2Player.isRunEnabled();
Rs2Player.toggleRunEnergy(true);

// Potion 效果
boolean hasAntifire = Rs2Player.hasAntiFireActive();
boolean hasRanging = Rs2Player.hasRangingPotionActive();
boolean hasAntiVenom = Rs2Player.hasAntiVenomActive();

// 其他
boolean isMember = Rs2Player.isMember();
boolean inPoh = Rs2Player.isInPoh();
Rs2Player.logout();
Rs2Player.waitForWalking();  // 等待走路完成
```

### Rs2Walker - 寻路与移动

```java
// 基础移动
Rs2Walker.walkTo(new WorldPoint(3100, 3500, 0));
Rs2Walker.walkTo(3100, 3500, 0);
Rs2Walker.walkTo(target, 5);  // 5 格内即可

// 小地图点击
Rs2Walker.walkMiniMap(new WorldPoint(3100, 3500, 0));

// 画布点击（短距离）
Rs2Walker.walkFastCanvas(new WorldPoint(3100, 3500, 0));

// 路径查询
List<WorldPoint> path = Rs2Walker.getWalkPath(target);
boolean canReach = Rs2Walker.canReach(target);
int distance = Rs2Walker.getTotalTiles(target);

// 控制
Rs2Walker.setTarget(target);  // 设置目标
Rs2Walker.setTarget(null);    // 取消目标
```

### Global - 延迟与等待工具

```java
// 固定延迟
sleep(1000);  // 1 秒
sleep(500, 1500);  // 500-1500 毫秒随机
sleepGaussian(1000, 200);  // 高斯分布，均值 1000，标准差 200

// 疲劳调整（反检测）
sleepFatigued(1000);  // 根据会话疲劳调整延迟
sleepGaussianFatigued(1000, 200);

// 条件等待
sleepUntil(() -> Rs2Bank.isOpen(), 5000);  // 最多等 5 秒
sleepUntilOnClientThread(() -> widget.isHidden(), 3000);

// Tick 等待
sleepUntilNextTick();  // 等待下一个游戏 tick
sleepTicks(3);  // 等待 3 个 tick

// 带动作的等待
sleepUntil(
    () -> Rs2Inventory.contains("Logs"),  // 条件
    () -> tree.click("Chop down"),        // 重复动作
    10000,  // 超时
    600     // 轮询间隔
);
```

---

## 实体交互模式

### NPC 交互

```java
// 方法 1: 缓存 API（推荐）
Rs2NpcModel banker = Microbot.getRs2NpcCache().query()
    .withName("Banker")
    .where(npc -> !npc.isInteracting())
    .nearestOnClientThread();

if (banker != null) {
    banker.click("Bank");
    sleepUntil(() -> Rs2Bank.isOpen(), 3000);
}

// 方法 2: 战斗脚本模式
Rs2NpcModel enemy = Microbot.getRs2NpcCache().query()
    .withName("Goblin")
    .where(npc -> !npc.isInteracting())
    .where(npc -> npc.getHealthRatio() > 0)
    .nearest(10);

if (enemy != null && !Rs2Player.isInCombat()) {
    enemy.click("Attack");
    sleepUntil(() -> Rs2Player.isInCombat(), 2000);
}
```

### 游戏对象交互

```java
// 树木砍伐
Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache().query()
    .withName("Oak tree")
    .nearestOnClientThread();

if (tree != null && !Rs2Player.isAnimating()) {
    tree.click("Chop down");
    sleepUntil(() -> Rs2Player.isAnimating(), 3000);
}

// 门户交互
Rs2TileObjectModel portal = Microbot.getRs2TileObjectCache().query()
    .withName("Portal")
    .nearestOnClientThread();

if (portal != null) {
    portal.click("Enter");
    sleepUntil(() -> Rs2Player.isMoving(), 2000);
}
```

### 地面物品拾取

```java
// 拾取特定物品
Rs2TileItemModel item = Microbot.getRs2TileItemCache().query()
    .withName("Coins")
    .nearestOnClientThread();

if (item != null && !Rs2Inventory.isFull()) {
    item.pickup();
    sleepUntil(() -> Rs2Inventory.contains("Coins"), 3000);
}

// 拾取值钱的掉落物
Rs2TileItemModel loot = Microbot.getRs2TileItemCache().query()
    .where(Rs2TileItemModel::isLootAble)
    .where(i -> i.getTotalGeValue() >= 1000)
    .where(i -> !i.willDespawnWithin(10))  // 不会在 10 秒内消失
    .nearestOnClientThread();

if (loot != null) {
    loot.pickup();
}
```

---

## 游戏画面 Overlay 绘制

### 什么是 Overlay？

Overlay 是覆盖在游戏画面上的自定义图形层，可以用来：
- 显示脚本状态信息（面板）
- 在游戏世界中高亮显示对象、地块、路径
- 在小地图上绘制标记
- 显示调试信息

### Overlay 类型

| 类型 | 基类 | 用途 |
|------|------|------|
| **OverlayPanel** | 信息面板 | 显示文本状态、统计数据（通常在角落） |
| **Overlay** | 游戏世界绘制 | 高亮地块、NPC、对象、路径 |
| **WidgetItemOverlay** | 物品高亮 | 高亮背包/银行中的特定物品 |

### 创建信息面板 Overlay

#### 步骤 1: 创建 Overlay 类

```java
package net.runelite.client.plugins.microbot.myscript;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import javax.inject.Inject;
import java.awt.*;

public class MyScriptOverlay extends OverlayPanel {
    private final MyScriptPlugin plugin;
    
    @Inject
    MyScriptOverlay(MyScriptPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);  // 面板位置
        setPreferredSize(new Dimension(200, 150));  // 面板大小
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        // 清空之前的内容
        panelComponent.getChildren().clear();
        
        // 添加标题
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("我的脚本")
            .color(Color.GREEN)
            .build());
        
        // 添加分隔线
        panelComponent.getChildren().add(LineComponent.builder().build());
        
        // 添加状态信息
        panelComponent.getChildren().add(LineComponent.builder()
            .left("状态:")
            .right("运行中")
            .leftColor(Color.WHITE)
            .rightColor(Color.GREEN)
            .build());
        
        // 添加计数器
        panelComponent.getChildren().add(LineComponent.builder()
            .left("完成次数:")
            .right(String.valueOf(plugin.getCompletedCount()))
            .build());
        
        return super.render(graphics);
    }
}
```

#### 步骤 2: 在插件中注册 Overlay

```java
package net.runelite.client.plugins.microbot.myscript;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Inject;

@PluginDescriptor(
    name = "我的脚本",
    description = "脚本描述",
    enabledByDefault = false
)
public class MyScriptPlugin extends Plugin {
    
    @Inject
    private MyScriptOverlay overlay;
    
    @Inject
    private OverlayManager overlayManager;
    
    @Inject
    private MyScript script;
    
    private int completedCount = 0;
    
    @Override
    protected void startUp() {
        overlayManager.add(overlay);  // 添加 overlay
        script.run();
    }
    
    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);  // 移除 overlay
        script.shutdown();
    }
    
    public int getCompletedCount() {
        return completedCount;
    }
    
    public void incrementCount() {
        completedCount++;
    }
}
```

### 游戏世界绘制 Overlay

用于在 3D 游戏世界中绘制高亮、标记等。

```java
package net.runelite.client.plugins.microbot.myscript;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;

public class MyWorldOverlay extends Overlay {
    
    private final Client client;
    private final MyScriptPlugin plugin;
    
    @Inject
    public MyWorldOverlay(Client client, MyScriptPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);  // 跟随游戏视角
        setLayer(OverlayLayer.ABOVE_SCENE);    // 绘制在场景上方
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (!Microbot.isLoggedIn()) {
            return null;
        }
        
        // 获取要高亮的地块列表
        List<WorldPoint> tilesToHighlight = plugin.getTilesToHighlight();
        
        for (WorldPoint worldPoint : tilesToHighlight) {
            // 转换为本地坐标
            LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
            if (localPoint == null) {
                continue;
            }
            
            // 获取地块的多边形
            Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
            if (poly == null) {
                continue;
            }
            
            // 绘制填充
            graphics.setColor(new Color(0, 255, 0, 50));  // 半透明绿色
            graphics.fillPolygon(poly);
            
            // 绘制边框
            graphics.setColor(new Color(0, 255, 0, 150));  // 绿色边框
            graphics.drawPolygon(poly);
        }
        
        return null;
    }
}
```

### Overlay 位置选项

```java
// 信息面板位置
setPosition(OverlayPosition.TOP_LEFT);      // 左上角
setPosition(OverlayPosition.TOP_CENTER);    // 顶部中间
setPosition(OverlayPosition.TOP_RIGHT);     // 右上角
setPosition(OverlayPosition.BOTTOM_LEFT);   // 左下角
setPosition(OverlayPosition.BOTTOM_RIGHT);  // 右下角

// 游戏世界绘制
setPosition(OverlayPosition.DYNAMIC);       // 跟随游戏视角
```

### Overlay 层级

```java
setLayer(OverlayLayer.UNDER_WIDGETS);   // 在游戏 UI 下方
setLayer(OverlayLayer.ABOVE_SCENE);     // 在场景上方
setLayer(OverlayLayer.ABOVE_WIDGETS);   // 在游戏 UI 上方
setLayer(OverlayLayer.MANUAL);          // 手动控制
```

### 高级示例：绘制路径

```java
public class PathOverlay extends Overlay {
    
    private final Client client;
    private final MyScriptPlugin plugin;
    
    @Inject
    public PathOverlay(Client client, MyScriptPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (!Microbot.isLoggedIn()) {
            return null;
        }
        
        List<WorldPoint> path = plugin.getCurrentPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        // 绘制路径线
        graphics.setStroke(new BasicStroke(2));
        graphics.setColor(Color.CYAN);
        
        for (int i = 0; i < path.size() - 1; i++) {
            WorldPoint current = path.get(i);
            WorldPoint next = path.get(i + 1);
            
            LocalPoint currentLocal = LocalPoint.fromWorld(client, current);
            LocalPoint nextLocal = LocalPoint.fromWorld(client, next);
            
            if (currentLocal == null || nextLocal == null) {
                continue;
            }
            
            Polygon currentPoly = Perspective.getCanvasTilePoly(client, currentLocal);
            Polygon nextPoly = Perspective.getCanvasTilePoly(client, nextLocal);
            
            if (currentPoly != null && nextPoly != null) {
                Point currentCenter = getPolygonCenter(currentPoly);
                Point nextCenter = getPolygonCenter(nextPoly);
                
                graphics.drawLine(
                    currentCenter.getX(), currentCenter.getY(),
                    nextCenter.getX(), nextCenter.getY()
                );
            }
        }
        
        // 高亮目标点
        WorldPoint destination = path.get(path.size() - 1);
        LocalPoint destLocal = LocalPoint.fromWorld(client, destination);
        if (destLocal != null) {
            Polygon destPoly = Perspective.getCanvasTilePoly(client, destLocal);
            if (destPoly != null) {
                graphics.setColor(new Color(255, 0, 0, 100));
                graphics.fillPolygon(destPoly);
                graphics.setColor(Color.RED);
                graphics.drawPolygon(destPoly);
            }
        }
        
        return null;
    }
    
    private Point getPolygonCenter(Polygon polygon) {
        int centerX = 0;
        int centerY = 0;
        for (int i = 0; i < polygon.npoints; i++) {
            centerX += polygon.xpoints[i];
            centerY += polygon.ypoints[i];
        }
        return new Point(centerX / polygon.npoints, centerY / polygon.npoints);
    }
}
```

### 在文本旁绘制

```java
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.OverlayUtil;

@Override
public Dimension render(Graphics2D graphics) {
    WorldPoint target = plugin.getTargetLocation();
    if (target == null) return null;
    
    LocalPoint localPoint = LocalPoint.fromWorld(client, target);
    if (localPoint == null) return null;
    
    // 在地块上方显示文本
    Point textLocation = Perspective.getCanvasTextLocation(
        client, graphics, localPoint, "目标", 0
    );
    
    if (textLocation != null) {
        OverlayUtil.renderTextLocation(graphics, textLocation, "目标", Color.YELLOW);
    }
    
    return null;
}
```

### 完整示例：战斗脚本 Overlay

```java
public class CombatScriptOverlay extends OverlayPanel {
    private final CombatScript script;
    
    @Inject
    CombatScriptOverlay(CombatScriptPlugin plugin, CombatScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        
        // 标题
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("战斗脚本")
            .color(Color.RED)
            .build());
        
        panelComponent.getChildren().add(LineComponent.builder().build());
        
        // 状态
        panelComponent.getChildren().add(LineComponent.builder()
            .left("状态:")
            .right(script.getState())
            .leftColor(Color.WHITE)
            .rightColor(getStateColor(script.getState()))
            .build());
        
        // 击杀数
        panelComponent.getChildren().add(LineComponent.builder()
            .left("击杀:")
            .right(String.valueOf(script.getKillCount()))
            .build());
        
        // 掉落拾取
        panelComponent.getChildren().add(LineComponent.builder()
            .left("拾取:")
            .right(String.valueOf(script.getLootCount()))
            .build());
        
        // 运行时间
        long runtime = script.getRuntime();
        String timeStr = String.format("%02d:%02d:%02d", 
            runtime / 3600, (runtime % 3600) / 60, runtime % 60);
        panelComponent.getChildren().add(LineComponent.builder()
            .left("运行时间:")
            .right(timeStr)
            .build());
        
        return super.render(graphics);
    }
    
    private Color getStateColor(String state) {
        switch (state) {
            case "战斗中": return Color.RED;
            case "拾取": return Color.GREEN;
            case "吃食物": return Color.YELLOW;
            case "银行": return Color.CYAN;
            default: return Color.WHITE;
        }
    }
}
```

### Overlay 最佳实践

#### 1. 性能优化

```java
// ❌ 错误 - 每帧都查询
@Override
public Dimension render(Graphics2D graphics) {
    List<Rs2NpcModel> npcs = Microbot.getRs2NpcCache().query()
        .withName("Goblin")
        .toListOnClientThread();  // 每帧都查询，浪费性能
    // ...
}

// ✅ 正确 - 缓存查询结果
private List<Rs2NpcModel> cachedNpcs = new ArrayList<>();
private long lastUpdate = 0;

@Override
public Dimension render(Graphics2D graphics) {
    long now = System.currentTimeMillis();
    if (now - lastUpdate > 600) {  // 每个 tick 更新一次
        cachedNpcs = Microbot.getRs2NpcCache().query()
            .withName("Goblin")
            .toListOnClientThread();
        lastUpdate = now;
    }
    // 使用 cachedNpcs 绘制
}
```

#### 2. 空值检查

```java
@Override
public Dimension render(Graphics2D graphics) {
    if (!Microbot.isLoggedIn()) {
        return null;  // 未登录不绘制
    }
    
    LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
    if (localPoint == null) {
        return null;  // 坐标转换失败
    }
    
    Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
    if (poly == null) {
        return null;  // 不在视野内
    }
    
    // 绘制
}
```

#### 3. 可配置的显示开关

```java
// 在 Config 中添加
@ConfigItem(
    keyName = "showOverlay",
    name = "显示 Overlay",
    description = "是否显示脚本信息面板"
)
default boolean showOverlay() {
    return true;
}

// 在 Overlay 中检查
@Override
public Dimension render(Graphics2D graphics) {
    if (!config.showOverlay()) {
        return null;
    }
    // 正常绘制
}
```

---

## 常用开发模式

### 模式 1: 状态机脚本（3+ 阶段）

```java
public enum WoodcuttingState {
    WALK_TO_TREES,
    CHOP_TREE,
    WAIT_INVENTORY_FULL,
    WALK_TO_BANK,
    BANK_LOGS
}

public class WoodcuttingScript extends StateMachineScript<WoodcuttingState> {
    
    @Override
    protected WoodcuttingState initialState() {
        return WoodcuttingState.WALK_TO_TREES;
    }
    
    @Override
    protected List<Transition<WoodcuttingState>> defineTransitions() {
        return List.of(
            Transition.<WoodcuttingState>from(WALK_TO_TREES)
                .when(() -> isNearTrees(), "near trees")
                .because("Arrived at tree area")
                .goTo(CHOP_TREE),
                
            Transition.<WoodcuttingState>from(CHOP_TREE)
                .when(() -> Rs2Inventory.isFull(), "inventory full")
                .because("Inventory is full of logs")
                .goTo(WALK_TO_BANK),
                
            Transition.<WoodcuttingState>from(WALK_TO_BANK)
                .when(() -> Rs2Bank.isOpen(), "bank open")
                .because("Bank interface opened")
                .goTo(BANK_LOGS),
                
            Transition.<WoodcuttingState>from(BANK_LOGS)
                .when(() -> Rs2Inventory.isEmpty(), "inventory empty")
                .because("All logs deposited")
                .goTo(WALK_TO_TREES)
        );
    }
    
    @Override
    protected void onState(WoodcuttingState state) {
        switch (state) {
            case WALK_TO_TREES:
                Rs2Walker.walkTo(TREE_AREA);
                break;
                
            case CHOP_TREE:
                if (!Rs2Player.isAnimating()) {
                    Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache()
                        .query()
                        .withName("Oak tree")
                        .nearestOnClientThread();
                    if (tree != null) {
                        tree.click("Chop down");
                    }
                }
                sleep(600);
                break;
                
            case WALK_TO_BANK:
                Rs2Bank.walkToBank();
                break;
                
            case BANK_LOGS:
                if (!Rs2Bank.isOpen()) {
                    Rs2Bank.openBank();
                } else {
                    Rs2Bank.depositAllExcept("Bronze axe");
                }
                break;
        }
    }
}
```

### 模式 2: 简单循环脚本

```java
public class SimpleFishingScript extends Script {
    
    public boolean run(FishingConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                
                // 背包满了去银行
                if (Rs2Inventory.isFull()) {
                    bankFish();
                    return;
                }
                
                // 没在钓鱼就点击钓鱼点
                if (!Rs2Player.isAnimating()) {
                    Rs2NpcModel fishingSpot = Microbot.getRs2NpcCache().query()
                        .withName("Fishing spot")
                        .where(npc -> npc.hasAction("Net"))
                        .nearestOnClientThread();
                    
                    if (fishingSpot != null) {
                        fishingSpot.click("Net");
                        sleepUntil(() -> Rs2Player.isAnimating(), 2000);
                    }
                }
                
                sleep(600);
                
            } catch (Exception e) {
                Microbot.log("Error: " + e.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }
    
    private void bankFish() {
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.walkToBank();
            Rs2Bank.openBank();
        } else {
            Rs2Bank.depositAllExcept("Small fishing net");
            sleepUntil(() -> !Rs2Inventory.isFull(), 2000);
        }
    }
}
```

### 模式 3: 战斗脚本

```java
public class CombatScript extends Script {
    
    public boolean run(CombatConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                
                // 低血量吃食物
                if (Rs2Player.getHp() < config.eatAtHp()) {
                    Rs2Player.eatAt(config.eatAtHp());
                }
                
                // 拾取掉落物
                if (!Rs2Inventory.isFull()) {
                    lootItems();
                }
                
                // 不在战斗中就攻击
                if (!Rs2Player.isInCombat()) {
                    attackEnemy();
                }
                
                sleep(600);
                
            } catch (Exception e) {
                Microbot.log("Error: " + e.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }
    
    private void attackEnemy() {
        Rs2NpcModel enemy = Microbot.getRs2NpcCache().query()
            .withName("Goblin")
            .where(npc -> !npc.isInteracting())
            .where(npc -> npc.getHealthRatio() > 0)
            .nearest(10);
        
        if (enemy != null) {
            enemy.click("Attack");
            sleepUntil(() -> Rs2Player.isInCombat(), 2000);
        }
    }
    
    private void lootItems() {
        Rs2TileItemModel loot = Microbot.getRs2TileItemCache().query()
            .where(Rs2TileItemModel::isLootAble)
            .where(item -> item.getTotalGeValue() >= 100)
            .nearest(5);
        
        if (loot != null) {
            loot.pickup();
            sleepUntil(() -> Rs2Inventory.contains(loot.getName()), 3000);
        }
    }
}
```

---

## 调试与测试

### CLI 工具

```bash
# 编译检查
./gradlew :client:compileJava

# 运行测试
./gradlew :client:runUnitTests

# 查看游戏状态
./microbot-cli state

# 查看背包
./microbot-cli inventory

# 查看附近 NPC
./microbot-cli npcs --distance 10

# 查看银行
./microbot-cli bank

# 截图调试
./microbot-cli screenshot save --label debug

# 检查线程安全
./microbot-cli ct Player.getName
```

### 日志调试

```java
// 基础日志
Microbot.log("脚本开始");
Microbot.log("找到 NPC: " + npc.getName());

// 状态显示（显示在客户端）
Microbot.status = "正在砍树...";
Microbot.status = "背包满，去银行";

// 错误日志
try {
    // 代码
} catch (Exception e) {
    Microbot.log("错误: " + e.getMessage());
    Microbot.logStackTrace("详细错误: ", e);
}
```

### 状态机调试

```bash
# 获取状态机快照
curl -s http://127.0.0.1:8081/debug/snapshot | python3 -m json.tool

# 查看脚本健康状态
./microbot-cli scripts health --class "com.example.MyPlugin"

# 查看脚本状态
./microbot-cli scripts status --class "com.example.MyPlugin"
```

---

## 最佳实践

### 1. 空值检查

```java
// ❌ 错误
Rs2NpcModel banker = Microbot.getRs2NpcCache().query().withName("Banker").nearest();
banker.click("Bank");  // 可能 NullPointerException

// ✅ 正确
Rs2NpcModel banker = Microbot.getRs2NpcCache().query().withName("Banker").nearest();
if (banker != null) {
    banker.click("Bank");
} else {
    Microbot.log("找不到银行员");
}
```

### 2. 验证动作结果

```java
// ❌ 错误 - 不验证
banker.click("Bank");
Rs2Bank.depositAll("Bones");  // 银行可能还没开

// ✅ 正确 - 验证后继续
banker.click("Bank");
if (sleepUntil(() -> Rs2Bank.isOpen(), 3000)) {
    Rs2Bank.depositAll("Bones");
} else {
    Microbot.log("银行打开超时");
}
```

### 3. 使用疲劳延迟（反检测）

```java
// ❌ 机械化
sleep(600);
sleep(600);
sleep(600);

// ✅ 人性化
sleepGaussianFatigued(800, 150);  // 高斯分布 + 疲劳调整
sleepFatigued(500, 1000);  // 随机 + 疲劳
```

### 4. 合理使用距离限制

```java
// ❌ 可能查找很远的实体
Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
    .withName("Guard")
    .nearest();  // 可能在另一个房间

// ✅ 限制搜索范围
Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
    .withName("Guard")
    .within(15)  // 仅 15 格内
    .nearest();
```

### 5. 清理资源

```java
@Override
public void shutdown() {
    // 取消调度任务
    if (mainScheduledFuture != null) {
        mainScheduledFuture.cancel(true);
    }
    
    // 清理共享状态
    Microbot.status = "";
    Rs2Walker.setTarget(null);
    
    // 调用父类清理
    super.shutdown();
}
```

---

## 常见陷阱与解决方案

### 陷阱 1: 物品名称不匹配

**问题**：物品有充能或备注标记。

```java
// ❌ 找不到
Rs2Inventory.contains("Ring of dueling");  // 实际名称是 "Ring of dueling(8)"

// ✅ 解决方案 1 - 使用 ID
Rs2Inventory.contains(2552);

// ✅ 解决方案 2 - 部分匹配
Rs2Inventory.get(item -> item.getName().contains("Ring of dueling"));
```

### 陷阱 2: 银行缓存未就绪

**问题**：银行刚打开，缓存还没更新。

```java
// ❌ 可能看到空银行
Rs2Bank.openBank();
boolean has = Rs2Bank.hasBankItem("Logs");  // false（实际有）

// ✅ 正确 - 等待缓存更新
Rs2Bank.openBank();
sleepUntilNextTick();  // 等待一个 tick
boolean has = Rs2Bank.hasBankItem("Logs");  // 正确
```

### 陷阱 3: 硬编码物品动作

**问题**：不同物品有不同的菜单选项。

```java
// ❌ "Moonlight moth" 使用 "Release" 而不是 "Drink"
Rs2Inventory.interact(item, "Drink");  // 失败

// ✅ 从物品读取可用动作
String[] actions = item.getInventoryActions();
String action = Arrays.stream(actions)
    .filter(Objects::nonNull)
    .filter(a -> a.equalsIgnoreCase("drink") || a.equalsIgnoreCase("release"))
    .findFirst()
    .orElse(null);
if (action != null) {
    Rs2Inventory.interact(item, action);
}
```

### 陷阱 4: 路径阻塞未处理

**问题**：门关着但脚本不开门。

```java
// ❌ 直接走，可能卡住
Rs2Walker.walkTo(destination);

// ✅ Rs2Walker 自动处理门和障碍
// 只需确保目标可达
if (Rs2Walker.canReach(destination)) {
    Rs2Walker.walkTo(destination);
} else {
    Microbot.log("目标不可达");
}
```

### 陷阱 5: Widget ID 游戏更新后失效

**问题**：硬编码的 widget ID 在更新后可能改变。

```java
// ❌ 硬编码 ID
Rs2Widget.clickWidget(12, 42);  // 更新后可能失效

// ✅ 使用文本搜索
./microbot-cli widgets click --text "Deposit inventory"

// ✅ 代码中动态查找
Rs2Widget widget = Rs2Widget.getWidget(group, child -> 
    child.getText().contains("Deposit inventory"));
if (widget != null) {
    widget.interact();
}
```

---

## 附录：完整 API 索引

### 查询 API

- `Microbot.getRs2NpcCache().query()` - NPC 查询
- `Microbot.getRs2PlayerCache().query()` - 玩家查询
- `Microbot.getRs2TileItemCache().query()` - 地面物品查询
- `Microbot.getRs2TileObjectCache().query()` - 游戏对象查询

### 核心工具类

- `Rs2Inventory` - 背包管理
- `Rs2Bank` - 银行操作
- `Rs2Player` - 玩家状态
- `Rs2Walker` - 寻路移动
- `Rs2Combat` - 战斗相关
- `Rs2Magic` - 魔法施放
- `Rs2Prayer` - 祷告管理
- `Rs2Equipment` - 装备管理
- `Rs2Dialogue` - 对话处理
- `Rs2Widget` - 界面操作
- `Rs2Camera` - 相机控制
- `Rs2Keyboard` - 键盘输入
- `Rs2Tab` - 标签页切换

### 辅助工具

- `Global` - 延迟与等待
- `Rs2Random` - 随机数生成（反检测）
- `SessionFatigue` - 疲劳系统
- `Microbot.getClientThread()` - 线程管理
- `Microbot.status` - 状态显示

---

## 进一步学习

### 必读文档

1. **线程模型**：`runelite-client/.../microbot/AGENTS.md`
2. **状态机框架**：`runelite-client/.../statemachine/AGENTS.md`
3. **实体陷阱**：`docs/entity-guides/items.md`, `docs/entity-guides/movement.md`
4. **完整查询 API**：`runelite-client/.../api/QUERYABLE_API.md`
5. **CLI 工具**：`docs/MICROBOT_CLI.md`
6. **HTTP API**：`docs/AGENT_SERVER.md`

### 示例脚本

查看现有插件获取实际示例：
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/`

---

**最后更新**: 2026-08-27

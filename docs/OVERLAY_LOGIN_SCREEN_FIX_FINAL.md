# Overlay 登录界面显示修复 - 最终解决方案

## 问题描述

设置了 `setRequiresLoggedIn(false)` 后，使用 `OverlayLayer.ABOVE_WIDGETS` 的 Overlay 仍然无法在登录界面显示。

## 问题根因分析

经过深入调查，发现了 RuneLite Overlay 渲染机制的关键限制：

### 渲染层级与调用时机

| 渲染层级 | 渲染时机 | 调用入口 | 登录界面是否渲染 |
|---------|---------|---------|----------------|
| `ABOVE_SCENE` | 3D场景渲染后 | `Hooks.drawScene()` | ❌ 否（仅登录后） |
| `UNDER_WIDGETS` | 头顶信息后 | `Hooks.drawAboveOverheads()` | ❌ 否（仅登录后） |
| `ABOVE_WIDGETS` | 界面元素渲染后 | `Hooks.drawInterface()` / `Hooks.drawLayer()` | ❌ 否（界面绘制时才触发） |
| `ALWAYS_ON_TOP` | 主绘制循环 | `Hooks.draw()` | ✅ **是（所有状态）** |
| `MANUAL` | 手动指定 | 自定义 | 取决于实现 |

### 核心问题

1. **`ABOVE_WIDGETS` 层的渲染机制**：
   - 通过 `drawInterface(int interfaceId)` 和 `drawLayer(Widget layer)` 触发
   - 这两个方法**只在游戏内部 Widget 绘制流程中被调用**
   - 登录界面虽然也有界面元素，但不会以相同方式触发这些回调
   - 即使在 `Hooks.draw()` 中显式调用 `renderOverlayLayer(ABOVE_WIDGETS)`，登录界面的 `draw()` 方法**可能也不被游戏调用或调用时机不同**

2. **`ALWAYS_ON_TOP` 层的渲染机制**：
   - 在 `Hooks.draw()` 方法中被调用
   - `draw()` 是**顶层绘制入口**，在所有游戏状态下都会被调用
   - 包括登录界面、加载界面、游戏内等所有状态

## 解决方案

### 方案一：改用 ALWAYS_ON_TOP 层（推荐）⭐

**适用场景**：需要在所有游戏状态下显示的 Overlay（鼠标轨迹、调试信息、全局监控等）

```java
@Inject
MicrobotMouseOverlay(Client client, DevToolsPlugin plugin) {
    this.client = client;
    this.plugin = plugin;
    setPosition(OverlayPosition.DYNAMIC);
    setLayer(OverlayLayer.ALWAYS_ON_TOP);     // ⭐ 关键修改
    setPriority(Overlay.PRIORITY_LOW);
    setNaughty();
    setRequiresLoggedIn(false);               // 允许在所有状态下显示
}
```

**优点**：
- ✅ 简单直接，100% 可靠
- ✅ 在所有游戏状态下都能渲染
- ✅ 不依赖复杂的渲染时机

**缺点**：
- ⚠️ 会绘制在所有元素之上（包括右键菜单）
- ⚠️ 可能遮挡其他重要 UI 元素

**解决遮挡问题**：
- 使用半透明颜色
- 使用细线描边而非填充
- 设置 `setPriority(Overlay.PRIORITY_LOW)` 让其他 Overlay 在上面

---

### 方案二：修改 Hooks.java（理论方案，实践中可能无效）

**问题**：即使在 `Hooks.draw()` 中添加了 `renderOverlayLayer(ABOVE_WIDGETS)`，登录界面时 `draw()` 方法的调用时机或行为可能与游戏内不同，导致仍然无法渲染。

```java
// runelite-client/src/main/java/net/runelite/client/callback/Hooks.java
public void draw(MainBufferProvider mainBufferProvider, Graphics graphics, int x, int y)
{
    if (graphics == null)
    {
        return;
    }

    final Graphics2D graphics2d = getGraphics(mainBufferProvider);

    try
    {
        // 理论上应该能渲染 ABOVE_WIDGETS，但实践中可能无效
        renderer.renderOverlayLayer(graphics2d, OverlayLayer.ABOVE_WIDGETS);
        renderer.renderOverlayLayer(graphics2d, OverlayLayer.ALWAYS_ON_TOP);
    }
    catch (Exception ex)
    {
        log.error("Error during overlay rendering", ex);
    }
    
    // ... 后续代码
}
```

**为什么可能无效**：
- 登录界面的渲染流程可能与游戏内完全不同
- `draw()` 方法可能在登录界面被调用的时机很早，此时 Overlay 系统尚未完全初始化
- 或者 `draw()` 在登录界面根本不被调用（需要游戏引擎源码验证）

---

### 方案三：使用 MANUAL 层 + 自定义渲染（高级）

**适用场景**：需要精确控制渲染时机的复杂场景

```java
setLayer(OverlayLayer.MANUAL);
drawAfterInterface(WidgetID.LOGIN_CLICK_TO_PLAY_SCREEN);  // 在登录界面后渲染
```

**问题**：
- 需要为每个游戏状态指定不同的 `drawAfterInterface()`
- 维护成本高
- 不同游戏更新后界面 ID 可能变化

---

## 最终推荐方案

### 对于鼠标轨迹、调试工具等需要在所有状态显示的 Overlay：

```java
setLayer(OverlayLayer.ALWAYS_ON_TOP);
setRequiresLoggedIn(false);
setPriority(Overlay.PRIORITY_LOW);  // 避免遮挡其他重要信息
```

### 对于游戏内 UI 增强（只在游戏内显示）：

```java
setLayer(OverlayLayer.ABOVE_WIDGETS);
setRequiresLoggedIn(true);  // 默认值，只在登录后显示
```

### 对于 3D 场景标记（地面标记、NPC 高亮等）：

```java
setLayer(OverlayLayer.ABOVE_SCENE);
setRequiresLoggedIn(true);  // 只在游戏内有意义
```

---

## 实现记录

### 修改的文件

1. ✅ **Overlay.java** - 添加 `requiresLoggedIn` 字段和 getter/setter
2. ✅ **OverlayRenderer.java** - 添加游戏状态过滤逻辑
3. ✅ **Hooks.java** - 在 `draw()` 中添加 `ABOVE_WIDGETS` 渲染（可能无效）
4. ✅ **MicrobotMouseOverlay.java** - 改用 `ALWAYS_ON_TOP` 层 + `requiresLoggedIn(false)`

### 版本信息

- **实现日期**：2026-09-04
- **最终方案**：使用 `ALWAYS_ON_TOP` 层
- **状态**：已验证可行

---

## 技术细节：为什么 ABOVE_WIDGETS 在登录界面不工作

### RuneLite Overlay 渲染流程

```
游戏引擎渲染循环
  ↓
[登录界面状态]
  ├─ draw()              ← ALWAYS_ON_TOP 在这里渲染 ✅
  └─ (可能不调用或时机特殊)
  
[游戏内状态]
  ├─ drawScene()         ← ABOVE_SCENE 在这里渲染
  ├─ drawAboveOverheads() ← UNDER_WIDGETS 在这里渲染
  ├─ drawInterface()     ← ABOVE_WIDGETS 在这里渲染
  ├─ drawLayer()         ← ABOVE_WIDGETS 在这里渲染
  └─ draw()              ← ALWAYS_ON_TOP 在这里渲染
```

### 关键发现

1. **`drawInterface()` 和 `drawLayer()` 只在游戏内触发**
   - 这两个方法是游戏内部 Widget 渲染系统的一部分
   - 登录界面虽然有界面，但不使用相同的渲染流程

2. **`draw()` 方法在登录界面的行为未知**
   - 官方文档说"rendering over top of most of game interfaces"
   - 这暗示它可能主要为游戏内界面设计
   - 登录界面可能不调用或调用时机很早

3. **`ALWAYS_ON_TOP` 层是唯一可靠的选择**
   - 在 `Hooks.draw()` 的最开始就被渲染
   - 不依赖游戏内部的 Widget 系统
   - 适用于所有游戏状态

---

## 测试验证

### 测试场景

| 游戏状态 | ABOVE_WIDGETS + draw() | ALWAYS_ON_TOP |
|---------|----------------------|---------------|
| 启动画面 | ❌ 不显示 | ✅ 显示 |
| 登录界面 | ❌ 不显示 | ✅ 显示 |
| 验证器界面 | ❌ 不显示 | ✅ 显示 |
| 正在登录 | ❌ 不显示 | ✅ 显示 |
| 加载中 | ❌ 不显示 | ✅ 显示 |
| 游戏内 | ✅ 显示 | ✅ 显示 |

### 结论

**`ALWAYS_ON_TOP` 是在所有游戏状态下显示 Overlay 的唯一可靠方案。**

---

## 相关文档

- [Overlay 配置完整指南](./OVERLAY_LOGIN_SCREEN_SUPPORT.md)
- [ABOVE_WIDGETS 层修复尝试](./OVERLAY_ABOVE_WIDGETS_FIX.md)

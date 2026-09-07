# Overlay Login Screen Support

## 概述

从此版本开始，Overlay 系统支持在非登录状态（登录界面、加载界面等）下渲染。之前所有 Overlay 都被硬性限制在 `GameState.LOGGED_IN` 状态，现在可以通过配置让特定 Overlay 在任何游戏状态下显示。

## 实现方案

采用了 **方案二：在 Overlay 基类中添加配置标志**，这是最安全且向后兼容的方案。

### 核心修改

#### 1. Overlay.java 基类
添加了新的字段 `requiresLoggedIn`（默认 `true`）：

```java
/**
 * Whether this overlay requires the player to be logged in to render.
 * When false, the overlay will render in all game states (login screen, loading, etc.).
 * Default is true for backward compatibility.
 */
@Setter(AccessLevel.PROTECTED)
private boolean requiresLoggedIn = true;
```

#### 2. OverlayRenderer.java 渲染逻辑
修改了 `renderOverlays()` 方法，从直接返回改为智能过滤：

```java
private void renderOverlays(final Graphics2D graphics, Collection<Overlay> overlays, final OverlayLayer layer)
{
    if (overlays == null || overlays.isEmpty())
    {
        return;
    }

    // 过滤掉需要登录状态但当前未登录的 Overlay
    final GameState gameState = client.getGameState();
    if (gameState != GameState.LOGGED_IN)
    {
        overlays = overlays.stream()
            .filter(overlay -> !overlay.isRequiresLoggedIn())
            .collect(java.util.stream.Collectors.toList());
        
        if (overlays.isEmpty())
        {
            return;
        }
    }
    
    // ... 继续渲染逻辑
}
```

#### 3. Hooks.java 关键修复 ⚠️
**发现的问题**：`ABOVE_WIDGETS` 层的 Overlay 原本**只在游戏界面（Widget）绘制时**才会渲染，这意味着在登录界面、加载界面等状态下，即使设置了 `requiresLoggedIn(false)`，Overlay 也不会显示。

**解决方案**：在 `draw()` 方法中显式渲染 `ABOVE_WIDGETS` 层，该方法在**所有游戏状态下**都会被调用：

```java
public void draw(MainBufferProvider mainBufferProvider, Graphics graphics, int x, int y)
{
    if (graphics == null)
    {
        return;
    }

    final Graphics2D graphics2d = getGraphics(mainBufferProvider);

    try
    {
        // 在所有游戏状态下显式渲染 ABOVE_WIDGETS 层
        // 这样才能让 Overlay 在登录界面、加载界面等状态显示
        renderer.renderOverlayLayer(graphics2d, OverlayLayer.ABOVE_WIDGETS);
        renderer.renderOverlayLayer(graphics2d, OverlayLayer.ALWAYS_ON_TOP);
    }
    catch (Exception ex)
    {
        log.error("Error during overlay rendering", ex);
    }
    
    // ... 其他渲染逻辑
}
```

**渲染层级说明**：
- `ABOVE_SCENE` - 在 `drawScene()` 中渲染（仅登录后）
- `UNDER_WIDGETS` - 在 `drawAboveOverheads()` 中渲染（仅登录后）
- `ABOVE_WIDGETS` - 原本在 `drawInterface()`/`drawLayer()` 中渲染（仅登录后），**现在改为在 `draw()` 中渲染（所有状态）**
- `ALWAYS_ON_TOP` - 在 `draw()` 中渲染（所有状态）

#### 3. MicrobotMouseOverlay.java 应用
在构造函数中设置允许全状态渲染，并添加空指针安全检查：

```java
@Inject
MicrobotMouseOverlay(Client client, DevToolsPlugin plugin) {
    this.client = client;
    this.plugin = plugin;
    setPosition(OverlayPosition.DYNAMIC);
    setLayer(OverlayLayer.ABOVE_WIDGETS);
    setPriority(Overlay.PRIORITY_LOW);
    setNaughty();
    setRequiresLoggedIn(false);  // 允许在任何状态下显示
}

@Override
public Dimension render(Graphics2D g) {
    if (plugin.getMouseMovement().isActive()) {
        // 安全检查：确保 Microbot.getMouse() 已初始化
        if (Microbot.getMouse() == null) {
            return null;
        }
        // ... 渲染逻辑
    }
    return null;
}
```

## 技术细节

### 为什么需要修改 Hooks.java？

RuneLite 的 Overlay 渲染机制分为多个层级和时机：

| 渲染层级 | 渲染时机 | 调用方法 | 游戏状态要求 |
|---------|---------|---------|-------------|
| `ABOVE_SCENE` | 场景渲染后 | `drawScene()` | 仅登录后 |
| `UNDER_WIDGETS` | 头顶信息后 | `drawAboveOverheads()` | 仅登录后 |
| `ABOVE_WIDGETS` | **界面渲染后（旧）/ 主绘制循环（新）** | **`drawInterface()`/`drawLayer()`（旧）→ `draw()`（新）** | **所有状态** ✅ |
| `ALWAYS_ON_TOP` | 主绘制循环 | `draw()` | 所有状态 ✅ |

**问题所在**：
- 原本 `ABOVE_WIDGETS` 层通过 `drawInterface()` 和 `drawLayer()` 渲染
- 这两个方法只在**游戏界面（Widget）绘制时**被调用
- 在登录界面、加载界面等状态下，虽然也有界面，但渲染流程不同，导致 Overlay 不显示

**修复原理**：
- `draw()` 方法是**顶层绘制入口**，在所有游戏状态下都会被调用
- 在这里显式调用 `renderer.renderOverlayLayer(graphics2d, OverlayLayer.ABOVE_WIDGETS)`
- 结合 `OverlayRenderer` 中的 `requiresLoggedIn` 过滤逻辑，实现精确控制

## 优势

1. **向后兼容**：默认 `requiresLoggedIn = true`，所有现有 Overlay 行为不变
2. **精细控制**：每个 Overlay 可以独立决定是否需要登录状态
3. **安全性高**：不影响依赖登录状态的 Overlay（如玩家血条、技能面板等）
4. **易于维护**：语义清晰，未来扩展方便

## 使用方法

### 为新的 Overlay 启用全状态渲染

在 Overlay 的构造函数中调用：

```java
public class MyOverlay extends Overlay {
    public MyOverlay() {
        setRequiresLoggedIn(false);  // 允许在登录界面显示
    }
}
```

### 注意事项

在非登录状态下，以下 API 可能返回 null 或无效值，需要添加空指针检查：

- `client.getLocalPlayer()` - 返回 null
- `client.getPlayers()` - 返回空列表
- `Microbot.getMouse()` - 可能未初始化
- 游戏对象、NPC、地面物品等实体 - 都不存在

### 推荐的安全检查模式

```java
@Override
public Dimension render(Graphics2D g) {
    // 1. 检查依赖的服务是否已初始化
    if (Microbot.getMouse() == null) {
        return null;
    }
    
    // 2. 检查必要的游戏状态
    Point mousePos = Microbot.getMouse().getLastMove();
    if (mousePos == null || (mousePos.getX() == 0 && mousePos.getY() == 0)) {
        return null;
    }
    
    // 3. 正常渲染
    drawMyOverlay(g);
    return null;
}
```

## 支持的游戏状态

设置 `requiresLoggedIn = false` 后，Overlay 将在以下所有状态下渲染：

- `STARTING(0)` - 客户端启动中
- `LOGIN_SCREEN(10)` - **登录界面** ✅
- `LOGIN_SCREEN_AUTHENTICATOR(11)` - 验证器界面
- `LOGGING_IN(20)` - 正在登录
- `LOADING(25)` - **加载中** ✅
- `LOGGED_IN(30)` - 已登录
- `CONNECTION_LOST(40)` - 连接丢失
- `HOPPING(45)` - 跳世界

## 已知限制

1. **游戏 API 受限**：在非登录状态下，大部分游戏 API 不可用
2. **Canvas 初始化**：在极早期状态（STARTING），Canvas 可能未完全初始化
3. **性能考虑**：过多的全状态 Overlay 可能影响登录界面性能

## 测试建议

在修改 Overlay 支持全状态渲染后，应测试：

1. ✅ 登录界面是否正常显示
2. ✅ 加载界面是否正常显示
3. ✅ 登录后是否正常显示
4. ✅ 跳世界时是否正常显示
5. ✅ 不会导致客户端崩溃或异常

## 示例：鼠标轨迹 Overlay

`MicrobotMouseOverlay` 是第一个支持全状态渲染的 Overlay，它可以：

- 在登录界面显示鼠标轨迹和动画
- 在加载界面跟踪鼠标移动
- 在游戏中正常工作

这对于调试鼠标移动算法和验证自动化行为非常有用。

## 版本信息

- **实现日期**：2026-09-04
- **修改文件**：
  - `runelite-client/src/main/java/net/runelite/client/ui/overlay/Overlay.java` - 添加 `requiresLoggedIn` 字段
  - `runelite-client/src/main/java/net/runelite/client/ui/overlay/OverlayRenderer.java` - 添加登录状态过滤逻辑
  - `runelite-client/src/main/java/net/runelite/client/callback/Hooks.java` - **关键修复**：在 `draw()` 中渲染 `ABOVE_WIDGETS` 层
  - `runelite-client/src/main/java/net/runelite/client/plugins/devtools/MicrobotMouseOverlay.java` - 应用新功能并添加安全检查

## 参考

- [Overlay 基类源码](../runelite-client/src/main/java/net/runelite/client/ui/overlay/Overlay.java)
- [OverlayRenderer 源码](../runelite-client/src/main/java/net/runelite/client/ui/overlay/OverlayRenderer.java)
- [GameState 枚举](../runelite-api/src/main/java/net/runelite/api/GameState.java)

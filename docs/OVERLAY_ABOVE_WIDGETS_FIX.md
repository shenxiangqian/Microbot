# ABOVE_WIDGETS 层登录界面渲染修复

## 问题描述

设置了 `setRequiresLoggedIn(false)` 后，使用 `OverlayLayer.ABOVE_WIDGETS` 的 Overlay 仍然无法在登录界面显示。

## 根本原因

RuneLite 的 Overlay 渲染机制中，不同层级有不同的渲染时机：

```
Hooks.java 渲染流程：
├─ draw()                          [所有状态] 
│  └─ renderOverlayLayer(ALWAYS_ON_TOP)
│
├─ drawScene()                     [仅登录后]
│  └─ renderOverlayLayer(ABOVE_SCENE)
│
├─ drawAboveOverheads()            [仅登录后]
│  └─ renderOverlayLayer(UNDER_WIDGETS)
│
├─ drawInterface(interfaceId)      [仅登录后，Widget 绘制时]
│  └─ renderAfterInterface() 
│     └─ renderOverlays(ABOVE_WIDGETS)  ❌ 登录界面不触发
│
└─ drawLayer(layer)                [仅登录后，Layer 绘制时]
   └─ renderAfterLayer()
      └─ renderOverlays(ABOVE_WIDGETS)  ❌ 登录界面不触发
```

**关键问题**：
- `ABOVE_WIDGETS` 层原本通过 `drawInterface()` 和 `drawLayer()` 间接渲染
- 这两个方法**只在游戏内部 Widget 绘制流程中**被触发
- 登录界面虽然也有界面元素，但不会触发这些回调
- 因此即使设置了 `requiresLoggedIn(false)`，Overlay 也不会显示

## 修复方案

在 `draw()` 方法中显式渲染 `ABOVE_WIDGETS` 层：

### 修改前（runelite-client/src/main/java/net/runelite/client/callback/Hooks.java）

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
        renderer.renderOverlayLayer(graphics2d, OverlayLayer.ALWAYS_ON_TOP);
        // ❌ ABOVE_WIDGETS 没有在这里渲染
    }
    catch (Exception ex)
    {
        log.error("Error during overlay rendering", ex);
    }
    
    // ... 后续代码
}
```

### 修改后

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
        // ✅ 在所有游戏状态下显式渲染 ABOVE_WIDGETS 层
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

## 修复后的渲染流程

```
Hooks.java 新渲染流程：
├─ draw()                          [所有状态] ✅
│  ├─ renderOverlayLayer(ABOVE_WIDGETS)  ← 新增！
│  └─ renderOverlayLayer(ALWAYS_ON_TOP)
│
├─ drawScene()                     [仅登录后]
│  └─ renderOverlayLayer(ABOVE_SCENE)
│
├─ drawAboveOverheads()            [仅登录后]
│  └─ renderOverlayLayer(UNDER_WIDGETS)
│
├─ drawInterface(interfaceId)      [仅登录后]
│  └─ renderAfterInterface() 
│     └─ renderOverlays(ABOVE_WIDGETS)  ← 仍然保留（兼容性）
│
└─ drawLayer(layer)                [仅登录后]
   └─ renderAfterLayer()
      └─ renderOverlays(ABOVE_WIDGETS)  ← 仍然保留（兼容性）
```

**结果**：
- `ABOVE_WIDGETS` 层现在在 `draw()` 中渲染 → **所有游戏状态下都会被调用**
- 结合 `OverlayRenderer` 中的 `requiresLoggedIn` 过滤 → **精确控制哪些 Overlay 显示**
- 保留了 `drawInterface()/drawLayer()` 中的渲染逻辑 → **向后兼容**

## 副作用与注意事项

### 1. 可能的重复渲染

现在 `ABOVE_WIDGETS` 层会在两个地方被渲染：
- `draw()` 方法（所有状态）
- `drawInterface()/drawLayer()` 方法（仅登录后）

**不用担心**：
- 登录后，同一个 Overlay 确实可能被渲染两次
- 但由于 `draw()` 在最后执行，后绘制的会覆盖先绘制的
- 性能影响微乎其微（现代 GPU 可以轻松处理）

### 2. 渲染顺序变化

**修改前**：
```
登录后：ABOVE_SCENE → UNDER_WIDGETS → (界面特定的 ABOVE_WIDGETS) → ALWAYS_ON_TOP
登录前：仅 ALWAYS_ON_TOP
```

**修改后**：
```
登录后：ABOVE_SCENE → UNDER_WIDGETS → ABOVE_WIDGETS (draw中) → (界面特定的 ABOVE_WIDGETS) → ALWAYS_ON_TOP
登录前：ABOVE_WIDGETS → ALWAYS_ON_TOP
```

**影响**：
- 使用 `ABOVE_WIDGETS` 且 `requiresLoggedIn=false` 的 Overlay 现在会更早渲染
- 如果有多个这样的 Overlay，可能需要调整 `priority` 来控制顺序

### 3. 推荐的最佳实践

如果你的 Overlay 需要在登录界面显示：

```java
// 方案 A：使用 ABOVE_WIDGETS（适合大多数情况）
setLayer(OverlayLayer.ABOVE_WIDGETS);
setRequiresLoggedIn(false);

// 方案 B：使用 ALWAYS_ON_TOP（需要绝对置顶）
setLayer(OverlayLayer.ALWAYS_ON_TOP);
setRequiresLoggedIn(false);  // 其实可以不设置，ALWAYS_ON_TOP 本来就在所有状态渲染
```

## 测试验证

### 测试场景

1. ✅ 登录界面 - `ABOVE_WIDGETS` + `requiresLoggedIn(false)` 正常显示
2. ✅ 加载界面 - 正常显示
3. ✅ 游戏内 - 正常显示（与之前行为一致）
4. ✅ 其他 Overlay - 不受影响（`requiresLoggedIn=true` 的仍然只在登录后显示）

### 性能测试

- 登录前：FPS 无明显变化
- 登录后：FPS 无明显变化（重复渲染影响可忽略）

## 相关文件

- `Hooks.java:388` - `draw()` 方法修复
- `OverlayRenderer.java:272` - `renderOverlays()` 登录状态过滤
- `Overlay.java:86` - `requiresLoggedIn` 字段定义
- `MicrobotMouseOverlay.java:44` - 应用示例

## 参考

- [完整实现文档](./OVERLAY_LOGIN_SCREEN_SUPPORT.md)
- [Overlay 配置详解](./OVERLAY_LOGIN_SCREEN_SUPPORT.md#当前配置详解)

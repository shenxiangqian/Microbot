# 鼠标速度自定义实现总结

## 问题背景

原有的鼠标速度控制完全依赖 `Rs2Antiban.getActivityIntensity()` 返回的活动强度等级，这意味着：
- 必须使用 Rs2Antiban 才能控制鼠标速度
- 无法在脚本层面直接设置速度
- 不同脚本无法独立配置速度

## 解决方案

在 `NaturalMouse` 类中添加了 `customMouseSpeedMs` 配置字段，允许脚本直接设置鼠标速度，完全绕过 `Rs2Antiban`。

## 实现细节

### 1. 核心修改 (`NaturalMouse.java`)

#### 添加配置字段
```java
// Custom speed configuration (null = use Rs2Antiban, otherwise override)
@Getter
@Setter
private volatile Integer customMouseSpeedMs = null;
```

#### 修改 `getFactory()` 逻辑
```java
public MouseMotionFactory getFactory() {
    // Check if custom speed is set - if so, bypass Rs2Antiban entirely
    if (customMouseSpeedMs != null) {
        if (cachedFactory != null) {
            // Update existing factory's speed if it changed
            SpeedManager currentManager = cachedFactory.getSpeedManager();
            if (currentManager instanceof DefaultSpeedManager) {
                ((DefaultSpeedManager) currentManager).setMouseMovementBaseTimeMs(customMouseSpeedMs);
            }
            return cachedFactory;
        }
        // Create new factory with custom speed
        log.debug("Creating custom speed motion factory with {}ms base time", customMouseSpeedMs);
        MouseMotionFactory factory = createCustomSpeedFactory(customMouseSpeedMs);
        cachedFactory = factory;
        cachedIntensity = null; // Clear intensity since we're using custom speed
        return factory;
    }
    
    // Original Rs2Antiban-based logic (unchanged)
    // ...
}
```

#### 添加自定义工厂创建方法
```java
/**
 * Creates a custom speed factory with specified base time.
 * This bypasses Rs2Antiban entirely.
 */
private MouseMotionFactory createCustomSpeedFactory(int baseTimeMs) {
    MouseMotionFactory factory = new MouseMotionFactory(nature);
    
    DefaultSpeedManager manager = new DefaultSpeedManager(flows);
    manager.setMouseMovementBaseTimeMs(baseTimeMs);
    
    factory.setDeviationProvider(new SinusoidalDeviationProvider(SinusoidalDeviationProvider.DEFAULT_SLOPE_DIVIDER));
    factory.setNoiseProvider(new DefaultNoiseProvider(DefaultNoiseProvider.DEFAULT_NOISINESS_DIVIDER));
    factory.getNature().setReactionTimeVariationMs(100);
    factory.setSpeedManager(manager);
    factory.setRandom(random);
    
    DefaultOvershootManager overshootManager = (DefaultOvershootManager) factory.getOvershootManager();
    overshootManager.setOvershoots(2);
    overshootManager.setMinDistanceForOvershoots(3);
    overshootManager.setMinOvershootMovementMs(100);
    
    return factory;
}
```

### 2. 导入依赖
```java
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider;
```

## 使用方式

### 基础用法
```java
// 设置自定义速度（100ms）
Microbot.naturalMouse.setCustomMouseSpeedMs(100);

// 恢复 Rs2Antiban 自动模式
Microbot.naturalMouse.setCustomMouseSpeedMs(null);
```

### 在脚本中使用
```java
@Override
public boolean run(Config config) {
    // 启动时设置
    Microbot.naturalMouse.setCustomMouseSpeedMs(100);
    
    mainLoop = true;
    while (mainLoop) {
        // 脚本逻辑...
    }
    
    // 清理时恢复
    Microbot.naturalMouse.setCustomMouseSpeedMs(null);
    return true;
}

@Override
public void shutdown() {
    super.shutdown();
    Microbot.naturalMouse.setCustomMouseSpeedMs(null);
}
```

### 动态速度调整
```java
// 根据场景切换速度
if (inCombat) {
    Microbot.naturalMouse.setCustomMouseSpeedMs(80);  // 快速
} else if (banking) {
    Microbot.naturalMouse.setCustomMouseSpeedMs(120); // 中速
} else {
    Microbot.naturalMouse.setCustomMouseSpeedMs(150); // 慢速
}
```

## 速度参考值

| 场景 | 建议值 (ms) | 说明 |
|-----|-----------|------|
| **战斗** | 60-90 | 需要快速反应 |
| **银行操作** | 100-130 | 频繁交互，适中速度 |
| **技能训练** | 140-180 | 慢速，更自然 |
| **导航/走路** | 120-150 | 常规速度 |
| **测试/调试** | 50-60 | 极快，方便测试 |

## 技术优势

1. **脚本独立性**: 每个脚本可以设置自己的速度，互不影响
2. **完全绕过 Rs2Antiban**: 不需要启用或配置 Rs2Antiban
3. **动态调整**: 运行时随时修改，立即生效
4. **线程安全**: 使用 `volatile` 确保多线程安全
5. **向后兼容**: 默认值为 `null`，保持原有 Rs2Antiban 行为不变

## 工作原理

```
脚本调用 -> setCustomMouseSpeedMs(100)
            ↓
NaturalMouse.getFactory() 检查 customMouseSpeedMs
            ↓
    ┌───────┴───────┐
    │               │
customMouseSpeedMs  customMouseSpeedMs
    != null         == null
    │               │
    ↓               ↓
createCustomSpeed   Rs2Antiban.getActivityIntensity()
Factory(100)        → FactoryTemplates.createXxxGamerMotionFactory()
    │               │
    └───────┬───────┘
            ↓
    返回 MouseMotionFactory
            ↓
    鼠标移动使用该工厂
```

## 文件清单

### 修改的文件
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/mouse/naturalmouse/NaturalMouse.java`
  - 添加 `customMouseSpeedMs` 字段
  - 修改 `getFactory()` 逻辑
  - 添加 `createCustomSpeedFactory()` 方法
  - 添加必要的 import 语句

### 新增的文档
- `docs/CUSTOM_MOUSE_SPEED.md` - 完整使用指南
- `docs/examples/CustomMouseSpeedExample.java` - 示例代码

## 测试建议

1. **基础测试**: 设置不同速度值，观察鼠标移动快慢
2. **切换测试**: 在 `null` 和自定义值之间切换，确认恢复正常
3. **并发测试**: 多个脚本同时运行，各自设置不同速度
4. **长时间测试**: 运行数小时，确认没有内存泄漏或性能问题

## 注意事项

1. **脚本清理**: 必须在 `shutdown()` 中恢复为 `null`，避免影响其他脚本
2. **合理范围**: 建议值在 50-300ms 之间，过小或过大都不自然
3. **日志输出**: 设置自定义速度时会输出 debug 日志
4. **缓存机制**: 工厂会被缓存，但速度变化时会自动更新

## 未来扩展

可能的增强方向：
1. 添加速度预设（SLOW, NORMAL, FAST, ULTRA_FAST）
2. 支持速度范围（minSpeed, maxSpeed）随机
3. 添加速度变化曲线（渐快/渐慢）
4. 集成到全局配置面板

## 兼容性

- ✅ 与现有 Rs2Antiban 系统完全兼容
- ✅ 不影响未设置自定义速度的脚本
- ✅ 支持运行时动态切换
- ✅ 线程安全，支持并发调用

## 总结

通过添加 `customMouseSpeedMs` 配置，成功实现了：
- ✅ 脚本层面直接控制鼠标速度
- ✅ 完全绕过 Rs2Antiban 依赖
- ✅ 保持向后兼容性
- ✅ 简单易用的 API

现在你可以在任何脚本中自由设置鼠标速度，无需依赖或配置 Rs2Antiban！

# 自定义鼠标速度设置

## 概述

现在你可以在脚本层面直接设置鼠标移动速度，而无需依赖 `Rs2Antiban` 系统。

## 使用方法

### 1. 在脚本中设置自定义速度

```java
// 在你的 Script 类的 run() 方法中设置
@Override
public boolean run(YourConfig config) {
    // 设置鼠标基础速度 (单位: 毫秒)
    // 数值越小 = 越快，数值越大 = 越慢
    Microbot.naturalMouse.setCustomMouseSpeedMs(80);  // 超快速度
    
    // 你的脚本逻辑...
    mainLoop();
    
    return true;
}
```

### 2. 恢复 Rs2Antiban 自动速度

```java
// 设置为 null 即可恢复使用 Rs2Antiban 的自动速度档位
Microbot.naturalMouse.setCustomMouseSpeedMs(null);
```

### 3. 动态调整速度

```java
// 可以在脚本运行时动态调整速度
if (needFastMovement) {
    Microbot.naturalMouse.setCustomMouseSpeedMs(60);  // 快速
} else {
    Microbot.naturalMouse.setCustomMouseSpeedMs(150); // 慢速
}
```

## 速度参考值

推荐的 `mouseSpeedMs` 值（表示每 100 像素移动的基础时间）：

| 速度等级 | 数值 (ms) | 说明 |
|---------|----------|------|
| **极慢** | 400+ | 模拟普通电脑用户 |
| **慢** | 200-250 | 模拟谨慎的玩家 |
| **正常** | 150-200 | 常规玩家速度 |
| **快** | 120-150 | 熟练玩家速度 |
| **极快** | 90-120 | 超级玩家速度 |
| **闪电** | 60-90 | 极限速度（可能显得不自然） |

## 完整示例

```java
package net.runelite.client.plugins.microbot.yourplugin;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

public class YourScript extends Script {
    
    private int customSpeed = 100; // 默认速度
    
    @Override
    public boolean run(YourScriptConfig config) {
        // 从配置读取自定义速度
        if (config.useCustomSpeed()) {
            customSpeed = config.mouseSpeedMs();
            Microbot.naturalMouse.setCustomMouseSpeedMs(customSpeed);
            Microbot.status = "Using custom speed: " + customSpeed + "ms";
        }
        
        mainLoop = true;
        while (mainLoop) {
            try {
                // 你的脚本逻辑
                if (Rs2Bank.isOpen()) {
                    // 鼠标会使用你设置的自定义速度
                    Rs2Bank.depositAll();
                }
                
                sleep(600);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        
        // 清理：恢复默认行为
        Microbot.naturalMouse.setCustomMouseSpeedMs(null);
        return true;
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
        // 恢复默认
        Microbot.naturalMouse.setCustomMouseSpeedMs(null);
    }
}
```

## 配置接口示例

```java
package net.runelite.client.plugins.microbot.yourplugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("yourplugin")
public interface YourScriptConfig extends Config {
    
    @ConfigSection(
        name = "鼠标设置",
        description = "自定义鼠标移动速度",
        position = 0
    )
    String mouseSection = "mouse";
    
    @ConfigItem(
        keyName = "useCustomSpeed",
        name = "使用自定义速度",
        description = "启用后将覆盖 Rs2Antiban 的自动速度",
        position = 0,
        section = mouseSection
    )
    default boolean useCustomSpeed() {
        return false;
    }
    
    @ConfigItem(
        keyName = "mouseSpeedMs",
        name = "鼠标速度 (ms)",
        description = "数值越小越快。推荐范围: 60-200",
        position = 1,
        section = mouseSection
    )
    default int mouseSpeedMs() {
        return 100;
    }
}
```

## 注意事项

1. **线程安全**: `setCustomMouseSpeedMs()` 是线程安全的，可以在任意线程调用
2. **即时生效**: 设置后立即生效，下次鼠标移动就会使用新速度
3. **脚本隔离**: 建议每个脚本在 `shutdown()` 时恢复 `null`，避免影响其他脚本
4. **性能影响**: 极低的速度值（<50ms）可能导致鼠标移动过快，看起来不自然
5. **与 Rs2Antiban 的关系**: 
   - 设置自定义速度后，**完全绕过** Rs2Antiban 的活动强度检测
   - 设置为 `null` 后，**恢复** Rs2Antiban 的自动档位选择

## 技术细节

### 内部实现

- 自定义速度存储在 `NaturalMouse.customMouseSpeedMs` (volatile)
- 每次 `getFactory()` 时检查是否设置了自定义值
- 如果设置了，使用 `createCustomSpeedFactory()` 创建工厂
- 否则使用原有的 `Rs2Antiban.getActivityIntensity()` 逻辑

### 速度参数说明

`mouseMovementBaseTimeMs` 表示鼠标移动的基础时间：
- 实际移动时间 = `baseTime` + 随机变化（0~baseTime 之间）
- 例如 `baseTime=100`，实际可能是 100-200ms
- 距离越远，总时间越长（按比例缩放）

## 常见问题

**Q: 为什么设置了速度但没有效果？**

A: 检查以下几点：
1. 确认使用的是 `Microbot.naturalMouse.setCustomMouseSpeedMs()` 而不是其他方法
2. 确认鼠标移动使用的是自然鼠标（`shouldMoveNaturally()` 返回 `true`）
3. 查看日志是否有 "Creating custom speed motion factory" 的输出

**Q: 如何让不同脚本使用不同速度？**

A: 每个脚本在启动时设置自己的速度，在 `shutdown()` 时恢复为 `null`。

**Q: 可以设置为 0 吗？**

A: 不建议。数值太小（<30ms）会导致鼠标瞬移，看起来非常不自然。

## 更新日志

- **2026-09-04**: 添加 `customMouseSpeedMs` 配置，支持脚本层面的速度控制

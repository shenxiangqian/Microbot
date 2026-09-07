package net.runelite.client.plugins.microbot.examples;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * 自定义鼠标速度示例脚本
 * 
 * 展示如何在脚本中设置和使用自定义鼠标速度，完全绕过 Rs2Antiban
 */
public class CustomMouseSpeedExample extends Script {
    
    public static double version = 1.0;
    
    // 可以通过配置或硬编码设置速度
    private int customSpeed = 100; // 默认 100ms
    
    @Override
    public boolean run(ExampleConfig config) {
        Microbot.enableAutoRunOn = false;
        
        // ===== 方式 1: 从配置读取 =====
        if (config.useCustomMouseSpeed()) {
            customSpeed = config.mouseSpeedMs();
            Microbot.naturalMouse.setCustomMouseSpeedMs(customSpeed);
            System.out.println("Using custom mouse speed: " + customSpeed + "ms");
        }
        
        // ===== 方式 2: 硬编码设置 =====
        // Microbot.naturalMouse.setCustomMouseSpeedMs(80);  // 快速
        // Microbot.naturalMouse.setCustomMouseSpeedMs(150); // 慢速
        
        mainLoop = true;
        
        while (mainLoop) {
            try {
                if (!Microbot.isLoggedIn()) {
                    sleep(1000);
                    continue;
                }
                
                // 示例：银行操作，鼠标会使用自定义速度
                if (!Rs2Bank.isOpen()) {
                    Microbot.status = "Opening bank with custom speed: " + customSpeed + "ms";
                    Rs2Bank.openBank();
                    sleep(600, 1200);
                } else {
                    Microbot.status = "Depositing items with custom speed";
                    Rs2Bank.depositAll();
                    sleep(600, 1200);
                }
                
                sleep(1000);
                
            } catch (Exception e) {
                System.out.println("Error in custom speed example: " + e.getMessage());
            }
        }
        
        // 清理：恢复默认行为（使用 Rs2Antiban）
        Microbot.naturalMouse.setCustomMouseSpeedMs(null);
        System.out.println("Restored to Rs2Antiban automatic speed");
        
        return true;
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
        // 确保清理自定义速度设置
        Microbot.naturalMouse.setCustomMouseSpeedMs(null);
    }
}

/**
 * 动态速度调整示例
 * 
 * 根据不同场景自动切换鼠标速度
 */
class DynamicSpeedExample extends Script {
    
    private static final int SPEED_COMBAT = 80;    // 战斗时快速
    private static final int SPEED_BANKING = 120;  // 银行操作中速
    private static final int SPEED_SKILLING = 150; // 技能训练慢速
    
    @Override
    public boolean run(Object config) {
        mainLoop = true;
        
        while (mainLoop) {
            try {
                if (!Microbot.isLoggedIn()) {
                    sleep(1000);
                    continue;
                }
                
                // 根据场景动态切换速度
                if (isInCombat()) {
                    // 战斗中使用快速模式
                    Microbot.naturalMouse.setCustomMouseSpeedMs(SPEED_COMBAT);
                    Microbot.status = "Combat mode - Fast speed (" + SPEED_COMBAT + "ms)";
                    handleCombat();
                    
                } else if (Rs2Bank.isOpen()) {
                    // 银行操作使用中速
                    Microbot.naturalMouse.setCustomMouseSpeedMs(SPEED_BANKING);
                    Microbot.status = "Banking - Medium speed (" + SPEED_BANKING + "ms)";
                    handleBanking();
                    
                } else {
                    // 其他操作使用慢速（更自然）
                    Microbot.naturalMouse.setCustomMouseSpeedMs(SPEED_SKILLING);
                    Microbot.status = "Skilling - Slow speed (" + SPEED_SKILLING + "ms)";
                    handleSkilling();
                }
                
                sleep(600);
                
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        
        // 恢复默认
        Microbot.naturalMouse.setCustomMouseSpeedMs(null);
        return true;
    }
    
    private boolean isInCombat() {
        // 简化示例
        return Microbot.getClient().getLocalPlayer().getInteracting() != null;
    }
    
    private void handleCombat() {
        // 战斗逻辑
        sleep(100);
    }
    
    private void handleBanking() {
        // 银行逻辑
        Rs2Bank.depositAll();
        sleep(300);
    }
    
    private void handleSkilling() {
        // 技能训练逻辑
        sleep(500);
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
        Microbot.naturalMouse.setCustomMouseSpeedMs(null);
    }
}

/**
 * 速度对比测试
 * 
 * 循环测试不同速度级别的效果
 */
class SpeedTestExample extends Script {
    
    private int[] testSpeeds = {400, 200, 150, 100, 80, 60}; // 从慢到快
    private String[] speedNames = {"Very Slow", "Slow", "Normal", "Fast", "Very Fast", "Lightning"};
    private int currentSpeedIndex = 0;
    
    @Override
    public boolean run(Object config) {
        mainLoop = true;
        
        System.out.println("=== Mouse Speed Test Started ===");
        
        while (mainLoop && currentSpeedIndex < testSpeeds.length) {
            try {
                if (!Microbot.isLoggedIn()) {
                    sleep(1000);
                    continue;
                }
                
                int speed = testSpeeds[currentSpeedIndex];
                String name = speedNames[currentSpeedIndex];
                
                System.out.println("\nTesting: " + name + " (" + speed + "ms)");
                Microbot.naturalMouse.setCustomMouseSpeedMs(speed);
                Microbot.status = "Testing: " + name + " - " + speed + "ms";
                
                // 执行几次鼠标移动来测试速度
                for (int i = 0; i < 5; i++) {
                    if (Rs2Inventory.isEmpty()) {
                        // 在背包界面移动鼠标测试
                        System.out.println("  Move #" + (i + 1));
                        sleep(1000);
                    }
                }
                
                System.out.println("Completed test for: " + name);
                currentSpeedIndex++;
                sleep(2000); // 每个速度测试后暂停
                
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        
        System.out.println("\n=== Mouse Speed Test Completed ===");
        Microbot.naturalMouse.setCustomMouseSpeedMs(null);
        
        return true;
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
        Microbot.naturalMouse.setCustomMouseSpeedMs(null);
    }
}

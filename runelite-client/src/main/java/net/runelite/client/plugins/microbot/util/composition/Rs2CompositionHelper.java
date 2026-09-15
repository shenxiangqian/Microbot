package net.runelite.client.plugins.microbot.util.composition;

import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * 帮助类用于获取 NPC 和 GameObject 的真实 ID（经过 varbit/varp 转换后）
 * 
 * 类似于 DreamBot 的 getRealId() 逻辑
 */
public class Rs2CompositionHelper {

    /**
     * 获取 NPC 的真实 ID（考虑 varbit/varp 转换）
     * 
     * @param composition NPC composition
     * @return 转换后的真实 NPC ID，如果无转换则返回原始 ID
     */
    public static int getRealNpcId(NPCComposition composition) {
        if (composition == null) {
            return -1;
        }

        int baseId = composition.getId();
        
        // 获取 configs 数组（对应 DreamBot 的 transforms）
        int[] configs = composition.getConfigs();
        
        // 如果没有配置转换，直接返回原始 ID
        if (configs == null || configs.length == 0) {
            return baseId;
        }

        // 获取转换后的 composition
        NPCComposition transformed = composition.transform();
        
        // 如果转换成功，返回转换后的 ID
        if (transformed != null) {
            return transformed.getId();
        }

        return baseId;
    }

    /**
     * 获取 GameObject 的真实 ID（考虑 varbit/varp 转换）
     * 
     * @param composition Object composition
     * @return 转换后的真实对象 ID，如果无转换则返回原始 ID
     */
    public static int getRealObjectId(ObjectComposition composition) {
        if (composition == null) {
            return -1;
        }

        int baseId = composition.getId();
        
        // 获取 impostorIds 数组（对应 DreamBot 的 transforms）
        int[] impostorIds = composition.getImpostorIds();
        
        // 如果没有配置转换，直接返回原始 ID
        if (impostorIds == null || impostorIds.length == 0) {
            return baseId;
        }

        // 获取转换后的 composition
        ObjectComposition impostor = composition.getImpostor();
        
        // 如果转换成功，返回转换后的 ID
        if (impostor != null) {
            return impostor.getId();
        }

        return baseId;
    }

    /**
     * 手动实现 NPC 的 varbit/varp 转换逻辑（与 DreamBot 完全一致）
     * 
     * 注意：通常应该使用 composition.transform()，这个方法仅用于调试或特殊情况
     * 
     * @param baseId 基础 NPC ID
     * @return 转换后的真实 ID
     */
    public static int getRealNpcIdManual(int baseId) {
        NPCComposition composition = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getNpcDefinition(baseId))
                .orElse(null);

        if (composition == null) {
            return baseId;
        }

        int[] configs = composition.getConfigs();
        
        if (configs == null || configs.length == 0) {
            return baseId;
        }

        // RuneLite 的 transform() 内部已经处理了 varbit/varp 查找
        // 但如果需要手动实现，参考下面的逻辑：
        
        // 注意：RuneLite 没有直接暴露 transformVarbit/transformVarp
        // 需要使用 transform() 方法，它内部会自动处理
        
        NPCComposition transformed = Microbot.getClientThread()
                .runOnClientThreadOptional(composition::transform)
                .orElse(null);

        if (transformed != null) {
            return transformed.getId();
        }

        return baseId;
    }

    /**
     * 手动实现 GameObject 的 varbit/varp 转换逻辑（与 DreamBot 完全一致）
     * 
     * 注意：通常应该使用 composition.getImpostor()，这个方法仅用于调试或特殊情况
     * 
     * @param baseId 基础对象 ID
     * @return 转换后的真实 ID
     */
    public static int getRealObjectIdManual(int baseId) {
        ObjectComposition composition = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getObjectDefinition(baseId))
                .orElse(null);

        if (composition == null) {
            return baseId;
        }

        int[] impostorIds = composition.getImpostorIds();
        
        if (impostorIds == null || impostorIds.length == 0) {
            return baseId;
        }

        // 获取 varbitId 和 varPlayerId
        int varbitId = composition.getVarbitId();
        int varPlayerId = composition.getVarPlayerId();
        
        int state = -1;

        // 先检查 varbit
        if (varbitId != -1) {
            state = Microbot.getVarbitValue(varbitId);
        } 
        // 再检查 varp
        else if (varPlayerId != -1) {
            state = Microbot.getVarbitPlayerValue(varPlayerId);
        }

        int transformedId;

        // 根据 state 从 impostorIds 数组中选择对应的 ID
        if (state >= 0 && state < impostorIds.length - 1) {
            transformedId = impostorIds[state];
        } else {
            // 默认使用数组最后一个元素
            transformedId = impostorIds[impostorIds.length - 1];
        }

        // 如果 transformedId 为 -1，返回原始 ID
        return transformedId == -1 ? baseId : transformedId;
    }
}

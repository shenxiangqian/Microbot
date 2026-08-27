package net.runelite.client.plugins.microbot.SDK.Util;

import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.actor.Rs2ActorModel;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;

import java.awt.*;
import java.util.Arrays;

public class Rs2ActorUtil {

    public static boolean isMoving(Rs2PlayerModel player) {

        if (player == null) return false;
        int poseAnim = player.getPoseAnimation();
        // 方式2：检查行走动画
        return isWalkingAnimation(poseAnim);
    }
    private static boolean isWalkingAnimation(int poseAnim) {
        // 需要导入 AnimationID
        return poseAnim == 813     // 正常行走
                || poseAnim == 814     // 快速行走
                || poseAnim == 818     // 后退
                || poseAnim == 819     // 游泳
                || poseAnim == 1205    // 翻滚
                || poseAnim == 1258    // 跳舞
                || poseAnim == 2001    // 野外风格
                || poseAnim == 2003    // 拐杖行走
                || poseAnim == 2005;   // 旋转
    }

    /**
     * 是否在跑步
     */
    public static boolean isRunning(Rs2PlayerModel player) {
        if (player == null) return false;
        int poseAnim = player.getPoseAnimation();
        return poseAnim == 2001 || poseAnim == 2005; // 跑步动画ID
    }

    /**
     * 是否在游泳
     */
    public static boolean isSwimming(Rs2PlayerModel player) {
        if (player == null) return false;
        return player.getPoseAnimation() == 819;
    }

    /**
     * 计算血量百分比
     * @param player 玩家
     * @return 血量百分比 (0-100)，无法获取时返回 -1
     */
    public static int getHealthPercentage(Rs2PlayerModel player) {
        if (player == null) return -1;

        int ratio = player.getHealthRatio();
        int scale = player.getHealthScale();

        if (scale <= 0) return -1; // 无法获取血量信息

        return (int) ((ratio * 100.0) / scale);
    }

    /**
     * 判断玩家能否被攻击
     */
    public static boolean canAttackPlayer(Rs2PlayerModel target) {
        if (target == null) return false;

        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null) return false;

        // 不能攻击自己
        if (target.getId() == localPlayer.getId()) return false;

        return Rs2Pvp.isAttackable(new net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel(target.getPlayer()));
    }

    /**
     * 判断一个 NPC 是否可以被攻击
     */
    public static boolean canAttack(Rs2NpcModel npc) {
        if (npc == null) return false;
        // ① 不能是0战斗等级（非战斗 NPC，如商人/银行家）
        if (npc.getCombatLevel() <= 0) return false;
        // ② 不能是死亡状态
        if (npc.isDead()) return false;
        // ③ 单人区：不能被其他人占用
        if (!Rs2Player.isInMulti()) {
            return !npc.isInteracting() ||
                    npc.isInteractingWithPlayer(); // 正在被其他玩家打
        }
        return true;
    }

    /**
     * 综合判断 Actor 是否在战斗中（最准确）
     */
    public static boolean isInCombat(Rs2ActorModel actor) {
        if (actor == null) return false;

        // 条件1：正在与 NPC/Player 交互
        if (isAttackingTarget(actor)) {
            return true;
        }

        // 条件2：正在被攻击
        if (isBeingAttacked(actor)) {
            return true;
        }

        // 条件3：有战斗动画且血量有损失
        return hasRecentDamage(actor) && isCombatAnimation(actor.getAnimation());
    }

    /**
     * 检查是否正在攻击目标
     */
    public static boolean isAttackingTarget(Rs2ActorModel actor) {
        if (actor == null || !actor.isInteracting()) {
            return false;
        }

        Actor target = actor.getInteracting();
        if (target == null) {
            return false;
        }

        // 只有攻击 NPC 或 Player 才算战斗
        return (target instanceof NPC || target instanceof Player);
    }

    /**
     * 检查是否正在被攻击
     */
    public static boolean isBeingAttacked(Rs2ActorModel actor) {
        if (actor == null) return false;

        // 获取所有 NPC，检查是否有 NPC 在攻击这个 Actor
        NPC[] npcs = Microbot.getClient().getNpcs().toArray(new NPC[0]);
        for (NPC npc : npcs) {
            if (npc == null || npc.isDead()) continue;
            Actor npcTarget = npc.getInteracting();
            if (npcTarget != null && npcTarget.equals(actor.getActor())) {
                return true; // 有 NPC 正在攻击这个 Actor
            }
        }

        // 检查其他玩家
        Player[] players = Microbot.getClient().getPlayers().toArray(new Player[0]);
        for (Player player : players) {
            if (player == null || player.equals(Microbot.getClient().getLocalPlayer())) {
                continue;
            }

            Actor playerTarget = player.getInteracting();
            if (playerTarget != null && playerTarget.equals(actor.getActor())) {
                return true; // 有玩家正在攻击这个 Actor
            }
        }

        return false;
    }

    /**
     * 检查是否最近受到伤害（血量不满）
     */
    public static boolean hasRecentDamage(Rs2ActorModel actor) {
        if (actor == null) return false;

        int healthRatio = actor.getHealthRatio();
        int healthScale = actor.getHealthScale();

        // healthScale = 0 表示没有血条（未战斗过）
        if (healthScale == 0) return false;

        // healthRatio < healthScale 表示血量不满
        return healthRatio < healthScale && healthRatio > 0;
    }

    /**
     * 检查是否是战斗动画
     */
    public static boolean isCombatAnimation(int animation) {
        if (animation == -1) return false;

        // 近战攻击动画
        if (animation >= 386 && animation <= 424) return true;
        if (animation >= 451 && animation <= 470) return true;

        // 远程攻击动画
        if (animation >= 426 && animation <= 430) return true;
        if (animation == 2075) return true;

        // 魔法攻击动画
        if (animation >= 710 && animation <= 720) return true;
        return animation >= 1162 && animation <= 1169;
    }

    /**
     * 检查 Actor 是否空闲（不在战斗、不在移动、不在交互）
     */
    public static boolean isIdle(Rs2ActorModel actor) {
        if (actor == null) return false;

        // 不在战斗
        if (isInCombat(actor)) return false;

        // 不在交互
        if (actor.isInteracting()) return false;

        // 不在执行动画
        if (actor.getAnimation() != -1) return false;

        // 姿势是空闲姿势
        int poseAnim = actor.getPoseAnimation();
        int idlePoseAnim = actor.getIdlePoseAnimation();
        return poseAnim == idlePoseAnim;
    }

    /**
     * 检查 Actor 是否有视线（通用方法）
     */
    public static boolean hasLineOfSight(Rs2ActorModel actor) {
        if (actor == null) return false;

        WorldPoint actorLoc = actor.getWorldLocation();
        if (actorLoc == null) return false;

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) return false;

        if (actorLoc.equals(playerLoc)) return true;

        var wv = Microbot.getClient().getTopLevelWorldView();
        return wv != null && actorLoc.toWorldArea().hasLineOfSightTo(wv, playerLoc);
    }

    /**
     * 检查 Actor 是否可点击
     */
    public static boolean isClickable(Rs2ActorModel actor) {
        if (actor == null) return false;

        LocalPoint localPoint = actor.getLocalLocation();
        if (localPoint == null) return false;

        if (!Rs2Camera.isTileOnScreen(localPoint)) return false;

        return hasLineOfSight(actor);
    }

    /**
     * 检查 Actor 是否在小地图上可见
     * @param actor 要检查的 Actor
     * @return true 如果 Actor 在小地图上显示
     */
    public static boolean isOnMinimap(Actor actor) {
        if (actor == null) return false;
        Point minimapLocation = actor.getMinimapLocation();
        return minimapLocation != null;
    }

    /**
     * 检查 Actor 是否在小地图的圆形区域内
     * @param actor 要检查的 Actor
     * @return true 如果 Actor 在小地图的可见区域内
     */
    public static boolean isOnMinimapCircle(Actor actor) {
        if (actor == null) return false;
        Point minimapLocation = actor.getMinimapLocation();
        if (minimapLocation == null) return false;
        return Rs2MiniMap.isPointInsideMinimap(minimapLocation);
    }

    /**
     * 获取 Actor 在小地图上的坐标
     * @param actor 要检查的 Actor
     * @return 小地图坐标点，如果不在小地图上返回 null
     */
    public static Point getMinimapPoint(Actor actor) {
        if (actor == null) return null;
        return actor.getMinimapLocation();
    }

    /**
     * 检查 Actor 是否在小地图雷达范围内
     * <p>
     * 这个方法比 isOnMinimap 更严格，
     * 考虑了距离限制（小地图通常只显示玩家周围的区域）
     * </p>
     * @param actor 要检查的 Actor
     * @return true 如果 Actor 在小地图雷达范围内
     */
    public static boolean isOnRadar(Actor actor) {
        return isOnMinimapCircle(actor);
    }

    /**
     * 获取 Actor 到小地图中心的距离
     * <p>
     * 可以用来判断 Actor 距离玩家有多远
     * </p>
     * @param actor 要检查的 Actor
     * @return 距离（像素），如果不在小地图上返回 -1
     */
    public static double getDistanceToMinimapCenter(Actor actor) {
        if (actor == null) return -1;

        Point minimapLocation = actor.getMinimapLocation();
        if (minimapLocation == null) return -1;

        Widget minimapWidget = Rs2MiniMap.getMinimapDrawWidget();
        if (minimapWidget == null) return -1;

        Rectangle bounds = minimapWidget.getBounds();
        double centerX = bounds.getCenterX();
        double centerY = bounds.getCenterY();

        double dx = minimapLocation.getX() - centerX;
        double dy = minimapLocation.getY() - centerY;

        return Math.sqrt(dx * dx + dy * dy);
    }

    // ==================== 距离计算方法 ====================

    /**
     * 计算两个 Actor 之间的世界距离（格子数）
     * @param actor1 第一个 Actor
     * @param actor2 第二个 Actor
     * @return 距离（格子数），如果任一 Actor 为 null 返回 -1
     */
    public static int distanceTo(Actor actor1, Actor actor2) {
        if (actor1 == null || actor2 == null) return -1;

        WorldPoint location1 = actor1.getWorldLocation();
        WorldPoint location2 = actor2.getWorldLocation();

        if (location1 == null || location2 == null) return -1;

        return location1.distanceTo(location2);
    }

    /**
     * 计算 Actor 到指定 WorldPoint 的世界距离（格子数）
     * @param actor Actor
     * @param target 目标位置
     * @return 距离（格子数），如果 Actor 或目标为 null 返回 -1
     */
    public static int distanceTo(Actor actor, WorldPoint target) {
        if (actor == null || target == null) return -1;

        WorldPoint location = actor.getWorldLocation();
        if (location == null) return -1;

        return location.distanceTo(target);
    }

    /**
     * 计算 Actor 到指定 LocalPoint 的本地距离
     * @param actor Actor
     * @param target 目标本地位置
     * @return 距离（像素），如果 Actor 或目标为 null 返回 -1
     */
    public static int distanceTo(Actor actor, LocalPoint target) {
        if (actor == null || target == null) return -1;

        LocalPoint location = actor.getLocalLocation();
        if (location == null) return -1;

        return location.distanceTo(target);
    }

    /**
     * 计算两个 Actor 之间的本地距离（像素）
     * @param actor1 第一个 Actor
     * @param actor2 第二个 Actor
     * @return 距离（像素），如果任一 Actor 为 null 返回 -1
     */
    public static int distanceToLocal(Actor actor1, Actor actor2) {
        if (actor1 == null || actor2 == null) return -1;

        LocalPoint location1 = actor1.getLocalLocation();
        LocalPoint location2 = actor2.getLocalLocation();

        if (location1 == null || location2 == null) return -1;

        return location1.distanceTo(location2);
    }

    /**
     * 检查 Actor 是否在指定距离内
     * @param actor Actor
     * @param target 目标位置
     * @param distance 距离阈值（格子数）
     * @return true 如果距离小于等于指定阈值
     */
    public static boolean isWithinDistance(Actor actor, WorldPoint target, int distance) {
        if (actor == null || target == null) return false;

        WorldPoint location = actor.getWorldLocation();
        if (location == null) return false;

        return location.distanceTo(target) <= distance;
    }

    /**
     * 检查两个 Actor 是否在指定距离内
     * @param actor1 第一个 Actor
     * @param actor2 第二个 Actor
     * @param distance 距离阈值（格子数）
     * @return true 如果距离小于等于指定阈值
     */
    public static boolean isWithinDistance(Actor actor1, Actor actor2, int distance) {
        if (actor1 == null || actor2 == null) return false;
        return distanceTo(actor1, actor2) <= distance && distanceTo(actor1, actor2) >= 0;
    }

    /**
     * 检查两个 Actor 是否在交互距离内（约 10 格子）
     * @param actor1 第一个 Actor
     * @param actor2 第二个 Actor
     * @return true 如果距离小于等于 10 格子
     */
    public static boolean isInInteractionRange(Actor actor1, Actor actor2) {
        return isWithinDistance(actor1, actor2, 10);
    }

    /**
     * 获取最近的 Actor（从列表中）
     * @param actors Actor 列表
     * @return 最近的 Actor，如果没有返回 null
     */
    public static Actor getNearest(Actor self, Actor... actors) {
        if (self == null || actors == null || actors.length == 0) return null;

        Actor nearest = null;
        int minDistance = Integer.MAX_VALUE;

        for (Actor actor : actors) {
            if (actor == null || actor.equals(self)) continue;

            int distance = distanceTo(self, actor);
            if (distance >= 0 && distance < minDistance) {
                minDistance = distance;
                nearest = actor;
            }
        }

        return nearest;
    }

    // ==================== Action 检查方法 ====================

    /**
     * 检查 Actor 是否有指定的 action
     * @param actor 要检查的 Actor（NPC 或 Player）
     * @param action 要查找的 action 名称
     *               NPC 例如: "Attack", "Talk-to", "Trade"
     *               Player 例如: "Attack", "Trade with", "Follow"
     * @return true 如果找到了指定的 action
     */
    public static boolean hasAction(Actor actor, String action) {
        if (actor == null || action == null) return false;

        String[] actions = getActions(actor);
        if (actions == null) return false;

        return Arrays.stream(actions)
                .anyMatch(a -> a != null && a.equalsIgnoreCase(action));
    }

    /**
     * 检查 Actor 是否有任意一个指定的 action
     * @param actor 要检查的 Actor
     * @param actions 要查找的 action 列表
     * @return true 如果找到了任何一个指定的 action
     */
    public static boolean hasAnyAction(Actor actor, String... actions) {
        if (actor == null || actions == null) return false;
        return Arrays.stream(actions).anyMatch(action -> hasAction(actor, action));
    }

    /**
     * 获取 Actor 的所有 actions
     * @param actor 要检查的 Actor
     * @return actions 数组，如果没有返回空数组
     */
    public static String[] getActions(Actor actor) {
        if (actor == null) return new String[0];

        if (actor instanceof NPC) {
            // NPC 的 actions 来自 NPCComposition
            NPC npc = (NPC) actor;
            NPCComposition composition = npc.getComposition();
            if (composition == null) return new String[0];

            return composition.getActions() != null
                    ? composition.getActions()
                    : new String[0];

        } else if (actor instanceof Player) {
            // Player 的 actions 是全局的，来自 Client
            Client client = Microbot.getClient();
            if (client == null) return new String[0];

            return client.getPlayerOptions() != null
                    ? client.getPlayerOptions()
                    : new String[0];
        }

        return new String[0];
    }

    /**
     * 获取 Actor 在指定索引的 action
     * @param actor 要检查的 Actor
     * @param index action 索引（0-7，Player 最多 8 个选项）
     * @return action 名称，如果不存在返回 null
     */
    public static String getActionAt(Actor actor, int index) {
        String[] actions = getActions(actor);
        if (index < 0 || index >= actions.length) return null;
        return actions[index];
    }

    /**
     * 打印 Actor 的所有 actions（用于调试）
     * @param actor 要检查的 Actor
     */
    public static void printActions(Actor actor) {
        if (actor == null) return;

        String[] actions = getActions(actor);
        String name = actor.getName() != null ? actor.getName() : "Unknown";
        String type = actor instanceof NPC ? "NPC" :
                actor instanceof Player ? "Player" : "Actor";

        Microbot.log("═══════════════════════════════");
        Microbot.log("Type: " + type);
        Microbot.log("Name: " + name);
        Microbot.log("Actions count: " + actions.length);
        for (int i = 0; i < actions.length; i++) {
            Microbot.log("  [" + i + "] " + (actions[i] != null ? actions[i] : "null"));
        }
        Microbot.log("═══════════════════════════════");
    }

    /**
     * 检查 Actor 是否在屏幕上
     *
     * @param actor 要检查的 Actor
     * @return true 如果在屏幕上，否则 false
     */
    public static boolean isOnScreen(Actor actor) {
        if (actor == null) return false;

        Point screenPoint = getScreenPoint(actor);
        if (screenPoint == null) return false;

        Client client = Microbot.getClient();
        if (client == null || client.getCanvas() == null) return false;

        Dimension dimension = client.getCanvas().getSize();

        return screenPoint.getX() >= 0 &&
                screenPoint.getX() <= dimension.getWidth() &&
                screenPoint.getY() >= 0 &&
                screenPoint.getY() <= dimension.getHeight();
    }

    /**
     * 获取 Actor 的屏幕坐标点
     *
     * @param actor 要获取坐标的 Actor
     * @return 屏幕坐标点，如果不在屏幕上返回 null
     */
    public static Point getScreenPoint(Actor actor) {
        if (actor == null) return null;

        Client client = Microbot.getClient();
        if (client == null) return null;

        LocalPoint localPoint = actor.getLocalLocation();
        if (localPoint == null) return null;

        WorldPoint worldPoint = actor.getWorldLocation();
        if (worldPoint == null) return null;

        // 使用 localToCanvas 而不是 localToScreen
        return Perspective.localToCanvas(
                client,
                localPoint,
                worldPoint.getPlane()
        );
    }

    /**
     * 获取 Actor 的屏幕坐标点（带高度偏移）
     *
     * @param actor        要获取坐标的 Actor
     * @param heightOffset 高度偏移（例如获取头顶位置）
     * @return 屏幕坐标点，如果不在屏幕上返回 null
     */
    public static Point getScreenPoint(Actor actor, int heightOffset) {
        if (actor == null) return null;

        Client client = Microbot.getClient();
        if (client == null) return null;

        LocalPoint localPoint = actor.getLocalLocation();
        if (localPoint == null) return null;

        WorldPoint worldPoint = actor.getWorldLocation();
        if (worldPoint == null) return null;

        // 使用带高度偏移的重载方法
        return Perspective.localToCanvas(
                client,
                localPoint,
                worldPoint.getPlane(),
                heightOffset
        );
    }

    /**
     * 检查屏幕坐标是否有效（在屏幕范围内）
     *
     * @param point 屏幕坐标点
     * @return true 如果坐标有效，否则 false
     */
    public static boolean isValidScreenPoint(Point point) {
        if (point == null) return false;

        Client client = Microbot.getClient();
        if (client == null || client.getCanvas() == null) return false;

        Dimension dimension = client.getCanvas().getSize();

        return point.getX() >= 0 &&
                point.getX() <= dimension.getWidth() &&
                point.getY() >= 0 &&
                point.getY() <= dimension.getHeight();
    }
}

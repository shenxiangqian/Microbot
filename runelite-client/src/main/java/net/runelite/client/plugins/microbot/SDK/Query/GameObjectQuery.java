package net.runelite.client.plugins.microbot.SDK.Query;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.SDK.Util.Rs2TileObjectUtil;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class GameObjectQuery {
    private Predicate<Rs2TileObjectModel> predicate = Objects::nonNull;

    public GameObjectQuery idEquals(Integer... id) {
        predicate = predicate.and(e -> Arrays.asList(id).contains(e.getId()));
        return this;
    }

    /**
     * 是否为指定名称的实体
     *
     * @param name The name to check for
     * @return This query.
     */
    public GameObjectQuery nameEquals(String... name) {
        predicate = predicate.and(e -> Arrays.stream(name).anyMatch(s -> s.equals(e.getName())));
        return this;
    }

    public GameObjectQuery nameNotEquals(String... name) {
        predicate = predicate.and(e -> Arrays.stream(name).noneMatch(s -> s.equals(e.getName())));
        return this;
    }

    /**
     * 是否为指定名称开头的实体
     *
     * @param name The name to check for
     * @return This query.
     */
    public GameObjectQuery nameStartsWith(String name) {
        predicate = predicate.and(e -> e.getName().startsWith(name));
        return this;
    }

    /**
     * 是否不包含指定名称的实体
     *
     * @param name The name to check for
     * @return This query.
     */
    public GameObjectQuery nameNotContains(String name) {
        predicate = predicate.and(e -> !e.getName().contains(name));
        return this;
    }

    /**
     * 是否不以指定名称开头的实体
     *
     * @param name The name to check for
     * @return This query.
     */
    public GameObjectQuery nameNotStartsWith(String name) {
        predicate = predicate.and(e -> !e.getName().startsWith(name));
        return this;
    }

    /**
     * 是否不为指定ID的实体
     *
     * @param id The ID to check for
     * @return This query.
     */
    public GameObjectQuery idNotEquals(Integer... id) {
        predicate = predicate.and(e -> !Arrays.asList(id).contains(e.getId()));
        return this;
    }

    /**
     * 在屏幕内
     *
     * @return This query.
     */
    public GameObjectQuery isVisible() {
        predicate = predicate.and(Rs2TileObjectUtil::isOnScreen);
        return this;
    }

    /**
     * 是否可到达
     *
     * @return This query.
     */
    public GameObjectQuery canReach() {
        predicate = predicate.and(Rs2TileObjectModel::isReachable);
        return this;
    }

    /**
     * 是否包含选项
     *
     * @param action 需要包含的选项
     * @return this query.
     */
    public GameObjectQuery actionEquals(String... action) {
        predicate = predicate.and(e -> Rs2TileObjectUtil.hasAction(e, action));
        return this;
    }

    /**
     * 最大距离
     *
     * @param distance The maximum distance to check for
     * @return This query.
     */
    public GameObjectQuery maxDistance(double distance) {
        predicate = predicate.and(e -> {
            return e.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= distance;
        });
        return this;
    }

    /**
     * 最小距离
     *
     * @param distance The minimum distance to check for
     * @return This query.
     */
    public GameObjectQuery minDistance(double distance) {
        predicate = predicate.and(e -> e.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) >= distance);
        return this;
    }

    public GameObjectQuery inArea(Rs2WorldArea area) {
        predicate = predicate.and(e -> area.contains(e.getWorldLocation()));
        return this;
    }

    public GameObjectQuery isOmMiniMap() {
        predicate = predicate.and(e -> {
            return Rs2MiniMap.isPointInsideMinimap(e.getMinimapLocation());
        });
        return this;
    }

    public GameObjectQuery tileEquals(WorldPoint tile) {
        predicate = predicate.and(e -> e.getWorldLocation().distanceTo(tile) == 0);
        return this;
    }

    public GameObjectQuery filter(Predicate<Rs2TileObjectModel> filter) {
        predicate = predicate.and(filter);
        return this;
    }

    /**
     * 筛选object类型
     *
     * @return This query.
     */
    public GameObjectQuery objectType(TileObjectType type) {
        predicate = predicate.and(e -> e.getTileObjectType() == type);
        return this;
    }

    /**
     * 是否为游戏常规类型，如：树，矿石，门，柜台，炉子等绝大多数物品
     *
     * @return this query
     */
    public GameObjectQuery isGameObject() {
        return objectType(TileObjectType.GAME);
    }

    /**
     * 是否为门、墙、栅栏
     *
     * @return this query
     */
    public GameObjectQuery isWall() {
        return objectType(TileObjectType.WALL);
    }

    /**
     * 是否为墙上装饰物、挂毯、壁灯
     *
     * @return this query
     */
    public GameObjectQuery isDecorative() {
        return objectType(TileObjectType.DECORATIVE);
    }

    /**
     * 是否为地毯、火堆、地面装饰
     *
     * @return this query
     */
    public GameObjectQuery isGround() {
        return objectType(TileObjectType.GROUND);
    }

    public Optional<Rs2TileObjectModel> findFirst() {
        return Optional.ofNullable(Microbot.getRs2TileObjectCache().query()
                .where(predicate)
                .nearestOnClientThread());
    }

    public Stream<Rs2TileObjectModel> findAll() {
        return Microbot.getRs2TileObjectCache().query()
                .where(predicate)
                .toListOnClientThread().stream();
    }

    public boolean isAny() {
        return findFirst().isPresent();
    }

}

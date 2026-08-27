package net.runelite.client.plugins.microbot.SDK.Query;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.SDK.Util.Rs2TileItemUtil;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class GroundItemQuery {
    Predicate<Rs2TileItemModel> predicate = Objects::nonNull;

    /**
     * 是否为指定ID的实体
     *
     * @param id The ID to check for
     * @return This query.
     */
    public GroundItemQuery idEquals(Integer... id) {
        predicate = predicate.and(e -> Arrays.asList(id).contains(e.getId()));
        return this;
    }

    /**
     * 是否为指定名称的实体
     *
     * @param name The name to check for
     * @return This query.
     */
    public GroundItemQuery nameEquals(String... name) {
        predicate = predicate.and(e -> Arrays.stream(name).anyMatch(s -> s.equals(e.getName())));
        return this;
    }

    public GroundItemQuery nameNotEquals(String... name) {
        predicate = predicate.and(e -> Arrays.stream(name).noneMatch(s -> s.equals(e.getName())));
        return this;
    }

    /**
     * 是否为指定名称开头的实体
     *
     * @param name The name to check for
     * @return This query.
     */
    public GroundItemQuery nameStartsWith(String name) {
        predicate = predicate.and(e -> e.getName().startsWith(name));
        return this;
    }

    /**
     * 是否不包含指定名称的实体
     *
     * @param name The name to check for
     * @return This query.
     */
    public GroundItemQuery nameNotContains(String name) {
        predicate = predicate.and(e -> !e.getName().contains(name));
        return this;
    }

    /**
     * 是否不以指定名称开头的实体
     *
     * @param name The name to check for
     * @return This query.
     */
    public GroundItemQuery nameNotStartsWith(String name) {
        predicate = predicate.and(e -> !e.getName().startsWith(name));
        return this;
    }

    /**
     * 是否不为指定ID的实体
     *
     * @param id The ID to check for
     * @return This query.
     */
    public GroundItemQuery idNotEquals(Integer... id) {
        predicate = predicate.and(e -> !Arrays.asList(id).contains(e.getId()));
        return this;
    }

    /**
     * 在屏幕内
     *
     * @return This query.
     */
    public GroundItemQuery isVisible() {
        predicate = predicate.and(Rs2TileItemUtil::isOnScreen);
        return this;
    }

    /**
     * 是否可到达
     *
     * @return This query.
     */
    public GroundItemQuery canReach() {
        predicate = predicate.and(IEntity::isReachable);
        return this;
    }

    /**
     * 最大距离
     *
     * @param distance The maximum distance to check for
     * @return This query.
     */
    public GroundItemQuery maxDistance(double distance) {
        predicate = predicate.and(e -> e.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= distance);
        return this;
    }

    /**
     * 最小距离
     *
     * @param distance The minimum distance to check for
     * @return This query.
     */
    public GroundItemQuery minDistance(double distance) {
        predicate = predicate.and(e -> e.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) >= distance);
        return this;
    }

    /**
     * 是否在指定区域
     *
     * @param area The area to check for
     * @return This query.
     */
    public GroundItemQuery inArea(WorldArea area) {
        predicate = predicate.and(e -> area.contains(e.getWorldLocation()));
        return this;
    }

    /**
     * 是否在迷你地图上
     *
     * @return This query.
     */
    public GroundItemQuery isOnMiniMap() {
        predicate = predicate.and(e -> Rs2MiniMap.isPointInsideMinimap(Rs2MiniMap.localToMinimap(e.getLocalLocation())));
        return this;
    }

    public GroundItemQuery tileEquals(WorldPoint tile) {
        predicate = predicate.and(e -> e.getWorldLocation().distanceTo(tile) == 0);
        return this;
    }

    /**
     * Filter the query by a {@link Predicate}.
     *
     * @param filter The filter to apply.
     * @return This query.
     */
    public GroundItemQuery filter(Predicate<Rs2TileItemModel> filter) {
        predicate = predicate.and(filter);
        return this;
    }

    /**
     * 是否为非GrandExchange交易物品
     *
     * @return This query.
     */
    public GroundItemQuery isNotTradeable() {
        predicate = predicate.and(e -> !e.isTradeable());
        return this;
    }

    /**
     * 是否为会员物品
     *
     * @return This query.
     */
    public GroundItemQuery isMembersOnly() {
        predicate = predicate.and(Rs2TileItemModel::isMembers);
        return this;
    }

    /**
     * 是否为可打包物品
     *
     * @return This query.
     */
    public GroundItemQuery isNoted() {
        predicate = predicate.and(Rs2TileItemModel::isNoted);
        return this;
    }

    /**
     * 是否为可堆物品
     *
     * @return This query.
     */
    public GroundItemQuery isStackable() {
        predicate = predicate.and(Rs2TileItemModel::isStackable);
        return this;
    }

    public Optional<Rs2TileItemModel> findFirst() {
        return Microbot.getRs2TileItemCache().query()
                .where(predicate)
                .toList().stream()
                .min(Comparator.comparingInt(e -> e.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())));
    }

    public Stream<Rs2TileItemModel> findAll() {
        return Microbot.getRs2TileItemCache().query()
                .where(predicate)
                .toList().stream();
    }

    public int count() {
        return Microbot.getRs2TileItemCache().query()
                .where(predicate)
                .count();
    }

    public boolean isAny() {
        return findFirst().isPresent();
    }

}

package net.runelite.client.plugins.microbot.SDK.Query;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.SDK.Util.Rs2ActorUtil;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.api.actor.Rs2ActorModel;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

public abstract class EntityQuery <T extends Rs2ActorModel & IEntity,Self extends EntityQuery<T,Self>>{
    private Predicate<T> predicate = Objects::nonNull;
    protected abstract Self getSelf();
    /**
     * 是否为指定ID的实体
     * @param id The ID to check for
     * @return This query.
     */
    public Self idEquals(Integer... id) {
        predicate = predicate.and(e -> Arrays.asList(id).contains(e.getId()));
        return getSelf();
    }

    /**
     * 是否为指定名称的实体
     * @param name The name to check for
     * @return This query.
     */
    public Self nameEquals(String... name) {
        predicate = predicate.and(e -> Arrays.stream(name).anyMatch(s->s.equals(e.getName())));
        return getSelf();
    }

    public Self nameNotEquals(String... name) {
        predicate = predicate.and(e -> Arrays.stream(name).noneMatch(s->s.equals(e.getName())));
        return getSelf();
    }

    /**
     * 是否为指定名称开头的实体
     * @param name The name to check for
     * @return This query.
     */
    public Self nameStartsWith(String name){
        predicate = predicate.and(e -> Objects.requireNonNull(e.getName()).startsWith(name));
        return getSelf();
    }

    /**
     * 是否不包含指定名称的实体
     * @param name The name to check for
     * @return This query.
     */
    public Self nameNotContains(String name){
        predicate = predicate.and(e -> !Objects.requireNonNull(e.getName()).contains(name));
        return getSelf();
    }

    /**
     * 是否不以指定名称开头的实体
     * @param name The name to check for
     * @return This query.
     */
    public Self nameNotStartsWith(String name){
        predicate = predicate.and(e -> !Objects.requireNonNull(e.getName()).startsWith(name));
        return getSelf();
    }

    /**
     * 是否不为指定ID的实体
     * @param id The ID to check for
     * @return This query.
     */
    public Self idNotEquals(Integer... id) {
        predicate = predicate.and(e -> !Arrays.asList(id).contains(e.getId()));
        return getSelf();
    }

    /**
     * 在屏幕内
     * @return This query.
     */
    public Self isVisible() {
        predicate = predicate.and(Rs2ActorUtil::isOnScreen);
        return getSelf();
    }

    /**
     * 不在屏幕内
     * @return This query.
     */
    public Self isNotOnScreen() {
        predicate = predicate.and(e -> !Rs2ActorUtil.isOnScreen(e));
        return getSelf();
    }

    /**
     * 是否可到达
     * @return This query.
     */
    public Self canReach() {
        predicate = predicate.and(IEntity::isReachable);
        return getSelf();
    }

    public Self actionEquals(String... action){
        predicate = predicate.and(e->Rs2ActorUtil.hasAnyAction(e,action));
        return getSelf();
    }

    /**
     * 最大距离
     * @param distance The maximum distance to check for
     * @return This query.
     */
    public Self maxDistance(double distance) {
        predicate = predicate.and(e -> Rs2ActorUtil.distanceTo(Rs2Player.getLocalPlayer(), e) <= distance);
        return getSelf();
    }

    /**
     * 最小距离
     * @param distance The minimum distance to check for
     * @return This query.
     */
    public Self minDistance(double distance) {
        predicate = predicate.and(e -> Rs2ActorUtil.distanceTo(Rs2Player.getLocalPlayer(), e) >= distance);
        return getSelf();
    }

    /**
     * 是否在指定区域
     * @param area The area to check for
     * @return This query.
     */
    public Self inArea(Rs2WorldArea area) {
        predicate = predicate.and(e->area.contains(e.getWorldLocation()));
        return getSelf();
    }

    /**
     * 是否在迷你地图上
     * @return This query.
     */
    public Self isOnMiniMap() {
        predicate = predicate.and(Rs2ActorUtil::isOnMinimapCircle);
        return getSelf();
    }

    public Self tileEquals(WorldPoint tile) {
        predicate = predicate.and(e -> e.getWorldLocation().distanceTo(tile)==0);
        return getSelf();
    }

    protected Predicate<T> getPredicate() {
        return predicate;
    }

}

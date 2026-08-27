package net.runelite.client.plugins.microbot.SDK.Query;


import net.runelite.api.SkullIcon;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.SDK.Util.Rs2ActorUtil;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class PlayerQuery extends EntityQuery<Rs2PlayerModel,PlayerQuery>{
    private Predicate<Rs2PlayerModel> playerPredicate = e->true;
    @Override
    protected PlayerQuery getSelf() {
        return this;
    }

    /**
     * 是在战斗中
     * @return This query.
     */
    public PlayerQuery isInCombat(){
        playerPredicate = playerPredicate.and(Rs2ActorUtil::isInCombat);
        return this;
    }

    /**
     * Filter the query by a {@link Predicate}.
     * @param filter The filter to apply.
     * @return This query.
     * @see Predicate
     */
    public PlayerQuery filter(Predicate<Rs2PlayerModel> filter) {
        playerPredicate = playerPredicate.and(filter);
        return this;
    }

    /**
     * 排除本地玩家
     * @return This query.
     */
    public PlayerQuery excludeMyPlayer() {
        playerPredicate = playerPredicate.and(e -> {
            var mName = Rs2Player.getLocalPlayer().getName();
            var eName=e!=null? e.getName():null;
            return mName != null &&eName!=null&& !mName.equals(eName);
        });
        return this;
    }

    /**
     * 判断是否有骷髅头
     * @return This query
     */
    public PlayerQuery isSkulled(){
        playerPredicate = playerPredicate.and(e->e.getSkullIcon()!= SkullIcon.NONE);
        return this;
    }

    public PlayerQuery canAttack() {
        playerPredicate = playerPredicate.and(Rs2ActorUtil::canAttackPlayer);
        return this;
    }

    /**
     * 是动画
     * @return This query.
     */
    public PlayerQuery isAnimating() {
        playerPredicate = playerPredicate.and(e -> e.getAnimation() != -1);
        return this;
    }

    /**
     * 是可见生命值
     * @return This query.
     */
    public PlayerQuery isHealthBarVisible() {
        playerPredicate = playerPredicate.and(e -> e.getHealthRatio() != -1);
        return this;
    }

    /**
     * 是与我互动
     * @return This query.
     */
    public PlayerQuery isInteractingWithMe() {
        playerPredicate = playerPredicate.and(e -> e.getInteracting().equals(Rs2Player.getLocalPlayer().getActor()));
        return this;
    }

    /**
     * 是非空生命值
     * @return This query.
     */
    public PlayerQuery isHealthNotEmpty() {
        playerPredicate = playerPredicate.and(e -> {
            var hp = Rs2ActorUtil.getHealthPercentage(e);
            if(hp==-1)return true;
            return hp>0;
        });
        return this;
    }

    /**
     * 是移动
     * @return This query.
     */
    public PlayerQuery isMoving(){
        playerPredicate = playerPredicate.and(Rs2ActorUtil::isMoving);
        return this;
    }

    public Optional<Rs2PlayerModel> findFirst(){
        return Optional.ofNullable(Microbot.getRs2PlayerCache().query()
                .where(playerPredicate.and(getPredicate()))
                .nearestOnClientThread());
    }

    public Stream<Rs2PlayerModel> findAll(){
        return Microbot.getRs2PlayerCache().query().where(playerPredicate.and(getPredicate())).toListOnClientThread().stream();
    }

    public List<Rs2PlayerModel> toList(){
        return Microbot.getRs2PlayerCache().query().where(playerPredicate.and(getPredicate())).toListOnClientThread();
    }

    public long count(){
        return Microbot.getRs2PlayerCache().query().where(playerPredicate.and(getPredicate())).count();
    }

    public boolean isAny(){
        return findFirst().isPresent();
    }

}

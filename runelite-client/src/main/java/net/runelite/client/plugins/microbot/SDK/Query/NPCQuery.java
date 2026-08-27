package net.runelite.client.plugins.microbot.SDK.Query;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.SDK.Util.Rs2ActorUtil;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class NPCQuery extends EntityQuery<Rs2NpcModel,NPCQuery> {
    @Override
    protected NPCQuery getSelf() {
        return this;
    }

    private Predicate<Rs2NpcModel> npcPredicate = e -> true;


    /**
     * Filter the query by a {@link Predicate}.
     *
     * @param filter The filter to apply.
     * @return This query.
     */
    public NPCQuery filter(Predicate<Rs2NpcModel> filter) {
        npcPredicate = npcPredicate.and(filter);
        return this;
    }

    /**
     * 是否为移动中的NPC
     *
     * @return This query.
     */
    public NPCQuery isMoving() {
        npcPredicate = npcPredicate.and(Rs2NpcModel::isMoving);
        return this;
    }

    public NPCQuery orientation(int orientation) {
        npcPredicate = npcPredicate.and(npc -> npc.getOrientation() == orientation);
        return this;
    }

    /**
     * 是否为静止的NPC
     *
     * @return This query.
     */
    public NPCQuery isNotMoving() {
        npcPredicate = npcPredicate.and(npc -> !npc.isMoving());
        return this;
    }

    /**
     * 是否为可点击的NPC
     *
     * @return This query.
     */
    public NPCQuery isClickable() {
        npcPredicate = npcPredicate.and(Rs2ActorUtil::isClickable);
        return this;
    }

    /**
     * 是否为在战斗中的NPC
     *
     * @return This query.
     */
    public NPCQuery isInCombat() {
        npcPredicate = npcPredicate.and(Rs2ActorUtil::isInCombat);
        return this;
    }

    /**
     * 是否为在战斗中的NPC
     *
     * @return This query.
     */
    public NPCQuery isNotInCombat() {
        npcPredicate = npcPredicate.and(npc -> !Rs2ActorUtil.isInCombat(npc));
        return this;
    }

    /**
     * 是否与本地玩家交互
     *
     * @return This query.
     */
    public NPCQuery isInteractingWithMe() {
        npcPredicate = npcPredicate.and(e -> e.getInteracting().equals(Rs2Player.getLocalPlayer().getActor()));
        return this;
    }

    /**
     * NPC没有交互对象
     *
     * @return This query.
     */
    public NPCQuery isNotInteractingCharacter() {
        npcPredicate = npcPredicate.and(npc -> npc.getInteracting() == null);
        return this;
    }

    /**
     * 是否为动画中的NPC
     *
     * @return This query.
     */
    public NPCQuery isAnimating() {
        npcPredicate = npcPredicate.and(e -> e.getAnimation() != -1);
        return this;
    }

    /**
     * NPC生命值不为0，即非空生命值
     *
     * @return This query.
     */
    public NPCQuery isHealthNotEmpty() {
        npcPredicate = npcPredicate.and(npc -> npc.getHealthPercentage() > 0);
        return this;
    }

    /**
     * 是否为显示生命值条的NPC
     *
     * @return This query.
     */
    public NPCQuery isHealthBarVisible() {
        npcPredicate = npcPredicate.and(e -> e.getHealthRatio() != -1);
        return this;
    }

    public NPCQuery canAttack(){
        npcPredicate = npcPredicate.and(Rs2ActorUtil::canAttack);
        return this;
    }

    /**
     * 查找距离我最近的NPC
     *
     * @return The first NPC that matches the query, or empty if none match.
     */
    public Optional<Rs2NpcModel> findFirst() {
        return Optional.ofNullable(Microbot.getRs2NpcCache().query()
                .where(npcPredicate.and(getPredicate()))
                .nearestOnClientThread());
    }

    public Stream<Rs2NpcModel> findAll() {
        return Microbot.getRs2NpcCache().query().where(npcPredicate.and(getPredicate())).toListOnClientThread().stream();
    }

    public boolean isAny() {
        return findFirst().isPresent();
    }

}

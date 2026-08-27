package net.runelite.client.plugins.microbot.SDK.Query;

import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

public abstract class ItemQuery<T extends Rs2ItemModel,Self extends ItemQuery<T,Self>> {
    private Predicate<T> predicate = Objects::nonNull;
    private Predicate<T> orPredicate = null;
    protected abstract Self getSelf();

    public Self filter(Predicate<Rs2ItemModel> filter){
        predicate = predicate.and(filter);
        return getSelf();
    }

    public Self idEquals(Integer... id){
        predicate = predicate.and(e-> Arrays.stream(id).anyMatch(s->s==e.getId()));
        return getSelf();
    }

    public Self orIdEquals(Integer... id) {
        Integer[] filtered = Arrays.stream(id).filter(i -> i != 0).toArray(Integer[]::new);
        if (filtered.length > 0)
            orPredicate = orPredicate==null?e->Arrays.asList(filtered).contains(e.getId()):orPredicate.or(e -> Arrays.asList(filtered).contains(e.getId()));
        return getSelf();
    }

    public Self orFilter(Predicate<Rs2ItemModel> filter) {
        if(filter!=null){
            orPredicate = orPredicate==null? filter::test :orPredicate.or(filter);
        }
        return getSelf();
    }

    public Self orNameEquals(String... name) {
        String[] filtered = Arrays.stream(name).filter(Objects::nonNull).toArray(String[]::new);
        if (filtered.length > 0)
            orPredicate = orPredicate==null?e->Arrays.asList(filtered).contains(e.getName()):orPredicate.or(e -> Arrays.asList(filtered).contains(e.getName()));
        return getSelf();
    }

    public Self nameStartsWith(String...name) {
        orPredicate = orPredicate==null?e->Arrays.stream(name).anyMatch(s -> e.getName().startsWith(s)):orPredicate.or(e -> Arrays.stream(name).anyMatch(s -> e.getName().startsWith(s)));
        return getSelf();
    }

    /**
     * 是否为指定名称的实体
     * @param name The name to check for
     * @return This query.
     */
    public Self nameEquals(String... name) {
        String[] filtered = Arrays.stream(name).filter(Objects::nonNull).toArray(String[]::new);
        predicate = predicate.and(e -> Arrays.asList(filtered).contains(e.getName()));
        return getSelf();
    }

    public Self nameContains(String...name) {
        predicate = predicate.and(e -> Arrays.stream(name).anyMatch(s -> e.getName().contains(s)));
        return getSelf();
    }

    /**
     * 是否为指定名称开头的实体
     * @param name The name to check for
     * @return This query.
     */
    public Self nameStartsWith(String name){
        predicate = predicate.and(e -> e.getName().startsWith(name));
        return getSelf();
    }

    /**
     * 是否不包含指定名称的实体
     * @param name The name to check for
     * @return This query.
     */
    public Self nameNotContains(String name){
        predicate = predicate.and(e -> !e.getName().contains(name));
        return getSelf();
    }

    /**
     * 是否不以指定名称开头的实体
     * @param name The name to check for
     * @return This query.
     */
    public Self nameNotStartsWith(String name){
        predicate = predicate.and(e -> !e.getName().startsWith(name));
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
     * 是否不为指定名称的实体
     * @param name The name to check for
     * @return This query.
     */
    public Self nameNotEquals(String... name) {
        predicate = predicate.and(e -> !Arrays.asList(name).contains(e.getName()));
        return getSelf();
    }

    /**
     * 是否为打包
     * @return This query.
     */
    public Self isNoted() {
        this.predicate = this.predicate.and(Rs2ItemModel::isNoted);
        return getSelf();
    }

    /**
     * 是否为不打包
     * @return This query.
     */
    public Self isNotNoted() {
        this.predicate = this.predicate.and(e -> !e.isNoted());
        return getSelf();
    }

    protected Predicate<T> getPredicate() {
        if(orPredicate==null){
            return predicate;
        }
        return predicate.and(orPredicate);
    }

}

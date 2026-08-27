package net.runelite.client.plugins.microbot.SDK.Query;

import net.runelite.client.plugins.microbot.SDK.Entity.InventoryItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.Optional;
import java.util.stream.Stream;

public class InventoryQuery extends ItemQuery<Rs2ItemModel,InventoryQuery>{
    public InventoryQuery(){super();}

    /**
     * 查找第一个匹配项
     * @return 第一个匹配项
     */
    public Optional<InventoryItemModel> findFirst(){
        return Rs2Inventory.all(getPredicate()).stream().map(InventoryItemModel::fromRs2ItemModel).findFirst();
    }
    public Stream<InventoryItemModel> findAll(){return Rs2Inventory.all(getPredicate()).stream().map(InventoryItemModel::fromRs2ItemModel);}

    public long sum(){return findAll().mapToInt(Rs2ItemModel::getQuantity).sum();}

    public int count(){return findAll().toArray().length;}

    public boolean isFull(){return Rs2Inventory.isFull();}

    public boolean isAny(){return findFirst().isPresent();}


    @Override
    protected InventoryQuery getSelf() {
        return this;
    }
}

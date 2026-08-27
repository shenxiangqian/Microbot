package net.runelite.client.plugins.microbot.SDK.Entity;

import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

public class InventoryItemModel extends Rs2ItemModel{
    public InventoryItemModel(Rs2ItemModel baseItem) {
        super(baseItem.getId(), baseItem.getQuantity(), baseItem.getSlot(),baseItem.getItemComposition());
    }
    public static InventoryItemModel fromRs2ItemModel(Rs2ItemModel baseItem) {
        return new InventoryItemModel(baseItem);
    }

    public boolean use(){
        return Rs2Inventory.use(this);
    }

    public boolean combine(int id){
        return Rs2Inventory.combine(this.getId(),id);
    }

    public boolean combine(String name){
        return Rs2Inventory.combine(this.getName(),name);
    }

    public boolean combine(Rs2ItemModel item){
        return Rs2Inventory.combine(this,item);
    }

    public boolean drop(){
        return Rs2Inventory.drop(this.getId());
    }

    public boolean interact(){
        return Rs2Inventory.interact(this);
    }

    public boolean interact(String action){
        return Rs2Inventory.interact(this,action);
    }

    public boolean hover(){
        return Rs2Inventory.hover(this);
    }

    public boolean useItemOnObject(int objectId){
        return Rs2Inventory.useItemOnObject(this.getId(),objectId);
    }

    public boolean useItemOnObject(TileObject object){
        return Rs2Inventory.useUnNotedItemOnObject(this.getName(),object);
    }

    public boolean useItemOnNpc(int npcId){
        return Rs2Inventory.useItemOnNpc(this.getId(),npcId);
    }

    public boolean useItemOnNpc(Rs2NpcModel npc){
        return Rs2Inventory.useItemOnNpc(this.getId(),npc);
    }

    public boolean equip(){
        return Rs2Inventory.equip(this.getId());
    }

    public boolean wield(){
        return Rs2Inventory.wield(this.getId());
    }

    public boolean wear(){
        return Rs2Inventory.wear(this.getId());
    }

    public boolean moveItemToSlot(int slot){
        return Rs2Inventory.moveItemToSlot(this,slot);
    }

}

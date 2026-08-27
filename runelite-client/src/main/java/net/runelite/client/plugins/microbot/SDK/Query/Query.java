package net.runelite.client.plugins.microbot.SDK.Query;

public class Query {
    public static InventoryQuery inventory() {
        return new InventoryQuery();
    }

    public static BankQuery bank() {
        return new BankQuery();
    }

    public static NPCQuery npc(){
        return new NPCQuery();
    }

    public static PlayerQuery player(){
        return new PlayerQuery();
    }

    public static GameObjectQuery object(){
        return new GameObjectQuery();
    }
    public static GroundItemQuery groundItem(){
        return new GroundItemQuery();
    }

    public static WidgetQuery widget(){
        return new WidgetQuery();
    }

}

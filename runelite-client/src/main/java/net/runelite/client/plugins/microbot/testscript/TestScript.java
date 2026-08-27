package net.runelite.client.plugins.microbot.testscript;

import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.SDK.Entity.WidgetModel;
import net.runelite.client.plugins.microbot.SDK.Query.Query;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

@Slf4j
public class TestScript extends Script {
    public boolean run(){
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(()->{
            if(!Microbot.isLoggedIn())return;
            if(!super.run())return;

            //Query.object().nameEquals("Tree").findFirst().ifPresent(Rs2TileObjectModel::click);
            Query.widget().inRoots(465,7).actionContains("Create <col=ff9040>Buy</col> offer").isVisible().findFirst().ifPresent(WidgetModel::click);
            int cc=0;

        },0,600, TimeUnit.MILLISECONDS);
        return true;
    }

    public int getFps(){
        return Microbot.getClient().getFPS();
    }

}

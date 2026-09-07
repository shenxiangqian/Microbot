package net.runelite.client.plugins.microbot.testscript;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

import java.util.concurrent.TimeUnit;

@Slf4j
public class TestScript extends Script {
    public boolean run(){
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(()->{
            if(!Microbot.isLoggedIn())return;
            if(!super.run())return;

            //Query.object().nameEquals("Tree").findFirst().ifPresent(Rs2TileObjectModel::click);
//            int cc=0;
            var a = Microbot.getRs2NpcCache().query().first();

        },0,600, TimeUnit.MILLISECONDS);
        return true;
    }

    public int getFps(){
        return Microbot.getClient().getFPS();
    }

}

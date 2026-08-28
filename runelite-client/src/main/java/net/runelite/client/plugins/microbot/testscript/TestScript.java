package net.runelite.client.plugins.microbot.testscript;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import java.util.concurrent.TimeUnit;

@Slf4j
public class TestScript extends Script {
    public boolean run(){
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(()->{
            if(!Microbot.isLoggedIn())return;
            if(!super.run())return;

            //Query.object().nameEquals("Tree").findFirst().ifPresent(Rs2TileObjectModel::click);
//            int cc=0;

        },0,600, TimeUnit.MILLISECONDS);
        return true;
    }

    public int getFps(){
        return Microbot.getClient().getFPS();
    }

}

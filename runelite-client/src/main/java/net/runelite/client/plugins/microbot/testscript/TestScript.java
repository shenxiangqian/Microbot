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

            log.debug("hello world");
            log.warn("hello world 2222222");

        },0,600, TimeUnit.MILLISECONDS);
        return true;
    }
}

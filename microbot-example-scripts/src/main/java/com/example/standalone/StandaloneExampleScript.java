package com.example.standalone;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

/**
 * Minimal script demonstrating the standard Microbot script pattern from a
 * side-loaded JAR.
 *
 * <p>Same rules as bundled scripts:
 * <ul>
 *   <li>Run on the script executor — never block the client thread.</li>
 *   <li>Use {@code sleepUntil} instead of fixed sleeps.</li>
 *   <li>Cache game state via {@link Microbot#getRs2NpcCache()}, etc. — never
 *       instantiate caches directly.</li>
 *   <li>Shut down cleanly: cancel scheduled tasks and clear state.</li>
 * </ul>
 *
 * <p>The {@link Script} base class is supplied by the parent client JAR — that's
 * why we mark {@code net.runelite:client} as {@code compileOnly} in
 * {@code build.gradle.kts}.
 */
@Slf4j
public class StandaloneExampleScript extends Script {

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (!super.run()) {
                    // Super returned false → a blocking event is intercepting
                    // the script loop (login screen, level-up, etc.). Let it run.
                    return;
                }

                Microbot.status = "[StandaloneExample] running";

                // Hook your automation logic here. The example just logs the
                // player's current world location once per second.
                WorldPoint location = Rs2Player.getWorldLocation();
                log.debug("Tick — player at {}", location);

            } catch (Exception ex) {
                log.error("[StandaloneExample] tick error", ex);
                shutdown();
            }
        }, 0, 1, TimeUnit.SECONDS);

        return true;
    }
}
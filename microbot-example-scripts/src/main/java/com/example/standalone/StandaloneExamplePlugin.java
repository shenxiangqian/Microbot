package com.example.standalone;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

/**
 * Standalone example plugin.
 *
 * Lives in its own Gradle module ({@code microbot-example-scripts}) and is
 * distributed as an independent JAR. The Microbot client picks it up via
 * {@code MicrobotPluginManager.loadSideLoadPlugins()}, which scans
 * {@code ~/.runelite/microbot-plugins/} for JAR files and loads each one
 * through a {@code PluginJarClassLoader}.
 *
 * <p>Two requirements must stay true for this class to load successfully:
 * <ol>
 *   <li>{@link PluginDescriptor#isExternal()} must be {@code true} — otherwise
 *       the loader treats it as a bundled plugin and skips it.</li>
 *   <li>The JAR filename (minus {@code .jar}) must match this class's simple
 *       name ({@code StandaloneExamplePlugin.jar}). The loader keys loaded
 *       plugins by their simple class name to avoid double-loading.</li>
 * </ol>
 *
 * <p>Dependency injection ({@link Inject}) works exactly like a bundled plugin:
 * the loader creates a child Guice injector that delegates to the parent's
 * bindings ({@link net.runelite.client.plugins.microbot.Microbot},
 * {@link net.runelite.api.Client}, etc.).
 */
@Slf4j
@PluginDescriptor(
        name = "Standalone Example",
        description = "Demonstrates how to ship a Microbot plugin as a separate JAR",
        tags = {"example", "standalone", "microbot"},
        enabledByDefault = false,
        isExternal = true,
        version = "1.0.0",
        minClientVersion = "2.0.0",
        authors = {"Microbot Contributors"}
)
public class StandaloneExamplePlugin extends Plugin {

    @Inject
    private StandaloneExampleScript script;

    @Inject
    private StandaloneExampleOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Override
    protected void startUp() throws Exception {
        log.info("StandaloneExamplePlugin starting up");
        overlayManager.add(overlay);
        script.run();
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("StandaloneExamplePlugin shutting down");
        overlayManager.remove(overlay);
        script.shutdown();
    }
}
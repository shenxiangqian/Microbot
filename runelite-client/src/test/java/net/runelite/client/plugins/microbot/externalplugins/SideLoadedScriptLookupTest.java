package net.runelite.client.plugins.microbot.externalplugins;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SideLoadedScriptLookupTest {

    @PluginDescriptor(name = "Alpha", isExternal = true)
    public static class AlphaPlugin extends Plugin {
    }

    @PluginDescriptor(name = "Beta", isExternal = true)
    public static class BetaPlugin extends Plugin {
    }

    @PluginDescriptor(name = "Hidden", isExternal = true, hidden = true)
    public static class HiddenPlugin extends Plugin {
    }

    private static SideLoadedPluginDeployment deploymentOf(Plugin... plugins) {
        File jar = new File("SideLoadedScriptLookupTest.jar");
        return new SideLoadedPluginDeployment("test", jar, jar, null, Arrays.asList(plugins), null);
    }

    @Test
    public void findsScriptByDisplayName() {
        SideLoadedPluginDeployment deployment = deploymentOf(new AlphaPlugin(), new BetaPlugin());

        Plugin found = MicrobotPluginManager.findDeploymentPluginByDisplayName(deployment, "Beta");

        assertNotNull(found);
        assertEquals(BetaPlugin.class, found.getClass());
    }

    @Test
    public void returnsNothingForUnknownDisplayName() {
        SideLoadedPluginDeployment deployment = deploymentOf(new AlphaPlugin());

        assertNull(MicrobotPluginManager.findDeploymentPluginByDisplayName(deployment, "Gamma"));
    }

    @Test
    public void returnsNothingWithoutDisplayName() {
        SideLoadedPluginDeployment deployment = deploymentOf(new AlphaPlugin());

        assertNull(MicrobotPluginManager.findDeploymentPluginByDisplayName(deployment, null));
        assertNull(MicrobotPluginManager.findDeploymentPluginByDisplayName(deployment, ""));
        assertNull(MicrobotPluginManager.findDeploymentPluginByDisplayName(
                deploymentOf(Collections.emptyList().toArray(new Plugin[0])), "Alpha"));
    }

    @Test
    public void ignoresHiddenScripts() {
        SideLoadedPluginDeployment deployment = deploymentOf(new HiddenPlugin());

        assertNull(MicrobotPluginManager.findDeploymentPluginByDisplayName(deployment, "Hidden"));
    }
}

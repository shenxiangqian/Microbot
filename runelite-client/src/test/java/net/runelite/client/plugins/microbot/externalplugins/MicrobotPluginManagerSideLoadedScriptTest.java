package net.runelite.client.plugins.microbot.externalplugins;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MicrobotPluginManagerSideLoadedScriptTest {
    @Test
    public void acceptsVisibleExternalPlugin() {
        assertTrue(MicrobotPluginManager.isLaunchableSideLoadedScript(new ExternalPlugin()));
    }

    @Test
    public void rejectsInternalAndNonLaunchableExternalPlugins() {
        assertFalse(MicrobotPluginManager.isLaunchableSideLoadedScript(new InternalPlugin()));
        assertFalse(MicrobotPluginManager.isLaunchableSideLoadedScript(new HiddenExternalPlugin()));
        assertFalse(MicrobotPluginManager.isLaunchableSideLoadedScript(new AlwaysOnExternalPlugin()));
        assertFalse(MicrobotPluginManager.isLaunchableSideLoadedScript(new DisabledExternalPlugin()));
    }

    @Test
    public void matchesSideLoadedScriptBySupportedNamesIgnoringCase() {
        String internalName = "test-script";
        String className = "example.scripts.TestScriptPlugin";
        String displayName = "Test Script";

        assertTrue(MicrobotPluginManager.matchesSideLoadedScriptName(
                "test script", internalName, className, displayName));
        assertTrue(MicrobotPluginManager.matchesSideLoadedScriptName(
                "TEST-SCRIPT", internalName, className, displayName));
        assertTrue(MicrobotPluginManager.matchesSideLoadedScriptName(
                "example.scripts.TestScriptPlugin", internalName, className, displayName));
        assertTrue(MicrobotPluginManager.matchesSideLoadedScriptName(
                "testscriptplugin", internalName, className, displayName));
    }

    @Test
    public void rejectsUnknownOrEmptySideLoadedScriptName() {
        assertFalse(MicrobotPluginManager.matchesSideLoadedScriptName(
                "unknown", "test-script", "example.TestScriptPlugin", "Test Script"));
        assertFalse(MicrobotPluginManager.matchesSideLoadedScriptName(
                "", "test-script", "example.TestScriptPlugin", "Test Script"));
    }

    @PluginDescriptor(name = "External", isExternal = true, enabledByDefault = false)
    private static final class ExternalPlugin extends Plugin {
    }

    @PluginDescriptor(name = "Internal", enabledByDefault = false)
    private static final class InternalPlugin extends Plugin {
    }

    @PluginDescriptor(name = "Hidden", isExternal = true, hidden = true, enabledByDefault = false)
    private static final class HiddenExternalPlugin extends Plugin {
    }

    @PluginDescriptor(name = "Always On", isExternal = true, alwaysOn = true)
    private static final class AlwaysOnExternalPlugin extends Plugin {
    }

    @PluginDescriptor(name = "Disabled", isExternal = true, disable = true, enabledByDefault = false)
    private static final class DisabledExternalPlugin extends Plugin {
    }
}

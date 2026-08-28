package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MicrobotPluginListPanelFilterTest {
    @Test
    public void acceptsVisibleInternalMicrobotPlugin() {
        assertTrue(MicrobotPluginListPanel.isVisibleInternalPlugin(new InternalPlugin()));
    }

    @Test
    public void rejectsExternalAndHiddenPlugins() {
        assertFalse(MicrobotPluginListPanel.isVisibleInternalPlugin(new ExternalPlugin()));
        assertFalse(MicrobotPluginListPanel.isVisibleInternalPlugin(new HiddenPlugin()));
    }

    @PluginDescriptor(name = "Internal", enabledByDefault = false)
    private static final class InternalPlugin extends Plugin {
    }

    @PluginDescriptor(name = "External", isExternal = true, enabledByDefault = false)
    private static final class ExternalPlugin extends Plugin {
    }

    @PluginDescriptor(name = "Hidden", hidden = true, enabledByDefault = false)
    private static final class HiddenPlugin extends Plugin {
    }
}

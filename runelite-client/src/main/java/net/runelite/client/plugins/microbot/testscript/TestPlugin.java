package net.runelite.client.plugins.microbot.testscript;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(name = "Test Plugin",description = "测试脚本",enabledByDefault = false)
public class TestPlugin extends Plugin {
    @Inject
    private TestScript script;

    @Override
    protected void startUp() throws AWTException {
        script.run();
    }

    @Override
    protected void shutDown(){script.shutdown();}

}

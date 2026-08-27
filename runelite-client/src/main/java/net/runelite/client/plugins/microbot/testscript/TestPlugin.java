package net.runelite.client.plugins.microbot.testscript;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(name = "Test Plugin",description = "测试脚本",enabledByDefault = false)
public class TestPlugin extends Plugin {

    @Inject
    private MyScriptOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private TestScript script;

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        script.run();
    }

    @Override
    protected void shutDown(){
        overlayManager.remove(overlay);
        script.shutdown();
    }

    public int getFps(){
        int fps = script!=null?script.getFps():0;
        if(script!=null)return script.getFps();
        return 0;
    }

}

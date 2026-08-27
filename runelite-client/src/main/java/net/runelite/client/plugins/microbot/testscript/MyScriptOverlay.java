package net.runelite.client.plugins.microbot.testscript;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class MyScriptOverlay extends OverlayPanel {
    private final TestPlugin plugin = null;

    @Inject
    MyScriptOverlay(TestPlugin plugin){
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setPreferredSize(new Dimension(200,150));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // 清空之前的内容
        panelComponent.getChildren().clear();

        // 添加标题
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("我的脚本")
                .color(Color.GREEN)
                .build());

        // 添加分隔线
        panelComponent.getChildren().add(LineComponent.builder().build());

        // 添加状态信息
        panelComponent.getChildren().add(LineComponent.builder()
                .left("状态:")
                .right("运行中")
                .leftColor(Color.WHITE)
                .rightColor(Color.GREEN)
                .build());

        // 添加fps显示
//        panelComponent.getChildren().add(LineComponent.builder()
//                .left("fps:")
//                .right(String.valueOf(plugin.getFps()))
//                .build());

        return super.render(graphics);
    }

}

package net.runelite.client.plugins.microbot.SDK.Entity;

import lombok.Getter;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Getter
public class WidgetModel {
    public Widget widget;
    public WidgetModel(Widget widget){
        this.widget = widget;
    }
    public static WidgetModel of(Widget widget){return new WidgetModel(widget);}
    public boolean click(){
        return Rs2Widget.clickWidget(widget);
    }
}

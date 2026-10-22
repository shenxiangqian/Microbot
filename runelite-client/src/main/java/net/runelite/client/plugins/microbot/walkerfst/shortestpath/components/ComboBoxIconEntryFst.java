package net.runelite.client.plugins.microbot.walkerfst.shortestpath.components;

import javax.annotation.Nullable;
import javax.swing.Icon;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ComboBoxIconEntryFst
{
    private Icon icon;
    private String text;
    @Nullable
    private Object data;
}
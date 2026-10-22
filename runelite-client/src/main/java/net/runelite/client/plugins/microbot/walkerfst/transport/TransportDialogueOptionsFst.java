package net.runelite.client.plugins.microbot.walkerfst.transport;

import java.util.List;

public final class TransportDialogueOptionsFst
{
    private TransportDialogueOptionsFst()
    {
    }

    public static List<String> destinationAliases(String npcName, String displayInfo)
    {
        if ("Veos".equals(npcName) && "Port Piscarilius".equals(displayInfo))
        {
            return List.of(displayInfo, "Great Kourend");
        }
        return List.of(displayInfo);
    }
}
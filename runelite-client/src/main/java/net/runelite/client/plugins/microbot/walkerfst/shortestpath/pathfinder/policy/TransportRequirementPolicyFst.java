package net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder.policy;

import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportFst;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;

public final class TransportRequirementPolicyFst {
    private TransportRequirementPolicyFst() {
    }

    public static boolean completedQuests(TransportFst transport, List<QuestState> questStateOrder) {
        return transport.getQuests().entrySet().stream()
                .allMatch(entry -> {
                    QuestState playerState = Rs2Player.getQuestState(entry.getKey());
                    QuestState requiredState = entry.getValue();
                    int playerIndex = questStateOrder.indexOf(playerState);
                    int requiredIndex = questStateOrder.indexOf(requiredState);
                    if (requiredIndex < 0 || playerIndex < 0) {
                        return false;
                    }
                    return playerIndex >= requiredIndex;
                });
    }

    public static boolean varbitChecks(TransportFst transport) {
        return transport.getVarbits().isEmpty()
                || transport.getVarbits().stream()
                .allMatch(varbitCheck -> varbitCheck.matches(Microbot.getVarbitValue(varbitCheck.getVarbitId())));
    }

    public static boolean varplayerChecks(TransportFst transport) {
        return transport.getVarplayers().isEmpty()
                || transport.getVarplayers().stream()
                .allMatch(varplayerCheck -> varplayerCheck.matches(Microbot.getVarbitPlayerValue(varplayerCheck.getVarplayerId())));
    }
}

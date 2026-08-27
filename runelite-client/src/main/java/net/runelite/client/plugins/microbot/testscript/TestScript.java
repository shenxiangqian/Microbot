package net.runelite.client.plugins.microbot.testscript;

import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.SDK.BankTask.BankAmount;
import net.runelite.client.plugins.microbot.SDK.BankTask.BankTask;
import net.runelite.client.plugins.microbot.SDK.BankTask.EquipmentReq;
import net.runelite.client.plugins.microbot.SDK.Entity.WidgetModel;
import net.runelite.client.plugins.microbot.SDK.Query.Query;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

@Slf4j
public class TestScript extends Script {
    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;

            log.info("{}", Microbot.getClient().getFPS());

            Query.widget().actionContains("Enter name").findFirst().ifPresent(WidgetModel::click);

            var task = BankTask.builder()
                    .addInvItem(563, BankAmount.of(10))
                    .addEquipmentItem(EquipmentReq.slot(EquipmentInventorySlot.WEAPON).item(666, BankAmount.of(1)))
                    .build();
            if (!task.isSatisfied()) task.execute();

        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public int getFps(){
        return Microbot.getClient().getFPS();
    }

}

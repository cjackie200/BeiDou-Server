package org.gms.client.command.commands.gm2;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.server.quest.ElementalResonanceQuest;

public class ElementReadyCommand extends Command {
    {
        setDescription("补齐当前元素共鸣任务步骤的测试条件");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (player == null) {
            return;
        }

        ElementalResonanceQuest.TestSupplyResult result =
                ElementalResonanceQuest.supplyCurrentTestRequirements(player);
        player.yellowMessage(result.message());
    }
}

package org.gms.client.command.commands.gm0;

import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.server.MobVacManager;

public class CloseMobVacCommand extends Command {
    {
        setDescription("关闭当前角色的聚怪功能。");
    }

    @Override
    public void execute(Client client, String[] params) {
        if (client.getPlayer() == null) {
            return;
        }
        if (MobVacManager.disable(client.getPlayer())) {
            client.getPlayer().dropMessage(5, "聚怪功能已关闭。");
        } else {
            client.getPlayer().dropMessage(5, "聚怪功能当前未开启。");
        }
    }
}

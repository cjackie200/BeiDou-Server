package org.gms.client.command.commands.gm0;

import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.server.MobVacManager;

public class OpenMobVacCommand extends Command {
    {
        setDescription("开启当前角色的聚怪功能。");
    }

    @Override
    public void execute(Client client, String[] params) {
        if (client.getPlayer() == null) {
            return;
        }
        if (MobVacManager.isEnabled(client.getPlayer())) {
            client.getPlayer().dropMessage(5, "聚怪功能已经开启。输入 @gbjg 可关闭。");
            return;
        }

        MobVacManager.Result result = MobVacManager.enable(client.getPlayer());
        if (result.status() == MobVacManager.Status.BLOCKED) {
            client.getPlayer().dropMessage(5, "副本、组队任务或活动地图里不能开启聚怪。");
            return;
        }
        client.getPlayer().dropMessage(5, "聚怪功能已开启。聚怪点已固定，输入 @gbjg 可关闭。本次聚集：" + result.moved() + " 只。");
    }
}

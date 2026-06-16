package org.gms.client.command.commands.gm0;

import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.server.MobVacManager;

public class MobVacCommand extends Command {
    {
        setDescription("切换当前角色的聚怪功能。");
    }

    @Override
    public void execute(Client client, String[] params) {
        if (client.getPlayer() == null) {
            return;
        }
        MobVacManager.Result result = MobVacManager.toggle(client.getPlayer());
        if (result.status() == MobVacManager.Status.CLOSED) {
            client.getPlayer().dropMessage(5, "聚怪功能已关闭。");
        } else if (result.status() == MobVacManager.Status.BLOCKED) {
            client.getPlayer().dropMessage(5, "副本、组队任务或活动地图里不能开启聚怪。");
        } else if (result.status() == MobVacManager.Status.OPENED) {
            client.getPlayer().dropMessage(5, "聚怪功能已开启。聚怪点已固定，输入 @gbjg 可关闭。本次聚集：" + result.moved() + " 只。");
        }
    }
}

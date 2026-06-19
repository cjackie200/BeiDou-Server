package org.gms.client.command.commands.gm3;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.command.Command;
import org.gms.server.hpchallenge.HpChallengeService;
import org.gms.util.StringUtil;

public class HpChallengeCommand extends Command {
    {
        setDescription("生命之证进度管理");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            showUsage(player);
            return;
        }

        String action = params[0].toLowerCase();
        String result;
        switch (action) {
            case "status" -> {
                Character target = params.length >= 2 ? findTarget(c, params[1]) : player;
                result = HpChallengeService.gmStatus(target);
            }
            case "complete" -> {
                if (params.length < 5) {
                    showUsage(player);
                    return;
                }
                Character target = findTarget(c, params[1]);
                int stage = parseStage(params[2], player);
                if (stage < 1) {
                    return;
                }
                result = HpChallengeService.gmComplete(player, target, stage, params[3], params[4]);
            }
            case "next" -> {
                Character target = params.length >= 2 ? findTarget(c, params[1]) : player;
                result = HpChallengeService.gmCompleteCurrent(player, target);
            }
            case "reset" -> {
                if (params.length < 3) {
                    showUsage(player);
                    return;
                }
                Character target = findTarget(c, params[1]);
                int stage = parseStage(params[2], player);
                if (stage < 1) {
                    return;
                }
                result = HpChallengeService.gmResetStage(player, target, stage);
            }
            case "rollback" -> {
                if (params.length < 2) {
                    showUsage(player);
                    return;
                }
                Character target = findTarget(c, params[1]);
                result = HpChallengeService.gmRollbackLastReward(player, target);
            }
            case "unlock" -> {
                if (params.length < 2) {
                    showUsage(player);
                    return;
                }
                Character target = findTarget(c, params[1]);
                result = HpChallengeService.gmUnlockRoute(player, target);
            }
            default -> {
                showUsage(player);
                return;
            }
        }

        sendMultiline(player, result);
    }

    private static Character findTarget(Client c, String nameOrId) {
        Character target = c.getWorldServer().getPlayerStorage().getCharacterByName(nameOrId);
        if (target == null && StringUtil.isNumeric(nameOrId)) {
            target = c.getWorldServer().getPlayerStorage().getCharacterById(Integer.parseInt(nameOrId));
        }
        return target;
    }

    private static int parseStage(String value, Character player) {
        try {
            int stage = Integer.parseInt(value);
            if (stage >= 1 && stage <= 7) {
                return stage;
            }
        } catch (NumberFormatException ignored) {
        }
        player.yellowMessage("阶段必须是 1-7。");
        return -1;
    }

    private static void showUsage(Character player) {
        player.yellowMessage("用法：!hpchallenge status [角色]");
        player.yellowMessage("用法：!hpchallenge next [角色]");
        player.yellowMessage("用法：!hpchallenge complete <角色> <阶段> <main_common|main_job|optional> <task_key>");
        player.yellowMessage("用法：!hpchallenge reset <角色> <阶段>");
        player.yellowMessage("用法：!hpchallenge rollback <角色>");
        player.yellowMessage("用法：!hpchallenge unlock <角色>");
    }

    private static void sendMultiline(Character player, String text) {
        if (text == null || text.isBlank()) {
            player.yellowMessage("无结果。");
            return;
        }
        for (String line : text.replace("\r", "").split("\n")) {
            player.yellowMessage(line);
        }
    }
}

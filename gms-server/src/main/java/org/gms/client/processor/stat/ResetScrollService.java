package org.gms.client.processor.stat;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Job;
import org.gms.client.Skill;
import org.gms.constants.game.GameConstants;
import org.gms.model.pojo.SkillEntry;

import java.util.HashMap;
import java.util.Map;

public final class ResetScrollService {
    public static final int AP_RESET_SCROLL = 5050000;

    private ResetScrollService() {
    }

    public static int resetSkillStage(Character player, int stage) {
        if (player == null || stage < 1 || stage > 4) {
            return 0;
        }
        Map<Integer, Integer> refunds = new HashMap<>();
        for (Map.Entry<Skill, SkillEntry> entry : player.getSkills().entrySet()) {
            Skill skill = entry.getKey();
            int level = entry.getValue().skillLevel;
            if (level <= 0 || GameConstants.getJobBranch(Job.getById(skill.getId() / 10000)) != stage) {
                continue;
            }
            int skillBook = GameConstants.getSkillBook(skill.getId() / 10000);
            refunds.merge(skillBook, level, Integer::sum);
            player.changeSkillLevel(skill, (byte) 0, player.getMasterLevel(skill), player.getSkillExpiration(skill));
        }
        int total = 0;
        for (Map.Entry<Integer, Integer> refund : refunds.entrySet()) {
            player.gainSp(refund.getValue(), refund.getKey(), false);
            total += refund.getValue();
        }
        return total;
    }

    public static int getRemovableAp(Character player, int stat) {
        if (player == null) {
            return 0;
        }
        return switch (stat) {
            case 64 -> Math.max(0, player.getStr() - 4);
            case 128 -> Math.max(0, player.getDex() - 4);
            case 256 -> Math.max(0, player.getInt() - 4);
            case 512 -> Math.max(0, player.getLuk() - 4);
            case 2048 -> removableHp(player);
            case 8192 -> removableMp(player);
            default -> 0;
        };
    }

    private static int removableHp(Character player) {
        int minimum = player.getLevel() * 14 + 148;
        if (player.getMaxHp() < minimum || player.getHpMpApUsed() < 1) {
            return 0;
        }
        int loss = hpLoss(player.getJob());
        return Math.min(player.getHpMpApUsed(), (player.getMaxHp() - minimum) / loss + 1);
    }

    private static int removableMp(Character player) {
        int level = player.getLevel();
        Job job = player.getJob();
        int minimum;
        if (job.isA(Job.MAGICIAN) || job.isA(Job.BLAZEWIZARD1)) {
            minimum = 22 * level + 449;
        } else if (job.isA(Job.SPEARMAN)) {
            minimum = 4 * level + 155;
        } else if (job.isA(Job.FIGHTER) || job.isA(Job.ARAN1)) {
            minimum = 4 * level + 55;
        } else if (job.isA(Job.PIRATE) || job.isA(Job.THUNDERBREAKER1)) {
            minimum = 18 * level + 95;
        } else {
            minimum = 14 * level + 135;
        }
        if (player.getMaxMp() < minimum || player.getHpMpApUsed() < 1) {
            return 0;
        }
        int loss = mpLoss(job);
        return Math.min(player.getHpMpApUsed(), (player.getMaxMp() - minimum) / loss + 1);
    }

    private static int hpLoss(Job job) {
        if (job.isA(Job.WARRIOR) || job.isA(Job.DAWNWARRIOR1) || job.isA(Job.ARAN1)) return 54;
        if (job.isA(Job.MAGICIAN) || job.isA(Job.BLAZEWIZARD1)) return 10;
        if (job.isA(Job.PIRATE) || job.isA(Job.THUNDERBREAKER1)) return 42;
        if (job.isA(Job.THIEF) || job.isA(Job.NIGHTWALKER1)
                || job.isA(Job.BOWMAN) || job.isA(Job.WINDARCHER1)) return 20;
        return 12;
    }

    private static int mpLoss(Job job) {
        if (job.isA(Job.WARRIOR) || job.isA(Job.DAWNWARRIOR1) || job.isA(Job.ARAN1)) return 4;
        if (job.isA(Job.MAGICIAN) || job.isA(Job.BLAZEWIZARD1)) return 30;
        if (job.isA(Job.PIRATE) || job.isA(Job.THUNDERBREAKER1)) return 16;
        if (job.isA(Job.THIEF) || job.isA(Job.NIGHTWALKER1)
                || job.isA(Job.BOWMAN) || job.isA(Job.WINDARCHER1)) return 12;
        return 8;
    }

    public static int batchResetAp(Client client, int from, int to, int amount) {
        if (client == null || client.getPlayer() == null || amount <= 0 || from == to) {
            return 0;
        }
        Character player = client.getPlayer();
        if (getRemovableAp(player, from) < amount) {
            return 0;
        }
        int completed = 0;
        while (completed < amount && AssignAPProcessor.APResetAction(client, from, to)) {
            completed++;
        }
        return completed;
    }
}

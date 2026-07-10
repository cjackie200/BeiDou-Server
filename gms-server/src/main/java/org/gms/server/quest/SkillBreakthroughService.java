package org.gms.server.quest;

import org.gms.client.Character;
import org.gms.client.QuestStatus;
import org.gms.client.Skill;
import org.gms.client.SkillFactory;
import org.gms.constants.game.GameConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SkillBreakthroughService {
    public static final int QUEST_ID = 30006;
    public static final int ZAKUM_MOB_ID = 8800002;
    public static final int REQUIRED_ZAKUM_KILLS = 1;

    private static final Map<Integer, List<Integer>> SKILLS_BY_JOB = new HashMap<>();
    private static final Set<Integer> BREAKTHROUGH_SKILLS = new HashSet<>();

    static {
        register(112, 1001003, 1001004, 1001005, 1100002, 1100003, 1101004, 1101005, 1101006,
                1111002, 1111008, 1120003, 1121002, 1121010, 1121008);
        register(122, 1001003, 1001004, 1001005, 1200002, 1200003, 1201004, 1201005, 1201007,
                1211003, 1211005, 1211007, 1211004, 1211006, 1211008, 1221002, 1221003,
                1221004, 1221009);
        register(132, 1001003, 1001004, 1001005, 1300002, 1300003, 1301004, 1301005, 1301007,
                1311001, 1311003, 1311002, 1311004, 1311006, 1321002, 1320006);
        register(212, 2001002, 2001003, 2001005, 2101001, 2101004, 2110001, 2111002, 2111005,
                2111006, 2121005, 2121003, 2121006);
        register(222, 2001002, 2001003, 2001005, 2201001, 2201004, 2210001, 2211002, 2211005,
                2211006, 2221005, 2221003, 2221006);
        register(232, 2001002, 2001003, 2001005, 2301001, 2301002, 2301004, 2301005, 2311003,
                2311004, 2321006, 2321003, 2321007);
        register(312, 3000000, 3001003, 3001005, 3000001, 3100001, 3101002, 3101005, 3101004,
                3111006, 3111004, 3121002, 3121004, 3121008);
        register(322, 3000000, 3001003, 3001005, 3000001, 3200001, 3201002, 3201004, 3201005,
                3211004, 3211006, 3221002);
        register(412, 4000000, 4001344, 4101003, 4101004, 4100001, 4110000, 4111005, 4111002,
                4121007);
        register(422, 4000000, 4001334, 4201002, 4201003, 4201005, 4211004, 4211005, 4221001,
                4221007, 4221006);
        register(512, 5000000, 5001001, 5001002, 5101006, 5101002, 5101003, 5101004, 5111005,
                5111006, 5121003, 5121007, 5121001, 5121004);
        register(522, 5000000, 5001003, 5201003, 5201001, 5210000, 5221003, 5221007, 5221004,
                5221008, 5221006);
    }

    private SkillBreakthroughService() {
    }

    private static void register(int jobId, int... skillIds) {
        List<Integer> skills = new ArrayList<>(skillIds.length);
        for (int skillId : skillIds) {
            skills.add(skillId);
            BREAKTHROUGH_SKILLS.add(skillId);
        }
        SKILLS_BY_JOB.put(jobId, Collections.unmodifiableList(skills));
    }

    public static boolean isSupportedJob(int jobId) {
        return SKILLS_BY_JOB.containsKey(jobId);
    }

    public static boolean isQuestId(int questId) {
        return questId == QUEST_ID;
    }

    public static boolean isQuestMob(int questId, int mobId) {
        return isQuestId(questId) && mobId == ZAKUM_MOB_ID;
    }

    public static int getRequiredMobKills(int questId, int mobId) {
        if (!isQuestMob(questId, mobId)) {
            return 0;
        }
        return REQUIRED_ZAKUM_KILLS;
    }

    public static boolean isBreakthroughSkill(int skillId) {
        return BREAKTHROUGH_SKILLS.contains(skillId);
    }

    public static boolean hasCompletedBreakthrough(Character player) {
        if (player == null) {
            return false;
        }
        return player.getQuest(Quest.getInstance(QUEST_ID)).getStatus() == QuestStatus.Status.COMPLETED;
    }

    public static boolean canAssignLevel(Character player, Skill skill, int nextLevel) {
        if (player == null || skill == null) {
            return false;
        }
        if (!isBreakthroughSkill(skill.getId())) {
            return true;
        }
        if (nextLevel < skill.getMaxLevel()) {
            return true;
        }
        return nextLevel == skill.getMaxLevel() && hasCompletedBreakthrough(player);
    }

    public static CompletionReward grantCompletionReward(Character player) {
        if (player == null) {
            return CompletionReward.unsupported();
        }

        List<Integer> skillIds = SKILLS_BY_JOB.get(player.getJob().getId());
        if (skillIds == null || skillIds.isEmpty()) {
            return CompletionReward.unsupported();
        }

        List<String> grantedSkills = new ArrayList<>();
        int grantedSp = 0;
        for (int skillId : skillIds) {
            Skill skill = SkillFactory.getSkill(skillId);
            if (skill == null) {
                continue;
            }

            int skillLevel = player.getSkillLevel(skill);
            int maxLevel = skill.getMaxLevel();
            int masterLevel = Math.max(player.getMasterLevel(skill), maxLevel);
            player.changeSkillLevel(skill, (byte) skillLevel, masterLevel, player.getSkillExpiration(skill));
            player.gainSp(1, GameConstants.getSkillBook(skillId / 10000), false);
            grantedSp++;

            String skillName = SkillFactory.getSkillName(skillId);
            grantedSkills.add(skillName == null ? String.valueOf(skillId) : skillName);
        }

        return new CompletionReward(true, grantedSp, Collections.unmodifiableList(grantedSkills));
    }

    public record CompletionReward(boolean supported, int grantedSp, List<String> grantedSkills) {
        public static CompletionReward unsupported() {
            return new CompletionReward(false, 0, List.of());
        }
    }
}

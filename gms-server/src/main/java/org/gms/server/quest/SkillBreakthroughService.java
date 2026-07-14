package org.gms.server.quest;

import org.gms.client.Character;
import org.gms.client.Job;
import org.gms.client.QuestStatus;
import org.gms.client.Skill;
import org.gms.client.SkillFactory;
import org.gms.constants.game.GameConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SkillBreakthroughService {
    public static final int FIRST_QUEST_ID = 30006;
    public static final int LAST_QUEST_ID = 30009;
    public static final int QUEST_ID = FIRST_QUEST_ID;
    public static final int ZAKUM_MOB_ID = 8800002;
    public static final int REQUIRED_ZAKUM_KILLS = 1;
    public static final int LEGACY_PROGRESS_MARKER = -30009;
    public static final String LEGACY_PROGRESS_VALUE = "legacy-all";

    private static final Map<Integer, Stage> STAGES_BY_QUEST = Map.of(
            30006, new Stage(1, 30006, 2220000, 3, 10),
            30007, new Stage(2, 30007, 3220000, 3, 30),
            30008, new Stage(3, 30008, 7220000, 3, 70),
            30009, new Stage(4, 30009, ZAKUM_MOB_ID, REQUIRED_ZAKUM_KILLS, 120)
    );
    private static final Map<Integer, List<Integer>> SKILLS_BY_FINAL_JOB = new HashMap<>();
    private static final Map<Integer, Integer> STAGE_BY_SKILL = new HashMap<>();
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

    private static void register(int finalJobId, int... skillIds) {
        List<Integer> skills = new ArrayList<>(skillIds.length);
        for (int skillId : skillIds) {
            int stage = GameConstants.getJobBranch(Job.getById(skillId / 10000));
            if (stage < 1 || stage > 4) {
                throw new IllegalArgumentException("Invalid breakthrough skill job stage: " + skillId);
            }
            skills.add(skillId);
            BREAKTHROUGH_SKILLS.add(skillId);
            STAGE_BY_SKILL.put(skillId, stage);
        }
        SKILLS_BY_FINAL_JOB.put(finalJobId, Collections.unmodifiableList(skills));
    }

    public static boolean isSupportedJob(int jobId) {
        return !getSkillsForJobAndStage(jobId, 0).isEmpty();
    }

    public static boolean isQuestId(int questId) {
        return STAGES_BY_QUEST.containsKey(questId);
    }

    public static boolean isQuestMob(int questId, int mobId) {
        Stage stage = STAGES_BY_QUEST.get(questId);
        return stage != null && stage.mobId() == mobId;
    }

    public static int getQuestMobId(int questId) {
        Stage stage = STAGES_BY_QUEST.get(questId);
        return stage == null ? 0 : stage.mobId();
    }

    public static int getRequiredMobKills(int questId, int mobId) {
        Stage stage = STAGES_BY_QUEST.get(questId);
        return stage != null && stage.mobId() == mobId ? stage.requiredKills() : 0;
    }

    public static int getStageForQuest(int questId) {
        Stage stage = STAGES_BY_QUEST.get(questId);
        return stage == null ? 0 : stage.stage();
    }

    public static int getMinimumLevel(Character player, int questId) {
        Stage stage = STAGES_BY_QUEST.get(questId);
        if (stage == null || player == null) {
            return Integer.MAX_VALUE;
        }
        if (stage.stage() == 1 && player.getJob().getId() / 100 == 2) {
            return 8;
        }
        return stage.minimumLevel();
    }

    public static boolean canStartQuest(Character player, int questId) {
        Stage stage = STAGES_BY_QUEST.get(questId);
        if (player == null || stage == null || player.getLevel() < getMinimumLevel(player, questId)) {
            return false;
        }
        if (GameConstants.getJobBranch(player.getJob()) < stage.stage()) {
            return false;
        }
        if (getSkillsForJobAndStage(player.getJob().getId(), stage.stage()).isEmpty()) {
            return false;
        }
        for (int previousStage = 1; previousStage < stage.stage(); previousStage++) {
            if (getSkillsForJobAndStage(player.getJob().getId(), previousStage).isEmpty()) {
                continue;
            }
            if (!hasCompletedStage(player, previousStage)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isBreakthroughSkill(int skillId) {
        return BREAKTHROUGH_SKILLS.contains(skillId);
    }

    public static boolean hasCompletedBreakthrough(Character player) {
        if (player == null) {
            return false;
        }
        for (int stage = 1; stage <= 4; stage++) {
            if (!getSkillsForJobAndStage(player.getJob().getId(), stage).isEmpty()
                    && !hasCompletedStage(player, stage)) {
                return false;
            }
        }
        return isSupportedJob(player.getJob().getId());
    }

    public static boolean hasCompletedStage(Character player, int stage) {
        int questId = questIdForStage(stage);
        return player != null && questId != 0
                && player.getQuest(Quest.getInstance(questId)).getStatus() == QuestStatus.Status.COMPLETED;
    }

    public static boolean canAssignLevel(Character player, Skill skill, int nextLevel) {
        if (player == null || skill == null) {
            return false;
        }
        if (!isBreakthroughSkill(skill.getId()) || nextLevel < skill.getMaxLevel()) {
            return true;
        }
        Integer stage = STAGE_BY_SKILL.get(skill.getId());
        return nextLevel == skill.getMaxLevel() && stage != null && hasCompletedStage(player, stage);
    }

    public static CompletionReward grantCompletionReward(Character player, int questId) {
        Stage stage = STAGES_BY_QUEST.get(questId);
        if (player == null || stage == null) {
            return CompletionReward.unsupported();
        }

        QuestStatus current = player.getQuest(Quest.getInstance(questId));
        if (current.getStatus() != QuestStatus.Status.STARTED
                || getProgress(current, stage.mobId()) < stage.requiredKills()) {
            return CompletionReward.unsupported();
        }
        boolean legacyAll = questId == LAST_QUEST_ID
                && LEGACY_PROGRESS_VALUE.equals(current.getProgress(LEGACY_PROGRESS_MARKER));
        List<Integer> skillIds = legacyAll
                ? getSkillsForJobAndStage(player.getJob().getId(), 0)
                : getSkillsForJobAndStage(player.getJob().getId(), stage.stage());
        if (skillIds.isEmpty()) {
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

        if (legacyAll) {
            completeLegacyPreviousStages(player);
        }
        return new CompletionReward(true, grantedSp, Collections.unmodifiableList(grantedSkills), legacyAll);
    }

    public static boolean isReadyForCompletion(Character player, int questId) {
        if (player == null || !isQuestId(questId)) {
            return false;
        }
        return player.getQuest(Quest.getInstance(questId)).isRemoteCompletionReady();
    }

    public static CompletionReward grantCompletionReward(Character player) {
        return grantCompletionReward(player, QUEST_ID);
    }

    public static List<Integer> getSkillsForJobAndStage(int jobId, int stage) {
        Job currentJob = Job.getById(jobId);
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (Map.Entry<Integer, List<Integer>> entry : SKILLS_BY_FINAL_JOB.entrySet()) {
            Job finalJob = Job.getById(entry.getKey());
            if (!finalJob.isA(currentJob)) {
                continue;
            }
            for (int skillId : entry.getValue()) {
                if (stage == 0 || STAGE_BY_SKILL.get(skillId) == stage) {
                    result.add(skillId);
                }
            }
        }
        return List.copyOf(result);
    }

    private static void completeLegacyPreviousStages(Character player) {
        for (int stage = 1; stage < 4; stage++) {
            if (getSkillsForJobAndStage(player.getJob().getId(), stage).isEmpty()) {
                continue;
            }
            Quest quest = Quest.getInstance(questIdForStage(stage));
            QuestStatus completed = new QuestStatus(quest, QuestStatus.Status.COMPLETED);
            completed.setCompletionTime(System.currentTimeMillis());
            player.updateQuestStatus(completed);
        }
    }

    private static int getProgress(QuestStatus status, int mobId) {
        try {
            return Integer.parseInt(status.getProgress(mobId));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static int questIdForStage(int stage) {
        return stage >= 1 && stage <= 4 ? FIRST_QUEST_ID + stage - 1 : 0;
    }

    public record Stage(int stage, int questId, int mobId, int requiredKills, int minimumLevel) {
    }

    public record CompletionReward(boolean supported, int grantedSp, List<String> grantedSkills,
                                   boolean legacyAllStages) {
        public static CompletionReward unsupported() {
            return new CompletionReward(false, 0, List.of(), false);
        }
    }
}

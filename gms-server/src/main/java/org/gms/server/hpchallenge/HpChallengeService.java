package org.gms.server.hpchallenge;

import org.gms.client.Character;
import org.gms.client.Job;
import org.gms.client.Stat;
import org.gms.config.GameConfig;
import org.gms.constants.inventory.ItemConstants;
import org.gms.scripting.event.EventInstanceManager;
import org.gms.server.ItemInformationProvider;
import org.gms.server.quest.MonsterCardRingQuest;
import org.gms.util.DatabaseConnection;
import org.gms.util.PacketCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class HpChallengeService {
    private static final Logger log = LoggerFactory.getLogger(HpChallengeService.class);

    static final int MIN_LEVEL = 120;
    static final int MAX_STAT = 30000;
    static final int MAIN_COMMON_KILL_REQUIRED = 100;
    static final int MAIN_JOB_KILL_REQUIRED = 200;
    static final int OPTIONAL_KILL_REQUIRED = 999;
    private static final int OPTIONAL_REQUIRED_COUNT = 3;
    private static final Set<Integer> INSTRUCTOR_IDS = Set.of(1022000, 1032001, 1012100, 1052001, 1090000);
    private static final Map<Integer, StageConfig> STAGES = buildStages();

    private HpChallengeService() {
    }

    enum TaskGroup {
        MAIN_COMMON("main_common"),
        MAIN_JOB("main_job"),
        OPTIONAL("optional");

        private final String code;

        TaskGroup(String code) {
            this.code = code;
        }
    }

    enum TargetType {
        KILL,
        BOSS,
        MAP,
        PQ_ANY,
        PQ_PIRATE,
        PQ_TOY_OR_PIRATE,
        MESO,
        SCROLL_100,
        NPC_TALK,
        JUMP_MANUAL
    }

    enum JobBranch {
        WARRIOR,
        MAGE,
        BOWMAN,
        THIEF,
        PIRATE
    }

    record Task(String key, TaskGroup group, TargetType targetType, int requiredCount, String description,
                        List<Integer> targetIds, int optionNo, int mesoCost, boolean perMob) {
        int primaryTarget() {
            return targetIds.isEmpty() ? 0 : targetIds.getFirst();
        }

        boolean isOptional() {
            return group == TaskGroup.OPTIONAL;
        }

        boolean isUniqueEvent() {
            return targetType == TargetType.MAP || targetType == TargetType.SCROLL_100;
        }
    }

    record StageConfig(int stage, int requiredLevel, int mageHp, int mageMp, int warriorHp, int brawlerHp,
                               int otherHp, List<Task> commonTasks, Map<JobBranch, List<Task>> jobTasks,
                               List<Task> optionalTasks) {
    }

    private record State(int characterId, boolean routeLocked, int currentStage, int highestRewardedStage,
                         String status) {
    }

    record RewardTarget(int targetHp, int targetMp) {
    }

    record LifeProofOptionalProgress(Task task, int currentCount, int requiredCount, boolean active,
                                     boolean completed, int taskOrder) {
    }

    private record ActiveTask(Task task, ProgressRow row) {
    }

    public static boolean isRouteLocked(Character chr) {
        if (chr == null) {
            return false;
        }
        State state = loadState(chr.getId());
        return state != null && state.routeLocked();
    }

    public static boolean isLifeProofStarted(Character chr) {
        if (chr == null) {
            return false;
        }
        State state = loadState(chr.getId());
        return state != null && !"WASHING".equalsIgnoreCase(state.status());
    }

    public static boolean markWashingRoute(Character chr) {
        if (chr == null) {
            return false;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            State state = loadState(con, chr.getId());
            if (state != null) {
                return "WASHING".equalsIgnoreCase(state.status());
            }
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO hp_challenge_state
                    (character_id, route_locked, current_stage, highest_rewarded_stage, status)
                    VALUES (?, 0, 1, 0, 'WASHING')
                    """)) {
                ps.setInt(1, chr.getId());
                return ps.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            log.warn("mark washing route failed", e);
            return false;
        }
    }

    public static boolean blocksHpMpAp(Character chr, int apFrom, int apTo) {
        return isLifeProofStarted(chr) && (isHpMpAp(apFrom) || isHpMpAp(apTo));
    }

    public static boolean blocksHpMpAp(Character chr, int apTo) {
        return isLifeProofStarted(chr) && isHpMpAp(apTo);
    }

    public static void onMapChanged(Character chr) {
        // 地图探查已改为生命之证收集任务，保留 hook 但不再写旧进度表。
    }

    public static void onMonsterKilled(Character chr, int mobId) {
        if (chr == null) {
            return;
        }
        LifeProofQuest.onMonsterKilled(chr, mobId);
    }

    public static void onPartyQuestCleared(EventInstanceManager eim) {
        if (eim == null) {
            return;
        }
        String eventName = eim.getName() == null ? "" : eim.getName();
        Collection<Character> players = eim.getPlayers();
        for (Character chr : players) {
            LifeProofQuest.onPartyQuestCleared(chr, eventName);
        }
    }

    public static void onScrollUsed(Character chr, int scrollId, boolean success) {
        if (chr == null || !success || !isOneHundredPercentScroll(scrollId)) {
            return;
        }
        LifeProofQuest.onScrollUsed(chr, scrollId);
    }

    public static String tryCompleteNpcTalk(Character chr, int npcId) {
        if (chr == null || !INSTRUCTOR_IDS.contains(npcId)) {
            return null;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            State state = loadState(con, chr.getId());
            if (state == null || !isOpenJob(chr) || chr.getLevel() < MIN_LEVEL) {
                return null;
            }
            con.setAutoCommit(false);
            try {
                lockStageProgress(con, chr.getId(), state.currentStage());
                ensureProgressRows(con, chr, state.currentStage());
                ActiveTask activeTask = loadActiveTask(con, chr, state.currentStage());
                if (activeTask == null || activeTask.task().targetType() != TargetType.NPC_TALK
                        || !activeTask.task().targetIds().contains(npcId)) {
                    con.rollback();
                    return null;
                }
                if (!completeTask(con, chr.getId(), state.currentStage(), activeTask.task().group(),
                        activeTask.task().key(), true)) {
                    con.rollback();
                    return null;
                }
                activateNextTaskIfNeeded(con, chr, state.currentStage());
                con.commit();
                syncNpcScriptable(chr);
                return "已完成当前拜访：" + activeTask.task().description() + "\r\n\r\n"
                        + nextStepText(con, chr, state.currentStage());
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warn("complete hp challenge npc talk failed", e);
            return "拜访任务处理失败，请稍后再试。";
        }
    }

    public static String gmStatus(Character chr) {
        if (chr == null) {
            return "角色不存在。";
        }
        String lifeProofStatus = LifeProofQuest.gmStatus(chr);
        if (lifeProofStatus != null) {
            return lifeProofStatus;
        }
        return buildProgressText(chr);
    }

    public static String gmComplete(Character operator, Character target, int stage, String taskGroup, String taskKey) {
        if (target == null) {
            return "目标角色不存在。";
        }
        ensureStateAndProgress(target);
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement("""
                     UPDATE hp_challenge_progress
                     SET current_count = required_count, completed = 1, active = 0, completed_at = CURRENT_TIMESTAMP
                     WHERE character_id = ? AND stage = ? AND task_group = ? AND task_key = ?
                     """)) {
                ps.setInt(1, target.getId());
                ps.setInt(2, stage);
                ps.setString(3, taskGroup);
                ps.setString(4, taskKey);
                int updated = ps.executeUpdate();
                if (updated <= 0) {
                    con.rollback();
                    return "没有找到指定任务。";
                }
                activateNextTaskIfNeeded(con, target, stage);
                con.commit();
                syncNpcScriptable(target);
                logGm(operator, target.getId(), "complete", "stage=" + stage + ", group=" + taskGroup + ", key=" + taskKey);
                return "已补齐任务：" + taskGroup + "/" + taskKey;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warn("gm complete hp challenge task failed", e);
            return "补齐任务失败。";
        }
    }

    public static String gmCompleteCurrent(Character operator, Character target) {
        if (target == null) {
            return "目标角色不存在。";
        }
        String lifeProofResult = LifeProofQuest.gmCompleteCurrent(operator, target);
        if (lifeProofResult != null) {
            return lifeProofResult;
        }
        if (!ensureStateAndProgress(target)) {
            return openRequirementText(target);
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                State state = loadState(con, target.getId());
                if (state == null) {
                    con.rollback();
                    return "目标角色尚未开启生命之证。";
                }
                lockStageProgress(con, target.getId(), state.currentStage());
                ActiveTask activeTask = loadActiveTask(con, target, state.currentStage());
                if (activeTask == null) {
                    if (isOptionalChoiceAvailable(con, target.getId(), state.currentStage())) {
                        con.rollback();
                        return "当前需要先选择第 " + (selectedOptionalCount(con, target.getId(), state.currentStage()) + 1)
                                + " 个附加挑战。";
                    }
                    if (isStageReady(target.getId(), state.currentStage())) {
                        con.rollback();
                        return "当前阶段任务已经完成，可以领取奖励。";
                    }
                    con.rollback();
                    return "没有可补齐的当前任务，请检查进度。";
                }
                if (!completeTask(con, target.getId(), state.currentStage(), activeTask.task().group(),
                        activeTask.task().key(), true)) {
                    con.rollback();
                    return "当前任务已经变化，请重新查看进度。";
                }
                activateNextTaskIfNeeded(con, target, state.currentStage());
                String nextStep = nextStepText(con, target, state.currentStage());
                con.commit();
                syncNpcScriptable(target);
                logGm(operator, target.getId(), "complete_current",
                        "stage=" + state.currentStage() + ", group=" + activeTask.task().group().code
                                + ", key=" + activeTask.task().key());
                return "已补齐当前任务：" + activeTask.task().description() + "\r\n" + nextStep;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warn("gm complete current hp challenge task failed", e);
            return "补齐当前任务失败。";
        }
    }

    public static String gmResetStage(Character operator, Character target, int stage) {
        if (target == null) {
            return "目标角色不存在。";
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM hp_challenge_progress WHERE character_id = ? AND stage = ?")) {
                ps.setInt(1, target.getId());
                ps.setInt(2, stage);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM hp_challenge_event_log WHERE character_id = ? AND stage = ?")) {
                ps.setInt(1, target.getId());
                ps.setInt(2, stage);
                ps.executeUpdate();
            }
            logGm(operator, target.getId(), "reset_stage", "stage=" + stage);
            ensureStateAndProgress(target);
            return "已重置阶段 " + stage + " 的进度。";
        } catch (SQLException e) {
            log.warn("gm reset hp challenge stage failed", e);
            return "重置阶段失败。";
        }
    }

    public static String gmUnlockRoute(Character operator, Character target) {
        if (target == null) {
            return "目标角色不存在。";
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE hp_challenge_state SET route_locked = 0 WHERE character_id = ?")) {
            ps.setInt(1, target.getId());
            ps.executeUpdate();
            logGm(operator, target.getId(), "unlock_route", "route_locked=0");
            return "已解除生命之证路线锁定。";
        } catch (SQLException e) {
            log.warn("gm unlock hp challenge route failed", e);
            return "解除路线锁定失败。";
        }
    }

    public static String gmRollbackLastReward(Character operator, Character target) {
        if (target == null) {
            return "目标角色不存在。";
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            RewardLog latest = latestRewardLog(con, target.getId());
            if (latest == null) {
                return "没有可回滚的奖励。";
            }
            if (hasHigherActiveReward(con, target.getId(), latest.stage())) {
                return "后续阶段已经领取，必须先从最高阶段开始回滚。";
            }
            target.updateMaxHpMaxMp(latest.beforeMaxHp(), latest.beforeMaxMp());
            target.updateHp(latest.beforeHp());
            target.updateMp(latest.beforeMp());

            try (PreparedStatement ps = con.prepareStatement("UPDATE hp_challenge_reward_log SET reverted = 1, reverted_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                ps.setInt(1, latest.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("""
                    UPDATE hp_challenge_state
                    SET highest_rewarded_stage = ?, current_stage = ?, route_locked = CASE WHEN ? = 0 THEN 0 ELSE route_locked END, status = 'STARTED'
                    WHERE character_id = ?
                    """)) {
                int highest = latest.stage() - 1;
                ps.setInt(1, highest);
                ps.setInt(2, latest.stage());
                ps.setInt(3, highest);
                ps.setInt(4, target.getId());
                ps.executeUpdate();
            }
            logGm(operator, target.getId(), "rollback_reward", "stage=" + latest.stage());
            return "已回滚阶段 " + latest.stage() + " 奖励。";
        } catch (SQLException e) {
            log.warn("gm rollback hp challenge reward failed", e);
            return "回滚奖励失败。";
        }
    }

    public static String claimCurrentStageReward(Character chr) {
        if (!ensureStateAndProgress(chr)) {
            return openRequirementText(chr);
        }
        State state = loadState(chr.getId());
        StageConfig stage = stage(state.currentStage());
        if (chr.getLevel() < stage.requiredLevel()) {
            return "当前阶段需要等级达到 " + stage.requiredLevel() + "。";
        }
        if (!isStageReady(chr.getId(), stage.stage())) {
            return "当前阶段尚未完成。请先完成公共阶段核心、职业主线分支，以及 3 个附加挑战。";
        }
        if (hasActiveReward(chr.getId(), stage.stage())) {
            return "该阶段奖励已经领取。";
        }

        RewardTarget target = rewardTarget(chr, stage);
        int beforeMaxHp = chr.getMaxHp();
        int beforeMaxMp = chr.getMaxMp();
        int beforeHp = chr.getHp();
        int beforeMp = chr.getMp();
        int afterMaxHp = Math.min(MAX_STAT, Math.max(beforeMaxHp, target.targetHp()));
        int afterMaxMp = Math.min(MAX_STAT, Math.max(beforeMaxMp, target.targetMp()));

        try (Connection con = DatabaseConnection.getConnection()) {
            // Double-check inside the transaction to close the TOCTOU window (BUG #2).
            if (hasActiveReward(chr.getId(), stage.stage())) {
                return "该阶段奖励已经领取。";
            }
            chr.updateMaxHpMaxMp(afterMaxHp, afterMaxMp);
            chr.updateHp(afterMaxHp);
            chr.updateMp(afterMaxMp);
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO hp_challenge_reward_log
                    (character_id, stage, job_id, before_maxhp, before_maxmp, before_hp, before_mp,
                     after_maxhp, after_maxmp, after_hp, after_mp, operator)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                    """)) {
                ps.setInt(1, chr.getId());
                ps.setInt(2, stage.stage());
                ps.setInt(3, chr.getJob().getId());
                ps.setInt(4, beforeMaxHp);
                ps.setInt(5, beforeMaxMp);
                ps.setInt(6, beforeHp);
                ps.setInt(7, beforeMp);
                ps.setInt(8, afterMaxHp);
                ps.setInt(9, afterMaxMp);
                ps.setInt(10, afterMaxHp);
                ps.setInt(11, afterMaxMp);
                ps.executeUpdate();
            }
            int nextStage = Math.min(7, stage.stage() + 1);
            String status = stage.stage() >= 7 ? "COMPLETED" : "STARTED";
            try (PreparedStatement ps = con.prepareStatement("""
                    UPDATE hp_challenge_state
                    SET route_locked = 1, highest_rewarded_stage = ?, current_stage = ?, status = ?
                    WHERE character_id = ?
                    """)) {
                ps.setInt(1, stage.stage());
                ps.setInt(2, nextStage);
                ps.setString(3, status);
                ps.setInt(4, chr.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn("claim hp challenge reward failed", e);
            return "奖励已经写入角色，但奖励日志写入失败，请联系 GM 检查。";
        }

        ensureStateAndProgress(chr);
        syncNpcScriptable(chr);
        return "领取成功。\r\n"
                + "HP：" + beforeMaxHp + " -> " + afterMaxHp + "\r\n"
                + "MP：" + beforeMaxMp + " -> " + afterMaxMp + "\r\n"
                + "当前 HP 和 MP 已回满。";
    }

    public static String claimLifeProofStageReward(Character chr, int stageNo) {
        if (!ensureLifeProofState(chr)) {
            return openRequirementText(chr);
        }
        StageConfig stage = stage(stageNo);
        if (chr.getLevel() < stage.requiredLevel()) {
            return "当前阶段需要等级达到 " + stage.requiredLevel() + "。";
        }
        if (hasActiveReward(chr.getId(), stage.stage())) {
            return "该阶段奖励已经领取。";
        }

        RewardTarget target = rewardTarget(chr, stage);
        int beforeMaxHp = chr.getMaxHp();
        int beforeMaxMp = chr.getMaxMp();
        int beforeHp = chr.getHp();
        int beforeMp = chr.getMp();
        int afterMaxHp = Math.min(MAX_STAT, Math.max(beforeMaxHp, target.targetHp()));
        int afterMaxMp = Math.min(MAX_STAT, Math.max(beforeMaxMp, target.targetMp()));

        try (Connection con = DatabaseConnection.getConnection()) {
            // Double-check inside the transaction to close the TOCTOU window (BUG #2).
            if (hasActiveReward(chr.getId(), stage.stage())) {
                return "该阶段奖励已经领取。";
            }
            chr.updateMaxHpMaxMp(afterMaxHp, afterMaxMp);
            chr.updateHp(afterMaxHp);
            chr.updateMp(afterMaxMp);
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO hp_challenge_reward_log
                    (character_id, stage, job_id, before_maxhp, before_maxmp, before_hp, before_mp,
                     after_maxhp, after_maxmp, after_hp, after_mp, operator)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                    """)) {
                ps.setInt(1, chr.getId());
                ps.setInt(2, stage.stage());
                ps.setInt(3, chr.getJob().getId());
                ps.setInt(4, beforeMaxHp);
                ps.setInt(5, beforeMaxMp);
                ps.setInt(6, beforeHp);
                ps.setInt(7, beforeMp);
                ps.setInt(8, afterMaxHp);
                ps.setInt(9, afterMaxMp);
                ps.setInt(10, afterMaxHp);
                ps.setInt(11, afterMaxMp);
                ps.executeUpdate();
            }
            int nextStage = Math.min(7, stage.stage() + 1);
            String status = stage.stage() >= 7 ? "COMPLETED" : "STARTED";
            try (PreparedStatement ps = con.prepareStatement("""
                    UPDATE hp_challenge_state
                    SET route_locked = 1, highest_rewarded_stage = GREATEST(highest_rewarded_stage, ?),
                        current_stage = GREATEST(current_stage, ?), status = ?
                    WHERE character_id = ?
                    """)) {
                ps.setInt(1, stage.stage());
                ps.setInt(2, nextStage);
                ps.setString(3, status);
                ps.setInt(4, chr.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn("claim life proof reward failed", e);
            return "奖励已经写入角色，但奖励日志写入失败，请联系 GM 检查。";
        }

        return "领取成功。\r\n"
                + "HP：" + beforeMaxHp + " -> " + afterMaxHp + "\r\n"
                + "MP：" + beforeMaxMp + " -> " + afterMaxMp + "\r\n"
                + "当前 HP 和 MP 已回满。";
    }

    private static void incrementMatching(Character chr, TargetType eventType, int eventId, String eventKey, String eventName) {
        if (!ensureStateAndProgress(chr)) {
            return;
        }
        boolean matched = false;
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                State state = loadState(con, chr.getId());
                if (state == null) {
                    con.rollback();
                    return;
                }
                ActiveTask activeTask = loadActiveTask(con, chr, state.currentStage());
                if (activeTask == null || !matches(activeTask.task(), eventType, eventId, eventName)) {
                    con.rollback();
                    return;
                }
                incrementTask(con, chr, state.currentStage(), activeTask.task(),
                        eventKey != null ? eventKey : activeTask.task().key() + ":" + eventId + ":" + System.nanoTime());
                matched = true;
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warn("increment hp challenge task failed", e);
        }
        if (matched) {
            syncNpcScriptable(chr);
        }
    }

    private static void incrementTask(Connection con, Character chr, int stage, Task task, String eventKey) throws SQLException {
        ProgressRow row = loadProgressRow(con, chr.getId(), stage, task.group(), task.key());
        if (row == null || row.completed || !row.active) {
            return;
        }
        if (task.isOptional() && !row.selected) {
            return;
        }
        if (task.isUniqueEvent() && !insertUniqueEvent(con, chr.getId(), stage, task, eventKey)) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("""
                UPDATE hp_challenge_progress
                SET current_count = LEAST(required_count, current_count + 1),
                    completed = CASE WHEN current_count + 1 >= required_count THEN 1 ELSE completed END,
                    active = CASE WHEN current_count + 1 >= required_count THEN 0 ELSE active END,
                    completed_at = CASE WHEN current_count + 1 >= required_count THEN CURRENT_TIMESTAMP ELSE completed_at END
                WHERE character_id = ? AND stage = ? AND task_group = ? AND task_key = ?
                    AND active = 1 AND completed = 0
                """)) {
            ps.setInt(1, chr.getId());
            ps.setInt(2, stage);
            ps.setString(3, task.group().code);
            ps.setString(4, task.key());
            if (ps.executeUpdate() <= 0) {
                return;
            }
        }
        ProgressRow updated = loadProgressRow(con, chr.getId(), stage, task.group(), task.key());
        if (updated != null && updated.completed) {
            activateNextTaskIfNeeded(con, chr, stage);
        }
    }

    private static String paySelectedMesoOption(Character chr) {
        if (!ensureStateAndProgress(chr)) {
            return openRequirementText(chr);
        }
        State state = loadState(chr.getId());
        StageConfig stage = stage(state.currentStage());
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                lockStageProgress(con, chr.getId(), stage.stage());
                ActiveTask activeTask = loadActiveTask(con, chr, stage.stage());
                if (activeTask == null || activeTask.task().targetType() != TargetType.MESO) {
                    con.rollback();
                    return "当前步骤不是金币挑战。";
                }
                Task mesoTask = activeTask.task();
                if (chr.getMeso() < mesoTask.mesoCost()) {
                    con.rollback();
                    return "金币不足，需要 " + mesoTask.mesoCost() + " 金币。";
                }
                if (!completeTask(con, chr.getId(), stage.stage(), mesoTask.group(), mesoTask.key(), true)) {
                    con.rollback();
                    return "当前金币挑战已经处理，请重新打开菜单确认。";
                }
                chr.gainMeso(-mesoTask.mesoCost(), true, true, true);
                activateNextTaskIfNeeded(con, chr, stage.stage());
                con.commit();
                syncNpcScriptable(chr);
                return "已缴纳 " + mesoTask.mesoCost() + " 金币，金币挑战完成。";
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warn("pay hp challenge meso failed", e);
            return "缴纳金币失败。";
        }
    }

    private static boolean ensureStateAndProgress(Character chr) {
        if (chr == null || !isOpenJob(chr) || chr.getLevel() < MIN_LEVEL) {
            return false;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            State state = loadState(con, chr.getId());
            if (state == null) {
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO hp_challenge_state (character_id, route_locked, current_stage, highest_rewarded_stage, status)
                        VALUES (?, 0, 1, 0, 'STARTED')
                        """)) {
                    ps.setInt(1, chr.getId());
                    ps.executeUpdate();
                }
                state = loadState(con, chr.getId());
            }
            int currentStage = Math.max(1, Math.min(7, state.currentStage()));
            ensureProgressRows(con, chr, currentStage);
            return true;
        } catch (SQLException e) {
            log.warn("ensure hp challenge state failed", e);
            return false;
        }
    }

    static boolean ensureLifeProofState(Character chr) {
        if (chr == null || !isOpenJob(chr) || chr.getLevel() < MIN_LEVEL) {
            return false;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            State state = loadState(con, chr.getId());
            if (state != null) {
                if ("WASHING".equalsIgnoreCase(state.status())) {
                    return false;
                }
                ensureProgressRows(con, chr, Math.max(1, Math.min(7, state.currentStage())));
                return true;
            }
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO hp_challenge_state (character_id, route_locked, current_stage, highest_rewarded_stage, status)
                    VALUES (?, 0, 1, 0, 'STARTED')
                    """)) {
                ps.setInt(1, chr.getId());
                ps.executeUpdate();
            }
            ensureProgressRows(con, chr, 1);
            return true;
        } catch (SQLException e) {
            log.warn("ensure life proof state failed", e);
            return false;
        }
    }

    private static void ensureProgressRows(Connection con, Character chr, int stageNo) throws SQLException {
        StageConfig stage = stage(stageNo);
        List<Task> tasks = orderedStageTasks(chr, stage);
        int taskOrder = 1;
        for (Task task : tasks) {
            int initialOrder = task.isOptional() ? 0 : taskOrder++;
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO hp_challenge_progress
                    (character_id, stage, task_group, task_key, task_order, target_type, target_id, current_count,
                     required_count, selected, active, completed, accepted_at)
                    SELECT ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 0, 0, NULL
                    WHERE NOT EXISTS (
                        SELECT 1 FROM hp_challenge_progress
                        WHERE character_id = ? AND stage = ? AND task_group = ? AND task_key = ?
                    )
                    """)) {
                ps.setInt(1, chr.getId());
                ps.setInt(2, stageNo);
                ps.setString(3, task.group().code);
                ps.setString(4, task.key());
                ps.setInt(5, initialOrder);
                ps.setString(6, task.targetType().name());
                ps.setInt(7, task.primaryTarget());
                ps.setInt(8, task.requiredCount());
                ps.setInt(9, task.isOptional() ? 0 : 1);
                ps.setInt(10, chr.getId());
                ps.setInt(11, stageNo);
                ps.setString(12, task.group().code);
                ps.setString(13, task.key());
                ps.executeUpdate();
            }
        }
        activateNextTaskIfNeeded(con, chr, stageNo);
    }

    private static boolean matches(Task task, TargetType eventType, int eventId, String eventName) {
        return switch (task.targetType()) {
            case KILL, BOSS -> eventType == TargetType.KILL && task.targetIds().contains(eventId);
            case MAP -> eventType == TargetType.MAP && task.targetIds().contains(eventId);
            case NPC_TALK -> eventType == TargetType.NPC_TALK && task.targetIds().contains(eventId);
            case PQ_ANY -> eventType == TargetType.PQ_ANY;
            case PQ_PIRATE -> eventType == TargetType.PQ_ANY && containsEventName(eventName, "pirate");
            case PQ_TOY_OR_PIRATE -> eventType == TargetType.PQ_ANY && (containsEventName(eventName, "pirate") || containsEventName(eventName, "ludi"));
            case SCROLL_100 -> eventType == TargetType.SCROLL_100;
            case MESO, JUMP_MANUAL -> false;
        };
    }

    private static boolean containsEventName(String eventName, String keyword) {
        return eventName != null && eventName.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static boolean isStageReady(int characterId, int stage) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return activeCount(con, characterId, stage) == 0
                    && incompleteCount(con, characterId, stage, TaskGroup.MAIN_COMMON) == 0
                    && incompleteCount(con, characterId, stage, TaskGroup.MAIN_JOB) == 0
                    && completedSelectedOptionalCount(con, characterId, stage) >= OPTIONAL_REQUIRED_COUNT;
        } catch (SQLException e) {
            log.warn("check hp challenge stage ready failed", e);
            return false;
        }
    }

    private static String buildSummary(Character chr) {
        if (!isOpenJob(chr)) {
            return openRequirementText(chr);
        }
        ensureStateAndProgress(chr);
        State state = loadState(chr.getId());
        StageConfig stage = stage(state.currentStage());
        RewardTarget target = rewardTarget(chr, stage);
        return "当前阶段：T" + stage.stage() + "，等级要求：" + stage.requiredLevel() + "\r\n"
                + "当前职业分支：" + branchName(branch(chr.getJob())) + "\r\n"
                + "阶段目标 HP：" + target.targetHp() + "，阶段目标 MP：" + target.targetMp() + "\r\n"
                + "路线锁定：" + (state.routeLocked() ? "已锁定" : "未锁定");
    }

    private static String buildProgressText(Character chr) {
        if (!ensureStateAndProgress(chr)) {
            return openRequirementText(chr);
        }
        State state = loadState(chr.getId());
        StageConfig stage = stage(state.currentStage());
        StringBuilder sb = new StringBuilder();
        sb.append(buildSummary(chr)).append("\r\n\r\n");
        ActiveTask activeTask = loadActiveTask(chr, stage.stage());
        if (activeTask != null) {
            sb.append("#e当前任务#n\r\n");
            appendTaskProgress(sb, activeTask.task(), activeTask.row());
        } else if (isOptionalChoiceAvailable(chr.getId(), stage.stage())) {
            sb.append("#e当前任务#n\r\n");
            sb.append("选择第 ").append(nextOptionalSlot(chr.getId(), stage.stage()))
                    .append(" 个附加挑战。\r\n");
        } else if (isStageReady(chr.getId(), stage.stage())) {
            sb.append("#e当前任务#n\r\n领取阶段奖励。\r\n");
        }

        sb.append("\r\n#e已完成记录#n\r\n");
        appendCompletedProgress(sb, chr, stage);
        return sb.toString();
    }

    private static void appendCompletedProgress(StringBuilder sb, Character chr, StageConfig stage) {
        boolean appended = false;
        Map<String, ProgressRow> commonRows = loadProgress(chr.getId(), stage.stage(), TaskGroup.MAIN_COMMON);
        Map<String, ProgressRow> jobRows = loadProgress(chr.getId(), stage.stage(), TaskGroup.MAIN_JOB);
        Map<String, ProgressRow> optionalRows = loadProgress(chr.getId(), stage.stage(), TaskGroup.OPTIONAL);
        for (Task task : orderedStageTasks(chr, stage)) {
            Map<String, ProgressRow> rows = switch (task.group()) {
                case MAIN_COMMON -> commonRows;
                case MAIN_JOB -> jobRows;
                case OPTIONAL -> optionalRows;
            };
            ProgressRow row = rows.get(task.key());
            if (row == null || !row.completed || task.isOptional() && !row.selected) {
                continue;
            }
            appendTaskProgress(sb, task, row);
            appended = true;
        }
        if (!appended) {
            sb.append("暂无已完成任务。\r\n");
        }
    }

    private static void appendTaskProgress(StringBuilder sb, Task task, ProgressRow row) {
        String prefix = task.isOptional() ? "附加挑战 " + task.optionNo() + "：" : "";
        sb.append("- ").append(prefix).append(task.description()).append("：")
                .append(row.currentCount).append("/").append(row.requiredCount)
                .append(row.completed ? "，完成" : "").append("\r\n");
    }

    private static String buildRulesText(Character chr) {
        return "生命之证规则：\r\n"
                + "1. 120 级四转冒险家可以开启。\r\n"
                + "2. 每阶段任务按顺序逐个完成，未解锁任务不提前计数。\r\n"
                + "3. 领取首次奖励后锁定生命之证路线，不能再通过 AP 操作洗 HP/MP。\r\n"
                + "4. 奖励直接补到阶段目标 maxhp/maxmp，不使用血量戒指。\r\n"
                + "5. 附加挑战每次只选择 1 个，完成 3 个后才可领取阶段奖励。";
    }

    static String openRequirementText(Character chr) {
        if (chr == null) {
            return "角色不存在。";
        }
        if (!isOpenJob(chr)) {
            return "生命之证仅开放给四转冒险家职业。";
        }
        if (chr.getLevel() < MIN_LEVEL) {
            return "生命之证需要达到 120 级后开启。";
        }
        return "暂时无法开启生命之证。";
    }

    static RewardTarget rewardTarget(Character chr, StageConfig stage) {
        Job job = chr.getJob();
        if (job.isA(Job.MAGICIAN)) {
            return new RewardTarget(stage.mageHp(), stage.mageMp());
        }
        if (job.isA(Job.WARRIOR)) {
            return new RewardTarget(stage.warriorHp(), chr.getMaxMp());
        }
        if (job.isA(Job.BUCCANEER)) {
            return new RewardTarget(stage.brawlerHp(), chr.getMaxMp());
        }
        return new RewardTarget(stage.otherHp(), chr.getMaxMp());
    }

    static boolean isOpenJob(Character chr) {
        return chr != null && isOpenJob(chr.getJob());
    }

    static boolean isOpenJob(Job job) {
        return switch (job) {
            case HERO, PALADIN, DARKKNIGHT, FP_ARCHMAGE, IL_ARCHMAGE, BISHOP, BOWMASTER, MARKSMAN,
                 NIGHTLORD, SHADOWER, BUCCANEER, CORSAIR -> true;
            default -> false;
        };
    }

    private static void syncNpcScriptable(Character chr) {
        if (chr == null || chr.getClient() == null || !GameConfig.getServerBoolean("use_npcs_scriptable")) {
            return;
        }
        chr.getClient().sendPacket(PacketCreator.setNPCScriptable(MonsterCardRingQuest.getScriptableNpcIds(chr)));
    }

    static int instructorNpcForJob(Job job) {
        if (job.isA(Job.WARRIOR)) {
            return 1022000;
        }
        if (job.isA(Job.MAGICIAN)) {
            return 1032001;
        }
        if (job.isA(Job.BOWMAN)) {
            return 1012100;
        }
        if (job.isA(Job.THIEF)) {
            return 1052001;
        }
        if (job.isA(Job.PIRATE)) {
            return 1090000;
        }
        return 0;
    }

    static JobBranch branch(Job job) {
        if (job.isA(Job.WARRIOR)) {
            return JobBranch.WARRIOR;
        }
        if (job.isA(Job.MAGICIAN)) {
            return JobBranch.MAGE;
        }
        if (job.isA(Job.BOWMAN)) {
            return JobBranch.BOWMAN;
        }
        if (job.isA(Job.THIEF)) {
            return JobBranch.THIEF;
        }
        return JobBranch.PIRATE;
    }

    private static String branchName(JobBranch branch) {
        return switch (branch) {
            case WARRIOR -> "战士";
            case MAGE -> "法师";
            case BOWMAN -> "弓箭手";
            case THIEF -> "飞侠";
            case PIRATE -> "海盗";
        };
    }

    private static boolean isHpMpAp(int ap) {
        return ap == 2048 || ap == 8192;
    }

    static boolean isOneHundredPercentScroll(int scrollId) {
        if (ItemConstants.getInventoryType(scrollId) != org.gms.client.inventory.InventoryType.USE) {
            return false;
        }
        Map<String, Integer> stats = ItemInformationProvider.getInstance().getEquipStats(scrollId);
        return stats != null && Objects.equals(stats.get("success"), 100);
    }

    static StageConfig stage(int stage) {
        StageConfig config = STAGES.get(stage);
        if (config == null) {
            throw new IllegalArgumentException("Unknown hp challenge stage: " + stage);
        }
        return config;
    }

    static Task optionalTask(int stage, int optionNo) {
        if (optionNo <= 0) {
            return null;
        }
        return stage(stage).optionalTasks().stream()
                .filter(task -> task.optionNo() == optionNo)
                .findFirst()
                .orElse(null);
    }

    private static Task t(String key, TaskGroup group, TargetType type, int required, String description, int... ids) {
        return new Task(key, group, type, normalizedRequired(group, type, required), description,
                Arrays.stream(ids).boxed().collect(Collectors.toList()), 0, 0, false);
    }

    private static Task tPerMob(String key, TaskGroup group, TargetType type, int required, String description, int... ids) {
        return new Task(key, group, type, normalizedRequired(group, type, required), description,
                Arrays.stream(ids).boxed().collect(Collectors.toList()), 0, 0, true);
    }

    private static Task opt(int optionNo, TargetType type, int required, String description, int... ids) {
        return new Task("optional_" + optionNo, TaskGroup.OPTIONAL, type,
                normalizedRequired(TaskGroup.OPTIONAL, type, required), description,
                Arrays.stream(ids).boxed().collect(Collectors.toList()), optionNo, 0, false);
    }

    private static int normalizedRequired(TaskGroup group, TargetType type, int required) {
        if (type != TargetType.KILL) {
            return required;
        }
        return switch (group) {
            case MAIN_COMMON -> MAIN_COMMON_KILL_REQUIRED;
            case MAIN_JOB -> MAIN_JOB_KILL_REQUIRED;
            case OPTIONAL -> OPTIONAL_KILL_REQUIRED;
        };
    }

    private static Task mesoOpt(int optionNo, int meso) {
        return new Task("optional_" + optionNo, TaskGroup.OPTIONAL, TargetType.MESO, 1,
                "向一转教官缴纳金币 " + meso, List.of(), optionNo, meso, false);
    }

    private static Task npcVisit(String key, int npcId, String npcName) {
        return t(key, TaskGroup.MAIN_COMMON, TargetType.NPC_TALK, 1, "拜访" + npcName, npcId);
    }

    private static Map<JobBranch, List<Task>> jobs(List<Task> warrior, List<Task> mage, List<Task> bowman,
                                                   List<Task> thief, List<Task> pirate) {
        Map<JobBranch, List<Task>> map = new LinkedHashMap<>();
        map.put(JobBranch.WARRIOR, warrior);
        map.put(JobBranch.MAGE, mage);
        map.put(JobBranch.BOWMAN, bowman);
        map.put(JobBranch.THIEF, thief);
        map.put(JobBranch.PIRATE, pirate);
        return map;
    }

    private static Map<Integer, StageConfig> buildStages() {
        Map<Integer, StageConfig> stages = new LinkedHashMap<>();
        stages.put(1, new StageConfig(1, 120, 1850, 15000, 10200, 8950, 4800,
                List.of(
                        npcVisit("visit_instructor_athena", 1012100, "赫丽娜"),
                        npcVisit("visit_instructor_grendel", 1032001, "汉斯"),
                        npcVisit("visit_instructor_balrog", 1022000, "武术教练"),
                        npcVisit("visit_instructor_dark_lord", 1052001, "达克鲁"),
                        npcVisit("visit_instructor_kyrin", 1090000, "凯琳"),
                        t("kill_crimson_balrog", TaskGroup.MAIN_COMMON, TargetType.BOSS, 1, "参与击杀蝙蝠魔", 8150000)
                ),
                jobs(
                        List.of(t("warrior_tauromacis", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀月牙牛魔王", 7130100), t("warrior_taurospear", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀长枪牛魔王", 7130101), t("warrior_commander_skeleton", TaskGroup.MAIN_JOB, TargetType.KILL, 500, "击杀骷髅指挥官", 7130103)),
                        List.of(t("mage_buffoon", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀时之鬼兵", 6300100), t("mage_deep_buffoon", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀时之鬼将", 6400100), t("mage_master_death_teddy", TaskGroup.MAIN_JOB, TargetType.KILL, 500, "击杀死灵王", 7130300)),
                        List.of(t("bowman_harp", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀哈维", 8140001), t("bowman_blood_harp", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀血腥哈维", 8140002), t("bowman_werewolf", TaskGroup.MAIN_JOB, TargetType.KILL, 500, "击杀狼人", 7130200)),
                        List.of(t("thief_death_teddy", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀死灵", 7130010), t("thief_soul_teddy", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀恶灵附身的娃娃", 6230400), t("thief_ghost_pirate", TaskGroup.MAIN_JOB, TargetType.KILL, 500, "击杀蓝帽海贼", 7140000)),
                        List.of(t("pirate_goby_house", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀刺鳍鱼之屋", 8140555), t("pirate_bain", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀火焰猎犬", 8140500), t("pirate_dual_ghost_pirate", TaskGroup.MAIN_JOB, TargetType.KILL, 500, "击杀绿帽海贼", 7160000))
                ),
                List.of(opt(1, TargetType.SCROLL_100, 30, "使用任意 100% 卷轴"), opt(2, TargetType.BOSS, 3, "参与击杀蝙蝠魔", 8150000), opt(3, TargetType.PQ_ANY, 5, "完成任意组队任务"), opt(4, TargetType.MAP, 10, "在隐藏地图击杀怪物收集初醒隐秘生命之证", 100000005, 101000100, 102000100, 103000100, 105040300, 220020300, 230040400, 240040510, 270030500, 261020401), mesoOpt(5, 100000000), opt(6, TargetType.KILL, 1200, "击杀黄小丑", 6130200), opt(7, TargetType.KILL, 1200, "击杀恶灵附身的娃娃", 6230400), opt(8, TargetType.JUMP_MANUAL, 3, "完成任意跳跳任务"))
        ));
        stages.put(2, new StageConfig(2, 130, 2025, 18750, 12250, 10550, 5750,
                List.of(t("visit_leafre_maps", TaskGroup.MAIN_COMMON, TargetType.MAP, 4, "收集力量水晶", 240020000, 240020100, 240020200, 240020300), tPerMob("kill_centaur_common", TaskGroup.MAIN_COMMON, TargetType.KILL, 500, "击杀三色半人马", 8140101, 8140102, 8140103), t("kill_leafre_boss", TaskGroup.MAIN_COMMON, TargetType.BOSS, 1, "参与击杀火焰龙或天鹰", 8180000, 8180001)),
                jobs(
                        List.of(t("warrior_dark_cornian", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀邪恶双刀蜥蜴", 8150201), t("warrior_birk", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀邪恶绵羊", 8140110), t("warrior_brexton", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀犀牛龙怪", 8140703)),
                        List.of(t("mage_red_wyvern", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀红飞龙", 8150300), t("mage_blue_wyvern", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀蓝飞龙", 8150301), t("mage_dark_wyvern", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀黑飞龙", 8150302)),
                        List.of(t("bowman_harp", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀哈维", 8140001), t("bowman_blood_harp", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀血腥哈维", 8140002), t("bowman_blue_dragon_turtle", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀蓝海龟龙", 8140700)),
                        List.of(t("thief_green_cornian", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀邪恶短刃蜥蜴", 8150200), t("thief_jr_newtie", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀小蜥蜴", 8190000), t("thief_nest_golem", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀泥人妖", 8190005)),
                        List.of(t("pirate_red_dragon_turtle", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀红海龟龙", 8140701), t("pirate_rexton", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀犀牛龙", 8140702), t("pirate_nest_golem", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀泥人妖", 8190005))
                ),
                List.of(opt(1, TargetType.BOSS, 2, "参与击杀火焰龙", 8180000), opt(2, TargetType.BOSS, 2, "参与击杀天鹰", 8180001), opt(3, TargetType.KILL, 1000, "击杀犀牛龙", 8140702), opt(4, TargetType.KILL, 1000, "击杀犀牛龙怪", 8140703), opt(5, TargetType.MAP, 8, "在神木村龙族地图击杀龙族怪物收集龙巢生命之证", 240030000, 240030100, 240030200, 240040000, 240040100, 240040200, 240040300, 240040400), opt(6, TargetType.KILL, 2500, "击杀任意神木村怪物", 8140101, 8140102, 8140103, 8150200, 8150201, 8140001, 8140002, 8140700, 8140701, 8140702, 8140703), mesoOpt(7, 150000000), opt(8, TargetType.PQ_ANY, 8, "完成任意组队任务"))
        ));
        stages.put(3, new StageConfig(3, 140, 2200, 23000, 14600, 12900, 6800,
                List.of(t("visit_ludi_time", TaskGroup.MAIN_COMMON, TargetType.MAP, 2, "收集智慧水晶", 220070100, 220070200), tPerMob("kill_clocks_common", TaskGroup.MAIN_COMMON, TargetType.KILL, 600, "击杀大立钟和高级大立钟", 8140200, 8140300), t("kill_pap_common", TaskGroup.MAIN_COMMON, TargetType.BOSS, 1, "参与击杀帕普拉图斯", 8500002)),
                jobs(
                        List.of(t("warrior_gigantic_viking", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀大海贼王", 8141100), t("warrior_dual_ghost_pirate", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀绿帽海贼", 7160000), t("warrior_master_death_teddy", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀死灵王", 7130300)),
                        List.of(t("mage_phantom_watch", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀时之鬼爵", 8142000), t("mage_grim_phantom_watch", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀时之鬼王", 8143000), t("mage_master_soul_teddy", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀大恶灵附身的娃娃", 6230500)),
                        List.of(t("bowman_dual_ghost_pirate", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀绿帽海贼", 7160000), t("bowman_spirit_viking", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀大海贼", 8141000), t("bowman_gigantic_viking", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀大海贼王", 8141100)),
                        List.of(t("thief_spirit_viking", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀大海贼", 8141000), t("thief_ghost_pirate", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀蓝帽海贼", 7140000), t("thief_dark_rash", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀黑暗莱西", 7130501)),
                        List.of(t("pirate_rash", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀莱西", 7130500), t("pirate_hobi", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀侏儒怪", 7130600), t("pirate_green_hobi", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀邪恶侏儒怪", 7130601))
                ),
                List.of(opt(1, TargetType.BOSS, 3, "参与击杀帕普拉图斯", 8500002), opt(2, TargetType.KILL, 1500, "击杀蓝帽海贼", 7140000), opt(3, TargetType.KILL, 1200, "击杀大海贼王", 8141100), opt(4, TargetType.KILL, 1500, "击杀大立钟", 8140200), opt(5, TargetType.MAP, 12, "在玩具城和地球防御本部击杀怪物收集玩具塔生命之证", 220000000, 220010000, 220020000, 220030000, 220040000, 221000000, 221020000, 221030000, 221040000, 221041000, 222020000, 222020400), opt(6, TargetType.JUMP_MANUAL, 3, "完成玩具塔相关任务或跳跳任务"), opt(7, TargetType.KILL, 3000, "击杀任意时间裂缝相关怪物", 8140200, 8140300, 7140000, 8141100, 8142000, 8143000), mesoOpt(8, 200000000))
        ));
        stages.put(4, new StageConfig(4, 150, 2375, 27750, 17250, 15075, 7950,
                List.of(t("visit_expedition", TaskGroup.MAIN_COMMON, TargetType.MAP, 2, "收集黑暗水晶", 211042200, 211042300), tPerMob("kill_skel_common", TaskGroup.MAIN_COMMON, TargetType.KILL, 600, "击杀骷髅龙和老骷髅龙", 8190003, 8190004), t("kill_mid_boss_common", TaskGroup.MAIN_COMMON, TargetType.BOSS, 2, "参与击杀任意中阶 Boss", 8800002, 8500002, 8510000, 8520000)),
                jobs(
                        List.of(t("warrior_memory_monk", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀追忆的祭司", 8200001), t("warrior_memory_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀追忆的神官", 8200002), t("warrior_memory_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀追忆的守护兵", 8200003)),
                        List.of(t("mage_qualm_monk", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀后悔的祭司", 8200005), t("mage_qualm_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀后悔的神官", 8200006), t("mage_qualm_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀后悔的守护兵", 8200007)),
                        List.of(t("bowman_bain", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀火焰猎犬", 8140500), t("bowman_birk", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀邪恶绵羊", 8140110), t("bowman_eye_of_time", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀时间之眼", 8200000)),
                        List.of(t("thief_chief_qualm_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀后悔的守护队长", 8200008), t("thief_chief_memory_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀追忆的守护队长", 8200004), t("thief_oblivion_monk", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的祭司", 8200009)),
                        List.of(t("pirate_cold_shark", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀尖鼻鲨鱼", 8150101), t("pirate_green_cornian", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀邪恶短刃蜥蜴", 8150200), t("pirate_dark_cornian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀邪恶双刀蜥蜴", 8150201))
                ),
                List.of(opt(1, TargetType.BOSS, 2, "参与击杀扎昆", 8800002), opt(2, TargetType.BOSS, 5, "参与击杀帕普拉图斯", 8500002), opt(3, TargetType.BOSS, 3, "参与击杀皮亚奴斯", 8510000, 8520000), opt(4, TargetType.BOSS, 1, "参与击杀愤怒的心疤狮王", 9420549), opt(5, TargetType.BOSS, 1, "参与击杀愤怒的暴力熊", 9420544), opt(6, TargetType.KILL, 1800, "击杀骷髅龙", 8190003), opt(7, TargetType.KILL, 1800, "击杀老骷髅龙", 8190004), mesoOpt(8, 300000000))
        ));
        stages.put(5, new StageConfig(5, 160, 2650, 30000, 20200, 17400, 9200,
                List.of(t("visit_deep_sea", TaskGroup.MAIN_COMMON, TargetType.MAP, 3, "收集敏捷水晶", 230040000, 230040420, 230040300), t("kill_deep_common", TaskGroup.MAIN_COMMON, TargetType.KILL, 1400, "击杀骨骸鱼、乌贼怪或致命乌贼怪", 8140600, 8141300, 8142100), t("kill_pianus_common", TaskGroup.MAIN_COMMON, TargetType.BOSS, 1, "参与击杀皮亚奴斯", 8510000, 8520000)),
                jobs(
                        List.of(t("warrior_memory_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀追忆的守护兵", 8200003), t("warrior_chief_memory_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀追忆的守护队长", 8200004), t("warrior_oblivion_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀忘却的守护兵", 8200011)),
                        List.of(t("mage_qualm_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀后悔的守护兵", 8200007), t("mage_chief_qualm_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀后悔的守护队长", 8200008), t("mage_oblivion_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀忘却的神官", 8200010)),
                        List.of(t("bowman_oblivion_monk", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的祭司", 8200009), t("bowman_chief_oblivion_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的守护队长", 8200012), t("bowman_memory_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀追忆的神官", 8200002)),
                        List.of(t("thief_eye_of_time", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀时间之眼", 8200000), t("thief_memory_monk", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀追忆的祭司", 8200001), t("thief_qualm_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀后悔的神官", 8200006)),
                        List.of(t("pirate_shark", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀鲨鱼", 8150100), t("pirate_cold_shark", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀尖鼻鲨鱼", 8150101), t("pirate_bombing_fish_house", TaskGroup.MAIN_JOB, TargetType.KILL, 1000, "击杀刺鳍鱼之屋", 8140555))
                ),
                List.of(opt(1, TargetType.BOSS, 5, "参与击杀皮亚奴斯", 8510000, 8520000), opt(2, TargetType.PQ_PIRATE, 10, "完成海盗船组队任务"), opt(3, TargetType.BOSS, 5, "参与击杀老海盗", 9300119), opt(4, TargetType.KILL, 2000, "击杀骨骸鱼", 8140600), opt(5, TargetType.KILL, 3000, "击杀鲨鱼或尖鼻鲨鱼", 8150100, 8150101), opt(6, TargetType.MAP, 10, "在水下世界隐藏地图击杀怪物收集暗流生命之证", 230040000, 230040100, 230040200, 230040300, 230040400, 230040410, 230040420, 230040430, 230040500, 230040600), opt(7, TargetType.KILL, 4000, "击杀任意深海怪物", 8140555, 8140600, 8141300, 8142100, 8150100, 8150101), mesoOpt(8, 400000000))
        ));
        stages.put(6, new StageConfig(6, 170, 3000, 30000, 23450, 19875, 10550,
                List.of(t("visit_temple", TaskGroup.MAIN_COMMON, TargetType.MAP, 7, "收集幸运水晶", 270010100, 270010200, 270030100, 270030200), tPerMob("kill_temple_priests", TaskGroup.MAIN_COMMON, TargetType.KILL, 600, "击杀后悔的祭司和忘却的祭司", 8200005, 8200009), t("kill_temple_boss", TaskGroup.MAIN_COMMON, TargetType.BOSS, 1, "参与击杀多多、玄冰独角兽或雷卡", 8220004, 8220005, 8220006)),
                jobs(
                        List.of(t("warrior_oblivion_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的神官", 8200010), t("warrior_oblivion_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的守护兵", 8200011), t("warrior_chief_oblivion_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的守护队长", 8200012)),
                        List.of(t("mage_memory_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀追忆的神官", 8200002), t("mage_memory_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀追忆的守护兵", 8200003), t("mage_chief_memory_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀追忆的守护队长", 8200004)),
                        List.of(t("bowman_qualm_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀后悔的神官", 8200006), t("bowman_qualm_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀后悔的守护兵", 8200007), t("bowman_chief_qualm_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀后悔的守护队长", 8200008)),
                        List.of(t("thief_oblivion_monk", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的祭司", 8200009), t("thief_oblivion_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的神官", 8200010), t("thief_eye_of_time", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀时间之眼", 8200000)),
                        List.of(t("pirate_shark", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀鲨鱼", 8150100), t("pirate_cold_shark", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀尖鼻鲨鱼", 8150101), t("pirate_bone_fish", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀骨骸鱼", 8140600))
                ),
                List.of(opt(1, TargetType.BOSS, 3, "参与击杀多多", 8220004), opt(2, TargetType.BOSS, 3, "参与击杀玄冰独角兽", 8220005), opt(3, TargetType.BOSS, 3, "参与击杀雷卡", 8220006), opt(4, TargetType.BOSS, 1, "参与击杀暗黑龙王的灵魂", 8810018), opt(5, TargetType.KILL, 1800, "击杀忘却的守护兵", 8200011), opt(6, TargetType.KILL, 1800, "击杀忘却的守护队长", 8200012), opt(7, TargetType.MAP, 15, "在时间神殿击杀怪物收集回忆生命之证", 270000000, 270010000, 270010100, 270010200, 270010300, 270010400, 270020000, 270020100, 270020200, 270020300, 270020400, 270030000, 270030100, 270030200, 270030300), mesoOpt(8, 500000000))
        ));
        stages.put(7, new StageConfig(7, 180, 3400, 30000, 27000, 22500, 12000,
                List.of(t("visit_final", TaskGroup.MAIN_COMMON, TargetType.MAP, 5, "收集五种水晶各10个", 240040510, 240040511, 240040600), tPerMob("kill_final_common", TaskGroup.MAIN_COMMON, TargetType.KILL, 1200, "击杀老骷髅龙和忘却的守护队长", 8190004, 8200012), t("kill_mid_boss_final", TaskGroup.MAIN_COMMON, TargetType.BOSS, 5, "完成任意中阶 Boss 参与击杀", 8800002, 8500002, 8510000, 8520000, 9420549, 9420544)),
                jobs(
                        List.of(t("warrior_skelegon", TaskGroup.MAIN_JOB, TargetType.KILL, 2000, "击杀骷髅龙", 8190003), t("warrior_oblivion_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的守护兵", 8200011), t("warrior_chief_oblivion_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的守护队长", 8200012)),
                        List.of(t("mage_oblivion_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 2000, "击杀忘却的神官", 8200010), t("mage_oblivion_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀忘却的守护兵", 8200011), t("mage_qualm_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀后悔的守护兵", 8200007)),
                        List.of(t("bowman_brexton", TaskGroup.MAIN_JOB, TargetType.KILL, 2000, "击杀犀牛龙怪", 8140703), t("bowman_gigantic_viking", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀大海贼王", 8141100), t("bowman_chief_qualm_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀后悔的守护队长", 8200008)),
                        List.of(t("thief_qualm_monk_trainee", TaskGroup.MAIN_JOB, TargetType.KILL, 2000, "击杀后悔的神官", 8200006), t("thief_memory_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀追忆的守护兵", 8200003), t("thief_chief_memory_guardian", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀追忆的守护队长", 8200004)),
                        List.of(t("pirate_shark", TaskGroup.MAIN_JOB, TargetType.KILL, 2000, "击杀鲨鱼", 8150100), t("pirate_cold_shark", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀尖鼻鲨鱼", 8150101), t("pirate_bone_fish", TaskGroup.MAIN_JOB, TargetType.KILL, 1500, "击杀骨骸鱼", 8140600))
                ),
                List.of(opt(1, TargetType.BOSS, 3, "参与击杀暗黑龙王的灵魂", 8810018), opt(2, TargetType.BOSS, 1, "参与击杀时间的宠儿－品克缤", 8820001), opt(3, TargetType.BOSS, 5, "参与击杀扎昆", 8800002), opt(4, TargetType.BOSS, 8, "参与击杀帕普拉图斯", 8500002), opt(5, TargetType.BOSS, 8, "参与击杀多多、玄冰独角兽、雷卡", 8220004, 8220005, 8220006), opt(6, TargetType.KILL, 3000, "击杀老骷髅龙", 8190004), opt(7, TargetType.KILL, 3000, "击杀忘却的守护队长", 8200012), mesoOpt(8, 800000000))
        ));
        return stages;
    }

    private record ProgressRow(int currentCount, int requiredCount, boolean selected, boolean active, boolean completed,
                               int taskOrder) {
    }

    private record RewardLog(int id, int stage, int beforeMaxHp, int beforeMaxMp, int beforeHp, int beforeMp) {
    }

    private static State loadState(int characterId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return loadState(con, characterId);
        } catch (SQLException e) {
            log.warn("load hp challenge state failed", e);
            return null;
        }
    }

    private static State loadState(Connection con, int characterId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT character_id, route_locked, current_stage, highest_rewarded_stage, status
                FROM hp_challenge_state WHERE character_id = ?
                """)) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new State(rs.getInt("character_id"), rs.getBoolean("route_locked"), rs.getInt("current_stage"),
                        rs.getInt("highest_rewarded_stage"), rs.getString("status"));
            }
        }
    }

    private static Map<String, ProgressRow> loadProgress(int characterId, int stage, TaskGroup group) {
        try (Connection con = DatabaseConnection.getConnection()) {
            Map<String, ProgressRow> rows = new HashMap<>();
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT task_key, current_count, required_count, selected, active, completed, task_order
                    FROM hp_challenge_progress
                    WHERE character_id = ? AND stage = ? AND task_group = ?
                    """)) {
                ps.setInt(1, characterId);
                ps.setInt(2, stage);
                ps.setString(3, group.code);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.put(rs.getString("task_key"), new ProgressRow(rs.getInt("current_count"),
                                rs.getInt("required_count"), rs.getBoolean("selected"), rs.getBoolean("active"),
                                rs.getBoolean("completed"), rs.getInt("task_order")));
                    }
                }
            }
            return rows;
        } catch (SQLException e) {
            log.warn("load hp challenge progress failed", e);
            return Map.of();
        }
    }

    private static ProgressRow loadProgressRow(Connection con, int characterId, int stage, TaskGroup group, String taskKey) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT current_count, required_count, selected, active, completed, task_order
                FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ? AND task_group = ? AND task_key = ?
                """)) {
            ps.setInt(1, characterId);
            ps.setInt(2, stage);
            ps.setString(3, group.code);
            ps.setString(4, taskKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ProgressRow(rs.getInt("current_count"), rs.getInt("required_count"),
                        rs.getBoolean("selected"), rs.getBoolean("active"), rs.getBoolean("completed"),
                        rs.getInt("task_order"));
            }
        }
    }

    private static ActiveTask loadActiveTask(Character chr, int stage) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return loadActiveTask(con, chr, stage);
        } catch (SQLException e) {
            log.warn("load hp challenge active task failed", e);
            return null;
        }
    }

    private static ActiveTask loadActiveTask(Connection con, Character chr, int stageNo) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT task_group, task_key, current_count, required_count, selected, active, completed, task_order
                FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ? AND active = 1 AND completed = 0
                ORDER BY task_order, id
                LIMIT 1
                """)) {
            ps.setInt(1, chr.getId());
            ps.setInt(2, stageNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                TaskGroup group = TaskGroup.valueOf(rs.getString("task_group").toUpperCase(Locale.ROOT));
                String taskKey = rs.getString("task_key");
                Task task = orderedStageTasks(chr, stage(stageNo)).stream()
                        .filter(t -> t.group() == group && t.key().equals(taskKey))
                        .findFirst()
                        .orElse(null);
                if (task == null) {
                    return null;
                }
                ProgressRow row = new ProgressRow(rs.getInt("current_count"), rs.getInt("required_count"),
                        rs.getBoolean("selected"), rs.getBoolean("active"), rs.getBoolean("completed"),
                        rs.getInt("task_order"));
                return new ActiveTask(task, row);
            }
        }
    }

    private static void activateNextTaskIfNeeded(Connection con, Character chr, int stageNo) throws SQLException {
        if (activeCount(con, chr.getId(), stageNo) > 0) {
            return;
        }
        StageConfig stage = stage(stageNo);
        for (Task task : orderedMainTasks(chr, stage)) {
            ProgressRow row = loadProgressRow(con, chr.getId(), stageNo, task.group(), task.key());
            if (row != null && !row.completed) {
                activateTask(con, chr.getId(), stageNo, task);
                return;
            }
        }
        Task selectedOptional = firstSelectedIncompleteOptional(con, chr, stageNo);
        if (selectedOptional != null) {
            activateTask(con, chr.getId(), stageNo, selectedOptional);
        }
    }

    private static void activateTask(Connection con, int characterId, int stage, Task task) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                UPDATE hp_challenge_progress
                SET active = 1, accepted_at = COALESCE(accepted_at, CURRENT_TIMESTAMP)
                WHERE character_id = ? AND stage = ? AND task_group = ? AND task_key = ?
                    AND completed = 0
                """)) {
            ps.setInt(1, characterId);
            ps.setInt(2, stage);
            ps.setString(3, task.group().code);
            ps.setString(4, task.key());
            ps.executeUpdate();
        }
    }

    private static Task firstSelectedIncompleteOptional(Connection con, Character chr, int stageNo) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT task_key
                FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ? AND task_group = 'optional'
                    AND selected = 1 AND completed = 0
                ORDER BY task_order, id
                LIMIT 1
                """)) {
            ps.setInt(1, chr.getId());
            ps.setInt(2, stageNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String taskKey = rs.getString("task_key");
                return stage(stageNo).optionalTasks().stream()
                        .filter(task -> task.key().equals(taskKey))
                        .findFirst()
                        .orElse(null);
            }
        }
    }

    private static LifeProofOptionalProgress selectedOptionalForOrder(Connection con, Character chr, int stageNo,
                                                                      int taskOrder) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT task_key, current_count, required_count, active, completed, task_order
                FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ? AND task_group = 'optional'
                    AND selected = 1 AND task_order = ?
                ORDER BY id
                LIMIT 1
                """)) {
            ps.setInt(1, chr.getId());
            ps.setInt(2, stageNo);
            ps.setInt(3, taskOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String taskKey = rs.getString("task_key");
                Task task = stage(stageNo).optionalTasks().stream()
                        .filter(candidate -> candidate.key().equals(taskKey))
                        .findFirst()
                        .orElse(null);
                if (task == null) {
                    return null;
                }
                return new LifeProofOptionalProgress(task, rs.getInt("current_count"),
                        rs.getInt("required_count"), rs.getBoolean("active"), rs.getBoolean("completed"),
                        rs.getInt("task_order"));
            }
        }
    }

    private static String nextStepText(Connection con, Character chr, int stageNo) throws SQLException {
        ActiveTask activeTask = loadActiveTask(con, chr, stageNo);
        if (activeTask != null) {
            return "下一步：" + activeTask.task().description();
        }
        if (isOptionalChoiceAvailable(con, chr.getId(), stageNo)) {
            return "下一步：选择第 " + (selectedOptionalCount(con, chr.getId(), stageNo) + 1) + " 个附加挑战。";
        }
        if (incompleteCount(con, chr.getId(), stageNo, TaskGroup.MAIN_COMMON) == 0
                && incompleteCount(con, chr.getId(), stageNo, TaskGroup.MAIN_JOB) == 0
                && completedSelectedOptionalCount(con, chr.getId(), stageNo) >= OPTIONAL_REQUIRED_COUNT) {
            return "下一步：领取阶段奖励。";
        }
        return "下一步：请重新打开导师菜单查看当前任务。";
    }

    static List<Task> orderedStageTasks(Character chr, StageConfig stage) {
        List<Task> tasks = new ArrayList<>(orderedMainTasks(chr, stage));
        tasks.addAll(stage.optionalTasks());
        return tasks;
    }

    static List<Task> orderedMainTasks(Character chr, StageConfig stage) {
        List<Task> tasks = new ArrayList<>();
        tasks.addAll(orderedCommonTasks(chr, stage));
        tasks.addAll(stage.jobTasks().getOrDefault(branch(chr.getJob()), List.of()));
        return tasks;
    }

    static List<Task> orderedCommonTasks(Character chr, StageConfig stage) {
        if (stage.stage() != 1) {
            return stage.commonTasks();
        }
        List<Task> tasks = new ArrayList<>();
        for (Integer npcId : instructorVisitOrderForJob(chr.getJob())) {
            stage.commonTasks().stream()
                    .filter(task -> task.targetType() == TargetType.NPC_TALK && task.primaryTarget() == npcId)
                    .findFirst()
                    .ifPresent(tasks::add);
        }
        for (Task task : stage.commonTasks()) {
            if (task.targetType() != TargetType.NPC_TALK) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    static List<Integer> instructorVisitOrderForJob(Job job) {
        return instructorVisitOrderForInstructor(instructorNpcForJob(job));
    }

    static List<Integer> instructorVisitOrderForInstructor(int ownInstructor) {
        List<Integer> order = new ArrayList<>();
        for (Task task : stage(1).commonTasks()) {
            if (task.targetType() == TargetType.NPC_TALK && task.primaryTarget() != ownInstructor) {
                order.add(task.primaryTarget());
            }
        }
        for (Task task : stage(1).commonTasks()) {
            if (task.targetType() == TargetType.NPC_TALK && task.primaryTarget() == ownInstructor) {
                order.add(task.primaryTarget());
            }
        }
        return order;
    }

    private static int selectedOptionalCount(Connection con, int characterId, int stage) throws SQLException {
        return count(con, """
                SELECT COUNT(*) FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ? AND task_group = 'optional' AND selected = 1
                """, characterId, stage);
    }

    static int selectedLifeProofOptionalCount(Character chr, int stage) {
        if (chr == null) {
            return 0;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            return selectedOptionalCount(con, chr.getId(), stage);
        } catch (SQLException e) {
            log.warn("count life proof selected optional failed", e);
            return 0;
        }
    }

    static boolean isLifeProofOptionalSelected(Character chr, int stage, int optionNo) {
        if (chr == null || optionNo <= 0) {
            return false;
        }
        Task task = optionalTask(stage, optionNo);
        if (task == null) {
            return false;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            return isTaskSelected(con, chr.getId(), stage, task.key());
        } catch (SQLException e) {
            log.warn("check life proof optional selected failed", e);
            return false;
        }
    }

    static Task selectLifeProofOptional(Character chr, int stage, int optionNo, int taskOrder) {
        if (chr == null || taskOrder <= 0 || taskOrder > OPTIONAL_REQUIRED_COUNT) {
            return null;
        }
        Task task = optionalTask(stage, optionNo);
        if (task == null) {
            return null;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                ensureProgressRows(con, chr, stage);
                lockStageProgress(con, chr.getId(), stage);
                if (selectedOptionalCount(con, chr.getId(), stage) >= OPTIONAL_REQUIRED_COUNT
                        || isTaskSelected(con, chr.getId(), stage, task.key())
                        || selectedOptionalForOrder(con, chr, stage, taskOrder) != null) {
                    con.rollback();
                    return null;
                }
                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE hp_challenge_progress
                        SET selected = 1, active = 1, completed = 0, task_order = ?,
                            current_count = 0, required_count = ?, accepted_at = CURRENT_TIMESTAMP,
                            completed_at = NULL
                        WHERE character_id = ? AND stage = ? AND task_group = 'optional' AND task_key = ?
                        """)) {
                    ps.setInt(1, taskOrder);
                    ps.setInt(2, task.requiredCount());
                    ps.setInt(3, chr.getId());
                    ps.setInt(4, stage);
                    ps.setString(5, task.key());
                    if (ps.executeUpdate() <= 0) {
                        con.rollback();
                        return null;
                    }
                }
                con.commit();
                return task;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warn("select life proof optional failed", e);
            return null;
        }
    }

    static LifeProofOptionalProgress selectedLifeProofOptional(Character chr, int stage, int taskOrder) {
        if (chr == null || taskOrder <= 0) {
            return null;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            return selectedOptionalForOrder(con, chr, stage, taskOrder);
        } catch (SQLException e) {
            log.warn("load life proof selected optional failed", e);
            return null;
        }
    }

    static boolean setLifeProofOptionalProgress(Character chr, int stage, int taskOrder, int currentCount) {
        if (chr == null || taskOrder <= 0) {
            return false;
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     UPDATE hp_challenge_progress
                     SET current_count = LEAST(required_count, GREATEST(0, ?))
                     WHERE character_id = ? AND stage = ? AND task_group = 'optional'
                         AND selected = 1 AND task_order = ? AND completed = 0
                     """)) {
            ps.setInt(1, currentCount);
            ps.setInt(2, chr.getId());
            ps.setInt(3, stage);
            ps.setInt(4, taskOrder);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.warn("set life proof optional progress failed", e);
            return false;
        }
    }

    static boolean incrementLifeProofOptionalProgress(Character chr, int stage, int taskOrder,
                                                      TargetType eventType, int eventId, String eventName) {
        if (chr == null || taskOrder <= 0) {
            return false;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                lockStageProgress(con, chr.getId(), stage);
                LifeProofOptionalProgress selected = selectedOptionalForOrder(con, chr, stage, taskOrder);
                if (selected == null || selected.completed() || !selected.active()
                        || !matches(selected.task(), eventType, eventId, eventName)) {
                    con.rollback();
                    return false;
                }
                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE hp_challenge_progress
                        SET current_count = LEAST(required_count, current_count + 1)
                        WHERE character_id = ? AND stage = ? AND task_group = 'optional'
                            AND selected = 1 AND task_order = ? AND active = 1 AND completed = 0
                        """)) {
                    ps.setInt(1, chr.getId());
                    ps.setInt(2, stage);
                    ps.setInt(3, taskOrder);
                    if (ps.executeUpdate() <= 0) {
                        con.rollback();
                        return false;
                    }
                }
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warn("increment life proof optional progress failed", e);
            return false;
        }
    }

    static boolean completeLifeProofOptional(Character chr, int stage, int taskOrder) {
        if (chr == null || taskOrder <= 0) {
            return false;
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     UPDATE hp_challenge_progress
                     SET current_count = required_count, completed = 1, active = 0,
                         completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP)
                     WHERE character_id = ? AND stage = ? AND task_group = 'optional'
                         AND selected = 1 AND task_order = ?
                     """)) {
            ps.setInt(1, chr.getId());
            ps.setInt(2, stage);
            ps.setInt(3, taskOrder);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.warn("complete life proof optional failed", e);
            return false;
        }
    }

    static boolean clearLifeProofOptionalSelection(Character chr, int stage, int optionNo) {
        if (chr == null || optionNo <= 0) {
            return false;
        }
        Task task = optionalTask(stage, optionNo);
        if (task == null) {
            return false;
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     UPDATE hp_challenge_progress
                     SET selected = 0, active = 0, completed = 0, task_order = 0,
                         current_count = 0, completed_at = NULL
                     WHERE character_id = ? AND stage = ? AND task_group = 'optional' AND task_key = ?
                     """)) {
            ps.setInt(1, chr.getId());
            ps.setInt(2, stage);
            ps.setString(3, task.key());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.warn("clear life proof optional selection failed", e);
            return false;
        }
    }

    static Set<Integer> activeLifeProofRewardStages(Character chr) {
        if (chr == null) {
            return Set.of();
        }
        Set<Integer> stages = new java.util.HashSet<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT stage FROM hp_challenge_reward_log
                     WHERE character_id = ? AND reverted = 0
                     """)) {
            ps.setInt(1, chr.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stages.add(rs.getInt("stage"));
                }
            }
        } catch (SQLException e) {
            log.warn("load life proof reward stages failed", e);
            return Set.of();
        }
        return stages;
    }

    static Set<Integer> lifeProofStateCompletedStages(Character chr) {
        if (chr == null) {
            return Set.of();
        }
        State state = loadState(chr.getId());
        return lifeProofStateCompletedStages(state == null ? 0 : state.currentStage(),
                state == null ? 0 : state.highestRewardedStage());
    }

    static Set<Integer> lifeProofStateCompletedStages(int currentStage, int highestRewardedStage) {
        Set<Integer> stages = new java.util.HashSet<>();
        int highestFromReward = Math.max(0, Math.min(7, highestRewardedStage));
        for (int stage = 1; stage <= highestFromReward; stage++) {
            stages.add(stage);
        }
        int highestBeforeCurrent = Math.max(0, Math.min(7, currentStage - 1));
        for (int stage = 1; stage <= highestBeforeCurrent; stage++) {
            stages.add(stage);
        }
        return stages;
    }

    private static int activeCount(Connection con, int characterId, int stage) throws SQLException {
        return count(con, """
                SELECT COUNT(*) FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ? AND active = 1 AND completed = 0
                """, characterId, stage);
    }

    private static int nextOptionalSlot(int characterId, int stage) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return selectedOptionalCount(con, characterId, stage) + 1;
        } catch (SQLException e) {
            log.warn("load hp challenge optional slot failed", e);
            return 1;
        }
    }

    private static boolean isOptionalChoiceAvailable(int characterId, int stage) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return isOptionalChoiceAvailable(con, characterId, stage);
        } catch (SQLException e) {
            log.warn("check hp challenge optional choice failed", e);
            return false;
        }
    }

    private static boolean isOptionalChoiceAvailable(Connection con, int characterId, int stage) throws SQLException {
        int selected = selectedOptionalCount(con, characterId, stage);
        return activeCount(con, characterId, stage) == 0
                && incompleteCount(con, characterId, stage, TaskGroup.MAIN_COMMON) == 0
                && incompleteCount(con, characterId, stage, TaskGroup.MAIN_JOB) == 0
                && selected < OPTIONAL_REQUIRED_COUNT
                && selected == completedSelectedOptionalCount(con, characterId, stage);
    }

    private static int nextTaskOrder(Connection con, int characterId, int stage) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT COALESCE(MAX(task_order), 0) + 1
                FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ?
                """)) {
            ps.setInt(1, characterId);
            ps.setInt(2, stage);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    private static int completedSelectedOptionalCount(Connection con, int characterId, int stage) throws SQLException {
        return count(con, """
                SELECT COUNT(*) FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ? AND task_group = 'optional' AND selected = 1 AND completed = 1
                """, characterId, stage);
    }

    private static int incompleteCount(Connection con, int characterId, int stage, TaskGroup group) throws SQLException {
        return count(con, """
                SELECT COUNT(*) FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ? AND task_group = ? AND completed = 0
                """, characterId, stage, group.code);
    }

    private static int count(Connection con, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void lockStageProgress(Connection con, int characterId, int stage) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT id
                FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ?
                ORDER BY id
                FOR UPDATE
                """)) {
            ps.setInt(1, characterId);
            ps.setInt(2, stage);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Consume all rows to acquire the row locks for this stage.
                }
            }
        }
    }

    private static boolean isTaskSelected(Connection con, int characterId, int stage, String taskKey) throws SQLException {
        return count(con, """
                SELECT COUNT(*) FROM hp_challenge_progress
                WHERE character_id = ? AND stage = ? AND task_group = 'optional' AND task_key = ? AND selected = 1
                """, characterId, stage, taskKey) > 0;
    }

    private static boolean insertUniqueEvent(Connection con, int characterId, int stage, Task task, String eventKey) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO hp_challenge_event_log (character_id, stage, task_group, task_key, event_key)
                SELECT ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM hp_challenge_event_log
                    WHERE character_id = ? AND stage = ? AND task_group = ? AND task_key = ? AND event_key = ?
                )
                """)) {
            ps.setInt(1, characterId);
            ps.setInt(2, stage);
            ps.setString(3, task.group().code);
            ps.setString(4, task.key());
            ps.setString(5, eventKey);
            ps.setInt(6, characterId);
            ps.setInt(7, stage);
            ps.setString(8, task.group().code);
            ps.setString(9, task.key());
            ps.setString(10, eventKey);
            return ps.executeUpdate() > 0;
        }
    }

    private static boolean completeTask(Connection con, int characterId, int stage, TaskGroup group, String taskKey,
                                        boolean requireActive) throws SQLException {
        String activeClause = requireActive ? " AND active = 1" : "";
        try (PreparedStatement ps = con.prepareStatement("""
                UPDATE hp_challenge_progress
                SET current_count = required_count, completed = 1, active = 0, completed_at = CURRENT_TIMESTAMP
                WHERE character_id = ? AND stage = ? AND task_group = ? AND task_key = ?
                    AND completed = 0
                """ + activeClause)) {
            ps.setInt(1, characterId);
            ps.setInt(2, stage);
            ps.setString(3, group.code);
            ps.setString(4, taskKey);
            return ps.executeUpdate() > 0;
        }
    }

    static boolean hasActiveReward(int characterId, int stage) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return count(con, """
                    SELECT COUNT(*) FROM hp_challenge_reward_log
                    WHERE character_id = ? AND stage = ? AND reverted = 0
                    """, characterId, stage) > 0;
        } catch (SQLException e) {
            log.warn("check hp challenge reward failed", e);
            return true;
        }
    }

    private static RewardLog latestRewardLog(Connection con, int characterId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT id, stage, before_maxhp, before_maxmp, before_hp, before_mp
                FROM hp_challenge_reward_log
                WHERE character_id = ? AND reverted = 0
                ORDER BY stage DESC, id DESC
                LIMIT 1
                """)) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new RewardLog(rs.getInt("id"), rs.getInt("stage"), rs.getInt("before_maxhp"),
                        rs.getInt("before_maxmp"), rs.getInt("before_hp"), rs.getInt("before_mp"));
            }
        }
    }

    private static boolean hasHigherActiveReward(Connection con, int characterId, int stage) throws SQLException {
        return count(con, """
                SELECT COUNT(*) FROM hp_challenge_reward_log
                WHERE character_id = ? AND stage > ? AND reverted = 0
                """, characterId, stage) > 0;
    }

    static void logGm(Character operator, int targetCharacterId, String action, String detail) {
        if (operator == null) {
            return;
        }
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     INSERT INTO hp_challenge_gm_log (character_id, operator_id, operator, action, detail)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            ps.setInt(1, targetCharacterId);
            ps.setInt(2, operator.getId());
            ps.setString(3, operator.getName());
            ps.setString(4, action);
            ps.setString(5, detail);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("write hp challenge gm log failed", e);
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof Integer v) {
                ps.setInt(i + 1, v);
            } else if (param instanceof String v) {
                ps.setString(i + 1, v);
            } else if (param instanceof Boolean v) {
                ps.setBoolean(i + 1, v);
            } else {
                ps.setObject(i + 1, param);
            }
        }
    }
}

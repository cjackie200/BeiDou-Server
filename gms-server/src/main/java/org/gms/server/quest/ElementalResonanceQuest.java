package org.gms.server.quest;

import org.gms.client.Character;
import org.gms.client.Job;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.constants.inventory.ItemConstants;
import org.gms.server.ItemInformationProvider;
import org.gms.server.quest.hook.InteractionHookAction;
import org.gms.server.quest.hook.InteractionHookContext;
import org.gms.server.quest.hook.InteractionHookPackets;
import org.gms.server.quest.hook.InteractionHookProgressEntry;
import org.gms.util.PacketCreator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ElementalResonanceQuest {
    public static final int NPC_ID = 1032001;
    public static final short FIRST_QUEST_ID = 29991;
    public static final short LAST_QUEST_ID = 29995;
    public static final short FIRST_BRIDGE_QUEST_ID = 29950;
    public static final short LAST_BRIDGE_QUEST_ID = 29970;

    private static final int VIRTUAL_PROGRESS_KEY = 0;
    private static final String PROGRESS_NOT_READY = "000";
    private static final String PROGRESS_READY = "001";

    private static final int BLACK_CRYSTAL = 4021008;
    private static final int STAR_ROCK = 4021009;

    private static final Element[] ELEMENTS = {
            new Element("火", "火焰", "星焰"),
            new Element("毒", "毒雾", "幽梦"),
            new Element("冰", "寒冰", "霜月"),
            new Element("雷", "雷电", "金霆"),
            new Element("圣", "圣光", "白曜")
    };

    private static final Stage[] STAGES = {
            new Stage(1, FIRST_QUEST_ID, 70, "元素共鸣:初声",
                    new int[]{1372035, 1372036, 1372037, 1372038, 1372047},
                    List.of(boss(2220000, 4033012), boss(3220000, 4033013), boss(6130101, 4033014)),
                    List.of(req(4000059, 100), req(4000060, 100), req(4000061, 100), req(STAR_ROCK, 1)),
                    2_000_000,
                    List.of(),
                    0,
                    (short) 29950),
            new Stage(2, (short) 29992, 100, "元素共鸣:回响",
                    new int[]{1382045, 1382046, 1382047, 1382048, 1382061},
                    List.of(boss(5220000, 4033015), boss(5220002, 4033016), boss(5220003, 4033017)),
                    List.of(req(4000144, 100), req(4000146, 100), req(4000176, 20), req(BLACK_CRYSTAL, 2), req(STAR_ROCK, 1)),
                    8_000_000,
                    List.of(req(BLACK_CRYSTAL, 1), req(STAR_ROCK, 1)),
                    2_000_000,
                    (short) 29954),
            new Stage(3, (short) 29993, 130, "元素共鸣:裂隙",
                    new int[]{1372039, 1372040, 1372041, 1372042, 1372048},
                    List.of(boss(6220000, 4033018), boss(6300005, 4033019), boss(8130100, 4033020)),
                    List.of(req(BLACK_CRYSTAL, 5), req(STAR_ROCK, 3)),
                    18_000_000,
                    List.of(req(BLACK_CRYSTAL, 3), req(STAR_ROCK, 2)),
                    5_000_000,
                    (short) 29958),
            new Stage(4, (short) 29994, 160, "元素共鸣:风暴",
                    new int[]{1382049, 1382050, 1382051, 1382052, 1382063},
                    List.of(boss(8150000, 4033021), boss(8180000, 4033022), boss(8180001, 4033023)),
                    List.of(req(4000235, 3), req(4000243, 3), req(4001084, 1), req(BLACK_CRYSTAL, 8), req(STAR_ROCK, 5)),
                    30_000_000,
                    List.of(req(BLACK_CRYSTAL, 5), req(STAR_ROCK, 3)),
                    10_000_000,
                    (short) 29962),
            new Stage(5, (short) 29995, 190, "元素共鸣:终章",
                    new int[]{1372059, 1372060, 1372061, 1372062, 1372063},
                    List.of(boss(8500002, 4033024), boss(8800002, 4033025),
                            boss(8810018, 4033026), boss(8820001, 4033027)),
                    List.of(req(4001083, 1), req(4001084, 1), req(4000235, 5), req(4000243, 5), req(BLACK_CRYSTAL, 10), req(STAR_ROCK, 10)),
                    80_000_000,
                    List.of(req(BLACK_CRYSTAL, 10), req(STAR_ROCK, 5)),
                    30_000_000,
                    (short) 29966)
    };

    private static final Set<Integer> ELEMENTAL_WEAPONS = new HashSet<>();
    private static final Set<Integer> QUEST_ITEMS = new HashSet<>();

    static {
        for (Stage stage : STAGES) {
            for (int itemId : stage.rewardItemIds) {
                ELEMENTAL_WEAPONS.add(itemId);
                QUEST_ITEMS.add(itemId);
            }
            for (BossTarget bossTarget : stage.bossTargets) {
                QUEST_ITEMS.add(bossTarget.tokenId());
            }
            for (Requirement requirement : stage.baseRequirements) {
                QUEST_ITEMS.add(requirement.itemId());
            }
            for (Requirement requirement : stage.switchRequirements) {
                QUEST_ITEMS.add(requirement.itemId());
            }
        }
    }

    private ElementalResonanceQuest() {
    }

    public static boolean isQuestId(int questId) {
        return getStepByQuestId(questId) != null;
    }

    public static boolean isHiddenBridgeQuestId(int questId) {
        return questId >= FIRST_BRIDGE_QUEST_ID && questId <= LAST_BRIDGE_QUEST_ID;
    }

    public static boolean isElementalWeapon(int itemId) {
        return ELEMENTAL_WEAPONS.contains(itemId);
    }

    public static boolean isQuestRelevantItem(int itemId) {
        return QUEST_ITEMS.contains(itemId);
    }

    public static boolean isBossTokenItem(int itemId) {
        for (Stage stage : STAGES) {
            for (BossTarget bossTarget : stage.bossTargets) {
                if (bossTarget.tokenId() == itemId) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isBossTokenForMonster(int mobId, int itemId, int questId) {
        StepRef ref = getStepByQuestId(questId);
        if (ref == null || ref.type != StepType.BOSS) {
            return false;
        }
        BossTarget bossTarget = ref.bossTarget();
        return bossTarget != null && bossTarget.mobId() == mobId && bossTarget.tokenId() == itemId;
    }

    public static boolean isAllowedBossTokenDrop(Character chr, int mobId, int itemId, int questId) {
        if (!isBossTokenItem(itemId)) {
            return true;
        }
        StepRef ref = getStepByQuestId(questId);
        if (ref == null || ref.type != StepType.BOSS) {
            return false;
        }
        BossTarget current = ref.bossTarget();
        return current != null
                && current.mobId() == mobId
                && current.tokenId() == itemId
                && needBossToken(chr, itemId, questId);
    }

    public static boolean needBossToken(Character chr, int itemId, int questId) {
        if (!isBossTokenItem(itemId)) {
            return true;
        }
        StepRef ref = getStepByQuestId(questId);
        if (ref == null || ref.type != StepType.BOSS) {
            return false;
        }
        if (chr == null || chr.getQuestStatus(questId) != QuestStatus.Status.STARTED.getId()) {
            return false;
        }
        BossTarget current = ref.bossTarget();
        return current != null
                && current.tokenId() == itemId
                && chr.getItemQuantity(itemId, false) < 1;
    }

    public static void syncQuestStateIfRelevant(Character chr, int itemId) {
        if (isQuestRelevantItem(itemId)) {
            syncQuestState(chr);
        }
    }

    public static void syncQuestStateIfMesoRelevant(Character chr) {
        if (chr == null) {
            return;
        }
        for (int questId : getAllQuestIds()) {
            if (chr.getQuestStatus(questId) == QuestStatus.Status.STARTED.getId()) {
                syncQuestState(chr);
                return;
            }
        }
    }

    public static List<Integer> getAllQuestIds() {
        List<Integer> questIds = new ArrayList<>();
        for (short questId = FIRST_BRIDGE_QUEST_ID; questId <= LAST_BRIDGE_QUEST_ID; questId++) {
            questIds.add((int) questId);
        }
        for (short questId = FIRST_QUEST_ID; questId <= LAST_QUEST_ID; questId++) {
            questIds.add((int) questId);
        }
        return questIds;
    }

    public static List<Integer> getHookQuestIds(Character chr) {
        if (chr == null) {
            return getAllQuestIds();
        }
        syncQuestStateSilently(chr);
        List<Integer> questIds = new ArrayList<>();
        for (int questId : getAllQuestIds()) {
            if (chr.getQuestStatus(questId) != QuestStatus.Status.NOT_STARTED.getId()) {
                questIds.add(questId);
            }
        }
        resolveCurrentQuestId(chr).ifPresent(questId -> {
            if (!questIds.contains(questId)) {
                questIds.add(questId);
            }
        });
        return questIds;
    }

    public static Optional<Integer> resolveNpcHook(Character chr, int npcId) {
        if (npcId != NPC_ID) {
            return Optional.empty();
        }
        return resolveCurrentQuestId(chr);
    }

    public static Stage getStage(int stageIndex) {
        if (stageIndex < 1 || stageIndex > STAGES.length) {
            return null;
        }
        return STAGES[stageIndex - 1];
    }

    public static Stage getStageByQuestId(int questId) {
        StepRef ref = getStepByQuestId(questId);
        return ref == null ? null : ref.stage;
    }

    private static StepRef getStepByQuestId(int questId) {
        for (Stage stage : STAGES) {
            for (int i = 0; i < stage.bossTargets.size(); i++) {
                short stepQuestId = stage.bossBridgeQuestId(i);
                if (questId == stepQuestId) {
                    return new StepRef(stage, StepType.BOSS, i, stepQuestId);
                }
            }
            if (questId == stage.materialBridgeQuestId()) {
                return new StepRef(stage, StepType.BASE_MATERIALS, -1, stage.materialBridgeQuestId());
            }
            if (questId == stage.questId) {
                return new StepRef(stage, StepType.REWARD, -1, stage.questId);
            }
        }
        return null;
    }

    public static Optional<Integer> resolveCurrentQuestId(Character chr) {
        if (chr == null || !isEligibleMage(chr)) {
            return Optional.empty();
        }

        Stage stage = resolveEligibleStage(chr);
        if (stage == null) {
            return Optional.empty();
        }
        Optional<Integer> current = resolveCurrentQuestId(chr, stage);
        if (current.isPresent()) {
            return current;
        }
        StaffState staffState = getStaffState(chr);
        if (staffState.total() == 0 && stage.index == 1) {
            return Optional.of((int) stage.firstStepQuestId());
        }
        return Optional.empty();
    }

    private static Stage resolveEligibleStage(Character chr) {
        StaffState staffState = getStaffState(chr);
        if (staffState.total() == 0) {
            Stage firstStage = STAGES[0];
            return chr.getLevel() >= firstStage.requiredLevel ? firstStage : null;
        }

        StaffInfo current = staffState.current();
        if (current == null || current.stage() >= STAGES.length) {
            return null;
        }

        Stage nextStage = STAGES[current.stage()];
        if (chr.getLevel() < nextStage.requiredLevel) {
            return null;
        }
        return nextStage;
    }

    private static Optional<Integer> resolveCurrentQuestId(Character chr, Stage stage) {
        for (short questId : stage.stepQuestIds()) {
            if (chr.getQuestStatus(questId) != QuestStatus.Status.COMPLETED.getId()) {
                return Optional.of((int) questId);
            }
        }
        return Optional.empty();
    }

    public static void syncQuestState(Character chr) {
        syncQuestState(chr, true);
    }

    public static void syncQuestStateSilently(Character chr) {
        syncQuestState(chr, false);
    }

    private static void syncQuestState(Character chr, boolean announce) {
        if (chr == null) {
            return;
        }

        StaffState staffState = getStaffState(chr);
        if (staffState.total() == 0) {
            syncNoStaffState(chr, announce);
            syncClientQuestEntrypoints(chr, announce);
            return;
        }

        StaffInfo current = staffState.current();
        int currentStage = current == null ? 0 : current.stage();
        for (Stage stage : STAGES) {
            if (stage.index <= currentStage) {
                setStageCompleted(chr, stage, announce);
                continue;
            }

            if (stage.index == currentStage + 1 && chr.getLevel() >= stage.requiredLevel && isEligibleMage(chr)) {
                syncStageInProgress(chr, stage, announce);
                continue;
            }

            resetStageQuests(chr, stage, announce);
        }
        syncClientQuestEntrypoints(chr, announce);
    }

    private static void syncNoStaffState(Character chr, boolean announce) {
        for (Stage stage : STAGES) {
            if (stage.index == 1 && chr.getLevel() >= stage.requiredLevel && isEligibleMage(chr)) {
                Optional<Integer> current = resolveCurrentQuestId(chr, stage);
                if (current.isEmpty()) {
                    resetStageQuests(chr, stage, announce);
                    continue;
                }
                syncStageInProgress(chr, stage, announce);
                continue;
            }
            resetStageQuests(chr, stage, announce);
        }
    }

    private static void syncStageInProgress(Character chr, Stage stage, boolean announce) {
        Optional<Integer> currentQuestId = resolveCurrentQuestId(chr, stage);
        for (short questId : stage.stepQuestIds()) {
            if (chr.getQuestStatus(questId) == QuestStatus.Status.COMPLETED.getId()) {
                continue;
            }
            if (currentQuestId.isPresent() && currentQuestId.get() == questId
                    && chr.getQuestStatus(questId) == QuestStatus.Status.STARTED.getId()) {
                String progress = currentStepReady(chr, getStepByQuestId(questId)) ? PROGRESS_READY : PROGRESS_NOT_READY;
                setQuestStatus(chr, questId, QuestStatus.Status.STARTED, announce, progress, true);
                continue;
            }
            if (currentQuestId.isPresent() && currentQuestId.get() == questId) {
                continue;
            }
            setQuestStatus(chr, questId, QuestStatus.Status.NOT_STARTED, announce, null, false);
        }
    }

    private static void setStageCompleted(Character chr, Stage stage, boolean announce) {
        for (short questId : stage.stepQuestIds()) {
            setQuestStatus(chr, questId, QuestStatus.Status.COMPLETED, announce, null, true);
        }
    }

    private static void resetStageQuests(Character chr, Stage stage, boolean announce) {
        for (short questId : stage.stepQuestIds()) {
            setQuestStatus(chr, questId, QuestStatus.Status.NOT_STARTED, announce, null, false);
        }
    }

    private static void syncClientQuestEntrypoints(Character chr, boolean announce) {
        if (!announce || chr == null || chr.getClient() == null) {
            return;
        }
        InteractionHookPackets.sendCharacterQuestRules(chr.getClient());
        InteractionHookPackets.sendProgress(chr.getClient());
    }

    public static StartResult startStage(Character chr, int questId) {
        StepRef ref = getStepByQuestId(questId);
        StartValidation validation = validateStart(chr, ref);
        if (!validation.isOk()) {
            syncQuestState(chr);
            return StartResult.fail(validation.getMessage());
        }

        setQuestStatus(chr, ref.questId, QuestStatus.Status.STARTED, true,
                currentStepReady(chr, ref) ? PROGRESS_READY : PROGRESS_NOT_READY, false);
        syncQuestState(chr);
        return StartResult.success(stepStartedMessage(chr, ref));
    }

    public static String startPrompt(Character chr, int questId) {
        StepRef ref = getStepByQuestId(questId);
        StartValidation validation = validateStart(chr, ref);
        if (!validation.isOk()) {
            return validation.getMessage();
        }

        Stage stage = ref.stage;
        return "#e" + stage.name + "#n\r\n\r\n"
                + "元素共鸣会按任务列表中的步骤逐步推进。完成当前目标后回到汉斯处提交，下一步才会开启。\r\n\r\n"
                + stepRequirementText(chr, ref, -1);
    }

    public static String progressText(Character chr, int questId) {
        StepRef ref = getStepByQuestId(questId);
        if (ref == null) {
            return "这个元素共鸣任务暂时无法处理。";
        }

        Stage stage = ref.stage;
        StringBuilder text = new StringBuilder("#e").append(stage.name).append("#n\r\n\r\n");
        text.append(stepRequirementText(chr, ref, -1));
        if (ref.type == StepType.REWARD) {
            CompletionValidation validation = validateCompletion(chr, ref, -1);
            if (validation.isOk()) {
                text.append("\r\n#b重铸准备已经完成。点击完成书本选择新的元素杖。#k");
                return text.toString();
            }
            text.append("\r\n#r当前不能重铸：#k").append(validation.getMessage());
            return text.toString();
        }

        StepAdvanceValidation validation = validateStepAdvance(chr, ref);
        if (validation.isOk()) {
            text.append("\r\n#b当前步骤已经完成。点击完成书本向汉斯报告，推进到下一步。#k");
            return text.toString();
        }

        text.append("\r\n#r当前步骤未完成：#k").append(validation.getMessage());
        return text.toString();
    }

    public static boolean isFinalRewardStep(Character chr, int questId) {
        StepRef ref = getStepByQuestId(questId);
        return ref != null && ref.type == StepType.REWARD;
    }

    public static List<InteractionHookProgressEntry> progressEntries(Character chr) {
        if (chr == null) {
            return List.of();
        }
        syncQuestStateSilently(chr);
        List<InteractionHookProgressEntry> entries = new ArrayList<>();
        for (int questId : getAllQuestIds()) {
            byte status = chr.getQuestStatus(questId);
            if (status != QuestStatus.Status.STARTED.getId()) {
                continue;
            }
            StepRef ref = getStepByQuestId(questId);
            if (ref != null) {
                entries.add(progressEntryForStep(chr, ref));
            }
        }
        return entries;
    }

    public static InteractionHookAction resolveCurrentAction(Character chr, int questId) {
        StepRef ref = getStepByQuestId(questId);
        if (chr == null || ref == null) {
            return InteractionHookAction.QUERY_PROGRESS;
        }
        byte status = chr.getQuestStatus(ref.questId);
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            return InteractionHookAction.QUERY_START;
        }
        if (status != QuestStatus.Status.STARTED.getId()) {
            return InteractionHookAction.QUERY_PROGRESS;
        }
        if (ref.type == StepType.REWARD) {
            return validateCompletion(chr, ref, -1).isOk()
                    ? InteractionHookAction.QUERY_COMPLETE
                    : InteractionHookAction.QUERY_PROGRESS;
        }
        return validateStepAdvance(chr, ref).isOk()
                ? InteractionHookAction.QUERY_COMPLETE
                : InteractionHookAction.QUERY_PROGRESS;
    }

    public static void openHook(InteractionHookContext context) {
        if (context == null || context.player() == null) {
            return;
        }

        Character chr = context.player();
        syncQuestState(chr);
        StepRef ref = getStepByQuestId(context.questId());
        if (ref == null) {
            context.sendOk("这个元素共鸣任务暂时无法处理。");
            return;
        }

        byte status = chr.getQuestStatus(ref.questId);
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            StartValidation validation = validateStart(chr, ref);
            if (!validation.isOk()) {
                context.sendOk(validation.getMessage());
                return;
            }
            context.sendYesNo(startPrompt(chr, ref.questId));
            return;
        }

        if (status == QuestStatus.Status.STARTED.getId()) {
            openStartedHook(context, chr, ref);
            return;
        }

        context.sendOk(questDisplayName(ref) + "已经完成。");
    }

    public static void handleHookAction(InteractionHookContext context, byte mode, byte lastMessage, int selection) {
        if (context == null || context.player() == null) {
            return;
        }
        if (mode <= 0) {
            context.close();
            return;
        }

        Character chr = context.player();
        syncQuestState(chr);
        StepRef ref = getStepByQuestId(context.questId());
        if (ref == null) {
            context.sendOk("这个元素共鸣任务暂时无法处理。");
            return;
        }

        byte status = chr.getQuestStatus(ref.questId);
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            StartResult result = startStage(chr, ref.questId);
            context.sendOk(result.message());
            return;
        }

        if (status != QuestStatus.Status.STARTED.getId()) {
            context.sendOk(questDisplayName(ref) + "已经完成。");
            return;
        }

        if (ref.type == StepType.REWARD) {
            handleRewardHookAction(context, chr, ref, selection);
            return;
        }

        StepAdvanceResult result = advanceCurrentStep(chr, ref.questId);
        context.sendOk(result.message());
    }

    private static void openStartedHook(InteractionHookContext context, Character chr, StepRef ref) {
        if (ref.type == StepType.REWARD) {
            CompletionValidation validation = validateCompletion(chr, ref, -1);
            if (!validation.isOk()) {
                context.sendOk(validation.getMessage());
                return;
            }
            context.sendSimple(rewardSelectionPrompt(chr, ref.questId));
            return;
        }

        StepAdvanceValidation validation = validateStepAdvance(chr, ref);
        if (!validation.isOk()) {
            context.sendOk(progressText(chr, ref.questId));
            return;
        }
        context.sendYesNo(advancePrompt(chr, ref.questId));
    }

    private static void handleRewardHookAction(InteractionHookContext context, Character chr, StepRef ref,
                                               int selection) {
        if (context.selectedOption() < 0) {
            CompletionValidation validation = validateCompletion(chr, ref, selection);
            if (!validation.isOk()) {
                context.sendOk(validation.getMessage());
                return;
            }
            context.setSelectedOption(selection);
            context.sendYesNo(completionPrompt(chr, ref.questId, selection));
            return;
        }

        CompletionResult result = completeStage(chr, ref.questId, context.selectedOption());
        context.sendOk(result.message());
    }

    private static InteractionHookProgressEntry progressEntryForStep(Character chr, StepRef ref) {
        Stage stage = ref.stage;
        List<InteractionHookProgressEntry.Condition> conditions = new ArrayList<>();
        int completedSteps = completedStepCount(chr, stage);
        boolean ready = currentStepReady(chr, ref);
        conditions.add(new InteractionHookProgressEntry.Condition(
                ready ? 1 : 0,
                1,
                "当前步骤：第 #b" + (ref.stepOrder() + 1) + "#k/#r" + stage.totalStepCount()
                        + "#k 步，已完成 #b" + completedSteps + "#k/#r" + stage.totalStepCount() + "#k"));

        if (ref.type == StepType.BOSS) {
            BossTarget bossTarget = ref.bossTarget();
            int held = Math.min(1, chr.getItemQuantity(bossTarget.tokenId(), false));
            conditions.add(new InteractionHookProgressEntry.Condition(
                    held,
                    1,
                    "目标：击败 #o" + bossTarget.mobId() + "#，取得 #i" + bossTarget.tokenId()
                            + "# #t" + bossTarget.tokenId() + "# #b" + held + "#k/#r1#k"));
            conditions.add(new InteractionHookProgressEntry.Condition(
                    held,
                    1,
                    "下一步：持有凭证后回到#p" + NPC_ID + "#提交"));
            return new InteractionHookProgressEntry(ref.questId, chr.getQuestStatus(ref.questId), conditions);
        }

        if (ref.type == StepType.BASE_MATERIALS) {
            conditions.add(new InteractionHookProgressEntry.Condition(
                    ready ? 1 : 0,
                    1,
                    "目标：交付本阶段重铸材料和基础金币"));
            for (Requirement requirement : stage.baseRequirements) {
                int held = Math.min(requirement.count(), chr.getItemQuantity(requirement.itemId(), false));
                conditions.add(new InteractionHookProgressEntry.Condition(
                        held,
                        requirement.count(),
                        "#i" + requirement.itemId() + "# #t" + requirement.itemId() + "# #b"
                                + held + "#k/#r" + requirement.count() + "#k"));
            }
            int meso = Math.min(stage.baseMeso, chr.getMeso());
            conditions.add(new InteractionHookProgressEntry.Condition(
                    meso,
                    stage.baseMeso,
                    "基础金币：#b" + formatMeso(meso) + "#k/#r" + formatMeso(stage.baseMeso) + "#k"));
            conditions.add(new InteractionHookProgressEntry.Condition(
                    ready ? 1 : 0,
                    1,
                    "下一步：材料和金币满足后回到#p" + NPC_ID + "#提交"));
            return new InteractionHookProgressEntry(ref.questId, chr.getQuestStatus(ref.questId), conditions);
        }

        conditions.add(new InteractionHookProgressEntry.Condition(
                ready ? 1 : 0,
                1,
                "目标：选择 1 把本阶段元素杖完成重铸"));
        if (stage.index > 1) {
            StaffInfo current = getStaffState(chr).current();
            String staffText = current == null
                    ? "上一阶段元素杖：#r未持有#k"
                    : "上一阶段元素杖：#i" + current.itemId() + "# #t" + current.itemId() + "#";
            conditions.add(new InteractionHookProgressEntry.Condition(1, 1, staffText));
            conditions.add(new InteractionHookProgressEntry.Condition(
                    1,
                    1,
                    "跨属性重铸额外消耗：" + switchRequirementSummary(stage)));
        }
        conditions.add(new InteractionHookProgressEntry.Condition(
                ready ? 1 : 0,
                1,
                "下一步：点击#p" + NPC_ID + "#的完成书本，选择元素属性"));
        return new InteractionHookProgressEntry(ref.questId, chr.getQuestStatus(ref.questId), conditions);
    }

    private static int completedStepCount(Character chr, Stage stage) {
        int completed = 0;
        for (int i = 0; i < stage.bossTargets.size(); i++) {
            if (isQuestCompleted(chr, stage.bossBridgeQuestId(i))) {
                completed++;
            }
        }
        if (isQuestCompleted(chr, stage.materialBridgeQuestId())) {
            completed++;
        }
        if (isQuestCompleted(chr, stage.questId)) {
            completed++;
        }
        return completed;
    }

    public static StepAdvanceValidation validateStepAdvance(Character chr, int questId) {
        return validateStepAdvance(chr, getStepByQuestId(questId));
    }

    public static String advancePrompt(Character chr, int questId) {
        StepRef ref = getStepByQuestId(questId);
        StepAdvanceValidation validation = validateStepAdvance(chr, ref);
        if (!validation.isOk()) {
            return validation.getMessage();
        }

        Stage stage = ref.stage;
        StringBuilder text = new StringBuilder("#e").append(stage.name).append("#n\r\n\r\n");
        text.append(stepRequirementText(chr, ref, -1)).append("\r\n");
        if (ref.type == StepType.BOSS) {
            BossTarget current = ref.bossTarget();
            text.append("要把 #b#o").append(current.mobId()).append("##k 的共鸣凭证交给汉斯分析吗？\r\n\r\n");
            text.append("#r交付后会消耗这枚凭证，并开启下一步。#k");
            return text.toString();
        }

        text.append("要把这些普通材料和基础金币交给汉斯完成重铸准备吗？\r\n\r\n");
        text.append("#r交付后会消耗普通材料和基础金币，并开启最终元素杖选择。#k");
        return text.toString();
    }

    public static String rewardSelectionPrompt(Character chr, int questId) {
        StepRef ref = getStepByQuestId(questId);
        CompletionValidation validation = validateCompletion(chr, ref, -1);
        if (!validation.isOk()) {
            return validation.getMessage();
        }

        Stage stage = ref.stage;
        StringBuilder text = new StringBuilder("#e选择元素杖#n\r\n\r\n");
        text.append(stepRequirementText(chr, ref, -1));
        text.append("\r\n请选择这次要共鸣的属性：\r\n");
        for (int i = 0; i < stage.rewardItemIds.length; i++) {
            int itemId = stage.rewardItemIds[i];
            text.append("#L").append(i).append("##i").append(itemId).append("# #t").append(itemId).append("#  ")
                    .append(ELEMENTS[i].displayName()).append("#l\r\n");
        }
        return text.toString();
    }

    public static String completionPrompt(Character chr, int questId, int selection) {
        StepRef ref = getStepByQuestId(questId);
        CompletionValidation validation = validateCompletion(chr, ref, selection);
        if (!validation.isOk()) {
            return validation.getMessage();
        }

        Stage stage = ref.stage;
        int rewardItemId = stage.rewardItemIds[selection];
        StringBuilder text = new StringBuilder("要选择 #b#i").append(rewardItemId).append("##t").append(rewardItemId)
                .append("##k 吗？\r\n\r\n");
        if (stage.index > 1 && validation.current() != null) {
            text.append("将消耗上一阶段元素杖：#r#i").append(validation.current().itemId()).append("##t")
                    .append(validation.current().itemId()).append("##k\r\n");
        }
        if (validation.requirements().isEmpty() && validation.requiredMeso() == 0) {
            text.append("普通材料已经在上一步交付，本次不再额外消耗材料和金币。\r\n");
        } else {
            if (!validation.requirements().isEmpty()) {
                text.append("将额外消耗材料：\r\n").append(requirementText(chr, validation.requirements()));
            }
            if (validation.requiredMeso() > 0) {
                text.append("额外金币：#b").append(formatMeso(chr.getMeso())).append("#k / #r")
                        .append(formatMeso(validation.requiredMeso())).append("#k\r\n");
            }
        }
        if (validation.switchElement()) {
            text.append("\r\n#r本次会跨属性重铸，额外消耗星石、黑水晶和金币。#k");
        }
        return text.toString();
    }

    public static StepAdvanceResult advanceCurrentStep(Character chr, int questId) {
        StepRef ref = getStepByQuestId(questId);
        StepAdvanceValidation validation = validateStepAdvance(chr, ref);
        if (!validation.isOk()) {
            syncQuestState(chr);
            return StepAdvanceResult.fail(validation.getMessage());
        }

        Stage stage = ref.stage;
        if (ref.type == StepType.BOSS) {
            BossTarget current = ref.bossTarget();
            removeRequirements(chr, List.of(req(current.tokenId(), 1)));
            completeQuestStep(chr, ref.questId);
            syncQuestState(chr);
            return StepAdvanceResult.success("#e" + stage.name + "#n\r\n\r\n"
                    + "#b#o" + current.mobId() + "##k 的共鸣凭证已经确认。\r\n\r\n"
                    + nextStepMessage(chr, stage));
        }

        removeRequirements(chr, stage.baseRequirements);
        if (stage.baseMeso > 0) {
            chr.gainMeso(-stage.baseMeso, true, false, true);
        }
        completeQuestStep(chr, ref.questId);
        syncQuestState(chr);
        return StepAdvanceResult.success("#e" + stage.name + "#n\r\n\r\n"
                + "普通材料已经交付，元素杖的重铸准备完成。\r\n\r\n下一步：点击完成书本，选择这次要共鸣的元素杖。");
    }

    public static CompletionResult completeStage(Character chr, int questId, int selection) {
        StepRef ref = getStepByQuestId(questId);
        CompletionValidation validation = validateCompletion(chr, ref, selection);
        if (!validation.isOk()) {
            syncQuestState(chr);
            return CompletionResult.fail(validation.getMessage());
        }

        Stage stage = ref.stage;
        int oldWeapon = validation.current() == null ? 0 : validation.current().itemId();
        int rewardItemId = stage.rewardItemIds[selection];

        if (oldWeapon > 0) {
            InventoryManipulator.removeById(chr.getClient(), InventoryType.EQUIP, oldWeapon, 1, true, false);
            chr.sendPacket(PacketCreator.getShowItemGain(oldWeapon, (short) -1, true));
        }

        Item reward = gainRawEquip(chr, rewardItemId);
        if (reward == null) {
            if (oldWeapon > 0) {
                gainRawEquip(chr, oldWeapon);
            }
            syncQuestState(chr);
            return CompletionResult.fail("装备栏空间不足，共鸣没有完成。请整理装备栏后再试。");
        }

        removeRequirements(chr, validation.requirements());

        if (validation.requiredMeso() > 0) {
            chr.gainMeso(-validation.requiredMeso(), true, false, true);
        }

        completeQuestStep(chr, stage.questId);
        syncQuestState(chr);
        return CompletionResult.success("#e元素共鸣完成#n\r\n\r\n你获得了 #b#i" + rewardItemId + "##t"
                + rewardItemId + "##k。\r\n" + nextStageMessage(stage));
    }

    public static CompletionValidation validateCompletion(Character chr, int questId, int selection) {
        return validateCompletion(chr, getStepByQuestId(questId), selection);
    }

    public static StartValidation validateStart(Character chr, int questId) {
        return validateStart(chr, getStepByQuestId(questId));
    }

    public static TestSupplyResult supplyCurrentTestRequirements(Character chr) {
        if (chr == null) {
            return TestSupplyResult.fail("角色状态异常，无法补齐元素共鸣测试条件。");
        }

        syncQuestState(chr);
        StepRef activeStep = null;
        for (int questId : getAllQuestIds()) {
            if (chr.getQuestStatus(questId) != QuestStatus.Status.STARTED.getId()) {
                continue;
            }
            StepRef ref = getStepByQuestId(questId);
            if (ref == null) {
                continue;
            }
            if (activeStep != null) {
                return TestSupplyResult.fail("当前存在多个进行中的元素共鸣任务，请先修复任务状态。");
            }
            activeStep = ref;
        }

        if (activeStep == null) {
            Optional<Integer> nextQuestId = resolveCurrentQuestId(chr);
            if (nextQuestId.isPresent()) {
                StepRef nextStep = getStepByQuestId(nextQuestId.get());
                return TestSupplyResult.fail("当前没有进行中的元素共鸣任务，请先在汉斯处领取 "
                        + questDisplayName(nextStep) + "。");
            }
            return TestSupplyResult.fail("当前没有可补齐的元素共鸣任务，请检查职业、等级和元素杖阶段。");
        }

        StartValidation startValidation = validateStartedQuest(chr, activeStep);
        if (!startValidation.isOk()) {
            syncQuestState(chr);
            return TestSupplyResult.fail(startValidation.getMessage());
        }

        Stage activeStage = activeStep.stage;
        if (activeStep.type == StepType.BOSS) {
            BossTarget current = activeStep.bossTarget();
            if (current == null) {
                return TestSupplyResult.fail("当前 Boss 步骤状态异常，无法补齐测试凭证。");
            }
            if (!addMissingRequirements(chr, List.of(req(current.tokenId(), 1)))) {
                return TestSupplyResult.fail("背包空间不足，无法补齐当前 Boss 凭证。");
            }
            syncQuestState(chr);
            return TestSupplyResult.success("已补齐 " + activeStage.name + " 当前 Boss 凭证：怪物 "
                    + current.mobId() + "，凭证 " + current.tokenId() + "。请点击完成书本向汉斯报告。");
        }

        if (activeStep.type == StepType.BASE_MATERIALS) {
            if (!addMissingRequirements(chr, activeStage.baseRequirements)) {
                return TestSupplyResult.fail("背包空间不足，无法补齐当前普通材料。");
            }
            int gainedMeso = addMissingMeso(chr, activeStage.baseMeso);
            syncQuestState(chr);
            return TestSupplyResult.success("已补齐 " + activeStage.name + " 当前普通材料和基础金币，补发金币 "
                    + formatMeso(gainedMeso) + "。请点击完成书本向汉斯交付材料。");
        }

        CompletionValidation validation = validateCompletion(chr, activeStep, -1);
        if (!validation.isOk()) {
            syncQuestState(chr);
            return TestSupplyResult.fail(validation.getMessage());
        }

        if (!addMissingRequirements(chr, activeStage.switchRequirements)) {
            return TestSupplyResult.fail("背包空间不足，无法补齐跨属性备用材料。");
        }
        int gainedMeso = addMissingMeso(chr, activeStage.switchMeso);
        syncQuestState(chr);
        return TestSupplyResult.success("已补齐 " + activeStage.name + " 奖励选择步骤的跨属性备用材料和金币，补发金币 "
                + formatMeso(gainedMeso) + "。请点击完成书本选择元素杖。");
    }

    private static StartValidation validateStart(Character chr, StepRef ref) {
        if (chr == null) {
            return StartValidation.fail("角色状态异常，暂时无法开始元素共鸣。");
        }
        if (ref == null) {
            return StartValidation.fail("这个元素共鸣任务暂时无法处理。");
        }
        StartValidation stageValidation = validateStageAccess(chr, ref.stage);
        if (!stageValidation.isOk()) {
            return stageValidation;
        }
        if (chr.getQuestStatus(ref.questId) == QuestStatus.Status.COMPLETED.getId()) {
            return StartValidation.fail("这一步元素共鸣已经完成。");
        }
        Optional<Integer> currentQuestId = resolveCurrentQuestId(chr);
        if (currentQuestId.isEmpty() || currentQuestId.get() != ref.questId) {
            return StartValidation.fail("请按元素共鸣任务链顺序推进，先完成当前开放的步骤。");
        }
        return StartValidation.success();
    }

    private static StartValidation validateStageAccess(Character chr, Stage stage) {
        if (chr == null) {
            return StartValidation.fail("角色状态异常，暂时无法开始元素共鸣。");
        }
        if (stage == null) {
            return StartValidation.fail("这个元素共鸣任务暂时无法处理。");
        }
        if (!isEligibleMage(chr)) {
            return StartValidation.fail("元素共鸣只开放给法师职业。");
        }
        if (chr.getLevel() < stage.requiredLevel) {
            return StartValidation.fail("你的等级还不够。\r\n需要等级：#r" + stage.requiredLevel + "#k");
        }

        StaffState staffState = getStaffState(chr);
        if (stage.index == 1) {
            if (staffState.total() > 0) {
                return StartValidation.fail("你已经拥有元素杖。请继续当前阶段，不要重复领取初阶共鸣。");
            }
            return StartValidation.success();
        }

        if (staffState.total() == 0) {
            return StartValidation.fail("你没有上一阶段元素杖。如果已经丢弃，可以从初阶共鸣重新开始。");
        }
        if (staffState.total() > 1) {
            return StartValidation.fail("你身上存在多个元素杖。为了避免唯一装备状态异常，请先整理到只保留一把。");
        }

        StaffInfo current = staffState.current();
        if (current == null || current.stage() != stage.index - 1) {
            return StartValidation.fail("当前元素杖阶段不匹配。需要上一阶段任意元素杖。");
        }
        if (current.equipped() > 0) {
            return StartValidation.fail("请先卸下 #b#i" + current.itemId() + "##t" + current.itemId()
                    + "##k，并把它放在装备栏背包内。");
        }
        if (current.inBag() <= 0) {
            return StartValidation.fail("请把上一阶段元素杖放在装备栏背包内后再来升级。");
        }
        return StartValidation.success();
    }

    private static CompletionValidation validateCompletion(Character chr, StepRef ref, int selection) {
        if (ref == null || ref.type != StepType.REWARD) {
            return CompletionValidation.fail("这个元素共鸣任务不是奖励选择步骤。");
        }
        Stage stage = ref.stage;
        StartValidation startValidation = validateStartedQuest(chr, ref);
        if (!startValidation.isOk()) {
            return CompletionValidation.fail(startValidation.getMessage());
        }

        if (!previousStepsCompleted(chr, ref)) {
            return CompletionValidation.fail("请先按顺序完成当前元素共鸣步骤。");
        }

        if (selection >= stage.rewardItemIds.length) {
            return CompletionValidation.fail("请选择有效的元素杖。");
        }

        StaffState staffState = getStaffState(chr);
        StaffInfo current = staffState.current();
        if (stage.index == 1) {
            if (staffState.total() > 0) {
                return CompletionValidation.fail("你已经拥有元素杖，不能重复完成初阶共鸣。");
            }
        } else {
            if (staffState.total() == 0) {
                return CompletionValidation.fail("你没有上一阶段元素杖。如果已经丢弃，可以从初阶共鸣重新开始。");
            }
            if (staffState.total() > 1) {
                return CompletionValidation.fail("你身上存在多个元素杖。为了避免唯一装备状态异常，请先整理到只保留一把。");
            }
            if (current == null || current.stage() != stage.index - 1) {
                return CompletionValidation.fail("请带来上一阶段任意元素杖。");
            }
            if (current.equipped() > 0) {
                return CompletionValidation.fail("请先卸下 #b#i" + current.itemId() + "##t" + current.itemId()
                        + "##k，并把它放在装备栏背包内。");
            }
            if (current.inBag() <= 0) {
                return CompletionValidation.fail("请把上一阶段元素杖放在装备栏背包内后再来升级。");
            }
        }

        if (selection < 0) {
            return validateAnyRewardSelection(chr, stage, current);
        }
        return validateExactRewardSelection(chr, stage, current, selection);
    }

    private static StartValidation validateStartedQuest(Character chr, StepRef ref) {
        if (ref == null) {
            return StartValidation.fail("这个元素共鸣任务暂时无法处理。");
        }
        StartValidation startValidation = validateStageAccess(chr, ref.stage);
        if (!startValidation.isOk()) {
            return startValidation;
        }
        if (chr.getQuestStatus(ref.questId) != QuestStatus.Status.STARTED.getId()) {
            return StartValidation.fail("请先在汉斯处接受这个元素共鸣任务。");
        }
        Optional<Integer> currentQuestId = resolveCurrentQuestId(chr);
        if (currentQuestId.isPresent() && currentQuestId.get() != ref.questId) {
            return StartValidation.fail("请按元素共鸣任务链顺序推进，先完成当前开放的步骤。");
        }
        return StartValidation.success();
    }

    private static StepAdvanceValidation validateStepAdvance(Character chr, StepRef ref) {
        if (ref == null) {
            return StepAdvanceValidation.fail("这个元素共鸣任务暂时无法处理。");
        }
        if (ref.type == StepType.REWARD) {
            return StepAdvanceValidation.fail("重铸准备已经完成，请选择新的元素杖。");
        }
        StartValidation startValidation = validateStartedQuest(chr, ref);
        if (!startValidation.isOk()) {
            return StepAdvanceValidation.fail(startValidation.getMessage());
        }

        if (!previousStepsCompleted(chr, ref)) {
            return StepAdvanceValidation.fail("请先完成上一项元素共鸣步骤。");
        }

        if (ref.type == StepType.BOSS) {
            BossTarget current = ref.bossTarget();
            return validateRequirements(chr, List.of(req(current.tokenId(), 1)), 0);
        }

        return validateRequirements(chr, ref.stage.baseRequirements, ref.stage.baseMeso);
    }

    private static CompletionValidation validateAnyRewardSelection(Character chr, Stage stage, StaffInfo current) {
        CompletionValidation firstFailure = null;
        for (int selection = 0; selection < stage.rewardItemIds.length; selection++) {
            CompletionValidation validation = validateExactRewardSelection(chr, stage, current, selection);
            if (validation.isOk()) {
                return validation;
            }
            if (firstFailure == null) {
                firstFailure = validation;
            }
        }
        return firstFailure == null ? CompletionValidation.fail("请选择有效的元素杖。") : firstFailure;
    }

    private static CompletionValidation validateExactRewardSelection(Character chr, Stage stage, StaffInfo current, int selection) {
        boolean switchElement = current != null && current.elementIndex() != selection;
        List<Requirement> requirements = stage.finalRequirementsFor(switchElement);
        for (Requirement requirement : requirements) {
            if (chr.getItemQuantity(requirement.itemId(), false) < requirement.count()) {
                return CompletionValidation.fail("材料不足。\r\n需要：#b#i" + requirement.itemId() + "##t"
                        + requirement.itemId() + "# x" + requirement.count() + "#k");
            }
        }

        int requiredMeso = stage.finalRequiredMeso(switchElement);
        if (chr.getMeso() < requiredMeso) {
            return CompletionValidation.fail("金币不足。\r\n当前：#b" + formatMeso(chr.getMeso()) + "#k\r\n需要：#r"
                    + formatMeso(requiredMeso) + "#k");
        }

        return CompletionValidation.success(stage, current, selection, switchElement, requirements, requiredMeso);
    }

    private static StepAdvanceValidation validateRequirements(Character chr, List<Requirement> requirements, int requiredMeso) {
        for (Requirement requirement : requirements) {
            if (chr.getItemQuantity(requirement.itemId(), false) < requirement.count()) {
                return StepAdvanceValidation.fail("材料不足。\r\n需要：#b#i" + requirement.itemId() + "##t"
                        + requirement.itemId() + "# x" + requirement.count() + "#k");
            }
        }

        if (chr.getMeso() < requiredMeso) {
            return StepAdvanceValidation.fail("金币不足。\r\n当前：#b" + formatMeso(chr.getMeso()) + "#k\r\n需要：#r"
                    + formatMeso(requiredMeso) + "#k");
        }
        return StepAdvanceValidation.success();
    }

    private static boolean isEligibleMage(Character chr) {
        if (chr == null || chr.getJob() == null) {
            return false;
        }
        int jobId = chr.getJob().getId();
        return jobId >= Job.MAGICIAN.getId() && jobId < 300;
    }

    public static StaffState getStaffState(Character chr) {
        Inventory equipInv = chr.getInventory(InventoryType.EQUIP);
        Inventory equippedInv = chr.getInventory(InventoryType.EQUIPPED);
        StaffInfo current = null;
        int total = 0;

        for (Stage stage : STAGES) {
            for (int elementIndex = 0; elementIndex < stage.rewardItemIds.length; elementIndex++) {
                int itemId = stage.rewardItemIds[elementIndex];
                int inBag = equipInv.countById(itemId);
                int equipped = equippedInv.countById(itemId);
                int count = inBag + equipped;
                if (count <= 0) {
                    continue;
                }

                total += count;
                if (current == null || stage.index > current.stage()) {
                    current = new StaffInfo(itemId, stage.index, elementIndex, inBag, equipped);
                }
            }
        }

        return new StaffState(total, current);
    }

    private static boolean currentStepReady(Character chr, StepRef ref) {
        if (ref == null) {
            return false;
        }
        if (ref.type == StepType.REWARD) {
            return validateCompletion(chr, ref, -1).isOk();
        }
        return validateStepAdvance(chr, ref).isOk();
    }

    private static boolean previousStepsCompleted(Character chr, StepRef ref) {
        if (chr == null || ref == null) {
            return false;
        }
        for (short questId : ref.stage.stepQuestIds()) {
            if (questId == ref.questId) {
                return true;
            }
            if (!isQuestCompleted(chr, questId)) {
                return false;
            }
        }
        return false;
    }

    private static boolean isQuestCompleted(Character chr, short questId) {
        return chr.getQuestStatus(questId) == QuestStatus.Status.COMPLETED.getId();
    }

    private static void completeQuestStep(Character chr, short questId) {
        if (chr.getMap() != null) {
            Quest.getInstance(questId).forceComplete(chr, NPC_ID);
            return;
        }
        setQuestStatus(chr, questId, QuestStatus.Status.COMPLETED, true, null, true);
    }

    private static void setQuestStatus(Character chr, short questId, QuestStatus.Status status, boolean announce,
                                       String virtualProgress, boolean preserveCompletedData) {
        Quest quest = Quest.getInstance(questId);
        QuestStatus oldStatus = chr.getQuestNoAdd(quest);
        if (oldStatus == null && status == QuestStatus.Status.NOT_STARTED) {
            return;
        }
        if (oldStatus != null && oldStatus.getStatus() == status
                && (status != QuestStatus.Status.STARTED
                || oldStatus.getNpc() == NPC_ID
                && oldStatus.getProgress(VIRTUAL_PROGRESS_KEY).equals(safeProgress(virtualProgress)))) {
            return;
        }

        QuestStatus newStatus = new QuestStatus(quest, status, NPC_ID);
        if (status == QuestStatus.Status.STARTED) {
            newStatus.setProgress(VIRTUAL_PROGRESS_KEY, safeProgress(virtualProgress));
        }
        if (oldStatus != null && preserveCompletedData && status == QuestStatus.Status.COMPLETED
                && oldStatus.getStatus() == QuestStatus.Status.COMPLETED) {
            copyQuestData(oldStatus, newStatus);
        }

        if (announce) {
            chr.updateQuestStatus(newStatus);
            return;
        }

        synchronized (chr.getQuests()) {
            chr.getQuests().put(questId, newStatus);
        }
    }

    private static String safeProgress(String progress) {
        return progress == null || progress.isBlank() ? PROGRESS_NOT_READY : progress;
    }

    private static void copyQuestData(QuestStatus oldStatus, QuestStatus newStatus) {
        newStatus.setForfeited(oldStatus.getForfeited());
        newStatus.setCompleted(oldStatus.getCompleted());
        newStatus.setExpirationTime(oldStatus.getExpirationTime());
        if (oldStatus.getStatus() == QuestStatus.Status.COMPLETED) {
            newStatus.setCompletionTime(oldStatus.getCompletionTime());
        }
        for (var entry : oldStatus.getProgress().entrySet()) {
            newStatus.setProgress(entry.getKey(), entry.getValue());
        }
    }

    private static Item gainRawEquip(Character chr, int itemId) {
        Item item = ItemInformationProvider.getInstance().getEquipById(itemId);
        if (item == null) {
            return null;
        }
        if (!InventoryManipulator.addFromDrop(chr.getClient(), item, false, -1)) {
            return null;
        }
        chr.sendPacket(PacketCreator.getShowItemGain(itemId, (short) 1, true));
        return item;
    }

    private static String stepStartedMessage(Character chr, StepRef ref) {
        return "#e" + questDisplayName(ref) + "#n\r\n\r\n"
                + stepRequirementText(chr, ref, -1);
    }

    private static String nextStageMessage(Stage stage) {
        if (stage.index >= STAGES.length) {
            return "元素共鸣已经抵达终章。";
        }
        Stage next = STAGES[stage.index];
        return "下一阶段会在 #r" + next.requiredLevel + "#k 级开启。";
    }

    private static String nextStepMessage(Character chr, Stage stage) {
        Optional<Integer> nextQuestId = resolveCurrentQuestId(chr);
        if (nextQuestId.isPresent()) {
            StepRef next = getStepByQuestId(nextQuestId.get());
            if (next != null && next.stage == stage) {
                return "下一步：领取 #b" + questDisplayName(next) + "#k。";
            }
        }
        return nextStageMessage(stage);
    }

    private static String stepRequirementText(Character chr, StepRef ref, int selection) {
        Stage stage = ref.stage;
        boolean switchElement = false;
        StaffState staffState = getStaffState(chr);
        StaffInfo current = staffState.current();
        if (selection >= 0 && current != null) {
            switchElement = current.elementIndex() != selection;
        }

        StringBuilder text = new StringBuilder();
        text.append("等级：#b").append(chr.getLevel()).append("#k / #r").append(stage.requiredLevel).append("#k\r\n");
        if (stage.index > 1) {
            text.append("上一阶段元素杖：").append(staffLocationText(current, stage.index - 1)).append("\r\n");
        }
        text.append("已完成步骤：").append(completedStepText(chr, stage)).append("\r\n");

        if (ref.type == StepType.BOSS) {
            BossTarget bossTarget = ref.bossTarget();
            text.append("\r\n当前目标：击败 #o").append(bossTarget.mobId())
                    .append("#，取得 #i").append(bossTarget.tokenId()).append("# #t")
                    .append(bossTarget.tokenId()).append("#\r\n");
            text.append("当前进度：\r\n");
            text.append(requirementText(chr, List.of(req(bossTarget.tokenId(), 1))));
            text.append("完成方式：持有凭证后回到#p").append(NPC_ID).append("#提交。\r\n");
            text.append("下一步：提交后开放下一个元素共鸣步骤。");
            return text.toString();
        }

        if (ref.type == StepType.BASE_MATERIALS) {
            text.append("\r\n当前目标：交付本阶段重铸材料和基础金币\r\n");
            text.append("当前进度：\r\n");
            text.append(requirementText(chr, stage.baseRequirements));
            text.append("基础金币：#b").append(formatMeso(chr.getMeso())).append("#k / #r")
                    .append(formatMeso(stage.baseMeso)).append("#k\r\n");
            text.append("完成方式：材料和金币满足后回到#p").append(NPC_ID).append("#提交。\r\n");
            text.append("下一步：材料交付后开放元素杖选择步骤。");
            return text.toString();
        }

        text.append("\r\n当前目标：选择 1 把本阶段元素杖完成重铸\r\n");
        text.append("当前条件：\r\n");
        if (stage.index > 1) {
            text.append("跨属性重铸额外消耗：");
            if (selection < 0) {
                text.append("#b").append(switchRequirementSummary(stage)).append("#k\r\n");
            } else if (switchElement) {
                text.append("\r\n").append(requirementText(chr, stage.switchRequirements));
                text.append("额外金币：#b").append(formatMeso(chr.getMeso())).append("#k / #r")
                        .append(formatMeso(stage.switchMeso)).append("#k\r\n");
            } else {
                text.append("#b不需要#k\r\n");
            }
        } else {
            text.append("普通材料已经交付，可以直接选择第一把元素短杖。\r\n");
        }
        text.append("完成方式：点击#p").append(NPC_ID).append("#的完成书本，选择本次共鸣的属性。");
        return text.toString();
    }

    private static String completedStepText(Character chr, Stage stage) {
        List<String> completed = new ArrayList<>();
        for (int i = 0; i < stage.bossTargets.size(); i++) {
            BossTarget bossTarget = stage.bossTargets.get(i);
            if (isQuestCompleted(chr, stage.bossBridgeQuestId(i))) {
                completed.add("#o" + bossTarget.mobId() + "# 凭证已报告");
            }
        }
        if (isQuestCompleted(chr, stage.materialBridgeQuestId())) {
            completed.add("普通材料已交付");
        }
        if (isQuestCompleted(chr, stage.questId)) {
            completed.add("元素杖已选择");
        }
        if (completed.isEmpty()) {
            return "无";
        }
        return String.join("、", completed);
    }

    private static String questDisplayName(StepRef ref) {
        if (ref == null) {
            return "元素共鸣";
        }
        if (ref.type == StepType.BOSS) {
            return ref.stage.name + " - 击败 #o" + ref.bossTarget().mobId() + "#";
        }
        if (ref.type == StepType.BASE_MATERIALS) {
            return ref.stage.name + " - 交付材料";
        }
        return ref.stage.name + " - 选择元素杖";
    }

    private static String staffLocationText(StaffInfo current, int requiredStage) {
        if (current == null) {
            return "#r未持有#k";
        }
        if (current.stage() != requiredStage) {
            return "#r阶段不匹配#k";
        }
        if (current.inBag() > 0) {
            return "#b#i" + current.itemId() + "##t" + current.itemId() + "# 已在装备栏背包#k";
        }
        if (current.equipped() > 0) {
            return "#r#i" + current.itemId() + "##t" + current.itemId() + "# 当前穿戴中，请先卸下#k";
        }
        return "#r未在装备栏背包#k";
    }

    private static String requirementText(Character chr, List<Requirement> requirements) {
        StringBuilder text = new StringBuilder();
        for (Requirement requirement : requirements) {
            int count = chr.getItemQuantity(requirement.itemId(), false);
            text.append("#i").append(requirement.itemId()).append("# #t").append(requirement.itemId()).append("#：#b")
                    .append(count).append("#k / #r").append(requirement.count()).append("#k\r\n");
        }
        return text.toString();
    }

    private static void removeRequirements(Character chr, List<Requirement> requirements) {
        for (Requirement requirement : requirements) {
            InventoryManipulator.removeById(chr.getClient(), ItemConstants.getInventoryType(requirement.itemId()),
                    requirement.itemId(), requirement.count(), true, false);
            chr.sendPacket(PacketCreator.getShowItemGain(requirement.itemId(), (short) -requirement.count(), true));
        }
    }

    private static boolean addMissingRequirements(Character chr, List<Requirement> requirements) {
        for (Requirement requirement : mergeRequirements(requirements)) {
            int missing = requirement.count() - chr.getItemQuantity(requirement.itemId(), false);
            if (missing <= 0) {
                continue;
            }
            if (!InventoryManipulator.addById(chr.getClient(), requirement.itemId(), (short) missing,
                    chr.getName(), -1, (short) 0, -1)) {
                return false;
            }
            chr.sendPacket(PacketCreator.getShowItemGain(requirement.itemId(), (short) missing, true));
        }
        return true;
    }

    private static int addMissingMeso(Character chr, int requiredMeso) {
        int missing = Math.max(0, requiredMeso - chr.getMeso());
        if (missing > 0) {
            chr.gainMeso(missing, true, false, true);
        }
        return missing;
    }

    private static List<Requirement> tokenRequirements(Stage stage) {
        List<Requirement> requirements = new ArrayList<>();
        for (BossTarget bossTarget : stage.bossTargets) {
            requirements.add(req(bossTarget.tokenId(), 1));
        }
        return requirements;
    }

    private static String switchRequirementSummary(Stage stage) {
        return requirementSummary(stage.switchRequirements) + "，金币 " + formatMeso(stage.switchMeso);
    }

    private static String requirementSummary(List<Requirement> requirements) {
        List<String> parts = new ArrayList<>();
        for (Requirement requirement : requirements) {
            parts.add(itemName(requirement.itemId()) + " x" + requirement.count());
        }
        return String.join("、", parts);
    }

    private static String itemName(int itemId) {
        try {
            String name = ItemInformationProvider.getInstance().getName(itemId);
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (RuntimeException ignored) {
            // Dialog text must remain readable even when WZ string data is unavailable.
        }
        return "道具 " + itemId;
    }

    private static String formatMeso(int meso) {
        return String.format("%,d", meso);
    }

    private static Requirement req(int itemId, int count) {
        return new Requirement(itemId, count);
    }

    private static BossTarget boss(int mobId, int tokenId) {
        return new BossTarget(mobId, tokenId);
    }

    private enum StepType {
        BOSS,
        BASE_MATERIALS,
        REWARD
    }

    private record StepRef(Stage stage, StepType type, int bossIndex, short questId) {
        private BossTarget bossTarget() {
            if (type != StepType.BOSS || bossIndex < 0 || bossIndex >= stage.bossTargets.size()) {
                return null;
            }
            return stage.bossTargets.get(bossIndex);
        }

        private int stepOrder() {
            if (type == StepType.BOSS) {
                return bossIndex;
            }
            if (type == StepType.BASE_MATERIALS) {
                return stage.bossTargets.size();
            }
            return stage.bossTargets.size() + 1;
        }
    }

    private record Element(String displayName, String themeName, String finalName) {
    }

    public record Requirement(int itemId, int count) {
    }

    public record BossTarget(int mobId, int tokenId) {
    }

    public record StaffState(int total, StaffInfo current) {
    }

    public record StaffInfo(int itemId, int stage, int elementIndex, int inBag, int equipped) {
    }

    public static final class Stage {
        private final int index;
        private final short questId;
        private final int requiredLevel;
        private final String name;
        private final int[] rewardItemIds;
        private final List<BossTarget> bossTargets;
        private final List<Requirement> baseRequirements;
        private final int baseMeso;
        private final List<Requirement> switchRequirements;
        private final int switchMeso;
        private final short bridgeStartQuestId;

        private Stage(int index, short questId, int requiredLevel, String name, int[] rewardItemIds,
                      List<BossTarget> bossTargets, List<Requirement> baseRequirements, int baseMeso,
                      List<Requirement> switchRequirements, int switchMeso, short bridgeStartQuestId) {
            this.index = index;
            this.questId = questId;
            this.requiredLevel = requiredLevel;
            this.name = name;
            this.rewardItemIds = rewardItemIds;
            this.bossTargets = List.copyOf(bossTargets);
            this.baseRequirements = List.copyOf(baseRequirements);
            this.baseMeso = baseMeso;
            this.switchRequirements = List.copyOf(switchRequirements);
            this.switchMeso = switchMeso;
            this.bridgeStartQuestId = bridgeStartQuestId;
        }

        public int getIndex() {
            return index;
        }

        public short getQuestId() {
            return questId;
        }

        public int getRequiredLevel() {
            return requiredLevel;
        }

        public String getName() {
            return name;
        }

        public int getRewardItemId(int selection) {
            if (selection < 0 || selection >= rewardItemIds.length) {
                return 0;
            }
            return rewardItemIds[selection];
        }

        public List<Integer> getBossTokenIds() {
            List<Integer> tokenIds = new ArrayList<>();
            for (BossTarget bossTarget : bossTargets) {
                tokenIds.add(bossTarget.tokenId());
            }
            return tokenIds;
        }

        public List<BossTarget> getBossTargets() {
            return bossTargets;
        }

        public List<Requirement> getBaseRequirements() {
            return baseRequirements;
        }

        public List<Requirement> getSwitchRequirements() {
            return switchRequirements;
        }

        public int getBaseMeso() {
            return baseMeso;
        }

        public int getSwitchMeso() {
            return switchMeso;
        }

        private short bossBridgeQuestId(int bossIndex) {
            return (short) (bridgeStartQuestId + bossIndex);
        }

        private short materialBridgeQuestId() {
            return (short) (bridgeStartQuestId + bossTargets.size());
        }

        private short firstStepQuestId() {
            return bridgeStartQuestId;
        }

        private List<Short> stepQuestIds() {
            List<Short> questIds = new ArrayList<>();
            for (short questId = bridgeStartQuestId; questId <= materialBridgeQuestId(); questId++) {
                questIds.add(questId);
            }
            questIds.add(questId);
            return questIds;
        }

        private int totalStepCount() {
            return bossTargets.size() + 2;
        }

        private List<Requirement> finalRequirementsFor(boolean switchElement) {
            if (!switchElement) {
                return List.of();
            }
            return mergeRequirements(switchRequirements);
        }

        private int finalRequiredMeso(boolean switchElement) {
            return switchElement ? switchMeso : 0;
        }
    }

    private static List<Requirement> mergeRequirements(List<Requirement> requirements) {
        List<Requirement> merged = new ArrayList<>();
        for (Requirement requirement : requirements) {
            int existingIndex = -1;
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).itemId() == requirement.itemId()) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex < 0) {
                merged.add(requirement);
                continue;
            }
            Requirement existing = merged.get(existingIndex);
            merged.set(existingIndex, req(existing.itemId(), existing.count() + requirement.count()));
        }
        return merged;
    }

    public static final class StartValidation {
        private final boolean ok;
        private final String message;

        private StartValidation(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        private static StartValidation success() {
            return new StartValidation(true, "");
        }

        private static StartValidation fail(String message) {
            return new StartValidation(false, message);
        }

        public boolean isOk() {
            return ok;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class StepAdvanceValidation {
        private final boolean ok;
        private final String message;

        private StepAdvanceValidation(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        private static StepAdvanceValidation success() {
            return new StepAdvanceValidation(true, "");
        }

        private static StepAdvanceValidation fail(String message) {
            return new StepAdvanceValidation(false, message);
        }

        public boolean isOk() {
            return ok;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class CompletionValidation {
        private final boolean ok;
        private final String message;
        private final Stage stage;
        private final StaffInfo current;
        private final int selection;
        private final boolean switchElement;
        private final List<Requirement> requirements;
        private final int requiredMeso;

        private CompletionValidation(boolean ok, String message, Stage stage, StaffInfo current, int selection,
                                     boolean switchElement, List<Requirement> requirements, int requiredMeso) {
            this.ok = ok;
            this.message = message;
            this.stage = stage;
            this.current = current;
            this.selection = selection;
            this.switchElement = switchElement;
            this.requirements = requirements == null ? List.of() : List.copyOf(requirements);
            this.requiredMeso = requiredMeso;
        }

        private static CompletionValidation success(Stage stage, StaffInfo current, int selection,
                                                    boolean switchElement, List<Requirement> requirements,
                                                    int requiredMeso) {
            return new CompletionValidation(true, "", stage, current, selection, switchElement, requirements, requiredMeso);
        }

        private static CompletionValidation fail(String message) {
            return new CompletionValidation(false, message, null, null, -1, false, List.of(), 0);
        }

        public boolean isOk() {
            return ok;
        }

        public String getMessage() {
            return message;
        }

        public Stage stage() {
            return stage;
        }

        public StaffInfo current() {
            return current;
        }

        public int selection() {
            return selection;
        }

        public boolean switchElement() {
            return switchElement;
        }

        public List<Requirement> requirements() {
            return requirements;
        }

        public int requiredMeso() {
            return requiredMeso;
        }
    }

    public record StartResult(boolean success, String message) {
        private static StartResult success(String message) {
            return new StartResult(true, message);
        }

        private static StartResult fail(String message) {
            return new StartResult(false, message);
        }
    }

    public record StepAdvanceResult(boolean success, String message) {
        private static StepAdvanceResult success(String message) {
            return new StepAdvanceResult(true, message);
        }

        private static StepAdvanceResult fail(String message) {
            return new StepAdvanceResult(false, message);
        }
    }

    public record CompletionResult(boolean success, String message) {
        private static CompletionResult success(String message) {
            return new CompletionResult(true, message);
        }

        private static CompletionResult fail(String message) {
            return new CompletionResult(false, message);
        }
    }

    public record TestSupplyResult(boolean success, String message) {
        private static TestSupplyResult success(String message) {
            return new TestSupplyResult(true, message);
        }

        private static TestSupplyResult fail(String message) {
            return new TestSupplyResult(false, message);
        }
    }
}

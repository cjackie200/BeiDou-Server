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
    public static final short FIRST_BRIDGE_QUEST_ID = 29960;
    public static final short LAST_BRIDGE_QUEST_ID = 29969;

    private static final int VIRTUAL_PROGRESS_KEY = 0;
    private static final String PROGRESS_NOT_READY = "000";
    private static final String PROGRESS_READY = "001";
    private static final int BRIDGES_PER_STAGE = 2;

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
            new Stage(1, FIRST_QUEST_ID, 70, "元素共鸣的初声",
                    new int[]{1372035, 1372036, 1372037, 1372038, 1372047},
                    List.of(4033012, 4033013, 4033014),
                    List.of(req(4000059, 100), req(4000060, 100), req(4000061, 100), req(STAR_ROCK, 1)),
                    2_000_000,
                    List.of(),
                    0),
            new Stage(2, (short) 29992, 100, "元素共鸣的回响",
                    new int[]{1382045, 1382046, 1382047, 1382048, 1382061},
                    List.of(4033015, 4033016, 4033017),
                    List.of(req(4000144, 100), req(4000146, 100), req(4000176, 20), req(BLACK_CRYSTAL, 2), req(STAR_ROCK, 1)),
                    8_000_000,
                    List.of(req(BLACK_CRYSTAL, 1), req(STAR_ROCK, 1)),
                    2_000_000),
            new Stage(3, (short) 29993, 133, "元素共鸣的裂隙",
                    new int[]{1372039, 1372040, 1372041, 1372042, 1372048},
                    List.of(4033018, 4033019, 4033020),
                    List.of(req(BLACK_CRYSTAL, 5), req(STAR_ROCK, 3)),
                    18_000_000,
                    List.of(req(BLACK_CRYSTAL, 3), req(STAR_ROCK, 2)),
                    5_000_000),
            new Stage(4, (short) 29994, 160, "元素共鸣的风暴",
                    new int[]{1382049, 1382050, 1382051, 1382052, 1382063},
                    List.of(4033021, 4033022, 4033023),
                    List.of(req(4000235, 3), req(4000243, 3), req(4001084, 1), req(BLACK_CRYSTAL, 8), req(STAR_ROCK, 5)),
                    30_000_000,
                    List.of(req(BLACK_CRYSTAL, 5), req(STAR_ROCK, 3)),
                    10_000_000),
            new Stage(5, (short) 29995, 190, "元素共鸣的终章",
                    new int[]{1372059, 1372060, 1372061, 1372062, 1372063},
                    List.of(4033024, 4033025, 4033026, 4033027),
                    List.of(req(4001083, 1), req(4001084, 1), req(4000235, 5), req(4000243, 5), req(BLACK_CRYSTAL, 10), req(STAR_ROCK, 10)),
                    80_000_000,
                    List.of(req(BLACK_CRYSTAL, 10), req(STAR_ROCK, 5)),
                    30_000_000)
    };

    private static final Set<Integer> ELEMENTAL_WEAPONS = new HashSet<>();
    private static final Set<Integer> QUEST_ITEMS = new HashSet<>();

    static {
        for (Stage stage : STAGES) {
            for (int itemId : stage.rewardItemIds) {
                ELEMENTAL_WEAPONS.add(itemId);
                QUEST_ITEMS.add(itemId);
            }
            QUEST_ITEMS.addAll(stage.bossTokenIds);
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
        return questId >= FIRST_QUEST_ID && questId <= LAST_QUEST_ID;
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

    public static void syncQuestStateIfRelevant(Character chr, int itemId) {
        if (isQuestRelevantItem(itemId)) {
            syncQuestState(chr);
        }
    }

    public static void syncQuestStateIfMesoRelevant(Character chr) {
        if (chr == null) {
            return;
        }
        for (Stage stage : STAGES) {
            if (chr.getQuestStatus(stage.questId) == QuestStatus.Status.STARTED.getId()) {
                syncQuestState(chr);
                return;
            }
        }
    }

    public static List<Integer> getAllQuestIds() {
        List<Integer> questIds = new ArrayList<>();
        for (short questId = FIRST_QUEST_ID; questId <= LAST_QUEST_ID; questId++) {
            questIds.add((int) questId);
        }
        return questIds;
    }

    public static Stage getStage(int stageIndex) {
        if (stageIndex < 1 || stageIndex > STAGES.length) {
            return null;
        }
        return STAGES[stageIndex - 1];
    }

    public static Stage getStageByQuestId(int questId) {
        if (!isQuestId(questId)) {
            return null;
        }
        return STAGES[questId - FIRST_QUEST_ID];
    }

    public static Optional<Integer> resolveCurrentQuestId(Character chr) {
        if (chr == null || !isEligibleMage(chr)) {
            return Optional.empty();
        }

        StaffState staffState = getStaffState(chr);
        if (staffState.total() == 0) {
            Stage firstStage = STAGES[0];
            return chr.getLevel() >= firstStage.requiredLevel ? Optional.of((int) firstStage.questId) : Optional.empty();
        }

        StaffInfo current = staffState.current();
        if (current == null || current.stage() >= STAGES.length) {
            return Optional.empty();
        }

        Stage nextStage = STAGES[current.stage()];
        if (chr.getLevel() < nextStage.requiredLevel) {
            return Optional.empty();
        }
        return Optional.of((int) nextStage.questId);
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
            return;
        }

        StaffInfo current = staffState.current();
        int currentStage = current == null ? 0 : current.stage();
        for (Stage stage : STAGES) {
            if (stage.index <= currentStage) {
                setQuestStatus(chr, stage.questId, QuestStatus.Status.COMPLETED, announce, null, true);
                continue;
            }

            if (stage.index == currentStage + 1 && chr.getQuestStatus(stage.questId) == QuestStatus.Status.STARTED.getId()) {
                String progress = currentStepReady(chr, stage) ? PROGRESS_READY : PROGRESS_NOT_READY;
                setQuestStatus(chr, stage.questId, QuestStatus.Status.STARTED, announce, progress, true);
                continue;
            }

            setQuestStatus(chr, stage.questId, QuestStatus.Status.NOT_STARTED, announce, null, false);
            if (stage.index > currentStage + 1) {
                resetStageBridges(chr, stage);
            }
        }
    }

    private static void syncNoStaffState(Character chr, boolean announce) {
        for (Stage stage : STAGES) {
            if (stage.index == 1 && chr.getQuestStatus(stage.questId) == QuestStatus.Status.STARTED.getId()) {
                String progress = currentStepReady(chr, stage) ? PROGRESS_READY : PROGRESS_NOT_READY;
                setQuestStatus(chr, stage.questId, QuestStatus.Status.STARTED, announce, progress, true);
                continue;
            }
            setQuestStatus(chr, stage.questId, QuestStatus.Status.NOT_STARTED, announce, null, false);
            resetStageBridges(chr, stage);
        }
    }

    public static StartResult startStage(Character chr, int questId) {
        Stage stage = getStageByQuestId(questId);
        StartValidation validation = validateStart(chr, stage);
        if (!validation.isOk()) {
            syncQuestState(chr);
            return StartResult.fail(validation.getMessage());
        }

        setQuestStatus(chr, stage.questId, QuestStatus.Status.STARTED, true, PROGRESS_NOT_READY, false);
        syncQuestState(chr);
        return StartResult.success(stageStartedMessage(stage));
    }

    public static String startPrompt(Character chr, int questId) {
        Stage stage = getStageByQuestId(questId);
        StartValidation validation = validateStart(chr, stage);
        if (!validation.isOk()) {
            return validation.getMessage();
        }

        return "#e" + stage.name + "#n\r\n\r\n"
                + "元素的回声会沿着你的武器留下痕迹。这次共鸣会分成三步推进：先取回 Boss 共鸣凭证，再交付重铸材料，最后选择新的元素杖。\r\n\r\n"
                + stageRequirementText(chr, stage, -1)
                + "\r\n每完成一步都回到汉斯身边确认，下一步才会开启。";
    }

    public static String progressText(Character chr, int questId) {
        Stage stage = getStageByQuestId(questId);
        if (stage == null) {
            return "这个元素共鸣任务暂时无法处理。";
        }

        StringBuilder text = new StringBuilder("#e").append(stage.name).append("#n\r\n\r\n");
        text.append(stageRequirementText(chr, stage, -1));
        Step step = currentStep(chr, stage);
        if (step == Step.REWARD) {
            CompletionValidation validation = validateCompletion(chr, stage, -1);
            if (validation.isOk()) {
                text.append("\r\n#b重铸准备已经完成。点击完成书本选择新的元素杖。#k");
                return text.toString();
            }
            text.append("\r\n#r当前不能重铸：#k").append(validation.getMessage());
            return text.toString();
        }

        StepAdvanceValidation validation = validateStepAdvance(chr, stage);
        if (validation.isOk()) {
            text.append("\r\n#b当前步骤已经完成。点击完成书本向汉斯报告，推进到下一步。#k");
            return text.toString();
        }

        text.append("\r\n#r当前步骤未完成：#k").append(validation.getMessage());
        return text.toString();
    }

    public static boolean isFinalRewardStep(Character chr, int questId) {
        Stage stage = getStageByQuestId(questId);
        return stage != null && currentStep(chr, stage) == Step.REWARD;
    }

    public static StepAdvanceValidation validateStepAdvance(Character chr, int questId) {
        return validateStepAdvance(chr, getStageByQuestId(questId));
    }

    public static String advancePrompt(Character chr, int questId) {
        Stage stage = getStageByQuestId(questId);
        StepAdvanceValidation validation = validateStepAdvance(chr, stage);
        if (!validation.isOk()) {
            return validation.getMessage();
        }

        Step step = currentStep(chr, stage);
        StringBuilder text = new StringBuilder("#e").append(stage.name).append("#n\r\n\r\n");
        text.append(stageRequirementText(chr, stage, -1)).append("\r\n");
        if (step == Step.BOSS_TOKENS) {
            text.append("要把这些 Boss 共鸣凭证交给汉斯分析吗？\r\n\r\n");
            text.append("#r交付后会消耗这些凭证，并开启普通材料准备步骤。#k");
            return text.toString();
        }

        text.append("要把这些普通材料和基础金币交给汉斯完成重铸准备吗？\r\n\r\n");
        text.append("#r交付后会消耗普通材料和基础金币，并开启最终元素杖选择。#k");
        return text.toString();
    }

    public static String rewardSelectionPrompt(Character chr, int questId) {
        Stage stage = getStageByQuestId(questId);
        CompletionValidation validation = validateCompletion(chr, stage, -1);
        if (!validation.isOk()) {
            return validation.getMessage();
        }

        StringBuilder text = new StringBuilder("#e选择元素杖#n\r\n\r\n");
        text.append(stageRequirementText(chr, stage, -1));
        text.append("\r\n请选择这次要共鸣的属性：\r\n");
        for (int i = 0; i < stage.rewardItemIds.length; i++) {
            int itemId = stage.rewardItemIds[i];
            text.append("#L").append(i).append("##i").append(itemId).append("# #t").append(itemId).append("#  ")
                    .append(ELEMENTS[i].displayName()).append("#l\r\n");
        }
        return text.toString();
    }

    public static String completionPrompt(Character chr, int questId, int selection) {
        Stage stage = getStageByQuestId(questId);
        CompletionValidation validation = validateCompletion(chr, stage, selection);
        if (!validation.isOk()) {
            return validation.getMessage();
        }

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
        Stage stage = getStageByQuestId(questId);
        StepAdvanceValidation validation = validateStepAdvance(chr, stage);
        if (!validation.isOk()) {
            syncQuestState(chr);
            return StepAdvanceResult.fail(validation.getMessage());
        }

        Step step = currentStep(chr, stage);
        if (step == Step.BOSS_TOKENS) {
            removeRequirements(chr, tokenRequirements(stage));
            setBridgeCompleted(chr, stage.bossBridgeQuestId());
            setQuestStatus(chr, stage.questId, QuestStatus.Status.STARTED, true,
                    currentStepReady(chr, stage) ? PROGRESS_READY : PROGRESS_NOT_READY, true);
            return StepAdvanceResult.success("#e" + stage.name + "#n\r\n\r\n"
                    + "Boss 共鸣凭证已经确认。\r\n\r\n下一步：准备普通材料和基础金币，再回到汉斯身边交付。");
        }

        removeRequirements(chr, stage.baseRequirements);
        setBridgeCompleted(chr, stage.materialBridgeQuestId());
        if (stage.baseMeso > 0) {
            chr.gainMeso(-stage.baseMeso, true, false, true);
        }
        setQuestStatus(chr, stage.questId, QuestStatus.Status.STARTED, true,
                currentStepReady(chr, stage) ? PROGRESS_READY : PROGRESS_NOT_READY, true);
        return StepAdvanceResult.success("#e" + stage.name + "#n\r\n\r\n"
                + "普通材料已经交付，元素杖的重铸准备完成。\r\n\r\n下一步：点击完成书本，选择这次要共鸣的元素杖。");
    }

    public static CompletionResult completeStage(Character chr, int questId, int selection) {
        Stage stage = getStageByQuestId(questId);
        CompletionValidation validation = validateCompletion(chr, stage, selection);
        if (!validation.isOk()) {
            syncQuestState(chr);
            return CompletionResult.fail(validation.getMessage());
        }

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

        Quest.getInstance(stage.questId).forceComplete(chr, NPC_ID);
        syncQuestState(chr);
        return CompletionResult.success("#e元素共鸣完成#n\r\n\r\n你获得了 #b#i" + rewardItemId + "##t"
                + rewardItemId + "##k。\r\n" + nextStageMessage(stage));
    }

    public static CompletionValidation validateCompletion(Character chr, int questId, int selection) {
        return validateCompletion(chr, getStageByQuestId(questId), selection);
    }

    public static StartValidation validateStart(Character chr, int questId) {
        return validateStart(chr, getStageByQuestId(questId));
    }

    private static StartValidation validateStart(Character chr, Stage stage) {
        if (chr == null) {
            return StartValidation.fail("角色状态异常，暂时无法开始元素共鸣。");
        }
        if (stage == null) {
            return StartValidation.fail("这个元素共鸣任务暂时无法处理。");
        }
        if (!isEligibleMage(chr)) {
            return StartValidation.fail("元素共鸣只开放给完成二转的法师。");
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
        return StartValidation.success();
    }

    private static CompletionValidation validateCompletion(Character chr, Stage stage, int selection) {
        StartValidation startValidation = validateStartedQuest(chr, stage);
        if (!startValidation.isOk()) {
            return CompletionValidation.fail(startValidation.getMessage());
        }

        if (currentStep(chr, stage) != Step.REWARD) {
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

    private static StartValidation validateStartedQuest(Character chr, Stage stage) {
        StartValidation startValidation = validateStart(chr, stage);
        if (!startValidation.isOk()) {
            return startValidation;
        }
        if (chr.getQuestStatus(stage.questId) != QuestStatus.Status.STARTED.getId()) {
            return StartValidation.fail("请先在汉斯处接受这个元素共鸣任务。");
        }
        return StartValidation.success();
    }

    private static StepAdvanceValidation validateStepAdvance(Character chr, Stage stage) {
        StartValidation startValidation = validateStartedQuest(chr, stage);
        if (!startValidation.isOk()) {
            return StepAdvanceValidation.fail(startValidation.getMessage());
        }

        Step step = currentStep(chr, stage);
        if (step == Step.REWARD) {
            return StepAdvanceValidation.fail("重铸准备已经完成，请选择新的元素杖。");
        }

        if (step == Step.BOSS_TOKENS) {
            return validateRequirements(chr, tokenRequirements(stage), 0);
        }

        return validateRequirements(chr, stage.baseRequirements, stage.baseMeso);
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
        return jobId >= Job.FP_WIZARD.getId() && jobId < 300;
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

    private static Step currentStep(Character chr, Stage stage) {
        if (chr == null || stage == null) {
            return Step.BOSS_TOKENS;
        }
        if (isBridgeCompleted(chr, stage.materialBridgeQuestId())) {
            return Step.REWARD;
        }
        if (isBridgeCompleted(chr, stage.bossBridgeQuestId())) {
            return Step.BASE_MATERIALS;
        }
        return Step.BOSS_TOKENS;
    }

    private static boolean currentStepReady(Character chr, Stage stage) {
        Step step = currentStep(chr, stage);
        if (step == Step.REWARD) {
            return validateCompletion(chr, stage, -1).isOk();
        }
        return validateStepAdvance(chr, stage).isOk();
    }

    private static boolean isBridgeCompleted(Character chr, short questId) {
        return chr.getQuestStatus(questId) == QuestStatus.Status.COMPLETED.getId();
    }

    private static void setBridgeCompleted(Character chr, short questId) {
        QuestStatus newStatus = new QuestStatus(Quest.getInstance(questId), QuestStatus.Status.COMPLETED, NPC_ID);
        synchronized (chr.getQuests()) {
            chr.getQuests().put(questId, newStatus);
        }
    }

    private static void resetStageBridges(Character chr, Stage stage) {
        setBridgeNotStarted(chr, stage.bossBridgeQuestId());
        setBridgeNotStarted(chr, stage.materialBridgeQuestId());
    }

    private static void setBridgeNotStarted(Character chr, short questId) {
        Quest quest = Quest.getInstance(questId);
        QuestStatus oldStatus = chr.getQuestNoAdd(quest);
        if (oldStatus == null || oldStatus.getStatus() == QuestStatus.Status.NOT_STARTED) {
            return;
        }
        synchronized (chr.getQuests()) {
            chr.getQuests().put(questId, new QuestStatus(quest, QuestStatus.Status.NOT_STARTED, NPC_ID));
        }
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

    private static String stageStartedMessage(Stage stage) {
        return "#e" + stage.name + "#n\r\n\r\n"
                + "去击败指定 Boss，收集共鸣凭证和材料。准备好后回到汉斯身边，我会替你完成这次元素共鸣。";
    }

    private static String nextStageMessage(Stage stage) {
        if (stage.index >= STAGES.length) {
            return "元素共鸣已经抵达终章。";
        }
        Stage next = STAGES[stage.index];
        return "下一阶段会在 #r" + next.requiredLevel + "#k 级开启。";
    }

    private static String stageRequirementText(Character chr, Stage stage, int selection) {
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

        Step step = currentStep(chr, stage);
        if (step == Step.BOSS_TOKENS) {
            text.append("\r\n#e当前步骤 1/3：收集 Boss 共鸣凭证#n\r\n");
            text.append(requirementText(chr, tokenRequirements(stage)));
            text.append("下一步：带着凭证回到汉斯身边报告。");
            return text.toString();
        }

        if (step == Step.BASE_MATERIALS) {
            text.append("\r\n#e当前步骤 2/3：交付普通材料#n\r\n");
            text.append(requirementText(chr, stage.baseRequirements));
            text.append("基础金币：#b").append(formatMeso(chr.getMeso())).append("#k / #r")
                    .append(formatMeso(stage.baseMeso)).append("#k\r\n");
            text.append("下一步：材料交付后再选择新的元素杖。");
            return text.toString();
        }

        text.append("\r\n#e当前步骤 3/3：选择元素杖#n\r\n");
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
        text.append("下一步：选择本次共鸣的属性。");
        return text.toString();
    }

    private static String completedStepText(Character chr, Stage stage) {
        List<String> completed = new ArrayList<>();
        if (isBridgeCompleted(chr, stage.bossBridgeQuestId())) {
            completed.add("Boss 共鸣凭证已报告");
        }
        if (isBridgeCompleted(chr, stage.materialBridgeQuestId())) {
            completed.add("普通材料已交付");
        }
        if (completed.isEmpty()) {
            return "无";
        }
        return String.join("、", completed);
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

    private static List<Requirement> tokenRequirements(Stage stage) {
        List<Requirement> requirements = new ArrayList<>();
        for (int tokenId : stage.bossTokenIds) {
            requirements.add(req(tokenId, 1));
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

    private enum Step {
        BOSS_TOKENS,
        BASE_MATERIALS,
        REWARD
    }

    private record Element(String displayName, String themeName, String finalName) {
    }

    public record Requirement(int itemId, int count) {
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
        private final List<Integer> bossTokenIds;
        private final List<Requirement> baseRequirements;
        private final int baseMeso;
        private final List<Requirement> switchRequirements;
        private final int switchMeso;

        private Stage(int index, short questId, int requiredLevel, String name, int[] rewardItemIds,
                      List<Integer> bossTokenIds, List<Requirement> baseRequirements, int baseMeso,
                      List<Requirement> switchRequirements, int switchMeso) {
            this.index = index;
            this.questId = questId;
            this.requiredLevel = requiredLevel;
            this.name = name;
            this.rewardItemIds = rewardItemIds;
            this.bossTokenIds = List.copyOf(bossTokenIds);
            this.baseRequirements = List.copyOf(baseRequirements);
            this.baseMeso = baseMeso;
            this.switchRequirements = List.copyOf(switchRequirements);
            this.switchMeso = switchMeso;
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
            return bossTokenIds;
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

        private short bossBridgeQuestId() {
            return (short) (FIRST_BRIDGE_QUEST_ID + (index - 1) * BRIDGES_PER_STAGE);
        }

        private short materialBridgeQuestId() {
            return (short) (bossBridgeQuestId() + 1);
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
}

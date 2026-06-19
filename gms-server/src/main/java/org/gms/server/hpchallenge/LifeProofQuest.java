package org.gms.server.hpchallenge;

import org.gms.client.Character;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.constants.game.DelayedQuestUpdate;
import org.gms.constants.inventory.ItemConstants;
import org.gms.server.life.MonsterDropEntry;
import org.gms.server.quest.Quest;
import org.gms.server.quest.hook.InteractionHookAction;
import org.gms.server.quest.hook.InteractionHookContext;
import org.gms.util.PacketCreator;
import org.gms.util.StringUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LifeProofQuest {
    public static final int FIRST_QUEST_ID = 5100;
    public static final int LAST_QUEST_ID = 5974;
    public static final int BLOCK_SIZE = 25;
    public static final int MAIN_SLOT_START = 0;
    public static final int MAIN_SLOT_END = 8;
    public static final int SELECTOR_SLOT_START = 9;
    public static final int OPTION_SLOT_START = 12;
    public static final int OPTION_SLOT_END = 19;
    public static final int REWARD_SLOT = 20;
    public static final int BRIDGE_SLOT_START = 21;
    public static final int RESERVED_SLOT = 24;
    public static final int OPTIONAL_REQUIRED_COUNT = 3;
    public static final int PROOF_ITEM_ID = 4033011;
    public static final int DYNAMIC_DROP_CHANCE = 350000;
    private static final int CUSTOM_PROGRESS_KEY = 0;

    private static final String TOPIC = "生命之证";
    private static final String OK_PREFIX = "OK|";
    private static final String ERR_PREFIX = "ERR|";
    private static final String INFO_PREFIX = "INFO|";
    private static final String READY_PREFIX = "READY|";

    private static final List<HpChallengeService.JobBranch> BRANCH_ORDER = List.of(
            HpChallengeService.JobBranch.WARRIOR,
            HpChallengeService.JobBranch.MAGE,
            HpChallengeService.JobBranch.BOWMAN,
            HpChallengeService.JobBranch.THIEF,
            HpChallengeService.JobBranch.PIRATE
    );
    private static final Map<HpChallengeService.JobBranch, BranchInfo> BRANCH_INFO = buildBranchInfo();
    private static final Map<String, ItemCollection> ITEM_COLLECTIONS = buildItemCollections();
    private static final Map<Integer, QuestMeta> QUESTS = buildQuests();

    private LifeProofQuest() {
    }

    enum QuestKind {
        MAIN,
        SELECTOR,
        OPTION,
        REWARD,
        BRIDGE,
        RESERVED
    }

    enum ObjectiveType {
        KILL,
        BOSS,
        ITEM,
        PQ_ANY,
        PQ_PIRATE,
        PQ_TOY_OR_PIRATE,
        MESO,
        SCROLL_100,
        NPC_TALK,
        JUMP_MANUAL,
        SELECT_OPTION,
        REWARD
    }

    record BranchInfo(String name, int instructorNpcId, List<Integer> jobIds) {
    }

    record ItemCollection(int itemId, String itemName, int requiredCount, List<Integer> droppers) {
    }

    record Objective(ObjectiveType type, int requiredCount, String description, List<Integer> targetIds, int itemId,
                     int mesoCost) {
        boolean isCustomProgress() {
            return switch (type) {
                case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, NPC_TALK, JUMP_MANUAL -> true;
                default -> false;
            };
        }

        boolean requiresProofItem() {
            return false;
        }

        boolean isCollection() {
            return type == ObjectiveType.ITEM;
        }
    }

    record QuestMeta(int questId, int stage, HpChallengeService.JobBranch branch, QuestKind kind, int slot,
                     int optionNo, int selectorNo, HpChallengeService.Task task, Objective objective, String name) {
        boolean isVisible() {
            return kind != QuestKind.BRIDGE && kind != QuestKind.RESERVED;
        }
    }

    public static boolean isQuestId(int questId) {
        return questId >= FIRST_QUEST_ID && questId <= LAST_QUEST_ID;
    }

    public static boolean isVisibleQuestId(int questId) {
        QuestMeta meta = QUESTS.get(questId);
        return meta != null && meta.isVisible();
    }

    public static boolean isSelectorQuest(int questId) {
        QuestMeta meta = QUESTS.get(questId);
        return meta != null && meta.kind() == QuestKind.SELECTOR;
    }

    public static boolean isForfeitBlocked(int questId) {
        return isQuestId(questId);
    }

    public static int startedVisibleQuestIdForNpc(Character chr, int npcId) {
        if (chr == null || npcId <= 0) {
            return 0;
        }
        return QUESTS.values().stream()
                .filter(QuestMeta::isVisible)
                .filter(meta -> canOpenStartedQuestAtNpc(meta, npcId))
                .filter(meta -> chr.getQuestStatus(meta.questId()) == QuestStatus.Status.STARTED.getId())
                .mapToInt(QuestMeta::questId)
                .findFirst()
                .orElse(0);
    }

    public static int npcEntryQuestId(Character chr, int npcId) {
        int activeQuestId = startedVisibleQuestIdForNpc(chr, npcId);
        if (activeQuestId > 0) {
            return activeQuestId;
        }
        if (chr == null || npcId <= 0 || !HpChallengeService.ensureLifeProofState(chr)) {
            return 0;
        }
        HpChallengeService.JobBranch branch = HpChallengeService.branch(chr.getJob());
        BranchInfo branchInfo = BRANCH_INFO.get(branch);
        if (branchInfo == null || branchInfo.instructorNpcId() != npcId || currentStartedVisibleQuest(chr) != null) {
            return 0;
        }
        QuestMeta next = nextAvailableQuest(chr, branch);
        if (next == null || !canOpenProgressAtNpc(next.questId(), npcId)) {
            return 0;
        }
        return next.questId();
    }

    public static List<Integer> npcHookIds(Character chr) {
        if (chr == null || !HpChallengeService.ensureLifeProofState(chr)) {
            return List.of();
        }
        HpChallengeService.JobBranch branch = HpChallengeService.branch(chr.getJob());
        BranchInfo branchInfo = BRANCH_INFO.get(branch);
        if (branchInfo == null) {
            return List.of();
        }
        Set<Integer> npcIds = new LinkedHashSet<>();
        QuestMeta active = currentStartedVisibleQuest(chr);
        if (active != null) {
            npcIds.add(startNpcId(active));
            npcIds.add(completeNpcId(active));
            return List.copyOf(npcIds);
        }
        QuestMeta next = nextAvailableQuest(chr, branch);
        if (next != null) {
            npcIds.add(startNpcId(next));
        }
        return List.copyOf(npcIds);
    }

    public static boolean containsQuest(int questId) {
        return isVisibleQuestId(questId);
    }

    public static List<Integer> getAllQuestIds() {
        return visibleQuestIds();
    }

    public static List<Integer> getHookQuestIds(Character chr) {
        if (chr == null) {
            return visibleQuestIds();
        }
        Set<Integer> questIds = new LinkedHashSet<>();
        for (QuestMeta meta : QUESTS.values()) {
            if (meta.isVisible() && chr.getQuestStatus(meta.questId()) != QuestStatus.Status.NOT_STARTED.getId()) {
                questIds.add(meta.questId());
            }
        }
        resolveCurrentQuestId(chr).ifPresent(questIds::add);
        return List.copyOf(questIds);
    }

    public static List<Integer> getCurrentInstructorNpcIds(Character chr) {
        return npcHookIds(chr);
    }

    public static Optional<Integer> resolveCurrentQuestId(Character chr) {
        if (chr == null || !HpChallengeService.ensureLifeProofState(chr)) {
            return Optional.empty();
        }
        QuestMeta active = currentStartedVisibleQuest(chr);
        if (active != null) {
            return Optional.of(active.questId());
        }
        HpChallengeService.JobBranch branch = HpChallengeService.branch(chr.getJob());
        QuestMeta next = nextAvailableQuest(chr, branch);
        return next == null ? Optional.empty() : Optional.of(next.questId());
    }

    public static Optional<Integer> resolveNpcHook(Character chr, int npcId) {
        int questId = npcEntryQuestId(chr, npcId);
        return questId > 0 ? Optional.of(questId) : Optional.empty();
    }

    public static InteractionHookAction resolveCurrentAction(Character chr, int questId) {
        if (chr == null || questId <= 0) {
            return InteractionHookAction.QUERY_PROGRESS;
        }
        if (chr.getQuestStatus(questId) == QuestStatus.Status.NOT_STARTED.getId()) {
            return InteractionHookAction.QUERY_START;
        }
        if (chr.getQuestStatus(questId) == QuestStatus.Status.STARTED.getId()) {
            QuestMeta meta = QUESTS.get(questId);
            int npcId = meta == null ? 0 : completeNpcId(meta);
            return meta != null && objectiveSatisfied(chr, meta, npcId)
                    ? InteractionHookAction.QUERY_COMPLETE
                    : InteractionHookAction.QUERY_PROGRESS;
        }
        return InteractionHookAction.QUERY_PROGRESS;
    }

    public static void openHook(InteractionHookContext context) {
        if (context == null || context.player() == null) {
            return;
        }
        Character chr = context.player();
        QuestMeta meta = QUESTS.get(context.questId());
        if (meta == null || !meta.isVisible()) {
            context.sendOk("这个任务暂时无法处理。");
            return;
        }

        int npcId = effectiveNpcId(context);
        byte status = chr.getQuestStatus(meta.questId());
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            if (npcId <= 0 || npcId != startNpcId(meta)) {
                context.sendOk("请前往#p" + startNpcId(meta) + "#领取这一步生命之证试炼。");
                return;
            }
            if (meta.kind() == QuestKind.SELECTOR) {
                context.sendSimple(selectionMenu(chr, meta.questId()));
                return;
            }
            context.sendYesNo(startPrompt(chr, meta.questId()));
            return;
        }
        if (status == QuestStatus.Status.STARTED.getId()) {
            String prompt = endPrompt(chr, meta.questId(), npcId);
            if (isReadyResult(prompt)) {
                context.sendYesNo(resultMessage(prompt));
                return;
            }
            context.sendOk(resultMessage(prompt));
            return;
        }
        context.sendOk(meta.name() + "已经完成。");
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
        QuestMeta meta = QUESTS.get(context.questId());
        if (meta == null || !meta.isVisible()) {
            context.sendOk("这个任务暂时无法处理。");
            return;
        }

        int npcId = effectiveNpcId(context);
        byte status = chr.getQuestStatus(meta.questId());
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            startFromHook(context, chr, meta, npcId, selection);
            return;
        }
        if (status == QuestStatus.Status.STARTED.getId()) {
            completeFromHook(context, chr, meta, npcId);
            return;
        }
        context.sendOk(meta.name() + "已经完成。");
    }

    private static int effectiveNpcId(InteractionHookContext context) {
        return context.sourceNpcId();
    }

    private static void startFromHook(InteractionHookContext context, Character chr, QuestMeta meta, int npcId,
                                      int selection) {
        Quest quest = Quest.getInstance(meta.questId());
        if (!canStartAtNpc(chr, quest, meta, npcId)) {
            context.sendOk("请前往#p" + startNpcId(meta) + "#领取这一步生命之证试炼。");
            return;
        }

        if (meta.kind() == QuestKind.SELECTOR) {
            String result = selectOptional(chr, meta.questId(), selection, npcId);
            if (!isOkResult(result)) {
                context.sendOk(resultMessage(result));
                return;
            }
            int selectedQuest = selectedQuestId(result);
            if (selectedQuest <= 0) {
                context.sendOk("选择试炼失败，请重新打开任务。");
                return;
            }
            quest.forceStart(chr, npcId);
            quest.forceComplete(chr, npcId);
            Quest.getInstance(selectedQuest).forceStart(chr, npcId);
            onStarted(chr, selectedQuest);
            context.sendOk(selectedMessage(result));
            return;
        }

        String result = startQuest(chr, meta.questId());
        if (isOkResult(result)) {
            quest.forceStart(chr, npcId);
            onStarted(chr, meta.questId());
        }
        context.sendOk(resultMessage(result));
    }

    private static void completeFromHook(InteractionHookContext context, Character chr, QuestMeta meta, int npcId) {
        String prompt = endPrompt(chr, meta.questId(), npcId);
        if (!isReadyResult(prompt)) {
            context.sendOk(resultMessage(prompt));
            return;
        }

        String result = complete(chr, meta.questId(), npcId);
        if (isOkResult(result)) {
            Quest.getInstance(meta.questId()).forceComplete(chr, npcId);
        }
        context.sendOk(resultMessage(result));
    }

    static boolean canOpenProgressAtNpc(int questId, int npcId) {
        QuestMeta meta = QUESTS.get(questId);
        if (meta == null || !meta.isVisible() || npcId <= 0) {
            return false;
        }
        return canOpenStartedQuestAtNpc(meta, npcId);
    }

    static Collection<QuestMeta> allVisibleQuests() {
        return QUESTS.values().stream()
                .filter(QuestMeta::isVisible)
                .toList();
    }

    public static List<Integer> visibleQuestIds() {
        return QUESTS.values().stream()
                .filter(QuestMeta::isVisible)
                .map(QuestMeta::questId)
                .toList();
    }

    static Collection<QuestMeta> allQuestMetas() {
        return Collections.unmodifiableCollection(QUESTS.values());
    }

    public static String startPrompt(Character chr, int questId) {
        QuestMeta meta = QUESTS.get(questId);
        if (meta == null || !meta.isVisible()) {
            return "这个任务暂时无法处理。";
        }
        if (!HpChallengeService.ensureLifeProofState(chr)) {
            return HpChallengeService.openRequirementText(chr);
        }
        if (meta.kind() == QuestKind.SELECTOR) {
            return selectionMenu(chr, questId);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("#e").append(meta.name()).append("#n\r\n\r\n");
        sb.append(stageStory(meta.stage())).append("\r\n\r\n");
        if (meta.objective().type() == ObjectiveType.NPC_TALK) {
            sb.append(mentorStartLine(meta)).append("\r\n\r\n");
        }
        sb.append("目标：").append(meta.objective().description()).append("\r\n");
        appendObjectiveHint(sb, meta);
        sb.append("\r\n是否接受这一步试炼？");
        return sb.toString();
    }

    public static String startQuest(Character chr, int questId) {
        QuestMeta meta = QUESTS.get(questId);
        if (meta == null || !meta.isVisible()) {
            return error("这个任务暂时无法处理。");
        }
        if (!HpChallengeService.ensureLifeProofState(chr)) {
            return error(HpChallengeService.openRequirementText(chr));
        }
        return ok("已接受：" + meta.name());
    }

    public static void onStarted(Character chr, int questId) {
        QuestMeta meta = QUESTS.get(questId);
        if (chr == null || meta == null || !meta.objective().isCustomProgress()) {
            return;
        }
        QuestStatus status = chr.getQuest(Quest.getInstance(questId));
        status.setProgress(CUSTOM_PROGRESS_KEY, "000");
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
    }

    public static String selectionMenu(Character chr, int selectorQuestId) {
        QuestMeta selector = QUESTS.get(selectorQuestId);
        if (selector == null || selector.kind() != QuestKind.SELECTOR) {
            return "当前没有可选择的试炼。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("#e").append(selector.name()).append("#n\r\n\r\n");
        sb.append("在这一轮试炼中选择一个方向。选定后需要完成它，才会开放下一轮选择。\r\n\r\n");
        List<QuestMeta> options = availableOptions(chr, selector);
        if (options.isEmpty()) {
            sb.append("#L0#暂无可选择的试炼#l");
            return sb.toString();
        }
        for (QuestMeta option : options) {
            sb.append("#L").append(option.optionNo()).append("#")
                    .append(option.optionNo()).append(". ")
                    .append(option.objective().description())
                    .append("#l\r\n");
        }
        return sb.toString();
    }

    public static String selectOptional(Character chr, int selectorQuestId, int optionNo, int npcId) {
        QuestMeta selector = QUESTS.get(selectorQuestId);
        if (selector == null || selector.kind() != QuestKind.SELECTOR) {
            return error("当前没有可选择的试炼。");
        }
        if (optionNo <= 0) {
            return error("请选择一个有效试炼。");
        }
        QuestMeta option = stageBranchOptions(selector.stage(), selector.branch()).stream()
                .filter(meta -> meta.optionNo() == optionNo)
                .findFirst()
                .orElse(null);
        if (option == null) {
            return error("选择的试炼不存在。");
        }
        if (!availableOptions(chr, selector).contains(option)) {
            return error("该试炼当前不能选择。");
        }
        return OK_PREFIX + option.questId() + "|" + "已选择：" + option.objective().description();
    }

    public static boolean isOkResult(String result) {
        return result != null && result.startsWith(OK_PREFIX);
    }

    public static boolean isInfoResult(String result) {
        return result != null && result.startsWith(INFO_PREFIX);
    }

    public static boolean isReadyResult(String result) {
        return result != null && result.startsWith(READY_PREFIX);
    }

    public static String resultMessage(String result) {
        if (result == null) {
            return "";
        }
        int separator = result.indexOf('|');
        if (separator < 0) {
            return result;
        }
        return result.substring(separator + 1);
    }

    public static int selectedQuestId(String result) {
        if (result == null || !result.startsWith(OK_PREFIX)) {
            return 0;
        }
        int separator = result.indexOf('|', OK_PREFIX.length());
        if (separator < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(result.substring(OK_PREFIX.length(), separator));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static String selectedMessage(String result) {
        if (result == null || !result.startsWith(OK_PREFIX)) {
            return "";
        }
        int separator = result.indexOf('|', OK_PREFIX.length());
        if (separator < 0) {
            return "";
        }
        return result.substring(separator + 1);
    }

    public static String completePrompt(Character chr, int questId) {
        QuestMeta meta = QUESTS.get(questId);
        if (meta == null || !meta.isVisible()) {
            return "这个任务暂时无法处理。";
        }
        return completePrompt(chr, meta, completeNpcId(meta));
    }

    private static String completePrompt(Character chr, QuestMeta meta, int npcId) {
        StringBuilder sb = new StringBuilder();
        sb.append("#e").append(meta.name()).append("#n\r\n\r\n");
        if (meta.kind() == QuestKind.REWARD) {
            sb.append("这枚印记已经完整。确认领取本阶段的生命之证奖励？");
            return sb.toString();
        }
        if (meta.objective().type() == ObjectiveType.NPC_TALK) {
            sb.append(mentorCompletionLine(npcId)).append("\r\n\r\n");
            sb.append("确认由#p").append(npcId).append("#记录这一步生命之证？");
            return sb.toString();
        }
        sb.append("确认提交这一步试炼？\r\n\r\n");
        sb.append("目标：").append(meta.objective().description());
        appendObjectiveHint(sb, meta);
        return sb.toString();
    }

    public static String endPrompt(Character chr, int questId, int npcId) {
        QuestMeta meta = QUESTS.get(questId);
        if (meta == null || !meta.isVisible()) {
            return error("这个任务暂时无法处理。");
        }
        if (!HpChallengeService.ensureLifeProofState(chr)) {
            return error(HpChallengeService.openRequirementText(chr));
        }
        Quest quest = Quest.getInstance(questId);
        if (chr.getQuest(quest).getStatus() != QuestStatus.Status.STARTED) {
            return error("这一步生命之证试炼当前没有进行中。");
        }
        if (canConfirmAtNpc(chr, quest, meta, npcId)) {
            return ready(completePrompt(chr, meta, npcId));
        }
        return info(progressPrompt(chr, meta, npcId));
    }

    public static String complete(Character chr, int questId, int npcId) {
        QuestMeta meta = QUESTS.get(questId);
        if (meta == null || !meta.isVisible()) {
            return error("这个任务暂时无法处理。");
        }
        Quest quest = Quest.getInstance(questId);
        if (chr.getQuest(quest).getStatus() != QuestStatus.Status.STARTED) {
            return error("这一步生命之证试炼当前没有进行中。");
        }
        if (meta.objective().type() == ObjectiveType.NPC_TALK && npcId == completeNpcId(meta)) {
            markNpcTalkProgress(chr, meta, npcId, false);
        }
        if (!canSubmitAtNpc(chr, quest, npcId)) {
            return error(submitBlockedText(chr, meta, npcId));
        }
        if (meta.kind() == QuestKind.REWARD) {
            if (HpChallengeService.hasActiveReward(chr.getId(), meta.stage())) {
                return ok("该阶段奖励已记录，任务状态将同步完成。");
            }
            String result = HpChallengeService.claimLifeProofStageReward(chr, meta.stage());
            if (result.startsWith("领取成功")) {
                chr.yellowMessage("生命之证：" + stageTitle(meta.stage()) + "奖励已领取。");
                return ok(result);
            }
            return error(result);
        }

        Objective objective = meta.objective();
        if (objective.isCollection() && !removeItem(chr, objective.itemId(), objective.requiredCount())) {
            return error("提交物品不足。");
        }
        if (objective.type() == ObjectiveType.MESO) {
            if (chr.getMeso() < objective.mesoCost()) {
                return error("金币不足，需要 " + objective.mesoCost() + " 金币。");
            }
            chr.gainMeso(-objective.mesoCost(), true, true, true);
        }
        if (meta.kind() == QuestKind.OPTION) {
            completeNextBridgeSilently(chr, meta);
        }
        chr.yellowMessage("生命之证：" + meta.name() + "完成。");
        return ok("已完成：" + meta.name());
    }

    private static String progressPrompt(Character chr, QuestMeta meta, int npcId) {
        StringBuilder sb = new StringBuilder();
        sb.append("#e").append(meta.name()).append("#n\r\n\r\n");
        sb.append(stageStory(meta.stage())).append("\r\n\r\n");
        sb.append("当前目标：").append(meta.objective().description()).append("\r\n");
        sb.append("当前进度：").append(progressText(chr, meta, npcId)).append("\r\n");
        sb.append("下一步：").append(nextStepText(chr, meta, npcId));
        return sb.toString();
    }

    private static String submitBlockedText(Character chr, QuestMeta meta, int npcId) {
        int completeNpcId = completeNpcId(meta);
        if (npcId != completeNpcId) {
            if (meta.objective().type() == ObjectiveType.NPC_TALK) {
                return "请前往#p" + completeNpcId + "#，点击任务完成图标，由对方确认这一步生命之证。";
            }
            return "请前往#p" + completeNpcId + "#提交这一步生命之证试炼。";
        }
        return "当前目标还没有完成。\r\n\r\n" + progressPrompt(chr, meta, npcId);
    }

    private static String progressText(Character chr, QuestMeta meta, int npcId) {
        Objective objective = meta.objective();
        return switch (objective.type()) {
            case KILL, BOSS -> mobTargetText(objective) + "，" + mobProgress(chr, meta)
                    + "/" + objective.requiredCount();
            case ITEM -> "#i" + objective.itemId() + "# #t" + objective.itemId() + "# "
                    + itemCount(chr, objective.itemId()) + "/" + objective.requiredCount();
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL -> customProgress(chr, meta)
                    + "/" + objective.requiredCount();
            case MESO -> "当前金币 " + chr.getMeso() + " / 需要 " + objective.mesoCost();
            case NPC_TALK -> "目标 NPC：#p" + targetNpcId(meta) + "#，"
                    + customProgress(chr, meta) + "/" + objective.requiredCount();
            case SELECT_OPTION -> "等待选择 1 项附加试炼";
            case REWARD -> rewardProgressText(chr, meta);
        };
    }

    private static String nextStepText(Character chr, QuestMeta meta, int npcId) {
        int completeNpcId = completeNpcId(meta);
        if (meta.objective().type() == ObjectiveType.NPC_TALK) {
            if (npcId == completeNpcId) {
                return "点击确定后，由#p" + completeNpcId + "#确认这一步生命之证。";
            }
            return "前往#p" + completeNpcId + "#，点击任务完成图标，由对方确认这一步生命之证。";
        }
        if (!objectiveSatisfied(chr, meta, npcId)) {
            return switch (meta.objective().type()) {
                case ITEM -> "继续收集#t" + meta.objective().itemId() + "#。";
                case KILL, BOSS -> "继续完成目标怪物击杀。";
                case MESO -> "准备足够金币后回#p" + completeNpcId + "#提交。";
                case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL ->
                        "继续完成试炼进度，然后回#p" + completeNpcId + "#提交。";
                case SELECT_OPTION -> "重新打开任务，选择本轮附加试炼。";
                case REWARD -> "完成本阶段全部试炼后回#p" + completeNpcId + "#领取奖励。";
                case NPC_TALK -> throw new IllegalStateException("handled above");
            };
        }
        if (npcId != completeNpcId) {
            return "条件已满足，请前往#p" + completeNpcId + "#提交。";
        }
        return "条件已满足，可以提交这一步试炼。";
    }

    private static boolean objectiveSatisfied(Character chr, QuestMeta meta, int npcId) {
        Objective objective = meta.objective();
        return switch (objective.type()) {
            case KILL, BOSS -> mobProgress(chr, meta) >= objective.requiredCount();
            case ITEM -> itemCount(chr, objective.itemId()) >= objective.requiredCount();
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL ->
                    customProgress(chr, meta) >= objective.requiredCount();
            case MESO -> chr.getMeso() >= objective.mesoCost();
            case NPC_TALK -> customProgress(chr, meta) >= objective.requiredCount();
            case SELECT_OPTION -> false;
            case REWARD -> true;
        };
    }

    static int startNpcId(QuestMeta meta) {
        return BRANCH_INFO.get(meta.branch()).instructorNpcId();
    }

    static int completeNpcId(QuestMeta meta) {
        if (meta.objective().type() == ObjectiveType.NPC_TALK) {
            return targetNpcId(meta);
        }
        return startNpcId(meta);
    }

    private static int targetNpcId(QuestMeta meta) {
        Objective objective = meta.objective();
        if (!objective.targetIds().isEmpty()) {
            return objective.targetIds().getFirst();
        }
        return startNpcId(meta);
    }

    static boolean isNpcTalkVisitQuest(QuestMeta meta) {
        return meta != null && meta.isVisible() && meta.objective().type() == ObjectiveType.NPC_TALK;
    }

    private static boolean canOpenStartedQuestAtNpc(QuestMeta meta, int npcId) {
        return npcId == startNpcId(meta) || npcId == completeNpcId(meta);
    }

    private static boolean canStartAtNpc(Character chr, Quest quest, QuestMeta meta, int npcId) {
        return canUseNpc(chr, npcId) && npcId == startNpcId(meta) && quest.canStart(chr, npcId);
    }

    private static boolean canConfirmAtNpc(Character chr, Quest quest, QuestMeta meta, int npcId) {
        if (!canUseNpc(chr, npcId) || npcId != completeNpcId(meta)) {
            return false;
        }
        if (meta.objective().type() == ObjectiveType.NPC_TALK) {
            return quest.canComplete(chr, npcId);
        }
        return objectiveSatisfied(chr, meta, npcId) && quest.canComplete(chr, npcId);
    }

    private static boolean canSubmitAtNpc(Character chr, Quest quest, int npcId) {
        QuestMeta meta = QUESTS.get((int) quest.getId());
        return meta != null
                && canUseNpc(chr, npcId)
                && npcId == completeNpcId(meta)
                && objectiveSatisfied(chr, meta, npcId)
                && quest.canComplete(chr, npcId);
    }

    private static boolean canUseNpc(Character chr, int npcId) {
        return chr != null && chr.getMap() != null && chr.getMap().containsNPC(npcId);
    }

    private static int itemCount(Character chr, int itemId) {
        return chr.getInventory(ItemConstants.getInventoryType(itemId)).countById(itemId);
    }

    private static String mobTargetText(Objective objective) {
        if (objective.targetIds().isEmpty()) {
            return "目标怪物：未指定";
        }
        StringBuilder sb = new StringBuilder("目标怪物：");
        for (int i = 0; i < objective.targetIds().size(); i++) {
            if (i > 0) {
                sb.append("/");
            }
            sb.append("#o").append(objective.targetIds().get(i)).append("#");
        }
        return sb.toString();
    }

    private static String rewardProgressText(Character chr, QuestMeta meta) {
        HpChallengeService.RewardTarget target = HpChallengeService.rewardTarget(chr, HpChallengeService.stage(meta.stage()));
        return "阶段目标 HP " + target.targetHp() + "，阶段目标 MP " + target.targetMp();
    }

    public static void addDynamicQuestDrops(Character chr, int mobId, List<MonsterDropEntry> visibleQuestEntry) {
        if (chr == null || visibleQuestEntry == null) {
            return;
        }
        QuestMeta active = QUESTS.values().stream()
                .filter(meta -> meta.isVisible() && meta.objective().isCollection())
                .filter(meta -> chr.getQuestStatus(meta.questId()) == QuestStatus.Status.STARTED.getId())
                .filter(meta -> meta.objective().targetIds().contains(mobId))
                .findFirst()
                .orElse(null);
        if (active == null) {
            return;
        }
        int itemId = active.objective().itemId();
        int held = chr.getInventory(ItemConstants.getInventoryType(itemId)).countById(itemId);
        if (held >= active.objective().requiredCount()) {
            return;
        }
        visibleQuestEntry.add(new MonsterDropEntry(itemId, DYNAMIC_DROP_CHANCE, 1, 1, (short) active.questId()));
    }

    public static void onPartyQuestCleared(Character chr, String eventName) {
        incrementCustomProgress(chr, ObjectiveType.PQ_ANY, eventName == null ? "" : eventName, "组队任务完成");
    }

    public static void onScrollUsed(Character chr, int scrollId) {
        if (!HpChallengeService.isOneHundredPercentScroll(scrollId)) {
            return;
        }
        incrementCustomProgress(chr, ObjectiveType.SCROLL_100, "", "100% 卷轴使用成功");
    }

    public static void onMonsterKilled(Character chr, int mobId) {
        if (chr == null) {
            return;
        }
        QuestMeta active = QUESTS.values().stream()
                .filter(meta -> meta.isVisible())
                .filter(meta -> meta.objective().type() == ObjectiveType.KILL
                        || meta.objective().type() == ObjectiveType.BOSS)
                .filter(meta -> meta.objective().targetIds().size() > 1)
                .filter(meta -> meta.objective().targetIds().contains(mobId))
                .filter(meta -> chr.getQuestStatus(meta.questId()) == QuestStatus.Status.STARTED.getId())
                .findFirst()
                .orElse(null);
        if (active == null) {
            return;
        }
        QuestStatus status = chr.getQuest(Quest.getInstance(active.questId()));
        int nextProgress = Math.min(active.objective().requiredCount(),
                parseProgress(status.getProgress(mobId)) + 1);
        String progress = StringUtil.getLeftPaddedStr(Integer.toString(nextProgress), '0', 3);
        for (int targetId : active.objective().targetIds()) {
            if (targetId != mobId) {
                status.setProgress(targetId, progress);
            }
        }
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
        chr.yellowMessage("生命之证：" + active.objective().description() + " " + nextProgress
                + "/" + active.objective().requiredCount());
    }

    public static String gmStatus(Character chr) {
        if (chr == null) {
            return "角色不存在。";
        }
        if (!HpChallengeService.ensureLifeProofState(chr)) {
            return HpChallengeService.openRequirementText(chr);
        }
        HpChallengeService.JobBranch branch = HpChallengeService.branch(chr.getJob());
        if (branch == null) {
            return null;
        }

        QuestMeta active = currentStartedVisibleQuest(chr);
        QuestMeta next = active == null ? nextAvailableQuest(chr, branch) : null;
        int statusStage = active != null ? active.stage() : next != null ? next.stage() : completedRewardStage(chr, branch);
        if (statusStage <= 0) {
            statusStage = 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("生命之证状态\r\n");
        sb.append("职业分支：").append(BRANCH_INFO.get(branch).name()).append("\r\n");
        sb.append("当前阶段：").append(stageTitle(statusStage)).append("\r\n");
        sb.append("已领奖阶段：").append(completedRewardStage(chr, branch)).append("/7\r\n\r\n");
        if (active != null) {
            sb.append("进行中：").append(active.questId()).append(" ").append(active.name()).append("\r\n");
            appendGmObjectiveProgress(sb, chr, active);
            return sb.toString();
        }
        if (next != null) {
            sb.append("可领取：").append(next.questId()).append(" ").append(next.name()).append("\r\n");
            sb.append("目标：").append(next.objective().description()).append("\r\n");
            return sb.toString();
        }
        sb.append("当前没有进行中或可领取的生命之证任务。");
        return sb.toString();
    }

    public static String gmCompleteCurrent(Character operator, Character target) {
        if (target == null) {
            return "目标角色不存在。";
        }
        if (!HpChallengeService.ensureLifeProofState(target)) {
            return HpChallengeService.openRequirementText(target);
        }
        QuestMeta active = currentStartedVisibleQuest(target);
        if (active == null) {
            HpChallengeService.JobBranch branch = HpChallengeService.branch(target.getJob());
            QuestMeta next = branch == null ? null : nextAvailableQuest(target, branch);
            if (next != null) {
                return "当前没有进行中的生命之证任务，可以领取：" + next.name();
            }
            return "当前没有可补齐的生命之证任务。";
        }
        String result = completeCurrentForGm(target, active);
        if (result != null) {
            HpChallengeService.logGm(operator, target.getId(), "life_proof_complete_current",
                    "quest=" + active.questId() + ", name=" + active.name());
            return result;
        }
        return "当前生命之证任务不能用 GM next 补齐，请按任务提示完成。";
    }

    static int questId(int stage, HpChallengeService.JobBranch branch, int slot) {
        int jobIndex = BRANCH_ORDER.indexOf(branch);
        if (stage < 1 || stage > 7 || jobIndex < 0 || slot < 0 || slot >= BLOCK_SIZE) {
            throw new IllegalArgumentException("invalid life proof quest id arguments");
        }
        return FIRST_QUEST_ID + (((stage - 1) * BRANCH_ORDER.size() + jobIndex) * BLOCK_SIZE) + slot;
    }

    static int bridgeQuestId(QuestMeta selector) {
        if (selector.kind() != QuestKind.SELECTOR) {
            throw new IllegalArgumentException("selector quest required");
        }
        return questId(selector.stage(), selector.branch(), BRIDGE_SLOT_START + selector.selectorNo() - 1);
    }

    static int reservedQuestId(int stage, HpChallengeService.JobBranch branch) {
        return questId(stage, branch, RESERVED_SLOT);
    }

    static List<QuestMeta> stageBranchOptions(int stage, HpChallengeService.JobBranch branch) {
        return QUESTS.values().stream()
                .filter(meta -> meta.stage() == stage && meta.branch() == branch && meta.kind() == QuestKind.OPTION)
                .toList();
    }

    static List<QuestMeta> stageBranchVisibleQuests(int stage, HpChallengeService.JobBranch branch) {
        return QUESTS.values().stream()
                .filter(meta -> meta.stage() == stage && meta.branch() == branch && meta.isVisible())
                .toList();
    }

    static BranchInfo branchInfo(HpChallengeService.JobBranch branch) {
        return BRANCH_INFO.get(branch);
    }

    static Map<String, ItemCollection> itemCollections() {
        return Collections.unmodifiableMap(ITEM_COLLECTIONS);
    }

    private static Map<HpChallengeService.JobBranch, BranchInfo> buildBranchInfo() {
        Map<HpChallengeService.JobBranch, BranchInfo> info = new LinkedHashMap<>();
        info.put(HpChallengeService.JobBranch.WARRIOR, new BranchInfo("战士", 1022000, List.of(112, 122, 132)));
        info.put(HpChallengeService.JobBranch.MAGE, new BranchInfo("法师", 1032001, List.of(212, 222, 232)));
        info.put(HpChallengeService.JobBranch.BOWMAN, new BranchInfo("弓箭手", 1012100, List.of(312, 322)));
        info.put(HpChallengeService.JobBranch.THIEF, new BranchInfo("飞侠", 1052001, List.of(412, 422)));
        info.put(HpChallengeService.JobBranch.PIRATE, new BranchInfo("海盗", 1090000, List.of(512, 522)));
        return info;
    }

    private static Map<String, ItemCollection> buildItemCollections() {
        Map<String, ItemCollection> items = new HashMap<>();
        items.put("visit_hidden_maps", new ItemCollection(4033000, "初醒隐秘生命之证", 50, List.of(6130200, 6230400)));
        items.put("visit_leafre_maps", new ItemCollection(4033001, "龙林生命之证", 20, List.of(8140101, 8140102, 8140103)));
        items.put("visit_leafre_dragon_maps", new ItemCollection(4033002, "龙巢生命之证", 40, List.of(8140700, 8140701, 8140702, 8140703)));
        items.put("visit_ludi_time", new ItemCollection(4033003, "时间裂缝生命之证", 10, List.of(8140200, 8140300)));
        items.put("visit_ludi_maps", new ItemCollection(4033004, "玩具塔生命之证", 60, List.of(7140000, 8141100, 8140200, 8140300)));
        items.put("visit_expedition", new ItemCollection(4033005, "远征生命之证", 10, List.of(8190003, 8190004, 8140500)));
        items.put("visit_deep_sea", new ItemCollection(4033006, "深海生命之证", 15, List.of(8140600, 8141300, 8142100)));
        items.put("visit_deep_sea_hidden", new ItemCollection(4033007, "暗流生命之证", 50, List.of(7130020, 8140600, 8150100, 8150101)));
        items.put("visit_temple", new ItemCollection(4033008, "神殿生命之证", 35, List.of(8200005, 8200006, 8200009, 8200010)));
        items.put("visit_temple_maps", new ItemCollection(4033009, "回忆生命之证", 75, List.of(8200005, 8200006, 8200007, 8200008, 8200009, 8200010, 8200011, 8200012)));
        items.put("visit_final", new ItemCollection(4033010, "终印生命之证", 15, List.of(8190004, 8200011, 8200012)));
        return items;
    }

    private static Map<Integer, QuestMeta> buildQuests() {
        Map<Integer, QuestMeta> quests = new LinkedHashMap<>();
        for (int stageNo = 1; stageNo <= 7; stageNo++) {
            HpChallengeService.StageConfig stage = HpChallengeService.stage(stageNo);
            for (HpChallengeService.JobBranch branch : BRANCH_ORDER) {
                List<HpChallengeService.Task> mainTasks = orderedMainTasks(stage, branch);
                for (int index = 0; index < mainTasks.size(); index++) {
                    if (index > MAIN_SLOT_END) {
                        throw new IllegalStateException("life proof main task overflow: stage=" + stageNo + ", branch=" + branch);
                    }
                    HpChallengeService.Task task = mainTasks.get(index);
                    int questId = questId(stageNo, branch, MAIN_SLOT_START + index);
                    QuestMeta meta = new QuestMeta(questId, stageNo, branch, QuestKind.MAIN, MAIN_SLOT_START + index,
                            0, 0, task, objective(stageNo, task), questName(stageNo, index + 1, task.description()));
                    quests.put(questId, meta);
                }
                for (int selectorNo = 1; selectorNo <= OPTIONAL_REQUIRED_COUNT; selectorNo++) {
                    int questId = questId(stageNo, branch, SELECTOR_SLOT_START + selectorNo - 1);
                    Objective objective = new Objective(ObjectiveType.SELECT_OPTION, 1,
                            "选择第 " + selectorNo + " 项附加试炼", List.of(), 0, 0);
                    QuestMeta meta = new QuestMeta(questId, stageNo, branch, QuestKind.SELECTOR,
                            SELECTOR_SLOT_START + selectorNo - 1, 0, selectorNo, null, objective,
                            stageTitle(stageNo) + "：选择试炼 " + selectorNo);
                    quests.put(questId, meta);
                }
                for (HpChallengeService.Task task : stage.optionalTasks()) {
                    int slot = OPTION_SLOT_START + task.optionNo() - 1;
                    int questId = questId(stageNo, branch, slot);
                    QuestMeta meta = new QuestMeta(questId, stageNo, branch, QuestKind.OPTION, slot,
                            task.optionNo(), 0, task, objective(stageNo, task),
                            stageTitle(stageNo) + "：附加试炼 " + task.optionNo());
                    quests.put(questId, meta);
                }
                int rewardQuestId = questId(stageNo, branch, REWARD_SLOT);
                Objective reward = new Objective(ObjectiveType.REWARD, 1, "领取本阶段生命之证奖励", List.of(), 0, 0);
                quests.put(rewardQuestId, new QuestMeta(rewardQuestId, stageNo, branch, QuestKind.REWARD, REWARD_SLOT,
                        0, 0, null, reward, stageTitle(stageNo) + "：生命之证"));
                for (int bridgeNo = 1; bridgeNo <= OPTIONAL_REQUIRED_COUNT; bridgeNo++) {
                    int bridgeQuestId = questId(stageNo, branch, BRIDGE_SLOT_START + bridgeNo - 1);
                    quests.put(bridgeQuestId, new QuestMeta(bridgeQuestId, stageNo, branch, QuestKind.BRIDGE,
                            BRIDGE_SLOT_START + bridgeNo - 1, 0, bridgeNo, null, reward,
                            "hidden bridge " + bridgeNo));
                }
                int reservedQuestId = questId(stageNo, branch, RESERVED_SLOT);
                quests.put(reservedQuestId, new QuestMeta(reservedQuestId, stageNo, branch, QuestKind.RESERVED,
                        RESERVED_SLOT, 0, 0, null, reward, "reserved"));
            }
        }
        return quests;
    }

    private static List<HpChallengeService.Task> orderedMainTasks(HpChallengeService.StageConfig stage,
                                                                  HpChallengeService.JobBranch branch) {
        List<HpChallengeService.Task> tasks = new ArrayList<>();
        if (stage.stage() == 1) {
            int ownInstructor = BRANCH_INFO.get(branch).instructorNpcId();
            for (int npcId : HpChallengeService.instructorVisitOrderForInstructor(ownInstructor)) {
                stage.commonTasks().stream()
                        .filter(task -> task.targetType() == HpChallengeService.TargetType.NPC_TALK)
                        .filter(task -> task.primaryTarget() == npcId)
                        .findFirst()
                        .ifPresent(tasks::add);
            }
            stage.commonTasks().stream()
                    .filter(task -> task.targetType() != HpChallengeService.TargetType.NPC_TALK)
                    .forEach(tasks::add);
        } else {
            tasks.addAll(stage.commonTasks());
        }
        tasks.addAll(stage.jobTasks().getOrDefault(branch, List.of()));
        return tasks;
    }

    private static Objective objective(int stage, HpChallengeService.Task task) {
        ItemCollection collection = collectionForTask(stage, task);
        if (collection != null) {
            return new Objective(ObjectiveType.ITEM, collection.requiredCount(), "收集#t" + collection.itemId() + "#",
                    collection.droppers(), collection.itemId(), 0);
        }
        ObjectiveType type = switch (task.targetType()) {
            case KILL -> ObjectiveType.KILL;
            case BOSS -> ObjectiveType.BOSS;
            case PQ_ANY -> ObjectiveType.PQ_ANY;
            case PQ_PIRATE -> ObjectiveType.PQ_PIRATE;
            case PQ_TOY_OR_PIRATE -> ObjectiveType.PQ_TOY_OR_PIRATE;
            case MESO -> ObjectiveType.MESO;
            case SCROLL_100 -> ObjectiveType.SCROLL_100;
            case NPC_TALK -> ObjectiveType.NPC_TALK;
            case JUMP_MANUAL -> ObjectiveType.JUMP_MANUAL;
            case MAP -> throw new IllegalStateException("unmapped map task: " + task.key());
        };
        return new Objective(type, task.requiredCount(), task.description(), task.targetIds(), 0, task.mesoCost());
    }

    private static ItemCollection collectionForTask(int stage, HpChallengeService.Task task) {
        if (task.targetType() != HpChallengeService.TargetType.MAP) {
            return null;
        }
        String collectionKey = switch (task.key()) {
            case "visit_leafre_maps" -> "visit_leafre_maps";
            case "visit_ludi_time" -> "visit_ludi_time";
            case "visit_expedition" -> "visit_expedition";
            case "visit_deep_sea" -> "visit_deep_sea";
            case "visit_temple" -> "visit_temple";
            case "visit_final" -> "visit_final";
            default -> switch (stage + ":" + task.optionNo()) {
                case "1:4" -> "visit_hidden_maps";
                case "2:5" -> "visit_leafre_dragon_maps";
                case "3:5" -> "visit_ludi_maps";
                case "5:6" -> "visit_deep_sea_hidden";
                case "6:7" -> "visit_temple_maps";
                default -> throw new IllegalStateException("unmapped map task: stage=" + stage + ", key=" + task.key());
            };
        };
        return ITEM_COLLECTIONS.get(collectionKey);
    }

    private static void incrementCustomProgress(Character chr, ObjectiveType eventType, String eventName,
                                                String eventText) {
        QuestMeta active = currentStartedCustomQuest(chr);
        if (active == null || !matchesCustom(active.objective(), eventType, eventName)) {
            return;
        }
        int next = Math.min(active.objective().requiredCount(), customProgress(chr, active) + 1);
        QuestStatus status = chr.getQuest(Quest.getInstance(active.questId()));
        status.setProgress(CUSTOM_PROGRESS_KEY, StringUtil.getLeftPaddedStr(Integer.toString(next), '0', 3));
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
        chr.yellowMessage("生命之证：" + eventText + " " + next + "/" + active.objective().requiredCount());
        if (next >= active.objective().requiredCount()) {
            completeCustomProgress(chr, active, next);
        }
    }

    private static boolean markNpcTalkProgress(Character chr, QuestMeta meta, int npcId, boolean showMessage) {
        if (chr == null || meta == null || npcId <= 0 || !meta.isVisible()
                || meta.objective().type() != ObjectiveType.NPC_TALK
                || chr.getQuestStatus(meta.questId()) != QuestStatus.Status.STARTED.getId()
                || npcId != completeNpcId(meta)) {
            return false;
        }
        int current = customProgress(chr, meta);
        if (current >= meta.objective().requiredCount()) {
            return false;
        }
        int next = Math.min(meta.objective().requiredCount(), current + 1);
        QuestStatus status = chr.getQuest(Quest.getInstance(meta.questId()));
        status.setProgress(CUSTOM_PROGRESS_KEY, StringUtil.getLeftPaddedStr(Integer.toString(next), '0', 3));
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
        if (showMessage) {
            chr.yellowMessage("生命之证：" + meta.objective().description() + " " + next + "/"
                    + meta.objective().requiredCount() + "，请由#p" + completeNpcId(meta) + "#确认。");
        }
        return true;
    }

    private static boolean matchesCustom(Objective objective, ObjectiveType eventType, String eventName) {
        return switch (objective.type()) {
            case PQ_ANY -> eventType == ObjectiveType.PQ_ANY;
            case PQ_PIRATE -> eventType == ObjectiveType.PQ_ANY && containsEventName(eventName, "pirate");
            case PQ_TOY_OR_PIRATE -> eventType == ObjectiveType.PQ_ANY
                    && (containsEventName(eventName, "pirate") || containsEventName(eventName, "ludi"));
            case SCROLL_100 -> eventType == ObjectiveType.SCROLL_100;
            default -> false;
        };
    }

    private static boolean containsEventName(String eventName, String keyword) {
        return eventName != null && eventName.toLowerCase().contains(keyword);
    }

    private static QuestMeta currentStartedCustomQuest(Character chr) {
        if (chr == null) {
            return null;
        }
        return QUESTS.values().stream()
                .filter(meta -> meta.isVisible() && meta.objective().isCustomProgress())
                .filter(meta -> chr.getQuestStatus(meta.questId()) == QuestStatus.Status.STARTED.getId())
                .findFirst()
                .orElse(null);
    }

    private static QuestMeta currentStartedVisibleQuest(Character chr) {
        if (chr == null) {
            return null;
        }
        return QUESTS.values().stream()
                .filter(QuestMeta::isVisible)
                .filter(meta -> chr.getQuestStatus(meta.questId()) == QuestStatus.Status.STARTED.getId())
                .findFirst()
                .orElse(null);
    }

    private static QuestMeta nextAvailableQuest(Character chr, HpChallengeService.JobBranch branch) {
        if (chr == null || branch == null) {
            return null;
        }
        int instructorNpcId = BRANCH_INFO.get(branch).instructorNpcId();
        for (int stage = 1; stage <= 7; stage++) {
            for (QuestMeta meta : stageBranchVisibleQuests(stage, branch)) {
                if (chr.getQuestStatus(meta.questId()) != QuestStatus.Status.NOT_STARTED.getId()) {
                    continue;
                }
                if (Quest.getInstance(meta.questId()).canStart(chr, instructorNpcId)) {
                    return meta;
                }
            }
        }
        return null;
    }

    private static int completedRewardStage(Character chr, HpChallengeService.JobBranch branch) {
        int completedStage = 0;
        for (int stage = 1; stage <= 7; stage++) {
            int questId = questId(stage, branch, REWARD_SLOT);
            if (chr.getQuestStatus(questId) == QuestStatus.Status.COMPLETED.getId()) {
                completedStage = stage;
            }
        }
        return completedStage;
    }

    private static String completeCurrentForGm(Character target, QuestMeta active) {
        Objective objective = active.objective();
        return switch (objective.type()) {
            case KILL, BOSS -> {
                fillMobProgress(target, active);
                yield "已补齐当前生命之证击杀进度：" + active.name() + "\r\n玩家可以回任务指定 NPC 完成任务。";
            }
            case ITEM -> grantMissingItemsForGm(target, active);
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL -> {
                completeCustomProgress(target, active, objective.requiredCount());
                yield "已补齐当前生命之证任务：" + active.name() + "\r\n玩家可以回一转教官完成任务。";
            }
            case MESO -> {
                int missing = objective.mesoCost() - target.getMeso();
                if (missing > 0) {
                    target.gainMeso(missing, true, true, true);
                    yield "已补齐金币：" + missing + "\r\n玩家可以回一转教官提交任务。";
                }
                yield "当前金币已经足够，玩家可以回一转教官提交任务。";
            }
            case NPC_TALK -> {
                QuestStatus status = target.getQuest(Quest.getInstance(active.questId()));
                status.setProgress(CUSTOM_PROGRESS_KEY, StringUtil.getLeftPaddedStr(
                        Integer.toString(objective.requiredCount()), '0', 3));
                target.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
                target.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
                Quest.getInstance(active.questId()).forceComplete(target, completeNpcId(active));
                yield "已完成当前生命之证拜访任务：" + active.name();
            }
            case SELECT_OPTION -> "当前需要通过任务菜单选择附加试炼。";
            case REWARD -> "当前阶段任务已经完成，可以回一转教官领取奖励。";
        };
    }

    private static void fillMobProgress(Character chr, QuestMeta meta) {
        QuestStatus status = chr.getQuest(Quest.getInstance(meta.questId()));
        String progress = StringUtil.getLeftPaddedStr(Integer.toString(meta.objective().requiredCount()), '0', 3);
        for (int targetId : meta.objective().targetIds()) {
            status.setProgress(targetId, progress);
        }
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
        chr.yellowMessage("生命之证：" + meta.name() + " " + meta.objective().requiredCount()
                + "/" + meta.objective().requiredCount());
    }

    private static String grantMissingItemsForGm(Character chr, QuestMeta meta) {
        Objective objective = meta.objective();
        int held = chr.getInventory(ItemConstants.getInventoryType(objective.itemId())).countById(objective.itemId());
        int missing = objective.requiredCount() - held;
        if (missing <= 0) {
            return "当前收集物已经足够，玩家可以回一转教官提交任务。";
        }
        if (!InventoryManipulator.addById(chr.getClient(), objective.itemId(), (short) missing)) {
            return "背包空间不足，无法补齐#t" + objective.itemId() + "#。";
        }
        chr.sendPacket(PacketCreator.getShowItemGain(objective.itemId(), (short) missing, true));
        return "已补齐当前生命之证收集物：" + meta.name() + "\r\n玩家可以回一转教官提交任务。";
    }

    private static void appendGmObjectiveProgress(StringBuilder sb, Character chr, QuestMeta meta) {
        Objective objective = meta.objective();
        switch (objective.type()) {
            case KILL, BOSS -> sb.append("进度：")
                    .append(mobProgress(chr, meta)).append("/")
                    .append(objective.requiredCount()).append("\r\n");
            case ITEM -> sb.append("进度：")
                    .append(chr.getInventory(ItemConstants.getInventoryType(objective.itemId()))
                            .countById(objective.itemId()))
                    .append("/").append(objective.requiredCount())
                    .append(" #t").append(objective.itemId()).append("#\r\n");
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL -> sb.append("进度：")
                    .append(customProgress(chr, meta)).append("/")
                    .append(objective.requiredCount()).append("\r\n");
            case MESO -> sb.append("金币：")
                    .append(Math.min(chr.getMeso(), objective.mesoCost()))
                    .append("/").append(objective.mesoCost()).append("\r\n");
            case NPC_TALK -> sb.append("目标 NPC：").append(targetNpcId(meta))
                    .append("，进度：").append(customProgress(chr, meta)).append("/")
                    .append(objective.requiredCount()).append("\r\n");
            case SELECT_OPTION -> sb.append("目标：选择 1 项附加试炼\r\n");
            case REWARD -> sb.append("目标：领取本阶段奖励\r\n");
        }
    }

    private static int mobProgress(Character chr, QuestMeta meta) {
        QuestStatus status = chr.getQuest(Quest.getInstance(meta.questId()));
        int progress = Integer.MAX_VALUE;
        for (int targetId : meta.objective().targetIds()) {
            progress = Math.min(progress, parseProgress(status.getProgress(targetId)));
        }
        return progress == Integer.MAX_VALUE ? 0 : progress;
    }

    private static int customProgress(Character chr, QuestMeta meta) {
        String progress = chr.getQuest(Quest.getInstance(meta.questId())).getProgress(CUSTOM_PROGRESS_KEY);
        if (progress.isBlank()) {
            return 0;
        }
        return parseProgress(progress);
    }

    private static int parseProgress(String progress) {
        try {
            return Integer.parseInt(progress);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void completeCustomProgress(Character chr, QuestMeta meta, int progress) {
        QuestStatus status = chr.getQuest(Quest.getInstance(meta.questId()));
        status.setProgress(CUSTOM_PROGRESS_KEY, StringUtil.getLeftPaddedStr(
                Integer.toString(Math.min(progress, meta.objective().requiredCount())), '0', 3));
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.yellowMessage("生命之证：" + meta.name() + "已达成，请回一转教官完成任务。");
    }

    private static void completeNextBridgeSilently(Character chr, QuestMeta option) {
        for (int bridgeNo = 1; bridgeNo <= OPTIONAL_REQUIRED_COUNT; bridgeNo++) {
            int bridgeQuestId = questId(option.stage(), option.branch(), BRIDGE_SLOT_START + bridgeNo - 1);
            Quest bridgeQuest = Quest.getInstance(bridgeQuestId);
            QuestStatus oldStatus = chr.getQuestNoAdd(bridgeQuest);
            if (oldStatus != null && oldStatus.getStatus() == QuestStatus.Status.COMPLETED) {
                continue;
            }
            QuestStatus newStatus = new QuestStatus(bridgeQuest, QuestStatus.Status.COMPLETED,
                    BRANCH_INFO.get(option.branch()).instructorNpcId());
            synchronized (chr.getQuests()) {
                chr.getQuests().put((short) bridgeQuestId, newStatus);
            }
            return;
        }
    }

    private static List<QuestMeta> availableOptions(Character chr, QuestMeta selector) {
        if (chr == null) {
            return List.of();
        }
        int expectedBridge = selector.selectorNo() == 1 ? 0 :
                questId(selector.stage(), selector.branch(), BRIDGE_SLOT_START + selector.selectorNo() - 2);
        if (expectedBridge > 0 && chr.getQuestStatus(expectedBridge) != QuestStatus.Status.COMPLETED.getId()) {
            return List.of();
        }
        return stageBranchOptions(selector.stage(), selector.branch()).stream()
                .filter(option -> chr.getQuestStatus(option.questId()) == QuestStatus.Status.NOT_STARTED.getId())
                .toList();
    }

    private static boolean removeItem(Character chr, int itemId, int count) {
        InventoryType type = ItemConstants.getInventoryType(itemId);
        if (chr.getInventory(type).countById(itemId) < count) {
            return false;
        }
        InventoryManipulator.removeById(chr.getClient(), type, itemId, count, true, false);
        chr.sendPacket(PacketCreator.getShowItemGain(itemId, (short) -count, true));
        return true;
    }

    static String questName(int stage, int order, String description) {
        return stageTitle(stage) + "：" + description;
    }

    static String stageTitle(int stage) {
        return "生命之证 " + switch (stage) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            default -> Integer.toString(stage);
        };
    }

    static String stageStory(int stage) {
        return switch (stage) {
            case 1 -> "导师要你先确认五大职业的意志，再从旧日强敌身上取回第一枚印记。";
            case 2 -> "龙林中的气息开始回应，必须证明你能在神木村独自站稳。";
            case 3 -> "时间裂缝会放大生命的缺口，只有稳定心神的人才能继续前行。";
            case 4 -> "远征的号角已经响起，你需要跨过真正的首领试炼。";
            case 5 -> "深海会吞没迟疑，生命之证需要从暗流中重新凝聚。";
            case 6 -> "神殿记录着过去与后悔，只有看清自身的人才能拿起下一枚印记。";
            case 7 -> "最后的印记只承认完成全部试炼的冒险家。";
            default -> "继续完成生命之证试炼。";
        };
    }

    private static String mentorStartLine(QuestMeta meta) {
        int targetNpcId = completeNpcId(meta);
        return switch (startNpcId(meta)) {
            case 1012100 -> "风会记住脚步，也会记住迟疑。先去见#p" + targetNpcId
                    + "#，让对方确认你的呼吸没有乱。";
            case 1032001 -> "生命之证不是蛮力的试炼。去见#p" + targetNpcId
                    + "#，让不同职业的秩序为你留下第一道记录。";
            case 1022000 -> "先别急着说自己准备好了。去见#p" + targetNpcId
                    + "#，站稳，听完，再把确认带回来写进生命之证。";
            case 1052001 -> "影子会暴露犹豫。去见#p" + targetNpcId
                    + "#，让对方判断你是不是能活着走过这条路。";
            case 1090000 -> "别绕远路。去找#p" + targetNpcId
                    + "#，让对方直接确认你有没有继续前进的胆量。";
            default -> "前往#p" + targetNpcId + "#，完成这一步生命之证确认。";
        };
    }

    private static String mentorCompletionLine(int npcId) {
        return switch (npcId) {
            case 1012100 -> "你的呼吸还算平稳。风没有把急躁留在你身上，这一步我会为你确认。";
            case 1032001 -> "你的魔力流动没有失序。能沉下心观察自己，才有资格继续生命之证。";
            case 1022000 -> "站得住，也听得进话。少说空话，多把身体练到能撑住下一场试炼。";
            case 1052001 -> "你没有被阴影吓退。记住这种警惕，活下来的人才配继续往前。";
            case 1090000 -> "眼神没有躲。海风会吹散犹豫，我认可你继续走生命之证。";
            default -> "这一步生命之证已经确认。";
        };
    }

    private static void appendObjectiveHint(StringBuilder sb, QuestMeta meta) {
        Objective objective = meta.objective();
        switch (objective.type()) {
            case ITEM -> sb.append("\r\n#i").append(objective.itemId()).append("# #t").append(objective.itemId())
                    .append("# #b#c").append(objective.itemId()).append("# / ")
                    .append(objective.requiredCount()).append("#k");
            case MESO -> sb.append("\r\n需要缴纳金币：").append(objective.mesoCost());
            case NPC_TALK -> sb.append("\r\n前往#p").append(completeNpcId(meta))
                    .append("#，点击任务完成图标，由对方确认这一步生命之证。");
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL ->
                    sb.append("\r\n进度达成后回一转教官提交。");
            default -> {
            }
        }
    }

    private static String ok(String message) {
        return OK_PREFIX + message;
    }

    private static String error(String message) {
        return ERR_PREFIX + message;
    }

    private static String info(String message) {
        return INFO_PREFIX + message;
    }

    private static String ready(String message) {
        return READY_PREFIX + message;
    }
}

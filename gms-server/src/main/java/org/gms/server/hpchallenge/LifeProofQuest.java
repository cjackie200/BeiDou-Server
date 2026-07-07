package org.gms.server.hpchallenge;

import org.gms.client.Character;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.constants.game.DelayedQuestUpdate;
import org.gms.constants.inventory.ItemConstants;
import org.gms.server.life.MonsterDropEntry;
import org.gms.server.life.MonsterInformationProvider;
import org.gms.server.quest.Quest;
import org.gms.server.quest.hook.InteractionHookAction;
import org.gms.server.quest.hook.InteractionHookContext;
import org.gms.server.quest.hook.InteractionHookPackets;
import org.gms.server.quest.hook.InteractionHookProgressEntry;
import org.gms.util.PacketCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(LifeProofQuest.class);
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

    /**
     * Returns the map-collection item ID for the player's current active LifeProof MAP quest,
     * or 0 if the player has no active MAP-collection quest.
     * Used by Reactor scripts to grant the correct stage-specific collection item.
     */
    public static int activeMapCollectionItemId(Character chr) {
        if (chr == null) return 0;
        Optional<Integer> questId = resolveCurrentQuestId(chr);
        if (questId.isEmpty()) return 0;
        QuestMeta meta = QUESTS.get(questId.get());
        if (meta == null || !meta.isVisible()) return 0;
        if (multiItemCollections(meta) != null) return 0;
        Objective objective = effectiveObjective(chr, meta);
        if (objective == null || objective.type() != ObjectiveType.ITEM) return 0;
        return objective.itemId();
    }
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
    private static final Map<String, List<ItemCollection>> MULTI_ITEM_COLLECTIONS = buildMultiItemCollections();
    private static final Map<Integer, QuestMeta> QUESTS = buildQuests();

    private LifeProofQuest() {
    }

    enum QuestKind {
        MAIN,
        SELECTOR,
        OPTION_SLOT,
        RETIRED_OPTION,
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
        OPTION_SLOT,
        REWARD
    }

    record BranchInfo(String name, int instructorNpcId, List<Integer> jobIds) {
    }

    record ItemCollection(int itemId, String itemName, int requiredCount, List<Integer> droppers) {
    }

    record Objective(ObjectiveType type, int requiredCount, String description, List<Integer> targetIds, int itemId,
                     int mesoCost, boolean perMob) {
        boolean isCustomProgress() {
            return switch (type) {
                case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, MESO, SCROLL_100, NPC_TALK, JUMP_MANUAL, OPTION_SLOT -> true;
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
            return kind != QuestKind.BRIDGE && kind != QuestKind.RESERVED && kind != QuestKind.RETIRED_OPTION;
        }
    }

    private record ProgressValue(int current, int required) {
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
        HpChallengeService.JobBranch branch = lifeProofBranch(chr);
        if (branch == null) {
            return 0;
        }
        return QUESTS.values().stream()
                .filter(QuestMeta::isVisible)
                .filter(meta -> meta.branch() == branch)
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
        if (chr == null || npcId <= 0 || !ensureAndMigrateState(chr)) {
            return 0;
        }
        HpChallengeService.JobBranch branch = HpChallengeService.branch(chr.getJob());
        if (BRANCH_INFO.get(branch) == null || currentStartedVisibleQuest(chr) != null) {
            return 0;
        }
        QuestMeta next = nextAvailableQuest(chr, branch);
        if (next == null || startNpcId(next) != npcId) {
            return 0;
        }
        return next.questId();
    }

    public static List<Integer> npcHookIds(Character chr) {
        if (chr == null || !ensureAndMigrateState(chr)) {
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
        return resolveCurrentQuestId(chr)
                .map(List::of)
                .orElseGet(List::of);
    }

    public static List<Integer> getCurrentInstructorNpcIds(Character chr) {
        return npcHookIds(chr);
    }

    public static Optional<Integer> resolveCurrentQuestId(Character chr) {
        if (chr == null || !ensureAndMigrateState(chr)) {
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
            startOptionSlot(chr, selectedQuest, npcId);
            // 不发送 updateQuestFinish 标准包，避免客户端收到后
            // 自动发起新任务的 QUEST_ACTION 与 sendOk 对话框产生时序竞争。
            chr.yellowMessage("生命之证：" + selectedMessage(result));
            context.sendOk(selectedMessage(result));
            return;
        }

        String result = startQuest(chr, meta.questId());
        if (isOkResult(result)) {
            quest.forceStart(chr, npcId);
            onStarted(chr, meta.questId());
            refreshQuestRules(chr);
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
            refreshQuestRules(chr);
            QuestMeta next = nextContinuationAtNpc(chr, meta, npcId);
            if (next != null) {
                // 不发送 updateQuestFinish 标准包，否则客户端收到后会
                // 自动发起新任务的 QUEST_ACTION，与下面的 openHook 产生
                // 双重触发导致客户端崩溃(退回到登录界面)。
                // Hook 系统通过 switchQuest + openHook 完成切换即可。
                context.switchQuest(next.questId(), npcId, resolveCurrentAction(chr, next.questId()));
                openHook(context);
                return;
            }
        }
        context.sendOk(resultMessage(result));
    }

    public static boolean canOpenProgressAtNpc(int questId, int npcId) {
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

    static String questListParent(QuestMeta meta) {
        if (meta == null || !meta.isVisible()) {
            return "";
        }
        BranchInfo branchInfo = BRANCH_INFO.get(meta.branch());
        if (branchInfo == null) {
            return "";
        }
        return stageTitle(meta.stage()) + "（" + branchInfo.name() + "）";
    }

    static int questListOrder(QuestMeta meta) {
        if (meta == null || !meta.isVisible()) {
            return 0;
        }
        int index = stageBranchVisibleQuests(meta.stage(), meta.branch()).indexOf(meta);
        return index < 0 ? 0 : index + 1;
    }

    static String mesoProgressValue(QuestMeta meta, int meso) {
        if (meta == null || meta.objective().type() != ObjectiveType.MESO) {
            return "000";
        }
        return meso >= meta.objective().mesoCost() ? "001" : "000";
    }

    public static List<InteractionHookProgressEntry> progressEntries(Character chr) {
        if (chr == null) {
            return List.of();
        }
        HpChallengeService.JobBranch branch = lifeProofBranch(chr);
        if (branch == null) {
            return List.of();
        }

        List<InteractionHookProgressEntry> entries = new ArrayList<>();
        for (QuestMeta meta : QUESTS.values()) {
            if (!meta.isVisible() || meta.branch() != branch) {
                continue;
            }
            try {
                List<InteractionHookProgressEntry.Condition> conditions = conditionsForObjective(chr, meta);
                if (conditions.isEmpty()) {
                    continue;
                }
                entries.add(new InteractionHookProgressEntry(
                        meta.questId(),
                        chr.getQuestStatus(meta.questId()),
                        conditions
                ));
            } catch (Exception e) {
                log.warn("LifeProof progressEntries failed for quest {}: {}", meta.questId(), e.getMessage());
            }
        }
        return entries;
    }

    private static List<InteractionHookProgressEntry.Condition> conditionsForObjective(
            Character chr, QuestMeta meta) {
        // Skip quests that haven't been started to avoid auto-creating QuestStatus entries.
        byte statusByte = chr.getQuestStatus(meta.questId());
        if (statusByte != QuestStatus.Status.STARTED.getId()
                && statusByte != QuestStatus.Status.COMPLETED.getId()) {
            return List.of();
        }
        // Resolve the effective objective: for OPTION_SLOT quests this is the selected trial,
        // for all other quests it's the static objective defined at build time.
        Objective objective = effectiveObjective(chr, meta);
        if (objective == null) {
            return List.of();
        }
        ObjectiveType type = objective.type();

        // OPTION_SLOT quests dispatch to the selected trial's objective type.
        if (type == ObjectiveType.KILL || type == ObjectiveType.BOSS) {
            // For OPTION_SLOT quests, KILL/BOSS progress is stored in hp_challenge_progress DB,
            // not in the quest status per-mob map. Use questProgressValue which reads the DB source.
            if (meta.kind() == QuestKind.OPTION_SLOT) {
                ProgressValue progress = questProgressValue(chr, meta);
                StringBuilder mobNames = new StringBuilder();
                for (int i = 0; i < objective.targetIds().size(); i++) {
                    if (i > 0) mobNames.append("/");
                    mobNames.append(MonsterInformationProvider.getInstance().getMobNameFromId(objective.targetIds().get(i)));
                }
                String label = mobNames.isEmpty() ? "击杀进度" : mobNames + " 进度";
                return List.of(new InteractionHookProgressEntry.Condition(
                        progress.current(), progress.required(),
                        label + "：#b" + progress.current() + "#k/#r" + progress.required() + "#k"));
            }
            Quest quest = Quest.getInstance(meta.questId());
            if (quest == null) {
                return List.of();
            }
            QuestStatus status = chr.getQuest(quest);
            if (status == null) {
                return List.of();
            }
            if (objective.perMob()) {
                List<InteractionHookProgressEntry.Condition> conditions = new ArrayList<>();
                for (int targetId : objective.targetIds()) {
                    int mobProgress = parseProgress(status.getProgress(targetId));
                    String mobName = MonsterInformationProvider.getInstance().getMobNameFromId(targetId);
                    conditions.add(new InteractionHookProgressEntry.Condition(
                            mobProgress, objective.requiredCount(),
                            mobName + " #b" + mobProgress + "#k/#r" + objective.requiredCount() + "#k"));
                }
                return conditions;
            }
            int progress = mobProgress(chr, meta);
            StringBuilder mobNames = new StringBuilder();
            for (int i = 0; i < objective.targetIds().size(); i++) {
                if (i > 0) mobNames.append("/");
                mobNames.append(MonsterInformationProvider.getInstance().getMobNameFromId(objective.targetIds().get(i)));
            }
            return List.of(new InteractionHookProgressEntry.Condition(
                    progress, objective.requiredCount(),
                    mobNames + " #b" + progress + "#k/#r" + objective.requiredCount() + "#k"));
        }

        ProgressValue progress = questProgressValue(chr, meta);
        if (type == ObjectiveType.ITEM) {
            List<ItemCollection> multi = multiItemCollections(meta);
            if (multi != null) {
                List<InteractionHookProgressEntry.Condition> conditions = new ArrayList<>();
                int totalCurrent = 0;
                int totalRequired = 0;
                for (ItemCollection c : multi) {
                    int held = Math.min(c.requiredCount(), itemCount(chr, c.itemId()));
                    totalCurrent += held;
                    totalRequired += c.requiredCount();
                    conditions.add(new InteractionHookProgressEntry.Condition(
                            held, c.requiredCount(),
                            "#i" + c.itemId() + "# #t" + c.itemId() + "# #b" + held + "#k/#r" + c.requiredCount() + "#k"
                                    + dropperText(c.droppers())));
                }
                // Add a summary line first
                conditions.add(0, new InteractionHookProgressEntry.Condition(
                        totalCurrent, totalRequired,
                        "五种水晶合计：#b" + totalCurrent + "#k/#r" + totalRequired + "#k"));
                return conditions;
            }
        }
        String text = switch (type) {
            case KILL, BOSS -> throw new IllegalStateException("unreachable");
            case ITEM -> "#i" + objective.itemId() + "# #t" + objective.itemId()
                    + "# #b" + progress.current() + "#k/#r" + progress.required() + "#k"
                    + dropperText(objective.targetIds());
            case MESO -> "金币：#b" + progress.current() + "#k/#r" + progress.required() + "#k";
            case NPC_TALK -> {
                int npcId = objective.targetIds().isEmpty() ? 0 : objective.targetIds().getFirst();
                yield "与#p" + npcId + "#确认 #b" + progress.current() + "#k/1";
            }
            case PQ_ANY -> "组队任务：#b" + progress.current() + "#k/#r" + progress.required() + "#k 次";
            case PQ_PIRATE ->
                    "海盗船组队任务：#b" + progress.current() + "#k/#r" + progress.required() + "#k 次";
            case PQ_TOY_OR_PIRATE ->
                    "玩具城/海盗船组队任务：#b" + progress.current() + "#k/#r" + progress.required() + "#k 次";
            case SCROLL_100 -> "卷轴强化：#b" + progress.current() + "#k/#r" + progress.required() + "#k 次";
            case JUMP_MANUAL -> "跳跃试炼：#b" + progress.current() + "#k/#r" + progress.required() + "#k 次";
            case SELECT_OPTION -> "选择 1 项附加试炼";
            case OPTION_SLOT -> "请在附加试炼选择任务中选定本轮目标";
            case REWARD -> "领取阶段奖励";
        };
        return List.of(new InteractionHookProgressEntry.Condition(progress.current(), progress.required(), text));
    }

    public static void normalizeForLogin(Character chr) {
        if (chr == null || !ensureAndMigrateState(chr)) {
            return;
        }
        normalizeCompletedStages(chr);
        syncActiveObjectiveProgress(chr);
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
        if (!ensureAndMigrateState(chr)) {
            return HpChallengeService.openRequirementText(chr);
        }
        if (meta.kind() == QuestKind.SELECTOR) {
            return selectionMenu(chr, questId);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("#e").append(meta.name()).append("#n\r\n\r\n");
        sb.append(stageStory(meta.stage())).append("\r\n\r\n");
        Objective objective = effectiveObjective(chr, meta);
        if (objective.type() == ObjectiveType.NPC_TALK) {
            sb.append(mentorStartLine(meta)).append("\r\n\r\n");
        }
        String startText = startObjectiveText(meta, objective);
        if (!startText.isBlank()) {
            sb.append(startText).append("\r\n");
        }
        appendObjectiveHint(sb, meta, objective);
        sb.append("\r\n是否接受这一步试炼？");
        return sb.toString();
    }

    public static String startQuest(Character chr, int questId) {
        QuestMeta meta = QUESTS.get(questId);
        if (meta == null || !meta.isVisible()) {
            return error("这个任务暂时无法处理。");
        }
        if (!ensureAndMigrateState(chr)) {
            return error(HpChallengeService.openRequirementText(chr));
        }
        if (meta.kind() == QuestKind.OPTION_SLOT && selectedOptional(chr, meta) == null) {
            return error("请先通过附加试炼选择任务选择本轮目标。");
        }
        return ok("这一步生命之证已经开始。");
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
        if (meta.kind() == QuestKind.OPTION_SLOT || meta.objective().type() == ObjectiveType.MESO) {
            syncActiveObjectiveProgress(chr);
        }
        refreshQuestProgress(chr);
    }

    public static void startOptionSlot(Character chr, int questId, int npcId) {
        QuestMeta meta = QUESTS.get(questId);
        if (chr == null || meta == null || meta.kind() != QuestKind.OPTION_SLOT) {
            return;
        }
        Quest quest = Quest.getInstance(questId);
        QuestStatus status = new QuestStatus(quest, QuestStatus.Status.STARTED, npcId);
        status.setProgress(CUSTOM_PROGRESS_KEY, "000");
        chr.updateQuestStatus(status);
        syncActiveObjectiveProgress(chr);
        refreshQuestRules(chr);
    }

    public static String selectionMenu(Character chr, int selectorQuestId) {
        QuestMeta selector = QUESTS.get(selectorQuestId);
        if (selector == null || selector.kind() != QuestKind.SELECTOR) {
            return "当前没有可选择的试炼。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("#e").append(selector.name()).append("#n\r\n\r\n");
        sb.append("在这一轮试炼中选择一个方向。选定后需要完成它，才会开放下一轮选择。\r\n\r\n");
        List<HpChallengeService.Task> options = availableOptions(chr, selector);
        if (options.isEmpty()) {
            sb.append("#L0#暂无可选择的试炼#l");
            return sb.toString();
        }
        for (HpChallengeService.Task option : options) {
            sb.append("#L").append(option.optionNo()).append("#")
                    .append(option.optionNo()).append(". ")
                    .append(objective(selector.stage(), option).description())
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
        HpChallengeService.Task option = HpChallengeService.optionalTask(selector.stage(), optionNo);
        if (option == null) {
            return error("选择的试炼不存在。");
        }
        if (!availableOptions(chr, selector).contains(option)) {
            return error("该试炼当前不能选择。");
        }
        HpChallengeService.Task selected = HpChallengeService.selectLifeProofOptional(chr, selector.stage(),
                optionNo, selector.selectorNo());
        if (selected == null) {
            return error("该试炼当前不能选择。");
        }
        int slotQuestId = questId(selector.stage(), selector.branch(), OPTION_SLOT_START + selector.selectorNo() - 1);
        return OK_PREFIX + slotQuestId + "|" + "已选定：" + objective(selector.stage(), selected).description();
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

    public static boolean isAutoCompleteNpcTalk(Character chr, int questId, int npcId) {
        QuestMeta meta = QUESTS.get(questId);
        if (chr == null || meta == null || !meta.isVisible()
                || meta.objective().type() != ObjectiveType.NPC_TALK) {
            return false;
        }
        if (chr.getQuestStatus(questId) != QuestStatus.Status.STARTED.getId()) {
            return false;
        }
        return canUseNpc(chr, npcId) && npcId == completeNpcId(meta);
    }

    public static int nextContinuationQuestIdAtNpc(Character chr, int questId, int npcId) {
        QuestMeta completed = QUESTS.get(questId);
        QuestMeta next = nextContinuationAtNpc(chr, completed, npcId);
        return next == null ? 0 : next.questId();
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

    private static boolean ensureAndMigrateState(Character chr) {
        if (!HpChallengeService.ensureLifeProofState(chr)) {
            return false;
        }
        migrateLegacyOptionalQuestState(chr);
        return true;
    }

    private static HpChallengeService.LifeProofOptionalProgress selectedOptional(Character chr, QuestMeta meta) {
        if (chr == null || meta == null || meta.kind() != QuestKind.OPTION_SLOT) {
            return null;
        }
        return HpChallengeService.selectedLifeProofOptional(chr, meta.stage(), meta.selectorNo());
    }

    private static Objective effectiveObjective(Character chr, QuestMeta meta) {
        if (meta == null || meta.kind() != QuestKind.OPTION_SLOT) {
            return meta == null ? null : meta.objective();
        }
        HpChallengeService.LifeProofOptionalProgress selected = selectedOptional(chr, meta);
        return selected == null ? meta.objective() : objective(meta.stage(), selected.task());
    }

    private static HpChallengeService.Task effectiveTask(Character chr, QuestMeta meta) {
        if (meta == null) {
            return null;
        }
        if (meta.kind() != QuestKind.OPTION_SLOT) {
            return meta.task();
        }
        HpChallengeService.LifeProofOptionalProgress selected = selectedOptional(chr, meta);
        return selected == null ? null : selected.task();
    }

    private static void migrateLegacyOptionalQuestState(Character chr) {
        HpChallengeService.JobBranch branch = lifeProofBranch(chr);
        if (chr == null || branch == null) {
            return;
        }
        for (int stage = 1; stage <= 7; stage++) {
            int selectedCount = HpChallengeService.selectedLifeProofOptionalCount(chr, stage);
            for (int optionNo = 1; optionNo <= 8; optionNo++) {
                int legacyQuestId = questId(stage, branch, OPTION_SLOT_START + optionNo - 1);
                QuestStatus legacyStatus = chr.getQuestNoAdd(Quest.getInstance(legacyQuestId));
                if (legacyStatus == null || legacyStatus.getStatus() != QuestStatus.Status.STARTED
                        && legacyStatus.getStatus() != QuestStatus.Status.COMPLETED) {
                    continue;
                }
                if (optionNo <= OPTIONAL_REQUIRED_COUNT
                        && HpChallengeService.selectedLifeProofOptional(chr, stage, optionNo) != null) {
                    continue;
                }
                if (selectedCount >= OPTIONAL_REQUIRED_COUNT) {
                    resetLegacyOptionalQuest(chr, legacyQuestId);
                    HpChallengeService.clearLifeProofOptionalSelection(chr, stage, optionNo);
                    continue;
                }

                int slotNo = selectedCount + 1;
                HpChallengeService.Task task = HpChallengeService.selectLifeProofOptional(chr, stage, optionNo, slotNo);
                if (task == null) {
                    continue;
                }
                selectedCount = slotNo;
                int slotQuestId = questId(stage, branch, OPTION_SLOT_START + slotNo - 1);
                Objective objective = objective(stage, task);
                int progress = legacyStatus.getStatus() == QuestStatus.Status.COMPLETED
                        ? objective.requiredCount()
                        : legacyOptionalProgress(chr, legacyQuestId, objective);
                HpChallengeService.setLifeProofOptionalProgress(chr, stage, slotNo, progress);

                if (legacyStatus.getStatus() == QuestStatus.Status.COMPLETED) {
                    HpChallengeService.completeLifeProofOptional(chr, stage, slotNo);
                    putQuestStatus(chr, slotQuestId, QuestStatus.Status.COMPLETED,
                            BRANCH_INFO.get(branch).instructorNpcId(), "001");
                    completeNextBridgeSilently(chr, QUESTS.get(slotQuestId));
                } else {
                    putQuestStatus(chr, slotQuestId, QuestStatus.Status.STARTED,
                            BRANCH_INFO.get(branch).instructorNpcId(),
                            progress >= objective.requiredCount() ? "001" : "000");
                }

                if (legacyQuestId != slotQuestId) {
                    resetLegacyOptionalQuest(chr, legacyQuestId);
                }
            }
        }
    }

    private static void normalizeCompletedStages(Character chr) {
        HpChallengeService.JobBranch branch = lifeProofBranch(chr);
        if (chr == null || branch == null) {
            return;
        }

        Set<Integer> completedStages = completedStageMarkers(chr, branch);
        normalizeCompletedStages(chr, branch, completedStages);
    }

    static Set<Integer> completedStageMarkers(Character chr, HpChallengeService.JobBranch branch) {
        if (chr == null || branch == null) {
            return Set.of();
        }
        Set<Integer> completedStages = new LinkedHashSet<>();
        completedStages.addAll(HpChallengeService.activeLifeProofRewardStages(chr));
        completedStages.addAll(HpChallengeService.lifeProofStateCompletedStages(chr));
        for (int stage = 1; stage <= 7; stage++) {
            int rewardQuestId = questId(stage, branch, REWARD_SLOT);
            if (chr.getQuestStatus(rewardQuestId) == QuestStatus.Status.COMPLETED.getId()) {
                completedStages.add(stage);
            }
        }
        return completedStages;
    }

    static void normalizeCompletedStages(Character chr, HpChallengeService.JobBranch branch,
                                         Set<Integer> completedStages) {
        if (chr == null || branch == null || completedStages == null || completedStages.isEmpty()) {
            return;
        }
        for (int stage = 1; stage <= 7; stage++) {
            if (!completedStages.contains(stage)) {
                continue;
            }
            normalizeCompletedStage(chr, stage, branch);
        }
    }

    private static void normalizeCompletedStage(Character chr, int stage, HpChallengeService.JobBranch branch) {
        for (QuestMeta meta : stageBranchVisibleQuests(stage, branch)) {
            if (chr.getQuestStatus(meta.questId()) != QuestStatus.Status.COMPLETED.getId()) {
                if (meta.kind() == QuestKind.OPTION_SLOT) {
                    HpChallengeService.completeLifeProofOptional(chr, stage, meta.selectorNo());
                    completeNextBridgeSilently(chr, meta);
                }
                putQuestStatus(chr, meta.questId(), QuestStatus.Status.COMPLETED, completeNpcId(meta), "001");
            }
        }

        for (int slot = OPTION_SLOT_START + OPTIONAL_REQUIRED_COUNT; slot <= OPTION_SLOT_END; slot++) {
            int oldQuestId = questId(stage, branch, slot);
            if (hasStartedOrCompletedQuest(chr, oldQuestId)) {
                resetLegacyOptionalQuest(chr, oldQuestId);
                HpChallengeService.clearLifeProofOptionalSelection(chr, stage, slot - OPTION_SLOT_START + 1);
            }
        }
        for (int slot = BRIDGE_SLOT_START; slot <= RESERVED_SLOT; slot++) {
            int hiddenQuestId = questId(stage, branch, slot);
            if (hasStartedOrCompletedQuest(chr, hiddenQuestId)) {
                resetLegacyOptionalQuest(chr, hiddenQuestId);
            }
        }
    }

    private static boolean hasStartedOrCompletedQuest(Character chr, int questId) {
        QuestStatus status = chr.getQuestNoAdd(Quest.getInstance(questId));
        return status != null
                && (status.getStatus() == QuestStatus.Status.STARTED
                || status.getStatus() == QuestStatus.Status.COMPLETED);
    }

    private static int legacyOptionalProgress(Character chr, int questId, Objective objective) {
        QuestStatus status = chr.getQuest(Quest.getInstance(questId));
        return switch (objective.type()) {
            case KILL, BOSS -> {
                int progress = 0;
                for (int targetId : objective.targetIds()) {
                    progress = Math.max(progress, parseProgress(status.getProgress(targetId)));
                }
                yield progress;
            }
            case ITEM -> Math.min(objective.requiredCount(), itemCount(chr, objective.itemId()));
            case MESO -> chr.getMeso() >= objective.mesoCost() ? objective.requiredCount() : 0;
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL, NPC_TALK ->
                    parseProgress(status.getProgress(CUSTOM_PROGRESS_KEY));
            case SELECT_OPTION, OPTION_SLOT, REWARD -> 0;
        };
    }

    private static void resetLegacyOptionalQuest(Character chr, int questId) {
        putQuestStatus(chr, questId, QuestStatus.Status.NOT_STARTED, 0, "");
    }

    private static void putQuestStatus(Character chr, int questId, QuestStatus.Status status, int npcId,
                                       String customProgress) {
        Quest quest = Quest.getInstance(questId);
        QuestStatus newStatus = new QuestStatus(quest, status, npcId);
        if (status == QuestStatus.Status.STARTED && customProgress != null && !customProgress.isBlank()) {
            newStatus.setProgress(CUSTOM_PROGRESS_KEY, customProgress);
        }
        synchronized (chr.getQuests()) {
            chr.getQuests().put((short) questId, newStatus);
        }
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
        Objective objective = effectiveObjective(chr, meta);
        if (objective.type() == ObjectiveType.NPC_TALK) {
            sb.append(mentorCompletionLine(npcId)).append("\r\n\r\n");
            sb.append("确认由#p").append(npcId).append("#记录这一步生命之证？");
            return sb.toString();
        }
        sb.append("这一步试炼已经达成。确认提交吗？\r\n\r\n");
        sb.append(completionReviewText(objective));
        appendObjectiveHint(sb, meta, objective);
        return sb.toString();
    }

    public static String endPrompt(Character chr, int questId, int npcId) {
        QuestMeta meta = QUESTS.get(questId);
        if (meta == null || !meta.isVisible()) {
            return error("这个任务暂时无法处理。");
        }
        if (!ensureAndMigrateState(chr)) {
            return error(HpChallengeService.openRequirementText(chr));
        }
        Quest quest = Quest.getInstance(questId);
        if (chr.getQuest(quest).getStatus() != QuestStatus.Status.STARTED) {
            return error("这一步生命之证试炼当前没有进行中。");
        }
        syncActiveObjectiveProgress(chr);
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
        Objective objective = effectiveObjective(chr, meta);
        if (objective.type() == ObjectiveType.NPC_TALK && npcId == completeNpcId(meta)) {
            markNpcTalkProgress(chr, meta, npcId, false);
        }
        syncActiveObjectiveProgress(chr);
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

        if (objective.isCollection()) {
            List<ItemCollection> multi = multiItemCollections(meta);
            if (multi != null) {
                if (!removeMultiItems(chr, multi)) {
                    return error("提交物品不足。");
                }
            } else if (!removeItem(chr, objective.itemId(), objective.requiredCount())) {
                return error("提交物品不足。");
            }
        }
        if (objective.type() == ObjectiveType.MESO) {
            if (chr.getMeso() < objective.mesoCost()) {
                return error("金币不足，需要 " + objective.mesoCost() + " 金币。");
            }
            if (meta.kind() == QuestKind.OPTION_SLOT) {
                chr.gainMeso(-objective.mesoCost(), true, true, true);
                HpChallengeService.completeLifeProofOptional(chr, meta.stage(), meta.selectorNo());
                completeNextBridgeSilently(chr, meta);
            } else {
                chr.gainMeso(-objective.mesoCost(), true, true, true);
            }
        }
        if (meta.kind() == QuestKind.OPTION_SLOT && objective.type() != ObjectiveType.MESO) {
            HpChallengeService.completeLifeProofOptional(chr, meta.stage(), meta.selectorNo());
            completeNextBridgeSilently(chr, meta);
        }
        chr.yellowMessage("生命之证：" + meta.name() + "完成。");
        return ok("这一步生命之证已经记录。");
    }

    public static String afterNativeComplete(Character chr, int questId, int npcId) {
        if (chr == null) {
            return "";
        }
        refreshQuestRules(chr);

        QuestMeta completed = QUESTS.get(questId);
        if (completed == null) {
            return "";
        }
        QuestMeta next = nextAvailableQuest(chr, completed.branch());
        if (next == null) {
            return "";
        }

        int startNpcId = startNpcId(next);
        if (startNpcId == npcId) {
            return "\r\n\r\n继续找#p" + startNpcId + "#，点击该 NPC 头上的生命之证任务入口。";
        }
        return "\r\n\r\n前往#p" + startNpcId + "#，点击生命之证任务入口。";
    }

    private static String progressPrompt(Character chr, QuestMeta meta, int npcId) {
        StringBuilder sb = new StringBuilder();
        Objective objective = effectiveObjective(chr, meta);
        sb.append("#e").append(meta.name()).append("#n\r\n\r\n");
        sb.append(stageStory(meta.stage())).append("\r\n\r\n");
        sb.append(progressLeadText(meta, objective, npcId)).append("\r\n");
        sb.append(progressText(chr, meta, npcId)).append("\r\n\r\n");
        sb.append(nextStepText(chr, meta, npcId));
        return sb.toString();
    }

    private static String submitBlockedText(Character chr, QuestMeta meta, int npcId) {
        int completeNpcId = completeNpcId(meta);
        if (npcId != completeNpcId) {
            if (effectiveObjective(chr, meta).type() == ObjectiveType.NPC_TALK) {
                return "请前往#p" + completeNpcId + "#，点击任务完成图标，由对方确认这一步生命之证。";
            }
            return "请前往#p" + completeNpcId + "#提交这一步生命之证试炼。";
        }
        return "#r这一步试炼还没有完成。#k\r\n\r\n" + progressPrompt(chr, meta, npcId);
    }

    private static String progressText(Character chr, QuestMeta meta, int npcId) {
        Objective objective = effectiveObjective(chr, meta);
        return switch (objective.type()) {
            case KILL, BOSS -> {
                if (objective.perMob()) {
                    StringBuilder sb = new StringBuilder();
                    QuestStatus st = chr.getQuest(Quest.getInstance(meta.questId()));
                    for (int i = 0; i < objective.targetIds().size(); i++) {
                        if (i > 0) sb.append("\r\n");
                        int tid = objective.targetIds().get(i);
                        int p = parseProgress(st.getProgress(tid));
                        sb.append(MonsterInformationProvider.getInstance().getMobNameFromId(tid))
                                .append(" #b").append(p)
                                .append("#k/#r").append(objective.requiredCount()).append("#k");
                    }
                    yield sb.toString();
                }
                yield mobTargetText(objective) + "，#b" + mobProgress(chr, meta)
                        + "#k/#r" + objective.requiredCount() + "#k";
            }
            case ITEM -> {
                List<ItemCollection> multi = multiItemCollections(meta);
                if (multi != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < multi.size(); i++) {
                        if (i > 0) sb.append("\r\n");
                        ItemCollection c = multi.get(i);
                        sb.append("#i").append(c.itemId()).append("# #t").append(c.itemId()).append("# ")
                                .append("#b").append(Math.min(c.requiredCount(), itemCount(chr, c.itemId())))
                                .append("#k/#r").append(c.requiredCount()).append("#k")
                                .append(dropperText(c.droppers()));
                    }
                    yield sb.toString();
                }
                yield "#i" + objective.itemId() + "# #t" + objective.itemId() + "# "
                        + "#b" + itemCount(chr, objective.itemId()) + "#k/#r" + objective.requiredCount() + "#k"
                        + dropperText(objective.targetIds());
            }
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL -> "#b" + customProgress(chr, meta)
                    + "#k/#r" + objective.requiredCount() + "#k";
            case MESO -> "金币 #b" + chr.getMeso() + "#k / #r" + objective.mesoCost() + "#k";
            case NPC_TALK -> "与#p" + targetNpcId(meta, objective) + "#确认 #b"
                    + customProgress(chr, meta) + "#k/#r" + objective.requiredCount() + "#k";
            case SELECT_OPTION -> "等待选择 1 项附加试炼";
            case OPTION_SLOT -> "等待选择后的附加试炼目标";
            case REWARD -> rewardProgressText(chr, meta);
        };
    }

    private static ProgressValue questProgressValue(Character chr, QuestMeta meta) {
        if (chr == null || meta == null) {
            return new ProgressValue(0, 1);
        }

        Objective objective = effectiveObjective(chr, meta);
        if (objective == null) {
            return new ProgressValue(0, 1);
        }

        int required = Math.max(1, objective.requiredCount());
        if (chr.getQuestStatus(meta.questId()) == QuestStatus.Status.COMPLETED.getId()) {
            return new ProgressValue(required, required);
        }

        int current = switch (objective.type()) {
            case KILL, BOSS -> mobProgress(chr, meta);
            case ITEM -> {
                List<ItemCollection> multi = multiItemCollections(meta);
                yield multi != null ? multiItemProgress(chr, multi) : itemCount(chr, objective.itemId());
            }
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL, NPC_TALK -> customProgress(chr, meta);
            case MESO -> Math.min(chr.getMeso(), objective.mesoCost());
            case SELECT_OPTION -> 0;
            case OPTION_SLOT -> optionSlotProgress(chr, meta, objective);
            case REWARD -> HpChallengeService.stage(meta.stage()).requiredLevel() <= chr.getLevel() && !HpChallengeService.hasActiveReward(chr.getId(), meta.stage()) ? 1 : 0;
        };
        return new ProgressValue(Math.max(0, Math.min(current, required)), required);
    }

    private static int optionSlotProgress(Character chr, QuestMeta meta, Objective objective) {
        HpChallengeService.LifeProofOptionalProgress selected = selectedOptional(chr, meta);
        if (selected == null) {
            return 0;
        }
        int current = selected.currentCount();
        return switch (objective.type()) {
            case ITEM -> Math.max(current, itemCount(chr, objective.itemId()));
            case MESO -> Math.max(current, chr.getMeso() >= objective.mesoCost() ? objective.requiredCount() : 0);
            default -> current;
        };
    }

    private static String startObjectiveText(QuestMeta meta, Objective objective) {
        int completeNpcId = completeNpcId(meta);
        return switch (objective.type()) {
            case NPC_TALK -> "";
            case ITEM -> "把需要的证明物带回#p" + completeNpcId + "#。";
            case KILL -> "按导师指定的战斗试炼证明自己。";
            case BOSS -> "参与首领战，把胜利记录进生命之证。";
            case MESO -> "准备这一步试炼所需的金币。";
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE -> "完成指定次数的协作试炼。";
            case SCROLL_100 -> "用稳定的强化结果证明自己。";
            case JUMP_MANUAL -> "完成指定的跳跃试炼。";
            case SELECT_OPTION -> "从剩余的附加试炼中选定一个方向。";
            case OPTION_SLOT -> "继续完成已经选定的附加试炼。";
            case REWARD -> "领取本阶段生命之证奖励。";
        };
    }

    private static String completionReviewText(Objective objective) {
        return switch (objective.type()) {
            case NPC_TALK -> "请导师留下确认。";
            case ITEM -> "把收集到的证明物交给导师。";
            case KILL, BOSS -> "把战斗记录写入生命之证。";
            case MESO -> "缴纳准备好的金币。";
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE -> "记录这项协作试炼。";
            case SCROLL_100 -> "记录这次强化试炼。";
            case JUMP_MANUAL -> "记录这次跳跃试炼。";
            case SELECT_OPTION -> "记录这次附加试炼选择。";
            case OPTION_SLOT -> "记录已经完成的附加试炼。";
            case REWARD -> "领取本阶段生命之证奖励。";
        };
    }

    private static String progressLeadText(QuestMeta meta, Objective objective, int npcId) {
        int completeNpcId = completeNpcId(meta);
        return switch (objective.type()) {
            case NPC_TALK -> npcId == completeNpcId
                    ? "#p" + completeNpcId + "#会确认这一步生命之证。"
                    : "去见#p" + completeNpcId + "#，让对方确认这一步生命之证。";
            case ITEM -> "把需要的物品收齐后，回#p" + completeNpcId + "#提交。";
            case KILL -> "完成指定击杀后，回#p" + completeNpcId + "#提交。";
            case BOSS -> "完成首领战记录后，回#p" + completeNpcId + "#提交。";
            case MESO -> "准备足够金币后，回#p" + completeNpcId + "#提交。";
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL ->
                    "完成这项试炼后，回#p" + completeNpcId + "#提交。";
            case SELECT_OPTION -> "从剩余的附加试炼中选定一个方向。";
            case OPTION_SLOT -> "完成已经选定的附加试炼。";
            case REWARD -> "本阶段试炼已经收束，回#p" + completeNpcId + "#领取生命之证奖励。";
        };
    }

    private static String nextStepText(Character chr, QuestMeta meta, int npcId) {
        int completeNpcId = completeNpcId(meta);
        Objective objective = effectiveObjective(chr, meta);
        if (objective.type() == ObjectiveType.NPC_TALK) {
            if (npcId == completeNpcId) {
                return "点击确定后，由#p" + completeNpcId + "#确认这一步生命之证。";
            }
            return "前往#p" + completeNpcId + "#，点击任务完成图标，由对方确认这一步生命之证。";
        }
        if (!objectiveSatisfied(chr, meta, npcId)) {
            return switch (objective.type()) {
                case ITEM -> multiItemCollections(meta) != null
                        ? "继续收集五种水晶。"
                        : "继续收集#t" + objective.itemId() + "#。";
                case KILL, BOSS -> "继续完成指定击杀。";
                case MESO -> "准备足够金币后回#p" + completeNpcId + "#提交。";
                case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL ->
                        "继续完成试炼进度，然后回#p" + completeNpcId + "#提交。";
                case SELECT_OPTION -> "重新打开任务，选择本轮附加试炼。";
                case OPTION_SLOT -> "重新打开选择任务，选择本轮附加试炼。";
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
        Objective objective = effectiveObjective(chr, meta);
        return switch (objective.type()) {
            case KILL, BOSS -> mobProgress(chr, meta) >= objective.requiredCount();
            case ITEM -> {
                List<ItemCollection> multi = multiItemCollections(meta);
                if (multi != null) {
                    yield multiItemSatisfied(chr, multi);
                }
                yield itemCount(chr, objective.itemId()) >= objective.requiredCount();
            }
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL ->
                    customProgress(chr, meta) >= objective.requiredCount();
            case MESO -> chr.getMeso() >= objective.mesoCost();
            case NPC_TALK -> customProgress(chr, meta) >= objective.requiredCount();
            case SELECT_OPTION -> false;
            case OPTION_SLOT -> false;
            case REWARD -> true;
        };
    }

    private static boolean multiItemSatisfied(Character chr, List<ItemCollection> multi) {
        for (ItemCollection c : multi) {
            if (itemCount(chr, c.itemId()) < c.requiredCount()) return false;
        }
        return true;
    }

    private static int multiItemProgress(Character chr, List<ItemCollection> multi) {
        int total = 0;
        for (ItemCollection c : multi) {
            total += Math.min(c.requiredCount(), itemCount(chr, c.itemId()));
        }
        return total;
    }

    static int startNpcId(QuestMeta meta) {
        if (meta == null) {
            return 0;
        }
        if (isFirstStageVisitQuest(meta) && meta.slot() > MAIN_SLOT_START) {
            QuestMeta previous = previousVisibleQuest(meta);
            if (previous != null && isFirstStageVisitQuest(previous)) {
                return completeNpcId(previous);
            }
        }
        return branchInstructorNpcId(meta);
    }

    static int branchInstructorNpcId(QuestMeta meta) {
        return BRANCH_INFO.get(meta.branch()).instructorNpcId();
    }

    static int completeNpcId(QuestMeta meta) {
        if (meta.objective().type() == ObjectiveType.NPC_TALK) {
            return targetNpcId(meta);
        }
        return startNpcId(meta);
    }

    private static int targetNpcId(QuestMeta meta) {
        return targetNpcId(meta, meta.objective());
    }

    private static int targetNpcId(QuestMeta meta, Objective objective) {
        if (!objective.targetIds().isEmpty()) {
            return objective.targetIds().getFirst();
        }
        return startNpcId(meta);
    }

    static boolean isNpcTalkVisitQuest(QuestMeta meta) {
        return meta != null && meta.isVisible() && meta.objective().type() == ObjectiveType.NPC_TALK;
    }

    static int staticNextQuestId(QuestMeta meta) {
        if (meta == null || meta.kind() != QuestKind.MAIN) {
            return 0;
        }
        QuestMeta next = nextVisibleQuest(meta);
        if (next == null || next.kind() != QuestKind.MAIN && next.kind() != QuestKind.SELECTOR) {
            return 0;
        }
        return next.questId();
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
            return true;
        }
        return objectiveSatisfied(chr, meta, npcId) && quest.canComplete(chr, npcId);
    }

    private static boolean canSubmitAtNpc(Character chr, Quest quest, int npcId) {
        QuestMeta meta = QUESTS.get((int) quest.getId());
        if (meta == null || !canUseNpc(chr, npcId) || npcId != completeNpcId(meta)) {
            return false;
        }
        if (meta.objective().type() == ObjectiveType.NPC_TALK) {
            return objectiveSatisfied(chr, meta, npcId);
        }
        return objectiveSatisfied(chr, meta, npcId)
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

    private static String dropperText(List<Integer> mobIds) {
        if (mobIds == null || mobIds.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\r\n获取：");
        for (int i = 0; i < mobIds.size(); i++) {
            if (i > 0) {
                sb.append("/");
            }
            sb.append("#o").append(mobIds.get(i)).append("#");
        }
        return sb.toString();
    }

    private static String rewardProgressText(Character chr, QuestMeta meta) {
        HpChallengeService.RewardTarget target = HpChallengeService.rewardTarget(chr, HpChallengeService.stage(meta.stage()));
        return "阶段目标 HP #b" + target.targetHp() + "#k，阶段目标 MP #b" + target.targetMp() + "#k";
    }

    public static void addDynamicQuestDrops(Character chr, int mobId, List<MonsterDropEntry> visibleQuestEntry) {
        if (chr == null || visibleQuestEntry == null) {
            return;
        }
        HpChallengeService.JobBranch branch = lifeProofBranch(chr);
        if (branch == null) {
            return;
        }
        QuestMeta active = QUESTS.values().stream()
                .filter(QuestMeta::isVisible)
                .filter(meta -> meta.branch() == branch)
                .filter(meta -> chr.getQuestStatus(meta.questId()) == QuestStatus.Status.STARTED.getId())
                .filter(meta -> {
                    Objective objective = effectiveObjective(chr, meta);
                    return objective.isCollection() && objective.targetIds().contains(mobId);
                })
                .findFirst()
                .orElse(null);
        if (active == null) {
            return;
        }
        Objective objective = effectiveObjective(chr, active);
        // Handle multi-item collection (Stage VII five crystals)
        List<ItemCollection> multi = multiItemCollections(active);
        if (multi != null) {
            for (ItemCollection c : multi) {
                if (!c.droppers().contains(mobId)) continue;
                int held = chr.getInventory(ItemConstants.getInventoryType(c.itemId())).countById(c.itemId());
                if (held >= c.requiredCount()) continue;
                visibleQuestEntry.add(new MonsterDropEntry(c.itemId(), DYNAMIC_DROP_CHANCE, 1, 1, (short) active.questId()));
            }
            return;
        }
        int itemId = objective.itemId();
        int held = chr.getInventory(ItemConstants.getInventoryType(itemId)).countById(itemId);
        if (held >= objective.requiredCount()) {
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
        QuestMeta active = currentStartedVisibleQuest(chr);
        Objective objective = effectiveObjective(chr, active);
        if (active == null || objective == null
                || objective.type() != ObjectiveType.KILL && objective.type() != ObjectiveType.BOSS
                || !objective.targetIds().contains(mobId)) {
            return;
        }

        if (active.kind() == QuestKind.OPTION_SLOT) {
            int before = mobProgress(chr, active);
            if (!HpChallengeService.incrementLifeProofOptionalProgress(chr, active.stage(), active.selectorNo(),
                    HpChallengeService.TargetType.KILL, mobId, "")) {
                return;
            }
            syncActiveObjectiveProgress(chr);
            int after = mobProgress(chr, active);
            chr.yellowMessage("生命之证：" + objective.description() + " " + after + "/"
                    + objective.requiredCount());
            if (before < objective.requiredCount() && after >= objective.requiredCount()) {
                refreshQuestRules(chr);
            } else {
                refreshQuestProgress(chr);
            }
            return;
        }

        QuestStatus status = chr.getQuest(Quest.getInstance(active.questId()));
        boolean changed = migrateLegacyMainMobProgress(status, objective);
        changed = syncSharedMainMobProgress(status, objective) || changed;
        refreshMainMobProgress(chr, active, status, changed);
    }

    public static boolean shouldSkipGenericMobProgress(Character chr, QuestStatus status, int mobId) {
        if (chr == null || status == null || status.getStatus() != QuestStatus.Status.STARTED) {
            return false;
        }
        QuestMeta meta = QUESTS.get((int) status.getQuest().getId());
        if (meta == null || meta != currentStartedVisibleQuest(chr)) {
            return false;
        }
        if (meta.kind() != QuestKind.OPTION_SLOT) {
            return false;
        }
        Objective objective = effectiveObjective(chr, meta);
        return objective != null
                && (objective.type() == ObjectiveType.KILL || objective.type() == ObjectiveType.BOSS)
                && objective.targetIds().contains(mobId);
    }

    private static boolean migrateLegacyMainMobProgress(QuestStatus status, Objective objective) {
        if (status == null || objective == null
                || objective.type() != ObjectiveType.KILL && objective.type() != ObjectiveType.BOSS) {
            return false;
        }
        boolean changed = false;
        int legacyProgress = parseProgress(status.getProgress(CUSTOM_PROGRESS_KEY));
        if (legacyProgress > 0) {
            int migratedCount = Math.min(legacyProgress, objective.requiredCount());
            String migratedProgress = paddedProgress(migratedCount);
            for (int targetId : objective.targetIds()) {
                if (parseProgress(status.getProgress(targetId)) < migratedCount) {
                    status.setProgress(targetId, migratedProgress);
                    changed = true;
                }
            }
        }
        if (status.removeProgress(CUSTOM_PROGRESS_KEY)) {
            changed = true;
        }
        return changed;
    }

    private static boolean syncSharedMainMobProgress(QuestStatus status, Objective objective) {
        if (status == null || objective == null || objective.perMob()
                || objective.type() != ObjectiveType.KILL && objective.type() != ObjectiveType.BOSS) {
            return false;
        }
        int progress = 0;
        for (int targetId : objective.targetIds()) {
            progress = Math.max(progress, parseProgress(status.getProgress(targetId)));
        }
        progress = Math.min(objective.requiredCount(), progress);
        String value = paddedProgress(progress);
        boolean changed = false;
        for (int targetId : objective.targetIds()) {
            if (!value.equals(status.getProgress(targetId))) {
                status.setProgress(targetId, value);
                changed = true;
            }
        }
        return changed;
    }

    private static void refreshMainMobProgress(Character chr, QuestMeta meta, QuestStatus status, boolean changed) {
        if (changed) {
            chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
            chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
        }
        Quest quest = Quest.getInstance(meta.questId());
        if (quest.canComplete(chr, completeNpcId(meta))) {
            refreshQuestRules(chr);
            return;
        }
        refreshQuestProgress(chr);
    }

    private static String paddedProgress(int progress) {
        return StringUtil.getLeftPaddedStr(Integer.toString(Math.max(0, progress)), '0', 3);
    }

    public static void syncActiveMesoProgress(Character chr) {
        syncActiveObjectiveProgress(chr);
    }

    public static String gmStatus(Character chr) {
        if (chr == null) {
            return "角色不存在。";
        }
        if (!ensureAndMigrateState(chr)) {
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
        if (!ensureAndMigrateState(target)) {
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
        items.put("visit_leafre_maps", new ItemCollection(4005000, "力量水晶", 30, List.of(8140101, 8140102, 8140103)));
        items.put("visit_leafre_dragon_maps", new ItemCollection(4033002, "龙巢生命之证", 40, List.of(8140700, 8140701, 8140702, 8140703)));
        items.put("visit_ludi_time", new ItemCollection(4005001, "智慧水晶", 30, List.of(8140200, 8140300)));
        items.put("visit_ludi_maps", new ItemCollection(4033004, "玩具塔生命之证", 60, List.of(7140000, 8141100, 8140200, 8140300)));
        items.put("visit_expedition", new ItemCollection(4005004, "黑暗水晶", 30, List.of(8190003, 8190004, 8140500)));
        items.put("visit_deep_sea", new ItemCollection(4005002, "敏捷水晶", 30, List.of(8140600, 8141300, 8142100)));
        items.put("visit_deep_sea_hidden", new ItemCollection(4033007, "暗流生命之证", 50, List.of(7130020, 8140600, 8150100, 8150101)));
        items.put("visit_temple", new ItemCollection(4005003, "幸运水晶", 30, List.of(8200005, 8200006, 8200009, 8200010)));
        items.put("visit_temple_maps", new ItemCollection(4033009, "回忆生命之证", 75, List.of(8200005, 8200006, 8200007, 8200008, 8200009, 8200010, 8200011, 8200012)));
        items.put("visit_final", new ItemCollection(4005004, "黑暗水晶", 30, List.of(8190004, 8200011, 8200012)));
        return items;
    }

    private static Map<String, List<ItemCollection>> buildMultiItemCollections() {
        Map<String, List<ItemCollection>> multi = new HashMap<>();
        multi.put("visit_final", List.of(
                new ItemCollection(4005000, "力量水晶", 10, List.of(8140101, 8140102, 8140103)),
                new ItemCollection(4005001, "智慧水晶", 10, List.of(8140200, 8140300)),
                new ItemCollection(4005002, "敏捷水晶", 10, List.of(8140600, 8141300, 8142100)),
                new ItemCollection(4005003, "幸运水晶", 10, List.of(8200005, 8200006, 8200009, 8200010)),
                new ItemCollection(4005004, "黑暗水晶", 10, List.of(8190004, 8200011, 8200012))
        ));
        return multi;
    }

    /**
     * Returns the multi-item collection list for a task, or null if it's a single-item collection.
     */
    static List<ItemCollection> multiItemCollections(QuestMeta meta) {
        if (meta == null) return null;
        HpChallengeService.Task task = meta.task();
        if (task == null) return null;
        String collectionKey = switch (task.key()) {
            case "visit_final" -> "visit_final";
            default -> null;
        };
        return collectionKey == null ? null : MULTI_ITEM_COLLECTIONS.get(collectionKey);
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
                            "选择第 " + selectorNo + " 项附加试炼", List.of(), 0, 0, false);
                    QuestMeta meta = new QuestMeta(questId, stageNo, branch, QuestKind.SELECTOR,
                            SELECTOR_SLOT_START + selectorNo - 1, 0, selectorNo, null, objective,
                            stageTitle(stageNo) + "：选择试炼 " + selectorNo);
                    quests.put(questId, meta);
                }
                for (int slotNo = 1; slotNo <= OPTIONAL_REQUIRED_COUNT; slotNo++) {
                    int slot = OPTION_SLOT_START + slotNo - 1;
                    int questId = questId(stageNo, branch, slot);
                    Objective objective = new Objective(ObjectiveType.OPTION_SLOT, 1,
                            "完成第 " + slotNo + " 项已选择附加试炼", List.of(), 0, 0, false);
                    QuestMeta meta = new QuestMeta(questId, stageNo, branch, QuestKind.OPTION_SLOT, slot,
                            0, slotNo, null, objective, stageTitle(stageNo) + "：附加试炼 " + slotNo);
                    quests.put(questId, meta);
                }
                for (int optionNo = OPTIONAL_REQUIRED_COUNT + 1; optionNo <= 8; optionNo++) {
                    HpChallengeService.Task task = HpChallengeService.optionalTask(stageNo, optionNo);
                    int slot = OPTION_SLOT_START + optionNo - 1;
                    int questId = questId(stageNo, branch, slot);
                    QuestMeta meta = new QuestMeta(questId, stageNo, branch, QuestKind.RETIRED_OPTION, slot,
                            optionNo, 0, task, objective(stageNo, task),
                            stageTitle(stageNo) + "：附加试炼 " + optionNo);
                    quests.put(questId, meta);
                }
                int rewardQuestId = questId(stageNo, branch, REWARD_SLOT);
                Objective reward = new Objective(ObjectiveType.REWARD, 1, "领取本阶段生命之证奖励", List.of(), 0, 0, false);
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
        // Check multi-item collection first (Stage VII five crystals)
        String multiKey = switch (task.key()) {
            case "visit_final" -> "visit_final";
            default -> null;
        };
        if (multiKey != null) {
            List<ItemCollection> multi = MULTI_ITEM_COLLECTIONS.get(multiKey);
            if (multi != null) {
                int total = multi.stream().mapToInt(ItemCollection::requiredCount).sum();
                List<Integer> allDroppers = multi.stream()
                        .flatMap(c -> c.droppers().stream()).distinct().toList();
                return new Objective(ObjectiveType.ITEM, total,
                        task.description(), allDroppers, 0, 0, false);
            }
        }
        ItemCollection collection = collectionForTask(stage, task);
        if (collection != null) {
            return new Objective(ObjectiveType.ITEM, collection.requiredCount(), "收集#t" + collection.itemId() + "#",
                    collection.droppers(), collection.itemId(), 0, false);
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
        return new Objective(type, task.requiredCount(), task.description(), task.targetIds(), 0, task.mesoCost(), task.perMob());
    }

    private static ItemCollection collectionForTask(int stage, HpChallengeService.Task task) {
        if (task == null) {
            return null;
        }
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
                default -> {
                    log.warn("LifeProof unmapped map task: stage={} key={}", stage, task.key());
                    yield null;
                }
            };
        };
        return ITEM_COLLECTIONS.get(collectionKey);
    }

    private static void incrementCustomProgress(Character chr, ObjectiveType eventType, String eventName,
                                                String eventText) {
        QuestMeta active = currentStartedCustomQuest(chr);
        Objective objective = effectiveObjective(chr, active);
        if (active == null || objective == null || !matchesCustom(objective, eventType, eventName)) {
            return;
        }
        int before = customProgress(chr, active);
        int next = Math.min(objective.requiredCount(), before + 1);
        if (active.kind() == QuestKind.OPTION_SLOT) {
            boolean incremented = HpChallengeService.incrementLifeProofOptionalProgress(chr, active.stage(),
                    active.selectorNo(),
                    eventType == ObjectiveType.SCROLL_100
                            ? HpChallengeService.TargetType.SCROLL_100
                            : HpChallengeService.TargetType.PQ_ANY,
                    0, eventName);
            if (!incremented) {
                return;
            }
            syncActiveObjectiveProgress(chr);
            next = customProgress(chr, active);
            chr.yellowMessage("生命之证：" + eventText + " " + next + "/" + objective.requiredCount());
            if (before < objective.requiredCount() && next >= objective.requiredCount()) {
                refreshQuestRules(chr);
            } else {
                refreshQuestProgress(chr);
            }
            return;
        }
        QuestStatus status = chr.getQuest(Quest.getInstance(active.questId()));
        status.setProgress(CUSTOM_PROGRESS_KEY, StringUtil.getLeftPaddedStr(Integer.toString(next), '0', 3));
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
        chr.yellowMessage("生命之证：" + eventText + " " + next + "/" + objective.requiredCount());
        if (next >= objective.requiredCount()) {
            completeCustomProgress(chr, active, next);
        } else {
            refreshQuestProgress(chr);
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
        refreshQuestProgress(chr);
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
        HpChallengeService.JobBranch branch = lifeProofBranch(chr);
        if (branch == null) {
            return null;
        }
        return QUESTS.values().stream()
                .filter(QuestMeta::isVisible)
                .filter(meta -> meta.branch() == branch)
                .filter(meta -> chr.getQuestStatus(meta.questId()) == QuestStatus.Status.STARTED.getId())
                .filter(meta -> {
                    Objective objective = effectiveObjective(chr, meta);
                    return objective != null && objective.isCustomProgress();
                })
                .findFirst()
                .orElse(null);
    }

    private static QuestMeta currentStartedVisibleQuest(Character chr) {
        if (chr == null) {
            return null;
        }
        HpChallengeService.JobBranch branch = lifeProofBranch(chr);
        if (branch == null) {
            return null;
        }
        return QUESTS.values().stream()
                .filter(QuestMeta::isVisible)
                .filter(meta -> meta.branch() == branch)
                .filter(meta -> chr.getQuestStatus(meta.questId()) == QuestStatus.Status.STARTED.getId())
                .findFirst()
                .orElse(null);
    }

    private static HpChallengeService.JobBranch lifeProofBranch(Character chr) {
        if (chr == null || chr.getJob() == null) {
            return null;
        }
        HpChallengeService.JobBranch branch = HpChallengeService.branch(chr.getJob());
        BranchInfo info = BRANCH_INFO.get(branch);
        if (info == null || !info.jobIds().contains(chr.getJob().getId())) {
            return null;
        }
        return branch;
    }

    private static QuestMeta nextAvailableQuest(Character chr, HpChallengeService.JobBranch branch) {
        if (chr == null || branch == null) {
            return null;
        }
        for (int stage = 1; stage <= 7; stage++) {
            for (QuestMeta meta : stageBranchVisibleQuests(stage, branch)) {
                if (chr.getQuestStatus(meta.questId()) != QuestStatus.Status.NOT_STARTED.getId()) {
                    continue;
                }
                if (Quest.getInstance(meta.questId()).canStart(chr, startNpcId(meta))) {
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
        Objective objective = effectiveObjective(target, active);
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
                syncActiveObjectiveProgress(target);
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
            case OPTION_SLOT -> "当前需要先选择附加试炼。";
            case REWARD -> "当前阶段任务已经完成，可以回一转教官领取奖励。";
        };
    }

    private static void fillMobProgress(Character chr, QuestMeta meta) {
        Objective objective = effectiveObjective(chr, meta);
        if (meta.kind() == QuestKind.OPTION_SLOT) {
            HpChallengeService.setLifeProofOptionalProgress(chr, meta.stage(), meta.selectorNo(),
                    objective.requiredCount());
            syncActiveObjectiveProgress(chr);
            chr.yellowMessage("生命之证：" + meta.name() + " " + objective.requiredCount()
                    + "/" + objective.requiredCount());
            return;
        }
        QuestStatus status = chr.getQuest(Quest.getInstance(meta.questId()));
        String progress = StringUtil.getLeftPaddedStr(Integer.toString(objective.requiredCount()), '0', 3);
        for (int targetId : objective.targetIds()) {
            status.setProgress(targetId, progress);
        }
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
        chr.yellowMessage("生命之证：" + meta.name() + " " + objective.requiredCount()
                + "/" + objective.requiredCount());
    }

    private static String grantMissingItemsForGm(Character chr, QuestMeta meta) {
        Objective objective = effectiveObjective(chr, meta);
        List<ItemCollection> multi = multiItemCollections(meta);
        if (multi != null) {
            StringBuilder sb = new StringBuilder("已补齐五种水晶：\r\n");
            boolean anyGranted = false;
            for (ItemCollection c : multi) {
                int held = chr.getInventory(ItemConstants.getInventoryType(c.itemId())).countById(c.itemId());
                int missing = c.requiredCount() - held;
                if (missing <= 0) {
                    sb.append("#t").append(c.itemId()).append("#已足够\r\n");
                    continue;
                }
                if (!InventoryManipulator.addById(chr.getClient(), c.itemId(), (short) missing)) {
                    return "背包空间不足，无法补齐#t" + c.itemId() + "#。";
                }
                chr.sendPacket(PacketCreator.getShowItemGain(c.itemId(), (short) missing, true));
                sb.append("#t").append(c.itemId()).append("# +").append(missing).append("\r\n");
                anyGranted = true;
            }
            if (anyGranted) {
                syncActiveObjectiveProgress(chr);
            }
            sb.append("玩家可以回一转教官提交任务。");
            return sb.toString();
        }
        int held = chr.getInventory(ItemConstants.getInventoryType(objective.itemId())).countById(objective.itemId());
        int missing = objective.requiredCount() - held;
        if (missing <= 0) {
            return "当前收集物已经足够，玩家可以回一转教官提交任务。";
        }
        if (!InventoryManipulator.addById(chr.getClient(), objective.itemId(), (short) missing)) {
            return "背包空间不足，无法补齐#t" + objective.itemId() + "#。";
        }
        chr.sendPacket(PacketCreator.getShowItemGain(objective.itemId(), (short) missing, true));
        syncActiveObjectiveProgress(chr);
        return "已补齐当前生命之证收集物：" + meta.name() + "\r\n玩家可以回一转教官提交任务。";
    }

    private static void appendGmObjectiveProgress(StringBuilder sb, Character chr, QuestMeta meta) {
        Objective objective = effectiveObjective(chr, meta);
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
            case NPC_TALK -> sb.append("目标 NPC：").append(targetNpcId(meta, objective))
                    .append("，进度：").append(customProgress(chr, meta)).append("/")
                    .append(objective.requiredCount()).append("\r\n");
            case SELECT_OPTION -> sb.append("目标：选择 1 项附加试炼\r\n");
            case OPTION_SLOT -> sb.append("目标：等待选择后的附加试炼\r\n");
            case REWARD -> sb.append("目标：领取本阶段奖励\r\n");
        }
    }

    private static int mobProgress(Character chr, QuestMeta meta) {
        if (meta != null && meta.kind() == QuestKind.OPTION_SLOT) {
            HpChallengeService.LifeProofOptionalProgress selected = selectedOptional(chr, meta);
            return selected == null ? 0 : selected.currentCount();
        }
        QuestStatus status = chr.getQuest(Quest.getInstance(meta.questId()));
        int progress = Integer.MAX_VALUE;
        Objective objective = effectiveObjective(chr, meta);
        for (int targetId : objective.targetIds()) {
            progress = Math.min(progress, parseProgress(status.getProgress(targetId)));
        }
        return progress == Integer.MAX_VALUE ? 0 : progress;
    }

    private static int customProgress(Character chr, QuestMeta meta) {
        if (meta != null && meta.kind() == QuestKind.OPTION_SLOT) {
            HpChallengeService.LifeProofOptionalProgress selected = selectedOptional(chr, meta);
            return selected == null ? 0 : selected.currentCount();
        }
        String progress = chr.getQuest(Quest.getInstance(meta.questId())).getProgress(CUSTOM_PROGRESS_KEY);
        if (progress.isBlank()) {
            return 0;
        }
        return parseProgress(progress);
    }

    private static int parseProgress(String progress) {
        if (progress == null || progress.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(progress);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void completeCustomProgress(Character chr, QuestMeta meta, int progress) {
        Objective objective = effectiveObjective(chr, meta);
        if (meta.kind() == QuestKind.OPTION_SLOT) {
            HpChallengeService.setLifeProofOptionalProgress(chr, meta.stage(), meta.selectorNo(),
                    Math.min(progress, objective.requiredCount()));
            syncActiveObjectiveProgress(chr);
            chr.yellowMessage("生命之证：" + meta.name() + "已达成，请回一转教官完成任务。");
            refreshQuestProgress(chr);
            return;
        }
        QuestStatus status = chr.getQuest(Quest.getInstance(meta.questId()));
        status.setProgress(CUSTOM_PROGRESS_KEY, StringUtil.getLeftPaddedStr(
                Integer.toString(Math.min(progress, objective.requiredCount())), '0', 3));
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
        chr.yellowMessage("生命之证：" + meta.name() + "已达成，请回一转教官完成任务。");
        refreshQuestProgress(chr);
    }

    public static void syncActiveObjectiveProgress(Character chr) {
        QuestMeta active = currentStartedVisibleQuest(chr);
        if (active == null) {
            return;
        }
        if (active.kind() == QuestKind.OPTION_SLOT) {
            syncOptionSlotProgress(chr, active);
            return;
        }
        Objective objective = effectiveObjective(chr, active);
        if (objective.type() == ObjectiveType.KILL || objective.type() == ObjectiveType.BOSS) {
            QuestStatus status = chr.getQuest(Quest.getInstance(active.questId()));
            boolean changed = migrateLegacyMainMobProgress(status, objective);
            changed = syncSharedMainMobProgress(status, objective) || changed;
            refreshMainMobProgress(chr, active, status, changed);
            return;
        }
        if (objective.type() == ObjectiveType.MESO) {
            syncMesoProgress(chr, active);
        }
    }

    private static void syncOptionSlotProgress(Character chr, QuestMeta meta) {
        HpChallengeService.LifeProofOptionalProgress selected = selectedOptional(chr, meta);
        if (chr == null || meta == null || selected == null
                || chr.getQuestStatus(meta.questId()) != QuestStatus.Status.STARTED.getId()) {
            return;
        }
        Objective objective = objective(meta.stage(), selected.task());
        int progress = selected.currentCount();
        if (selected.completed()) {
            progress = objective.requiredCount();
        } else if (objective.type() == ObjectiveType.ITEM) {
            progress = Math.min(objective.requiredCount(), itemCount(chr, objective.itemId()));
            HpChallengeService.setLifeProofOptionalProgress(chr, meta.stage(), meta.selectorNo(), progress);
        } else if (objective.type() == ObjectiveType.MESO) {
            progress = chr.getMeso() >= objective.mesoCost() ? objective.requiredCount() : 0;
            HpChallengeService.setLifeProofOptionalProgress(chr, meta.stage(), meta.selectorNo(), progress);
        } else if (objective.type() == ObjectiveType.KILL || objective.type() == ObjectiveType.BOSS) {
            // KILL/BOSS progress is already stored in hp_challenge_progress by kill handlers.
            // selected.currentCount() reflects the DB value; keep it as the authoritative progress.
        }

        String value = progress >= objective.requiredCount() ? "001" : "000";
        QuestStatus status = chr.getQuest(Quest.getInstance(meta.questId()));
        String before = status.getProgress(CUSTOM_PROGRESS_KEY);
        if (value.equals(before)) {
            if ("001".equals(value)) {
                refreshQuestRules(chr);
            } else {
                refreshQuestProgress(chr);
            }
            return;
        }
        status.setProgress(CUSTOM_PROGRESS_KEY, value);
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
        if ("001".equals(value)) {
            refreshQuestRules(chr);
        } else {
            refreshQuestProgress(chr);
        }
    }

    private static void syncMesoProgress(Character chr, QuestMeta meta) {
        Objective objective = effectiveObjective(chr, meta);
        if (chr == null || meta == null || objective == null || objective.type() != ObjectiveType.MESO
                || chr.getQuestStatus(meta.questId()) != QuestStatus.Status.STARTED.getId()) {
            return;
        }
        if (meta.kind() == QuestKind.OPTION_SLOT) {
            syncActiveObjectiveProgress(chr);
            return;
        }
        String progress = chr.getMeso() >= objective.mesoCost() ? "001" : "000";
        QuestStatus status = chr.getQuest(Quest.getInstance(meta.questId()));
        if (progress.equals(status.getProgress(CUSTOM_PROGRESS_KEY))) {
            refreshQuestProgress(chr);
            return;
        }
        status.setProgress(CUSTOM_PROGRESS_KEY, progress);
        chr.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        chr.announceUpdateQuest(DelayedQuestUpdate.INFO, status);
        refreshQuestProgress(chr);
    }

    private static void completeNextBridgeSilently(Character chr, QuestMeta option) {
        int bridgeNo = option.selectorNo();
        if (bridgeNo <= 0 || bridgeNo > OPTIONAL_REQUIRED_COUNT) {
            return;
        }
        int bridgeQuestId = questId(option.stage(), option.branch(), BRIDGE_SLOT_START + bridgeNo - 1);
        Quest bridgeQuest = Quest.getInstance(bridgeQuestId);
        QuestStatus oldStatus = chr.getQuestNoAdd(bridgeQuest);
        if (oldStatus != null && oldStatus.getStatus() == QuestStatus.Status.COMPLETED) {
            return;
        }
        QuestStatus newStatus = new QuestStatus(bridgeQuest, QuestStatus.Status.COMPLETED,
                BRANCH_INFO.get(option.branch()).instructorNpcId());
        synchronized (chr.getQuests()) {
            chr.getQuests().put((short) bridgeQuestId, newStatus);
        }
    }

    private static QuestMeta nextContinuationAtNpc(Character chr, QuestMeta current, int npcId) {
        if (chr == null || current == null || npcId <= 0) {
            return null;
        }
        QuestMeta next = nextAvailableQuest(chr, current.branch());
        if (next == null || startNpcId(next) != npcId
                || !Quest.getInstance(next.questId()).canStart(chr, npcId)) {
            return null;
        }
        return next;
    }

    private static void refreshQuestRules(Character chr) {
        if (chr == null || chr.getClient() == null) {
            return;
        }
        InteractionHookPackets.sendCharacterQuestRules(chr.getClient());
        InteractionHookPackets.sendProgress(chr.getClient());
    }

    private static void refreshQuestProgress(Character chr) {
        if (chr == null || chr.getClient() == null) {
            return;
        }
        InteractionHookPackets.sendProgress(chr.getClient());
    }

    static boolean isFirstStageVisitQuest(QuestMeta meta) {
        return meta != null
                && meta.stage() == 1
                && meta.kind() == QuestKind.MAIN
                && meta.slot() >= MAIN_SLOT_START
                && meta.slot() < 5
                && meta.isVisible()
                && meta.objective().type() == ObjectiveType.NPC_TALK;
    }

    private static QuestMeta previousVisibleQuest(QuestMeta meta) {
        List<QuestMeta> visible = stageBranchVisibleQuests(meta.stage(), meta.branch());
        int index = visible.indexOf(meta);
        if (index <= 0) {
            return null;
        }
        return visible.get(index - 1);
    }

    private static QuestMeta nextVisibleQuest(QuestMeta meta) {
        List<QuestMeta> visible = stageBranchVisibleQuests(meta.stage(), meta.branch());
        int index = visible.indexOf(meta);
        if (index < 0 || index + 1 >= visible.size()) {
            return null;
        }
        return visible.get(index + 1);
    }

    private static List<HpChallengeService.Task> availableOptions(Character chr, QuestMeta selector) {
        if (chr == null || selector == null || selector.selectorNo() <= 0) {
            return List.of();
        }
        int expectedBridge = selector.selectorNo() == 1 ? 0 :
                questId(selector.stage(), selector.branch(), BRIDGE_SLOT_START + selector.selectorNo() - 2);
        if (expectedBridge > 0 && chr.getQuestStatus(expectedBridge) != QuestStatus.Status.COMPLETED.getId()) {
            return List.of();
        }
        return HpChallengeService.stage(selector.stage()).optionalTasks().stream()
                .filter(option -> !HpChallengeService.isLifeProofOptionalSelected(chr, selector.stage(),
                        option.optionNo()))
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

    private static boolean removeMultiItems(Character chr, List<ItemCollection> multi) {
        if (chr == null || multi == null) return false;
        for (ItemCollection c : multi) {
            if (itemCount(chr, c.itemId()) < c.requiredCount()) return false;
        }
        for (ItemCollection c : multi) {
            removeItem(chr, c.itemId(), c.requiredCount());
        }
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

    private static void appendObjectiveHint(StringBuilder sb, QuestMeta meta, Objective objective) {
        switch (objective.type()) {
            case ITEM -> {
                List<ItemCollection> multi = multiItemCollections(meta);
                if (multi != null) {
                    sb.append("\r\n需要收集以下五种水晶各10个：");
                    for (ItemCollection c : multi) {
                        sb.append("\r\n#i").append(c.itemId()).append("# #t").append(c.itemId())
                                .append("# #b#c").append(c.itemId()).append("# / ")
                                .append(c.requiredCount()).append("#k");
                    }
                    sb.append("\r\n掉落区域：神木村、玩具城、水下世界、时间神殿、死龙巢穴");
                } else {
                    sb.append("\r\n需要：#i").append(objective.itemId()).append("# #t").append(objective.itemId())
                            .append("# #b#c").append(objective.itemId()).append("# / ")
                            .append(objective.requiredCount()).append("#k");
                    ItemCollection collection = collectionForTask(meta.stage(), meta.task());
                    if (collection != null && !collection.droppers().isEmpty()) {
                        sb.append("\r\n掉落怪物：");
                        for (int i = 0; i < collection.droppers().size(); i++) {
                            if (i > 0) sb.append("、");
                            sb.append("#o").append(collection.droppers().get(i)).append("#");
                        }
                    }
                }
            }
            case MESO -> sb.append("\r\n需要缴纳金币：").append(objective.mesoCost());
            case NPC_TALK -> {
            }
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

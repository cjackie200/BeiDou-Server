package org.gms.server.quest;

import org.gms.client.Character;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.config.GameConfig;
import org.gms.constants.inventory.ItemConstants;
import org.gms.server.ItemInformationProvider;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.server.quest.hook.InteractionHookAction;
import org.gms.server.quest.hook.InteractionHookContext;
import org.gms.util.PacketCreator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MonsterCardRingQuest {
    public static final int NPC_ID = 2006;
    public static final int BASE_RING = 1112415;
    public static final int MAX_LEVEL = 10;
    public static final int SETS_PER_LEVEL = 30;
    public static final int MATERIAL_QTY = 10;
    public static final short CLAIM_QUEST_ID = 29980;
    public static final short LAST_QUEST_ID = (short) (CLAIM_QUEST_ID + MAX_LEVEL);

    private static final int[] MATERIALS = {
            4021000, 4021001, 4021002, 4021003, 4021004,
            4021005, 4021006, 4021007, 4021008, 4021009
    };

    private MonsterCardRingQuest() {
    }

    public static int getBaseRingId() {
        return BASE_RING;
    }

    public static int getMaxLevel() {
        return MAX_LEVEL;
    }

    public static int getSetsPerLevel() {
        return SETS_PER_LEVEL;
    }

    public static int getMaterialQty() {
        return MATERIAL_QTY;
    }

    public static int getMaterialForLevel(int targetLevel) {
        if (targetLevel < 1 || targetLevel > MAX_LEVEL) {
            return 0;
        }
        return MATERIALS[targetLevel - 1];
    }

    public static short getClaimQuestId() {
        return CLAIM_QUEST_ID;
    }

    public static short getUpgradeQuestId(int targetLevel) {
        return (short) (CLAIM_QUEST_ID + targetLevel);
    }

    public static int getTargetLevelByQuestId(int questId) {
        return questId - CLAIM_QUEST_ID;
    }

    public static boolean isQuestId(int questId) {
        return questId >= CLAIM_QUEST_ID && questId <= LAST_QUEST_ID;
    }

    public static boolean isMonsterCardRingQuest(int questId) {
        return isQuestId(questId);
    }

    public static List<Integer> getAllQuestIds() {
        List<Integer> questIds = new ArrayList<>();
        for (short questId = CLAIM_QUEST_ID; questId <= LAST_QUEST_ID; questId++) {
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
        for (short questId = CLAIM_QUEST_ID; questId <= LAST_QUEST_ID; questId++) {
            if (chr.getQuestStatus(questId) != QuestStatus.Status.NOT_STARTED.getId()) {
                questIds.add((int) questId);
            }
        }
        resolveCurrentQuestId(chr).ifPresent(questId -> {
            if (!questIds.contains(questId)) {
                questIds.add(questId);
            }
        });
        return questIds;
    }

    public static List<Integer> getHookNpcIds(Character chr) {
        return List.of(NPC_ID);
    }

    public static Optional<Integer> resolveCurrentQuestId(Character chr) {
        if (chr == null) {
            return Optional.empty();
        }
        syncQuestStateSilently(chr);
        if (canClaimBaseRing(chr)) {
            return Optional.of((int) CLAIM_QUEST_ID);
        }
        RingState ringState = getRingState(chr);
        RingInfo current = ringState.getCurrent();
        if (current == null || current.getLevel() >= MAX_LEVEL) {
            return Optional.empty();
        }
        return Optional.of((int) getUpgradeQuestId(current.getLevel() + 1));
    }

    public static Optional<Integer> resolveNpcHook(Character chr, int npcId) {
        if (npcId != NPC_ID) {
            return Optional.empty();
        }
        return resolveCurrentQuestId(chr);
    }

    public static InteractionHookAction resolveCurrentAction(Character chr, int questId) {
        if (questId == CLAIM_QUEST_ID && canClaimBaseRing(chr)) {
            return InteractionHookAction.QUERY_START;
        }
        if (isUpgradeQuest(questId) && validateUpgrade(chr).isOk()) {
            return InteractionHookAction.QUERY_COMPLETE;
        }
        return InteractionHookAction.QUERY_PROGRESS;
    }

    public static void openHook(InteractionHookContext context) {
        if (context == null || context.player() == null) {
            return;
        }
        Character chr = context.player();
        syncQuestState(chr);
        int questId = context.questId();
        if (questId == CLAIM_QUEST_ID) {
            if (canClaimBaseRing(chr)) {
                context.sendYesNo(claimPrompt());
            } else {
                context.sendOk(progressText(chr));
            }
            return;
        }
        if (isUpgradeQuest(questId)) {
            UpgradeValidation validation = validateUpgrade(chr);
            if (validation.isOk()) {
                context.sendYesNo(upgradePrompt(validation));
                return;
            }
            context.sendOk(progressText(chr, validation));
            return;
        }
        context.sendOk("这个怪物卡戒指任务暂时无法处理。");
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
        int questId = context.questId();
        if (questId == CLAIM_QUEST_ID) {
            context.sendOk(claimBaseRing(chr));
            return;
        }
        if (isUpgradeQuest(questId)) {
            UpgradeResult result = upgradeRing(chr);
            context.sendOk(result.message());
            return;
        }
        context.sendOk("这个怪物卡戒指任务暂时无法处理。");
    }

    public static boolean isClaimQuest(int questId) {
        return questId == CLAIM_QUEST_ID;
    }

    public static boolean isUpgradeQuest(int questId) {
        return questId > CLAIM_QUEST_ID && questId <= LAST_QUEST_ID;
    }

    public static boolean isRingItem(int itemId) {
        return itemId >= BASE_RING && itemId <= BASE_RING + MAX_LEVEL;
    }

    public static boolean isMaterialItem(int itemId) {
        for (int material : MATERIALS) {
            if (material == itemId) {
                return true;
            }
        }
        return false;
    }

    public static boolean isQuestRelevantItem(int itemId) {
        return isRingItem(itemId) || isMaterialItem(itemId);
    }

    public static void syncQuestStateIfRelevant(Character chr, int itemId) {
        if (isQuestRelevantItem(itemId)) {
            syncQuestState(chr);
        }
    }

    public static void syncQuestState(Character chr) {
        syncQuestState(chr, true);
    }

    public static void syncQuestStateSilently(Character chr) {
        syncQuestState(chr, false);
    }

    private static void syncQuestState(Character chr, boolean announce) {
        if (chr == null || chr.getMonsterBook() == null) {
            return;
        }

        RingState ringState = getRingState(chr);
        if (ringState.getTotal() == 0) {
            setQuestStatus(chr, CLAIM_QUEST_ID, QuestStatus.Status.NOT_STARTED, announce);
            resetUpgradeQuests(chr, announce);
            syncNpcScriptable(chr, announce);
            return;
        }

        setQuestStatus(chr, CLAIM_QUEST_ID, QuestStatus.Status.COMPLETED, announce);

        RingInfo current = ringState.getCurrent();
        int currentLevel = current == null ? 0 : current.getLevel();
        int nextTargetLevel = current != null && currentLevel < MAX_LEVEL ? currentLevel + 1 : 0;
        int readyTargetLevel = 0;
        if (ringState.getTotal() == 1 && nextTargetLevel > 0 && canShowUpgradeNotice(chr, current, nextTargetLevel)) {
            readyTargetLevel = nextTargetLevel;
        }

        for (int level = 1; level <= MAX_LEVEL; level++) {
            QuestStatus.Status status;
            if (level <= currentLevel) {
                status = QuestStatus.Status.COMPLETED;
            } else if (level == readyTargetLevel) {
                status = QuestStatus.Status.STARTED;
            } else {
                status = QuestStatus.Status.NOT_STARTED;
            }
            setQuestStatus(chr, getUpgradeQuestId(level), status, announce);
        }
        syncNpcScriptable(chr, announce);
    }

    private static void syncNpcScriptable(Character chr, boolean announce) {
        if (announce) {
            syncNpcScriptable(chr);
        }
    }

    private static void resetUpgradeQuests(Character chr, boolean announce) {
        for (int level = 1; level <= MAX_LEVEL; level++) {
            setQuestStatus(chr, getUpgradeQuestId(level), QuestStatus.Status.NOT_STARTED, announce);
        }
    }

    private static void setQuestStatus(Character chr, short questId, QuestStatus.Status status, boolean announce) {
        Quest quest = Quest.getInstance(questId);
        QuestStatus oldStatus = chr.getQuestNoAdd(quest);
        if (oldStatus == null && status == QuestStatus.Status.NOT_STARTED) {
            return;
        }
        if (oldStatus != null && oldStatus.getStatus() == status
                && (status != QuestStatus.Status.STARTED || oldStatus.getNpc() == NPC_ID)) {
            return;
        }

        QuestStatus newStatus = new QuestStatus(quest, status, NPC_ID);
        if (oldStatus != null) {
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

    private static void copyQuestData(QuestStatus oldStatus, QuestStatus newStatus) {
        newStatus.setForfeited(oldStatus.getForfeited());
        newStatus.setCompleted(oldStatus.getCompleted());
        newStatus.setExpirationTime(oldStatus.getExpirationTime());
        if (oldStatus.getStatus() == QuestStatus.Status.COMPLETED) {
            newStatus.setCompletionTime(oldStatus.getCompletionTime());
        }
        for (Map.Entry<Integer, String> entry : oldStatus.getProgress().entrySet()) {
            newStatus.setProgress(entry.getKey(), entry.getValue());
        }
    }

    public static boolean canClaimBaseRing(Character chr) {
        return getRingState(chr).getTotal() == 0;
    }

    public static String claimPrompt() {
        return "你要领取 #b#i" + BASE_RING + "##t" + BASE_RING + "##k 吗？\r\n\r\n"
                + "这是怪物卡戒指的起点，没有属性，但会用于后续升级。";
    }

    public static String claimBaseRing(Character chr) {
        syncQuestState(chr);
        if (!canClaimBaseRing(chr)) {
            syncQuestState(chr);
            return "你已经拥有怪物卡戒指了，不能重复领取。";
        }
        if (gainRawEquip(chr, BASE_RING) == null) {
            syncQuestState(chr);
            return "请先在装备栏背包空出 1 格。";
        }
        syncQuestState(chr);
        return "拿着这个 #b#i" + BASE_RING + "##t" + BASE_RING + "##k。\r\n"
                + "以后直接来找我，我会告诉你怪物卡和材料进度。\r\n\r\n"
                + progressText(chr, validateUpgrade(chr));
    }

    public static String progressText(Character chr) {
        return progressText(chr, validateUpgrade(chr));
    }

    public static String progressText(Character chr, UpgradeValidation validation) {
        int completedSets = countCompletedCardSets(chr);
        RingState ringState = getRingState(chr);
        RingInfo current = ringState.getCurrent();
        StringBuilder text = new StringBuilder("#e怪物卡戒指进度#n\r\n\r\n");

        text.append("满套怪物卡：#b").append(completedSets).append("#k 套\r\n");
        if (current == null) {
            text.append("当前戒指：未领取\r\n\r\n");
            text.append("请先领取 #b#i").append(BASE_RING).append("##t").append(BASE_RING).append("##k。");
            return text.toString();
        }

        text.append("当前戒指：#b#i").append(current.getId()).append("##t").append(current.getId())
                .append("# Lv").append(current.getLevel()).append("#k\r\n");
        if (current.getLevel() >= MAX_LEVEL) {
            text.append("\r\n你的怪物卡戒指已经达到最高等级。");
            return text.toString();
        }

        int targetLevel = current.getLevel() + 1;
        int requiredSets = targetLevel * SETS_PER_LEVEL;
        int material = getMaterialForLevel(targetLevel);
        int materialCount = chr.getItemQuantity(material, false);

        text.append("\r\n#e下一档升级：Lv").append(targetLevel).append("#n\r\n");
        text.append("需要满套怪物卡：#b").append(completedSets).append("#k / #r")
                .append(requiredSets).append("#k 套\r\n");
        text.append("需要宝石：#b#i").append(material).append("##t").append(material)
                .append("# ").append(materialCount).append(" / ").append(MATERIAL_QTY).append("#k\r\n");
        text.append("上一级戒指：");
        if (current.getInBag() > 0) {
            text.append("#b已在装备栏背包#k\r\n");
        } else if (current.getEquipped() > 0) {
            text.append("#r当前穿戴中，请先卸下再兑换#k\r\n");
        } else {
            text.append("#r未在装备栏背包#k\r\n");
        }

        if (validation != null && !validation.isOk()) {
            text.append("\r\n#r当前不能升级：#k").append(validation.getMessage());
        } else {
            text.append("\r\n#b条件已经满足。点击完成书本可升级到 Lv").append(targetLevel).append("。#k");
        }
        return text.toString();
    }

    public static String upgradePrompt(UpgradeValidation validation) {
        if (validation == null || !validation.isOk()) {
            return validation == null ? "当前不能升级。" : validation.getMessage();
        }
        int targetLevel = validation.getTargetLevel();
        int oldRing = validation.getCurrent().getId();
        int newRing = BASE_RING + targetLevel;
        int material = validation.getMaterial();

        return "要把 #b#i" + oldRing + "##t" + oldRing + "##k 升级为 #r#i" + newRing + "##t" + newRing
                + "##k 吗？\r\n\r\n"
                + "需要满套怪物卡：#b" + validation.getRequiredSets() + "#k 套\r\n"
                + "消耗材料：#b#i" + material + "##t" + material + "# x" + MATERIAL_QTY + "#k\r\n\r\n"
                + "升级后会消耗上一级戒指。";
    }

    public static UpgradeResult upgradeRing(Character chr) {
        UpgradeValidation validation = validateUpgrade(chr);
        if (!validation.isOk()) {
            syncQuestState(chr);
            return UpgradeResult.fail(validation.getMessage());
        }

        int targetLevel = validation.getTargetLevel();
        int oldRing = validation.getCurrent().getId();
        int newRing = BASE_RING + targetLevel;
        int material = validation.getMaterial();

        InventoryManipulator.removeById(chr.getClient(), InventoryType.EQUIP, oldRing, 1, true, false);
        Item gained = gainRawEquip(chr, newRing);
        if (gained == null) {
            gainRawEquip(chr, oldRing);
            syncQuestState(chr);
            return UpgradeResult.fail("装备栏空间不足，升级没有完成。请整理装备栏后再试。");
        }

        InventoryManipulator.removeById(chr.getClient(), ItemConstants.getInventoryType(material), material,
                MATERIAL_QTY, true, false);
        chr.sendPacket(PacketCreator.getShowItemGain(material, (short) -MATERIAL_QTY, true));
        syncQuestState(chr);
        return UpgradeResult.success("升级完成。\r\n你获得了 #b#i" + newRing + "##t" + newRing + "##k。");
    }

    private static Item gainRawEquip(Character chr, int itemId) {
        Item item = ItemInformationProvider.getInstance().getEquipById(itemId);
        if (item == null || !InventoryManipulator.checkSpace(chr.getClient(), itemId, 1, "")) {
            return null;
        }
        if (!InventoryManipulator.addFromDrop(chr.getClient(), item, false, -1)) {
            return null;
        }
        chr.sendPacket(PacketCreator.getShowItemGain(itemId, (short) 1, true));
        return item;
    }

    public static Map<Integer, String> getScriptableNpcIds(Character chr) {
        Map<Integer, String> configuredNpcIds = GameConfig.getServerObject(
                "npcs_scriptable", new HashMap<Integer, String>());
        Map<Integer, String> npcsIds = new HashMap<>(configuredNpcIds);

        if (GameConfig.getServerBoolean("use_rebirth_system")) {
            npcsIds.put(GameConfig.getServerInt("rebirth_npc_id"), "Rebirth");
        }
        npcsIds.remove(NPC_ID);
        return npcsIds;
    }

    public static void syncNpcScriptable(Character chr) {
        if (chr == null || chr.getClient() == null || !GameConfig.getServerBoolean("use_npcs_scriptable")) {
            return;
        }
        chr.getClient().sendPacket(PacketCreator.setNPCScriptable(getScriptableNpcIds(chr)));
    }

    public static UpgradeValidation validateUpgrade(Character chr) {
        return validateUpgrade(chr, getRingState(chr));
    }

    public static TestPreparation prepareNextUpgradeForTesting(Character chr) {
        if (chr == null || !chr.isGM()) {
            return TestPreparation.fail("只有 GM 可以使用怪物卡戒指测试准备功能。");
        }

        RingState ringState = getRingState(chr);
        if (ringState.getTotal() == 0) {
            return TestPreparation.fail("请先领取 0 级怪物卡戒指，再补齐升级测试条件。");
        }

        if (ringState.getTotal() > 1) {
            return TestPreparation.fail("身上存在多个怪物卡戒指，无法判断下一档测试目标。");
        }

        RingInfo current = ringState.getCurrent();
        if (current == null || current.getLevel() >= MAX_LEVEL) {
            return TestPreparation.fail("当前已经没有下一档可测试升级。");
        }

        int targetLevel = current.getLevel() + 1;
        int requiredSets = targetLevel * SETS_PER_LEVEL;
        int beforeSets = countCompletedCardSets(chr);
        int addedCards = chr.getMonsterBook().fillCompletedCardSetsForTesting(chr.getClient(), requiredSets);
        syncQuestState(chr);

        int afterSets = countCompletedCardSets(chr);
        if (afterSets < requiredSets) {
            return TestPreparation.fail("怪物卡数据不足，无法补齐到 " + requiredSets + " 套。");
        }

        return TestPreparation.success(targetLevel, requiredSets, beforeSets, afterSets, addedCards,
                getMaterialForLevel(targetLevel));
    }

    private static boolean canShowUpgradeNotice(Character chr, RingInfo current, int targetLevel) {
        int requiredSets = targetLevel * SETS_PER_LEVEL;
        int completedSets = countCompletedCardSets(chr);
        if (completedSets < requiredSets) {
            return false;
        }

        int material = getMaterialForLevel(targetLevel);
        return material > 0 && chr.getItemQuantity(material, false) >= MATERIAL_QTY
                && current.getLevel() == targetLevel - 1;
    }

    private static UpgradeValidation validateUpgrade(Character chr, RingState ringState) {
        if (ringState.getTotal() == 0) {
            return UpgradeValidation.fail("你还没有怪物卡戒指。请先领取 #b#i" + BASE_RING + "##t" + BASE_RING + "##k。");
        }

        if (ringState.getTotal() > 1) {
            return UpgradeValidation.fail("你身上存在多个怪物卡戒指。为了避免兑换异常，请联系管理员处理后再升级。");
        }

        RingInfo current = ringState.getCurrent();
        if (current == null) {
            return UpgradeValidation.fail("没有找到可升级的怪物卡戒指。");
        }

        if (current.getLevel() >= MAX_LEVEL) {
            return UpgradeValidation.fail("你的怪物卡戒指已经达到最高等级。");
        }

        if (current.getEquipped() > 0) {
            return UpgradeValidation.fail("请先卸下 #b#i" + current.getId() + "##t" + current.getId() + "##k，并把它放在装备栏背包内。");
        }

        if (current.getInBag() <= 0) {
            return UpgradeValidation.fail("请把上一级戒指放在装备栏背包内后再来升级。");
        }

        int targetLevel = current.getLevel() + 1;
        int requiredSets = targetLevel * SETS_PER_LEVEL;
        int completedSets = countCompletedCardSets(chr);
        if (completedSets < requiredSets) {
            return UpgradeValidation.fail("满套怪物卡数量不足。\r\n当前：#b" + completedSets + "#k 套\r\n需要：#r" + requiredSets + "#k 套");
        }

        int material = getMaterialForLevel(targetLevel);
        if (material <= 0 || chr.getItemQuantity(material, false) < MATERIAL_QTY) {
            return UpgradeValidation.fail("材料不足。\r\n需要：#b#i" + material + "##t" + material + "# x" + MATERIAL_QTY + "#k");
        }

        return UpgradeValidation.success(current, targetLevel, requiredSets, completedSets, material);
    }

    public static int countCompletedCardSets(Character chr) {
        if (chr == null || chr.getMonsterBook() == null) {
            return 0;
        }

        int completed = 0;
        for (Map.Entry<Integer, Integer> entry : chr.getMonsterBook().getCardSet()) {
            if (entry.getValue() != null && entry.getValue() >= 5) {
                completed++;
            }
        }
        return completed;
    }

    public static RingState getRingState(Character chr) {
        Inventory equipInv = chr.getInventory(InventoryType.EQUIP);
        Inventory equippedInv = chr.getInventory(InventoryType.EQUIPPED);
        RingInfo current = null;
        int total = 0;

        for (int level = 0; level <= MAX_LEVEL; level++) {
            int itemId = BASE_RING + level;
            int inBag = equipInv.countById(itemId);
            int equipped = equippedInv.countById(itemId);
            int count = inBag + equipped;

            if (count <= 0) {
                continue;
            }

            total += count;
            if (current == null || level > current.getLevel()) {
                current = new RingInfo(itemId, level, inBag, equipped);
            }
        }

        return new RingState(total, current);
    }

    public static final class RingState {
        private final int total;
        private final RingInfo current;

        private RingState(int total, RingInfo current) {
            this.total = total;
            this.current = current;
        }

        public int getTotal() {
            return total;
        }

        public RingInfo getCurrent() {
            return current;
        }
    }

    public static final class RingInfo {
        private final int id;
        private final int level;
        private final int inBag;
        private final int equipped;

        private RingInfo(int id, int level, int inBag, int equipped) {
            this.id = id;
            this.level = level;
            this.inBag = inBag;
            this.equipped = equipped;
        }

        public int getId() {
            return id;
        }

        public int getLevel() {
            return level;
        }

        public int getInBag() {
            return inBag;
        }

        public int getEquipped() {
            return equipped;
        }
    }

    public static final class UpgradeValidation {
        private final boolean ok;
        private final String message;
        private final RingInfo current;
        private final int targetLevel;
        private final int requiredSets;
        private final int completedSets;
        private final int material;

        private UpgradeValidation(boolean ok, String message, RingInfo current, int targetLevel,
                                  int requiredSets, int completedSets, int material) {
            this.ok = ok;
            this.message = message;
            this.current = current;
            this.targetLevel = targetLevel;
            this.requiredSets = requiredSets;
            this.completedSets = completedSets;
            this.material = material;
        }

        private static UpgradeValidation fail(String message) {
            return new UpgradeValidation(false, message, null, 0, 0, 0, 0);
        }

        private static UpgradeValidation success(RingInfo current, int targetLevel, int requiredSets,
                                                 int completedSets, int material) {
            return new UpgradeValidation(true, "", current, targetLevel, requiredSets, completedSets, material);
        }

        public boolean isOk() {
            return ok;
        }

        public String getMessage() {
            return message;
        }

        public RingInfo getCurrent() {
            return current;
        }

        public int getTargetLevel() {
            return targetLevel;
        }

        public int getRequiredSets() {
            return requiredSets;
        }

        public int getCompletedSets() {
            return completedSets;
        }

        public int getMaterial() {
            return material;
        }
    }

    public record UpgradeResult(boolean success, String message) {
        private static UpgradeResult success(String message) {
            return new UpgradeResult(true, message);
        }

        private static UpgradeResult fail(String message) {
            return new UpgradeResult(false, message);
        }
    }

    public static final class TestPreparation {
        private final boolean ok;
        private final String message;
        private final int targetLevel;
        private final int requiredSets;
        private final int beforeSets;
        private final int afterSets;
        private final int addedCards;
        private final int material;

        private TestPreparation(boolean ok, String message, int targetLevel, int requiredSets,
                                int beforeSets, int afterSets, int addedCards, int material) {
            this.ok = ok;
            this.message = message;
            this.targetLevel = targetLevel;
            this.requiredSets = requiredSets;
            this.beforeSets = beforeSets;
            this.afterSets = afterSets;
            this.addedCards = addedCards;
            this.material = material;
        }

        private static TestPreparation fail(String message) {
            return new TestPreparation(false, message, 0, 0, 0, 0, 0, 0);
        }

        private static TestPreparation success(int targetLevel, int requiredSets, int beforeSets,
                                               int afterSets, int addedCards, int material) {
            return new TestPreparation(true, "", targetLevel, requiredSets, beforeSets, afterSets,
                    addedCards, material);
        }

        public boolean isOk() {
            return ok;
        }

        public String getMessage() {
            return message;
        }

        public int getTargetLevel() {
            return targetLevel;
        }

        public int getRequiredSets() {
            return requiredSets;
        }

        public int getBeforeSets() {
            return beforeSets;
        }

        public int getAfterSets() {
            return afterSets;
        }

        public int getAddedCards() {
            return addedCards;
        }

        public int getMaterial() {
            return material;
        }
    }
}

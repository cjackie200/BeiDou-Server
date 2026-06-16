var status = -1;

var MonsterCardRingQuest = Java.type("org.gms.server.quest.MonsterCardRingQuest");

var BASE_RING = MonsterCardRingQuest.getBaseRingId();
var CLAIM_QUEST = MonsterCardRingQuest.getClaimQuestId();
var MAX_LEVEL = MonsterCardRingQuest.getMaxLevel();
var SETS_PER_LEVEL = MonsterCardRingQuest.getSetsPerLevel();
var MATERIAL_QTY = MonsterCardRingQuest.getMaterialQty();

function start(mode, type, selection) {
    if (qm.getQuest() == CLAIM_QUEST) {
        startClaim(mode, type, selection);
        return;
    }

    if (MonsterCardRingQuest.isUpgradeQuest(qm.getQuest())) {
        startUpgradeProgress(mode, type, selection);
        return;
    }

    qm.dispose();
}

function startClaim(mode, type, selection) {
    if (!advance(mode, type)) {
        return;
    }

    if (status == 0) {
        MonsterCardRingQuest.syncQuestState(qm.getPlayer());
        if (!MonsterCardRingQuest.canClaimBaseRing(qm.getPlayer())) {
            qm.sendOk("你已经拥有怪物卡戒指了，不能重复领取。");
            MonsterCardRingQuest.syncQuestState(qm.getPlayer());
            qm.dispose();
            return;
        }

        qm.sendYesNo("你要领取 #b#i" + BASE_RING + "##t" + BASE_RING + "##k 吗？\r\n\r\n这是怪物卡戒指的起点，没有属性，但会用于后续升级。");
    } else if (status == 1) {
        claimBaseRing();
        qm.dispose();
    }
}

function startUpgradeProgress(mode, type, selection) {
    if (!advance(mode, type)) {
        return;
    }

    if (status == 0) {
        MonsterCardRingQuest.syncQuestState(qm.getPlayer());
        var validation = MonsterCardRingQuest.validateUpgrade(qm.getPlayer());
        var text = getProgressText(validation);

        if (validation.isOk()) {
            qm.sendOk(text + "\r\n\r\n#b条件已经满足。请点击任务完成书本提示升级到 Lv" + validation.getTargetLevel() + "。#k");
            qm.dispose();
            return;
        }

        if (qm.getPlayer().isGM() && canPrepareTest(validation)) {
            qm.sendSimple(text + "\r\n\r\n#L90##r[GM测试] 补齐下一档怪物卡和宝石#l");
            return;
        }

        qm.sendOk(text);
    } else if (status == 1) {
        if (selection == 90) {
            prepareNextUpgradeTest();
        }
        qm.dispose();
    }
}

function end(mode, type, selection) {
    if (!MonsterCardRingQuest.isUpgradeQuest(qm.getQuest())) {
        qm.dispose();
        return;
    }

    if (!advance(mode, type)) {
        return;
    }

    if (status == 0) {
        sendUpgradeConfirm();
    } else if (status == 1) {
        upgradeRing();
        qm.dispose();
    }
}

function advance(mode, type) {
    if (mode == -1 || (mode == 0 && status >= 0)) {
        MonsterCardRingQuest.syncQuestState(qm.getPlayer());
        qm.dispose();
        return false;
    }

    if (mode == 1) {
        status++;
    } else {
        status--;
    }
    return true;
}

function claimBaseRing() {
    MonsterCardRingQuest.syncQuestState(qm.getPlayer());
    if (!MonsterCardRingQuest.canClaimBaseRing(qm.getPlayer())) {
        qm.sendOk("你已经拥有怪物卡戒指了，不能重复领取。");
        return;
    }

    if (!qm.canHold(BASE_RING, 1)) {
        qm.sendOk("请先在装备栏背包空出 1 格。");
        return;
    }

    if (qm.gainRawEquip(BASE_RING) == null) {
        qm.sendOk("装备栏空间不足，暂时无法领取。");
        return;
    }

    MonsterCardRingQuest.syncQuestState(qm.getPlayer());
    qm.sendOk("拿着这个 #b#i" + BASE_RING + "##t" + BASE_RING + "##k。\r\n"
        + "以后直接来找我，我会告诉你怪物卡和材料进度。\r\n\r\n"
        + getProgressText(MonsterCardRingQuest.validateUpgrade(qm.getPlayer())));
}

function sendUpgradeConfirm() {
    var validation = MonsterCardRingQuest.validateUpgrade(qm.getPlayer());
    if (!validation.isOk()) {
        qm.sendOk(validation.getMessage());
        MonsterCardRingQuest.syncQuestState(qm.getPlayer());
        qm.dispose();
        return;
    }

    var targetLevel = validation.getTargetLevel();
    var oldRing = validation.getCurrent().getId();
    var newRing = BASE_RING + targetLevel;
    var material = validation.getMaterial();

    qm.sendYesNo("要把 #b#i" + oldRing + "##t" + oldRing + "##k 升级为 #r#i" + newRing + "##t" + newRing + "##k 吗？\r\n\r\n"
        + "需要满套怪物卡：#b" + validation.getRequiredSets() + "#k 套\r\n"
        + "消耗材料：#b#i" + material + "##t" + material + "# x" + MATERIAL_QTY + "#k\r\n\r\n"
        + "升级后会消耗上一级戒指。");
}

function upgradeRing() {
    var validation = MonsterCardRingQuest.validateUpgrade(qm.getPlayer());
    if (!validation.isOk()) {
        qm.sendOk(validation.getMessage());
        MonsterCardRingQuest.syncQuestState(qm.getPlayer());
        return;
    }

    var targetLevel = validation.getTargetLevel();
    var oldRing = validation.getCurrent().getId();
    var newRing = BASE_RING + targetLevel;
    var material = validation.getMaterial();

    qm.gainItem(oldRing, -1);
    var gained = qm.gainRawEquip(newRing);
    if (gained == null) {
        qm.gainRawEquip(oldRing);
        MonsterCardRingQuest.syncQuestState(qm.getPlayer());
        qm.sendOk("装备栏空间不足，升级没有完成。请整理装备栏后再试。");
        return;
    }

    qm.gainItem(material, -MATERIAL_QTY);
    MonsterCardRingQuest.syncQuestState(qm.getPlayer());
    qm.sendOk("升级完成。\r\n你获得了 #b#i" + newRing + "##t" + newRing + "##k。");
}

function getProgressText(validation) {
    var completedSets = MonsterCardRingQuest.countCompletedCardSets(qm.getPlayer());
    var ringState = MonsterCardRingQuest.getRingState(qm.getPlayer());
    var current = ringState.getCurrent();
    var text = "#e怪物卡戒指进度#n\r\n\r\n";

    text += "满套怪物卡：#b" + completedSets + "#k 套\r\n";
    if (current == null) {
        text += "当前戒指：未领取\r\n\r\n";
        text += "请先领取 #b#i" + BASE_RING + "##t" + BASE_RING + "##k。";
        return text;
    }

    text += "当前戒指：#b#i" + current.getId() + "##t" + current.getId() + "# Lv" + current.getLevel() + "#k\r\n";
    if (current.getLevel() >= MAX_LEVEL) {
        text += "\r\n你的怪物卡戒指已经达到最高等级。";
        return text;
    }

    var targetLevel = current.getLevel() + 1;
    var requiredSets = targetLevel * SETS_PER_LEVEL;
    var material = MonsterCardRingQuest.getMaterialForLevel(targetLevel);
    var materialCount = qm.getItemQuantity(material);

    text += "\r\n#e下一档升级：Lv" + targetLevel + "#n\r\n";
    text += "需要满套怪物卡：#b" + completedSets + "#k / #r" + requiredSets + "#k 套\r\n";
    text += "需要宝石：#b#i" + material + "##t" + material + "# " + materialCount + " / " + MATERIAL_QTY + "#k\r\n";
    text += "上一级戒指：";
    if (current.getInBag() > 0) {
        text += "#b已在装备栏背包#k\r\n";
    } else if (current.getEquipped() > 0) {
        text += "#r当前穿戴中，请先卸下再兑换#k\r\n";
    } else {
        text += "#r未在装备栏背包#k\r\n";
    }

    if (validation != null && !validation.isOk()) {
        text += "\r\n#r当前不能升级：#k" + validation.getMessage();
    }
    return text;
}

function canPrepareTest(validation) {
    var ringState = MonsterCardRingQuest.getRingState(qm.getPlayer());
    var current = ringState.getCurrent();
    return current != null && current.getLevel() < MAX_LEVEL && !validation.isOk();
}

function prepareNextUpgradeTest() {
    var result = MonsterCardRingQuest.prepareNextUpgradeForTesting(qm.getPlayer());
    if (!result.isOk()) {
        qm.sendOk(result.getMessage());
        MonsterCardRingQuest.syncQuestState(qm.getPlayer());
        return;
    }

    var material = result.getMaterial();
    var missingMaterial = MATERIAL_QTY - qm.getItemQuantity(material);
    if (missingMaterial > 0) {
        if (!qm.canHold(material, missingMaterial)) {
            qm.sendOk("GM测试补齐了怪物卡，但背包放不下宝石。\r\n需要：#b#i" + material + "##t" + material + "# x" + missingMaterial + "#k");
            MonsterCardRingQuest.syncQuestState(qm.getPlayer());
            return;
        }
        qm.gainItem(material, missingMaterial);
    }

    MonsterCardRingQuest.syncQuestState(qm.getPlayer());
    qm.sendOk("GM测试条件已补齐。\r\n\r\n"
        + "目标等级：#bLv" + result.getTargetLevel() + "#k\r\n"
        + "满套怪物卡：#b" + result.getBeforeSets() + "#k -> #b" + result.getAfterSets() + "#k / " + result.getRequiredSets() + "\r\n"
        + "新增满套卡片：#b" + result.getAddedCards() + "#k\r\n"
        + "补齐材料：#b#i" + material + "##t" + material + "# x" + Math.max(missingMaterial, 0) + "#k\r\n\r\n"
        + "关闭对话后应出现可完成任务的书本提示，请点击书本提示完成升级。");
}

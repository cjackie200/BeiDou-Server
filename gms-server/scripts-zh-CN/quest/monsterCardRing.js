var status = -1;

var MonsterCardRingQuest = Java.type("org.gms.server.quest.MonsterCardRingQuest");

var BASE_RING = MonsterCardRingQuest.getBaseRingId();
var CLAIM_QUEST = MonsterCardRingQuest.getClaimQuestId();
var MATERIAL_QTY = MonsterCardRingQuest.getMaterialQty();

function start(mode, type, selection) {
    if (qm.getQuest() != CLAIM_QUEST) {
        qm.dispose();
        return;
    }

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
    qm.sendOk("拿着这个 #b#i" + BASE_RING + "##t" + BASE_RING + "##k。\r\n收集更多怪物卡满套后，再来找我升级。");
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

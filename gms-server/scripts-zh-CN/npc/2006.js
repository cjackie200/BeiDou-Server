/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation version 3 as published by
 the Free Software Foundation. You may not use, modify or distribute
 this program under any other version of the GNU Affero General Public
 License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

var status = -1;
var flow = "";

var MonsterCardRingQuest = Java.type("org.gms.server.quest.MonsterCardRingQuest");

var BASE_RING = MonsterCardRingQuest.getBaseRingId();
var MAX_LEVEL = MonsterCardRingQuest.getMaxLevel();
var SETS_PER_LEVEL = MonsterCardRingQuest.getSetsPerLevel();
var MATERIAL_QTY = MonsterCardRingQuest.getMaterialQty();
var MAX_RING_ID = MonsterCardRingQuest.getMaxRingId();
var MAX_RING_COPY_COST = MonsterCardRingQuest.getMaxRingCopyCost();
var MAX_RING_COPY_LIMIT = MonsterCardRingQuest.getMaxRingCopyLimit();

function start() {
    action(1, 0, 0);
}

function action(mode, type, selection) {
    try {
        handleAction(mode, type, selection);
    } catch (e) {
        cm.sendOk("怪物卡戒指兑换暂时无法继续。\r\n请联系管理员检查 NPC 2006 脚本。\r\n\r\n错误信息：" + e);
        cm.dispose();
    }
}

function handleAction(mode, type, selection) {
    if (mode == -1 || (mode == 0 && status >= 0)) {
        syncQuestState();
        cm.dispose();
        return;
    }

    if (mode == 1) {
        status++;
    } else {
        status--;
    }

    if (status == 0) {
        sendEntry();
    } else if (status == 1) {
        if (selection == 90) {
            prepareNextUpgradeTest();
            flow = "close";
        } else if (selection == 91) {
            flow = "copyConfirm";
            cm.sendYesNo("确定消耗 #b#i4310000##t4310000# x" + MAX_RING_COPY_COST
                + "#k，复制 1 个 #r#i" + MAX_RING_ID + "##t" + MAX_RING_ID + "##k 吗？\r\n\r\n"
                + "每个角色最多可以复制 " + MAX_RING_COPY_LIMIT + " 次。");
        } else {
            cm.dispose();
        }
    } else if (status == 2 && flow == "copyConfirm") {
        copyMaxRing();
        flow = "close";
    } else if (status >= 2) {
        cm.dispose();
    }
}

function sendEntry() {
    syncQuestState();

    if (MonsterCardRingQuest.canClaimBaseRing(cm.getPlayer())) {
        flow = "close";
        cm.sendOk("请点击我头上的任务提示领取 #b#i" + BASE_RING + "##t" + BASE_RING + "##k。");
        return;
    }

    var validation = MonsterCardRingQuest.validateUpgrade(cm.getPlayer());
    var ringState = MonsterCardRingQuest.getRingState(cm.getPlayer());
    var current = ringState.getCurrent();
    if (current != null && current.getLevel() >= MAX_LEVEL) {
        showMaxRingCopyEntry(validation);
        return;
    }

    if (validation.isOk()) {
        flow = "close";
        cm.sendOk(getProgressText(validation) + "\r\n\r\n"
            + "#b条件已经满足。请点击任务完成书本提示升级到 Lv" + validation.getTargetLevel() + "。#k");
        return;
    }

    flow = "progress";
    var text = getProgressText(validation);
    if (cm.getPlayer().isGM() && canPrepareTest(validation)) {
        cm.sendSimple(text + "\r\n\r\n#L90##r[GM测试] 补齐下一档怪物卡和宝石#l");
        return;
    }

    flow = "close";
    cm.sendOk(text);
}

function showMaxRingCopyEntry(validation) {
    var copyValidation = MonsterCardRingQuest.validateMaxRingCopy(cm.getPlayer());
    var text = getProgressText(validation) + "\r\n\r\n"
        + "#e满级戒指复制#n\r\n"
        + "消耗：#b#i4310000##t4310000# x" + MAX_RING_COPY_COST + "#k\r\n"
        + "已复制：#b" + copyValidation.getCopyCount() + "#k / " + MAX_RING_COPY_LIMIT + " 次\r\n"
        + "剩余次数：#b" + copyValidation.getRemainingCopies() + "#k 次";

    if (!copyValidation.isOk()) {
        flow = "close";
        cm.sendOk(text + "\r\n\r\n#r当前不能复制：#k" + copyValidation.getMessage());
        return;
    }

    flow = "copySelect";
    cm.sendSimple(text + "\r\n\r\n#L91##b复制 1 个满级怪物卡戒指#l");
}

function copyMaxRing() {
    var result = MonsterCardRingQuest.copyMaxRing(cm.getPlayer());
    if (!result.success()) {
        cm.sendOk(result.message());
        return;
    }
    cm.sendOk(result.message() + "\r\n\r\n"
        + "已复制：#b" + result.copyCount() + "#k / " + MAX_RING_COPY_LIMIT + " 次\r\n"
        + "剩余次数：#b" + (MAX_RING_COPY_LIMIT - result.copyCount()) + "#k 次");
}

function prepareNextUpgradeTest() {
    var result = MonsterCardRingQuest.prepareNextUpgradeForTesting(cm.getPlayer());
    if (!result.isOk()) {
        cm.sendOk(result.getMessage());
        syncQuestState();
        return;
    }

    var material = result.getMaterial();
    var missingMaterial = MATERIAL_QTY - cm.getItemQuantity(material);
    if (missingMaterial > 0) {
        if (!cm.canHold(material, missingMaterial)) {
            cm.sendOk("GM测试补齐了怪物卡，但背包放不下宝石。\r\n需要：#b#i" + material + "##t" + material + "# x" + missingMaterial + "#k");
            syncQuestState();
            return;
        }
        cm.gainItem(material, missingMaterial);
    }

    syncQuestState();
    cm.sendOk("GM测试条件已补齐。\r\n\r\n"
        + "目标等级：#bLv" + result.getTargetLevel() + "#k\r\n"
        + "满套怪物卡：#b" + result.getBeforeSets() + "#k -> #b" + result.getAfterSets() + "#k / " + result.getRequiredSets() + "\r\n"
        + "新增满套卡片：#b" + result.getAddedCards() + "#k\r\n"
        + "补齐材料：#b#i" + material + "##t" + material + "# x" + Math.max(missingMaterial, 0) + "#k\r\n\r\n"
        + "关闭对话后应出现可完成任务的书本提示，请点击书本提示完成升级。");
}

function getProgressText(validation) {
    var completedSets = MonsterCardRingQuest.countCompletedCardSets(cm.getPlayer());
    var ringState = MonsterCardRingQuest.getRingState(cm.getPlayer());
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
    var materialCount = cm.getItemQuantity(material);

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
    var ringState = MonsterCardRingQuest.getRingState(cm.getPlayer());
    var current = ringState.getCurrent();
    return current != null && current.getLevel() < MAX_LEVEL && !validation.isOk();
}

function syncQuestState() {
    MonsterCardRingQuest.syncQuestState(cm.getPlayer());
}

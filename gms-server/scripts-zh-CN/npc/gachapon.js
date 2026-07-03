/*
 * Common Gachapon NPC script.
 * Uses all available normal tickets in one interaction, capped to avoid a long
 * script loop blocking the NPC engine.
 */

var status = -1;
var ticketId = 5220000;
var batchLimit = 50;

var mapName = [
    "射手村",
    "魔法密林",
    "勇士部落",
    "废弃都市",
    "林中之城",
    "蘑菇神社",
    "昭和澡堂（男）",
    "昭和澡堂（女）",
    "玩具城",
    "新叶城",
    "冰峰雪域",
    "诺特勒斯号"
];
var curMapName = "";

function start() {
    status = -1;
    curMapName = mapName[(cm.getNpc() != 9100117 && cm.getNpc() != 9100109) ? (cm.getNpc() - 9100100) : cm.getNpc() == 9100109 ? 9 : 11];
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode < 0) {
        cm.dispose();
        return;
    }

    if (mode == 1) {
        status++;
    } else {
        status--;
    }

    if (status == 0 && mode == 1) {
        if (cm.haveItem(ticketId)) {
            var count = cm.itemQuantity(ticketId);
            var openCount = Math.min(count, batchLimit);
            cm.sendYesNo("你当前有 #b" + count + "#k 张#t" + ticketId + "#。\r\n"
                    + "是否在" + curMapName + "快乐百宝箱连续开启 #b" + openCount + "#k 张？\r\n"
                    + "#r背包空间不足时会自动停止。#k");
        } else {
            cm.sendSimple("欢迎来到" + curMapName + "快乐百宝箱。我可以为您做些什么呢？\r\n\r\n"
                    + "#L0#什么是快乐百宝箱？#l\r\n"
                    + "#L1#在哪里可以买快乐百宝券？#l");
        }
    } else if (status == 1 && cm.haveItem(ticketId)) {
        openTickets();
        cm.dispose();
    } else if (status == 1) {
        if (selection == 0) {
            cm.sendNext("玩转快乐百宝箱，赢得稀有卷轴、装备、椅子、技能书和其他物品！你只需要一张#b快乐百宝券#k，就有机会获得随机奖励。");
        } else {
            cm.sendNext("快乐百宝券可以在#r现金商店#k购买。");
        }
    } else if (status == 2) {
        cm.sendNextPrev("你会在" + curMapName + "的快乐百宝箱中找到各种物品。");
    } else {
        cm.dispose();
    }
}

function openTickets() {
    var opened = 0;
    var failed = false;
    var count = Math.min(cm.itemQuantity(ticketId), batchLimit);

    for (var i = 0; i < count; i++) {
        if (!hasRewardSpace()) {
            break;
        }

        cm.gainItem(ticketId, -1);
        if (!cm.doGachapon()) {
            cm.gainItem(ticketId, 1);
            failed = true;
            break;
        }
        opened++;
    }

    if (opened <= 0) {
        cm.sendOk("没有成功开启百宝券，请确认背包空间足够，或联系管理员检查该百宝箱奖池。");
        return;
    }

    var message = "本次已开启 #b" + opened + "#k 张#t" + ticketId + "#。\r\n"
            + "剩余：#b" + cm.itemQuantity(ticketId) + "#k 张。";
    if (opened >= batchLimit && cm.haveItem(ticketId)) {
        message += "\r\n\r\n为了避免一次开启过多造成卡顿，每次最多开启 #b" + batchLimit + "#k 张。";
    }
    if (!hasRewardSpace()) {
        message += "\r\n\r\n#r背包空间不足，已自动停止。#k";
    }
    if (failed) {
        message += "\r\n\r\n#r奖池抽取失败，已停止并退还未成功消耗的券。#k";
    }
    cm.sendOk(message);
}

function hasRewardSpace() {
    return cm.canHold(1302000) && cm.canHold(2000000) && cm.canHold(3010001) && cm.canHold(4000000);
}

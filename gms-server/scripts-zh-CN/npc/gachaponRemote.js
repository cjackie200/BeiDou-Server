/*
 * Remote Gachapon ticket script.
 * Advanced tickets are opened in batches instead of forcing one interaction per
 * ticket.
 */

var status = -1;
var ticketId = 5451000;
var batchLimit = 50;

function start() {
    status = -1;
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

    if (status == 0) {
        if (!cm.haveItem(ticketId)) {
            cm.sendOk("你没有#t" + ticketId + "#。");
            cm.dispose();
            return;
        }

        var count = cm.itemQuantity(ticketId);
        var openCount = Math.min(count, batchLimit);
        cm.sendYesNo("你当前有 #b" + count + "#k 张#t" + ticketId + "#。\r\n"
                + "是否连续开启 #b" + openCount + "#k 张？\r\n"
                + "#r背包空间不足时会自动停止。#k");
    } else if (status == 1) {
        openTickets();
        cm.dispose();
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
        cm.sendOk("没有成功开启高级百宝券，请确认背包空间足够，或联系管理员检查百宝箱奖池。");
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

var ResetScrollService = Java.type("org.gms.client.processor.stat.ResetScrollService");

var status = -1;
var fromStat = 0;
var toStat = 0;
var amount = 0;
var AP_SCROLL = 5050000;
var stats = [64, 128, 256, 512, 2048, 8192];
var names = ["力量", "敏捷", "智力", "运气", "HP", "MP"];
var HpChallengeService = Java.type("org.gms.server.hpchallenge.HpChallengeService");
var lifeProofStarted = false;

function start() {
    lifeProofStarted = HpChallengeService.isLifeProofStarted(cm.getPlayer());
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode != 1) {
        cm.dispose();
        return;
    }
    status++;
    if (status == 0 && lifeProofStarted) {
        cm.sendNext("生命之证任务已经开始，无法再洗HP或MP。接下来只显示力量、敏捷、智力和运气。");
        return;
    }
    var step = status - (lifeProofStarted ? 1 : 0);
    if (step == 0) {
        var text = "请选择要洗掉的属性：\r\n\r\n";
        var limit = lifeProofStarted ? 4 : stats.length;
        for (var i = 0; i < limit; i++) {
            var removable = ResetScrollService.getRemovableAp(cm.getPlayer(), stats[i]);
            text += "#L" + i + "#" + names[i] + "（最多可洗 " + removable + " 点）#l\r\n";
        }
        cm.sendSimple(text);
    } else if (step == 1) {
        fromStat = stats[selection];
        var max = ResetScrollService.getRemovableAp(cm.getPlayer(), fromStat);
        if (max <= 0) {
            cm.sendOk("该属性没有可洗掉的点数。");
            cm.dispose();
            return;
        }
        cm.sendGetNumber("请输入要从" + statName(fromStat) + "洗掉的点数。", 1, 1, max);
    } else if (step == 2) {
        amount = selection;
        var text = "请选择要增加的属性：\r\n\r\n";
        var limit = lifeProofStarted ? 4 : stats.length;
        for (var i = 0; i < limit; i++) {
            var hpMpSwap = fromStat == 2048 || fromStat == 8192;
            var isSwapTarget = fromStat == 2048 && stats[i] == 8192
                    || fromStat == 8192 && stats[i] == 2048;
            if (stats[i] != fromStat && (!hpMpSwap || isSwapTarget)) {
                text += "#L" + i + "#" + names[i] + "#l\r\n";
            }
        }
        cm.sendSimple(text);
    } else if (step == 3) {
        toStat = stats[selection];
        var scrolls = cm.itemQuantity(AP_SCROLL);
        if (scrolls < amount) {
            cm.sendOk("本次需要 #r" + amount + "#k 张#i" + AP_SCROLL + "##t" + AP_SCROLL + "#，当前只有 #b" + scrolls + "#k 张。");
            cm.dispose();
            return;
        }
        cm.sendYesNo("确认从#r" + statName(fromStat) + "#k洗掉 #r" + amount + "#k 点，并全部增加到#b" + statName(toStat) + "#k？\r\n\r\n将消耗 #r" + amount + "#k 张#i" + AP_SCROLL + "##t" + AP_SCROLL + "#。");
    } else if (step == 4) {
        if (cm.itemQuantity(AP_SCROLL) < amount || ResetScrollService.getRemovableAp(cm.getPlayer(), fromStat) < amount) {
            cm.sendOk("属性或卷轴数量已经发生变化，请重新操作。");
            cm.dispose();
            return;
        }
        if ((fromStat == 2048 || fromStat == 8192 || toStat == 2048 || toStat == 8192)
                && !HpChallengeService.markWashingRoute(cm.getPlayer())) {
            cm.sendOk("生命之证任务已经开始，无法再洗HP或MP。");
            cm.dispose();
            return;
        }
        var oldHp = cm.getPlayer().getMaxHp();
        var oldMp = cm.getPlayer().getMaxMp();
        var completed = ResetScrollService.batchResetAp(cm.getClient(), fromStat, toStat, amount);
        if (completed > 0) {
            cm.gainItem(AP_SCROLL, -completed);
        }
        if (completed != amount) {
            cm.sendOk("只完成了 " + completed + " 点洗点，已按实际完成数量扣除卷轴。请检查属性限制后重试。");
        } else {
            var result = "洗点完成，共处理 " + completed + " 点。";
            var hpChange = cm.getPlayer().getMaxHp() - oldHp;
            var mpChange = cm.getPlayer().getMaxMp() - oldMp;
            if (hpChange != 0) result += "\r\nHP " + signedNumber(hpChange);
            if (mpChange != 0) result += "\r\nMP " + signedNumber(mpChange);
            if (hpChange == 0 && mpChange == 0) {
                result += "\r\n" + statName(fromStat) + " -" + completed
                        + "，" + statName(toStat) + " +" + completed + "。";
            }
            cm.sendOk(result);
        }
        cm.dispose();
    }
}

function signedNumber(value) {
    return value > 0 ? "+" + value : "" + value;
}

function statName(stat) {
    for (var i = 0; i < stats.length; i++) {
        if (stats[i] == stat) return names[i];
    }
    return "未知属性";
}

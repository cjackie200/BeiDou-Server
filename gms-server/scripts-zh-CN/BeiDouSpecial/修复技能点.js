var status = -1;
var missingSp = 0;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode !== 1) {
        cm.dispose();
        return;
    }

    status++;

    var player = cm.getPlayer();

    if (status === 0) {
        if (player.isGM()) {
            cm.sendOk("GM角色不适用这个修复功能。");
            cm.dispose();
            return;
        }

        if (!player.isSkillPointRepairSupported()) {
            cm.sendOk("当前职业暂不适用技能点修复。\r\n\r\n新手职业请先转职；Evan 等独立技能点表职业暂不处理，避免补错技能书。");
            cm.dispose();
            return;
        }

        var expectedSp = player.getCurrentJobExpectedSp();
        var ownedSp = player.getCurrentJobOwnedSp();
        missingSp = player.getMissingJobSp();

        var text = "#e修复技能点#n\r\n\r\n";
        text += "职业：#b" + cm.getJobName(cm.getJobId()) + "#k\r\n";
        text += "等级：#b" + cm.getLevel() + "#k\r\n";
        text += "理论应有SP：#b" + expectedSp + "#k\r\n";
        text += "当前已拥有SP：#b" + ownedSp + "#k\r\n";
        text += "预计补发SP：#r" + missingSp + "#k\r\n\r\n";

        if (missingSp <= 0) {
            cm.sendOk(text + "你的技能点没有缺口，不需要修复。");
            cm.dispose();
            return;
        }

        cm.sendYesNo(text + "确认补发缺少的技能点吗？\r\n\r\n#r只补发SP，不会自动加技能，也不会重置技能。#k");
    } else if (status === 1) {
        var repairedSp = player.repairMissingJobSp();
        if (repairedSp <= 0) {
            cm.sendOk("你的技能点没有缺口，不需要修复。");
        } else {
            cm.sendOk("技能点修复完成。\r\n\r\n本次补发：#b" + repairedSp + "#k 点SP。\r\n现在可以重新打开技能栏分配技能点。");
        }
        cm.dispose();
    } else {
        cm.dispose();
    }
}

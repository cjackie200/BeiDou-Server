var status = -1;

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

    if (status === 0) {
        cm.sendYesNo(
            "#e一键清空装备栏#n\r\n\r\n" +
            "确认后会删除背包 #r装备栏#k 中的所有物品。\r\n" +
            "#r此操作不可恢复，请先确认没有要保留的装备。#k"
        );
        return;
    }

    cm.removeAllByInventory(1);
    cm.sendOk("装备栏已清空。");
    cm.dispose();
}

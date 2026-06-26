var status = -1;

function start() {
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode !== 1) {
        cm.dispose();
        return;
    }

    status++;

    if (status === 0) {
        var text = "请选择要打开的超级商店：\r\n\r\n";
        text += "#L0#全部商品#l\r\n";
        text += "#L1#本职业武器卷轴60%/30%#l\r\n";
        cm.sendSimple(text);
        return;
    }

    if (status === 1) {
        if (selection === 0) {
            openShop(9900001);
            return;
        }
        if (selection === 1) {
            var shopId = getClassWeaponScrollShopId(cm.getJobId());
            if (shopId <= 0) {
                cm.sendOk("当前职业暂时没有匹配的武器卷轴商店。");
                cm.dispose();
                return;
            }
            openShop(shopId);
            return;
        }
    }

    cm.dispose();
}

function openShop(shopId) {
    cm.dispose();
    cm.openShopNPC(shopId);
    cm.dispose();
}

function getClassWeaponScrollShopId(jobId) {
    var family = Math.floor(jobId / 100);
    if (family === 1 || family === 11 || family === 21) {
        return 9900101;
    }
    if (family === 2 || family === 12 || family === 22) {
        return 9900102;
    }
    if (family === 3 || family === 13) {
        return 9900103;
    }
    if (family === 4 || family === 14) {
        return 9900104;
    }
    if (family === 5 || family === 15) {
        return 9900105;
    }
    return 0;
}

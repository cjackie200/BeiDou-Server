var status = -1;

var scrollShops = [
    { label: "头盔60%卷轴", shopId: 9900201 },
    { label: "脸饰60%卷轴", shopId: 9900202 },
    { label: "眼饰60%卷轴", shopId: 9900203 },
    { label: "耳环60%卷轴", shopId: 9900204 },
    { label: "上衣60%卷轴", shopId: 9900205 },
    { label: "套服60%卷轴", shopId: 9900206 },
    { label: "下装60%卷轴", shopId: 9900207 },
    { label: "鞋子60%卷轴", shopId: 9900208 },
    { label: "手套60%卷轴", shopId: 9900209 },
    { label: "盾牌60%卷轴", shopId: 9900210 },
    { label: "披风60%卷轴", shopId: 9900211 },
    { label: "饰品/特殊60%卷轴", shopId: 9900212 },
    { label: "武器60%卷轴", shopId: 9900213 },
    { label: "宠物装备60%卷轴", shopId: 9900214 }
];

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
        text += "#L0#正常超级商店#l\r\n\r\n";
        text += "#b===== 60%卷轴商店 =====#k\r\n";
        for (var i = 0; i < scrollShops.length; i++) {
            text += "#L" + (i + 1) + "#" + scrollShops[i].label + "#l";
            text += i % 2 === 1 ? "\r\n" : " \t ";
        }
        cm.sendSimple(text);
        return;
    }

    if (status === 1) {
        if (selection === 0) {
            openShop(9900001);
            return;
        }

        var index = selection - 1;
        if (index >= 0 && index < scrollShops.length) {
            openShop(scrollShops[index].shopId);
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

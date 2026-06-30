var status = -1;

var scrollShops = [
    { label: "头盔卷轴", shopId: 9900201 },
    { label: "脸饰卷轴", shopId: 9900202 },
    { label: "眼饰卷轴", shopId: 9900203 },
    { label: "耳环卷轴", shopId: 9900204 },
    { label: "上衣卷轴", shopId: 9900205 },
    { label: "套服卷轴", shopId: 9900206 },
    { label: "下装卷轴", shopId: 9900207 },
    { label: "鞋子卷轴", shopId: 9900208 },
    { label: "手套卷轴", shopId: 9900209 },
    { label: "盾牌卷轴", shopId: 9900210 },
    { label: "披风卷轴", shopId: 9900211 },
    { label: "饰品/特殊卷轴", shopId: 9900212 },
    { label: "武器卷轴", shopId: 9900213 },
    { label: "宠物装备卷轴", shopId: 9900214 }
];

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
        var text = "请选择要打开的超级商店：\r\n\r\n";
        text += "#L0#正常超级商店#l\r\n\r\n";
        text += "#b===== 卷轴分类商店 =====#k\r\n";
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
    cm.openShopNPC(shopId);
    cm.dispose();
}

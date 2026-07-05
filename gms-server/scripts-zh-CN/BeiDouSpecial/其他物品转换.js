var status = -1;
var selectedGroup = null;
var selectedTarget = 0;
var selectedSource = 0;
var selectedQuantity = 0;

var GROUPS = [
    {
        name: "母矿转换",
        desc: "水晶母矿、金属母矿、宝石母矿 1:1 转换",
        sets: [
            range(4004000, 4004004),
            range(4010000, 4010007),
            range(4020000, 4020008)
        ]
    },
    {
        name: "粉末转换",
        desc: "魔法粉末 1:1 转换",
        sets: [
            range(4007000, 4007007)
        ]
    },
    {
        name: "宝石转换",
        desc: "普通宝石、水晶、同等级制作宝石 1:1 转换",
        sets: [
            range(4005000, 4005004),
            range(4021000, 4021008),
            stepped(4250000, 4251400, 100),
            stepped(4250001, 4251401, 100),
            stepped(4250002, 4251402, 100)
        ]
    }
];

function start() {
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode === -1) {
        cm.dispose();
        return;
    }

    if (mode === 0 && status !== 3) {
        cm.dispose();
        return;
    }

    if (mode === 1) {
        status++;
    } else {
        status--;
    }

    if (status === 0) {
        showGroupMenu();
    } else if (status === 1) {
        selectedGroup = GROUPS[selection];
        if (selectedGroup == null) {
            cm.sendOk("请选择正确的转换类型。");
            cm.dispose();
            return;
        }
        showTargetMenu();
    } else if (status === 2) {
        selectedTarget = selection;
        if (!isValidItemInGroup(selectedGroup, selectedTarget)) {
            cm.sendOk("请选择正确的目标物品。");
            cm.dispose();
            return;
        }
        showSourceMenu();
    } else if (status === 3) {
        selectedSource = selection;
        if (!isValidSource(selectedGroup, selectedTarget, selectedSource)) {
            cm.sendOk("请选择同类型且不同于目标的来源物品。");
            cm.dispose();
            return;
        }

        var maxQuantity = cm.getItemQuantity(selectedSource);
        if (maxQuantity <= 0) {
            cm.sendOk("你没有可用于转换的#b#t" + selectedSource + "##k。");
            cm.dispose();
            return;
        }
        cm.sendGetNumber(
            "你想把多少个#b#t" + selectedSource + "##k转换为#b#t" + selectedTarget + "##k？\r\n\r\n"
            + "当前持有：#b" + maxQuantity + "#k 个\r\n"
            + "兑换比例：#b1:1#k",
            Math.min(maxQuantity, 1),
            1,
            maxQuantity
        );
    } else if (status === 4) {
        selectedQuantity = selection;
        if (!isPositiveQuantity(selectedQuantity)) {
            cm.sendOk("请输入正确的转换数量。");
            cm.dispose();
            return;
        }

        cm.sendYesNo(
            "请确认本次转换：\r\n\r\n"
            + "消耗：#b#i" + selectedSource + "# #t" + selectedSource + "# x" + selectedQuantity + "#k\r\n"
            + "获得：#b#i" + selectedTarget + "# #t" + selectedTarget + "# x" + selectedQuantity + "#k\r\n\r\n"
            + "#r确认后将立即扣除来源物品。#k"
        );
    } else if (status === 5) {
        performExchange();
    } else {
        cm.dispose();
    }
}

function showGroupMenu() {
    var text = "#e其他物品转换系统#n\r\n\r\n";
    text += "选择转换类型后，再选择目标物品、来源物品和数量。\r\n";
    text += "只支持同类型物品 1:1 等量兑换，不收手续费。\r\n\r\n";

    for (var i = 0; i < GROUPS.length; i++) {
        text += "#L" + i + "##b" + GROUPS[i].name + "#k - " + GROUPS[i].desc + "#l\r\n";
    }
    cm.sendSimple(text);
}

function showTargetMenu() {
    var text = "#e" + selectedGroup.name + "#n\r\n\r\n请选择你想要获得的目标物品：\r\n\r\n";
    text += buildItemMenu(flattenSets(selectedGroup.sets), 0);
    cm.sendSimple(text);
}

function showSourceMenu() {
    var sourceSet = findSetContaining(selectedGroup, selectedTarget);
    var text = "目标：#b#i" + selectedTarget + "# #t" + selectedTarget + "##k\r\n\r\n";
    text += "请选择要消耗的同类型来源物品：\r\n\r\n";

    var hasSource = false;
    for (var i = 0; i < sourceSet.length; i++) {
        var itemId = sourceSet[i];
        if (itemId === selectedTarget) {
            continue;
        }

        var quantity = cm.getItemQuantity(itemId);
        text += "#L" + itemId + "#";
        text += "#i" + itemId + "# #b#t" + itemId + "##k";
        text += " #d持有：" + quantity + "#k#l\r\n";
        if (quantity > 0) {
            hasSource = true;
        }
    }

    if (!hasSource) {
        text += "\r\n#r你当前没有可消耗的其它同类型物品。#k";
    }
    cm.sendSimple(text);
}

function performExchange() {
    if (!isValidSource(selectedGroup, selectedTarget, selectedSource) || !isPositiveQuantity(selectedQuantity)) {
        cm.sendOk("转换信息异常，请重新打开系统。");
        cm.dispose();
        return;
    }

    if (!cm.haveItem(selectedSource, selectedQuantity)) {
        cm.sendOk("你的#b#t" + selectedSource + "##k数量不足，转换已取消。");
        cm.dispose();
        return;
    }

    if (!cm.canHold(selectedTarget, selectedQuantity, selectedSource, selectedQuantity)) {
        cm.sendOk("背包空间不足，转换已取消。请整理其它栏背包后再试。");
        cm.dispose();
        return;
    }

    cm.gainItem(selectedSource, -selectedQuantity);
    cm.gainItem(selectedTarget, selectedQuantity);
    cm.sendOk(
        "转换成功！\r\n\r\n"
        + "消耗：#b#i" + selectedSource + "# #t" + selectedSource + "# x" + selectedQuantity + "#k\r\n"
        + "获得：#b#i" + selectedTarget + "# #t" + selectedTarget + "# x" + selectedQuantity + "#k"
    );
    cm.dispose();
}

function buildItemMenu(items, quantityMode) {
    var text = "";
    for (var i = 0; i < items.length; i++) {
        var itemId = items[i];
        text += "#L" + itemId + "##i" + itemId + "# #b#t" + itemId + "##k";
        if (quantityMode) {
            text += " #d持有：" + cm.getItemQuantity(itemId) + "#k";
        }
        text += "#l\r\n";
    }
    return text;
}

function isValidSource(group, targetItemId, sourceItemId) {
    if (targetItemId === sourceItemId) {
        return false;
    }

    var set = findSetContaining(group, targetItemId);
    if (set == null) {
        return false;
    }
    return contains(set, sourceItemId);
}

function isValidItemInGroup(group, itemId) {
    return findSetContaining(group, itemId) != null;
}

function findSetContaining(group, itemId) {
    if (group == null || group.sets == null) {
        return null;
    }

    for (var i = 0; i < group.sets.length; i++) {
        if (contains(group.sets[i], itemId)) {
            return group.sets[i];
        }
    }
    return null;
}

function contains(items, itemId) {
    for (var i = 0; i < items.length; i++) {
        if (items[i] === itemId) {
            return true;
        }
    }
    return false;
}

function flattenSets(sets) {
    var items = [];
    for (var i = 0; i < sets.length; i++) {
        for (var j = 0; j < sets[i].length; j++) {
            items.push(sets[i][j]);
        }
    }
    return items;
}

function range(start, end) {
    var items = [];
    for (var itemId = start; itemId <= end; itemId++) {
        items.push(itemId);
    }
    return items;
}

function stepped(start, end, step) {
    var items = [];
    for (var itemId = start; itemId <= end; itemId += step) {
        items.push(itemId);
    }
    return items;
}

function isPositiveQuantity(quantity) {
    return quantity > 0 && quantity <= 32767;
}

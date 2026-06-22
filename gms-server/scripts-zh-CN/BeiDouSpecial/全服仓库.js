var GlobalStorageService = Java.type("org.gms.server.GlobalStorageService");

var VIEW_PAGE_SIZE = 80;
var state = "main";
var viewGroup = 1;
var viewPageNo = 1;
var entries = null;
var depositItems = null;
var lastWithdrawMessage = "";
var lastDepositMessage = "";
var batchResultMessage = "";

function start() {
    showMain();
}

function action(mode, type, selection) {
    if (mode !== 1) {
        cm.dispose();
        return;
    }

    if (state === "main") {
        handleMain(selection);
    } else if (state === "view") {
        handleView(selection);
    } else if (state === "depositItem") {
        handleDepositItem(selection);
    } else if (state === "batchType") {
        handleBatchType(selection);
    } else if (state === "batchResult") {
        handleBatchResult(selection);
    } else if (state === "backToMain") {
        showMain();
    } else {
        cm.dispose();
    }
}

function showMain() {
    state = "main";
    var text = "#e全服仓库#n\r\n";
    text += "总容量：#b" + GlobalStorageService.getTotalCapacity() + "#k 格";
    text += "　剩余：#b" + GlobalStorageService.getRemainingCapacity() + "#k 格\r\n\r\n";
    text += "#L1#查看/取出装备#l\r\n";
    text += "#L2#查看/取出消耗#l\r\n";
    text += "#L3#查看/取出其他#l\r\n";
    text += "#L4#存入单个物品#l\r\n";
    text += "#L5#一键整栏存入#l\r\n";
    text += "#L0#关闭#l";
    cm.sendSimple(text);
}

function handleMain(selection) {
    if (selection >= 1 && selection <= 3) {
        showGroup(selection, 1);
    } else if (selection === 4) {
        lastDepositMessage = "";
        showDepositItemList();
    } else if (selection === 5) {
        showBatchType();
    } else {
        cm.dispose();
    }
}

function showGroup(group, page) {
    state = "view";
    viewGroup = group;
    var total = GlobalStorageService.countByInventoryGroup(group);
    var maxPage = Math.max(1, Math.ceil(total / VIEW_PAGE_SIZE));
    viewPageNo = Math.max(1, Math.min(page, maxPage));
    entries = getSortedPageEntries(group, total, viewPageNo);

    var text = "#e全服仓库 - " + getGroupName(group) + "#n\r\n";
    text += "数量：#b" + total + "#k　列表页：" + viewPageNo + " / " + maxPage + "\r\n";
    text += "点击物品会直接取出，请确认背包有空位。\r\n\r\n";

    if (lastWithdrawMessage !== "") {
        text += "#b" + lastWithdrawMessage + "#k\r\n\r\n";
        lastWithdrawMessage = "";
    }

    if (entries.isEmpty()) {
        text += "#d当前分类没有物品。#k\r\n";
    } else {
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            text += "#L" + i + "##v" + entry.itemId() + "# #z" + entry.itemId() + "#";
            text += " x" + entry.quantity();
            text += " #d[" + entry.depositCharName() + " " + entry.createTime() + "]#k#l\r\n";
        }
    }

    text += "\r\n";
    if (viewPageNo > 1) {
        text += "#L900001#上一页#l ";
    }
    if (viewPageNo < maxPage) {
        text += "#L900002#下一页#l ";
    }
    text += "#L900000#返回#l";
    cm.sendSimple(text);
}

function handleView(selection) {
    if (selection === 900000) {
        showMain();
        return;
    }
    if (selection === 900001) {
        showGroup(viewGroup, viewPageNo - 1);
        return;
    }
    if (selection === 900002) {
        showGroup(viewGroup, viewPageNo + 1);
        return;
    }
    if (selection < 0 || selection >= entries.size()) {
        showMessage("选择无效。");
        return;
    }

    var entry = entries.get(selection);
    var result = GlobalStorageService.withdraw(cm.getPlayer(), entry.id());
    lastWithdrawMessage = result.message();
    showGroup(viewGroup, viewPageNo);
}

function getSortedPageEntries(group, total, page) {
    var ArrayList = Java.type("java.util.ArrayList");
    var sortedEntries = new ArrayList();
    if (total <= 0) {
        return sortedEntries;
    }

    var allEntries = GlobalStorageService.listByInventoryGroup(group, 1, total);
    var currentCharName = String(cm.getPlayer().getName());
    var items = [];
    for (var i = 0; i < allEntries.size(); i++) {
        items.push(allEntries.get(i));
    }

    items.sort(function (left, right) {
        var leftOwned = String(left.depositCharName()) === currentCharName ? 0 : 1;
        var rightOwned = String(right.depositCharName()) === currentCharName ? 0 : 1;
        if (leftOwned !== rightOwned) {
            return leftOwned - rightOwned;
        }

        var categoryDiff = getItemCategory(left.itemId()) - getItemCategory(right.itemId());
        if (categoryDiff !== 0) {
            return categoryDiff;
        }

        var itemIdDiff = left.itemId() - right.itemId();
        if (itemIdDiff !== 0) {
            return itemIdDiff;
        }
        if (left.id() < right.id()) {
            return -1;
        }
        if (left.id() > right.id()) {
            return 1;
        }
        return 0;
    });

    var start = (page - 1) * VIEW_PAGE_SIZE;
    var end = Math.min(start + VIEW_PAGE_SIZE, items.length);
    for (var index = start; index < end; index++) {
        sortedEntries.add(items[index]);
    }
    return sortedEntries;
}

function getItemCategory(itemId) {
    // 冒险岛物品 ID 的前三位代表大类，例如 204 为卷轴、200 为药水。
    return Math.floor(itemId / 10000);
}

function showDepositItemList() {
    state = "depositItem";
    depositItems = GlobalStorageService.getDepositableItems(cm.getPlayer());

    var text = "#e选择要存入的物品#n\r\n";
    text += "全服仓库剩余：#b" + GlobalStorageService.getRemainingCapacity() + "#k 格\r\n";
    text += "点击后会自动放入仓库空位，并继续停留在此界面。\r\n\r\n";

    if (lastDepositMessage !== "") {
        text += "#b" + lastDepositMessage + "#k\r\n\r\n";
        lastDepositMessage = "";
    }

    if (depositItems.isEmpty()) {
        text += "#d背包里没有可以放入全服仓库的物品。#k\r\n";
    } else {
        for (var i = 0; i < depositItems.size(); i++) {
            var item = depositItems.get(i);
            text += "#L" + i + "##v" + item.getItemId() + "# #z" + item.getItemId() + "#";
            text += " x" + item.getQuantity();
            text += " #d[" + getInventoryName(item.getInventoryType().getType()) + "栏 格子 " + item.getPosition() + "]#k#l\r\n";
        }
    }

    text += "\r\n#L900000#返回全服仓库#l";
    cm.sendSimple(text);
}

function handleDepositItem(selection) {
    if (selection === 900000) {
        showMain();
        return;
    }
    if (selection < 0 || selection >= depositItems.size()) {
        lastDepositMessage = "选择无效。";
        showDepositItemList();
        return;
    }

    var item = depositItems.get(selection);
    var invType = item.getInventoryType().getType();
    var result = GlobalStorageService.depositAuto(cm.getPlayer(), invType, item.getPosition(), item.getQuantity());
    lastDepositMessage = result.message();
    showDepositItemList();
}

function showBatchType() {
    state = "batchType";
    var text = "#e一键整栏存入#n\r\n";
    text += "全服仓库剩余：#b" + GlobalStorageService.getRemainingCapacity() + "#k 格\r\n";
    text += "只会存入可交易、未锁定、非点装/宠物/戒指绑定的普通背包物品。\r\n\r\n";
    text += "#L1#装备栏#l\r\n";
    text += "#L2#消耗栏#l\r\n";
    text += "#L3#设置栏#l\r\n";
    text += "#L4#其他栏#l\r\n";
    text += "#L900000#返回全服仓库#l";
    cm.sendSimple(text);
}

function handleBatchType(selection) {
    if (selection === 900000) {
        showMain();
        return;
    }
    if (selection < 1 || selection > 4) {
        batchResultMessage = "请选择普通背包栏。";
        showBatchResult();
        return;
    }

    var result = GlobalStorageService.depositAllAuto(cm.getPlayer(), selection);
    batchResultMessage = result.message();
    showBatchResult();
}

function showBatchResult() {
    state = "batchResult";
    var text = "#e一键整栏存入结果#n\r\n";
    text += batchResultMessage + "\r\n\r\n";
    text += "#L1#继续存入单个物品#l\r\n";
    text += "#L2#继续一键整栏存入#l\r\n";
    text += "#L3#查看装备#l\r\n";
    text += "#L4#查看消耗#l\r\n";
    text += "#L5#查看其他#l\r\n";
    text += "#L0#返回全服仓库#l";
    cm.sendSimple(text);
}

function handleBatchResult(selection) {
    if (selection === 1) {
        lastDepositMessage = "";
        showDepositItemList();
    } else if (selection === 2) {
        showBatchType();
    } else if (selection >= 3 && selection <= 5) {
        showGroup(selection - 2, 1);
    } else {
        showMain();
    }
}

function getGroupName(group) {
    if (group === 1) {
        return "装备";
    }
    if (group === 2) {
        return "消耗";
    }
    return "其他";
}

function getInventoryName(invType) {
    if (invType === 1) {
        return "装备";
    }
    if (invType === 2) {
        return "消耗";
    }
    if (invType === 3) {
        return "设置";
    }
    if (invType === 4) {
        return "其他";
    }
    return "未知";
}

function showMessage(message) {
    state = "backToMain";
    cm.sendOk(message);
}

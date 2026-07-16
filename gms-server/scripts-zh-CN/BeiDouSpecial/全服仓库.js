var GlobalStorageService = Java.type("org.gms.server.GlobalStorageService");
var ArrayList = Java.type("java.util.ArrayList");

var VIEW_PAGE_SIZE = 80;
var state = "main";
var viewGroup = 1;
var viewPageNo = 1;
var viewKeyword = "";
var equipJobCategory = -1;
var depositInventoryType = 1;
var pendingWithdrawEntry = null;
var entries = new ArrayList();
var depositItems = new ArrayList();
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
    } else if (state === "searchInput") {
        handleSearchInput();
    } else if (state === "equipCategory") {
        handleEquipCategory(selection);
    } else if (state === "withdrawQuantity") {
        handleWithdrawQuantity(selection);
    } else if (state === "depositType") {
        handleDepositType(selection);
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
    text += "  剩余：#b" + GlobalStorageService.getRemainingCapacity() + "#k 格\r\n\r\n";
    text += "#L1#查看/取出装备#l\r\n";
    text += "#L2#查看/取出消耗#l\r\n";
    text += "#L3#查看/取出其他#l\r\n";
    text += "#L4#存入单个物品#l\r\n";
    text += "#L5#一键整栏存入#l\r\n";
    text += "#L0#关闭#l";
    cm.sendSimple(text);
}

function handleMain(selection) {
    if (selection === 1) {
        showEquipCategory();
    } else if (selection >= 2 && selection <= 3) {
        viewKeyword = "";
        showGroup(selection, 1);
    } else if (selection === 4) {
        lastDepositMessage = "";
        showDepositType();
    } else if (selection === 5) {
        showBatchType();
    } else {
        cm.dispose();
    }
}

function showEquipCategory() {
    state = "equipCategory";
    var text = "#e查看/取出装备 - 选择职业#n\r\n\r\n";
    text += "#L0#全部装备#l\r\n";
    text += "#L1#通用装备#l\r\n";
    text += "#L2#战士装备#l\r\n";
    text += "#L3#魔法师装备#l\r\n";
    text += "#L4#弓箭手装备#l\r\n";
    text += "#L5#飞侠装备#l\r\n";
    text += "#L6#海盗装备#l\r\n";
    text += "#L900000#返回全服仓库#l";
    cm.sendSimple(text);
}

function handleEquipCategory(selection) {
    if (selection === 900000) {
        showMain();
        return;
    }
    if (selection < 0 || selection > 6) {
        showEquipCategory();
        return;
    }
    equipJobCategory = selection - 1;
    viewKeyword = "";
    showGroup(1, 1);
}

function showGroup(group, page) {
    state = "view";
    viewGroup = group;

    var sortedEntries = getSortedEntries(group, viewKeyword);
    var total = sortedEntries.size();
    var maxPage = Math.max(1, Math.ceil(total / VIEW_PAGE_SIZE));
    viewPageNo = Math.max(1, Math.min(page, maxPage));
    entries = sliceEntries(sortedEntries, viewPageNo);

    var text = "#e全服仓库 - " + getGroupName(group) + getEquipCategorySuffix(group) + "#n\r\n";
    text += "数量：#b" + total + "#k  列表页：" + viewPageNo + " / " + maxPage + "\r\n";
    if (viewKeyword !== "") {
        text += "当前搜索：#b" + viewKeyword + "#k\r\n";
    }
    text += "点击物品会直接取出，请确认背包有空位。\r\n\r\n";
    text += "#L900003#关键词搜索#l ";
    if (viewKeyword !== "") {
        text += "#L900004#清除搜索#l ";
    }
    text += "#L900000#返回#l\r\n\r\n";

    if (lastWithdrawMessage !== "") {
        text += "#b" + lastWithdrawMessage + "#k\r\n\r\n";
        lastWithdrawMessage = "";
    }

    if (entries.isEmpty()) {
        if (viewKeyword !== "") {
            text += "#d当前分类没有包含这个关键词的物品。#k\r\n";
        } else {
            text += "#d当前分类没有物品。#k\r\n";
        }
    } else {
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            text += "#L" + i + "##v" + entry.itemId() + "# #z" + entry.itemId() + "#";
            text += " x" + entry.quantity();
            text += "#l\r\n";
        }
    }

    text += "\r\n";
    if (viewPageNo > 1) {
        text += "#L900001#上一页#l ";
    }
    if (viewPageNo < maxPage) {
        text += "#L900002#下一页#l ";
    }
    cm.sendSimple(text);
}

function handleView(selection) {
    if (selection === 900000) {
        if (viewGroup === 1) {
            showEquipCategory();
        } else {
            showMain();
        }
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
    if (selection === 900003) {
        state = "searchInput";
        cm.sendGetText("请输入要搜索的物品关键词，可以输入物品名或物品ID。\r\n留空则清除搜索。");
        return;
    }
    if (selection === 900004) {
        viewKeyword = "";
        showGroup(viewGroup, 1);
        return;
    }
    if (selection < 0 || selection >= entries.size()) {
        showMessage("选择无效。");
        return;
    }

    var entry = entries.get(selection);
    if (entry.inventoryType() !== 1 && entry.quantity() > 1) {
        pendingWithdrawEntry = entry;
        state = "withdrawQuantity";
        cm.sendGetNumber("#e取出数量#n\r\n#v" + entry.itemId() + "# #z" + entry.itemId()
                + "#\r\n仓库共有 #b" + entry.quantity() + "#k 个，请输入取出数量。",
                1, 1, Math.min(entry.quantity(), 32767));
        return;
    }
    var result = GlobalStorageService.withdraw(cm.getPlayer(), entry.id(), 1);
    lastWithdrawMessage = result.message();
    showGroup(viewGroup, viewPageNo);
}

function handleWithdrawQuantity(quantity) {
    if (pendingWithdrawEntry === null) {
        showGroup(viewGroup, viewPageNo);
        return;
    }
    var result = GlobalStorageService.withdraw(cm.getPlayer(), pendingWithdrawEntry.id(), quantity);
    pendingWithdrawEntry = null;
    lastWithdrawMessage = result.message();
    showGroup(viewGroup, viewPageNo);
}

function handleSearchInput() {
    viewKeyword = normalizeKeyword(cm.getText());
    showGroup(viewGroup, 1);
}

function getSortedEntries(group, keyword) {
    var sortedEntries = new ArrayList();
    var total = GlobalStorageService.countByInventoryGroup(group);
    if (total <= 0) {
        return sortedEntries;
    }

    var allEntries = GlobalStorageService.listByInventoryGroup(group, 1, total);
    var items = [];
    for (var i = 0; i < allEntries.size(); i++) {
        var entry = allEntries.get(i);
        if (matchesKeyword(entry, keyword) && matchesEquipJobCategory(entry)) {
            items.push(entry);
        }
    }

    items.sort(function (left, right) {
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

    for (var index = 0; index < items.length; index++) {
        sortedEntries.add(items[index]);
    }
    return sortedEntries;
}

function matchesEquipJobCategory(entry) {
    if (entry.inventoryType() !== 1 || equipJobCategory < 0) {
        return true;
    }
    return GlobalStorageService.getEquipJobCategory(entry.itemId()) === equipJobCategory;
}

function sliceEntries(sortedEntries, page) {
    var pageEntries = new ArrayList();
    var start = (page - 1) * VIEW_PAGE_SIZE;
    var end = Math.min(start + VIEW_PAGE_SIZE, sortedEntries.size());
    for (var index = start; index < end; index++) {
        pageEntries.add(sortedEntries.get(index));
    }
    return pageEntries;
}

function matchesKeyword(entry, keyword) {
    keyword = normalizeKeyword(keyword);
    if (keyword === "") {
        return true;
    }

    var lowerKeyword = keyword.toLowerCase();
    var itemIdText = String(entry.itemId());
    var itemName = String(entry.getName()).toLowerCase();
    return itemIdText.indexOf(lowerKeyword) >= 0 || itemName.indexOf(lowerKeyword) >= 0;
}

function normalizeKeyword(text) {
    if (text === null || text === undefined) {
        return "";
    }
    return String(text).replace(/^\s+|\s+$/g, "");
}

function getItemCategory(itemId) {
    return Math.floor(itemId / 10000);
}

function showDepositType() {
    state = "depositType";
    var text = "#e存入单个物品 - 选择背包页签#n\r\n\r\n";
    text += "#L1#装备栏#l\r\n";
    text += "#L2#消耗栏#l\r\n";
    text += "#L3#设置栏#l\r\n";
    text += "#L4#其他栏#l\r\n";
    text += "#L900000#返回全服仓库#l";
    cm.sendSimple(text);
}

function handleDepositType(selection) {
    if (selection === 900000) {
        showMain();
        return;
    }
    if (selection < 1 || selection > 4) {
        showDepositType();
        return;
    }
    depositInventoryType = selection;
    showDepositItemList();
}

function showDepositItemList() {
    state = "depositItem";
    depositItems = GlobalStorageService.getDepositableItems(cm.getPlayer(), depositInventoryType);

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

    text += "\r\n#L900001#切换背包页签#l  #L900000#返回全服仓库#l";
    cm.sendSimple(text);
}

function handleDepositItem(selection) {
    if (selection === 900000) {
        showMain();
        return;
    }
    if (selection === 900001) {
        showDepositType();
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
        showDepositType();
    } else if (selection === 2) {
        showBatchType();
    } else if (selection >= 3 && selection <= 5) {
        viewKeyword = "";
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

function getEquipCategorySuffix(group) {
    if (group !== 1 || equipJobCategory < 0) {
        return "";
    }
    var names = ["通用", "战士", "魔法师", "弓箭手", "飞侠", "海盗"];
    return " - " + names[equipJobCategory];
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

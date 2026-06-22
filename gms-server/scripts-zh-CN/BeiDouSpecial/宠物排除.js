var status = -1;
var pending = 0;
var PetItemIgnore = Java.type("org.gms.client.inventory.PetItemIgnore");

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
        var text = "#e宠物排除快捷设置#n\r\n\r\n";
        text += "此处会应用到当前已召唤的宠物。\r\n";
        text += "需要宠物具备道具排除能力，拾取时才会生效。\r\n\r\n";
        text += "#b#L1#排除所有补血/补蓝道具#l\r\n";
        text += "#L2#取消排除所有补血/补蓝道具#l\r\n";
        text += "#L3#排除指定等级以下装备#l\r\n";
        text += "#L4#取消排除指定等级以下装备#l\r\n";
        cm.sendSimple(text);
        return;
    }

    if (status === 1) {
        if (selection === 1) {
            applyRule(PetItemIgnore.HP_MP_CONSUMABLES, true, "所有补血/补蓝道具");
            return;
        }
        if (selection === 2) {
            applyRule(PetItemIgnore.HP_MP_CONSUMABLES, false, "所有补血/补蓝道具");
            return;
        }
        if (selection === 3) {
            pending = 3;
            cm.sendGetNumber("请输入要排除的装备等级上限。\r\n例如输入 #b100#k，则宠物不会拾取 #r100级以下#k 的装备。", 100, 1, 300);
            return;
        }
        if (selection === 4) {
            applyRule(PetItemIgnore.equipBelowLevelRule(1), false, "指定等级以下装备");
            return;
        }

        cm.sendOk("未知选项。");
        cm.dispose();
        return;
    }

    if (status === 2 && pending === 3) {
        var level = Math.max(1, Math.min(300, selection));
        applyRule(PetItemIgnore.equipBelowLevelRule(level), true, level + "级以下装备");
        return;
    }

    cm.dispose();
}

function applyRule(ruleId, enabled, name) {
    var count = cm.getPlayer().setSpecialPetIgnoreRuleForSummonedPets(ruleId, enabled);
    if (count <= 0) {
        cm.sendOk("没有可更新的召唤宠物，或该规则已经是当前状态。");
    } else {
        cm.sendOk((enabled ? "已开启：" : "已取消：") + name + "\r\n已更新宠物数量：" + count);
    }
    cm.dispose();
}

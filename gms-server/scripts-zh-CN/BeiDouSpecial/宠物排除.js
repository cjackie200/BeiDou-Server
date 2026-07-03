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
        showMenu();
        return;
    }

    if (status === 1) {
        if (selection === 1) {
            toggleRule(PetItemIgnore.HP_MP_CONSUMABLES, "补血/补蓝道具");
            return;
        }
        if (selection === 2) {
            toggleRule(PetItemIgnore.SCROLLS_EXCEPT_WHITE, "除祝福卷轴外的卷轴");
            return;
        }
        if (selection === 3) {
            var currentLevel = cm.getPlayer().getSummonedPetIgnoreEquipBelowLevel();
            if (currentLevel > 0) {
                applyRule(PetItemIgnore.equipBelowLevelRule(1), false, currentLevel + "级以下装备");
                return;
            }
            pending = 3;
            cm.sendGetNumber("请输入要屏蔽拾取的装备等级上限。\r\n例如输入 #b100#k，宠物将不拾取 #r100级以下#k 的装备。", 100, 1, 300);
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

function showMenu() {
    var text = "#e宠物排除快捷设置#n\r\n\r\n";
    text += "设置会应用到当前已召唤的宠物。\r\n";
    text += "需要宠物装备了道具排除能力，拾取时才会生效。\r\n\r\n";
    text += optionLine(1, "屏蔽补血/补蓝道具", cm.getPlayer().hasSpecialPetIgnoreRuleForSummonedPets(PetItemIgnore.HP_MP_CONSUMABLES));
    text += optionLine(2, "屏蔠除祝福卷轴外的卷轴", cm.getPlayer().hasSpecialPetIgnoreRuleForSummonedPets(PetItemIgnore.SCROLLS_EXCEPT_WHITE));

    var equipLevel = cm.getPlayer().getSummonedPetIgnoreEquipBelowLevel();
    var equipText = equipLevel > 0 ? "屏蔽" + equipLevel + "级以下装备" : "屏蔽X级以下装备";
    text += optionLine(3, equipText, equipLevel > 0);
    cm.sendSimple(text);
}

function optionLine(selection, name, enabled) {
    return "#L" + selection + "#" + (enabled ? "#r[已屏蔽]#k " : "#g[未屏蔽]#k ") + name + "#l\r\n";
}

function toggleRule(ruleId, name) {
    var enabled = !cm.getPlayer().hasSpecialPetIgnoreRuleForSummonedPets(ruleId);
    applyRule(ruleId, enabled, name);
}

function applyRule(ruleId, enabled, name) {
    var count = cm.getPlayer().setSpecialPetIgnoreRuleForSummonedPets(ruleId, enabled);
    if (count <= 0) {
        cm.sendOk("没有可更新的召唤宠物，或该规则已经是当前状态。");
    } else {
        cm.sendOk((enabled ? "已屏蔽：" : "已取消屏蔽：") + name + "\r\n已更新宠物数量：" + count);
    }
    cm.dispose();
}

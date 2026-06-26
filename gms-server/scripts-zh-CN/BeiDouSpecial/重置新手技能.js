var status = -1;

var SkillFactory = Java.type("org.gms.client.SkillFactory");

function start() {
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode === 1) {
        status++;
    } else {
        cm.dispose();
        return;
    }

    if (status === 0) {
        showConfirm();
        return;
    }

    if (status === 1) {
        resetBeginnerSkills();
        return;
    }

    cm.dispose();
}

function showConfirm() {
    var skills = getBeginnerSkills();
    var totalLevel = getTotalSkillLevel(skills);
    var text = "#e重置新手技能#n\r\n\r\n";
    text += "当前新手技能点数：#b" + totalLevel + "#k\r\n\r\n";
    text += buildSkillLine(skills[0]);
    text += buildSkillLine(skills[1]);
    text += buildSkillLine(skills[2]);
    text += "\r\n是否把以上三个新手技能全部重置为 0？\r\n";
    text += "重置后可以在技能窗口重新分配。";
    cm.sendYesNo(text);
}

function resetBeginnerSkills() {
    var skills = getBeginnerSkills();
    var totalLevel = getTotalSkillLevel(skills);

    if (totalLevel <= 0) {
        cm.sendOk("你当前没有已分配的新手技能点。");
        cm.dispose();
        return;
    }

    var player = cm.getPlayer();
    for (var i = 0; i < skills.length; i++) {
        var skill = SkillFactory.getSkill(skills[i]);
        if (skill == null) {
            continue;
        }
        player.changeSkillLevel(skill, 0, skill.getMaxLevel(), -1);
    }

    cm.sendOk("新手技能已经重置。\r\n请打开技能窗口重新分配蜗牛投掷术、团队治疗、疾风步。");
    cm.dispose();
}

function getBeginnerSkills() {
    var base = cm.getPlayer().getJobType() * 10000000 + 1000;
    return [base, base + 1, base + 2];
}

function getTotalSkillLevel(skills) {
    var player = cm.getPlayer();
    var total = 0;
    for (var i = 0; i < skills.length; i++) {
        total += player.getSkillLevel(skills[i]);
    }
    return total;
}

function buildSkillLine(skillId) {
    return "#s" + skillId + "# #q" + skillId + "#：#b" + cm.getPlayer().getSkillLevel(skillId) + "#k\r\n";
}

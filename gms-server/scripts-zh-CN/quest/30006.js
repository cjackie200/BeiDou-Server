var SkillBreakthroughService = Java.type("org.gms.server.quest.SkillBreakthroughService");

var status = -1;

function start(mode, type, selection) {
    if (!advance(mode)) {
        return;
    }

    if (status == 0) {
        if (qm.getLevel() < 150) {
            qm.sendOk("达到150级后，再来接受突破任务。");
            qm.dispose();
            return;
        }
        if (!SkillBreakthroughService.isSupportedJob(qm.getPlayer().getJob().getId())) {
            qm.sendOk("当前职业暂时没有可突破的1-4转技能。");
            qm.dispose();
            return;
        }
        qm.sendAcceptDecline("你已经拥有挑战更高技能上限的资格。击败扎昆后，我会为你解锁本职业可突破技能的上限，并赠送对应的突破技能点。");
        return;
    }

    if (status == 1) {
        qm.forceStartQuest();
        qm.sendOk("去击败扎昆吧。完成后可直接通过灯泡领取突破奖励。");
    }
    qm.dispose();
}

function end(mode, type, selection) {
    if (!advance(mode)) {
        return;
    }

    if (status == 0) {
        var reward = SkillBreakthroughService.grantCompletionReward(qm.getPlayer());
        if (!reward.supported()) {
            qm.sendOk("当前职业暂时没有可突破的1-4转技能。");
            qm.dispose();
            return;
        }

        var text = "突破成功！\r\n\r\n已解锁本职业可突破技能的上限，并赠送 #b" + reward.grantedSp() + "#k 点突破技能点。\r\n";
        text += "请打开技能栏，把已满级的可突破技能提升到新的最高等级。";
        qm.sendOk(text);
        qm.forceCompleteQuest();
        return;
    }

    qm.dispose();
}

function advance(mode) {
    if (mode == -1) {
        qm.dispose();
        return false;
    }

    if (mode == 1) {
        status++;
    } else {
        status--;
    }

    if (status < 0) {
        qm.dispose();
        return false;
    }
    return true;
}

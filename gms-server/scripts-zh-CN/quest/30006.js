var SkillBreakthroughService = Java.type("org.gms.server.quest.SkillBreakthroughService");

var QUEST_ID = 30006;
var STAGE_NAME = "一转";
var status = -1;

function start(mode, type, selection) {
    if (!advance(mode)) {
        return;
    }

    if (status == 0) {
        if (SkillBreakthroughService.isReadyForCompletion(qm.getPlayer(), QUEST_ID)) {
            qm.forceStartQuest();
            var readyReward = SkillBreakthroughService.grantCompletionReward(qm.getPlayer(), QUEST_ID);
            if (!readyReward.supported()) {
                qm.sendOk("突破奖励结算失败，请重新登录后再试。");
                qm.dispose();
                return;
            }
            qm.forceCompleteQuest();
            qm.sendOk(STAGE_NAME + "突破成功！\r\n\r\n已解锁对应突破技能上限，并赠送 #b"
                    + readyReward.grantedSp() + "#k 点突破技能点。");
            return;
        }
        if (!SkillBreakthroughService.canStartQuest(qm.getPlayer(), QUEST_ID)) {
            qm.sendOk("你目前还不满足" + STAGE_NAME + "突破任务的条件，请先完成此前实际存在的突破阶段。");
            qm.dispose();
            return;
        }
        var mobId = SkillBreakthroughService.getQuestMobId(QUEST_ID);
        var count = SkillBreakthroughService.getRequiredMobKills(QUEST_ID, mobId);
        qm.sendAcceptDecline("接受" + STAGE_NAME + "突破试炼？需要击败 #r#o" + mobId + "##k " + count + "只。完成后会解锁本阶段可突破技能，并赠送对应技能点。");
        return;
    }

    if (status == 1) {
        qm.forceStartQuest();
        qm.getPlayer().flushDelayedUpdateQuests();
        qm.sendOk("突破试炼已经开始。完成目标后，可通过灯泡领取奖励。");
    }
    qm.dispose();
}
function end(mode, type, selection) {
    if (!advance(mode)) {
        return;
    }

    if (status == 0) {
        var reward = SkillBreakthroughService.grantCompletionReward(qm.getPlayer(), QUEST_ID);
        if (!reward.supported()) {
            qm.sendOk("当前职业在这个转职阶段没有可突破技能。");
            qm.dispose();
            return;
        }

        var text = STAGE_NAME + "突破成功！\r\n\r\n已解锁对应突破技能上限，并赠送 #b" + reward.grantedSp() + "#k 点突破技能点。";
        if (reward.legacyAllStages()) {
            text += "\r\n#d你的旧版突破任务已按原待遇结算，所有有效阶段均已解锁。#k";
        }
        qm.sendOk(text);
        qm.forceCompleteQuest();
        return;
    }

    qm.getPlayer().flushDelayedUpdateQuests();
    qm.dispose();
}

function advance(mode) {
    if (mode == -1) {
        qm.dispose();
        return false;
    }
    status += mode == 1 ? 1 : -1;
    if (status < 0) {
        qm.dispose();
        return false;
    }
    return true;
}

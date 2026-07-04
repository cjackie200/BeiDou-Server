var status = -1;
var selectedReward = -1;
var action = 0;

var ACTION_ADVANCE = 1;
var ACTION_REWARD = 2;

var QuestStatus = Java.type("org.gms.client.QuestStatus");
var ElementalResonanceQuest = Java.type("org.gms.server.quest.ElementalResonanceQuest");

function start(mode, type, selection) {
    if (!advance(mode, type)) {
        return;
    }

    if (status == 0) {
        ElementalResonanceQuest.syncQuestState(qm.getPlayer());
        if (qm.getPlayer().getQuestStatus(qm.getQuest()) == QuestStatus.Status.STARTED.getId()) {
            qm.sendOk(ElementalResonanceQuest.progressText(qm.getPlayer(), qm.getQuest()));
            qm.dispose();
            return;
        }

        var validation = ElementalResonanceQuest.validateStart(qm.getPlayer(), qm.getQuest());
        if (!validation.isOk()) {
            qm.sendOk(validation.getMessage());
            ElementalResonanceQuest.syncQuestState(qm.getPlayer());
            qm.dispose();
            return;
        }

        qm.sendYesNo(ElementalResonanceQuest.startPrompt(qm.getPlayer(), qm.getQuest()));
    } else if (status == 1) {
        var result = ElementalResonanceQuest.startStage(qm.getPlayer(), qm.getQuest());
        qm.sendOk(result.message());
        qm.dispose();
    }
}

function end(mode, type, selection) {
    if (!advance(mode, type)) {
        return;
    }

    if (status == 0) {
        ElementalResonanceQuest.syncQuestState(qm.getPlayer());
        if (ElementalResonanceQuest.isFinalRewardStep(qm.getPlayer(), qm.getQuest())) {
            action = ACTION_REWARD;
            var validation = ElementalResonanceQuest.validateCompletion(qm.getPlayer(), qm.getQuest(), -1);
            if (!validation.isOk()) {
                qm.sendOk(validation.getMessage());
                ElementalResonanceQuest.syncQuestState(qm.getPlayer());
                qm.dispose();
                return;
            }

            qm.sendSimple(ElementalResonanceQuest.rewardSelectionPrompt(qm.getPlayer(), qm.getQuest()));
            return;
        }

        action = ACTION_ADVANCE;
        var advanceValidation = ElementalResonanceQuest.validateStepAdvance(qm.getPlayer(), qm.getQuest());
        if (!advanceValidation.isOk()) {
            qm.sendOk(advanceValidation.getMessage());
            ElementalResonanceQuest.syncQuestState(qm.getPlayer());
            qm.dispose();
            return;
        }

        qm.sendYesNo(ElementalResonanceQuest.advancePrompt(qm.getPlayer(), qm.getQuest()));
    } else if (status == 1) {
        if (action == ACTION_ADVANCE) {
            var advanceResult = ElementalResonanceQuest.advanceCurrentStep(qm.getPlayer(), qm.getQuest());
            qm.sendOk(advanceResult.message());
            qm.dispose();
            return;
        }

        selectedReward = selection;
        var confirmValidation = ElementalResonanceQuest.validateCompletion(qm.getPlayer(), qm.getQuest(), selectedReward);
        if (!confirmValidation.isOk()) {
            qm.sendOk(confirmValidation.getMessage());
            ElementalResonanceQuest.syncQuestState(qm.getPlayer());
            qm.dispose();
            return;
        }

        qm.sendYesNo(ElementalResonanceQuest.completionPrompt(qm.getPlayer(), qm.getQuest(), selectedReward));
    } else if (status == 2) {
        if (action != ACTION_REWARD) {
            ElementalResonanceQuest.syncQuestState(qm.getPlayer());
            qm.dispose();
            return;
        }

        var result = ElementalResonanceQuest.completeStage(qm.getPlayer(), qm.getQuest(), selectedReward);
        qm.sendOk(result.message());
        qm.dispose();
    }
}

function advance(mode, type) {
    if (mode == -1 || (mode == 0 && status >= 0)) {
        ElementalResonanceQuest.syncQuestState(qm.getPlayer());
        qm.dispose();
        return false;
    }

    if (mode == 1) {
        status++;
    } else {
        status--;
    }
    return true;
}

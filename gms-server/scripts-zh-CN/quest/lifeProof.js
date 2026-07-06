var LifeProofQuest = Java.type('org.gms.server.hpchallenge.LifeProofQuest');
var status = -1;
var endAction = '';
var nextQuestId = 0;

function isOk(result) {
    return String(result).indexOf('OK|') == 0;
}

function isErr(result) {
    return String(result).indexOf('ERR|') == 0;
}

function isInfo(result) {
    return String(result).indexOf('INFO|') == 0;
}

function isReady(result) {
    return String(result).indexOf('READY|') == 0;
}

function message(result) {
    var text = String(result);
    var separator = text.indexOf('|');
    if (separator < 0) {
        return text;
    }
    return text.substring(separator + 1);
}

function selectedQuestId(result) {
    var text = String(result);
    var separator = text.indexOf('|', 3);
    if (separator < 0) {
        return 0;
    }
    return parseInt(text.substring(3, separator), 10);
}

function selectedMessage(result) {
    var text = String(result);
    var separator = text.indexOf('|', 3);
    if (separator < 0) {
        return '';
    }
    return text.substring(separator + 1);
}

function start(mode, type, selection) {
    if (mode == -1 || (mode == 0 && status == 0)) {
        qm.dispose();
        return;
    }
    if (mode == 1) {
        status++;
    } else {
        status--;
    }

    var questId = qm.getQuest();
    if (LifeProofQuest.isSelectorQuest(questId)) {
        startSelector(questId, selection);
        return;
    }

    if (status == 0) {
        qm.sendYesNo(LifeProofQuest.startPrompt(qm.getPlayer(), questId));
        return;
    }
    if (status == 1) {
        var result = LifeProofQuest.startQuest(qm.getPlayer(), questId);
        if (isOk(result)) {
            qm.forceStartQuest();
            LifeProofQuest.onStarted(qm.getPlayer(), questId);
        }
        qm.sendOk(message(result));
        return;
    }
    qm.dispose();
}

function startSelector(questId, selection) {
    if (status == 0) {
        qm.sendSimple(LifeProofQuest.selectionMenu(qm.getPlayer(), questId));
        return;
    }
    if (status == 1) {
        var result = LifeProofQuest.selectOptional(qm.getPlayer(), questId, selection, qm.getNpc());
        if (!isOk(result)) {
            qm.sendOk(message(result));
            return;
        }
        var selectedQuest = selectedQuestId(result);
        if (selectedQuest <= 0) {
            qm.sendOk('选择试炼失败，请重新打开任务。');
            return;
        }
        qm.forceStartQuest(questId);
        qm.forceCompleteQuest(questId);
        LifeProofQuest.startOptionSlot(qm.getPlayer(), selectedQuest, qm.getNpc());
        qm.getPlayer().yellowMessage('生命之证：' + selectedMessage(result));
        qm.dispose();
        return;
    }
    qm.dispose();
}

function end(mode, type, selection) {
    if (mode == -1 || (mode == 0 && status == 0)) {
        qm.dispose();
        return;
    }
    if (mode == 1) {
        status++;
    } else {
        status--;
    }

    var questId = qm.getQuest();
    if (status == 0) {
        if (LifeProofQuest.isAutoCompleteNpcTalk(qm.getPlayer(), questId, qm.getNpc())) {
            var autoResult = LifeProofQuest.complete(qm.getPlayer(), questId, qm.getNpc());
            if (!isOk(autoResult)) {
                endAction = 'done';
                qm.sendOk(message(autoResult));
                return;
            }
            qm.forceCompleteQuest();
            var nextTip = LifeProofQuest.afterNativeComplete(qm.getPlayer(), questId, qm.getNpc());
            nextQuestId = LifeProofQuest.nextContinuationQuestIdAtNpc(qm.getPlayer(), questId, qm.getNpc());
            if (nextQuestId > 0) {
                endAction = 'startNext';
                qm.sendYesNo(LifeProofQuest.startPrompt(qm.getPlayer(), nextQuestId));
                return;
            }
            endAction = 'done';
            qm.sendOk(message(autoResult) + String(nextTip));
            return;
        }
        var prompt = LifeProofQuest.endPrompt(qm.getPlayer(), questId, qm.getNpc());
        if (isReady(prompt)) {
            endAction = 'ready';
            qm.sendYesNo(message(prompt));
            return;
        }
        if (isInfo(prompt) || isErr(prompt)) {
            endAction = 'done';
            qm.sendOk(message(prompt));
            return;
        }
        endAction = 'done';
        qm.sendOk(String(prompt));
        return;
    }
    if (status == 1) {
        if (endAction == 'startNext') {
            var startResult = LifeProofQuest.startQuest(qm.getPlayer(), nextQuestId);
            if (isOk(startResult)) {
                qm.forceStartQuest(nextQuestId);
                LifeProofQuest.onStarted(qm.getPlayer(), nextQuestId);
            }
            qm.sendOk(message(startResult));
            return;
        }
        if (endAction != 'ready') {
            qm.dispose();
            return;
        }
        var result = LifeProofQuest.complete(qm.getPlayer(), questId, qm.getNpc());
        if (isOk(result)) {
            qm.forceCompleteQuest();
            var nextTip = LifeProofQuest.afterNativeComplete(qm.getPlayer(), questId, qm.getNpc());
            qm.sendOk(message(result) + String(nextTip));
            return;
        }
        qm.sendOk(message(result));
        return;
    }
    qm.dispose();
}

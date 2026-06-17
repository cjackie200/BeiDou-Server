var LifeProofQuest = Java.type('org.gms.server.hpchallenge.LifeProofQuest');
var status = -1;

function isOk(result) {
    return String(result).indexOf('OK|') == 0;
}

function message(result) {
    return String(result).substring(3);
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
        qm.forceStartQuest(selectedQuest);
        LifeProofQuest.onStarted(qm.getPlayer(), selectedQuest);
        qm.sendOk(selectedMessage(result));
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
        qm.sendYesNo(LifeProofQuest.completePrompt(qm.getPlayer(), questId));
        return;
    }
    if (status == 1) {
        var result = LifeProofQuest.complete(qm.getPlayer(), questId, qm.getNpc());
        if (isOk(result)) {
            qm.forceCompleteQuest();
        }
        qm.sendOk(message(result));
        return;
    }
    qm.dispose();
}

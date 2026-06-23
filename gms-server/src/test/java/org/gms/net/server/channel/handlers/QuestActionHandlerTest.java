package org.gms.net.server.channel.handlers;

import org.gms.server.hpchallenge.LifeProofQuest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestActionHandlerTest {

    @Test
    void hookQuestActionsUseOnlyStartAndCompleteActions() {
        assertTrue(LifeProofQuest.isVisibleQuestId(LifeProofQuest.FIRST_QUEST_ID));

        assertTrue(QuestActionHandler.isHookQuestAction((byte) 1));
        assertTrue(QuestActionHandler.isHookQuestAction((byte) 2));
        assertTrue(QuestActionHandler.isHookQuestAction((byte) 4));
        assertTrue(QuestActionHandler.isHookQuestAction((byte) 5));
        assertFalse(QuestActionHandler.isHookQuestAction((byte) 0));
        assertFalse(QuestActionHandler.isHookQuestAction((byte) 3));
    }

    @Test
    void darkWukongHuntQuestCanUseRemoteScriptEntry() {
        assertTrue(QuestActionHandler.isRemoteScriptQuest((short) 30005));
        assertFalse(QuestActionHandler.isRemoteScriptQuest((short) 30004));
    }
}

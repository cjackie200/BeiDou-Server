package org.gms.net.server.channel.handlers;

import org.gms.server.hpchallenge.LifeProofQuest;
import org.gms.server.quest.MonsterCardRingQuest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestActionHandlerTest {

    @Test
    void nativeLifeProofQuestActionsAreConsumedBeforeOriginalFlow() {
        short questId = (short) LifeProofQuest.FIRST_QUEST_ID;

        assertTrue(QuestActionHandler.shouldConsumeNativeLifeProofQuestAction(questId, (byte) 1));
        assertTrue(QuestActionHandler.shouldConsumeNativeLifeProofQuestAction(questId, (byte) 2));
        assertTrue(QuestActionHandler.shouldConsumeNativeLifeProofQuestAction(questId, (byte) 4));
        assertTrue(QuestActionHandler.shouldConsumeNativeLifeProofQuestAction(questId, (byte) 5));
        assertFalse(QuestActionHandler.shouldConsumeNativeLifeProofQuestAction(questId, (byte) 0));
        assertFalse(QuestActionHandler.shouldConsumeNativeLifeProofQuestAction(questId, (byte) 3));
    }

    @Test
    void nativeMonsterCardRingQuestActionsKeepExistingHookFlow() {
        assertFalse(QuestActionHandler.shouldConsumeNativeLifeProofQuestAction(
                MonsterCardRingQuest.CLAIM_QUEST_ID, (byte) 5));
    }
}

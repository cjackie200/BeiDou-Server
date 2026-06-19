package org.gms.server.quest.hook;

import org.gms.server.hpchallenge.LifeProofQuest;
import org.gms.server.quest.MonsterCardRingQuest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionHookRegistryTest {

    @Test
    void rulesIncludeLifeProofAndMonsterCardRingQuestActions() {
        Map<Integer, InteractionHookRule> questRules = InteractionHookRegistry.rules(null).stream()
                .filter(rule -> rule.eventMask() == InteractionHookProtocol.EVENT_MASK_QUEST_ACTION)
                .collect(Collectors.toMap(InteractionHookRule::questId, rule -> rule));

        assertEquals(InteractionHookAction.ALL_MASK, questRules.get(LifeProofQuest.FIRST_QUEST_ID).actionMask());
        assertEquals(InteractionHookAction.ALL_MASK, questRules.get((int) MonsterCardRingQuest.CLAIM_QUEST_ID).actionMask());
        assertEquals(InteractionHookAction.ALL_MASK, questRules.get((int) MonsterCardRingQuest.LAST_QUEST_ID).actionMask());
        assertFalse(questRules.containsKey(1000));
    }

    @Test
    void npcRulesUseExplicitNpcTargets() {
        assertNull(InteractionHookRegistry.resolveNpcHook(null, 1032001));

        boolean monsterCardNpcRule = InteractionHookRegistry.rules(null).stream()
                .anyMatch(rule -> rule.eventMask() == InteractionHookProtocol.EVENT_MASK_NPC_CLICK
                        && rule.targetType() == InteractionHookProtocol.TARGET_NPC
                        && rule.targetId() == MonsterCardRingQuest.NPC_ID);
        assertTrue(monsterCardNpcRule);
    }

    @Test
    void questRawActionsMapToStableHookActions() {
        assertEquals(InteractionHookAction.QUERY_START, InteractionHookAction.fromQuestRawAction(1));
        assertEquals(InteractionHookAction.QUERY_START, InteractionHookAction.fromQuestRawAction(4));
        assertEquals(InteractionHookAction.QUERY_COMPLETE, InteractionHookAction.fromQuestRawAction(2));
        assertEquals(InteractionHookAction.QUERY_PROGRESS, InteractionHookAction.fromQuestRawAction(5));
        assertNull(InteractionHookAction.fromQuestRawAction(0));
        assertTrue((InteractionHookAction.ALL_MASK & InteractionHookAction.QUERY_START.mask()) != 0);
        assertTrue((InteractionHookAction.ALL_MASK & InteractionHookAction.CONFIRM_COMPLETE.mask()) != 0);
    }
}

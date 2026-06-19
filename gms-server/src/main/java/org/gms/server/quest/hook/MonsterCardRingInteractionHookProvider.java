package org.gms.server.quest.hook;

import org.gms.client.Character;
import org.gms.server.quest.MonsterCardRingQuest;

import java.util.ArrayList;
import java.util.List;

final class MonsterCardRingInteractionHookProvider implements InteractionHookProvider {
    static final int MENU_SELECTION_ID = 520000;

    @Override
    public boolean supports(int questId, InteractionHookAction action) {
        return InteractionHookRegistry.isMonsterCardRingQuest(questId);
    }

    @Override
    public List<InteractionHookRule> rules(Character chr) {
        List<InteractionHookRule> rules = new ArrayList<>();
        for (int questId : MonsterCardRingQuest.getAllQuestIds()) {
            int stateMask = InteractionHookPackets.questStateMask(chr, questId);
            rules.add(InteractionHookRegistry.questRule(questId, stateMask));
        }
        for (int npcId : MonsterCardRingQuest.getHookNpcIds(chr)) {
            int questId = MonsterCardRingQuest.resolveCurrentQuestId(chr).orElse(0);
            int stateMask = questId > 0
                ? InteractionHookPackets.questStateMask(chr, questId)
                : InteractionHookProtocol.QUEST_STATE_MASK_ANY;
            rules.add(InteractionHookRegistry.npcRule(npcId, questId, stateMask));
            rules.add(InteractionHookRegistry.selectionRule(MENU_SELECTION_ID, questId, stateMask));
        }
        return rules;
    }

    @Override
    public InteractionHookTarget resolveNpcHook(Character chr, int npcId) {
        return MonsterCardRingQuest.resolveNpcHook(chr, npcId)
            .map(questId -> new InteractionHookTarget(questId, npcId, MonsterCardRingQuest.resolveCurrentAction(chr, questId)))
            .orElse(null);
    }

    @Override
    public InteractionHookTarget resolveSelectionHook(Character chr, int npcId, int selection) {
        if (selection != MENU_SELECTION_ID) {
            return null;
        }
        return MonsterCardRingQuest.resolveCurrentQuestId(chr)
            .map(questId -> new InteractionHookTarget(questId, npcId, MonsterCardRingQuest.resolveCurrentAction(chr, questId)))
            .orElse(null);
    }

    @Override
    public void open(InteractionHookContext context) {
        MonsterCardRingQuest.openHook(context);
    }

    @Override
    public void action(InteractionHookContext context, byte mode, byte lastMessage, int selection) {
        MonsterCardRingQuest.handleHookAction(context, mode, lastMessage, selection);
    }
}

package org.gms.server.quest.hook;

import org.gms.client.Character;
import org.gms.server.hpchallenge.LifeProofQuest;

import java.util.ArrayList;
import java.util.List;

final class LifeProofInteractionHookProvider implements InteractionHookProvider {
    static final int MENU_SELECTION_ID = 510000;

    @Override
    public boolean supports(int questId, InteractionHookAction action) {
        return InteractionHookRegistry.isLifeProofQuest(questId);
    }

    @Override
    public List<InteractionHookRule> rules(Character chr) {
        List<InteractionHookRule> rules = new ArrayList<>();
        for (int questId : LifeProofQuest.getAllQuestIds()) {
            int stateMask = InteractionHookPackets.questStateMask(chr, questId);
            rules.add(InteractionHookRegistry.questRule(questId, stateMask));
        }
        for (int npcId : LifeProofQuest.getCurrentInstructorNpcIds(chr)) {
            int questId = LifeProofQuest.resolveCurrentQuestId(chr).orElse(0);
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
        return LifeProofQuest.resolveNpcHook(chr, npcId)
            .map(questId -> new InteractionHookTarget(questId, npcId, LifeProofQuest.resolveCurrentAction(chr, questId)))
            .orElse(null);
    }

    @Override
    public InteractionHookTarget resolveSelectionHook(Character chr, int npcId, int selection) {
        if (selection != MENU_SELECTION_ID) {
            return null;
        }
        return LifeProofQuest.resolveCurrentQuestId(chr)
            .map(questId -> new InteractionHookTarget(questId, npcId, LifeProofQuest.resolveCurrentAction(chr, questId)))
            .orElse(null);
    }

    @Override
    public boolean shouldFallbackNpcClick(Character chr, int npcId) {
        return LifeProofQuest.markProgressOnlyNpcTalk(chr, npcId);
    }

    @Override
    public void open(InteractionHookContext context) {
        LifeProofQuest.openHook(context);
    }

    @Override
    public void action(InteractionHookContext context, byte mode, byte lastMessage, int selection) {
        LifeProofQuest.handleHookAction(context, mode, lastMessage, selection);
    }
}

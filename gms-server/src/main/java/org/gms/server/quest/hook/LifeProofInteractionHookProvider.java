package org.gms.server.quest.hook;

import org.gms.client.Character;
import org.gms.server.hpchallenge.LifeProofQuest;

import java.util.ArrayList;
import java.util.List;

final class LifeProofInteractionHookProvider implements InteractionHookProvider {
    @Override
    public boolean supports(int questId, InteractionHookAction action) {
        return InteractionHookRegistry.isLifeProofQuest(questId);
    }

    @Override
    public List<InteractionHookRule> characterRules(Character chr) {
        List<InteractionHookRule> rules = new ArrayList<>();
        for (int questId : LifeProofQuest.getHookQuestIds(chr)) {
            int stateMask = InteractionHookPackets.questStateMask(chr, questId);
            rules.add(InteractionHookRegistry.questRule(questId, stateMask));
        }
        return rules;
    }

    @Override
    public InteractionHookTarget resolveNpcHook(Character chr, int npcId) {
        return null;
    }

    @Override
    public InteractionHookTarget resolveSelectionHook(Character chr, int npcId, int selection) {
        return null;
    }

    @Override
    public boolean shouldFallbackNpcClick(Character chr, int npcId) {
        return false;
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

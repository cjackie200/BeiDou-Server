package org.gms.server.quest.hook;

import org.gms.client.Character;
import org.gms.server.quest.ElementalResonanceQuest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ElementalResonanceInteractionHookProvider implements InteractionHookProvider {
    @Override
    public boolean supports(int questId, InteractionHookAction action) {
        return InteractionHookRegistry.isElementalResonanceQuest(questId);
    }

    @Override
    public List<InteractionHookRule> characterRules(Character chr) {
        List<InteractionHookRule> rules = new ArrayList<>();
        for (int questId : ElementalResonanceQuest.getHookQuestIds(chr)) {
            int stateMask = InteractionHookPackets.questStateMask(chr, questId);
            rules.add(InteractionHookRegistry.questRule(questId, stateMask));
        }
        return rules;
    }

    @Override
    public List<InteractionHookRule> mapNpcRules(Character chr, Set<Integer> mapNpcIds) {
        if (mapNpcIds == null || !mapNpcIds.contains(ElementalResonanceQuest.NPC_ID)) {
            return List.of();
        }
        int questId = ElementalResonanceQuest.resolveCurrentQuestId(chr).orElse(0);
        if (questId <= 0) {
            return List.of();
        }
        int stateMask = InteractionHookPackets.questStateMask(chr, questId);
        return List.of(InteractionHookRegistry.npcRule(ElementalResonanceQuest.NPC_ID, questId, stateMask));
    }

    @Override
    public InteractionHookTarget resolveNpcHook(Character chr, int npcId) {
        return ElementalResonanceQuest.resolveNpcHook(chr, npcId)
                .map(questId -> new InteractionHookTarget(
                        questId,
                        npcId,
                        ElementalResonanceQuest.resolveCurrentAction(chr, questId)))
                .orElse(null);
    }

    @Override
    public InteractionHookTarget resolveSelectionHook(Character chr, int npcId, int selection) {
        return null;
    }

    @Override
    public void open(InteractionHookContext context) {
        ElementalResonanceQuest.openHook(context);
    }

    @Override
    public void action(InteractionHookContext context, byte mode, byte lastMessage, int selection) {
        ElementalResonanceQuest.handleHookAction(context, mode, lastMessage, selection);
    }
}

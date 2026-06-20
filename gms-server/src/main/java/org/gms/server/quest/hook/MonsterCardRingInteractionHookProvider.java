package org.gms.server.quest.hook;

import org.gms.client.Character;
import org.gms.server.quest.MonsterCardRingQuest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class MonsterCardRingInteractionHookProvider implements InteractionHookProvider {
    @Override
    public boolean supports(int questId, InteractionHookAction action) {
        return InteractionHookRegistry.isMonsterCardRingQuest(questId);
    }

    @Override
    public List<InteractionHookRule> characterRules(Character chr) {
        List<InteractionHookRule> rules = new ArrayList<>();
        for (int questId : MonsterCardRingQuest.getHookQuestIds(chr)) {
            int stateMask = InteractionHookPackets.questStateMask(chr, questId);
            rules.add(InteractionHookRegistry.questRule(questId, stateMask));
        }
        return rules;
    }

    @Override
    public List<InteractionHookRule> mapNpcRules(Character chr, Set<Integer> mapNpcIds) {
        if (mapNpcIds == null || mapNpcIds.isEmpty()) {
            return List.of();
        }
        List<InteractionHookRule> rules = new ArrayList<>();
        for (int npcId : MonsterCardRingQuest.getHookNpcIds(chr)) {
            if (!mapNpcIds.contains(npcId)) {
                continue;
            }
            int questId = MonsterCardRingQuest.resolveCurrentQuestId(chr).orElse(0);
            int stateMask = questId > 0
                ? InteractionHookPackets.questStateMask(chr, questId)
                : InteractionHookProtocol.QUEST_STATE_MASK_ANY;
            rules.add(InteractionHookRegistry.npcRule(npcId, questId, stateMask));
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
        return null;
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

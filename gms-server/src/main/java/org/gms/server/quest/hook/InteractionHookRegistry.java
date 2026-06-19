package org.gms.server.quest.hook;

import org.gms.client.Character;
import org.gms.server.hpchallenge.LifeProofQuest;
import org.gms.server.quest.MonsterCardRingQuest;

import java.util.ArrayList;
import java.util.List;

public final class InteractionHookRegistry {
    private static final List<InteractionHookProvider> PROVIDERS = List.of(
        new LifeProofInteractionHookProvider(),
        new MonsterCardRingInteractionHookProvider()
    );

    private InteractionHookRegistry() {
    }

    public static List<InteractionHookRule> rules(Character chr) {
        List<InteractionHookRule> rules = new ArrayList<>();
        for (InteractionHookProvider provider : PROVIDERS) {
            rules.addAll(provider.rules(chr));
        }
        return rules;
    }

    public static InteractionHookProvider provider(int questId) {
        for (InteractionHookProvider provider : PROVIDERS) {
            if (provider.supports(questId, InteractionHookAction.QUERY_PROGRESS)) {
                return provider;
            }
        }
        return null;
    }

    public static boolean hasQuestHook(Character chr, int questId, InteractionHookAction action) {
        InteractionHookProvider provider = provider(questId);
        return provider != null && provider.supports(questId, action);
    }

    public static InteractionHookTarget resolveNpcHook(Character chr, int npcId) {
        for (InteractionHookProvider provider : PROVIDERS) {
            InteractionHookTarget target = provider.resolveNpcHook(chr, npcId);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    public static InteractionHookTarget resolveSelectionHook(Character chr, int npcId, int selection) {
        for (InteractionHookProvider provider : PROVIDERS) {
            InteractionHookTarget target = provider.resolveSelectionHook(chr, npcId, selection);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    static InteractionHookRule questRule(int questId, int questStateMask) {
        return new InteractionHookRule(
            InteractionHookProtocol.EVENT_MASK_QUEST_ACTION,
            InteractionHookProtocol.TARGET_QUEST,
            questId,
            questId,
            questStateMask,
            InteractionHookAction.ALL_MASK,
            InteractionHookProtocol.ANY_ID
        );
    }

    static InteractionHookRule npcRule(int npcId, int questId, int questStateMask) {
        return new InteractionHookRule(
            InteractionHookProtocol.EVENT_MASK_NPC_CLICK,
            InteractionHookProtocol.TARGET_NPC,
            npcId,
            questId,
            questStateMask,
            InteractionHookProtocol.ACTION_MASK_ANY,
            InteractionHookProtocol.ANY_ID
        );
    }

    static InteractionHookRule selectionRule(int selectionId, int questId, int questStateMask) {
        return new InteractionHookRule(
            InteractionHookProtocol.EVENT_MASK_NPC_DIALOG_SELECTION,
            InteractionHookProtocol.TARGET_DIALOG_SELECTION,
            selectionId,
            questId,
            questStateMask,
            InteractionHookProtocol.ACTION_MASK_ANY,
            selectionId
        );
    }

    static boolean isLifeProofQuest(int questId) {
        return LifeProofQuest.containsQuest(questId);
    }

    static boolean isMonsterCardRingQuest(int questId) {
        return MonsterCardRingQuest.isMonsterCardRingQuest(questId);
    }
}

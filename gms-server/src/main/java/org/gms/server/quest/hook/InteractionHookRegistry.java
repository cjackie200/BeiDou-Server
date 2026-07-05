package org.gms.server.quest.hook;

import org.gms.client.Character;
import org.gms.server.life.NPC;
import org.gms.server.maps.MapObject;
import org.gms.server.maps.MapObjectType;
import org.gms.server.hpchallenge.LifeProofQuest;
import org.gms.server.quest.ElementalResonanceQuest;
import org.gms.server.quest.MonsterCardRingQuest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InteractionHookRegistry {
    private static final List<InteractionHookProvider> PROVIDERS = List.of(
        new LifeProofInteractionHookProvider(),
        new MonsterCardRingInteractionHookProvider(),
        new ElementalResonanceInteractionHookProvider()
    );

    private InteractionHookRegistry() {
    }

    public static List<InteractionHookRule> characterRules(Character chr) {
        List<InteractionHookRule> rules = new ArrayList<>();
        for (InteractionHookProvider provider : PROVIDERS) {
            rules.addAll(provider.characterRules(chr));
        }
        return rules;
    }

    public static List<InteractionHookRule> mapNpcRules(Character chr) {
        List<InteractionHookRule> rules = new ArrayList<>();
        Set<Integer> mapNpcIds = currentMapNpcIds(chr);
        for (InteractionHookProvider provider : PROVIDERS) {
            rules.addAll(provider.mapNpcRules(chr, mapNpcIds));
        }
        return rules;
    }

    public static List<InteractionHookRule> rules(Character chr) {
        List<InteractionHookRule> rules = new ArrayList<>(characterRules(chr));
        rules.addAll(mapNpcRules(chr));
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

    static boolean isLifeProofQuest(int questId) {
        return LifeProofQuest.containsQuest(questId);
    }

    static boolean isMonsterCardRingQuest(int questId) {
        return MonsterCardRingQuest.isMonsterCardRingQuest(questId);
    }

    static boolean isElementalResonanceQuest(int questId) {
        return ElementalResonanceQuest.isQuestId(questId);
    }

    private static Set<Integer> currentMapNpcIds(Character chr) {
        if (chr == null || chr.getMap() == null) {
            return Set.of();
        }
        Set<Integer> npcIds = new HashSet<>();
        for (MapObject object : chr.getMap().getMapObjects()) {
            if (object.getType() == MapObjectType.NPC && object instanceof NPC npc) {
                npcIds.add(npc.getId());
            }
        }
        return npcIds;
    }
}

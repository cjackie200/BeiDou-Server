package org.gms.server.quest.hook;

import org.gms.client.Character;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface InteractionHookProvider {
    boolean supports(int questId, InteractionHookAction action);

    default List<InteractionHookRule> characterRules(Character chr) {
        return Collections.emptyList();
    }

    default List<InteractionHookRule> mapNpcRules(Character chr, Set<Integer> mapNpcIds) {
        return Collections.emptyList();
    }

    InteractionHookTarget resolveNpcHook(Character chr, int npcId);

    InteractionHookTarget resolveSelectionHook(Character chr, int npcId, int selection);

    default boolean shouldFallbackNpcClick(Character chr, int npcId) {
        return false;
    }

    void open(InteractionHookContext context);

    void action(InteractionHookContext context, byte mode, byte lastMessage, int selection);
}

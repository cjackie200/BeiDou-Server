package org.gms.server.quest.hook;

import org.gms.client.Character;

import java.util.List;

public interface InteractionHookProvider {
    boolean supports(int questId, InteractionHookAction action);

    List<InteractionHookRule> rules(Character chr);

    InteractionHookTarget resolveNpcHook(Character chr, int npcId);

    InteractionHookTarget resolveSelectionHook(Character chr, int npcId, int selection);

    default boolean shouldFallbackNpcClick(Character chr, int npcId) {
        return false;
    }

    void open(InteractionHookContext context);

    void action(InteractionHookContext context, byte mode, byte lastMessage, int selection);
}

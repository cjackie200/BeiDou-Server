package org.gms.server.quest.hook;

public record InteractionHookProgressEntry(
        int questId,
        int state,
        int current,
        int required,
        String text
) {
}

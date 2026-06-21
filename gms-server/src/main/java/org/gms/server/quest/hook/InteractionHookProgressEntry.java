package org.gms.server.quest.hook;

import java.util.List;

/**
 * A single quest progress entry sent via the INTERACTION_HOOK_PROGRESS packet (0x1004).
 * Supports multiple conditions per quest — each condition tracks its own current/required
 * progress and carries a formatted display text (may contain WZ macros like #b, #k, #i, #t).
 *
 * @param questId    the quest this entry describes
 * @param state      quest status (see {@link org.gms.client.QuestStatus.Status})
 * @param conditions zero or more progress conditions; the client renders them separated by \r\n
 */
public record InteractionHookProgressEntry(
        int questId,
        int state,
        java.util.List<Condition> conditions
) {

    public record Condition(
            int current,
            int required,
            String text
    ) {
        public Condition {
            if (text == null) {
                throw new IllegalArgumentException("condition text must not be null");
            }
        }
    }

    public InteractionHookProgressEntry {
        if (conditions == null) {
            conditions = List.of();
        }
    }
}

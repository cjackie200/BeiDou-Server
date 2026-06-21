package org.gms.server.quest.hook;

public final class InteractionHookProtocol {
    public static final int LEGACY_RULES_VERSION = 3;
    public static final int VERSION = 5; // multi-condition progress (Condition entries per quest)
    public static final int ANY_ID = -1;
    public static final int MAX_RULES_PER_PACKET = 100;

    public static final int SCOPE_ALL_RULES = 0;
    public static final int SCOPE_CHARACTER_QUEST_RULES = 1;
    public static final int SCOPE_MAP_NPC_RULES = 2;
    public static final int SCOPE_DIALOG_TEMP_RULES = 3;

    public static final int REPLACE_SCOPE = 1;
    public static final int CLEAR_SCOPE = 2;

    public static final int EVENT_NPC_CLICK = 1;
    public static final int EVENT_NPC_DIALOG_SELECTION = 2;
    public static final int EVENT_QUEST_ACTION = 3;

    public static final int EVENT_MASK_NPC_CLICK = 1;
    public static final int EVENT_MASK_NPC_DIALOG_SELECTION = 1 << 1;
    public static final int EVENT_MASK_QUEST_ACTION = 1 << 2;

    public static final int TARGET_ANY = 0;
    public static final int TARGET_NPC = 1;
    public static final int TARGET_QUEST = 2;
    public static final int TARGET_DIALOG_SELECTION = 3;

    public static final int QUEST_STATE_NONE = 0;
    public static final int QUEST_STATE_NOT_STARTED = 1;
    public static final int QUEST_STATE_STARTED = 2;
    public static final int QUEST_STATE_COMPLETED = 3;

    public static final int QUEST_STATE_MASK_ANY = 0;
    public static final int QUEST_STATE_MASK_NOT_STARTED = 1;
    public static final int QUEST_STATE_MASK_STARTED = 1 << 1;
    public static final int QUEST_STATE_MASK_COMPLETED = 1 << 2;

    public static final int ACTION_MASK_ANY = 0;

    public static final int DIALOG_CONTEXT_NONE = 0;
    public static final int DIALOG_CONTEXT_NPC = 1;
    public static final int DIALOG_CONTEXT_QUEST = 2;
    public static final int DIALOG_CONTEXT_INTERACTION_HOOK = 3;

    public static final int DIALOG_STATE_NONE = 0;
    public static final int DIALOG_STATE_OPEN = 1;
    public static final int DIALOG_STATE_WAIT_SELECTION = 2;
    public static final int DIALOG_STATE_WAIT_CONFIRM = 3;

    private InteractionHookProtocol() {
    }

    public static int eventMask(int eventType) {
        return switch (eventType) {
            case EVENT_NPC_CLICK -> EVENT_MASK_NPC_CLICK;
            case EVENT_NPC_DIALOG_SELECTION -> EVENT_MASK_NPC_DIALOG_SELECTION;
            case EVENT_QUEST_ACTION -> EVENT_MASK_QUEST_ACTION;
            default -> 0;
        };
    }
}

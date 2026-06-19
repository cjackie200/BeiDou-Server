package org.gms.server.quest.hook;

public enum InteractionHookAction {
    QUERY_START(1),
    CONFIRM_START(1 << 1),
    QUERY_PROGRESS(1 << 2),
    QUERY_COMPLETE(1 << 3),
    CONFIRM_COMPLETE(1 << 4);

    public static final int ALL_MASK = QUERY_START.mask
        | CONFIRM_START.mask
        | QUERY_PROGRESS.mask
        | QUERY_COMPLETE.mask
        | CONFIRM_COMPLETE.mask;

    private final int mask;

    InteractionHookAction(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }

    public static InteractionHookAction fromQuestRawAction(int action) {
        return switch (action) {
            case 1, 4 -> QUERY_START;
            case 2 -> QUERY_COMPLETE;
            case 5 -> QUERY_PROGRESS;
            default -> null;
        };
    }
}

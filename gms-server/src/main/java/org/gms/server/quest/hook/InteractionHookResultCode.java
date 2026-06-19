package org.gms.server.quest.hook;

public enum InteractionHookResultCode {
    HANDLED_DIALOG(0),
    HANDLED_UPDATE(1),
    FALLBACK_ORIGINAL(2),
    REJECTED(3),
    ERROR(4);

    private final int code;

    InteractionHookResultCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}

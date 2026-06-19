package org.gms.server.quest.hook;

import org.gms.net.packet.OutPacket;

public record InteractionHookRule(
    int eventMask,
    int targetType,
    int targetId,
    int questId,
    int questStateMask,
    int actionMask,
    int selectionId
) {
    public void writeTo(OutPacket packet) {
        packet.writeInt(eventMask);
        packet.writeInt(targetType);
        packet.writeInt(targetId);
        packet.writeInt(questId);
        packet.writeInt(questStateMask);
        packet.writeInt(actionMask);
        packet.writeInt(selectionId);
    }
}

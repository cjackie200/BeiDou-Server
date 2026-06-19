package org.gms.server.quest.hook;

import org.gms.net.packet.InPacket;
import org.gms.net.packet.OutPacket;

public record InteractionHookEvent(
    int requestId,
    int eventType,
    int targetType,
    int targetId,
    int objectId,
    int clientNpcId,
    int questId,
    int questState,
    int rawAction,
    int selection,
    int dialogContext,
    int dialogState,
    int reserved
) {
    public static final int INT_COUNT = 13;
    private static final int BYTE_COUNT = INT_COUNT * Integer.BYTES;

    public static InteractionHookEvent read(InPacket reader) {
        if (reader.available() < BYTE_COUNT) {
            return null;
        }
        return new InteractionHookEvent(
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt()
        );
    }

    public void writeTo(OutPacket writer) {
        writer.writeInt(requestId);
        writer.writeInt(eventType);
        writer.writeInt(targetType);
        writer.writeInt(targetId);
        writer.writeInt(objectId);
        writer.writeInt(clientNpcId);
        writer.writeInt(questId);
        writer.writeInt(questState);
        writer.writeInt(rawAction);
        writer.writeInt(selection);
        writer.writeInt(dialogContext);
        writer.writeInt(dialogState);
        writer.writeInt(reserved);
    }

    public InteractionHookAction questAction() {
        return InteractionHookAction.fromQuestRawAction(rawAction);
    }

    public int resolvedQuestId() {
        if (questId > 0) {
            return questId;
        }
        return targetType == InteractionHookProtocol.TARGET_QUEST ? targetId : 0;
    }

    public int resolvedNpcId() {
        if (clientNpcId > 0) {
            return clientNpcId;
        }
        return targetType == InteractionHookProtocol.TARGET_NPC ? targetId : 0;
    }
}

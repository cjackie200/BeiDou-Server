package org.gms.server.quest.hook;

import org.gms.client.Client;
import org.gms.client.QuestStatus;
import org.gms.net.opcodes.SendOpcode;
import org.gms.net.packet.OutPacket;

import java.util.List;

public final class InteractionHookPackets {
    public static final int C2S_INTERACTION_HOOK_EVENT = 0x1003;

    private InteractionHookPackets() {
    }

    public static void sendRules(Client client) {
        if (client == null || client.getPlayer() == null) {
            return;
        }
        List<InteractionHookRule> rules = InteractionHookRegistry.rules(client.getPlayer());
        OutPacket packet = OutPacket.create(SendOpcode.INTERACTION_HOOK_RULES);
        packet.writeShort(C2S_INTERACTION_HOOK_EVENT);
        packet.writeInt(InteractionHookProtocol.VERSION);
        packet.writeInt(rules.size());
        for (InteractionHookRule rule : rules) {
            rule.writeTo(packet);
        }
        client.sendPacket(packet);
    }

    public static void sendResult(Client client, int requestId, InteractionHookResultCode resultCode) {
        if (client == null) {
            return;
        }
        OutPacket packet = OutPacket.create(SendOpcode.INTERACTION_HOOK_RESULT);
        packet.writeInt(InteractionHookProtocol.VERSION);
        packet.writeInt(requestId);
        packet.writeInt(resultCode.code());
        client.sendPacket(packet);
    }

    static int questStateMask(org.gms.client.Character chr, int questId) {
        if (chr == null || questId <= 0) {
            return InteractionHookProtocol.QUEST_STATE_MASK_ANY;
        }
        byte status = chr.getQuestStatus(questId);
        if (status == QuestStatus.Status.STARTED.getId()) {
            return InteractionHookProtocol.QUEST_STATE_MASK_STARTED;
        }
        if (status == QuestStatus.Status.COMPLETED.getId()) {
            return InteractionHookProtocol.QUEST_STATE_MASK_COMPLETED;
        }
        return InteractionHookProtocol.QUEST_STATE_MASK_NOT_STARTED;
    }
}

package org.gms.server.quest.hook;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.constants.id.NpcId;
import org.gms.scripting.npc.NPCScriptManager;
import org.gms.scripting.quest.QuestScriptManager;
import org.gms.util.PacketCreator;

public final class InteractionHookContext {
    private final Client client;
    private final int requestId;
    private final int questId;
    private final int sourceNpcId;
    private final int displayNpcId;
    private final InteractionHookAction action;
    private int dialogState = InteractionHookProtocol.DIALOG_STATE_NONE;

    InteractionHookContext(Client client, int requestId, int questId, int sourceNpcId, InteractionHookAction action) {
        this.client = client;
        this.requestId = requestId;
        this.questId = questId;
        this.sourceNpcId = sourceNpcId;
        this.displayNpcId = sourceNpcId > 0 ? sourceNpcId : NpcId.MAPLE_ADMINISTRATOR;
        this.action = action;
    }

    public Client client() {
        return client;
    }

    public Character player() {
        return client.getPlayer();
    }

    public int requestId() {
        return requestId;
    }

    public int questId() {
        return questId;
    }

    public int sourceNpcId() {
        return sourceNpcId;
    }

    public InteractionHookAction action() {
        return action;
    }

    public int dialogState() {
        return dialogState;
    }

    public void sendOk(String text) {
        dialogState = InteractionHookProtocol.DIALOG_STATE_OPEN;
        client.sendPacket(PacketCreator.getNPCTalk(displayNpcId, (byte) 0, text, "00 00", (byte) 0));
    }

    public void sendYesNo(String text) {
        dialogState = InteractionHookProtocol.DIALOG_STATE_WAIT_CONFIRM;
        client.sendPacket(PacketCreator.getNPCTalk(displayNpcId, (byte) 1, text, "00 00", (byte) 0));
    }

    public void sendSimple(String text) {
        dialogState = InteractionHookProtocol.DIALOG_STATE_WAIT_SELECTION;
        client.sendPacket(PacketCreator.getNPCTalk(displayNpcId, (byte) 4, text, "00 00", (byte) 0));
    }

    public void close() {
        client.sendPacket(PacketCreator.enableActions());
        dispose();
    }

    public void dispose() {
        dialogState = InteractionHookProtocol.DIALOG_STATE_NONE;
        InteractionHookManager.dispose(client);
    }

    public void closeNativeScripts() {
        NPCScriptManager.getInstance().dispose(client);
        QuestScriptManager.getInstance().dispose(client);
    }
}

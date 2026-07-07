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
    private int questId;
    private int sourceNpcId;
    private int displayNpcId;
    private InteractionHookAction action;
    private final int eventType;
    private final int sourceDialogContext;
    private final int sourceDialogState;
    private final int rawAction;
    private int dialogState = InteractionHookProtocol.DIALOG_STATE_NONE;
    private boolean visibleDialogSent;
    private int selectedOption = -1;

    InteractionHookContext(Client client, int requestId, int questId, int sourceNpcId, InteractionHookAction action) {
        this(client, requestId, questId, sourceNpcId, action, null);
    }

    InteractionHookContext(Client client, int requestId, int questId, int sourceNpcId, InteractionHookAction action,
                           InteractionHookEvent event) {
        this.client = client;
        this.requestId = requestId;
        this.questId = questId;
        this.sourceNpcId = sourceNpcId;
        this.displayNpcId = sourceNpcId > 0 ? sourceNpcId : NpcId.MAPLE_ADMINISTRATOR;
        this.action = action;
        this.eventType = event == null ? 0 : event.eventType();
        this.sourceDialogContext = event == null ? InteractionHookProtocol.DIALOG_CONTEXT_NONE : event.dialogContext();
        this.sourceDialogState = event == null ? InteractionHookProtocol.DIALOG_STATE_NONE : event.dialogState();
        this.rawAction = event == null ? 0 : event.rawAction();
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

    public int displayNpcId() {
        return displayNpcId;
    }

    public InteractionHookAction action() {
        return action;
    }

    public int eventType() {
        return eventType;
    }

    public int sourceDialogContext() {
        return sourceDialogContext;
    }

    public int sourceDialogState() {
        return sourceDialogState;
    }

    public int rawAction() {
        return rawAction;
    }

    public int dialogState() {
        return dialogState;
    }

    public boolean hasVisibleDialogSent() {
        return visibleDialogSent;
    }

    public int selectedOption() {
        return selectedOption;
    }

    public void setSelectedOption(int selectedOption) {
        this.selectedOption = selectedOption;
    }

    public void resetVisibleDialogSent() {
        visibleDialogSent = false;
    }

    public void switchQuest(int questId, int sourceNpcId, InteractionHookAction action) {
        this.questId = questId;
        this.sourceNpcId = sourceNpcId;
        this.displayNpcId = sourceNpcId > 0 ? sourceNpcId : NpcId.MAPLE_ADMINISTRATOR;
        this.action = action;
    }

    public void sendOk(String text) {
        dialogState = InteractionHookProtocol.DIALOG_STATE_OPEN;
        visibleDialogSent = true;
        client.sendPacket(PacketCreator.getNPCTalk(displayNpcId, (byte) 0, text, "00 00", (byte) 0));
    }

    public void sendNext(String text) {
        dialogState = InteractionHookProtocol.DIALOG_STATE_NEXT;
        visibleDialogSent = true;
        client.sendPacket(PacketCreator.getNPCTalk(displayNpcId, (byte) 0, text, "00 01", (byte) 0));
    }

    public void sendYesNo(String text) {
        dialogState = InteractionHookProtocol.DIALOG_STATE_WAIT_CONFIRM;
        visibleDialogSent = true;
        client.sendPacket(PacketCreator.getNPCTalk(displayNpcId, (byte) 1, text, "", (byte) 0));
    }

    public void sendSimple(String text) {
        dialogState = InteractionHookProtocol.DIALOG_STATE_WAIT_SELECTION;
        visibleDialogSent = true;
        client.sendPacket(PacketCreator.getNPCTalk(displayNpcId, (byte) 4, text, "", (byte) 0));
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

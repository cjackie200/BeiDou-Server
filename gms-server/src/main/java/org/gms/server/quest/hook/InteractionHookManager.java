package org.gms.server.quest.hook;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.constants.id.NpcId;
import org.gms.net.packet.InPacket;
import org.gms.server.hpchallenge.LifeProofQuest;
import org.gms.server.life.NPC;
import org.gms.server.maps.MapObject;
import org.gms.server.maps.MapObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InteractionHookManager {
    private static final Logger log = LoggerFactory.getLogger(InteractionHookManager.class);
    private static final Map<Client, InteractionHookContext> CONTEXTS = new ConcurrentHashMap<>();

    private InteractionHookManager() {
    }

    public static boolean handlePacket(Client client, InPacket packet) {
        InteractionHookEvent event = InteractionHookEvent.read(packet);
        if (event == null) {
            sendResult(client, 0, InteractionHookResultCode.ERROR);
            return true;
        }
        log.info(
                "InteractionHook event player={} requestId={} eventType={} targetType={} targetId={} objectId={} npcId={} questId={} questState={} rawAction={} selection={} dialogContext={} dialogState={}",
                client.getPlayer() == null ? "?" : client.getPlayer().getName(),
                event.requestId(),
                event.eventType(),
                event.targetType(),
                event.targetId(),
                event.objectId(),
                event.clientNpcId(),
                event.questId(),
                event.questState(),
                event.rawAction(),
                event.selection(),
                event.dialogContext(),
                event.dialogState()
        );
        return handleEvent(client, event);
    }

    public static boolean handleNativeNpcClick(Client client, int objectId, int fallbackNpcId) {
        if (client == null || client.getPlayer() == null || client.consumeSkipNextNativeInteractionHook()) {
            return false;
        }

        int npcId = resolveServerNpcId(client, objectId, fallbackNpcId).orElse(0);
        if (npcId <= 0) {
            return false;
        }

        InteractionHookTarget target = InteractionHookRegistry.resolveNpcHook(client.getPlayer(), npcId);
        if (target == null) {
            return false;
        }
        return open(client, null, target.questId(), npcId, target.action(), false);
    }

    public static boolean handleNativeQuestAction(Client client, int questId, int npcId, int rawAction) {
        if (client == null || client.getPlayer() == null || client.consumeSkipNextNativeInteractionHook()) {
            return false;
        }

        InteractionHookAction action = InteractionHookAction.fromQuestRawAction(rawAction);
        if (LifeProofQuest.isVisibleQuestId(questId)
                && !isValidNativeLifeProofQuestAction(client.getPlayer(), questId, npcId, action)) {
            return false;
        }
        if (questId <= 0 || action == null || !InteractionHookRegistry.hasQuestHook(client.getPlayer(), questId, action)) {
            return false;
        }
        return open(client, null, questId, npcId, action, false);
    }

    public static boolean handleNativeDialogSelection(Client client, byte mode, byte lastMessage, int selection) {
        if (client == null || client.getPlayer() == null || client.consumeSkipNextNativeInteractionHook()) {
            return false;
        }

        InteractionHookContext context = CONTEXTS.get(client);
        if (context != null) {
            if (context.dialogState() == InteractionHookProtocol.DIALOG_STATE_OPEN) {
                context.close();
                return true;
            }
            if (context.dialogState() == InteractionHookProtocol.DIALOG_STATE_NEXT) {
                InteractionHookProvider nextProvider = InteractionHookRegistry.provider(context.questId());
                if (nextProvider == null) {
                    dispose(client);
                    return false;
                }
                try {
                    context.resetVisibleDialogSent();
                    nextProvider.open(context);
                } catch (RuntimeException e) {
                    log.error("原生交互 Hook Next 对话继续执行失败: questId={}, npcId={}, action={}",
                            context.questId(), context.sourceNpcId(), context.action(), e);
                    if (!context.hasVisibleDialogSent()) {
                        dispose(client);
                    }
                }
                return true;
            }
            InteractionHookProvider provider = InteractionHookRegistry.provider(context.questId());
            if (provider == null) {
                dispose(client);
                return false;
            }
            provider.action(context, mode, lastMessage, selection);
            return true;
        }

        int npcId = currentNativeNpcId(client);
        InteractionHookTarget target = InteractionHookRegistry.resolveSelectionHook(client.getPlayer(), npcId, selection);
        if (target == null) {
            return false;
        }
        return open(client, null, target.questId(), npcId, target.action(), false);
    }

    public static boolean hasContext(Client client) {
        return CONTEXTS.containsKey(client);
    }

    public static void dispose(Client client) {
        if (client == null) {
            return;
        }
        CONTEXTS.remove(client);
        InteractionHookPackets.sendCharacterQuestRules(client);
        InteractionHookPackets.sendProgress(client);
        InteractionHookPackets.clearDialogTempRules(client);
    }

    private static boolean handleEvent(Client client, InteractionHookEvent event) {
        if (client == null || client.getPlayer() == null) {
            return true;
        }

        return switch (event.eventType()) {
            case InteractionHookProtocol.EVENT_NPC_CLICK -> handleNpcClickEvent(client, event);
            case InteractionHookProtocol.EVENT_NPC_DIALOG_SELECTION -> handleDialogSelectionEvent(client, event);
            case InteractionHookProtocol.EVENT_QUEST_ACTION -> handleQuestActionEvent(client, event);
            default -> fallbackOriginal(client, event.requestId());
        };
    }

    private static boolean handleNpcClickEvent(Client client, InteractionHookEvent event) {
        Optional<Integer> serverNpcId = resolveServerNpcId(client, event.objectId(), event.resolvedNpcId());
        if (serverNpcId.isEmpty()) {
            return fallbackOriginal(client, event.requestId());
        }

        int npcId = serverNpcId.get();
        InteractionHookTarget target = InteractionHookRegistry.resolveNpcHook(client.getPlayer(), npcId);
        if (target == null) {
            return fallbackOriginal(client, event.requestId());
        }

        InteractionHookProvider provider = InteractionHookRegistry.provider(target.questId());
        if (provider != null && provider.shouldFallbackNpcClick(client.getPlayer(), npcId)) {
            return fallbackOriginal(client, event.requestId());
        }
        return open(client, event, target.questId(), npcId, target.action(), true);
    }

    private static boolean handleDialogSelectionEvent(Client client, InteractionHookEvent event) {
        InteractionHookContext context = CONTEXTS.get(client);
        if (context != null) {
            if (context.dialogState() == InteractionHookProtocol.DIALOG_STATE_OPEN) {
                context.close();
                sendHandledUpdate(client, event);
                return true;
            }
            if (context.dialogState() == InteractionHookProtocol.DIALOG_STATE_NEXT) {
                InteractionHookProvider nextProvider = InteractionHookRegistry.provider(context.questId());
                if (nextProvider == null) {
                    sendResult(client, event.requestId(), InteractionHookResultCode.ERROR);
                    dispose(client);
                    return true;
                }
                boolean nextPreAckSent = sendPreDialogResultIfNeeded(client, event, context, true);
                try {
                    context.resetVisibleDialogSent();
                    nextProvider.open(context);
                    sendPostDialogResultIfNeeded(client, event, context, true, nextPreAckSent);
                } catch (RuntimeException e) {
                    log.error("交互 Hook Next 对话继续执行失败: questId={}, npcId={}, action={}",
                            context.questId(), context.sourceNpcId(), context.action(), e);
                    if (!nextPreAckSent && !context.hasVisibleDialogSent()) {
                        sendResult(client, event.requestId(), InteractionHookResultCode.ERROR);
                    }
                }
                return true;
            }
            InteractionHookProvider provider = InteractionHookRegistry.provider(context.questId());
            if (provider == null) {
                sendResult(client, event.requestId(), InteractionHookResultCode.ERROR);
                dispose(client);
                return true;
            }
            boolean preAckSent = sendPreDialogResultIfNeeded(client, event, context, true);
            try {
                context.resetVisibleDialogSent();
                provider.action(context, (byte) event.rawAction(), (byte) 0, event.selection());
                sendPostDialogResultIfNeeded(client, event, context, true, preAckSent);
            } catch (RuntimeException e) {
                log.error("交互 Hook 对话继续执行失败: questId={}, npcId={}, action={}",
                        context.questId(), context.sourceNpcId(), context.action(), e);
                if (!preAckSent && !context.hasVisibleDialogSent()) {
                    sendResult(client, event.requestId(), InteractionHookResultCode.ERROR);
                }
            }
            return true;
        }

        int npcId = event.resolvedNpcId();
        if (npcId <= 0) {
            npcId = currentNativeNpcId(client);
        }
        InteractionHookTarget target = InteractionHookRegistry.resolveSelectionHook(client.getPlayer(), npcId, event.selection());
        if (target == null) {
            return fallbackOriginal(client, event.requestId());
        }
        return open(client, event, target.questId(), npcId, target.action(), true);
    }

    private static boolean handleQuestActionEvent(Client client, InteractionHookEvent event) {
        int questId = event.resolvedQuestId();
        InteractionHookAction action = event.questAction();
        if (LifeProofQuest.isVisibleQuestId(questId)) {
            return handleLifeProofQuestActionEvent(client, event, questId, action);
        }

        if (questId <= 0 || action == null || !InteractionHookRegistry.hasQuestHook(client.getPlayer(), questId, action)) {
            return fallbackOriginal(client, event.requestId());
        }

        int npcId = event.resolvedNpcId();
        return open(client, event, questId, npcId, action, true);
    }

    private static boolean handleLifeProofQuestActionEvent(Client client, InteractionHookEvent event, int questId,
                                                           InteractionHookAction action) {
        if (!isValidLifeProofQuestEvent(client.getPlayer(), event, questId, action)) {
            return reject(client, event.requestId());
        }

        int npcId = event.resolvedNpcId();
        return open(client, event, questId, npcId, action, true);
    }

    static boolean isValidLifeProofQuestEvent(Character chr, InteractionHookEvent event, int questId,
                                              InteractionHookAction action) {
        if (chr == null || questId <= 0 || !isLifeProofQuestEventSourceValid(event, action)) {
            return false;
        }
        if (!LifeProofQuest.resolveCurrentQuestId(chr).filter(current -> current == questId).isPresent()) {
            return false;
        }
        return LifeProofQuest.canOpenProgressAtNpc(questId, event.resolvedNpcId());
    }

    static boolean isLifeProofQuestEventSourceValid(InteractionHookEvent event, InteractionHookAction action) {
        if (event == null || action == null) {
            return false;
        }
        return event.eventType() == InteractionHookProtocol.EVENT_QUEST_ACTION
                && event.dialogContext() == InteractionHookProtocol.DIALOG_CONTEXT_QUEST;
    }

    static boolean isValidNativeLifeProofQuestAction(Character chr, int questId, int npcId,
                                                     InteractionHookAction action) {
        if (chr == null || questId <= 0 || action == null) {
            return false;
        }
        if (!LifeProofQuest.resolveCurrentQuestId(chr).filter(current -> current == questId).isPresent()) {
            return false;
        }
        return LifeProofQuest.canOpenProgressAtNpc(questId, npcId);
    }

    private static boolean open(Client client, InteractionHookEvent event, int questId, int npcId,
                                InteractionHookAction action, boolean sendResult) {
        InteractionHookProvider provider = InteractionHookRegistry.provider(questId);
        if (provider == null || !provider.supports(questId, action)) {
            if (sendResult) {
                sendResult(client, event == null ? 0 : event.requestId(), InteractionHookResultCode.FALLBACK_ORIGINAL);
            }
            return false;
        }

        int requestId = event == null ? 0 : event.requestId();
        InteractionHookContext context = new InteractionHookContext(client, requestId, questId, npcId, action, event);
        boolean preAckSent = false;
        try {
            context.closeNativeScripts();
            CONTEXTS.put(client, context);
            client.setClickedNPC();
            preAckSent = sendPreDialogResultIfNeeded(client, event, context, sendResult);
            context.resetVisibleDialogSent();
            provider.open(context);
            sendPostDialogResultIfNeeded(client, event, context, sendResult, preAckSent);
            return true;
        } catch (RuntimeException e) {
            CONTEXTS.remove(client);
            log.error("交互 Hook 执行失败: questId={}, npcId={}, action={}", questId, npcId, action, e);
            if (sendResult && !preAckSent && !context.hasVisibleDialogSent()) {
                sendResult(client, requestId, InteractionHookResultCode.ERROR);
            }
            return true;
        }
    }

    private static boolean fallbackOriginal(Client client, int requestId) {
        client.markSkipNextNativeInteractionHook();
        InteractionHookPackets.clearDialogTempRules(client);
        sendResult(client, requestId, InteractionHookResultCode.FALLBACK_ORIGINAL);
        return true;
    }

    private static boolean reject(Client client, int requestId) {
        InteractionHookPackets.clearDialogTempRules(client);
        sendResult(client, requestId, InteractionHookResultCode.REJECTED);
        return true;
    }

    private static boolean sendPreDialogResultIfNeeded(Client client, InteractionHookEvent event,
                                                       InteractionHookContext context, boolean sendResult) {
        if (!shouldSendResult(event, sendResult) || canUseNpcTalkAck(event, context)) {
            return false;
        }
        sendResult(client, event.requestId(), InteractionHookResultCode.HANDLED_UPDATE);
        return true;
    }

    private static void sendPostDialogResultIfNeeded(Client client, InteractionHookEvent event,
                                                     InteractionHookContext context, boolean sendResult,
                                                     boolean preAckSent) {
        if (!shouldSendResult(event, sendResult) || preAckSent || context.hasVisibleDialogSent()) {
            return;
        }
        sendResult(client, event.requestId(), InteractionHookResultCode.HANDLED_UPDATE);
    }

    private static void sendHandledUpdate(Client client, InteractionHookEvent event) {
        if (event == null || event.requestId() <= 0) {
            return;
        }
        sendResult(client, event.requestId(), InteractionHookResultCode.HANDLED_UPDATE);
    }

    private static boolean shouldSendResult(InteractionHookEvent event, boolean sendResult) {
        return sendResult && event != null && event.requestId() > 0;
    }

    static boolean canUseNpcTalkAck(InteractionHookEvent event, InteractionHookContext context) {
        if (event == null || context == null || event.requestId() <= 0) {
            return false;
        }
        if (LifeProofQuest.isVisibleQuestId(context.questId())) {
            return false;
        }
        int expectedNpcId = expectedDialogNpcId(event);
        return expectedNpcId > 0 && expectedNpcId == context.displayNpcId();
    }

    static int expectedDialogNpcId(InteractionHookEvent event) {
        if (event == null) {
            return 0;
        }
        return switch (event.eventType()) {
            case InteractionHookProtocol.EVENT_NPC_CLICK, InteractionHookProtocol.EVENT_NPC_DIALOG_SELECTION ->
                    event.resolvedNpcId() > 0 ? event.resolvedNpcId() : 0;
            case InteractionHookProtocol.EVENT_QUEST_ACTION ->
                    event.resolvedNpcId() > 0 ? event.resolvedNpcId() : NpcId.MAPLE_ADMINISTRATOR;
            default -> 0;
        };
    }

    private static void sendResult(Client client, int requestId, InteractionHookResultCode resultCode) {
        log.info("InteractionHook result player={} requestId={} result={}",
                client == null || client.getPlayer() == null ? "?" : client.getPlayer().getName(),
                requestId,
                resultCode);
        InteractionHookPackets.sendResult(client, requestId, resultCode);
    }

    private static Optional<Integer> resolveServerNpcId(Client client, int objectId, int fallbackNpcId) {
        if (client == null || client.getPlayer() == null || client.getPlayer().getMap() == null) {
            return Optional.empty();
        }

        if (objectId > 0) {
            MapObject object = client.getPlayer().getMap().getMapObject(objectId);
            if (object != null && object.getType() == MapObjectType.NPC && object instanceof NPC npc) {
                return Optional.of(npc.getId());
            }
        }
        if (fallbackNpcId > 0) {
            return Optional.of(fallbackNpcId);
        }
        return Optional.empty();
    }

    private static int currentNativeNpcId(Client client) {
        if (client.getCM() != null && client.getCM().getNpc() > 0) {
            return client.getCM().getNpc();
        }
        if (client.getQM() != null && client.getQM().getNpc() > 0) {
            return client.getQM().getNpc();
        }
        return 0;
    }
}

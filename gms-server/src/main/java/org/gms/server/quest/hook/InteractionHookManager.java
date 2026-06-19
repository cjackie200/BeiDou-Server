package org.gms.server.quest.hook;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.net.packet.InPacket;
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
                "InteractionHook event player={} requestId={} eventType={} targetType={} targetId={} objectId={} npcId={} questId={} questState={} rawAction={} selection={}",
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
                event.selection()
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
        return open(client, 0, target.questId(), npcId, target.action(), false);
    }

    public static boolean handleNativeQuestAction(Client client, int questId, int npcId, int rawAction) {
        if (client == null || client.getPlayer() == null || client.consumeSkipNextNativeInteractionHook()) {
            return false;
        }

        InteractionHookAction action = InteractionHookAction.fromQuestRawAction(rawAction);
        if (questId <= 0 || action == null || !InteractionHookRegistry.hasQuestHook(client.getPlayer(), questId, action)) {
            return false;
        }
        return open(client, 0, questId, npcId, action, false);
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
        return open(client, 0, target.questId(), npcId, target.action(), false);
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
        return open(client, event.requestId(), target.questId(), npcId, target.action(), true);
    }

    private static boolean handleDialogSelectionEvent(Client client, InteractionHookEvent event) {
        InteractionHookContext context = CONTEXTS.get(client);
        if (context != null) {
            if (context.dialogState() == InteractionHookProtocol.DIALOG_STATE_OPEN) {
                context.close();
                sendResult(client, event.requestId(), InteractionHookResultCode.HANDLED_DIALOG);
                return true;
            }
            InteractionHookProvider provider = InteractionHookRegistry.provider(context.questId());
            if (provider == null) {
                sendResult(client, event.requestId(), InteractionHookResultCode.ERROR);
                dispose(client);
                return true;
            }
            provider.action(context, (byte) event.rawAction(), (byte) 0, event.selection());
            sendResult(client, event.requestId(), InteractionHookResultCode.HANDLED_DIALOG);
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
        return open(client, event.requestId(), target.questId(), npcId, target.action(), true);
    }

    private static boolean handleQuestActionEvent(Client client, InteractionHookEvent event) {
        int questId = event.resolvedQuestId();
        InteractionHookAction action = event.questAction();
        if (questId <= 0 || action == null || !InteractionHookRegistry.hasQuestHook(client.getPlayer(), questId, action)) {
            return fallbackOriginal(client, event.requestId());
        }

        int npcId = event.resolvedNpcId();
        return open(client, event.requestId(), questId, npcId, action, true);
    }

    private static boolean open(Client client, int requestId, int questId, int npcId, InteractionHookAction action, boolean sendResult) {
        InteractionHookProvider provider = InteractionHookRegistry.provider(questId);
        if (provider == null || !provider.supports(questId, action)) {
            if (sendResult) {
                sendResult(client, requestId, InteractionHookResultCode.FALLBACK_ORIGINAL);
            }
            return false;
        }

        InteractionHookContext context = new InteractionHookContext(client, requestId, questId, npcId, action);
        try {
            context.closeNativeScripts();
            CONTEXTS.put(client, context);
            client.setClickedNPC();
            provider.open(context);
            if (sendResult) {
                sendResult(client, requestId, InteractionHookResultCode.HANDLED_DIALOG);
            }
            return true;
        } catch (RuntimeException e) {
            CONTEXTS.remove(client);
            log.error("交互 Hook 执行失败: questId={}, npcId={}, action={}", questId, npcId, action, e);
            if (sendResult) {
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

package org.gms.server.quest.hook;

import org.gms.client.Client;
import org.gms.client.QuestStatus;
import org.gms.net.opcodes.SendOpcode;
import org.gms.net.packet.OutPacket;
import org.gms.net.packet.Packet;
import org.gms.server.hpchallenge.LifeProofQuest;
import org.gms.server.quest.MonsterCardRingQuest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InteractionHookPackets {
    private static final Logger log = LoggerFactory.getLogger(InteractionHookPackets.class);
    public static final int C2S_INTERACTION_HOOK_EVENT = 0x1003;
    private static final int V4_RULE_PAYLOAD_HEADER_BYTES = 30;
    private static final int RULE_BYTES = 28;

    private InteractionHookPackets() {
    }

    public static void sendInitialRules(Client client) {
        if (!canSendRules(client)) {
            return;
        }
        clearAllRules(client);
        sendCharacterQuestRules(client);
        sendMapNpcRules(client);
        sendProgress(client);
    }

    public static void sendCharacterQuestRules(Client client) {
        if (!canSendRules(client)) {
            return;
        }
        sendRuleScope(client, InteractionHookProtocol.SCOPE_CHARACTER_QUEST_RULES,
                InteractionHookRegistry.characterRules(client.getPlayer()));
    }

    public static void sendMapNpcRules(Client client) {
        if (!canSendRules(client)) {
            return;
        }
        sendRuleScope(client, InteractionHookProtocol.SCOPE_MAP_NPC_RULES,
                InteractionHookRegistry.mapNpcRules(client.getPlayer()));
    }

    public static void clearDialogTempRules(Client client) {
        if (!canSendRules(client)) {
            return;
        }
        sendClearScope(client, InteractionHookProtocol.SCOPE_DIALOG_TEMP_RULES);
    }

    public static void clearAllRules(Client client) {
        if (!canSendRules(client)) {
            return;
        }
        sendClearScope(client, InteractionHookProtocol.SCOPE_ALL_RULES);
    }

    public static void sendProgress(Client client) {
        if (!canSendRules(client)) {
            return;
        }
        try {
            List<InteractionHookProgressEntry> entries = progressEntries(client.getPlayer());
            Packet packet = buildProgressPacket(entries);
            client.sendPacket(packet);
            log.info("InteractionHook progress sent player={} count={} payloadBytes={}",
                    client.getPlayer().getName(),
                    entries.size(),
                    Math.max(0, packet.getBytes().length - 2));
            for (InteractionHookProgressEntry entry : entries) {
                for (InteractionHookProgressEntry.Condition cond : entry.conditions()) {
                    log.info("  progress entry questId={} state={} current={} required={} text=[{}]",
                            entry.questId(), entry.state(), cond.current(), cond.required(),
                            cond.text() == null ? "<null>" : cond.text().replace("\r", "\\r").replace("\n", "\\n"));
                }
            }
        } catch (Exception e) {
            log.error("InteractionHook sendProgress failed player={}: {}",
                    client.getPlayer() != null ? client.getPlayer().getName() : "?",
                    e.getMessage(), e);
        }
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

    static List<Packet> buildRulePackets(int batchId, int scope, int replaceMode, List<InteractionHookRule> rules) {
        if (!isValidScope(scope) || !isValidReplaceMode(replaceMode) || batchId <= 0) {
            return Collections.emptyList();
        }
        List<InteractionHookRule> safeRules = rules == null ? List.of() : rules;
        if (replaceMode == InteractionHookProtocol.CLEAR_SCOPE) {
            return List.of(createRulePacket(scope, batchId, 0, 1, replaceMode, List.of()));
        }
        if (scope == InteractionHookProtocol.SCOPE_ALL_RULES) {
            return Collections.emptyList();
        }

        int batchCount = Math.max(1, (safeRules.size() + InteractionHookProtocol.MAX_RULES_PER_PACKET - 1)
                / InteractionHookProtocol.MAX_RULES_PER_PACKET);
        List<Packet> packets = new ArrayList<>(batchCount);
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int from = batchIndex * InteractionHookProtocol.MAX_RULES_PER_PACKET;
            int to = Math.min(safeRules.size(), from + InteractionHookProtocol.MAX_RULES_PER_PACKET);
            List<InteractionHookRule> batchRules = from >= to ? List.of() : safeRules.subList(from, to);
            packets.add(createRulePacket(scope, batchId, batchIndex, batchCount, replaceMode, batchRules));
        }
        return packets;
    }

    static Packet buildLegacyRulePacket(List<InteractionHookRule> rules) {
        List<InteractionHookRule> safeRules = rules == null ? List.of() : rules;
        OutPacket packet = OutPacket.create(SendOpcode.INTERACTION_HOOK_RULES);
        packet.writeShort(C2S_INTERACTION_HOOK_EVENT);
        packet.writeInt(InteractionHookProtocol.LEGACY_RULES_VERSION);
        packet.writeInt(safeRules.size());
        for (InteractionHookRule rule : safeRules) {
            rule.writeTo(packet);
        }
        return packet;
    }

    static Packet buildProgressPacket(List<InteractionHookProgressEntry> entries) {
        List<InteractionHookProgressEntry> safeEntries = entries == null ? List.of() : entries;
        OutPacket packet = OutPacket.create(SendOpcode.INTERACTION_HOOK_PROGRESS);
        packet.writeInt(InteractionHookProtocol.VERSION);
        packet.writeInt(safeEntries.size());
        for (InteractionHookProgressEntry entry : safeEntries) {
            packet.writeInt(entry.questId());
            packet.writeInt(entry.state());
            List<InteractionHookProgressEntry.Condition> conditions = entry.conditions();
            packet.writeInt(conditions.size());
            for (InteractionHookProgressEntry.Condition cond : conditions) {
                packet.writeInt(cond.current());
                packet.writeInt(cond.required());
                packet.writeString(cond.text() == null ? "" : cond.text());
            }
        }
        return packet;
    }

    static List<InteractionHookProgressEntry> progressEntries(org.gms.client.Character chr) {
        if (chr == null) {
            return List.of();
        }
        List<InteractionHookProgressEntry> entries = new ArrayList<>();
        entries.addAll(LifeProofQuest.progressEntries(chr));
        entries.addAll(MonsterCardRingQuest.progressEntries(chr));
        return entries;
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

    private static boolean canSendRules(Client client) {
        return client != null && client.getPlayer() != null;
    }

    private static void sendRuleScope(Client client, int scope, List<InteractionHookRule> rules) {
        int batchId = client.nextInteractionHookRuleBatchId();
        List<Packet> packets = buildRulePackets(batchId, scope, InteractionHookProtocol.REPLACE_SCOPE, rules);
        sendPackets(client, scope, batchId, InteractionHookProtocol.REPLACE_SCOPE, packets);
    }

    private static void sendClearScope(Client client, int scope) {
        int batchId = client.nextInteractionHookRuleBatchId();
        List<Packet> packets = buildRulePackets(batchId, scope, InteractionHookProtocol.CLEAR_SCOPE, List.of());
        sendPackets(client, scope, batchId, InteractionHookProtocol.CLEAR_SCOPE, packets);
    }

    private static void sendPackets(Client client, int scope, int batchId, int replaceMode, List<Packet> packets) {
        for (int i = 0; i < packets.size(); i++) {
            Packet packet = packets.get(i);
            client.sendPacket(packet);
            logRulePacket(client, scope, batchId, replaceMode, i, packets.size(), packet.getBytes().length);
        }
    }

    private static Packet createRulePacket(int scope, int batchId, int batchIndex, int batchCount, int replaceMode,
                                           List<InteractionHookRule> rules) {
        OutPacket packet = OutPacket.create(SendOpcode.INTERACTION_HOOK_RULES);
        packet.writeShort(C2S_INTERACTION_HOOK_EVENT);
        packet.writeInt(InteractionHookProtocol.VERSION);
        packet.writeInt(scope);
        packet.writeInt(batchId);
        packet.writeInt(batchIndex);
        packet.writeInt(batchCount);
        packet.writeInt(replaceMode);
        packet.writeInt(rules.size());
        for (InteractionHookRule rule : rules) {
            rule.writeTo(packet);
        }
        return packet;
    }

    private static void logRulePacket(Client client, int scope, int batchId, int replaceMode, int batchIndex,
                                      int batchCount, int packetBytes) {
        int payloadBytes = Math.max(0, packetBytes - 2);
        int ruleCount = Math.max(0, (payloadBytes - V4_RULE_PAYLOAD_HEADER_BYTES) / RULE_BYTES);
        log.info("InteractionHook rules sent player={} scope={} batchId={} batch={}/{} mode={} count={} payloadBytes={}",
                client.getPlayer().getName(),
                scopeName(scope),
                batchId,
                batchIndex + 1,
                batchCount,
                replaceModeName(replaceMode),
                ruleCount,
                payloadBytes);
    }

    private static boolean isValidScope(int scope) {
        return scope == InteractionHookProtocol.SCOPE_ALL_RULES
                || scope == InteractionHookProtocol.SCOPE_CHARACTER_QUEST_RULES
                || scope == InteractionHookProtocol.SCOPE_MAP_NPC_RULES
                || scope == InteractionHookProtocol.SCOPE_DIALOG_TEMP_RULES;
    }

    private static boolean isValidReplaceMode(int replaceMode) {
        return replaceMode == InteractionHookProtocol.REPLACE_SCOPE
                || replaceMode == InteractionHookProtocol.CLEAR_SCOPE;
    }

    private static String scopeName(int scope) {
        return switch (scope) {
            case InteractionHookProtocol.SCOPE_ALL_RULES -> "ALL_RULES";
            case InteractionHookProtocol.SCOPE_CHARACTER_QUEST_RULES -> "CHARACTER_QUEST_RULES";
            case InteractionHookProtocol.SCOPE_MAP_NPC_RULES -> "MAP_NPC_RULES";
            case InteractionHookProtocol.SCOPE_DIALOG_TEMP_RULES -> "DIALOG_TEMP_RULES";
            default -> "UNKNOWN";
        };
    }

    private static String replaceModeName(int replaceMode) {
        return switch (replaceMode) {
            case InteractionHookProtocol.REPLACE_SCOPE -> "REPLACE_SCOPE";
            case InteractionHookProtocol.CLEAR_SCOPE -> "CLEAR_SCOPE";
            default -> "UNKNOWN";
        };
    }
}

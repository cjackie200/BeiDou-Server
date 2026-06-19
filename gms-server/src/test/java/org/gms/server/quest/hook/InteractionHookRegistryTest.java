package org.gms.server.quest.hook;

import org.gms.server.hpchallenge.LifeProofQuest;
import org.gms.server.quest.MonsterCardRingQuest;
import org.gms.client.Client;
import org.gms.constants.id.NpcId;
import org.gms.net.packet.Packet;
import org.gms.util.PacketCreator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionHookRegistryTest {

    @Test
    void rulesIncludeLifeProofAndMonsterCardRingQuestActions() {
        Map<Integer, InteractionHookRule> questRules = InteractionHookRegistry.characterRules(null).stream()
                .filter(rule -> rule.eventMask() == InteractionHookProtocol.EVENT_MASK_QUEST_ACTION)
                .collect(Collectors.toMap(InteractionHookRule::questId, rule -> rule));

        assertFalse(questRules.containsKey(LifeProofQuest.FIRST_QUEST_ID));
        assertEquals(InteractionHookAction.ALL_MASK, questRules.get((int) MonsterCardRingQuest.CLAIM_QUEST_ID).actionMask());
        assertEquals(InteractionHookAction.ALL_MASK, questRules.get((int) MonsterCardRingQuest.LAST_QUEST_ID).actionMask());
        assertFalse(questRules.containsKey(1000));
    }

    @Test
    void mapNpcRulesUseExplicitNpcTargets() {
        assertNull(InteractionHookRegistry.resolveNpcHook(null, 1032001));

        MonsterCardRingInteractionHookProvider provider = new MonsterCardRingInteractionHookProvider();
        boolean monsterCardNpcRule = provider.mapNpcRules(null, Set.of(MonsterCardRingQuest.NPC_ID)).stream()
                .anyMatch(rule -> rule.eventMask() == InteractionHookProtocol.EVENT_MASK_NPC_CLICK
                        && rule.targetType() == InteractionHookProtocol.TARGET_NPC
                        && rule.targetId() == MonsterCardRingQuest.NPC_ID);
        assertTrue(monsterCardNpcRule);

        assertTrue(provider.mapNpcRules(null, Set.of(1032001)).isEmpty());
        assertTrue(InteractionHookRegistry.mapNpcRules(null).isEmpty());
    }

    @Test
    void lifeProofNpcClickFallbackDoesNotMutateProgress() {
        LifeProofInteractionHookProvider provider = new LifeProofInteractionHookProvider();

        assertFalse(provider.shouldFallbackNpcClick(null, 1012100));
        assertFalse(provider.shouldFallbackNpcClick(null, 1032001));
    }

    @Test
    void questRawActionsMapToStableHookActions() {
        assertEquals(InteractionHookAction.QUERY_START, InteractionHookAction.fromQuestRawAction(1));
        assertEquals(InteractionHookAction.QUERY_START, InteractionHookAction.fromQuestRawAction(4));
        assertEquals(InteractionHookAction.QUERY_COMPLETE, InteractionHookAction.fromQuestRawAction(2));
        assertEquals(InteractionHookAction.QUERY_PROGRESS, InteractionHookAction.fromQuestRawAction(5));
        assertNull(InteractionHookAction.fromQuestRawAction(0));
        assertTrue((InteractionHookAction.ALL_MASK & InteractionHookAction.QUERY_START.mask()) != 0);
        assertTrue((InteractionHookAction.ALL_MASK & InteractionHookAction.CONFIRM_COMPLETE.mask()) != 0);
    }

    @Test
    void v4RulePacketsUseStableHeaderAndBatching() {
        List<InteractionHookRule> rules = java.util.stream.IntStream.range(0, 250)
                .mapToObj(questId -> InteractionHookRegistry.questRule(5100 + questId,
                        InteractionHookProtocol.QUEST_STATE_MASK_STARTED))
                .toList();

        List<Packet> packets = InteractionHookPackets.buildRulePackets(7,
                InteractionHookProtocol.SCOPE_CHARACTER_QUEST_RULES,
                InteractionHookProtocol.REPLACE_SCOPE,
                rules);

        assertEquals(3, packets.size());
        assertRuleHeader(packets.getFirst(), InteractionHookProtocol.SCOPE_CHARACTER_QUEST_RULES, 7, 0, 3,
                InteractionHookProtocol.REPLACE_SCOPE, 100);
        assertRuleHeader(packets.get(1), InteractionHookProtocol.SCOPE_CHARACTER_QUEST_RULES, 7, 1, 3,
                InteractionHookProtocol.REPLACE_SCOPE, 100);
        assertRuleHeader(packets.get(2), InteractionHookProtocol.SCOPE_CHARACTER_QUEST_RULES, 7, 2, 3,
                InteractionHookProtocol.REPLACE_SCOPE, 50);
    }

    @Test
    void v4ClearAllPacketClearsWithoutRules() {
        List<Packet> packets = InteractionHookPackets.buildRulePackets(8,
                InteractionHookProtocol.SCOPE_ALL_RULES,
                InteractionHookProtocol.CLEAR_SCOPE,
                List.of(InteractionHookRegistry.questRule(5100, InteractionHookProtocol.QUEST_STATE_MASK_STARTED)));

        assertEquals(1, packets.size());
        assertRuleHeader(packets.getFirst(), InteractionHookProtocol.SCOPE_ALL_RULES, 8, 0, 1,
                InteractionHookProtocol.CLEAR_SCOPE, 0);
    }

    @Test
    void v4RejectsAllRulesReplaceAndBatchIdIsMonotonic() {
        assertTrue(InteractionHookPackets.buildRulePackets(9,
                InteractionHookProtocol.SCOPE_ALL_RULES,
                InteractionHookProtocol.REPLACE_SCOPE,
                List.of()).isEmpty());

        Client client = Client.createMock();
        int first = client.nextInteractionHookRuleBatchId();
        int second = client.nextInteractionHookRuleBatchId();
        assertTrue(second > first);
    }

    @Test
    void hookDialogPacketsMatchNativeNpcDialogLayout() {
        CapturingClient client = new CapturingClient();
        InteractionHookContext context = new InteractionHookContext(client, 1, LifeProofQuest.FIRST_QUEST_ID,
                1032001, InteractionHookAction.QUERY_PROGRESS);

        assertFalse(context.hasVisibleDialogSent());
        context.sendOk("ok");
        assertTrue(context.hasVisibleDialogSent());
        assertPacketEquals(PacketCreator.getNPCTalk(1032001, (byte) 0, "ok", "00 00", (byte) 0), client.lastPacket);

        context.resetVisibleDialogSent();
        assertFalse(context.hasVisibleDialogSent());
        context.sendYesNo("yes");
        assertTrue(context.hasVisibleDialogSent());
        assertPacketEquals(PacketCreator.getNPCTalk(1032001, (byte) 1, "yes", "", (byte) 0), client.lastPacket);

        context.resetVisibleDialogSent();
        context.sendSimple("simple");
        assertTrue(context.hasVisibleDialogSent());
        assertPacketEquals(PacketCreator.getNPCTalk(1032001, (byte) 4, "simple", "", (byte) 0), client.lastPacket);

        InteractionHookContext fallbackContext = new InteractionHookContext(client, 1, LifeProofQuest.FIRST_QUEST_ID,
                0, InteractionHookAction.QUERY_PROGRESS);
        assertEquals(NpcId.MAPLE_ADMINISTRATOR, fallbackContext.displayNpcId());
    }

    @Test
    void npcTalkAckUsesClientPredictableDialogNpcOnly() {
        CapturingClient client = new CapturingClient();
        InteractionHookContext instructorContext = new InteractionHookContext(client, 1, LifeProofQuest.FIRST_QUEST_ID,
                1032001, InteractionHookAction.QUERY_PROGRESS);
        InteractionHookContext fallbackContext = new InteractionHookContext(client, 1, LifeProofQuest.FIRST_QUEST_ID,
                0, InteractionHookAction.QUERY_PROGRESS);

        assertTrue(InteractionHookManager.canUseNpcTalkAck(
                event(1, InteractionHookProtocol.EVENT_QUEST_ACTION, InteractionHookProtocol.TARGET_QUEST,
                        LifeProofQuest.FIRST_QUEST_ID, 0, 1032001, LifeProofQuest.FIRST_QUEST_ID, -1),
                instructorContext));
        assertTrue(InteractionHookManager.canUseNpcTalkAck(
                event(2, InteractionHookProtocol.EVENT_QUEST_ACTION, InteractionHookProtocol.TARGET_QUEST,
                        LifeProofQuest.FIRST_QUEST_ID, 0, 0, LifeProofQuest.FIRST_QUEST_ID, -1),
                fallbackContext));
        assertTrue(InteractionHookManager.canUseNpcTalkAck(
                event(3, InteractionHookProtocol.EVENT_NPC_DIALOG_SELECTION, InteractionHookProtocol.TARGET_DIALOG_SELECTION,
                        0, 0, 1032001, 0, 0),
                instructorContext));
        assertFalse(InteractionHookManager.canUseNpcTalkAck(
                event(4, InteractionHookProtocol.EVENT_NPC_CLICK, InteractionHookProtocol.TARGET_NPC,
                        0, 100, 0, 0, -1),
                instructorContext));
        assertFalse(InteractionHookManager.canUseNpcTalkAck(
                event(5, InteractionHookProtocol.EVENT_NPC_DIALOG_SELECTION, InteractionHookProtocol.TARGET_DIALOG_SELECTION,
                        0, 0, 1032002, 0, 0),
                instructorContext));
    }

    @Test
    void lifeProofQuestEventsOnlyAcceptQuestDialogContext() {
        InteractionHookEvent valid = event(1, InteractionHookProtocol.EVENT_QUEST_ACTION,
                InteractionHookProtocol.TARGET_QUEST, LifeProofQuest.FIRST_QUEST_ID, 0, 1032001,
                LifeProofQuest.FIRST_QUEST_ID, -1, 5, InteractionHookProtocol.DIALOG_CONTEXT_QUEST);
        InteractionHookEvent npcDialog = event(2, InteractionHookProtocol.EVENT_QUEST_ACTION,
                InteractionHookProtocol.TARGET_QUEST, LifeProofQuest.FIRST_QUEST_ID, 0, 1032001,
                LifeProofQuest.FIRST_QUEST_ID, -1, 5, InteractionHookProtocol.DIALOG_CONTEXT_NPC);
        InteractionHookEvent noDialog = event(3, InteractionHookProtocol.EVENT_QUEST_ACTION,
                InteractionHookProtocol.TARGET_QUEST, LifeProofQuest.FIRST_QUEST_ID, 0, 1032001,
                LifeProofQuest.FIRST_QUEST_ID, -1, 5, InteractionHookProtocol.DIALOG_CONTEXT_NONE);
        InteractionHookEvent wrongEventType = event(4, InteractionHookProtocol.EVENT_NPC_DIALOG_SELECTION,
                InteractionHookProtocol.TARGET_DIALOG_SELECTION, 0, 0, 1032001,
                LifeProofQuest.FIRST_QUEST_ID, 0, 5, InteractionHookProtocol.DIALOG_CONTEXT_QUEST);

        assertTrue(InteractionHookManager.isLifeProofQuestEventSourceValid(valid, InteractionHookAction.QUERY_PROGRESS));
        assertFalse(InteractionHookManager.isLifeProofQuestEventSourceValid(npcDialog, InteractionHookAction.QUERY_PROGRESS));
        assertFalse(InteractionHookManager.isLifeProofQuestEventSourceValid(noDialog, InteractionHookAction.QUERY_PROGRESS));
        assertFalse(InteractionHookManager.isLifeProofQuestEventSourceValid(wrongEventType, InteractionHookAction.QUERY_PROGRESS));
        assertFalse(InteractionHookManager.isLifeProofQuestEventSourceValid(valid, null));
    }

    private static void assertRuleHeader(Packet packet, int scope, int batchId, int batchIndex, int batchCount,
                                         int replaceMode, int ruleCount) {
        byte[] bytes = packet.getBytes();
        assertEquals(0x1001, readU16(bytes, 0));
        assertEquals(InteractionHookPackets.C2S_INTERACTION_HOOK_EVENT, readU16(bytes, 2));
        assertEquals(InteractionHookProtocol.VERSION, readI32(bytes, 4));
        assertEquals(scope, readI32(bytes, 8));
        assertEquals(batchId, readI32(bytes, 12));
        assertEquals(batchIndex, readI32(bytes, 16));
        assertEquals(batchCount, readI32(bytes, 20));
        assertEquals(replaceMode, readI32(bytes, 24));
        assertEquals(ruleCount, readI32(bytes, 28));
        assertEquals(32 + ruleCount * 28, bytes.length);
    }

    private static int readU16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static int readI32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static void assertPacketEquals(Packet expected, Packet actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected.getBytes(), actual.getBytes());
    }

    private static InteractionHookEvent event(int requestId, int eventType, int targetType, int targetId, int objectId,
                                              int npcId, int questId, int selection) {
        return event(requestId, eventType, targetType, targetId, objectId, npcId, questId, selection, 0,
                InteractionHookProtocol.DIALOG_CONTEXT_NONE);
    }

    private static InteractionHookEvent event(int requestId, int eventType, int targetType, int targetId, int objectId,
                                              int npcId, int questId, int selection, int rawAction, int dialogContext) {
        return new InteractionHookEvent(requestId, eventType, targetType, targetId, objectId, npcId, questId,
                InteractionHookProtocol.QUEST_STATE_NONE, rawAction, selection,
                dialogContext, InteractionHookProtocol.DIALOG_STATE_NONE, 0);
    }

    private static final class CapturingClient extends Client {
        private Packet lastPacket;

        private CapturingClient() {
            super(null, -1, null, null, -123, -123);
        }

        @Override
        public void sendPacket(Packet packet) {
            lastPacket = packet;
        }
    }
}

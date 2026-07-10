package org.gms.server.quest.hook;

import org.gms.server.hpchallenge.LifeProofQuest;
import org.gms.server.quest.ElementalResonanceQuest;
import org.gms.server.quest.MonsterCardRingQuest;
import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Job;
import org.gms.client.MonsterBook;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.constants.id.NpcId;
import org.gms.net.packet.Packet;
import org.gms.property.ServiceProperty;
import org.gms.server.life.NPC;
import org.gms.server.life.NPCStats;
import org.gms.server.maps.MapleMap;
import org.gms.server.quest.Quest;
import org.gms.service.ConfigService;
import org.gms.util.PacketCreator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InteractionHookRegistryTest {

    @BeforeAll
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void setUpApplicationContext() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        ServiceProperty serviceProperty = new ServiceProperty();
        MessageSource messageSource = mock(MessageSource.class);
        ConfigService configService = mock(ConfigService.class);
        Map<Class<?>, Object> beans = new HashMap<>();

        when(configService.loadGameConfigs()).thenReturn(List.of());
        when(messageSource.getMessage(anyString(), any(Object[].class), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        beans.put(ServiceProperty.class, serviceProperty);
        beans.put(MessageSource.class, messageSource);
        beans.put(ConfigService.class, configService);

        doAnswer(invocation -> bean(beans, invocation.getArgument(0)))
                .when(context).getBean(any(Class.class));
        doAnswer(invocation -> bean(beans, invocation.getArgument(1)))
                .when(context).getBean(anyString(), any(Class.class));

        Field field = org.gms.manager.ServerManager.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, context);
    }

    @Test
    void rulesIncludeOnlyCharacterSpecificQuestActions() {
        Map<Integer, InteractionHookRule> questRules = InteractionHookRegistry.characterRules(null).stream()
                .filter(rule -> rule.eventMask() == InteractionHookProtocol.EVENT_MASK_QUEST_ACTION)
                .collect(Collectors.toMap(InteractionHookRule::questId, rule -> rule));

        assertFalse(questRules.containsKey(LifeProofQuest.FIRST_QUEST_ID));
        assertFalse(questRules.containsKey((int) MonsterCardRingQuest.CLAIM_QUEST_ID));
        assertFalse(questRules.containsKey((int) MonsterCardRingQuest.LAST_QUEST_ID));
        assertEquals(InteractionHookAction.ALL_MASK, questRules.get((int) ElementalResonanceQuest.FIRST_QUEST_ID).actionMask());
        assertEquals(InteractionHookAction.ALL_MASK, questRules.get((int) ElementalResonanceQuest.LAST_QUEST_ID).actionMask());
        assertFalse(questRules.containsKey(1000));
    }

    @Test
    void monsterCardRingRulesOnlyIncludeCurrentInteractiveQuest() {
        Character claimCharacter = newCharacter(Job.HERO, 0);
        assertEquals(List.of((int) MonsterCardRingQuest.CLAIM_QUEST_ID),
                MonsterCardRingQuest.getHookQuestIds(claimCharacter));

        Character upgradeCharacter = newCharacter(Job.HERO, 0);
        addRing(upgradeCharacter, 3);
        assertEquals(List.of((int) MonsterCardRingQuest.getUpgradeQuestId(4)),
                MonsterCardRingQuest.getHookQuestIds(upgradeCharacter));
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
    void elementalResonanceHookProgressShowsCurrentStep() {
        Character chr = newElementalMage(70);
        assertTrue(ElementalResonanceQuest.startStage(chr, 29950).success());

        ElementalResonanceInteractionHookProvider provider = new ElementalResonanceInteractionHookProvider();
        assertTrue(provider.mapNpcRules(chr, Set.of(ElementalResonanceQuest.NPC_ID)).stream()
                .anyMatch(rule -> rule.eventMask() == InteractionHookProtocol.EVENT_MASK_NPC_CLICK
                        && rule.targetId() == ElementalResonanceQuest.NPC_ID
                        && rule.questId() == 29950));

        InteractionHookProgressEntry entry = InteractionHookPackets.progressEntries(chr).stream()
                .filter(progress -> progress.questId() == 29950)
                .findFirst()
                .orElseThrow();

        assertEquals(QuestStatus.Status.STARTED.getId(), entry.state());
        assertTrue(entry.conditions().stream().anyMatch(condition ->
                condition.text().contains("#o2220000#")
                        && condition.text().contains("#i4033012#")
                        && condition.text().contains("#t4033012#")
                        && !condition.text().contains("目标：")));
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
    void progressPacketsUseStableLayout() {
        Packet packet = InteractionHookPackets.buildProgressPacket(List.of(
                new InteractionHookProgressEntry(5100, 1, List.of(
                        new InteractionHookProgressEntry.Condition(3, 16, "3/16")))));
        byte[] bytes = packet.getBytes();

        assertEquals(0x1004, readU16(bytes, 0));
        assertEquals(InteractionHookProtocol.VERSION, readI32(bytes, 2));
        assertEquals(1, readI32(bytes, 6));
        assertEquals(5100, readI32(bytes, 10));
        assertEquals(1, readI32(bytes, 14));
        assertEquals(1, readI32(bytes, 18)); // conditionCount
        assertEquals(3, readI32(bytes, 22));
        assertEquals(16, readI32(bytes, 26));
        assertEquals(4, readU16(bytes, 30));
        assertEquals('3', bytes[32]);
        assertEquals('/', bytes[33]);
        assertEquals('1', bytes[34]);
        assertEquals('6', bytes[35]);
        assertEquals(36, bytes.length);
    }

    @Test
    void clientRuntimeConfigPacketUsesStableLayout() {
        Packet enabled = InteractionHookPackets.buildClientRuntimeConfigPacket(true);
        byte[] enabledBytes = enabled.getBytes();

        assertEquals(0x1005, readU16(enabledBytes, 0));
        assertEquals(InteractionHookProtocol.VERSION, readI32(enabledBytes, 2));
        assertEquals(1, readI32(enabledBytes, 6));
        assertEquals(10, enabledBytes.length);

        Packet disabled = InteractionHookPackets.buildClientRuntimeConfigPacket(false);
        byte[] disabledBytes = disabled.getBytes();

        assertEquals(0x1005, readU16(disabledBytes, 0));
        assertEquals(InteractionHookProtocol.VERSION, readI32(disabledBytes, 2));
        assertEquals(0, readI32(disabledBytes, 6));
        assertEquals(10, disabledBytes.length);
    }

    @Test
    void progressEntriesMergeLifeProofAndMonsterCardRing() {
        Character chr = newCharacter(Job.HERO, 30);
        addRing(chr, 0);
        addItem(chr, MonsterCardRingQuest.getMaterialForLevel(1), MonsterCardRingQuest.getMaterialQty());

        List<InteractionHookProgressEntry> entries = InteractionHookPackets.progressEntries(chr);
        InteractionHookProgressEntry ringEntry = entries.stream()
                .filter(entry -> entry.questId() == 29981)
                .findFirst()
                .orElseThrow();

        // LifeProof entries are only included for STARTED/COMPLETED quests (BUG #5 fix).
        // MonsterCardRing entries are included for started upgrade quests.
        assertTrue(entries.size() >= 1, "should have at least the MonsterCardRing entry");
        assertTrue(ringEntry.questId() > 0);
        assertEquals(2, ringEntry.conditions().size());
        assertTrue(ringEntry.conditions().get(0).text().contains("怪物卡收集进度："));
        assertTrue(ringEntry.conditions().get(1).text().contains("材料收集进度："));
        assertFalse(ringEntry.conditions().get(0).text().contains("#"), ringEntry.conditions().get(0).text());
        assertFalse(ringEntry.conditions().get(1).text().contains("#"), ringEntry.conditions().get(1).text());
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
    void lifeProofNpcTalkCompletionShowsCurrentStepBeforeContinuation() throws Exception {
        Character chr = newLifeProofMage();
        CapturingClient client = (CapturingClient) chr.getClient();
        addNpc(chr, 1032001);
        putQuest(chr, 5129, QuestStatus.Status.STARTED, "000");

        InteractionHookContext context = new InteractionHookContext(client, 1, 5129,
                1032001, InteractionHookAction.QUERY_COMPLETE);

        LifeProofQuest.openHook(context);

        String prompt = LifeProofQuest.completePrompt(chr, 5129);
        assertTrue(prompt.contains("生命之证 I：拜访汉斯"), prompt);
        assertFalse(prompt.contains("是否接受这一步试炼？"), prompt);
        assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(5129));
        assertEquals(QuestStatus.Status.NOT_STARTED.getId(), chr.getQuestStatus(5130));
        assertPacketEquals(PacketCreator.getNPCTalk(1032001, (byte) 1, prompt, "", (byte) 0), client.lastPacket);
    }

    @Test
    void nativeNextDialogReopensSwitchedElementalRewardStep() {
        Character chr = newElementalMage(70);
        CapturingClient client = (CapturingClient) chr.getClient();
        InteractionHookManager.dispose(client);

        try {
            assertTrue(ElementalResonanceQuest.startStage(chr, 29950).success());
            addItem(chr, 4033012, 1);
            assertTrue(ElementalResonanceQuest.advanceCurrentStep(chr, 29950).success());

            assertTrue(ElementalResonanceQuest.startStage(chr, 29951).success());
            addItem(chr, 4033013, 1);
            assertTrue(ElementalResonanceQuest.advanceCurrentStep(chr, 29951).success());

            assertTrue(ElementalResonanceQuest.startStage(chr, 29952).success());
            addItem(chr, 4033014, 1);
            assertTrue(ElementalResonanceQuest.advanceCurrentStep(chr, 29952).success());

            assertTrue(ElementalResonanceQuest.startStage(chr, 29953).success());
            addItem(chr, 4000059, 100);
            addItem(chr, 4000060, 100);
            addItem(chr, 4000061, 100);
            addItem(chr, 4021009, 1);
            chr.setMeso(2_000_000);

            assertTrue(InteractionHookManager.handleNativeQuestAction(client, 29953,
                    ElementalResonanceQuest.NPC_ID, 2));
            assertTrue(InteractionHookManager.handleNativeDialogSelection(client, (byte) 1, (byte) 0, 0));
            assertEquals(QuestStatus.Status.COMPLETED.getId(), chr.getQuestStatus(29953));
            assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(29991));

            assertTrue(InteractionHookManager.handleNativeDialogSelection(client, (byte) 1, (byte) 0, 0));
            assertPacketEquals(PacketCreator.getNPCTalk(ElementalResonanceQuest.NPC_ID, (byte) 4,
                    ElementalResonanceQuest.rewardSelectionPrompt(chr, 29991), "", (byte) 0), client.lastPacket);
        } finally {
            InteractionHookManager.dispose(client);
        }
    }

    @Test
    void cancellingNativeNextDialogClosesSwitchedElementalRewardContext() {
        Character chr = newElementalMage(70);
        CapturingClient client = (CapturingClient) chr.getClient();
        InteractionHookManager.dispose(client);

        try {
            assertTrue(ElementalResonanceQuest.startStage(chr, 29950).success());
            addItem(chr, 4033012, 1);
            assertTrue(ElementalResonanceQuest.advanceCurrentStep(chr, 29950).success());
            assertTrue(ElementalResonanceQuest.startStage(chr, 29951).success());
            addItem(chr, 4033013, 1);
            assertTrue(ElementalResonanceQuest.advanceCurrentStep(chr, 29951).success());
            assertTrue(ElementalResonanceQuest.startStage(chr, 29952).success());
            addItem(chr, 4033014, 1);
            assertTrue(ElementalResonanceQuest.advanceCurrentStep(chr, 29952).success());
            assertTrue(ElementalResonanceQuest.startStage(chr, 29953).success());
            addItem(chr, 4000059, 100);
            addItem(chr, 4000060, 100);
            addItem(chr, 4000061, 100);
            addItem(chr, 4021009, 1);
            chr.setMeso(2_000_000);

            assertTrue(InteractionHookManager.handleNativeQuestAction(client, 29953,
                    ElementalResonanceQuest.NPC_ID, 2));
            assertTrue(InteractionHookManager.handleNativeDialogSelection(client, (byte) 1, (byte) 0, 0));
            assertTrue(InteractionHookManager.hasContext(client));

            assertTrue(InteractionHookManager.handleNativeDialogSelection(client, (byte) -1, (byte) 0, 0));
            assertFalse(InteractionHookManager.hasContext(client));
        } finally {
            InteractionHookManager.dispose(client);
        }
    }

    @Test
    void npcTalkAckUsesClientPredictableDialogNpcOnly() {
        CapturingClient client = new CapturingClient();
        InteractionHookContext instructorContext = new InteractionHookContext(client, 1, LifeProofQuest.FIRST_QUEST_ID,
                1032001, InteractionHookAction.QUERY_PROGRESS);
        InteractionHookContext fallbackContext = new InteractionHookContext(client, 1, LifeProofQuest.FIRST_QUEST_ID,
                0, InteractionHookAction.QUERY_PROGRESS);
        InteractionHookContext monsterCardContext = new InteractionHookContext(client, 1,
                MonsterCardRingQuest.CLAIM_QUEST_ID, MonsterCardRingQuest.NPC_ID,
                InteractionHookAction.QUERY_PROGRESS);

        assertFalse(InteractionHookManager.canUseNpcTalkAck(
                event(1, InteractionHookProtocol.EVENT_QUEST_ACTION, InteractionHookProtocol.TARGET_QUEST,
                        LifeProofQuest.FIRST_QUEST_ID, 0, 1032001, LifeProofQuest.FIRST_QUEST_ID, -1),
                instructorContext));
        assertFalse(InteractionHookManager.canUseNpcTalkAck(
                event(2, InteractionHookProtocol.EVENT_QUEST_ACTION, InteractionHookProtocol.TARGET_QUEST,
                        LifeProofQuest.FIRST_QUEST_ID, 0, 0, LifeProofQuest.FIRST_QUEST_ID, -1),
                fallbackContext));
        assertFalse(InteractionHookManager.canUseNpcTalkAck(
                event(3, InteractionHookProtocol.EVENT_NPC_DIALOG_SELECTION, InteractionHookProtocol.TARGET_DIALOG_SELECTION,
                        0, 0, 1032001, 0, 0),
                instructorContext));
        assertTrue(InteractionHookManager.canUseNpcTalkAck(
                event(6, InteractionHookProtocol.EVENT_NPC_DIALOG_SELECTION, InteractionHookProtocol.TARGET_DIALOG_SELECTION,
                        0, 0, MonsterCardRingQuest.NPC_ID, 0, 0),
                monsterCardContext));
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

    @Test
    void fallbackReplayTokenOnlyBypassesTheMatchingNativeInteraction() {
        CapturingClient client = new CapturingClient();
        InteractionHookEvent questEvent = event(1, InteractionHookProtocol.EVENT_QUEST_ACTION,
                InteractionHookProtocol.TARGET_QUEST, 29953, 0, 9010000,
                29953, -1, 5, InteractionHookProtocol.DIALOG_CONTEXT_QUEST);
        InteractionHookManager.rememberFallbackReplay(client, questEvent);

        assertFalse(InteractionHookManager.consumeFallbackQuestReplay(client, 29952, 9010000, 5));
        assertFalse(InteractionHookManager.consumeFallbackQuestReplay(client, 29953, 9010001, 5));
        assertTrue(InteractionHookManager.consumeFallbackQuestReplay(client, 29953, 9010000, 5));
        assertFalse(InteractionHookManager.consumeFallbackQuestReplay(client, 29953, 9010000, 5));

        InteractionHookEvent dialogEvent = event(2, InteractionHookProtocol.EVENT_NPC_DIALOG_SELECTION,
                InteractionHookProtocol.TARGET_DIALOG_SELECTION, 7, 0, 9010000,
                0, 7, 0xFF, InteractionHookProtocol.DIALOG_CONTEXT_INTERACTION_HOOK);
        InteractionHookManager.rememberFallbackReplay(client, dialogEvent);
        assertTrue(InteractionHookManager.consumeFallbackDialogReplay(client, (byte) -1, 7));
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

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS));
    }

    private static Character newCharacter(Job job, int completedSets) {
        Client client = Client.createMock();
        Character chr = Character.getDefault(client);
        client.setPlayer(chr);
        chr.setJob(job);
        chr.setMonsterBook(monsterBookWithCompletedSets(completedSets));
        return chr;
    }

    private static Character newElementalMage(int level) {
        CapturingClient client = new CapturingClient();
        Character chr = Character.getDefault(client);
        client.setPlayer(chr);
        chr.setLevel(level);
        chr.setJob(Job.FP_WIZARD);
        return chr;
    }

    private static Character newLifeProofMage() {
        CapturingClient client = new CapturingClient();
        Character chr = Character.getDefault(client);
        client.setPlayer(chr);
        chr.setId(10001);
        chr.setLevel(180);
        chr.setJob(Job.FP_ARCHMAGE);
        return chr;
    }

    private static void addNpc(Character chr, int npcId) {
        if (chr.getMap() == null) {
            MapleMap map = new MapleMap(100000000, 0, 1, 100000000, 1.0f);
            chr.setMap(map);
            chr.setMap(100000000);
        }
        chr.getMap().addMapObject(new NPC(npcId, new NPCStats("test-npc-" + npcId)));
    }

    private static void putQuest(Character chr, int questId, QuestStatus.Status status, String progress) {
        QuestStatus questStatus = new QuestStatus(Quest.getInstance(questId), status, 1032001);
        if (progress != null) {
            questStatus.setProgress(0, progress);
        }
        chr.getQuests().put((short) questId, questStatus);
    }

    private static MonsterBook monsterBookWithCompletedSets(int completedSets) {
        MonsterBook monsterBook = mock(MonsterBook.class);
        Map<Integer, Integer> cards = new LinkedHashMap<>();
        for (int i = 0; i < completedSets; i++) {
            cards.put(100000 + i, 5);
        }
        when(monsterBook.getCardSet()).thenReturn(new LinkedHashSet<>(cards.entrySet()));
        return monsterBook;
    }

    private static short addRing(Character chr, int level) {
        return addItem(chr, MonsterCardRingQuest.BASE_RING + level, 1);
    }

    private static short addItem(Character chr, int itemId, int quantity) {
        InventoryType type = org.gms.constants.inventory.ItemConstants.getInventoryType(itemId);
        return chr.getInventory(type).addItem(new Item(itemId, (short) 0, (short) quantity));
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

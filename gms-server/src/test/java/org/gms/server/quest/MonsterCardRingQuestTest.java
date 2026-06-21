package org.gms.server.quest;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.MonsterBook;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.net.packet.Packet;
import org.gms.property.ServiceProperty;
import org.gms.server.quest.hook.InteractionHookProgressEntry;
import org.gms.service.ConfigService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonsterCardRingQuestTest {
    private static final int PROGRESS_KEY = 0;

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
    void syncQuestStateUsesHeldRingLevelAsQuestStep() {
        Character noRing = newCharacter(0);
        MonsterCardRingQuest.syncQuestStateSilently(noRing);
        assertQuest(noRing, 29980, QuestStatus.Status.NOT_STARTED);
        assertQuest(noRing, 29981, QuestStatus.Status.NOT_STARTED);
        assertEquals(29980, MonsterCardRingQuest.resolveCurrentQuestId(noRing).orElseThrow());

        Character lv0 = newCharacter(0);
        addRing(lv0, 0);
        MonsterCardRingQuest.syncQuestStateSilently(lv0);
        assertQuest(lv0, 29980, QuestStatus.Status.COMPLETED);
        assertStartedProgress(lv0, 29981, "000");
        assertQuest(lv0, 29982, QuestStatus.Status.NOT_STARTED);

        Character lv5 = newCharacter(0);
        addRing(lv5, 5);
        MonsterCardRingQuest.syncQuestStateSilently(lv5);
        for (int questId = 29980; questId <= 29985; questId++) {
            assertQuest(lv5, questId, QuestStatus.Status.COMPLETED);
        }
        assertStartedProgress(lv5, 29986, "000");
        assertQuest(lv5, 29987, QuestStatus.Status.NOT_STARTED);

        Character lv10 = newCharacter(0);
        addRing(lv10, 10);
        MonsterCardRingQuest.syncQuestStateSilently(lv10);
        for (int questId = 29980; questId <= 29990; questId++) {
            assertQuest(lv10, questId, QuestStatus.Status.COMPLETED);
        }
        assertTrue(MonsterCardRingQuest.resolveCurrentQuestId(lv10).isEmpty());
    }

    @Test
    void discardingHighestRingBacktracksToRemainingRing() {
        Character chr = newCharacter(0);
        short lv2Slot = addRing(chr, 2);
        short lv5Slot = addRing(chr, 5);
        MonsterCardRingQuest.syncQuestStateSilently(chr);
        assertStartedProgress(chr, 29986, "000");

        chr.getInventory(InventoryType.EQUIP).removeItem(lv5Slot);
        MonsterCardRingQuest.syncQuestStateSilently(chr);
        assertQuest(chr, 29982, QuestStatus.Status.COMPLETED);
        assertStartedProgress(chr, 29983, "000");
        assertQuest(chr, 29984, QuestStatus.Status.NOT_STARTED);

        chr.getInventory(InventoryType.EQUIP).removeItem(lv2Slot);
        MonsterCardRingQuest.syncQuestStateSilently(chr);
        assertQuest(chr, 29980, QuestStatus.Status.NOT_STARTED);
        assertQuest(chr, 29981, QuestStatus.Status.NOT_STARTED);
        assertEquals(29980, MonsterCardRingQuest.resolveCurrentQuestId(chr).orElseThrow());
    }

    @Test
    void baseRingClaimClearsOldUpgradeCompletionState() {
        Character chr = newCharacter(0);
        putQuest(chr, 29981, QuestStatus.Status.COMPLETED, "001");
        putQuest(chr, 29990, QuestStatus.Status.COMPLETED, "001");
        addRing(chr, 0);

        MonsterCardRingQuest.onBaseRingClaimed(chr);

        assertQuest(chr, 29980, QuestStatus.Status.COMPLETED);
        assertStartedProgress(chr, 29981, "000");
        assertQuest(chr, 29982, QuestStatus.Status.NOT_STARTED);
        assertEquals("", chr.getQuest(Quest.getInstance(29990)).getProgress(PROGRESS_KEY));
    }

    @Test
    void currentUpgradeStepUsesVirtualInfoExProgress() {
        Character chr = newCharacter(29);
        addRing(chr, 0);
        addItem(chr, MonsterCardRingQuest.getMaterialForLevel(1), MonsterCardRingQuest.getMaterialQty());
        MonsterCardRingQuest.syncQuestStateSilently(chr);
        assertStartedProgress(chr, 29981, "000");

        chr.setMonsterBook(monsterBookWithCompletedSets(30));
        MonsterCardRingQuest.syncQuestStateSilently(chr);
        assertStartedProgress(chr, 29981, "001");
    }

    @Test
    void syncQuestStateRefreshesProgressPacketEvenWhenNotReadyValueDoesNotChange() {
        Character chr = newCharacter(0);
        CapturingClient client = (CapturingClient) chr.getClient();
        addRing(chr, 0);
        MonsterCardRingQuest.syncQuestState(chr);
        int before = client.progressPackets;

        addItem(chr, MonsterCardRingQuest.getMaterialForLevel(1), 1);
        MonsterCardRingQuest.syncQuestState(chr);

        assertStartedProgress(chr, 29981, "000");
        assertTrue(client.progressPackets > before);
    }

    @Test
    void progressEntryExplainsMissingMaterial() {
        Character chr = newCharacter(30);
        addRing(chr, 0);

        var conditions = MonsterCardRingQuest.progressEntry(chr).orElseThrow().conditions();
        assertEquals(2, conditions.size());
        assertTrue(conditions.get(0).text().contains("#b30#k/30 套"));
        assertTrue(conditions.get(1).text().contains("#i4021000# #t4021000# #b0#k/10"));
        assertQProgressTextSafe(conditions.get(0).text());
    }

    @Test
    void progressEntriesIncludeCurrentStartedQuestFromRingLevel() {
        Character chr = newCharacter(60);
        addRing(chr, 1);
        addItem(chr, MonsterCardRingQuest.getMaterialForLevel(2), MonsterCardRingQuest.getMaterialQty());

        List<InteractionHookProgressEntry> entries = MonsterCardRingQuest.progressEntries(chr);

        assertEquals(1, entries.size());
        InteractionHookProgressEntry entry = entries.getFirst();
        assertEquals(29982, entry.questId());
        assertEquals(QuestStatus.Status.STARTED.getId(), entry.state());
        assertEquals(2, entry.conditions().size());
        assertEquals(60, entry.conditions().get(0).current());
        assertEquals(60, entry.conditions().get(0).required());
        assertEquals(10, entry.conditions().get(1).current());
        assertEquals(10, entry.conditions().get(1).required());
        assertTrue(entry.conditions().get(0).text().contains("#b60#k/60 套"));
        assertTrue(entry.conditions().get(1).text().contains("#i4021001# #t4021001# #b10#k/10"));
    }

    @Test
    void progressEntriesNormalizeStaleQuestStateBeforeBuildingEntries() {
        Character chr = newCharacter(60);
        addRing(chr, 1);
        addItem(chr, MonsterCardRingQuest.getMaterialForLevel(2), MonsterCardRingQuest.getMaterialQty());
        putQuest(chr, 29981, QuestStatus.Status.STARTED, "001");
        putQuest(chr, 29982, QuestStatus.Status.NOT_STARTED, null);

        List<InteractionHookProgressEntry> entries = MonsterCardRingQuest.progressEntries(chr);

        assertQuest(chr, 29981, QuestStatus.Status.COMPLETED);
        assertStartedProgress(chr, 29982, "001");
        assertEquals(1, entries.size());
        assertEquals(29982, entries.getFirst().questId());
        assertEquals(2, entries.getFirst().conditions().size());
        assertTrue(entries.getFirst().conditions().get(1).text().contains("#t4021001#"));
    }

    @Test
    void progressEntryExplainsMissingCardSets() {
        Character chr = newCharacter(29);
        addRing(chr, 0);
        addItem(chr, MonsterCardRingQuest.getMaterialForLevel(1), MonsterCardRingQuest.getMaterialQty());

        var conditions = MonsterCardRingQuest.progressEntry(chr).orElseThrow().conditions();
        assertEquals(2, conditions.size());
        assertTrue(conditions.get(0).text().contains("#b29#k/30 套"));
        assertTrue(conditions.get(1).text().contains("#i4021000# #t4021000# #b10#k/10"));
    }

    @Test
    void progressEntryExplainsEquippedRing() {
        Character chr = newCharacter(30);
        equipRing(chr, 1);
        addItem(chr, MonsterCardRingQuest.getMaterialForLevel(2), MonsterCardRingQuest.getMaterialQty());

        var conditions = MonsterCardRingQuest.progressEntry(chr).orElseThrow().conditions();
        assertEquals(2, conditions.size());
        assertTrue(conditions.get(0).text().contains("#b30#k/60 套"));
        assertTrue(conditions.get(1).text().contains("#i4021001# #t4021001# #b10#k/10"));
    }

    @Test
    void progressEntryShowsReadyState() {
        Character chr = newCharacter(30);
        addRing(chr, 0);
        addItem(chr, MonsterCardRingQuest.getMaterialForLevel(1), MonsterCardRingQuest.getMaterialQty());

        var conditions = MonsterCardRingQuest.progressEntry(chr).orElseThrow().conditions();
        assertEquals(2, conditions.size());
        assertTrue(conditions.get(0).text().contains("#b30#k/30 套"));
        assertTrue(conditions.get(1).text().contains("#i4021000# #t4021000# #b10#k/10"));
    }

    @Test
    void npcProgressTextKeepsWzMacrosForDialog() {
        Character chr = newCharacter(30);
        addRing(chr, 0);

        String text = MonsterCardRingQuest.progressText(chr);

        assertTrue(text.contains("#i4021000#"));
        assertTrue(text.contains("#t4021000#"));
        assertTrue(text.contains("#b"));
        assertTrue(text.contains("#k"));
    }

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS));
    }

    private static Character newCharacter(int completedSets) {
        CapturingClient client = new CapturingClient();
        Character chr = Character.getDefault(client);
        client.setPlayer(chr);
        chr.setMonsterBook(monsterBookWithCompletedSets(completedSets));
        return chr;
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

    private static short equipRing(Character chr, int level) {
        return addItem(chr, InventoryType.EQUIPPED, MonsterCardRingQuest.BASE_RING + level, 1);
    }

    private static short addItem(Character chr, int itemId, int quantity) {
        InventoryType type = org.gms.constants.inventory.ItemConstants.getInventoryType(itemId);
        return addItem(chr, type, itemId, quantity);
    }

    private static short addItem(Character chr, InventoryType type, int itemId, int quantity) {
        return chr.getInventory(type).addItem(new Item(itemId, (short) 0, (short) quantity));
    }

    private static void assertQProgressTextSafe(String text) {
        // #b, #k, #i, #t, #r, #e, #n macros are now allowed for old-style formatting.
        // Only reject unresolved markers and placeholder text.
        assertFalse(text.contains("@@"), text);
        assertFalse(text.contains("..."), text);
        assertFalse(text.contains("进度正在同步"), text);
    }

    private static int readU16(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]) | Byte.toUnsignedInt(bytes[offset + 1]) << 8;
    }

    private static void putQuest(Character chr, int questId, QuestStatus.Status status, String progress) {
        QuestStatus questStatus = new QuestStatus(Quest.getInstance(questId), status, MonsterCardRingQuest.NPC_ID);
        if (progress != null) {
            questStatus.setProgress(PROGRESS_KEY, progress);
        }
        chr.getQuests().put((short) questId, questStatus);
    }

    private static void assertQuest(Character chr, int questId, QuestStatus.Status status) {
        assertEquals(status.getId(), chr.getQuestStatus(questId), "quest " + questId);
    }

    private static void assertStartedProgress(Character chr, int questId, String progress) {
        assertQuest(chr, questId, QuestStatus.Status.STARTED);
        assertEquals(progress, chr.getQuest(Quest.getInstance(questId)).getProgress(PROGRESS_KEY), "quest " + questId);
    }

    private static final class CapturingClient extends Client {
        private int progressPackets;

        private CapturingClient() {
            super(null, -1, null, null, -123, -123);
        }

        @Override
        public void sendPacket(Packet packet) {
            if (packet == null || packet.getBytes().length < 2) {
                return;
            }
            if (readU16(packet.getBytes(), 0) == 0x1004) {
                progressPackets++;
            }
        }
    }
}

package org.gms.server.quest;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Job;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.net.packet.Packet;
import org.gms.property.ServiceProperty;
import org.gms.service.ConfigService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.util.HashMap;
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

class ElementalResonanceQuestTest {
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
    void heldStaffUnlocksNextStageWithoutAutoStarting() {
        Character chr = newMage(100);
        addItem(chr, 1372035, 1);

        ElementalResonanceQuest.syncQuestStateSilently(chr);

        assertQuest(chr, 29991, QuestStatus.Status.COMPLETED);
        assertQuest(chr, 29992, QuestStatus.Status.NOT_STARTED);
        assertEquals(29992, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
    }

    @Test
    void thirdStageOpensAtLevel133() {
        Character chr = newMage(132);
        addItem(chr, 1382045, 1);

        ElementalResonanceQuest.syncQuestStateSilently(chr);

        assertTrue(ElementalResonanceQuest.resolveCurrentQuestId(chr).isEmpty());

        chr.setLevel(133);

        assertEquals(29993, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
    }

    @Test
    void startedTierOneUsesVirtualReadyProgress() {
        Character chr = newMage(70);

        var start = ElementalResonanceQuest.startStage(chr, 29991);
        assertTrue(start.success());
        assertStartedProgress(chr, 29991, "000");

        addTierOneRequirements(chr);
        ElementalResonanceQuest.syncQuestStateSilently(chr);

        assertStartedProgress(chr, 29991, "001");
    }

    @Test
    void completionValidationRejectsEquippedPreviousStaff() {
        Character chr = newMage(100);
        addItem(chr, InventoryType.EQUIPPED, 1372035, 1);
        putQuest(chr, 29992, QuestStatus.Status.STARTED, "001");
        addTierTwoBaseRequirements(chr);

        var validation = ElementalResonanceQuest.validateCompletion(chr, 29992, 0);

        assertFalse(validation.isOk());
        assertTrue(validation.getMessage().contains("卸下"));
    }

    @Test
    void completionValidationRejectsMultipleElementalStaffs() {
        Character chr = newMage(100);
        addItem(chr, 1372035, 1);
        addItem(chr, 1372036, 1);
        putQuest(chr, 29992, QuestStatus.Status.STARTED, "001");
        addTierTwoBaseRequirements(chr);

        var validation = ElementalResonanceQuest.validateCompletion(chr, 29992, 0);

        assertFalse(validation.isOk());
        assertTrue(validation.getMessage().contains("多个元素杖"));
    }

    @Test
    void crossElementRequiresExtraMaterialsAndMeso() {
        Character chr = newMage(100);
        addItem(chr, 1372035, 1);
        putQuest(chr, 29992, QuestStatus.Status.STARTED, "001");
        addTierTwoBaseRequirements(chr);

        var sameElement = ElementalResonanceQuest.validateCompletion(chr, 29992, 0);
        assertTrue(sameElement.isOk());
        assertFalse(sameElement.switchElement());
        assertEquals(8_000_000, sameElement.requiredMeso());

        var missingExtra = ElementalResonanceQuest.validateCompletion(chr, 29992, 1);
        assertFalse(missingExtra.isOk());

        addItem(chr, 4021008, 1);
        addItem(chr, 4021009, 1);
        chr.setMeso(10_000_000);

        var switchedElement = ElementalResonanceQuest.validateCompletion(chr, 29992, 1);
        assertTrue(switchedElement.isOk());
        assertTrue(switchedElement.switchElement());
        assertEquals(10_000_000, switchedElement.requiredMeso());
    }

    @Test
    void discardingStaffAllowsTierOneRestart() {
        Character chr = newMage(70);
        putQuest(chr, 29991, QuestStatus.Status.COMPLETED, "001");
        putQuest(chr, 29992, QuestStatus.Status.STARTED, "000");

        ElementalResonanceQuest.syncQuestStateSilently(chr);

        assertQuest(chr, 29991, QuestStatus.Status.NOT_STARTED);
        assertQuest(chr, 29992, QuestStatus.Status.NOT_STARTED);
        assertEquals(29991, ElementalResonanceQuest.resolveCurrentQuestId(chr).orElseThrow());
    }

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS));
    }

    private static Character newMage(int level) {
        CapturingClient client = new CapturingClient();
        Character chr = Character.getDefault(client);
        client.setPlayer(chr);
        chr.setLevel(level);
        chr.setJob(Job.FP_WIZARD);
        return chr;
    }

    private static void addTierOneRequirements(Character chr) {
        addItem(chr, 4033012, 1);
        addItem(chr, 4033013, 1);
        addItem(chr, 4033014, 1);
        addItem(chr, 4000059, 100);
        addItem(chr, 4000060, 100);
        addItem(chr, 4000061, 100);
        addItem(chr, 4021009, 1);
        chr.setMeso(2_000_000);
    }

    private static void addTierTwoBaseRequirements(Character chr) {
        addItem(chr, 4033015, 1);
        addItem(chr, 4033016, 1);
        addItem(chr, 4033017, 1);
        addItem(chr, 4000144, 100);
        addItem(chr, 4000146, 100);
        addItem(chr, 4000176, 20);
        addItem(chr, 4021008, 2);
        addItem(chr, 4021009, 1);
        chr.setMeso(8_000_000);
    }

    private static short addItem(Character chr, int itemId, int quantity) {
        InventoryType type = org.gms.constants.inventory.ItemConstants.getInventoryType(itemId);
        return addItem(chr, type, itemId, quantity);
    }

    private static short addItem(Character chr, InventoryType type, int itemId, int quantity) {
        return chr.getInventory(type).addItem(new Item(itemId, (short) 0, (short) quantity));
    }

    private static void putQuest(Character chr, int questId, QuestStatus.Status status, String progress) {
        QuestStatus questStatus = new QuestStatus(Quest.getInstance(questId), status, ElementalResonanceQuest.NPC_ID);
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
        private CapturingClient() {
            super(null, -1, null, null, -123, -123);
        }

        @Override
        public void sendPacket(Packet packet) {
            // Tests only need quest state side effects.
        }
    }
}

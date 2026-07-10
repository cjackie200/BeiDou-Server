package org.gms.server.quest.actions;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.net.packet.Packet;
import org.gms.property.ServiceProperty;
import org.gms.provider.Data;
import org.gms.provider.wz.DataType;
import org.gms.server.quest.Quest;
import org.gms.service.ConfigService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemActionTest {

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
    void randomRewardCapacityIncludesDeterministicRewardsButNotOtherAlternatives() {
        ItemAction action = action(
                item(1002001, 1, null),
                item(1002002, 1, 50),
                item(1002003, 1, 50));

        Character oneFreeSlot = newCharacter();
        fillEquipInventory(oneFreeSlot, 1);
        assertFalse(action.check(oneFreeSlot, null));

        Character twoFreeSlots = newCharacter();
        fillEquipInventory(twoFreeSlots, 2);
        assertTrue(action.check(twoFreeSlots, null));
    }

    @Test
    void invalidExternalSelectionIsRejectedWithoutIndexFailure() {
        ItemAction action = action(
                item(2000000, 1, -1),
                item(2000001, 1, -1));
        Character chr = newCharacter();

        assertFalse(action.check(chr, null));
        assertFalse(action.check(chr, -1));
        assertFalse(action.check(chr, 2));
        assertTrue(action.check(chr, 0));
    }

    @Test
    void zeroQuantityEntryIsIgnored() {
        ItemAction action = action(item(1002140, 0, null));
        Character chr = newCharacter();

        assertTrue(action.check(chr, null));
        action.run(chr, null);

        assertEquals(0, chr.getInventory(InventoryType.EQUIP).countById(1002140));
    }

    @Test
    void exactRequiredEquipQuantityMayComeFromEquippedInventory() {
        int itemId = 1902000;
        ItemAction action = action(item(itemId, -1, null));
        Character chr = newCharacter();
        Equip equipped = equippedItem(itemId, (short) -18);
        chr.getInventory(InventoryType.EQUIPPED).addItemFromDB(equipped);

        assertTrue(action.check(chr, null));
        action.run(chr, null);
        assertEquals(0, chr.getInventory(InventoryType.EQUIPPED).countById(itemId));
    }

    @Test
    void duplicateRequiredItemsAreValidatedAsOneTotal() {
        int itemId = 4000000;
        ItemAction action = action(
                item(itemId, -1, null),
                item(itemId, -1, null));

        Character oneItem = newCharacter();
        addItem(oneItem, InventoryType.ETC, new Item(itemId, (short) 0, (short) 1));
        assertFalse(action.check(oneItem, null));
        assertEquals(1, oneItem.getInventory(InventoryType.ETC).countById(itemId));

        Character twoItems = newCharacter();
        addItem(twoItems, InventoryType.ETC, new Item(itemId, (short) 0, (short) 2));
        assertTrue(action.check(twoItems, null));
    }

    @Test
    void randomNegativeRequirementIsAggregatedWithDeterministicRequirement() {
        int itemId = 4000000;
        ItemAction action = action(
                item(itemId, -1, null),
                item(itemId, -1, 1),
                item(2000000, 1, 1));
        Character chr = newCharacter();
        addItem(chr, InventoryType.ETC, new Item(itemId, (short) 0, (short) 1));

        assertFalse(action.check(chr, null));
    }

    @Test
    void selectedNegativeRequirementOnlyChecksAndRemovesChosenOption() {
        int firstItemId = 4000000;
        int secondItemId = 4000001;
        ItemAction action = action(
                item(firstItemId, -1, -1),
                item(secondItemId, -1, -1));
        Character chr = newCharacter();
        addItem(chr, InventoryType.ETC, new Item(secondItemId, (short) 0, (short) 1));

        assertFalse(action.check(chr, 0));
        assertTrue(action.check(chr, 1));
        action.run(chr, 1);
        assertEquals(0, chr.getInventory(InventoryType.ETC).countById(secondItemId));
    }

    @Test
    void equippedAndCarriedEquipQuantitiesAreCombinedAndRemoved() {
        int itemId = 1902000;
        ItemAction action = action(item(itemId, -2, null));
        Character chr = newCharacter();
        addItem(chr, InventoryType.EQUIP, new Item(itemId, (short) 0, (short) 1));
        chr.getInventory(InventoryType.EQUIPPED).addItemFromDB(equippedItem(itemId, (short) -18));

        assertTrue(action.check(chr, null));
        action.run(chr, null);

        assertEquals(0, chr.getInventory(InventoryType.EQUIP).countById(itemId));
        assertEquals(0, chr.getInventory(InventoryType.EQUIPPED).countById(itemId));
    }

    @Test
    void propBelowMinusOneIsRejectedAndNeverExecuted() {
        int itemId = 2000000;
        ItemAction action = action(item(itemId, 1, -2));
        Character chr = newCharacter();

        assertFalse(action.check(chr, null));
        action.run(chr, null);
        assertEquals(0, chr.getInventory(InventoryType.USE).countById(itemId));
    }

    private static ItemAction action(Data... items) {
        Quest quest = mock(Quest.class);
        when(quest.getId()).thenReturn((short) 20000);
        Data root = mock(Data.class);
        when(root.getChildren()).thenReturn(List.of(items));
        return new ItemAction(quest, root);
    }

    private static Data item(int itemId, int count, Integer prop) {
        Data entry = mock(Data.class);
        Map<String, Data> children = new HashMap<>();
        children.put("id", integer(itemId));
        children.put("count", integer(count));
        if (prop != null) {
            children.put("prop", integer(prop));
        }
        when(entry.getName()).thenReturn("0");
        when(entry.getChildByPath(anyString()))
                .thenAnswer(invocation -> children.get(invocation.getArgument(0)));
        return entry;
    }

    private static Data integer(int value) {
        Data data = mock(Data.class);
        when(data.getData()).thenReturn(value);
        when(data.getType()).thenReturn(DataType.INT);
        return data;
    }

    private static Character newCharacter() {
        CapturingClient client = new CapturingClient();
        Character chr = Character.getDefault(client);
        client.setPlayer(chr);
        return chr;
    }

    private static void addItem(Character chr, InventoryType type, Item item) {
        assertTrue(chr.getInventory(type).addItem(item) > 0);
    }

    private static Equip equippedItem(int itemId, short position) {
        Equip equip = mock(Equip.class);
        AtomicInteger quantity = new AtomicInteger(1);
        when(equip.getItemId()).thenReturn(itemId);
        when(equip.getPosition()).thenReturn(position);
        when(equip.getInventoryType()).thenReturn(InventoryType.EQUIP);
        when(equip.copy()).thenReturn(equip);
        when(equip.getQuantity()).thenAnswer(invocation -> (short) quantity.get());
        doAnswer(invocation -> {
            quantity.set(invocation.getArgument(0, Short.class));
            return null;
        }).when(equip).setQuantity(anyShort());
        return equip;
    }

    private static void fillEquipInventory(Character chr, int freeSlots) {
        Inventory inventory = chr.getInventory(InventoryType.EQUIP);
        int occupiedSlots = inventory.getSlotLimit() - freeSlots;
        List<Short> addedSlots = new ArrayList<>();
        for (int i = 0; i < occupiedSlots; i++) {
            addedSlots.add(inventory.addItem(new Item(1002000, (short) 0, (short) 1)));
        }
        assertTrue(addedSlots.stream().allMatch(slot -> slot > 0));
    }

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS));
    }

    private static final class CapturingClient extends Client {
        private CapturingClient() {
            super(null, -1, null, null, -123, -123);
        }

        @Override
        public void sendPacket(Packet packet) {
        }
    }
}

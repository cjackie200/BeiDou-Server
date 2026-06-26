package org.gms.client;

import org.gms.client.inventory.InventoryType;
import org.gms.dao.entity.CharactersDO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterInventorySlotTest {
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
    void defaultCharacterStartsWithMaxRegularInventorySlots() {
        Character chr = Character.getDefault(Client.createMock());

        assertEquals(96, chr.getSlots(InventoryType.EQUIP.getType()));
        assertEquals(96, chr.getSlots(InventoryType.USE.getType()));
        assertEquals(96, chr.getSlots(InventoryType.SETUP.getType()));
        assertEquals(96, chr.getSlots(InventoryType.ETC.getType()));
        assertEquals(96, chr.getSlots(InventoryType.CASH.getType()));
    }

    @Test
    void maxRegularInventorySlotsCannotBeExpandedFurther() {
        Character chr = Character.getDefault(Client.createMock());

        assertFalse(chr.canGainSlots(InventoryType.EQUIP.getType(), 4));
        assertFalse(chr.canGainSlots(InventoryType.USE.getType(), 4));
        assertFalse(chr.canGainSlots(InventoryType.SETUP.getType(), 4));
        assertFalse(chr.canGainSlots(InventoryType.ETC.getType(), 4));
    }

    @Test
    void characterDataUsesRegularInventoryTypeIndexesForSlotPersistence() {
        Character chr = Character.getDefault(Client.createMock());
        chr.getInventory(InventoryType.EQUIP).setSlotLimit(84);
        chr.getInventory(InventoryType.USE).setSlotLimit(88);
        chr.getInventory(InventoryType.SETUP).setSlotLimit(92);
        chr.getInventory(InventoryType.ETC).setSlotLimit(96);

        CharactersDO data = Character.toCharactersDO(chr);

        assertEquals(84, data.getEquipslots());
        assertEquals(88, data.getUseslots());
        assertEquals(92, data.getSetupslots());
        assertEquals(96, data.getEtcslots());
    }

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS));
    }
}

package org.gms.util;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.InventoryType;
import org.gms.property.ServiceProperty;
import org.gms.service.ConfigService;
import org.gms.server.life.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PacketCreatorElementalWeaponTest {
    private static final int FINAL_POISON_STAFF = 1372060;
    private static final short WEAPON_SLOT = -11;

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
    void elementalWeaponConfigUsesEquippedWeaponInstanceElementStats() {
        Character chr = newCharacter();
        Equip weapon = weapon(FINAL_POISON_STAFF, 141, 142, 143, 144, 145, 76);
        chr.getInventory(InventoryType.EQUIPPED).addItemFromDB(weapon);

        byte[] bytes = PacketCreator.elementalWeaponConfig(chr).getBytes();

        assertEquals(14, bytes.length);
        assertEquals(0x1006, readU16(bytes, 0));
        assertElementStats(bytes, 141, 142, 143, 144, 145, 76);
    }

    @Test
    void weaponElementBonusMapsEveryElementToInstanceStats() {
        Equip weapon = weapon(FINAL_POISON_STAFF, 141, 142, 143, 144, 145, 76);

        assertEquals(141, weapon.getElementBonus(Element.FIRE));
        assertEquals(142, weapon.getElementBonus(Element.POISON));
        assertEquals(143, weapon.getElementBonus(Element.ICE));
        assertEquals(144, weapon.getElementBonus(Element.LIGHTING));
        assertEquals(145, weapon.getElementBonus(Element.HOLY));
        assertEquals(0, weapon.getElementBonus(Element.NEUTRAL));
    }

    @Test
    void sameWeaponItemIdCanSendDifferentElementStatInstances() {
        Character first = newCharacter();
        first.getInventory(InventoryType.EQUIPPED)
                .addItemFromDB(weapon(FINAL_POISON_STAFF, 0, 125, 0, 0, 0, 80));
        Character second = newCharacter();
        second.getInventory(InventoryType.EQUIPPED)
                .addItemFromDB(weapon(FINAL_POISON_STAFF, 0, 200, 0, 0, 0, 50));

        byte[] firstBytes = PacketCreator.elementalWeaponConfig(first).getBytes();
        byte[] secondBytes = PacketCreator.elementalWeaponConfig(second).getBytes();

        assertElementStats(firstBytes, 0, 125, 0, 0, 0, 80);
        assertElementStats(secondBytes, 0, 200, 0, 0, 0, 50);
    }

    @Test
    void elementalWeaponConfigDoesNotFallbackToItemIdStats() {
        Character chr = newCharacter();
        chr.getInventory(InventoryType.EQUIPPED)
                .addItemFromDB(weapon(FINAL_POISON_STAFF, 0, 0, 0, 0, 0, 0));

        byte[] bytes = PacketCreator.elementalWeaponConfig(chr).getBytes();

        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0}, readElementStats(bytes));
    }

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS));
    }

    private static Character newCharacter() {
        return Character.getDefault(Client.createMock());
    }

    private static Equip weapon(int itemId, int fire, int poison, int ice, int lightning, int holy, int elemDefault) {
        Equip weapon = new Equip(itemId, WEAPON_SLOT);
        weapon.setIncRMAF((short) fire);
        weapon.setIncRMAS((short) poison);
        weapon.setIncRMAI((short) ice);
        weapon.setIncRMAL((short) lightning);
        weapon.setIncRMAH((short) holy);
        weapon.setElemDefault((short) elemDefault);
        return weapon;
    }

    private static void assertElementStats(byte[] bytes, int fire, int poison, int ice, int lightning, int holy, int elemDefault) {
        assertArrayEquals(new int[]{fire, poison, ice, lightning, holy, elemDefault}, readElementStats(bytes));
    }

    private static int[] readElementStats(byte[] bytes) {
        return new int[]{
                readU16(bytes, 2),
                readU16(bytes, 4),
                readU16(bytes, 6),
                readU16(bytes, 8),
                readU16(bytes, 10),
                readU16(bytes, 12)
        };
    }

    private static int readU16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }
}

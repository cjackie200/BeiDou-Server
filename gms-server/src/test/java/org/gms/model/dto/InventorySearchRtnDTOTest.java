package org.gms.model.dto;

import org.gms.client.inventory.Equip;
import org.gms.client.inventory.Item;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InventorySearchRtnDTOTest {

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
    void toItemPreservesElementalWeaponStats() {
        InventorySearchRtnDTO dto = InventorySearchRtnDTO.builder()
                .itemId(1372060)
                .position((short) -11)
                .quantity((short) 1)
                .flag((short) 0)
                .expiration(0L)
                .equipment(true)
                .inventoryEquipment(InventoryEquipRtnDTO.builder()
                        .incRMAF((short) 10)
                        .incRMAS((short) 200)
                        .incRMAI((short) 30)
                        .incRMAL((short) 40)
                        .incRMAH((short) 50)
                        .elemDefault((short) 50)
                        .build())
                .build();

        Item item = dto.toItem();

        Equip equip = assertInstanceOf(Equip.class, item);
        assertEquals(10, equip.getIncRMAF());
        assertEquals(200, equip.getIncRMAS());
        assertEquals(30, equip.getIncRMAI());
        assertEquals(40, equip.getIncRMAL());
        assertEquals(50, equip.getIncRMAH());
        assertEquals(50, equip.getElemDefault());
    }

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS));
    }
}

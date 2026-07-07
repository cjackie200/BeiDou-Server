package org.gms.server.life;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Skill;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.InventoryType;
import org.gms.client.status.MonsterStatus;
import org.gms.client.status.MonsterStatusEffect;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonsterPoisonDotTest {
    private static final int FINAL_POISON_STAFF = 1372060;

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
    void poisonDamageKeepsOriginalFormulaWithoutRmas() {
        Skill poison = skill(2101005, Element.POISON);

        assertEquals(175, MonsterPoisonDot.calculatePoisonDamage(7000, 30, null, poison));
    }

    @Test
    void poisonDamageUsesRmasFromEquippedInstanceAndCapsAtShortMax() {
        Skill poison = skill(2101005, Element.POISON);
        Equip weapon = weapon(FINAL_POISON_STAFF, 200);

        assertEquals(200, MonsterPoisonDot.resolvePoisonElementRate(poison, weapon));
        assertEquals(350, MonsterPoisonDot.applyPoisonElementRate(175, 200));
        assertEquals(Short.MAX_VALUE, MonsterPoisonDot.applyPoisonElementRate(20000, 200));
    }

    @Test
    void poisonDamageReadsCurrentEquippedWeaponInstanceRmas() {
        Character chr = newCharacter();
        Equip weapon = weapon(FINAL_POISON_STAFF, 125);
        chr.getInventory(InventoryType.EQUIPPED)
                .addItemFromDB(weapon);

        assertEquals(125, MonsterPoisonDot.resolvePoisonElementRate(chr, skill(2101005, Element.POISON)));
    }

    @Test
    void sameWeaponItemIdCanHaveDifferentPoisonRmasInstances() {
        Skill poison = skill(2101005, Element.POISON);

        assertEquals(125, MonsterPoisonDot.resolvePoisonElementRate(poison, weapon(FINAL_POISON_STAFF, 125)));
        assertEquals(200, MonsterPoisonDot.resolvePoisonElementRate(poison, weapon(FINAL_POISON_STAFF, 200)));
    }

    @Test
    void fireSkillReusingPoisonStatusDoesNotUsePoisonRmas() {
        Skill flameGear = skill(12111005, Element.FIRE);
        Equip weapon = weapon(FINAL_POISON_STAFF, 200);

        assertEquals(100, MonsterPoisonDot.resolvePoisonElementRate(flameGear, weapon));
        assertEquals(175, MonsterPoisonDot.applyPoisonElementRate(175,
                MonsterPoisonDot.resolvePoisonElementRate(flameGear, weapon)));
    }

    @Test
    void firePoisonCompositionDotUsesPoisonElementRateFromStatusElement() {
        Skill elementComposition = skill(2111006, Element.NEUTRAL);
        Equip poisonWeapon = weapon(FINAL_POISON_STAFF, 200);
        Equip fireWeapon = weapon(FINAL_POISON_STAFF, 0);
        fireWeapon.setIncRMAF((short) 200);

        assertFalse(SkillElementResolver.hasAttackElement(elementComposition, Element.POISON));
        assertTrue(SkillElementResolver.hasPoisonDotElement(elementComposition));
        assertEquals(200, MonsterPoisonDot.resolvePoisonElementRate(elementComposition, poisonWeapon));
        assertEquals(350, MonsterPoisonDot.applyPoisonElementRate(175,
                MonsterPoisonDot.resolvePoisonElementRate(elementComposition, poisonWeapon)));
        assertEquals(100, MonsterPoisonDot.resolvePoisonElementRate(elementComposition, fireWeapon));
    }

    @Test
    void bossStatusFilterOnlyAddsPlayerPoisonDotException() {
        MonsterStatusEffect playerPoison = new MonsterStatusEffect(
                Map.of(MonsterStatus.POISON, 1), skill(2101005, Element.POISON), null, false);
        MonsterStatusEffect monsterPoison = new MonsterStatusEffect(
                Map.of(MonsterStatus.POISON, 1), skill(2101005, Element.POISON), null, true);

        assertTrue(MonsterPoisonDot.isPlayerPoisonDot(playerPoison, true, false));
        assertFalse(MonsterPoisonDot.rejectsBossStatus(playerPoison.getStati(), true));
        assertFalse(MonsterPoisonDot.isPlayerPoisonDot(monsterPoison, true, false));
        assertTrue(MonsterPoisonDot.rejectsBossStatus(monsterPoison.getStati(), false));
    }

    @Test
    void poisonEffectivenessBlocksImmuneStrongAndNeutralOnly() {
        assertTrue(MonsterPoisonDot.blocksPoisonDot(ElementalEffectiveness.IMMUNE));
        assertTrue(MonsterPoisonDot.blocksPoisonDot(ElementalEffectiveness.STRONG));
        assertTrue(MonsterPoisonDot.blocksPoisonDot(ElementalEffectiveness.NEUTRAL));
        assertFalse(MonsterPoisonDot.blocksPoisonDot(ElementalEffectiveness.NORMAL));
        assertFalse(MonsterPoisonDot.blocksPoisonDot(ElementalEffectiveness.WEAK));
    }

    @Test
    void firePoisonClassPoisonDotGrantsTemporaryFireWeakness() {
        MonsterStatusEffect poisonBreath = new MonsterStatusEffect(
                Map.of(MonsterStatus.POISON, 1), skill(2101005, Element.POISON), null, false);
        MonsterStatusEffect poisonMist = new MonsterStatusEffect(
                Map.of(MonsterStatus.POISON, 1), skill(2111003, Element.POISON), null, false);
        MonsterStatusEffect poisonComposition = new MonsterStatusEffect(
                Map.of(MonsterStatus.POISON, 1), skill(2111006, Element.NEUTRAL), null, false);
        MonsterStatusEffect monsterPoison = new MonsterStatusEffect(
                Map.of(MonsterStatus.POISON, 1), skill(2101005, Element.POISON), null, true);
        MonsterStatusEffect venom = new MonsterStatusEffect(
                Map.of(MonsterStatus.POISON, 1), skill(4120005, Element.POISON), null, false);
        MonsterStatusEffect fireDemon = new MonsterStatusEffect(
                Map.of(MonsterStatus.POISON, 1), skill(2121003, Element.FIRE), null, false);
        MonsterStatusEffect slow = new MonsterStatusEffect(
                Map.of(MonsterStatus.SPEED, 1), skill(2201003, Element.NEUTRAL), null, false);

        assertTrue(MonsterPoisonDot.grantsFireWeakness(poisonBreath, true));
        assertTrue(MonsterPoisonDot.grantsFireWeakness(poisonMist, true));
        assertTrue(MonsterPoisonDot.grantsFireWeakness(poisonComposition, true));
        assertFalse(MonsterPoisonDot.grantsFireWeakness(monsterPoison, false));
        assertFalse(MonsterPoisonDot.grantsFireWeakness(venom, true));
        assertFalse(MonsterPoisonDot.grantsFireWeakness(fireDemon, true));
        assertFalse(MonsterPoisonDot.grantsFireWeakness(slow, false));
    }

    @Test
    void temporaryFireWeaknessRestoresOriginalEffectiveness() {
        MonsterStats stats = new MonsterStats();
        stats.setHp(1000);
        stats.setEffectiveness(Element.FIRE, ElementalEffectiveness.STRONG);
        Monster monster = new Monster(100100, stats);

        Runnable restoreFire = monster.applyTemporaryEffectiveness(Element.FIRE, ElementalEffectiveness.WEAK);

        assertEquals(ElementalEffectiveness.WEAK, monster.getElementalEffectiveness(Element.FIRE));
        restoreFire.run();
        assertEquals(ElementalEffectiveness.STRONG, monster.getElementalEffectiveness(Element.FIRE));
    }

    @Test
    void temporaryFireWeaknessDoesNotOverrideNaturalFireWeakness() {
        MonsterStats stats = new MonsterStats();
        stats.setHp(1000);
        stats.setEffectiveness(Element.FIRE, ElementalEffectiveness.WEAK);
        Monster monster = new Monster(100100, stats);

        Runnable restoreFire = monster.applyTemporaryEffectiveness(Element.FIRE, ElementalEffectiveness.WEAK);

        assertNull(restoreFire);
        assertEquals(ElementalEffectiveness.WEAK, monster.getElementalEffectiveness(Element.FIRE));
    }

    @Test
    void poisonTickCanKillWhileOtherDotTicksStayNonLethal() {
        assertEquals(100, MonsterPoisonDot.damageForTick(100, 150, true));
        assertEquals(1, MonsterPoisonDot.damageForTick(1, 150, true));
        assertEquals(99, MonsterPoisonDot.damageForTick(100, 150, false));
        assertEquals(0, MonsterPoisonDot.damageForTick(1, 150, false));
    }

    @Test
    void venomOnlyUsesRmasWhenSkillElementIsPoison() {
        Equip weapon = weapon(FINAL_POISON_STAFF, 200);

        assertEquals(200, MonsterPoisonDot.applyPoisonElementRate(100,
                MonsterPoisonDot.resolvePoisonElementRate(skill(4120005, Element.POISON), weapon)));
        assertEquals(100, MonsterPoisonDot.applyPoisonElementRate(100,
                MonsterPoisonDot.resolvePoisonElementRate(skill(4120005, Element.NEUTRAL), weapon)));
    }

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS));
    }

    private static Skill skill(int id, Element element) {
        Skill skill = new Skill(id);
        skill.setElement(element);
        return skill;
    }

    private static Equip weapon(int itemId, int rmas) {
        Equip weapon = new Equip(itemId, MonsterPoisonDot.WEAPON_SLOT);
        weapon.setIncRMAS((short) rmas);
        return weapon;
    }

    private static Character newCharacter() {
        CapturingClient client = new CapturingClient();
        Character chr = Character.getDefault(client);
        client.setPlayer(chr);
        return chr;
    }

    private static final class CapturingClient extends Client {
        private CapturingClient() {
            super(null, -1, null, null, -123, -123);
        }

        @Override
        public void sendPacket(Packet packet) {
            // Tests only need inventory and skill state.
        }
    }
}

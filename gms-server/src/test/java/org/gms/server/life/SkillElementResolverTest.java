package org.gms.server.life;

import org.gms.client.Skill;
import org.gms.client.inventory.Equip;
import org.gms.constants.skills.FPMage;
import org.gms.constants.skills.ILMage;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillElementResolverTest {
    private static final short WEAPON_SLOT = -11;

    @Test
    void normalSkillUsesWzElementOnly() {
        Skill fire = skill(2111002, Element.FIRE);
        Skill neutral = skill(2001002, Element.NEUTRAL);

        assertEquals(Set.of(Element.FIRE), SkillElementResolver.getAttackElements(fire));
        assertTrue(SkillElementResolver.getAttackElements(neutral).isEmpty());
    }

    @Test
    void elementCompositionSkillsUseServerSideDualElementMapping() {
        Skill firePoison = skill(FPMage.ELEMENT_COMPOSITION, Element.NEUTRAL);
        Skill iceLightning = skill(ILMage.ELEMENT_COMPOSITION, Element.NEUTRAL);

        assertEquals(Set.of(Element.FIRE, Element.POISON), SkillElementResolver.getAttackElements(firePoison));
        assertEquals(Set.of(Element.ICE, Element.LIGHTING), SkillElementResolver.getAttackElements(iceLightning));
        assertTrue(SkillElementResolver.hasAttackElement(firePoison, Element.POISON));
        assertTrue(SkillElementResolver.hasAttackElement(iceLightning, Element.LIGHTING));
    }

    @Test
    void dualElementWeaponBonusUsesBestMatchingInstanceStatWithoutStacking() {
        Equip weapon = weapon(180, 200, 170, 160);

        assertEquals(200, SkillElementResolver.bestWeaponElementBonus(
                weapon, skill(FPMage.ELEMENT_COMPOSITION, Element.NEUTRAL)));
        assertEquals(170, SkillElementResolver.bestWeaponElementBonus(
                weapon, skill(ILMage.ELEMENT_COMPOSITION, Element.NEUTRAL)));
    }

    @Test
    void weaponBonusIgnoresNonMatchingElementStats() {
        Equip weapon = weapon(0, 200, 0, 0);

        assertEquals(0, SkillElementResolver.bestWeaponElementBonus(weapon, skill(2111002, Element.FIRE)));
        assertFalse(SkillElementResolver.hasAttackElement(skill(12111005, Element.FIRE), Element.POISON));
    }

    private static Skill skill(int id, Element element) {
        Skill skill = new Skill(id);
        skill.setElement(element);
        return skill;
    }

    private static Equip weapon(int fire, int poison, int ice, int lightning) {
        Equip weapon = new Equip(1372060, WEAPON_SLOT);
        weapon.setIncRMAF((short) fire);
        weapon.setIncRMAS((short) poison);
        weapon.setIncRMAI((short) ice);
        weapon.setIncRMAL((short) lightning);
        return weapon;
    }
}

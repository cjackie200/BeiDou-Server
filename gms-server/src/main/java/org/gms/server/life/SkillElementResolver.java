package org.gms.server.life;

import org.gms.client.Skill;
import org.gms.client.inventory.Equip;
import org.gms.constants.skills.FPMage;
import org.gms.constants.skills.ILMage;

import java.util.Set;

public final class SkillElementResolver {
    private static final Set<Element> FIRE_POISON_ELEMENTS = Set.of(Element.FIRE, Element.POISON);
    private static final Set<Element> ICE_LIGHTNING_ELEMENTS = Set.of(Element.ICE, Element.LIGHTING);

    private SkillElementResolver() {
    }

    public static Set<Element> getAttackElements(Skill skill) {
        if (skill == null) {
            return Set.of();
        }

        return switch (skill.getId()) {
            case FPMage.ELEMENT_COMPOSITION -> FIRE_POISON_ELEMENTS;
            case ILMage.ELEMENT_COMPOSITION -> ICE_LIGHTNING_ELEMENTS;
            default -> singleElement(skill.getElement());
        };
    }

    public static boolean hasAttackElement(Skill skill, Element element) {
        if (element == null) {
            return false;
        }

        return getAttackElements(skill).contains(element);
    }

    public static short bestWeaponElementBonus(Equip weapon, Skill skill) {
        if (weapon == null || skill == null) {
            return 0;
        }

        short bestBonus = 0;
        for (Element element : getAttackElements(skill)) {
            short bonus = weapon.getElementBonus(element);
            if (bonus > bestBonus) {
                bestBonus = bonus;
            }
        }
        return bestBonus;
    }

    private static Set<Element> singleElement(Element element) {
        if (element == null || element == Element.NEUTRAL) {
            return Set.of();
        }

        return Set.of(element);
    }
}

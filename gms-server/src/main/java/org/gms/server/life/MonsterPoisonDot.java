package org.gms.server.life;

import org.gms.client.Character;
import org.gms.client.Skill;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.status.MonsterStatus;
import org.gms.client.status.MonsterStatusEffect;
import org.gms.constants.skills.FPMage;
import org.gms.constants.skills.FPWizard;

import java.util.Map;

final class MonsterPoisonDot {
    static final int MAX_DOT_DAMAGE = Short.MAX_VALUE;
    static final int DEFAULT_ELEMENT_RATE = 100;
    static final int DEFAULT_TICK_DELAY_MS = 1000;
    static final short WEAPON_SLOT = -11;

    private MonsterPoisonDot() {
    }

    static boolean isPlayerPoisonDot(MonsterStatusEffect status, boolean poison, boolean venom) {
        if (status == null || status.isMonsterSkill() || status.getSkill() == null) {
            return false;
        }
        if (!poison && !venom) {
            return false;
        }
        return status.getStati().containsKey(MonsterStatus.POISON);
    }

    static boolean blocksPoisonDot(ElementalEffectiveness effectiveness) {
        return effectiveness == ElementalEffectiveness.IMMUNE;
    }

    static boolean allowsReducedPoisonDotOnResistance(MonsterStatusEffect status, boolean playerPoisonDot) {
        return playerPoisonDot
                && status != null
                && status.getSkill() != null
                && SkillElementResolver.hasPoisonDotElement(status.getSkill());
    }

    static boolean grantsFireWeakness(MonsterStatusEffect status, boolean playerPoisonDot) {
        return isFirePoisonClassPoisonDot(status, playerPoisonDot);
    }

    static boolean rejectsBossStatus(Map<MonsterStatus, Integer> statis, boolean playerPoisonDot) {
        if (playerPoisonDot) {
            return false;
        }
        return !(statis.containsKey(MonsterStatus.SPEED)
                && statis.containsKey(MonsterStatus.NINJA_AMBUSH)
                && statis.containsKey(MonsterStatus.WATK));
    }

    static int calculatePoisonDamage(int maxHp, int skillLevel, Character from, Skill skill) {
        return applyPoisonElementRate(calculateBasePoisonDamage(maxHp, skillLevel),
                resolvePoisonElementRate(from, skill));
    }

    static int calculateBasePoisonDamage(int maxHp, int skillLevel) {
        if (maxHp <= 0) {
            return 0;
        }

        int denominator = 70 - skillLevel;
        if (denominator <= 0) {
            return MAX_DOT_DAMAGE;
        }

        return capDotDamage((long) Math.ceil(maxHp / (double) denominator));
    }

    static int applyPoisonElementRate(int baseDamage, int rate) {
        if (baseDamage <= 0) {
            return 0;
        }

        int effectiveRate = rate > 0 ? rate : DEFAULT_ELEMENT_RATE;
        return capDotDamage((long) Math.ceil(baseDamage * (effectiveRate / 100.0)));
    }

    static int applyPoisonEffectivenessRate(int baseDamage, ElementalEffectiveness effectiveness) {
        if (baseDamage <= 0) {
            return 0;
        }

        return switch (effectiveness) {
            case IMMUNE -> 0;
            case STRONG -> capDotDamage((long) Math.ceil(baseDamage * 0.5));
            case NEUTRAL -> capDotDamage((long) Math.ceil(baseDamage * 0.75));
            case NORMAL -> baseDamage;
            case WEAK -> capDotDamage((long) Math.ceil(baseDamage * 1.5));
        };
    }

    static int resolvePoisonElementRate(Character from, Skill skill) {
        if (skill == null || !SkillElementResolver.hasPoisonDotElement(skill) || from == null) {
            return DEFAULT_ELEMENT_RATE;
        }

        Inventory equipped = from.getInventory(InventoryType.EQUIPPED);
        if (equipped == null) {
            return DEFAULT_ELEMENT_RATE;
        }

        Item weapon = equipped.getItem(WEAPON_SLOT);
        if (!(weapon instanceof Equip equip)) {
            return DEFAULT_ELEMENT_RATE;
        }

        return resolvePoisonElementRate(skill, equip);
    }

    static int resolvePoisonElementRate(Skill skill, Equip weapon) {
        if (skill == null || !SkillElementResolver.hasPoisonDotElement(skill) || weapon == null) {
            return DEFAULT_ELEMENT_RATE;
        }

        int rmas = weapon.getIncRMAS();
        return rmas > 0 ? rmas : DEFAULT_ELEMENT_RATE;
    }

    static int damageForTick(int currentHp, int dealDamage, boolean lethal) {
        if (currentHp <= 0 || dealDamage <= 0) {
            return 0;
        }
        if (lethal) {
            return Math.min(dealDamage, currentHp);
        }
        if (currentHp <= 1) {
            return 0;
        }
        return Math.min(dealDamage, currentHp - 1);
    }

    static int tickDelay(MonsterStatusEffect status, boolean playerPoisonDot) {
        return DEFAULT_TICK_DELAY_MS;
    }

    private static boolean isFirePoisonClassPoisonDot(MonsterStatusEffect status, boolean playerPoisonDot) {
        if (!playerPoisonDot || status == null || !status.getStati().containsKey(MonsterStatus.POISON)) {
            return false;
        }

        Skill skill = status.getSkill();
        if (skill == null) {
            return false;
        }

        return switch (skill.getId()) {
            case FPWizard.POISON_BREATH, FPMage.POISON_MIST, FPMage.ELEMENT_COMPOSITION -> true;
            default -> false;
        };
    }

    private static int capDotDamage(long damage) {
        return (int) Math.min(MAX_DOT_DAMAGE, Math.max(0, damage));
    }
}

package org.gms.client.inventory;

import org.gms.constants.inventory.ItemConstants;
import org.gms.server.ItemInformationProvider;
import org.gms.server.StatEffect;

import java.util.Map;
import java.util.Set;

public final class PetItemIgnore {
    public static final int MESO = Integer.MAX_VALUE;
    public static final int HP_MP_CONSUMABLES = Integer.MAX_VALUE - 1;
    private static final int EQUIP_BELOW_LEVEL_BASE = Integer.MAX_VALUE - 1000;
    private static final int MIN_EQUIP_LEVEL_RULE = 1;
    private static final int MAX_EQUIP_LEVEL_RULE = 300;

    private PetItemIgnore() {
    }

    public static boolean shouldIgnore(Set<Integer> ignoredItems, int itemId) {
        if (ignoredItems == null || ignoredItems.isEmpty()) {
            return false;
        }
        if (ignoredItems.contains(itemId)) {
            return true;
        }
        if (ignoredItems.contains(HP_MP_CONSUMABLES) && isHpMpConsumable(itemId)) {
            return true;
        }
        Integer equipLevelRule = getEquipBelowLevelRule(ignoredItems);
        return equipLevelRule != null && isEquipBelowLevel(itemId, equipLevelRule);
    }

    public static boolean isSpecialRule(int itemId) {
        return itemId == MESO || itemId == HP_MP_CONSUMABLES || isEquipBelowLevelRule(itemId);
    }

    public static boolean isEquipBelowLevelRule(int itemId) {
        return itemId >= equipBelowLevelRule(MIN_EQUIP_LEVEL_RULE)
                && itemId <= equipBelowLevelRule(MAX_EQUIP_LEVEL_RULE);
    }

    public static int equipBelowLevelRule(int level) {
        int normalizedLevel = Math.max(MIN_EQUIP_LEVEL_RULE, Math.min(MAX_EQUIP_LEVEL_RULE, level));
        return EQUIP_BELOW_LEVEL_BASE + normalizedLevel;
    }

    private static Integer getEquipBelowLevelRule(Set<Integer> ignoredItems) {
        return ignoredItems.stream()
                .filter(PetItemIgnore::isEquipBelowLevelRule)
                .map(itemId -> itemId - EQUIP_BELOW_LEVEL_BASE)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private static boolean isHpMpConsumable(int itemId) {
        if (ItemConstants.getInventoryType(itemId) != InventoryType.USE) {
            return false;
        }

        StatEffect effect;
        try {
            effect = ItemInformationProvider.getInstance().getItemEffect(itemId);
        } catch (RuntimeException e) {
            return false;
        }
        return effect != null && (effect.getHp() > 0 || effect.getHpRate() > 0.0
                || effect.getMp() > 0 || effect.getMpRate() > 0.0);
    }

    private static boolean isEquipBelowLevel(int itemId, int level) {
        if (!ItemConstants.isEquipment(itemId)) {
            return false;
        }

        Map<String, Integer> stats = ItemInformationProvider.getInstance().getEquipStats(itemId);
        if (stats == null) {
            return false;
        }
        return stats.getOrDefault("reqLevel", 0) < level;
    }
}

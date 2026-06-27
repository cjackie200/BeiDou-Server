package org.gms.client.inventory;

import org.gms.constants.inventory.ItemConstants;
import org.gms.server.ItemInformationProvider;
import org.gms.server.StatEffect;

import java.util.Map;
import java.util.Set;

public final class PetItemIgnore {
    public static final int MESO = Integer.MAX_VALUE;
    public static final int HP_MP_CONSUMABLES = Integer.MAX_VALUE - 1;
    public static final int SCROLLS_10_60 = Integer.MAX_VALUE - 2;
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
        if (ignoredItems.contains(SCROLLS_10_60) && isTenOrSixtyPercentScroll(itemId)) {
            return true;
        }
        Integer equipLevelRule = getEquipBelowLevelRule(ignoredItems);
        return equipLevelRule != null && isEquipBelowLevel(itemId, equipLevelRule);
    }

    public static boolean isSpecialRule(int itemId) {
        return itemId == MESO || itemId == HP_MP_CONSUMABLES || itemId == SCROLLS_10_60 || isEquipBelowLevelRule(itemId);
    }

    public static boolean isEquipBelowLevelRule(int itemId) {
        return itemId >= equipBelowLevelRule(MIN_EQUIP_LEVEL_RULE)
                && itemId <= equipBelowLevelRule(MAX_EQUIP_LEVEL_RULE);
    }

    public static int equipBelowLevelRule(int level) {
        int normalizedLevel = Math.max(MIN_EQUIP_LEVEL_RULE, Math.min(MAX_EQUIP_LEVEL_RULE, level));
        return EQUIP_BELOW_LEVEL_BASE + normalizedLevel;
    }

    public static int equipBelowLevelFromRule(int itemId) {
        if (!isEquipBelowLevelRule(itemId)) {
            return 0;
        }
        return itemId - EQUIP_BELOW_LEVEL_BASE;
    }

    private static Integer getEquipBelowLevelRule(Set<Integer> ignoredItems) {
        return ignoredItems.stream()
                .filter(PetItemIgnore::isEquipBelowLevelRule)
                .map(PetItemIgnore::equipBelowLevelFromRule)
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
        return effect != null && (effect.getHp() > 0 || effect.getMp() > 0);
    }

    private static boolean isTenOrSixtyPercentScroll(int itemId) {
        if (ItemConstants.getInventoryType(itemId) != InventoryType.USE) {
            return false;
        }

        Map<String, Integer> stats = ItemInformationProvider.getInstance().getEquipStats(itemId);
        if (stats == null) {
            return false;
        }
        int success = stats.getOrDefault("success", 0);
        return success == 10 || success == 60;
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

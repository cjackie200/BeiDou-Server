package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.QuestRequirementType;

import java.util.ArrayList;
import java.util.List;

public class EquippedItemRequirement extends AbstractQuestRequirement {
    private final List<Integer> itemIds = new ArrayList<>();
    private final boolean requireAll;

    public EquippedItemRequirement(QuestRequirementType type, Data data, boolean requireAll) {
        super(type);
        this.requireAll = requireAll;
        processData(data);
    }

    @Override
    public void processData(Data data) {
        for (Data item : data.getChildren()) {
            int itemId = DataTool.getInt(item, 0);
            if (itemId > 0) {
                itemIds.add(itemId);
            }
        }
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        if (itemIds.isEmpty()) {
            return false;
        }
        Inventory equipped = chr.getInventory(InventoryType.EQUIPPED);
        if (requireAll) {
            return itemIds.stream().allMatch(itemId -> equipped.countById(itemId) > 0);
        }
        return itemIds.stream().anyMatch(itemId -> equipped.countById(itemId) > 0);
    }
}

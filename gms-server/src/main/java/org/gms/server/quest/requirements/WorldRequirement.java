package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.QuestRequirementType;

public class WorldRequirement extends AbstractQuestRequirement {
    private int worldId;

    public WorldRequirement(QuestRequirementType type, Data data) {
        super(type);
        if (type != QuestRequirementType.WORLD_MIN && type != QuestRequirementType.WORLD_MAX) {
            throw new IllegalArgumentException("Unsupported world requirement type: " + type);
        }
        processData(data);
    }

    @Override
    public void processData(Data data) {
        worldId = DataTool.getInt(data, -1);
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        if (worldId < 0) {
            return false;
        }
        if (getType() == QuestRequirementType.WORLD_MIN) {
            return chr.getWorld() >= worldId;
        }
        return chr.getWorld() <= worldId;
    }
}

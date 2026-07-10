package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.client.Mount;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

public class MinMountLevelRequirement extends AbstractQuestRequirement {
    private int minimumLevel;

    public MinMountLevelRequirement(Quest quest, Data data) {
        super(QuestRequirementType.MIN_MOUNT_LEVEL);
        processData(data);
    }

    @Override
    public void processData(Data data) {
        minimumLevel = DataTool.getInt(data);
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        Mount mount = chr.getMapleMount();
        return mount != null && mount.getLevel() >= minimumLevel;
    }
}

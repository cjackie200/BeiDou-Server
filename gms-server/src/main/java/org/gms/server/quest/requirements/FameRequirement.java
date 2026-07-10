package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

public class FameRequirement extends AbstractQuestRequirement {
    private int minimumFame;

    public FameRequirement(Quest quest, Data data) {
        super(QuestRequirementType.FAME);
        processData(data);
    }

    @Override
    public void processData(Data data) {
        minimumFame = DataTool.getInt(data);
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        return chr.getFame() >= minimumFame;
    }
}

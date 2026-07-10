package org.gms.server.quest.requirements;

import org.gms.client.BuffStat;
import org.gms.client.Character;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

public class MorphRequirement extends AbstractQuestRequirement {
    private int morphId;

    public MorphRequirement(Quest quest, Data data) {
        super(QuestRequirementType.MORPH);
        processData(data);
    }

    @Override
    public void processData(Data data) {
        morphId = DataTool.getInt(data);
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        Integer currentMorph = chr.getBuffedValue(BuffStat.MORPH);
        return currentMorph != null && currentMorph == morphId;
    }
}

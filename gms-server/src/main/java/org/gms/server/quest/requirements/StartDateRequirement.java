package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

public class StartDateRequirement extends AbstractQuestRequirement {
    private String timeStr;

    public StartDateRequirement(Quest quest, Data data) {
        super(QuestRequirementType.START);
        processData(data);
    }

    @Override
    public void processData(Data data) {
        timeStr = DataTool.getString(data);
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        long startTime = EndDateRequirement.parseTimeMillis(timeStr);
        return startTime >= 0 && startTime <= System.currentTimeMillis();
    }
}

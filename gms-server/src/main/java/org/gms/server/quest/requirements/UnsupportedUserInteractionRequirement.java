package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.provider.Data;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

public class UnsupportedUserInteractionRequirement extends AbstractQuestRequirement {
    public UnsupportedUserInteractionRequirement(Quest quest) {
        super(QuestRequirementType.USER_INTERACT);
    }

    @Override
    public void processData(Data data) {
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        return false;
    }
}

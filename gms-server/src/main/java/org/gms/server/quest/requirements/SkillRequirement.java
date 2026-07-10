package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

import java.util.ArrayList;
import java.util.List;

public class SkillRequirement extends AbstractQuestRequirement {
    private final List<SkillState> skills = new ArrayList<>();

    public SkillRequirement(Quest quest, Data data) {
        super(QuestRequirementType.SKILL);
        processData(data);
    }

    @Override
    public void processData(Data data) {
        for (Data skill : data.getChildren()) {
            int skillId = DataTool.getInt(skill.getChildByPath("id"), 0);
            if (skillId <= 0) {
                continue;
            }
            boolean mustBeAcquired = DataTool.getInt("acquire", skill, 0) > 0;
            skills.add(new SkillState(skillId, mustBeAcquired));
        }
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        for (SkillState skill : skills) {
            boolean acquired = chr.getSkillLevel(skill.id()) > 0 || chr.getMasterLevel(skill.id()) > 0;
            if (acquired != skill.mustBeAcquired()) {
                return false;
            }
        }
        return true;
    }

    private record SkillState(int id, boolean mustBeAcquired) {
    }
}

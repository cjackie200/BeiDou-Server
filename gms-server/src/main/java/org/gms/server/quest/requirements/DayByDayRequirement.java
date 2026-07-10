package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.client.QuestStatus;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class DayByDayRequirement extends AbstractQuestRequirement {
    private final Quest quest;
    private boolean enabled;

    public DayByDayRequirement(Quest quest, Data data) {
        super(QuestRequirementType.DAY_BY_DAY);
        this.quest = quest;
        processData(data);
    }

    @Override
    public void processData(Data data) {
        enabled = DataTool.getInt(data, 0) > 0;
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        if (!enabled) {
            return true;
        }
        QuestStatus status = chr.getQuest(quest);
        if (status.getStatus() != QuestStatus.Status.COMPLETED) {
            return true;
        }
        LocalDate completionDate = Instant.ofEpochMilli(status.getCompletionTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return completionDate.isBefore(LocalDate.now());
    }
}

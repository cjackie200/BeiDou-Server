package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

public class PartyQuestSRequirement extends AbstractQuestRequirement {
    private static final int[] PARTY_QUEST_AREAS = {
            1200, 1201, 1202, 1203, 1204, 1205, 1206, 1300, 1301, 1302
    };

    private int requiredRankings;

    public PartyQuestSRequirement(Quest quest, Data data) {
        super(QuestRequirementType.PARTY_QUEST_S);
        processData(data);
    }

    @Override
    public void processData(Data data) {
        requiredRankings = DataTool.getInt(data, 0);
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        if (requiredRankings <= 0) {
            return false;
        }
        int sRankings = 0;
        for (int area : PARTY_QUEST_AREAS) {
            String info = chr.getAreaInfos().get((short) area);
            if ("S".equals(infoValue(info, "rank")) && ++sRankings >= requiredRankings) {
                return true;
            }
        }
        return false;
    }

    private static String infoValue(String info, String key) {
        if (info == null || info.isBlank()) {
            return null;
        }
        for (String entry : info.split(";")) {
            int separator = entry.indexOf('=');
            if (separator > 0 && key.equals(entry.substring(0, separator))) {
                return entry.substring(separator + 1);
            }
        }
        return null;
    }
}

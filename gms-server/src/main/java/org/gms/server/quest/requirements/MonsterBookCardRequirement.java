package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

import java.util.LinkedHashMap;
import java.util.Map;

public class MonsterBookCardRequirement extends AbstractQuestRequirement {
    private final Map<Integer, Integer> minimumLevels = new LinkedHashMap<>();

    public MonsterBookCardRequirement(Quest quest, Data data) {
        super(QuestRequirementType.MONSTER_BOOK_CARD);
        processData(data);
    }

    @Override
    public void processData(Data data) {
        for (Data card : data.getChildren()) {
            int cardId = DataTool.getInt(card.getChildByPath("id"), 0);
            int minimumLevel = DataTool.getInt("min", card, 0);
            if (cardId > 0 && minimumLevel > 0) {
                minimumLevels.put(cardId, minimumLevel);
            }
        }
    }

    @Override
    public boolean check(Character chr, Integer npcId) {
        for (Map.Entry<Integer, Integer> card : minimumLevels.entrySet()) {
            if (chr.getMonsterBook().getCardLevel(card.getKey()) < card.getValue()) {
                return false;
            }
        }
        return true;
    }
}

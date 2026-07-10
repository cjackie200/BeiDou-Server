package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.client.inventory.Pet;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

public class PetAttributeLimitRequirement extends AbstractQuestRequirement {
    private final Quest quest;
    private final Pet.PetAttribute attribute;
    private boolean enabled;

    public PetAttributeLimitRequirement(Quest quest, QuestRequirementType type, Data data,
                                        Pet.PetAttribute attribute) {
        super(type);
        this.quest = quest;
        this.attribute = attribute;
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
        return quest.getMatchedPet(chr) != null;
    }

    public boolean matches(Pet pet) {
        return !enabled || pet != null && (pet.getPetAttribute() & attribute.getValue()) == 0;
    }
}

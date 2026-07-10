/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation version 3 as published by
 the Free Software Foundation. You may not use, modify or distribute
 this program under any other version of the GNU Affero General Public
 License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gms.server.quest.actions;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.inventory.Pet;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestActionType;

/**
 * @author Tyler (Twdtwd)
 */
public class PetSkillAction extends AbstractQuestAction {
    private final Quest quest;
    int flag;

    public PetSkillAction(Quest quest, Data data) {
        super(QuestActionType.PETSKILL, quest);
        this.quest = quest;
        questID = quest.getId();
        processData(data);
    }


    @Override
    public void processData(Data data) {
        flag = DataTool.getInt(data);
    }

    @Override
    public boolean check(Character chr, Integer extSelection) {
        Pet pet = quest.getMatchedPet(chr, true);
        Pet.PetAttribute attribute = Pet.PetAttribute.fromQuestSkill(flag);
        if (pet == null || attribute == null) {
            return false;
        }
        return (pet.getPetAttribute() & attribute.getValue()) == 0;
    }

    @Override
    public void run(Character chr, Integer extSelection) {
        Pet pet = quest.getMatchedPet(chr, true);
        Pet.PetAttribute attribute = Pet.PetAttribute.fromQuestSkill(flag);
        if (pet == null || attribute == null) {
            return;
        }

        Client client = chr.getClient();
        client.lockClient();
        try {
            pet.addPetAttribute(chr, attribute);
        } finally {
            client.unlockClient();
        }
    }
}

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
package org.gms.server.quest.requirements;

import org.gms.client.Character;
import org.gms.client.inventory.Pet;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tyler (Twdtwd)
 */
public class PetRequirement extends AbstractQuestRequirement {
    private final List<Integer> petIDs = new ArrayList<>();


    public PetRequirement(Quest quest, Data data) {
        super(QuestRequirementType.PET);
        processData(data);
    }


    @Override
    public void processData(Data data) {
        for (Data petData : data.getChildren()) {
            petIDs.add(DataTool.getInt(petData.getChildByPath("id")));
        }
    }


    @Override
    public boolean check(Character chr, Integer npcid) {
        return getMatchingPet(chr) != null;
    }

    public Pet getMatchingPet(Character chr) {
        for (Pet pet : chr.getPets()) {
            if (matches(pet)) {
                return pet;
            }
        }

        return null;
    }

    public boolean matches(Pet pet) {
        return pet != null && petIDs.contains(pet.getItemId());
    }
}

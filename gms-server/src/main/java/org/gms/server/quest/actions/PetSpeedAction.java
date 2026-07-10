/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

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
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestActionType;

/**
 * @author Ronan
 */
public class PetSpeedAction extends AbstractQuestAction {
    private final Quest quest;

    public PetSpeedAction(Quest quest, Data data) {
        super(QuestActionType.PETSPEED, quest);
        this.quest = quest;
        questID = quest.getId();
    }


    @Override
    public void processData(Data data) {}

    @Override
    public boolean check(Character chr, Integer extSelection) {
        return quest.getMatchedPet(chr, true) != null;
    }

    @Override
    public void run(Character chr, Integer extSelection) {
        Pet pet = quest.getMatchedPet(chr, true);
        if (pet == null) {
            return;
        }

        Client c = chr.getClient();
        c.lockClient();
        try {
            pet.addPetAttribute(chr, Pet.PetAttribute.OWNER_SPEED);
        } finally {
            c.unlockClient();
        }

    }
} 

package org.gms.server.quest.actions;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.inventory.Pet;
import org.gms.provider.Data;
import org.gms.server.quest.Quest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PetSkillActionTest {

    @Test
    void recallSkillRequiresMatchedPetWithoutRecallAndPersistsAttribute() {
        PetSkillAction action = action(4660, 128);
        Character chr = mock(Character.class);
        assertFalse(action.check(chr, null));

        Pet pet = mock(Pet.class);
        Client client = mock(Client.class);
        when(chr.getPet(0)).thenReturn(pet);
        when(chr.getClient()).thenReturn(client);
        when(pet.getPetAttribute()).thenReturn(0);

        assertTrue(action.check(chr, null));
        action.run(chr, null);

        verify(client).lockClient();
        verify(pet).addPetAttribute(chr, Pet.PetAttribute.RECALL);
        verify(client).unlockClient();

        when(pet.getPetAttribute()).thenReturn(Pet.PetAttribute.RECALL.getValue());
        assertFalse(action.check(chr, null));
    }

    @Test
    void autoSpeakingSkillUsesFull256BitInsteadOfTruncatedItemFlag() {
        PetSkillAction action = action(4661, 256);
        Character chr = mock(Character.class);
        Pet pet = mock(Pet.class);
        Client client = mock(Client.class);
        when(chr.getPet(0)).thenReturn(pet);
        when(chr.getClient()).thenReturn(client);
        when(pet.getPetAttribute()).thenReturn(0);

        assertTrue(action.check(chr, null));
        action.run(chr, null);

        verify(pet).addPetAttribute(chr, Pet.PetAttribute.AUTO_SPEAKING);
    }

    @Test
    void petSkillUsesTheQuestMatchedPetWhenItIsNotTheLeader() {
        Quest quest = mock(Quest.class);
        Data data = mock(Data.class);
        when(quest.getId()).thenReturn((short) 4660);
        when(data.getData()).thenReturn(128);
        PetSkillAction action = new PetSkillAction(quest, data);
        Character chr = mock(Character.class);
        Pet leader = mock(Pet.class);
        Pet target = mock(Pet.class);
        Client client = mock(Client.class);
        when(chr.getPet(0)).thenReturn(leader);
        when(chr.getClient()).thenReturn(client);
        when(quest.getMatchedPet(chr, true)).thenReturn(target);
        when(target.getPetAttribute()).thenReturn(0);

        assertTrue(action.check(chr, null));
        action.run(chr, null);

        verify(target).addPetAttribute(chr, Pet.PetAttribute.RECALL);
        verify(leader, never()).addPetAttribute(chr, Pet.PetAttribute.RECALL);
    }

    @Test
    void petTamenessAndSpeedUseTheQuestMatchedPet() {
        Quest quest = mock(Quest.class);
        Data tamenessData = mock(Data.class);
        when(quest.getId()).thenReturn((short) 3083);
        when(tamenessData.getData()).thenReturn(10);
        PetTamenessAction tamenessAction = new PetTamenessAction(quest, tamenessData);
        PetSpeedAction speedAction = new PetSpeedAction(quest, mock(Data.class));
        Character chr = mock(Character.class);
        Pet target = mock(Pet.class);
        Client client = mock(Client.class);
        when(chr.getClient()).thenReturn(client);
        when(quest.getMatchedPet(chr, true)).thenReturn(target);

        assertTrue(tamenessAction.check(chr, null));
        assertTrue(speedAction.check(chr, null));
        tamenessAction.run(chr, null);
        speedAction.run(chr, null);

        verify(target).gainTamenessFullness(chr, 10, 0, 0);
        verify(target).addPetAttribute(chr, Pet.PetAttribute.OWNER_SPEED);
        verify(client, times(2)).lockClient();
        verify(client, times(2)).unlockClient();
    }

    private static PetSkillAction action(int questId, int flag) {
        Quest quest = mock(Quest.class);
        Data data = mock(Data.class);
        when(quest.getId()).thenReturn((short) questId);
        when(quest.getMatchedPet(any(Character.class), eq(true)))
                .thenAnswer(invocation -> ((Character) invocation.getArgument(0)).getPet(0));
        when(data.getData()).thenReturn(flag);
        return new PetSkillAction(quest, data);
    }
}

package org.gms.server.quest.requirements;

import org.gms.client.BuffStat;
import org.gms.client.Character;
import org.gms.client.MonsterBook;
import org.gms.client.Mount;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Pet;
import org.gms.provider.Data;
import org.gms.provider.wz.DataType;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestRequirementType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestRequirementRegressionTest {

    @Test
    void monsterBookCardRequirementChecksEveryConfiguredCard() {
        MonsterBookCardRequirement requirement = new MonsterBookCardRequirement(mock(Quest.class), node("mbcard",
                node("0", scalar("id", 2382040), scalar("min", 1)),
                node("1", scalar("id", 2382049), scalar("min", 2))));
        Character chr = mock(Character.class);
        MonsterBook book = mock(MonsterBook.class);
        when(chr.getMonsterBook()).thenReturn(book);
        when(book.getCardLevel(2382040)).thenReturn(1);
        when(book.getCardLevel(2382049)).thenReturn(1);

        assertFalse(requirement.check(chr, 0));

        when(book.getCardLevel(2382049)).thenReturn(2);
        assertTrue(requirement.check(chr, 0));
    }

    @Test
    void mountEquipMorphAndPetLimitsMatchClientConditions() {
        Character chr = mock(Character.class);
        Mount mount = mock(Mount.class);
        when(chr.getMapleMount()).thenReturn(mount);
        when(mount.getLevel()).thenReturn(2, 3);
        MinMountLevelRequirement mountRequirement = new MinMountLevelRequirement(
                mock(Quest.class), scalar("tamingmoblevelmin", 3));
        assertFalse(mountRequirement.check(chr, 0));
        assertTrue(mountRequirement.check(chr, 0));

        Inventory equipped = mock(Inventory.class);
        when(chr.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.countById(1002000)).thenReturn(1);
        when(equipped.countById(1002001)).thenReturn(0);
        Data equipData = node("equip", scalar("0", 1002000), scalar("1", 1002001));
        assertFalse(new EquippedItemRequirement(QuestRequirementType.EQUIP_ALL, equipData, true).check(chr, 0));
        assertTrue(new EquippedItemRequirement(QuestRequirementType.EQUIP_ANY, equipData, false).check(chr, 0));

        MorphRequirement morph = new MorphRequirement(mock(Quest.class), scalar("morph", 1012));
        when(chr.getBuffedValue(BuffStat.MORPH)).thenReturn(null, 1012);
        assertFalse(morph.check(chr, 0));
        assertTrue(morph.check(chr, 0));

        Pet pet = mock(Pet.class);
        Quest petQuest = mock(Quest.class);
        when(petQuest.getMatchedPet(chr)).thenReturn(null, pet, null);
        PetAttributeLimitRequirement recall = new PetAttributeLimitRequirement(
                petQuest, QuestRequirementType.PET_RECALL_LIMIT,
                scalar("petRecallLimit", 1), Pet.PetAttribute.RECALL);
        assertFalse(recall.check(chr, 0));
        assertTrue(recall.check(chr, 0));
        assertFalse(recall.check(chr, 0));
    }

    @Test
    void partyQuestSRequirementParsesExactRankFieldsAndFailsClosed() {
        PartyQuestSRequirement requirement = new PartyQuestSRequirement(
                mock(Quest.class), scalar("partyQuest_S", 2));
        Character chr = mock(Character.class);
        Map<Short, String> areaInfo = new LinkedHashMap<>();
        when(chr.getAreaInfos()).thenReturn(areaInfo);

        areaInfo.put((short) 1200, "rank=S;time=100");
        areaInfo.put((short) 1201, "rank=A;note=S");
        assertFalse(requirement.check(chr, 0));

        areaInfo.put((short) 1302, "time=90;rank=S");
        assertTrue(requirement.check(chr, 0));
    }

    @Test
    void dayByDayAllowsOneStartPerLocalCalendarDay() {
        Quest quest = mock(Quest.class);
        DayByDayRequirement requirement = new DayByDayRequirement(quest, scalar("dayByDay", 1));
        Character chr = mock(Character.class);
        QuestStatus status = mock(QuestStatus.class);
        when(chr.getQuest(quest)).thenReturn(status);
        when(status.getStatus()).thenReturn(QuestStatus.Status.COMPLETED);
        when(status.getCompletionTime()).thenReturn(System.currentTimeMillis());

        assertFalse(requirement.check(chr, 0));

        long yesterday = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        when(status.getCompletionTime()).thenReturn(yesterday);
        assertTrue(requirement.check(chr, 0));
    }

    @Test
    void worldBoundsAndStrictQuestTimestampParsingAreEnforced() {
        Character chr = mock(Character.class);
        when(chr.getWorld()).thenReturn(10);
        assertTrue(new WorldRequirement(QuestRequirementType.WORLD_MIN, scalar("worldmin", "10"))
                .check(chr, 0));
        assertFalse(new WorldRequirement(QuestRequirementType.WORLD_MAX, scalar("worldmax", "9"))
                .check(chr, 0));

        long parsed = EndDateRequirement.parseTimeMillis("2030123101");
        assertEquals(12, Instant.ofEpochMilli(parsed).atZone(ZoneId.systemDefault()).getMonthValue());
        assertEquals(-1, EndDateRequirement.parseTimeMillis("2030133101"));
    }

    @Test
    void fieldEnterAcceptsAnyConfiguredMapInsteadOfOnlyFirstChild() {
        FieldEnterRequirement requirement = new FieldEnterRequirement(mock(Quest.class), node("fieldEnter",
                scalar("0", 100000000),
                scalar("1", 101000000)));
        Character chr = mock(Character.class);

        when(chr.getMapId()).thenReturn(100000000, 101000000, 102000000);
        assertTrue(requirement.check(chr, 0));
        assertTrue(requirement.check(chr, 0));
        assertFalse(requirement.check(chr, 0));
    }

    @Test
    void infoExSupportsExactMinimumAndMaximumComparisons() {
        InfoExRequirement requirement = new InfoExRequirement(mock(Quest.class), node("infoex",
                node("0", scalar("value", "ready")),
                node("1", scalar("value", "1000"), scalar("cond", 1)),
                node("2", scalar("value", "254"), scalar("cond", 2))));

        assertTrue(requirement.matches(0, "ready"));
        assertFalse(requirement.matches(0, "READY"));
        assertTrue(requirement.matches(1, "1000"));
        assertTrue(requirement.matches(1, "2500"));
        assertFalse(requirement.matches(1, "999"));
        assertTrue(requirement.matches(2, "254"));
        assertTrue(requirement.matches(2, "100"));
        assertFalse(requirement.matches(2, "255"));
        assertFalse(requirement.matches(1, "not-a-number"));
    }

    @Test
    void petRequirementsUseThePetMatchingTheQuestInsteadOfTheLeader() {
        Quest quest = mock(Quest.class);
        PetRequirement petRequirement = new PetRequirement(quest, node("pet",
                node("0", scalar("id", 5000048)),
                node("1", scalar("id", 5000049))));
        Character chr = mock(Character.class);
        Pet leader = mock(Pet.class);
        Pet target = mock(Pet.class);
        when(leader.getItemId()).thenReturn(5000000);
        when(target.getItemId()).thenReturn(5000049);
        when(chr.getPets()).thenReturn(new Pet[]{leader, target, null});

        assertSame(target, petRequirement.getMatchingPet(chr));

        when(quest.getMatchedPet(chr)).thenReturn(target);
        when(target.getPetAttribute()).thenReturn(0);
        when(target.getTameness()).thenReturn(1500);
        assertTrue(new PetAttributeLimitRequirement(quest, QuestRequirementType.PET_RECALL_LIMIT,
                scalar("petRecallLimit", 1), Pet.PetAttribute.RECALL).check(chr, 0));
        assertTrue(new MinTamenessRequirement(quest, scalar("pettamenessmin", 1400)).check(chr, 0));
    }

    private static Data scalar(String name, Object value) {
        Data data = mock(Data.class);
        when(data.getName()).thenReturn(name);
        when(data.getData()).thenReturn(value);
        when(data.getType()).thenReturn(value instanceof String ? DataType.STRING : DataType.INT);
        when(data.getChildren()).thenReturn(List.of());
        return data;
    }

    private static Data node(String name, Data... children) {
        Data data = mock(Data.class);
        Map<String, Data> byName = new LinkedHashMap<>();
        for (Data child : children) {
            byName.put(child.getName(), child);
        }
        when(data.getName()).thenReturn(name);
        when(data.getChildren()).thenReturn(List.of(children));
        when(data.getChildByPath(anyString())).thenAnswer(invocation -> byName.get(invocation.getArgument(0)));
        return data;
    }
}

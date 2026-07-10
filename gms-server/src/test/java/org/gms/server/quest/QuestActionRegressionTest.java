package org.gms.server.quest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Job;
import org.gms.client.QuestStatus;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.Pet;
import org.gms.constants.inventory.ItemConstants;
import org.gms.net.packet.ByteBufInPacket;
import org.gms.net.packet.Packet;
import org.gms.net.server.channel.handlers.QuestActionHandler;
import org.gms.property.ServiceProperty;
import org.gms.server.life.NPC;
import org.gms.server.life.NPCStats;
import org.gms.server.maps.MapleMap;
import org.gms.service.ConfigService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class QuestActionRegressionTest {

    @BeforeAll
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void setUpApplicationContext() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        ServiceProperty serviceProperty = new ServiceProperty();
        MessageSource messageSource = mock(MessageSource.class);
        ConfigService configService = mock(ConfigService.class);
        Map<Class<?>, Object> beans = new HashMap<>();

        when(configService.loadGameConfigs()).thenReturn(List.of());
        when(messageSource.getMessage(anyString(), any(Object[].class), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        beans.put(ServiceProperty.class, serviceProperty);
        beans.put(MessageSource.class, messageSource);
        beans.put(ConfigService.class, configService);

        doAnswer(invocation -> bean(beans, invocation.getArgument(0)))
                .when(context).getBean(any(Class.class));
        doAnswer(invocation -> bean(beans, invocation.getArgument(1)))
                .when(context).getBean(anyString(), any(Class.class));

        Field field = org.gms.manager.ServerManager.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, context);
    }

    @Test
    void wzQuestAndFameActionNamesMapToImplementedActions() {
        assertEquals(QuestActionType.QUEST, QuestActionType.getByWZName("quest"));
        assertEquals(QuestActionType.FAME, QuestActionType.getByWZName("fame"));
        assertEquals(QuestActionType.FAME, QuestActionType.getByWZName("pop"));
        assertEquals(QuestRequirementType.MESO, QuestRequirementType.getByWZName("endmeso"));
        assertEquals(QuestRequirementType.SKILL, QuestRequirementType.getByWZName("skill"));
        assertEquals(QuestRequirementType.MONSTER_BOOK_CARD, QuestRequirementType.getByWZName("mbcard"));
        assertEquals(QuestRequirementType.MIN_LEVEL, QuestRequirementType.getByWZName("level"));
        assertEquals(QuestRequirementType.FAME, QuestRequirementType.getByWZName("pop"));
        assertEquals(QuestRequirementType.START, QuestRequirementType.getByWZName("start"));
        assertEquals(QuestRequirementType.WORLD_MIN, QuestRequirementType.getByWZName("worldmin"));
        assertEquals(QuestRequirementType.WORLD_MAX, QuestRequirementType.getByWZName("worldmax"));
    }

    @Test
    void quest2001RequiresItsWzMinimumFame() {
        Character chr = newCharacter(Job.WARRIOR, 30, 1020000);
        putQuest(chr, 2000, QuestStatus.Status.COMPLETED, 1020000);
        Quest.clearCache(2001);
        Quest quest = Quest.getInstance(2001);

        chr.setFame(9);
        assertFalse(quest.canStart(chr, 1020000));

        chr.setFame(10);
        assertTrue(quest.canStart(chr, 1020000));
    }

    @Test
    void completionLevelAliasIsEnforcedForQuest29300() {
        Character chr = newCharacter(Job.WARRIOR, 199, 9000040);
        putQuest(chr, 29300, QuestStatus.Status.STARTED, 9000040);
        Quest.clearCache(29300);
        Quest quest = Quest.getInstance(29300);

        assertFalse(quest.canComplete(chr, 9000040));

        chr.setLevel(200);
        assertTrue(quest.canComplete(chr, 9000040));
    }

    @Test
    void skillRequirementsHonorExplicitAcquireAndDefaultNotAcquired() {
        Character warrior = spy(newCharacter(Job.WARRIOR, 45, 2110004));
        doReturn(0).when(warrior).getSkillLevel(1007);
        doReturn(0).when(warrior).getMasterLevel(1007);
        Quest.clearCache(6029);
        Quest craftQuest = Quest.getInstance(6029);

        assertTrue(craftQuest.canStart(warrior, 2110004));
        doReturn(1).when(warrior).getSkillLevel(1007);
        assertFalse(craftQuest.canStart(warrior, 2110004));

        Character explicitAcquire = spy(newCharacter(Job.WARRIOR, 30, 1022104));
        doReturn(0).when(explicitAcquire).getSkillLevel(1001004);
        doReturn(0).when(explicitAcquire).getMasterLevel(1001004);
        putQuest(explicitAcquire, 2410, QuestStatus.Status.COMPLETED, 1022104);
        Quest.clearCache(2415);
        Quest stanceQuest = Quest.getInstance(2415);

        assertFalse(stanceQuest.canStart(explicitAcquire, 1022104));
        doReturn(1).when(explicitAcquire).getSkillLevel(1001004);
        assertTrue(stanceQuest.canStart(explicitAcquire, 1022104));
    }

    @Test
    void futureDatedQuestCannotStartBeforeItsWzStartTime() {
        Character chr = newCharacter(Job.BEGINNER, 200, 9010000);
        Quest.clearCache(9625);

        assertFalse(Quest.getInstance(9625).canStart(chr, 9010000));
    }

    @Test
    void autoStartMetadataDoesNotBypassWzRequirements() {
        Character chr = newCharacter(Job.BEGINNER, 12, 9010010);
        Quest.clearCache(10230);

        Quest.getInstance(10230).start(chr, 9010010);

        assertEquals(QuestStatus.Status.NOT_STARTED.getId(), chr.getQuestStatus(10230));
    }

    @Test
    void disabledResourceQuestsFailClosedOnOldClientRequests() {
        Character chr = newCharacter(Job.BEGINNER, 200, 9000040);
        for (int questId : List.of(4490, 8510, 8540, 29000)) {
            Quest.clearCache(questId);
            assertFalse(Quest.getInstance(questId).canStart(chr, 9000040), "quest " + questId);
        }
    }

    @Test
    void completedBreakthroughResourceChainIsNotBlockedAsDisabled() {
        Character chr = newCharacter(Job.HERO, 150, 9900000);
        Quest.clearCache(30006);

        assertTrue(Quest.getInstance(30006).canStart(chr, 9900000));
    }

    @Test
    void multiPetQuestSelectsAnEligibleNonLeaderPet() {
        Character chr = mock(Character.class);
        Pet leader = mock(Pet.class);
        Pet target = mock(Pet.class);
        when(leader.getItemId()).thenReturn(5000048);
        when(leader.getPetAttribute()).thenReturn(Pet.PetAttribute.RECALL.getValue());
        when(target.getItemId()).thenReturn(5000049);
        when(target.getPetAttribute()).thenReturn(0);
        when(chr.getPets()).thenReturn(new Pet[]{leader, target, null});
        Quest.clearCache(4660);

        assertSame(target, Quest.getInstance(4660).getMatchedPet(chr, false));
    }

    @Test
    void unknownQuestIdsCannotCreateEmptyStartedOrCompletedStates() {
        int unknownQuestId = 32760;
        Character chr = newCharacter(Job.BEGINNER, 200, 9000040);
        Quest.clearCache(unknownQuestId);
        Quest quest = Quest.getInstance(unknownQuestId);

        assertFalse(quest.canStart(chr, 9000040));
        quest.start(chr, 9000040);
        assertEquals(QuestStatus.Status.NOT_STARTED.getId(), chr.getQuestStatus(unknownQuestId));

        putQuest(chr, unknownQuestId, QuestStatus.Status.STARTED, 9000040);
        assertFalse(quest.canComplete(chr, 9000040));
        quest.complete(chr, 9000040);
        assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(unknownQuestId));
    }

    @Test
    void startingQuest2101CompletesItsPredecessorThroughWzQuestAction() {
        Character chr = newCharacter(Job.BEGINNER, 35, 1012108);
        putQuest(chr, 2100, QuestStatus.Status.STARTED, 1012108);
        Quest.clearCache(2101);
        Quest quest = Quest.getInstance(2101);

        assertTrue(quest.canStart(chr, 1012108));
        quest.start(chr, 1012108);

        assertEquals(QuestStatus.Status.COMPLETED.getId(), chr.getQuestStatus(2100));
        assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(2101));
    }

    @Test
    void completingQuest8255AwardsItsWzFameAction() {
        Character chr = newCharacter(Job.BEGINNER, 50, 9201106);
        putQuest(chr, 8255, QuestStatus.Status.STARTED, 9201106);
        addItem(chr, 4032133, 1);
        Quest.clearCache(8255);
        Quest quest = Quest.getInstance(8255);

        assertTrue(quest.canComplete(chr, 9201106));
        quest.complete(chr, 9201106);

        assertEquals(QuestStatus.Status.COMPLETED.getId(), chr.getQuestStatus(8255));
        assertEquals(10, chr.getFame());
    }

    @Test
    void scriptedStartWithoutScriptFileFallsBackToNativeWzStart() {
        Character chr = newCharacter(Job.BEGINNER, 10, 22000);
        putQuest(chr, 1027, QuestStatus.Status.COMPLETED, 22000);
        addItem(chr, 1042003, 1);
        Quest.clearCache(1028);

        new QuestActionHandler().handlePacket(packet((byte) 4, (short) 1028, 22000), chr.getClient());

        assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(1028));
    }

    @Test
    void quest2215CompletionRequiresItsWzEndMesoAmount() {
        Character chr = newCharacter(Job.BEGINNER, 30, 1052108);
        putQuest(chr, 2215, QuestStatus.Status.STARTED, 1052108);
        Quest.clearCache(2215);
        Quest quest = Quest.getInstance(2215);

        chr.setMeso(1999);
        assertFalse(quest.canComplete(chr, 1052108));

        chr.setMeso(2000);
        assertTrue(quest.canComplete(chr, 1052108));
    }

    @Test
    void quest2029StartDoesNotPartiallyChargeWhenMesoIsInsufficient() {
        Character chr = newCharacter(Job.BEGINNER, 15, 1052103);
        Quest.clearCache(2029);
        Quest quest = Quest.getInstance(2029);

        chr.setMeso(999);
        assertTrue(quest.canStart(chr, 1052103));
        quest.start(chr, 1052103);
        assertEquals(QuestStatus.Status.NOT_STARTED.getId(), chr.getQuestStatus(2029));
        assertEquals(999, chr.getMeso());

        chr.setMeso(1000);
        quest.start(chr, 1052103);
        assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(2029));
        assertEquals(0, chr.getMeso());
    }

    @Test
    void restoredQuest4101StartAwardsItsNativeWzItem() {
        Character chr = newCharacter(Job.BEGINNER, 45, 9310010);
        putQuest(chr, 4100, QuestStatus.Status.COMPLETED, 9310010);
        Quest.clearCache(4101);
        Quest quest = Quest.getInstance(4101);

        assertTrue(quest.canStart(chr, 9310010));
        quest.start(chr, 9310010);

        assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(4101));
        assertEquals(1, chr.getItemQuantity(4031288, false));
    }

    @Test
    void quest4109StartRequiresLevelAndActivePrerequisite() {
        Character chr = newCharacter(Job.BEGINNER, 44, 9310005);
        Quest.clearCache(4109);
        Quest quest = Quest.getInstance(4109);

        putQuest(chr, 4103, QuestStatus.Status.STARTED, 9310008);
        assertFalse(quest.canStart(chr, 9310005));

        chr.setLevel(45);
        chr.getQuests().remove((short) 4103);
        assertFalse(quest.canStart(chr, 9310005));

        putQuest(chr, 4103, QuestStatus.Status.STARTED, 9310008);
        assertTrue(quest.canStart(chr, 9310005));
        quest.start(chr, 9310005);
        assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(4109));
    }

    @Test
    void restoredQuest8530CompletionConsumesMaterialsAndAwardsFame() {
        Character chr = newCharacter(Job.CRUSADER, 90, 9310041);
        Quest.clearCache(8530);
        putQuest(chr, 8530, QuestStatus.Status.STARTED, 9310041);
        addItem(chr, 4000393, 50);
        addItem(chr, 4000394, 40);
        addItem(chr, 4000395, 30);
        addItem(chr, 4000396, 20);
        addItem(chr, 4000397, 10);
        Quest quest = Quest.getInstance(8530);

        assertTrue(quest.canComplete(chr, 9310041));
        quest.complete(chr, 9310041);

        assertEquals(QuestStatus.Status.COMPLETED.getId(), chr.getQuestStatus(8530));
        assertEquals(0, chr.getItemQuantity(4000393, false));
        assertEquals(0, chr.getItemQuantity(4000394, false));
        assertEquals(0, chr.getItemQuantity(4000395, false));
        assertEquals(0, chr.getItemQuantity(4000396, false));
        assertEquals(0, chr.getItemQuantity(4000397, false));
        assertEquals(1, chr.getFame());
    }

    @Test
    void restoredEmptyActNodeLetsCustomizedQuest30002RunNatively() {
        Character chr = newCharacter(Job.BEGINNER, 1, 9101002);
        Quest.clearCache(30002);
        Quest quest = Quest.getInstance(30002);

        assertTrue(quest.canStart(chr, 9101002));
        quest.start(chr, 9101002);
        assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(30002));

        chr.getQuest(quest).setProgress(9409001, "005");
        assertTrue(quest.canComplete(chr, 9101002));
        quest.complete(chr, 9101002);
        assertEquals(QuestStatus.Status.COMPLETED.getId(), chr.getQuestStatus(30002));
    }

    @Test
    void darkWukongQuestEnforcesLevelAndDailyInterval() {
        Character chr = newCharacter(Job.BEGINNER, 39, 9900000);
        Quest.clearCache(30005);
        Quest quest = Quest.getInstance(30005);

        assertFalse(quest.canStart(chr, 9900000));

        chr.setLevel(40);
        assertTrue(quest.canStart(chr, 9900000));

        QuestStatus completed = new QuestStatus(quest, QuestStatus.Status.COMPLETED, 0);
        completed.setCompletionTime(System.currentTimeMillis());
        chr.getQuests().put((short) 30005, completed);
        assertFalse(quest.canStart(chr, 9900000));

        completed.setCompletionTime(System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(1));
        assertTrue(quest.canStart(chr, 9900000));
    }

    @Test
    void darkWukongQuestCompletesThroughNativeWzRewards() {
        Character chr = newCharacter(Job.BEGINNER, 40, 9900000);
        Quest.clearCache(30005);
        Quest quest = Quest.getInstance(30005);
        putQuest(chr, 30005, QuestStatus.Status.STARTED, 0);
        chr.getQuest(quest).setProgress(4230101, "200");
        chr.setMeso(0);

        assertTrue(quest.canComplete(chr, 0));
        quest.complete(chr, 0);

        assertEquals(QuestStatus.Status.COMPLETED.getId(), chr.getQuestStatus(30005));
        assertEquals(10_000_000, chr.getMeso());
        assertEquals(10, chr.getItemQuantity(2340000, false));
    }

    @Test
    void autoPreCompleteMetadataDoesNotBypassDarkWukongProgress() {
        Character chr = newCharacter(Job.BEGINNER, 40, 9900000);
        Quest.clearCache(30005);
        Quest quest = Quest.getInstance(30005);
        putQuest(chr, 30005, QuestStatus.Status.STARTED, 0);

        quest.complete(chr, 0);

        assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(30005));
        assertEquals(0, chr.getMeso());
        assertEquals(0, chr.getItemQuantity(2340000, false));
    }

    @Test
    void darkWukongRewardDoesNotCompleteWhenRateAdjustedMesoWouldOverflow() throws Exception {
        Character chr = newCharacter(Job.BEGINNER, 40, 9900000);
        Field mesoRate = Character.class.getDeclaredField("mesoRate");
        mesoRate.setAccessible(true);
        mesoRate.setFloat(chr, 2.0f);
        Quest.clearCache(30005);
        Quest quest = Quest.getInstance(30005);
        putQuest(chr, 30005, QuestStatus.Status.STARTED, 0);
        chr.getQuest(quest).setProgress(4230101, "200");
        chr.setMeso(Integer.MAX_VALUE - 15_000_000);

        quest.complete(chr, 0);

        assertEquals(QuestStatus.Status.STARTED.getId(), chr.getQuestStatus(30005));
        assertEquals(Integer.MAX_VALUE - 15_000_000, chr.getMeso());
        assertEquals(0, chr.getItemQuantity(2340000, false));
    }

    @Test
    void restoredQuests8538And8539ManageLettersThroughNativeWzActions() {
        Character chr = newCharacter(Job.CRUSADER, 100, 9310052);
        addNpc(chr, 9310040);
        Quest.clearCache(8538);
        Quest.clearCache(8539);

        Quest quest8538 = Quest.getInstance(8538);
        assertTrue(quest8538.canStart(chr, 9310052));
        quest8538.start(chr, 9310052);
        assertEquals(1, chr.getItemQuantity(4031786, false));

        assertTrue(quest8538.canComplete(chr, 9310040));
        quest8538.complete(chr, 9310040);
        assertEquals(QuestStatus.Status.COMPLETED.getId(), chr.getQuestStatus(8538));
        assertEquals(0, chr.getItemQuantity(4031786, false));

        Quest quest8539 = Quest.getInstance(8539);
        assertTrue(quest8539.canStart(chr, 9310040));
        quest8539.start(chr, 9310040);
        assertEquals(1, chr.getItemQuantity(4031787, false));

        assertTrue(quest8539.canComplete(chr, 9310052));
        quest8539.complete(chr, 9310052);
        assertEquals(QuestStatus.Status.COMPLETED.getId(), chr.getQuestStatus(8539));
        assertEquals(0, chr.getItemQuantity(4031787, false));
        assertEquals(10, chr.getItemQuantity(2000004, false));
        assertEquals(1, chr.getFame());
    }

    @Test
    void scriptedCompletionWithoutScriptFileFallsBackToNativeWzCompletion() {
        Character chr = newCharacter(Job.CORSAIR, 120, 2095000);
        putQuest(chr, 6410, QuestStatus.Status.STARTED, 2095000);
        Quest.clearCache(6410);

        new QuestActionHandler().handlePacket(packet((byte) 5, (short) 6410, 2095000), chr.getClient());

        assertEquals(QuestStatus.Status.COMPLETED.getId(), chr.getQuestStatus(6410));
    }

    private static ByteBufInPacket packet(byte action, short questId, int npcId) {
        ByteBuf buffer = Unpooled.buffer(7);
        buffer.writeByte(action);
        buffer.writeShortLE(questId);
        buffer.writeIntLE(npcId);
        return new ByteBufInPacket(buffer);
    }

    private static Character newCharacter(Job job, int level, int npcId) {
        CapturingClient client = new CapturingClient();
        Character chr = Character.getDefault(client);
        client.setPlayer(chr);
        chr.setJob(job);
        chr.setLevel(level);
        chr.setPosition(new Point(0, 0));

        MapleMap map = new MapleMap(100000000, 0, 1, 100000000, 1.0f);
        NPC npc = new NPC(npcId, new NPCStats("test-npc-" + npcId));
        npc.setPosition(new Point(0, 0));
        map.addMapObject(npc);
        chr.setMap(map);
        chr.setMap(map.getId());
        return chr;
    }

    private static void addNpc(Character chr, int npcId) {
        NPC npc = new NPC(npcId, new NPCStats("test-npc-" + npcId));
        npc.setPosition(new Point(0, 0));
        chr.getMap().addMapObject(npc);
    }

    private static void putQuest(Character chr, int questId, QuestStatus.Status status, int npcId) {
        chr.getQuests().put((short) questId, new QuestStatus(Quest.getInstance(questId), status, npcId));
    }

    private static void addItem(Character chr, int itemId, int quantity) {
        InventoryType type = ItemConstants.getInventoryType(itemId);
        short slot = chr.getInventory(type).addItem(new Item(itemId, (short) 0, (short) quantity));
        assertTrue(slot > 0);
    }

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS));
    }

    private static final class CapturingClient extends Client {
        private CapturingClient() {
            super(null, -1, null, null, -123, -123);
        }

        @Override
        public void sendPacket(Packet packet) {
        }
    }
}

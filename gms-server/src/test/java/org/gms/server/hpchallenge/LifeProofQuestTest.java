package org.gms.server.hpchallenge;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Job;
import org.gms.client.QuestStatus;
import org.gms.net.packet.Packet;
import org.gms.property.ServiceProperty;
import org.gms.server.quest.MonsterCardRingQuest;
import org.gms.server.quest.Quest;
import org.gms.service.ConfigService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LifeProofQuestTest {
    private static final int PROGRESS_KEY = 0;

    @BeforeAll
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void setUpApplicationContext() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        ServiceProperty serviceProperty = new ServiceProperty();
        MessageSource messageSource = mock(MessageSource.class);
        ConfigService configService = mock(ConfigService.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        Map<Class<?>, Object> beans = new HashMap<>();

        when(configService.loadGameConfigs()).thenReturn(List.of());
        when(messageSource.getMessage(anyString(), any(Object[].class), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        beans.put(ServiceProperty.class, serviceProperty);
        beans.put(MessageSource.class, messageSource);
        beans.put(ConfigService.class, configService);
        beans.put(DataSource.class, dataSource);

        doAnswer(invocation -> bean(beans, invocation.getArgument(0)))
                .when(context).getBean(any(Class.class));
        doAnswer(invocation -> bean(beans, invocation.getArgument(1)))
                .when(context).getBean(anyString(), any(Class.class));

        Field field = org.gms.manager.ServerManager.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, context);
    }

    @Test
    void questIdsFollowStageJobBlockFormula() {
        assertEquals(5100, LifeProofQuest.questId(1, HpChallengeService.JobBranch.WARRIOR, 0));
        assertEquals(5125, LifeProofQuest.questId(1, HpChallengeService.JobBranch.MAGE, 0));
        assertEquals(5225, LifeProofQuest.questId(2, HpChallengeService.JobBranch.WARRIOR, 0));
        assertEquals(5950, LifeProofQuest.questId(7, HpChallengeService.JobBranch.PIRATE, 0));
        assertEquals(5970, LifeProofQuest.questId(7, HpChallengeService.JobBranch.PIRATE, LifeProofQuest.REWARD_SLOT));
        assertTrue(LifeProofQuest.LAST_QUEST_ID < 30000);
    }

    @Test
    void visibleAndHiddenQuestCountsMatchLayout() {
        assertEquals(785, LifeProofQuest.allQuestMetas().size());
        assertEquals(470, LifeProofQuest.allVisibleQuests().size());

        int bridge = LifeProofQuest.questId(1, HpChallengeService.JobBranch.WARRIOR, LifeProofQuest.BRIDGE_SLOT_START);
        assertTrue(LifeProofQuest.isQuestId(bridge));
        assertFalse(LifeProofQuest.isVisibleQuestId(bridge));
    }

    @Test
    void lifeProofStateCompletedStagesUseCurrentStageAndHighestRewardedStage() {
        assertEquals(Set.of(1, 2), HpChallengeService.lifeProofStateCompletedStages(3, 0));
        assertEquals(Set.of(1, 2), HpChallengeService.lifeProofStateCompletedStages(1, 2));
        assertEquals(Set.of(1, 2, 3), HpChallengeService.lifeProofStateCompletedStages(3, 3));
        assertTrue(HpChallengeService.lifeProofStateCompletedStages(1, 0).isEmpty());
    }

    @Test
    void stateCompletedStagesNormalizeMageVisibleCountsAndClearHiddenQuestState() {
        Character chr = newLifeProofCharacter(Job.FP_ARCHMAGE);
        int retiredT1 = 5142;
        int retiredT2 = 5268;
        int bridgeT1 = LifeProofQuest.questId(1, HpChallengeService.JobBranch.MAGE,
                LifeProofQuest.BRIDGE_SLOT_START);
        int reservedT2 = LifeProofQuest.reservedQuestId(2, HpChallengeService.JobBranch.MAGE);
        putQuest(chr, retiredT1, QuestStatus.Status.COMPLETED, "001");
        putQuest(chr, retiredT2, QuestStatus.Status.STARTED, "000");
        putQuest(chr, bridgeT1, QuestStatus.Status.COMPLETED, null);
        putQuest(chr, reservedT2, QuestStatus.Status.STARTED, null);

        LifeProofQuest.normalizeCompletedStages(chr, HpChallengeService.JobBranch.MAGE,
                HpChallengeService.lifeProofStateCompletedStages(3, 0));

        assertCompletedVisibleCount(chr, 1, HpChallengeService.JobBranch.MAGE, 16);
        assertCompletedVisibleCount(chr, 2, HpChallengeService.JobBranch.MAGE, 13);
        assertQuestStatus(chr, retiredT1, QuestStatus.Status.NOT_STARTED);
        assertQuestStatus(chr, retiredT2, QuestStatus.Status.NOT_STARTED);
        assertQuestStatus(chr, bridgeT1, QuestStatus.Status.NOT_STARTED);
        assertQuestStatus(chr, reservedT2, QuestStatus.Status.NOT_STARTED);
    }

    @Test
    void firstStageWarriorVisitsOwnInstructorLast() {
        List<LifeProofQuest.QuestMeta> quests = LifeProofQuest.stageBranchVisibleQuests(1, HpChallengeService.JobBranch.WARRIOR);

        assertEquals(5100, quests.get(0).questId());
        assertEquals(List.of(1012100), quests.get(0).objective().targetIds());
        assertEquals(5104, quests.get(4).questId());
        assertEquals(List.of(1022000), quests.get(4).objective().targetIds());
    }

    @Test
    void activeLifeProofProgressCanOpenAtStartAndCompleteNpc() {
        assertTrue(LifeProofQuest.canOpenProgressAtNpc(5100, 1022000));
        assertTrue(LifeProofQuest.canOpenProgressAtNpc(5100, 1012100));
        assertFalse(LifeProofQuest.canOpenProgressAtNpc(5100, 1032001));

        assertTrue(LifeProofQuest.canOpenProgressAtNpc(5101, 1012100));
        assertTrue(LifeProofQuest.canOpenProgressAtNpc(5101, 1032001));
        assertFalse(LifeProofQuest.canOpenProgressAtNpc(5101, 1022000));

        assertTrue(LifeProofQuest.canOpenProgressAtNpc(5125, 1032001));
        assertTrue(LifeProofQuest.canOpenProgressAtNpc(5125, 1012100));
        assertFalse(LifeProofQuest.canOpenProgressAtNpc(5125, 1022000));

        assertTrue(LifeProofQuest.canOpenProgressAtNpc(5126, 1012100));
        assertTrue(LifeProofQuest.canOpenProgressAtNpc(5126, 1022000));
        assertFalse(LifeProofQuest.canOpenProgressAtNpc(5126, 1032001));
    }

    @Test
    void lifeProofHookRulesDoNotFallBackToFullQuestListWithoutCurrentCharacter() {
        assertTrue(LifeProofQuest.getHookQuestIds(null).isEmpty());
    }

    @Test
    void firstStageNpcTalkQuestsCompleteAtTargetInstructor() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveQuestXml("wz-zh-CN/Quest.wz/Check.img.xml").toFile());

        assertQuestStartAndEndNpc(document, 5100, 1022000, 1012100);
        assertQuestStartAndEndNpc(document, 5101, 1012100, 1032001);
        assertQuestStartAndEndNpc(document, 5102, 1032001, 1052001);
        assertQuestStartAndEndNpc(document, 5103, 1052001, 1090000);
        assertQuestStartAndEndNpc(document, 5104, 1090000, 1022000);

        assertQuestStartAndEndNpc(document, 5125, 1032001, 1012100);
        assertQuestStartAndEndNpc(document, 5126, 1012100, 1022000);
        assertQuestStartAndEndNpc(document, 5127, 1022000, 1052001);
        assertQuestStartAndEndNpc(document, 5128, 1052001, 1090000);
        assertQuestStartAndEndNpc(document, 5129, 1090000, 1032001);

        assertQuestStartAndEndNpc(document, 5150, 1012100, 1032001);
        assertQuestStartAndEndNpc(document, 5151, 1032001, 1022000);
        assertQuestStartAndEndNpc(document, 5152, 1022000, 1052001);
        assertQuestStartAndEndNpc(document, 5153, 1052001, 1090000);
        assertQuestStartAndEndNpc(document, 5154, 1090000, 1012100);

        assertQuestStartAndEndNpc(document, 5175, 1052001, 1012100);
        assertQuestStartAndEndNpc(document, 5176, 1012100, 1032001);
        assertQuestStartAndEndNpc(document, 5177, 1032001, 1022000);
        assertQuestStartAndEndNpc(document, 5178, 1022000, 1090000);
        assertQuestStartAndEndNpc(document, 5179, 1090000, 1052001);

        assertQuestStartAndEndNpc(document, 5200, 1090000, 1012100);
        assertQuestStartAndEndNpc(document, 5201, 1012100, 1032001);
        assertQuestStartAndEndNpc(document, 5202, 1032001, 1022000);
        assertQuestStartAndEndNpc(document, 5203, 1022000, 1052001);
        assertQuestStartAndEndNpc(document, 5204, 1052001, 1090000);
    }

    @Test
    void npcTalkObjectiveOnlyExistsForFirstStageInstructorVisits() {
        List<LifeProofQuest.QuestMeta> npcTalkQuests = LifeProofQuest.allVisibleQuests().stream()
                .filter(LifeProofQuest::isNpcTalkVisitQuest)
                .toList();

        assertEquals(25, npcTalkQuests.size());
        for (LifeProofQuest.QuestMeta meta : npcTalkQuests) {
            assertEquals(1, meta.stage(), "NPC_TALK must only be used by first-stage instructor visits");
            assertTrue(meta.slot() >= LifeProofQuest.MAIN_SLOT_START && meta.slot() < 5,
                    "NPC_TALK must stay in first-stage instructor visit slots: " + meta.questId());
            assertEquals(LifeProofQuest.branchInstructorNpcId(meta),
                    LifeProofQuest.branchInfo(meta.branch()).instructorNpcId());
            assertEquals(meta.objective().targetIds().getFirst(), LifeProofQuest.completeNpcId(meta));
            if (meta.slot() == LifeProofQuest.MAIN_SLOT_START) {
                assertEquals(LifeProofQuest.branchInstructorNpcId(meta), LifeProofQuest.startNpcId(meta));
            } else {
                assertEquals(previousVisibleQuest(meta).objective().targetIds().getFirst(),
                        LifeProofQuest.startNpcId(meta));
            }
        }
    }

    @Test
    void npcTalkQuestInfoUsesProgressMarker() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveQuestXml("wz-zh-CN/Quest.wz/QuestInfo.img.xml").toFile());

        // NPC_TALK quests now use the standard minimal field 1 format
        assertQuestInfoContains(document, 5100, "#e任务进度#n");
        assertQuestInfoContains(document, 5126, "#e任务进度#n");
        assertQuestInfoContains(document, 5204, "#e任务进度#n");
    }

    @Test
    void mapTasksAreConvertedToCollectionItems() {
        LifeProofQuest.QuestMeta t2Common = LifeProofQuest.stageBranchVisibleQuests(2, HpChallengeService.JobBranch.WARRIOR)
                .stream()
                .filter(meta -> meta.questId() == 5225)
                .findFirst()
                .orElseThrow();
        assertEquals(LifeProofQuest.ObjectiveType.ITEM, t2Common.objective().type());
        assertEquals(4005000, t2Common.objective().itemId()); // 力量水晶
        assertEquals(30, t2Common.objective().requiredCount());

        LifeProofQuest.QuestMeta t1OptionalMap = LifeProofQuest.allQuestMetas()
                .stream()
                .filter(meta -> meta.questId() == 5115)
                .findFirst()
                .orElseThrow();
        assertEquals(LifeProofQuest.ObjectiveType.ITEM, t1OptionalMap.objective().type());
        assertEquals(4033000, t1OptionalMap.objective().itemId());
        assertEquals(50, t1OptionalMap.objective().requiredCount());
    }

    @Test
    void itemCollectionDefinitionsMatchDynamicDropPlan() {
        assertEquals(11, LifeProofQuest.itemCollections().size());

        LifeProofQuest.ItemCollection temple = LifeProofQuest.itemCollections().get("visit_temple_maps");
        assertEquals(4033009, temple.itemId());
        assertEquals(75, temple.requiredCount());
        assertEquals(List.of(8200005, 8200006, 8200007, 8200008, 8200009, 8200010, 8200011, 8200012),
                temple.droppers());
    }

    @Test
    void questInfoUsesMinimalClientSafeFields() throws Exception {
        assertLifeProofQuestInfoClientSafe(resolveQuestXml("wz-zh-CN/Quest.wz/QuestInfo.img.xml"));
    }

    @Test
    void visibleLifeProofQuestsUseStageBranchParentAndMetadataNextQuest() throws Exception {
        Document info = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveQuestXml("wz-zh-CN/Quest.wz/QuestInfo.img.xml").toFile());
        Document act = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveQuestXml("wz-zh-CN/Quest.wz/Act.img.xml").toFile());

        Map<String, Integer> parentCounts = new HashMap<>();
        for (LifeProofQuest.QuestMeta meta : LifeProofQuest.allVisibleQuests()) {
            Element quest = topLevelImgDir(info, meta.questId());
            assertEquals(LifeProofQuest.questListParent(meta), childValue(quest, "string", "parent"),
                    "visible life proof quest parent must be stage+branch series: " + meta.questId());
            assertEquals(Integer.toString(LifeProofQuest.questListOrder(meta)), childValue(quest, "int", "order"),
                    "visible life proof quest order must follow branch stage metadata: " + meta.questId());
            parentCounts.merge(LifeProofQuest.questListParent(meta), 1, Integer::sum);

            int expectedNextQuest = LifeProofQuest.staticNextQuestId(meta);
            String actualNextQuest = childValue(childImgDir(topLevelImgDir(act, meta.questId()), "1"),
                    "int", "nextQuest");
            assertEquals(expectedNextQuest <= 0 ? "" : Integer.toString(expectedNextQuest), actualNextQuest,
                    "static nextQuest must match deterministic metadata only: " + meta.questId());
        }

        for (LifeProofQuest.QuestMeta meta : LifeProofQuest.allVisibleQuests()) {
            int expectedCount = meta.stage() == 1 ? 16 : 13;
            assertEquals(expectedCount, parentCounts.get(LifeProofQuest.questListParent(meta)),
                    "quest parent must contain only one branch-stage series: " + LifeProofQuest.questListParent(meta));
        }
    }

    @Test
    void questInfoProgressMacrosMatchQuestProgressOrder() throws Exception {
        Document info = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveQuestXml("wz-zh-CN/Quest.wz/QuestInfo.img.xml").toFile());
        Document check = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveQuestXml("wz-zh-CN/Quest.wz/Check.img.xml").toFile());

        for (LifeProofQuest.QuestMeta meta : LifeProofQuest.allVisibleQuests()) {
            String detail = childValue(topLevelImgDir(info, meta.questId()), "string", "1");
            assertLifeProofQuestDetailComplete(detail, meta);
            // All quest types now use the minimal field 1 format with progress marker.
            assertLifeProofProgressMarker(detail, meta.questId());
            // No #a macros in WZ — client-side kill tracking replaced by server-generated conditions.
            assertFalse(Pattern.compile("#a").matcher(detail).find(),
                    "life proof quest detail must not use client #a macro: " + meta.questId());
        }
    }

    @Test
    void questCategoryUsesLegendRoad() throws Exception {
        assertEquals("empty", childValue(DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(resolveQuestXml("wz/Etc.wz/QuestCategory.img.xml").toFile())
                        .getDocumentElement(), "string", "31"),
                "base QuestCategory 31 must not carry zh-CN custom category");
        assertLegendRoadQuestCategory(resolveQuestXml("wz-zh-CN/Etc.wz/QuestCategory.img.xml"));
    }

    @Test
    void monsterCardRingQuestWzUsesLegendRoad() throws Exception {
        assertMonsterCardRingQuestWz("wz-zh-CN/Quest.wz");
    }

    @Test
    void questWzDoesNotContainClientDanglingLifeProofNodes() throws Exception {
        assertLifeProofQuestWzClientSafe("wz-zh-CN/Quest.wz");
    }

    @Test
    void customLifeProofCompletionUsesProgressGate() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveQuestXml("wz-zh-CN/Quest.wz/Check.img.xml").toFile());
        int gated = 0;

        for (LifeProofQuest.QuestMeta meta : LifeProofQuest.allVisibleQuests()) {
            Element complete = childImgDir(topLevelImgDir(document, meta.questId()), "1");
            if (requiresInfoExCompletionGate(meta)) {
                gated++;
                assertEquals(String.format("%03d", meta.objective().requiredCount()),
                        childValue(complete, "infoex", "0", "string", "value"),
                        "life proof custom progress quest must require progress before completion: "
                                + meta.questId());
                assertEquals("",
                        childValue(complete, "int", "infoNumber"),
                        "life proof custom progress quest should use its own quest progress by default: "
                                + meta.questId());
                continue;
            }

            if (meta.objective().type() == LifeProofQuest.ObjectiveType.NPC_TALK) {
                assertNull(childImgDirOrNull(complete, "infoex"),
                        "NPC_TALK completion icon must be available at target NPC before progress is written: "
                                + meta.questId());
                continue;
            }

            if (meta.kind() != LifeProofQuest.QuestKind.REWARD) {
                assertTrue(hasCompletionGate(complete),
                        "visible life proof quest must not be completable by npc/script only: " + meta.questId());
            }
        }

        assertEquals(211, gated, "life proof custom progress completion gate count");
    }

    @Test
    void baseWzDoesNotContainZhCnCustomQuestSeries() throws Exception {
        assertNoCustomQuestSeries(resolveQuestXml("wz/Quest.wz/QuestInfo.img.xml"));
        assertNoCustomQuestSeries(resolveQuestXml("wz/Quest.wz/Check.img.xml"));
        assertNoCustomQuestSeries(resolveQuestXml("wz/Quest.wz/Act.img.xml"));
    }

    private static void assertLifeProofQuestInfoClientSafe(Path path) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
        int count = 0;

        for (Element quest : topLevelImgDirs(document)) {
            String name = quest.getAttribute("name");
            if (!name.matches("\\d+")) {
                continue;
            }
            int questId = Integer.parseInt(name);
            if (!LifeProofQuest.isVisibleQuestId(questId)) {
                continue;
            }

            count++;
            LifeProofQuest.QuestMeta meta = LifeProofQuest.allVisibleQuests().stream()
                    .filter(candidate -> candidate.questId() == questId)
                    .findFirst()
                    .orElseThrow();
            assertEquals(LifeProofQuest.questListParent(meta), childValue(quest, "string", "parent"),
                    "visible life proof QuestInfo.parent must be stage+branch series for quest " + questId + " in "
                            + path);
            assertEquals(Integer.toString(LifeProofQuest.questListOrder(meta)),
                    childValue(quest, "int", "order"),
                    "visible life proof QuestInfo.order must follow metadata for quest " + questId + " in " + path);
            assertEquals("31", childValue(quest, "int", "area"),
                    "life proof QuestInfo.area must stay in Legend Road for quest " + questId + " in " + path);
        }

        assertEquals(470, count, "visible life proof quest count in " + path);
    }

    private static void assertLegendRoadQuestCategory(Path path) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
        assertEquals("传奇之路", childValue(document.getDocumentElement(), "string", "31"),
                "QuestCategory 31 must be Legend Road in " + path);
    }

    private static void assertMonsterCardRingQuestWz(String questDir) throws Exception {
        Path infoPath = resolveQuestXml(questDir + "/QuestInfo.img.xml");
        Path checkPath = resolveQuestXml(questDir + "/Check.img.xml");
        Path actPath = resolveQuestXml(questDir + "/Act.img.xml");

        Document info = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(infoPath.toFile());
        Document check = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(checkPath.toFile());
        Document act = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(actPath.toFile());
        for (int questId = MonsterCardRingQuest.CLAIM_QUEST_ID; questId <= MonsterCardRingQuest.LAST_QUEST_ID; questId++) {
            int order = questId - MonsterCardRingQuest.CLAIM_QUEST_ID + 1;
            Element quest = topLevelImgDir(info, questId);
            assertEquals("怪物卡戒指", childValue(quest, "string", "parent"),
                    "monster card ring QuestInfo.parent must group the series for quest " + questId + " in " + infoPath);
            assertEquals(Integer.toString(order), childValue(quest, "int", "order"),
                    "monster card ring QuestInfo.order must be the 11-step series order for quest " + questId + " in "
                            + infoPath);
            assertEquals("31", childValue(quest, "int", "area"),
                    "monster card ring QuestInfo.area must stay in Legend Road for quest " + questId + " in " + infoPath);
            String detail = childValue(quest, "string", "1");
            if (questId == MonsterCardRingQuest.CLAIM_QUEST_ID) {
                assertFalse(detail.contains("@@BD_IH_PROGRESS:"),
                        "monster card ring claim quest must not contain hook progress marker: " + questId);
            } else {
                String marker = "@@BD_IH_PROGRESS:" + questId + "@@";
                assertEquals(1, countOccurrences(detail, marker),
                        "monster card ring upgrade detail must contain exactly one hook progress marker for quest "
                                + questId);
                assertTrue(detail.contains("任务进度"),
                        "monster card ring upgrade detail must include progress label for quest " + questId);
                assertFalse(detail.contains("怪物卡戒指升级目标："),
                        "monster card ring detail must not use old technical template: " + questId);
                assertFalse(detail.contains("当前进度："),
                        "monster card ring detail must not use old current-progress label: " + questId);
                assertFalse(detail.contains("完成方式："),
                        "monster card ring detail must not use old completion-method label: " + questId);
            }
            assertFalse(detail.contains("@@DB_IH_PROGRESS:"),
                    "monster card ring detail must not use typo interaction hook marker: " + questId);
            assertFalse(detail.contains("@@BD_LP_PROGRESS:"),
                    "monster card ring detail must not use life proof marker: " + questId);
            assertFalse(detail.contains("#a"),
                    "monster card ring detail must not use client #a macro: " + questId);

            if (questId > MonsterCardRingQuest.CLAIM_QUEST_ID) {
                Element complete = childImgDir(topLevelImgDir(check, questId), "1");
                assertEquals("001", childValue(complete, "infoex", "0", "string", "value"),
                        "monster card ring upgrade must use infoex completion gate for quest " + questId + " in "
                                + checkPath);
            }

            Element completeAct = childImgDir(topLevelImgDir(act, questId), "1");
            if (questId < MonsterCardRingQuest.LAST_QUEST_ID) {
                assertEquals(Integer.toString(questId + 1), childValue(completeAct, "int", "nextQuest"),
                        "monster card ring Act.nextQuest must chain to the next step for quest " + questId + " in "
                                + actPath);
            } else {
                assertEquals("", childValue(completeAct, "int", "nextQuest"),
                        "monster card ring final step must not define Act.nextQuest in " + actPath);
            }
        }

        Set<Integer> infoIds = topLevelMonsterCardRingQuestIds(infoPath);
        Set<Integer> checkIds = topLevelMonsterCardRingQuestIds(checkPath);
        Set<Integer> actIds = topLevelMonsterCardRingQuestIds(actPath);
        assertEquals(11, infoIds.size(), "monster card ring QuestInfo count in " + infoPath);
        assertEquals(infoIds, checkIds, "Check.img must contain every monster card ring quest in " + questDir);
        assertEquals(infoIds, actIds, "Act.img must contain every monster card ring quest in " + questDir);
    }

    private static int countOccurrences(String text, String needle) {
        if (text == null || text.isEmpty() || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void assertNoCustomQuestSeries(Path path) throws Exception {
        assertTrue(topLevelLifeProofQuestIds(path, true).isEmpty(),
                "base wz must not contain life proof quest nodes in " + path);
        assertTrue(topLevelMonsterCardRingQuestIds(path).isEmpty(),
                "base wz must not contain monster card ring quest nodes in " + path);
    }

    private static void assertHiddenQuestInfoNotVisible(Element questInfo, int questId) {
        assertEquals("", childValue(questInfo, "string", "name"),
                "hidden life proof QuestInfo.name must be omitted: " + questId);
        assertEquals("", childValue(questInfo, "string", "0"),
                "hidden life proof QuestInfo start text must be omitted: " + questId);
        assertEquals("", childValue(questInfo, "string", "1"),
                "hidden life proof QuestInfo progress text must be omitted: " + questId);
        assertEquals("", childValue(questInfo, "string", "2"),
                "hidden life proof QuestInfo completion text must be omitted: " + questId);
        assertEquals("", childValue(questInfo, "string", "parent"),
                "hidden life proof QuestInfo.parent must be omitted: " + questId);
        assertEquals("", childValue(questInfo, "int", "order"),
                "hidden life proof QuestInfo.order must be omitted: " + questId);
        assertEquals("", childValue(questInfo, "int", "area"),
                "hidden life proof QuestInfo.area must be omitted: " + questId);
    }

    private static void assertHiddenQuestCheckNotStartable(Element check, int reservedQuestId, int questId,
                                                           String questDir) {
        Element start = childImgDir(check, "0");
        Element complete = childImgDir(check, "1");
        assertEquals("", childValue(start, "int", "npc"),
                "hidden life proof Check start npc must be omitted in " + questDir + ": " + questId);
        assertEquals("", childValue(start, "int", "lvmin"),
                "hidden life proof Check lvmin must be omitted in " + questDir + ": " + questId);
        assertEquals("", childValue(start, "string", "startscript"),
                "hidden life proof Check startscript must be omitted in " + questDir + ": " + questId);
        assertNull(childImgDirOrNull(start, "job"),
                "hidden life proof Check job gate must be omitted in " + questDir + ": " + questId);
        assertEquals(Integer.toString(reservedQuestId),
                childValue(start, "quest", "0", "int", "id"),
                "hidden life proof quest must depend on its reserved lock quest in " + questDir + ": " + questId);
        assertEquals("2",
                childValue(start, "quest", "0", "int", "state"),
                "hidden life proof quest lock must require completed reserved quest in " + questDir + ": " + questId);
        assertEquals("", childValue(complete, "int", "npc"),
                "hidden life proof Check complete npc must be omitted in " + questDir + ": " + questId);
        assertEquals("", childValue(complete, "string", "endscript"),
                "hidden life proof Check endscript must be omitted in " + questDir + ": " + questId);
    }

    private static void assertLifeProofQuestWzClientSafe(String questDir) throws Exception {
        Path infoPath = resolveQuestXml(questDir + "/QuestInfo.img.xml");
        Path checkPath = resolveQuestXml(questDir + "/Check.img.xml");
        Path actPath = resolveQuestXml(questDir + "/Act.img.xml");

        Set<Integer> infoIds = topLevelLifeProofQuestIds(infoPath, false);
        Set<Integer> checkIds = topLevelLifeProofQuestIds(checkPath, false);
        Set<Integer> actIds = topLevelLifeProofQuestIds(actPath, false);

        assertEquals(750, infoIds.size(), "life proof QuestInfo count in " + infoPath);
        assertEquals(infoIds, checkIds,
                "Check.img must not contain life proof quest nodes missing from QuestInfo.img in " + questDir);
        assertEquals(infoIds, actIds,
                "Act.img must contain every life proof quest node present in QuestInfo.img in " + questDir);

        for (int oldQuestId : topLevelLifeProofQuestIds(infoPath, true)) {
            assertFalse(oldQuestId >= 30100 && oldQuestId <= 30999,
                    "old 30000-range life proof QuestInfo id must not remain: " + oldQuestId);
        }
        for (int oldQuestId : topLevelLifeProofQuestIds(checkPath, true)) {
            assertFalse(oldQuestId >= 30100 && oldQuestId <= 30999,
                    "old 30000-range life proof Check id must not remain: " + oldQuestId);
        }
        for (int oldQuestId : topLevelLifeProofQuestIds(actPath, true)) {
            assertFalse(oldQuestId >= 30100 && oldQuestId <= 30999,
                    "old 30000-range life proof Act id must not remain: " + oldQuestId);
        }

        Document infoDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(infoPath.toFile());
        Document checkDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(checkPath.toFile());
        for (int stage = 1; stage <= 7; stage++) {
            for (HpChallengeService.JobBranch branch : List.of(
                    HpChallengeService.JobBranch.WARRIOR,
                    HpChallengeService.JobBranch.MAGE,
                    HpChallengeService.JobBranch.BOWMAN,
                    HpChallengeService.JobBranch.THIEF,
                    HpChallengeService.JobBranch.PIRATE)) {
                int reservedQuestId = LifeProofQuest.reservedQuestId(stage, branch);
                for (int slot = LifeProofQuest.OPTION_SLOT_START + LifeProofQuest.OPTIONAL_REQUIRED_COUNT;
                     slot <= LifeProofQuest.OPTION_SLOT_END; slot++) {
                    int retiredQuestId = LifeProofQuest.questId(stage, branch, slot);
                    Element retiredInfo = topLevelImgDir(infoDocument, retiredQuestId);
                    assertHiddenQuestInfoNotVisible(retiredInfo, retiredQuestId);
                    Element retired = topLevelImgDir(checkDocument, retiredQuestId);
                    assertHiddenQuestCheckNotStartable(retired, reservedQuestId, retiredQuestId, questDir);
                    assertEquals("", childValue(retiredInfo, "string", "parent"),
                            "retired optional QuestInfo.parent must be omitted from quest list: "
                                    + retiredQuestId);
                    assertEquals("", childValue(retiredInfo, "int", "order"),
                            "retired optional QuestInfo.order must be omitted from quest list: "
                                    + retiredQuestId);
                }
                for (int slot = LifeProofQuest.BRIDGE_SLOT_START; slot < LifeProofQuest.RESERVED_SLOT; slot++) {
                    int bridgeQuestId = LifeProofQuest.questId(stage, branch, slot);
                    Element bridgeInfo = topLevelImgDir(infoDocument, bridgeQuestId);
                    assertHiddenQuestInfoNotVisible(bridgeInfo, bridgeQuestId);
                    assertEquals("", childValue(bridgeInfo, "string", "parent"),
                            "bridge QuestInfo.parent must be omitted: " + bridgeQuestId);
                    assertEquals("", childValue(bridgeInfo, "int", "order"),
                            "bridge QuestInfo.order must be omitted: " + bridgeQuestId);
                    Element bridge = topLevelImgDir(checkDocument, bridgeQuestId);
                    assertHiddenQuestCheckNotStartable(bridge, reservedQuestId, bridgeQuestId, questDir);
                }
            }
        }
    }

    @Test
    void lifeProofCheckNodesMatchQuestMetadata() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveQuestXml("wz-zh-CN/Quest.wz/Check.img.xml").toFile());

        for (LifeProofQuest.QuestMeta meta : LifeProofQuest.allVisibleQuests()) {
            Element quest = topLevelImgDir(document, meta.questId());
            Element start = childImgDir(quest, "0");
            Element complete = childImgDir(quest, "1");

            assertEquals(Integer.toString(LifeProofQuest.startNpcId(meta)), childValue(start, "int", "npc"),
                    "start npc must match LifeProofQuest metadata: " + meta.questId());
            assertEquals(Integer.toString(HpChallengeService.stage(meta.stage()).requiredLevel()),
                    childValue(start, "int", "lvmin"),
                    "lvmin must match stage metadata: " + meta.questId());
            assertEquals("lifeProof", childValue(start, "string", "startscript"),
                    "startscript must use lifeProof: " + meta.questId());
            assertJobRequirement(start, meta);
            assertStartRequirement(start, meta);

            assertEquals(Integer.toString(LifeProofQuest.completeNpcId(meta)), childValue(complete, "int", "npc"),
                    "complete npc must match LifeProofQuest metadata: " + meta.questId());
            assertEquals("lifeProof", childValue(complete, "string", "endscript"),
                    "endscript must use lifeProof: " + meta.questId());
            assertCompletionGate(complete, meta);
        }
    }

    private static void assertJobRequirement(Element start, LifeProofQuest.QuestMeta meta) {
        Element job = childImgDir(start, "job");
        List<Integer> jobIds = LifeProofQuest.branchInfo(meta.branch()).jobIds();
        for (int i = 0; i < jobIds.size(); i++) {
            assertEquals(Integer.toString(jobIds.get(i)), childValue(job, "int", Integer.toString(i)),
                    "job requirement must match branch metadata: " + meta.questId());
        }
        assertEquals("", childValue(job, "int", Integer.toString(jobIds.size())),
                "job requirement must not include another branch job: " + meta.questId());
    }

    private static void assertStartRequirement(Element start, LifeProofQuest.QuestMeta meta) {
        int expectedQuestId = expectedStartRequirement(meta);
        Element quest = childImgDirOrNull(start, "quest");
        if (expectedQuestId <= 0) {
            assertNull(quest, "first life proof quest must not require previous quest: " + meta.questId());
            return;
        }
        assertEquals(Integer.toString(expectedQuestId),
                childValue(start, "quest", "0", "int", "id"),
                "start quest requirement must match metadata: " + meta.questId());
        assertEquals("2",
                childValue(start, "quest", "0", "int", "state"),
                "start quest requirement must require completed previous marker: " + meta.questId());
    }

    private static int expectedStartRequirement(LifeProofQuest.QuestMeta meta) {
        return switch (meta.kind()) {
            case MAIN -> {
                if (meta.slot() == LifeProofQuest.MAIN_SLOT_START) {
                    yield meta.stage() == 1
                            ? 0
                            : LifeProofQuest.questId(meta.stage() - 1, meta.branch(), LifeProofQuest.REWARD_SLOT);
                }
                yield previousVisibleQuest(meta).questId();
            }
            case SELECTOR -> meta.selectorNo() == 1
                    ? previousVisibleQuest(meta).questId()
                    : LifeProofQuest.questId(meta.stage(), meta.branch(),
                            LifeProofQuest.OPTION_SLOT_START + meta.selectorNo() - 2);
            case OPTION_SLOT -> LifeProofQuest.questId(meta.stage(), meta.branch(),
                    LifeProofQuest.SELECTOR_SLOT_START + meta.selectorNo() - 1);
            case REWARD -> LifeProofQuest.questId(meta.stage(), meta.branch(),
                    LifeProofQuest.OPTION_SLOT_START + LifeProofQuest.OPTIONAL_REQUIRED_COUNT - 1);
            case BRIDGE, RESERVED, RETIRED_OPTION -> 0;
        };
    }

    private static void assertCompletionGate(Element complete, LifeProofQuest.QuestMeta meta) {
        LifeProofQuest.Objective objective = meta.objective();
        switch (objective.type()) {
            case KILL, BOSS -> assertMobGate(complete, meta);
            case ITEM -> assertItemGate(complete, meta);
            case MESO -> assertEquals(Integer.toString(objective.mesoCost()), childValue(complete, "int", "money"),
                    "meso completion gate must match metadata: " + meta.questId());
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL, SELECT_OPTION, OPTION_SLOT ->
                    assertEquals(String.format("%03d", objective.requiredCount()),
                            childValue(complete, "infoex", "0", "string", "value"),
                            "custom progress completion gate must match metadata: " + meta.questId());
            case NPC_TALK, REWARD -> {
                assertNull(childImgDirOrNull(complete, "mob"),
                        "NPC/reward completion must not add mob gate: " + meta.questId());
                assertNull(childImgDirOrNull(complete, "item"),
                        "NPC/reward completion must not add item gate: " + meta.questId());
                assertNull(childImgDirOrNull(complete, "infoex"),
                        "NPC/reward completion must not add infoex gate: " + meta.questId());
                assertEquals("", childValue(complete, "int", "money"),
                        "NPC/reward completion must not add money gate: " + meta.questId());
            }
        }
    }

    private static void assertMobGate(Element complete, LifeProofQuest.QuestMeta meta) {
        Element mob = childImgDir(complete, "mob");
        List<Element> gates = childImgDirs(mob);
        assertEquals(meta.objective().targetIds().size(), gates.size(),
                "mob gate target count must match metadata: " + meta.questId());
        for (int i = 0; i < meta.objective().targetIds().size(); i++) {
            Element gate = gates.get(i);
            assertEquals(Integer.toString(meta.objective().targetIds().get(i)), childValue(gate, "int", "id"),
                    "mob gate id must match metadata: " + meta.questId());
            assertEquals(Integer.toString(meta.objective().requiredCount()), childValue(gate, "int", "count"),
                    "mob gate count must match metadata: " + meta.questId());
        }
    }

    private static void assertMobProgressMacros(Document check, String detail, LifeProofQuest.QuestMeta meta) {
        Element complete = childImgDir(topLevelImgDir(check, meta.questId()), "1");
        Element mob = childImgDir(complete, "mob");
        List<Element> gates = childImgDirs(mob);
        assertEquals(meta.objective().targetIds().size(), gates.size(),
                "mob progress macro target count must match metadata: " + meta.questId());
        for (int i = 0; i < gates.size(); i++) {
            int mobId = Integer.parseInt(childValue(gates.get(i), "int", "id"));
            String expected = "#o" + mobId + "# #r#a" + meta.questId() + (i + 1) + "##k";
            assertTrue(detail.contains(expected),
                    "#a index must follow WZ mob insertion order for quest " + meta.questId() + ": " + expected);
            assertProgressMacroWithoutTargetSuffix(detail, "#r#a" + meta.questId() + (i + 1) + "##k",
                    meta.questId());
            assertEquals(meta.objective().targetIds().get(i), mobId,
                    "WZ mob order must match LifeProofQuest metadata: " + meta.questId());
        }
    }

    private static void assertProgressMacroWithoutTargetSuffix(String detail, String macro, int questId) {
        assertTrue(detail.contains(macro), "quest detail must contain progress macro for quest " + questId + ": "
                + macro);
        assertFalse(Pattern.compile(Pattern.quote(macro) + "\\s*/\\s*\\d+").matcher(detail).find(),
                "quest detail must not append explicit target count after client progress macro: " + questId);
    }

    private static void assertLifeProofQuestDetailComplete(String detail, LifeProofQuest.QuestMeta meta) {
        assertTrue(detail.contains("#e任务进度#n"),
                "life proof quest detail must use bold progress header: " + meta.questId());
        assertTrue(detail.contains("@@BD_LP_PROGRESS:" + meta.questId() + "@@"),
                "life proof quest detail must contain hook progress marker: " + meta.questId());
        assertFalse(detail.contains("#a"),
                "life proof quest detail must not use client #a macro: " + meta.questId());
        assertFalse(detail.contains("..."),
                "quest detail must not rely on placeholder ellipsis for quest " + meta.questId());
    }

    private static void assertLifeProofProgressMarker(String detail, int questId) {
        assertTrue(detail.contains("@@BD_LP_PROGRESS:" + questId + "@@"),
                "quest detail must contain hook progress marker for quest " + questId);
        assertFalse(Pattern.compile("#a" + questId + "\\d#").matcher(detail).find(),
                "non-mob life proof quest detail must not use client #a macro: " + questId);
    }

    @Test
    void mesoProgressUsesSingleVirtualStep() {
        LifeProofQuest.QuestMeta meso = LifeProofQuest.allQuestMetas()
                .stream()
                .filter(meta -> meta.objective().type() == LifeProofQuest.ObjectiveType.MESO)
                .findFirst()
                .orElseThrow();

        assertEquals("000", LifeProofQuest.mesoProgressValue(meso, meso.objective().mesoCost() - 1));
        assertEquals("001", LifeProofQuest.mesoProgressValue(meso, meso.objective().mesoCost()));
        assertEquals("001", LifeProofQuest.mesoProgressValue(meso, meso.objective().mesoCost() + 1));
    }

    private static void assertItemGate(Element complete, LifeProofQuest.QuestMeta meta) {
        assertEquals(Integer.toString(meta.objective().itemId()),
                childValue(complete, "item", "0", "int", "id"),
                "item gate id must match metadata: " + meta.questId());
        assertEquals(Integer.toString(meta.objective().requiredCount()),
                childValue(complete, "item", "0", "int", "count"),
                "item gate count must match metadata: " + meta.questId());
    }

    private static Object bean(Map<Class<?>, Object> beans, Class<?> type) {
        return beans.computeIfAbsent(type, key -> mock(key));
    }

    private static Character newLifeProofCharacter(Job job) {
        Client client = new CapturingClient();
        Character chr = Character.getDefault(client);
        client.setPlayer(chr);
        chr.setId(10001);
        chr.setLevel(180);
        chr.setJob(job);
        return chr;
    }

    private static void putQuest(Character chr, int questId, QuestStatus.Status status, String progress) {
        QuestStatus questStatus = new QuestStatus(Quest.getInstance(questId), status, 1032001);
        if (progress != null) {
            questStatus.setProgress(PROGRESS_KEY, progress);
        }
        chr.getQuests().put((short) questId, questStatus);
    }

    private static void assertCompletedVisibleCount(Character chr, int stage, HpChallengeService.JobBranch branch,
                                                    int expectedCount) {
        int completed = 0;
        for (LifeProofQuest.QuestMeta meta : LifeProofQuest.stageBranchVisibleQuests(stage, branch)) {
            assertQuestStatus(chr, meta.questId(), QuestStatus.Status.COMPLETED);
            completed++;
        }
        assertEquals(expectedCount, completed, "visible completed count for stage " + stage + " " + branch);
    }

    private static void assertQuestStatus(Character chr, int questId, QuestStatus.Status status) {
        assertEquals(status.getId(), chr.getQuestStatus(questId), "quest " + questId);
    }

    private static List<LifeProofQuest.QuestMeta> stageBranchVisibleQuests(LifeProofQuest.QuestMeta meta) {
        return LifeProofQuest.stageBranchVisibleQuests(meta.stage(), meta.branch());
    }

    private static LifeProofQuest.QuestMeta previousVisibleQuest(LifeProofQuest.QuestMeta meta) {
        List<LifeProofQuest.QuestMeta> visible = stageBranchVisibleQuests(meta);
        int index = visible.indexOf(meta);
        if (index <= 0) {
            throw new AssertionError("missing previous visible quest for " + meta.questId());
        }
        return visible.get(index - 1);
    }

    private static Set<Integer> topLevelLifeProofQuestIds(Path path, boolean includeOldRange) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
        Set<Integer> ids = new HashSet<>();
        for (Element quest : topLevelImgDirs(document)) {
            String name = quest.getAttribute("name");
            if (!name.matches("\\d+")) {
                continue;
            }
            int questId = Integer.parseInt(name);
            if (LifeProofQuest.isQuestId(questId) || includeOldRange && questId >= 30100 && questId <= 30999) {
                ids.add(questId);
            }
        }
        return ids;
    }

    private static Set<Integer> topLevelMonsterCardRingQuestIds(Path path) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
        Set<Integer> ids = new HashSet<>();
        for (Element quest : topLevelImgDirs(document)) {
            String name = quest.getAttribute("name");
            if (!name.matches("\\d+")) {
                continue;
            }
            int questId = Integer.parseInt(name);
            if (MonsterCardRingQuest.isQuestId(questId)) {
                ids.add(questId);
            }
        }
        return ids;
    }

    private static List<Element> topLevelImgDirs(Document document) {
        NodeList children = document.getDocumentElement().getChildNodes();
        return java.util.stream.IntStream.range(0, children.getLength())
                .mapToObj(children::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .filter(element -> "imgdir".equals(element.getTagName()))
                .toList();
    }

    private static List<Element> childImgDirs(Element parent) {
        NodeList children = parent.getChildNodes();
        return java.util.stream.IntStream.range(0, children.getLength())
                .mapToObj(children::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .filter(element -> "imgdir".equals(element.getTagName()))
                .toList();
    }

    private static Element topLevelImgDir(Document document, int questId) {
        for (Element element : topLevelImgDirs(document)) {
            if (Integer.toString(questId).equals(element.getAttribute("name"))) {
                return element;
            }
        }
        throw new AssertionError("missing top-level quest node: " + questId);
    }

    private static String childValue(Element parent, String tagName, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child
                    && tagName.equals(child.getTagName())
                    && childName.equals(child.getAttribute("name"))) {
                return child.getAttribute("value");
            }
        }
        return "";
    }

    private static String childValue(Element parent, String firstImgDir, String secondImgDir, String thirdImgDir,
                                     String tagName, String childName) {
        Element first = childImgDir(parent, firstImgDir);
        Element second = childImgDir(first, secondImgDir);
        Element third = childImgDir(second, thirdImgDir);
        return childValue(third, tagName, childName);
    }

    private static String childValue(Element parent, String firstImgDir, String secondImgDir,
                                     String tagName, String childName) {
        Element first = childImgDir(parent, firstImgDir);
        Element second = childImgDir(first, secondImgDir);
        return childValue(second, tagName, childName);
    }

    private static Element childImgDir(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child
                    && "imgdir".equals(child.getTagName())
                    && childName.equals(child.getAttribute("name"))) {
                return child;
            }
        }
        throw new AssertionError("missing child imgdir " + childName + " under " + parent.getAttribute("name"));
    }

    private static void assertQuestStartAndEndNpc(Document document, int questId, int startNpcId, int endNpcId) {
        Element quest = topLevelImgDir(document, questId);
        assertEquals(Integer.toString(startNpcId), childValue(childImgDir(quest, "0"), "int", "npc"),
                "quest start NPC must be branch instructor: " + questId);
        assertEquals(Integer.toString(endNpcId), childValue(childImgDir(quest, "1"), "int", "npc"),
                "quest end NPC must be target instructor: " + questId);
        assertNull(childImgDirOrNull(childImgDir(quest, "1"), "infoex"),
                "NPC talk quest must not require infoex before the client shows target NPC completion icon: " + questId);
        assertFalse(hasProofItemRequirement(quest),
                "NPC talk quest must not require proof item after InteractionHook handles progress: " + questId);
    }

    private static void assertQuestInfoContains(Document document, int questId, String expectedText) {
        Element quest = topLevelImgDir(document, questId);
        assertTrue(childValue(quest, "string", "1").contains(expectedText),
                "life proof QuestInfo text must mention target instructor completion for quest " + questId);
    }

    private static boolean hasProofItemRequirement(Element quest) {
        NodeList values = quest.getElementsByTagName("int");
        for (int i = 0; i < values.getLength(); i++) {
            if (values.item(i) instanceof Element value
                    && "id".equals(value.getAttribute("name"))
                    && Integer.toString(LifeProofQuest.PROOF_ITEM_ID).equals(value.getAttribute("value"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCompletionGate(Element complete) {
        return childImgDirOrNull(complete, "mob") != null
                || childImgDirOrNull(complete, "item") != null
                || childImgDirOrNull(complete, "infoex") != null
                || !childValue(complete, "int", "money").isEmpty();
    }

    private static boolean requiresInfoExCompletionGate(LifeProofQuest.QuestMeta meta) {
        return switch (meta.objective().type()) {
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL, SELECT_OPTION,
                 OPTION_SLOT -> true;
            default -> false;
        };
    }

    private static Element childImgDirOrNull(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child
                    && "imgdir".equals(child.getTagName())
                    && childName.equals(child.getAttribute("name"))) {
                return child;
            }
        }
        return null;
    }

    private static Path resolveQuestXml(String relativePath) {
        Path modulePath = Path.of(relativePath);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("gms-server").resolve(relativePath);
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

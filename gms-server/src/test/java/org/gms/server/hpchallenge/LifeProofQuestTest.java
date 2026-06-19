package org.gms.server.hpchallenge;

import org.gms.server.quest.MonsterCardRingQuest;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeProofQuestTest {

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
        assertEquals(645, LifeProofQuest.allVisibleQuests().size());

        int bridge = LifeProofQuest.questId(1, HpChallengeService.JobBranch.WARRIOR, LifeProofQuest.BRIDGE_SLOT_START);
        assertTrue(LifeProofQuest.isQuestId(bridge));
        assertFalse(LifeProofQuest.isVisibleQuestId(bridge));
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
        assertTrue(LifeProofQuest.canOpenProgressAtNpc(5125, 1032001));
        assertTrue(LifeProofQuest.canOpenProgressAtNpc(5125, 1012100));
        assertFalse(LifeProofQuest.canOpenProgressAtNpc(5125, 1022000));
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
        assertQuestStartAndEndNpc(document, 5101, 1022000, 1032001);
        assertQuestStartAndEndNpc(document, 5102, 1022000, 1052001);
        assertQuestStartAndEndNpc(document, 5103, 1022000, 1090000);
        assertQuestStartAndEndNpc(document, 5104, 1022000, 1022000);

        assertQuestStartAndEndNpc(document, 5125, 1032001, 1012100);
        assertQuestStartAndEndNpc(document, 5126, 1032001, 1022000);
        assertQuestStartAndEndNpc(document, 5127, 1032001, 1052001);
        assertQuestStartAndEndNpc(document, 5128, 1032001, 1090000);
        assertQuestStartAndEndNpc(document, 5129, 1032001, 1032001);

        assertQuestStartAndEndNpc(document, 5150, 1012100, 1032001);
        assertQuestStartAndEndNpc(document, 5151, 1012100, 1022000);
        assertQuestStartAndEndNpc(document, 5152, 1012100, 1052001);
        assertQuestStartAndEndNpc(document, 5153, 1012100, 1090000);
        assertQuestStartAndEndNpc(document, 5154, 1012100, 1012100);

        assertQuestStartAndEndNpc(document, 5175, 1052001, 1012100);
        assertQuestStartAndEndNpc(document, 5176, 1052001, 1032001);
        assertQuestStartAndEndNpc(document, 5177, 1052001, 1022000);
        assertQuestStartAndEndNpc(document, 5178, 1052001, 1090000);
        assertQuestStartAndEndNpc(document, 5179, 1052001, 1052001);

        assertQuestStartAndEndNpc(document, 5200, 1090000, 1012100);
        assertQuestStartAndEndNpc(document, 5201, 1090000, 1032001);
        assertQuestStartAndEndNpc(document, 5202, 1090000, 1022000);
        assertQuestStartAndEndNpc(document, 5203, 1090000, 1052001);
        assertQuestStartAndEndNpc(document, 5204, 1090000, 1090000);
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
            assertEquals(LifeProofQuest.startNpcId(meta), LifeProofQuest.branchInfo(meta.branch()).instructorNpcId());
            assertEquals(meta.objective().targetIds().getFirst(), LifeProofQuest.completeNpcId(meta));
        }
    }

    @Test
    void npcTalkQuestInfoTellsPlayerToCompleteAtTargetInstructor() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveQuestXml("wz-zh-CN/Quest.wz/QuestInfo.img.xml").toFile());

        assertQuestInfoContains(document, 5100, "前往#p1012100#，点击任务完成图标");
        assertQuestInfoContains(document, 5126, "前往#p1022000#，点击任务完成图标");
        assertQuestInfoContains(document, 5204, "前往#p1090000#，点击任务完成图标");
    }

    @Test
    void mapTasksAreConvertedToCollectionItems() {
        LifeProofQuest.QuestMeta t2Common = LifeProofQuest.stageBranchVisibleQuests(2, HpChallengeService.JobBranch.WARRIOR)
                .stream()
                .filter(meta -> meta.questId() == 5225)
                .findFirst()
                .orElseThrow();
        assertEquals(LifeProofQuest.ObjectiveType.ITEM, t2Common.objective().type());
        assertEquals(4033001, t2Common.objective().itemId());
        assertEquals(20, t2Common.objective().requiredCount());

        LifeProofQuest.QuestMeta t1OptionalMap = LifeProofQuest.stageBranchVisibleQuests(1, HpChallengeService.JobBranch.WARRIOR)
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

        assertEquals(136, gated, "life proof custom progress completion gate count");
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
            assertEquals("", childValue(quest, "string", "parent"),
                    "life proof QuestInfo.parent must be omitted for quest " + questId + " in " + path);
            assertEquals("", childValue(quest, "int", "order"),
                    "life proof QuestInfo.order must be omitted for quest " + questId + " in " + path);
            assertEquals("31", childValue(quest, "int", "area"),
                    "life proof QuestInfo.area must stay in Legend Road for quest " + questId + " in " + path);
        }

        assertEquals(645, count, "visible life proof quest count in " + path);
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

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(infoPath.toFile());
        for (int questId = MonsterCardRingQuest.CLAIM_QUEST_ID; questId <= MonsterCardRingQuest.LAST_QUEST_ID; questId++) {
            Element quest = topLevelImgDir(document, questId);
            assertEquals("31", childValue(quest, "int", "area"),
                    "monster card ring QuestInfo.area must stay in Legend Road for quest " + questId + " in " + infoPath);
        }

        Set<Integer> infoIds = topLevelMonsterCardRingQuestIds(infoPath);
        Set<Integer> checkIds = topLevelMonsterCardRingQuestIds(checkPath);
        Set<Integer> actIds = topLevelMonsterCardRingQuestIds(actPath);
        assertEquals(11, infoIds.size(), "monster card ring QuestInfo count in " + infoPath);
        assertEquals(infoIds, checkIds, "Check.img must contain every monster card ring quest in " + questDir);
        assertEquals(infoIds, actIds, "Act.img must contain every monster card ring quest in " + questDir);
    }

    private static void assertNoCustomQuestSeries(Path path) throws Exception {
        assertTrue(topLevelLifeProofQuestIds(path, true).isEmpty(),
                "base wz must not contain life proof quest nodes in " + path);
        assertTrue(topLevelMonsterCardRingQuestIds(path).isEmpty(),
                "base wz must not contain monster card ring quest nodes in " + path);
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

        Document checkDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(checkPath.toFile());
        for (int stage = 1; stage <= 7; stage++) {
            for (HpChallengeService.JobBranch branch : List.of(
                    HpChallengeService.JobBranch.WARRIOR,
                    HpChallengeService.JobBranch.MAGE,
                    HpChallengeService.JobBranch.BOWMAN,
                    HpChallengeService.JobBranch.THIEF,
                    HpChallengeService.JobBranch.PIRATE)) {
                int reservedQuestId = LifeProofQuest.reservedQuestId(stage, branch);
                for (int slot = LifeProofQuest.BRIDGE_SLOT_START; slot < LifeProofQuest.RESERVED_SLOT; slot++) {
                    int bridgeQuestId = LifeProofQuest.questId(stage, branch, slot);
                    Element bridge = topLevelImgDir(checkDocument, bridgeQuestId);
                    assertEquals(Integer.toString(reservedQuestId),
                            childValue(bridge, "0", "quest", "0", "int", "id"),
                            "bridge quest must depend on its reserved lock quest in " + questDir + ": " + bridgeQuestId);
                    assertEquals("2",
                            childValue(bridge, "0", "quest", "0", "int", "state"),
                            "bridge quest lock must require completed reserved quest in " + questDir + ": " + bridgeQuestId);
                }
            }
        }
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
            case PQ_ANY, PQ_PIRATE, PQ_TOY_OR_PIRATE, SCROLL_100, JUMP_MANUAL, SELECT_OPTION -> true;
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
}

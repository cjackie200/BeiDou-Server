package org.gms.server.hpchallenge;

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
        assertLifeProofQuestInfoClientSafe(resolveQuestXml("wz/Quest.wz/QuestInfo.img.xml"));
        assertLifeProofQuestInfoClientSafe(resolveQuestXml("wz-zh-CN/Quest.wz/QuestInfo.img.xml"));
    }

    @Test
    void questWzDoesNotContainClientDanglingLifeProofNodes() throws Exception {
        assertLifeProofQuestWzClientSafe("wz/Quest.wz");
        assertLifeProofQuestWzClientSafe("wz-zh-CN/Quest.wz");
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
            assertEquals("30", childValue(quest, "int", "area"),
                    "life proof QuestInfo.area must stay in General for quest " + questId + " in " + path);
        }

        assertEquals(645, count, "visible life proof quest count in " + path);
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

    private static Path resolveQuestXml(String relativePath) {
        Path modulePath = Path.of(relativePath);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("gms-server").resolve(relativePath);
    }
}

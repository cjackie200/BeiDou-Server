package org.gms.server.hpchallenge;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeProofQuestTest {

    @Test
    void questIdsFollowStageJobBlockFormula() {
        assertEquals(30100, LifeProofQuest.questId(1, HpChallengeService.JobBranch.WARRIOR, 0));
        assertEquals(30125, LifeProofQuest.questId(1, HpChallengeService.JobBranch.MAGE, 0));
        assertEquals(30225, LifeProofQuest.questId(2, HpChallengeService.JobBranch.WARRIOR, 0));
        assertEquals(30950, LifeProofQuest.questId(7, HpChallengeService.JobBranch.PIRATE, 0));
        assertEquals(30970, LifeProofQuest.questId(7, HpChallengeService.JobBranch.PIRATE, LifeProofQuest.REWARD_SLOT));
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

        assertEquals(30100, quests.get(0).questId());
        assertEquals(List.of(1012100), quests.get(0).objective().targetIds());
        assertEquals(30104, quests.get(4).questId());
        assertEquals(List.of(1022000), quests.get(4).objective().targetIds());
    }

    @Test
    void mapTasksAreConvertedToCollectionItems() {
        LifeProofQuest.QuestMeta t2Common = LifeProofQuest.stageBranchVisibleQuests(2, HpChallengeService.JobBranch.WARRIOR)
                .stream()
                .filter(meta -> meta.questId() == 30225)
                .findFirst()
                .orElseThrow();
        assertEquals(LifeProofQuest.ObjectiveType.ITEM, t2Common.objective().type());
        assertEquals(4033001, t2Common.objective().itemId());
        assertEquals(20, t2Common.objective().requiredCount());

        LifeProofQuest.QuestMeta t1OptionalMap = LifeProofQuest.stageBranchVisibleQuests(1, HpChallengeService.JobBranch.WARRIOR)
                .stream()
                .filter(meta -> meta.questId() == 30115)
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
    void questInfoOrderStaysWithinClientSafeRange() throws Exception {
        assertLifeProofQuestInfoOrder(resolveQuestInfoXml("wz/Quest.wz/QuestInfo.img.xml"));
        assertLifeProofQuestInfoOrder(resolveQuestInfoXml("wz-zh-CN/Quest.wz/QuestInfo.img.xml"));
    }

    private static void assertLifeProofQuestInfoOrder(Path path) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
        NodeList quests = document.getDocumentElement().getElementsByTagName("imgdir");
        int count = 0;
        int maxOrder = 0;

        for (int i = 0; i < quests.getLength(); i++) {
            Element quest = (Element) quests.item(i);
            String name = quest.getAttribute("name");
            if (!name.matches("\\d+")) {
                continue;
            }
            int questId = Integer.parseInt(name);
            if (!LifeProofQuest.isVisibleQuestId(questId)) {
                continue;
            }
            if (!"生命之证".equals(childValue(quest, "string", "parent"))) {
                continue;
            }

            count++;
            int order = Integer.parseInt(childValue(quest, "int", "order"));
            maxOrder = Math.max(maxOrder, order);
            assertTrue(order >= 1 && order <= 31, "unsafe QuestInfo.order for quest " + questId + " in " + path);
        }

        assertEquals(645, count, "visible life proof quest count in " + path);
        assertEquals(21, maxOrder, "life proof max QuestInfo.order in " + path);
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
        throw new AssertionError("missing child " + childName + " under " + parent.getAttribute("name"));
    }

    private static Path resolveQuestInfoXml(String relativePath) {
        Path modulePath = Path.of(relativePath);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("gms-server").resolve(relativePath);
    }
}

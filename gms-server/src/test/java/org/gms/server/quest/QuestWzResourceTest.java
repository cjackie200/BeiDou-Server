package org.gms.server.quest;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestWzResourceTest {
    private static final List<Integer> RESTORED_CHECK_IDS = concat(
            range(4100, 4108),
            range(8530, 8534),
            range(8536, 8539),
            List.of(30000, 30001, 30004));
    private static final List<Integer> RESTORED_ACT_IDS = concat(
            RESTORED_CHECK_IDS,
            List.of(30002, 30003));
    private static final List<Integer> DISABLED_IDS = concat(
            range(8510, 8515),
            List.of(4490, 8540, 8541, 29000));

    @Test
    void restoredQuestNodesAreCompleteAndDisabledGroupsAreAbsent() throws Exception {
        Document questInfo = parse("QuestInfo.img.xml");
        Document check = parse("Check.img.xml");
        Document act = parse("Act.img.xml");
        Document say = parse("Say.img.xml");

        for (int questId : RESTORED_CHECK_IDS) {
            assertEquals(1, topLevelCount(questInfo, questId), "QuestInfo quest " + questId);
            assertEquals(1, topLevelCount(check, questId), "Check quest " + questId);
        }
        for (int questId : RESTORED_ACT_IDS) {
            assertEquals(1, topLevelCount(act, questId), "Act quest " + questId);
        }

        for (int questId : DISABLED_IDS) {
            assertEquals(0, topLevelCount(questInfo, questId), "disabled QuestInfo quest " + questId);
            assertEquals(0, topLevelCount(check, questId), "disabled Check quest " + questId);
            assertEquals(0, topLevelCount(act, questId), "disabled Act quest " + questId);
            assertEquals(0, topLevelCount(say, questId), "disabled Say quest " + questId);
        }

        assertEquals(0, topLevelCount(say, 30005), "scripted remote quest must not keep dead Say data");
        assertEquals(1, topLevelCount(questInfo, 29580), "internal eligibility QuestInfo");
        assertEquals(1, topLevelCount(check, 29580), "internal eligibility Check");
        assertEquals(1, topLevelCount(act, 29580), "internal eligibility Act");
        assertEquals(0, topLevelCount(say, 29580), "internal eligibility quest has no Say node");
    }

    @Test
    void customizedBeginnerQuestNodesWereNotReplacedByMasterData() throws Exception {
        Document check = parse("Check.img.xml");
        Document act = parse("Act.img.xml");

        assertCustomizedMobCheck(check, 30002, 9409001);
        assertCustomizedMobCheck(check, 30003, 9409000);

        Element quest30005Check = topLevelQuest(check, 30005);
        assertEquals("40", value(child(quest30005Check, "imgdir", "0"), "int", "lvmin"));
        assertEquals("1440", value(child(quest30005Check, "imgdir", "0"), "int", "interval"));
        assertEquals("30005", value(child(quest30005Check, "imgdir", "0"), "string", "startscript"));
        assertEquals("30005", value(child(quest30005Check, "imgdir", "1"), "string", "endscript"));

        Element quest29508Check = topLevelQuest(check, 29508);
        assertEquals("q29508s", value(child(quest29508Check, "imgdir", "0"), "string", "startscript"));
        Element eligibility = child(child(child(quest29508Check, "imgdir", "1"), "imgdir", "quest"),
                "imgdir", "0");
        assertEquals("29580", value(eligibility, "int", "id"));
        assertEquals("1", value(eligibility, "int", "state"));

        Element quest30005Act = topLevelQuest(act, 30005);
        Element completion = child(quest30005Act, "imgdir", "1");
        assertEquals("10000000", value(completion, "int", "money"));
        Element reward = child(child(completion, "imgdir", "item"), "imgdir", "0");
        assertEquals("2340000", value(reward, "int", "id"));
        assertEquals("10", value(reward, "int", "count"));
    }

    @Test
    void npcScriptsDoNotDuplicateNativeQuest8538And8539ItemActions() throws Exception {
        for (String scriptDirectory : List.of("scripts", "scripts-zh-CN")) {
            String juniorMonk = Files.readString(resolveNpcScript(scriptDirectory, "9310052.js"));
            String seniorMonk = Files.readString(resolveNpcScript(scriptDirectory, "9310040.js"));

            assertFalse(juniorMonk.contains("cm.gainItem(ITEM_LETTER_TO_SENIOR"), scriptDirectory);
            assertFalse(juniorMonk.contains("cm.removeItem(ITEM_LETTER_FROM_SENIOR"), scriptDirectory);
            assertFalse(seniorMonk.contains("cm.removeItem(ITEM_LETTER_TO_SENIOR"), scriptDirectory);
            assertFalse(seniorMonk.contains("cm.gainItem(ITEM_LETTER_FROM_SENIOR"), scriptDirectory);
        }

        String restrictedAreaNpc = Files.readString(resolveNpcScript("scripts-zh-CN", "9310005.js"));
        assertFalse(restrictedAreaNpc.contains("cm.startQuest(QuestID)"));
        assertTrue(restrictedAreaNpc.contains("quest.canStart(cm.getPlayer(), cm.getNpc())"));

        String darkWukongQuest = Files.readString(resolveQuestScript("scripts-zh-CN", "30005.js"));
        assertTrue(darkWukongQuest.contains("qm.canHold(2340000, 10)"));
        assertTrue(darkWukongQuest.contains("quest.complete(qm.getPlayer(), qm.getNpc())"));
        assertFalse(darkWukongQuest.contains("qm.gainItem("));
        assertFalse(darkWukongQuest.contains("qm.gainMeso("));
        assertFalse(darkWukongQuest.contains("qm.forceCompleteQuest("));
    }

    @Test
    void quest8538And8539LettersRemainMarkedAsQuestItems() throws Exception {
        for (String wzDirectory : List.of("wz", "wz-zh-CN")) {
            Document itemData = parseResource(wzDirectory, "Item.wz", "Etc", "0403.img.xml");
            for (String itemNode : List.of("04031786", "04031787")) {
                Element item = topLevelNamed(itemData, itemNode);
                assertEquals("1", value(child(item, "imgdir", "info"), "int", "quest"),
                        wzDirectory + "/" + itemNode);
            }
        }
    }

    @Test
    void correctedQuestInfoUsesItemAndNpcMacrosWithMatchingIds() throws Exception {
        Document questInfo = parse("QuestInfo.img.xml");

        String quest6211 = stringValues(topLevelQuest(questInfo, 6211));
        assertFalse(quest6211.contains("#t1000103#"), quest6211);
        assertTrue(quest6211.contains("#t4000103#"), quest6211);

        String quest2224 = stringValues(topLevelQuest(questInfo, 2224));
        assertFalse(quest2224.contains("#t1032107#"), quest2224);
        assertTrue(quest2224.contains("#p1032107#"), quest2224);

        assertQuestMacro(questInfo, 28271, "10502000", "1052000");
        assertQuestMacro(questInfo, 6920, "20200010", "2020010");
        for (int questId = 29900; questId <= 29903; questId++) {
            assertQuestMacro(questInfo, questId, "900040", "9000040");
        }
    }

    @Test
    void everyFunctionalTopLevelCheckFieldHasAnExplicitServerMapping() throws Exception {
        Document check = parse("Check.img.xml");
        NodeList quests = check.getDocumentElement().getChildNodes();
        for (int i = 0; i < quests.getLength(); i++) {
            if (!(quests.item(i) instanceof Element quest) || !"imgdir".equals(quest.getTagName())) {
                continue;
            }
            NodeList phases = quest.getChildNodes();
            for (int j = 0; j < phases.getLength(); j++) {
                if (!(phases.item(j) instanceof Element phase) || !"imgdir".equals(phase.getTagName())) {
                    continue;
                }
                NodeList fields = phase.getChildNodes();
                for (int k = 0; k < fields.getLength(); k++) {
                    if (!(fields.item(k) instanceof Element field)) {
                        continue;
                    }
                    String name = field.getAttribute("name");
                    if (name.chars().allMatch(java.lang.Character::isDigit)) {
                        continue;
                    }
                    assertFalse(QuestRequirementType.getByWZName(name) == QuestRequirementType.UNDEFINED,
                            "unmapped Check field quest=" + quest.getAttribute("name")
                                    + " phase=" + phase.getAttribute("name") + " field=" + name);
                }
            }
        }
    }

    @Test
    void authoritativeQuestFilesHaveUniqueTopLevelQuestIds() throws Exception {
        for (String fileName : List.of("QuestInfo.img.xml", "Check.img.xml", "Act.img.xml")) {
            Document document = parse(fileName);
            Set<String> questIds = new HashSet<>();
            NodeList children = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element element && "imgdir".equals(element.getTagName())) {
                    assertTrue(questIds.add(element.getAttribute("name")),
                            "duplicate top-level quest " + element.getAttribute("name") + " in " + fileName);
                }
            }
        }
    }

    private static void assertQuestMacro(Document questInfo, int questId, String invalidNpcId, String npcId) {
        String text = stringValues(topLevelQuest(questInfo, questId));
        assertFalse(text.contains(invalidNpcId), "quest " + questId + ": " + text);
        assertTrue(text.contains(npcId), "quest " + questId + ": " + text);
    }

    private static void assertCustomizedMobCheck(Document check, int questId, int mobId) {
        Element quest = topLevelQuest(check, questId);
        Element start = child(quest, "imgdir", "0");
        assertFalse(hasChild(start, "int", "npc"), "quest " + questId + " start npc must stay removed");
        assertFalse(hasChild(start, "string", "startscript"),
                "quest " + questId + " start script must stay removed");

        Element mob = child(child(child(quest, "imgdir", "1"), "imgdir", "mob"), "imgdir", "0");
        assertEquals(Integer.toString(mobId), value(mob, "int", "id"));
        assertEquals("5", value(mob, "int", "count"));
    }

    private static Document parse(String fileName) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveQuestXml(fileName).toFile());
    }

    private static Document parseResource(String... pathParts) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveResource(pathParts).toFile());
    }

    private static Path resolveQuestXml(String fileName) {
        Path modulePath = Path.of("wz-zh-CN", "Quest.wz", fileName);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("gms-server", "wz-zh-CN", "Quest.wz", fileName);
    }

    private static Path resolveResource(String... pathParts) {
        Path modulePath = Path.of(pathParts[0], java.util.Arrays.copyOfRange(pathParts, 1, pathParts.length));
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("gms-server").resolve(modulePath);
    }

    private static Path resolveNpcScript(String scriptDirectory, String fileName) {
        Path modulePath = Path.of(scriptDirectory, "npc", fileName);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("gms-server", scriptDirectory, "npc", fileName);
    }

    private static Path resolveQuestScript(String scriptDirectory, String fileName) {
        Path modulePath = Path.of(scriptDirectory, "quest", fileName);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("gms-server", scriptDirectory, "quest", fileName);
    }

    private static int topLevelCount(Document document, int questId) {
        int count = 0;
        NodeList children = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element
                    && "imgdir".equals(element.getTagName())
                    && Integer.toString(questId).equals(element.getAttribute("name"))) {
                count++;
            }
        }
        return count;
    }

    private static Element topLevelQuest(Document document, int questId) {
        return topLevelNamed(document, Integer.toString(questId));
    }

    private static Element topLevelNamed(Document document, String name) {
        NodeList children = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element
                    && "imgdir".equals(element.getTagName())
                    && name.equals(element.getAttribute("name"))) {
                return element;
            }
        }
        throw new AssertionError("missing top-level node " + name);
    }

    private static Element child(Element parent, String tagName, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element
                    && tagName.equals(element.getTagName())
                    && name.equals(element.getAttribute("name"))) {
                return element;
            }
        }
        throw new AssertionError("missing " + tagName + "/" + name);
    }

    private static boolean hasChild(Element parent, String tagName, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element
                    && tagName.equals(element.getTagName())
                    && name.equals(element.getAttribute("name"))) {
                return true;
            }
        }
        return false;
    }

    private static String value(Element parent, String tagName, String name) {
        return child(parent, tagName, name).getAttribute("value");
    }

    private static String stringValues(Element parent) {
        StringBuilder values = new StringBuilder();
        NodeList strings = parent.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            values.append(((Element) strings.item(i)).getAttribute("value")).append('\n');
        }
        return values.toString();
    }

    private static List<Integer> range(int first, int last) {
        List<Integer> ids = new ArrayList<>();
        for (int id = first; id <= last; id++) {
            ids.add(id);
        }
        return ids;
    }

    @SafeVarargs
    private static List<Integer> concat(List<Integer>... groups) {
        List<Integer> ids = new ArrayList<>();
        for (List<Integer> group : groups) {
            ids.addAll(group);
        }
        return List.copyOf(ids);
    }
}

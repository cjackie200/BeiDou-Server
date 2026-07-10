package org.gms.server.quest;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillBreakthroughQuestTest {
    @Test
    void quest30006HasRequiredLightbulbNodes() throws Exception {
        Element check = child(img("wz-zh-CN/Quest.wz/Check.img.xml"), "30006");
        Element start = child(check, "0");
        assertEquals("9900000", attr(child(start, "npc"), "value"));
        assertEquals("150", attr(child(start, "lvmin"), "value"));
        assertEquals("250", attr(child(start, "lvmax"), "value"));
        Element jobs = child(start, "job");
        List<Integer> expectedJobs = List.of(112, 122, 132, 212, 222, 232, 312, 322, 412, 422, 512, 522);
        for (int i = 0; i < expectedJobs.size(); i++) {
            assertEquals(String.valueOf(expectedJobs.get(i)), attr(child(jobs, String.valueOf(i)), "value"));
        }
        assertEquals("1", attr(child(start, "normalAutoStart"), "value"));
        assertEquals("q30006s", attr(child(start, "startscript"), "value"));

        Element complete = child(check, "1");
        assertEquals("9900000", attr(child(complete, "npc"), "value"));
        assertEquals("q30006e", attr(child(complete, "endscript"), "value"));
        Element mob = child(child(complete, "mob"), "0");
        assertEquals("8800002", attr(child(mob, "id"), "value"));
        assertEquals("1", attr(child(mob, "count"), "value"));

        Element info = child(img("wz-zh-CN/Quest.wz/QuestInfo.img.xml"), "30006");
        assertEquals("突破任务", attr(child(info, "name"), "value"));
        assertEquals("1", attr(child(info, "autoStart"), "value"));
        assertEquals("1", attr(child(info, "autoPreComplete"), "value"));

        Element act = child(img("wz-zh-CN/Quest.wz/Act.img.xml"), "30006");
        assertNotNull(child(act, "0"));
        assertNotNull(child(act, "1"));

        Element say = child(img("wz-zh-CN/Quest.wz/Say.img.xml"), "30006");
        assertNotNull(child(say, "0"));
        assertNotNull(child(say, "1"));
    }

    @Test
    void breakthroughSkillsUseCurrentMaxPlusOneLevels() throws Exception {
        assertSkillLevelValue(1001004, 21, "damage", "380");
        assertSkillLevelValue(3121002, 31, "y", "145");
        assertSkillLevelValue(5221006, 11, "cooltime", "0");
        assertSkillLevelValue(5221006, 11, "speed", "40");
        assertSkillLevelValue(5221006, 11, "jump", "20");
    }

    @Test
    void breakthroughSkillDescriptionsMentionQuestRequirement() throws Exception {
        Element strings = img("wz-zh-CN/String.wz/Skill.img.xml");
        assertTrue(attr(child(child(strings, "1001004"), "h21"), "value").contains("完成150级突破任务后可学习"));
        assertTrue(attr(child(child(strings, "3121002"), "h31"), "value").contains("完成150级突破任务后可学习"));
        assertTrue(attr(child(child(strings, "5221006"), "h11"), "value").contains("完成150级突破任务后可学习"));
    }

    @Test
    void breakthroughServiceRegistersSupportedJobsAndSkills() {
        assertTrue(SkillBreakthroughService.isSupportedJob(112));
        assertTrue(SkillBreakthroughService.isSupportedJob(522));
        assertTrue(SkillBreakthroughService.isBreakthroughSkill(1001004));
        assertTrue(SkillBreakthroughService.isBreakthroughSkill(5221006));
    }

    @Test
    void breakthroughQuestRegistersZakumProgressRequirement() {
        assertTrue(SkillBreakthroughService.isQuestId(30006));
        assertTrue(SkillBreakthroughService.isQuestMob(30006, 8800002));
        assertEquals(1, SkillBreakthroughService.getRequiredMobKills(30006, 8800002));
    }

    private static void assertSkillLevelValue(int skillId, int level, String field, String value) throws Exception {
        Element levelNode = child(child(child(img("wz/Skill.wz/" + (skillId / 10000) + ".img.xml"),
                "skill"), String.valueOf(skillId)), "level");
        Element breakthroughLevel = child(levelNode, String.valueOf(level));
        assertEquals(value, attr(child(breakthroughLevel, field), "value"));
    }

    private static Element img(String relativePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document document = factory.newDocumentBuilder().parse(resolveResource(relativePath).toFile());
        return document.getDocumentElement();
    }

    private static Path resolveResource(String relativePath) {
        Path modulePath = Path.of(relativePath);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("gms-server").resolve(relativePath);
    }

    private static Element child(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && name.equals(element.getAttribute("name"))) {
                return element;
            }
        }
        throw new AssertionError("Missing child '" + name + "' under '" + parent.getAttribute("name") + "'");
    }

    private static String attr(Element element, String name) {
        assertNotNull(element);
        return element.getAttribute(name);
    }
}

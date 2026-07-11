package org.gms.server.quest;

import org.gms.client.Character;
import org.gms.client.Job;
import org.gms.client.QuestStatus;
import org.gms.client.Skill;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillBreakthroughQuestTest {
    @Test
    void stagedBreakthroughQuestsHaveRequiredLightbulbNodes() throws Exception {
        int[] questIds = {30006, 30007, 30008, 30009};
        int[] levels = {8, 30, 70, 120};
        int[] mobs = {2220000, 3220000, 7220000, 8800002};
        int[] kills = {3, 3, 3, 1};
        for (int i = 0; i < questIds.length; i++) {
            String questId = String.valueOf(questIds[i]);
            Element check = child(img("wz-zh-CN/Quest.wz/Check.img.xml"), questId);
            Element start = child(check, "0");
            assertEquals(String.valueOf(levels[i]), attr(child(start, "lvmin"), "value"));
            assertEquals("1", attr(child(start, "normalAutoStart"), "value"));
            assertEquals("q" + questId + "s", attr(child(start, "startscript"), "value"));

            Element complete = child(check, "1");
            assertEquals("q" + questId + "e", attr(child(complete, "endscript"), "value"));
            Element mob = child(child(complete, "mob"), "0");
            assertEquals(String.valueOf(mobs[i]), attr(child(mob, "id"), "value"));
            assertEquals(String.valueOf(kills[i]), attr(child(mob, "count"), "value"));

            Element info = child(img("wz-zh-CN/Quest.wz/QuestInfo.img.xml"), questId);
            assertEquals("1", attr(child(info, "autoStart"), "value"));
            assertEquals("1", attr(child(info, "autoPreComplete"), "value"));
            assertNotNull(child(img("wz-zh-CN/Quest.wz/Act.img.xml"), questId));
            assertNotNull(child(img("wz-zh-CN/Quest.wz/Say.img.xml"), questId));
        }
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
    void breakthroughSkillDescriptionsMentionStageRequirement() throws Exception {
        Element strings = img("wz-zh-CN/String.wz/Skill.img.xml");
        assertTrue(attr(child(child(strings, "1001004"), "h21"), "value")
                .contains("完成一转突破任务后可学习"));
        assertTrue(attr(child(child(strings, "3121002"), "h31"), "value")
                .contains("完成四转突破任务后可学习"));
        assertTrue(attr(child(child(strings, "5221006"), "h11"), "value")
                .contains("完成四转突破任务后可学习"));
    }

    @Test
    void breakthroughServiceGroupsSkillsByCurrentJobAndStage() {
        assertTrue(SkillBreakthroughService.isSupportedJob(100));
        assertTrue(SkillBreakthroughService.isSupportedJob(310));
        assertTrue(SkillBreakthroughService.isSupportedJob(522));
        assertEquals(List.of(1001003, 1001004, 1001005),
                SkillBreakthroughService.getSkillsForJobAndStage(112, 1));
        assertFalse(SkillBreakthroughService.getSkillsForJobAndStage(112, 4).isEmpty());
        assertTrue(SkillBreakthroughService.isBreakthroughSkill(5221006));
    }

    @Test
    void breakthroughQuestsRegisterStageProgressRequirements() {
        assertTrue(SkillBreakthroughService.isQuestMob(30006, 2220000));
        assertEquals(3, SkillBreakthroughService.getRequiredMobKills(30006, 2220000));
        assertTrue(SkillBreakthroughService.isQuestMob(30007, 3220000));
        assertTrue(SkillBreakthroughService.isQuestMob(30008, 7220000));
        assertTrue(SkillBreakthroughService.isQuestMob(30009, 8800002));
        assertEquals(1, SkillBreakthroughService.getRequiredMobKills(30009, 8800002));
    }

    @Test
    void stageEligibilityUsesJobMinimumLevelAndCompletedPreviousStage() {
        Character magician = mock(Character.class);
        when(magician.getJob()).thenReturn(Job.MAGICIAN);
        when(magician.getLevel()).thenReturn(8);
        assertTrue(SkillBreakthroughService.canStartQuest(magician, 30006));

        Character warrior = mock(Character.class);
        when(warrior.getJob()).thenReturn(Job.WARRIOR);
        when(warrior.getLevel()).thenReturn(9);
        assertFalse(SkillBreakthroughService.canStartQuest(warrior, 30006));

        Character fighter = mock(Character.class);
        when(fighter.getJob()).thenReturn(Job.FIGHTER);
        when(fighter.getLevel()).thenReturn(30);
        QuestStatus firstStage = mock(QuestStatus.class);
        when(fighter.getQuest(Quest.getInstance(30006))).thenReturn(firstStage);
        when(firstStage.getStatus()).thenReturn(QuestStatus.Status.STARTED);
        assertFalse(SkillBreakthroughService.canStartQuest(fighter, 30007));
        when(firstStage.getStatus()).thenReturn(QuestStatus.Status.COMPLETED);
        assertTrue(SkillBreakthroughService.canStartQuest(fighter, 30007));
    }

    @Test
    void assigningBreakthroughLevelRequiresMatchingStageCompletion() {
        Character player = mock(Character.class);
        when(player.getJob()).thenReturn(Job.HERO);
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(1001004);
        when(skill.getMaxLevel()).thenReturn(21);
        QuestStatus firstStage = mock(QuestStatus.class);
        when(player.getQuest(Quest.getInstance(30006))).thenReturn(firstStage);
        when(firstStage.getStatus()).thenReturn(QuestStatus.Status.STARTED);
        assertFalse(SkillBreakthroughService.canAssignLevel(player, skill, 21));
        when(firstStage.getStatus()).thenReturn(QuestStatus.Status.COMPLETED);
        assertTrue(SkillBreakthroughService.canAssignLevel(player, skill, 21));
    }

    @Test
    void breakthroughScriptsFlushStateAndUseStageReward() throws Exception {
        for (int questId = 30006; questId <= 30009; questId++) {
            String script = Files.readString(resolveResource("scripts-zh-CN/quest/" + questId + ".js"));
            assertTrue(script.indexOf("qm.forceStartQuest()")
                    < script.indexOf("qm.getPlayer().flushDelayedUpdateQuests()"));
            assertTrue(script.contains("grantCompletionReward(qm.getPlayer(), QUEST_ID)"));
        }
    }

    @Test
    void legacyMigrationPreservesStartedProgressAndMarksCompletedCharacters() throws Exception {
        String sql = Files.readString(resolveResource(
                "src/main/resources/db/migration/V1.11.47__migrate_staged_skill_breakthrough_quests.sql"));
        assertTrue(sql.contains("SET qs.quest = 30009"));
        assertTrue(sql.contains("-30009"));
        assertTrue(sql.contains("'legacy-all'"));
        assertTrue(sql.contains("WHERE NOT EXISTS"));
    }

    private static void assertSkillLevelValue(int skillId, int level, String field, String value) throws Exception {
        Element levelNode = child(child(child(img("wz/Skill.wz/" + (skillId / 10000) + ".img.xml"),
                "skill"), String.valueOf(skillId)), "level");
        assertEquals(value, attr(child(child(levelNode, String.valueOf(level)), field), "value"));
    }

    private static Element img(String relativePath) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(resolveResource(relativePath).toFile());
        return document.getDocumentElement();
    }

    private static Path resolveResource(String relativePath) {
        Path modulePath = Path.of(relativePath);
        return Files.exists(modulePath) ? modulePath : Path.of("gms-server").resolve(relativePath);
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

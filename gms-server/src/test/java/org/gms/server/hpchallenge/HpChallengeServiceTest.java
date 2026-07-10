package org.gms.server.hpchallenge;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HpChallengeServiceTest {

    @Test
    void stagesMatchChallengeWashDesign() throws Exception {
        Map<Integer, ?> stages = stages();

        assertEquals(7, stages.size());
        assertStage(stages.get(1), 120, 1850, 15000, 10200, 8950, 4800, 6, 100000000);
        assertStage(stages.get(2), 130, 2025, 18750, 12250, 10550, 5750, 3, 150000000);
        assertStage(stages.get(3), 140, 2200, 23000, 14600, 12900, 6800, 3, 200000000);
        assertStage(stages.get(4), 150, 2375, 27750, 17250, 15075, 7950, 3, 300000000);
        assertStage(stages.get(5), 160, 2650, 30000, 20200, 17400, 9200, 3, 400000000);
        assertStage(stages.get(6), 170, 3000, 30000, 23450, 19875, 10550, 3, 500000000);
        assertStage(stages.get(7), 180, 3400, 30000, 27000, 22500, 12000, 3, 800000000);
    }

    @Test
    void firstStageVisitsOwnInstructorLast() {
        assertIterableEquals(List.of(1012100, 1032001, 1052001, 1090000, 1022000),
                HpChallengeService.instructorVisitOrderForInstructor(1022000));
        assertIterableEquals(List.of(1012100, 1022000, 1052001, 1090000, 1032001),
                HpChallengeService.instructorVisitOrderForInstructor(1032001));
        assertIterableEquals(List.of(1032001, 1022000, 1052001, 1090000, 1012100),
                HpChallengeService.instructorVisitOrderForInstructor(1012100));
        assertIterableEquals(List.of(1012100, 1032001, 1022000, 1090000, 1052001),
                HpChallengeService.instructorVisitOrderForInstructor(1052001));
        assertIterableEquals(List.of(1012100, 1032001, 1022000, 1052001, 1090000),
                HpChallengeService.instructorVisitOrderForInstructor(1090000));
    }

    @Test
    void firstStageUsesNpcTalkForInstructorVisits() throws Exception {
        List<?> commonTasks = listValue(stages().get(1), "commonTasks");

        assertEquals("NPC_TALK", invoke(commonTasks.get(0), "targetType").toString());
        assertEquals(1012100, intValue(commonTasks.get(0), "primaryTarget"));
        assertEquals("NPC_TALK", invoke(commonTasks.get(4), "targetType").toString());
        assertEquals(1090000, intValue(commonTasks.get(4), "primaryTarget"));
        assertEquals("BOSS", invoke(commonTasks.get(5), "targetType").toString());
    }

    @Test
    void killCountsAreNormalizedForNativeMobQuestProgress() throws Exception {
        for (Object stage : stages().values()) {
            for (Object task : listValue(stage, "commonTasks")) {
                assertKillCount(task);
            }
            Map<?, ?> jobTasks = mapValue(stage, "jobTasks");
            for (Object tasks : jobTasks.values()) {
                for (Object task : (List<?>) tasks) {
                    assertKillCount(task);
                }
            }
            for (Object task : listValue(stage, "optionalTasks")) {
                assertKillCount(task);
            }
        }
    }

    private static void assertStage(Object stage, int requiredLevel, int mageHp, int mageMp, int warriorHp,
                                    int brawlerHp, int otherHp, int commonTasks, int mesoCost) throws Exception {
        assertEquals(requiredLevel, intValue(stage, "requiredLevel"));
        assertEquals(mageHp, intValue(stage, "mageHp"));
        assertEquals(mageMp, intValue(stage, "mageMp"));
        assertEquals(warriorHp, intValue(stage, "warriorHp"));
        assertEquals(brawlerHp, intValue(stage, "brawlerHp"));
        assertEquals(otherHp, intValue(stage, "otherHp"));

        assertEquals(commonTasks, listValue(stage, "commonTasks").size());
        assertEquals(8, listValue(stage, "optionalTasks").size());

        Map<?, ?> jobTasks = mapValue(stage, "jobTasks");
        assertEquals(5, jobTasks.size());
        for (Object tasks : jobTasks.values()) {
            assertEquals(3, ((List<?>) tasks).size());
        }

        boolean foundMesoTask = listValue(stage, "optionalTasks").stream()
                .anyMatch(task -> intValueUnchecked(task, "mesoCost") == mesoCost);
        assertTrue(foundMesoTask, "missing meso task " + mesoCost);
    }

    private static void assertKillCount(Object task) throws Exception {
        if (!"KILL".equals(invoke(task, "targetType").toString())) {
            return;
        }
        String group = invoke(task, "group").toString();
        int expected = switch (group) {
            case "MAIN_COMMON" -> 100;
            case "MAIN_JOB" -> 200;
            case "OPTIONAL" -> 999;
            default -> throw new AssertionError("unknown task group " + group);
        };
        assertEquals(expected, intValue(task, "requiredCount"),
                "normalized KILL count for " + group + " task " + invoke(task, "key"));
    }

    @Test
    void lifeProofKillTargetsUseFarmableMapSpawns() throws Exception {
        Map<Integer, Integer> spawnCounts = lifeProofKillTargetSpawnCounts();

        for (Object stage : stages().values()) {
            assertKillTargetsHaveFarmableSpawns(listValue(stage, "commonTasks"), spawnCounts);
            Map<?, ?> jobTasks = mapValue(stage, "jobTasks");
            for (Object tasks : jobTasks.values()) {
                assertKillTargetsHaveFarmableSpawns((List<?>) tasks, spawnCounts);
            }
            assertKillTargetsHaveFarmableSpawns(listValue(stage, "optionalTasks"), spawnCounts);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, ?> stages() throws Exception {
        Field field = HpChallengeService.class.getDeclaredField("STAGES");
        field.setAccessible(true);
        return (Map<Integer, ?>) field.get(null);
    }

    private static int intValue(Object target, String methodName) throws Exception {
        return (Integer) invoke(target, methodName);
    }

    private static int intValueUnchecked(Object target, String methodName) {
        try {
            return intValue(target, methodName);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static List<?> listValue(Object target, String methodName) throws Exception {
        return (List<?>) invoke(target, methodName);
    }

    private static Map<?, ?> mapValue(Object target, String methodName) throws Exception {
        return (Map<?, ?>) invoke(target, methodName);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void assertKillTargetsHaveFarmableSpawns(List<?> tasks,
                                                            Map<Integer, Integer> spawnCounts) throws Exception {
        for (Object task : tasks) {
            if (!"KILL".equals(invoke(task, "targetType").toString())) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Integer> targetIds = (List<Integer>) invoke(task, "targetIds");
            for (int targetId : targetIds) {
                int maxSpawnCount = spawnCounts.getOrDefault(targetId, 0);
                assertTrue(maxSpawnCount >= 3,
                        "life proof KILL target must have at least 3 direct map spawns: "
                                + targetId + " task=" + invoke(task, "key"));
            }
        }
    }

    private static Map<Integer, Integer> lifeProofKillTargetSpawnCounts() throws Exception {
        Set<Integer> targetIds = new HashSet<>();
        for (Object stage : stages().values()) {
            collectKillTargetIds(listValue(stage, "commonTasks"), targetIds);
            Map<?, ?> jobTasks = mapValue(stage, "jobTasks");
            for (Object tasks : jobTasks.values()) {
                collectKillTargetIds((List<?>) tasks, targetIds);
            }
            collectKillTargetIds(listValue(stage, "optionalTasks"), targetIds);
        }

        Map<Integer, Integer> maxSpawnByMob = new HashMap<>();
        Path mapRoot = resolveWzPath("wz/Map.wz/Map");
        try (var paths = Files.walk(mapRoot)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".img.xml"))
                    .forEach(path -> collectMapSpawnCounts(path, targetIds, maxSpawnByMob));
        }
        return maxSpawnByMob;
    }

    private static void collectKillTargetIds(List<?> tasks, Set<Integer> targetIds) throws Exception {
        for (Object task : tasks) {
            if (!"KILL".equals(invoke(task, "targetType").toString())) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Integer> ids = (List<Integer>) invoke(task, "targetIds");
            targetIds.addAll(ids);
        }
    }

    private static void collectMapSpawnCounts(Path path, Set<Integer> targetIds,
                                              Map<Integer, Integer> maxSpawnByMob) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
            Element life = childImgDirOrNull(document.getDocumentElement(), "life");
            if (life == null) {
                return;
            }
            Map<Integer, Integer> mapCounts = new HashMap<>();
            for (Element spawn : childImgDirs(life)) {
                if (!"m".equals(childValue(spawn, "string", "type"))) {
                    continue;
                }
                String idValue = childValue(spawn, "string", "id");
                if (idValue.isBlank()) {
                    continue;
                }
                int mobId = Integer.parseInt(idValue);
                if (targetIds.contains(mobId)) {
                    mapCounts.merge(mobId, 1, Integer::sum);
                }
            }
            for (Map.Entry<Integer, Integer> entry : mapCounts.entrySet()) {
                maxSpawnByMob.merge(entry.getKey(), entry.getValue(), Math::max);
            }
        } catch (Exception e) {
            throw new AssertionError("failed to parse map XML " + path, e);
        }
    }

    private static List<Element> childImgDirs(Element parent) {
        java.util.ArrayList<Element> result = new java.util.ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child
                    && "imgdir".equals(child.getTagName())) {
                result.add(child);
            }
        }
        return result;
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

    private static Path resolveWzPath(String relativePath) {
        Path modulePath = Path.of(relativePath);
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("gms-server").resolve(relativePath);
    }
}

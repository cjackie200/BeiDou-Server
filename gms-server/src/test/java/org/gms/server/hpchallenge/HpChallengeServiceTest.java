package org.gms.server.hpchallenge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HpChallengeServiceTest {

    @Test
    void stagesMatchChallengeWashDesign() throws Exception {
        Map<Integer, ?> stages = stages();

        assertEquals(7, stages.size());
        assertStage(stages.get(1), 120, 1850, 15000, 10200, 8950, 4800, 2, 100000000);
        assertStage(stages.get(2), 130, 2025, 18750, 12250, 10550, 5750, 3, 150000000);
        assertStage(stages.get(3), 140, 2200, 23000, 14600, 12900, 6800, 3, 200000000);
        assertStage(stages.get(4), 150, 2375, 27750, 17250, 15075, 7950, 3, 300000000);
        assertStage(stages.get(5), 160, 2650, 30000, 20200, 17400, 9200, 3, 400000000);
        assertStage(stages.get(6), 170, 3000, 30000, 23450, 19875, 10550, 3, 500000000);
        assertStage(stages.get(7), 180, 3400, 30000, 27000, 22500, 12000, 3, 800000000);
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
}

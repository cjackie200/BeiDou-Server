package org.gms.server.life;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class MonsterMobVacTest {
    @Test
    void recentMobVacPositionKeepsIndependentOriginSnapshot() throws Exception {
        Monster monster = new Monster(100100, new MonsterStats());
        Point origin = new Point(120, 45);
        Method mark = Monster.class.getDeclaredMethod("markMobVacPosition", Point.class);
        mark.setAccessible(true);

        mark.invoke(monster, origin);
        origin.move(999, 999);

        Point recorded = monster.getRecentMobVacPosition(2500);
        assertEquals(new Point(120, 45), recorded);
        assertNotSame(origin, recorded);
    }
}

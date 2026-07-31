package org.gms.constants.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExpTableTest {
    @Test
    void keepsExistingLevel200Requirement() {
        assertEquals(1_697_021_059, ExpTable.getExpNeededForLevel(200));
    }

    @Test
    void post200RequirementsIncreaseWithoutOverflowing() {
        int previous = ExpTable.getExpNeededForLevel(200);

        for (int level = 201; level < ExpTable.MAX_PLAYER_LEVEL; level++) {
            int required = ExpTable.getExpNeededForLevel(level);
            assertTrue(required > previous, "level " + level + " must require more EXP");
            assertTrue(required > 0, "level " + level + " EXP must remain positive");
            previous = required;
        }

        assertEquals(2_089_021_059, ExpTable.getExpNeededForLevel(249));
    }

    @Test
    void rejectsNegativeLevels() {
        assertThrows(IllegalArgumentException.class, () -> ExpTable.getExpNeededForLevel(-1));
    }
}

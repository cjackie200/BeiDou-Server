package org.gms.server.quest;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WuGongPQScriptTest {
    @Test
    void centipedeQuestInstanceHasNoLevelRestriction() throws Exception {
        String event = Files.readString(Path.of("scripts-zh-CN/event/WuGongPQ.js"));
        String npc = Files.readString(Path.of("scripts-zh-CN/npc/9310006.js"));

        assertFalse(event.contains("minLevel"));
        assertFalse(event.contains("maxLevel"));
        assertFalse(event.contains("getLevel()"));
        assertFalse(event.contains("等级要求"));
        assertTrue(event.contains("ch.getMapId() == recruitMap"));

        assertFalse(npc.contains("LevelMin"));
        assertFalse(npc.contains("LevelMax"));
        assertFalse(npc.contains("cm.getLevel()"));
        assertFalse(npc.contains("等级要求"));
    }
}

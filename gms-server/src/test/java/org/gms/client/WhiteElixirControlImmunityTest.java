package org.gms.client;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhiteElixirControlImmunityTest {
    private static final long START_TIME = 1_000_000L;

    @Test
    void blocksSeduceAndConfuseForFiveMinutes() {
        WhiteElixirControlImmunity immunity = new WhiteElixirControlImmunity();
        immunity.activate(START_TIME);

        assertTrue(immunity.blocks(Disease.SEDUCE, START_TIME));
        assertTrue(immunity.blocks(Disease.CONFUSE, START_TIME + 299_999L));
        assertFalse(immunity.blocks(Disease.SEDUCE, START_TIME + 300_000L));
    }

    @Test
    void doesNotBlockOtherAbnormalStatuses() {
        WhiteElixirControlImmunity immunity = new WhiteElixirControlImmunity();
        immunity.activate(START_TIME);

        assertFalse(immunity.blocks(Disease.STUN, START_TIME));
        assertFalse(immunity.blocks(Disease.SEAL, START_TIME));
    }

    @Test
    void itemDataCarriesFiveMinuteBuffDuration() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(Path.of("wz", "Item.wz", "Consume", "0202.img.xml").toFile());
        Element item = findChild(document.getDocumentElement(), "imgdir", "02022544");
        Element spec = findChild(item, "imgdir", "spec");
        Element time = findChild(spec, "int", "time");

        assertNotNull(time);
        assertEquals("300000", time.getAttribute("value"));
    }

    private static Element findChild(Node parent, String tagName, String name) {
        if (parent == null) {
            return null;
        }
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && tagName.equals(element.getTagName())
                    && name.equals(element.getAttribute("name"))) {
                return element;
            }
        }
        return null;
    }
}

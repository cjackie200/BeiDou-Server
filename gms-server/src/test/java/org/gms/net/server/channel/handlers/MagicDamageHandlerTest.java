package org.gms.net.server.channel.handlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MagicDamageHandlerTest {

    @Test
    void bounceFillsAtMostSixTotalTargets() {
        assertEquals(5, MagicDamageHandler.calculateBounceCount(1, 10));
        assertEquals(3, MagicDamageHandler.calculateBounceCount(3, 10));
        assertEquals(0, MagicDamageHandler.calculateBounceCount(6, 10));
    }

    @Test
    void bounceNeverExceedsAvailableCandidates() {
        assertEquals(2, MagicDamageHandler.calculateBounceCount(1, 2));
        assertEquals(0, MagicDamageHandler.calculateBounceCount(1, 0));
        assertEquals(5, MagicDamageHandler.calculateBounceCount(-1, 10));
    }

}

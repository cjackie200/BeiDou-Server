package org.gms.net.server.channel.handlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractDealDamageHandlerTest {
    @Test
    void poisonFireWeaknessDamageBonusAppliesOneAndHalfRate() {
        assertEquals(150, AbstractDealDamageHandler.applyPoisonFireWeaknessDamageBonus(100));
        assertEquals(1, AbstractDealDamageHandler.applyPoisonFireWeaknessDamageBonus(1));
        assertEquals(0, AbstractDealDamageHandler.applyPoisonFireWeaknessDamageBonus(0));
        assertEquals(-100, AbstractDealDamageHandler.applyPoisonFireWeaknessDamageBonus(-100));
    }

    @Test
    void poisonFireWeaknessDamageBonusCapsAtIntegerMax() {
        assertEquals(Integer.MAX_VALUE,
                AbstractDealDamageHandler.applyPoisonFireWeaknessDamageBonus(Integer.MAX_VALUE));
    }
}

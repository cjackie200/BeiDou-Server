package org.gms.client;

import java.util.concurrent.TimeUnit;

final class WhiteElixirControlImmunity {
    static final long DURATION_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private volatile long expiresAt;

    void activate(long currentTime) {
        expiresAt = currentTime + DURATION_MILLIS;
    }

    boolean blocks(Disease disease, long currentTime) {
        if (disease != Disease.SEDUCE && disease != Disease.CONFUSE) {
            return false;
        }
        return currentTime < expiresAt;
    }
}

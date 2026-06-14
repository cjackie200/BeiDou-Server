package org.gms.server;

import org.gms.client.Character;

import java.awt.Point;

public final class MobVacState {
    private static final String PROP_PREFIX = "beidou.mobVac.";
    private static final String TARGET_X_SUFFIX = ".x";
    private static final String TARGET_Y_SUFFIX = ".y";
    private static final String MAP_SUFFIX = ".map";

    private MobVacState() {
    }

    public static String getToken(Character chr) {
        return System.getProperty(getKey(chr));
    }

    public static boolean isEnabled(Character chr) {
        String token = getToken(chr);
        return token != null && !token.isEmpty();
    }

    public static void enable(Character chr, String token, Point target) {
        System.setProperty(getKey(chr), token);
        System.setProperty(getTargetXKey(chr), Integer.toString(target.x));
        System.setProperty(getTargetYKey(chr), Integer.toString(target.y));
        System.setProperty(getMapKey(chr), Integer.toString(chr.getMapId()));
    }

    public static void disable(Character chr) {
        System.clearProperty(getKey(chr));
        System.clearProperty(getTargetXKey(chr));
        System.clearProperty(getTargetYKey(chr));
        System.clearProperty(getMapKey(chr));
    }

    public static Point getTarget(Character chr) {
        try {
            String x = System.getProperty(getTargetXKey(chr));
            String y = System.getProperty(getTargetYKey(chr));
            if (x == null || y == null) {
                return null;
            }
            return new Point(Integer.parseInt(x), Integer.parseInt(y));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isSameMap(Character chr) {
        try {
            String mapId = System.getProperty(getMapKey(chr));
            return mapId != null && Integer.parseInt(mapId) == chr.getMapId();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String getKey(Character chr) {
        return PROP_PREFIX + chr.getId();
    }

    private static String getTargetXKey(Character chr) {
        return getKey(chr) + TARGET_X_SUFFIX;
    }

    private static String getTargetYKey(Character chr) {
        return getKey(chr) + TARGET_Y_SUFFIX;
    }

    private static String getMapKey(Character chr) {
        return getKey(chr) + MAP_SUFFIX;
    }
}

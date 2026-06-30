package org.gms.server;

import org.gms.client.Character;
import org.gms.server.life.Monster;
import org.gms.server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

public final class MobVacManager {
    private static final int INTERVAL_MS = 1000;
    private static final int FRONT_DISTANCE = 120;
    private static final int MAX_MONSTERS = 80;
    private static final int TARGET_TOLERANCE_PIXELS = 24;

    private MobVacManager() {
    }

    public static Result enable(Character chr) {
        if (chr.getEventInstance() != null) {
            return Result.blocked();
        }

        String token = System.currentTimeMillis() + "_" + chr.getId();
        Point target = createTargetPoint(chr);
        MobVacState.enable(chr, token, target);

        Result result = pullMobs(chr);
        scheduleNext(chr, token);
        return result;
    }

    public static boolean disable(Character chr) {
        boolean enabled = MobVacState.isEnabled(chr);
        MobVacState.disable(chr);
        return enabled;
    }

    public static Result toggle(Character chr) {
        if (MobVacState.isEnabled(chr)) {
            MobVacState.disable(chr);
            return Result.closed();
        }
        return enable(chr);
    }

    public static boolean isEnabled(Character chr) {
        return MobVacState.isEnabled(chr);
    }

    private static void scheduleNext(Character chr, String token) {
        TimerManager.getInstance().schedule(() -> runLoop(chr, token), INTERVAL_MS);
    }

    private static void runLoop(Character chr, String token) {
        if (chr == null || !token.equals(MobVacState.getToken(chr))) {
            return;
        }

        try {
            if (!chr.isLoggedIn() || chr.getClient() == null) {
                MobVacState.disable(chr);
                return;
            }
            if (chr.getEventInstance() != null) {
                MobVacState.disable(chr);
                chr.dropMessage(5, "进入副本/活动地图，聚怪功能已自动关闭。");
                return;
            }
            if (!MobVacState.isSameMap(chr)) {
                MobVacState.disable(chr);
                chr.dropMessage(5, "切换地图后，聚怪功能已自动关闭。");
                return;
            }

            pullMobs(chr);
            scheduleNext(chr, token);
        } catch (Exception e) {
            MobVacState.disable(chr);
            chr.dropMessage(5, "聚怪功能异常，已自动关闭：" + e.getMessage());
        }
    }

    private static Result pullMobs(Character chr) {
        MapleMap map = chr.getMap();
        if (map == null) {
            return Result.opened(0, 0, 0);
        }

        Point target = MobVacState.getTarget(chr);
        if (target == null) {
            target = createTargetPoint(chr);
            MobVacState.enable(chr, MobVacState.getToken(chr), target);
        }

        int moved = 0;
        int skippedBoss = 0;
        int failed = 0;
        List<Monster> mobs = map.getAllMonsters();
        for (Monster mob : mobs) {
            if (moved >= MAX_MONSTERS) {
                break;
            }
            if (mob == null) {
                continue;
            }
            if (mob.isBoss()) {
                skippedBoss++;
                continue;
            }
            if (mob.isNearMobVacPosition(target, TARGET_TOLERANCE_PIXELS)) {
                continue;
            }
            try {
                mob.resetMobVacPosition(target);
                moved++;
            } catch (Exception e) {
                failed++;
            }
        }

        return Result.opened(moved, skippedBoss, failed);
    }

    private static Point createTargetPoint(Character chr) {
        Point pos = chr.getPosition();
        return new Point(pos.x + getFrontDirection(chr) * FRONT_DISTANCE, pos.y);
    }

    private static int getFrontDirection(Character chr) {
        return chr.getStance() % 2 == 0 ? 1 : -1;
    }

    public record Result(Status status, int moved, int skippedBoss, int failed) {
        private static Result opened(int moved, int skippedBoss, int failed) {
            return new Result(Status.OPENED, moved, skippedBoss, failed);
        }

        private static Result closed() {
            return new Result(Status.CLOSED, 0, 0, 0);
        }

        private static Result blocked() {
            return new Result(Status.BLOCKED, 0, 0, 0);
        }
    }

    public enum Status {
        OPENED,
        CLOSED,
        BLOCKED
    }
}

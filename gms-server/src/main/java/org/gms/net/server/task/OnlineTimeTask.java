package org.gms.net.server.task;

import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.constants.string.ExtendKey;
import org.gms.net.server.Server;
import org.gms.net.server.channel.Channel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class OnlineTimeTask implements Runnable {
    private static final int RESET_HOUR = 6;
    private final AtomicReference<LocalDate> lastUpdated = new AtomicReference<>(getRewardCycleDate());
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void run() {
        if (!Server.getInstance().isOnline()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        LocalDate now = getRewardCycleDate();
        boolean isNextDay = now.isAfter(lastUpdated.get());
        try {
            for (final Channel chan : Server.getInstance().getAllChannels()) {
                if (chan == null || chan.getPlayerStorage() == null) {
                    continue;
                }
                for (final Character chr : chan.getPlayerStorage().getAllCharacters()) {
                    updateCharacterOnlineTime(chr, isNextDay);
                }
            }
        } finally {
            lastUpdated.set(now);
            running.set(false);
        }
    }

    private void updateCharacterOnlineTime(Character chr, boolean isNextDay) {
        if (chr == null) {
            return;
        }
        try {
            int previousOnlineTime = chr.getCurrentOnlineTime();
            int initialOnlineTime = previousOnlineTime == -1 ? getInitialOnlineTime(chr) : previousOnlineTime;
            int onlineTime = chr.updateCurrentOnlineTime(initialOnlineTime, isNextDay, System.currentTimeMillis());
            if (shouldPersistOnlineTime(previousOnlineTime, onlineTime, isNextDay)) {
                chr.updateOnlineTime();
            }
        } catch (Exception e) {
            log.warn("Failed to update online reward time for character {}", chr.getId(), e);
        }
    }

    private boolean shouldPersistOnlineTime(int previousOnlineTime, int onlineTime, boolean isNextDay) {
        return isNextDay
                || previousOnlineTime == -1
                || previousOnlineTime / 60 != onlineTime / 60;
    }

    private int getInitialOnlineTime(Character chr) {
        try {
            String cycleStr = chr.getAbstractPlayerInteraction().getAccountExtendValue(ExtendKey.ONLINE_REWARD_CYCLE.getKey());
            String currentCycle = getRewardCycleDate().toString();
            if (cycleStr == null) {
                String legacyTimeStr = chr.getAbstractPlayerInteraction().getAccountExtendValue(ExtendKey.ONLINE_TIME.getKey(), true);
                int legacyTime = legacyTimeStr == null ? 0 : Integer.parseInt(legacyTimeStr);
                chr.getAbstractPlayerInteraction().saveOrUpdateAccountExtendValue(ExtendKey.ONLINE_TIME.getKey(), String.valueOf(legacyTime));
                chr.getAbstractPlayerInteraction().saveOrUpdateAccountExtendValue(ExtendKey.ONLINE_REWARD_CYCLE.getKey(), currentCycle);
                return legacyTime;
            }
            if (!currentCycle.equals(cycleStr)) {
                return 0;
            }
            String timeStr = chr.getAbstractPlayerInteraction().getAccountExtendValue(ExtendKey.ONLINE_TIME.getKey());
            return timeStr == null ? 0 : Integer.parseInt(timeStr);
        } catch (Exception e) {
            return 0;
        }
    }

    public static LocalDate getRewardCycleDate() {
        return LocalDateTime.now().minusHours(RESET_HOUR).toLocalDate();
    }
}

package org.gms.constants.string;

import lombok.Getter;

public enum ExtendKey {
    ONLINE_TIME("每日在线时间"),
    ONLINE_REWARD_CYCLE("在线奖励刷新周期");

    @Getter
    private final String key;

    ExtendKey(String key) {
        this.key = key;
    }
}

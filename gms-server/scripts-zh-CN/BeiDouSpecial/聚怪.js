var INTERVAL_MS = 5000;
var FRONT_DISTANCE = 120;
var MAX_MONSTERS = 80;
var PROP_PREFIX = "beidou.mobVac.";

function start() {
    var player = cm.getPlayer();
    var propKey = getPropKey(player);
    var System = Java.type("java.lang.System");

    if (isEnabled(propKey)) {
        System.clearProperty(propKey);
        cm.sendOk("聚怪功能已关闭。");
        cm.dispose();
        return;
    }

    if (player.getEventInstance() != null) {
        cm.sendOk("副本、组队任务或活动地图里不能开启聚怪。");
        cm.dispose();
        return;
    }

    var token = String(System.currentTimeMillis()) + "_" + player.getId();
    System.setProperty(propKey, token);

    var result = pullMobs(player);
    scheduleNext(player, token);

    cm.sendOk("聚怪功能已开启。\r\n\r\n每 5 秒自动吸一次普通怪，Boss 会跳过。\r\n再次点击本功能即可关闭。\r\n\r\n本次已聚集普通怪：" + result.moved + " 只"
        + (result.skippedBoss > 0 ? "\r\n已跳过 Boss：" + result.skippedBoss + " 只" : "")
        + (result.failed > 0 ? "\r\n失败：" + result.failed + " 只" : ""));
    cm.dispose();
}

function scheduleNext(player, token) {
    try {
        var TimerManager = Java.type("org.gms.server.TimerManager");
        var Runnable = Java.type("java.lang.Runnable");
        var task;
        try {
            task = new Runnable({
                run: function () {
                    runLoop(player, token);
                }
            });
        } catch (e1) {
            var Task = Java.extend(Runnable, {
                run: function () {
                    runLoop(player, token);
                }
            });
            task = new Task();
        }
        TimerManager.getInstance().schedule(task, INTERVAL_MS);
    } catch (e) {
        try {
            player.dropMessage(5, "聚怪定时任务启动失败：" + e);
        } catch (ignore) {
        }
    }
}

function runLoop(player, token) {
    var System = Java.type("java.lang.System");
    var propKey = getPropKey(player);

    if (System.getProperty(propKey) !== token) {
        return;
    }

    try {
        if (player == null || !player.isLoggedIn() || player.getClient() == null) {
            System.clearProperty(propKey);
            return;
        }
        if (player.getEventInstance() != null) {
            System.clearProperty(propKey);
            player.dropMessage(5, "进入副本/活动地图，聚怪功能已自动关闭。");
            return;
        }

        pullMobs(player);
        scheduleNext(player, token);
    } catch (e) {
        System.clearProperty(propKey);
        try {
            player.dropMessage(5, "聚怪功能异常，已自动关闭：" + e);
        } catch (ignore) {
        }
    }
}

function pullMobs(player) {
    var map = player.getMap();
    var result = {
        moved: 0,
        skippedBoss: 0,
        failed: 0
    };

    if (map == null) {
        return result;
    }

    var mobs = map.getAllMonsters();
    if (mobs == null || mobs.length == 0) {
        return result;
    }

    var Point = Java.type("java.awt.Point");
    var pos = player.getPosition();
    var target = new Point(pos.x + getFrontDirection(player) * FRONT_DISTANCE, pos.y);
    try {
        target = map.calcPointBelow(target);
    } catch (e) {
    }

    for (var i = 0; i < mobs.length && result.moved < MAX_MONSTERS; i++) {
        var mob = mobs[i];
        if (mob == null) {
            continue;
        }
        try {
            if (mob.isBoss()) {
                result.skippedBoss++;
                continue;
            }
        } catch (e) {
        }

        if (moveMob(map, mob, target)) {
            result.moved++;
        } else {
            result.failed++;
        }
    }

    return result;
}

function getPropKey(player) {
    return PROP_PREFIX + player.getId();
}

function isEnabled(propKey) {
    var System = Java.type("java.lang.System");
    var token = System.getProperty(propKey);
    return token != null && token.length > 0;
}

function getFrontDirection(player) {
    try {
        var stance = player.getStance();
        return stance % 2 == 0 ? 1 : -1;
    } catch (e) {
        return 1;
    }
}

function moveMob(map, mob, target) {
    var moved = false;

    try {
        mob.resetMobPosition(target);
        moved = true;
    } catch (e1) {
        try {
            map.moveMonster(mob, target);
            moved = true;
        } catch (e2) {
            try {
                mob.moveMonster(target);
                moved = true;
            } catch (e3) {
            }
        }
    }

    try {
        mob.setPosition(target);
        moved = true;
    } catch (e4) {
    }

    try {
        mob.refreshMobPosition();
        moved = true;
    } catch (e5) {
    }

    if (moved) {
        refreshMobForPlayers(map, mob);
    }

    return moved;
}

function refreshMobForPlayers(map, mob) {
    try {
        var players = map.getPlayers();
        var it = players.iterator();
        while (it.hasNext()) {
            var chr = it.next();
            if (chr == null || chr.getClient() == null) {
                continue;
            }
            var client = chr.getClient();
            try {
                mob.sendDestroyData(client);
            } catch (e1) {
            }
            try {
                mob.sendSpawnData(client);
            } catch (e2) {
            }
        }
    } catch (e3) {
        try {
            var PacketCreator = Java.type("org.gms.util.PacketCreator");
            map.broadcastMessage(PacketCreator.spawnMonster(mob, false));
            map.broadcastMessage(PacketCreator.controlMonster(mob, false, false));
        } catch (e4) {
        }
    }
}

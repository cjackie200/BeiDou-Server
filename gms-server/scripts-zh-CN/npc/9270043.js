/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
//Gachapon

var ids = [2000004, 2020012, 2000005, 2030007, 2022027, 2040001, 2041002, 2040805, 2040702, 2043802, 2040402, 2043702, 1302022, 1322021, 1322026, 1302026, 1442017, 1082147, 1102043, 1442016, 1402012, 1302027, 1322027, 1322025, 1312012, 1062000, 1332020, 1302028, 1372002, 1002033, 1092022, 1302021, 1102041, 1102042, 1322024, 1082148, 1002012, 1322012, 1322022, 1002020, 1302013, 1082146, 1442014, 1002096, 1302017, 1442012, 1322010, 1442011, 1442018, 1092011, 1092014, 1302003, 1432001, 1312011, 1002088, 1041020, 1322015, 1442004, 1422008, 1302056, 1432000, 1382001, 1041053, 1060014, 1050053, 1051032, 1050073, 1061036, 1002253, 1002034, 1051025, 1050067, 1051052, 1002072, 1002144, 1051054, 1050069, 1372007, 1050056, 1050074, 1002254, 1002274, 1002218, 1051055, 1382010, 1002246, 1050039, 1382007, 1372000, 1002013, 1050072, 1002036, 1002243, 1372008, 1382008, 1382011, 1092021, 1051034, 1050047, 1040019, 1041031, 1051033, 1002153, 1002252, 1051024, 1002153, 1050068, 1382003, 1382006, 1050055, 1051031, 1050025, 1002155, 1002245, 1452004, 1452023, 1060057, 1040071, 1002137, 1462009, 1452017, 1040025, 1041027, 1452005, 1452007, 1061057, 1472006, 1472019, 1060084, 1472028, 1002179, 1082074, 1332015, 1432001, 1060071, 1472007, 1472002, 1051009, 1061037, 1332016, 1332034, 1472020, 1102084, 1102086, 1102042, 1032026, 1082149];
var status = -1;
var ADVANCED_TICKET_ID = 5451000;
var NORMAL_TICKET_ID = 5220000;
var ADVANCED_BATCH_LIMIT = 50;

function start() {
    if (cm.haveItem(ADVANCED_TICKET_ID)) {
        var count = Math.min(cm.itemQuantity(ADVANCED_TICKET_ID), ADVANCED_BATCH_LIMIT);
        cm.sendYesNo(
            "\u4f60\u6301\u6709 #b" + cm.itemQuantity(ADVANCED_TICKET_ID) + "#k \u5f20#t" + ADVANCED_TICKET_ID + "#。\r\n\r\n" +
            "\u672c\u6b21\u5c06\u4e00\u6b21\u5f00\u542f #r" + count + "#k \u5f20\uff0c\u4e0d\u6ee1 " + ADVANCED_BATCH_LIMIT + " \u5f20\u5c31\u5168\u90e8\u5f00\u542f\u3002\r\n" +
            "\u8bf7\u786e\u8ba4\u80cc\u5305\u7684\u88c5\u5907\u3001\u6d88\u8017\u3001\u8bbe\u7f6e\u548c\u5176\u5b83\u680f\u90fd\u6709\u7a7a\u4f4d\u3002"
        );
    } else if (cm.haveItem(NORMAL_TICKET_ID)) {
        cm.sendYesNo("你可以使用快乐百宝箱。你想使用你的快乐百宝券吗？");
    } else {
        cm.sendSimple("欢迎来到冒险岛的快乐百宝箱。我可以为您做些什么呢？\r\n#L0#什么是快乐百宝箱？#l\r\n#L1#在哪里可以购买快乐百宝券？#l");
    }
}

function action(mode, type, selection) {
    if (mode !== 1) {
        cm.dispose();
        return;
    }

    if (cm.haveItem(ADVANCED_TICKET_ID)) {
        doAdvancedBatchOpen();
        cm.dispose();
        return;
    }

    if (cm.haveItem(NORMAL_TICKET_ID)) {
        if (hasRewardSpace()) {
            cm.gainItem(NORMAL_TICKET_ID, -1, false);
            if (!cm.doGachapon()) {
                cm.gainItem(NORMAL_TICKET_ID, 1, false);
            }
        } else {
            cm.sendOk("\u8bf7\u786e\u4fdd\u4f60\u7684 #r\u88c5\u5907#k\u3001#r\u6d88\u8017#k\u3001#r\u8bbe\u7f6e#k \u548c #r\u5176\u5b83#k \u7269\u54c1\u680f\u4e2d\u81f3\u5c11\u5404\u6709 1 \u4e2a\u7a7a\u4f4d\u3002");
        }
        cm.dispose();
        return;
    }

    status++;
    if (selection == 0) {
        cm.sendNext("玩转快乐百宝箱，赢得稀有卷轴、装备、椅子、技能书和其他酷炫物品！你只需要一张 #b快乐百宝券#k 就有机会获得随机组合的物品。");
    } else if (selection == 1) {
        cm.sendNext("快乐百宝券可以在#r现金商店#k购买，可以使用NX或枫叶点购买。点击屏幕右下角的红色商店图标进入#r现金商店#k，就能购买快乐百宝券。");
        cm.dispose();
    } else if (status == 1) {
        cm.sendNext("你会在快乐百宝箱中找到各种各样的物品，也更容易获得与当前城镇相关的物品和卷轴。");
        cm.dispose();
    }
}

function doAdvancedBatchOpen() {
    var ticketCount = cm.itemQuantity(ADVANCED_TICKET_ID);
    var targetCount = Math.min(ticketCount, ADVANCED_BATCH_LIMIT);
    var opened = 0;

    for (var i = 0; i < targetCount; i++) {
        if (!hasRewardSpace()) {
            break;
        }

        cm.gainItem(ADVANCED_TICKET_ID, -1, false);
        if (!cm.doGachapon()) {
            cm.gainItem(ADVANCED_TICKET_ID, 1, false);
            break;
        }
        opened++;
    }

    if (opened <= 0) {
        cm.sendOk("\u6ca1\u6709\u6210\u529f\u5f00\u542f\u3002\u8bf7\u786e\u8ba4\u5956\u6c60\u53ef\u7528\uff0c\u5e76\u4e14\u80cc\u5305\u7684\u88c5\u5907\u3001\u6d88\u8017\u3001\u8bbe\u7f6e\u548c\u5176\u5b83\u680f\u90fd\u6709\u7a7a\u4f4d\u3002");
        return;
    }

    var remain = cm.itemQuantity(ADVANCED_TICKET_ID);
    cm.sendOk(
        "\u672c\u6b21\u5df2\u5f00\u542f #b" + opened + "#k \u5f20#t" + ADVANCED_TICKET_ID + "#。\r\n" +
        "\u5269\u4f59\uff1a#b" + remain + "#k \u5f20\u3002"
    );
}

function hasRewardSpace() {
    return cm.canHold(1302000) && cm.canHold(2000000) && cm.canHold(3010001) && cm.canHold(4000000);
}

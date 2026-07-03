/*
	NPC ID: 2104
	NPC Name: 昨日酣睡
	Map: 青苹果乐园 (map 4)
	Description: Quest 30000 引导 NPC — 传送到 map 1 并完成任务
*/

var status = -1;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (mode == 1) {
            status++;
        } else {
            status--;
        }
        if (status == 0) {
            cm.sendNext("好的，那么我们出发吧。冒险旅途开始了！");
        } else if (status == 1) {
            cm.warp(1);
            cm.forceCompleteQuest(30000);
            cm.gainMeso(100000);
            cm.sendOk("欢迎来到北斗冒险岛！这是给你的启动资金，希望对您的冒险有所帮助。\r\n\r\n#fUI/CashShop.img/CSDiscount/bonus# 金币: 100,000");
        } else {
            cm.dispose();
        }
    }
}

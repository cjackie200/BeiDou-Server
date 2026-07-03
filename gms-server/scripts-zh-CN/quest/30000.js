/*
	Quest ID: 30000
	Quest Name: 来自北斗开发者的问候
	Type: autoStart 新手任务
	Description: 新手玩家点击灯泡 → 对话 → 传送到青苹果乐园(map 4) → 与NPC昨日酣睡对话完成
*/

var status = -1;

function start(mode, type, selection) {
    if (mode == -1) {
        qm.dispose();
    } else {
        if (mode == 1) {
            status++;
        } else {
            status--;
        }
        if (status == 0) {
            qm.sendNext("哦，尊贵的冒险者#r#h ##k，欢迎莅临#b《北斗冒险岛》#k这方被神秘与奇迹所笼罩的奇幻之地！吾乃您的引导者，#r小睡#k，一个集智慧与优雅于一身的存在，特此恭候您的到来。");
        } else if (status == 1) {
            qm.sendNextPrev("哈哈，恭喜您，被命运的骰子扔进了这场荒诞不经的大冒险！您准备好了吗？前方是无尽的搞笑、离奇和不可思议！从玛加提亚城的狂欢派对，直接跳入冰峰雪域的冰雪奇缘，再一头扎进森林迷宫的魔幻蘑菇圈，最后飘向星空岛屿的银河漂流瓶大会！");
        } else if (status == 2) {
            if (qm.getJobId() == 0) {
                qm.sendAcceptDecline("新手冒险家，您是否想前往#b青苹果乐园#k接受新手指导呢？");
            } else {
                qm.sendAcceptDecline("欢迎回归北斗冒险岛！要领取你的启动资金吗？");
            }
        } else if (status == 3) {
            if (qm.getJobId() == 0) {
                qm.warp(4);
                qm.forceStartQuest();
                qm.sendOk("好的，那么我们出发吧！请跟你面前的#b昨日酣睡#k对话，她会带你正式进入冒险岛世界。祝您游戏开心！");
            } else {
                qm.forceStartQuest();
                qm.gainMeso(100000);
                qm.forceCompleteQuest();
                qm.sendOk("欢迎回到北斗！这是给你的启动资金，希望对您的冒险有所帮助。\r\n\r\n#fUI/CashShop.img/CSDiscount/bonus# 金币: 100,000");
            }
        } else {
            qm.dispose();
        }
    }
}

function end(mode, type, selection) {
    if (mode == -1) {
        qm.dispose();
    } else {
        if (mode == 1) {
            status++;
        } else {
            status--;
        }
        if (status == 0) {
            qm.gainMeso(100000);
            qm.forceCompleteQuest();
            qm.sendOk("看来你已经见过小睡了，欢迎来到北斗，这是给你的启动资金，希望对您的冒险有所帮助。\r\n\r\n#fUI/CashShop.img/CSDiscount/bonus# 金币: 100,000");
        } else {
            qm.dispose();
        }
    }
}

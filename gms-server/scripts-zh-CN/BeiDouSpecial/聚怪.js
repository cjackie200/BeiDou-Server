function start() {
    var player = cm.getPlayer();
    var MobVacManager = Java.type("org.gms.server.MobVacManager");
    var statusText = MobVacManager.isEnabled(player) ? "#g当前状态：已开启#k" : "#r当前状态：未开启#k";

    cm.sendOk(statusText
        + "\r\n\r\n请在聊天输入框输入命令控制聚怪："
        + "\r\n开启聚怪：#b@kqjg#k"
        + "\r\n关闭聚怪：#b@gbjg#k"
        + "\r\n\r\n聚怪开启后，聚怪点会固定在首次定位位置，不会跟随你移动。"
        + "\r\n切换地图、进入副本/活动地图会自动关闭。");
    cm.dispose();
}

package org.gms.net.server.handlers.login;

import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.util.PacketCreator;

/**
 * @author kevintjuh93
 */
public final class AcceptToSHandler extends AbstractPacketHandler {

    @Override
    public boolean validateState(Client c) {
        return !c.isLoggedIn();
    }

    @Override
    public final void handlePacket(InPacket p, Client c) {
        if (p.available() == 0 || p.readByte() != 1 || c.acceptToS()) {
            c.disconnect(false, false);//Client dc's but just because I am cool I do this (:
            return;
        }
        // 注意：不调用 finishLogin()，避免将 loggedIn 设为 true
        // 导致后续 LoginPasswordHandler.validateState() 返回 false 而丢弃登录包
        // TOS 协议确认后，由 LoginPasswordHandler 完成实际的登录状态设置
        c.sendPacket(PacketCreator.getAuthSuccess(c));
    }
}

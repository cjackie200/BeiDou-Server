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
package org.gms.net.server.handlers;

import org.gms.client.Client;
import org.gms.net.PacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.server.quest.hook.InteractionHookManager;
import org.gms.server.quest.hook.InteractionHookPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomPacketHandler implements PacketHandler {
    private static final Logger log = LoggerFactory.getLogger(CustomPacketHandler.class);

    @Override
    public void handlePacket(InPacket p, Client c) {
        if (p.available() < 2) {
            return;
        }
        int subCommand = Short.toUnsignedInt(p.readShort());
        log.info("CustomPacket subCommand={} player={}", subCommand, c.getPlayer() == null ? "?" : c.getPlayer().getName());
        if (subCommand == InteractionHookPackets.C2S_INTERACTION_HOOK_EVENT) {
            InteractionHookManager.handlePacket(c, p);
        }
    }

    @Override
    public boolean validateState(Client c) {
        return true;
    }
}

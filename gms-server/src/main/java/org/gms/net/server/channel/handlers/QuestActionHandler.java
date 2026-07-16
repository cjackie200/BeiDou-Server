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
package org.gms.net.server.channel.handlers;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.QuestStatus;
import org.gms.constants.id.MapId;
import org.gms.constants.game.DelayedQuestUpdate;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.scripting.quest.QuestScriptManager;
import org.gms.server.hpchallenge.LifeProofQuest;
import org.gms.server.life.NPC;
import org.gms.server.quest.MonsterCardRingQuest;
import org.gms.server.quest.Quest;
import org.gms.server.quest.SkillBreakthroughService;
import org.gms.server.quest.hook.InteractionHookManager;
import org.gms.util.I18nUtil;
import org.gms.util.PacketCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * @author Matze
 */
public final class QuestActionHandler extends AbstractPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(QuestActionHandler.class);
    private static final short DARK_WUKONG_HUNT_QUEST = 30005;

    // isNpcNearby thanks to GabrielSin
    private static boolean isNpcNearby(InPacket p, Character player, Quest quest, int npcId) {
        Point playerP;
        Point pos = player.getPosition();

        if (p.available() >= 4) {
            playerP = new Point(p.readShort(), p.readShort());
            if (playerP.distance(pos) > 1000) {     // thanks Darter (YungMoozi) for reporting unchecked player position
                playerP = pos;
            }
        } else {
            playerP = pos;
        }

        if (!quest.isAutoStart() && !quest.isAutoComplete()) {
            NPC npc = player.getMap().getNPCById(npcId);
            if (npc == null) {
                return false;
            }

            Point npcP = npc.getPosition();
            if (Math.abs(npcP.getX() - playerP.getX()) > 1200 || Math.abs(npcP.getY() - playerP.getY()) > 800) {
                player.dropMessage(5, I18nUtil.getMessage("QuestActionHandler.isNpcNearby.message1"));
                return false;
            }
        }

        return true;
    }

    private static boolean shouldOpenLifeProofEndScript(Character player, Quest quest, short questId) {
        return player != null
                && LifeProofQuest.isVisibleQuestId(questId)
                && player.getQuest(quest).getStatus() == QuestStatus.Status.STARTED;
    }

    private static boolean handleInteractionHook(Client c, short questId, int npcId, byte action) {
        boolean handled = InteractionHookManager.handleNativeQuestAction(c, questId, npcId, action);
        Character player = c.getPlayer();
        boolean lifeProofQuest = LifeProofQuest.isVisibleQuestId(questId);
        if (player != null && (lifeProofQuest || MonsterCardRingQuest.isMonsterCardRingQuest(questId))) {
            log.debug("Native QUEST_ACTION player={} action={} questId={} npcId={} hookHandled={}",
                    player.getName(), action, questId, npcId, handled);
        }
        // 不再静默拦截未处理的 LifeProof 任务操作。
        // 让原生任务流程（canStart/canComplete + 任务脚本）处理，
        // 避免玩家点击任务入口后毫无反应。
        return handled;
    }

    static boolean isHookQuestAction(byte action) {
        return action == 1 || action == 2 || action == 4 || action == 5;
    }

    static boolean isRemoteScriptQuest(short questId) {
        return questId == DARK_WUKONG_HUNT_QUEST || SkillBreakthroughService.isQuestId(questId);
    }

    private static boolean canUseQuestNpc(Client c, InPacket p, Character player, Quest quest, short questId, int npcId) {
        if (isRemoteScriptQuest(questId)) {
            return true;
        }
        if (isNpcNearby(p, player, quest, npcId)) {
            return true;
        }
        c.sendPacket(PacketCreator.enableActions());
        return false;
    }

    private static boolean startQuestScriptIfPresent(Client c, short questId, int npcId) {
        QuestScriptManager scripts = QuestScriptManager.getInstance();
        if (!scripts.checkFunctionExists(c, questId, npcId, "start")) {
            return false;
        }
        scripts.start(c, questId, npcId);
        return true;
    }

    private static boolean endQuestScriptIfPresent(Client c, short questId, int npcId) {
        QuestScriptManager scripts = QuestScriptManager.getInstance();
        if (!scripts.checkFunctionExists(c, questId, npcId, "end")) {
            return false;
        }
        scripts.end(c, questId, npcId);
        return true;
    }

    private static boolean openReadyRemoteQuestCompletion(Client c, Character player, Quest quest,
                                                           short questId, int npcId) {
        if (!isRemoteScriptQuest(questId)) {
            return false;
        }
        QuestStatus status = player.getQuest(quest);
        if (!status.isRemoteCompletionReady()) {
            return false;
        }
        status.setStatus(QuestStatus.Status.STARTED);
        player.announceUpdateQuest(DelayedQuestUpdate.UPDATE, status, false);
        return endQuestScriptIfPresent(c, questId, npcId);
    }

    private static void completeQuestNatively(InPacket p, Character player, Quest quest, int npcId) {
        if (p.available() >= 2) {
            quest.complete(player, npcId, (int) p.readShort());
            return;
        }
        quest.complete(player, npcId);
    }

    @Override
    public final void handlePacket(InPacket p, Client c) {
        byte action = p.readByte();
        short questid = p.readShort();
        Character player = c.getPlayer();
        Quest quest = Quest.getInstance(questid);
        if (player.getMapId() == MapId.JAIL) {   //监狱地图不可使用任务脚本
            player.dropMessage(1,I18nUtil.getMessage("ActionHandler.map.message1"));
            c.sendPacket(PacketCreator.enableActions());
            return;
        }
        switch (action) {
            case 0: // Restore lost item, Credits Darter ( Rajan )
                p.readInt();
                int itemid = p.readInt();
                quest.restoreLostItem(player, itemid);
                break;
            case 1: { // Start Quest
                int npc = p.readInt();
                if (openReadyRemoteQuestCompletion(c, player, quest, questid, npc)) {
                    return;
                }
                if (handleInteractionHook(c, questid, npc, action)) {
                    return;
                }
                if (!canUseQuestNpc(c, p, player, quest, questid, npc)) {
                    return;
                }
                if (!quest.canStart(player, npc)) {
                    c.sendPacket(PacketCreator.enableActions());
                    break;
                }
                boolean success = QuestScriptManager.getInstance().checkFunctionExists(c, questid, npc, "start");
                boolean hasScriptRequirement = quest.hasScriptRequirement(false);
                if (success && (hasScriptRequirement || isRemoteScriptQuest(questid) || quest.isAutoStart())) {
                    QuestScriptManager.getInstance().start(c, questid, npc);
                } else {
                    quest.start(player, npc);
                }
                break;
            }
            case 2: { // Complete Quest
                int npc = p.readInt();
                if (openReadyRemoteQuestCompletion(c, player, quest, questid, npc)) {
                    return;
                }
                if (handleInteractionHook(c, questid, npc, action)) {
                    return;
                }
                boolean lifeProofProgress = shouldOpenLifeProofEndScript(player, quest, questid);
                boolean npcNearby = isRemoteScriptQuest(questid) || isNpcNearby(p, player, quest, npc);
                if (!npcNearby && !lifeProofProgress) {
                    c.sendPacket(PacketCreator.enableActions());
                    return;
                }
                int scriptNpc = npcNearby ? npc : 0;
                if (quest.canComplete(player, scriptNpc)) {
                    boolean success = QuestScriptManager.getInstance().checkFunctionExists(c, questid, scriptNpc, "end");
                    boolean hasScriptRequirement = quest.hasScriptRequirement(true);
                    if (success && (hasScriptRequirement || isRemoteScriptQuest(questid) || quest.isAutoStart())) {
                        QuestScriptManager.getInstance().end(c, questid, scriptNpc);
                    } else {
                        completeQuestNatively(p, player, quest, scriptNpc);
                    }
                } else if (shouldOpenLifeProofEndScript(player, quest, questid)) {
                    QuestScriptManager.getInstance().end(c, questid, scriptNpc);
                } else {
                    c.sendPacket(PacketCreator.enableActions());
                }
                break;
            }
            case 3: // forfeit quest
                if (LifeProofQuest.isForfeitBlocked(questid)) {
                    player.dropMessage(5, "生命之证任务不能放弃。");
                    return;
                }
                quest.forfeit(player);
                break;
            case 4: { // scripted start quest
                int npc = p.readInt();
                if (openReadyRemoteQuestCompletion(c, player, quest, questid, npc)) {
                    return;
                }
                if (handleInteractionHook(c, questid, npc, action)) {
                    return;
                }
                if (!canUseQuestNpc(c, p, player, quest, questid, npc)) {
                    return;
                }
                if (!quest.canStart(player, npc)) {
                    c.sendPacket(PacketCreator.enableActions());
                    break;
                }
                if (!startQuestScriptIfPresent(c, questid, npc)) {
                    quest.start(player, npc);
                }
                break;
            }
            case 5: { // scripted end quests
                int npc = p.readInt();
                if (openReadyRemoteQuestCompletion(c, player, quest, questid, npc)) {
                    return;
                }
                if (handleInteractionHook(c, questid, npc, action)) {
                    return;
                }
                boolean lifeProofProgress = shouldOpenLifeProofEndScript(player, quest, questid);
                boolean npcNearby = isRemoteScriptQuest(questid) || isNpcNearby(p, player, quest, npc);
                if (!npcNearby && !lifeProofProgress) {
                    c.sendPacket(PacketCreator.enableActions());
                    return;
                }
                int scriptNpc = npcNearby ? npc : 0;
                boolean canComplete = quest.canComplete(player, scriptNpc);
                if (!canComplete && !lifeProofProgress) {
                    c.sendPacket(PacketCreator.enableActions());
                    break;
                }
                if (!endQuestScriptIfPresent(c, questid, scriptNpc)) {
                    if (canComplete) {
                        completeQuestNatively(p, player, quest, scriptNpc);
                    } else {
                        c.sendPacket(PacketCreator.enableActions());
                    }
                }
                break;
            }
        }
    }
}
